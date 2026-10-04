package com.example.engine

import com.example.model.*
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

object ProceduralItemEngine {

    data class ItemBlueprint(
        val baseId: String,
        val name: String,
        val type: ItemType,
        val slot: EquipSlot,
        val baseDmg: Int,
        val baseDef: Int
    )

    data class Prefix(
        val name: String,
        val statMultiplier: Float,
        val element: String,
        val effect: String
    )

    data class Suffix(
        val name: String,
        val bonusCrit: Float,
        val bonusStr: Int,
        val bonusAgi: Int
    )

    val BASE_ITEMS = listOf(
        // Weapons
        ItemBlueprint("void_blade", "Void Blade", ItemType.WEAPON, EquipSlot.MAIN_WEAPON, 26, 0),
        ItemBlueprint("astral_staff", "Astral Staff", ItemType.WEAPON, EquipSlot.MAIN_WEAPON, 32, 0),
        ItemBlueprint("shadow_dagger", "Shadow Dagger", ItemType.WEAPON, EquipSlot.MAIN_WEAPON, 19, 0),
        ItemBlueprint("phase_scythe", "Phase Scythe", ItemType.WEAPON, EquipSlot.MAIN_WEAPON, 36, 0),
        ItemBlueprint("cosmic_bow", "Cosmic Bow", ItemType.WEAPON, EquipSlot.MAIN_WEAPON, 29, 0),
        ItemBlueprint("chrono_wand", "Chrono Wand", ItemType.WEAPON, EquipSlot.MAIN_WEAPON, 23, 0),
        ItemBlueprint("nether_cleaver", "Nether Cleaver", ItemType.WEAPON, EquipSlot.MAIN_WEAPON, 42, 0),
        ItemBlueprint("singularity_mace", "Singularity Mace", ItemType.WEAPON, EquipSlot.MAIN_WEAPON, 34, 0),
        // Armor & Accessories
        ItemBlueprint("void_helm", "Void Crown", ItemType.ARMOR, EquipSlot.HELMET, 0, 16),
        ItemBlueprint("astral_cuirass", "Astral Cuirass", ItemType.ARMOR, EquipSlot.CHEST, 0, 32),
        ItemBlueprint("phase_greaves", "Phase Greaves", ItemType.ARMOR, EquipSlot.BOOTS, 0, 18),
        ItemBlueprint("shadow_gauntlets", "Shadow Gauntlets", ItemType.ARMOR, EquipSlot.GLOVES, 0, 14),
        ItemBlueprint("celestial_ring", "Celestial Ring", ItemType.ARMOR, EquipSlot.RING_1, 6, 6),
        ItemBlueprint("abyssal_pendant", "Abyssal Pendant", ItemType.ARMOR, EquipSlot.NECKLACE, 10, 8)
    )

    val PREFIXES = listOf(
        Prefix("Abyssal", 1.40f, "Void", "Void Corrosion: 5% armor pierce"),
        Prefix("Radiant", 1.30f, "Astral", "Astral Flare: Blinds target on crit"),
        Prefix("Glacial", 1.25f, "Ice", "Frostbite: Chills target by 20%"),
        Prefix("Volcanic", 1.35f, "Fire", "Ignite: Burns target for 3s"),
        Prefix("Thunderous", 1.30f, "Lightning", "Shock: Arc lightning to nearby enemy"),
        Prefix("Chaotic", 1.50f, "Chaos", "Entropy: Random 50-200% damage roll"),
        Prefix("Obsidian", 1.20f, "Physical", "Indomitable: +15% block rate"),
        Prefix("Dreadful", 1.35f, "Void", "Terror: Reduces target attack power"),
        Prefix("Ethereal", 1.25f, "Astral", "Phase Shift: 8% dodge chance"),
        Prefix("Sovereign", 1.60f, "Void", "Supreme Aura: Boosts attributes by 10%"),
        Prefix("Vampiric", 1.25f, "Chaos", "Lifesteal: Heals for 12% damage dealt"),
        Prefix("Vorpal", 1.45f, "Physical", "Decapitate: 3x damage against weakened foes"),
        Prefix("Singularity", 1.70f, "Void", "Gravitational Collapse: Pulls enemies close"),
        Prefix("Primordial", 1.55f, "Astral", "Genesis: Regenerates 5 Energy/sec")
    )

    val SUFFIXES = listOf(
        Suffix("of the Void", 8f, 10, 8),
        Suffix("of Annihilation", 15f, 18, 12),
        Suffix("of Eternity", 5f, 25, 5),
        Suffix("of the Astral Sea", 10f, 8, 18),
        Suffix("of Bloodthirst", 12f, 14, 14),
        Suffix("of the Harbinger", 16f, 22, 20),
        Suffix("of Retribution", 6f, 16, 6),
        Suffix("of Swiftness", 10f, 4, 24),
        Suffix("of Absolute Ruin", 20f, 30, 25),
        Suffix("of the Cosmos", 14f, 20, 20)
    )

    /**
     * Procedural Item Generation Engine
     * Yields over 1,000,000 unique permutations with deterministic scaling.
     */
    fun generateItem(
        level: Int = 1,
        forcedRarity: ItemRarity? = null,
        seed: Long = Random.nextLong()
    ): GameItem {
        val rng = Random(seed)
        val base = BASE_ITEMS[rng.nextInt(BASE_ITEMS.size)]
        val prefix = PREFIXES[rng.nextInt(PREFIXES.size)]
        val suffix = SUFFIXES[rng.nextInt(SUFFIXES.size)]

        val rarity = forcedRarity ?: rollRarity(rng.nextFloat())
        val levelScale = 1.0f + (level - 1) * 0.22f

        val damage = if (base.type == ItemType.WEAPON) {
            (base.baseDmg * levelScale * rarity.multiplier * prefix.statMultiplier).roundToInt()
        } else 0

        val defense = if (base.type == ItemType.ARMOR) {
            (base.baseDef * levelScale * rarity.multiplier * prefix.statMultiplier).roundToInt()
        } else 0

        val critBonus = (suffix.bonusCrit * (rarity.multiplier / 2.2f)).coerceAtMost(50f)
        val strBonus = (suffix.bonusStr * levelScale * (rarity.multiplier / 2.5f)).roundToInt()
        val agiBonus = (suffix.bonusAgi * levelScale * (rarity.multiplier / 2.5f)).roundToInt()

        val fullName = "${prefix.name} ${base.name} ${suffix.name}"
        val itemCode = "proc_${base.baseId}_${rarity.name.lowercase()}_lvl${level}_${seed.toString(16)}"

        return GameItem(
            id = UUID.randomUUID().toString(),
            itemCode = itemCode,
            name = fullName,
            itemType = base.type,
            equipSlot = base.slot,
            rarity = rarity,
            requiredLevel = level,
            damage = damage,
            defense = defense,
            critBonus = critBonus,
            strBonus = strBonus,
            agiBonus = agiBonus,
            element = prefix.element,
            specialEffect = prefix.effect,
            isStackable = false,
            maxStack = 1,
            baseValue = (50L * levelScale * rarity.multiplier).toLong(),
            seedHash = seed.toString(16)
        )
    }

    private fun rollRarity(roll: Float): ItemRarity {
        return when {
            roll < 0.0005f -> ItemRarity.ANCIENT
            roll < 0.003f -> ItemRarity.DIVINE
            roll < 0.015f -> ItemRarity.MYTHIC
            roll < 0.05f -> ItemRarity.LEGENDARY
            roll < 0.12f -> ItemRarity.EPIC
            roll < 0.25f -> ItemRarity.RARE
            roll < 0.55f -> ItemRarity.UNCOMMON
            else -> ItemRarity.COMMON
        }
    }
}
