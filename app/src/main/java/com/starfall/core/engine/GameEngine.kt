package com.starfall.core.engine

import com.starfall.core.dungeon.DungeonGenerator
import com.starfall.core.model.Entity
import com.starfall.core.model.Level
import com.starfall.core.model.Player
import com.starfall.core.model.Position
import com.starfall.core.model.PlayerEffectType
import com.starfall.core.model.Stats
import com.starfall.core.model.Tile
import com.starfall.core.model.Item
import com.starfall.core.model.ItemType
import com.starfall.core.overworld.OverworldRegions
import com.starfall.core.progression.MetaProfile
import com.starfall.core.progression.MetaProgression
import com.starfall.core.progression.MetaProgressionState
import com.starfall.core.progression.PlayerProfile
import com.starfall.core.progression.XpManager
import com.starfall.core.progression.toSave
import com.starfall.core.mutation.MutationManager
import com.starfall.core.run.RunManager
import com.starfall.core.save.RunSaveSnapshot
import com.starfall.core.save.SaveManager
import java.util.ArrayDeque
import kotlin.math.abs

/** Facade that coordinates dungeon generation, state, and turn processing. */
class GameEngine(private val dungeonGenerator: DungeonGenerator) {

    lateinit var currentLevel: Level
        private set
    lateinit var player: Player
        private set
    private lateinit var xpManager: XpManager
    private lateinit var mutationManager: MutationManager
    private var metaProgressionState: MetaProgressionState = MetaProgressionState()
    private var metaProfile: MetaProfile = SaveManager.loadMetaProfileModel()
    private var runEndManager: RunEndManager = RunEndManager(metaProgressionState, metaProfile)

    private var turnManager: TurnManager? = null
    var isGameOver: Boolean = false
        private set
    private var totalFloors: Int = RunConfig.MAX_FLOOR
    private var currentFloor: Int = 0
    private val currentlyVisibleTiles: MutableSet<Position> = mutableSetOf()

    /** Starts a brand new game. */
    fun newGame(profile: PlayerProfile = SaveManager.loadMetaProfileModel().toSave().toPlayerProfile()): List<GameEvent> {
        metaProfile = SaveManager.loadMetaProfileModel()
        metaProgressionState = profile.metaProgressionState
        val bonuses = MetaProgression.computeEffectiveBonuses(metaProfile)
        val boostedMaxHp = 20 + bonuses.extraMaxHp
        val playerStats = Stats(maxHp = boostedMaxHp, hp = boostedMaxHp, attack = 5, defense = 2)
        player = Player(
            id = PLAYER_ID,
            name = "Starfarer",
            position = Position(0, 0),
            glyph = '@',
            stats = playerStats
        )
        if (bonuses.extraStartingPotions > 0) {
            grantStartingPotions(bonuses.extraStartingPotions)
        }
        mutationManager = MutationManager(
            optionsRange = run {
                val options = (2 + bonuses.mutationChoicesBonus).coerceAtLeast(1)
                options..options
            }
        )
        xpManager = XpManager(player, mutationManager)
        runEndManager = RunEndManager(metaProgressionState, metaProfile)
        val selectedRegion = metaProfile.lastSelectedRegionId?.let { OverworldRegions.byId[it] }
            ?: OverworldRegions.FALLEN_TITAN
        RunManager.startNewRun(
            profile,
            regionId = selectedRegion.id,
            minFloors = selectedRegion.minFloors,
            maxFloors = selectedRegion.maxFloors,
            player = player,
            metaProfile = metaProfile
        )
        isGameOver = false
        totalFloors = RunManager.maxDepth()
        currentFloor = 0
        return generateNewLevelEvents(GameConfig.DEFAULT_LEVEL_WIDTH, GameConfig.DEFAULT_LEVEL_HEIGHT) +
            listOf(
                GameEvent.InventoryChanged(player.inventorySnapshot()),
                GameEvent.Message("You descend into the Starfall Depths...")
            )
    }

    fun resumeFromSnapshot(snapshot: RunSaveSnapshot, profile: PlayerProfile): List<GameEvent> {
        metaProfile = SaveManager.loadMetaProfileModel()
        metaProgressionState = profile.metaProgressionState
        runEndManager = RunEndManager(metaProgressionState, metaProfile)
        player = snapshot.player.toPlayer()
        val regeneratedLevel = snapshot.dungeon.toDungeon(snapshot.runState.seed)
        currentLevel = regeneratedLevel
        currentLevel.addEntity(player)
        totalFloors = snapshot.runState.maxFloor
        currentFloor = snapshot.runState.currentFloor
        mutationManager = MutationManager()
        xpManager = XpManager(player, mutationManager)
        turnManager = TurnManager(currentLevel, player, xpManager, mutationManager, runEndManager)
        RunManager.continueRun(snapshot, profile)
        isGameOver = snapshot.runState.isFinished
        currentlyVisibleTiles.clear()
        updateFieldOfView()
        return listOf(
            GameEvent.LevelGenerated(
                currentLevel.width,
                currentLevel.height,
                currentFloor,
                totalFloors
            ),
            GameEvent.InventoryChanged(player.inventorySnapshot())
        )
    }

    private fun grantStartingPotions(count: Int) {
        repeat(count) {
            player.addItem(
                Item(
                    id = Level.allocateGlobalItemId(),
                    type = ItemType.HEALING_POTION,
                    position = null
                )
            )
        }
    }

    /** Handles a single player action if the game is still active. */
    fun handlePlayerAction(action: GameAction): List<GameEvent> {
        if (isGameOver || runEndManager.hasEnded()) {
            return listOf(GameEvent.Message("The depths claim you. Start a new game."))
        }

        if (action is GameAction.DescendStairs) {
            return attemptDescend()
        }

        if (action is GameAction.ChooseMutation) {
            return turnManager?.applyMutationChoice(action.mutationId).orEmpty()
        }

        val events = turnManager?.processPlayerAction(action).orEmpty()
        updateFieldOfView()
        if (events.any { it is GameEvent.GameOver || it is GameEvent.RunEnded }) {
            isGameOver = true
        }
        return events
    }

    /** Provides a snapshot of entities for rendering or debugging. */
    fun getEntitiesSnapshot(): List<Entity> = currentLevel.entities.toList()

    /** Provides a snapshot copy of tiles for rendering or debugging. */
    fun getTileSnapshot(): Array<Array<Tile>> =
        Array(currentLevel.height) { y ->
            Array(currentLevel.width) { x -> currentLevel.tiles[y][x].copy() }
        }

    fun getGroundItemsSnapshot(): List<Item> = currentLevel.groundItems.map { it.copy() }

    fun getInventorySnapshot(): List<Item> = player.inventorySnapshot()

    fun isOnFinalFloor(): Boolean = RunManager.isFinalFloor()

    private fun attemptDescend(): List<GameEvent> {
        val stairsPos = currentLevel.stairsDownPosition
        return if (stairsPos != null && stairsPos == player.position) {
            if (RunManager.isFinalFloor()) {
                val result = runEndManager.endRun(true, RunEndCause.FINAL_BOSS_DEFEATED)
                RunManager.onFinalBossDefeated()
                isGameOver = true
                return listOf(GameEvent.RunEnded(result))
            }
            RunManager.onFloorCompleted()
            val events = mutableListOf<GameEvent>(GameEvent.PlayerDescended)
            events += generateNewLevelEvents(currentLevel.width, currentLevel.height)
            events
        } else {
            listOf(GameEvent.Message("You are not standing on stairs."))
        }
    }

    private fun generateNewLevelEvents(width: Int, height: Int): List<GameEvent> {
        if (this::currentLevel.isInitialized) {
            currentLevel.removeEntity(player)
        }
        currentFloor += 1
        RunManager.currentRun?.currentFloor = currentFloor
        runEndManager.recordFloorReached(currentFloor)
        player.activeEffects.removeAll { it.type == PlayerEffectType.STAIRS_COMPASS }
        currentLevel = dungeonGenerator.generate(width, height, currentFloor)
        currentLevel.isFinalFloor = currentFloor >= totalFloors
        currentlyVisibleTiles.clear()
        val spawn = findSpawnPosition(currentLevel)
        player.position = spawn
        currentLevel.addEntity(player)
        turnManager = TurnManager(currentLevel, player, xpManager, mutationManager, runEndManager)
        val events = mutableListOf<GameEvent>()
        events += GameEvent.LevelGenerated(width, height, currentFloor, totalFloors)
        events += GameEvent.PlayerStatsChanged(
            player.stats.hp,
            player.stats.maxHp,
            player.stats.armor,
            player.stats.maxArmor
        )
        events += GameEvent.InventoryChanged(player.inventorySnapshot())
        RunManager.persistSnapshot(player, currentLevel)
        updateFieldOfView()
        return events
    }

    private fun findSpawnPosition(level: Level): Position {
        level.playerSpawnPosition?.let { return it }
        val center = Position(level.width / 2, level.height / 2)
        if (level.isWalkable(center)) return center
        for (y in 0 until level.height) {
            for (x in 0 until level.width) {
                val pos = Position(x, y)
                if (level.isWalkable(pos)) {
                    return pos
                }
            }
        }
        return Position(0, 0)
    }

    fun updateFieldOfView(originOverride: Position? = null) {
        if (!this::currentLevel.isInitialized || !this::player.isInitialized) return
        val level = currentLevel
        clearPreviouslyVisibleTiles(level)

        val origin = originOverride ?: player.position
        val radius = GameConfig.PLAYER_VISION_RADIUS
        val visited = mutableSetOf<Position>()
        val queue = ArrayDeque<Pair<Position, Int>>()
        queue.add(origin to 0)
        while (queue.isNotEmpty()) {
            val (pos, distance) = queue.removeFirst()
            if (!level.inBounds(pos) || distance > radius || !visited.add(pos)) continue
            markVisible(pos, level)
            if (distance == radius || level.tiles[pos.y][pos.x].blocksVision) continue
            val neighbors = listOf(
                Position(pos.x + 1, pos.y),
                Position(pos.x - 1, pos.y),
                Position(pos.x, pos.y + 1),
                Position(pos.x, pos.y - 1),
                Position(pos.x + 1, pos.y + 1),
                Position(pos.x - 1, pos.y - 1),
                Position(pos.x + 1, pos.y - 1),
                Position(pos.x - 1, pos.y + 1)
            )
            neighbors.forEach { neighbor ->
                if (blocksCornerView(pos, neighbor, level)) return@forEach
                queue.add(neighbor to distance + 1)
            }
        }
    }

    private fun blocksCornerView(from: Position, to: Position, level: Level): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y
        if (dx == 0 || dy == 0) return false

        val stepX = Position(from.x + dx, from.y)
        val stepY = Position(from.x, from.y + dy)

        val blocksX = level.inBounds(stepX) && level.tiles[stepX.y][stepX.x].blocksVision
        val blocksY = level.inBounds(stepY) && level.tiles[stepY.y][stepY.x].blocksVision

        return blocksX && blocksY
    }

    private fun clearPreviouslyVisibleTiles(level: Level) {
        if (currentlyVisibleTiles.isEmpty()) return
        val iterator = currentlyVisibleTiles.iterator()
        while (iterator.hasNext()) {
            val position = iterator.next()
            if (level.inBounds(position)) {
                level.tiles[position.y][position.x].visible = false
            }
            iterator.remove()
        }
    }

    private fun markVisible(position: Position, level: Level) {
        if (!level.inBounds(position)) return
        val tile = level.tiles[position.y][position.x]
        tile.visible = true
        tile.discovered = true
        currentlyVisibleTiles += position
    }

    companion object {
        private const val PLAYER_ID = 1
    }
}
