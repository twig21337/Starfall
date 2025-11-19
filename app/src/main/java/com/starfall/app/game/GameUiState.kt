package com.starfall.app.game

/** Represents all UI-facing state for the dungeon screen. */
data class GameUiState(
    val width: Int = 0,
    val height: Int = 0,
    val tiles: List<List<TileUiModel>> = emptyList(),
    val entities: List<EntityUiModel> = emptyList(),
    val playerX: Int = 0,
    val playerY: Int = 0,
    val playerHp: Int = 0,
    val playerMaxHp: Int = 0,
    val messages: List<String> = emptyList(),
    val isGameOver: Boolean = false
)

data class TileUiModel(
    val x: Int,
    val y: Int,
    val type: String,
    val visible: Boolean,
    val discovered: Boolean
)

data class EntityUiModel(
    val id: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val glyph: Char,
    val isPlayer: Boolean
)
