package com.starfall.core.model

/** Types of lootable items the player can collect. */
enum class ItemType(val displayName: String, val icon: String, val description: String) {
    HEALING_POTION(
        displayName = "Healing Potion",
        icon = "🧪",
        description = "Restores 5 HP when consumed."
    ),
    WOOD_SWORD(
        displayName = "Wood Sword",
        icon = "⚔️",
        description = "+1 attack while equipped."
    ),
    WOOD_ARMOR(
        displayName = "Wood Body Armor",
        icon = "🛡️",
        description = "+1 defense while equipped."
    );
}

/** Represents an item that can live on the ground or in the player's inventory. */
data class Item(
    val id: Int,
    val type: ItemType,
    var position: Position? = null,
    var isEquipped: Boolean = false
)
