package com.starfall.core.model

/** Available high-level enemy behavior archetypes. */
enum class EnemyBehaviorType {
    SIMPLE_CHASER,
    PASSIVE,
    FLEEING
}

/** Data class describing an enemy actor. */
class Enemy(
    id: Int,
    name: String,
    position: Position,
    glyph: Char,
    stats: Stats,
    val behaviorType: EnemyBehaviorType
) : Entity(id, name, position, glyph, true, stats)
