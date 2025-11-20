package com.starfall.core.items

/**
 * Equipment slots for armor pieces.
 */
enum class ArmorSlot(
    val displayName: String,
    val chestRelativeScale: Double
) {
    HELM("Helm", chestRelativeScale = 0.6),
    CHEST("Chest", chestRelativeScale = 1.0),
    GLOVES("Gloves", chestRelativeScale = 0.5),
    BOOTS("Boots", chestRelativeScale = 0.5),
    SHIELD("Shield", chestRelativeScale = 0.8);
}
