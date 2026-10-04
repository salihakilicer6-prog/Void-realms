import jwt from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { UserRole } from '../types';
import { query } from '../database/db';

export interface TokenPayload {
  userId: string;
  username: string;
  role: UserRole;
  jti: string;
  iat?: number;
  exp?: number;
}

// In-memory quick lookup cache for revoked tokens & banned users
const revokedTokenIds = new Set<string>();
const bannedUserIds = new Set<string>();

// Rate limiter state: Map<ipOrUser, { count: number, resetTime: number, lockedUntil?: number }>
const rateLimits = new Map<string, { count: number; resetTime: number; lockedUntil?: number }>();

export function getJwtSecret(): string {
  const secret = process.env.JWT_SECRET;
  if (!secret || secret.trim().length < 16) {
    throw new Error('FATAL: JWT_SECRET environment variable is missing or less than 16 characters. Refusing to operate with fallback secrets.');
  }
  return secret;
}

export function generateToken(user: { id: string; username: string; role: UserRole }): string {
  const jti = uuidv4();
  const payload: TokenPayload = {
    userId: user.id,
    username: user.username,
    role: user.role,
    jti,
  };
  return jwt.sign(payload, getJwtSecret(), { expiresIn: '7d' });
}

export async function verifyToken(tokenString: string): Promise<TokenPayload> {
  const decoded = jwt.verify(tokenString, getJwtSecret()) as TokenPayload;

  if (revokedTokenIds.has(decoded.jti)) {
    throw new Error('TOKEN_REVOKED: Token has been revoked');
  }

  if (bannedUserIds.has(decoded.userId)) {
    throw new Error('USER_BANNED: User account has been suspended');
  }

  // Check database if not cached
  try {
    const revoked = await query('SELECT 1 FROM revoked_tokens WHERE jti = $1', [decoded.jti]);
    if (revoked.length > 0) {
      revokedTokenIds.add(decoded.jti);
      throw new Error('TOKEN_REVOKED: Token has been revoked');
    }

    const user = await query('SELECT is_banned FROM users WHERE id = $1', [decoded.userId]);
    if (user.length === 0 || user[0].is_banned) {
      bannedUserIds.add(decoded.userId);
      throw new Error('USER_BANNED: User account has been suspended');
    }
  } catch (err: any) {
    if (err.message.startsWith('TOKEN_REVOKED') || err.message.startsWith('USER_BANNED')) {
      throw err;
    }
    // If table doesn't exist yet during initial setup, proceed with memory check
  }

  return decoded;
}

export async function revokeToken(jti: string, userId: string): Promise<void> {
  revokedTokenIds.add(jti);
  try {
    await query(`
      CREATE TABLE IF NOT EXISTS revoked_tokens (
        jti VARCHAR(64) PRIMARY KEY,
        user_id UUID NOT NULL,
        revoked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
      );
    `);
    await query('INSERT INTO revoked_tokens (jti, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING', [jti, userId]);
  } catch (e) {
    console.error('[TOKEN] Error persisting token revocation:', e);
  }
}

export async function revokeAllUserTokens(userId: string): Promise<void> {
  bannedUserIds.add(userId);
  try {
    await query(`
      CREATE TABLE IF NOT EXISTS revoked_tokens (
        jti VARCHAR(64) PRIMARY KEY,
        user_id UUID NOT NULL,
        revoked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
      );
    `);
    await query('UPDATE users SET is_banned = TRUE WHERE id = $1', [userId]);
  } catch (e) {
    console.error('[TOKEN] Error marking user revoked:', e);
  }
}

export function checkRateLimit(key: string, maxAttempts = 5, windowMs = 300000, lockoutMs = 900000): { allowed: boolean; retryAfterSec?: number } {
  const now = Date.now();
  const entry = rateLimits.get(key);

  if (entry) {
    if (entry.lockedUntil && now < entry.lockedUntil) {
      return { allowed: false, retryAfterSec: Math.ceil((entry.lockedUntil - now) / 1000) };
    }

    if (now > entry.resetTime) {
      rateLimits.set(key, { count: 1, resetTime: now + windowMs });
      return { allowed: true };
    }

    entry.count++;
    if (entry.count > maxAttempts) {
      entry.lockedUntil = now + lockoutMs;
      return { allowed: false, retryAfterSec: Math.ceil(lockoutMs / 1000) };
    }
    return { allowed: true };
  }

  rateLimits.set(key, { count: 1, resetTime: now + windowMs });
  return { allowed: true };
}

export function clearRateLimit(key: string): void {
  rateLimits.delete(key);
}
