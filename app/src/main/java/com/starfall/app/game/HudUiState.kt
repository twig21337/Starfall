package com.starfall.app.game

/**
 * Bottom HUD tab definitions. Add a new tab by extending this enum and wiring
 * the new branch into [HudUiState] and the [BottomHud] composable.
 */
enum class BottomHudTab {
    STATS,
    MUTATIONS,
    XP,
    MAP
}

/**
 * Basic combat stats for the Stats panel. Adjust the exposed fields here when
 * the underlying player stats evolve.
 */
data class StatsPanelState(
    val attack: Int = 0,
    val defense: Int = 0,
    val critChance: Int = 0,
    val dodgeChance: Int = 0,
    val statusEffects: List<String> = emptyList()
)

data class MutationEntry(
    val name: String,
    val tier: Int,
    val shortDescription: String
)

data class MutationsPanelState(
    val mutations: List<MutationEntry> = emptyList()
)

data class XpPanelState(
    val level: Int = 1,
    val xp: Int = 0,
    val xpToNext: Int = 0
)

data class MapPanelState(
    val floorNumber: Int = 1,
    val maxFloor: Int = 1,
    val discoveredPercentage: Int = 0
)

/**
 * Composite HUD state. Fields are updated from RunManager, the active Player,
 * and the current Dungeon Level.
 */
data class HudUiState(
    val selectedTab: BottomHudTab = BottomHudTab.STATS,
    val currentHp: Int = 0,
    val maxHp: Int = 0,
    val currentFloor: Int = 1,
    val maxFloor: Int = 1,
    val currentLevel: Int = 1,
    val currentXp: Int = 0,
    val xpToNext: Int = 0,
    val statsPanel: StatsPanelState = StatsPanelState(),
    val mutationsPanel: MutationsPanelState = MutationsPanelState(),
    val xpPanel: XpPanelState = XpPanelState(),
    val mapPanel: MapPanelState = MapPanelState()
)
