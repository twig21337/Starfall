package com.starfall.core.engine

import com.starfall.core.model.Enemy
import com.starfall.core.model.EnemyBehaviorType
import com.starfall.core.model.Entity
import com.starfall.core.model.Level
import com.starfall.core.model.Player
import com.starfall.core.model.Position
import com.starfall.core.model.TileType
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.max

/** Orchestrates turn-by-turn sequencing for the player and enemies. */
class TurnManager(private val level: Level, private val player: Player) {

    /** Processes the player's action followed by all enemies. */
    fun processPlayerAction(action: GameAction): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        var actionConsumed = false
        var enemyTurnsHandled = false
        when (action) {
            is GameAction.Move -> {
                val destination = action.direction.applyTo(player.position)
                val result = attemptPlayerStep(destination, events)
                if (
                    result == MoveStepResult.MOVED ||
                    result == MoveStepResult.ATTACKED ||
                    result == MoveStepResult.REACHED_STAIRS
                ) {
                    actionConsumed = true
                }
            }
            is GameAction.MoveTo -> {
                val target = Position(action.x, action.y)
                if (!level.inBounds(target)) {
                    events += GameEvent.Message("That tile is outside the dungeon.")
                } else if (target == player.position) {
                    events += GameEvent.Message("You are already standing there.")
                } else {
                    val occupant = level.getEntityAt(target)
                    val isTargetingEntity = occupant != null && occupant != player
                    if (!isTargetingEntity && !level.isWalkable(target)) {
                        events += GameEvent.Message("That destination is blocked.")
                    } else {
                        val path = findPath(player.position, target)
                        if (path == null) {
                            events += GameEvent.Message("No path leads there.")
                        } else {
                            val steps = path.drop(1)
                            val (consumed, handledEnemyTurns) = followPath(steps, events)
                            actionConsumed = consumed
                            enemyTurnsHandled = handledEnemyTurns
                        }
                    }
                }
            }
            GameAction.Wait -> {
                events += GameEvent.Message("You wait and listen.")
                actionConsumed = true
            }
            GameAction.DescendStairs -> {
                // Descending is handled by the engine layer; treat as no-op here.
            }
        }

        if (actionConsumed && !player.isDead() && !enemyTurnsHandled) {
            events += processEnemiesTurn()
        }
        return events
    }

    private fun followPath(
        steps: List<Position>,
        events: MutableList<GameEvent>
    ): Pair<Boolean, Boolean> {
        var consumed = false
        var handledEnemyTurns = false

        for (destination in steps) {
            val result = attemptPlayerStep(destination, events)
            when (result) {
                MoveStepResult.MOVED -> consumed = true
                MoveStepResult.ATTACKED -> {
                    consumed = true
                    if (!player.isDead()) {
                        val enemyEvents = processEnemiesTurn()
                        if (enemyEvents.isNotEmpty()) {
                            events += enemyEvents
                        }
                        handledEnemyTurns = true
                    }
                    return consumed to handledEnemyTurns
                }
                MoveStepResult.REACHED_STAIRS -> {
                    consumed = true
                    val enemyEvents = processEnemiesTurn()
                    if (enemyEvents.isNotEmpty()) {
                        events += enemyEvents
                    }
                    handledEnemyTurns = true
                    return consumed to handledEnemyTurns
                }
                MoveStepResult.BLOCKED -> return consumed to handledEnemyTurns
            }

            if (player.isDead()) {
                return consumed to handledEnemyTurns
            }

            val enemyEvents = processEnemiesTurn()
            if (enemyEvents.isNotEmpty()) {
                events += enemyEvents
            }
            handledEnemyTurns = true

            if (player.isDead()) {
                return consumed to handledEnemyTurns
            }
        }

        return consumed to handledEnemyTurns
    }

    private fun attemptPlayerStep(
        destination: Position,
        events: MutableList<GameEvent>
    ): MoveStepResult {
        if (!level.inBounds(destination)) {
            events += GameEvent.Message("You cannot move there.")
            return MoveStepResult.BLOCKED
        }

        val occupant = level.getEntityAt(destination)
        return when {
            occupant == null && level.isWalkable(destination) -> {
                val from = player.position
                level.moveEntity(player, destination)
                events += GameEvent.EntityMoved(player.id, from, destination)
                val tile = level.getTile(destination)
                if (tile.type == TileType.STAIRS_DOWN) {
                    events += GameEvent.PlayerSteppedOnStairs
                    MoveStepResult.REACHED_STAIRS
                } else {
                    MoveStepResult.MOVED
                }
            }
            occupant != null && occupant != player -> {
                performAttack(player, occupant, events)
                MoveStepResult.ATTACKED
            }
            else -> {
                events += GameEvent.Message("Something blocks your path.")
                MoveStepResult.BLOCKED
            }
        }
    }

    private fun processEnemiesTurn(): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        val enemies = level.entities.filterIsInstance<Enemy>().toList()
        for (enemy in enemies) {
            if (enemy.isDead()) continue
            when (enemy.behaviorType) {
                EnemyBehaviorType.SIMPLE_CHASER -> handleSimpleChaser(enemy, events)
                EnemyBehaviorType.PASSIVE -> {}
                EnemyBehaviorType.FLEEING -> {}
            }
            if (player.isDead()) {
                events += GameEvent.GameOver
                break
            }
        }
        return events
    }

    private fun handleSimpleChaser(enemy: Enemy, events: MutableList<GameEvent>) {
        val dx = player.position.x - enemy.position.x
        val dy = player.position.y - enemy.position.y
        if (abs(dx) + abs(dy) == 1) {
            performAttack(enemy, player, events)
            return
        }

        val preferredAxisHorizontal = abs(dx) >= abs(dy)
        val attemptedPositions = mutableListOf<Position>()
        if (preferredAxisHorizontal) {
            val stepX = dx.sign()
            if (stepX != 0) attemptedPositions += enemy.position.translated(stepX, 0)
            val stepY = dy.sign()
            if (stepY != 0) attemptedPositions += enemy.position.translated(0, stepY)
        } else {
            val stepY = dy.sign()
            if (stepY != 0) attemptedPositions += enemy.position.translated(0, stepY)
            val stepX = dx.sign()
            if (stepX != 0) attemptedPositions += enemy.position.translated(stepX, 0)
        }

        for (pos in attemptedPositions) {
            if (level.isWalkable(pos)) {
                val from = enemy.position
                level.moveEntity(enemy, pos)
                events += GameEvent.EntityMoved(enemy.id, from, pos)
                return
            }
        }
    }

    private fun performAttack(attacker: Entity, target: Entity, events: MutableList<GameEvent>) {
        val baseDamage = max(1, attacker.stats.attack - target.stats.defense)
        val damage = target.stats.takeDamage(baseDamage)
        events += GameEvent.EntityAttacked(attacker.id, target.id, damage)
        if (target === player) {
            events += GameEvent.PlayerStatsChanged(player.stats.hp, player.stats.maxHp)
        }
        if (target.isDead()) {
            level.removeEntity(target)
            events += GameEvent.EntityDied(target.id)
            if (target === player) {
                events += GameEvent.GameOver
            }
        }
    }

    private fun Int.sign(): Int = when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }

    private fun findPath(start: Position, goal: Position): List<Position>? {
        if (!level.inBounds(goal)) return null
        val frontier = ArrayDeque<Position>()
        val cameFrom = mutableMapOf<Position, Position?>()
        frontier.add(start)
        cameFrom[start] = null

        while (frontier.isNotEmpty()) {
            val current = frontier.removeFirst()
            if (current == goal) break
            for (neighbor in neighbors(current)) {
                if (!level.inBounds(neighbor)) continue
                if (neighbor != goal && !level.isWalkable(neighbor)) continue
                if (cameFrom.containsKey(neighbor)) continue
                frontier.add(neighbor)
                cameFrom[neighbor] = current
            }
        }

        if (!cameFrom.containsKey(goal)) return null

        val path = mutableListOf<Position>()
        var current: Position? = goal
        while (current != null) {
            path.add(current)
            current = cameFrom[current]
        }
        return path.asReversed()
    }

    private fun neighbors(position: Position): List<Position> = listOf(
        position.translated(1, 0),
        position.translated(-1, 0),
        position.translated(0, 1),
        position.translated(0, -1)
    )

    private enum class MoveStepResult {
        MOVED,
        ATTACKED,
        REACHED_STAIRS,
        BLOCKED
    }
}
