package com.starfall.core.run

/**
 * Persistent state for a single active run. The owner is [RunManager.currentRun].
 * Extend this class as new run-level metrics come online (e.g., damage taken).
 *
 * Both [RunState] and [RunResult] live here to keep a clear source of truth for
 * run lifecycle data that other systems (UI, meta progression, bosses) can query.
 */
data class RunState(
    val runId: String,
    val regionId: String,
    val seed: Long,
    val maxFloor: Int,
    var currentFloor: Int,
    var floorsCleared: Int,
    var enemiesKilled: Int,
    var elitesKilled: Int,
    var bossesKilled: Int,
    var miniBossesKilled: Int,
    var metaCurrencyEarned: Int,
    val mutationsChosen: MutableList<String>,
    val startTimeMillis: Long,
    var endTimeMillis: Long?,
    var isFinished: Boolean,
    var isVictory: Boolean
)

/**
 * Lightweight summary of a completed run used by UI and meta-progression layers.
 */
data class RunResult(
    val runId: String,
    val isVictory: Boolean,
    val maxFloor: Int,
    val floorsCleared: Int,
    val enemiesKilled: Int,
    val elitesKilled: Int,
    val bossesKilled: Int,
    val miniBossesKilled: Int,
    val metaCurrencyEarned: Int,
    val durationMillis: Long
)
