package com.starfall.app.game

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Composes hero sprites by layering the base hero frames with equipped weapon and armor overlays.
 *
 * Weapon and armor frames are loaded lazily; missing assets simply omit that layer so the hero
 * still renders correctly while the art pipeline is being filled in.
 */
class HeroSpriteComposer(private val assets: AssetManager) {

    private val idleLeftFrames: List<ImageBitmap> = loadFrames("animations/idle Facing Left")
    private val idleRightFrames: List<ImageBitmap> = loadFrames("animations/idle Facing Right")

    private val weaponCache: MutableMap<String, EquipmentFrames> = mutableMapOf()
    private val armorCache: MutableMap<String, EquipmentFrames> = mutableMapOf()

    fun idleFrames(direction: FacingDirection, appearance: HeroSpriteLayers): List<ImageBitmap> {
        val baseFrames = when (direction) {
            FacingDirection.LEFT -> idleLeftFrames
            FacingDirection.RIGHT -> idleRightFrames
        }.ifEmpty { emptyList() }

        val weaponFrames = appearance.weaponSpriteKey?.let { key ->
            weaponCache.getOrPut(key) { loadEquipmentFrames("weapons/$key") }
        }
        val armorFrames = appearance.armorSpriteKey?.let { key ->
            armorCache.getOrPut(key) { loadEquipmentFrames("armor/$key") }
        }

        if (baseFrames.isEmpty()) return emptyList()
        if (weaponFrames == null && armorFrames == null) return baseFrames

        val composedFrames = mutableListOf<ImageBitmap>()
        val frameCount = baseFrames.size
        repeat(frameCount) { index ->
            val overlays = listOfNotNull(
                armorFrames?.frameAt(index, direction),
                weaponFrames?.frameAt(index, direction)
            )
            composedFrames += compose(baseFrames[index], overlays)
        }
        return composedFrames
    }

    private fun EquipmentFrames.frameAt(index: Int, direction: FacingDirection): ImageBitmap? {
        val frames = framesFor(direction)
        if (frames.isEmpty()) return null
        return frames.getOrNull(index % frames.size)
    }

    private fun compose(base: ImageBitmap, overlays: List<ImageBitmap>): ImageBitmap {
        if (overlays.isEmpty()) return base
        val resultBitmap = base.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        overlays.forEach { overlay ->
            canvas.drawBitmap(overlay.asAndroidBitmap(), 0f, 0f, null)
        }
        return resultBitmap.asImageBitmap()
    }

    private fun loadEquipmentFrames(path: String): EquipmentFrames {
        val left = loadFrames("animations/$path/idle_left")
        val right = loadFrames("animations/$path/idle_right")
        return EquipmentFrames(left, right)
    }

    private fun loadFrames(path: String): List<ImageBitmap> {
        val files = assets.list(path)?.filter { it.endsWith(".png") }?.sorted().orEmpty()
        return files.mapNotNull { file ->
            runCatching {
                assets.open("$path/$file").use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }.filterNotNull()
    }
}

data class HeroSpriteLayers(
    val weaponSpriteKey: String? = null,
    val armorSpriteKey: String? = null
)

private data class EquipmentFrames(
    private val leftFrames: List<ImageBitmap>,
    private val rightFrames: List<ImageBitmap>
) {
    fun framesFor(direction: FacingDirection): List<ImageBitmap> {
        return when (direction) {
            FacingDirection.LEFT -> leftFrames.ifEmpty { rightFrames }
            FacingDirection.RIGHT -> rightFrames.ifEmpty { leftFrames }
        }
    }
}
