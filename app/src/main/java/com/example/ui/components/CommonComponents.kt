package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun TopVitalsBar(
    player: PlayerStats,
    zoneName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = VoidSurface,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, VoidOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Header Row: Character Name, Level Badge, Zone & Currencies
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(AstralVioletDark, NeonCyanDark)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LV ${player.level}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = player.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    RoleBadge(role = player.role)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Gold
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = "Gold",
                        tint = VoidGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${player.gold}",
                        color = VoidGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    // Void Shards (Premium)
                    Icon(
                        imageVector = Icons.Default.Diamond,
                        contentDescription = "Void Shards",
                        tint = AstralShard,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${player.voidShards}",
                        color = AstralShard,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bars Row: HP, Mana, and XP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // HP Bar
                VitalsBar(
                    label = "HP",
                    current = player.hp,
                    max = player.maxHp,
                    fillColor = HealthRed,
                    bgColor = HealthRedBg,
                    modifier = Modifier.weight(1f)
                )

                // Mana Bar
                VitalsBar(
                    label = "MP",
                    current = player.mana,
                    max = player.maxMana,
                    fillColor = ManaBlue,
                    bgColor = ManaBlueBg,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // XP Bar
            val reqXp = player.level * player.level * 100L
            val xpProgress = (player.xp.toFloat() / reqXp.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "XP",
                    color = XpGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier.width(20.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(XpGreenBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(xpProgress)
                            .background(XpGreen)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(xpProgress * 100).toInt()}%",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun VitalsBar(
    label: String,
    current: Int,
    max: Int,
    fillColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    val progress = (current.toFloat() / max.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(1.dp, VoidOutline, RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(fillColor)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "$current / $max",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RoleBadge(role: UserRole) {
    val color = when (role) {
        UserRole.OWNER -> RarityOwner
        UserRole.ADMIN -> VoidGold
        UserRole.MODERATOR -> NeonCyan
        UserRole.PLAYER -> Color.Gray
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = role.displayName.uppercase(),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}

@Composable
fun RarityBadge(rarity: ItemRarity) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(rarity.color.copy(alpha = 0.2f))
            .border(1.dp, rarity.color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = rarity.displayName.uppercase(),
            color = rarity.color,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun ItemDetailDialog(
    item: GameItem,
    currentlyEquipped: GameItem?,
    quantity: Int = 1,
    isLocked: Boolean = false,
    canEquip: Boolean = true,
    isAlreadyEquipped: Boolean = false,
    onEquip: () -> Unit = {},
    onUnequip: () -> Unit = {},
    onSell: () -> Unit = {},
    onToggleLock: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface),
            border = BorderStroke(1.5.dp, item.rarity.color),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RarityBadge(rarity = item.rarity)
                    item.equipSlot?.let {
                        Text(
                            text = it.label,
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Requires Level ${item.requiredLevel}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = VoidOutline
                )

                // Stats breakdown
                if (item.damage > 0) {
                    StatRow(label = "Attack Damage", value = "+${item.damage}", color = VoidCrimson)
                }
                if (item.defense > 0) {
                    StatRow(label = "Armor Defense", value = "+${item.defense}", color = NeonCyan)
                }
                if (item.critBonus > 0f) {
                    StatRow(label = "Critical Strike", value = "+${item.critBonus}%", color = VoidGold)
                }
                if (item.strBonus > 0) {
                    StatRow(label = "Strength", value = "+${item.strBonus}", color = AstralViolet)
                }
                if (item.agiBonus > 0) {
                    StatRow(label = "Agility", value = "+${item.agiBonus}", color = AstralShard)
                }
                StatRow(label = "Element", value = item.element, color = Color.White)

                item.specialEffect?.let { effect ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(VoidBlack)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "★ $effect",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Comparison section if not already equipped and equipment slot exists
                if (!isAlreadyEquipped && item.equipSlot != null && currentlyEquipped != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "VS CURRENT EQUIPPED:",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val dmgDiff = item.damage - currentlyEquipped.damage
                    val defDiff = item.defense - currentlyEquipped.defense

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (item.itemType == ItemType.WEAPON) {
                            Text(
                                text = "DMG: ${if (dmgDiff >= 0) "+$dmgDiff" else "$dmgDiff"}",
                                color = if (dmgDiff >= 0) XpGreen else VoidCrimson,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (item.itemType == ItemType.ARMOR) {
                            Text(
                                text = "DEF: ${if (defDiff >= 0) "+$defDiff" else "$defDiff"}",
                                color = if (defDiff >= 0) XpGreen else VoidCrimson,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAlreadyEquipped) {
                        Button(
                            onClick = {
                                onUnequip()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VoidCrimson),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unequip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (canEquip && item.equipSlot != null) {
                        Button(
                            onClick = {
                                onEquip()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AstralVioletDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Equip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isAlreadyEquipped) {
                        OutlinedButton(
                            onClick = {
                                onSell()
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoidGold),
                            border = BorderStroke(1.dp, VoidGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sell (${item.baseValue * quantity}g)", fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = onToggleLock,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock",
                                tint = if (isLocked) VoidGold else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
        Text(text = value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
