import { v4 as uuidv4 } from 'uuid';
import { GameItem, ItemRarity, EquipSlot, ElementType } from '../types';
import { query } from '../database/db';

export interface ProceduralBlueprint {
  baseId: string;
  name: string;
  type: 'WEAPON' | 'ARMOR';
  slot: EquipSlot;
  baseDmg: number;
  baseDef: number;
}

export const BASE_ITEMS: ProceduralBlueprint[] = [
  // Weapons
  { baseId: 'void_blade', name: 'Void Blade', type: 'WEAPON', slot: 'MAIN_WEAPON', baseDmg: 25, baseDef: 0 },
  { baseId: 'astral_staff', name: 'Astral Staff', type: 'WEAPON', slot: 'MAIN_WEAPON', baseDmg: 30, baseDef: 0 },
  { baseId: 'shadow_dagger', name: 'Shadow Dagger', type: 'WEAPON', slot: 'MAIN_WEAPON', baseDmg: 18, baseDef: 0 },
  { baseId: 'phase_scythe', name: 'Phase Scythe', type: 'WEAPON', slot: 'MAIN_WEAPON', baseDmg: 35, baseDef: 0 },
  { baseId: 'cosmic_bow', name: 'Cosmic Bow', type: 'WEAPON', slot: 'MAIN_WEAPON', baseDmg: 28, baseDef: 0 },
  { baseId: 'chrono_wand', name: 'Chrono Wand', type: 'WEAPON', slot: 'MAIN_WEAPON', baseDmg: 22, baseDef: 0 },
  { baseId: 'nether_cleaver', name: 'Nether Cleaver', type: 'WEAPON', slot: 'MAIN_WEAPON', baseDmg: 40, baseDef: 0 },
  { baseId: 'singularity_mace', name: 'Singularity Mace', type: 'WEAPON', slot: 'MAIN_WEAPON', baseDmg: 32, baseDef: 0 },
  // Armor
  { baseId: 'void_helm', name: 'Void Crown', type: 'ARMOR', slot: 'HELMET', baseDmg: 0, baseDef: 15 },
  { baseId: 'astral_cuirass', name: 'Astral Cuirass', type: 'ARMOR', slot: 'CHEST', baseDmg: 0, baseDef: 30 },
  { baseId: 'phase_greaves', name: 'Phase Greaves', type: 'ARMOR', slot: 'BOOTS', baseDmg: 0, baseDef: 18 },
  { baseId: 'shadow_gauntlets', name: 'Shadow Gauntlets', type: 'ARMOR', slot: 'GLOVES', baseDmg: 0, baseDef: 14 },
  { baseId: 'celestial_ring', name: 'Celestial Ring', type: 'ARMOR', slot: 'RING_1', baseDmg: 5, baseDef: 5 },
  { baseId: 'abyssal_pendant', name: 'Abyssal Pendant', type: 'ARMOR', slot: 'NECKLACE', baseDmg: 8, baseDef: 8 },
];

export const PREFIXES = [
  { name: 'Abyssal', statMultiplier: 1.4, element: 'VOID' as ElementType, effect: 'Void Corrosion: 5% armor pierce' },
  { name: 'Radiant', statMultiplier: 1.3, element: 'ASTRAL' as ElementType, effect: 'Astral Flare: Blinds on crit' },
  { name: 'Glacial', statMultiplier: 1.25, element: 'ICE' as ElementType, effect: 'Frostbite: Chills target by 20%' },
  { name: 'Volcanic', statMultiplier: 1.35, element: 'FIRE' as ElementType, effect: 'Ignite: Burn for 3s' },
  { name: 'Thunderous', statMultiplier: 1.3, element: 'LIGHTNING' as ElementType, effect: 'Shock: Arc lightning to nearby foe' },
  { name: 'Chaotic', statMultiplier: 1.5, element: 'CHAOS' as ElementType, effect: 'Entropy: Random 50-200% damage roll' },
  { name: 'Obsidian', statMultiplier: 1.2, element: 'PHYSICAL' as ElementType, effect: 'Indomitable: +15% block rate' },
  { name: 'Dreadful', statMultiplier: 1.35, element: 'VOID' as ElementType, effect: 'Terror: Decreases enemy attack' },
  { name: 'Ethereal', statMultiplier: 1.25, element: 'ASTRAL' as ElementType, effect: 'Phase Shift: 8% dodge chance' },
  { name: 'Sovereign', statMultiplier: 1.6, element: 'VOID' as ElementType, effect: 'Supreme Aura: Boosts all attributes by 10%' },
  { name: 'Vampiric', statMultiplier: 1.2, element: 'CHAOS' as ElementType, effect: 'Lifesteal: Heals for 12% damage dealt' },
  { name: 'Vorpal', statMultiplier: 1.45, element: 'PHYSICAL' as ElementType, effect: 'Decapitate: 3x damage against weakened foes' },
  { name: 'Singularity', statMultiplier: 1.7, element: 'VOID' as ElementType, effect: 'Gravitational Collapse: Pulls enemies close' },
  { name: 'Primordial', statMultiplier: 1.55, element: 'ASTRAL' as ElementType, effect: 'Genesis: Regenerates 5 Mana per sec' },
];

export const SUFFIXES = [
  { name: 'of the Void', bonusCrit: 8, bonusStr: 10, bonusAgi: 8 },
  { name: 'of Annihilation', bonusCrit: 15, bonusStr: 18, bonusAgi: 12 },
  { name: 'of Eternity', bonusCrit: 5, bonusStr: 25, bonusAgi: 5 },
  { name: 'of the Astral Sea', bonusCrit: 10, bonusStr: 8, bonusAgi: 18 },
  { name: 'of Bloodthirst', bonusCrit: 12, bonusStr: 14, bonusAgi: 14 },
  { name: 'of the Harbinger', bonusCrit: 16, bonusStr: 22, bonusAgi: 20 },
  { name: 'of Retribution', bonusCrit: 6, bonusStr: 16, bonusAgi: 6 },
  { name: 'of Swiftness', bonusCrit: 10, bonusStr: 4, bonusAgi: 24 },
  { name: 'of Absolute Ruin', bonusCrit: 20, bonusStr: 30, bonusAgi: 25 },
  { name: 'of the Cosmos', bonusCrit: 14, bonusStr: 20, bonusAgi: 20 },
];

export const RARITY_CONFIG: Record<ItemRarity, { multiplier: number; dropRate: number; colorHex: string }> = {
  COMMON: { multiplier: 1.0, dropRate: 0.50, colorHex: '#9E9E9E' },
  UNCOMMON: { multiplier: 1.25, dropRate: 0.28, colorHex: '#4CAF50' },
  RARE: { multiplier: 1.6, dropRate: 0.12, colorHex: '#2196F3' },
  EPIC: { multiplier: 2.1, dropRate: 0.06, colorHex: '#9C27B0' },
  LEGENDARY: { multiplier: 3.0, dropRate: 0.028, colorHex: '#FF9800' },
  MYTHIC: { multiplier: 4.5, dropRate: 0.009, colorHex: '#E91E63' },
  DIVINE: { multiplier: 7.0, dropRate: 0.0025, colorHex: '#00E5FF' },
  ANCIENT: { multiplier: 11.0, dropRate: 0.0005, colorHex: '#FFD700' },
  OWNER: { multiplier: 25.0, dropRate: 0.0, colorHex: '#FF1744' },
};

/**
 * Procedural Item Generator
 * Mathematical combination space:
 * 14 base items * 14 prefixes * 10 suffixes * 9 rarities * 100 levels * 6 elements
 * = Over 10,584,000 unique item permutations, plus continuous stat variance!
 */
export function generateProceduralItem(opts: {
  level: number;
  fixedRarity?: ItemRarity;
  seed?: number;
}): GameItem {
  const seed = opts.seed ?? Math.floor(Math.random() * 100000000);
  const rng = pseudoRandom(seed);

  // 1. Pick Base
  const base = BASE_ITEMS[Math.floor(rng() * BASE_ITEMS.length)];
  // 2. Pick Prefix & Suffix
  const prefix = PREFIXES[Math.floor(rng() * PREFIXES.length)];
  const suffix = SUFFIXES[Math.floor(rng() * SUFFIXES.length)];

  // 3. Roll Rarity if not forced
  let rarity: ItemRarity = opts.fixedRarity || rollRarity(rng());

  const rarityMeta = RARITY_CONFIG[rarity];
  const levelScale = 1 + (opts.level - 1) * 0.22;

  // Stat calculation
  const damage = base.type === 'WEAPON'
    ? Math.round(base.baseDmg * levelScale * rarityMeta.multiplier * prefix.statMultiplier)
    : 0;

  const defense = base.type === 'ARMOR'
    ? Math.round(base.baseDef * levelScale * rarityMeta.multiplier * prefix.statMultiplier)
    : 0;

  const critBonus = Math.min(50, Math.round((suffix.bonusCrit * (rarityMeta.multiplier / 2)) * 10) / 10);
  const strBonus = Math.round(suffix.bonusStr * levelScale * (rarityMeta.multiplier / 2.5));
  const agiBonus = Math.round(suffix.bonusAgi * levelScale * (rarityMeta.multiplier / 2.5));

  const fullName = `${prefix.name} ${base.name} ${suffix.name}`;
  const itemCode = `proc_${base.baseId}_${rarity.toLowerCase()}_lvl${opts.level}_${seed}`;

  return {
    id: uuidv4(),
    itemCode,
    name: fullName,
    itemType: base.type,
    equipSlot: base.slot,
    rarity,
    requiredLevel: opts.level,
    damage,
    defense,
    critBonus,
    strBonus,
    agiBonus,
    element: prefix.element,
    specialEffect: prefix.effect,
    isStackable: false,
    maxStack: 1,
    baseValue: Math.round(50 * levelScale * rarityMeta.multiplier),
    seedHash: seed.toString(16),
  };
}

function rollRarity(roll: number): ItemRarity {
  if (roll < 0.0005) return 'ANCIENT';
  if (roll < 0.003) return 'DIVINE';
  if (roll < 0.012) return 'MYTHIC';
  if (roll < 0.04) return 'LEGENDARY';
  if (roll < 0.10) return 'EPIC';
  if (roll < 0.22) return 'RARE';
  if (roll < 0.50) return 'UNCOMMON';
  return 'COMMON';
}

function pseudoRandom(seed: number) {
  let s = seed;
  return function() {
    s = (s * 9301 + 49297) % 233280;
    return s / 233280;
  };
}

export async function persistItemToDatabase(item: GameItem): Promise<void> {
  const sql = `
    INSERT INTO items
    (id, item_code, name, item_type, equip_slot, rarity, required_level, damage, defense, crit_bonus, str_bonus, agi_bonus, element, special_effect, is_stackable, max_stack, base_value, seed_hash)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18)
    ON CONFLICT (id) DO NOTHING
  `;
  await query(sql, [
    item.id,
    item.itemCode,
    item.name,
    item.itemType,
    item.equipSlot,
    item.rarity,
    item.requiredLevel,
    item.damage,
    item.defense,
    item.critBonus,
    item.strBonus,
    item.agiBonus,
    item.element,
    item.specialEffect,
    item.isStackable,
    item.maxStack,
    item.baseValue,
    item.seedHash,
  ]);
}
