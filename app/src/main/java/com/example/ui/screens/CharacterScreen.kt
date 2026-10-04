package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.model.EquipSlot
import com.example.model.GameItem
import com.example.ui.components.ItemDetailDialog
import com.example.ui.components.StatRow
import com.example.ui.theme.*

@Composable
fun CharacterScreen(
    repository: GameRepository,
    modifier: Modifier = Modifier
) {
    val player by repository.player.collectAsState()
    var selectedItemForDetail by remember { mutableStateOf<GameItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "CHARACTER EQUIPMENT & ATTRIBUTES",
            color = NeonCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 10 Equipment Slots (2 columns layout)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VoidDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoidOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "EQUIPPED GEAR",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val slots = EquipSlot.values()
                for (i in slots.indices step 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EquipmentSlotCard(
                            slot = slots[i],
                            item = player.equipment[slots[i]],
                            modifier = Modifier.weight(1f),
                            onClick = { item -> selectedItemForDetail = item }
                        )
                        if (i + 1 < slots.size) {
                            EquipmentSlotCard(
                                slot = slots[i + 1],
                                item = player.equipment[slots[i + 1]],
                                modifier = Modifier.weight(1f),
                                onClick = { item -> selectedItemForDetail = item }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Attributes Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VoidDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, VoidOutline),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "COMBAT ATTRIBUTES",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                StatRow(label = "Health Capacity", value = "${player.maxHp}", color = HealthRed)
                StatRow(label = "Energy / Mana", value = "${player.maxMana}", color = ManaBlue)
                HorizontalDivider(color = VoidOutline, modifier = Modifier.padding(vertical = 4.dp))
                StatRow(label = "Physical Strength", value = "${player.strength}", color = AstralViolet)
                StatRow(label = "Armor Defense", value = "${player.defense}", color = NeonCyan)
                StatRow(label = "Agility (Move/Attack)", value = "${player.agility}", color = AstralShard)
                StatRow(label = "Critical Strike Chance", value = "${player.critChance}%", color = VoidGold)
                StatRow(label = "Critical Strike Multiplier", value = "${player.critDamage}%", color = VoidCrimson)
            }
        }
    }

    selectedItemForDetail?.let { item ->
        ItemDetailDialog(
            item = item,
            currentlyEquipped = item,
            isAlreadyEquipped = true,
            onUnequip = {
                item.equipSlot?.let { repository.unequipItem(it) }
                selectedItemForDetail = null
            },
            onDismiss = { selectedItemForDetail = null }
        )
    }
}

@Composable
fun EquipmentSlotCard(
    slot: EquipSlot,
    item: GameItem?,
    modifier: Modifier = Modifier,
    onClick: (GameItem) -> Unit
) {
    val borderColor = item?.rarity?.color ?: VoidOutline
    val bgColor = if (item != null) item.rarity.color.copy(alpha = 0.12f) else VoidSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = item != null) { item?.let(onClick) }
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = slot.label.uppercase(),
                color = if (item != null) item.rarity.color else Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item?.name ?: "Empty Slot",
                color = if (item != null) Color.White else Color.DarkGray,
                fontSize = 11.sp,
                fontWeight = if (item != null) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
            if (item != null) {
                val statSummary = if (item.damage > 0) "+${item.damage} DMG" else "+${item.defense} DEF"
                Text(
                    text = statSummary,
                    color = NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
