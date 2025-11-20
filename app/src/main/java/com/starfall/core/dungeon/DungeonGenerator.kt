package com.starfall.core.dungeon

import com.starfall.core.model.Level

/** Contract for producing new dungeon levels. */
interface DungeonGenerator {
    fun generate(width: Int, height: Int, depth: Int): Level
}
