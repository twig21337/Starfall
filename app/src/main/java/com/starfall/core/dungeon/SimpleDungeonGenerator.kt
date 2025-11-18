package com.starfall.core.dungeon

import com.starfall.core.model.Enemy
import com.starfall.core.model.EnemyBehaviorType
import com.starfall.core.model.Level
import com.starfall.core.model.Position
import com.starfall.core.model.Stats
import com.starfall.core.model.Tile
import com.starfall.core.model.TileType
import kotlin.math.max
import kotlin.random.Random

/** Very small placeholder generator that carves a room and scatters enemies. */
class SimpleDungeonGenerator : DungeonGenerator {
    private var nextEntityId: Int = 1_000

    override fun generate(width: Int, height: Int): Level {
        val tiles = Array(height) { Array(width) { Tile(TileType.WALL) } }
        val roomWidth = max(6, width / 2)
        val roomHeight = max(6, height / 2)
        val startX = (width - roomWidth) / 2
        val startY = (height - roomHeight) / 2

        val floorPositions = mutableListOf<Position>()
        for (y in startY until startY + roomHeight) {
            for (x in startX until startX + roomWidth) {
                tiles[y][x] = Tile(TileType.FLOOR)
                floorPositions += Position(x, y)
            }
        }

        val level = Level(width, height, tiles, mutableListOf())
        val stairsPos = Position(startX + roomWidth - 2, startY + roomHeight - 2)
        tiles[stairsPos.y][stairsPos.x] = Tile(TileType.STAIRS_DOWN)
        level.stairsDownPosition = stairsPos

        val available = floorPositions.filter { it != stairsPos }.toMutableList()
        val spawnPos = Position(width / 2, height / 2)
        available.remove(spawnPos)
        val enemyCount = max(1, available.size / 15)
        repeat(enemyCount) {
            if (available.isEmpty()) return@repeat
            val index = Random.nextInt(available.size)
            val pos = available.removeAt(index)
            val stats = Stats(maxHp = 8, hp = 8, attack = 3, defense = 1)
            val enemy = Enemy(
                id = nextEntityId++,
                name = "Goblin",
                position = pos,
                glyph = 'g',
                stats = stats,
                behaviorType = EnemyBehaviorType.SIMPLE_CHASER
            )
            level.addEntity(enemy)
        }

        return level
    }
}
