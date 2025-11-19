package com.starfall.core.engine

/** Shared configuration constants for the engine. */
object GameConfig {
    const val DEFAULT_LEVEL_WIDTH = 32
    const val DEFAULT_LEVEL_HEIGHT = 18
    const val PLAYER_VISION_RADIUS = 6
    const val CAMERA_VIEW_WIDTH = PLAYER_VISION_RADIUS * 2 + 1
    const val CAMERA_VIEW_HEIGHT = PLAYER_VISION_RADIUS * 2 + 1
}
