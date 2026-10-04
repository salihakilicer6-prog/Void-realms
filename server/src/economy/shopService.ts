import { withTransaction } from '../database/db';
import { v4 as uuidv4 } from 'uuid';

export class ShopService {
  /**
   * Authoritative player item sell:
   * 1. Query DB inventory to ensure player actually owns the item.
   * 2. Ensure item is not locked.
   * 3. Calculate sell price based strictly on server DB `items.base_value` (never trust client).
   * 4. Remove/decrement inventory item.
   * 5. Credit gold within transaction.
   */
  public static async sellItem(characterId: string, itemId: string, quantity = 1): Promise<{ success: boolean; goldEarned: number; newBalance: number }> {
    if (quantity <= 0) {
      throw new Error('Quantity must be greater than zero.');
    }

    return await withTransaction(async (client) => {
      // 1. Lock character
      const charRes = await client.query('SELECT id, gold FROM characters WHERE id = $1 FOR UPDATE', [characterId]);
      if (charRes.rows.length === 0) {
        throw new Error('Character not found.');
      }

      // 2. Lock inventory row
      const invRes = await client.query(
        'SELECT id, quantity, is_locked FROM inventory WHERE character_id = $1 AND item_id = $2 FOR UPDATE',
        [characterId, itemId]
      );
      if (invRes.rows.length === 0) {
        throw new Error('Item not found in character inventory.');
      }

      const invRow = invRes.rows[0];
      if (invRow.is_locked) {
        throw new Error('Cannot sell a locked item.');
      }
      if (invRow.quantity < quantity) {
        throw new Error('Insufficient quantity.');
      }

      // 3. Query authoritative item value
      const itemRes = await client.query('SELECT base_value FROM items WHERE id = $1', [itemId]);
      if (itemRes.rows.length === 0) {
        throw new Error('Item definition not found.');
      }

      const baseValue = Number(itemRes.rows[0].base_value);
      // Sell price is 50% of base value, minimum 1 gold
      const unitSellPrice = Math.max(1, Math.floor(baseValue * 0.5));
      const totalEarned = unitSellPrice * quantity;

      // 4. Update inventory
      if (invRow.quantity === quantity) {
        await client.query('DELETE FROM inventory WHERE id = $1', [invRow.id]);
      } else {
        await client.query('UPDATE inventory SET quantity = quantity - $1 WHERE id = $2', [quantity, invRow.id]);
      }

      // 5. Credit character gold
      const updatedChar = await client.query(
        'UPDATE characters SET gold = gold + $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2 RETURNING gold',
        [totalEarned, characterId]
      );

      const newBalance = Number(updatedChar.rows[0].gold);

      // 6. Log transaction
      await client.query(
        `INSERT INTO transactions (id, character_id, transaction_type, currency, amount, balance_after, reference_id)
         VALUES ($1, $2, 'SHOP_SELL', 'GOLD', $3, $4, $5)`,
        [uuidv4(), characterId, totalEarned, newBalance, itemId]
      );

      return { success: true, goldEarned: totalEarned, newBalance };
    });
  }

  /**
   * Authoritative player item buy from official shop:
   * 1. Query DB for official buy price.
   * 2. Lock character and verify sufficient gold.
   * 3. Check inventory slots (< 40).
   * 4. Deduct gold and insert item.
   */
  public static async buyItem(characterId: string, itemCode: string, quantity = 1): Promise<{ success: boolean; goldSpent: number; newBalance: number }> {
    if (quantity <= 0) {
      throw new Error('Quantity must be greater than zero.');
    }

    return await withTransaction(async (client) => {
      // 1. Lock character
      const charRes = await client.query('SELECT id, gold FROM characters WHERE id = $1 FOR UPDATE', [characterId]);
      if (charRes.rows.length === 0) {
        throw new Error('Character not found.');
      }

      // 2. Query item definition
      const itemRes = await client.query('SELECT id, base_value, is_stackable, max_stack FROM items WHERE item_code = $1', [itemCode]);
      if (itemRes.rows.length === 0) {
        throw new Error('Item not available in shop.');
      }
      const itemDef = itemRes.rows[0];
      const unitBuyPrice = Number(itemDef.base_value);
      const totalCost = unitBuyPrice * quantity;

      const currentGold = Number(charRes.rows[0].gold);
      if (currentGold < totalCost) {
        throw new Error(`Insufficient gold. Required: ${totalCost}, Current: ${currentGold}`);
      }

      // 3. Check inventory capacity (max 40 slots)
      const slotCountRes = await client.query('SELECT COUNT(*) as count FROM inventory WHERE character_id = $1', [characterId]);
      const currentSlots = parseInt(slotCountRes.rows[0].count, 10);
      if (currentSlots >= 40) {
        throw new Error('Inventory is full (40/40 slots).');
      }

      // 4. Find next available slot index (0..39)
      const occupiedSlotsRes = await client.query('SELECT slot_index FROM inventory WHERE character_id = $1', [characterId]);
      const occupied = new Set(occupiedSlotsRes.rows.map((r: any) => r.slot_index));
      let nextSlot = 0;
      while (occupied.has(nextSlot)) nextSlot++;

      // 5. Deduct gold
      const updatedChar = await client.query(
        'UPDATE characters SET gold = gold - $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2 RETURNING gold',
        [totalCost, characterId]
      );
      const newBalance = Number(updatedChar.rows[0].gold);

      // 6. Insert item into inventory
      await client.query(
        `INSERT INTO inventory (id, character_id, item_id, slot_index, quantity)
         VALUES ($1, $2, $3, $4, $5)`,
        [uuidv4(), characterId, itemDef.id, nextSlot, quantity]
      );

      // 7. Record transaction
      await client.query(
        `INSERT INTO transactions (id, character_id, transaction_type, currency, amount, balance_after, reference_id)
         VALUES ($1, $2, 'SHOP_BUY', 'GOLD', $3, $4, $5)`,
        [uuidv4(), characterId, totalCost, newBalance, itemDef.id]
      );

      return { success: true, goldSpent: totalCost, newBalance };
    });
  }
}
