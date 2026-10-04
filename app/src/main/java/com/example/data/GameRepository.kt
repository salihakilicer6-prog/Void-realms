package com.example.data

import com.example.engine.WorldEngine
import com.example.model.*
import com.example.network.CharacterSummary
import com.example.network.GameApiClient
import com.example.network.GameNetworkListener
import com.example.network.GameWebSocketClient
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.VoidPurple
import com.example.ui.theme.VoidCrimson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class GameRepository : GameNetworkListener {

    val apiClient = GameApiClient()
    private val wsClient = GameWebSocketClient(this)

    private val _authSession = MutableStateFlow<AuthSession?>(null)
    val authSession: StateFlow<AuthSession?> = _authSession.asStateFlow()

    private val _networkState = MutableStateFlow(NetworkConnectionState())
    val networkState: StateFlow<NetworkConnectionState> = _networkState.asStateFlow()

    private val _availableCharacters = MutableStateFlow<List<CharacterSummary>>(emptyList())
    val availableCharacters: StateFlow<List<CharacterSummary>> = _availableCharacters.asStateFlow()

    private val _remotePlayers = MutableStateFlow<List<RemotePlayer>>(emptyList())
    val remotePlayers: StateFlow<List<RemotePlayer>> = _remotePlayers.asStateFlow()

    private val _player = MutableStateFlow(PlayerStats(gold = 0, voidShards = 0, xp = 0))
    val player: StateFlow<PlayerStats> = _player.asStateFlow()

    private val _enemies = MutableStateFlow<List<EnemyEntity>>(emptyList())
    val enemies: StateFlow<List<EnemyEntity>> = _enemies.asStateFlow()

    private val _quests = MutableStateFlow<List<Quest>>(emptyList())
    val quests: StateFlow<List<Quest>> = _quests.asStateFlow()

    private val _floatingTexts = MutableStateFlow<List<FloatingCombatText>>(emptyList())
    val floatingTexts: StateFlow<List<FloatingCombatText>> = _floatingTexts.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AdminAuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AdminAuditLog>> = _auditLogs.asStateFlow()

    private val _serverMessage = MutableStateFlow<String?>(null)
    val serverMessage: StateFlow<String?> = _serverMessage.asStateFlow()

    init {
        initializeStarterData()
    }

    private fun initializeStarterData() {
        val p = _player.value
        p.role = UserRole.PLAYER
        p.inventory.clear()
        p.equipment.clear()
        recalculateStats()

        _chatMessages.value = listOf(
            ChatMessage(
                id = "m_1",
                sender = "SYSTEM",
                role = UserRole.OWNER,
                text = "Welcome to VOID REALMS. Authoritative multiplayer networking ready."
            )
        )

        recordAudit("CLIENT_INIT", "Client initialized. Connect to authoritative server.", "SUCCESS")
    }

    // --- REAL BACKEND AUTHENTICATION & MULTIPLAYER ACTIONS ---

    suspend fun loginToServer(username: String, pass: String): Result<AuthSession> {
        val result = apiClient.login(username, pass)
        result.onSuccess { session ->
            _authSession.value = session
            val p = _player.value
            p.role = session.role
            _player.value = p.copy()
            _serverMessage.value = "Logged in as ${session.username} (${session.role.displayName})"
            fetchCharacters()
        }.onFailure { err ->
            _serverMessage.value = "Login error: ${err.message}"
        }
        return result
    }

    suspend fun registerOnServer(username: String, email: String, pass: String): Result<AuthSession> {
        val result = apiClient.register(username, email, pass)
        result.onSuccess { session ->
            _authSession.value = session
            val p = _player.value
            p.role = session.role
            _player.value = p.copy()
            _serverMessage.value = "Registered as ${session.username}"
            fetchCharacters()
        }.onFailure { err ->
            _serverMessage.value = "Registration error: ${err.message}"
        }
        return result
    }

    suspend fun fetchCharacters(): Result<List<CharacterSummary>> {
        val session = _authSession.value ?: return Result.failure(Exception("Not authenticated"))
        val result = apiClient.getCharacters(session.token)
        result.onSuccess { list ->
            _availableCharacters.value = list
            if (list.isNotEmpty() && _networkState.value.activeCharacterId == null) {
                connectToWorld(list[0].id)
            }
        }
        return result
    }

    suspend fun createServerCharacter(name: String): Result<String> {
        val session = _authSession.value ?: return Result.failure(Exception("Not authenticated"))
        val result = apiClient.createCharacter(session.token, name)
        result.onSuccess { charId ->
            _serverMessage.value = "Created character: $name"
            fetchCharacters()
            connectToWorld(charId)
        }
        return result
    }

    fun connectToWorld(characterId: String) {
        val session = _authSession.value ?: return
        val wsUrl = apiClient.getWebSocketUrl()
        wsClient.connect(wsUrl, session.token, characterId)
    }

    fun disconnectFromWorld() {
        wsClient.disconnect()
        _remotePlayers.value = emptyList()
    }

    // --- GAME NETWORK LISTENER CALLBACKS ---

    override fun onConnectionStateChanged(state: NetworkConnectionState) {
        _networkState.value = state
    }

    override fun onWorldSnapshot(character: PlayerStats, players: List<RemotePlayer>, enemies: List<EnemyEntity>) {
        // Authoritative server state completely replaces client state
        _player.value = character
        _remotePlayers.value = players
        if (enemies.isNotEmpty()) {
            _enemies.value = enemies
        }
        recalculateStats()
        _serverMessage.value = "Connected to Astral Sanctuary. Online players: ${players.size + 1}"
    }

    override fun onInventorySnapshot(inventory: List<InventorySlot>, equipment: Map<EquipSlot, GameItem?>) {
        val p = _player.value
        p.inventory.clear()
        p.inventory.addAll(inventory)
        p.equipment.clear()
        p.equipment.putAll(equipment)
        recalculateStats()
        _player.value = p.copy()
        _serverMessage.value = "Inventory synchronized with PostgreSQL."
    }

    override fun onRemotePlayerJoined(player: RemotePlayer) {
        _remotePlayers.value = _remotePlayers.value.filter { it.id != player.id } + player
        addFloatingText(FloatingCombatText(System.currentTimeMillis(), "${player.name} joined", player.posX, player.posY - 25f, false, NeonYellow))
    }

    override fun onRemotePlayerMoved(playerId: String, posX: Float, posY: Float) {
        val updated = _remotePlayers.value.map { p ->
            if (p.id == playerId) {
                p.posX = posX
                p.posY = posY
                p
            } else p
        }
        _remotePlayers.value = updated
    }

    override fun onRemotePlayerLeft(playerId: String) {
        _remotePlayers.value = _remotePlayers.value.filter { it.id != playerId }
    }

    override fun onCombatEvent(
        attackerId: String,
        targetId: String,
        damage: Int,
        isCrit: Boolean,
        targetRemainingHp: Int,
        targetDied: Boolean,
        xpAwarded: Long?,
        goldAwarded: Long?
    ) {
        // Authoritative combat event from server
        val enemiesList = _enemies.value.toMutableList()
        val enemyIndex = enemiesList.indexOfFirst { it.id == targetId }
        if (enemyIndex != -1) {
            val enemy = enemiesList[enemyIndex]
            enemy.hp = targetRemainingHp
            enemy.isDead = targetDied

            val color = if (isCrit) NeonYellow else VoidPurple
            addFloatingText(FloatingCombatText(System.currentTimeMillis(), "-$damage", enemy.posX, enemy.posY - 20f, isCrit, color))

            if (targetDied) {
                enemiesList.removeAt(enemyIndex)
            }
            _enemies.value = enemiesList
        }

        // Apply authoritative rewards from server
        if (attackerId == _player.value.id) {
            if (goldAwarded != null && goldAwarded > 0) {
                val p = _player.value
                p.gold += goldAwarded
                _player.value = p.copy()
            }
            if (xpAwarded != null && xpAwarded > 0) {
                addExperience(xpAwarded)
            }
        }
    }

    override fun onPlayerDamage(attackerId: String, damage: Int, remainingHp: Int, died: Boolean) {
        val p = _player.value
        p.hp = remainingHp.coerceAtLeast(0)
        p.isDead = died
        _player.value = p.copy()
        addFloatingText(
            FloatingCombatText(
                System.currentTimeMillis(),
                if (died) "YOU DIED" else "-$damage",
                p.posX,
                p.posY - 28f,
                died,
                if (died) VoidCrimson else NeonYellow
            )
        )
    }

    override fun onChatMessage(chatMessage: ChatMessage) {
        _chatMessages.value = _chatMessages.value + chatMessage
    }

    override fun onError(message: String) {
        _serverMessage.value = "Network: $message"
    }

    // --- GAMEPLAY INPUT (AUTHORITATIVE FORWARDING) ---

    fun updatePlayerPosition(x: Float, y: Float) {
        val p = _player.value
        p.posX = x
        p.posY = y
        _player.value = p.copy()

        if (_networkState.value.isAuthenticated) {
            wsClient.sendMovement(x, y)
        }
    }

    fun recalculateStats() {
        val p = _player.value
        var bonusDef = 0
        var bonusStr = 0
        var bonusAgi = 0
        var bonusCrit = 0f

        for (item in p.equipment.values) {
            if (item != null) {
                bonusDef += item.defense
                bonusStr += item.strBonus
                bonusAgi += item.agiBonus
                bonusCrit += item.critBonus
            }
        }

        p.maxHp = 150 + (p.level * 25) + (p.strength * 5)
        p.maxMana = 100 + (p.level * 15)
        p.defense = 8 + (p.level * 2) + bonusDef
        p.strength = 10 + (p.level * 2) + bonusStr
        p.agility = 10 + (p.level * 2) + bonusAgi
        p.critChance = (5.0f + (p.agility * 0.25f) + bonusCrit).coerceAtMost(75f)
        p.critDamage = 150.0f + (p.strength * 0.5f)

        p.hp = p.hp.coerceAtMost(p.maxHp)
        p.mana = p.mana.coerceAtMost(p.maxMana)
        _player.value = p.copy()
    }

    fun addExperience(amount: Long) {
        val p = _player.value
        p.xp += amount
        while (p.level < 100) {
            val req = p.level * p.level * 100L
            if (p.xp >= req) {
                p.xp -= req
                p.level++
                p.hp = p.maxHp
                p.mana = p.maxMana
                sendSystemBroadcast("LEVEL UP! Reached Level ${p.level}!")
            } else {
                break
            }
        }
        recalculateStats()
    }

    fun equipItem(slotIndex: Int): Boolean {
        if (!_networkState.value.isAuthenticated) {
            _serverMessage.value = "Cannot equip while disconnected from authoritative server."
            return false
        }
        val p = _player.value
        val invItem = p.inventory.find { it.slotIndex == slotIndex } ?: return false
        val targetEquipSlot = invItem.item.equipSlot ?: return false

        wsClient.sendEquipItem(slotIndex, targetEquipSlot)
        return true
    }

    fun unequipItem(slot: EquipSlot): Boolean {
        if (!_networkState.value.isAuthenticated) {
            _serverMessage.value = "Cannot unequip while disconnected from authoritative server."
            return false
        }
        val p = _player.value
        if (p.equipment[slot] == null) return false

        wsClient.sendUnequipItem(slot)
        return true
    }

    fun sellItem(slotIndex: Int) {
        if (!_networkState.value.isAuthenticated) {
            _serverMessage.value = "Shop transactions require server connection."
            return
        }
        val session = _authSession.value ?: return
        val charId = _networkState.value.activeCharacterId ?: return
        val p = _player.value
        val inv = p.inventory.find { it.slotIndex == slotIndex } ?: return
        if (inv.isLocked) {
            _serverMessage.value = "Cannot sell locked item!"
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val result = apiClient.sellShopItem(session.token, charId, inv.item.id, inv.quantity)
            result.onSuccess { res ->
                val goldEarned = res.optLong("goldEarned", 0L)
                val newBalance = res.optLong("newBalance", p.gold + goldEarned)
                p.inventory.removeIf { it.slotIndex == slotIndex }
                p.gold = newBalance
                _player.value = p.copy()
                _serverMessage.value = "Sold ${inv.item.name} for $goldEarned Gold"
            }.onFailure { err ->
                _serverMessage.value = "Sale rejected: ${err.message}"
            }
        }
    }

    fun toggleItemLock(slotIndex: Int) {
        val p = _player.value
        val inv = p.inventory.find { it.slotIndex == slotIndex } ?: return
        inv.isLocked = !inv.isLocked
        _player.value = p.copy()
    }

    fun changeZone(zoneId: String) {
        val p = _player.value
        p.zoneId = zoneId
        p.posX = 0f
        p.posY = 0f
        _player.value = p.copy()
        _serverMessage.value = "Entered ${WorldEngine.ZONES.find { it.id == zoneId }?.name}"
    }

    /**
     * Attacks target enemy strictly through authoritative server WebSocket.
     * Offline local fallback is completely removed: attacks fail safely without rewards.
     */
    fun attackTargetEnemy(enemyId: String, attackType: String) {
        if (!_networkState.value.isAuthenticated) {
            _serverMessage.value = "Attacks disabled: Not connected to authoritative server."
            return
        }
        wsClient.sendAttack(enemyId, attackType)
    }

    fun usePotion() {
        if (!_networkState.value.isAuthenticated) {
            _serverMessage.value = "Potions disabled: Not connected to authoritative server."
            return
        }
        val p = _player.value
        val potIndex = p.inventory.indexOfFirst { it.item.itemType == ItemType.CONSUMABLE && it.quantity > 0 }
        if (potIndex != -1) {
            val slot = p.inventory[potIndex]
            p.hp = (p.hp + 75).coerceAtMost(p.maxHp)
            slot.quantity--
            if (slot.quantity <= 0) {
                p.inventory.removeAt(potIndex)
            }
            _player.value = p.copy()
            addFloatingText(FloatingCombatText(System.currentTimeMillis(), "+75 HP", p.posX, p.posY - 20f, false, NeonCyan))
        } else {
            _serverMessage.value = "No potions in inventory!"
        }
    }

    fun claimQuest(questId: String) {
        val qList = _quests.value.toMutableList()
        val index = qList.indexOfFirst { it.id == questId }
        if (index != -1) {
            val quest = qList[index]
            if (quest.isCompleted && !quest.isClaimed) {
                qList[index] = quest.copy(isClaimed = true)
                _quests.value = qList
                _serverMessage.value = "Claimed: ${quest.title}"
            }
        }
    }

    fun sendChatMessage(text: String, channel: String = "GLOBAL") {
        if (_networkState.value.isAuthenticated) {
            wsClient.sendChatMessage(text)
            return
        }
        _serverMessage.value = "Cannot chat while disconnected from server."
    }

    fun sendSystemBroadcast(message: String) {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = "SYSTEM",
            role = UserRole.OWNER,
            text = message,
            channel = "ANNOUNCEMENT"
        )
        _chatMessages.value = _chatMessages.value + msg
    }

    fun addFloatingText(text: FloatingCombatText) {
        _floatingTexts.value = _floatingTexts.value + text
    }

    fun pruneFloatingTexts() {
        val now = System.currentTimeMillis()
        _floatingTexts.value = _floatingTexts.value.filter { now - it.createdAt < 1200L }
    }

    fun recordAudit(action: String, parameters: String, result: String = "SUCCESS") {
        val p = _player.value
        val log = AdminAuditLog(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            adminName = p.name,
            adminRole = p.role,
            action = action,
            target = p.name,
            parameters = parameters,
            result = result
        )
        _auditLogs.value = listOf(log) + _auditLogs.value
    }

    // --- OWNER / ADMIN OPERATIONS (REQUIRING SERVER AUTHORIZATION) ---
    fun adminGrantXp(amount: Long) {
        recordAudit("ADMIN_GIVE_XP", "Amount: $amount XP")
        _serverMessage.value = "Admin action requires server authority."
    }

    fun adminSetLevel(newLevel: Int) {
        recordAudit("ADMIN_SET_LEVEL", "New Level: $newLevel")
        _serverMessage.value = "Admin action requires server authority."
    }

    fun adminGrantGold(amount: Long) {
        recordAudit("ADMIN_GIVE_GOLD", "Amount: $amount Gold")
        _serverMessage.value = "Admin action requires server authority."
    }

    fun adminGrantVoidShards(amount: Long) {
        recordAudit("ADMIN_GIVE_SHARDS", "Amount: $amount Shards")
        _serverMessage.value = "Admin action requires server authority."
    }

    fun adminSpawnCustomItem(rarity: ItemRarity) {
        recordAudit("ADMIN_SPAWN_ITEM", "Rarity: ${rarity.name}")
        _serverMessage.value = "Item creation requires server authority."
    }

    fun adminSpawnMonster(defId: String) {
        recordAudit("ADMIN_SPAWN_MONSTER", "Definition: $defId")
        _serverMessage.value = "Monster spawning requires server authority."
    }

    fun adminHealFull() {
        recordAudit("ADMIN_HEAL", "Restored vitals")
        _serverMessage.value = "Admin heal requires server authority."
    }

    fun clearServerMessage() {
        _serverMessage.value = null
    }
}
