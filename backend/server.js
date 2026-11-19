require('dotenv').config({
  path: require('path').join(__dirname, 'mesh.env')
});
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
  connectionString: process.env.DATABASE_URL
});

// Secret key for JWT
const JWT_SECRET = process.env.JWT_SECRET;

if (!JWT_SECRET) {
  throw new Error('JWT_SECRET is missing from mesh.env');
}

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
  message_type VARCHAR(50) DEFAULT 'CHAT',
  client_msg_id VARCHAR(100),
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


// FIELD USER REGISTRATION
app.post('/api/register', async (req, res) => {
  const { name, username, email, phone, password } = req.body;

  try {
    // Validate required fields
    if (!name || !username || !email || !phone || !password) {
      return res.status(400).json({
        error: "All fields are required."
      });
    }

    if (password.length < 8) {
      return res.status(400).json({
        error: "Password must be at least 8 characters."
      });
    }

    // Hash password
    const hashedPassword = await bcrypt.hash(password, 10);

    // Always create public registrations as FIELD_USER
    const result = await pool.query(
      `INSERT INTO users
       (name, username, email, phone, password, role)
       VALUES ($1, $2, $3, $4, $5, 'FIELD_USER')
       RETURNING id, name, username, email, phone, role`,
      [name.trim(), username.trim(), email.trim().toLowerCase(),
       phone.trim(), hashedPassword]
    );

    res.status(201).json({
      message: "Field User registered successfully.",
      user: result.rows[0]
    });

  } catch (err) {
    console.error("Registration error:", err);

    if (err.code === '23505') {
      return res.status(409).json({
        error: "Username or email already exists."
      });
    }

    res.status(500).json({
      error: "Registration failed. Please try again."
    });
  }
});



 // ROLE-BASED LOGIN
app.post('/api/login', async (req, res) => {
  const { username, password } = req.body;

  try {
    if (!username || !password) {
      return res.status(400).json({
        error: "Username and password are required."
      });
    }

    const userResult = await pool.query(
      'SELECT * FROM users WHERE username = $1',
      [username.trim()]
    );

    if (userResult.rows.length === 0) {
      return res.status(401).json({
        error: "Invalid username or password."
      });
    }

    const user = userResult.rows[0];

    // Check account status
    if (!user.is_active) {
      return res.status(403).json({
        error: "Your account has been deactivated. Contact Admin."
      });
    }

    // Verify password
    const match = await bcrypt.compare(password, user.password);

    if (!match) {
      return res.status(401).json({
        error: "Invalid username or password."
      });
    }

    // Generate JWT with role
    const token = jwt.sign(
      {
        id: user.id,
        username: user.username,
        role: user.role
      },
      JWT_SECRET,
      { expiresIn: '7d' }
    );

    res.json({
      message: "Login successful.",
      token,
      user: {
        id: user.id,
        name: user.name,
        username: user.username,
        email: user.email,
        phone: user.phone,
        role: user.role
      }
    });

  } catch (err) {
    console.error("Login error:", err);

    res.status(500).json({
      error: "Server error during login."
    });
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

// Get Only SOS / Emergency Messages (Protected Route)
app.get('/api/messages/sos', authenticateToken, async (req, res) => {
  try {
    const result = await pool.query(
      "SELECT * FROM messages WHERE content ILIKE '%SOS%' OR is_mesh = true ORDER BY timestamp DESC"
    );
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: "Failed to fetch SOS alerts." });
  }
});

app.patch('/api/messages/sos/:id/status', authenticateToken, async (req, res) => {
  try {
    const { status } = req.body;
    const { id } = req.params;

    const allowedStatuses = ['PENDING', 'ACKNOWLEDGED', 'RESOLVED'];

    if (!allowedStatuses.includes(status)) {
      return res.status(400).json({ error: 'Invalid status.' });
    }

    const result = await pool.query(
      `UPDATE messages
       SET status = $1
       WHERE id = $2
       RETURNING *`,
      [status, id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'SOS alert not found.' });
    }

    const updatedMessage = result.rows[0];

    req.app.get('io')?.emit('alert_status_updated', updatedMessage);

    res.json(updatedMessage);
  } catch (err) {
    console.error('Failed to update SOS status:', err);
    res.status(500).json({ error: 'Failed to update SOS status.' });
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
    const {
  sender,
  recipient,
  content,
  message_type,
  client_msg_id,
  is_mesh
} = data;
    const targetRecipient = recipient || 'rescue_room';

    try {
      const saved = await pool.query(
      `INSERT INTO messages
(sender, recipient, content, message_type, client_msg_id, is_mesh)
VALUES ($1, $2, $3, $4, $5, $6)
RETURNING *`,
[
  sender,
  targetRecipient,
  content,
  message_type || 'CHAT',
  client_msg_id || null,
  is_mesh || false
]
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