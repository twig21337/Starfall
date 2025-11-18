package com.starfall.core.model

/** Represents a coordinate within a dungeon level. */
data class Position(val x: Int, val y: Int) {
    /** Returns a new position translated by the given delta. */
    fun translated(dx: Int, dy: Int): Position = Position(x + dx, y + dy)

    override fun toString(): String = "($x,$y)"
}
