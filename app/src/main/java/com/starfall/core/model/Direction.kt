package com.starfall.core.model

/** Cardinal directions for grid-based movement. */
enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    /** Applies this direction to the provided position. */
    fun applyTo(position: Position): Position = position.translated(dx, dy)
}
