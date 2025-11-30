package com.starfall.core.engine

import com.starfall.core.boss.BossManager
import com.starfall.core.model.Enemy
import com.starfall.core.enemy.EnemyTemplates
import com.starfall.core.model.EnemyBehaviorType
import com.starfall.core.model.EnemyIntent
import com.starfall.core.model.EnemyIntentType
import com.starfall.core.model.Entity
import com.starfall.core.model.Level
import com.starfall.core.model.Player
import com.starfall.core.model.Position
import com.starfall.core.model.Stats
import com.starfall.core.model.Item
import com.starfall.core.model.ItemLootTable
import com.starfall.core.model.ItemType
import com.starfall.core.model.Tile
import com.starfall.core.model.TileType
import com.starfall.core.model.PlayerEffect
import com.starfall.core.model.PlayerEffectType
import com.starfall.core.items.WeaponTemplate
import com.starfall.core.items.LootGenerator
import com.starfall.core.progression.XpManager
import com.starfall.core.mutation.MutationManager
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

/** Orchestrates turn-by-turn sequencing for the player and enemies. */
class TurnManager(
    private val level: Level,
    private val player: Player,
    private val xpManager: XpManager? = null,
    private val mutationManager: MutationManager? = null
) {

    private var turnCounter: Int = 0
    private val enemyLastSeenTurn: MutableMap<Int, Int> = mutableMapOf()
    private val bossAreaTelegraphs: MutableList<PendingAreaAttack> = mutableListOf()
    private val astromancerStates: MutableMap<Int, AstromancerState> = mutableMapOf()
    private val colossusStates: MutableMap<Int, ColossusState> = mutableMapOf()
    private val hiveMindStates: MutableMap<Int, HiveMindState> = mutableMapOf()
    private val echoStates: MutableMap<Int, EchoKnightState> = mutableMapOf()
    private val wyrmStates: MutableMap<Int, WyrmState> = mutableMapOf()
    private val hollowStalkerStates: MutableMap<Int, HollowStalkerState> = mutableMapOf()
    private val boneReaverStates: MutableMap<Int, BoneReaverState> = mutableMapOf()
    private val maulerStates: MutableMap<Int, MaulerState> = mutableMapOf()
    private val riftArcherStates: MutableMap<Int, RiftArcherState> = mutableMapOf()
    private val spitterStates: MutableMap<Int, SpitterState> = mutableMapOf()
    private val javelinerStates: MutableMap<Int, JavelinerState> = mutableMapOf()
    private val wispStates: MutableMap<Int, WispState> = mutableMapOf()
    private val acolyteStates: MutableMap<Int, AcolyteState> = mutableMapOf()
    private val frostCultistStates: MutableMap<Int, FrostCultistState> = mutableMapOf()
    private val broodHostStates: MutableMap<Int, BroodHostState> = mutableMapOf()
    private val shamanStates: MutableMap<Int, ShamanState> = mutableMapOf()
    private val carapaceStates: MutableMap<Int, CarapaceState> = mutableMapOf()
    private val poisonPuddles: MutableList<PoisonPuddle> = mutableListOf()
    private val glyphTraps: MutableList<GlyphTrap> = mutableListOf()
    private var chillStacks: Int = 0
    private var nextSummonId: Int = 200_000

    /** Processes the player's action followed by all enemies. */
    fun processPlayerAction(action: GameAction): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        var actionConsumed = false
        var enemyTurnsHandled = false
        if (player.hasEffect(PlayerEffectType.FROZEN)) {
            events += GameEvent.Message("You are frozen in place!")
            val expired = player.tickEffects()
            expired.forEach { events += GameEvent.Message(it) }
            events += GameEvent.PlayerStatsChanged(
                player.stats.hp,
                player.stats.maxHp,
                player.stats.armor,
                player.stats.maxArmor
            )
            handlePlayerDeathFromEffects(events)
            actionConsumed = true
        }
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
            is GameAction.ChooseMutation -> {
                events += applyMutationChoice(action.mutationId)
                actionConsumed = true
                enemyTurnsHandled = true
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
                val canEquip = item?.let {
                    it.type == ItemType.EQUIPMENT_WEAPON || it.type == ItemType.EQUIPMENT_ARMOR ||
                        it.weaponTemplate != null || it.armorTemplate != null
                } ?: false

                when {
                    item == null -> events += GameEvent.Message("You don't have that item.")
                    !canEquip -> events += GameEvent.Message("That item can't be equipped.")
                    else -> {
                        player.equip(action.itemId)
                        val name = item.displayName
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
                    }
                }
            }

            is GameAction.DiscardItem -> {
                val item = player.inventory.firstOrNull { it.id == action.itemId }
                if (item != null) {
//                    events += GameEvent.Message(
//                        "Discard log: itemId=${action.itemId} qty=${action.quantity} inventory=${formatInventorySnapshot()}"
//                    )
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
            is GameAction.InventoryTapLog -> {
                // Inventory tap logging is handled in the app layer; no engine processing needed.
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
            handlePlayerDeathFromEffects(events)
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

    private fun handlePlayerDeathFromEffects(events: MutableList<GameEvent>) {
        if (!player.stats.isDead()) return
        if (player.mutationState.tryConsumeResurrection()) {
            player.stats.hp = max(1, (player.stats.maxHp * 0.5).roundToInt())
            events += GameEvent.Message("Your regenerator core surges you back to life!")
            events += GameEvent.PlayerStatsChanged(
                player.stats.hp,
                player.stats.maxHp,
                player.stats.armor,
                player.stats.maxArmor
            )
            return
        }
        events += GameEvent.GameOver
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
        tickEnvironmentalHazards(events)
        tickPendingBossTelegraphs(events)
        val enemies = level.entities.filterIsInstance<Enemy>().toList()
        for (enemy in enemies) {
            if (enemy.isDead()) continue
            when (enemy.behaviorType) {
                EnemyBehaviorType.SIMPLE_CHASER -> handleSimpleChaser(enemy, events)
                EnemyBehaviorType.PASSIVE -> {}
                EnemyBehaviorType.FLEEING -> {}
                EnemyBehaviorType.HOLLOW_STALKER -> handleHollowStalker(enemy, events)
                EnemyBehaviorType.BONE_REAVER -> handleBoneReaver(enemy, events)
                EnemyBehaviorType.ABYSSAL_MAULER -> handleAbyssalMauler(enemy, events)
                EnemyBehaviorType.RIFT_ARCHER -> handleRiftArcher(enemy, events)
                EnemyBehaviorType.BLIGHT_SPITTER -> handleBlightSpitter(enemy, events)
                EnemyBehaviorType.CRYSTAL_JAVELINER -> handleCrystalJaveliner(enemy, events)
                EnemyBehaviorType.EMBER_WISP -> handleEmberWisp(enemy, events)
                EnemyBehaviorType.HEXBOUND_ACOLYTE -> handleHexboundAcolyte(enemy, events)
                EnemyBehaviorType.FROSTBOUND_CULTIST -> handleFrostboundCultist(enemy, events)
                EnemyBehaviorType.BROOD_HOST -> handleBroodHost(enemy, events)
                EnemyBehaviorType.WARPED_SHAMAN -> handleWarpedShaman(enemy, events)
                EnemyBehaviorType.VOID_CARAPACE -> handleVoidCarapace(enemy, events)
                EnemyBehaviorType.BOSS_FALLEN_ASTROMANCER -> handleFallenAstromancer(enemy, events)
                EnemyBehaviorType.BOSS_BONE_FORGED_COLOSSUS -> handleBoneForgedColossus(enemy, events)
                EnemyBehaviorType.BOSS_BLIGHTED_HIVE_MIND -> handleBlightedHiveMind(enemy, events)
                EnemyBehaviorType.BOSS_ECHO_KNIGHT_REMNANT -> handleEchoKnight(enemy, events)
                EnemyBehaviorType.BOSS_HEARTSTEALER_WYRM -> handleHeartstealerWyrm(enemy, events)
            }
            if (enemy.isDead()) {
                enemyLastSeenTurn.remove(enemy.id)
                astromancerStates.remove(enemy.id)
                colossusStates.remove(enemy.id)
                hiveMindStates.remove(enemy.id)
                echoStates.remove(enemy.id)
                wyrmStates.remove(enemy.id)
                hollowStalkerStates.remove(enemy.id)
                boneReaverStates.remove(enemy.id)
                maulerStates.remove(enemy.id)
                riftArcherStates.remove(enemy.id)
                spitterStates.remove(enemy.id)
                javelinerStates.remove(enemy.id)
                wispStates.remove(enemy.id)
                acolyteStates.remove(enemy.id)
                frostCultistStates.remove(enemy.id)
                broodHostStates.remove(enemy.id)
                shamanStates.remove(enemy.id)
                carapaceStates.remove(enemy.id)
                bossAreaTelegraphs.removeAll { it.sourceId == enemy.id }
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

    private fun handleHollowStalker(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = hollowStalkerStates.getOrPut(enemy.id) { HollowStalkerState() }
        val distance = manhattan(enemy.position, player.position)
        val canSee = distance <= enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)
        val target = state.lungeTarget
        if (target != null) {
            clearIntent(enemy)
            state.lungeTarget = null
            val path = lineBetween(enemy.position, target, maxDistance = 2)
            var current = enemy.position
            for (step in path) {
                if (!level.isWalkable(step)) break
                val from = current
                current = step
                level.moveEntity(enemy, current)
                events += GameEvent.EntityMoved(enemy.id, from, current)
                val occupant = level.getEntityAt(current)
                if (occupant == player) {
                    rollAndApplyAttackFromEnemy(enemy, events)
                    return
                }
            }
            return
        }

        if (distance == 1) {
            clearIntent(enemy)
            performAttack(enemy, player, events)
            return
        }
        if (distance in 2..3 && canSee) {
            state.lungeTarget = player.position
            setIntent(enemy, EnemyIntentType.LUNGE, listOf(player.position))
            return
        }
        if (canSee) {
            attemptStepToward(enemy, player.position, events)
        }
    }

    private fun handleBoneReaver(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = boneReaverStates.getOrPut(enemy.id) { BoneReaverState() }
        val distance = manhattan(enemy.position, player.position)
        if (state.blocking) {
            clearIntent(enemy)
            state.blocking = false
            if (distance == 1) {
                rollAndApplyAttackFromEnemy(enemy, events, weaponTemplate = null)
            }
            return
        }
        if (distance == 1) {
            if (Random.nextBoolean()) {
                state.blocking = true
                setIntent(enemy, EnemyIntentType.BLOCK)
            } else {
                performAttack(enemy, player, events)
            }
            return
        }
        if (distance <= enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)) {
            attemptStepToward(enemy, player.position, events)
        }
    }

    private fun handleAbyssalMauler(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = maulerStates.getOrPut(enemy.id) { MaulerState() }
        val distance = manhattan(enemy.position, player.position)
        val prepared = state.smashTiles
        if (prepared.isNotEmpty()) {
            val tiles = prepared.toList()
            state.smashTiles = emptyList()
            clearIntent(enemy)
            tiles.filter { it == player.position }.forEach {
                val damage = max(4, enemy.stats.attack + 2)
                applyDirectDamageToPlayer(damage, enemy.id, events, attacker = enemy)
            }
            return
        }
        if (distance <= 2) {
            val aoe = positionsInRadius(enemy.position, radius = 1)
            state.smashTiles = aoe
            setIntent(enemy, EnemyIntentType.SMASH, aoe)
            return
        }
        if (distance <= enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)) {
            attemptStepToward(enemy, player.position, events)
        }
    }

    private fun handleRiftArcher(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = riftArcherStates.getOrPut(enemy.id) { RiftArcherState() }
        if (state.warpCooldown > 0) state.warpCooldown -= 1
        if (state.retreatCooldown > 0) state.retreatCooldown -= 1
        val distance = manhattan(enemy.position, player.position)
        if (state.warpShotTarget != null) {
            val target = state.warpShotTarget
            state.warpShotTarget = null
            clearIntent(enemy)
            if (target == player.position || manhattan(target!!, player.position) == 1) {
                applyDirectDamageToPlayer(max(2, enemy.stats.attack), enemy.id, events, attacker = enemy)
            }
            state.warpCooldown = 2
            return
        }
        if (distance <= 2 && state.retreatCooldown == 0) {
            val retreat = randomWalkableTileAround(enemy.position, radius = 2)
            if (retreat != null && manhattan(retreat, player.position) > distance) {
                val from = enemy.position
                level.moveEntity(enemy, retreat)
                events += GameEvent.EntityMoved(enemy.id, from, retreat)
                state.retreatCooldown = 2
                return
            }
        }
        if (distance <= enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)) {
            if (state.warpCooldown == 0) {
                state.warpShotTarget = player.position
                setIntent(enemy, EnemyIntentType.WARP_SHOT, listOf(player.position))
            } else {
                rollAndApplyAttackFromEnemy(enemy, events)
            }
            return
        }
        attemptStepToward(enemy, player.position, events)
    }

    private fun handleBlightSpitter(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = spitterStates.getOrPut(enemy.id) { SpitterState() }
        if (state.retreatCooldown > 0) state.retreatCooldown -= 1
        val distance = manhattan(enemy.position, player.position)
        if (state.globTarget != null) {
            val target = state.globTarget
            state.globTarget = null
            clearIntent(enemy)
            if (target != null) {
                poisonPuddles += PoisonPuddle(target, duration = 3, damage = max(2, enemy.stats.attack / 2), sourceId = enemy.id)
                if (target == player.position) {
                    applyDirectDamageToPlayer(max(1, enemy.stats.attack / 2), enemy.id, events, attacker = enemy)
                }
            }
            return
        }
        if (distance == 1) {
            if (state.retreatCooldown == 0) {
                val retreat = randomWalkableTileAround(enemy.position, radius = 1)
                if (retreat != null && manhattan(retreat, player.position) > 1) {
                    val from = enemy.position
                    level.moveEntity(enemy, retreat)
                    events += GameEvent.EntityMoved(enemy.id, from, retreat)
                    state.retreatCooldown = 2
                    return
                }
            }
            rollAndApplyAttackFromEnemy(enemy, events)
            return
        }
        if (distance <= enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)) {
            state.globTarget = player.position
            setIntent(enemy, EnemyIntentType.TOXIC_GLOB, listOf(player.position))
            return
        }
        attemptStepToward(enemy, player.position, events)
    }

    private fun handleCrystalJaveliner(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = javelinerStates.getOrPut(enemy.id) { JavelinerState() }
        if (state.throwLine.isNotEmpty()) {
            val line = state.throwLine
            state.throwLine = emptyList()
            clearIntent(enemy)
            val damage = max(3, enemy.stats.attack)
            line.forEach { pos ->
                val entity = level.getEntityAt(pos)
                if (entity != null && entity != enemy) {
                    if (entity == player) {
                        applyDirectDamageToPlayer(damage, enemy.id, events, attacker = enemy)
                    } else if (entity is Enemy) {
                        entity.stats.takeDamage(damage)
                        if (entity.isDead()) {
                            handleEnemyDefeat(entity, events)
                        }
                    }
                }
            }
            return
        }
        val aligned = enemy.position.x == player.position.x || enemy.position.y == player.position.y
        val distance = manhattan(enemy.position, player.position)
        if (aligned && distance <= enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)) {
            val line = lineBetween(enemy.position, player.position, maxDistance = enemy.sightRange)
            state.throwLine = line
            setIntent(enemy, EnemyIntentType.SHARD_THROW, line)
            return
        }
        if (distance <= enemy.sightRange) {
            attemptStepToward(enemy, player.position, events)
        }
    }

    private fun handleEmberWisp(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = wispStates.getOrPut(enemy.id) { WispState() }
        val distance = manhattan(enemy.position, player.position)
        if (state.exploding) {
            clearIntent(enemy)
            val area = positionsInRadius(enemy.position, radius = 1)
            val damage = max(4, enemy.stats.attack + 2)
            if (area.any { it == player.position }) {
                applyDirectDamageToPlayer(damage, enemy.id, events, attacker = enemy)
            }
            enemy.stats.hp = 0
            handleEnemyDefeat(enemy, events)
            return
        }
        if (distance == 1) {
            state.exploding = true
            setIntent(enemy, EnemyIntentType.EXPLODE, positionsInRadius(enemy.position, radius = 1))
            return
        }
        if (distance <= enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)) {
            attemptStepToward(enemy, player.position, events)
        }
    }

    private fun handleHexboundAcolyte(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = acolyteStates.getOrPut(enemy.id) { AcolyteState() }
        val distance = manhattan(enemy.position, player.position)
        if (state.preparingHex) {
            clearIntent(enemy)
            state.preparingHex = false
            if (!player.hasEffect(PlayerEffectType.STATUS_IMMUNITY)) {
                player.addEffect(PlayerEffect(PlayerEffectType.WEAKENED, remainingTurns = 5, magnitude = 20))
                events += GameEvent.PlayerStatsChanged(player.stats.hp, player.stats.maxHp, player.stats.armor, player.stats.maxArmor)
                events += GameEvent.Message("A weakening hex saps your strength!")
            }
            return
        }
        if (state.trapTarget != null) {
            val tile = state.trapTarget
            state.trapTarget = null
            clearIntent(enemy)
            glyphTraps += GlyphTrap(tile!!, duration = 2, damage = max(3, enemy.stats.attack), sourceId = enemy.id)
            return
        }
        if (distance == 1) {
            val retreat = randomWalkableTileAround(enemy.position, radius = 2)
            if (retreat != null) {
                val from = enemy.position
                level.moveEntity(enemy, retreat)
                events += GameEvent.EntityMoved(enemy.id, from, retreat)
            }
            return
        }
        if (!player.hasEffect(PlayerEffectType.WEAKENED) && distance <= enemy.sightRange) {
            state.preparingHex = true
            setIntent(enemy, EnemyIntentType.WEAKEN_HEX, listOf(player.position))
            return
        }
        if (distance <= enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)) {
            state.trapTarget = player.position
            setIntent(enemy, EnemyIntentType.GLYPH_TRAP, listOf(player.position))
            return
        }
        attemptStepToward(enemy, player.position, events)
    }

    private fun handleFrostboundCultist(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = frostCultistStates.getOrPut(enemy.id) { FrostCultistState() }
        val distance = manhattan(enemy.position, player.position)
        if (state.orbTarget != null) {
            val target = state.orbTarget
            state.orbTarget = null
            clearIntent(enemy)
            if (target == player.position) {
                applyDirectDamageToPlayer(max(3, enemy.stats.attack), enemy.id, events, attacker = enemy)
                addChillStack(events)
            }
            return
        }
        if (distance <= enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)) {
            state.orbTarget = player.position
            setIntent(enemy, EnemyIntentType.FROST_ORB, listOf(player.position))
            return
        }
        attemptStepToward(enemy, player.position, events)
    }

    private fun handleBroodHost(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = broodHostStates.getOrPut(enemy.id) { BroodHostState() }
        val nearbyBrood = level.entities.filterIsInstance<Enemy>()
            .count { it != enemy && it.glyph == 'ʚ' && manhattan(it.position, enemy.position) <= 4 }
        if (state.summoning) {
            clearIntent(enemy)
            state.summoning = false
            if (nearbyBrood < 3) {
                repeat(2) {
                    val pos = randomWalkableTileAround(enemy.position, radius = 2) ?: return@repeat
                    val stats = Stats(maxHp = 5, hp = 5, attack = 2, defense = 0)
                    val broodling = Enemy(
                        id = nextSummonId++,
                        name = "Broodling",
                        position = pos,
                        glyph = 'ʚ',
                        stats = stats,
                        behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
                        sightRange = 5
                    )
                    level.addEntity(broodling)
                    events += GameEvent.EntityMoved(broodling.id, pos, pos)
                }
                events += GameEvent.Message("${enemy.name} tears open skittering broodlings!")
            }
            return
        }
        if (nearbyBrood < 2) {
            state.summoning = true
            setIntent(enemy, EnemyIntentType.SUMMON, positionsInRadius(enemy.position, radius = 1))
            return
        }
        if (manhattan(enemy.position, player.position) == 1) {
            val retreat = randomWalkableTileAround(enemy.position, radius = 1)
            if (retreat != null) {
                val from = enemy.position
                level.moveEntity(enemy, retreat)
                events += GameEvent.EntityMoved(enemy.id, from, retreat)
            }
            return
        }
    }

    private fun handleWarpedShaman(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = shamanStates.getOrPut(enemy.id) { ShamanState() }
        val allies = level.entities.filterIsInstance<Enemy>().filter { it != enemy && it.bossData == null }
        val lowAlly = allies.firstOrNull { it.stats.hp < it.stats.maxHp / 3 }
        if (lowAlly != null) {
            val healed = lowAlly.stats.heal(4)
            if (healed > 0) {
                events += GameEvent.Message("${enemy.name} mends ${lowAlly.name}.")
            }
            return
        }
        if (state.buffTarget != null) {
            val targetId = state.buffTarget
            state.buffTarget = null
            clearIntent(enemy)
            val ally = allies.firstOrNull { it.id == targetId }
            ally?.let {
                it.stats.attack += 2
                it.stats.defense += 1
                state.activeBuffs[it.id] = 3
                events += GameEvent.Message("${enemy.name} empowers ${it.name}!")
            }
            return
        }
        val unbuffed = allies.firstOrNull { !state.activeBuffs.containsKey(it.id) }
        if (unbuffed != null) {
            state.buffTarget = unbuffed.id
            setIntent(enemy, EnemyIntentType.BUFF, listOf(unbuffed.position))
            return
        }
        val distance = manhattan(enemy.position, player.position)
        if (distance == 1) {
            val retreat = randomWalkableTileAround(enemy.position, radius = 1)
            if (retreat != null) {
                val from = enemy.position
                level.moveEntity(enemy, retreat)
                events += GameEvent.EntityMoved(enemy.id, from, retreat)
            }
            return
        }
        if (allies.isEmpty() && distance <= enemy.sightRange) {
            performAttack(enemy, player, events)
        }
        state.activeBuffs.entries.removeIf { (id, turns) ->
            val remaining = turns - 1
            if (remaining <= 0) {
                level.entities.filterIsInstance<Enemy>().firstOrNull { it.id == id }?.let {
                    it.stats.attack = max(1, it.stats.attack - 2)
                    it.stats.defense = max(0, it.stats.defense - 1)
                }
                true
            } else {
                state.activeBuffs[id] = remaining
                false
            }
        }
    }

    private fun handleVoidCarapace(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = carapaceStates.getOrPut(enemy.id) { CarapaceState() }
        val aligned = enemy.position.x == player.position.x || enemy.position.y == player.position.y
        val distance = manhattan(enemy.position, player.position)
        if (state.chargePath.isNotEmpty()) {
            val path = state.chargePath
            state.chargePath = emptyList()
            clearIntent(enemy)
            path.forEach { pos ->
                if (!level.isWalkable(pos)) return@forEach
                val from = enemy.position
                state.facing = Position(pos.x - from.x, pos.y - from.y)
                level.moveEntity(enemy, pos)
                events += GameEvent.EntityMoved(enemy.id, from, pos)
                val occupant = level.getEntityAt(pos)
                if (occupant == player) {
                    applyDirectDamageToPlayer(max(4, enemy.stats.attack + 2), enemy.id, events, attacker = enemy)
                }
            }
            return
        }
        if (aligned && distance in 2..enemy.sightRange && hasLineOfSight(enemy.position, player.position, level)) {
            val line = lineBetween(enemy.position, player.position, maxDistance = enemy.sightRange)
            state.chargePath = line
            state.facing = if (line.isNotEmpty()) Position(line.first().x - enemy.position.x, line.first().y - enemy.position.y) else state.facing
            setIntent(enemy, EnemyIntentType.CHARGE, line)
            return
        }
        if (distance <= enemy.sightRange) {
            state.facing = Position((player.position.x - enemy.position.x).sign(), (player.position.y - enemy.position.y).sign())
            attemptStepToward(enemy, player.position, events)
        }
    }

    private fun tickPendingBossTelegraphs(events: MutableList<GameEvent>) {
        val iterator = bossAreaTelegraphs.iterator()
        while (iterator.hasNext()) {
            val aoe = iterator.next()
            aoe.turnsRemaining -= 1
            if (aoe.turnsRemaining <= 0) {
                val sourceEnemy = level.entities.filterIsInstance<Enemy>().firstOrNull { it.id == aoe.sourceId }
                if (aoe.positions.any { it == player.position }) {
                    val damage = applyDirectDamageToPlayer(aoe.damage, aoe.sourceId, events, attacker = sourceEnemy)
                    if (damage > 0) {
                        events += GameEvent.Message("${aoe.name} strikes you for $damage damage!")
                    }
                }
                iterator.remove()
            }
        }
    }

    private fun queueAreaAttack(
        sourceId: Int,
        name: String,
        positions: List<Position>,
        delay: Int,
        damage: Int
    ) {
        if (positions.isEmpty()) return
        bossAreaTelegraphs += PendingAreaAttack(sourceId, name, positions, delay, damage)
    }

    private fun attemptStepToward(enemy: Enemy, target: Position, events: MutableList<GameEvent>) {
        val dx = target.x - enemy.position.x
        val dy = target.y - enemy.position.y
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

    private fun handleFallenAstromancer(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = astromancerStates.getOrPut(enemy.id) { AstromancerState() }
        val tier = enemy.bossData?.tier ?: 1
        state.starfallCooldown = (state.starfallCooldown - 1).coerceAtLeast(0)
        state.warpCooldown = (state.warpCooldown - 1).coerceAtLeast(0)
        state.shieldCooldown = (state.shieldCooldown - 1).coerceAtLeast(0)

        if (state.shieldTurns > 0) {
            state.shieldTurns -= 1
            if (state.shieldTurns <= 0 && state.shieldBuffer > 0) {
                enemy.stats.maxArmor = max(0, enemy.stats.maxArmor - state.shieldBuffer)
                enemy.stats.armor = enemy.stats.armor.coerceAtMost(enemy.stats.maxArmor)
                state.shieldBuffer = 0
                events += GameEvent.Message("${enemy.name}'s luminant shield fades.")
            }
        }

        val distance = abs(player.position.x - enemy.position.x) + abs(player.position.y - enemy.position.y)
        val starfallReady = state.starfallCooldown <= 0
        val shieldReady = state.shieldCooldown <= 0

        when {
            shieldReady -> {
                val buffer = max(2, tier + 1)
                enemy.stats.maxArmor += buffer
                enemy.stats.armor += buffer
                state.shieldBuffer = buffer
                state.shieldTurns = 3 + tier
                state.shieldCooldown = max(4, 7 - tier)
                events += GameEvent.Message("${enemy.name} surrounds itself with a luminant shield.")
            }
            starfallReady -> {
                val markers = (2 + tier).coerceAtMost(6)
                val radius = 3 + tier
                val targets = mutableListOf<Position>()
                repeat(markers) {
                    val dx = Random.nextInt(-radius, radius + 1)
                    val dy = Random.nextInt(-radius, radius + 1)
                    val pos = player.position.translated(dx, dy)
                    if (level.inBounds(pos) && level.isWalkable(pos)) {
                        targets += pos
                    }
                }
                if (targets.isNotEmpty()) {
                    val damage = max(4, enemy.stats.attack + tier)
                    queueAreaAttack(enemy.id, "Starfall strike", targets, delay = 1, damage = damage)
                    state.starfallCooldown = max(2, 5 - tier)
                    events += GameEvent.Message("${enemy.name} marks the ground with falling stars!")
                }
            }
            state.warpCooldown <= 0 -> {
                val destination = randomWalkableTileAround(enemy.position, radius = 6) ?: enemy.position
                val from = enemy.position
                if (destination != from) {
                    level.moveEntity(enemy, destination)
                    events += GameEvent.EntityMoved(enemy.id, from, destination)
                    state.warpCooldown = max(3, 6 - tier)
                    val distortion = positionsInRadius(from, radius = 1)
                    val damage = max(2, enemy.stats.attack / 2)
                    queueAreaAttack(enemy.id, "Warp distortion", distortion, delay = 1, damage = damage)
                    events += GameEvent.Message("${enemy.name} warps across the chamber, leaving distortion behind.")
                } else {
                    attemptStepToward(enemy, player.position, events)
                }
            }
            distance <= 1 -> performAttack(enemy, player, events)
            else -> attemptStepToward(enemy, player.position, events)
        }
    }

    private fun handleBoneForgedColossus(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = colossusStates.getOrPut(enemy.id) { ColossusState(lastHp = enemy.stats.hp) }
        val tier = enemy.bossData?.tier ?: 1
        state.marrowCooldown = (state.marrowCooldown - 1).coerceAtLeast(0)
        state.shardCooldown = (state.shardCooldown - 1).coerceAtLeast(0)

        if (state.lastHp > enemy.stats.hp) {
            state.armorStacks = min(state.armorStacks + 1, 3 + tier)
            val gain = 1 + tier
            enemy.stats.maxArmor += gain
            enemy.stats.armor += gain
            state.decayTimer = 3
            events += GameEvent.Message("${enemy.name}'s bone plates harden!")
        } else if (state.decayTimer > 0) {
            state.decayTimer -= 1
        } else if (state.armorStacks > 0) {
            val loss = 1 + tier
            enemy.stats.maxArmor = max(0, enemy.stats.maxArmor - loss)
            enemy.stats.armor = enemy.stats.armor.coerceAtMost(enemy.stats.maxArmor)
            state.armorStacks = (state.armorStacks - 1).coerceAtLeast(0)
        }
        state.lastHp = enemy.stats.hp

        val distance = abs(player.position.x - enemy.position.x) + abs(player.position.y - enemy.position.y)
        when {
            distance <= 1 && state.marrowCooldown <= 0 -> {
                val damage = max(6, enemy.stats.attack + tier * 2)
                val area = positionsInRadius(player.position, radius = 1)
                queueAreaAttack(enemy.id, "Marrow crush", area, delay = 1, damage = damage)
                state.marrowCooldown = max(3, 6 - tier)
                events += GameEvent.Message("${enemy.name} raises its maul for a crushing slam!")
            }
            distance in 2..4 && state.shardCooldown <= 0 -> {
                val path = lineBetween(enemy.position, player.position, 4 + tier)
                val damage = max(4, enemy.stats.attack + tier)
                queueAreaAttack(enemy.id, "Bone shards", path, delay = 0, damage = damage)
                state.shardCooldown = max(2, 5 - tier)
                events += GameEvent.Message("${enemy.name} unleashes a volley of bone shards!")
            }
            else -> attemptStepToward(enemy, player.position, events)
        }
    }

    private fun handleBlightedHiveMind(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = hiveMindStates.getOrPut(enemy.id) { HiveMindState() }
        val tier = enemy.bossData?.tier ?: 1
        state.swarmCooldown = (state.swarmCooldown - 1).coerceAtLeast(0)
        state.summonCooldown = (state.summonCooldown - 1).coerceAtLeast(0)

        val auraRange = 2 + tier / 2
        val distance = abs(player.position.x - enemy.position.x) + abs(player.position.y - enemy.position.y)
        if (distance <= auraRange) {
            val damage = max(1, 2 + tier)
            applyDirectDamageToPlayer(damage, enemy.id, events, attacker = enemy)
            events += GameEvent.Message("You are seared by the pestilent aura!")
        }

        when {
            state.swarmCooldown <= 0 -> {
                val projectiles = 2 + tier
                val targets = mutableListOf<Position>()
                repeat(projectiles) {
                    val dx = Random.nextInt(-1, 2)
                    val dy = Random.nextInt(-1, 2)
                    targets += player.position.translated(dx, dy)
                }
                val damage = max(3, enemy.stats.attack + tier)
                queueAreaAttack(enemy.id, "Swarm burst", targets, delay = 1, damage = damage)
                state.swarmCooldown = max(2, 5 - tier)
                events += GameEvent.Message("${enemy.name} releases a blighted swarm!")
            }
            state.summonCooldown <= 0 -> {
                summonBroodlings(enemy, tier, events)
                state.summonCooldown = max(4, 7 - tier)
            }
            distance > 1 -> attemptStepToward(enemy, player.position, events)
            else -> performAttack(enemy, player, events)
        }
    }

    private fun handleEchoKnight(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = echoStates.getOrPut(enemy.id) { EchoKnightState() }
        val tier = enemy.bossData?.tier ?: 1
        state.phaseTurns = (state.phaseTurns - 1).coerceAtLeast(0)
        state.dashCooldown = (state.dashCooldown - 1).coerceAtLeast(0)
        state.cloneCooldown = (state.cloneCooldown - 1).coerceAtLeast(0)
        state.flickerCooldown = (state.flickerCooldown - 1).coerceAtLeast(0)

        if (state.phaseTurns == 0) {
            state.phase = when (state.phase) {
                EchoPhase.CLONES -> EchoPhase.DASH
                EchoPhase.DASH -> EchoPhase.FLICKER
                EchoPhase.FLICKER -> EchoPhase.CLONES
            }
            state.phaseTurns = 3
        }

        when (state.phase) {
            EchoPhase.CLONES -> {
                if (state.cloneCooldown <= 0) {
                    spawnEchoClones(enemy, tier, events)
                    state.cloneCooldown = max(4, 7 - tier)
                }
                if (adjacentToPlayer(enemy)) {
                    performAttack(enemy, player, events)
                } else {
                    attemptStepToward(enemy, player.position, events)
                }
            }
            EchoPhase.DASH -> {
                if (state.dashCooldown <= 0) {
                    val damage = max(4, enemy.stats.attack + tier)
                    performEchoDash(enemy, damage, events)
                    state.dashCooldown = max(3, 6 - tier)
                } else if (adjacentToPlayer(enemy)) {
                    performAttack(enemy, player, events)
                } else {
                    attemptStepToward(enemy, player.position, events)
                }
            }
            EchoPhase.FLICKER -> {
                if (state.flickerCooldown <= 0) {
                    val strikePos = player.position
                    val destination = randomWalkableTileAround(strikePos, radius = 2) ?: strikePos
                    val from = enemy.position
                    level.moveEntity(enemy, destination)
                    events += GameEvent.EntityMoved(enemy.id, from, destination)
                    val damage = max(5, enemy.stats.attack + tier * 2)
                    queueAreaAttack(enemy.id, "Temporal flicker", listOf(strikePos), delay = 1, damage = damage)
                    state.flickerCooldown = max(4, 7 - tier)
                    events += GameEvent.Message("${enemy.name} vanishes and reappears with a heavy strike charge!")
                } else if (adjacentToPlayer(enemy)) {
                    performAttack(enemy, player, events)
                }
            }
        }
    }

    private fun handleHeartstealerWyrm(enemy: Enemy, events: MutableList<GameEvent>) {
        val state = wyrmStates.getOrPut(enemy.id) { WyrmState() }
        val tier = enemy.bossData?.tier ?: 1
        state.biteCooldown = (state.biteCooldown - 1).coerceAtLeast(0)
        state.pulseCooldown = (state.pulseCooldown - 1).coerceAtLeast(0)
        state.burrowCooldown = (state.burrowCooldown - 1).coerceAtLeast(0)

        if (state.burrowed) {
            state.burrowTurns -= 1
            if (state.burrowTurns <= 0) {
                val emergePos = randomWalkableTileAround(player.position, radius = 2) ?: player.position
                val from = enemy.position
                level.moveEntity(enemy, emergePos)
                events += GameEvent.EntityMoved(enemy.id, from, emergePos)
                val damage = max(5, enemy.stats.attack + tier)
                queueAreaAttack(enemy.id, "Burrow reemerge", listOf(emergePos), delay = 0, damage = damage)
                state.burrowed = false
                state.burrowCooldown = max(5, 8 - tier)
                events += GameEvent.Message("${enemy.name} erupts from below in a spray of stone and blood!")
            }
            return
        }

        val distance = abs(player.position.x - enemy.position.x) + abs(player.position.y - enemy.position.y)
        when {
            state.burrowCooldown <= 0 -> {
                state.burrowed = true
                state.burrowTurns = max(1, 3 - tier)
                events += GameEvent.Message("${enemy.name} burrows beneath the ground!")
            }
            distance <= 1 && state.biteCooldown <= 0 -> {
                val damage = rollAndApplyAttackFromEnemy(enemy, events)
                if (damage > 0) {
                    val healAmount = max(1, (damage * (15 + tier * 5)) / 100)
                    enemy.stats.heal(healAmount)
                    events += GameEvent.Message("${enemy.name} siphons $healAmount life with its bite!")
                }
                state.biteCooldown = 2
            }
            distance <= 2 && state.pulseCooldown <= 0 -> {
                val damage = max(4, enemy.stats.attack + tier)
                val area = positionsInRadius(enemy.position, radius = 1 + tier / 2)
                queueAreaAttack(enemy.id, "Corrupted pulse", area, delay = 0, damage = damage)
                state.pulseCooldown = max(3, 6 - tier)
                events += GameEvent.Message("${enemy.name} unleashes a corrupted pulse!")
            }
            else -> attemptStepToward(enemy, player.position, events)
        }
    }

    private fun randomWalkableTileAround(origin: Position, radius: Int): Position? {
        val candidates = mutableListOf<Position>()
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                val pos = origin.translated(dx, dy)
                if (pos == origin) continue
                if (dx != 0 && dy != 0) continue
                if (!level.inBounds(pos)) continue
                if (!level.isWalkable(pos)) continue
                candidates += pos
            }
        }
        return candidates.randomOrNull()
    }

    private fun positionsInRadius(center: Position, radius: Int): List<Position> {
        val positions = mutableListOf<Position>()
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                val pos = center.translated(dx, dy)
                if (!level.inBounds(pos)) continue
                positions += pos
            }
        }
        return positions
    }

    private fun lineBetween(start: Position, end: Position, maxDistance: Int): List<Position> {
        val points = mutableListOf<Position>()
        var current = start
        var steps = 0
        while (current != end && steps < maxDistance) {
            val dx = end.x - current.x
            val dy = end.y - current.y
            val next = when {
                abs(dx) >= abs(dy) && dx != 0 -> Position(current.x + dx.sign(), current.y)
                dy != 0 -> Position(current.x, current.y + dy.sign())
                else -> current
            }
            if (next == current) break
            points += next
            current = next
            steps++
        }
        return points
    }

    private fun summonBroodlings(enemy: Enemy, tier: Int, events: MutableList<GameEvent>) {
        val summons = (2 + tier / 2).coerceAtMost(4)
        repeat(summons) {
            val pos = randomWalkableTileAround(enemy.position, radius = 2) ?: return@repeat
            val stats = Stats(maxHp = 4 + tier, hp = 4 + tier, attack = 2 + tier, defense = 0)
            val broodling = Enemy(
                id = nextSummonId++,
                name = "Broodling",
                position = pos,
                glyph = 'ʚ',
                stats = stats,
                behaviorType = EnemyBehaviorType.SIMPLE_CHASER
            )
            level.addEntity(broodling)
            events += GameEvent.EntityMoved(broodling.id, pos, pos)
        }
        events += GameEvent.Message("${enemy.name} summons broodlings from the hive walls!")
    }

    private fun spawnEchoClones(enemy: Enemy, tier: Int, events: MutableList<GameEvent>) {
        val clones = (1 + tier).coerceAtMost(3)
        repeat(clones) {
            val pos = randomWalkableTileAround(enemy.position, radius = 2) ?: return@repeat
            val stats = Stats(maxHp = 6 + tier, hp = 6 + tier, attack = 2 + tier, defense = 1)
            val clone = Enemy(
                id = nextSummonId++,
                name = "Echo Clone",
                position = pos,
                glyph = '҂',
                stats = stats,
                behaviorType = EnemyBehaviorType.SIMPLE_CHASER
            )
            level.addEntity(clone)
            events += GameEvent.Message("A shadowy clone takes form!")
        }
    }

    private fun adjacentToPlayer(enemy: Enemy): Boolean {
        val dx = abs(enemy.position.x - player.position.x)
        val dy = abs(enemy.position.y - player.position.y)
        return dx + dy == 1
    }

    private fun performEchoDash(enemy: Enemy, damage: Int, events: MutableList<GameEvent>) {
        val dx = (player.position.x - enemy.position.x).sign()
        val dy = (player.position.y - enemy.position.y).sign()
        var current = enemy.position
        repeat(3) {
            val next = current.translated(dx, dy)
            if (!level.inBounds(next) || !level.isWalkable(next)) return@repeat
            val occupant = level.getEntityAt(next)
            val from = enemy.position
            current = next
            level.moveEntity(enemy, current)
            events += GameEvent.EntityMoved(enemy.id, from, current)
            if (current == player.position || occupant == player) {
                applyDirectDamageToPlayer(damage, enemy.id, events, attacker = enemy)
            }
        }
    }

    private fun healingPotionAmount(): Int = 10 + player.level + level.depth

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
            val added = player.addItem(inventoryItem)
            if (!added) {
                events += GameEvent.Message("Your inventory is full.")
                return
            }

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

        val added = player.addItem(item)
        if (!added) {
            events += GameEvent.Message("Your inventory is full.")
            return
        }
        level.removeItem(item)
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
                val healed = player.consumePotion(item.id, healingPotionAmount())
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
            handleEnemyDefeat(target, events)
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
        if (attacker is Enemy && target is Player && !enemyVisibleToPlayer(attacker)) {
            return
        }
        if (target is Player && target.mutationState.shouldPhaseDodge()) {
            events += GameEvent.EntityAttacked(attacker.id, target.id, 0, wasCritical = false, wasMiss = true, armorDamage = 0)
            return
        }

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

        val damage = target.stats.takeDamage(modifyDamageForTarget(attacker, target, attackRoll.damage))
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
            val armorBroken = targetArmorBefore > 0 && player.stats.armor <= 0 && player.hasEquippedArmor()
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

            if (player.stats.isDead() && player.mutationState.tryConsumeResurrection()) {
                player.stats.hp = max(1, (player.stats.maxHp * 0.5).roundToInt())
                events += GameEvent.Message("Your regenerator core surges you back to life!")
                events += GameEvent.PlayerStatsChanged(
                    player.stats.hp,
                    player.stats.maxHp,
                    player.stats.armor,
                    player.stats.maxArmor
                )
                return
            }
        }
        if (target.isDead()) {
            level.removeEntity(target)
            events += GameEvent.EntityDied(target.id)
            if (target is Enemy) {
                handleEnemyDefeat(target, events)
            }
            if (target === player) {
                events += GameEvent.GameOver
            }
        }
    }

    private fun rollDamage(attacker: Entity, target: Entity, weaponTemplate: WeaponTemplate?): AttackResult {
        val missChance = (BASE_MISS_CHANCE + targetDodgeBonus(target)).coerceAtMost(0.95)
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

    private fun targetDodgeBonus(target: Entity): Double =
        (target as? Player)?.mutationState?.dodgeBonus ?: 0.0

    private fun modifyDamageForTarget(attacker: Entity?, target: Entity, rolledDamage: Int): Int {
        if (target is Enemy) {
            if (target.behaviorType == EnemyBehaviorType.BONE_REAVER && boneReaverStates[target.id]?.blocking == true) {
                return max(1, (rolledDamage * 0.3).roundToInt())
            }
            if (target.behaviorType == EnemyBehaviorType.VOID_CARAPACE) {
                val state = carapaceStates[target.id]
                if (state != null && attacker != null) {
                    val dx = target.position.x - attacker.position.x
                    val dy = target.position.y - attacker.position.y
                    if (state.facing == Position(dx.sign(), dy.sign())) {
                        return max(1, (rolledDamage * 0.4).roundToInt())
                    }
                }
            }
            return rolledDamage
        }
        if (target !is Player) return rolledDamage
        return target.mutationState.reduceDamage(rolledDamage)
    }

    private fun enemyVisibleToPlayer(enemy: Enemy): Boolean {
        val tile = level.tiles[enemy.position.y][enemy.position.x]
        return tile.visible
    }

    private fun applyDirectDamageToPlayer(
        rawDamage: Int,
        sourceId: Int?,
        events: MutableList<GameEvent>,
        attacker: Enemy? = null
    ): Int {
        if (attacker != null && !enemyVisibleToPlayer(attacker)) {
            return 0
        }
        if (player.isDead()) return 0
        val targetArmorBefore = player.stats.armor
        val damage = player.stats.takeDamage(modifyDamageForTarget(null, player, rawDamage))
        val armorDamage = (targetArmorBefore - player.stats.armor).coerceAtLeast(0)
        val attackerId = sourceId ?: -1
        events += GameEvent.EntityAttacked(
            attackerId = attackerId,
            targetId = player.id,
            damage = damage,
            wasCritical = false,
            wasMiss = false,
            armorDamage = armorDamage
        )
        events += GameEvent.PlayerStatsChanged(
            player.stats.hp,
            player.stats.maxHp,
            player.stats.armor,
            player.stats.maxArmor
        )
        if (player.stats.isDead() && player.mutationState.tryConsumeResurrection()) {
            player.stats.hp = max(1, (player.stats.maxHp * 0.5).roundToInt())
            events += GameEvent.Message("Your regenerator core surges you back to life!")
            events += GameEvent.PlayerStatsChanged(
                player.stats.hp,
                player.stats.maxHp,
                player.stats.armor,
                player.stats.maxArmor
            )
            return damage
        }
        if (player.isDead()) {
            events += GameEvent.GameOver
        }
        return damage
    }

    private fun rollAndApplyAttackFromEnemy(
        enemy: Enemy,
        events: MutableList<GameEvent>,
        weaponTemplate: WeaponTemplate? = null
    ): Int {
        if (!enemyVisibleToPlayer(enemy)) {
            return 0
        }
        val attackRoll = rollDamage(enemy, player, weaponTemplate)
        if (attackRoll.wasMiss) {
            events += GameEvent.EntityAttacked(enemy.id, player.id, 0, wasCritical = false, wasMiss = true, armorDamage = 0)
            return 0
        }

        val targetArmorBefore = player.stats.armor
        val damage = player.stats.takeDamage(modifyDamageForTarget(enemy, player, attackRoll.damage))
        val armorDamage = (targetArmorBefore - player.stats.armor).coerceAtLeast(0)
        events += GameEvent.EntityAttacked(
            attackerId = enemy.id,
            targetId = player.id,
            damage = damage,
            wasCritical = attackRoll.wasCritical,
            wasMiss = false,
            armorDamage = armorDamage
        )
        events += GameEvent.PlayerStatsChanged(
            player.stats.hp,
            player.stats.maxHp,
            player.stats.armor,
            player.stats.maxArmor
        )
        if (player.stats.isDead() && player.mutationState.tryConsumeResurrection()) {
            player.stats.hp = max(1, (player.stats.maxHp * 0.5).roundToInt())
            events += GameEvent.Message("Your regenerator core surges you back to life!")
            events += GameEvent.PlayerStatsChanged(
                player.stats.hp,
                player.stats.maxHp,
                player.stats.armor,
                player.stats.maxArmor
            )
            return damage
        }
        if (player.isDead()) {
            events += GameEvent.GameOver
        }
        return damage
    }

    private data class PendingAreaAttack(
        val sourceId: Int,
        val name: String,
        val positions: List<Position>,
        var turnsRemaining: Int,
        val damage: Int
    )

    private data class AstromancerState(
        var starfallCooldown: Int = 0,
        var warpCooldown: Int = 0,
        var shieldCooldown: Int = 0,
        var shieldTurns: Int = 0,
        var shieldBuffer: Int = 0
    )

    private data class ColossusState(
        var marrowCooldown: Int = 0,
        var shardCooldown: Int = 0,
        var armorStacks: Int = 0,
        var decayTimer: Int = 0,
        var lastHp: Int = -1
    )

    private data class HiveMindState(
        var swarmCooldown: Int = 0,
        var summonCooldown: Int = 0
    )

    private enum class EchoPhase { CLONES, DASH, FLICKER }

    private data class EchoKnightState(
        var phase: EchoPhase = EchoPhase.CLONES,
        var phaseTurns: Int = 3,
        var dashCooldown: Int = 0,
        var cloneCooldown: Int = 0,
        var flickerCooldown: Int = 0
    )

    private data class WyrmState(
        var biteCooldown: Int = 0,
        var pulseCooldown: Int = 0,
        var burrowCooldown: Int = 0,
        var burrowed: Boolean = false,
        var burrowTurns: Int = 0
    )

    private data class HollowStalkerState(var lungeTarget: Position? = null)
    private data class BoneReaverState(var blocking: Boolean = false)
    private data class MaulerState(var smashTiles: List<Position> = emptyList())
    private data class RiftArcherState(
        var warpShotTarget: Position? = null,
        var warpCooldown: Int = 0,
        var retreatCooldown: Int = 0
    )
    private data class SpitterState(var globTarget: Position? = null, var retreatCooldown: Int = 0)
    private data class JavelinerState(var throwLine: List<Position> = emptyList())
    private data class WispState(var exploding: Boolean = false)
    private data class AcolyteState(var preparingHex: Boolean = false, var trapTarget: Position? = null)
    private data class FrostCultistState(var orbTarget: Position? = null)
    private data class BroodHostState(var summoning: Boolean = false)
    private data class ShamanState(var buffTarget: Int? = null, val activeBuffs: MutableMap<Int, Int> = mutableMapOf())
    private data class CarapaceState(
        var chargePath: List<Position> = emptyList(),
        var facing: Position = Position(0, 0)
    )
    private data class PoisonPuddle(
        var position: Position,
        var duration: Int,
        val damage: Int,
        val sourceId: Int?
    )
    private data class GlyphTrap(
        var position: Position,
        var duration: Int,
        val damage: Int,
        val sourceId: Int?
    )

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

    private fun manhattan(a: Position, b: Position): Int = abs(a.x - b.x) + abs(a.y - b.y)

    private fun setIntent(
        enemy: Enemy,
        type: EnemyIntentType,
        tiles: List<Position> = emptyList(),
        turnsUntilResolve: Int = 1
    ) {
        enemy.currentIntent = EnemyIntent(type, tiles, turnsUntilResolve)
    }

    private fun clearIntent(enemy: Enemy) {
        enemy.currentIntent = null
    }

    private fun tickEnvironmentalHazards(events: MutableList<GameEvent>) {
        val iterator = poisonPuddles.iterator()
        while (iterator.hasNext()) {
            val puddle = iterator.next()
            puddle.duration -= 1
            if (puddle.position == player.position) {
                applyDirectDamageToPlayer(puddle.damage, sourceId = puddle.sourceId, events = events)
                player.addEffect(PlayerEffect(PlayerEffectType.POISONED, remainingTurns = 3, magnitude = 1))
            }
            if (puddle.duration <= 0) iterator.remove()
        }
        val trapIterator = glyphTraps.iterator()
        while (trapIterator.hasNext()) {
            val trap = trapIterator.next()
            trap.duration -= 1
            if (trap.position == player.position) {
                applyDirectDamageToPlayer(trap.damage, sourceId = trap.sourceId, events = events)
                trapIterator.remove()
            } else if (trap.duration <= 0) {
                trapIterator.remove()
            }
        }
    }

    private fun addChillStack(events: MutableList<GameEvent>) {
        chillStacks = (chillStacks + 1).coerceAtMost(3)
        if (chillStacks >= 3) {
            chillStacks = 0
            player.addEffect(PlayerEffect(PlayerEffectType.FROZEN, remainingTurns = 1))
            events += GameEvent.Message("Frost locks you in place!")
        } else {
            player.addEffect(PlayerEffect(PlayerEffectType.CHILLED, remainingTurns = 3, magnitude = chillStacks))
            events += GameEvent.Message("Chill creeps over you ($chillStacks).")
        }
    }

    private fun dropLootForEnemy(enemy: Enemy, events: MutableList<GameEvent>) {
        val bossData = enemy.bossData
        if (bossData != null) {
            val loot = BossManager.rollBossLoot(bossData)
            val position = enemy.position
            val drops = mutableListOf<Item>()
            loot.itemDrops.forEach { itemType ->
                drops += createConsumableItem(itemType, position)
            }
            loot.equipmentDrops.forEach { drop ->
                drops += createEquipmentItem(drop, position)
            }
            drops.forEach { item ->
                level.addItem(item)
                events += GameEvent.Message("${enemy.name} drops ${item.displayName}.")
            }
            return
        }

        if (enemy.templateId != EnemyTemplates.GOBLIN_ID) return

        val roll = Random.nextDouble()
        val adjustedRoll = (roll - ((level.depth - 1) * 0.02).coerceAtMost(0.2)).coerceAtLeast(0.0)
        val position = enemy.position
        val item: Item? = when {
            adjustedRoll < 0.3 -> createConsumableItem(ItemLootTable.randomConsumableForDepth(level.depth), position)
            adjustedRoll < 0.7 -> {
                val drop = LootGenerator.rollRandomEquipmentForDepth(level.depth)
                drop?.let { createEquipmentItem(it, position) }
            }
            adjustedRoll < 0.9 -> Item(
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

    private fun handleEnemyDefeat(enemy: Enemy, events: MutableList<GameEvent>) {
        level.removeEntity(enemy)
        events += GameEvent.EntityDied(enemy.id)
        enemyLastSeenTurn.remove(enemy.id)
        dropLootForEnemy(enemy, events)
        if (enemy.bossData != null) {
            level.bossDefeated = true
            handleBossPostFight(enemy, events)
        }

        val xp = xpManager ?: return
        val reward = enemy.bossData?.xpReward
            ?: enemy.xpReward
            ?: max(1, kotlin.math.ceil(xp.getRequiredXpForNextLevel() / 10.0).toInt())
        val levelUps = xp.gainXp(reward)
        events += GameEvent.Message("You gain $reward XP.")
        if (levelUps.isNotEmpty()) {
            levelUps.forEach { result ->
                events += GameEvent.Message("You reach level ${result.newLevel}!")
                events += GameEvent.PlayerStatsChanged(
                    player.stats.hp,
                    player.stats.maxHp,
                    player.stats.armor,
                    player.stats.maxArmor
                )
                events += GameEvent.PlayerLeveledUp(result.newLevel, result.mutationChoices)
            }
        }
    }

    private fun handleBossPostFight(enemy: Enemy, events: MutableList<GameEvent>) {
        if (level.isFinalFloor) {
            events += GameEvent.Message("You have conquered the final boss of these depths!")
            return
        }

        if (level.stairsDownPosition == null) {
            val pos = enemy.position
            level.tiles[pos.y][pos.x] = Tile(TileType.STAIRS_DOWN)
            level.stairsDownPosition = pos
            events += GameEvent.Message("A portal opens deeper below.")
            events += GameEvent.PlayerStatsChanged(
                player.stats.hp,
                player.stats.maxHp,
                player.stats.armor,
                player.stats.maxArmor
            )
        }
    }

    fun applyMutationChoice(mutationId: String): List<GameEvent> {
        val manager = mutationManager ?: return listOf(GameEvent.Message("No mutation manager available."))
        val events = mutableListOf<GameEvent>()
        val applied = manager.applyChosenMutation(player, mutationId)
        if (!applied) {
            events += GameEvent.Message("That mutation choice is no longer available.")
            return events
        }

        events += GameEvent.Message("You embrace a new mutation.")
        events += GameEvent.MutationApplied(mutationId)
        events += GameEvent.PlayerStatsChanged(
            player.stats.hp,
            player.stats.maxHp,
            player.stats.armor,
            player.stats.maxArmor
        )
        return events
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

    private fun formatInventorySnapshot(): String {
        return player.inventorySnapshot().mapIndexed { idx, item ->
            val equippedFlag = if (item.isEquipped) "E" else "-"
            "[$idx:${item.displayName}(id=${item.id},type=${item.type.name},qty=${item.quantity},eq=$equippedFlag,slot=${item.inventoryIndex})]"
        }.joinToString(separator = " ")
    }

    private enum class MoveStepResult {
        MOVED,
        ATTACKED,
        REACHED_STAIRS,
        BLOCKED
    }
}
