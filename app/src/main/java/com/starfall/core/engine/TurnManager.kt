package com.starfall.core.engine

import com.starfall.core.model.Enemy
import com.starfall.core.model.EnemyBehaviorType
import com.starfall.core.model.Entity
import com.starfall.core.model.Level
import com.starfall.core.model.Player
import com.starfall.core.model.Position
import com.starfall.core.model.Item
import com.starfall.core.model.ItemLootTable
import com.starfall.core.model.ItemType
import com.starfall.core.model.TileType
import com.starfall.core.model.PlayerEffect
import com.starfall.core.model.PlayerEffectType
import com.starfall.core.items.WeaponTemplate
import com.starfall.core.items.LootGenerator
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

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
            GameAction.PickUp -> {
                val collected = attemptPickupAt(player.position, events)
                if (!collected) {
                    events += GameEvent.Message("There is nothing to pick up.")
                }
                actionConsumed = collected
            }
            GameAction.DescendStairs -> {
                // Descending is handled by the engine layer; treat as no-op here.
            }
            is GameAction.UseItem -> {
                val item = player.inventory.firstOrNull { it.id == action.itemId }
                if (item != null) {
                    val used = useConsumable(item, events)
                    if (used) {
                        actionConsumed = true
                    }
                } else {
                    events += GameEvent.Message("You don't have that item.")
                }
            }
            is GameAction.UseItemOnTile -> {
                val item = player.inventory.firstOrNull { it.id == action.itemId }
                if (item != null) {
                    val used = useTargetedConsumable(item, Position(action.x, action.y), events)
                    if (used) {
                        actionConsumed = true
                    }
                } else {
                    events += GameEvent.Message("You don't have that item.")
                }
            }
            is GameAction.EquipItem -> {
                val item = player.inventory.firstOrNull { it.id == action.itemId }
                val equipped = player.equip(action.itemId)
                if (equipped) {
                    val name = item?.displayName ?: "Item"
                    val updatedItem = player.inventory.firstOrNull { it.id == action.itemId }
                    val message = if (updatedItem?.isEquipped == true) {
                        "You equip $name."
                    } else {
                        "You unequip $name."
                    }
                    events += GameEvent.Message(message)
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

            is GameAction.DiscardItem -> {
                val item = player.inventory.firstOrNull { it.id == action.itemId }
                if (item != null) {
                    val wasEquipped = item.isEquipped
                    val removedCount = player.removeItemQuantity(item.id, action.quantity)
                    if (removedCount > 0) {
                        val quantityText = if (removedCount > 1) " (x$removedCount)" else ""
                        events += GameEvent.Message("You discard ${item.displayName}$quantityText.")
                        if (wasEquipped && (item.weaponTemplate != null || item.armorTemplate != null)) {
                            events += GameEvent.PlayerStatsChanged(
                                player.stats.hp,
                                player.stats.maxHp,
                                player.stats.armor,
                                player.stats.maxArmor
                            )
                        }
                        events += GameEvent.InventoryChanged(player.inventorySnapshot())
                        actionConsumed = true
                    } else {
                        events += GameEvent.Message("You don't have that item.")
                    }
                } else {
                    events += GameEvent.Message("You don't have that item.")
                }
            }
        }

        if (actionConsumed) {
            val effectMessages = player.tickEffects()
            effectMessages.forEach { events += GameEvent.Message(it) }
            events += GameEvent.PlayerStatsChanged(
                player.stats.hp,
                player.stats.maxHp,
                player.stats.armor,
                player.stats.maxArmor
            )
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
        if (occupant != null && occupant != player && player.hasEffect(PlayerEffectType.SLIPSHADOW)) {
            val dx = destination.x - player.position.x
            val dy = destination.y - player.position.y
            if (abs(dx) + abs(dy) == 1) {
                val passThrough = destination.translated(dx, dy)
                if (level.inBounds(passThrough) && level.isWalkable(passThrough) && level.getEntityAt(passThrough) == null) {
                    val from = player.position
                    level.moveEntity(player, passThrough)
                    events += GameEvent.EntityMoved(player.id, from, passThrough)
                    val reachedStairs = handleArrival(passThrough, events)
                    return if (reachedStairs) MoveStepResult.REACHED_STAIRS else MoveStepResult.MOVED
                }
            }
        }

        return when {
            occupant == null && level.isWalkable(destination) -> {
                val from = player.position
                level.moveEntity(player, destination)
                events += GameEvent.EntityMoved(player.id, from, destination)
                val reachedStairs = handleArrival(destination, events)
                if (reachedStairs) MoveStepResult.REACHED_STAIRS else MoveStepResult.MOVED
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

    private fun collectGroundItem(item: Item, events: MutableList<GameEvent>) {
        if (item.type == ItemType.HEALING_POTION) {
            val currentCount = player.inventory
                .filter { it.type == ItemType.HEALING_POTION }
                .sumOf { it.quantity }
            val capacity = 5 - currentCount
            if (capacity <= 0) {
                events += GameEvent.Message("You can't carry more healing potions (max 5).")
                return
            }

            val toTake = item.quantity.coerceAtMost(capacity)
            val remaining = item.quantity - toTake
            val inventoryItem = item.copy(quantity = toTake)
            player.addItem(inventoryItem)

            level.removeItem(item)
            if (remaining > 0) {
                val leftover = item.copy(quantity = remaining)
                level.addItem(leftover)
            }

            val quantityText = if (toTake > 1) " (x$toTake)" else ""
            events += GameEvent.Message("You pick up ${item.displayName}$quantityText.")
            events += GameEvent.InventoryChanged(player.inventorySnapshot())
            return
        }

        level.removeItem(item)
        player.addItem(item)
        val quantityText = if (item.quantity > 1) " (x${item.quantity})" else ""
        events += GameEvent.Message("You pick up ${item.displayName}$quantityText.")
        events += GameEvent.InventoryChanged(player.inventorySnapshot())
    }

    private fun attemptPickupAt(position: Position, events: MutableList<GameEvent>): Boolean {
        val itemsHere = level.getItemsAt(position)
        val itemToCollect = itemsHere.firstOrNull() ?: return false
        collectGroundItem(itemToCollect, events)
        return true
    }

    private fun handleArrival(position: Position, events: MutableList<GameEvent>): Boolean {
        attemptPickupAt(position, events)
        val tile = level.getTile(position)
        val onStairs = tile.type == TileType.STAIRS_DOWN
        if (onStairs) {
            events += GameEvent.PlayerSteppedOnStairs
        }
        return onStairs
    }

    private fun useConsumable(item: Item, events: MutableList<GameEvent>): Boolean {
        return when (item.type) {
            ItemType.HEALING_POTION -> {
                val healed = player.consumePotion(item.id)
                events += GameEvent.Message("You recover from your wounds, healing $healed HP.")
                events += GameEvent.PlayerStatsChanged(
                    player.stats.hp,
                    player.stats.maxHp,
                    player.stats.armor,
                    player.stats.maxArmor
                )
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.TITANBLOOD_TONIC -> {
                consumeStack(item)
                player.addEffect(PlayerEffect(PlayerEffectType.TITANBLOOD, remainingTurns = 25, magnitude = 3, critBonus = 0.10))
                events += GameEvent.Message("Titanblood surges through you, empowering your strikes.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.IRONBARK_INFUSION -> {
                consumeStack(item)
                val buffer = Random.nextInt(15, 26)
                player.addEffect(PlayerEffect(PlayerEffectType.IRONBARK_SHIELD, remainingTurns = 40, magnitude = buffer))
                events += GameEvent.Message("Ironbark hardens around you, adding $buffer armor buffer.")
                events += GameEvent.PlayerStatsChanged(
                    player.stats.hp,
                    player.stats.maxHp,
                    player.stats.armor,
                    player.stats.maxArmor
                )
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.ASTRAL_SURGE_DRAUGHT -> {
                consumeStack(item)
                player.addEffect(PlayerEffect(PlayerEffectType.ASTRAL_SURGE, remainingTurns = 20))
                events += GameEvent.Message("Astral energies amplify your magic and strikes.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.VOIDRAGE_AMPOULE -> {
                consumeStack(item)
                player.addEffect(PlayerEffect(PlayerEffectType.VOIDRAGE, remainingTurns = 20))
                events += GameEvent.Message("Rage from the void floods you, trading pain for power.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.HOLLOWSIGHT_VIAL -> {
                consumeStack(item)
                player.addEffect(
                    PlayerEffect(
                        PlayerEffectType.VISION_BOOST,
                        remainingTurns = 50,
                        visionBonus = 2
                    )
                )
                events += GameEvent.Message("Your vision sharpens, piercing the gloom.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.LUMENVEIL_ELIXIR -> {
                consumeStack(item)
                player.addEffect(
                    PlayerEffect(
                        PlayerEffectType.LUMENVEIL,
                        remainingTurns = 30,
                        revealRadius = 9
                    )
                )
                events += GameEvent.Message("Radiant motes peel back the surrounding fog.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.STARSEERS_PHIAL -> {
                consumeStack(item)
                player.addEffect(
                    PlayerEffect(
                        PlayerEffectType.WALLSIGHT,
                        remainingTurns = 40,
                        wallSenseRadius = 8
                    )
                )
                events += GameEvent.Message("You glimpse silhouettes through stone and shadow.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.VEILBREAKER_DRAUGHT -> {
                consumeStack(item)
                events += GameEvent.Message("You glimpse the entire floor layout in your mind.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.LANTERN_OF_ECHOES -> {
                consumeStack(item)
                player.addEffect(
                    PlayerEffect(
                        PlayerEffectType.TRAP_SENSE,
                        remainingTurns = 25,
                        trapSense = true
                    )
                )
                events += GameEvent.Message("Echoes highlight hidden traps nearby.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.PHASESTEP_DUST -> {
                consumeStack(item)
                val moved = attemptShortBlink(events)
                if (!moved) {
                    events += GameEvent.Message("You fail to find space to step through the veil.")
                }
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.SLIPSHADOW_OIL -> {
                consumeStack(item)
                player.addEffect(
                    PlayerEffect(
                        PlayerEffectType.SLIPSHADOW,
                        remainingTurns = 20
                    )
                )
                events += GameEvent.Message("Shadows cling to you, letting you weave past foes.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.BLINKSTONE_SHARD -> {
                consumeStack(item)
                val blinked = randomBlink(events)
                if (!blinked) {
                    events += GameEvent.Message("The shard fizzles without effect.")
                }
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.VOIDFLARE_ORB,
            ItemType.FROSTSHARD_ORB,
            ItemType.STARSPIKE_DART -> {
                events += GameEvent.Message("Select a tile or enemy to throw ${item.displayName} at.")
                false
            }

            ItemType.GLOOMSMOKE_VESSEL -> {
                consumeStack(item)
                events += GameEvent.Message("A cloud of gloomsmoke spills out, breaking sight lines.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.VOIDBANE_SALTS -> {
                consumeStack(item)
                purgeNegativeEffects()
                events += GameEvent.Message("You feel poisons and rot burn away.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.LUMENPURE_DRAUGHT -> {
                consumeStack(item)
                purgeNegativeEffects()
                val duration = Random.nextInt(3, 6)
                player.addEffect(
                    PlayerEffect(
                        PlayerEffectType.STATUS_IMMUNITY,
                        remainingTurns = duration,
                        statusImmunity = true
                    )
                )
                events += GameEvent.Message("Pure light washes away lingering ailments.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.HOLLOWGUARD_INFUSION -> {
                consumeStack(item)
                player.addEffect(PlayerEffect(PlayerEffectType.HOLLOWGUARD, remainingTurns = 40))
                events += GameEvent.Message("A ward dampens incoming corruption.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.MINDWARD_CAPSULE -> {
                consumeStack(item)
                player.addEffect(PlayerEffect(PlayerEffectType.MINDWARD, remainingTurns = 30))
                events += GameEvent.Message("Your thoughts steady against psychic assault.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.STARGAZERS_INK -> {
                consumeStack(item)
                events += GameEvent.Message("You sketch the nearby terrain into your map.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.ECHO_TUNED_COMPASS -> {
                consumeStack(item)
                player.addEffect(
                    PlayerEffect(
                        PlayerEffectType.STAIRS_COMPASS,
                        remainingTurns = 60
                    )
                )
                events += GameEvent.Message("Your compass hums, pointing toward the stairs.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.VEILKEY_PICK -> {
                consumeStack(item)
                events += GameEvent.Message("You attune a veilkey, ready to bypass a lock." )
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.STARBOUND_HOOK -> {
                consumeStack(item)
                events += GameEvent.Message("You ready the hook to cross chasms swiftly." )
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.ASTRAL_RESIDUE_PHIAL -> {
                consumeStack(item)
                player.addEffect(PlayerEffect(PlayerEffectType.MUTATION_BOON, remainingTurns = 30))
                events += GameEvent.Message("Astral residue heightens your mutation flow.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.HOLLOW_SHARD_FRAGMENT -> {
                consumeStack(item)
                val buffer = 50
                player.addEffect(PlayerEffect(PlayerEffectType.HOLLOW_SHARD_BARRIER, remainingTurns = 15, magnitude = buffer))
                events += GameEvent.Message("The shard hardens into a massive barrier at a corrupting cost.")
                events += GameEvent.PlayerStatsChanged(
                    player.stats.hp,
                    player.stats.maxHp,
                    player.stats.armor,
                    player.stats.maxArmor
                )
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.TITAN_EMBER_GRANULE -> {
                consumeStack(item)
                val cost = max(1, (player.stats.hp * 0.1).roundToInt())
                player.stats.hp = max(0, player.stats.hp - cost)
                events += GameEvent.Message("You burn vitality ($cost HP) to kindle titan ember power.")
                events += GameEvent.PlayerStatsChanged(
                    player.stats.hp,
                    player.stats.maxHp,
                    player.stats.armor,
                    player.stats.maxArmor
                )
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            ItemType.LUNAR_ECHO_VIAL -> {
                consumeStack(item)
                events += GameEvent.Message("Lunar echoes promise a future mutation reroll.")
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }

            else -> false
        }
    }

    private fun useTargetedConsumable(
        item: Item,
        target: Position,
        events: MutableList<GameEvent>
    ): Boolean {
        if (item.type !in TARGETED_ITEMS) {
            return useConsumable(item, events)
        }

        if (!level.inBounds(target)) {
            events += GameEvent.Message("That space is out of range.")
            return false
        }
        if (!isWithinThrowRange(target)) {
            events += GameEvent.Message("That target is too far away.")
            return false
        }

        return when (item.type) {
            ItemType.VOIDFLARE_ORB -> {
                consumeStack(item)
                val hits = targetedScorchArea(target, 2, 8..12, events)
                if (hits == 0) {
                    events += GameEvent.Message("The voidflare bursts harmlessly.")
                }
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }
            ItemType.FROSTSHARD_ORB -> {
                consumeStack(item)
                val hits = targetedScorchArea(target, 2, 4..6, events)
                if (hits == 0) {
                    events += GameEvent.Message("Frost shards rattle without striking anything.")
                }
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }
            ItemType.STARSPIKE_DART -> {
                val enemy = level.getEntityAt(target) as? Enemy
                if (enemy == null) {
                    events += GameEvent.Message("No target there to strike.")
                    return false
                }
                consumeStack(item)
                val armorBypass = max(1, (enemy.stats.armor * 0.5).roundToInt())
                enemy.stats.armor = max(0, enemy.stats.armor - armorBypass)
                val damage = Random.nextInt(10, 15)
                applyDirectDamage(enemy, damage, bypassArmor = true, events = events)
                events += GameEvent.InventoryChanged(player.inventorySnapshot())
                true
            }
            else -> false
        }
    }

    private fun isWithinThrowRange(target: Position): Boolean {
        val distance = abs(target.x - player.position.x) + abs(target.y - player.position.y)
        return distance <= THROW_RANGE
    }

    private fun targetedScorchArea(
        center: Position,
        radius: Int,
        damageRange: IntRange,
        events: MutableList<GameEvent>
    ): Int {
        val targets = level.entities
            .filterIsInstance<Enemy>()
            .filter { abs(it.position.x - center.x) + abs(it.position.y - center.y) <= radius }

        var hits = 0
        targets.forEach { enemy ->
            val damage = Random.nextInt(damageRange.first, damageRange.last + 1)
            applyDirectDamage(enemy, damage, bypassArmor = false, events = events)
            hits++
        }
        return hits
    }

    private fun consumeStack(item: Item) {
        val index = player.inventory.indexOfFirst { it.id == item.id }
        if (index == -1) return
        val existing = player.inventory[index]
        val newQuantity = existing.quantity - 1
        if (newQuantity > 0) {
            player.inventory[index] = existing.copy(quantity = newQuantity)
        } else {
            player.inventory.removeAt(index)
        }
    }

    private fun purgeNegativeEffects() {
        player.activeEffects.removeAll { it.type == PlayerEffectType.VOIDRAGE }
    }

    private fun applyDirectDamage(target: Enemy, damage: Int, bypassArmor: Boolean, events: MutableList<GameEvent>) {
        val armorBefore = target.stats.armor
        val actualDamage = if (bypassArmor) {
            val hpDamage = damage.coerceAtLeast(1)
            target.stats.hp = max(0, target.stats.hp - hpDamage)
            hpDamage
        } else {
            target.stats.takeDamage(damage)
        }

        events += GameEvent.EntityAttacked(
            attackerId = player.id,
            targetId = target.id,
            damage = actualDamage,
            wasCritical = false,
            wasMiss = false,
            armorDamage = (armorBefore - target.stats.armor).coerceAtLeast(0)
        )

        if (target.isDead()) {
            level.removeEntity(target)
            events += GameEvent.EntityDied(target.id)
            dropLootForEnemy(target, events)
        }
    }

    private fun attemptShortBlink(events: MutableList<GameEvent>): Boolean {
        val deltas = listOf(Position(1, 0), Position(-1, 0), Position(0, 1), Position(0, -1)).shuffled()
        for (delta in deltas) {
            val target = player.position.translated(delta.x * 2, delta.y * 2)
            if (!level.inBounds(target)) continue
            if (!level.isWalkable(target)) continue
            val from = player.position
            level.moveEntity(player, target)
            events += GameEvent.EntityMoved(player.id, from, target)
            handleArrival(target, events)
            return true
        }
        return false
    }

    private fun randomBlink(events: MutableList<GameEvent>): Boolean {
        val radius = 6
        val candidates = mutableListOf<Position>()
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                val target = player.position.translated(dx, dy)
                if (target == player.position) continue
                if (!level.inBounds(target)) continue
                if (abs(dx) + abs(dy) > radius) continue
                if (!level.isWalkable(target)) continue
                candidates += target
            }
        }
        val destination = candidates.randomOrNull() ?: return false
        val from = player.position
        level.moveEntity(player, destination)
        events += GameEvent.EntityMoved(player.id, from, destination)
        handleArrival(destination, events)
        return true
    }

    private fun performAttack(attacker: Entity, target: Entity, events: MutableList<GameEvent>) {
        val weaponTemplate = equippedWeapon(attacker)
        val attackRoll = rollDamage(attacker, target, weaponTemplate)

        if (attackRoll.wasMiss) {
            events += GameEvent.EntityAttacked(attacker.id, target.id, 0, wasCritical = false, wasMiss = true, armorDamage = 0)
            return
        }

        val targetArmorBefore = target.stats.armor
        val armorBypass = calculateArmorBypass(target, weaponTemplate)
        if (armorBypass > 0) {
            target.stats.armor = max(0, target.stats.armor - armorBypass)
        }

        val damage = target.stats.takeDamage(attackRoll.damage)
        val armorDamage = (targetArmorBefore - target.stats.armor).coerceAtLeast(0)

        events += GameEvent.EntityAttacked(
            attackerId = attacker.id,
            targetId = target.id,
            damage = damage,
            wasCritical = attackRoll.wasCritical,
            wasMiss = false,
            armorDamage = armorDamage
        )

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
                dropLootForEnemy(target, events)
            }
            if (target === player) {
                events += GameEvent.GameOver
            }
        }
    }

    private fun rollDamage(attacker: Entity, target: Entity, weaponTemplate: WeaponTemplate?): AttackResult {
        val missChance = BASE_MISS_CHANCE
        if (Random.nextDouble() < missChance) {
            return AttackResult(damage = 0, wasCritical = false, wasMiss = true)
        }

        val effectBonus = effectDamageBonus(attacker)
        val baseDamage = max(1, attacker.stats.attack + effectBonus - target.stats.defense)
        val variance = weaponTemplate?.let { WEAPON_VARIANCE_HIGH } ?: WEAPON_VARIANCE_LOW
        val minDamage = max(1, (baseDamage * (1 - variance)).roundToInt())
        val maxDamage = max(minDamage, (baseDamage * (1 + variance)).roundToInt())
        val rolledDamage = Random.nextInt(minDamage, maxDamage + 1)

        val critChance = BASE_CRIT_CHANCE + (weaponTemplate?.critChanceBonus ?: 0.0) + effectCritBonus(attacker)
        val isCritical = Random.nextDouble() < critChance
        val critMultiplier = if (isCritical) CRIT_MULTIPLIER else 1.0
        val finalDamage = max(1, (rolledDamage * critMultiplier * effectDamageMultiplier(attacker)).roundToInt())

        return AttackResult(damage = finalDamage, wasCritical = isCritical, wasMiss = false)
    }

    private fun calculateArmorBypass(target: Entity, weaponTemplate: WeaponTemplate?): Int {
        if (weaponTemplate == null) return 0
        val armorBreak = weaponTemplate.armorBreakBonus
        if (armorBreak <= 0.0 || target.stats.armor <= 0) return 0
        return max(1, (target.stats.armor * armorBreak).roundToInt())
    }

    private fun equippedWeapon(entity: Entity): WeaponTemplate? {
        if (entity !is Player) return null
        val weaponId = entity.equippedWeaponId ?: return null
        return entity.inventory.firstOrNull { it.id == weaponId }?.weaponTemplate
    }

    private data class AttackResult(
        val damage: Int,
        val wasCritical: Boolean,
        val wasMiss: Boolean
    )

    private fun effectDamageBonus(attacker: Entity): Int =
        (attacker as? Player)?.effectDamageBonus() ?: 0

    private fun effectCritBonus(attacker: Entity): Double =
        (attacker as? Player)?.effectCritBonus() ?: 0.0

    private fun effectDamageMultiplier(attacker: Entity): Double =
        (attacker as? Player)?.effectDamageMultiplier() ?: 1.0

    private companion object {
        private const val BASE_MISS_CHANCE = 0.05
        private const val BASE_CRIT_CHANCE = 0.05
        private const val CRIT_MULTIPLIER = 1.5
        private const val WEAPON_VARIANCE_HIGH = 0.25
        private const val WEAPON_VARIANCE_LOW = 0.15
        private const val THROW_RANGE = 6
        private val TARGETED_ITEMS = setOf(
            ItemType.VOIDFLARE_ORB,
            ItemType.FROSTSHARD_ORB,
            ItemType.STARSPIKE_DART
        )
    }

    private fun dropLootForEnemy(enemy: Enemy, events: MutableList<GameEvent>) {
        if (enemy.name != "Goblin") return

        val roll = Random.nextDouble()
        val position = enemy.position
        val item: Item? = when {
            roll < 0.3 -> createConsumableItem(ItemLootTable.randomConsumableForDepth(level.depth), position)
            roll < 0.7 -> {
                val drop = LootGenerator.rollRandomEquipmentForDepth(level.depth)
                drop?.let { createEquipmentItem(it, position) }
            }
            roll < 0.9 -> Item(
                id = level.allocateItemId(),
                type = ItemType.HEALING_POTION,
                position = position
            )
            else -> null
        }

        if (item != null) {
            level.addItem(item)
            events += GameEvent.Message("${enemy.name} drops ${item.displayName}.")
        }
    }

    private fun createEquipmentItem(
        drop: LootGenerator.EquipmentDropResult,
        position: Position
    ): Item = when (drop) {
        is LootGenerator.EquipmentDropResult.WeaponDrop -> Item(
            id = level.allocateItemId(),
            type = ItemType.EQUIPMENT_WEAPON,
            position = position,
            weaponTemplate = drop.template
        )
        is LootGenerator.EquipmentDropResult.ArmorDrop -> Item(
            id = level.allocateItemId(),
            type = ItemType.EQUIPMENT_ARMOR,
            position = position,
            armorTemplate = drop.template
        )
    }

    private fun createConsumableItem(type: ItemType, position: Position): Item =
        Item(
            id = level.allocateItemId(),
            type = type,
            position = position
        )

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
