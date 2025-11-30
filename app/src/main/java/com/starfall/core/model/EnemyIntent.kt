package com.starfall.core.model

/** Describes a telegraphed enemy action that the UI can surface to the player. */
data class EnemyIntent(
    val type: EnemyIntentType,
    val targetTiles: List<Position> = emptyList(),
    val turnsUntilResolve: Int = 1
)

enum class EnemyIntentType {
    LUNGE,
    BLOCK,
    SMASH,
    WARP_SHOT,
    TOXIC_GLOB,
    SHARD_THROW,
    EXPLODE,
    WEAKEN_HEX,
    GLYPH_TRAP,
    FROST_ORB,
    SUMMON,
    BUFF,
    CHARGE
}
