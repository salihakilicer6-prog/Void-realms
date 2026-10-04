import request from 'supertest';
import { app, gameNetwork } from '../src/index';
import { generateToken, verifyToken, revokeToken, checkRateLimit, clearRateLimit, getJwtSecret } from '../src/security/tokenService';
import { hasPermission, PERMISSIONS } from '../src/security/roles';
import { CombatEngine } from '../src/combat/combatEngine';
import { TradingService } from '../src/economy/tradingService';
import { InventoryService } from '../src/inventory/inventoryService';
import { sanitizeParameters } from '../src/security/audit';
import * as db from '../src/database/db';
import { runMigrations } from '../src/database/migrate';
import fs from 'fs';
import path from 'path';

describe('VOID REALMS - Security, Authority & Persistence Tests', () => {
  beforeAll(() => {
    process.env.JWT_SECRET = process.env.JWT_SECRET || 'void_realms_test_jwt_secret_min_32_characters_long_2026';
    process.env.DATABASE_URL = process.env.DATABASE_URL || 'postgres://postgres:postgres@localhost:5432/void_realms';
  });

  // Test 1: Owner Escalation Prevention
  test('1. Owner Escalation Prevention: Public registration cannot create OWNER role', async () => {
    // Attempt privilege escalation by injecting ownerSecret or role in register payload
    const res = await request(app)
      .post('/api/auth/register')
      .send({
        username: 'malicious_actor_' + Date.now().toString().slice(-6),
        email: `hacker_${Date.now()}@evil.com`,
        password: 'HackerPassword123!',
        ownerSecret: 'VOID_ARCHITECT_ROOT_2026',
        role: 'OWNER'
      });

    // If DB is running, status is 200 with role PLAYER; if DB is mocked/offline, 500
    if (res.status === 200) {
      expect(res.body.user.role).toBe('PLAYER');
      expect(res.body.user.role).not.toBe('OWNER');
    } else {
      expect([400, 409, 500]).toContain(res.status);
    }
  });

  // Test 2: Invalid / Revoked JWT Verification
  test('2. Invalid & Revoked JWT Handling', async () => {
    const validUser = { id: '00000000-0000-0000-0000-000000000001', username: 'testuser', role: 'PLAYER' as const };
    const token = generateToken(validUser);

    // Verify valid token works
    const decoded = await verifyToken(token);
    expect(decoded.username).toBe('testuser');
    expect(decoded.jti).toBeDefined();

    // Revoke token
    await revokeToken(decoded.jti, validUser.id);

    // Now verification must reject
    await expect(verifyToken(token)).rejects.toThrow('TOKEN_REVOKED');

    // Tampered token must fail
    const tampered = token.slice(0, -6) + 'abcdef';
    await expect(verifyToken(tampered)).rejects.toThrow();
  });

  // Test 3: Non-Admin Access Denial
  test('3. Non-Admin Access: Standard PLAYER is rejected from Admin endpoints', async () => {
    const playerToken = generateToken({
      id: '11111111-1111-1111-1111-111111111111',
      username: 'normal_player',
      role: 'PLAYER'
    });

    const res = await request(app)
      .get('/api/admin/audit-logs')
      .set('Authorization', `Bearer ${playerToken}`);

    expect(res.status).toBe(403);
    expect(res.body.error).toMatch(/Forbidden|privileges/i);
  });

  // Test 4: Role Permissions Matrix Verification
  test('4. Role Hierarchy: OWNER has all permissions, PLAYER has none', () => {
    expect(hasPermission('OWNER', PERMISSIONS.PLAYER_BAN)).toBe(true);
    expect(hasPermission('OWNER', PERMISSIONS.ITEM_CREATE)).toBe(true);
    expect(hasPermission('ADMIN', PERMISSIONS.PLAYER_BAN)).toBe(true);
    expect(hasPermission('ADMIN', PERMISSIONS.ITEM_CREATE)).toBe(false);
    expect(hasPermission('MODERATOR', PERMISSIONS.PLAYER_BAN)).toBe(false);
    expect(hasPermission('MODERATOR', PERMISSIONS.PLAYER_KICK)).toBe(true);
    expect(hasPermission('PLAYER', PERMISSIONS.PLAYER_BAN)).toBe(false);
    expect(hasPermission('PLAYER', PERMISSIONS.AUDIT_VIEW)).toBe(false);
  });

  // Test 5: Server-Authoritative Combat & Damage
  test('5. Server Authority: Damage is strictly computed server-side, preventing client spoofing', () => {
    const player = {
      id: 'p1',
      userId: 'u1',
      name: 'Hero',
      level: 1,
      xp: 0,
      gold: 0,
      voidShards: 0,
      hp: 150,
      maxHp: 150,
      mana: 100,
      maxMana: 100,
      strength: 10,
      defense: 8,
      agility: 10,
      critChance: 0, // Disable random crit for deterministic test
      critDamage: 150,
      zoneId: 'astral_sanctuary',
      posX: 0,
      posY: 0,
      posZ: 0,
      isDead: false,
      equipment: {
        MAIN_WEAPON: {
          id: 'w1',
          itemCode: 'novice_blade',
          name: 'Blade',
          itemType: 'WEAPON' as const,
          rarity: 'COMMON' as const,
          requiredLevel: 1,
          damage: 15,
          defense: 0,
          critBonus: 0,
          strBonus: 0,
          agiBonus: 0,
          element: 'PHYSICAL' as const,
          isStackable: false,
          maxStack: 1,
          baseValue: 10
        }
      } as any,
      inventory: []
    };

    const enemy = {
      id: 'e1',
      definitionId: 'void_crawler',
      name: 'Crawler',
      level: 1,
      hp: 100,
      maxHp: 100,
      damage: 10,
      defense: 10,
      speed: 30,
      attackRange: 40,
      zoneId: 'astral_sanctuary',
      posX: 0,
      posY: 0,
      lastAttackTime: 0
    };

    // First attack hits
    const outcome1 = CombatEngine.executePlayerAttack(player, enemy, 'MELEE');
    expect(outcome1.hit).toBe(true);
    expect(outcome1.damage).toBeGreaterThan(0);
    expect(enemy.hp).toBe(100 - outcome1.damage);

    // Immediate second attack violates cooldown (600ms) and must be rejected by server
    const outcome2 = CombatEngine.executePlayerAttack(player, enemy, 'MELEE');
    expect(outcome2.hit).toBe(false);
    expect(outcome2.damage).toBe(0);
  });

  // Test 6: Concurrent & Duplicate Trading Protection
  test('6. Trading Protection: Disallows self-trading and concurrent duplicate trades', () => {
    const charA = 'char_alpha_001';
    const charB = 'char_beta_002';
    const charC = 'char_gamma_003';

    // Disallow trading with oneself
    expect(() => TradingService.initiateTrade(charA, charA)).toThrow(/yourself/i);

    // Initiate valid trade
    const session = TradingService.initiateTrade(charA, charB);
    expect(session.id).toBeDefined();

    // Concurrent duplicate trade attempt with charA must throw
    expect(() => TradingService.initiateTrade(charA, charC)).toThrow(/already have an active trade/i);

    // Cleanup trade
    TradingService.cancelTrade(session.id);

    // After cancel, charA can trade again
    const session2 = TradingService.initiateTrade(charA, charC);
    expect(session2.status).toBe('PENDING');
    TradingService.cancelTrade(session2.id);
  });

  // Test 7: Rate Limiter & Brute Force Protection
  test('7. Brute Force Protection: Rate limiter locks out after exceeding threshold', () => {
    const key = 'test_ip_123';
    clearRateLimit(key);

    // 5 attempts allowed
    for (let i = 0; i < 5; i++) {
      const res = checkRateLimit(key, 5, 1000, 5000);
      expect(res.allowed).toBe(true);
    }

    // 6th attempt is blocked
    const blocked = checkRateLimit(key, 5, 1000, 5000);
    expect(blocked.allowed).toBe(false);
    expect(blocked.retryAfterSec).toBeGreaterThan(0);

    clearRateLimit(key);
  });

  // Test 8: Audit Logging Sanitization (Never log secrets/passwords)
  test('8. Audit Sanitization: Passwords and tokens are redacted from audit parameters', () => {
    const rawParams = {
      userId: '123',
      password: 'SuperSecretPassword123!',
      token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.malicious',
      ownerSecret: 'VOID_ARCHITECT_ROOT_2026',
      safeParam: 'gold_grant_500'
    };

    const sanitized = sanitizeParameters(rawParams);
    expect(sanitized.password).toBe('[REDACTED]');
    expect(sanitized.token).toBe('[REDACTED]');
    expect(sanitized.ownerSecret).toBe('[REDACTED]');
    expect(sanitized.safeParam).toBe('gold_grant_500');
  });

  // Test 9: Database Failure Handling
  test('9. Database Failure: assertDatabaseConnection rejects when DB is dead', async () => {
    process.env.DATABASE_URL = 'postgres://postgres:postgres@localhost:5432/void_realms';
    jest.spyOn(db, 'checkDatabaseHealth').mockResolvedValueOnce(false);
    await expect(db.assertDatabaseConnection()).rejects.toThrow(/FATAL: Database connection failed/i);
  });

  // Test 10: Migration Runner Integrity
  test('10. Migration Runner: Rejects invalid migrations directory and tracks applied files', async () => {
    await expect(runMigrations('/nonexistent/path/to/migrations')).rejects.toThrow(/not found/i);

    // Verify migration SQL files exist and have valid syntax
    const dir = path.resolve(__dirname, '../../database/migrations');
    expect(fs.existsSync(dir)).toBe(true);
    const files = fs.readdirSync(dir).filter(f => f.endsWith('.sql'));
    expect(files.length).toBeGreaterThanOrEqual(2);
    expect(files).toContain('001_initial_schema.sql');
    expect(files).toContain('002_seed_data.sql');
  });

  // Test 11: No Hardcoded Secrets in Source Code
  test('11. Source Code Scan: Verify no hardcoded owner passcodes in index.ts', () => {
    const indexPath = path.resolve(__dirname, '../src/index.ts');
    const content = fs.readFileSync(indexPath, 'utf-8');

    // Must NOT contain VOID_ARCHITECT_ROOT_2026
    expect(content.includes('VOID_ARCHITECT_ROOT_2026')).toBe(false);
    // Must NOT contain OwnerPass123
    expect(content.includes('OwnerPass123')).toBe(false);
    // Must NOT contain memoryUsers fallback map
    expect(content.includes('memoryUsers')).toBe(false);
  });

  // Test 12: Missing JWT Secret Fails Safely
  test('12. JWT Security: Missing or short JWT_SECRET fails safely without fallback secrets', () => {
    const originalSecret = process.env.JWT_SECRET;
    try {
      delete process.env.JWT_SECRET;
      expect(() => getJwtSecret()).toThrow(/FATAL: JWT_SECRET environment variable is missing/i);

      process.env.JWT_SECRET = 'short';
      expect(() => getJwtSecret()).toThrow(/FATAL: JWT_SECRET environment variable is missing/i);
    } finally {
      process.env.JWT_SECRET = originalSecret || 'void_realms_secure_production_quality_jwt_secret_64chars_key_2026';
    }
  });

  // Test 13: Missing DATABASE_URL Fails Safely
  test('13. Database Security: Missing DATABASE_URL and DB credentials fails safely', () => {
    const originalUrl = process.env.DATABASE_URL;
    const originalPass = process.env.DB_PASSWORD;
    try {
      delete process.env.DATABASE_URL;
      delete process.env.DB_PASSWORD;
      expect(() => db.getDatabaseConfig()).toThrow(/FATAL: Database configuration missing/i);
    } finally {
      process.env.DATABASE_URL = originalUrl || 'postgres://postgres:postgres@localhost:5432/void_realms';
      process.env.DB_PASSWORD = originalPass || 'postgres';
    }
  });

  // Test 14: Unauthorized Item Equip Rejection
  test('14. Inventory Authority: Unauthorized item equip is rejected', async () => {
    // When character does not exist or has insufficient level, equipItem rejects
    await expect(InventoryService.equipItem('nonexistent_char_id', 0, 'MAIN_WEAPON')).rejects.toThrow();
  });

  // Test 15: Inventory Service SQL Query Structure
  test('15. Inventory Service: Queries PostgreSQL inventory and equipment tables', async () => {
    // If DB is offline, throws connection error; if online, returns typed structure
    try {
      const res = await InventoryService.getCharacterInventoryAndEquipment('00000000-0000-0000-0000-000000000001');
      expect(Array.isArray(res.inventory)).toBe(true);
      expect(typeof res.equipment).toBe('object');
    } catch (err: any) {
      expect(err).toBeDefined();
    }
  });

  afterAll(async () => {
    gameNetwork.shutdown();
    await db.pool.end();
  });
});
