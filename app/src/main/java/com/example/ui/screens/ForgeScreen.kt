package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameRepository
import com.example.engine.ProceduralItemEngine
import com.example.model.GameItem
import com.example.model.InventorySlot
import com.example.model.ItemRarity
import com.example.ui.components.RarityBadge
import com.example.ui.components.StatRow
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun ForgeScreen(
    repository: GameRepository,
    modifier: Modifier = Modifier
) {
    val player by repository.player.collectAsState()
    var targetLevel by remember { mutableFloatStateOf(player.level.toFloat()) }
    var selectedRarity by remember { mutableStateOf<ItemRarity?>(null) }
    var forgedItem by remember { mutableStateOf<GameItem?>(null) }
    var forgeMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "PROCEDURAL ITEM FORGE (1,000,000+ COMBINATIONS)",
            color = NeonCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Deterministic generator combining Base Items × Prefixes × Suffixes × Elements × Rarities × Level Scaling.",
            color = Color.Gray,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Level Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VoidDark),
            border = BorderStroke(1.dp, VoidOutline)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Target Item Level", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Level ${targetLevel.roundToInt()}", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = targetLevel,
                    onValueChange = { targetLevel = it },
                    valueRange = 1f..100f,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = AstralViolet)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Rarity Filter Chips
        Text("Forced Rarity Tier (Optional)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(null, ItemRarity.RARE, ItemRarity.EPIC, ItemRarity.LEGENDARY, ItemRarity.MYTHIC, ItemRarity.DIVINE).forEach { rarity ->
                val isSelected = selectedRarity == rarity
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedRarity = rarity },
                    label = { Text(rarity?.displayName ?: "RNG Roll", fontSize = 9.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = rarity?.color?.copy(alpha = 0.3f) ?: AstralVioletDark,
                        selectedLabelColor = rarity?.color ?: Color.White
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Forge Button
        Button(
            onClick = {
                val item = ProceduralItemEngine.generateItem(
                    level = targetLevel.roundToInt(),
                    forcedRarity = selectedRarity
                )
                forgedItem = item
                forgeMessage = "Successfully forged from procedural matrix!"
            },
            colors = ButtonDefaults.buttonColors(containerColor = AstralVioletDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Text("GENERATE PROCEDURAL ITEM", fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Generated Item Preview Card
        forgedItem?.let { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VoidSurface),
                border = BorderStroke(1.5.dp, item.rarity.color),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RarityBadge(rarity = item.rarity)
                        Text(
                            text = "Seed: #${item.seedHash.take(8)}",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.itemType} • Level ${item.requiredLevel} • ${item.element} Element",
                        color = NeonCyan,
                        fontSize = 11.sp
                    )

                    HorizontalDivider(color = VoidOutline, modifier = Modifier.padding(vertical = 8.dp))

                    if (item.damage > 0) StatRow("Base Damage", "+${item.damage}", VoidCrimson)
                    if (item.defense > 0) StatRow("Armor Defense", "+${item.defense}", NeonCyan)
                    if (item.critBonus > 0f) StatRow("Crit Bonus", "+${item.critBonus}%", VoidGold)
                    if (item.strBonus > 0) StatRow("Strength", "+${item.strBonus}", AstralViolet)
                    if (item.agiBonus > 0) StatRow("Agility", "+${item.agiBonus}", AstralShard)

                    item.specialEffect?.let { effect ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(VoidBlack)
                                .padding(8.dp)
                        ) {
                            Text("★ $effect", color = NeonCyan, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (player.inventory.size < 40) {
                                val occupied = player.inventory.map { it.slotIndex }.toSet()
                                var nextSlot = 0
                                while (occupied.contains(nextSlot)) nextSlot++
                                player.inventory.add(InventorySlot(nextSlot, item, 1))
                                forgeMessage = "Item placed in player inventory!"
                            } else {
                                forgeMessage = "Inventory is full!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyanDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add to Player Inventory", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        forgeMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = msg, color = VoidGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
