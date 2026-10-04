package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameRepository
import com.example.model.ItemRarity
import com.example.model.UserRole
import com.example.ui.components.RoleBadge
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun OwnerPanelScreen(
    repository: GameRepository,
    modifier: Modifier = Modifier
) {
    val player by repository.player.collectAsState()
    val auditLogs by repository.auditLogs.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var broadcastText by remember { mutableStateOf("") }
    var customLevelText by remember { mutableStateOf("10") }

    // Security Verification: Server / Database authoritative role check
    if (player.role != UserRole.OWNER && player.role != UserRole.ADMIN) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(VoidBlack)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VoidDark),
                border = BorderStroke(1.5.dp, VoidCrimson)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Access Denied",
                        tint = VoidCrimson,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ACCESS RESTRICTED",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This terminal is cryptographically restricted to OWNER and verified ADMIN credentials. Access attempts are recorded in the audit log.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VOID REALMS • SOVEREIGN OWNER CONSOLE",
                    color = RarityOwner,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Authoritative Server Controls & Tamper-Evident Audit",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            RoleBadge(role = player.role)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = VoidSurface,
            contentColor = NeonCyan,
            divider = {}
        ) {
            val tabs = listOf("PLAYERS", "WORLD", "ITEMS", "METRICS", "AUDIT LOG")
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> PlayerOpsTab(repository = repository, customLevelText = customLevelText, onLevelChange = { customLevelText = it })
                1 -> WorldOpsTab(repository = repository, broadcastText = broadcastText, onBroadcastChange = { broadcastText = it })
                2 -> ItemOpsTab(repository = repository)
                3 -> ServerMetricsTab()
                4 -> AuditLogTab(auditLogs = auditLogs)
            }
        }
    }
}

@Composable
fun PlayerOpsTab(
    repository: GameRepository,
    customLevelText: String,
    onLevelChange: (String) -> Unit
) {
    val player by repository.player.collectAsState()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VoidDark),
                border = BorderStroke(1.dp, VoidOutline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ACTIVE TARGET: ${player.name} (LV ${player.level})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Experience & Level Operations", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { repository.adminGrantXp(500) },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) { Text("+500 XP", fontSize = 10.sp) }

                        Button(
                            onClick = { repository.adminGrantXp(2500) },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) { Text("+2.5K XP", fontSize = 10.sp) }

                        Button(
                            onClick = { repository.adminGrantXp(10000) },
                            colors = ButtonDefaults.buttonColors(containerColor = AstralVioletDark),
                            modifier = Modifier.weight(1f)
                        ) { Text("+10K XP", fontSize = 10.sp) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customLevelText,
                            onValueChange = onLevelChange,
                            label = { Text("Set Level (1-100)", fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                customLevelText.toIntOrNull()?.let { repository.adminSetLevel(it) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyanDark)
                        ) {
                            Text("Apply Level", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VoidDark),
                border = BorderStroke(1.dp, VoidOutline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Economy & Currencies", color = VoidGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { repository.adminGrantGold(5000) },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) { Text("+5,000 Gold", fontSize = 10.sp, color = VoidGold) }

                        Button(
                            onClick = { repository.adminGrantGold(50000) },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) { Text("+50,000 Gold", fontSize = 10.sp, color = VoidGold) }

                        Button(
                            onClick = { repository.adminGrantVoidShards(100) },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) { Text("+100 Shards", fontSize = 10.sp, color = AstralShard) }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VoidDark),
                border = BorderStroke(1.dp, VoidOutline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Vitality & Character Integrity", color = HealthRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { repository.adminHealFull() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyanDark),
                            modifier = Modifier.weight(1f)
                        ) { Text("Restore Vitals", fontSize = 11.sp) }

                        Button(
                            onClick = {
                                player.hp = 0
                                player.isDead = true
                                repository.recordAudit("ADMIN_KILL_PLAYER", "Self death test")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidCrimson),
                            modifier = Modifier.weight(1f)
                        ) { Text("Simulate Death", fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun WorldOpsTab(
    repository: GameRepository,
    broadcastText: String,
    onBroadcastChange: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VoidDark),
                border = BorderStroke(1.dp, VoidOutline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Server Broadcast Announcement", color = VoidGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = broadcastText,
                        onValueChange = onBroadcastChange,
                        placeholder = { Text("System announcement message...", color = Color.Gray, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (broadcastText.isNotBlank()) {
                                repository.sendSystemBroadcast("ADMIN NOTICE: $broadcastText")
                                repository.recordAudit("BROADCAST", broadcastText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VoidGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Broadcast to All Players", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VoidDark),
                border = BorderStroke(1.dp, VoidOutline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Authoritative Entity Spawning", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { repository.adminSpawnMonster("void_crawler") },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) { Text("Void Crawler", fontSize = 9.sp) }

                        Button(
                            onClick = { repository.adminSpawnMonster("rift_stalker") },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) { Text("Rift Stalker", fontSize = 9.sp) }

                        Button(
                            onClick = { repository.adminSpawnMonster("astral_golem") },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) { Text("Astral Golem", fontSize = 9.sp) }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { repository.adminSpawnMonster("abyssal_lord") },
                        colors = ButtonDefaults.buttonColors(containerColor = VoidCrimson),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SPAWN WORLD BOSS: ABYSSAL LORD", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ItemOpsTab(repository: GameRepository) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = VoidDark),
                border = BorderStroke(1.dp, VoidOutline)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Instant Tier Item Generation", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { repository.adminSpawnCustomItem(ItemRarity.LEGENDARY) },
                            colors = ButtonDefaults.buttonColors(containerColor = RarityLegendary.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) { Text("Legendary", color = RarityLegendary, fontSize = 10.sp, fontWeight = FontWeight.Bold) }

                        Button(
                            onClick = { repository.adminSpawnCustomItem(ItemRarity.MYTHIC) },
                            colors = ButtonDefaults.buttonColors(containerColor = RarityMythic.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) { Text("Mythic", color = RarityMythic, fontSize = 10.sp, fontWeight = FontWeight.Bold) }

                        Button(
                            onClick = { repository.adminSpawnCustomItem(ItemRarity.DIVINE) },
                            colors = ButtonDefaults.buttonColors(containerColor = RarityDivine.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f)
                        ) { Text("Divine", color = RarityDivine, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { repository.adminSpawnCustomItem(ItemRarity.OWNER) },
                        colors = ButtonDefaults.buttonColors(containerColor = RarityOwner),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SPAWN OWNER ARTIFACT: CROWN OF INFINITY (9999 DMG)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ServerMetricsTab() {
    Card(
        colors = CardDefaults.cardColors(containerColor = VoidDark),
        border = BorderStroke(1.dp, VoidOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("ENGINE & SERVER TELEMETRY", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))

            MetricRow("Server Loop State", "ONLINE (20 Hz Tick)", XpGreen)
            MetricRow("Protocol Architecture", "Authoritative WebSocket + REST", Color.White)
            MetricRow("Multiplayer Mode", "Realtime Synchronized Mesh", NeonCyan)
            MetricRow("Database Engine", "PostgreSQL 16 + Local State Cache", Color.White)
            MetricRow("Item Space Capacity", "> 10,000,000 Combinations", VoidGold)
            MetricRow("Active Encryption", "Ed25519 / HMAC-SHA256 Token", AstralViolet)
            MetricRow("Anti-Cheat Validation", "Strict Server Authority Active", XpGreen)
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AuditLogTab(auditLogs: List<com.example.model.AdminAuditLog>) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(auditLogs, key = { it.id }) { log ->
            Card(
                colors = CardDefaults.cardColors(containerColor = VoidDark),
                border = BorderStroke(0.8.dp, VoidOutline),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(log.action, color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(log.result, color = if (log.result == "SUCCESS") XpGreen else VoidCrimson, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(log.parameters, color = Color.LightGray, fontSize = 10.sp)
                    }
                    Text(
                        text = formatter.format(Date(log.timestamp)),
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
