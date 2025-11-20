package com.starfall.core.items

/**
 * Core melee weapon archetypes for Starfall.
 */
enum class WeaponType(
    val displayName: String,
    val baseDamage: Int
) {
    DAGGER("Dagger", baseDamage = 2),
    SWORD("Sword", baseDamage = 3),
    AXE("Axe", baseDamage = 4),
    SPEAR("Spear", baseDamage = 3),
    STAFF("Staff", baseDamage = 2);
}
