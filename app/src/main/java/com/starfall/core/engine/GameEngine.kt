package com.starfall.core.engine

import com.starfall.core.dungeon.DungeonGenerator
import com.starfall.core.model.Entity
import com.starfall.core.model.Level
import com.starfall.core.model.Player
import com.starfall.core.model.Position
import com.starfall.core.model.Stats
import com.starfall.core.model.Tile
import kotlin.math.abs
import kotlin.random.Random

/** Facade that coordinates dungeon generation, state, and turn processing. */
class GameEngine(private val dungeonGenerator: DungeonGenerator) {

    lateinit var currentLevel: Level
        private set
    lateinit var player: Player
        private set

    private var turnManager: TurnManager? = null
    var isGameOver: Boolean = false
        private set
    private var totalFloors: Int = GameConfig.MIN_DUNGEON_FLOORS
    private var currentFloor: Int = 0

    /** Starts a brand new game. */
    fun newGame(): List<GameEvent> {
        val playerStats = Stats(maxHp = 20, hp = 20, attack = 5, defense = 2)
        player = Player(
            id = PLAYER_ID,
            name = "Starfarer",
            position = Position(0, 0),
            glyph = '@',
            stats = playerStats
        )
        isGameOver = false
        totalFloors = Random.nextInt(GameConfig.MIN_DUNGEON_FLOORS, GameConfig.MAX_DUNGEON_FLOORS + 1)
        currentFloor = 0
        return generateNewLevelEvents(GameConfig.DEFAULT_LEVEL_WIDTH, GameConfig.DEFAULT_LEVEL_HEIGHT) +
            GameEvent.Message("You descend into the Starfall Depths...")
    }

    /** Handles a single player action if the game is still active. */
    fun handlePlayerAction(action: GameAction): List<GameEvent> {
        if (isGameOver) {
            return listOf(GameEvent.Message("The depths claim you. Start a new game."))
        }

        if (action is GameAction.DescendStairs) {
            return attemptDescend()
        }

        val events = turnManager?.processPlayerAction(action).orEmpty()
        updateFieldOfView()
        if (events.any { it is GameEvent.GameOver }) {
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

    private fun attemptDescend(): List<GameEvent> {
        val stairsPos = currentLevel.stairsDownPosition
        return if (stairsPos != null && stairsPos == player.position) {
            if (currentFloor >= totalFloors) {
                return listOf(
                    GameEvent.Message("You have reached the bottom of these depths.")
                )
            }
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
        currentLevel = dungeonGenerator.generate(width, height)
        val spawn = findSpawnPosition(currentLevel)
        player.position = spawn
        currentLevel.addEntity(player)
        turnManager = TurnManager(currentLevel, player)
        val events = mutableListOf<GameEvent>()
        events += GameEvent.LevelGenerated(width, height, currentFloor, totalFloors)
        events += GameEvent.PlayerStatsChanged(player.stats.hp, player.stats.maxHp)
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

    private fun updateFieldOfView() {
        if (!this::currentLevel.isInitialized || !this::player.isInitialized) return
        val level = currentLevel
        for (y in 0 until level.height) {
            for (x in 0 until level.width) {
                level.tiles[y][x].visible = false
            }
        }

        val origin = player.position
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
                }
            }
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
