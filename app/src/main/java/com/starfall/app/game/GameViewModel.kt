package com.starfall.app.game

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starfall.core.dungeon.SimpleDungeonGenerator
import com.starfall.core.engine.GameAction
import com.starfall.core.engine.GameEngine
import com.starfall.core.engine.GameEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    private val engine: GameEngine = GameEngine(SimpleDungeonGenerator())
    private val _uiState: MutableState<GameUiState> = mutableStateOf(GameUiState())
    val uiState: State<GameUiState> get() = _uiState
    private var actionJob: Job? = null

    init {
        startNewGame()
    }

    fun startNewGame() {
        actionJob?.cancel()
        actionJob = null
        val events = engine.newGame()
        applyEvents(events)
        rebuildTilesAndEntitiesFromEngine()
    }

    fun dismissDescendPrompt() {
        _uiState.value = _uiState.value.copy(showDescendPrompt = false)
    }

    fun onPlayerAction(action: GameAction) {
        val currentState = _uiState.value
        if (currentState.isGameOver) {
            applyEvents(listOf(GameEvent.Message("The game is over. Start a new game.")))
            return
        }

        if (actionJob?.isActive == true) {
            return
        }

        actionJob = viewModelScope.launch {
            val events = engine.handlePlayerAction(action)
            val slowPlayerPathing = action is GameAction.MoveTo
            applyEventsWithOptionalDelay(events, slowPlayerPathing)
        }
    }

    private suspend fun applyEventsWithOptionalDelay(
        events: List<GameEvent>,
        slowPlayerPathing: Boolean
    ) {
        if (events.isEmpty()) {
            rebuildTilesAndEntitiesFromEngine()
            return
        }

        var boardUpdated = false
        events.forEach { event ->
            applyEvents(listOf(event))
            if (event.shouldRebuildBoard()) {
                rebuildTilesAndEntitiesFromEngine()
                boardUpdated = true
            }
            if (slowPlayerPathing && event is GameEvent.EntityMoved && event.entityId == engine.player.id) {
                delay(PLAYER_PATH_STEP_DELAY_MS)
            }
        }

        if (!boardUpdated) {
            rebuildTilesAndEntitiesFromEngine()
        }
    }

    private fun GameEvent.shouldRebuildBoard(): Boolean = when (this) {
        is GameEvent.EntityMoved,
        is GameEvent.EntityDied,
        is GameEvent.LevelGenerated,
        is GameEvent.PlayerDescended -> true
        else -> false
    }

    private fun applyEvents(events: List<GameEvent>) {
        var updatedState = _uiState.value
        var width = updatedState.width
        var height = updatedState.height
        var hp = updatedState.playerHp
        var maxHp = updatedState.playerMaxHp
        var isGameOver = engine.isGameOver || updatedState.isGameOver
        var messages = updatedState.messages
        var currentFloor = updatedState.currentFloor
        var totalFloors = updatedState.totalFloors
        var showDescendPrompt = updatedState.showDescendPrompt

        events.forEach { event ->
            when (event) {
                is GameEvent.Message -> messages = appendMessage(messages, event.text)
                is GameEvent.EntityAttacked -> {
                    val attacker = resolveEntityName(event.attackerId)
                    val target = resolveEntityName(event.targetId)
                    val text = "$attacker hits $target for ${event.damage} damage"
                    messages = appendMessage(messages, text)
                }
                is GameEvent.EntityDied -> {
                    val name = resolveEntityName(event.entityId)
                    messages = appendMessage(messages, "$name dies.")
                    if (event.entityId == engine.player.id) {
                        isGameOver = true
                    }
                }
                is GameEvent.EntityMoved -> Unit
                is GameEvent.PlayerStatsChanged -> {
                    hp = event.hp
                    maxHp = event.maxHp
                }
                is GameEvent.LevelGenerated -> {
                    width = event.width
                    height = event.height
                    currentFloor = event.floorNumber
                    totalFloors = event.totalFloors
                    showDescendPrompt = false
                }
                is GameEvent.PlayerDescended -> {
                    messages = appendMessage(messages, "You descend deeper into the dungeon.")
                    showDescendPrompt = false
                }
                is GameEvent.PlayerSteppedOnStairs -> {
                    showDescendPrompt = true
                }
                is GameEvent.GameOver -> {
                    isGameOver = true
                    messages = appendMessage(messages, "Your journey ends here.")
                }
            }
        }

        updatedState = updatedState.copy(
            width = width,
            height = height,
            playerHp = hp,
            playerMaxHp = maxHp,
            messages = messages,
            isGameOver = isGameOver,
            currentFloor = currentFloor,
            totalFloors = totalFloors,
            showDescendPrompt = showDescendPrompt
        )
        _uiState.value = updatedState
    }

    private fun rebuildTilesAndEntitiesFromEngine() {
        val level = runCatching { engine.currentLevel }.getOrNull() ?: return
        val tiles = List(level.height) { y ->
            List(level.width) { x ->
                val tile = level.tiles[y][x]
                TileUiModel(
                    x = x,
                    y = y,
                    type = tile.type.name,
                    visible = tile.visible,
                    discovered = tile.discovered
                )
            }
        }

        val entities = engine.getEntitiesSnapshot().map { entity ->
            EntityUiModel(
                id = entity.id,
                name = entity.name,
                x = entity.position.x,
                y = entity.position.y,
                glyph = entity.glyph,
                isPlayer = runCatching { entity.id == engine.player.id }.getOrDefault(false)
            )
        }

        val playerPosition = runCatching { engine.player.position }.getOrNull()

        _uiState.value = _uiState.value.copy(
            tiles = tiles,
            entities = entities,
            playerX = playerPosition?.x ?: _uiState.value.playerX,
            playerY = playerPosition?.y ?: _uiState.value.playerY
        )
    }

    private fun resolveEntityName(entityId: Int): String {
        return runCatching {
            val player = engine.player
            if (player.id == entityId) {
                player.name
            } else {
                engine.currentLevel.entities.firstOrNull { it.id == entityId }?.name
            }
        }.getOrNull() ?: "Entity $entityId"
    }

    private fun appendMessage(current: List<String>, text: String): List<String> {
        return (current + text).takeLast(MAX_LOG_MESSAGES)
    }

    companion object {
        private const val MAX_LOG_MESSAGES = 5
        private const val PLAYER_PATH_STEP_DELAY_MS = 150L
    }
}
