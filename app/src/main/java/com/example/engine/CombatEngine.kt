package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.model.*
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.VoidCrimson
import com.example.ui.theme.VoidGold
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

data class AttackOutcome(
    val hit: Boolean,
    val damage: Int,
    val isCrit: Boolean,
    val enemyDied: Boolean,
    val xpGained: Long,
    val goldGained: Long,
    val droppedItem: GameItem?,
    val floatingText: FloatingCombatText?
)

object CombatEngine {
    private var lastPlayerAttackTime = 0L

    fun executePlayerAttack(
        player: PlayerStats,
        enemy: EnemyEntity,
        attackType: String
    ): AttackOutcome {
        val now = System.currentTimeMillis()
        val cooldown = if (attackType == "MELEE") 450L else 900L
        if (now - lastPlayerAttackTime < cooldown) {
            return AttackOutcome(false, 0, false, false, 0, 0, null, null)
        }
        lastPlayerAttackTime = now

        // Distance check
        val dx = player.posX - enemy.posX
        val dy = player.posY - enemy.posY
        val dist = sqrt(dx * dx + dy * dy)
        val maxDist = if (attackType == "MELEE") 85f else 280f
        if (dist > maxDist) {
            return AttackOutcome(false, 0, false, false, 0, 0, null, null)
        }

        // Damage formula
        val mainWeaponDmg = player.equipment[EquipSlot.MAIN_WEAPON]?.damage ?: 12
        val statMultiplier = 1.0f + (player.strength * 0.035f) + (player.agility * 0.02f)
        var rawDamage = (mainWeaponDmg * statMultiplier).roundToInt().coerceAtLeast(1)

        if (attackType == "VOID_BLAST") {
            rawDamage = (rawDamage * 1.8f).roundToInt()
        }

        val defMultiplier = 100f / (100f + enemy.defense.coerceAtLeast(0))
        var finalDamage = (rawDamage * defMultiplier).roundToInt().coerceAtLeast(1)

        val isCrit = Random.nextFloat() * 100f < player.critChance
        if (isCrit) {
            finalDamage = (finalDamage * (player.critDamage / 100f)).roundToInt()
        }

        enemy.hp = (enemy.hp - finalDamage).coerceAtLeast(0)
        val enemyDied = enemy.hp == 0

        var xpGained = 0L
        var goldGained = 0L
        var droppedItem: GameItem? = null

        if (enemyDied) {
            enemy.isDead = true
            enemy.deathTimestamp = now
            xpGained = (enemy.level * 40L + Random.nextInt(15, 30))
            goldGained = (enemy.level * 18L + Random.nextInt(10, 40))

            // Procedural loot drop chance (35%)
            if (Random.nextFloat() < 0.35f) {
                droppedItem = ProceduralItemEngine.generateItem(level = enemy.level)
            }
        }

        val floatText = FloatingCombatText(
            id = now + Random.nextLong(1000),
            text = if (isCrit) "CRIT! -$finalDamage" else "-$finalDamage",
            x = enemy.posX,
            y = enemy.posY - 25f,
            isCrit = isCrit,
            color = if (isCrit) VoidGold else if (attackType == "VOID_BLAST") NeonCyan else Color.White
        )

        return AttackOutcome(
            hit = true,
            damage = finalDamage,
            isCrit = isCrit,
            enemyDied = enemyDied,
            xpGained = xpGained,
            goldGained = goldGained,
            droppedItem = droppedItem,
            floatingText = floatText
        )
    }

    fun executeEnemyAttack(
        enemy: EnemyEntity,
        player: PlayerStats
    ): FloatingCombatText? {
        val defFactor = 100f / (100f + player.defense.coerceAtLeast(0))
        val damage = (enemy.damage * defFactor).roundToInt().coerceAtLeast(1)

        player.hp = (player.hp - damage).coerceAtLeast(0)
        if (player.hp == 0) {
            player.isDead = true
        }

        return FloatingCombatText(
            id = System.currentTimeMillis() + Random.nextLong(1000),
            text = "-$damage",
            x = player.posX,
            y = player.posY - 25f,
            isCrit = false,
            color = VoidCrimson
        )
    }
}
