package com.starfall.core.progression

/** Aggregates per-run bonuses derived from purchased meta upgrades. */
data class EffectiveMetaBonuses(
    val extraMaxHp: Int = 0,
    val extraStartingPotions: Int = 0,
    val mutationChoicesBonus: Int = 0,
    val shardGainMultiplier: Double = 1.0,
    val lootQualityBonus: Double = 0.0
)
