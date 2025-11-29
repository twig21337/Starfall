package com.starfall.core.enemy

import com.starfall.core.model.EnemyBehaviorType
import kotlin.random.Random

/** Registry for enemy templates used during procedural generation.
 *  Growth tuning lives in [EnemyScalingConfig] and the scaling utility in [EnemyScaler].
 */
object EnemyTemplates {
    const val GOBLIN_ID = "goblin"
    const val HOLLOW_STALKER_ID = "hollow_stalker"
    const val BONE_REAVER_ID = "bone_reaver"
    const val ABYSSAL_MAULER_ID = "abyssal_mauler"
    const val RIFT_ARCHER_ID = "rift_archer"
    const val BLIGHT_SPITTER_ID = "blight_spitter"
    const val CRYSTAL_JAVELINER_ID = "crystal_javeliner"
    const val EMBER_WISP_ID = "ember_wisp"
    const val HEXBOUND_ACOLYTE_ID = "hexbound_acolyte"
    const val FROSTBOUND_CULTIST_ID = "frostbound_cultist"
    const val BROOD_HOST_ID = "brood_host"
    const val WARPED_SHAMAN_ID = "warped_shaman"
    const val VOID_CARAPACE_ID = "void_carapace"

    private val earlyPool = listOf(
        EnemyTemplate(
            id = GOBLIN_ID,
            name = "Goblin",
            glyph = 'g',
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            sightRange = 6,
            baseHp = 8,
            baseDamage = 3,
            baseDefense = 1,
            baseXp = 8,
            hpGrowthPerFloor = 0.10,
            dmgGrowthPerFloor = 0.10,
            defGrowthPerFloor = 0.08,
            xpGrowthPerFloor = 0.12
        ),
        EnemyTemplate(
            id = HOLLOW_STALKER_ID,
            name = "Hollow Stalker",
            glyph = 's',
            behaviorType = EnemyBehaviorType.HOLLOW_STALKER,
            sightRange = 6,
            tags = setOf("melee", "ambusher", "fast"),
            baseHp = 10,
            baseDamage = 5,
            baseDefense = 1,
            baseXp = 12,
            hpGrowthPerFloor = 0.12,
            dmgGrowthPerFloor = 0.12,
            defGrowthPerFloor = 0.08,
            xpGrowthPerFloor = 0.14
        ),
        EnemyTemplate(
            id = RIFT_ARCHER_ID,
            name = "Rift Archer",
            glyph = 'a',
            behaviorType = EnemyBehaviorType.RIFT_ARCHER,
            sightRange = 10,
            tags = setOf("ranged", "kiter", "void"),
            baseHp = 10,
            baseDamage = 5,
            baseDefense = 1,
            baseXp = 14,
            hpGrowthPerFloor = 0.1,
            dmgGrowthPerFloor = 0.12,
            defGrowthPerFloor = 0.07,
            xpGrowthPerFloor = 0.14
        ),
        EnemyTemplate(
            id = BLIGHT_SPITTER_ID,
            name = "Blight Spitter",
            glyph = 'b',
            behaviorType = EnemyBehaviorType.BLIGHT_SPITTER,
            sightRange = 7,
            tags = setOf("ranged", "poison", "area_denial"),
            baseHp = 16,
            baseDamage = 3,
            baseDefense = 1,
            baseXp = 15,
            hpGrowthPerFloor = 0.12,
            dmgGrowthPerFloor = 0.1,
            defGrowthPerFloor = 0.08,
            xpGrowthPerFloor = 0.14
        ),
        EnemyTemplate(
            id = EMBER_WISP_ID,
            name = "Ember Wisp",
            glyph = 'w',
            behaviorType = EnemyBehaviorType.EMBER_WISP,
            sightRange = 6,
            tags = setOf("caster", "explosive", "fire"),
            baseHp = 6,
            baseDamage = 2,
            baseDefense = 0,
            baseXp = 10,
            hpGrowthPerFloor = 0.08,
            dmgGrowthPerFloor = 0.1,
            defGrowthPerFloor = 0.05,
            xpGrowthPerFloor = 0.12
        )
    )

    private val midPool = listOf(
        EnemyTemplate(
            id = BONE_REAVER_ID,
            name = "Bone Reaver",
            glyph = 'R',
            behaviorType = EnemyBehaviorType.BONE_REAVER,
            sightRange = 4,
            tags = setOf("melee", "bruiser", "defensive"),
            baseHp = 18,
            baseDamage = 6,
            baseDefense = 3,
            baseXp = 18,
            hpGrowthPerFloor = 0.13,
            dmgGrowthPerFloor = 0.12,
            defGrowthPerFloor = 0.1,
            xpGrowthPerFloor = 0.15
        ),
        EnemyTemplate(
            id = CRYSTAL_JAVELINER_ID,
            name = "Crystal Javeliner",
            glyph = 'J',
            behaviorType = EnemyBehaviorType.CRYSTAL_JAVELINER,
            sightRange = 12,
            tags = setOf("ranged", "piercing", "line_attack"),
            baseHp = 18,
            baseDamage = 7,
            baseDefense = 2,
            baseXp = 18,
            hpGrowthPerFloor = 0.12,
            dmgGrowthPerFloor = 0.13,
            defGrowthPerFloor = 0.09,
            xpGrowthPerFloor = 0.15
        ),
        EnemyTemplate(
            id = HEXBOUND_ACOLYTE_ID,
            name = "Hexbound Acolyte",
            glyph = 'h',
            behaviorType = EnemyBehaviorType.HEXBOUND_ACOLYTE,
            sightRange = 8,
            tags = setOf("caster", "debuff", "hex"),
            baseHp = 14,
            baseDamage = 3,
            baseDefense = 1,
            baseXp = 16,
            hpGrowthPerFloor = 0.12,
            dmgGrowthPerFloor = 0.1,
            defGrowthPerFloor = 0.08,
            xpGrowthPerFloor = 0.15
        ),
        EnemyTemplate(
            id = FROSTBOUND_CULTIST_ID,
            name = "Frostbound Cultist",
            glyph = 'f',
            behaviorType = EnemyBehaviorType.FROSTBOUND_CULTIST,
            sightRange = 7,
            tags = setOf("caster", "frost", "control"),
            baseHp = 16,
            baseDamage = 4,
            baseDefense = 2,
            baseXp = 17,
            hpGrowthPerFloor = 0.12,
            dmgGrowthPerFloor = 0.11,
            defGrowthPerFloor = 0.09,
            xpGrowthPerFloor = 0.15
        )
    )

    private val latePool = listOf(
        EnemyTemplate(
            id = ABYSSAL_MAULER_ID,
            name = "Abyssal Mauler",
            glyph = 'm',
            behaviorType = EnemyBehaviorType.ABYSSAL_MAULER,
            sightRange = 7,
            tags = setOf("melee", "aoe", "brute"),
            baseHp = 26,
            baseDamage = 8,
            baseDefense = 3,
            baseXp = 24,
            hpGrowthPerFloor = 0.15,
            dmgGrowthPerFloor = 0.14,
            defGrowthPerFloor = 0.1,
            xpGrowthPerFloor = 0.17
        ),
        EnemyTemplate(
            id = BROOD_HOST_ID,
            name = "Brood Host",
            glyph = 'B',
            behaviorType = EnemyBehaviorType.BROOD_HOST,
            sightRange = 4,
            tags = setOf("summoner", "support", "brood"),
            baseHp = 18,
            baseDamage = 2,
            baseDefense = 3,
            baseXp = 20,
            hpGrowthPerFloor = 0.13,
            dmgGrowthPerFloor = 0.09,
            defGrowthPerFloor = 0.11,
            xpGrowthPerFloor = 0.16
        ),
        EnemyTemplate(
            id = WARPED_SHAMAN_ID,
            name = "Warped Shaman",
            glyph = 'S',
            behaviorType = EnemyBehaviorType.WARPED_SHAMAN,
            sightRange = 8,
            tags = setOf("support", "healer", "buffer"),
            baseHp = 18,
            baseDamage = 3,
            baseDefense = 2,
            baseXp = 19,
            hpGrowthPerFloor = 0.13,
            dmgGrowthPerFloor = 0.1,
            defGrowthPerFloor = 0.09,
            xpGrowthPerFloor = 0.16
        ),
        EnemyTemplate(
            id = VOID_CARAPACE_ID,
            name = "Void Carapace",
            glyph = 'C',
            behaviorType = EnemyBehaviorType.VOID_CARAPACE,
            sightRange = 5,
            tags = setOf("tank", "charge", "directional_armor"),
            baseHp = 28,
            baseDamage = 6,
            baseDefense = 5,
            baseXp = 26,
            hpGrowthPerFloor = 0.16,
            dmgGrowthPerFloor = 0.12,
            defGrowthPerFloor = 0.12,
            xpGrowthPerFloor = 0.18
        )
    )

    fun randomEnemyForDepth(depth: Int): EnemyTemplate {
        val pool = when {
            depth <= 4 -> earlyPool
            depth <= 8 -> earlyPool + midPool
            else -> earlyPool + midPool + latePool
        }
        return pool[Random.nextInt(pool.size)]
    }
}
