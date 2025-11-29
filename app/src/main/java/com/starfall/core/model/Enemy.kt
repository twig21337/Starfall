package com.starfall.core.model

import com.starfall.core.boss.BossManager.BossInstance
import com.starfall.core.model.EnemyIntent

/** Available high-level enemy behavior archetypes. */
enum class EnemyBehaviorType {
    SIMPLE_CHASER,
    PASSIVE,
    FLEEING,
    HOLLOW_STALKER,
    BONE_REAVER,
    ABYSSAL_MAULER,
    RIFT_ARCHER,
    BLIGHT_SPITTER,
    CRYSTAL_JAVELINER,
    EMBER_WISP,
    HEXBOUND_ACOLYTE,
    FROSTBOUND_CULTIST,
    BROOD_HOST,
    WARPED_SHAMAN,
    VOID_CARAPACE,
    BOSS_FALLEN_ASTROMANCER,
    BOSS_BONE_FORGED_COLOSSUS,
    BOSS_BLIGHTED_HIVE_MIND,
    BOSS_ECHO_KNIGHT_REMNANT,
    BOSS_HEARTSTEALER_WYRM
}

/** Data class describing an enemy actor. */
class Enemy(
    id: Int,
    name: String,
    position: Position,
    glyph: Char,
    stats: Stats,
    val behaviorType: EnemyBehaviorType,
    val bossData: BossInstance? = null,
    val xpReward: Int? = null,
    val templateId: String? = null,
    val sightRange: Int = 6,
    val tags: Set<String> = emptySet(),
    var intent: EnemyIntent? = null
) : Entity(id, name, position, glyph, true, stats)
