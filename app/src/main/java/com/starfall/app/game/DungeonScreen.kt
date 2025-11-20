package com.starfall.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
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
                val hasItemsHere = uiState.groundItems.any { it.x == x && it.y == y }
                if (hasItemsHere) {
                    onAction(GameAction.PickUp)
                } else {
                    onAction(GameAction.Wait)
                }
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
            val itemType = item.type
            if (itemType == "EQUIPMENT_WEAPON" || itemType == "EQUIPMENT_ARMOR") {
                onAction(GameAction.EquipItem(item.id))
            } else {
                onAction(GameAction.UseItem(item.id))
            }
        }
    }

    if (uiState.showDescendPrompt) {
        val isExitPrompt = uiState.descendPromptIsExit
        DescendPrompt(
            isExitPrompt = isExitPrompt,
            onConfirm = {
                if (isExitPrompt) {
                    onDismissDescendPrompt()
                    onStartNewGame()
                } else {
                    onAction(GameAction.DescendStairs)
                    onDismissDescendPrompt()
                }
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
        uiState.groundItems.groupBy { it.x to it.y }
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
                        val entity = tile?.takeIf { it.visible }?.let { entityMap[it.x to it.y] }
                        val items = tile?.let { groundItemMap[it.x to it.y] }
                        TileCell(tile = tile, entity = entity, groundItems = items, onTileTapped = onTileTapped)
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
    groundItems: List<GroundItemUiModel>?,
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
            if (!groundItems.isNullOrEmpty() && tile != null && tile.discovered) {
                val firstItem = groundItems.first()
                val totalCount = groundItems.sumOf { it.quantity.coerceAtLeast(1) }
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = firstItem.icon,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = 6.dp)
                    )
                    if (totalCount > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.extraSmall
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "x $totalCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageLog(messages: List<String>) {
    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Messages",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
            val maxSlots = 15
            val paddedItems: List<InventoryItemUiModel?> =
                items.take(maxSlots).map { it as InventoryItemUiModel? } +
                    List(maxSlots - items.size.coerceAtMost(maxSlots)) { null }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(paddedItems) { item ->
                    if (item != null) {
                        InventoryTile(item = item, onClick = { onItemTapped(item) })
                    } else {
                        EmptyInventoryTile()
                    }
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
            .size(58.dp)
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, borderColor), MaterialTheme.shapes.small)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = item.icon,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 6.dp)
            )
            if (item.quantity > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "x ${item.quantity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.isEquipped) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyInventoryTile() {
    Column(
        modifier = Modifier
            .size(58.dp)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                MaterialTheme.shapes.small
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "–",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DescendPrompt(isExitPrompt: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = if (isExitPrompt) "Leave the dungeon?" else "Descend?"
    val body = if (isExitPrompt) {
        "These stairs lead out of the depths. Return to the surface and begin anew?"
    } else {
        "Do you want to descend to the next floor?"
    }
    val confirmLabel = if (isExitPrompt) "Leave" else "Yes"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("No") }
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body) }
    )
}
