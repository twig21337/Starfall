package com.starfall.app.game

import com.starfall.core.engine.RunConfig
import com.starfall.core.engine.RunResult
import com.starfall.core.model.EnemyIntentType
import com.starfall.core.model.Position

/** Represents all UI-facing state for the dungeon screen. */
data class GameUiState(
    val width: Int = 0,
    val height: Int = 0,
    val tiles: List<List<TileUiModel>> = emptyList(),
    val entities: List<EntityUiModel> = emptyList(),
    val enemyIntents: List<EnemyIntentUiModel> = emptyList(),
    val playerX: Int = 0,
    val playerY: Int = 0,
    val playerHp: Int = 0,
    val playerMaxHp: Int = 0,
    val playerArmor: Int = 0,
    val playerMaxArmor: Int = 0,
    val playerLevel: Int = 1,
    val messages: List<String> = emptyList(),
    val isGameOver: Boolean = false,
    val lastRunResult: RunResult? = null,
    val currentFloor: Int = 1,
    val totalFloors: Int = GameConfigDefaults.DEFAULT_TOTAL_FLOORS,
    val showDescendPrompt: Boolean = false,
    val descendPromptIsExit: Boolean = false,
    val inventory: List<InventoryItemUiModel> = emptyList(),
    val groundItems: List<GroundItemUiModel> = emptyList(),
    val targetingItemId: Int? = null,
    val targetingPrompt: String? = null,
    val compassDirection: String? = null,
    val playerFacing: FacingDirection = FacingDirection.RIGHT,
    val equippedWeaponSpriteKey: String? = null,
    val equippedArmorSpriteKey: String? = null,
    val levelUpBanner: String? = null,
    val pendingMutations: List<MutationUiModel> = emptyList()
)

/** Basic defaults for Compose previews without engine access. */
object GameConfigDefaults {
    const val DEFAULT_TOTAL_FLOORS: Int = RunConfig.MAX_FLOOR
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

data class EnemyIntentUiModel(
    val enemyId: Int,
    val enemyName: String,
    val enemyPosition: Position,
    val intentType: EnemyIntentType,
    val targetTiles: List<Position>,
    val turnsUntilResolve: Int
)

enum class FacingDirection {
    LEFT,
    RIGHT
}

data class InventoryItemUiModel(
    val id: Int,
    val name: String,
    val icon: String,
    val description: String,
    val isEquipped: Boolean,
    val type: String,
    val quantity: Int,
    val canEquip: Boolean,
    val requiresTarget: Boolean,
    val slotIndex: Int
)

data class GroundItemUiModel(
    val id: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val icon: String,
    val type: String,
    val quantity: Int
)

data class MutationUiModel(
    val id: String,
    val name: String,
    val description: String,
    val tier: String
)
