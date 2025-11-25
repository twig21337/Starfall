package com.starfall.core.mutation

import com.starfall.core.model.Player

/**
 * Immutable description of a mutation.
 * The [applyEffect] lambda mutates the player state and can be extended later
 * to hook into additional systems as they come online.
 */
data class Mutation(
    val id: String,
    val name: String,
    val description: String,
    val tier: MutationTier,
    val tags: Set<String> = emptySet(),
    val applyEffect: (Player) -> Unit
)
