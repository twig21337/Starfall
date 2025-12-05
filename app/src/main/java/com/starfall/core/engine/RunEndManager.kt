package com.starfall.core.engine

import com.starfall.core.model.Enemy
import com.starfall.core.progression.MetaProgressionState
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Notes for tuning and integration:
 * - RunConfig stores dungeon length (MAX_FLOOR), the final boss ID, and meta-currency reward knobs.
 * - Boss floor selection differentiates final vs. rotating bosses in SimpleDungeonGenerator and BossManager.
 * - RunEndManager produces RunResult summaries; UI layers can hook into GameEvent.RunEnded to surface them.
 */

/** Captures per-run stats and computes the end-of-run result. */
class RunEndManager(private val metaProgressionState: MetaProgressionState) {
    val runStats: RunStats = RunStats()

    private var finalizedResult: RunResult? = null

    fun recordFloorReached(floor: Int) {
        runStats.floorsCleared = max(runStats.floorsCleared, floor)
    }

    fun recordEnemyKill(enemy: Enemy) {
        runStats.enemiesKilled += 1
        if (enemy.tags.contains("elite")) {
            runStats.elitesKilled += 1
        }
        if (enemy.bossData != null) {
            runStats.bossesKilled += 1
        }
    }

    fun recordMutationChoice() {
        runStats.mutationsChosen += 1
    }

    fun hasEnded(): Boolean = finalizedResult != null

    fun endRun(victory: Boolean, cause: RunEndCause): RunResult {
        finalizedResult?.let { return it }
        val timeInRunMs = System.currentTimeMillis() - runStats.startTimeMs
        val baseCurrency =
            (RunConfig.META_CURRENCY_PER_FLOOR * runStats.floorsCleared) +
                (RunConfig.META_CURRENCY_PER_BOSS * runStats.bossesKilled) +
                (RunConfig.META_CURRENCY_PER_ELITE * runStats.elitesKilled)

        val adjustedCurrency = if (victory) {
            (baseCurrency * RunConfig.VICTORY_META_MULTIPLIER).roundToInt()
        } else {
            baseCurrency
        }

        metaProgressionState.addMetaCurrency(adjustedCurrency)

        val result = RunResult(
            isVictory = victory,
            floorsCleared = runStats.floorsCleared,
            bossesKilled = runStats.bossesKilled,
            enemiesKilled = runStats.enemiesKilled,
            elitesKilled = runStats.elitesKilled,
            mutationsChosen = runStats.mutationsChosen,
            metaCurrencyEarned = adjustedCurrency,
            metaCurrencyTotal = metaProgressionState.metaCurrency,
            cause = cause,
            timeInRunMs = timeInRunMs
        )
        finalizedResult = result
        return result
    }
}

data class RunStats(
    var floorsCleared: Int = 0,
    var enemiesKilled: Int = 0,
    var elitesKilled: Int = 0,
    var bossesKilled: Int = 0,
    var mutationsChosen: Int = 0,
    val startTimeMs: Long = System.currentTimeMillis()
)

data class RunResult(
    val isVictory: Boolean,
    val floorsCleared: Int,
    val bossesKilled: Int,
    val enemiesKilled: Int,
    val elitesKilled: Int,
    val mutationsChosen: Int,
    val metaCurrencyEarned: Int,
    val metaCurrencyTotal: Int,
    val cause: RunEndCause,
    val timeInRunMs: Long
)

enum class RunEndCause {
    PLAYER_DEAD,
    FINAL_BOSS_DEFEATED,
    ABANDONED
}
