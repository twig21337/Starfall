package com.starfall.core.engine

import com.starfall.core.dungeon.DungeonGenerator
import com.starfall.core.model.Entity
import com.starfall.core.model.Level
import com.starfall.core.model.Player
import com.starfall.core.model.Position
import com.starfall.core.model.Stats
import com.starfall.core.model.Tile

/** Facade that coordinates dungeon generation, state, and turn processing. */
class GameEngine(private val dungeonGenerator: DungeonGenerator) {

    lateinit var currentLevel: Level
        private set
    lateinit var player: Player
        private set

    private var turnManager: TurnManager? = null
    var isGameOver: Boolean = false
        private set

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
        currentLevel = dungeonGenerator.generate(width, height)
        val spawn = findSpawnPosition(currentLevel)
        player.position = spawn
        currentLevel.addEntity(player)
        turnManager = TurnManager(currentLevel, player)
        val events = mutableListOf<GameEvent>()
        events += GameEvent.LevelGenerated(width, height)
        events += GameEvent.PlayerStatsChanged(player.stats.hp, player.stats.maxHp)
        return events
    }

    private fun findSpawnPosition(level: Level): Position {
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

    companion object {
        private const val PLAYER_ID = 1
    }
}
