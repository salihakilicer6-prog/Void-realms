import { EnemyInstance } from '../types';
import { v4 as uuidv4 } from 'uuid';

export interface EnemyBlueprint {
  definitionId: string;
  name: string;
  level: number;
  hp: number;
  damage: number;
  defense: number;
  speed: number;
  aggroRange: number;
  attackRange: number;
}

export const ENEMY_DEFINITIONS: Record<string, EnemyBlueprint> = {
  void_crawler: {
    definitionId: 'void_crawler',
    name: 'Void Crawler',
    level: 1,
    hp: 60,
    damage: 8,
    defense: 4,
    speed: 35,
    aggroRange: 120,
    attackRange: 40,
  },
  rift_stalker: {
    definitionId: 'rift_stalker',
    name: 'Rift Stalker',
    level: 3,
    hp: 140,
    damage: 18,
    defense: 10,
    speed: 55,
    aggroRange: 160,
    attackRange: 45,
  },
  astral_golem: {
    definitionId: 'astral_golem',
    name: 'Astral Golem',
    level: 6,
    hp: 380,
    damage: 32,
    defense: 25,
    speed: 25,
    aggroRange: 100,
    attackRange: 50,
  },
  abyssal_lord: {
    definitionId: 'abyssal_lord',
    name: 'Abyssal Lord',
    level: 10,
    hp: 1200,
    damage: 65,
    defense: 40,
    speed: 40,
    aggroRange: 200,
    attackRange: 60,
  },
};

export class EnemyManager {
  private enemies: Map<string, EnemyInstance> = new Map();

  public spawnEnemy(definitionId: string, zoneId: string, posX: number, posY: number): EnemyInstance {
    const proto = ENEMY_DEFINITIONS[definitionId] || ENEMY_DEFINITIONS.void_crawler;
    const enemy: EnemyInstance = {
      id: uuidv4(),
      definitionId: proto.definitionId,
      name: proto.name,
      level: proto.level,
      hp: proto.hp,
      maxHp: proto.hp,
      damage: proto.damage,
      defense: proto.defense,
      speed: proto.speed,
      attackRange: proto.attackRange,
      zoneId,
      posX,
      posY,
      lastAttackTime: 0,
    };
    this.enemies.set(enemy.id, enemy);
    return enemy;
  }

  public getEnemy(id: string): EnemyInstance | undefined {
    return this.enemies.get(id);
  }

  public getEnemiesInZone(zoneId: string): EnemyInstance[] {
    return Array.from(this.enemies.values()).filter(e => e.zoneId === zoneId && e.hp > 0);
  }

  public removeEnemy(id: string): void {
    this.enemies.delete(id);
  }

  public updateEnemies(deltaSec: number, playerPositions: { id: string; x: number; y: number }[]): void {
    for (const enemy of this.enemies.values()) {
      if (enemy.hp <= 0) continue;

      const proto = ENEMY_DEFINITIONS[enemy.definitionId];
      if (!proto) continue;

      // Aggro check
      let closestPlayer: { id: string; dist: number; dx: number; dy: number } | null = null;
      for (const p of playerPositions) {
        const dx = p.x - enemy.posX;
        const dy = p.y - enemy.posY;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist <= proto.aggroRange) {
          if (!closestPlayer || dist < closestPlayer.dist) {
            closestPlayer = { id: p.id, dist, dx, dy };
          }
        }
      }

      if (closestPlayer) {
        enemy.aggroTargetId = closestPlayer.id;
        if (closestPlayer.dist > proto.attackRange) {
          // Move towards target
          const nx = closestPlayer.dx / closestPlayer.dist;
          const ny = closestPlayer.dy / closestPlayer.dist;
          enemy.posX += nx * enemy.speed * deltaSec;
          enemy.posY += ny * enemy.speed * deltaSec;
        }
      } else {
        enemy.aggroTargetId = undefined;
      }
    }
  }
}
