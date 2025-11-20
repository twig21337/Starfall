package com.starfall.core.items

import kotlin.random.Random

/**
 * Central helper for generating random weapon and armor templates
 * appropriate for a given dungeon depth.
 *
 * This works purely at the template level; your inventory / item systems
 * are responsible for turning templates into actual items in the world.
 */
object LootGenerator {

    /**
     * Decide which material tiers are allowed at a given dungeon depth.
     *
     * This is a simple, tunable mapping. You can tweak the ranges later.
     *
     * Example (you can encode it as you see fit):
     * - Depth 1–3: WOOD, BONE, BRONZE
     * - Depth 4–6: BONE, BRONZE, IRON, STEEL
     * - Depth 7–10: BRONZE, IRON, STEEL, DARKSTEEL, AETHERSTEEL
     * - Depth 11+: STEEL, DARKSTEEL, AETHERSTEEL, VOIDGLASS, STARFORGED
     */
    fun allowedMaterialsForDepth(depth: Int): List<MaterialTier> {
        return when {
            depth <= 3 -> listOf(
                MaterialTier.WOOD,
                MaterialTier.BONE,
                MaterialTier.BRONZE
            )
            depth in 4..6 -> listOf(
                MaterialTier.BONE,
                MaterialTier.BRONZE,
                MaterialTier.IRON,
                MaterialTier.STEEL
            )
            depth in 7..10 -> listOf(
                MaterialTier.BRONZE,
                MaterialTier.IRON,
                MaterialTier.STEEL,
                MaterialTier.DARKSTEEL,
                MaterialTier.AETHERSTEEL
            )
            else -> listOf(
                MaterialTier.STEEL,
                MaterialTier.DARKSTEEL,
                MaterialTier.AETHERSTEEL,
                MaterialTier.VOIDGLASS,
                MaterialTier.STARFORGED
            )
        }
    }

    /**
     * Given a list of allowed material tiers and a Random, return a random material
     * with a slight bias toward lower tiers (so high-tier items are rarer).
     */
    fun weightedRandomMaterial(
        allowed: List<MaterialTier>,
        rng: Random = Random.Default
    ): MaterialTier? {
        if (allowed.isEmpty()) return null

        // Simple weighting: earlier tiers in the list are more common.
        // e.g. weights 4, 3, 2, 1 for a 4-tier list.
        val weights = allowed.indices.map { index ->
            (allowed.size - index).toDouble()
        }
        val totalWeight = weights.sum()
        val roll = rng.nextDouble() * totalWeight

        var cumulative = 0.0
        for (i in allowed.indices) {
            cumulative += weights[i]
            if (roll <= cumulative) {
                return allowed[i]
            }
        }
        return allowed.last()
    }

    /**
     * Roll to see if any equipment should drop at all.
     *
     * You can tune this per depth. For now:
     * - Early depths: lower chance
     * - Deeper depths: slightly higher chance
     */
    fun shouldDropEquipment(
        depth: Int,
        rng: Random = Random.Default
    ): Boolean {
        val baseChance = when {
            depth <= 3 -> 0.20
            depth in 4..6 -> 0.25
            depth in 7..10 -> 0.30
            else -> 0.35
        }
        return rng.nextDouble() < baseChance
    }

    /**
     * Randomly choose whether a drop should be a weapon or armor.
     * You can tweak this ratio later.
     */
    fun rollWeaponVsArmor(rng: Random = Random.Default): DropType {
        val pWeapon = 0.5 // 50/50 split for now
        return if (rng.nextDouble() < pWeapon) {
            DropType.WEAPON
        } else {
            DropType.ARMOR
        }
    }

    /**
     * Return a random WeaponTemplate appropriate for the given depth,
     * or null if no valid templates are found.
     */
    fun randomWeaponTemplateForDepth(
        depth: Int,
        rng: Random = Random.Default
    ): WeaponTemplate? {
        val allowedMaterials = allowedMaterialsForDepth(depth)
        if (allowedMaterials.isEmpty()) return null

        // Filter all known weapon templates down to the allowed materials.
        val candidates = ItemBalance.ALL_WEAPON_TEMPLATES.filter { template ->
            allowedMaterials.contains(template.material)
        }
        if (candidates.isEmpty()) return null

        // Optionally bias the material tier within candidates.
        val chosenMaterial = weightedRandomMaterial(allowedMaterials, rng)
        val materialFiltered = if (chosenMaterial != null) {
            candidates.filter { it.material == chosenMaterial }.ifEmpty { candidates }
        } else {
            candidates
        }

        return materialFiltered.random(rng)
    }

    /**
     * Return a random ArmorTemplate appropriate for the given depth,
     * or null if no valid templates are found.
     *
     * For v1, all armor slots and weights are allowed; later you can
     * restrict by class or dungeon type if needed.
     */
    fun randomArmorTemplateForDepth(
        depth: Int,
        rng: Random = Random.Default
    ): ArmorTemplate? {
        val allowedMaterials = allowedMaterialsForDepth(depth)
        if (allowedMaterials.isEmpty()) return null

        val candidates = ItemBalance.ALL_ARMOR_TEMPLATES.filter { template ->
            allowedMaterials.contains(template.material)
        }
        if (candidates.isEmpty()) return null

        val chosenMaterial = weightedRandomMaterial(allowedMaterials, rng)
        val materialFiltered = if (chosenMaterial != null) {
            candidates.filter { it.material == chosenMaterial }.ifEmpty { candidates }
        } else {
            candidates
        }

        return materialFiltered.random(rng)
    }

    /**
     * Top-level helper: roll to see if equipment should drop,
     * and if so, whether it is a weapon or armor, returning a
     * template plus its type.
     */
    fun rollRandomEquipmentForDepth(
        depth: Int,
        rng: Random = Random.Default
    ): EquipmentDropResult? {
        if (!shouldDropEquipment(depth, rng)) return null

        return when (rollWeaponVsArmor(rng)) {
            DropType.WEAPON -> {
                val weapon = randomWeaponTemplateForDepth(depth, rng) ?: return null
                EquipmentDropResult.WeaponDrop(weapon)
            }
            DropType.ARMOR -> {
                val armor = randomArmorTemplateForDepth(depth, rng) ?: return null
                EquipmentDropResult.ArmorDrop(armor)
            }
        }
    }

    /**
     * What kind of equipment drop was generated.
     */
    sealed class EquipmentDropResult {
        data class WeaponDrop(val template: WeaponTemplate) : EquipmentDropResult()
        data class ArmorDrop(val template: ArmorTemplate) : EquipmentDropResult()
    }

    enum class DropType {
        WEAPON,
        ARMOR
    }
}

