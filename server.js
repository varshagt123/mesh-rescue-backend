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

// Get Message History
app.get('/api/messages', async (req, res) => {
try {
const result = await pool.query('SELECT * FROM messages ORDER BY timestamp ASC LIMIT 100');
res.json(result.rows);
} catch (err) {
res.status(500).json({ error: "Failed to fetch messages." });
}
});

// --- SOCKET.IO REAL-TIME & MESH RELAY HANDLING ---
io.on('connection', (socket) => {
console.log('A user connected: ${socket.id}');

// Handle incoming chat messages (supports offline mesh hop simulation)
socket.on('send_message', async (data) => {
const { sender, recipient, content, is_mesh } = data;

try {
  // Save message to database
  await pool.query(
    'INSERT INTO messages (sender, recipient, content, is_mesh) VALUES ($1, $2, $3, $4)',
    [sender, recipient || 'global', content, is_mesh || false]
  );

  // Broadcast message to all connected mesh clients
  io.emit('receive_message', {
    sender,
    recipient: recipient || 'global',
    content,
    is_mesh: is_mesh || false,
    timestamp: new Date()
  });
} catch (err) {
  console.error("Failed to save/broadcast message:", err);
}


});

socket.on('disconnect', () => {
console.log('User disconnected: ${socket.id}');
});
});

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => {
console.log('Mesh Messenger Backend running on port ${PORT}');
});