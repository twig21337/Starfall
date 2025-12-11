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
import com.starfall.core.progression.MetaProgressionState
import com.starfall.core.progression.PlayerProfile
import com.starfall.core.progression.XpManager
import com.starfall.core.mutation.MutationManager
import com.starfall.core.run.RunManager
import com.starfall.core.save.SaveManager
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
    private var metaProfile = SaveManager.loadMetaProfile()
    private var runEndManager: RunEndManager = RunEndManager(metaProgressionState)

    private var turnManager: TurnManager? = null
    var isGameOver: Boolean = false
        private set
    private var totalFloors: Int = RunConfig.MAX_FLOOR
    private var currentFloor: Int = 0
    private val currentlyVisibleTiles: MutableSet<Position> = mutableSetOf()

    /** Starts a brand new game. */
    fun newGame(profile: PlayerProfile = metaProfile.toPlayerProfile()): List<GameEvent> {
        metaProgressionState = profile.metaProgressionState
        val playerStats = Stats(maxHp = 20, hp = 20, attack = 5, defense = 2)
        player = Player(
            id = PLAYER_ID,
            name = "Starfarer",
            position = Position(0, 0),
            glyph = '@',
            stats = playerStats
        )
        mutationManager = MutationManager()
        xpManager = XpManager(player, mutationManager)
        runEndManager = RunEndManager(metaProgressionState)
        RunManager.startNewRun(profile, player = player, metaProfile = metaProfile)
        isGameOver = false
        totalFloors = RunManager.maxDepth()
        currentFloor = 0
        return generateNewLevelEvents(GameConfig.DEFAULT_LEVEL_WIDTH, GameConfig.DEFAULT_LEVEL_HEIGHT) +
            listOf(
                GameEvent.InventoryChanged(player.inventorySnapshot()),
                GameEvent.Message("You descend into the Starfall Depths...")
            )
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
                return listOf(
                    GameEvent.Message("You have reached the bottom of these depths.")
                )
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
        val radiusSquared = radius * radius
        for (y in (origin.y - radius)..(origin.y + radius)) {
            for (x in (origin.x - radius)..(origin.x + radius)) {
                val pos = Position(x, y)
                if (!level.inBounds(pos)) continue
                val dx = origin.x - x
                val dy = origin.y - y
                if (dx * dx + dy * dy > radiusSquared) continue
                if (hasLineOfSight(origin, pos, level)) {
                    val tile = level.tiles[y][x]
                    tile.visible = true
                    tile.discovered = true
                    currentlyVisibleTiles += pos
                }
            }
        }
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

    private fun hasLineOfSight(start: Position, end: Position, level: Level): Boolean {
        var x0 = start.x
        var y0 = start.y
        val x1 = end.x
        val y1 = end.y
        var dx = abs(x1 - x0)
        var dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            if (x0 == x1 && y0 == y1) {
                return true
            }
            if (!(x0 == start.x && y0 == start.y)) {
                val tile = level.tiles[y0][x0]
                if (tile.blocksVision) {
                    return false
                }
            }
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x0 += sx
            }
            if (e2 < dx) {
                err += dx
                y0 += sy
            }
        }
    }

    companion object {
        private const val PLAYER_ID = 1
    }
}
