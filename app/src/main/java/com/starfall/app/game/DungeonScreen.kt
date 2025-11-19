package com.starfall.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
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
import com.starfall.core.model.Direction

@Composable
fun DungeonScreen(uiState: GameUiState, onAction: (GameAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderSection(uiState)
        DungeonGrid(uiState)
        MovementControls(onAction)
        MessageLog(uiState.messages)
    }
}

@Composable
private fun HeaderSection(uiState: GameUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "HP: ${uiState.playerHp} / ${uiState.playerMaxHp}",
            style = MaterialTheme.typography.titleMedium
        )
        if (uiState.isGameOver) {
            Text(
                text = "GAME OVER",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DungeonGrid(uiState: GameUiState) {
    val entityMap = remember(uiState.entities) {
        uiState.entities.associateBy { it.x to it.y }
    }

    val levelWidth = uiState.width
    val levelHeight = uiState.height
    val viewportWidth = minOf(GameConfig.CAMERA_VIEW_WIDTH, levelWidth)
    val viewportHeight = minOf(GameConfig.CAMERA_VIEW_HEIGHT, levelHeight)
    val maxStartX = (levelWidth - viewportWidth).coerceAtLeast(0)
    val maxStartY = (levelHeight - viewportHeight).coerceAtLeast(0)
    val halfViewportWidth = viewportWidth / 2
    val halfViewportHeight = viewportHeight / 2
    val startX = (uiState.playerX - halfViewportWidth).coerceIn(0, maxStartX)
    val startY = (uiState.playerY - halfViewportHeight).coerceIn(0, maxStartY)
    val endXExclusive = (startX + viewportWidth).coerceAtMost(levelWidth)
    val endYExclusive = (startY + viewportHeight).coerceAtMost(levelHeight)

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
                val row = uiState.tiles.getOrNull(y) ?: continue
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (x in startX until endXExclusive) {
                        val tile = row.getOrNull(x) ?: continue
                        val entity = entityMap[tile.x to tile.y]
                        TileCell(tile = tile, entity = entity)
                    }
                }
            }
        }
    }
}

@Composable
private fun TileCell(tile: TileUiModel, entity: EntityUiModel?) {
    val backgroundColor = when {
        !tile.discovered -> Color(0xFF101010)
        !tile.visible -> Color(0xFF303030)
        else -> when (tile.type) {
            "FLOOR" -> Color(0xFF4D4D4D)
            "WALL" -> Color(0xFF222222)
            "STAIRS_DOWN" -> Color(0xFF155E63)
            else -> Color(0xFF3A3A3A)
        }
    }

    val modifier = Modifier
        .size(32.dp)
        .background(backgroundColor, shape = MaterialTheme.shapes.small)
        .then(
            if (tile.type == "WALL" && tile.visible) {
                Modifier.border(BorderStroke(1.dp, Color.Black), shape = MaterialTheme.shapes.small)
            } else {
                Modifier
            }
        )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        if (entity != null) {
            val textColor = if (entity.isPlayer) Color(0xFF4CAF50) else Color(0xFFE53935)
            Text(
                text = entity.glyph.toString(),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MovementControls(onAction: (GameAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { onAction(GameAction.Move(Direction.UP)) }) {
            Text("Up")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAction(GameAction.Move(Direction.LEFT)) }) {
                Text("Left")
            }
            Button(onClick = { onAction(GameAction.Wait) }) {
                Text("Wait")
            }
            Button(onClick = { onAction(GameAction.Move(Direction.RIGHT)) }) {
                Text("Right")
            }
        }
        Button(onClick = { onAction(GameAction.Move(Direction.DOWN)) }) {
            Text("Down")
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
