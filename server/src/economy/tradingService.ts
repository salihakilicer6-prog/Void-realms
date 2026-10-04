import { query, withTransaction } from '../database/db';
import { v4 as uuidv4 } from 'uuid';

export interface TradeOffer {
  gold: number;
  itemIds: string[];
  confirmed: boolean;
}

export interface TradeSession {
  id: string;
  senderCharacterId: string;
  receiverCharacterId: string;
  senderOffer: TradeOffer;
  receiverOffer: TradeOffer;
  status: 'PENDING' | 'ACCEPTED' | 'CANCELLED' | 'COMPLETED';
  createdAt: number;
}

// In-memory active trade sessions
const activeTrades = new Map<string, TradeSession>();
const characterActiveTrade = new Map<string, string>(); // characterId -> tradeId

export class TradingService {
  /**
   * Initiates a trade between two players.
   * Prevents concurrent trades per character.
   */
  public static initiateTrade(senderCharId: string, receiverCharId: string): TradeSession {
    if (senderCharId === receiverCharId) {
      throw new Error('Cannot trade with yourself.');
    }

    if (characterActiveTrade.has(senderCharId)) {
      throw new Error('You already have an active trade.');
    }

    if (characterActiveTrade.has(receiverCharId)) {
      throw new Error('Target player is currently busy in another trade.');
    }

    const tradeId = uuidv4();
    const session: TradeSession = {
      id: tradeId,
      senderCharacterId: senderCharId,
      receiverCharacterId: receiverCharId,
      senderOffer: { gold: 0, itemIds: [], confirmed: false },
      receiverOffer: { gold: 0, itemIds: [], confirmed: false },
      status: 'PENDING',
      createdAt: Date.now(),
    };

    activeTrades.set(tradeId, session);
    characterActiveTrade.set(senderCharId, tradeId);
    characterActiveTrade.set(receiverCharId, tradeId);

    return session;
  }

  /**
   * Sets the offer (gold and items) for one side of the trade.
   * Resets confirmation on change.
   */
  public static setOffer(tradeId: string, characterId: string, gold: number, itemIds: string[]): TradeSession {
    const session = activeTrades.get(tradeId);
    if (!session || session.status !== 'PENDING') {
      throw new Error('Trade session not active.');
    }

    if (gold < 0) {
      throw new Error('Gold amount cannot be negative.');
    }

    // Reset both confirmations when offer changes (prevents bait-and-switch)
    session.senderOffer.confirmed = false;
    session.receiverOffer.confirmed = false;

    if (session.senderCharacterId === characterId) {
      session.senderOffer.gold = gold;
      session.senderOffer.itemIds = itemIds;
    } else if (session.receiverCharacterId === characterId) {
      session.receiverOffer.gold = gold;
      session.receiverOffer.itemIds = itemIds;
    } else {
      throw new Error('Character not part of this trade.');
    }

    return session;
  }

  /**
   * Player confirms their trade side.
   * If both confirm, executes atomic database swap with row locks.
   */
  public static async confirmTrade(tradeId: string, characterId: string): Promise<{ completed: boolean; session: TradeSession }> {
    const session = activeTrades.get(tradeId);
    if (!session || session.status !== 'PENDING') {
      throw new Error('Trade session not active.');
    }

    if (session.senderCharacterId === characterId) {
      session.senderOffer.confirmed = true;
    } else if (session.receiverCharacterId === characterId) {
      session.receiverOffer.confirmed = true;
    } else {
      throw new Error('Character not part of this trade.');
    }

    // If both confirmed, execute atomic swap
    if (session.senderOffer.confirmed && session.receiverOffer.confirmed) {
      await this.executeAtomicTrade(session);
      session.status = 'COMPLETED';
      characterActiveTrade.delete(session.senderCharacterId);
      characterActiveTrade.delete(session.receiverCharacterId);
      activeTrades.delete(tradeId);
      return { completed: true, session };
    }

    return { completed: false, session };
  }

  /**
   * Cancels an active trade session safely.
   */
  public static cancelTrade(tradeId: string): void {
    const session = activeTrades.get(tradeId);
    if (session) {
      session.status = 'CANCELLED';
      characterActiveTrade.delete(session.senderCharacterId);
      characterActiveTrade.delete(session.receiverCharacterId);
      activeTrades.delete(tradeId);
    }
  }

  /**
   * Atomic database transfer with pessimistic row locking.
   * Guarantees zero item duplication and zero currency exploits.
   */
  private static async executeAtomicTrade(session: TradeSession): Promise<void> {
    const { senderCharacterId, receiverCharacterId, senderOffer, receiverOffer } = session;

    await withTransaction(async (client) => {
      // 1. Lock character rows in consistent sorted order to prevent deadlocks
      const [firstChar, secondChar] = [senderCharacterId, receiverCharacterId].sort();
      const charRows = await client.query(
        'SELECT id, gold FROM characters WHERE id IN ($1, $2) FOR UPDATE',
        [firstChar, secondChar]
      );

      const senderRow = charRows.rows.find((r: any) => r.id === senderCharacterId);
      const receiverRow = charRows.rows.find((r: any) => r.id === receiverCharacterId);

      if (!senderRow || !receiverRow) {
        throw new Error('One or both trade participants no longer exist.');
      }

      // 2. Validate gold balances
      if (BigInt(senderRow.gold) < BigInt(senderOffer.gold)) {
        throw new Error('Sender has insufficient gold.');
      }
      if (BigInt(receiverRow.gold) < BigInt(receiverOffer.gold)) {
        throw new Error('Receiver has insufficient gold.');
      }

      // 3. Validate sender item ownership & locked status
      for (const itemId of senderOffer.itemIds) {
        const invRow = await client.query(
          'SELECT id, is_locked FROM inventory WHERE character_id = $1 AND item_id = $2 FOR UPDATE',
          [senderCharacterId, itemId]
        );
        if (invRow.rows.length === 0) {
          throw new Error(`Sender does not own item ${itemId}`);
        }
        if (invRow.rows[0].is_locked) {
          throw new Error(`Item ${itemId} is locked.`);
        }
      }

      // 4. Validate receiver item ownership & locked status
      for (const itemId of receiverOffer.itemIds) {
        const invRow = await client.query(
          'SELECT id, is_locked FROM inventory WHERE character_id = $1 AND item_id = $2 FOR UPDATE',
          [receiverCharacterId, itemId]
        );
        if (invRow.rows.length === 0) {
          throw new Error(`Receiver does not own item ${itemId}`);
        }
        if (invRow.rows[0].is_locked) {
          throw new Error(`Item ${itemId} is locked.`);
        }
      }

      // 5. Transfer Gold
      const netSenderGold = BigInt(receiverOffer.gold) - BigInt(senderOffer.gold);
      await client.query(
        'UPDATE characters SET gold = gold + $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
        [netSenderGold.toString(), senderCharacterId]
      );
      await client.query(
        'UPDATE characters SET gold = gold - $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2',
        [netSenderGold.toString(), receiverCharacterId]
      );

      // 6. Transfer Items: sender -> receiver
      for (const itemId of senderOffer.itemIds) {
        await client.query(
          'UPDATE inventory SET character_id = $1 WHERE character_id = $2 AND item_id = $3',
          [receiverCharacterId, senderCharacterId, itemId]
        );
      }

      // 7. Transfer Items: receiver -> sender
      for (const itemId of receiverOffer.itemIds) {
        await client.query(
          'UPDATE inventory SET character_id = $1 WHERE character_id = $2 AND item_id = $3',
          [senderCharacterId, receiverCharacterId, itemId]
        );
      }

      // 8. Record completed trade
      await client.query(
        `INSERT INTO trades (id, sender_id, receiver_id, sender_gold, receiver_gold, status, completed_at)
         VALUES ($1, $2, $3, $4, $5, 'COMPLETED', CURRENT_TIMESTAMP)`,
        [session.id, senderCharacterId, receiverCharacterId, senderOffer.gold, receiverOffer.gold]
      );
    });
  }
}
