package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.model.EnemyEntity
import com.example.model.ZoneInfo
import kotlin.math.sqrt
import kotlin.random.Random

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
        )
    )

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
