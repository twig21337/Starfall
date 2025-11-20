package com.starfall.app.game

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import com.starfall.core.dungeon.SimpleDungeonGenerator
import com.starfall.core.engine.GameAction
import com.starfall.core.engine.GameEngine
import com.starfall.core.engine.GameEvent
import com.starfall.core.model.Item
import com.starfall.core.model.Position
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showDescendPrompt = false,
                descendPromptIsExit = false
            )
            val events = withContext(Dispatchers.Default) {
                engine.newGame()
            }
            applyEvents(events)
            rebuildTilesAndEntitiesFromEngine()
        }
    }

    fun dismissDescendPrompt() {
        _uiState.value = _uiState.value.copy(
            showDescendPrompt = false,
            descendPromptIsExit = false
        )
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
            val events = withContext(Dispatchers.Default) {
                engine.handlePlayerAction(action)
            }
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

        var needsFinalBoardSync = false
        events.forEach { event ->
            applyEvents(listOf(event))
            when (event) {
                is GameEvent.EntityMoved -> {
                    applyEntityMovementToUi(event)
                    needsFinalBoardSync = true
                    if (slowPlayerPathing && event.entityId == engine.player.id) {
                        delay(PLAYER_PATH_STEP_DELAY_MS)
                    }
                }
                is GameEvent.EntityDied -> {
                    removeEntityFromUi(event.entityId)
                    needsFinalBoardSync = true
                }
                else -> if (event.requiresImmediateBoardSync()) {
                    rebuildTilesAndEntitiesFromEngine()
                    needsFinalBoardSync = false
                }
            }
        }

        if (needsFinalBoardSync) {
            rebuildTilesAndEntitiesFromEngine()
        }
    }

    private fun GameEvent.requiresImmediateBoardSync(): Boolean = when (this) {
        is GameEvent.LevelGenerated,
        is GameEvent.InventoryChanged,
        is GameEvent.PlayerDescended -> true
        else -> false
    }

    private fun applyEntityMovementToUi(event: GameEvent.EntityMoved) {
        val currentState = _uiState.value
        val updatedEntities = currentState.entities.map { entity ->
            if (entity.id == event.entityId) {
                entity.copy(x = event.to.x, y = event.to.y)
            } else {
                entity
            }
        }
        _uiState.value = currentState.copy(
            entities = updatedEntities,
            playerX = if (event.entityId == engine.player.id) event.to.x else currentState.playerX,
            playerY = if (event.entityId == engine.player.id) event.to.y else currentState.playerY
        )
    }

    private fun removeEntityFromUi(entityId: Int) {
        if (entityId == runCatching { engine.player.id }.getOrNull()) {
            return
        }
        val currentState = _uiState.value
        val updatedEntities = currentState.entities.filterNot { it.id == entityId }
        if (updatedEntities.size == currentState.entities.size) {
            return
        }
        _uiState.value = currentState.copy(entities = updatedEntities)
    }

    private fun applyEvents(events: List<GameEvent>) {
        var updatedState = _uiState.value
        var width = updatedState.width
        var height = updatedState.height
        var hp = updatedState.playerHp
        var maxHp = updatedState.playerMaxHp
        var armor = updatedState.playerArmor
        var maxArmor = updatedState.playerMaxArmor
        var isGameOver = engine.isGameOver
        var messages = updatedState.messages
        var currentFloor = updatedState.currentFloor
        var totalFloors = updatedState.totalFloors
        var showDescendPrompt = updatedState.showDescendPrompt
        var descendPromptIsExit = updatedState.descendPromptIsExit
        var inventory = updatedState.inventory

        if (events.any { it is GameEvent.LevelGenerated }) {
            messages = emptyList()
        }

        events.forEach { event ->
            when (event) {
                is GameEvent.Message -> messages = appendMessage(messages, event.text)
                is GameEvent.EntityAttacked -> {
                    val attacker = resolveEntityName(event.attackerId)
                    val target = resolveEntityName(event.targetId)
                    val text = when {
                        event.wasMiss -> "$attacker misses $target."
                        event.wasCritical -> {
                            val armorNote = if (event.armorDamage > 0) " (damaged ${event.armorDamage} armor)" else ""
                            "$attacker critically hits $target for ${event.damage} damage$armorNote"
                        }
                        event.damage <= 0 -> "$attacker hits $target but deals no damage."
                        else -> {
                            val armorNote = if (event.armorDamage > 0) " (damaged ${event.armorDamage} armor)" else ""
                            "$attacker hits $target for ${event.damage} damage$armorNote"
                        }
                    }
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
                    armor = event.armor
                    maxArmor = event.maxArmor
                }
                is GameEvent.InventoryChanged -> {
                    inventory = mapInventory(event.inventory)
                }
                is GameEvent.LevelGenerated -> {
                    width = event.width
                    height = event.height
                    currentFloor = event.floorNumber
                    totalFloors = event.totalFloors
                    showDescendPrompt = false
                    descendPromptIsExit = false
                    messages = appendMessage(
                        messages,
                        "You arrive on floor ${event.floorNumber} of ${event.totalFloors}."
                    )
                }
                is GameEvent.PlayerDescended -> {
                    messages = appendMessage(messages, "You descend deeper into the dungeon.")
                    showDescendPrompt = false
                    descendPromptIsExit = false
                }
                is GameEvent.PlayerSteppedOnStairs -> {
                    showDescendPrompt = true
                    descendPromptIsExit = engine.isOnFinalFloor()
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
            playerArmor = armor,
            playerMaxArmor = maxArmor,
            messages = messages,
            isGameOver = isGameOver,
            currentFloor = currentFloor,
            totalFloors = totalFloors,
            showDescendPrompt = showDescendPrompt,
            descendPromptIsExit = descendPromptIsExit,
            inventory = inventory
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
            groundItems = mapGroundItems(engine.getGroundItemsSnapshot()),
            inventory = mapInventory(engine.getInventorySnapshot()),
            playerX = playerPosition?.x ?: _uiState.value.playerX,
            playerY = playerPosition?.y ?: _uiState.value.playerY,
            playerHp = runCatching { engine.player.stats.hp }.getOrElse { _uiState.value.playerHp },
            playerMaxHp = runCatching { engine.player.stats.maxHp }.getOrElse { _uiState.value.playerMaxHp },
            playerArmor = runCatching { engine.player.stats.armor }.getOrElse { _uiState.value.playerArmor },
            playerMaxArmor = runCatching { engine.player.stats.maxArmor }.getOrElse { _uiState.value.playerMaxArmor }
        )
    }

    private fun mapInventory(items: List<Item>): List<InventoryItemUiModel> = items.map { item ->
        InventoryItemUiModel(
            id = item.id,
            name = item.displayName,
            icon = item.icon,
            description = item.description,
            isEquipped = item.isEquipped,
            type = item.type.name,
            quantity = item.quantity
        )
    }

    private fun mapGroundItems(items: List<Item>): List<GroundItemUiModel> = items.map { item ->
        val position = item.position ?: Position(0, 0)
        GroundItemUiModel(
            id = item.id,
            name = item.displayName,
            x = position.x,
            y = position.y,
            icon = item.icon,
            type = item.type.name,
            quantity = item.quantity
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
        val updated = current + text
        return if (updated.size > MAX_MESSAGE_HISTORY) {
            updated.takeLast(MAX_MESSAGE_HISTORY)
        } else {
            updated
        }
    }

    companion object {
        private const val PLAYER_PATH_STEP_DELAY_MS = 225L
        private const val MAX_MESSAGE_HISTORY = 120
    }
}
