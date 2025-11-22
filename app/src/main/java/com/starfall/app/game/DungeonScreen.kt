package com.starfall.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.starfall.core.engine.GameAction
import com.starfall.core.engine.GameConfig
import com.starfall.core.model.TileType

@Composable
fun DungeonScreen(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
    onDismissDescendPrompt: () -> Unit,
    onStartNewGame: () -> Unit,
    onRequestTarget: (InventoryItemUiModel) -> Unit,
    onTileTarget: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection(uiState, onStartNewGame)
        val handleTileTap: (Int, Int) -> Unit = { x, y ->
            if (uiState.targetingItemId != null) {
                onTileTarget(x, y)
            } else if (x == uiState.playerX && y == uiState.playerY) {
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
        if (uiState.targetingPrompt != null) {
            TargetingBanner(uiState.targetingPrompt)
        }
        MessageLog(uiState.messages)
        InventorySection(uiState.inventory) { item ->
            if (item.canEquip) {
                onAction(GameAction.EquipItem(item.id))
            } else if (item.requiresTarget) {
                onRequestTarget(item)
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
        uiState.compassDirection?.let { direction ->
            Text(
                text = "Stairs: $direction",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
private fun TargetingBanner(prompt: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.inverseSurface, MaterialTheme.shapes.small)
            .padding(8.dp)
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
    val context = LocalContext.current
    val spriteProvider = remember(context) { TileSpriteProvider(context.assets) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            for (y in startY until endYExclusive) {
                val row = uiState.tiles.getOrNull(y)
                val aboveRow = uiState.tiles.getOrNull(y - 1)
                val belowRow = uiState.tiles.getOrNull(y + 1)
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    for (x in startX until endXExclusive) {
                        val tile = row?.getOrNull(x)
                        val aboveTile = aboveRow?.getOrNull(x)
                        val leftTile = row?.getOrNull(x - 1)
                        val rightTile = row?.getOrNull(x + 1)
                        val belowTile = belowRow?.getOrNull(x)
                        val entity = tile?.takeIf { it.visible }?.let { entityMap[it.x to it.y] }
                        val items = tile?.let { groundItemMap[it.x to it.y] }
                        TileCell(
                            tile = tile,
                            entity = entity,
                            groundItems = items,
                            spriteProvider = spriteProvider,
                            wallHasWallAbove = tile?.type == TileType.WALL.name && aboveTile?.type == TileType.WALL.name,
                            wallHasFloorToLeft = tile?.type == TileType.WALL.name && leftTile?.type == TileType.FLOOR.name,
                            wallHasFloorToRight = tile?.type == TileType.WALL.name && rightTile?.type == TileType.FLOOR.name,
                            wallHasFloorBelow = tile?.type == TileType.WALL.name && belowTile?.type != TileType.WALL.name,
                            onTileTapped = onTileTapped
                        )
                    }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0x142846FF))
        )
    }
}

@Composable
private fun TileCell(
    tile: TileUiModel?,
    entity: EntityUiModel?,
    groundItems: List<GroundItemUiModel>?,
    spriteProvider: TileSpriteProvider,
    wallHasWallAbove: Boolean = false,
    wallHasFloorToLeft: Boolean = false,
    wallHasFloorToRight: Boolean = false,
    wallHasFloorBelow: Boolean = false,
    onTileTapped: (Int, Int) -> Unit
) {
    val modifier = Modifier
        .size(48.dp)
        .then(
            if (tile?.discovered == true) {
                Modifier.clickable { onTileTapped(tile.x, tile.y) }
            } else {
                Modifier
            }
        )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        when {
            tile == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090909))
            )

            !tile.discovered -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF101018))
            )

            else -> TexturedTile(tile, spriteProvider, wallHasWallAbove, wallHasFloorToLeft, wallHasFloorToRight, wallHasFloorBelow)
        }

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
            GroundItemStackBadge(groundItems)
        }
    }
}

@Composable
private fun BoxScope.GroundItemStackBadge(groundItems: List<GroundItemUiModel>) {
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

@Composable
private fun TexturedTile(
    tile: TileUiModel,
    spriteProvider: TileSpriteProvider,
    wallHasWallAbove: Boolean,
    wallHasFloorToLeft: Boolean,
    wallHasFloorToRight: Boolean,
    wallHasFloorBelow: Boolean
) {
    val isWall = tile.type == TileType.WALL.name
    val isFloor = tile.type == TileType.FLOOR.name
    val baseBitmap = remember(tile) {
        if (isWall) {
            spriteProvider.wallFor(tile)
        } else {
            spriteProvider.floorFor(tile)
        }
    }
    val glowBitmap = remember(tile) {
        if (isFloor) spriteProvider.glowFor(tile) else null
    }
    val floorBrightnessFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix().apply { setToScale(1.15f, 1.15f, 1.15f, 1f) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (baseBitmap != null) {
            Image(
                bitmap = baseBitmap,
                contentDescription = "${tile.type.lowercase()} tile",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = if (tile.visible) 1f else 0.55f),
                contentScale = ContentScale.FillBounds,
                colorFilter = if (isFloor) floorBrightnessFilter else null,
                filterQuality = FilterQuality.None
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A10))
            )
        }

        if (isWall) {
            if (!wallHasWallAbove) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0x4DFFFFFF), Color.Transparent)
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x99000000))
                        )
                    )
            )
            if (wallHasFloorBelow) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xE0FFFFFF), Color(0x99FFFFFF))
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x33000000), Color.Transparent)
                        )
                    )
            )
            if (wallHasFloorToLeft) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xB3FFFFFF), Color.Transparent)
                            )
                        )
                )
            }
            if (wallHasFloorToRight) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .align(Alignment.CenterEnd)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color(0xB3FFFFFF))
                            )
                        )
                )
            }
        }

        if (glowBitmap != null) {
            val phaseOffset = remember(tile) { ((tile.x + tile.y) % 5) * 150 }
            val glowAlpha by rememberInfiniteTransition(label = "glowTransition")
                .animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 1600,
                            delayMillis = phaseOffset,
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glowAlpha"
                )

                Image(
                    bitmap = glowBitmap,
                    contentDescription = "glowing floor overlay",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = if (tile.visible) glowAlpha else glowAlpha * 0.55f),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None
            )
        }

        if (tile.type == TileType.STAIRS_DOWN.name) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x8034E0A1))
            )
        }

        if (!tile.visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050507).copy(alpha = 0.5f))
            )
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
