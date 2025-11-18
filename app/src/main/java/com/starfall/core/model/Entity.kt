package com.starfall.core.model

/** Base class for any object occupying the grid. */
abstract class Entity(
    val id: Int,
    val name: String,
    var position: Position,
    val glyph: Char,
    open val blocksMovement: Boolean = true,
    val stats: Stats
) {
    /** True if this entity has been defeated. */
    fun isDead(): Boolean = stats.isDead()
}
