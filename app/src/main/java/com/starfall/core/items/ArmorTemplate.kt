package com.starfall.core.items

/**
 * Data-only description of an armor piece (slot + weight + material).
 * Armor capacity is the total "buffer HP" this piece can absorb before breaking.
 */
data class ArmorTemplate(
    val id: String,
    val name: String,
    val material: MaterialTier,
    val slot: ArmorSlot,
    val weight: ArmorWeight,
    val armorCapacity: Int,
    val dodgePenalty: Double = 0.0,
    val movePenalty: Double = 0.0,
    val specialTags: List<String> = emptyList()
)
