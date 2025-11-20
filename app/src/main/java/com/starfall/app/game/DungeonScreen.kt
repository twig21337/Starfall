package com.starfall.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starfall.core.engine.GameAction
import com.starfall.core.engine.GameConfig

@Composable
fun DungeonScreen(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
    onDismissDescendPrompt: () -> Unit,
    onStartNewGame: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection(uiState, onStartNewGame)
        val handleTileTap: (Int, Int) -> Unit = { x, y ->
            if (x == uiState.playerX && y == uiState.playerY) {
                onAction(GameAction.Wait)
            } else {
                onAction(GameAction.MoveTo(x, y))
            }
        }
        DungeonGrid(
            uiState = uiState,
            onTileTapped = handleTileTap
        )
        MessageLog(uiState.messages)
        InventorySection(uiState.inventory) { item ->
            when (item.type) {
                "HEALING_POTION" -> onAction(GameAction.UseItem(item.id))
                "WOOD_SWORD", "WOOD_ARMOR" -> onAction(GameAction.EquipItem(item.id))
            }
        }
    }

    if (uiState.showDescendPrompt) {
        DescendPrompt(
            onConfirm = {
                onAction(GameAction.DescendStairs)
                onDismissDescendPrompt()
            },
            onDismiss = onDismissDescendPrompt
        )
    }
}

@Composable
private fun HeaderSection(uiState: GameUiState, onStartNewGame: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "HP: ${uiState.playerHp} / ${uiState.playerMaxHp} | Armor: ${uiState.playerArmor} / ${uiState.playerMaxArmor}",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Floor ${uiState.currentFloor} / ${uiState.totalFloors}",
            style = MaterialTheme.typography.titleMedium
        )
        if (uiState.isGameOver) {
            Text(
                text = "GAME OVER",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onStartNewGame() }
            )
        }
    }
}

@Composable
private fun DungeonGrid(uiState: GameUiState, onTileTapped: (Int, Int) -> Unit) {
    val entityMap = remember(uiState.entities) {
        uiState.entities.associateBy { it.x to it.y }
    }

    val groundItemMap = remember(uiState.groundItems) {
        uiState.groundItems.associateBy { it.x to it.y }
    }

    val viewportWidth = GameConfig.CAMERA_VIEW_WIDTH
    val viewportHeight = GameConfig.CAMERA_VIEW_HEIGHT
    val halfViewportWidth = viewportWidth / 2
    val halfViewportHeight = viewportHeight / 2
    val startX = uiState.playerX - halfViewportWidth
    val startY = uiState.playerY - halfViewportHeight
    val endXExclusive = startX + viewportWidth
    val endYExclusive = startY + viewportHeight

    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (y in startY until endYExclusive) {
                val row = uiState.tiles.getOrNull(y)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (x in startX until endXExclusive) {
                        val tile = row?.getOrNull(x)
                        val entity = tile?.let { entityMap[it.x to it.y] }
                        val item = tile?.let { groundItemMap[it.x to it.y] }
                        TileCell(tile = tile, entity = entity, groundItem = item, onTileTapped = onTileTapped)
                    }
                }
            }
        }
    }
}

@Composable
private fun TileCell(
    tile: TileUiModel?,
    entity: EntityUiModel?,
    groundItem: GroundItemUiModel?,
    onTileTapped: (Int, Int) -> Unit
) {
    val backgroundColor = when {
        tile == null -> Color(0xFF050505)
        !tile.discovered -> Color(0xFF101010)
        !tile.visible -> Color(0xFF303030)
        else -> when (tile.type) {
            "FLOOR" -> Color(0xFF8B5A2B)
            "WALL" -> Color(0xFF7A7A7A)
            "STAIRS_DOWN" -> Color(0xFF155E63)
            else -> Color(0xFF3A3A3A)
        }
    }

    val modifier = Modifier
        .size(48.dp)
        .background(backgroundColor, shape = MaterialTheme.shapes.small)
        .then(
            if (tile?.let { it.type == "WALL" && it.visible } == true) {
                Modifier.border(BorderStroke(1.dp, Color.Black), shape = MaterialTheme.shapes.small)
            } else {
                Modifier
            }
        )
        .then(
            if (tile?.discovered == true) {
                Modifier.clickable { onTileTapped(tile.x, tile.y) }
            } else {
                Modifier
            }
        )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        if (entity != null && tile != null) {
            val textColor = if (entity.isPlayer) Color(0xFF4CAF50) else Color(0xFFE53935)
            Text(
                text = entity.glyph.toString(),
                color = textColor,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
        if (groundItem != null && tile != null && tile.discovered) {
            Text(
                text = groundItem.icon,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun MessageLog(messages: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Messages",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (messages.isEmpty()) {
            Text("...")
        } else {
            messages.forEach { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun InventorySection(
    items: List<InventoryItemUiModel>,
    onItemTapped: (InventoryItemUiModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Inventory",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (items.isEmpty()) {
            Text("Your pack is empty.", style = MaterialTheme.typography.bodySmall)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    InventoryTile(item = item, onClick = { onItemTapped(item) })
                }
            }
        }
    }
}

@Composable
private fun InventoryTile(item: InventoryItemUiModel, onClick: () -> Unit) {
    val borderColor = if (item.isEquipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .size(88.dp)
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, borderColor), MaterialTheme.shapes.small)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = item.icon, style = MaterialTheme.typography.titleLarge)
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        if (item.isEquipped) {
            Text(
                text = "Equipped",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DescendPrompt(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onConfirm) { Text("Yes") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("No") }
        },
        title = { Text("Descend?", fontWeight = FontWeight.Bold) },
        text = { Text("Do you want to descend to the next floor?") }
    )
}
