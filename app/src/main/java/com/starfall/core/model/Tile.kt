package com.starfall.core.model

/** Represents the state of a single grid tile. */
data class Tile(
    val type: TileType,
    var visible: Boolean = false,
    var discovered: Boolean = false
) {
    /** True if entities can walk over this tile. */
    val isWalkable: Boolean
        get() = when (type) {
            TileType.FLOOR, TileType.DOOR_OPEN, TileType.STAIRS_DOWN -> true
            TileType.TRAP -> true
            TileType.WALL, TileType.DOOR_CLOSED -> false
        }
}
