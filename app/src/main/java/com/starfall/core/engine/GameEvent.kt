package com.starfall.core.engine

import com.starfall.core.model.Position
import com.starfall.core.model.Item
import com.starfall.core.mutation.Mutation

/** Events emitted by the engine for UI/log consumption. */
sealed class GameEvent {
    data class Message(val text: String) : GameEvent()
    data class EntityMoved(val entityId: Int, val from: Position, val to: Position) : GameEvent()
    data class EntityAttacked(
        val attackerId: Int,
        val targetId: Int,
        val damage: Int,
        val wasCritical: Boolean,
        val wasMiss: Boolean,
        val armorDamage: Int
    ) : GameEvent()
    data class EntityDied(val entityId: Int) : GameEvent()
    data class PlayerStatsChanged(
        val hp: Int,
        val maxHp: Int,
        val armor: Int,
        val maxArmor: Int
    ) : GameEvent()
    data class LevelGenerated(
        val width: Int,
        val height: Int,
        val floorNumber: Int,
        val totalFloors: Int
    ) : GameEvent()
    data class InventoryChanged(val inventory: List<Item>) : GameEvent()
    data class PlayerLeveledUp(val newLevel: Int, val mutationChoices: List<Mutation>) : GameEvent()
    data class MutationApplied(val mutationId: String) : GameEvent()
    object PlayerDescended : GameEvent()
    object PlayerSteppedOnStairs : GameEvent()
    object GameOver : GameEvent()
    data class RunEnded(val result: RunResult) : GameEvent()
}
