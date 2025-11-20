package com.starfall.core.model

import kotlin.math.max

/** Basic combat-related statistics. */
data class Stats(
    var maxHp: Int,
    var hp: Int,
    var attack: Int,
    var defense: Int
) {
    /** Returns true if the entity has no remaining HP. */
    fun isDead(): Boolean = hp <= 0

    /** Applies incoming damage, returning the actual damage taken. */
    fun takeDamage(amount: Int): Int {
        if (amount <= 0 || isDead()) return 0
        val damageTaken = max(0, amount.coerceAtMost(hp))
        hp -= damageTaken
        return damageTaken
    }

    /** Restores hit points without exceeding the maximum value. */
    fun heal(amount: Int): Int {
        if (amount <= 0 || isDead()) return 0
        val missing = maxHp - hp
        val healed = amount.coerceAtMost(missing)
        hp += healed
        return healed
    }
}
