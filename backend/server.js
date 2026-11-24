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

// Initialize Database Tables

// Initialize Database Tables
async function initDB() {
  try {
    await pool.query(`
      -- Users Table
      CREATE TABLE IF NOT EXISTS users (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100),
        username VARCHAR(50) UNIQUE NOT NULL,
        email VARCHAR(255) UNIQUE,
        phone VARCHAR(20),
        password VARCHAR(255) NOT NULL,
        role VARCHAR(30) DEFAULT 'FIELD_USER',
        is_active BOOLEAN DEFAULT true,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );

      -- Messages Table
      CREATE TABLE IF NOT EXISTS messages (
        id SERIAL PRIMARY KEY,
        sender VARCHAR(50) NOT NULL,
        recipient VARCHAR(50),
        content TEXT NOT NULL,
        message_type VARCHAR(50) DEFAULT 'CHAT',
        client_msg_id VARCHAR(100),
        is_mesh BOOLEAN DEFAULT false,
        status VARCHAR(20) DEFAULT 'PENDING',
        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );

      -- Update existing Users Table
      ALTER TABLE users
        ADD COLUMN IF NOT EXISTS name VARCHAR(100),
        ADD COLUMN IF NOT EXISTS email VARCHAR(255),
        ADD COLUMN IF NOT EXISTS phone VARCHAR(20),
        ADD COLUMN IF NOT EXISTS role VARCHAR(30) DEFAULT 'FIELD_USER',
        ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true,
        ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

      -- Update existing Messages Table
      ALTER TABLE messages
        ADD COLUMN IF NOT EXISTS recipient VARCHAR(50),
        ADD COLUMN IF NOT EXISTS message_type VARCHAR(50) DEFAULT 'CHAT',
        ADD COLUMN IF NOT EXISTS client_msg_id VARCHAR(100),
        ADD COLUMN IF NOT EXISTS is_mesh BOOLEAN DEFAULT false,
        ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING',
        ADD COLUMN IF NOT EXISTS timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
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

// --- ROLE-BASED AUTHORIZATION MIDDLEWARE ---

const ROLES = Object.freeze({
  ADMIN: 'ADMIN',
  RESPONDER: 'RESPONDER',
  FIELD_USER: 'FIELD_USER'
});

function authorizeRoles(...allowedRoles) {
  return (req, res, next) => {
    if (!req.user || !allowedRoles.includes(req.user.role)) {
      return res.status(403).json({
        error: 'Access denied. Insufficient permissions.'
      });
    }

    next();
  };
}
// Get registered mesh devices

// Get registered mesh devices
app.get(
  '/api/devices',
  authenticateToken,
  async (req, res) => {
    try {
      const role = String(req.user.role || '').toUpperCase();

      let result;

      if (role === 'ADMIN' || role === 'RESPONDER' || role === 'RESCUE_TEAM') {
        result = await pool.query(`
          SELECT
            d.device_id,
            u.username,
            d.connectivity_status,
            d.sync_status,
            d.pending_messages,
            d.last_seen
          FROM devices d
          LEFT JOIN users u ON d.user_id = u.id
          ORDER BY d.created_at DESC
        `);
      } else if (role === 'FIELD_USER') {
        result = await pool.query(`
          SELECT
            d.device_id,
            u.username,
            d.connectivity_status,
            d.sync_status,
            d.pending_messages,
            d.last_seen
          FROM devices d
          LEFT JOIN users u ON d.user_id = u.id
          WHERE d.user_id = $1
          ORDER BY d.created_at DESC
        `, [req.user.id]);
      } else {
        return res.status(403).json({
          error: 'Access denied. Insufficient permissions.'
        });
      }

      res.json(result.rows);
    } catch (error) {
      console.error('Failed to fetch devices:', error);
      res.status(500).json({ error: 'Failed to fetch devices.' });
    }
  }
);

// --- REST API ENDPOINTS ---


// FIELD USER REGISTRATION
// FIELD USER REGISTRATION
app.post('/api/register', async (req, res) => {
  const { name, username, email, phone, password } = req.body;

  try {
    // 1. Required field validation
    if (!name || !username || !email || !phone || !password) {
      return res.status(400).json({
        error: "All fields are required."
      });
    }

    const cleanUsername = username.trim();

    // 2. Username validation
    if (cleanUsername.length < 4 || cleanUsername.length > 20) {
      return res.status(400).json({
        error: "Username must be between 4 and 20 characters."
      });
    }

    if (!/^[a-zA-Z0-9_]+$/.test(cleanUsername)) {
      return res.status(400).json({
        error: "Username can contain only letters, numbers, and underscores."
      });
    }

    // 3. Password validation
    if (password.length < 8 || password.length > 20) {
      return res.status(400).json({
        error: "Password must be between 8 and 20 characters."
      });
    }

    if (
      !/[A-Z]/.test(password) ||
      !/[a-z]/.test(password) ||
      !/[0-9]/.test(password) ||
      !/[^A-Za-z0-9]/.test(password) ||
      /\s/.test(password)
    ) {
      return res.status(400).json({
        error: "Password must contain uppercase, lowercase, number, special character, and no spaces."
      });
    }

    // 4. Hash password
    const hashedPassword = await bcrypt.hash(password, 10);

    // 5. Public registration always creates FIELD_USER
    const result = await pool.query(
      `INSERT INTO users
       (name, username, email, phone, password, role, status, is_active)
       VALUES ($1, $2, $3, $4, $5, 'FIELD_USER', 'APPROVED', TRUE)
       RETURNING id, name, username, email, phone, role, status`,
      [
        name.trim(),
        cleanUsername,
        email.trim().toLowerCase(),
        phone.trim(),
        hashedPassword
      ]
    );

    const newUser = result.rows[0];

    // 6. Generate JWT token
    const token = jwt.sign(
      {
        id: newUser.id,
        username: newUser.username,
        role: newUser.role
      },
      JWT_SECRET,
      { expiresIn: '7d' }
    );

    return res.status(201).json({
      message: "Field User registered successfully.",
      token,
      user: newUser
    });

  } catch (err) {
    console.error("Registration error:", err);

    if (err.code === '23505') {
      return res.status(409).json({
        error: "Username or email already exists."
      });
    }

    return res.status(500).json({
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

    // Verify password first
    const match = await bcrypt.compare(password, user.password);

    if (!match) {
      return res.status(401).json({
        error: "Invalid username or password."
      });
    }

    const role = String(user.role || '').toUpperCase();
    const status = String(user.status || 'APPROVED').toUpperCase();

    // Responder approval check
    if (role === 'RESPONDER') {

      if (status === 'PENDING') {
        return res.status(403).json({
          error: "Your Responder account is waiting for Admin approval."
        });
      }

      if (status === 'REJECTED') {
        return res.status(403).json({
          error: "Your Responder access request was rejected. Contact Admin."
        });
      }

      if (status !== 'APPROVED') {
        return res.status(403).json({
          error: "Your Responder account is not approved."
        });
      }
    }

    // Check account activation
    if (user.is_active === false) {
      return res.status(403).json({
        error: "Your account has been deactivated. Contact Admin."
      });
    }

    // Generate JWT
    const token = jwt.sign(
      {
        id: user.id,
        username: user.username,
        role: role
      },
      JWT_SECRET,
      { expiresIn: '7d' }
    );

    return res.json({
      message: "Login successful.",
      token,
      user: {
        id: user.id,
        name: user.name,
        username: user.username,
        email: user.email,
        phone: user.phone,
        role: role,
        status: status
      }
    });

  } catch (err) {
    console.error("Login error:", err);

    return res.status(500).json({
      error: "Server error during login."
    });
  }
});

/* =====================================
   RESPONDER ACCESS REQUEST
===================================== */

// Anyone can request Responder access.
// They cannot log in until Admin approves.
app.post('/api/responder/request', async (req, res) => {
  const { name, username, email, phone, password, organization } = req.body;

  try {
    if (!name || !username || !email || !phone || !password || !organization) {
      return res.status(400).json({
        error: 'All fields are required.'
      });
    }

    const cleanUsername = username.trim();

    if (cleanUsername.length < 4 || cleanUsername.length > 20 ||
        !/^[a-zA-Z0-9_]+$/.test(cleanUsername)) {
      return res.status(400).json({
        error: 'Username must be 4-20 characters and contain only letters, numbers or underscores.'
      });
    }

    if (
      password.length < 8 ||
      password.length > 20 ||
      !/[A-Z]/.test(password) ||
      !/[a-z]/.test(password) ||
      !/[0-9]/.test(password) ||
      !/[^A-Za-z0-9]/.test(password) ||
      /\s/.test(password)
    ) {
      return res.status(400).json({
        error: 'Password must contain uppercase, lowercase, number and special character, with no spaces.'
      });
    }

    const hashedPassword = await bcrypt.hash(password, 10);

    await pool.query(
      `INSERT INTO users
       (name, username, email, phone, password, role, status, is_active, organization)
       VALUES ($1, $2, $3, $4, $5, 'RESPONDER', 'PENDING', FALSE, $6)`,
      [
        name.trim(),
        cleanUsername,
        email.trim().toLowerCase(),
        phone.trim(),
        hashedPassword,
        organization.trim()
      ]
    );

    return res.status(201).json({
      message: 'Responder access request submitted. Please wait for Admin approval.'
    });

  } catch (err) {
    console.error('Responder request error:', err);

    if (err.code === '23505') {
      return res.status(409).json({
        error: 'Username or email already exists.'
      });
    }

    return res.status(500).json({
      error: 'Could not submit Responder request.'
    });
  }
});


/* =====================================
   ADMIN: VIEW PENDING RESPONDER REQUESTS
===================================== */

app.get(
  '/api/responder/requests',
  authenticateToken,
  authorizeRoles(ROLES.ADMIN),
  async (req, res) => {
    try {
      const result = await pool.query(
        `SELECT id, name, username, email, phone, organization, created_at
         FROM users
         WHERE UPPER(role) = 'RESPONDER'
         AND UPPER(status) = 'PENDING'
         ORDER BY created_at ASC`
      );

      return res.json(result.rows);

    } catch (err) {
      console.error('Responder requests error:', err);
      return res.status(500).json({
        error: 'Could not load responder requests.'
      });
    }
  }
);


/* =====================================
   ADMIN: APPROVE RESPONDER
===================================== */

app.put(
  '/api/responder/approve/:id',
  authenticateToken,
  authorizeRoles(ROLES.ADMIN),
  async (req, res) => {
    try {
      const result = await pool.query(
        `UPDATE users
         SET status = 'APPROVED', is_active = TRUE
         WHERE id = $1
         AND UPPER(role) = 'RESPONDER'
         AND UPPER(status) = 'PENDING'
         RETURNING id, username, role, status, is_active`,
        [req.params.id]
      );

      if (result.rows.length === 0) {
        return res.status(404).json({
          error: 'Pending Responder request not found.'
        });
      }

      return res.json({
        message: 'Responder approved successfully.',
        user: result.rows[0]
      });

    } catch (err) {
      console.error('Responder approval error:', err);
      return res.status(500).json({
        error: 'Could not approve Responder.'
      });
    }
  }
);


/* =====================================
   ADMIN: REJECT RESPONDER
===================================== */

app.put(
  '/api/responder/reject/:id',
  authenticateToken,
  authorizeRoles(ROLES.ADMIN),
  async (req, res) => {
    try {
      const result = await pool.query(
        `UPDATE users
         SET status = 'REJECTED', is_active = FALSE
         WHERE id = $1
         AND UPPER(role) = 'RESPONDER'
         AND UPPER(status) = 'PENDING'
         RETURNING id, username, role, status`,
        [req.params.id]
      );

      if (result.rows.length === 0) {
        return res.status(404).json({
          error: 'Pending Responder request not found.'
        });
      }

      return res.json({
        message: 'Responder request rejected.',
        user: result.rows[0]
      });

    } catch (err) {
      console.error('Responder rejection error:', err);
      return res.status(500).json({
        error: 'Could not reject Responder.'
      });
    }
  }
);


// Get Message History (Protected: Requires valid JWT Token)
app.get('/api/messages', authenticateToken, async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM messages ORDER BY timestamp ASC LIMIT 100');
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: "Failed to fetch messages." });
  }
});
app.get(
  '/api/users',
  authenticateToken,
  authorizeRoles(ROLES.ADMIN),
  async (req, res) => {
    try {
      const result = await pool.query(
        `SELECT
           id,
           name,
           username,
           email,
           phone,
           role,
           status,
           is_active,
           organization,
           created_at
         FROM users
         ORDER BY id DESC`
      );

      res.json(result.rows);

    } catch (err) {
      console.error('Failed to fetch users:', err);
      res.status(500).json({
        error: 'Failed to fetch users.'
      });
    }
  }
);
app.delete(
  '/api/users/:id',
  authenticateToken,
  authorizeRoles(ROLES.ADMIN),
  async (req, res) => {
    try {
      const userId = Number(req.params.id);

      if (!Number.isInteger(userId) || userId <= 0) {
        return res.status(400).json({
          error: 'Invalid user ID.'
        });
      }

      // Protect Admin accounts
      const user = await pool.query(
        'SELECT id, role FROM users WHERE id = $1',
        [userId]
      );

      if (user.rows.length === 0) {
        return res.status(404).json({
          error: 'User not found.'
        });
      }

      if (String(user.rows[0].role).toUpperCase() === 'ADMIN') {
        return res.status(403).json({
          error: 'Admin accounts cannot be deleted.'
        });
      }

      await pool.query(
        'DELETE FROM users WHERE id = $1',
        [userId]
      );

      return res.json({
        message: 'User deleted successfully.'
      });

    } catch (err) {
      console.error('Delete user error:', err);
      return res.status(500).json({
        error: 'Failed to delete user.'
      });
    }
  }
);
// Dashboard Statistics API
app.get(
  '/api/stats',
  authenticateToken,
  authorizeRoles(ROLES.ADMIN, ROLES.RESPONDER),
  async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT
        COUNT(*) FILTER (
          WHERE content ILIKE '%SOS%' OR is_mesh = true
        )::int AS "totalSOS",

        COUNT(*) FILTER (
          WHERE (content ILIKE '%SOS%' OR is_mesh = true)
          AND UPPER(COALESCE(status, 'PENDING')) = 'PENDING'
        )::int AS pending,

        COUNT(*) FILTER (
          WHERE (content ILIKE '%SOS%' OR is_mesh = true)
          AND UPPER(COALESCE(status, 'PENDING')) = 'ACKNOWLEDGED'
        )::int AS acknowledged,

        COUNT(*) FILTER (
          WHERE (content ILIKE '%SOS%' OR is_mesh = true)
          AND UPPER(COALESCE(status, 'PENDING')) = 'RESOLVED'
        )::int AS resolved,

        (SELECT COUNT(*)::int FROM users WHERE is_active = true)
        AS "totalUsers"

      FROM messages;
    `);

    res.json(result.rows[0]);

  } catch (err) {
    console.error('Dashboard statistics error:', err);
    res.status(500).json({
      error: 'Failed to fetch dashboard statistics.'
    });
  }
});
// Reports API
app.get('/api/reports', authenticateToken, async (req, res) => {
  try {
    res.json({
      byLocation: [],
      message: 'Location data is not available yet.'
    });
  } catch (err) {
    console.error('Reports error:', err);
    res.status(500).json({
      error: 'Failed to fetch reports.'
    });
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
// Authenticate Socket.IO connections using JWT
io.use((socket, next) => {
  const token = socket.handshake.auth?.token;

  if (!token) {
    return next(new Error('Authentication token required'));
  }

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) {
      return next(new Error('Invalid or expired token'));
    }

    socket.user = user;
    next();
  });
});
// --- SOCKET.IO REAL-TIME & MESH RELAY HANDLING ---
io.on('connection', (socket) => {
  console.log(`A user connected: ${socket.id}`);
// Register authenticated field device
socket.on('register_node', async ({ device_id }) => {
  console.log('register_node event received:', device_id, 'User:', socket.user);
  try {
    
    const user = socket.user;

    if (!user || !user.id || !user.username) return;
   const role = String(user.role || '').toUpperCase();

if (!['ADMIN', 'RESPONDER', 'RESCUE_TEAM', 'FIELD_USER'].includes(role)) {
  return;
}
    if (!device_id) return;

    await pool.query(
      `INSERT INTO devices
       (device_id, user_id, connectivity_status, sync_status, pending_messages, last_seen)
       VALUES ($1, $2, 'ONLINE', 'SYNCED', 0, CURRENT_TIMESTAMP)
       ON CONFLICT (device_id)
       DO UPDATE SET
         user_id = EXCLUDED.user_id,
         connectivity_status = 'ONLINE',
         last_seen = CURRENT_TIMESTAMP`,
      [device_id, user.id]
    );

     socket.deviceId = device_id;
    socket.username = user.username;
    socket.role = user.role;
    socket.join(user.username);

    activeNodes.set(user.username, socket.id);

    io.to('rescue_room').emit(
      'update_active_nodes',
      Array.from(activeNodes.keys())
    );

    console.log(`Device ${device_id} registered for ${user.username}`);
  } catch (err) {
    console.error('Device registration failed:', err);
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
  is_mesh,
  latitude,
  longitude
} = data;
    const targetRecipient = recipient || 'rescue_room';

    try {
      const saved = await pool.query(
      `INSERT INTO messages
(sender, recipient, content, message_type, client_msg_id, is_mesh, latitude, longitude)
VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
RETURNING *`,
[
  sender,
  targetRecipient,
  content,
  message_type || 'CHAT',
  client_msg_id || null,
  is_mesh || false,
  latitude ?? null,
  longitude ?? null
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

 
socket.on('disconnect', async () => {
  console.log(`User disconnected: ${socket.id}`);

  try {
    if (socket.deviceId) {
      await pool.query(
       `UPDATE devices
 SET connectivity_status = 'OFFLINE',
     last_seen = CURRENT_TIMESTAMP
 WHERE device_id = $1`,
        [socket.deviceId]
      );

      console.log(`Device ${socket.deviceId} marked OFFLINE`);
    }

    if (
      socket.username &&
      activeNodes.get(socket.username) === socket.id
    ) {
      activeNodes.delete(socket.username);
      io.to('rescue_room').emit(
        'update_active_nodes',
        Array.from(activeNodes.keys())
      );
    }
  } catch (err) {
    console.error('Disconnect update failed:', err);
  }
});
});

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => {
  console.log(`Mesh Messenger Backend running on port ${PORT}`);
});