package com.starfall.core.run

import com.starfall.core.engine.RunConfig
import com.starfall.core.model.Enemy
import com.starfall.core.progression.MetaProgression
import com.starfall.core.progression.PlayerProfile
import kotlin.random.Random

/**
 * Single owner for active run state. Other systems should query [currentRun] and
 * update counters through the helpers provided here instead of maintaining their own copies.
 * Dungeon generation should lean on [currentDepth], [maxDepth], and [isBossFloor] when
 * constructing floors or selecting boss arenas.
 */
object RunManager {
    private const val MIN_RANDOM_FLOOR = 10
    private const val MAX_RANDOM_FLOOR = 20
    private const val DEFAULT_MAX_FLOOR = RunConfig.MAX_FLOOR
    private const val RUN_ID_PREFIX = "run-"
    private const val BOSS_META_REWARD = 100
    private const val FINAL_BOSS_META_REWARD = 500

    var currentRun: RunState? = null
        private set

    private var activeProfile: PlayerProfile? = null
    /** Optional callback invoked when a floor should be (re)generated. */
    var floorGenerator: ((Int) -> Unit)? = null
    /** Optional callback to reset player state for a new run. */
    var playerInitializer: ((PlayerProfile) -> Unit)? = null

    /**
     * Starts a fresh run for the provided profile. This should be invoked by the main menu or
     * new game UI before handing control to gameplay. The caller can optionally wire
     * [floorGenerator] and [playerInitializer] to connect to existing systems.
     */
    fun startNewRun(profile: PlayerProfile) {
        val now = System.currentTimeMillis()
        val seed = now
        val random = Random(seed)
        val maxFloor = random.nextInt(MIN_RANDOM_FLOOR, MAX_RANDOM_FLOOR + 1)
        val newRun = RunState(
            runId = "$RUN_ID_PREFIX$now",
            seed = seed,
            maxFloor = maxFloor,
            currentFloor = 1,
            floorsCleared = 0,
            enemiesKilled = 0,
            elitesKilled = 0,
            bossesKilled = 0,
            miniBossesKilled = 0,
            metaCurrencyEarned = 0,
            mutationsChosen = mutableListOf(),
            startTimeMillis = now,
            endTimeMillis = null,
            isFinished = false,
            isVictory = false
        )
        activeProfile = profile
        currentRun = newRun
        playerInitializer?.invoke(profile)
        floorGenerator?.invoke(newRun.currentFloor)
    }

    /**
     * Called when the player uses the stairs/portal to leave the current floor.
     * Dungeon generation callbacks should be registered via [floorGenerator].
     */
    fun onFloorCompleted() {
        val run = currentRun ?: return
        if (run.isFinished) return
        run.floorsCleared += 1
        run.currentFloor += 1
        if (run.currentFloor <= run.maxFloor) {
            floorGenerator?.invoke(run.currentFloor)
        } else {
            run.currentFloor = run.maxFloor
            onFinalBossDefeated()
        }
    }

    /** Called by combat/death handling when the player hits 0 HP. */
    fun onPlayerDeath() {
        finalizeRun(victory = false)
    }

    /** Called by boss systems when the final boss on [RunState.maxFloor] is defeated. */
    fun onFinalBossDefeated() {
        finalizeRun(victory = true)
    }

    fun isFinalFloor(): Boolean {
        val run = currentRun
        return run != null && run.currentFloor >= run.maxFloor
    }

    /**
     * Leans on existing boss cadence rules to inform dungeon generation and boss selection.
     */
    fun isBossFloor(floorNumber: Int = currentRun?.currentFloor ?: 1): Boolean {
        val run = currentRun
        val maxFloor = run?.maxFloor ?: DEFAULT_MAX_FLOOR
        return (floorNumber % 5 == 0 && floorNumber < maxFloor) || floorNumber == maxFloor
    }

    /** Convenience for dungeon generation to know which floor to build. */
    fun currentDepth(): Int = currentRun?.currentFloor ?: 1

    /** Max depth for this run; falls back to [RunConfig.MAX_FLOOR] before a run is seeded. */
    fun maxDepth(): Int = currentRun?.maxFloor ?: DEFAULT_MAX_FLOOR

    fun recordEnemyKill(enemy: Enemy) {
        val run = currentRun ?: return
        run.enemiesKilled += 1
        if (enemy.tags.contains("elite")) {
            run.elitesKilled += 1
        }
        if (enemy.bossData != null) {
            run.bossesKilled += 1
            val isFinalBossKill = run.currentFloor >= run.maxFloor
            val reward = if (isFinalBossKill) FINAL_BOSS_META_REWARD else BOSS_META_REWARD
            run.metaCurrencyEarned += reward
        }
        if (enemy.tags.any { it.contains("mini_boss") || it.contains("miniboss") }) {
            run.miniBossesKilled += 1
        }
    }

    fun recordMutationChoice(mutationId: String) {
        val run = currentRun ?: return
        if (!run.mutationsChosen.contains(mutationId)) {
            run.mutationsChosen += mutationId
        }
    }

    fun addMetaCurrency(amount: Int) {
        val run = currentRun ?: return
        if (amount <= 0) return
        run.metaCurrencyEarned += amount
    }

    private fun finalizeRun(victory: Boolean): RunResult? {
        val run = currentRun ?: return null
        if (run.isFinished) return buildRunResult(run)
        run.isFinished = true
        run.isVictory = victory
        if (victory && run.floorsCleared < run.currentFloor) {
            run.floorsCleared = run.currentFloor
        }
        run.endTimeMillis = System.currentTimeMillis()
        val result = buildRunResult(run)
        activeProfile?.let { MetaProgression.applyRunResult(result, it) }
        // TODO: Hook into UI layer to surface a run summary screen.
        return result
    }

    private fun buildRunResult(run: RunState): RunResult {
        val endTime = run.endTimeMillis ?: System.currentTimeMillis()
        return RunResult(
            runId = run.runId,
            isVictory = run.isVictory,
            maxFloor = run.maxFloor,
            floorsCleared = run.floorsCleared,
            enemiesKilled = run.enemiesKilled,
            elitesKilled = run.elitesKilled,
            bossesKilled = run.bossesKilled,
            miniBossesKilled = run.miniBossesKilled,
            metaCurrencyEarned = run.metaCurrencyEarned,
            durationMillis = endTime - run.startTimeMillis
        )
    }
}
