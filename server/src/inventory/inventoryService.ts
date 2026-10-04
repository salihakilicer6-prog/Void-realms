import { query, withTransaction } from '../database/db';
import { v4 as uuidv4 } from 'uuid';

export interface DBItem {
  id: string;
  itemCode: string;
  name: string;
  itemType: string;
  equipSlot: string | null;
  rarity: string;
  requiredLevel: number;
  damage: number;
  defense: number;
  critBonus: number;
  strBonus: number;
  agiBonus: number;
  element: string;
  specialEffect: string | null;
  isStackable: boolean;
  maxStack: number;
  baseValue: number;
}

export interface DBInventorySlot {
  id: string;
  slotIndex: number;
  quantity: number;
  isLocked: boolean;
  item: DBItem;
}

export class InventoryService {
  /**
   * Adds an item to a character's inventory in memory.
   */
  public static addItem(target: any, item: any): boolean {
    if (!target.inventory) target.inventory = [];
    if (target.inventory.length >= 40) return false;
    const occupied = new Set(target.inventory.map((s: any) => s.slotIndex));
    let nextSlot = 0;
    while (occupied.has(nextSlot)) nextSlot++;
    target.inventory.push({
      id: uuidv4(),
      slotIndex: nextSlot,
      item,
      quantity: 1,
      isLocked: false,
    });
    return true;
  }

  /**
   * Retrieves all inventory items and equipped gear for a character from PostgreSQL.
   */
  public static async getCharacterInventoryAndEquipment(characterId: string): Promise<{
    inventory: DBInventorySlot[];
    equipment: Record<string, DBItem>;
  }> {
    const invRows = await query(`
      SELECT inv.id, inv.slot_index, inv.quantity, inv.is_locked,
             i.id as item_id, i.item_code, i.name as item_name, i.item_type, i.equip_slot,
             i.rarity, i.required_level, i.damage, i.defense, i.crit_bonus, i.str_bonus,
             i.agi_bonus, i.element, i.special_effect, i.is_stackable, i.max_stack, i.base_value
      FROM inventory inv
      JOIN items i ON inv.item_id = i.id
      WHERE inv.character_id = $1
      ORDER BY inv.slot_index ASC
    `, [characterId]);

    const inventory: DBInventorySlot[] = invRows.map(r => ({
      id: r.id,
      slotIndex: r.slot_index,
      quantity: r.quantity,
      isLocked: r.is_locked,
      item: {
        id: r.item_id,
        itemCode: r.item_code,
        name: r.item_name,
        itemType: r.item_type,
        equipSlot: r.equip_slot,
        rarity: r.rarity,
        requiredLevel: r.required_level,
        damage: r.damage,
        defense: r.defense,
        critBonus: Number(r.crit_bonus),
        strBonus: r.str_bonus,
        agiBonus: r.agi_bonus,
        element: r.element,
        specialEffect: r.special_effect,
        isStackable: r.is_stackable,
        maxStack: r.max_stack,
        baseValue: Number(r.base_value),
      },
    }));

    const equipRows = await query(`
      SELECT eq.slot,
             i.id as item_id, i.item_code, i.name as item_name, i.item_type, i.equip_slot,
             i.rarity, i.required_level, i.damage, i.defense, i.crit_bonus, i.str_bonus,
             i.agi_bonus, i.element, i.special_effect, i.is_stackable, i.max_stack, i.base_value
      FROM equipment eq
      JOIN items i ON eq.item_id = i.id
      WHERE eq.character_id = $1
    `, [characterId]);

    const equipment: Record<string, DBItem> = {};
    for (const r of equipRows) {
      equipment[r.slot] = {
        id: r.item_id,
        itemCode: r.item_code,
        name: r.item_name,
        itemType: r.item_type,
        equipSlot: r.equip_slot,
        rarity: r.rarity,
        requiredLevel: r.required_level,
        damage: r.damage,
        defense: r.defense,
        critBonus: Number(r.crit_bonus),
        strBonus: r.str_bonus,
        agiBonus: r.agi_bonus,
        element: r.element,
        specialEffect: r.special_effect,
        isStackable: r.is_stackable,
        maxStack: r.max_stack,
        baseValue: Number(r.base_value),
      };
    }

    return { inventory, equipment };
  }

  /**
   * Equips an item from inventory to the target equipment slot within an ACID PostgreSQL transaction.
   * Validates level requirement, item type, and slot compatibility.
   */
  public static async equipItem(
    characterId: string,
    slotIndex: number,
    targetSlot: string
  ): Promise<{ inventory: DBInventorySlot[]; equipment: Record<string, DBItem> }> {
    return await withTransaction(async (client) => {
      // 1. Lock character to verify existence and level
      const charRes = await client.query('SELECT level FROM characters WHERE id = $1 FOR UPDATE', [characterId]);
      if (charRes.rows.length === 0) {
        throw new Error('Character not found');
      }
      const playerLevel = charRes.rows[0].level;

      // 2. Lock and find inventory item
      const invRes = await client.query(
        'SELECT id, item_id, quantity, is_locked FROM inventory WHERE character_id = $1 AND slot_index = $2 FOR UPDATE',
        [characterId, slotIndex]
      );
      if (invRes.rows.length === 0) {
        throw new Error('No item found in inventory slot');
      }
      const invRow = invRes.rows[0];

      // 3. Query authoritative item definition
      const itemRes = await client.query('SELECT * FROM items WHERE id = $1', [invRow.item_id]);
      if (itemRes.rows.length === 0) {
        throw new Error('Item definition missing');
      }
      const item = itemRes.rows[0];

      // 4. Validate equipment rules
      if (playerLevel < item.required_level) {
        throw new Error(`Requires Level ${item.required_level}`);
      }
      if (item.equip_slot && item.equip_slot !== targetSlot) {
        throw new Error(`Item cannot be equipped to slot ${targetSlot}`);
      }

      // 5. Check if another item is currently equipped in that slot
      const existingEquipRes = await client.query(
        'SELECT item_id FROM equipment WHERE character_id = $1 AND slot = $2 FOR UPDATE',
        [characterId, targetSlot]
      );

      // Remove from inventory
      await client.query('DELETE FROM inventory WHERE id = $1', [invRow.id]);

      // If an item was previously equipped, put it back into the vacated inventory slot
      if (existingEquipRes.rows.length > 0) {
        const previousItemId = existingEquipRes.rows[0].item_id;
        await client.query(
          `INSERT INTO inventory (id, character_id, item_id, slot_index, quantity)
           VALUES ($1, $2, $3, $4, 1)`,
          [uuidv4(), characterId, previousItemId, slotIndex]
        );
      }

      // Update or insert into equipment
      await client.query(
        `INSERT INTO equipment (id, character_id, slot, item_id)
         VALUES ($1, $2, $3, $4)
         ON CONFLICT (character_id, slot) DO UPDATE SET item_id = $4, equipped_at = CURRENT_TIMESTAMP`,
        [uuidv4(), characterId, targetSlot, item.id]
      );

      // Return refreshed snapshot from within transaction
      const updatedInv = await client.query(`
        SELECT inv.id, inv.slot_index, inv.quantity, inv.is_locked,
               i.id as item_id, i.item_code, i.name as item_name, i.item_type, i.equip_slot,
               i.rarity, i.required_level, i.damage, i.defense, i.crit_bonus, i.str_bonus,
               i.agi_bonus, i.element, i.special_effect, i.is_stackable, i.max_stack, i.base_value
        FROM inventory inv
        JOIN items i ON inv.item_id = i.id
        WHERE inv.character_id = $1
        ORDER BY inv.slot_index ASC
      `, [characterId]);

      const updatedEquip = await client.query(`
        SELECT eq.slot,
               i.id as item_id, i.item_code, i.name as item_name, i.item_type, i.equip_slot,
               i.rarity, i.required_level, i.damage, i.defense, i.crit_bonus, i.str_bonus,
               i.agi_bonus, i.element, i.special_effect, i.is_stackable, i.max_stack, i.base_value
        FROM equipment eq
        JOIN items i ON eq.item_id = i.id
        WHERE eq.character_id = $1
      `, [characterId]);

      const finalInv: DBInventorySlot[] = updatedInv.rows.map(r => ({
        id: r.id,
        slotIndex: r.slot_index,
        quantity: r.quantity,
        isLocked: r.is_locked,
        item: {
          id: r.item_id,
          itemCode: r.item_code,
          name: r.item_name,
          itemType: r.item_type,
          equipSlot: r.equip_slot,
          rarity: r.rarity,
          requiredLevel: r.required_level,
          damage: r.damage,
          defense: r.defense,
          critBonus: Number(r.crit_bonus),
          strBonus: r.str_bonus,
          agiBonus: r.agi_bonus,
          element: r.element,
          specialEffect: r.special_effect,
          isStackable: r.is_stackable,
          maxStack: r.max_stack,
          baseValue: Number(r.base_value),
        },
      }));

      const finalEquip: Record<string, DBItem> = {};
      for (const r of updatedEquip.rows) {
        finalEquip[r.slot] = {
          id: r.item_id,
          itemCode: r.item_code,
          name: r.item_name,
          itemType: r.item_type,
          equipSlot: r.equip_slot,
          rarity: r.rarity,
          requiredLevel: r.required_level,
          damage: r.damage,
          defense: r.defense,
          critBonus: Number(r.crit_bonus),
          strBonus: r.str_bonus,
          agiBonus: r.agi_bonus,
          element: r.element,
          specialEffect: r.special_effect,
          isStackable: r.is_stackable,
          maxStack: r.max_stack,
          baseValue: Number(r.base_value),
        };
      }

      return { inventory: finalInv, equipment: finalEquip };
    });
  }

  /**
   * Unequips an item from the specified slot back into the first open inventory slot within a transaction.
   */
  public static async unequipItem(
    characterId: string,
    slot: string
  ): Promise<{ inventory: DBInventorySlot[]; equipment: Record<string, DBItem> }> {
    return await withTransaction(async (client) => {
      // 1. Check equipment row
      const equipRes = await client.query(
        'SELECT item_id FROM equipment WHERE character_id = $1 AND slot = $2 FOR UPDATE',
        [characterId, slot]
      );
      if (equipRes.rows.length === 0) {
        throw new Error('No item equipped in this slot');
      }
      const itemId = equipRes.rows[0].item_id;

      // 2. Check inventory capacity (max 40)
      const invCountRes = await client.query(
        'SELECT COUNT(*) as count FROM inventory WHERE character_id = $1',
        [characterId]
      );
      const currentSlots = parseInt(invCountRes.rows[0].count, 10);
      if (currentSlots >= 40) {
        throw new Error('Inventory is full (40/40 slots)');
      }

      // 3. Find next available slot index 0..39
      const occupiedRes = await client.query(
        'SELECT slot_index FROM inventory WHERE character_id = $1',
        [characterId]
      );
      const occupied = new Set(occupiedRes.rows.map((r: any) => r.slot_index));
      let nextSlot = 0;
      while (occupied.has(nextSlot)) nextSlot++;

      // 4. Remove from equipment and insert into inventory
      await client.query('DELETE FROM equipment WHERE character_id = $1 AND slot = $2', [characterId, slot]);
      await client.query(
        `INSERT INTO inventory (id, character_id, item_id, slot_index, quantity)
         VALUES ($1, $2, $3, $4, 1)`,
        [uuidv4(), characterId, itemId, nextSlot]
      );

      // Return refreshed snapshot
      return await this.getCharacterInventoryAndEquipment(characterId);
    });
  }
}
