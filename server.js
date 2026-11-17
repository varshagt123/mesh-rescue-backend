const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const { Pool } = require('pg');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const cors = require('cors');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: "*" }
});

app.use(express.json());
app.use(cors());

// PostgreSQL Connection Pool
const pool = new Pool({
  connectionString: 'postgresql://postgres:postgres123@localhost:5432/mesh_rescue_db'
});

// Secret key for JWT
const JWT_SECRET = process.env.JWT_SECRET || 'super_secret_rescue_key';

// Track active connected field users in memory
const activeNodes = new Map(); // username -> socket.id

// Initialize Database Tables
async function initDB() {
  try {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        username VARCHAR(50) UNIQUE NOT NULL,
        password VARCHAR(255) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS messages (
        id SERIAL PRIMARY KEY,
        sender VARCHAR(50) NOT NULL,
        recipient VARCHAR(50),
        content TEXT NOT NULL,
        is_mesh BOOLEAN DEFAULT false,
        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    `);
    console.log("Database tables verified/created successfully.");
  } catch (err) {
    console.error("Error initializing database tables:", err);
  }
}
initDB();

// --- JWT AUTHENTICATION MIDDLEWARE ---
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1]; // Expects "Bearer TOKEN"

  if (!token) return res.status(401).json({ error: "Access token required." });

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) return res.status(403).json({ error: "Invalid or expired token." });
    req.user = user;
    next();
  });
}

// --- REST API ENDPOINTS ---

// Register User
app.post('/api/register', async (req, res) => {
  const { username, password } = req.body;
  try {
    const hashedPassword = await bcrypt.hash(password, 10);
    const result = await pool.query(
      'INSERT INTO users (username, password) VALUES ($1, $2) RETURNING id, username',
      [username, hashedPassword]
    );
    res.status(201).json({ message: "User registered successfully", user: result.rows[0] });
  } catch (err) {
    res.status(400).json({ error: "Username already exists or invalid data." });
  }
});

// Login User
app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;
  try {
    const userResult = await pool.query('SELECT * FROM users WHERE username = $1', [username]);
    if (userResult.rows.length === 0) {
      return res.status(401).json({ error: "Invalid username or password." });
    }

    const user = userResult.rows[0];
    const match = await bcrypt.compare(password, user.password);
    if (!match) {
      return res.status(401).json({ error: "Invalid username or password." });
    }

    const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '7d' });
    res.json({ message: "Login successful", token, username: user.username });
  } catch (err) {
    res.status(500).json({ error: "Server error during login." });
  }
});

// Get Message History (Protected: Requires valid JWT Token)
app.get('/api/messages', authenticateToken, async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM messages ORDER BY timestamp ASC LIMIT 100');
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: "Failed to fetch messages." });
  }
});

// --- SOCKET.IO REAL-TIME & MESH RELAY HANDLING ---
io.on('connection', (socket) => {
  console.log(`A user connected: ${socket.id}`);

  // 1. Register node and track online status
  socket.on('register_node', ({ username, role }) => {
    socket.username = username;
    socket.role = role; // 'user' or 'rescue_team'

    if (role === 'rescue_team') {
      socket.join('rescue_room');
      console.log(`Rescue Team member ${username} joined rescue_room`);
    } else if (username) {
      socket.join(username);
      activeNodes.set(username, socket.id);
      console.log(`Field user ${username} online`);
      
      // Broadcast live online user list to rescue team dashboard
      io.to('rescue_room').emit('update_active_nodes', Array.from(activeNodes.keys()));
    }
  });

  // 2. Handle incoming chat & SOS messages
  socket.on('send_message', async (data) => {
    const { sender, recipient, content, is_mesh } = data;
    const targetRecipient = recipient || 'rescue_room';

    try {
      const saved = await pool.query(
        'INSERT INTO messages (sender, recipient, content, is_mesh) VALUES ($1, $2, $3, $4) RETURNING *',
        [sender, targetRecipient, content, is_mesh || false]
      );

      const messagePayload = saved.rows[0];

      if (targetRecipient === 'rescue_room') {
        io.to('rescue_room').emit('receive_message', messagePayload);
      } else {
        io.to(targetRecipient).emit('receive_message', messagePayload);
        io.to('rescue_room').emit('receive_message', messagePayload); // keep dashboard updated
      }
    } catch (err) {
      console.error("Failed to save/broadcast message:", err);
    }
  });

  // 3. Offline Store-and-Forward Sync
  socket.on('sync_offline_messages', async (offlineQueue) => {
    if (!Array.isArray(offlineQueue) || offlineQueue.length === 0) return;

    for (const msg of offlineQueue) {
      try {
        const saved = await pool.query(
          'INSERT INTO messages (sender, recipient, content, is_mesh) VALUES ($1, $2, $3, $4) RETURNING *',
          [msg.sender, msg.recipient || 'rescue_room', msg.content, true]
        );
        io.to('rescue_room').emit('receive_message', saved.rows[0]);
      } catch (err) {
        console.error("Error syncing offline message:", err);
      }
    }
  });

  socket.on('disconnect', () => {
    console.log(`User disconnected: ${socket.id}`);
    if (socket.username && socket.role !== 'rescue_team') {
      activeNodes.delete(socket.username);
      io.to('rescue_room').emit('update_active_nodes', Array.from(activeNodes.keys()));
    }
  });
});

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => {
  console.log(`Mesh Messenger Backend running on port ${PORT}`);
});