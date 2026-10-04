import { WebSocketServer, WebSocket } from 'ws';
import { Server } from 'http';
import { CharacterState, WSPacket, UserRole } from '../types';
import { EnemyManager } from '../enemies/enemyManager';
import { CombatEngine } from '../combat/combatEngine';
import { AdminService } from '../admin/adminService';
import { verifyToken, checkRateLimit } from '../security/tokenService';
import { hasPermission, PERMISSIONS } from '../security/roles';
import { query, withTransaction } from '../database/db';
import { TradingService } from '../economy/tradingService';
import { InventoryService } from '../inventory/inventoryService';

interface ConnectedClient {
  ws: WebSocket;
  userId: string;
  username: string;
  role: UserRole;
  character?: CharacterState;
  isAlive: boolean;
  packetCount: number;
  lastPacketReset: number;
  lastChatMessageTime: number;
}

export class GameNetworkServer {
  private wss: WebSocketServer;
  private clients: Map<WebSocket, ConnectedClient> = new Map();
  private userToSocket: Map<string, WebSocket> = new Map();
  private enemyManager: EnemyManager = new EnemyManager();
  private tickInterval?: NodeJS.Timeout;
  private heartbeatInterval?: NodeJS.Timeout;

  constructor(server: Server) {
    this.wss = new WebSocketServer({ server, path: '/game' });
    this.setupSocketEvents();
    this.spawnInitialWorldEnemies();
    this.startTickLoop();
    this.startHeartbeat();
  }

  private spawnInitialWorldEnemies() {
    this.enemyManager.spawnEnemy('void_crawler', 'astral_sanctuary', 80, -60);
    this.enemyManager.spawnEnemy('void_crawler', 'astral_sanctuary', -90, 75);
    this.enemyManager.spawnEnemy('rift_stalker', 'astral_sanctuary', 150, 120);
    this.enemyManager.spawnEnemy('astral_golem', 'astral_sanctuary', -180, -140);
    this.enemyManager.spawnEnemy('abyssal_lord', 'void_wastes', 0, 0);
    this.enemyManager.spawnEnemy('abyssal_lord', 'abyssal_citadel', 0, 0);
  }

  private setupSocketEvents() {
    this.wss.on('connection', (ws: WebSocket, req) => {
      const ip = req.socket.remoteAddress || '127.0.0.1';

      ws.on('pong', () => {
        const client = this.clients.get(ws);
        if (client) client.isAlive = true;
      });

      ws.on('message', async (rawData: string) => {
        try {
          if (rawData.length > 4096) {
            ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'Packet size exceeded limit' } }));
            return;
          }

          let packet: WSPacket;
          try {
            packet = JSON.parse(rawData.toString());
          } catch {
            ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'Malformed JSON payload' } }));
            return;
          }

          if (!packet || typeof packet.type !== 'string') {
            ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'Invalid packet schema' } }));
            return;
          }

          await this.handlePacket(ws, packet, ip);
        } catch (err: any) {
          // Never let uncaught socket errors crash the process
          console.error('[WS Error] Handled message error:', err.message);
          try {
            ws.send(JSON.stringify({ type: 'ERROR', payload: { message: err.message || 'Server error' } }));
          } catch {
            // Socket might be closed
          }
        }
      });

      ws.on('close', () => {
        const client = this.clients.get(ws);
        if (client) {
          if (client.character) {
            this.broadcast('PLAYER_LEFT', { characterId: client.character.id });
          }
          this.userToSocket.delete(client.userId);
          this.clients.delete(ws);
        }
      });

      ws.on('error', (err) => {
        console.error('[WS Socket Error]:', err.message);
      });
    });
  }

  private async handlePacket(ws: WebSocket, packet: WSPacket, ip: string) {
    let client = this.clients.get(ws);

    // 1. Authentication Packet
    if (packet.type === 'AUTH') {
      try {
        const token = packet.payload?.token;
        if (!token || typeof token !== 'string') {
          ws.send(JSON.stringify({ type: 'AUTH_ERROR', payload: { message: 'Token string required' } }));
          return;
        }

        const decoded = await verifyToken(token);

        // Check if user is already connected on another socket; if so, disconnect the older one
        const existingSocket = this.userToSocket.get(decoded.userId);
        if (existingSocket && existingSocket !== ws) {
          existingSocket.send(JSON.stringify({ type: 'SESSION_TERMINATED', payload: { message: 'Logged in from another location.' } }));
          existingSocket.close();
          this.clients.delete(existingSocket);
        }

        client = {
          ws,
          userId: decoded.userId,
          username: decoded.username,
          role: decoded.role,
          isAlive: true,
          packetCount: 0,
          lastPacketReset: Date.now(),
          lastChatMessageTime: 0,
        };

        this.clients.set(ws, client);
        this.userToSocket.set(decoded.userId, ws);

        ws.send(JSON.stringify({
          type: 'AUTH_SUCCESS',
          payload: { userId: decoded.userId, username: decoded.username, role: decoded.role },
          timestamp: Date.now(),
        }));
      } catch (err: any) {
        ws.send(JSON.stringify({ type: 'AUTH_ERROR', payload: { message: err.message || 'Authentication failed' } }));
      }
      return;
    }

    // 2. Reject unauthenticated packets for all protected operations
    if (!client) {
      if (packet.type === 'PING') {
        ws.send(JSON.stringify({ type: 'PONG', timestamp: Date.now() }));
        return;
      }
      ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'UNAUTHENTICATED: Must send AUTH packet first.' } }));
      return;
    }

    // 3. Packet rate limiting (Max 30 packets / sec per client)
    const now = Date.now();
    if (now - client.lastPacketReset > 1000) {
      client.packetCount = 0;
      client.lastPacketReset = now;
    }
    client.packetCount++;
    if (client.packetCount > 30) {
      ws.send(JSON.stringify({ type: 'RATE_LIMIT', payload: { message: 'Packet rate exceeded. Please slow down.' } }));
      return;
    }

    // 4. Dispatch packets
    switch (packet.type) {
      case 'PING':
        client.isAlive = true;
        ws.send(JSON.stringify({ type: 'PONG', timestamp: Date.now() }));
        break;

      case 'JOIN_WORLD': {
        // SECURITY FIX: Never accept client-provided CharacterState!
        // Client sends only { characterId }. Server loads from PostgreSQL.
        const charId = packet.payload?.characterId;
        if (!charId || typeof charId !== 'string') {
          ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'Invalid or missing characterId' } }));
          return;
        }

        const charRows = await query(
          'SELECT * FROM characters WHERE id = $1 AND user_id = $2',
          [charId, client.userId]
        );

        if (charRows.length === 0) {
          ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'Character not found or does not belong to you.' } }));
          return;
        }

        const dbChar = charRows[0];
        const { inventory, equipment } = await InventoryService.getCharacterInventoryAndEquipment(dbChar.id);

        const character: CharacterState = {
          id: dbChar.id,
          userId: dbChar.user_id,
          name: dbChar.name,
          level: dbChar.level,
          xp: Number(dbChar.xp),
          gold: Number(dbChar.gold),
          voidShards: Number(dbChar.void_shards),
          hp: dbChar.hp,
          maxHp: dbChar.max_hp,
          mana: dbChar.mana,
          maxMana: dbChar.max_mana,
          strength: dbChar.strength,
          defense: dbChar.defense,
          agility: dbChar.agility,
          critChance: Number(dbChar.crit_chance),
          critDamage: Number(dbChar.crit_damage),
          zoneId: dbChar.zone_id,
          posX: Number(dbChar.pos_x),
          posY: Number(dbChar.pos_y),
          posZ: Number(dbChar.pos_z),
          isDead: dbChar.is_dead,
          equipment: equipment as any,
          inventory: inventory as any,
        };

        client.character = character;

        this.broadcast('PLAYER_JOINED', {
          id: character.id,
          name: character.name,
          level: character.level,
          posX: character.posX,
          posY: character.posY,
          hp: character.hp,
          maxHp: character.maxHp,
        }, ws);

        const otherPlayers = Array.from(this.clients.values())
          .filter(c => c.character && c.ws !== ws)
          .map(c => ({
            id: c.character!.id,
            name: c.character!.name,
            level: c.character!.level,
            posX: c.character!.posX,
            posY: c.character!.posY,
            hp: c.character!.hp,
            maxHp: c.character!.maxHp,
          }));

        ws.send(JSON.stringify({
          type: 'WORLD_SNAPSHOT',
          payload: {
            character,
            players: otherPlayers,
            enemies: this.enemyManager.getEnemiesInZone(character.zoneId),
          },
        }));
        break;
      }

      case 'PLAYER_MOVE': {
        if (!client.character) return;
        const x = Number(packet.payload?.x);
        const y = Number(packet.payload?.y);
        if (isNaN(x) || isNaN(y)) return;

        // Speed-hack check: validate delta distance
        const dx = x - client.character.posX;
        const dy = y - client.character.posY;
        const distSq = dx * dx + dy * dy;
        const maxDist = 60; // Max allowed travel between packets
        if (distSq <= maxDist * maxDist) {
          client.character.posX = x;
          client.character.posY = y;
          this.broadcast('PLAYER_MOVED', {
            id: client.character.id,
            posX: x,
            posY: y,
          }, ws);
        }
        break;
      }

      case 'ATTACK_ENEMY': {
        if (!client.character || client.character.hp <= 0) return;
        const enemyId = packet.payload?.enemyId;
        const attackType = packet.payload?.attackType === 'VOID_BLAST' ? 'VOID_BLAST' : 'MELEE';
        if (!enemyId) return;

        const enemy = this.enemyManager.getEnemy(enemyId);
        if (!enemy || enemy.hp <= 0) return;

        // Server authoritative attack computation
        const result = CombatEngine.executePlayerAttack(client.character, enemy, attackType);
        if (result.hit) {
          this.broadcast('COMBAT_EVENT', {
            attackerId: client.character.id,
            targetId: enemy.id,
            damage: result.damage,
            isCrit: result.isCrit,
            targetRemainingHp: result.targetRemainingHp,
            targetDied: result.targetDied,
            xpAwarded: result.xpAwarded,
            goldAwarded: result.goldAwarded,
          });

          if (result.targetDied) {
            // Persist XP and gold server-side
            if (result.xpAwarded || result.goldAwarded) {
              await withTransaction(async (dbClient) => {
                await dbClient.query(
                  'UPDATE characters SET xp = xp + $1, gold = gold + $2, updated_at = CURRENT_TIMESTAMP WHERE id = $3',
                  [result.xpAwarded || 0, result.goldAwarded || 0, client.character!.id]
                );
              });
            }

            setTimeout(() => {
              this.enemyManager.spawnEnemy(enemy.definitionId, enemy.zoneId, enemy.posX, enemy.posY);
            }, 10000);
          }
        }
        break;
      }

      case 'CHAT_MESSAGE': {
        // Chat flood / rate limit protection (Max 1 message per 1.5 seconds)
        if (now - client.lastChatMessageTime < 1500) {
          ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'Chat rate limit. Please wait before typing.' } }));
          return;
        }

        // Mute check
        const userCheck = await query('SELECT is_muted FROM users WHERE id = $1', [client.userId]);
        if (userCheck.length > 0 && userCheck[0].is_muted) {
          ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'Your account is currently muted.' } }));
          return;
        }

        const rawText = packet.payload?.text?.toString() || '';
        const cleanText = rawText.trim().slice(0, 180);
        if (!cleanText) return;

        client.lastChatMessageTime = now;

        this.broadcast('CHAT_BROADCAST', {
          sender: client.character?.name || client.username,
          role: client.role,
          text: cleanText,
          timestamp: now,
        });
        break;
      }

      case 'EQUIP_ITEM': {
        if (!client.character) return;
        const slotIndex = Number(packet.payload?.slotIndex);
        const equipSlot = packet.payload?.equipSlot?.toString();
        if (isNaN(slotIndex) || !equipSlot) {
          ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'Invalid slotIndex or equipSlot' } }));
          return;
        }

        try {
          const result = await InventoryService.equipItem(client.character.id, slotIndex, equipSlot);
          client.character.equipment = result.equipment as any;
          client.character.inventory = result.inventory as any;
          ws.send(JSON.stringify({
            type: 'INVENTORY_SNAPSHOT',
            payload: result,
          }));
        } catch (err: any) {
          ws.send(JSON.stringify({
            type: 'ERROR',
            payload: { message: err.message || 'Equip failed' },
          }));
        }
        break;
      }

      case 'UNEQUIP_ITEM': {
        if (!client.character) return;
        const equipSlot = packet.payload?.equipSlot?.toString();
        if (!equipSlot) {
          ws.send(JSON.stringify({ type: 'ERROR', payload: { message: 'Missing equipSlot' } }));
          return;
        }

        try {
          const result = await InventoryService.unequipItem(client.character.id, equipSlot);
          client.character.equipment = result.equipment as any;
          client.character.inventory = result.inventory as any;
          ws.send(JSON.stringify({
            type: 'INVENTORY_SNAPSHOT',
            payload: result,
          }));
        } catch (err: any) {
          ws.send(JSON.stringify({
            type: 'ERROR',
            payload: { message: err.message || 'Unequip failed' },
          }));
        }
        break;
      }

      case 'ADMIN_ACTION': {
        await this.handleAdminPacket(client, packet.payload, ip);
        break;
      }
    }
  }

  private async handleAdminPacket(client: ConnectedClient, payload: any, ip: string) {
    if (!payload || typeof payload.action !== 'string') return;

    const adminCtx = {
      userId: client.userId,
      username: client.username,
      role: client.role,
      ipAddress: ip,
    };

    switch (payload.action) {
      case 'GIVE_XP': {
        if (!hasPermission(client.role, PERMISSIONS.PLAYER_GIVE_XP)) {
          client.ws.send(JSON.stringify({ type: 'ADMIN_RESPONSE', payload: { success: false, message: 'Permission denied' } }));
          return;
        }
        if (client.character) {
          const res = await AdminService.giveXp(adminCtx, client.character, Math.min(100000, Number(payload.amount) || 500));
          client.ws.send(JSON.stringify({ type: 'ADMIN_RESPONSE', payload: res }));
        }
        break;
      }

      case 'BROADCAST': {
        if (!hasPermission(client.role, PERMISSIONS.WORLD_BROADCAST)) {
          client.ws.send(JSON.stringify({ type: 'ADMIN_RESPONSE', payload: { success: false, message: 'Permission denied' } }));
          return;
        }
        const msg = (payload.message || '').toString().slice(0, 200);
        this.broadcast('SYSTEM_ANNOUNCEMENT', { message: msg, sender: client.username });
        break;
      }

      case 'BAN_USER': {
        if (!hasPermission(client.role, PERMISSIONS.PLAYER_BAN)) {
          client.ws.send(JSON.stringify({ type: 'ADMIN_RESPONSE', payload: { success: false, message: 'Permission denied' } }));
          return;
        }
        const targetUserId = payload.targetUserId;
        if (targetUserId) {
          await AdminService.moderatePlayer(adminCtx, targetUserId, 'BAN', payload.reason);
          // EVICT active session immediately!
          this.evictUser(targetUserId, 'You have been suspended by an administrator.');
          client.ws.send(JSON.stringify({ type: 'ADMIN_RESPONSE', payload: { success: true, message: 'User banned and evicted.' } }));
        }
        break;
      }
    }
  }

  /**
   * Immediately disconnects an active user (e.g. upon ban or token revocation).
   */
  public evictUser(userId: string, reason: string): void {
    const socket = this.userToSocket.get(userId);
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'EVICTED', payload: { reason } }));
      socket.close(1008, reason);
      this.clients.delete(socket);
      this.userToSocket.delete(userId);
    }
  }

  public broadcast(type: string, payload: any, excludeWs?: WebSocket) {
    const message = JSON.stringify({ type, payload, timestamp: Date.now() });
    for (const [wsClient] of this.clients.entries()) {
      if (wsClient !== excludeWs && wsClient.readyState === WebSocket.OPEN) {
        wsClient.send(message);
      }
    }
  }

  private startHeartbeat() {
    this.heartbeatInterval = setInterval(() => {
      for (const [ws, client] of this.clients.entries()) {
        if (!client.isAlive) {
          console.log(`[WS] Terminating inactive socket for user ${client.username}`);
          ws.terminate();
          this.clients.delete(ws);
          this.userToSocket.delete(client.userId);
          continue;
        }
        client.isAlive = false;
        ws.ping();
      }
    }, 30000);
  }

  private startTickLoop() {
    this.tickInterval = setInterval(() => {
      const playerPos = Array.from(this.clients.values())
        .filter(c => c.character)
        .map(c => ({ id: c.character!.id, x: c.character!.posX, y: c.character!.posY }));

      this.enemyManager.updateEnemies(0.05, playerPos);

      // Authoritative enemy attacks: clients never decide incoming damage.
      for (const client of this.clients.values()) {
        const character = client.character;
        if (!character || character.isDead || character.hp <= 0) continue;
        const enemies = this.enemyManager.getEnemiesInZone(character.zoneId);
        for (const enemy of enemies) {
          if (enemy.hp <= 0) continue;
          const dx = character.posX - enemy.posX;
          const dy = character.posY - enemy.posY;
          if (dx * dx + dy * dy > enemy.attackRange * enemy.attackRange) continue;

          const result = CombatEngine.executeEnemyAttack(enemy, character);
          if (!result.hit) continue;
          client.ws.send(JSON.stringify({
            type: 'PLAYER_DAMAGE',
            payload: {
              attackerId: enemy.id,
              damage: result.damage,
              playerRemainingHp: result.playerRemainingHp,
              playerDied: result.playerDied,
            },
            timestamp: Date.now(),
          }));

          if (result.playerDied) {
            setTimeout(async () => {
              if (!client.character) return;
              client.character.isDead = false;
              client.character.hp = client.character.maxHp;
              client.character.posX = 0;
              client.character.posY = 0;
              try {
                await query(
                  'UPDATE characters SET hp = $1, is_dead = FALSE, pos_x = 0, pos_y = 0, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
                  [client.character.hp, client.character.id]
                );
              } catch (err) {
                console.error('[RESPAWN] Failed to persist:', err);
              }
            }, 3000);
          }
          break;
        }
      }
    }, 50);
  }

  public shutdown() {
    if (this.tickInterval) clearInterval(this.tickInterval);
    if (this.heartbeatInterval) clearInterval(this.heartbeatInterval);
    this.wss.close();
  }
}
