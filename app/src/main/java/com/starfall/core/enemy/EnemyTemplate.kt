package com.starfall.core.enemy

import com.starfall.core.model.EnemyBehaviorType

/** Template describing a family of enemies, including their growth behavior. */
data class EnemyTemplate(
    val id: String,
    val name: String,
    val glyph: Char,
    val behaviorType: EnemyBehaviorType,
    val sightRange: Int,
    val tags: Set<String> = emptySet(),
    val baseHp: Int,
    val baseDamage: Int,
    val baseDefense: Int,
    val baseXp: Int,
    val hpGrowthPerFloor: Double = EnemyScalingConfig.DEFAULT_HP_GROWTH_PER_FLOOR,
    val dmgGrowthPerFloor: Double = EnemyScalingConfig.DEFAULT_DAMAGE_GROWTH_PER_FLOOR,
    val defGrowthPerFloor: Double = EnemyScalingConfig.DEFAULT_DEFENSE_GROWTH_PER_FLOOR,
    val xpGrowthPerFloor: Double = EnemyScalingConfig.DEFAULT_XP_GROWTH_PER_FLOOR
)
