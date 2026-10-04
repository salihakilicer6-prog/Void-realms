package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameRepository
import com.example.engine.WorldEngine
import com.example.model.EnemyEntity
import com.example.model.PlayerStats
import com.example.model.RemotePlayer
import com.example.ui.components.ServerAccountDialog
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun WorldScreen(
    repository: GameRepository,
    modifier: Modifier = Modifier
) {
    val player by repository.player.collectAsState()
    val enemies by repository.enemies.collectAsState()
    val floatingTexts by repository.floatingTexts.collectAsState()
    val networkState by repository.networkState.collectAsState()
    val remotePlayers by repository.remotePlayers.collectAsState()

    var joystickOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedEnemyId by remember { mutableStateOf<String?>(null) }
    var slashEffectTimer by remember { mutableStateOf(0f) }
    var showZoneSelector by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var lastAttackAt by remember { mutableStateOf(0L) }
    var lastDodgeAt by remember { mutableStateOf(0L) }
    var hitFlash by remember { mutableStateOf(0f) }
    var comboCount by remember { mutableStateOf(0) }
    var lastHitAt by remember { mutableStateOf(0L) }
    var jumpTimer by remember { mutableStateOf(0f) }
    var showStory by remember { mutableStateOf(false) }
    var storyStep by remember { mutableStateOf(0) }

    // Real-time game loop ticker (~30 FPS)
    LaunchedEffect(Unit) {
        while (true) {
            delay(33)
            // Move player if joystick active
            if (joystickOffset.getDistance() > 10f) {
                val speed = (player.agility * 0.4f + 4.5f)
                val len = joystickOffset.getDistance()
                val nx = (joystickOffset.x / len) * speed
                val ny = (joystickOffset.y / len) * speed
                val newX = (player.posX + nx).coerceIn(-280f, 280f)
                val newY = (player.posY + ny).coerceIn(-280f, 280f)
                repository.updatePlayerPosition(newX, newY)
            }

            // Update enemy AI in world when offline
            if (!networkState.isAuthenticated) {
                WorldEngine.updateEnemyAI(enemies, player.posX, player.posY, 0.033f)
            }

            // Prune floating combat numbers
            repository.pruneFloatingTexts()

            if (slashEffectTimer > 0f) {
                slashEffectTimer = (slashEffectTimer - 0.1f).coerceAtLeast(0f)
            }
            if (hitFlash > 0f) {
                hitFlash = (hitFlash - 0.08f).coerceAtLeast(0f)
            }
            if (jumpTimer > 0f) {
                jumpTimer = (jumpTimer - 0.06f).coerceAtLeast(0f)
            }
            val now = System.currentTimeMillis()
            if (now - lastHitAt > 1600L && comboCount != 0) comboCount = 0
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
    ) {
        val screenCenterX = constraints.maxWidth / 2
        val screenCenterY = constraints.maxHeight / 2

        // 1. The World Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            // A short touch on the world selects the nearest enemy.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val worldX = player.posX + (start.x - center.x)
                            val worldY = player.posY + (start.y - center.y)
                            selectedEnemyId = enemies
                                .filter { !it.isDead }
                                .minByOrNull {
                                    val dx = it.posX - worldX
                                    val dy = it.posY - worldY
                                    dx * dx + dy * dy
                                }
                                ?.takeIf {
                                    val dx = it.posX - worldX
                                    val dy = it.posY - worldY
                                    dx * dx + dy * dy <= 80f * 80f
                                }?.id
                        },
                        onDragEnd = { joystickOffset = Offset.Zero },
                        onDragCancel = { joystickOffset = Offset.Zero },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val newX = (joystickOffset.x + dragAmount.x).coerceIn(-75f, 75f)
                            val newY = (joystickOffset.y + dragAmount.y).coerceIn(-75f, 75f)
                            joystickOffset = Offset(newX, newY)
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height * 0.58f)

            // V5: third-person-like mobile framing. The avatar stays near the lower
            // center while the world scrolls around it, matching the feel of
            // character-driven Roblox adventure games without copying their IP.
            // V4 visual direction: colorful, clean, blocky low-poly fantasy.
            // Gameplay remains the same: top-down RPG combat, quests and multiplayer.
            val grass = Color(0xFF79B84A)
            val grassLight = Color(0xFF8DCC59)
            val grassDark = Color(0xFF5B963B)
            val path = Color(0xFFC9A86A)
            val sky = Color(0xFF9BD8FF)
            drawRect(color = sky, size = size)
            drawRect(color = grass, topLeft = Offset(0f, size.height * 0.18f), size = Size(size.width, size.height * 0.82f))

            // Large simple terrain tiles.
            val tile = 56f
            val sx = ((center.x - player.posX) % tile) - tile
            val sy = ((center.y - player.posY) % tile) - tile
            var tx = sx
            while (tx < size.width + tile) {
                var ty = sy
                while (ty < size.height + tile) {
                    val worldX = tx - center.x + player.posX
                    val worldY = ty - center.y + player.posY
                    val checker = (floor(worldX / tile) + floor(worldY / tile)).toInt() and 1
                    drawRect(
                        color = if (checker == 0) grass else grassLight,
                        topLeft = Offset(tx, ty.coerceAtLeast(size.height * 0.18f)),
                        size = Size(tile + 1f, tile + 1f)
                    )
                    ty += tile
                }
                tx += tile
            }

            // A readable village/path strip gives the world a Roblox-like toy-map feel.
            val pathY = center.y - player.posY * 0.35f
            drawRect(color = path, topLeft = Offset(0f, pathY - 34f), size = Size(size.width, 68f))
            var stoneX = ((center.x - player.posX) % 46f) - 46f
            while (stoneX < size.width) {
                drawRoundRect(
                    color = Color(0xFFD9BD82),
                    topLeft = Offset(stoneX + 5f, pathY - 7f),
                    size = Size(34f, 14f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
                )
                stoneX += 46f
            }

            // Decorative blocky trees and rocks. They are visual only.
            val decorSeed = floor(player.posX / 100f) + floor(player.posY / 100f)
            for (i in 0 until 14) {
                val dx = ((i * 137 + decorSeed.toInt() * 31) % size.width.toInt()).toFloat()
                val dy = (size.height * 0.24f + ((i * 83) % (size.height.toInt().coerceAtLeast(1) / 2))).toFloat()
                drawRect(color = Color(0xFF8B5A32), topLeft = Offset(dx - 4f, dy + 12f), size = Size(8f, 22f))
                drawCircle(color = Color(0xFF3F8F3A), radius = 19f, center = Offset(dx, dy))
                drawCircle(color = Color(0xFF5EAD45), radius = 13f, center = Offset(dx - 5f, dy - 6f))
            }

            // World border is a subtle map edge instead of a neon sci-fi ring.
            val boundaryRadius = 320f
            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = boundaryRadius,
                center = Offset(center.x - player.posX, center.y - player.posY),
                style = Stroke(width = 4f)
            )

            // Draw Enemies
            for (enemy in enemies) {
                if (enemy.isDead) continue

                val enemyScreenPos = Offset(
                    center.x + (enemy.posX - player.posX),
                    center.y + (enemy.posY - player.posY)
                )

                // Skip drawing if outside viewport
                if (enemyScreenPos.x < -40 || enemyScreenPos.x > size.width + 40 ||
                    enemyScreenPos.y < -40 || enemyScreenPos.y > size.height + 40) {
                    continue
                }

                // Soft aggro range; no neon sci-fi presentation.
                drawCircle(
                    color = Color(0xFFFF6B6B).copy(alpha = 0.035f),
                    radius = enemy.aggroRange,
                    center = enemyScreenPos
                )

                // Enemy body
                val enemyColor = when (enemy.definitionId) {
                    "abyssal_lord" -> Color(0xFF7A3E9D)
                    "astral_golem" -> Color(0xFF7A7F86)
                    "rift_stalker" -> Color(0xFF3D79D8)
                    else -> Color(0xFFD9534F)
                }
                val enemyRadius = if (enemy.definitionId == "abyssal_lord") 24f else 17f

                // Blocky toy-like enemy silhouette.
                if (selectedEnemyId == enemy.id) {
                    drawRoundRect(
                        color = Color(0xFFFFD54F),
                        topLeft = Offset(enemyScreenPos.x - enemyRadius - 6f, enemyScreenPos.y - enemyRadius - 6f),
                        size = Size((enemyRadius + 6f) * 2f, (enemyRadius + 6f) * 2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f),
                        style = Stroke(width = 3f)
                    )
                }
                drawRoundRect(
                    color = enemyColor,
                    topLeft = Offset(enemyScreenPos.x - enemyRadius, enemyScreenPos.y - enemyRadius),
                    size = Size(enemyRadius * 2f, enemyRadius * 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
                )
                drawRect(
                    color = Color(0xFF20242A),
                    topLeft = Offset(enemyScreenPos.x - enemyRadius * 0.52f, enemyScreenPos.y - enemyRadius * 0.15f),
                    size = Size(enemyRadius * 0.22f, enemyRadius * 0.22f)
                )
                drawRect(
                    color = Color(0xFF20242A),
                    topLeft = Offset(enemyScreenPos.x + enemyRadius * 0.30f, enemyScreenPos.y - enemyRadius * 0.15f),
                    size = Size(enemyRadius * 0.22f, enemyRadius * 0.22f)
                )

                // Enemy HP Bar overhead
                val hpBarWidth = 40f
                val hpBarHeight = 5f
                val hpRatio = (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f)
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(enemyScreenPos.x - hpBarWidth / 2, enemyScreenPos.y - enemyRadius - 14f),
                    size = Size(hpBarWidth, hpBarHeight)
                )
                drawRect(
                    color = VoidCrimson,
                    topLeft = Offset(enemyScreenPos.x - hpBarWidth / 2, enemyScreenPos.y - enemyRadius - 14f),
                    size = Size(hpBarWidth * hpRatio, hpBarHeight)
                )
            }

            // Draw Remote Multiplayer Players
            for (remote in remotePlayers) {
                val remoteScreenPos = Offset(
                    center.x + (remote.posX - player.posX),
                    center.y + (remote.posY - player.posY)
                )

                // Remote aura
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(NeonCyan.copy(alpha = 0.4f), Color.Transparent),
                        center = remoteScreenPos,
                        radius = 28f
                    ),
                    radius = 28f,
                    center = remoteScreenPos
                )

                // Remote body
                drawCircle(
                    color = NeonCyan,
                    radius = 15f,
                    center = remoteScreenPos
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = remoteScreenPos
                )

                // Remote HP Bar
                val rBarWidth = 36f
                val rBarHeight = 4f
                val rRatio = (remote.hp.toFloat() / remote.maxHp.toFloat()).coerceIn(0f, 1f)
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(remoteScreenPos.x - rBarWidth / 2, remoteScreenPos.y - 22f),
                    size = Size(rBarWidth, rBarHeight)
                )
                drawRect(
                    color = NeonGreen,
                    topLeft = Offset(remoteScreenPos.x - rBarWidth / 2, remoteScreenPos.y - 22f),
                    size = Size(rBarWidth * rRatio, rBarHeight)
                )
            }

            // V5 player avatar: larger third-person block character with a shadow and
            // directional body tilt. This is a visual/control language, not Roblox code.
            val jumpLift = if (jumpTimer > 0f) sin((1f - jumpTimer) * PI).toFloat() * 34f else 0f
            val avatarY = center.y - jumpLift
            val faceX = when { joystickOffset.x > 12f -> 1f; joystickOffset.x < -12f -> -1f; else -> 0f }
            drawOval(
                color = Color.Black.copy(alpha = 0.28f),
                topLeft = Offset(center.x - 23f, center.y + 27f),
                size = Size(46f, 13f)
            )
            drawRoundRect(
                color = Color(0xFF4A90E2),
                topLeft = Offset(center.x - 18f, avatarY - 1f),
                size = Size(36f, 31f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
            drawRect(color = Color(0xFF2D5F9A), topLeft = Offset(center.x - 14f, avatarY + 29f), size = Size(10f, 17f))
            drawRect(color = Color(0xFF2D5F9A), topLeft = Offset(center.x + 4f, avatarY + 29f), size = Size(10f, 17f))
            drawRect(color = Color(0xFFF0C39B), topLeft = Offset(center.x - 15f, avatarY - 30f), size = Size(30f, 24f))
            drawRect(color = Color(0xFF3B2A20), topLeft = Offset(center.x - 15f, avatarY - 32f), size = Size(30f, 7f))
            val eyeOffset = if (faceX == 0f) 0f else faceX * 3f
            drawRect(color = Color(0xFF20242A), topLeft = Offset(center.x - 9f + eyeOffset, avatarY - 19f), size = Size(5f, 5f))
            drawRect(color = Color(0xFF20242A), topLeft = Offset(center.x + 4f + eyeOffset, avatarY - 19f), size = Size(5f, 5f))

            // Slash arc attack visual effect
            if (slashEffectTimer > 0f) {
                drawArc(
                    color = NeonCyan.copy(alpha = slashEffectTimer),
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - 35f, center.y - 35f),
                    size = Size(70f, 70f),
                    style = Stroke(width = 5f)
                )
            }
        }

        // 2. Combat HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hpRatio = (player.hp.toFloat() / player.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .height(9.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .border(1.dp, VoidOutline, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(hpRatio)
                        .background(VoidCrimson, RoundedCornerShape(8.dp))
                )
            }
            Text(
                text = "${player.hp} / ${player.maxHp} HP  •  LV ${player.level}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            val xpRequired = player.level * player.level * 100L
            val xpRatio = if (xpRequired > 0) (player.xp.toFloat() / xpRequired).coerceIn(0f, 1f) else 0f
            Box(modifier = Modifier.width(190.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(xpRatio).background(XpGreen))
            }
            Text(
                text = "${player.xp} / $xpRequired XP   •   ${player.gold} G",
                color = Color.LightGray,
                fontSize = 9.sp
            )

            if (comboCount > 1) {
                Text("COMBO x$comboCount", color = NeonYellow, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }

            selectedEnemyId?.let { id ->
                val target = enemies.firstOrNull { it.id == id && !it.isDead }
                if (target != null) {
                    val ratio = (target.hp.toFloat() / target.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Surface(
                        color = VoidDark.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, VoidCrimson.copy(alpha = 0.7f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${target.name}  •  LV ${target.level}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.width(150.dp).height(5.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(ratio).background(VoidCrimson))
                            }
                        }
                    }
                }
            }
        }

        // 3. Floating Combat Numbers & Remote Player Name Overlays
        Box(modifier = Modifier.fillMaxSize()) {
            floatingTexts.forEach { ft ->
                Text(
                    text = ft.text,
                    color = ft.color,
                    fontWeight = if (ft.isCrit) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = if (ft.isCrit) 18.sp else 14.sp,
                    modifier = Modifier.offset {
                        IntOffset(
                            (screenCenterX + (ft.x - player.posX)).toInt(),
                            (screenCenterY + (ft.y - player.posY)).toInt()
                        )
                    }
                )
            }

            // Remote Player Nametags
            remotePlayers.forEach { remote ->
                Text(
                    text = "${remote.name} (Lv${remote.level})",
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset {
                        IntOffset(
                            (screenCenterX + (remote.posX - player.posX) - 35).toInt(),
                            (screenCenterY + (remote.posY - player.posY) - 38).toInt()
                        )
                    }
                )
            }
        }

        // 4. Top Control Bar: Zone Fast Travel + Multiplayer Server Status
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showZoneSelector = true },
                colors = ButtonDefaults.buttonColors(containerColor = VoidSurface),
                border = BorderStroke(1.dp, VoidOutline),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("zone_select_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = "Zone",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = WorldEngine.ZONES.find { it.id == player.zoneId }?.name ?: "Realm",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Real Server Status Button
            Button(
                onClick = { showServerDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (networkState.isConnected) VoidSurfaceVariant else VoidDark
                ),
                border = BorderStroke(1.dp, if (networkState.isConnected) NeonGreen else VoidOutline),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("server_status_button")
            ) {
                Icon(
                    imageVector = if (networkState.isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = "Server Status",
                    tint = if (networkState.isConnected) NeonGreen else Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (networkState.isConnected) "Online (${remotePlayers.size + 1})" else "Connect",
                    color = if (networkState.isConnected) NeonGreen else Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // V5 main-story tracker: the world now has its own narrative identity.
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp).widthIn(max = 235.dp),
            color = Color.Black.copy(alpha = 0.62f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF6FA8FF).copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("VOID REALMS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Text("MAIN STORY", color = Color(0xFFFFD166), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (storyStep == 0) "Find the first Void Gate." else "The gate has awakened. Enter the Wastes.",
                    color = Color.White, fontSize = 10.sp
                )
            }
        }

        // 5. Minimap Radar (Top-Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f))
                .border(1.5.dp, Color(0xFF5D6875), CircleShape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rCenter = Offset(size.width / 2f, size.height / 2f)
                // Local player dot
                drawCircle(color = Color(0xFF2F80ED), radius = 3.5f, center = rCenter)

                // Remote players on radar
                for (remote in remotePlayers) {
                    val rx = rCenter.x + (remote.posX - player.posX) * 0.15f
                    val ry = rCenter.y + (remote.posY - player.posY) * 0.15f
                    if ((rx - rCenter.x).pow(2) + (ry - rCenter.y).pow(2) <= (size.width / 2f).pow(2)) {
                        drawCircle(color = NeonGreen, radius = 3f, center = Offset(rx, ry))
                    }
                }

                // Enemies on radar
                for (enemy in enemies) {
                    if (enemy.isDead) continue
                    val ex = rCenter.x + (enemy.posX - player.posX) * 0.15f
                    val ey = rCenter.y + (enemy.posY - player.posY) * 0.15f
                    if ((ex - rCenter.x).pow(2) + (ey - rCenter.y).pow(2) <= (size.width / 2f).pow(2)) {
                        drawCircle(color = VoidCrimson, radius = 2.5f, center = Offset(ex, ey))
                    }
                }
            }
        }

        // 6. Virtual Joystick (Bottom-Left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 28.dp)
                .size(110.dp)
                .clip(CircleShape)
                .background(VoidDark.copy(alpha = 0.6f))
                .border(1.5.dp, VoidOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(joystickOffset.x.toInt(), joystickOffset.y.toInt()) }
                    .size(45.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(AstralViolet, AstralVioletDark)
                        )
                    )
                    .border(1.dp, NeonCyan, CircleShape)
            )
        }

        // V5: jump + interact controls make movement feel like a character adventure game.
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 178.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    if (!player.isDead && jumpTimer <= 0f) jumpTimer = 1f
                },
                containerColor = VoidDark.copy(alpha = 0.92f),
                contentColor = Color.White,
                modifier = Modifier.size(50.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Jump")
            }
            FloatingActionButton(
                onClick = {
                    selectedEnemyId = enemies.filter { !it.isDead }.minByOrNull {
                        val dx = it.posX - player.posX; val dy = it.posY - player.posY
                        dx * dx + dy * dy
                    }?.id
                    showStory = true
                },
                containerColor = Color(0xFF2F6F52),
                contentColor = Color.White,
                modifier = Modifier.size(50.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Interact")
            }
        }

        // 7. Action Combat Buttons (Bottom-Right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quick Heal Potion button
            FloatingActionButton(
                onClick = { repository.usePotion() },
                containerColor = VoidDark,
                contentColor = NeonCyan,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("use_potion_button"),
                shape = CircleShape
            ) {
                Icon(Icons.Default.LocalHospital, contentDescription = "Drink Potion", tint = NeonCyan)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FloatingActionButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (!player.isDead && now - lastDodgeAt >= 1800L) {
                            lastDodgeAt = now
                            val dx = if (joystickOffset.getDistance() > 10f) joystickOffset.x else 0f
                            val dy = if (joystickOffset.getDistance() > 10f) joystickOffset.y else -1f
                            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                            val dash = 42f
                            repository.updatePlayerPosition(
                                (player.posX + dx / len * dash).coerceIn(-280f, 280f),
                                (player.posY + dy / len * dash).coerceIn(-280f, 280f)
                            )
                        }
                    },
                    containerColor = VoidSurface,
                    contentColor = NeonGreen,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = "Dodge", tint = NeonGreen)
                }

                // Ranged Void Blast Spell Button
                FloatingActionButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastAttackAt >= 1200L) {
                            val target = enemies.firstOrNull { it.id == selectedEnemyId && !it.isDead }
                                ?: enemies.filter { !it.isDead }.minByOrNull {
                                    val dx = it.posX - player.posX
                                    val dy = it.posY - player.posY
                                    dx * dx + dy * dy
                                }
                            if (target != null) {
                                selectedEnemyId = target.id
                                lastAttackAt = now
                                repository.attackTargetEnemy(target.id, "VOID_BLAST")
                            }
                        }
                    },
                    containerColor = VoidSurfaceVariant,
                    contentColor = NeonCyan,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("spell_blast_button"),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = "Void Blast", tint = NeonCyan)
                }

                // Primary Melee Slash Attack Button
                FloatingActionButton(
                    onClick = {
                        slashEffectTimer = 1f
                        val now = System.currentTimeMillis()
                        if (now - lastAttackAt >= 600L) {
                            val target = enemies.firstOrNull { it.id == selectedEnemyId && !it.isDead }
                                ?: enemies.filter { !it.isDead }.minByOrNull {
                                    val dx = it.posX - player.posX
                                    val dy = it.posY - player.posY
                                    dx * dx + dy * dy
                                }
                            if (target != null) {
                                selectedEnemyId = target.id
                                lastAttackAt = now
                                if (now - lastHitAt <= 1600L) comboCount++ else comboCount = 1
                                lastHitAt = now
                                slashEffectTimer = 1f
                                repository.attackTargetEnemy(target.id, "MELEE")
                            }
                        }
                    },
                    containerColor = AstralVioletDark,
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("attack_button"),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.SportsKabaddi,
                        contentDescription = "Melee Attack",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }

    // V3 damage vignette
    if (hitFlash > 0f) {
        Box(modifier = Modifier.fillMaxSize().background(VoidCrimson.copy(alpha = hitFlash * 0.18f)))
    }

    // Death state is visually explicit instead of silently leaving the player frozen.
    if (player.isDead) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("YOU DIED", color = Color(0xFFD9534F), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("The Void claims the fallen.", color = Color.LightGray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Respawning...", color = NeonCyan, fontSize = 11.sp)
            }
        }
    }

    if (showStory) {
        AlertDialog(
            onDismissRequest = { showStory = false },
            title = { Text("The First Void Gate", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (storyStep == 0)
                        "Something beneath Astral Sanctuary is calling you. The Void Gate has appeared, but only a marked fighter can awaken it."
                    else
                        "The gate is awake. Beyond it lies the Void Wastes, where the first fragment of your true story waits.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(onClick = { storyStep = 1; showStory = false }) {
                    Text("Continue", color = NeonCyan)
                }
            },
            containerColor = VoidDark
        )
    }

    // Zone Selection Dialog
    if (showZoneSelector) {
        AlertDialog(
            onDismissRequest = { showZoneSelector = false },
            title = { Text("Fast Travel Obelisk", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorldEngine.ZONES.forEach { zone ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repository.changeZone(zone.id)
                                    showZoneSelector = false
                                },
                            colors = CardDefaults.cardColors(containerColor = VoidSurfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(zone.name, color = NeonCyan, fontWeight = FontWeight.Bold)
                                Text(zone.description, color = Color.Gray, fontSize = 11.sp)
                                Text("Min Level: ${zone.minLevel}", color = VoidGold, fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZoneSelector = false }) {
                    Text("Close", color = NeonCyan)
                }
            },
            containerColor = VoidDark
        )
    }

    // Server Account & Connection Dialog
    if (showServerDialog) {
        ServerAccountDialog(
            repository = repository,
            onDismiss = { showServerDialog = false }
        )
    }
}
