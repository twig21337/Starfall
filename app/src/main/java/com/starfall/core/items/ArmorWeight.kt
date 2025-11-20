package com.starfall.core.items

/**
 * Rough weight class, used to scale armor capacity and penalties.
 */
enum class ArmorWeight(
    val displayName: String,
    val capacityMultiplier: Double
) {
    LIGHT("Light", capacityMultiplier = 1.0),
    MEDIUM("Medium", capacityMultiplier = 1.5),
    HEAVY("Heavy", capacityMultiplier = 2.0);
}
