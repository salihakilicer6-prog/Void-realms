package com.example.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.model.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface GameNetworkListener {
    fun onConnectionStateChanged(state: NetworkConnectionState)
    fun onWorldSnapshot(character: PlayerStats, players: List<RemotePlayer>, enemies: List<EnemyEntity>)
    fun onInventorySnapshot(inventory: List<InventorySlot>, equipment: Map<EquipSlot, GameItem?>)
    fun onRemotePlayerJoined(player: RemotePlayer)
    fun onRemotePlayerMoved(playerId: String, posX: Float, posY: Float)
    fun onRemotePlayerLeft(playerId: String)
    fun onCombatEvent(
        attackerId: String,
        targetId: String,
        damage: Int,
        isCrit: Boolean,
        targetRemainingHp: Int,
        targetDied: Boolean,
        xpAwarded: Long?,
        goldAwarded: Long?
    )
    fun onPlayerDamage(attackerId: String, damage: Int, remainingHp: Int, died: Boolean)
    fun onChatMessage(chatMessage: ChatMessage)
    fun onError(message: String)
}

class GameWebSocketClient(
    private val listener: GameNetworkListener
) {
    private val TAG = "GameWebSocketClient"
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val mainHandler: Handler? = try { Handler(Looper.getMainLooper()) } catch (_: Throwable) { null }

    private fun postToMain(action: () -> Unit) {
        if (mainHandler != null) {
            mainHandler.post(action)
        } else {
            action()
        }
    }

    private fun postDelayedToMain(delayMs: Long, action: () -> Unit) {
        if (mainHandler != null) {
            mainHandler.postDelayed(action, delayMs)
        } else {
            action()
        }
    }

    private var currentToken: String? = null
    private var currentCharacterId: String? = null
    private var currentUrl: String? = null

    private var isConnected = false
    private var isAuthenticated = false
    private var shouldReconnect = true
    private var reconnectAttempts = 0

    fun connect(wsUrl: String, token: String, characterId: String) {
        currentUrl = wsUrl
        currentToken = token
        currentCharacterId = characterId
        shouldReconnect = true
        reconnectAttempts = 0

        startWebSocketConnection()
    }

    private fun startWebSocketConnection() {
        val url = currentUrl ?: return
        val request = Request.Builder().url(url).build()

        updateStatus(false, false, "Connecting to $url...")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempts = 0
                updateStatus(true, false, "Connected. Authenticating...")

                // Send AUTH packet with real JWT
                val authPacket = JSONObject().apply {
                    put("type", "AUTH")
                    put("payload", JSONObject().apply {
                        put("token", currentToken)
                    })
                }
                ws.send(authPacket.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val packet = JSONObject(text)
                    val type = packet.optString("type")
                    val payload = packet.optJSONObject("payload") ?: JSONObject()

                    postToMain {
                        handleIncomingPacket(type, payload)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing incoming packet: ${e.message}")
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
                isAuthenticated = false
                updateStatus(false, false, "Disconnected ($reason)")
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                isAuthenticated = false
                val msg = t.message ?: "Connection failure"
                updateStatus(false, false, "Network Error: $msg")
                scheduleReconnect()
            }
        })
    }

    private fun handleIncomingPacket(type: String, payload: JSONObject) {
        when (type) {
            "AUTH_SUCCESS" -> {
                isAuthenticated = true
                updateStatus(true, true, "Authenticated. Joining world...")

                val joinPacket = JSONObject().apply {
                    put("type", "JOIN_WORLD")
                    put("payload", JSONObject().apply {
                        put("characterId", currentCharacterId)
                    })
                }
                webSocket?.send(joinPacket.toString())
            }

            "AUTH_ERROR" -> {
                val err = payload.optString("message", "Authentication rejected")
                updateStatus(true, false, "Auth Error: $err")
                listener.onError(err)
            }

            "WORLD_SNAPSHOT" -> {
                updateStatus(true, true, "Active in Astral Sanctuary")
                val charObj = payload.optJSONObject("character")
                val playersArr = payload.optJSONArray("players") ?: JSONArray()
                val enemiesArr = payload.optJSONArray("enemies") ?: JSONArray()

                val playerStats = if (charObj != null) parsePlayerStats(charObj) else PlayerStats()
                val playersList = mutableListOf<RemotePlayer>()
                for (i in 0 until playersArr.length()) {
                    val p = playersArr.getJSONObject(i)
                    playersList.add(
                        RemotePlayer(
                            id = p.getString("id"),
                            name = p.getString("name"),
                            level = p.optInt("level", 1),
                            posX = p.optDouble("posX", 0.0).toFloat(),
                            posY = p.optDouble("posY", 0.0).toFloat(),
                            hp = p.optInt("hp", 100),
                            maxHp = p.optInt("maxHp", 100)
                        )
                    )
                }

                val enemiesList = mutableListOf<EnemyEntity>()
                for (i in 0 until enemiesArr.length()) {
                    val e = enemiesArr.getJSONObject(i)
                    enemiesList.add(
                        EnemyEntity(
                            id = e.getString("id"),
                            definitionId = e.optString("definitionId", "void_crawler"),
                            name = e.optString("name", "Void Crawler"),
                            level = e.optInt("level", 1),
                            hp = e.optInt("hp", 100),
                            maxHp = e.optInt("maxHp", 100),
                            damage = e.optInt("damage", 10),
                            defense = e.optInt("defense", 8),
                            speed = e.optDouble("speed", 30.0).toFloat(),
                            zoneId = e.optString("zoneId", "astral_sanctuary"),
                            posX = e.optDouble("posX", 0.0).toFloat(),
                            posY = e.optDouble("posY", 0.0).toFloat(),
                            aggroRange = 140f,
                            attackRange = 45f
                        )
                    )
                }

                listener.onWorldSnapshot(playerStats, playersList, enemiesList)
            }

            "INVENTORY_SNAPSHOT" -> {
                val invArr = payload.optJSONArray("inventory") ?: JSONArray()
                val equipObj = payload.optJSONObject("equipment") ?: JSONObject()
                val inv = parseInventory(invArr)
                val eq = parseEquipment(equipObj)
                listener.onInventorySnapshot(inv, eq)
            }

            "PLAYER_JOINED" -> {
                val player = RemotePlayer(
                    id = payload.getString("id"),
                    name = payload.getString("name"),
                    level = payload.optInt("level", 1),
                    posX = payload.optDouble("posX", 0.0).toFloat(),
                    posY = payload.optDouble("posY", 0.0).toFloat(),
                    hp = payload.optInt("hp", 150),
                    maxHp = payload.optInt("maxHp", 150)
                )
                listener.onRemotePlayerJoined(player)
            }

            "PLAYER_MOVED" -> {
                val id = payload.getString("id")
                val x = payload.optDouble("posX", 0.0).toFloat()
                val y = payload.optDouble("posY", 0.0).toFloat()
                listener.onRemotePlayerMoved(id, x, y)
            }

            "PLAYER_LEFT" -> {
                val charId = payload.optString("characterId")
                if (charId.isNotBlank()) {
                    listener.onRemotePlayerLeft(charId)
                }
            }

            "COMBAT_EVENT" -> {
                listener.onCombatEvent(
                    attackerId = payload.optString("attackerId"),
                    targetId = payload.optString("targetId"),
                    damage = payload.optInt("damage", 0),
                    isCrit = payload.optBoolean("isCrit", false),
                    targetRemainingHp = payload.optInt("targetRemainingHp", 0),
                    targetDied = payload.optBoolean("targetDied", false),
                    xpAwarded = if (payload.has("xpAwarded")) payload.optLong("xpAwarded") else null,
                    goldAwarded = if (payload.has("goldAwarded")) payload.optLong("goldAwarded") else null
                )
            }

            "PLAYER_DAMAGE" -> {
                listener.onPlayerDamage(
                    attackerId = payload.optString("attackerId"),
                    damage = payload.optInt("damage", 0),
                    remainingHp = payload.optInt("playerRemainingHp", 0),
                    died = payload.optBoolean("playerDied", false)
                )
            }

            "CHAT_BROADCAST" -> {
                val msg = ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    sender = payload.optString("sender", "Unknown"),
                    role = parseRole(payload.optString("role", "PLAYER")),
                    text = payload.optString("text", ""),
                    timestamp = payload.optLong("timestamp", System.currentTimeMillis())
                )
                listener.onChatMessage(msg)
            }

            "SESSION_TERMINATED", "EVICTED" -> {
                val reason = payload.optString("reason", payload.optString("message", "Session terminated"))
                updateStatus(false, false, "Kicked: $reason")
                listener.onError(reason)
                shouldReconnect = false
                disconnect()
            }

            "ERROR", "RATE_LIMIT" -> {
                val errMsg = payload.optString("message", "Server error")
                listener.onError(errMsg)
            }
        }
    }

    fun sendMovement(x: Float, y: Float) {
        if (!isAuthenticated || webSocket == null) return
        val packet = JSONObject().apply {
            put("type", "PLAYER_MOVE")
            put("payload", JSONObject().apply {
                put("x", x)
                put("y", y)
            })
        }
        webSocket?.send(packet.toString())
    }

    fun sendAttack(enemyId: String, attackType: String = "MELEE") {
        if (!isAuthenticated || webSocket == null) return
        val packet = JSONObject().apply {
            put("type", "ATTACK_ENEMY")
            put("payload", JSONObject().apply {
                put("enemyId", enemyId)
                put("attackType", attackType)
            })
        }
        webSocket?.send(packet.toString())
    }

    fun sendEquipItem(slotIndex: Int, equipSlot: EquipSlot) {
        if (!isAuthenticated || webSocket == null) return
        val packet = JSONObject().apply {
            put("type", "EQUIP_ITEM")
            put("payload", JSONObject().apply {
                put("slotIndex", slotIndex)
                put("equipSlot", equipSlot.name)
            })
        }
        webSocket?.send(packet.toString())
    }

    fun sendUnequipItem(equipSlot: EquipSlot) {
        if (!isAuthenticated || webSocket == null) return
        val packet = JSONObject().apply {
            put("type", "UNEQUIP_ITEM")
            put("payload", JSONObject().apply {
                put("equipSlot", equipSlot.name)
            })
        }
        webSocket?.send(packet.toString())
    }

    fun sendChatMessage(text: String) {
        if (!isAuthenticated || webSocket == null) return
        val packet = JSONObject().apply {
            put("type", "CHAT_MESSAGE")
            put("payload", JSONObject().apply {
                put("text", text)
            })
        }
        webSocket?.send(packet.toString())
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
        isAuthenticated = false
        updateStatus(false, false, "Disconnected")
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect || reconnectAttempts >= 5) return
        reconnectAttempts++
        val delayMs = (reconnectAttempts * 2000L).coerceAtMost(10000L)
        postDelayedToMain(delayMs) {
            if (shouldReconnect && !isConnected) {
                startWebSocketConnection()
            }
        }
    }

    private fun updateStatus(conn: Boolean, auth: Boolean, msg: String) {
        postToMain {
            listener.onConnectionStateChanged(
                NetworkConnectionState(
                    isConnected = conn,
                    isAuthenticated = auth,
                    statusMessage = msg,
                    activeCharacterId = currentCharacterId
                )
            )
        }
    }

    fun parsePlayerStats(obj: JSONObject): PlayerStats {
        val stats = PlayerStats(
            id = obj.getString("id"),
            name = obj.getString("name"),
            level = obj.optInt("level", 1),
            xp = obj.optLong("xp", 0L),
            gold = obj.optLong("gold", 0L),
            voidShards = obj.optLong("voidShards", obj.optLong("void_shards", 0L)),
            hp = obj.optInt("hp", 150),
            maxHp = obj.optInt("maxHp", obj.optInt("max_hp", 150)),
            mana = obj.optInt("mana", 100),
            maxMana = obj.optInt("maxMana", obj.optInt("max_mana", 100)),
            strength = obj.optInt("strength", 10),
            defense = obj.optInt("defense", 8),
            agility = obj.optInt("agility", 10),
            critChance = obj.optDouble("critChance", 5.0).toFloat(),
            critDamage = obj.optDouble("critDamage", 150.0).toFloat(),
            zoneId = obj.optString("zoneId", "astral_sanctuary"),
            posX = obj.optDouble("posX", 0.0).toFloat(),
            posY = obj.optDouble("posY", 0.0).toFloat(),
            isDead = obj.optBoolean("isDead", false),
            role = parseRole(obj.optString("role", "PLAYER"))
        )

        val invArr = obj.optJSONArray("inventory")
        if (invArr != null) {
            stats.inventory.clear()
            stats.inventory.addAll(parseInventory(invArr))
        }

        val equipObj = obj.optJSONObject("equipment")
        if (equipObj != null) {
            stats.equipment.clear()
            stats.equipment.putAll(parseEquipment(equipObj))
        }

        return stats
    }

    fun parseInventory(arr: JSONArray): List<InventorySlot> {
        val list = mutableListOf<InventorySlot>()
        for (i in 0 until arr.length()) {
            val slotObj = arr.getJSONObject(i)
            val itemObj = slotObj.getJSONObject("item")
            list.add(
                InventorySlot(
                    slotIndex = slotObj.getInt("slotIndex"),
                    item = parseGameItem(itemObj),
                    quantity = slotObj.optInt("quantity", 1),
                    isLocked = slotObj.optBoolean("isLocked", false)
                )
            )
        }
        return list
    }

    fun parseEquipment(obj: JSONObject): Map<EquipSlot, GameItem?> {
        val map = mutableMapOf<EquipSlot, GameItem?>()
        for (slot in EquipSlot.values()) {
            if (obj.has(slot.name) && !obj.isNull(slot.name)) {
                map[slot] = parseGameItem(obj.getJSONObject(slot.name))
            } else {
                map[slot] = null
            }
        }
        return map
    }

    fun parseGameItem(obj: JSONObject): GameItem {
        val slotStr = obj.optString("equipSlot", "")
        val equipSlot = if (slotStr.isNotBlank()) {
            try { EquipSlot.valueOf(slotStr.uppercase()) } catch (_: Exception) { null }
        } else null

        val rarityStr = obj.optString("rarity", "COMMON")
        val rarity = try { ItemRarity.valueOf(rarityStr.uppercase()) } catch (_: Exception) { ItemRarity.COMMON }

        val typeStr = obj.optString("itemType", "WEAPON")
        val itemType = try { ItemType.valueOf(typeStr.uppercase()) } catch (_: Exception) { ItemType.WEAPON }

        return GameItem(
            id = obj.getString("id"),
            itemCode = obj.optString("itemCode", ""),
            name = obj.getString("name"),
            itemType = itemType,
            equipSlot = equipSlot,
            rarity = rarity,
            requiredLevel = obj.optInt("requiredLevel", 1),
            damage = obj.optInt("damage", 0),
            defense = obj.optInt("defense", 0),
            critBonus = obj.optDouble("critBonus", 0.0).toFloat(),
            strBonus = obj.optInt("strBonus", 0),
            agiBonus = obj.optInt("agiBonus", 0),
            element = obj.optString("element", "Physical"),
            specialEffect = if (obj.has("specialEffect")) obj.optString("specialEffect") else null,
            isStackable = obj.optBoolean("isStackable", false),
            maxStack = obj.optInt("maxStack", 1),
            baseValue = obj.optLong("baseValue", 10L)
        )
    }

    private fun parseRole(roleStr: String): UserRole {
        return when (roleStr.uppercase()) {
            "OWNER" -> UserRole.OWNER
            "ADMIN" -> UserRole.ADMIN
            "MODERATOR" -> UserRole.MODERATOR
            else -> UserRole.PLAYER
        }
    }
}
