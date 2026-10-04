import { UserRole, CharacterState, GameItem } from '../types';
import { hasPermission, PERMISSIONS, canActOn } from '../security/roles';
import { logAdminAction } from '../security/audit';
import { generateProceduralItem, persistItemToDatabase } from '../items/proceduralGenerator';
import { InventoryService } from '../inventory/inventoryService';
import { PlayerService } from '../players/playerService';
import { query } from '../database/db';

export interface AdminContext {
  userId: string;
  username: string;
  role: UserRole;
  ipAddress?: string;
}

export class AdminService {
  /**
   * Grant XP to player
   */
  public static async giveXp(
    admin: AdminContext,
    target: CharacterState,
    amount: number
  ): Promise<{ success: boolean; message: string }> {
    if (!hasPermission(admin.role, PERMISSIONS.PLAYER_GIVE_XP)) {
      await logAdminAction({
        adminUserId: admin.userId,
        adminUsername: admin.username,
        adminRole: admin.role,
        action: 'GIVE_XP',
        targetIdentifier: target.name,
        parameters: { amount },
        result: 'DENIED',
        ipAddress: admin.ipAddress,
      });
      return { success: false, message: 'Permission denied.' };
    }

    const res = PlayerService.addExperience(target, amount);
    await logAdminAction({
      adminUserId: admin.userId,
      adminUsername: admin.username,
      adminRole: admin.role,
      action: 'GIVE_XP',
      targetIdentifier: target.name,
      parameters: { amount, newLevel: res.newLevel },
      result: 'SUCCESS',
      ipAddress: admin.ipAddress,
    });

    return {
      success: true,
      message: `Granted ${amount} XP to ${target.name}. (Level: ${res.newLevel})`,
    };
  }

  /**
   * Set player level directly
   */
  public static async setLevel(
    admin: AdminContext,
    target: CharacterState,
    targetLevel: number
  ): Promise<{ success: boolean; message: string }> {
    if (!hasPermission(admin.role, PERMISSIONS.PLAYER_SET_LEVEL)) {
      await logAdminAction({
        adminUserId: admin.userId,
        adminUsername: admin.username,
        adminRole: admin.role,
        action: 'SET_LEVEL',
        targetIdentifier: target.name,
        parameters: { targetLevel },
        result: 'DENIED',
        ipAddress: admin.ipAddress,
      });
      return { success: false, message: 'Permission denied.' };
    }

    target.level = Math.max(1, Math.min(100, targetLevel));
    target.xp = 0;
    PlayerService.recalculateStats(target);
    target.hp = target.maxHp;
    target.mana = target.maxMana;

    await logAdminAction({
      adminUserId: admin.userId,
      adminUsername: admin.username,
      adminRole: admin.role,
      action: 'SET_LEVEL',
      targetIdentifier: target.name,
      parameters: { targetLevel: target.level },
      result: 'SUCCESS',
      ipAddress: admin.ipAddress,
    });

    return { success: true, message: `Set ${target.name} level to ${target.level}.` };
  }

  /**
   * Grant Gold or Void Shards
   */
  public static async giveCurrency(
    admin: AdminContext,
    target: CharacterState,
    currency: 'GOLD' | 'VOID_SHARDS',
    amount: number
  ): Promise<{ success: boolean; message: string }> {
    if (!hasPermission(admin.role, PERMISSIONS.PLAYER_GIVE_GOLD)) {
      await logAdminAction({
        adminUserId: admin.userId,
        adminUsername: admin.username,
        adminRole: admin.role,
        action: 'GIVE_CURRENCY',
        targetIdentifier: target.name,
        parameters: { currency, amount },
        result: 'DENIED',
        ipAddress: admin.ipAddress,
      });
      return { success: false, message: 'Permission denied.' };
    }

    if (currency === 'GOLD') {
      target.gold += amount;
    } else {
      target.voidShards += amount;
    }

    await logAdminAction({
      adminUserId: admin.userId,
      adminUsername: admin.username,
      adminRole: admin.role,
      action: 'GIVE_CURRENCY',
      targetIdentifier: target.name,
      parameters: { currency, amount },
      result: 'SUCCESS',
      ipAddress: admin.ipAddress,
    });

    return { success: true, message: `Granted ${amount} ${currency} to ${target.name}.` };
  }

  /**
   * Spawn and grant an item to a player
   */
  public static async giveItem(
    admin: AdminContext,
    target: CharacterState,
    itemCodeOrLevel: string | number,
    forcedRarity?: any
  ): Promise<{ success: boolean; message: string; item?: GameItem }> {
    if (!hasPermission(admin.role, PERMISSIONS.PLAYER_GIVE_ITEM)) {
      await logAdminAction({
        adminUserId: admin.userId,
        adminUsername: admin.username,
        adminRole: admin.role,
        action: 'GIVE_ITEM',
        targetIdentifier: target.name,
        parameters: { itemCodeOrLevel },
        result: 'DENIED',
        ipAddress: admin.ipAddress,
      });
      return { success: false, message: 'Permission denied.' };
    }

    const item = generateProceduralItem({
      level: typeof itemCodeOrLevel === 'number' ? itemCodeOrLevel : target.level,
      fixedRarity: forcedRarity,
    });

    await persistItemToDatabase(item);
    const added = InventoryService.addItem(target, item);
    if (!added) {
      return { success: false, message: `Failed: ${target.name}'s inventory is full.` };
    }

    await logAdminAction({
      adminUserId: admin.userId,
      adminUsername: admin.username,
      adminRole: admin.role,
      action: 'GIVE_ITEM',
      targetIdentifier: target.name,
      parameters: { itemId: item.id, itemName: item.name, rarity: item.rarity },
      result: 'SUCCESS',
      ipAddress: admin.ipAddress,
    });

    return { success: true, message: `Granted [${item.rarity}] ${item.name} to ${target.name}.`, item };
  }

  /**
   * Ban or Mute player
   */
  public static async moderatePlayer(
    admin: AdminContext,
    targetUserId: string,
    action: 'BAN' | 'UNBAN' | 'MUTE' | 'UNMUTE',
    reason?: string
  ): Promise<{ success: boolean; message: string }> {
    const perm = action === 'BAN' || action === 'UNBAN' ? PERMISSIONS.PLAYER_BAN : PERMISSIONS.PLAYER_MUTE;
    if (!hasPermission(admin.role, perm)) {
      return { success: false, message: 'Permission denied.' };
    }

    const isBan = action === 'BAN';
    const isMute = action === 'MUTE';

    if (action === 'BAN' || action === 'UNBAN') {
      await query(`UPDATE users SET is_banned = $1, ban_reason = $2 WHERE id = $3`, [isBan, reason || null, targetUserId]);
    } else {
      await query(`UPDATE users SET is_muted = $1 WHERE id = $2`, [isMute, targetUserId]);
    }

    await logAdminAction({
      adminUserId: admin.userId,
      adminUsername: admin.username,
      adminRole: admin.role,
      action,
      targetIdentifier: targetUserId,
      parameters: { reason },
      result: 'SUCCESS',
      ipAddress: admin.ipAddress,
    });

    return { success: true, message: `Player successfully ${action.toLowerCase()}ed.` };
  }
}
