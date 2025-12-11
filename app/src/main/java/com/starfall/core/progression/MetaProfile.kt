package com.starfall.core.progression

import com.starfall.core.save.MetaProfileSave

/**
 * Represents the long-term meta progression for the player. Titan Shards earned between
 * runs can be reinvested into [ownedUpgrades] to boost future attempts. Lifetime counters
 * help surfaces stats in meta UI screens.
 */
data class MetaProfile(
    var totalTitanShards: Int,
    var spentTitanShards: Int,
    val ownedUpgrades: MutableMap<String, Int>,
    var lifetimeRuns: Int,
    var lifetimeVictories: Int,
    var lifetimeFloorsCleared: Int,
    var lifetimeEnemiesKilled: Int,
    var lifetimeBossesKilled: Int
) {
    val availableTitanShards: Int
        get() = totalTitanShards - spentTitanShards
}

fun MetaProfile.toSave(): MetaProfileSave = MetaProfileSave(
    totalTitanShards = totalTitanShards,
    spentTitanShards = spentTitanShards,
    ownedUpgrades = ownedUpgrades.toMap(),
    lifetimeRuns = lifetimeRuns,
    lifetimeVictories = lifetimeVictories,
    lifetimeFloorsCleared = lifetimeFloorsCleared,
    lifetimeEnemiesKilled = lifetimeEnemiesKilled,
    lifetimeBossesKilled = lifetimeBossesKilled,
    lifetimeKills = lifetimeEnemiesKilled + lifetimeBossesKilled,
    lastRunId = null,
    unlockedMutations = emptyList()
)

fun MetaProfileSave.toMetaProfile(): MetaProfile = MetaProfile(
    totalTitanShards = totalTitanShards,
    spentTitanShards = spentTitanShards,
    ownedUpgrades = ownedUpgrades.toMutableMap(),
    lifetimeRuns = lifetimeRuns,
    lifetimeVictories = lifetimeVictories,
    lifetimeFloorsCleared = lifetimeFloorsCleared,
    lifetimeEnemiesKilled = lifetimeEnemiesKilled,
    lifetimeBossesKilled = lifetimeBossesKilled
)
