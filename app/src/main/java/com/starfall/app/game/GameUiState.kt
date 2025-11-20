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
    val playerArmor: Int = 0,
    val playerMaxArmor: Int = 0,
    val messages: List<String> = emptyList(),
    val isGameOver: Boolean = false,
    val currentFloor: Int = 1,
    val totalFloors: Int = GameConfigDefaults.DEFAULT_TOTAL_FLOORS,
    val showDescendPrompt: Boolean = false,
    val descendPromptIsExit: Boolean = false,
    val inventory: List<InventoryItemUiModel> = emptyList(),
    val groundItems: List<GroundItemUiModel> = emptyList()
)

/** Basic defaults for Compose previews without engine access. */
object GameConfigDefaults {
    const val DEFAULT_TOTAL_FLOORS: Int = 10
}

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

data class InventoryItemUiModel(
    val id: Int,
    val name: String,
    val icon: String,
    val description: String,
    val isEquipped: Boolean,
    val type: String
)

data class GroundItemUiModel(
    val id: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val icon: String,
    val type: String
)
