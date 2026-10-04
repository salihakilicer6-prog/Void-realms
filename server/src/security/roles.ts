import { UserRole } from '../types';

export const ROLE_HIERARCHY: Record<UserRole, number> = {
  OWNER: 100,
  ADMIN: 50,
  MODERATOR: 20,
  PLAYER: 1,
};

export const PERMISSIONS = {
  // Player operations
  PLAYER_VIEW: 'admin.player.view',
  PLAYER_KICK: 'admin.player.kick',
  PLAYER_BAN: 'admin.player.ban',
  PLAYER_UNBAN: 'admin.player.unban',
  PLAYER_MUTE: 'admin.player.mute',
  PLAYER_TELEPORT: 'admin.player.teleport',
  PLAYER_GIVE_XP: 'admin.player.give_xp',
  PLAYER_SET_LEVEL: 'admin.player.set_level',
  PLAYER_GIVE_GOLD: 'admin.player.give_gold',
  PLAYER_GIVE_ITEM: 'admin.player.give_item',
  PLAYER_RESET: 'admin.player.reset',

  // World operations
  WORLD_TELEPORT: 'admin.world.teleport',
  WORLD_SPAWN_NPC: 'admin.world.spawn_npc',
  WORLD_SPAWN_ENEMY: 'admin.world.spawn_enemy',
  WORLD_WEATHER: 'admin.world.weather',
  WORLD_BROADCAST: 'admin.world.broadcast',

  // Item operations
  ITEM_CREATE: 'admin.item.create',
  ITEM_GRANT: 'admin.item.grant',
  ITEM_CONFIG: 'admin.item.config',

  // Server and audit
  SERVER_MAINTENANCE: 'admin.server.maintenance',
  SERVER_METRICS: 'admin.server.metrics',
  ROLES_MANAGE: 'admin.roles.manage',
  AUDIT_VIEW: 'admin.audit.view',
} as const;

export type PermissionNode = typeof PERMISSIONS[keyof typeof PERMISSIONS];

const ROLE_PERMISSIONS_MAP: Record<UserRole, PermissionNode[]> = {
  OWNER: Object.values(PERMISSIONS),
  ADMIN: [
    PERMISSIONS.PLAYER_VIEW,
    PERMISSIONS.PLAYER_KICK,
    PERMISSIONS.PLAYER_BAN,
    PERMISSIONS.PLAYER_UNBAN,
    PERMISSIONS.PLAYER_MUTE,
    PERMISSIONS.PLAYER_TELEPORT,
    PERMISSIONS.PLAYER_GIVE_XP,
    PERMISSIONS.PLAYER_GIVE_GOLD,
    PERMISSIONS.PLAYER_GIVE_ITEM,
    PERMISSIONS.WORLD_BROADCAST,
    PERMISSIONS.SERVER_METRICS,
    PERMISSIONS.AUDIT_VIEW,
  ],
  MODERATOR: [
    PERMISSIONS.PLAYER_VIEW,
    PERMISSIONS.PLAYER_KICK,
    PERMISSIONS.PLAYER_MUTE,
    PERMISSIONS.WORLD_BROADCAST,
  ],
  PLAYER: [],
};

export function hasPermission(role: UserRole, permission: PermissionNode): boolean {
  if (role === 'OWNER') return true;
  const granted = ROLE_PERMISSIONS_MAP[role] || [];
  return granted.includes(permission);
}

export function canActOn(actorRole: UserRole, targetRole: UserRole): boolean {
  if (actorRole === 'OWNER') return true;
  return ROLE_HIERARCHY[actorRole] > ROLE_HIERARCHY[targetRole];
}
