package com.starfall.core.enemy

import com.starfall.core.model.EnemyBehaviorType
import kotlin.random.Random

/** Registry for enemy templates used during procedural generation.
 *  Growth tuning lives in [EnemyScalingConfig] and the scaling utility in [EnemyScaler].
 */
object EnemyTemplates {
    const val GOBLIN_ID = "goblin"

    private val templates = listOf(
        EnemyTemplate(
            id = GOBLIN_ID,
            name = "Goblin",
            glyph = 'g',
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            baseHp = 8,
            baseDamage = 3,
            baseDefense = 1,
            baseXp = 8,
            hpGrowthPerFloor = 0.10,
            dmgGrowthPerFloor = 0.10,
            defGrowthPerFloor = 0.08,
            xpGrowthPerFloor = 0.12
        )
    )

    fun randomEnemyForDepth(depth: Int): EnemyTemplate {
        // Placeholder selection logic; depth-based variety can be added here later.
        return templates[Random.nextInt(templates.size)]
    }
}
