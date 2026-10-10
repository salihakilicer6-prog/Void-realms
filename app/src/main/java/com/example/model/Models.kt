package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class UserRole(val displayName: String, val level: Int) {
    PLAYER("Player", 1),
    MODERATOR("Moderator", 20),
    ADMIN("Admin", 50),
    OWNER("Owner / Architect", 100)
}

enum class ItemRarity(val displayName: String, val color: Color, val multiplier: Float) {
    COMMON("Common", RarityCommon, 1.0f),
    UNCOMMON("Uncommon", RarityUncommon, 1.25f),
    RARE("Rare", RarityRare, 1.6f),
    EPIC("Epic", RarityEpic, 2.1f),
    LEGENDARY("Legendary", RarityLegendary, 3.0f),
    MYTHIC("Mythic", RarityMythic, 4.5f),
    DIVINE("Divine", RarityDivine, 7.0f),
    ANCIENT("Ancient", RarityAncient, 11.0f),
    OWNER("Owner", RarityOwner, 25.0f)
}

enum class EquipSlot(val label: String) {
    MAIN_WEAPON("Main Weapon"),
    OFF_HAND("Off-Hand"),
    HELMET("Helmet"),
    CHEST("Chest Armor"),
    GLOVES("Gloves"),
    BOOTS("Boots"),
    RING_1("Ring (Left)"),
    RING_2("Ring (Right)"),
    NECKLACE("Necklace"),
    SPECIAL("Special Artifact")
}

enum class ItemType {
    WEAPON, ARMOR, CONSUMABLE, MATERIAL, ARTIFACT
}

data class GameItem(
    val id: String,
    val itemCode: String,
    val name: String,
    val itemType: ItemType,
    val equipSlot: EquipSlot? = null,
    val rarity: ItemRarity = ItemRarity.COMMON,
    val requiredLevel: Int = 1,
    val damage: Int = 0,
    val defense: Int = 0,
    val critBonus: Float = 0f,
    val strBonus: Int = 0,
    val agiBonus: Int = 0,
    val element: String = "Physical",
    val specialEffect: String? = null,
    val isStackable: Boolean = false,
    val maxStack: Int = 1,
    val baseValue: Long = 10,
    val seedHash: String = ""
)

data class InventorySlot(
    val slotIndex: Int,
    val item: GameItem,
    var quantity: Int = 1,
    var isLocked: Boolean = false
)

data class PlayerStats(
    val id: String = "char_001",
    var name: String = "VoidStalker",
    var level: Int = 1,
    var xp: Long = 0,
    var gold: Long = 150,
    var voidShards: Long = 25,
    var hp: Int = 150,
    var maxHp: Int = 150,
    var mana: Int = 100,
    var maxMana: Int = 100,
    var strength: Int = 10,
    var defense: Int = 8,
    var agility: Int = 10,
    var critChance: Float = 5.0f,
    var critDamage: Float = 150.0f,
    var zoneId: String = "astral_sanctuary",
    var posX: Float = 0f,
    var posY: Float = 0f,
    var isDead: Boolean = false,
    var role: UserRole = UserRole.PLAYER,
    val equipment: MutableMap<EquipSlot, GameItem?> = mutableMapOf(),
    val inventory: MutableList<InventorySlot> = mutableListOf()
)

data class EnemyEntity(
    val id: String,
    val definitionId: String,
    val name: String,
    val level: Int,
    var hp: Int,
    val maxHp: Int,
    val damage: Int,
    val defense: Int,
    val speed: Float,
    val zoneId: String,
    var posX: Float,
    var posY: Float,
    val aggroRange: Float,
    val attackRange: Float,
    var isDead: Boolean = false,
    var deathTimestamp: Long = 0L
)

enum class QuestType { MAIN, SIDE, DAILY }
enum class QuestObjectiveType { KILL_MONSTER, COLLECT_ITEM, EXPLORE }

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val type: QuestType,
    val objectiveType: QuestObjectiveType,
    val targetId: String,
    val targetName: String,
    val requiredCount: Int,
    var currentCount: Int = 0,
    val rewardXp: Long,
    val rewardGold: Long,
    var isCompleted: Boolean = false,
    var isClaimed: Boolean = false
)

data class ChatMessage(
    val id: String,
    val sender: String,
    val role: UserRole,
    val text: String,
    val channel: String = "GLOBAL",
    val timestamp: Long = System.currentTimeMillis()
)

data class AdminAuditLog(
    val id: String,
    val timestamp: Long,
    val adminName: String,
    val adminRole: UserRole,
    val action: String,
    val target: String,
    val parameters: String,
    val result: String = "SUCCESS"
)

data class FloatingCombatText(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val isCrit: Boolean,
    val color: Color,
    var alpha: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis()
)

data class ZoneInfo(
    val id: String,
    val name: String,
    val description: String,
    val minLevel: Int,
    val isSafeZone: Boolean,
    val ambientColor: Color
)

data class RemotePlayer(
    val id: String,
    val name: String,
    val level: Int,
    var posX: Float,
    var posY: Float,
    var hp: Int,
    var maxHp: Int
)

data class AuthSession(
    val token: String,
    val userId: String,
    val username: String,
    val role: UserRole
)

data class NetworkConnectionState(
    val isConnected: Boolean = false,
    val isAuthenticated: Boolean = false,
    val currentZoneId: String = "astral_sanctuary",
    val latencyMs: Long = 0L,
    val statusMessage: String = "Disconnected",
    val activeCharacterId: String? = null
)

data class NpcEntity(
    val id: String,
    val name: String,
    val title: String,
    val role: String,
    val zoneId: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float = 0f,
    val primaryColor: Color,
    val outfitColor: Color,
    val dialogue: String,
    val questId: String? = null
)

data class WorldItemDrop(
    val id: String,
    val item: GameItem,
    val zoneId: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float = 0f,
    var isPickedUp: Boolean = false,
    var rotation: Float = 0f
)
