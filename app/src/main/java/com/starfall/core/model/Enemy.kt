package com.starfall.core.model

import com.starfall.core.boss.BossManager.BossInstance

/** Available high-level enemy behavior archetypes. */
enum class EnemyBehaviorType {
    SIMPLE_CHASER,
    PASSIVE,
    FLEEING,
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
    val bossData: BossInstance? = null
) : Entity(id, name, position, glyph, true, stats)
