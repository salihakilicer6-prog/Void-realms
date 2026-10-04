package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameRepository
import com.example.model.GameItem
import com.example.model.InventorySlot
import com.example.model.ItemType
import com.example.ui.components.ItemDetailDialog
import com.example.ui.theme.*

@Composable
fun InventoryScreen(
    repository: GameRepository,
    modifier: Modifier = Modifier
) {
    val player by repository.player.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedSlotForDetail by remember { mutableStateOf<InventorySlot?>(null) }

    val filteredItems = remember(player.inventory, selectedFilter) {
        when (selectedFilter) {
            "WEAPON" -> player.inventory.filter { it.item.itemType == ItemType.WEAPON }
            "ARMOR" -> player.inventory.filter { it.item.itemType == ItemType.ARMOR }
            "CONSUMABLE" -> player.inventory.filter { it.item.itemType == ItemType.CONSUMABLE }
            else -> player.inventory
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(12.dp)
    ) {
        // Header Row: Count & Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INVENTORY (${player.inventory.size}/40)",
                color = NeonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("ALL", "WEAPON", "ARMOR", "CONSUMABLE").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AstralVioletDark,
                            selectedLabelColor = Color.White,
                            containerColor = VoidSurface,
                            labelColor = Color.Gray
                        ),
                        border = BorderStroke(1.dp, if (isSelected) NeonCyan else VoidOutline),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 40-slot Inventory Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredItems, key = { it.slotIndex }) { slot ->
                InventorySlotCard(
                    slot = slot,
                    onClick = { selectedSlotForDetail = slot }
                )
            }
        }
    }

    // Detail and Action Dialog
    selectedSlotForDetail?.let { slot ->
        val item = slot.item
        val currentlyEquipped = item.equipSlot?.let { player.equipment[it] }

        ItemDetailDialog(
            item = item,
            currentlyEquipped = currentlyEquipped,
            quantity = slot.quantity,
            isLocked = slot.isLocked,
            isAlreadyEquipped = false,
            onEquip = {
                repository.equipItem(slot.slotIndex)
                selectedSlotForDetail = null
            },
            onSell = {
                repository.sellItem(slot.slotIndex)
                selectedSlotForDetail = null
            },
            onToggleLock = {
                repository.toggleItemLock(slot.slotIndex)
                // Refresh local dialog state
                selectedSlotForDetail = slot.copy(isLocked = !slot.isLocked)
            },
            onDismiss = { selectedSlotForDetail = null }
        )
    }
}

@Composable
fun InventorySlotCard(
    slot: InventorySlot,
    onClick: () -> Unit
) {
    val item = slot.item
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(VoidSurface)
            .border(1.5.dp, item.rarity.color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Item category badge
                Text(
                    text = item.itemType.name.take(3),
                    color = item.rarity.color,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                if (slot.isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = VoidGold,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Text(
                text = item.name,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (item.damage > 0) "+${item.damage}D" else if (item.defense > 0) "+${item.defense}A" else "",
                    color = NeonCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )

                if (slot.quantity > 1) {
                    Text(
                        text = "x${slot.quantity}",
                        color = VoidGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
