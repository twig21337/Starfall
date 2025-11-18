package com.starfall.core.engine

import com.starfall.core.model.Position

/** Events emitted by the engine for UI/log consumption. */
sealed class GameEvent {
    data class Message(val text: String) : GameEvent()
    data class EntityMoved(val entityId: Int, val from: Position, val to: Position) : GameEvent()
    data class EntityAttacked(val attackerId: Int, val targetId: Int, val damage: Int) : GameEvent()
    data class EntityDied(val entityId: Int) : GameEvent()
    data class PlayerStatsChanged(val hp: Int, val maxHp: Int) : GameEvent()
    data class LevelGenerated(val width: Int, val height: Int) : GameEvent()
    object PlayerDescended : GameEvent()
    object GameOver : GameEvent()
}
