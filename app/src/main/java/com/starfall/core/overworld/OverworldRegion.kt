package com.starfall.core.overworld

/**
 * Data-driven definitions for overworld destinations. Add new regions here and register them in
 * [OverworldRegions.all] so they become available to the hub screen.
 */
data class OverworldRegion(
    val id: String,
    val name: String,
    val description: String,
    val minFloors: Int,
    val maxFloors: Int,
    val isBossRequired: Boolean = true,
    val baseDifficulty: Int = 1,
    val unlockCostTitanShards: Int = 0,
    val dependencies: List<String> = emptyList()
)

/**
 * Canonical catalog of overworld regions. UI screens can iterate over [all] to show options and use
 * [byId] when resuming based on saved selections.
 */
object OverworldRegions {
    val FALLEN_TITAN = OverworldRegion(
        id = "fallen_titan_depths",
        name = "Fallen Titan Depths",
        description = "Descend into the heart-crater of the fallen Titan.",
        minFloors = 10,
        maxFloors = 20,
        isBossRequired = true,
        baseDifficulty = 1,
        unlockCostTitanShards = 0
    )

    val all: List<OverworldRegion> = listOf(FALLEN_TITAN)
    val byId: Map<String, OverworldRegion> = all.associateBy { it.id }
}
