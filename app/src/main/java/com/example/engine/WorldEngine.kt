package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.model.EnemyEntity
import com.example.model.ZoneInfo
import kotlin.math.sqrt
import kotlin.random.Random

import com.example.model.*
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs

object WorldEngine {

    val ZONES = listOf(
        ZoneInfo(
            id = "astral_sanctuary",
            name = "Astral Sanctuary",
            description = "The ancient floating citadel shielded from void decay. Safe haven for ascendants.",
            minLevel = 1,
            isSafeZone = false,
            ambientColor = Color(0xFF0F122B)
        ),
        ZoneInfo(
            id = "void_wastes",
            name = "Void Wastes",
            description = "Barren expanse of fractured reality roaming with phase predators.",
            minLevel = 3,
            isSafeZone = false,
            ambientColor = Color(0xFF140D24)
        ),
        ZoneInfo(
            id = "abyssal_depths",
            name = "Abyssal Depths",
            description = "Subterranean catacombs of the Void Lord. Extreme danger.",
            minLevel = 5,
            isSafeZone = false,
            ambientColor = Color(0xFF1F0918)
        ),
        ZoneInfo(
            id = "abyssal_catacombs",
            name = "Abyssal Catacombs",
            description = "Procedural 3D dungeon with BSP rooms, vaults, and boss chambers.",
            minLevel = 5,
            isSafeZone = false,
            ambientColor = Color(0xFF1E0F2B)
        )
    )

    fun createInitialNpcs(zoneId: String): List<NpcEntity> {
        return when (zoneId) {
            "astral_sanctuary" -> listOf(
                NpcEntity(
                    id = "npc_1",
                    name = "Elder Vaelen",
                    title = "High Archon",
                    role = "QUEST_GIVER",
                    zoneId = zoneId,
                    posX = -40f,
                    posY = -30f,
                    posZ = 0f,
                    primaryColor = Color(0xFF3B82F6),
                    outfitColor = Color(0xFF1E3A8A),
                    dialogue = "Welcome, Ascendant. The Void Gates are stirring. Speak to Blacksmith Thorne to arm yourself before stepping beyond the citadel."
                ),
                NpcEntity(
                    id = "npc_2",
                    name = "Blacksmith Thorne",
                    title = "Master Forge",
                    role = "FORGE",
                    zoneId = zoneId,
                    posX = 50f,
                    posY = -20f,
                    posZ = 0f,
                    primaryColor = Color(0xFFEF4444),
                    outfitColor = Color(0xFF7F1D1D),
                    dialogue = "Need steel refined with Void Shards? Bring me materials from the Wastes and I'll forge weapon upgrades for you."
                ),
                NpcEntity(
                    id = "npc_3",
                    name = "Merchant Kael",
                    title = "Astral Trader",
                    role = "MERCHANT",
                    zoneId = zoneId,
                    posX = 10f,
                    posY = 60f,
                    posZ = 0f,
                    primaryColor = Color(0xFF10B981),
                    outfitColor = Color(0xFF065F46),
                    dialogue = "Rare potions, scrolls, and divine artifacts for sale! Gold rules the Sanctuary, Ascendant."
                )
            )
            "void_wastes" -> listOf(
                NpcEntity(
                    id = "npc_4",
                    name = "Scholar Kaelen",
                    title = "Rift Explorer",
                    role = "EXPLORER",
                    zoneId = zoneId,
                    posX = -60f,
                    posY = -40f,
                    posZ = 0f,
                    primaryColor = Color(0xFF8B5CF6),
                    outfitColor = Color(0xFF4C1D95),
                    dialogue = "Beware the Phase Stalkers. The ruins ahead hold ancient Void Crystals that grant immense power."
                )
            )
            "abyssal_depths" -> listOf(
                NpcEntity(
                    id = "npc_5",
                    name = "Abyssal Sentinel",
                    title = "Guardian of the Deep",
                    role = "GUARDIAN",
                    zoneId = zoneId,
                    posX = 0f,
                    posY = -100f,
                    posZ = 0f,
                    primaryColor = Color(0xFFF59E0B),
                    outfitColor = Color(0xFF78350F),
                    dialogue = "Beyond this portal sleeps Abyssal Lord Val'khor. Prepare your spells and strike together!"
                )
            )
            else -> emptyList()
        }
    }

    fun createInitialWorldItems(zoneId: String): List<WorldItemDrop> {
        return when (zoneId) {
            "astral_sanctuary" -> listOf(
                WorldItemDrop(
                    id = "item_drop_1",
                    item = GameItem(
                        id = "item_blade_01",
                        itemCode = "astral_blade",
                        name = "Astral Blade",
                        itemType = ItemType.WEAPON,
                        equipSlot = EquipSlot.MAIN_WEAPON,
                        rarity = ItemRarity.RARE,
                        requiredLevel = 1,
                        damage = 22,
                        strBonus = 5,
                        baseValue = 120
                    ),
                    zoneId = zoneId,
                    posX = -120f,
                    posY = 40f
                ),
                WorldItemDrop(
                    id = "item_drop_2",
                    item = GameItem(
                        id = "item_pot_01",
                        itemCode = "health_elixir",
                        name = "Greater Health Elixir",
                        itemType = ItemType.CONSUMABLE,
                        rarity = ItemRarity.UNCOMMON,
                        baseValue = 45
                    ),
                    zoneId = zoneId,
                    posX = 90f,
                    posY = -110f
                ),
                WorldItemDrop(
                    id = "item_drop_3",
                    item = GameItem(
                        id = "item_crystal_01",
                        itemCode = "void_crystal",
                        name = "Radiant Void Crystal",
                        itemType = ItemType.ARTIFACT,
                        equipSlot = EquipSlot.SPECIAL,
                        rarity = ItemRarity.EPIC,
                        critBonus = 12f,
                        baseValue = 350
                    ),
                    zoneId = zoneId,
                    posX = 140f,
                    posY = 130f
                )
            )
            "void_wastes" -> listOf(
                WorldItemDrop(
                    id = "item_drop_4",
                    item = GameItem(
                        id = "item_aegis_01",
                        itemCode = "void_aegis",
                        name = "Aegis of the Rift",
                        itemType = ItemType.ARMOR,
                        equipSlot = EquipSlot.OFF_HAND,
                        rarity = ItemRarity.LEGENDARY,
                        defense = 35,
                        baseValue = 850
                    ),
                    zoneId = zoneId,
                    posX = -150f,
                    posY = 120f
                )
            )
            else -> emptyList()
        }
    }

    /**
     * Calculates 3D terrain elevation for smooth mountains, paths, hills, and ruins.
     */
    fun getTerrainHeight(x: Float, y: Float): Float {
        val distCenter = sqrt(x * x + y * y)
        // Mountain border beyond 220f
        if (distCenter > 220f) {
            val mountainHill = (distCenter - 220f) * 0.45f
            return mountainHill + sin(x * 0.05f) * 6f + cos(y * 0.05f) * 6f
        }
        // Smooth path valley in the center
        val mainRoad = (x * 0.1f).coerceIn(-15f, 15f)
        val hillPattern = (sin(x * 0.03f) * cos(y * 0.03f)) * 8f
        return (hillPattern - abs(mainRoad) * 0.2f).coerceAtLeast(-4f)
    }

    /**
     * Validates movement collisions. Fixes invisible walls that incorrectly trap player.
     * Restricts movement cleanly at map boundary (-270f to +270f).
     */
    fun checkMovementCollision(currentX: Float, currentY: Float, nextX: Float, nextY: Float): Pair<Float, Float> {
        val maxBound = 270f
        val clampedX = nextX.coerceIn(-maxBound, maxBound)
        val clampedY = nextY.coerceIn(-maxBound, maxBound)
        return Pair(clampedX, clampedY)
    }

    fun createInitialEnemies(zoneId: String): List<EnemyEntity> {
        return when (zoneId) {
            "astral_sanctuary" -> listOf(
                EnemyEntity("e_1", "void_crawler", "Void Crawler", 1, 65, 65, 8, 4, 35f, zoneId, 120f, -80f, 130f, 45f),
                EnemyEntity("e_2", "void_crawler", "Void Crawler", 1, 65, 65, 8, 4, 35f, zoneId, -140f, 90f, 130f, 45f),
                EnemyEntity("e_3", "rift_stalker", "Rift Stalker", 2, 130, 130, 16, 8, 50f, zoneId, 180f, 160f, 160f, 50f),
                EnemyEntity("e_4", "astral_golem", "Astral Sentry", 3, 260, 260, 24, 18, 28f, zoneId, -200f, -160f, 120f, 55f)
            )
            "void_wastes" -> listOf(
                EnemyEntity("e_5", "rift_stalker", "Phase Stalker", 3, 150, 150, 20, 12, 55f, zoneId, 80f, 80f, 170f, 50f),
                EnemyEntity("e_6", "rift_stalker", "Phase Stalker", 4, 190, 190, 25, 14, 55f, zoneId, -120f, -90f, 170f, 50f),
                EnemyEntity("e_7", "astral_golem", "Void Juggernaut", 5, 420, 420, 36, 26, 30f, zoneId, 190f, -150f, 140f, 60f)
            )
            "abyssal_depths" -> listOf(
                EnemyEntity("e_8", "astral_golem", "Nether Guardian", 6, 550, 550, 42, 30, 32f, zoneId, -100f, 100f, 150f, 60f),
                EnemyEntity("boss_1", "abyssal_lord", "Abyssal Lord Val'khor", 10, 1600, 1600, 75, 45, 42f, zoneId, 0f, -220f, 220f, 75f)
            )
            else -> emptyList()
        }
    }

    fun updateEnemyAI(
        enemies: List<EnemyEntity>,
        playerX: Float,
        playerY: Float,
        deltaSec: Float
    ) {
        val now = System.currentTimeMillis()
        for (enemy in enemies) {
            // Handle respawn after 8 seconds
            if (enemy.isDead) {
                if (now - enemy.deathTimestamp > 8000L) {
                    enemy.isDead = false
                    enemy.hp = enemy.maxHp
                    // Randomize respawn spot slightly
                    enemy.posX += Random.nextFloat() * 40f - 20f
                    enemy.posY += Random.nextFloat() * 40f - 20f
                }
                continue
            }

            // Calculate distance to player
            val dx = playerX - enemy.posX
            val dy = playerY - enemy.posY
            val dist = sqrt(dx * dx + dy * dy)

            if (dist <= enemy.aggroRange) {
                // If in aggro range and further than attack range, chase
                if (dist > enemy.attackRange) {
                    val nx = dx / dist
                    val ny = dy / dist
                    enemy.posX += nx * enemy.speed * deltaSec
                    enemy.posY += ny * enemy.speed * deltaSec
                }
            } else {
                // Gentle idle wander
                if (Random.nextFloat() < 0.02f) {
                    enemy.posX += (Random.nextFloat() * 16f - 8f)
                    enemy.posY += (Random.nextFloat() * 16f - 8f)
                }
            }
        }
    }
}
