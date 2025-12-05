package com.starfall.core.progression

/** Tracks long-term progression values across runs. */
data class MetaProgressionState(var metaCurrency: Int = 0) {
    fun addMetaCurrency(amount: Int) {
        if (amount <= 0) return
        metaCurrency += amount
    }
}
