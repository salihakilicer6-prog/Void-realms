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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameRepository
import com.example.engine.WorldEngine
import com.example.model.*
import com.example.ui.components.ServerAccountDialog
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * 3D Point projection helper for third-person perspective rendering.
 */
private data class ProjectedPoint(val x: Float, val y: Float, val scale: Float, val zDepth: Float)

private fun project3D(
    wx: Float, wy: Float, wz: Float,
    camX: Float, camY: Float, camZ: Float,
    pitchRad: Float,
    screenWidth: Float, screenHeight: Float
): ProjectedPoint {
    val dx = wx - camX
    val dy = wy - camY
    val dz = wz - camZ

    // Rotate around X-axis for pitch elevation
    val cosP = cos(pitchRad)
    val sinP = sin(pitchRad)

    val rotY = dy * cosP - dz * sinP
    val rotZ = dy * sinP + dz * cosP

    val depth = max(rotY, 10f)
    val focal = 380f
    val scale = focal / depth

    val projX = screenWidth / 2f + dx * scale
    val projY = screenHeight * 0.52f - rotZ * scale

    return ProjectedPoint(projX, projY, scale, depth)
}

@Composable
fun WorldScreen(
    repository: GameRepository,
    modifier: Modifier = Modifier
) {
    val player by repository.player.collectAsState()
    val enemies by repository.enemies.collectAsState()
    val npcs by repository.npcs.collectAsState()
    val worldItems by repository.worldItems.collectAsState()
    val floatingTexts by repository.floatingTexts.collectAsState()
    val networkState by repository.networkState.collectAsState()
    val remotePlayers by repository.remotePlayers.collectAsState()

    var joystickOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedEnemyId by remember { mutableStateOf<String?>(null) }
    var selectedNpc by remember { mutableStateOf<NpcEntity?>(null) }
    var nearbyItemDrop by remember { mutableStateOf<WorldItemDrop?>(null) }
    var slashEffectTimer by remember { mutableStateOf(0f) }
    var showZoneSelector by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var lastAttackAt by remember { mutableStateOf(0L) }
    var lastDodgeAt by remember { mutableStateOf(0L) }
    var hitFlash by remember { mutableStateOf(0f) }
    var comboCount by remember { mutableStateOf(0) }
    var lastHitAt by remember { mutableStateOf(0L) }
    var jumpTimer by remember { mutableStateOf(0f) }
    var walkAnimTimer by remember { mutableStateOf(0f) }
    var cameraPitch by remember { mutableStateOf(0.85f) } // Pitch angle

    // Real-time game loop ticker (~30 FPS)
    LaunchedEffect(Unit) {
        while (true) {
            delay(33)
            walkAnimTimer += 0.18f

            // Move player smoothly with joystick
            if (joystickOffset.getDistance() > 8f) {
                val speed = (player.agility * 0.38f + 4.2f)
                val len = joystickOffset.getDistance()
                val nx = (joystickOffset.x / len) * speed
                val ny = (joystickOffset.y / len) * speed

                val (nextX, nextY) = WorldEngine.checkMovementCollision(
                    player.posX, player.posY,
                    player.posX + nx, player.posY + ny
                )
                repository.updatePlayerPosition(nextX, nextY)
            }

            // Update enemy AI in world when offline
            if (!networkState.isAuthenticated) {
                WorldEngine.updateEnemyAI(enemies, player.posX, player.posY, 0.033f)
            }

            // Check nearby items for pickup prompt
            nearbyItemDrop = worldItems.firstOrNull { item ->
                !item.isPickedUp && hypot(item.posX - player.posX, item.posY - player.posY) < 45f
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
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val screenCenterX = screenWidth / 2f
        val screenCenterY = screenHeight / 2f

        val currentZone = WorldEngine.ZONES.find { it.id == player.zoneId } ?: WorldEngine.ZONES[0]

        // 1. 3D World Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            // Touch select enemy or NPC
                            val worldX = player.posX + (start.x - screenCenterX) * 0.8f
                            val worldY = player.posY + (start.y - screenCenterY) * 0.8f

                            // Priority 1: Enemy selection
                            selectedEnemyId = enemies
                                .filter { !it.isDead }
                                .minByOrNull { hypot(it.posX - worldX, it.posY - worldY) }
                                ?.takeIf { hypot(it.posX - worldX, it.posY - worldY) <= 80f }?.id

                            // Priority 2: NPC dialogue trigger
                            if (selectedEnemyId == null) {
                                val npcNear = npcs.minByOrNull { hypot(it.posX - player.posX, it.posY - player.posY) }
                                if (npcNear != null && hypot(npcNear.posX - player.posX, npcNear.posY - player.posY) <= 65f) {
                                    selectedNpc = npcNear
                                }
                            }
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
            val playerZ = if (jumpTimer > 0f) sin((1f - jumpTimer) * PI).toFloat() * 28f else 0f
            val camX = player.posX
            val camY = player.posY - 140f
            val camZ = 120f + playerZ

            // Atmospheric Sky Gradient
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        currentZone.ambientColor,
                        Color(0xFF1E293B),
                        Color(0xFF334155)
                    )
                ),
                size = size
            )

            // Horizon line projection
            val horizonProj = project3D(camX, camY + 800f, 0f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
            val horizonY = horizonProj.y.coerceIn(0f, screenHeight * 0.4f)

            // 3D Terrain Elevation Mesh & Ground Tiles
            val tileSize = 40f
            val gridRadius = 10
            val startTileX = (floor(player.posX / tileSize) - gridRadius).toInt()
            val endTileX = (floor(player.posX / tileSize) + gridRadius).toInt()
            val startTileY = (floor(player.posY / tileSize) - gridRadius).toInt()
            val endTileY = (floor(player.posY / tileSize) + gridRadius).toInt()

            for (ty in startTileY..endTileY) {
                for (tx in startTileX..endTileX) {
                    val wx1 = tx * tileSize
                    val wy1 = ty * tileSize
                    val wz1 = WorldEngine.getTerrainHeight(wx1, wy1)

                    val wx2 = wx1 + tileSize
                    val wy2 = wy1 + tileSize
                    val wz2 = WorldEngine.getTerrainHeight(wx2, wy2)

                    val p1 = project3D(wx1, wy1, wz1, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                    val p2 = project3D(wx2, wy1, wz1, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                    val p3 = project3D(wx2, wy2, wz2, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                    val p4 = project3D(wx1, wy2, wz2, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)

                    if (p1.zDepth > 0 && p2.zDepth > 0 && p3.zDepth > 0 && p4.zDepth > 0) {
                        val pathStyle = (abs(wx1) < 40f && abs(wy1) < 160f)
                        val isMountain = (wz1 > 8f)
                        val tileColor = when {
                            pathStyle -> Color(0xFFD97706)
                            isMountain -> Color(0xFF64748B)
                            (tx + ty) % 2 == 0 -> Color(0xFF15803D)
                            else -> Color(0xFF166534)
                        }

                        val poly = Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                            lineTo(p3.x, p3.y)
                            lineTo(p4.x, p4.y)
                            close()
                        }
                        drawPath(poly, color = tileColor)
                        drawPath(poly, color = tileColor.copy(alpha = 0.3f), style = Stroke(width = 1f))
                    }
                }
            }

            // 3D Ancient Ruins & Obelisks
            val ruinLocations = listOf(
                Pair(-80f, -80f), Pair(90f, -90f), Pair(-100f, 100f), Pair(110f, 80f)
            )
            for ((rx, ry) in ruinLocations) {
                val rHeight = 35f
                val baseP = project3D(rx, ry, 0f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                val topP = project3D(rx, ry, rHeight, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)

                if (baseP.zDepth > 0 && topP.zDepth > 0) {
                    val w = 14f * baseP.scale
                    val h = (baseP.y - topP.y)
                    drawRect(
                        color = Color(0xFF475569),
                        topLeft = Offset(baseP.x - w / 2, topP.y),
                        size = Size(w, h)
                    )
                    drawRect(
                        color = Color(0xFF94A3B8),
                        topLeft = Offset(baseP.x - w / 2, topP.y),
                        size = Size(w * 0.35f, h)
                    )
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.75f),
                        radius = 6f * topP.scale,
                        center = Offset(baseP.x, topP.y - 4f)
                    )
                }
            }

            // 3D Trees with Layered Canopies
            val treeLocations = listOf(
                Pair(-160f, -40f), Pair(-180f, 60f), Pair(150f, -50f), Pair(170f, 70f),
                Pair(-50f, -180f), Pair(60f, -170f), Pair(-70f, 180f), Pair(80f, 170f)
            )
            for ((tx, ty) in treeLocations) {
                val trunkP = project3D(tx, ty, 0f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                val topP = project3D(tx, ty, 42f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)

                if (trunkP.zDepth > 0 && topP.zDepth > 0) {
                    val tw = 8f * trunkP.scale
                    val th = (trunkP.y - topP.y)
                    drawRect(
                        color = Color(0xFF78350F),
                        topLeft = Offset(trunkP.x - tw / 2, topP.y + th * 0.3f),
                        size = Size(tw, th * 0.7f)
                    )
                    drawCircle(
                        color = Color(0xFF14532D),
                        radius = 22f * topP.scale,
                        center = Offset(trunkP.x, topP.y + th * 0.2f)
                    )
                    drawCircle(
                        color = Color(0xFF16A34A),
                        radius = 16f * topP.scale,
                        center = Offset(trunkP.x - 3f, topP.y + th * 0.1f)
                    )
                }
            }

            // 3D World Item Drops
            for (drop in worldItems) {
                if (drop.isPickedUp) continue

                val itemP = project3D(drop.posX, drop.posY, 8f + sin(walkAnimTimer * 2f) * 3f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                if (itemP.zDepth > 0) {
                    val itemSize = 14f * itemP.scale

                    drawCircle(
                        color = drop.item.rarity.color.copy(alpha = 0.35f),
                        radius = itemSize * 1.6f,
                        center = Offset(itemP.x, itemP.y)
                    )
                    drawRoundRect(
                        color = drop.item.rarity.color,
                        topLeft = Offset(itemP.x - itemSize / 2, itemP.y - itemSize / 2),
                        size = Size(itemSize, itemSize),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = itemSize * 0.25f,
                        center = Offset(itemP.x, itemP.y)
                    )
                }
            }

            // 3D Humanoid NPCs
            for (npc in npcs) {
                val npcP = project3D(npc.posX, npc.posY, 0f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                if (npcP.zDepth > 0) {
                    drawHumanoid3D(
                        drawScope = this,
                        proj = npcP,
                        bodyColor = npc.outfitColor,
                        headColor = npc.primaryColor,
                        walkPhase = 0f,
                        isMoving = false
                    )
                }
            }

            // 3D Mobs / Enemies
            for (enemy in enemies) {
                if (enemy.isDead) continue

                val enemyP = project3D(enemy.posX, enemy.posY, 0f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                if (enemyP.zDepth > 0) {
                    val mobColor = when (enemy.definitionId) {
                        "abyssal_lord" -> Color(0xFF7C3AED)
                        "astral_golem" -> Color(0xFF475569)
                        "rift_stalker" -> Color(0xFF2563EB)
                        else -> Color(0xFFDC2626)
                    }

                    if (selectedEnemyId == enemy.id) {
                        drawCircle(
                            color = VoidGold,
                            radius = 24f * enemyP.scale,
                            center = Offset(enemyP.x, enemyP.y),
                            style = Stroke(width = 3f)
                        )
                    }

                    drawHumanoid3D(
                        drawScope = this,
                        proj = enemyP,
                        bodyColor = mobColor,
                        headColor = Color(0xFF1E293B),
                        walkPhase = walkAnimTimer,
                        isMoving = true
                    )

                    val hpRatio = (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f)
                    val barW = 34f * enemyP.scale
                    val barH = 5f * enemyP.scale
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(enemyP.x - barW / 2, enemyP.y - 45f * enemyP.scale),
                        size = Size(barW, barH)
                    )
                    drawRect(
                        color = VoidCrimson,
                        topLeft = Offset(enemyP.x - barW / 2, enemyP.y - 45f * enemyP.scale),
                        size = Size(barW * hpRatio, barH)
                    )
                }
            }

            // 3D Remote Multiplayer Players
            for (remote in remotePlayers) {
                val remoteP = project3D(remote.posX, remote.posY, 0f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                if (remoteP.zDepth > 0) {
                    drawHumanoid3D(
                        drawScope = this,
                        proj = remoteP,
                        bodyColor = NeonCyan,
                        headColor = Color.White,
                        walkPhase = walkAnimTimer,
                        isMoving = true
                    )
                }
            }

            // 3D Local Player Avatar (Humanoid Roblox Style)
            val isPlayerMoving = joystickOffset.getDistance() > 8f
            val playerP = project3D(player.posX, player.posY, playerZ, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)

            if (playerP.zDepth > 0) {
                // Shadow
                drawOval(
                    color = Color.Black.copy(alpha = 0.35f),
                    topLeft = Offset(playerP.x - 22f * playerP.scale, playerP.y + 2f),
                    size = Size(44f * playerP.scale, 14f * playerP.scale)
                )

                // Humanoid Roblox Avatar
                drawHumanoid3D(
                    drawScope = this,
                    proj = playerP,
                    bodyColor = Color(0xFF2563EB),
                    headColor = Color(0xFFFDE047),
                    walkPhase = if (isPlayerMoving) walkAnimTimer else 0f,
                    isMoving = isPlayerMoving
                )

                // Slash Attack Arc
                if (slashEffectTimer > 0f) {
                    drawArc(
                        color = NeonCyan.copy(alpha = slashEffectTimer),
                        startAngle = -60f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(playerP.x - 45f * playerP.scale, playerP.y - 45f * playerP.scale),
                        size = Size(90f * playerP.scale, 90f * playerP.scale),
                        style = Stroke(width = 6f)
                    )
                }
            }
        }

        // 2. Overlays: NPC Nametags, World Item Names & Remote Player Nametags
        Box(modifier = Modifier.fillMaxSize()) {
            val camX = player.posX
            val camY = player.posY - 140f
            val camZ = 120f

            // Floating Combat Numbers
            floatingTexts.forEach { ft ->
                val ftP = project3D(ft.x, ft.y, 10f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                if (ftP.zDepth > 0) {
                    Text(
                        text = ft.text,
                        color = ft.color,
                        fontWeight = if (ft.isCrit) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = if (ft.isCrit) 18.sp else 14.sp,
                        modifier = Modifier.offset {
                            IntOffset(ftP.x.toInt(), ftP.y.toInt())
                        }
                    )
                }
            }

            // NPC Names and Roles Overhead
            npcs.forEach { npc ->
                val npcP = project3D(npc.posX, npc.posY, 40f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                if (npcP.zDepth > 0) {
                    val dist = hypot(npc.posX - player.posX, npc.posY - player.posY)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset { IntOffset((npcP.x - 50f).toInt(), (npcP.y - 40f).toInt()) }
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, npc.primaryColor)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(npc.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(npc.title, color = npc.primaryColor, fontSize = 8.sp)
                            }
                        }
                        if (dist <= 65f) {
                            Text("Tap [Interact]", color = NeonYellow, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // World Item Drops Overhead
            worldItems.forEach { drop ->
                if (!drop.isPickedUp) {
                    val dropP = project3D(drop.posX, drop.posY, 20f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                    if (dropP.zDepth > 0) {
                        Text(
                            text = drop.item.name,
                            color = drop.item.rarity.color,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset { IntOffset((dropP.x - 30f).toInt(), (dropP.y - 20f).toInt()) }
                        )
                    }
                }
            }

            // Remote Players
            remotePlayers.forEach { remote ->
                val remP = project3D(remote.posX, remote.posY, 40f, camX, camY, camZ, cameraPitch, screenWidth, screenHeight)
                if (remP.zDepth > 0) {
                    Text(
                        text = "${remote.name} (Lv${remote.level})",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset { IntOffset((remP.x - 35f).toInt(), (remP.y - 30f).toInt()) }
                    )
                }
            }
        }

        // 3. Combat HUD (Top Center)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hpRatio = (player.hp.toFloat() / player.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            val xpRequired = player.level * player.level * 100L
            val xpRatio = if (xpRequired > 0) (player.xp.toFloat() / xpRequired).coerceIn(0f, 1f) else 0f
            Box(modifier = Modifier.width(200.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
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
                        border = BorderStroke(1.dp, VoidCrimson.copy(alpha = 0.8f))
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

        // 4. Top Control Bar & World Objective Tracker
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
                    text = currentZone.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

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

        // 5. Minimap Radar (Top-Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.8f))
                .border(1.5.dp, VoidOutline, CircleShape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rCenter = Offset(size.width / 2f, size.height / 2f)
                drawCircle(color = NeonCyan, radius = 3.5f, center = rCenter)

                // NPCs
                for (npc in npcs) {
                    val rx = rCenter.x + (npc.posX - player.posX) * 0.18f
                    val ry = rCenter.y + (npc.posY - player.posY) * 0.18f
                    if (hypot(rx - rCenter.x, ry - rCenter.y) <= size.width / 2f) {
                        drawCircle(color = NeonYellow, radius = 3f, center = Offset(rx, ry))
                    }
                }

                // Enemies
                for (enemy in enemies) {
                    if (enemy.isDead) continue
                    val ex = rCenter.x + (enemy.posX - player.posX) * 0.18f
                    val ey = rCenter.y + (enemy.posY - player.posY) * 0.18f
                    if (hypot(ex - rCenter.x, ey - rCenter.y) <= size.width / 2f) {
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
                .background(VoidDark.copy(alpha = 0.65f))
                .border(1.5.dp, VoidOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(joystickOffset.x.toInt(), joystickOffset.y.toInt()) }
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(AstralViolet, AstralVioletDark)
                        )
                    )
                    .border(1.dp, NeonCyan, CircleShape)
            )
        }

        // 7. Mobile Gameplay Action Buttons (Jump, Interact, Attack, Spell, Dodge, Potion)
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 178.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    if (!player.isDead && jumpTimer <= 0f) jumpTimer = 1f
                },
                containerColor = VoidDark.copy(alpha = 0.9f),
                contentColor = Color.White,
                modifier = Modifier.size(50.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Jump")
            }

            // Universal Interaction Button (Talk to NPC or Pickup Item)
            FloatingActionButton(
                onClick = {
                    nearbyItemDrop?.let { drop ->
                        repository.pickupWorldItem(drop.id)
                    } ?: run {
                        val npcNear = npcs.minByOrNull { hypot(it.posX - player.posX, it.posY - player.posY) }
                        if (npcNear != null && hypot(npcNear.posX - player.posX, npcNear.posY - player.posY) <= 65f) {
                            selectedNpc = npcNear
                        }
                    }
                },
                containerColor = if (nearbyItemDrop != null) NeonYellow else Color(0xFF2F6F52),
                contentColor = Color.White,
                modifier = Modifier.size(50.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (nearbyItemDrop != null) Icons.Default.Backpack else Icons.Default.ChatBubbleOutline,
                    contentDescription = "Interact"
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                            val dash = 45f
                            val (nx, ny) = WorldEngine.checkMovementCollision(
                                player.posX, player.posY,
                                player.posX + dx / len * dash, player.posY + dy / len * dash
                            )
                            repository.updatePlayerPosition(nx, ny)
                        }
                    },
                    containerColor = VoidSurface,
                    contentColor = NeonGreen,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = "Dodge", tint = NeonGreen)
                }

                FloatingActionButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastAttackAt >= 1200L) {
                            val target = enemies.firstOrNull { it.id == selectedEnemyId && !it.isDead }
                                ?: enemies.filter { !it.isDead }.minByOrNull { hypot(it.posX - player.posX, it.posY - player.posY) }
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

                FloatingActionButton(
                    onClick = {
                        slashEffectTimer = 1f
                        val now = System.currentTimeMillis()
                        if (now - lastAttackAt >= 600L) {
                            val target = enemies.firstOrNull { it.id == selectedEnemyId && !it.isDead }
                                ?: enemies.filter { !it.isDead }.minByOrNull { hypot(it.posX - player.posX, it.posY - player.posY) }
                            if (target != null) {
                                selectedEnemyId = target.id
                                lastAttackAt = now
                                if (now - lastHitAt <= 1600L) comboCount++ else comboCount = 1
                                lastHitAt = now
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

    // NPC Dialogue Popup
    selectedNpc?.let { npc ->
        AlertDialog(
            onDismissRequest = { selectedNpc = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = npc.primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(npc.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(npc.title, color = npc.primaryColor, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Text(npc.dialogue, color = Color.LightGray, fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(onClick = { selectedNpc = null }) {
                    Text("Close", color = NeonCyan)
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

/**
 * Renders a 3D Roblox-style blocky humanoid character model (Head, Torso, Arms, Legs).
 */
private fun drawHumanoid3D(
    drawScope: DrawScope,
    proj: ProjectedPoint,
    bodyColor: Color,
    headColor: Color,
    walkPhase: Float,
    isMoving: Boolean
) {
    val scale = proj.scale
    val headSize = 14f * scale
    val torsoWidth = 16f * scale
    val torsoHeight = 22f * scale
    val limbWidth = 6f * scale
    val limbHeight = 18f * scale

    val armAngle = if (isMoving) sin(walkPhase) * 14f else 0f
    val legAngle = if (isMoving) sin(walkPhase) * 18f else 0f

    val cx = proj.x
    val cy = proj.y - 20f * scale

    // Left & Right Legs
    drawScope.drawRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(cx - torsoWidth / 2 + 1f, cy + torsoHeight / 2 + legAngle * 0.3f),
        size = Size(limbWidth, limbHeight)
    )
    drawScope.drawRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(cx + torsoWidth / 2 - limbWidth - 1f, cy + torsoHeight / 2 - legAngle * 0.3f),
        size = Size(limbWidth, limbHeight)
    )

    // Torso (Roblox block style)
    drawScope.drawRoundRect(
        color = bodyColor,
        topLeft = Offset(cx - torsoWidth / 2, cy - torsoHeight / 2),
        size = Size(torsoWidth, torsoHeight),
        cornerRadius = CornerRadius(3f * scale, 3f * scale)
    )

    // Arms
    drawScope.drawRect(
        color = bodyColor,
        topLeft = Offset(cx - torsoWidth / 2 - limbWidth - 1f, cy - torsoHeight / 2 + armAngle * 0.2f),
        size = Size(limbWidth, limbHeight * 0.85f)
    )
    drawScope.drawRect(
        color = bodyColor,
        topLeft = Offset(cx + torsoWidth / 2 + 1f, cy - torsoHeight / 2 - armAngle * 0.2f),
        size = Size(limbWidth, limbHeight * 0.85f)
    )

    // Head (Blocky style with face)
    val headY = cy - torsoHeight / 2 - headSize
    drawScope.drawRoundRect(
        color = headColor,
        topLeft = Offset(cx - headSize / 2, headY),
        size = Size(headSize, headSize),
        cornerRadius = CornerRadius(2f * scale, 2f * scale)
    )

    // Eyes
    drawScope.drawRect(
        color = Color.Black,
        topLeft = Offset(cx - headSize * 0.28f, headY + headSize * 0.35f),
        size = Size(2.5f * scale, 2.5f * scale)
    )
    drawScope.drawRect(
        color = Color.Black,
        topLeft = Offset(cx + headSize * 0.08f, headY + headSize * 0.35f),
        size = Size(2.5f * scale, 2.5f * scale)
    )
}
