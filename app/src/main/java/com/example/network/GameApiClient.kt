package com.example.network

import com.example.model.AuthSession
import com.example.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CharacterSummary(
    val id: String,
    val name: String,
    val level: Int,
    val xp: Long,
    val gold: Long,
    val voidShards: Long,
    val hp: Int,
    val maxHp: Int,
    val strength: Int,
    val defense: Int,
    val agility: Int,
    val zoneId: String
)

class GameApiClient(
    private var baseUrl: String = "http://10.0.2.2:3000"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun getBaseUrl(): String = baseUrl

    fun getWebSocketUrl(): String {
        return baseUrl.replace("http://", "ws://").replace("https://", "wss://") + "/game"
    }

    suspend fun register(username: String, email: String, pass: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("username", username)
                put("email", email)
                put("password", pass)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/auth/register")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errObj = try { JSONObject(body) } catch (_: Exception) { null }
                val msg = errObj?.optString("error") ?: "Registration failed (${response.code})"
                return@withContext Result.failure(Exception(msg))
            }

            val json = JSONObject(body)
            val token = json.getString("token")
            val userObj = json.getJSONObject("user")
            val session = AuthSession(
                token = token,
                userId = userObj.getString("id"),
                username = userObj.getString("username"),
                role = parseRole(userObj.optString("role", "PLAYER"))
            )
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, pass: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("username", username)
                put("password", pass)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/auth/login")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errObj = try { JSONObject(body) } catch (_: Exception) { null }
                val msg = errObj?.optString("error") ?: "Login failed (${response.code})"
                return@withContext Result.failure(Exception(msg))
            }

            val json = JSONObject(body)
            val token = json.getString("token")
            val userObj = json.getJSONObject("user")
            val session = AuthSession(
                token = token,
                userId = userObj.getString("id"),
                username = userObj.getString("username"),
                role = parseRole(userObj.optString("role", "PLAYER"))
            )
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCharacters(token: String): Result<List<CharacterSummary>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/characters")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch characters (${response.code})"))
            }

            val json = JSONObject(body)
            val arr = json.optJSONArray("characters") ?: JSONArray()
            val list = mutableListOf<CharacterSummary>()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                list.add(
                    CharacterSummary(
                        id = c.getString("id"),
                        name = c.getString("name"),
                        level = c.optInt("level", 1),
                        xp = c.optLong("xp", 0L),
                        gold = c.optLong("gold", 0L),
                        voidShards = c.optLong("void_shards", 0L),
                        hp = c.optInt("hp", 150),
                        maxHp = c.optInt("max_hp", 150),
                        strength = c.optInt("strength", 10),
                        defense = c.optInt("defense", 8),
                        agility = c.optInt("agility", 10),
                        zoneId = c.optString("zone_id", "astral_sanctuary")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createCharacter(token: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("name", name)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/characters")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errObj = try { JSONObject(body) } catch (_: Exception) { null }
                val msg = errObj?.optString("error") ?: "Character creation failed (${response.code})"
                return@withContext Result.failure(Exception(msg))
            }

            val json = JSONObject(body)
            Result.success(json.getString("characterId"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sellShopItem(token: String, characterId: String, itemId: String, quantity: Int = 1): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("characterId", characterId)
                put("itemId", itemId)
                put("quantity", quantity)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/shop/sell")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errObj = try { JSONObject(body) } catch (_: Exception) { null }
                val msg = errObj?.optString("error") ?: "Sale failed (${response.code})"
                return@withContext Result.failure(Exception(msg))
            }
            Result.success(JSONObject(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun buyShopItem(token: String, characterId: String, itemCode: String, quantity: Int = 1): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("characterId", characterId)
                put("itemCode", itemCode)
                put("quantity", quantity)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/shop/buy")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errObj = try { JSONObject(body) } catch (_: Exception) { null }
                val msg = errObj?.optString("error") ?: "Purchase failed (${response.code})"
                return@withContext Result.failure(Exception(msg))
            }
            Result.success(JSONObject(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
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
