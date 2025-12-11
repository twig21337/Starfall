package com.starfall.core.model

import com.starfall.core.boss.BossManager.BossInstance

/** Represents a single dungeon floor and its contents. */
class Level(
    val width: Int,
    val height: Int,
    val tiles: Array<Array<Tile>>,
    val entities: MutableList<Entity> = mutableListOf(),
    val groundItems: MutableList<Item> = mutableListOf(),
    val depth: Int = 1,
    var stairsDownPosition: Position? = null,
    var playerSpawnPosition: Position? = null,
    var isBossFloor: Boolean = false,
    var bossInstance: BossInstance? = null,
    var bossDefeated: Boolean = false,
    var isFinalFloor: Boolean = false
) {
    /** Returns true if the position lies within level bounds. */
    fun inBounds(pos: Position): Boolean =
        pos.x in 0 until width && pos.y in 0 until height

    /** Retrieves the tile at the provided position. */
    fun getTile(pos: Position): Tile {
        require(inBounds(pos)) { "Position $pos out of bounds" }
        return tiles[pos.y][pos.x]
    }

    /** True if the tile and occupying entities allow movement. */
    fun isWalkable(pos: Position): Boolean {
        if (!inBounds(pos)) return false
        if (!getTile(pos).isWalkable) return false
        val entity = getEntityAt(pos)
        return entity?.blocksMovement != true
    }

    /** Returns the first entity located at the provided position. */
    fun getEntityAt(pos: Position): Entity? = entities.firstOrNull { it.position == pos }

    /** Returns all items located at the provided position. */
    fun getItemsAt(pos: Position): List<Item> = groundItems.filter { it.position == pos }

    /** Returns the first item located at the provided position. */
    fun getItemAt(pos: Position): Item? = getItemsAt(pos).firstOrNull()

    /** Adds an entity to the level. */
    fun addEntity(entity: Entity) {
        entities.add(entity)
    }

    /** Removes an entity from the level. */
    fun removeEntity(entity: Entity) {
        entities.remove(entity)
    }

    /** Removes an item from the ground. */
    fun removeItem(item: Item) {
        groundItems.remove(item)
    }

    /** Adds an item to the ground. */
    fun addItem(item: Item) {
        groundItems.add(item)
    }

    fun allocateItemId(): Int = allocateGlobalItemId()

    companion object {
        /**
         * Shared item ID generator across all levels so inventory items never share IDs
         * even when the player travels between floors.
         */
        private var nextItemId: Int = 10_000

        fun allocateGlobalItemId(): Int = nextItemId++
    }

    /** Moves the entity to the new position. */
    fun moveEntity(entity: Entity, newPos: Position) {
        require(inBounds(newPos)) { "Position $newPos out of bounds" }
        entity.position = newPos
    }
}
