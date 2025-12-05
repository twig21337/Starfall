package com.starfall.core.dungeon

import com.starfall.core.boss.BossManager
import com.starfall.core.dungeon.boss.BossArenaRegistry
import com.starfall.core.engine.RunConfig
import com.starfall.core.enemy.EnemyScaler
import com.starfall.core.enemy.EnemyTemplates
import com.starfall.core.model.Enemy
import com.starfall.core.model.Level
import com.starfall.core.model.Item
import com.starfall.core.model.ItemType
import com.starfall.core.model.ItemLootTable
import com.starfall.core.model.Position
import com.starfall.core.model.Stats
import com.starfall.core.model.Tile
import com.starfall.core.model.TileType
import com.starfall.core.items.LootGenerator
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Rooms-and-corridors generator that carves rectangular rooms and connects them with tunnels. */
class SimpleDungeonGenerator : DungeonGenerator {
    private var nextEntityId: Int = 1_000

    override fun generate(width: Int, height: Int, depth: Int): Level {
        if (depth == RunConfig.MAX_FLOOR) {
            return generateBossLevel(width, height, depth, useFinalBoss = true)
        }
        if (BossManager.isBossFloor(depth)) {
            return generateBossLevel(width, height, depth, useFinalBoss = false)
        }
        return generateStandardLevel(width, height, depth)
    }

    private fun generateStandardLevel(width: Int, height: Int, depth: Int): Level {
        val tiles = Array(height) { Array(width) { Tile(TileType.WALL) } }
        val rooms = mutableListOf<Room>()
        val floorPositions = mutableSetOf<Position>()
        val preferredEnemyPositions = mutableSetOf<Position>()
        var spawnPos: Position? = null

        val desiredRoomCount = Random.nextInt(6, 11)
        val maxAttempts = desiredRoomCount * 5
        var attempts = 0
        while (rooms.size < desiredRoomCount && attempts < maxAttempts) {
            attempts++
            val roomWidth = randomRoomSize(width)
            val roomHeight = randomRoomSize(height)
            val maxX = width - roomWidth - 1
            val maxY = height - roomHeight - 1
            if (maxX <= 1 || maxY <= 1) continue
            val roomX = Random.nextInt(1, maxX + 1)
            val roomY = Random.nextInt(1, maxY + 1)
            val room = Room(roomX, roomY, roomWidth, roomHeight)
            if (rooms.any { it.intersects(room) }) continue

            val carved = carveRoom(room, tiles)
            floorPositions.addAll(carved)

            if (rooms.isEmpty()) {
                spawnPos = room.center()
            } else {
                preferredEnemyPositions.addAll(carved)
                val prevCenter = rooms.last().center()
                carveCorridor(prevCenter, room.center(), tiles, floorPositions)
            }

            rooms += room
        }

        if (rooms.isEmpty()) {
            val roomWidth = max(6, width / 2)
            val roomHeight = max(6, height / 2)
            val startX = (width - roomWidth) / 2
            val startY = (height - roomHeight) / 2
            val fallbackRoom = Room(startX, startY, roomWidth, roomHeight)
            val carved = carveRoom(fallbackRoom, tiles)
            floorPositions.addAll(carved)
            spawnPos = fallbackRoom.center()
            rooms += fallbackRoom
        }

        val level = Level(
            width = width,
            height = height,
            tiles = tiles,
            entities = mutableListOf(),
            groundItems = mutableListOf(),
            depth = depth
        )
        val playerSpawn = spawnPos ?: Position(width / 2, height / 2)
        level.playerSpawnPosition = playerSpawn
        val stairsPos = rooms.last().center()
        tiles[stairsPos.y][stairsPos.x] = Tile(TileType.STAIRS_DOWN)
        level.stairsDownPosition = stairsPos

        val allAvailablePositions = floorPositions
            .filter { it != playerSpawn && it != stairsPos }
            .toMutableList()
        val preferredPositions = preferredEnemyPositions
            .filter { it != playerSpawn && it != stairsPos }
            .toMutableList()

        val enemyCount = min(allAvailablePositions.size, Random.nextInt(4, 9))
        repeat(enemyCount) {
            val pos = if (preferredPositions.isNotEmpty()) {
                val index = Random.nextInt(preferredPositions.size)
                val chosen = preferredPositions.removeAt(index)
                allAvailablePositions.remove(chosen)
                chosen
            } else {
                val index = Random.nextInt(allAvailablePositions.size)
                allAvailablePositions.removeAt(index)
            }

            val template = EnemyTemplates.randomEnemyForDepth(depth)
            // Hook for elites; defaults to false until dedicated logic is added.
            val isElite = false
            val scaledStats = EnemyScaler.scaleEnemyForFloor(template, depth, isElite)
            val stats = Stats(
                maxHp = scaledStats.hp,
                hp = scaledStats.hp,
                attack = scaledStats.attack,
                defense = scaledStats.defense
            )
                val enemy = Enemy(
                    id = nextEntityId++,
                    name = template.name,
                    position = pos,
                    glyph = template.glyph,
                    stats = stats,
                    behaviorType = template.behaviorType,
                    xpReward = scaledStats.xp,
                    templateId = template.id,
                    sightRange = template.sightRange,
                    tags = template.tags
                )
                level.addEntity(enemy)
            }

        if (allAvailablePositions.isNotEmpty()) {
            val remainingPositions = allAvailablePositions.toMutableList()
            val desiredLoot = (1 + Random.nextInt(1, 3 + depth.coerceAtMost(6))).coerceAtMost(remainingPositions.size)
            repeat(desiredLoot) {
                val roll = Random.nextDouble()
                val placed = if (roll < 0.45) {
                    val itemType = ItemLootTable.randomConsumableForDepth(depth)
                    placeRandomItem(level, remainingPositions, itemType)
                    true
                } else {
                    placeEquipmentFromLootGenerator(level, remainingPositions, depth)
                }
                if (!placed) {
                    val fallbackType = ItemLootTable.randomConsumableForDepth(depth)
                    placeRandomItem(level, remainingPositions, fallbackType)
                }
            }
        }

        return level
    }

    private fun generateBossLevel(width: Int, height: Int, depth: Int, useFinalBoss: Boolean): Level {
        val tiles = Array(height) { Array(width) { Tile(TileType.WALL) } }

        val bossInstance = BossManager.selectBossForDepth(depth)
        val arenaLayout = BossArenaRegistry.buildArena(bossInstance.definition.id, tiles)

        val level = Level(
            width = width,
            height = height,
            tiles = tiles,
            entities = mutableListOf(),
            groundItems = mutableListOf(),
            depth = depth,
            isBossFloor = true,
            isFinalFloor = useFinalBoss
        )

        val spawn = arenaLayout.playerSpawn
        level.playerSpawnPosition = spawn

        level.bossInstance = bossInstance
        val bossPosition = arenaLayout.bossSpawn
        val boss = Enemy(
            id = nextEntityId++,
            name = bossInstance.definition.name,
            position = bossPosition,
            glyph = bossInstance.definition.glyph,
            stats = bossInstance.stats.copy(),
            behaviorType = bossInstance.definition.behaviorType,
            bossData = bossInstance
        )
        level.addEntity(boss)

        return level
    }

    private fun randomRoomSize(limit: Int): Int {
        val minSize = 4
        val maxSize = min(8, limit - 2)
        if (maxSize <= minSize) {
            return max(3, maxSize)
        }
        return Random.nextInt(minSize, maxSize + 1)
    }

    private fun carveRoom(room: Room, tiles: Array<Array<Tile>>): List<Position> {
        val carved = mutableListOf<Position>()
        for (y in room.y until room.y + room.height) {
            for (x in room.x until room.x + room.width) {
                tiles[y][x] = Tile(TileType.FLOOR)
                carved += Position(x, y)
            }
        }
        return carved
    }

    private fun carveCorridor(
        start: Position,
        end: Position,
        tiles: Array<Array<Tile>>,
        floorPositions: MutableSet<Position>
    ) {
        val horizontalFirst = Random.nextBoolean()
        if (horizontalFirst) {
            carveHorizontal(start.x, end.x, start.y, tiles, floorPositions)
            carveVertical(start.y, end.y, end.x, tiles, floorPositions)
        } else {
            carveVertical(start.y, end.y, start.x, tiles, floorPositions)
            carveHorizontal(start.x, end.x, end.y, tiles, floorPositions)
        }
    }

    private fun carveHorizontal(
        x1: Int,
        x2: Int,
        y: Int,
        tiles: Array<Array<Tile>>,
        floorPositions: MutableSet<Position>
    ) {
        val startX = min(x1, x2)
        val endX = max(x1, x2)
        for (x in startX..endX) {
            if (y in tiles.indices && x in tiles[y].indices) {
                tiles[y][x] = Tile(TileType.FLOOR)
                floorPositions += Position(x, y)
            }
        }
    }

    private fun carveVertical(
        y1: Int,
        y2: Int,
        x: Int,
        tiles: Array<Array<Tile>>,
        floorPositions: MutableSet<Position>
    ) {
        val startY = min(y1, y2)
        val endY = max(y1, y2)
        for (y in startY..endY) {
            if (y in tiles.indices && x in tiles[y].indices) {
                tiles[y][x] = Tile(TileType.FLOOR)
                floorPositions += Position(x, y)
            }
        }
    }

    private fun placeRandomItem(level: Level, positions: MutableList<Position>, itemType: ItemType) {
        if (positions.isEmpty()) return

        val itemPositionIndex = Random.nextInt(positions.size)
        val position = positions.removeAt(itemPositionIndex)
        val item = Item(
            id = level.allocateItemId(),
            type = itemType,
            position = position
        )
        level.addItem(item)
    }

    private fun placeEquipmentFromLootGenerator(
        level: Level,
        positions: MutableList<Position>,
        depth: Int
    ): Boolean {
        val drop = LootGenerator.rollRandomEquipmentForDepth(depth) ?: return false
        if (positions.isEmpty()) return false

        val itemPositionIndex = Random.nextInt(positions.size)
        val position = positions.removeAt(itemPositionIndex)
        val item = when (drop) {
            is LootGenerator.EquipmentDropResult.WeaponDrop -> Item(
                id = level.allocateItemId(),
                type = ItemType.EQUIPMENT_WEAPON,
                position = position,
                weaponTemplate = drop.template
            )
            is LootGenerator.EquipmentDropResult.ArmorDrop -> Item(
                id = level.allocateItemId(),
                type = ItemType.EQUIPMENT_ARMOR,
                position = position,
                armorTemplate = drop.template
            )
        }
        level.addItem(item)
        return true
    }

    private data class Room(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun center(): Position = Position(x + width / 2, y + height / 2)

        fun intersects(other: Room): Boolean {
            val margin = 1
            val thisLeft = x - margin
            val thisRight = x + width + margin
            val thisTop = y - margin
            val thisBottom = y + height + margin

            val otherLeft = other.x - margin
            val otherRight = other.x + other.width + margin
            val otherTop = other.y - margin
            val otherBottom = other.y + other.height + margin

            val horizontalOverlap = thisLeft < otherRight && thisRight > otherLeft
            val verticalOverlap = thisTop < otherBottom && thisBottom > otherTop
            return horizontalOverlap && verticalOverlap
        }
    }
}
