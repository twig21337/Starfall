package com.starfall.core.progression

/**
 * Static catalog of Titan Shard upgrades. Effects are interpreted by [MetaProgression] when
 * computing [EffectiveMetaBonuses], keeping the data definitions UI-friendly and easy to extend.
 */
data class MetaUpgradeDefinition(
    val id: String,
    val name: String,
    val description: String,
    val maxLevel: Int,
    val baseCost: Int,
    val costGrowthPerLevel: Int,
    val category: MetaUpgradeCategory
)

enum class MetaUpgradeCategory {
    SURVIVABILITY,
    ECONOMY,
    LOOT,
    MUTATIONS,
    UTILITY
}

object MetaUpgrades {
    // Add new upgrades by appending to [all] and wiring their effects in MetaProgression.computeEffectiveBonuses.
    val all: List<MetaUpgradeDefinition> = listOf(
        MetaUpgradeDefinition(
            id = "meta_max_hp",
            name = "Titan-Hardened Body",
            description = "+5 max HP per level (up to +25).",
            maxLevel = 5,
            baseCost = 25,
            costGrowthPerLevel = 10,
            category = MetaUpgradeCategory.SURVIVABILITY
        ),
        MetaUpgradeDefinition(
            id = "meta_starting_potion",
            name = "Starborn Resilience",
            description = "+1 starting healing potion per level.",
            maxLevel = 3,
            baseCost = 20,
            costGrowthPerLevel = 15,
            category = MetaUpgradeCategory.UTILITY
        ),
        MetaUpgradeDefinition(
            id = "meta_mutation_choices",
            name = "Echoes of Power",
            description = "+1 extra mutation choice on level-up per level.",
            maxLevel = 3,
            baseCost = 30,
            costGrowthPerLevel = 20,
            category = MetaUpgradeCategory.MUTATIONS
        ),
        MetaUpgradeDefinition(
            id = "meta_loot_quality",
            name = "Astromancer’s Insight",
            description = "Slightly improves loot quality each level.",
            maxLevel = 4,
            baseCost = 25,
            costGrowthPerLevel = 15,
            category = MetaUpgradeCategory.LOOT
        ),
        MetaUpgradeDefinition(
            id = "meta_shard_gain",
            name = "Titan’s Dividend",
            description = "+10% Titan Shard gain per level.",
            maxLevel = 5,
            baseCost = 35,
            costGrowthPerLevel = 20,
            category = MetaUpgradeCategory.ECONOMY
        )
    )

    val byId: Map<String, MetaUpgradeDefinition> = all.associateBy { it.id }
}
