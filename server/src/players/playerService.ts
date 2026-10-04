import { CharacterState, EquipSlot, GameItem, InventorySlot } from '../types';
import { query, withTransaction } from '../database/db';

export class PlayerService {
  /**
   * Recalculates stats from base stats + all equipped gear
   */
  public static recalculateStats(character: CharacterState): void {
    let bonusStr = 0;
    let bonusDef = 0;
    let bonusAgi = 0;
    let bonusCrit = 0;
    let bonusHp = 0;
    let bonusMana = 0;

    for (const slot of Object.values(character.equipment)) {
      if (!slot) continue;
      bonusDef += slot.defense || 0;
      bonusStr += slot.strBonus || 0;
      bonusAgi += slot.agiBonus || 0;
      bonusCrit += slot.critBonus || 0;
    }

    const baseLevel = character.level;
    character.maxHp = 150 + (baseLevel * 25) + (character.strength * 5) + bonusHp;
    character.maxMana = 100 + (baseLevel * 15) + bonusMana;
    character.defense = 8 + (baseLevel * 2) + bonusDef;
    character.strength = 10 + (baseLevel * 2) + bonusStr;
    character.agility = 10 + (baseLevel * 2) + bonusAgi;
    character.critChance = Math.min(75, 5.0 + (character.agility * 0.25) + bonusCrit);
    character.critDamage = 150.0 + (character.strength * 0.5);

    // Clamp vitals
    character.hp = Math.min(character.hp, character.maxHp);
    character.mana = Math.min(character.mana, character.maxMana);
  }

  /**
   * Authoritative XP grant and level up logic
   */
  public static addExperience(character: CharacterState, amount: number): { leveledUp: boolean; newLevel: number } {
    character.xp += amount;
    let leveledUp = false;

    // Standard RPG XP curve: ReqXP = Level^2 * 100
    while (character.level < 100) {
      const requiredXp = character.level * character.level * 100;
      if (character.xp >= requiredXp) {
        character.xp -= requiredXp;
        character.level++;
        leveledUp = true;
      } else {
        break;
      }
    }

    if (leveledUp) {
      this.recalculateStats(character);
      character.hp = character.maxHp;
      character.mana = character.maxMana;
    }

    return { leveledUp, newLevel: character.level };
  }

  /**
   * Save character state to PostgreSQL
   */
  public static async saveCharacter(char: CharacterState): Promise<void> {
    const sql = `
      UPDATE characters
      SET level = $1, xp = $2, gold = $3, void_shards = $4,
          hp = $5, max_hp = $6, mana = $7, max_mana = $8,
          strength = $9, defense = $10, agility = $11,
          crit_chance = $12, crit_damage = $13,
          zone_id = $14, pos_x = $15, pos_y = $16, pos_z = $17,
          is_dead = $18, updated_at = CURRENT_TIMESTAMP
      WHERE id = $19
    `;
    await query(sql, [
      char.level,
      char.xp,
      char.gold,
      char.voidShards,
      char.hp,
      char.maxHp,
      char.mana,
      char.maxMana,
      char.strength,
      char.defense,
      char.agility,
      char.critChance,
      char.critDamage,
      char.zoneId,
      char.posX,
      char.posY,
      char.posZ,
      char.isDead,
      char.id,
    ]);
  }
}
