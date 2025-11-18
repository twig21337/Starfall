package com.starfall.core.engine

import com.starfall.core.model.Enemy
import com.starfall.core.model.EnemyBehaviorType
import com.starfall.core.model.Entity
import com.starfall.core.model.Level
import com.starfall.core.model.Player
import com.starfall.core.model.Position
import kotlin.math.abs
import kotlin.math.max

/** Orchestrates turn-by-turn sequencing for the player and enemies. */
class TurnManager(private val level: Level, private val player: Player) {

    /** Processes the player's action followed by all enemies. */
    fun processPlayerAction(action: GameAction): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        var actionConsumed = false
        when (action) {
            is GameAction.Move -> {
                val destination = action.direction.applyTo(player.position)
                if (!level.inBounds(destination)) {
                    events += GameEvent.Message("You cannot move there.")
                } else {
                    val occupant = level.getEntityAt(destination)
                    when {
                        occupant == null && level.isWalkable(destination) -> {
                            val from = player.position
                            level.moveEntity(player, destination)
                            events += GameEvent.EntityMoved(player.id, from, destination)
                            actionConsumed = true
                        }
                        occupant != null && occupant != player -> {
                            performAttack(player, occupant, events)
                            actionConsumed = true
                        }
                        else -> {
                            events += GameEvent.Message("Something blocks your path.")
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

        if (actionConsumed && !player.isDead()) {
            events += processEnemiesTurn()
        }
        return events
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
}
