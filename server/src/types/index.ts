export type UserRole = 'PLAYER' | 'MODERATOR' | 'ADMIN' | 'OWNER';

export type ItemRarity =
  | 'COMMON'
  | 'UNCOMMON'
  | 'RARE'
  | 'EPIC'
  | 'LEGENDARY'
  | 'MYTHIC'
  | 'DIVINE'
  | 'ANCIENT'
  | 'OWNER';

export type EquipSlot =
  | 'MAIN_WEAPON'
  | 'OFF_HAND'
  | 'HELMET'
  | 'CHEST'
  | 'GLOVES'
  | 'BOOTS'
  | 'RING_1'
  | 'RING_2'
  | 'NECKLACE'
  | 'SPECIAL';

export type ElementType = 'PHYSICAL' | 'VOID' | 'ASTRAL' | 'FIRE' | 'ICE' | 'LIGHTNING' | 'CHAOS';

export interface UserAccount {
  id: string;
  username: string;
  email: string;
  role: UserRole;
  isBanned: boolean;
  banReason?: string;
  isMuted: boolean;
  createdAt: Date;
}

export interface CharacterState {
  id: string;
  userId: string;
  name: string;
  level: number;
  xp: number;
  gold: number;
  voidShards: number;
  hp: number;
  maxHp: number;
  mana: number;
  maxMana: number;
  strength: number;
  defense: number;
  agility: number;
  critChance: number;
  critDamage: number;
  zoneId: string;
  posX: number;
  posY: number;
  posZ: number;
  isDead: boolean;
  equipment: Record<EquipSlot, GameItem | null>;
  inventory: InventorySlot[];
}

export interface GameItem {
  id: string;
  itemCode: string;
  name: string;
  itemType: 'WEAPON' | 'ARMOR' | 'CONSUMABLE' | 'MATERIAL' | 'ARTIFACT';
  equipSlot?: EquipSlot;
  rarity: ItemRarity;
  requiredLevel: number;
  damage: number;
  defense: number;
  critBonus: number;
  strBonus: number;
  agiBonus: number;
  element: ElementType;
  specialEffect?: string;
  isStackable: boolean;
  maxStack: number;
  baseValue: number;
  seedHash?: string;
}

export interface InventorySlot {
  slotIndex: number;
  item: GameItem;
  quantity: number;
  isLocked: boolean;
}

export interface EnemyInstance {
  id: string;
  definitionId: string;
  name: string;
  level: number;
  hp: number;
  maxHp: number;
  damage: number;
  defense: number;
  speed: number;
  attackRange: number;
  zoneId: string;
  posX: number;
  posY: number;
  aggroTargetId?: string;
  lastAttackTime: number;
  respawnTime?: number;
}

export interface QuestObjective {
  questId: string;
  title: string;
  description: string;
  questType: 'MAIN' | 'SIDE' | 'DAILY';
  objectiveType: 'KILL_MONSTER' | 'COLLECT_ITEM' | 'TALK_NPC';
  targetId: string;
  requiredCount: number;
  currentCount: number;
  isCompleted: boolean;
  isClaimed: boolean;
  rewardXp: number;
  rewardGold: number;
}

export interface AdminAuditRecord {
  id: string;
  adminUserId: string;
  adminUsername: string;
  adminRole: UserRole;
  action: string;
  targetIdentifier?: string;
  parameters: Record<string, any>;
  result: 'SUCCESS' | 'DENIED' | 'FAILED';
  ipAddress?: string;
  createdAt: Date;
}

export interface WSPacket<T = any> {
  type: string;
  seq: number;
  payload: T;
  timestamp: number;
}
