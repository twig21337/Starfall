package com.starfall.core.boss

/** Tunable knobs for boss floor pacing, scaling, and rewards. */
object BossConfig {
    const val BOSS_FLOOR_INTERVAL = 5

    const val HP_SCALE_PER_TIER = 0.35
    const val DAMAGE_SCALE_PER_TIER = 0.20
    const val DEFENSE_SCALE_PER_TIER = 0.15

    const val BASE_BOSS_XP = 35
    const val XP_PER_TIER = 10

    const val GLOBAL_LOOT_ROLL_INTERVAL = 2
    const val BASE_UNIQUE_ROLL_CHANCE = 0.35
    const val UNIQUE_ROLL_PER_TIER = 0.1
    const val EQUIPMENT_ROLL_INTERVAL = 2
}
