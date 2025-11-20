package com.starfall.core.items

/**
 * Data-only description of a weapon variant (material + type).
 * Your inventory / item system can wrap or reference this.
 */
data class WeaponTemplate(
    val id: String,
    val name: String,
    val material: MaterialTier,
    val type: WeaponType,
    val baseDamage: Int,
    val critChanceBonus: Double = 0.0,
    val armorBreakBonus: Double = 0.0,
    val specialTags: List<String> = emptyList()
)
