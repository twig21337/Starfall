package com.starfall.core.enemy

import kotlin.math.max
import kotlin.math.roundToInt

/** Result of scaling an enemy template for a specific floor and variant. */
data class ScaledEnemyStats(
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val xp: Int
)

/** Utility for scaling enemy templates as floors increase. */
object EnemyScaler {
    fun scaleEnemyForFloor(template: EnemyTemplate, floorNumber: Int, isElite: Boolean = false): ScaledEnemyStats {
        val floorIndex = max(0, floorNumber - 1)
        val growthModifier = EnemyScalingConfig.floorGrowthModifier(floorNumber)

        val hpMultiplier = (1.0 + template.hpGrowthPerFloor * floorIndex * growthModifier) *
            EnemyScalingConfig.GLOBAL_STAT_MULTIPLIER
        val dmgMultiplier = (1.0 + template.dmgGrowthPerFloor * floorIndex * growthModifier) *
            EnemyScalingConfig.GLOBAL_STAT_MULTIPLIER
        val defMultiplier = (1.0 + template.defGrowthPerFloor * floorIndex * growthModifier) *
            EnemyScalingConfig.GLOBAL_STAT_MULTIPLIER
        val xpMultiplier = (1.0 + template.xpGrowthPerFloor * floorIndex * growthModifier) *
            EnemyScalingConfig.GLOBAL_XP_MULTIPLIER

        var scaledHp = (template.baseHp * hpMultiplier).roundToInt()
        var scaledDamage = (template.baseDamage * dmgMultiplier).roundToInt()
        var scaledDefense = (template.baseDefense * defMultiplier).roundToInt()
        var scaledXp = max(1, (template.baseXp * xpMultiplier).roundToInt())

        if (isElite) {
            scaledHp = (scaledHp * EnemyScalingConfig.ELITE_HP_MULTIPLIER).roundToInt()
            scaledDamage = (scaledDamage * EnemyScalingConfig.ELITE_DAMAGE_MULTIPLIER).roundToInt()
            scaledDefense = (scaledDefense * EnemyScalingConfig.ELITE_DEFENSE_MULTIPLIER).roundToInt()
            scaledXp = max(1, (scaledXp * EnemyScalingConfig.ELITE_XP_MULTIPLIER).roundToInt())
        }

        return ScaledEnemyStats(
            hp = max(1, scaledHp),
            attack = max(1, scaledDamage),
            defense = max(0, scaledDefense),
            xp = scaledXp
        )
    }
}
