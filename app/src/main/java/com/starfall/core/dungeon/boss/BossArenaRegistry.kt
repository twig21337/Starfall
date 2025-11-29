package com.starfall.core.dungeon.boss

import com.starfall.core.model.Position
import com.starfall.core.model.Tile
import com.starfall.core.model.TileType
import kotlin.math.max
import kotlin.math.min

/** Provides handcrafted arenas for each boss by ID, keeping layouts easy to extend. */
object BossArenaRegistry {

    data class BossArenaLayout(
        val playerSpawn: Position,
        val bossSpawn: Position
    )

    private typealias ArenaBuilder = (Array<Array<Tile>>, Int, Int) -> BossArenaLayout

    private val arenaBuilders: Map<String, ArenaBuilder> = mapOf(
        "fallen_astromancer" to ::buildAstromancerArena,
        "bone_forged_colossus" to ::buildColossusArena,
        "blighted_hive_mind" to ::buildHiveMindArena,
        "echo_knight_remnant" to ::buildEchoKnightArena,
        "heartstealer_wyrm" to ::buildHeartstealerArena
    )

    fun buildArena(bossId: String, tiles: Array<Array<Tile>>): BossArenaLayout {
        val width = tiles.firstOrNull()?.size ?: 0
        val height = tiles.size
        val builder = arenaBuilders[bossId] ?: ::buildDefaultArena
        return builder(tiles, width, height)
    }

    private fun buildDefaultArena(
        tiles: Array<Array<Tile>>,
        levelWidth: Int,
        levelHeight: Int
    ): BossArenaLayout {
        val arenaWidth = max(8, (levelWidth * 0.65).toInt())
        val arenaHeight = max(6, (levelHeight * 0.55).toInt())
        val startX = (levelWidth - arenaWidth) / 2
        val startY = (levelHeight - arenaHeight) / 2
        carveRectangle(tiles, startX, startY, arenaWidth, arenaHeight, TileType.FLOOR)
        val center = Position(startX + arenaWidth / 2, startY + arenaHeight / 2)
        val playerSpawn = Position(startX + 2, startY + arenaHeight / 2)
        return BossArenaLayout(playerSpawn = playerSpawn, bossSpawn = center)
    }

    private fun buildAstromancerArena(
        tiles: Array<Array<Tile>>,
        levelWidth: Int,
        levelHeight: Int
    ): BossArenaLayout {
        val arenaWidth = min(levelWidth - 2, 24)
        val arenaHeight = min(levelHeight - 2, 14)
        val startX = (levelWidth - arenaWidth) / 2
        val startY = (levelHeight - arenaHeight) / 2
        carveRectangle(tiles, startX, startY, arenaWidth, arenaHeight, TileType.FLOOR)

        val center = Position(startX + arenaWidth / 2, startY + arenaHeight / 2)
        val pillarOffsets = listOf(
            Position(2, 2),
            Position(arenaWidth - 3, 2),
            Position(2, arenaHeight - 3),
            Position(arenaWidth - 3, arenaHeight - 3)
        )
        pillarOffsets.forEach { offset ->
            setTileSafe(tiles, startX + offset.x, startY + offset.y, TileType.WALL)
        }

        val starPoints = listOf(
            center.copy(x = center.x - 1),
            center.copy(x = center.x + 1),
            center.copy(y = center.y - 1),
            center.copy(y = center.y + 1)
        )
        starPoints.forEach { pos -> setTileSafe(tiles, pos.x, pos.y, TileType.TRAP) }

        val playerSpawn = Position(startX + 2, startY + arenaHeight / 2)
        return BossArenaLayout(playerSpawn = playerSpawn, bossSpawn = center)
    }

    private fun buildColossusArena(
        tiles: Array<Array<Tile>>,
        levelWidth: Int,
        levelHeight: Int
    ): BossArenaLayout {
        val arenaWidth = min(levelWidth - 2, 24)
        val arenaHeight = min(levelHeight - 2, 13)
        val startX = (levelWidth - arenaWidth) / 2
        val startY = (levelHeight - arenaHeight) / 2
        carveRectangle(tiles, startX, startY, arenaWidth, arenaHeight, TileType.FLOOR)

        val obstacles = listOf(
            Rect(startX + 4, startY + 3, 3, 2),
            Rect(startX + arenaWidth - 7, startY + 3, 3, 2),
            Rect(startX + 6, startY + arenaHeight - 5, 4, 2),
            Rect(startX + arenaWidth - 10, startY + arenaHeight - 5, 4, 2),
            Rect(startX + arenaWidth / 2 - 5, startY + arenaHeight / 2 - 1, 3, 2),
            Rect(startX + arenaWidth / 2 + 2, startY + arenaHeight / 2 - 1, 3, 2)
        )
        obstacles.forEach { rect ->
            carveRectangle(tiles, rect.x, rect.y, rect.width, rect.height, TileType.WALL)
        }

        val bossSpawn = Position(startX + arenaWidth / 2, startY + arenaHeight / 2)
        val playerSpawn = Position(startX + 2, startY + arenaHeight - 3)
        return BossArenaLayout(playerSpawn = playerSpawn, bossSpawn = bossSpawn)
    }

    private fun buildHiveMindArena(
        tiles: Array<Array<Tile>>,
        levelWidth: Int,
        levelHeight: Int
    ): BossArenaLayout {
        val arenaWidth = min(levelWidth - 2, 20)
        val arenaHeight = min(levelHeight - 2, 12)
        val startX = (levelWidth - arenaWidth) / 2
        val startY = (levelHeight - arenaHeight) / 2
        carveRectangle(tiles, startX, startY, arenaWidth, arenaHeight, TileType.FLOOR)

        val poolSize = 2
        val cornerOffsets = listOf(
            Position(1, 1),
            Position(arenaWidth - poolSize - 1, 1),
            Position(1, arenaHeight - poolSize - 1),
            Position(arenaWidth - poolSize - 1, arenaHeight - poolSize - 1)
        )
        cornerOffsets.forEach { offset ->
            carveRectangle(tiles, startX + offset.x, startY + offset.y, poolSize, poolSize, TileType.TRAP)
        }

        val fungusClusters = listOf(
            Rect(startX + arenaWidth / 2 - 2, startY + arenaHeight / 2 - 1, 2, 2),
            Rect(startX + arenaWidth / 2 + 1, startY + arenaHeight / 2, 2, 2)
        )
        fungusClusters.forEach { rect ->
            carveRectangle(tiles, rect.x, rect.y, rect.width, rect.height, TileType.WALL)
        }

        val bossSpawn = Position(startX + arenaWidth / 2, startY + arenaHeight / 2)
        val playerSpawn = Position(startX + 2, startY + arenaHeight / 2)
        return BossArenaLayout(playerSpawn = playerSpawn, bossSpawn = bossSpawn)
    }

    private fun buildEchoKnightArena(
        tiles: Array<Array<Tile>>,
        levelWidth: Int,
        levelHeight: Int
    ): BossArenaLayout {
        val arenaWidth = min(levelWidth - 2, 22)
        val arenaHeight = min(levelHeight - 2, 14)
        val startX = (levelWidth - arenaWidth) / 2
        val startY = (levelHeight - arenaHeight) / 2

        val centerX = startX + arenaWidth / 2
        val centerY = startY + arenaHeight / 2
        val armThickness = 6
        carveRectangle(tiles, centerX - armThickness / 2, startY + 1, armThickness, arenaHeight - 2, TileType.FLOOR)
        carveRectangle(tiles, startX + 1, centerY - armThickness / 2, arenaWidth - 2, armThickness, TileType.FLOOR)

        val edgeObstacles = listOf(
            Position(centerX - 5, startY + 2),
            Position(centerX + 5, startY + 2),
            Position(centerX - 5, startY + arenaHeight - 3),
            Position(centerX + 5, startY + arenaHeight - 3)
        )
        edgeObstacles.forEach { pos -> setTileSafe(tiles, pos.x, pos.y, TileType.WALL) }

        val bossSpawn = Position(centerX, centerY)
        val playerSpawn = Position(centerX, startY + arenaHeight - 3)
        return BossArenaLayout(playerSpawn = playerSpawn, bossSpawn = bossSpawn)
    }

    private fun buildHeartstealerArena(
        tiles: Array<Array<Tile>>,
        levelWidth: Int,
        levelHeight: Int
    ): BossArenaLayout {
        val arenaWidth = min(levelWidth - 2, 26)
        val arenaHeight = min(levelHeight - 2, 14)
        val startX = (levelWidth - arenaWidth) / 2
        val startY = (levelHeight - arenaHeight) / 2
        carveRectangle(tiles, startX, startY, arenaWidth, arenaHeight, TileType.FLOOR)

        val hazardRingX = startX + 1
        val hazardRingY = startY + 1
        val hazardWidth = arenaWidth - 2
        val hazardHeight = arenaHeight - 2
        outlineRectangle(tiles, hazardRingX, hazardRingY, hazardWidth, hazardHeight, TileType.TRAP)

        val islands = listOf(
            Rect(startX + arenaWidth / 3 - 1, startY + arenaHeight / 2 - 1, 2, 2),
            Rect(startX + 2 * arenaWidth / 3 - 1, startY + arenaHeight / 2, 2, 2)
        )
        islands.forEach { rect -> carveRectangle(tiles, rect.x, rect.y, rect.width, rect.height, TileType.WALL) }

        val bossSpawn = Position(startX + arenaWidth / 2, startY + arenaHeight / 2)
        val playerSpawn = Position(startX + arenaWidth / 2, startY + arenaHeight - 3)
        return BossArenaLayout(playerSpawn = playerSpawn, bossSpawn = bossSpawn)
    }

    private fun carveRectangle(
        tiles: Array<Array<Tile>>,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        type: TileType
    ) {
        for (y in startY until startY + height) {
            for (x in startX until startX + width) {
                setTileSafe(tiles, x, y, type)
            }
        }
    }

    private fun outlineRectangle(
        tiles: Array<Array<Tile>>,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        type: TileType
    ) {
        for (x in startX until startX + width) {
            setTileSafe(tiles, x, startY, type)
            setTileSafe(tiles, x, startY + height - 1, type)
        }
        for (y in startY until startY + height) {
            setTileSafe(tiles, startX, y, type)
            setTileSafe(tiles, startX + width - 1, y, type)
        }
    }

    private fun setTileSafe(tiles: Array<Array<Tile>>, x: Int, y: Int, type: TileType) {
        if (y in tiles.indices && x in tiles[y].indices) {
            tiles[y][x] = Tile(type)
        }
    }

    private data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)
}
