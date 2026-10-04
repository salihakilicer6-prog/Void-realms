import { CharacterState, EnemyInstance, GameItem } from '../types';
import { generateProceduralItem, persistItemToDatabase } from '../items/proceduralGenerator';

export interface CombatResult {
  hit: boolean;
  damage: number;
  isCrit: boolean;
  targetRemainingHp: number;
  targetDied: boolean;
  xpAwarded?: number;
  goldAwarded?: number;
  lootDropped?: GameItem[];
}

export class CombatEngine {
  private static playerCooldowns: Map<string, number> = new Map();

  /**
   * Authoritative player attacking an enemy
   */
  public static executePlayerAttack(
    player: CharacterState,
    enemy: EnemyInstance,
    attackType: 'MELEE' | 'RANGED' | 'VOID_BLAST'
  ): CombatResult {
    const now = Date.now();
    const lastAttack = this.playerCooldowns.get(player.id) || 0;
    const cooldownMs = attackType === 'MELEE' ? 600 : 1200;

    if (now - lastAttack < cooldownMs) {
      return {
        hit: false,
        damage: 0,
        isCrit: false,
        targetRemainingHp: enemy.hp,
        targetDied: false,
      };
    }
    this.playerCooldowns.set(player.id, now);

    // Range validation
    const dx = player.posX - enemy.posX;
    const dy = player.posY - enemy.posY;
    const distSq = dx * dx + dy * dy;
    const maxRange = attackType === 'MELEE' ? 45 : 200;
    if (distSq > maxRange * maxRange) {
      return {
        hit: false,
        damage: 0,
        isCrit: false,
        targetRemainingHp: enemy.hp,
        targetDied: false,
      };
    }

    // Damage calculations
    let baseWeaponDmg = 10;
    if (player.equipment.MAIN_WEAPON) {
      baseWeaponDmg += player.equipment.MAIN_WEAPON.damage;
    }

    const statMultiplier = 1 + (player.strength * 0.04) + (player.agility * 0.02);
    let rawDamage = Math.max(1, Math.round(baseWeaponDmg * statMultiplier));
    if (attackType === 'VOID_BLAST') {
      rawDamage = Math.round(rawDamage * 1.75);
    }

    // Defense formula: Damage = Raw * (100 / (100 + Defense))
    const defMultiplier = 100 / (100 + Math.max(0, enemy.defense));
    let finalDamage = Math.max(1, Math.round(rawDamage * defMultiplier));

    // Critical roll
    const critRoll = Math.random() * 100;
    const isCrit = critRoll < player.critChance;
    if (isCrit) {
      finalDamage = Math.round(finalDamage * (player.critDamage / 100));
    }

    enemy.hp = Math.max(0, enemy.hp - finalDamage);
    const targetDied = enemy.hp === 0;

    let xpAwarded: number | undefined;
    let goldAwarded: number | undefined;
    let lootDropped: GameItem[] | undefined;

    if (targetDied) {
      xpAwarded = Math.round(enemy.level * 45 + Math.random() * 20);
      goldAwarded = Math.round(enemy.level * 15 + Math.random() * 25);

      // Roll procedural drop
      if (Math.random() < 0.40) {
        const droppedItem = generateProceduralItem({ level: enemy.level });
        lootDropped = [droppedItem];
        // Persist item so it becomes real in the database
        persistItemToDatabase(droppedItem).catch(console.error);
      }
    }

    return {
      hit: true,
      damage: finalDamage,
      isCrit,
      targetRemainingHp: enemy.hp,
      targetDied,
      xpAwarded,
      goldAwarded,
      lootDropped,
    };
  }

  /**
   * Authoritative enemy attacking a player
   */
  public static executeEnemyAttack(
    enemy: EnemyInstance,
    player: CharacterState
  ): { damage: number; playerRemainingHp: number; playerDied: boolean; hit: boolean } {
    const now = Date.now();
    const enemyAttackCooldownMs = 1100;
    if (now - enemy.lastAttackTime < enemyAttackCooldownMs) {
      return { damage: 0, playerRemainingHp: player.hp, playerDied: false, hit: false };
    }
    enemy.lastAttackTime = now;

    const rawDamage = enemy.damage;
    const defMultiplier = 100 / (100 + Math.max(0, player.defense));
    const damage = Math.max(1, Math.round(rawDamage * defMultiplier));

    player.hp = Math.max(0, player.hp - damage);
    const playerDied = player.hp === 0;
    if (playerDied) {
      player.isDead = true;
    }

    return {
      damage,
      playerRemainingHp: player.hp,
      playerDied,
      hit: true,
    };
  }
}
