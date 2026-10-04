import express, { Request, Response, NextFunction } from 'express';
import http from 'http';
import cors from 'cors';
import bcrypt from 'bcryptjs';
import { v4 as uuidv4 } from 'uuid';
import dotenv from 'dotenv';
import { query, checkDatabaseHealth, assertDatabaseConnection, withTransaction } from './database/db';
import { GameNetworkServer } from './network/websocketServer';
import { generateProceduralItem, persistItemToDatabase } from './items/proceduralGenerator';
import { getAuditLogs, logAdminAction } from './security/audit';
import { UserRole } from './types';
import { generateToken, verifyToken, checkRateLimit, clearRateLimit, revokeAllUserTokens } from './security/tokenService';
import { ShopService } from './economy/shopService';
import { TradingService } from './economy/tradingService';
import { hasPermission, PERMISSIONS } from './security/roles';

dotenv.config();

const app = express();
const server = http.createServer(app);
const PORT = parseInt(process.env.PORT || '3000', 10);

app.use(cors());
app.use(express.json({ limit: '100kb' }));

// Middleware: Verify JWT and extract user context
async function requireAuth(req: Request, res: Response, next: NextFunction) {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Unauthorized: Missing or invalid token format.' });
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = await verifyToken(token);
    (req as any).user = decoded;
    next();
  } catch (err: any) {
    return res.status(401).json({ error: 'Unauthorized: ' + (err.message || 'Token verification failed.') });
  }
}

// Health check endpoint
app.get('/api/health', async (req, res) => {
  const dbOk = await checkDatabaseHealth();
  res.json({
    status: dbOk ? 'HEALTHY' : 'DEGRADED',
    game: 'Void Realms',
    version: '1.0.0',
    databaseConnected: dbOk,
    uptimeSec: Math.floor(process.uptime()),
    timestamp: new Date().toISOString(),
  });
});

// 1. REGISTER: Strictly creates PLAYER accounts only. Never allows client role escalation.
app.post('/api/auth/register', async (req, res) => {
  const ip = req.ip || req.socket.remoteAddress || '127.0.0.1';
  const rate = checkRateLimit(`reg:${ip}`, 5, 300000, 600000);
  if (!rate.allowed) {
    return res.status(429).json({ error: `Too many registration attempts. Retry in ${rate.retryAfterSec}s.` });
  }

  const { username, email, password } = req.body;
  if (!username || !email || !password) {
    return res.status(400).json({ error: 'Username, email and password are required.' });
  }

  if (typeof username !== 'string' || username.length < 3 || username.length > 24) {
    return res.status(400).json({ error: 'Username must be between 3 and 24 characters.' });
  }

  if (typeof password !== 'string' || password.length < 8) {
    return res.status(400).json({ error: 'Password must be at least 8 characters long.' });
  }

  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    return res.status(400).json({ error: 'Invalid email address format.' });
  }

  try {
    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);
    const userId = uuidv4();

    // Security: Role is ALWAYS 'PLAYER' on public registration.
    await query(
      `INSERT INTO users (id, username, email, password_hash, salt, role)
       VALUES ($1, $2, $3, $4, $5, 'PLAYER')`,
      [userId, username.trim(), email.toLowerCase().trim(), passwordHash, salt]
    );

    const token = generateToken({ id: userId, username: username.trim(), role: 'PLAYER' });
    res.json({
      success: true,
      token,
      user: { id: userId, username: username.trim(), role: 'PLAYER' },
    });
  } catch (err: any) {
    // Sanitize DB error (do not leak table or schema details)
    if (err.code === '23505') { // Postgres unique_violation
      return res.status(409).json({ error: 'Username or email is already registered.' });
    }
    res.status(500).json({ error: 'Registration failed. Please try again.' });
  }
});

// 2. LOGIN: Protected with rate limiting, brute force lockout, and last_login update.
app.post('/api/auth/login', async (req, res) => {
  const ip = req.ip || req.socket.remoteAddress || '127.0.0.1';
  const { username, password } = req.body;
  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password required.' });
  }

  const rateKey = `login:${ip}:${username.toLowerCase()}`;
  const rate = checkRateLimit(rateKey, 5, 300000, 900000);
  if (!rate.allowed) {
    return res.status(429).json({ error: `Account temporarily locked due to failed attempts. Try again in ${rate.retryAfterSec}s.` });
  }

  try {
    const rows = await query('SELECT * FROM users WHERE LOWER(username) = LOWER($1)', [username]);
    if (rows.length === 0) {
      return res.status(401).json({ error: 'Invalid username or password.' });
    }

    const user = rows[0];
    const match = await bcrypt.compare(password, user.password_hash);
    if (!match) {
      return res.status(401).json({ error: 'Invalid username or password.' });
    }

    if (user.is_banned) {
      return res.status(403).json({ error: 'Account has been permanently or temporarily suspended.' });
    }

    // Login successful: reset rate limit counter & record last_login
    clearRateLimit(rateKey);
    await query('UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = $1', [user.id]);

    const token = generateToken({ id: user.id, username: user.username, role: user.role });
    res.json({
      success: true,
      token,
      user: { id: user.id, username: user.username, role: user.role },
    });
  } catch (err: any) {
    res.status(500).json({ error: 'Login service encountered an unexpected error.' });
  }
});

// 3. CHARACTER MANAGEMENT: Authenticated server-side creation & loading
app.get('/api/characters', requireAuth, async (req, res) => {
  const user = (req as any).user;
  try {
    const characters = await query(
      'SELECT id, name, level, xp, gold, void_shards, hp, max_hp, mana, max_mana, strength, defense, agility, crit_chance, crit_damage, zone_id, is_dead, created_at FROM characters WHERE user_id = $1 ORDER BY created_at ASC',
      [user.userId]
    );
    res.json({ characters });
  } catch (err: any) {
    res.status(500).json({ error: 'Failed to retrieve characters.' });
  }
});

app.post('/api/characters', requireAuth, async (req, res) => {
  const user = (req as any).user;
  const { name } = req.body;

  if (!name || typeof name !== 'string' || name.trim().length < 3 || name.trim().length > 18) {
    return res.status(400).json({ error: 'Character name must be between 3 and 18 characters.' });
  }

  try {
    const existing = await query('SELECT COUNT(*) as count FROM characters WHERE user_id = $1', [user.userId]);
    if (parseInt(existing[0].count, 10) >= 3) {
      return res.status(400).json({ error: 'Character slot limit reached (maximum 3 characters per account).' });
    }

    const charId = uuidv4();
    await withTransaction(async (client) => {
      // Create character
      await client.query(
        `INSERT INTO characters (id, user_id, name, level, hp, max_hp, mana, max_mana, strength, defense, agility, zone_id)
         VALUES ($1, $2, $3, 1, 150, 150, 100, 100, 10, 8, 10, 'astral_sanctuary')`,
        [charId, user.userId, name.trim()]
      );

      // Starter item: Novice Astral Blade
      const bladeRes = await client.query("SELECT id FROM items WHERE item_code = 'novice_void_blade'");
      if (bladeRes.rows.length > 0) {
        await client.query(
          `INSERT INTO inventory (id, character_id, item_id, slot_index, quantity)
           VALUES ($1, $2, $3, 0, 1)`,
          [uuidv4(), charId, bladeRes.rows[0].id]
        );
      }
    });

    res.json({ success: true, characterId: charId, name: name.trim() });
  } catch (err: any) {
    if (err.code === '23505') {
      return res.status(409).json({ error: 'Character name is already taken.' });
    }
    res.status(500).json({ error: 'Character creation failed.' });
  }
});

// 4. ECONOMY / SHOPS: Server-authoritative buy and sell with PostgreSQL transactions
app.post('/api/shop/buy', requireAuth, async (req, res) => {
  const user = (req as any).user;
  const { characterId, itemCode, quantity } = req.body;

  try {
    // Validate character ownership
    const ownerRes = await query('SELECT 1 FROM characters WHERE id = $1 AND user_id = $2', [characterId, user.userId]);
    if (ownerRes.length === 0) {
      return res.status(403).json({ error: 'You do not own this character.' });
    }

    const result = await ShopService.buyItem(characterId, itemCode, quantity || 1);
    res.json(result);
  } catch (err: any) {
    res.status(400).json({ error: err.message || 'Purchase failed.' });
  }
});

app.post('/api/shop/sell', requireAuth, async (req, res) => {
  const user = (req as any).user;
  const { characterId, itemId, quantity } = req.body;

  try {
    const ownerRes = await query('SELECT 1 FROM characters WHERE id = $1 AND user_id = $2', [characterId, user.userId]);
    if (ownerRes.length === 0) {
      return res.status(403).json({ error: 'You do not own this character.' });
    }

    const result = await ShopService.sellItem(characterId, itemId, quantity || 1);
    res.json(result);
  } catch (err: any) {
    res.status(400).json({ error: err.message || 'Sale failed.' });
  }
});

// 5. OWNER / ADMIN AUDIT: Server-authoritative permission check
app.get('/api/admin/audit-logs', requireAuth, async (req, res) => {
  const user = (req as any).user;
  if (!hasPermission(user.role, PERMISSIONS.AUDIT_VIEW)) {
    return res.status(403).json({ error: 'Forbidden: Insufficient administrative privileges.' });
  }

  try {
    const logs = await getAuditLogs(100);
    res.json({ logs });
  } catch (err: any) {
    res.status(500).json({ error: 'Failed to retrieve audit log.' });
  }
});

// 6. PROVISION OWNER (CLI / SECURE SERVER SCRIPT ONLY)
export async function provisionOwner(username: string, email: string, password: string): Promise<string> {
  const salt = await bcrypt.genSalt(12);
  const passwordHash = await bcrypt.hash(password, salt);
  const userId = uuidv4();

  await query(
    `INSERT INTO users (id, username, email, password_hash, salt, role)
     VALUES ($1, $2, $3, $4, $5, 'OWNER')
     ON CONFLICT (username) DO UPDATE SET role = 'OWNER', password_hash = $4, salt = $5`,
    [userId, username.trim(), email.toLowerCase().trim(), passwordHash, salt]
  );

  await logAdminAction({
    adminUserId: userId,
    adminUsername: username,
    adminRole: 'OWNER',
    action: 'PROVISION_OWNER',
    targetIdentifier: username,
    parameters: { email },
    result: 'SUCCESS',
    ipAddress: '127.0.0.1',
  });

  return userId;
}

// 7. WEBSOCKET SERVER INITIALIZATION
const gameNetwork = new GameNetworkServer(server);

// Strict server startup
if (process.env.NODE_ENV !== 'test' && require.main === module) {
  assertDatabaseConnection()
    .then(() => {
      server.listen(PORT, () => {
        console.log(`[VOID REALMS] Authoritative Game Server running on port ${PORT}`);
        console.log(`[VOID REALMS] WebSocket path: ws://localhost:${PORT}/game`);
      });
    })
    .catch((err) => {
      console.error('[FATAL] Database connection failed. Refusing to start production server.');
      console.error(err.message);
      process.exit(1);
    });
}

export { app, server, gameNetwork };
