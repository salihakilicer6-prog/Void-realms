package com.example

import com.example.data.GameRepository
import com.example.model.EnemyEntity
import com.example.model.PlayerStats
import com.example.model.RemotePlayer
import com.example.network.GameApiClient
import org.junit.Assert.*
import org.junit.Test

class NetworkClientUnitTest {

    @Test
    fun testApiClientUrlTransformation() {
        val client = GameApiClient("http://10.0.2.2:3000")
        assertEquals("http://10.0.2.2:3000", client.getBaseUrl())
        assertEquals("ws://10.0.2.2:3000/game", client.getWebSocketUrl())

        client.setBaseUrl("https://game.voidrealms.io/")
        assertEquals("https://game.voidrealms.io", client.getBaseUrl())
        assertEquals("wss://game.voidrealms.io/game", client.getWebSocketUrl())
    }

    @Test
    fun testServerWorldSnapshotOverridesClientState() {
        val repo = GameRepository()

        // Fabricate server-authoritative snapshot
        val serverPlayer = PlayerStats(
            id = "server_char_uuid_999",
            name = "AstralHero",
            level = 25,
            xp = 14500L,
            gold = 95000L,
            voidShards = 120L,
            hp = 775,
            maxHp = 775,
            strength = 60,
            defense = 58,
            agility = 60
        )

        val remotePlayers = listOf(
            RemotePlayer(
                id = "remote_1",
                name = "VanguardX",
                level = 30,
                posX = 50f,
                posY = -20f,
                hp = 900,
                maxHp = 900
            )
        )

        val serverEnemies = listOf(
            EnemyEntity(
                id = "enemy_srv_1",
                definitionId = "rift_stalker",
                name = "Rift Stalker",
                level = 24,
                hp = 480,
                maxHp = 480,
                damage = 48,
                defense = 24,
                speed = 35f,
                zoneId = "astral_sanctuary",
                posX = 120f,
                posY = 100f,
                aggroRange = 150f,
                attackRange = 45f
            )
        )

        // Dispatch snapshot to repo listener
        repo.onWorldSnapshot(serverPlayer, remotePlayers, serverEnemies)

        // Assert player state was synchronized from server
        assertEquals("server_char_uuid_999", repo.player.value.id)
        assertEquals("AstralHero", repo.player.value.name)
        assertEquals(25, repo.player.value.level)
        assertEquals(95000L, repo.player.value.gold)
        assertEquals(775, repo.player.value.hp)

        // Assert remote players list updated
        assertEquals(1, repo.remotePlayers.value.size)
        assertEquals("VanguardX", repo.remotePlayers.value[0].name)

        // Assert enemies list updated
        assertEquals(1, repo.enemies.value.size)
        assertEquals("enemy_srv_1", repo.enemies.value[0].id)
    }

    @Test
    fun testServerCombatEventRewardsApplication() {
        val repo = GameRepository()
        val initialGold = repo.player.value.gold
        val initialXp = repo.player.value.xp

        // Server reports combat event where enemy died and awarded 250 XP and 100 Gold
        repo.onCombatEvent(
            attackerId = repo.player.value.id,
            targetId = "dummy_enemy_id",
            damage = 85,
            isCrit = true,
            targetRemainingHp = 0,
            targetDied = true,
            xpAwarded = 250L,
            goldAwarded = 100L
        )

        assertEquals(initialGold + 100L, repo.player.value.gold)
        assertEquals(2, repo.player.value.level)
        assertEquals(150L, repo.player.value.xp)
    }

    @Test
    fun testRemotePlayerLifecycle() {
        val repo = GameRepository()

        val p1 = RemotePlayer("p1", "ShadowMage", 12, 10f, 15f, 300, 300)
        repo.onRemotePlayerJoined(p1)
        assertEquals(1, repo.remotePlayers.value.size)

        // Move
        repo.onRemotePlayerMoved("p1", 45f, 60f)
        val moved = repo.remotePlayers.value.find { it.id == "p1" }
        assertNotNull(moved)
        assertEquals(45f, moved!!.posX, 0.01f)
        assertEquals(60f, moved.posY, 0.01f)

        // Leave
        repo.onRemotePlayerLeft("p1")
        assertEquals(0, repo.remotePlayers.value.size)
    }

    @Test
    fun testOfflineAttackGivesNoRewardsAndFailsSafely() {
        val repo = GameRepository()
        val initialXp = repo.player.value.xp
        val initialGold = repo.player.value.gold

        // Client is not authenticated
        assertFalse(repo.networkState.value.isAuthenticated)

        // Attempt attack while offline
        repo.attackTargetEnemy("void_crawler_test", "MELEE")

        // Assert player XP and gold were NOT changed
        assertEquals(initialXp, repo.player.value.xp)
        assertEquals(initialGold, repo.player.value.gold)
        assertTrue(repo.serverMessage.value?.contains("Attacks disabled") == true)
    }

    @Test
    fun testClientCannotModifyXpOrGoldLocally() {
        val repo = GameRepository()
        val initialXp = repo.player.value.xp
        val initialGold = repo.player.value.gold

        // Attempt local admin grant operations
        repo.adminGrantXp(50000L)
        repo.adminGrantGold(1000000L)

        // Authoritative values must remain untouched
        assertEquals(initialXp, repo.player.value.xp)
        assertEquals(initialGold, repo.player.value.gold)
        assertTrue(repo.serverMessage.value?.contains("requires server authority") == true)
    }

    @Test
    fun testServerInventorySnapshotReplacesClientInventory() {
        val repo = GameRepository()
        assertTrue(repo.player.value.inventory.isEmpty())

        val serverBlade = com.example.model.GameItem(
            id = "db_item_1",
            itemCode = "novice_blade",
            name = "Novice Blade",
            itemType = com.example.model.ItemType.WEAPON,
            equipSlot = com.example.model.EquipSlot.MAIN_WEAPON,
            requiredLevel = 1,
            damage = 12
        )

        val serverInv = listOf(
            com.example.model.InventorySlot(slotIndex = 0, item = serverBlade, quantity = 1)
        )
        val serverEquip = mapOf(
            com.example.model.EquipSlot.MAIN_WEAPON to serverBlade
        )

        // Server sends INVENTORY_SNAPSHOT
        repo.onInventorySnapshot(serverInv, serverEquip)

        // Assert client inventory and equipment were populated by server
        assertEquals(1, repo.player.value.inventory.size)
        assertEquals("Novice Blade", repo.player.value.inventory[0].item.name)
        assertNotNull(repo.player.value.equipment[com.example.model.EquipSlot.MAIN_WEAPON])
        assertEquals("Novice Blade", repo.player.value.equipment[com.example.model.EquipSlot.MAIN_WEAPON]?.name)
    }

    @Test
    fun testEquipUnequipRequiresServerConnection() {
        val repo = GameRepository()
        assertFalse(repo.networkState.value.isAuthenticated)

        // Attempt equip/unequip without server connection
        val equipResult = repo.equipItem(0)
        assertFalse(equipResult)
        assertTrue(repo.serverMessage.value?.contains("disconnected from authoritative server") == true)

        val unequipResult = repo.unequipItem(com.example.model.EquipSlot.MAIN_WEAPON)
        assertFalse(unequipResult)
    }

    @Test
    fun testShopSaleRejectedWhenOffline() {
        val repo = GameRepository()
        assertFalse(repo.networkState.value.isAuthenticated)

        // Attempt sell while offline
        repo.sellItem(0)
        assertTrue(repo.serverMessage.value?.contains("require server connection") == true)
    }

    @Test
    fun testPotionConsumptionRejectedWhenOffline() {
        val repo = GameRepository()
        assertFalse(repo.networkState.value.isAuthenticated)

        // Attempt potion while offline
        repo.usePotion()
        assertTrue(repo.serverMessage.value?.contains("Not connected to authoritative server") == true)
    }
}
