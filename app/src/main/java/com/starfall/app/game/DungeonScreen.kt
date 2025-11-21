package com.starfall.app.game

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.starfall.core.engine.GameAction
import com.starfall.core.engine.GameConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

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
    val modifier = Modifier
        .size(48.dp)
        .clip(MaterialTheme.shapes.small)
        .background(Color(0xFF050505))
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

            else -> AssetBackedTile(tile)
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
private fun AssetBackedTile(tile: TileUiModel) {
    val selection = remember(tile.x, tile.y, tile.type, tile.visible) {
        chooseTileArt(tile)
    }
    val painter = rememberTilePainter(selection?.assetName)
    val glowTransition = rememberInfiniteTransition(label = "assetGlow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assetGlowAlpha"
    )

    if (painter == null) {
        AnimatedRunicTile(tile)
        return
    }

    val isWall = tile.type == "WALL"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(
                BorderStroke(1.dp, Color(0xFF0E1A28)),
                shape = MaterialTheme.shapes.small
            )
    ) {
        Image(
            painter = painter,
            contentDescription = "${tile.type.lowercase()} tile",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = if (tile.visible) 1f else 0.55f),
            colorFilter = if (isWall) {
                ColorFilter.tint(Color.White.copy(alpha = 0.2f), blendMode = BlendMode.Screen)
            } else {
                null
            }
        )

        if (selection?.isGlowing == true && tile.visible) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x66B8E6FF).copy(alpha = glowAlpha), Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.9f
                    ),
                    blendMode = BlendMode.Screen,
                    size = size
                )
            }
        }

        if (tile.type == "STAIRS_DOWN") {
            Text(
                text = "⇩",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF34E0A1),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (!tile.visible) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xAA050507)))
        }
    }
}

@Composable
private fun rememberTilePainter(assetName: String?): BitmapPainter? {
    if (assetName == null) return null
    val context = LocalContext.current
    var painter by remember(assetName) { mutableStateOf<BitmapPainter?>(null) }

    LaunchedEffect(assetName, context) {
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("$TILE_ASSET_PREFIX$assetName").use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
        painter = bitmap?.asImageBitmap()?.let { BitmapPainter(it) }
    }

    return painter
}

private data class TileArtSelection(val assetName: String, val isGlowing: Boolean)

private fun chooseTileArt(tile: TileUiModel): TileArtSelection? {
    val seed = abs(tile.x * 92821 + tile.y * 68917 + tile.type.hashCode())
    val random = Random(seed)

    return when (tile.type) {
        "WALL" -> {
            if (WALL_TILE_NAMES.isEmpty()) return null
            val assetName = WALL_TILE_NAMES[random.nextInt(WALL_TILE_NAMES.size)]
            TileArtSelection(assetName, isGlowing = false)
        }

        else -> {
            val useGlow = tile.visible &&
                GLOWING_TILE_NAMES.isNotEmpty() &&
                random.nextFloat() < GLOW_TILE_PROBABILITY
            val pool = if (useGlow) GLOWING_TILE_NAMES else FLOOR_TILE_NAMES
            if (pool.isEmpty()) return null

            val assetName = pool[random.nextInt(pool.size)]
            TileArtSelection(assetName, isGlowing = useGlow)
        }
    }
}

private const val TILE_ASSET_PREFIX = "tiles/tiles_grid/"
private const val GLOW_TILE_PROBABILITY = 0.08f

private val GLOWING_TILE_NAMES = listOf(
    "tile_r0_c2.png",
    "tile_r0_c4.png",
    "tile_r5_c3.png",
    "tile_r6_c1.png",
    "tile_r6_c3.png",
    "tile_r6_c4.png",
    "tile_r7_c0.png",
    "tile_r7_c2.png",
    "tile_r7_c4.png",
    "tile_r8_c1.png",
    "tile_r9_c0.png",
    "tile_r9_c4.png"
)

private val WALL_TILE_NAMES = listOf(
    "tile_r1_c0.png",
    "tile_r3_c3.png",
    "tile_r2_c3.png",
    "tile_r0_c3.png",
    "tile_r7_c1.png",
    "tile_r8_c0.png",
    "tile_r3_c4.png",
    "tile_r8_c2.png",
    "tile_r0_c1.png",
    "tile_r9_c3.png",
    "tile_r7_c3.png",
    "tile_r6_c0.png",
    "tile_r5_c0.png",
    "tile_r1_c4.png",
    "tile_r1_c2.png",
    "tile_r8_c3.png",
    "tile_r0_c0.png",
    "tile_r3_c0.png",
    "tile_r5_c1.png"
)

private val FLOOR_TILE_NAMES = listOf(
    "tile_r4_c3.png",
    "tile_r2_c0.png",
    "tile_r1_c3.png",
    "tile_r4_c2.png",
    "tile_r9_c2.png",
    "tile_r9_c1.png",
    "tile_r3_c2.png",
    "tile_r3_c1.png",
    "tile_r4_c1.png",
    "tile_r5_c4.png",
    "tile_r1_c1.png",
    "tile_r4_c0.png",
    "tile_r2_c1.png",
    "tile_r6_c2.png",
    "tile_r2_c2.png",
    "tile_r8_c4.png",
    "tile_r6_c0.png",
    "tile_r2_c4.png",
    "tile_r4_c4.png"
)

@Composable
private fun AnimatedRunicTile(tile: TileUiModel) {
    val glowTransition = rememberInfiniteTransition(label = "glowTransition")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val sweepProgress by glowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowSweep"
    )
    val pulse by glowTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val glowColor = if (tile.type == "WALL") Color(0xFF7CD4FF) else Color(0xFF63C9FF)
    val stoneColor = if (tile.type == "WALL") Color(0xFF11131A) else Color(0xFF0F0D15)
    val accentColor = if (tile.type == "WALL") Color(0xFF1E2434) else Color(0xFF191A27)

    val seed = remember(tile.x, tile.y, tile.type) {
        (tile.x * 92821) xor (tile.y * 68917) xor tile.type.hashCode()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (tile.type == "WALL") Color.Black.copy(alpha = 0.4f) else Color(0xFF0E1A28)
                ),
                shape = MaterialTheme.shapes.small
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = if (tile.visible) 1f else 0.55f)
        ) {
            val random = Random(seed)
            drawRect(color = stoneColor)

            val blockCount = if (tile.type == "WALL") 3 else 4
            val blockWidth = size.width / blockCount
            val blockHeight = size.height / blockCount

            for (row in 0 until blockCount) {
                for (col in 0 until blockCount) {
                    val jitterX = (random.nextFloat() - 0.5f) * blockWidth * 0.2f
                    val jitterY = (random.nextFloat() - 0.5f) * blockHeight * 0.2f
                    val width = blockWidth * (0.85f + random.nextFloat() * 0.25f)
                    val height = blockHeight * (0.85f + random.nextFloat() * 0.25f)
                    val topLeft = Offset(
                        x = col * blockWidth + jitterX,
                        y = row * blockHeight + jitterY
                    )

                    val shade = stoneColor.mixWith(accentColor, 0.25f + random.nextFloat() * 0.4f)
                    drawRoundRect(
                        color = shade,
                        topLeft = topLeft,
                        size = Size(width, height),
                        cornerRadius = CornerRadius(blockWidth * 0.12f, blockHeight * 0.12f)
                    )
                }
            }

            repeat(3) {
                val radius = min(size.width, size.height) * (0.12f + random.nextFloat() * 0.12f) * pulse
                val center = Offset(
                    x = random.nextFloat() * size.width,
                    y = random.nextFloat() * size.height
                )

                drawCircle(
                    color = glowColor.copy(alpha = 0.1f + 0.2f * glowAlpha),
                    radius = radius * 1.4f,
                    center = center,
                    blendMode = BlendMode.Screen
                )

                val runePath = buildRunePath(random, center, radius)
                drawPath(
                    path = runePath,
                    color = glowColor.copy(alpha = 0.55f * glowAlpha),
                    style = Stroke(width = radius * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                drawCircle(
                    color = glowColor.copy(alpha = 0.7f * glowAlpha),
                    radius = radius * 0.22f,
                    center = center,
                    blendMode = BlendMode.Screen
                )
            }

            val sweepDistance = size.width + size.height
            val sweepStart = sweepProgress * sweepDistance - size.height
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        glowColor.copy(alpha = glowAlpha * 0.45f),
                        Color.Transparent
                    ),
                    start = Offset(sweepStart, 0f),
                    end = Offset(sweepStart + size.height, size.height)
                ),
                size = size,
                blendMode = BlendMode.Screen
            )

            if (tile.type == "STAIRS_DOWN") {
                drawCircle(
                    color = Color(0xFF34E0A1).copy(alpha = 0.55f + glowAlpha * 0.25f),
                    radius = min(size.width, size.height) * 0.22f,
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    blendMode = BlendMode.Screen
                )
            }

            if (!tile.visible) {
                drawRect(color = Color(0xFF050507).copy(alpha = 0.5f))
            }
        }
    }
}

private fun buildRunePath(random: Random, center: Offset, radius: Float): Path {
    val path = Path()
    val twists = 1.25f + random.nextFloat() * 1.25f
    val steps = 42
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val angle = (t * twists * PI * 2.0 + random.nextDouble() * PI / 4).toFloat()
        val r = radius * (0.2f + 0.75f * t)
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    return path
}

private fun Color.mixWith(other: Color, fraction: Float): Color {
    val clamped = fraction.coerceIn(0f, 1f)
    return Color(
        red + (other.red - red) * clamped,
        green + (other.green - green) * clamped,
        blue + (other.blue - blue) * clamped,
        alpha + (other.alpha - alpha) * clamped
    )
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
