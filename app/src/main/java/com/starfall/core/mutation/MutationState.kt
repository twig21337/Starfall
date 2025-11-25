package com.starfall.core.mutation

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Tracks the player's acquired mutations and lightweight runtime bonuses.
 * It is intentionally data-only so it can be saved/restored separately from
 * UI or rendering state.
 */
data class MutationState(
    val acquiredMutationIds: MutableSet<String> = mutableSetOf(),
    var bonusInventorySlots: Int = 0,
    var dodgeBonus: Double = 0.0,
    var phaseDodgeChance: Double = 0.0,
    var damageReduction: Double = 0.0,
    var resurrectionCharges: Int = 0,
    var extraActionChance: Double = 0.0,
    var poisonChanceOnHit: Double = 0.0,
    var energyRegenBonus: Double = 0.0,
    var hasBioplasmaGland: Boolean = false,
    var timeRewindCharges: Int = 0
) {
    /** Returns true when a roll indicates the attack should miss due to phasing. */
    fun shouldPhaseDodge(random: Random = Random): Boolean =
        random.nextDouble() < phaseDodgeChance

    /** Applies fractional damage reduction, clamping to at least 0 damage. */
    fun reduceDamage(amount: Int): Int {
        if (amount <= 0) return 0
        val multiplier = max(0.0, 1 - damageReduction)
        return max(0, (amount * multiplier).roundToInt())
    }

    /** Consumes a stored resurrection and returns true if one was available. */
    fun tryConsumeResurrection(): Boolean {
        if (resurrectionCharges <= 0) return false
        resurrectionCharges -= 1
        return true
    }
}
