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
import com.starfall.core.model.Enemy
import com.starfall.core.model.Item
import com.starfall.core.model.ItemType
import com.starfall.core.model.PlayerEffectType
import com.starfall.core.model.Position
import com.starfall.core.mutation.Mutation
import com.starfall.core.mutation.MutationCatalog
import com.starfall.core.progression.XpManager
import com.starfall.core.run.RunManager
import com.starfall.core.save.SaveManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class GameViewModel : ViewModel() {
    private val engine: GameEngine = GameEngine(SimpleDungeonGenerator())
    private val _uiState: MutableState<GameUiState> = mutableStateOf(GameUiState())
    val uiState: State<GameUiState> get() = _uiState
    private val _hudUiState: MutableStateFlow<HudUiState> = MutableStateFlow(HudUiState())
    val hudUiState: StateFlow<HudUiState> = _hudUiState.asStateFlow()
    private var actionJob: Job? = null
    private var lastInventoryTap: InventoryTapInfo? = null

    init {
        bootstrapGame()
    }

    private fun bootstrapGame() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.Default) { RunManager.consumeCachedSnapshot() }
            if (snapshot != null) {
                val profile = SaveManager.loadMetaProfileModel().toSave().toPlayerProfile()
                val events = withContext(Dispatchers.Default) {
                    engine.resumeFromSnapshot(snapshot, profile)
                }
                applyEvents(events)
                rebuildTilesAndEntitiesFromEngine()
                refreshFromGameState()
            } else {
                startNewGame()
            }
        }
    }

    fun selectTab(tab: BottomHudTab) {
        val nextTab = if (_hudUiState.value.selectedTab == tab) {
            null
        } else {
            tab
        }
        _hudUiState.value = _hudUiState.value.copy(selectedTab = nextTab)
    }

    /**
     * Rebuilds the HUD snapshot from core systems without mutating the engine state.
     * It pulls run bounds from [RunManager], player stats/mutations from the active
     * [GameEngine.player].
     */
    fun refreshFromGameState() {
        val player = runCatching { engine.player }.getOrNull()
        val run = RunManager.currentRun
        val level = runCatching { engine.currentLevel }.getOrNull()
        val xpManager = player?.let { XpManager(it) }
        val xpToNext = xpManager?.getRequiredXpForNextLevel() ?: 0

        val inventoryById = player?.inventory?.associateBy { it.id }.orEmpty()

        val weaponCritBonus = player?.equippedWeaponId?.let { weaponId ->
            inventoryById[weaponId]?.weaponTemplate?.critChanceBonus
        } ?: 0.0
        val critChance = ((HUD_BASE_CRIT_CHANCE + weaponCritBonus + (player?.effectCritBonus() ?: 0.0)) * 100)
            .roundToInt()
        val armorDodgePenalty = player?.equippedArmorBySlot?.values?.sumOf { armorId ->
            inventoryById[armorId]?.armorTemplate?.dodgePenalty ?: 0.0
        } ?: 0.0
        val dodgeChance = (((player?.mutationState?.dodgeBonus ?: 0.0) - armorDodgePenalty)
            .coerceAtLeast(0.0) * 100).roundToInt()
        val statusEffects = player?.activeEffects?.map { effect ->
            "${effect.displayName} (${effect.remainingTurns}t)"
        }.orEmpty()
        val mutationLookup = MutationCatalog.all.associateBy { it.id }
        val mutationEntries = player?.mutationState?.acquiredMutationIds?.mapNotNull { id ->
            val mutation = mutationLookup[id] ?: return@mapNotNull null
            MutationEntry(
                name = mutation.name,
                tier = mutation.tier.ordinal + 1,
                shortDescription = mutation.description
            )
        }.orEmpty()

        val newHud = _hudUiState.value.copy(
            currentHp = player?.stats?.hp ?: 0,
            maxHp = player?.stats?.maxHp ?: 0,
            currentFloor = run?.currentFloor ?: level?.depth ?: _hudUiState.value.currentFloor,
            maxFloor = run?.maxFloor ?: RunManager.maxDepth(),
            currentLevel = player?.level ?: 1,
            currentXp = player?.experience ?: 0,
            xpToNext = xpToNext,
            statsPanel = StatsPanelState(
                attack = player?.stats?.attack ?: 0,
                defense = player?.stats?.defense ?: 0,
                armor = player?.stats?.armor ?: 0,
                maxArmor = player?.stats?.maxArmor ?: 0,
                critChance = critChance,
                dodgeChance = dodgeChance,
                statusEffects = statusEffects
            ),
            mutationsPanel = MutationsPanelState(mutations = mutationEntries),
            xpPanel = XpPanelState(
                level = player?.level ?: 1,
                xp = player?.experience ?: 0,
                xpToNext = xpToNext
            ),
            inventoryPanel = InventoryPanelState(
                items = _uiState.value.inventory,
                maxSlots = _uiState.value.maxInventorySlots
            ),
            menuPanel = MenuPanelState(
                canSave = player != null && level != null && run != null
            )
        )
        _hudUiState.value = newHud
    }

    fun saveGame() {
        viewModelScope.launch {
            val player = runCatching { engine.player }.getOrNull()
            val level = runCatching { engine.currentLevel }.getOrNull()
            if (player != null && level != null) {
                withContext(Dispatchers.Default) {
                    RunManager.persistSnapshot(player, level)
                }
            }
        }
    }

    fun startNewGame() {
        actionJob?.cancel()
        actionJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showDescendPrompt = false,
                descendPromptIsExit = false,
                isGameOver = false,
                lastRunResult = null
            )
            val events = withContext(Dispatchers.Default) {
                engine.newGame()
            }
            applyEvents(events)
            rebuildTilesAndEntitiesFromEngine()
            refreshFromGameState()
        }
    }

    fun dismissDescendPrompt() {
        _uiState.value = _uiState.value.copy(
            showDescendPrompt = false,
            descendPromptIsExit = false,
            targetingItemId = null,
            targetingPrompt = null
        )
    }

    fun onPlayerAction(action: GameAction) {
        if (action !is GameAction.ChooseMutation && _uiState.value.pendingMutations.isNotEmpty()) {
            applyEvents(listOf(GameEvent.Message("Choose a mutation before continuing.")))
            return
        }
        if (action is GameAction.InventoryTapLog) {
            logInventoryTap(action)
            return
        }

        val currentState = _uiState.value
        if (currentState.isGameOver) {
            applyEvents(listOf(GameEvent.Message("The game is over. Start a new game.")))
            return
        }

        if (actionJob?.isActive == true) {
            return
        }

        if (action !is GameAction.UseItemOnTile && _uiState.value.targetingItemId != null) {
            cancelTargetingSelection()
        }

        actionJob = viewModelScope.launch {
            val events = withContext(Dispatchers.Default) {
                engine.handlePlayerAction(action)
            }
            val slowPlayerPathing = action is GameAction.MoveTo
            applyEventsWithOptionalDelay(events, slowPlayerPathing)
        }
    }

    fun onMutationSelected(mutationId: String) {
        onPlayerAction(GameAction.ChooseMutation(mutationId))
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
        var pendingBoardSync = false
        var boardSyncedMidLoop = false
        var syncNeededAfterMidLoop = false
        events.forEach { event ->
            applyEvents(listOf(event), refreshHud = false)
            when (event) {
                is GameEvent.EntityMoved -> {
                    applyEntityMovementToUi(event)
                    needsFinalBoardSync = true
                    if (slowPlayerPathing && event.entityId == engine.player.id) {
                        delay(PLAYER_PATH_STEP_DELAY_MS)
                        engine.updateFieldOfView(event.to)
                        rebuildTilesAndEntitiesFromEngine(playerPositionOverride = event.to)
                        boardSyncedMidLoop = true
                        needsFinalBoardSync = false
                    } else if (boardSyncedMidLoop) {
                        syncNeededAfterMidLoop = true
                    }
                }
                is GameEvent.EntityDied -> {
                    removeEntityFromUi(event.entityId)
                    needsFinalBoardSync = true
                    if (boardSyncedMidLoop) {
                        syncNeededAfterMidLoop = true
                    }
                }
                else -> if (event.requiresImmediateBoardSync()) {
                    pendingBoardSync = true
                    if (boardSyncedMidLoop) {
                        syncNeededAfterMidLoop = true
                    }
                }
            }
        }

        val shouldSyncAfterLoop = needsFinalBoardSync || pendingBoardSync
        if (shouldSyncAfterLoop && (!boardSyncedMidLoop || syncNeededAfterMidLoop)) {
            rebuildTilesAndEntitiesFromEngine()
        } else {
            refreshFromGameState()
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
        var newFacing = currentState.playerFacing
        if (event.entityId == engine.player.id) {
            val dx = event.to.x - event.from.x
            newFacing = when {
                dx < 0 -> FacingDirection.LEFT
                dx > 0 -> FacingDirection.RIGHT
                else -> currentState.playerFacing
            }
        }
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
            playerY = if (event.entityId == engine.player.id) event.to.y else currentState.playerY,
            playerFacing = newFacing
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

    private fun applyEvents(events: List<GameEvent>, refreshHud: Boolean = true) {
        var updatedState = _uiState.value
        var width = updatedState.width
        var height = updatedState.height
        var hp = updatedState.playerHp
        var maxHp = updatedState.playerMaxHp
        var armor = updatedState.playerArmor
        var maxArmor = updatedState.playerMaxArmor
        var level = updatedState.playerLevel
        var isGameOver = engine.isGameOver
        var messages = updatedState.messages
        var currentFloor = updatedState.currentFloor
        var totalFloors = updatedState.totalFloors
        var showDescendPrompt = updatedState.showDescendPrompt
        var descendPromptIsExit = updatedState.descendPromptIsExit
        var inventory = updatedState.inventory
        var maxInventorySlots = updatedState.maxInventorySlots
        var levelUpBanner = updatedState.levelUpBanner
        var pendingMutations = updatedState.pendingMutations
        var lastRunResult = updatedState.lastRunResult

        if (events.any { it is GameEvent.LevelGenerated }) {
            messages = emptyList()
        }

        events.forEach { event ->
            when (event) {
                is GameEvent.Message -> {
                    messages = appendMessage(messages, event.text)
                    messages = maybeAppendEquipFailureLog(messages, event.text)
                }
                is GameEvent.EntityAttacked -> {
                    messages = appendMessage(messages, formatAttackLog(event))
                }
                is GameEvent.EntityDied -> {
                    messages = appendMessage(messages, formatDeathLog(event.entityId))
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
                is GameEvent.PlayerLeveledUp -> {
                    level = event.newLevel
                    pendingMutations = mapMutations(event.mutationChoices)
                    levelUpBanner = "Level Up! Reached level ${event.newLevel}"
                    clearBannerSoon()
                }
                is GameEvent.InventoryChanged -> {
                    inventory = mapInventory(event.inventory)
                    maxInventorySlots = runCatching { engine.player.currentInventorySlots() }
                        .getOrElse { maxInventorySlots }
                }
                is GameEvent.MutationApplied -> {
                    pendingMutations = emptyList()
                }
                is GameEvent.LevelGenerated -> {
                    width = event.width
                    height = event.height
                    currentFloor = event.floorNumber
                    totalFloors = event.totalFloors
                    level = runCatching { engine.player.level }.getOrElse { level }
                    showDescendPrompt = false
                    descendPromptIsExit = false
                    pendingMutations = emptyList()
                    levelUpBanner = null
                    cancelTargetingSelection()
                    messages = appendMessage(
                        messages,
                        formatFloorArrivalLog(event.floorNumber, event.totalFloors)
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
                is GameEvent.RunEnded -> {
                    isGameOver = true
                    lastRunResult = event.result
                    messages = appendMessage(
                        messages,
                        if (event.result.isVictory) {
                            "You emerge victorious from this run!"
                        } else {
                            "Your run has ended."
                        }
                    )
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
            inventory = inventory,
            maxInventorySlots = maxInventorySlots,
            compassDirection = computeCompassDirection(),
            playerLevel = level,
            levelUpBanner = levelUpBanner,
            pendingMutations = pendingMutations,
            lastRunResult = lastRunResult
        )
        _uiState.value = updatedState
        if (refreshHud) {
            refreshFromGameState()
        }
    }

    private fun rebuildTilesAndEntitiesFromEngine(playerPositionOverride: Position? = null) {
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

        val enemyIntents = mutableListOf<EnemyIntentUiModel>()
        val entities = engine.getEntitiesSnapshot().map { entity ->
            val overridePos =
                if (playerPositionOverride != null && runCatching { entity.id == engine.player.id }.getOrDefault(false)) {
                    playerPositionOverride
                } else {
                    entity.position
                }
            if (entity is Enemy) {
                val intent = entity.currentIntent
                if (intent != null) {
                    enemyIntents += EnemyIntentUiModel(
                        enemyId = entity.id,
                        enemyName = entity.name,
                        enemyPosition = overridePos,
                        intentType = intent.type,
                        targetTiles = intent.targetTiles,
                        turnsUntilResolve = intent.turnsUntilResolve
                    )
                }
            }
            EntityUiModel(
                id = entity.id,
                name = entity.name,
                x = overridePos.x,
                y = overridePos.y,
                glyph = entity.glyph,
                isPlayer = runCatching { entity.id == engine.player.id }.getOrDefault(false)
            )
        }

        val playerPosition = playerPositionOverride
            ?: runCatching { engine.player.position }.getOrNull()

        val inventorySnapshot = mapInventory(engine.getInventorySnapshot())
        val equippedWeaponKey = inventorySnapshot.firstOrNull {
            it.isEquipped && it.type == ItemType.EQUIPMENT_WEAPON.name
        }?.let(::spriteKeyFor)
        val equippedArmorKey = inventorySnapshot.firstOrNull {
            it.isEquipped && it.type == ItemType.EQUIPMENT_ARMOR.name
        }?.let(::spriteKeyFor)
        val inventorySlots = runCatching { engine.player.currentInventorySlots() }
            .getOrElse { _uiState.value.maxInventorySlots }

        _uiState.value = _uiState.value.copy(
            tiles = tiles,
            entities = entities,
            groundItems = mapGroundItems(engine.getGroundItemsSnapshot()),
            inventory = inventorySnapshot,
            maxInventorySlots = inventorySlots,
            playerX = playerPosition?.x ?: _uiState.value.playerX,
            playerY = playerPosition?.y ?: _uiState.value.playerY,
            playerHp = runCatching { engine.player.stats.hp }.getOrElse { _uiState.value.playerHp },
            playerMaxHp = runCatching { engine.player.stats.maxHp }.getOrElse { _uiState.value.playerMaxHp },
            playerArmor = runCatching { engine.player.stats.armor }.getOrElse { _uiState.value.playerArmor },
            playerMaxArmor = runCatching { engine.player.stats.maxArmor }.getOrElse { _uiState.value.playerMaxArmor },
            playerLevel = runCatching { engine.player.level }.getOrElse { _uiState.value.playerLevel },
            compassDirection = computeCompassDirection(),
            equippedWeaponSpriteKey = equippedWeaponKey,
            equippedArmorSpriteKey = equippedArmorKey,
            enemyIntents = enemyIntents,
            activeDebuffs = mapActiveDebuffs()
        )
        refreshFromGameState()
    }

    private fun spriteKeyFor(item: InventoryItemUiModel): String {
        return item.name.lowercase()
            .replace(" ", "_")
            .replace(Regex("[^a-z0-9_]") , "")
    }

    private fun mapInventory(items: List<Item>): List<InventoryItemUiModel> = items.mapIndexed { idx, item ->
        InventoryItemUiModel(
            id = item.id,
            name = item.displayName,
            icon = item.icon,
            description = item.description,
            isEquipped = item.isEquipped,
            type = item.type.name,
            quantity = item.quantity,
            canEquip = item.type == ItemType.EQUIPMENT_WEAPON || item.type == ItemType.EQUIPMENT_ARMOR,
            requiresTarget = item.type in TARGETED_ITEMS,
            slotIndex = if (item.inventoryIndex >= 0) item.inventoryIndex else idx
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

    private fun mapActiveDebuffs(): List<PlayerDebuffUiModel> {
        val player = runCatching { engine.player }.getOrNull() ?: return emptyList()
        val activeDebuffTypes = player.activeEffects.map { it.type }.toSet()
        val preferredOrder = listOf(
            PlayerEffectType.FROZEN,
            PlayerEffectType.CHILLED,
            PlayerEffectType.POISONED,
            PlayerEffectType.WEAKENED
        )

        return preferredOrder
            .filter { it in activeDebuffTypes }
            .mapNotNull { effectType ->
                val label = when (effectType) {
                    PlayerEffectType.POISONED -> "Poisoned"
                    PlayerEffectType.CHILLED -> "Chilled"
                    PlayerEffectType.FROZEN -> "Frozen"
                    PlayerEffectType.WEAKENED -> "Weakened"
                    else -> null
                }
                label?.let { PlayerDebuffUiModel(type = effectType, label = it) }
            }
    }

    fun prepareTargetedItem(itemId: Int) {
        val item = engine.getInventorySnapshot().firstOrNull { it.id == itemId }
        val prompt = if (item != null) {
            "Select a tile or enemy to throw ${item.displayName} at."
        } else {
            "Select a tile or enemy to throw."
        }
        val updatedMessages = appendMessage(_uiState.value.messages, prompt)
        _uiState.value = _uiState.value.copy(
            targetingItemId = itemId,
            targetingPrompt = prompt,
            messages = updatedMessages
        )
    }

    fun onTargetSelected(x: Int, y: Int) {
        val itemId = _uiState.value.targetingItemId ?: return
        if (actionJob?.isActive == true) return

        actionJob = viewModelScope.launch {
            val events = withContext(Dispatchers.Default) {
                engine.handlePlayerAction(GameAction.UseItemOnTile(itemId, x, y))
            }
            cancelTargetingSelection()
            applyEventsWithOptionalDelay(events, slowPlayerPathing = false)
        }
    }

    private fun cancelTargetingSelection() {
        _uiState.value = _uiState.value.copy(targetingItemId = null, targetingPrompt = null)
    }

    private fun logInventoryTap(action: GameAction.InventoryTapLog) {
        val inventorySnapshot = engine.getInventorySnapshot()
        val snapshotText = formatInventorySnapshot(inventorySnapshot)
//        val tapMessage =
//            "Inventory tap: row=${action.row} col=${action.col} index=${action.index} itemId=${action.itemId} type=${action.itemType} inventory=$snapshotText"
        lastInventoryTap = InventoryTapInfo(
            row = action.row,
            col = action.col,
            index = action.index,
            itemId = action.itemId,
            itemType = action.itemType,
            snapshot = snapshotText
        )
    }

    private fun computeCompassDirection(): String? {
        val player = runCatching { engine.player }.getOrNull() ?: return null
        if (!player.hasEffect(PlayerEffectType.STAIRS_COMPASS)) return null
        val stairs = runCatching { engine.currentLevel.stairsDownPosition }.getOrNull() ?: return null
        val dx = stairs.x - player.position.x
        val dy = stairs.y - player.position.y
        if (dx == 0 && dy == 0) return "Here"

        val vertical = when {
            dy < 0 -> "N"
            dy > 0 -> "S"
            else -> ""
        }
        val horizontal = when {
            dx < 0 -> "W"
            dx > 0 -> "E"
            else -> ""
        }

        return "$vertical$horizontal".ifEmpty { null }
    }

    private fun mapMutations(choices: List<Mutation>): List<MutationUiModel> = choices.map {
        MutationUiModel(
            id = it.id,
            name = it.name,
            description = it.description,
            tier = it.tier.name
        )
    }

    private fun clearBannerSoon() {
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(levelUpBanner = null)
        }
    }

    private fun resolveEntityName(entityId: Int): String {
        if (entityId == -1) return "Hazard"
        val cachedName = _uiState.value.entities.firstOrNull { it.id == entityId }?.name
        if (cachedName != null) return cachedName
        return runCatching {
            val player = engine.player
            if (player.id == entityId) {
                player.name
            } else {
                engine.currentLevel.entities.firstOrNull { it.id == entityId }?.name
            }
        }.getOrNull() ?: "Entity $entityId"
    }

    private fun maybeAppendEquipFailureLog(currentMessages: List<String>, eventText: String): List<String> {
        if (eventText != "That item can't be equipped.") return currentMessages
        val info = lastInventoryTap ?: return currentMessages
        if (!info.itemType.contains("weapon", ignoreCase = true) && !info.itemType.contains("armor", ignoreCase = true)) {
            return currentMessages
        }

        val inventorySnapshot = engine.getInventorySnapshot()
        val tappedItemName = inventorySnapshot.firstOrNull { it.id == info.itemId }?.displayName
            ?: "Item ${info.itemId}"
//        val detailMessage =
//            "Equip failure tap: row=${info.row} col=${info.col} index=${info.index} item=$tappedItemName (id=${info.itemId}, type=${info.itemType}) inventory=${info.snapshot}"
        return currentMessages
    }

    private fun formatAttackLog(event: GameEvent.EntityAttacked): String {
        val attacker = resolveEntityName(event.attackerId)
        val target = resolveEntityName(event.targetId)
        val armorNote = if (event.armorDamage > 0) " (damaged ${event.armorDamage} armor)" else ""
        return when {
            event.wasMiss -> "$attacker misses $target."
            event.wasCritical -> "$attacker critically hits $target for ${event.damage} damage$armorNote"
            event.damage <= 0 -> "$attacker hits $target but deals no damage."
            else -> "$attacker hits $target for ${event.damage} damage$armorNote"
        }
    }

    private fun formatDeathLog(entityId: Int): String {
        val name = resolveEntityName(entityId)
        return "$name dies."
    }

    private fun formatFloorArrivalLog(floorNumber: Int, totalFloors: Int): String {
        return "You arrive on floor $floorNumber of $totalFloors."
    }

    private fun formatInventorySnapshot(items: List<Item>): String {
        return items.mapIndexed { idx, item ->
            val equippedFlag = if (item.isEquipped) "E" else "-"
            "[$idx:${item.displayName}(id=${item.id},type=${item.type.name},qty=${item.quantity},eq=$equippedFlag,slot=${item.inventoryIndex})]"
        }.joinToString(separator = " ")
    }

    private fun appendMessage(current: List<String>, text: String): List<String> {
        val updated = current + text
        return if (updated.size > MAX_MESSAGE_HISTORY) {
            updated.takeLast(MAX_MESSAGE_HISTORY)
        } else {
            updated
        }
    }

    private data class InventoryTapInfo(
        val row: Int,
        val col: Int,
        val index: Int,
        val itemId: Int,
        val itemType: String,
        val snapshot: String
    )

    companion object {
        private const val PLAYER_PATH_STEP_DELAY_MS = 225L
        private const val MAX_MESSAGE_HISTORY = 120
        private const val HUD_BASE_CRIT_CHANCE = 0.05
        private val TARGETED_ITEMS = setOf(
            ItemType.VOIDFLARE_ORB,
            ItemType.FROSTSHARD_ORB,
            ItemType.STARSPIKE_DART
        )
    }
}
