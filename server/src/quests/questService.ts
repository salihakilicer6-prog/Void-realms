import { QuestObjective, CharacterState } from '../types';
import { PlayerService } from '../players/playerService';
import { query } from '../database/db';

export class QuestService {
  /**
   * Process a player event (e.g. killing a monster or collecting item)
   */
  public static handleKillObjective(
    character: CharacterState,
    objectives: QuestObjective[],
    monsterDefId: string
  ): { updated: boolean; completedQuests: string[] } {
    let updated = false;
    const completedQuests: string[] = [];

    for (const obj of objectives) {
      if (obj.isCompleted) continue;
      if (obj.objectiveType === 'KILL_MONSTER') {
        if (obj.targetId === monsterDefId || obj.targetId === 'any_void') {
          obj.currentCount++;
          updated = true;
          if (obj.currentCount >= obj.requiredCount) {
            obj.isCompleted = true;
            completedQuests.push(obj.questId);
          }
        }
      }
    }

    return { updated, completedQuests };
  }

  /**
   * Claim rewards for a completed quest
   */
  public static claimQuestReward(
    character: CharacterState,
    objective: QuestObjective
  ): { success: boolean; message: string; rewardXp: number; rewardGold: number } {
    if (!objective.isCompleted) {
      return { success: false, message: 'Quest objectives not finished.', rewardXp: 0, rewardGold: 0 };
    }
    if (objective.isClaimed) {
      return { success: false, message: 'Reward already claimed.', rewardXp: 0, rewardGold: 0 };
    }

    objective.isClaimed = true;
    character.gold += objective.rewardGold;
    PlayerService.addExperience(character, objective.rewardXp);

    return {
      success: true,
      message: `Claimed rewards for ${objective.title}!`,
      rewardXp: objective.rewardXp,
      rewardGold: objective.rewardGold,
    };
  }
}
