package com.starfall.core.engine

import com.starfall.core.model.Enemy
import com.starfall.core.model.EnemyBehaviorType
import com.starfall.core.model.Entity
import com.starfall.core.model.Level
import com.starfall.core.model.Player
import com.starfall.core.model.Position
import com.starfall.core.model.ItemType
import com.starfall.core.model.TileType
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.max

/** Orchestrates turn-by-turn sequencing for the player and enemies. */
class TurnManager(private val level: Level, private val player: Player) {

    private var turnCounter: Int = 0
    private val enemyLastSeenTurn: MutableMap<Int, Int> = mutableMapOf()

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
            is GameAction.UseItem -> {
                val potion = player.inventory.firstOrNull { it.id == action.itemId && it.type == ItemType.HEALING_POTION }
                if (potion != null) {
                    val healed = player.consumePotion(action.itemId)
                    events += GameEvent.Message("You drink a potion and heal $healed HP.")
                    events += GameEvent.PlayerStatsChanged(
                        player.stats.hp,
                        player.stats.maxHp,
                        player.stats.armor,
                        player.stats.maxArmor
                    )
                    events += GameEvent.InventoryChanged(player.inventorySnapshot())
                    actionConsumed = true
                } else {
                    events += GameEvent.Message("You don't have that item.")
                }
            }
            is GameAction.EquipItem -> {
                val equipped = player.equip(action.itemId)
                if (equipped) {
                    val item = player.inventory.firstOrNull { it.id == action.itemId }
                    val name = item?.type?.displayName ?: "Item"
                    events += GameEvent.Message("You equip $name.")
                    events += GameEvent.PlayerStatsChanged(
                        player.stats.hp,
                        player.stats.maxHp,
                        player.stats.armor,
                        player.stats.maxArmor
                    )
                    events += GameEvent.InventoryChanged(player.inventorySnapshot())
                    actionConsumed = true
                } else {
                    events += GameEvent.Message("You can't equip that.")
                }
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
                val item = level.getItemAt(destination)
                if (item != null) {
                    level.removeItem(item)
                    player.addItem(item)
                    events += GameEvent.Message("You pick up ${item.type.displayName}.")
                    events += GameEvent.InventoryChanged(player.inventorySnapshot())
                }
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
        turnCounter++
        val events = mutableListOf<GameEvent>()
        val enemies = level.entities.filterIsInstance<Enemy>().toList()
        for (enemy in enemies) {
            if (enemy.isDead()) continue
            when (enemy.behaviorType) {
                EnemyBehaviorType.SIMPLE_CHASER -> handleSimpleChaser(enemy, events)
                EnemyBehaviorType.PASSIVE -> {}
                EnemyBehaviorType.FLEEING -> {}
            }
            if (enemy.isDead()) {
                enemyLastSeenTurn.remove(enemy.id)
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

        val canSeePlayer = hasLineOfSight(enemy.position, player.position, level)
        if (canSeePlayer) {
            enemyLastSeenTurn[enemy.id] = turnCounter
        }

        val turnsSinceSeen = enemyLastSeenTurn[enemy.id]?.let { turnCounter - it }
        val shouldChase = canSeePlayer || (turnsSinceSeen != null && turnsSinceSeen <= 5)
        if (!shouldChase) {
            return
        }
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
        val targetArmorBefore = target.stats.armor
        val damage = target.stats.takeDamage(baseDamage)
        events += GameEvent.EntityAttacked(attacker.id, target.id, damage)

        if (target === player) {
            val armorBroken = targetArmorBefore > 0 && player.stats.armor <= 0 && player.equippedArmorId != null
            if (armorBroken) {
                player.breakEquippedArmor()
                events += GameEvent.Message("Your armor is destroyed.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
            }

            events += GameEvent.PlayerStatsChanged(
                player.stats.hp,
                player.stats.maxHp,
                player.stats.armor,
                player.stats.maxArmor
            )
        }
        if (target.isDead()) {
            level.removeEntity(target)
            events += GameEvent.EntityDied(target.id)
            if (target is Enemy) {
                enemyLastSeenTurn.remove(target.id)
            }
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

    private enum class MoveStepResult {
        MOVED,
        ATTACKED,
        REACHED_STAIRS,
        BLOCKED
    }
}
