package com.starfall.core.progression

import com.starfall.core.run.RunResult

/**
 * Hooks long-term progression updates to the end of a run.
 * The implementation is intentionally simple to avoid stepping on existing systems.
 */
object MetaProgression {
    fun applyRunResult(result: RunResult, profile: PlayerProfile) {
        profile.metaProgressionState.addMetaCurrency(result.metaCurrencyEarned)
        // Future extensions: achievements, unlocks, analytics, etc.
    }
}
