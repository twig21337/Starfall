package com.starfall.core.items

import kotlin.math.roundToInt

/**
 * Central place for item balance formulas and default templates.
 *
 * This does NOT know about your inventory or entity systems.
 * It only provides numbers and data you can plug into them.
 */
object ItemBalance {

    /**
     * Damage bonus per material tier.
     *
     * WOOD: +0
     * BONE: +1
     * BRONZE: +2
     * IRON: +3
     * STEEL: +4
     * DARKSTEEL: +5
     * AETHERSTEEL: +6
     * VOIDGLASS: +7
     * STARFORGED: +8
     */
    fun tierDamageBonus(material: MaterialTier): Int =
        when (material) {
            MaterialTier.WOOD -> 0
            MaterialTier.BONE -> 1
            MaterialTier.BRONZE -> 2
            MaterialTier.IRON -> 3
            MaterialTier.STEEL -> 4
            MaterialTier.DARKSTEEL -> 5
            MaterialTier.AETHERSTEEL -> 6
            MaterialTier.VOIDGLASS -> 7
            MaterialTier.STARFORGED -> 8
        }

    /**
     * Optional: small crit chance bonus per material tier.
     * Values are percentages expressed as 0.0–1.0.
     */
    fun tierCritBonus(material: MaterialTier): Double =
        when (material) {
            MaterialTier.WOOD -> 0.0
            MaterialTier.BONE -> 0.05   // jagged
            MaterialTier.BRONZE -> 0.0
            MaterialTier.IRON -> 0.02
            MaterialTier.STEEL -> 0.03
            MaterialTier.DARKSTEEL -> 0.05
            MaterialTier.AETHERSTEEL -> 0.05
            MaterialTier.VOIDGLASS -> 0.10
            MaterialTier.STARFORGED -> 0.10
        }

    /**
     * Optional: small armor-break bonus per material tier.
     * e.g. used if you want some weapons to chip armor faster.
     */
    fun tierArmorBreakBonus(material: MaterialTier): Double =
        when (material) {
            MaterialTier.WOOD -> 0.0
            MaterialTier.BONE -> 0.0
            MaterialTier.BRONZE -> 0.0
            MaterialTier.IRON -> 0.05
            MaterialTier.STEEL -> 0.10
            MaterialTier.DARKSTEEL -> 0.15
            MaterialTier.AETHERSTEEL -> 0.15
            MaterialTier.VOIDGLASS -> 0.20
            MaterialTier.STARFORGED -> 0.25
        }

    /**
     * Compute total weapon base damage from weapon type + material tier.
     * You can add this to character attack stats as desired.
     */
    fun weaponDamage(type: WeaponType, material: MaterialTier): Int {
        val base = type.baseDamage
        val bonus = tierDamageBonus(material)
        return base + bonus
    }

    /**
     * Base chest armor capacity per material tier, before slot/weight scaling.
     * Think of this as "how tough is a chest piece in this material".
     *
     * Other slots scale off this using ArmorSlot.chestRelativeScale.
     */
    fun chestArmorBase(material: MaterialTier): Int =
        when (material) {
            MaterialTier.WOOD -> 2
            MaterialTier.BONE -> 3
            MaterialTier.BRONZE -> 4
            MaterialTier.IRON -> 5
            MaterialTier.STEEL -> 6
            MaterialTier.DARKSTEEL -> 7
            MaterialTier.AETHERSTEEL -> 8
            MaterialTier.VOIDGLASS -> 9
            MaterialTier.STARFORGED -> 10
        }

    /**
     * Compute armor capacity for a given material, slot, and weight class.
     * This is the "buffer HP" the piece can absorb before breaking.
     */
    fun armorCapacity(
        material: MaterialTier,
        slot: ArmorSlot,
        weight: ArmorWeight
    ): Int {
        val chestBase = chestArmorBase(material).toDouble()
        val slotScale = slot.chestRelativeScale
        val weightScale = weight.capacityMultiplier
        val raw = chestBase * slotScale * weightScale
        return maxOf(1, raw.roundToInt())
    }

    /**
     * Create a canonical WeaponTemplate for the given material + weapon type.
     * IDs and names are predictable so you can look them up easily.
     */
    fun createWeaponTemplate(
        material: MaterialTier,
        type: WeaponType
    ): WeaponTemplate {
        val id = "${material.name.lowercase()}_${type.name.lowercase()}"
        val name = "${material.displayName} ${type.displayName}"

        val baseDamage = weaponDamage(type, material)
        val critBonus = tierCritBonus(material)
        val armorBreak = tierArmorBreakBonus(material)

        val tags = mutableListOf<String>()

        // Example flavor tags for later use
        when (material) {
            MaterialTier.BONE -> tags += "jagged"
            MaterialTier.DARKSTEEL -> tags += "shadow_touched"
            MaterialTier.AETHERSTEEL -> tags += "astral_tuned"
            MaterialTier.VOIDGLASS -> tags += "sharp_fragile"
            MaterialTier.STARFORGED -> tags += "titan_forged"
            else -> Unit
        }

        return WeaponTemplate(
            id = id,
            name = name,
            material = material,
            type = type,
            baseDamage = baseDamage,
            critChanceBonus = critBonus,
            armorBreakBonus = armorBreak,
            specialTags = tags
        )
    }

    /**
     * Create a canonical ArmorTemplate for the given material + slot + weight class.
     */
    fun createArmorTemplate(
        material: MaterialTier,
        slot: ArmorSlot,
        weight: ArmorWeight
    ): ArmorTemplate {
        val id = "${material.name.lowercase()}_${weight.name.lowercase()}_${slot.name.lowercase()}"
        val name = "${weight.displayName} ${material.displayName} ${slot.displayName}"

        val capacity = armorCapacity(material, slot, weight)

        // Simple penalties: heavier armor = more dodge/move penalty.
        val (dodgePenalty, movePenalty) = when (weight) {
            ArmorWeight.LIGHT -> 0.0 to 0.0
            ArmorWeight.MEDIUM -> 0.05 to 0.05
            ArmorWeight.HEAVY -> 0.10 to 0.10
        }

        val tags = mutableListOf<String>()

        when (material) {
            MaterialTier.AETHERSTEEL -> tags += "astral_resistant"
            MaterialTier.VOIDGLASS -> tags += "shatter_burst"
            MaterialTier.STARFORGED -> tags += "titan_regenerative"
            else -> Unit
        }

        return ArmorTemplate(
            id = id,
            name = name,
            material = material,
            slot = slot,
            weight = weight,
            armorCapacity = capacity,
            dodgePenalty = dodgePenalty,
            movePenalty = movePenalty,
            specialTags = tags
        )
    }

    /**
     * Convenience lists you can use when generating loot tables.
     *
     * Example:
     * - ALL_WEAPON_TEMPLATES.filter { it.material.tierIndex <= 3 } for early floors
     * - ALL_ARMOR_TEMPLATES.filter { it.material >= MaterialTier.IRON } for midgame, etc.
     */
    val ALL_WEAPON_TEMPLATES: List<WeaponTemplate> by lazy {
        MaterialTier.values().flatMap { material ->
            WeaponType.values().map { type ->
                createWeaponTemplate(material, type)
            }
        }
    }

    val ALL_ARMOR_TEMPLATES: List<ArmorTemplate> by lazy {
        MaterialTier.values().flatMap { material ->
            ArmorSlot.values().flatMap { slot ->
                ArmorWeight.values().map { weight ->
                    createArmorTemplate(material, slot, weight)
                }
            }
        }
    }
}
