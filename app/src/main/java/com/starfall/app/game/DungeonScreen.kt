package com.starfall.app.game

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.starfall.core.engine.GameAction
import com.starfall.core.engine.GameConfig
import com.starfall.core.engine.RunResult
import com.starfall.core.model.EnemyIntentType
import com.starfall.core.model.PlayerEffectType
import com.starfall.core.model.TileType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun DungeonScreen(
    uiState: GameUiState,
    hudUiState: HudUiState,
    onAction: (GameAction) -> Unit,
    onDismissDescendPrompt: () -> Unit,
    onStartNewGame: () -> Unit,
    onReturnToMainMenu: () -> Unit,
    onOpenOverworld: () -> Unit,
    onSaveGame: () -> Unit,
    onRequestTarget: (InventoryItemUiModel) -> Unit,
    onTileTarget: (Int, Int) -> Unit,
    onMutationSelected: (String) -> Unit,
    onHudTabSelected: (BottomHudTab) -> Unit
) {
    var discardCandidate by remember { mutableStateOf<InventoryItemUiModel?>(null) }
    val handleTileTap = remember(
        uiState.playerX,
        uiState.playerY,
        uiState.targetingItemId,
        uiState.groundItems
    ) {
        createTileTapHandler(uiState, onAction, onTileTarget)
    }
    val handleInventoryTap = remember(uiState.inventory) {
        createInventoryTapHandler(onAction, onRequestTarget)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunStatusBar(hudUiState)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    DungeonGrid(
                        uiState = uiState,
                        onTileTapped = handleTileTap
                    )
                    PlayerDebuffOverlay(
                        debuffs = uiState.activeDebuffs,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                    )
                    CompassDial(
                        direction = uiState.compassDirection,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
                if (uiState.targetingPrompt != null) {
                    TargetingBanner(uiState.targetingPrompt)
                }
                if (hudUiState.selectedTab == null) {
                    MessageLog(uiState.messages)
                }
            }
            BottomHud(
                uiState = hudUiState,
                onTabSelected = onHudTabSelected,
                onInventoryItemTapped = handleInventoryTap,
                onInventoryItemLongPressed = { item -> discardCandidate = item },
                onReturnToMainMenu = onReturnToMainMenu,
                onOpenOverworld = onOpenOverworld,
                onStartNewRun = onStartNewGame,
                onSaveGame = onSaveGame
            )
        }

        uiState.levelUpBanner?.let { banner ->
            LevelUpBanner(banner)
        }

        if (uiState.pendingMutations.isNotEmpty()) {
            MutationChoiceDialog(
                choices = uiState.pendingMutations,
                onChoice = onMutationSelected
            )
        }

        if (uiState.isGameOver) {
            GameOverOverlay(
                runResult = uiState.lastRunResult,
                onStartNewGame = onStartNewGame,
                onReturnToMainMenu = onReturnToMainMenu
            )
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

    discardCandidate?.let { item ->
        var discardQuantity by remember(item) { mutableStateOf(1) }
        val maxQuantity = item.quantity.coerceAtLeast(1)

        AlertDialog(
            onDismissRequest = { discardCandidate = null },
            confirmButton = {
                Button(onClick = {
                    onAction(GameAction.DiscardItem(item.id, discardQuantity))
                    discardCandidate = null
                }) { Text("Discard x$discardQuantity") }
            },
            dismissButton = {
                Button(onClick = { discardCandidate = null }) { Text("Keep") }
            },
            title = { Text("Discard ${item.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose how many to discard (1-$maxQuantity).")
                    if (item.quantity > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { discardQuantity = (discardQuantity - 1).coerceAtLeast(1) },
                                enabled = discardQuantity > 1
                            ) { Text("-") }
                            Text(
                                text = "x $discardQuantity",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.width(52.dp),
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { discardQuantity = (discardQuantity + 1).coerceAtMost(maxQuantity) },
                                enabled = discardQuantity < maxQuantity
                            ) { Text("+") }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun RunStatusBar(hudUiState: HudUiState) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "HP ${hudUiState.currentHp}/${hudUiState.maxHp}")
                    LinearProgressIndicator(
                        progress =
                        if (hudUiState.maxHp > 0) {
                            hudUiState.currentHp / hudUiState.maxHp.toFloat()
                        } else {
                            0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Armor ${hudUiState.statsPanel.armor}/${hudUiState.statsPanel.maxArmor}")
                    LinearProgressIndicator(
                        progress =
                        if (hudUiState.statsPanel.maxArmor > 0) {
                            hudUiState.statsPanel.armor / hudUiState.statsPanel.maxArmor.toFloat()
                        } else {
                            0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Floor ${hudUiState.currentFloor} / ${hudUiState.maxFloor}")
                Text(text = "Level ${hudUiState.currentLevel}")
            }
        }
    }
}

@Composable
private fun BoxScope.PlayerDebuffOverlay(
    debuffs: List<PlayerDebuffUiModel>,
    modifier: Modifier = Modifier
) {
    if (debuffs.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        debuffs.forEachIndexed { index, debuff ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            DebuffBadge(debuff)
        }
    }
}

@Composable
private fun DebuffBadge(debuff: PlayerDebuffUiModel) {
    val (accentColor, fallbackLabel) = when (debuff.type) {
        PlayerEffectType.POISONED -> Color(0xFF7ED957) to "Poisoned"
        PlayerEffectType.CHILLED -> Color(0xFF7FD0FF) to "Chilled"
        PlayerEffectType.FROZEN -> Color(0xFF9FDBFF) to "Frozen"
        PlayerEffectType.WEAKENED -> Color(0xFFF2A45A) to "Weakened"
        else -> MaterialTheme.colorScheme.secondary to debuff.label
    }
    val label = debuff.label.ifBlank { fallbackLabel }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = accentColor.copy(alpha = 0.22f),
            contentColor = accentColor,
            shape = CircleShape,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                DebuffIcon(type = debuff.type, tint = accentColor)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun DebuffIcon(type: PlayerEffectType, tint: Color) {
    Canvas(modifier = Modifier
        .size(40.dp)
        .padding(8.dp)) {
        val strokeWidth = size.minDimension * 0.14f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.minDimension / 2.2f

        when (type) {
            PlayerEffectType.POISONED -> {
                val drop = Path().apply {
                    moveTo(centerX, centerY - radius)
                    cubicTo(
                        centerX + radius * 0.45f,
                        centerY - radius * 0.15f,
                        centerX + radius * 0.55f,
                        centerY + radius * 0.35f,
                        centerX,
                        centerY + radius * 0.9f
                    )
                    cubicTo(
                        centerX - radius * 0.55f,
                        centerY + radius * 0.35f,
                        centerX - radius * 0.45f,
                        centerY - radius * 0.15f,
                        centerX,
                        centerY - radius
                    )
                    close()
                }
                drawPath(drop, color = tint.copy(alpha = 0.9f))
                drawCircle(
                    color = tint.copy(alpha = 0.55f),
                    radius = radius * 0.35f,
                    center = androidx.compose.ui.geometry.Offset(centerX + radius * 0.1f, centerY + radius * 0.1f)
                )
            }

            PlayerEffectType.CHILLED -> {
                val branch = radius * 0.8f
                val diagonal = branch * 0.75f
                val center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                drawLine(
                    color = tint,
                    start = center.copy(x = center.x - branch),
                    end = center.copy(x = center.x + branch),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = center.copy(y = center.y - branch),
                    end = center.copy(y = center.y + branch),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = center + androidx.compose.ui.geometry.Offset(-diagonal, -diagonal),
                    end = center + androidx.compose.ui.geometry.Offset(diagonal, diagonal),
                    strokeWidth = strokeWidth * 0.85f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = center + androidx.compose.ui.geometry.Offset(-diagonal, diagonal),
                    end = center + androidx.compose.ui.geometry.Offset(diagonal, -diagonal),
                    strokeWidth = strokeWidth * 0.85f,
                    cap = StrokeCap.Round
                )
            }

            PlayerEffectType.FROZEN -> {
                val diamond = Path().apply {
                    moveTo(centerX, centerY - radius)
                    lineTo(centerX + radius * 0.75f, centerY)
                    lineTo(centerX, centerY + radius)
                    lineTo(centerX - radius * 0.75f, centerY)
                    close()
                }
                drawPath(diamond, color = tint.copy(alpha = 0.4f))
                drawPath(
                    diamond,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                val shard = Path().apply {
                    moveTo(centerX, centerY - radius * 0.75f)
                    lineTo(centerX + radius * 0.25f, centerY)
                    lineTo(centerX, centerY + radius * 0.4f)
                    lineTo(centerX - radius * 0.25f, centerY)
                    close()
                }
                drawPath(shard, color = tint.copy(alpha = 0.8f))
            }

            PlayerEffectType.WEAKENED -> {
                val shield = Path().apply {
                    moveTo(centerX, centerY - radius)
                    lineTo(centerX + radius * 0.7f, centerY - radius * 0.15f)
                    lineTo(centerX + radius * 0.45f, centerY + radius)
                    lineTo(centerX, centerY + radius * 0.7f)
                    lineTo(centerX - radius * 0.45f, centerY + radius)
                    lineTo(centerX - radius * 0.7f, centerY - radius * 0.15f)
                    close()
                }
                drawPath(
                    shield,
                    color = tint.copy(alpha = 0.35f)
                )
                drawPath(
                    shield,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(centerX - radius * 0.2f, centerY - radius * 0.4f),
                    end = androidx.compose.ui.geometry.Offset(centerX + radius * 0.3f, centerY + radius * 0.55f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            else -> {
                drawCircle(color = tint.copy(alpha = 0.5f), radius = radius)
            }
        }
    }
}

private fun createTileTapHandler(
    uiState: GameUiState,
    onAction: (GameAction) -> Unit,
    onTileTarget: (Int, Int) -> Unit
): (Int, Int) -> Unit {
    val playerX = uiState.playerX
    val playerY = uiState.playerY
    val targetingItemId = uiState.targetingItemId
    val groundItems = uiState.groundItems
    return { x, y ->
        when {
            targetingItemId != null -> onTileTarget(x, y)
            x == playerX && y == playerY -> {
                val hasItemsHere = groundItems.any { it.x == x && it.y == y }
                if (hasItemsHere) {
                    onAction(GameAction.PickUp)
                } else {
                    onAction(GameAction.Wait)
                }
            }
            else -> onAction(GameAction.MoveTo(x, y))
        }
    }
}

private fun createInventoryTapHandler(
    onAction: (GameAction) -> Unit,
    onRequestTarget: (InventoryItemUiModel) -> Unit
): (InventoryItemUiModel, Int, Int, Int) -> Unit {
    return { item, row, col, index ->
        onAction(GameAction.InventoryTapLog(row, col, index, item.id, item.type))
        when {
            item.canEquip -> onAction(GameAction.EquipItem(item.id))
            item.requiresTarget -> onRequestTarget(item)
            else -> onAction(GameAction.UseItem(item.id))
        }
    }
}

@Composable
private fun CompassDial(direction: String?, modifier: Modifier = Modifier) {
    if (direction == null || direction == "Here") return
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .padding(4.dp)
            .width(64.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Stairs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = direction,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
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

    val intentTargets = remember(uiState.enemyIntents) {
        uiState.enemyIntents
            .flatMap { intent -> intent.targetTiles.map { (it.x to it.y) to intent } }
            .groupBy({ it.first }) { it.second }
            .mapValues { it.value.first() }
    }

    val actingEnemies = remember(uiState.enemyIntents) {
        uiState.enemyIntents.associateBy { it.enemyPosition.x to it.enemyPosition.y }
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
    val heroSpriteComposer = remember(context) { HeroSpriteComposer(context.assets) }
    val heroLayers = remember(uiState.equippedWeaponSpriteKey, uiState.equippedArmorSpriteKey) {
        HeroSpriteLayers(
            weaponSpriteKey = uiState.equippedWeaponSpriteKey,
            armorSpriteKey = uiState.equippedArmorSpriteKey
        )
    }

    val blinkTransition = rememberInfiniteTransition(label = "telegraphBlink")
    val blinkPhase by blinkTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "telegraphPhase"
    )
    val telegraphAlpha = 0.5f + 0.5f * sin(blinkPhase)

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp),
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
                        val targetIntent = tile?.let { intentTargets[it.x to it.y] }
                        val isActingEnemyTile = tile?.let { actingEnemies.containsKey(it.x to it.y) } == true
                        TileCell(
                            tile = tile,
                            entity = entity,
                            groundItems = items,
                            telegraphIntent = targetIntent,
                            telegraphAlpha = telegraphAlpha,
                            isActingEnemyTile = isActingEnemyTile,
                            spriteProvider = spriteProvider,
                            heroSpriteComposer = heroSpriteComposer,
                            heroSpriteLayers = heroLayers,
                            heroFacing = uiState.playerFacing,
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
                .fillMaxSize()
                .background(Color(0x142846FF))
        )
    }
}

@Composable
private fun TileCell(
    tile: TileUiModel?,
    entity: EntityUiModel?,
    groundItems: List<GroundItemUiModel>?,
    telegraphIntent: EnemyIntentUiModel?,
    telegraphAlpha: Float,
    isActingEnemyTile: Boolean,
    spriteProvider: TileSpriteProvider,
    heroSpriteComposer: HeroSpriteComposer,
    heroSpriteLayers: HeroSpriteLayers,
    heroFacing: FacingDirection,
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

        val tileVisible = tile?.visible == true

        if (tileVisible && telegraphIntent != null) {
            TelegraphOverlay(color = telegraphColorFor(telegraphIntent.intentType), alpha = telegraphAlpha)
        }

        if (tileVisible && isActingEnemyTile) {
            TelegraphOverlay(color = actingEnemyTelegraphColor(), alpha = telegraphAlpha * 0.9f)
        }

        if (entity != null && tile != null) {
            if (entity.isPlayer) {
                HeroSprite(
                    spriteComposer = heroSpriteComposer,
                    facing = heroFacing,
                    layers = heroSpriteLayers
                )
            } else {
                val textColor = Color(0xFFE53935)
                Text(
                    text = entity.glyph.toString(),
                    color = textColor,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (!groundItems.isNullOrEmpty() && tile != null && tile.discovered) {
            GroundItemStackBadge(groundItems)
        }
    }
}

@Composable
private fun TelegraphOverlay(color: Color, alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color.copy(alpha = alpha))
    )
}

private fun telegraphColorFor(type: EnemyIntentType): Color = when (type) {
    EnemyIntentType.BLOCK, EnemyIntentType.SUMMON, EnemyIntentType.BUFF -> Color(0xFF64B5F6)
    else -> Color(0xFFE53935)
}

private fun actingEnemyTelegraphColor(): Color = Color(0xFFFFC107)

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
                    initialValue = 0.75f,
                    targetValue = 1.2f,
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
private fun HeroSprite(
    spriteComposer: HeroSpriteComposer,
    facing: FacingDirection,
    layers: HeroSpriteLayers
) {
    val frames = remember(facing, layers) { spriteComposer.idleFrames(facing, layers) }
    val fallbackColor = MaterialTheme.colorScheme.primary
    if (frames.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(fallbackColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "@",
                style = MaterialTheme.typography.titleLarge,
                color = fallbackColor
            )
        }
        return
    }

    var frameIndex by remember { mutableStateOf(0) }
    LaunchedEffect(frames) {
        var index = 0
        while (frames.isNotEmpty()) {
            frameIndex = index % frames.size
            index = (index + 1) % frames.size
            delay(180)
        }
    }

    Image(
        bitmap = frames[frameIndex % frames.size],
        contentDescription = "Hero",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None
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
private fun LevelUpBanner(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MutationChoiceDialog(
    choices: List<MutationUiModel>,
    onChoice: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text("Choose a mutation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                choices.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { mutation ->
                            Surface(
                                tonalElevation = 2.dp,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = mutation.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = mutation.description,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = mutation.tier,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(onClick = { onChoice(mutation.id) }) {
                                            Text("Choose")
                                        }
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun BoxScope.GameOverOverlay(
    runResult: RunResult?,
    onStartNewGame: () -> Unit,
    onReturnToMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            tonalElevation = 8.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isVictory = runResult?.isVictory == true
                Text(
                    text = if (isVictory) "Run Complete" else "Run Over",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                runResult?.let { result ->
                    Text(
                        text = if (isVictory) {
                            "You conquered these depths!"
                        } else {
                            "Your journey ends here, but knowledge remains."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    RunStatsSummary(result)
                } ?: Text(
                    text = "Stats unavailable for this run.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartNewGame) {
                        Text("Begin New Run")
                    }
                    OutlinedButton(onClick = onReturnToMainMenu) {
                        Text("Return to Main Menu")
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStatsSummary(result: RunResult) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Floors Cleared", style = MaterialTheme.typography.bodyMedium)
            Text(result.floorsCleared.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Bosses Defeated", style = MaterialTheme.typography.bodyMedium)
            Text(result.bossesKilled.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Elites Defeated", style = MaterialTheme.typography.bodyMedium)
            Text(result.elitesKilled.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Enemies Defeated", style = MaterialTheme.typography.bodyMedium)
            Text(result.enemiesKilled.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Mutations Chosen", style = MaterialTheme.typography.bodyMedium)
            Text(result.mutationsChosen.toString(), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Meta Currency", style = MaterialTheme.typography.bodyMedium)
            Text("+${result.metaCurrencyEarned}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Run Time", style = MaterialTheme.typography.bodyMedium)
            Text(formatDuration(result.timeInRunMs), style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Final Cause", style = MaterialTheme.typography.bodyMedium)
            Text(result.cause.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
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
