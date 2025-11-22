package com.starfall.app.game

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlin.random.Random

/**
 * Loads and provides tile sprites from the bundled asset directories.
 */
class TileSpriteProvider(private val assets: AssetManager) {

    private val floorTiles: List<ImageBitmap> = loadBitmaps("tiles/sorted_tiles/floors")
    private val wallTiles: List<ImageBitmap> = loadBitmaps("tiles/sorted_tiles/walls")
    private val glowTiles: List<ImageBitmap> = loadBitmaps("tiles/sorted_tiles/glow")

    fun wallFor(tile: TileUiModel): ImageBitmap? = pickFrom(wallTiles, tile.seed())

    fun floorFor(tile: TileUiModel): ImageBitmap? = pickFrom(floorTiles, tile.seed())

    fun glowFor(tile: TileUiModel): ImageBitmap? {
        if (glowTiles.isEmpty()) return null
        val rng = Random(tile.seed())
        // Keep glow occurrences rare to preserve readability while making them easier to notice.
        if (rng.nextFloat() > 0.12f) return null
        return glowTiles[rng.nextInt(glowTiles.size)]
    }

    private fun loadBitmaps(path: String): List<ImageBitmap> {
        val files = assets.list(path)?.filter { it.endsWith(".png") }?.sorted().orEmpty()
        return files.mapNotNull { file ->
            runCatching {
                assets.open("$path/$file").use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }.filterNotNull()
    }

    private fun pickFrom(tiles: List<ImageBitmap>, seed: Int): ImageBitmap? {
        if (tiles.isEmpty()) return null
        val index = abs(seed % tiles.size)
        return tiles[index]
    }

    private fun TileUiModel.seed(): Int {
        val raw = x * 92821L + y * 68917L + type.hashCode().toLong()
        return (raw % Int.MAX_VALUE).toInt()
    }
}
