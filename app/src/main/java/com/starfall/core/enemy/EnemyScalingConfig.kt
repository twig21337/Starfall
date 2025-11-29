package com.starfall.core.enemy

/**
 * Centralized knobs for tuning enemy stat and XP growth.
 * Adjust these values to quickly rebalance non-boss encounters.
 */
object EnemyScalingConfig {
    // Baseline per-floor growth when templates do not override values.
    const val DEFAULT_HP_GROWTH_PER_FLOOR = 0.12
    const val DEFAULT_DAMAGE_GROWTH_PER_FLOOR = 0.10
    const val DEFAULT_DEFENSE_GROWTH_PER_FLOOR = 0.08
    const val DEFAULT_XP_GROWTH_PER_FLOOR = 0.12

    // Global multipliers to nudge overall difficulty without editing every enemy.
    const val GLOBAL_STAT_MULTIPLIER = 1.0
    const val GLOBAL_XP_MULTIPLIER = 1.0

    // Elite multipliers to make special variants stand out.
    const val ELITE_HP_MULTIPLIER = 1.6
    const val ELITE_DAMAGE_MULTIPLIER = 1.35
    const val ELITE_DEFENSE_MULTIPLIER = 1.25
    const val ELITE_XP_MULTIPLIER = 1.75

    /**
     * Hook for future tiered growth adjustments (early/mid/late floors, etc.).
     * For now this simply returns 1.0 to keep growth linear.
     */
    fun floorGrowthModifier(floorNumber: Int): Double = 1.0
}
