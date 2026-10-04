package com.example

import com.example.data.GameRepository
import com.example.engine.CombatEngine
import com.example.engine.ProceduralItemEngine
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testProceduralItemGeneration() {
        val item1 = ProceduralItemEngine.generateItem(level = 10, seed = 123456L)
        val item2 = ProceduralItemEngine.generateItem(level = 10, seed = 123456L)
        val item3 = ProceduralItemEngine.generateItem(level = 50, seed = 987654L)

        // Deterministic generation
        assertEquals(item1.name, item2.name)
        assertEquals(item1.damage, item2.damage)
        assertEquals(item1.defense, item2.defense)

        // Level scaling
        assertTrue(item3.requiredLevel == 50)
        assertTrue(item1.name.isNotBlank())
        assertTrue(item1.itemCode.startsWith("proc_"))
    }

    @Test
    fun testCombatDamageCalculation() {
        val player = PlayerStats(
            strength = 20,
            agility = 15,
            critChance = 0f, // 0 to avoid random crit in test
            critDamage = 150f
        )
        val enemy = EnemyEntity(
            id = "test_enemy",
            definitionId = "void_crawler",
            name = "Test Crawler",
            level = 1,
            hp = 100,
            maxHp = 100,
            damage = 10,
            defense = 10,
            speed = 30f,
            zoneId = "astral_sanctuary",
            posX = 0f,
            posY = 0f,
            aggroRange = 100f,
            attackRange = 40f
        )

        val outcome = CombatEngine.executePlayerAttack(player, enemy, "MELEE")
        assertTrue("Attack should hit within range", outcome.hit)
        assertTrue("Damage should be greater than 0", outcome.damage > 0)
        assertEquals("Enemy HP should decrease by damage dealt", 100 - outcome.damage, enemy.hp)
    }

    @Test
    fun testPlayerLevelingAndStats() {
        val repo = GameRepository()
        val initialLevel = repo.player.value.level
        val initialHp = repo.player.value.maxHp

        // Grant enough XP to level up
        repo.addExperience(1000)

        assertTrue(repo.player.value.level > initialLevel)
        assertTrue(repo.player.value.maxHp > initialHp)
    }

    @Test
    fun testInventoryEquipUnequip() {
        val repo = GameRepository()
        val p = repo.player.value
        val initialDefense = p.defense

        val armor = GameItem(
            id = "test_armor_1",
            itemCode = "armor_chest_plate",
            name = "Void Plate",
            itemType = ItemType.ARMOR,
            equipSlot = EquipSlot.CHEST,
            rarity = ItemRarity.RARE,
            requiredLevel = 1,
            defense = 35
        )

        // Authoritative inventory snapshot from server equips armor
        repo.onInventorySnapshot(emptyList(), mapOf(EquipSlot.CHEST to armor))

        assertEquals("Equipped slot should hold the armor", armor.id, repo.player.value.equipment[EquipSlot.CHEST]?.id)
        assertTrue("Player defense should increase", repo.player.value.defense >= initialDefense + 35)

        // Authoritative unequip snapshot from server
        repo.onInventorySnapshot(listOf(InventorySlot(0, armor, 1)), emptyMap())
        assertNull("Chest slot should now be null", repo.player.value.equipment[EquipSlot.CHEST])
    }

    @Test
    fun testRoleHierarchyAndSecurity() {
        assertTrue(UserRole.OWNER.level > UserRole.ADMIN.level)
        assertTrue(UserRole.ADMIN.level > UserRole.MODERATOR.level)
        assertTrue(UserRole.MODERATOR.level > UserRole.PLAYER.level)
    }
}
