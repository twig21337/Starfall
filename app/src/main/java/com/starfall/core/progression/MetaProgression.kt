package com.starfall.core.progression

import com.starfall.core.run.RunResult
import kotlin.math.roundToInt

/**
 * Hooks long-term progression updates to the end of a run.
 * The implementation is intentionally simple to avoid stepping on existing systems, but it now
 * includes Titan Shard calculation, upgrade purchasing, and effective bonus aggregation.
 */
object MetaProgression {

    private const val BASE_PER_FLOOR = 2
    private const val BOSS_BONUS = 5
    private const val MINI_BOSS_BONUS = 3
    private const val ELITE_KILL_BONUS = 1
    private const val VICTORY_BONUS_MULTIPLIER = 1.5

    /**
     * Converts a [RunResult] into Titan Shards using a simple kill/floor formula and applies
     * shard gain multipliers from owned upgrades. This value feeds the run summary UI and the
     * [MetaProfile.totalTitanShards] tally.
     */
    fun computeShardsForRun(result: RunResult, profile: MetaProfile): Int {
        val bonuses = computeEffectiveBonuses(profile)
        var shards = result.metaCurrencyEarned
        shards += (result.floorsCleared * BASE_PER_FLOOR) +
            (result.bossesKilled * BOSS_BONUS) +
            (result.miniBossesKilled * MINI_BOSS_BONUS) +
            (result.elitesKilled * ELITE_KILL_BONUS)

        if (result.isVictory) {
            shards = (shards * VICTORY_BONUS_MULTIPLIER).roundToInt()
        }

        return (shards * bonuses.shardGainMultiplier).roundToInt().coerceAtLeast(0)
    }

    fun applyRunResult(result: RunResult, profile: MetaProfile) {
        val shardsEarned = computeShardsForRun(result, profile)
        profile.totalTitanShards += shardsEarned
        profile.lifetimeRuns += 1
        if (result.isVictory) {
            profile.lifetimeVictories += 1
        }
        profile.lifetimeFloorsCleared += result.floorsCleared
        profile.lifetimeEnemiesKilled += result.enemiesKilled + result.elitesKilled + result.miniBossesKilled
        profile.lifetimeBossesKilled += result.bossesKilled
    }

    fun applyRunResult(result: RunResult, profile: PlayerProfile) {
        profile.metaProgressionState.addMetaCurrency(result.metaCurrencyEarned)
    }

    fun canPurchaseUpgrade(profile: MetaProfile, upgradeId: String): Boolean {
        val upgrade = MetaUpgrades.byId[upgradeId] ?: return false
        val currentLevel = getUpgradeLevel(profile, upgradeId)
        if (currentLevel >= upgrade.maxLevel) return false
        val cost = costForUpgrade(upgrade, currentLevel)
        return profile.availableTitanShards >= cost
    }

    fun purchaseUpgrade(profile: MetaProfile, upgradeId: String): Boolean {
        val upgrade = MetaUpgrades.byId[upgradeId] ?: return false
        val currentLevel = getUpgradeLevel(profile, upgradeId)
        if (currentLevel >= upgrade.maxLevel) return false
        val cost = costForUpgrade(upgrade, currentLevel)
        if (profile.availableTitanShards < cost) return false

        profile.ownedUpgrades[upgradeId] = currentLevel + 1
        profile.spentTitanShards += cost
        return true
    }

    fun listAvailableUpgrades(profile: MetaProfile): List<MetaUpgradeDefinition> = MetaUpgrades.all

    fun getUpgradeLevel(profile: MetaProfile, upgradeId: String): Int = profile.ownedUpgrades[upgradeId] ?: 0

    /**
     * Aggregates all purchased upgrades into concrete knobs that other systems can read at run
     * start (e.g., GameEngine grants HP and potions, mutation system increases options). Loot
     * and drop systems can also consume the loot quality bonus when tuned.
     */
    fun computeEffectiveBonuses(profile: MetaProfile): EffectiveMetaBonuses {
        var extraMaxHp = 0
        var extraStartingPotions = 0
        var mutationChoicesBonus = 0
        var shardGainMultiplier = 1.0
        var lootQualityBonus = 0.0

        profile.ownedUpgrades.forEach { (id, level) ->
            when (id) {
                "meta_max_hp" -> extraMaxHp += level * 5
                "meta_starting_potion" -> extraStartingPotions += level
                "meta_mutation_choices" -> mutationChoicesBonus += level
                "meta_shard_gain" -> shardGainMultiplier *= (1.0 + 0.10 * level)
                "meta_loot_quality" -> lootQualityBonus += level * 0.02
            }
        }

        return EffectiveMetaBonuses(
            extraMaxHp = extraMaxHp,
            extraStartingPotions = extraStartingPotions,
            mutationChoicesBonus = mutationChoicesBonus,
            shardGainMultiplier = shardGainMultiplier,
            lootQualityBonus = lootQualityBonus
        )
    }

    private fun costForUpgrade(upgrade: MetaUpgradeDefinition, currentLevel: Int): Int =
        upgrade.baseCost + (upgrade.costGrowthPerLevel * currentLevel)
}
