package com.starfall.core.mutation

import com.starfall.core.model.Player
import kotlin.random.Random

/**
 * Coordinates mutation rolls on level-up and applies selected mutations.
 * This class is UI-agnostic: consumers can read [pendingChoices] and present
 * them however they want before calling [applyChosenMutation].
 */
class MutationManager(
    private val mutationPool: Map<MutationTier, List<Mutation>> = MutationCatalog.byTier,
    private val random: Random = Random,
    private val optionsRange: IntRange = 2..2
) {

    private var pendingChoices: List<Mutation> = emptyList()

    fun onPlayerLevelUp(player: Player) {
        val count = random.nextInt(optionsRange.first, optionsRange.last + 1)
        pendingChoices = rollChoicesForPlayer(player, count)
    }

    fun getPendingChoices(): List<Mutation> = pendingChoices

    fun applyChosenMutation(player: Player, mutationId: String): Boolean {
        val mutation = pendingChoices.firstOrNull { it.id == mutationId } ?: return false
        mutation.applyEffect(player)
        player.mutationState.acquiredMutationIds += mutation.id
        pendingChoices = emptyList()
        return true
    }

    fun rollMutationTierForLevel(level: Int): MutationTier {
        val weights = tierWeightsForLevel(level)
        val totalWeight = weights.values.sum()
        val roll = random.nextDouble(0.0, totalWeight)
        var cursor = 0.0
        for ((tier, weight) in weights) {
            cursor += weight
            if (roll <= cursor) return tier
        }
        return MutationTier.TIER_1
    }

    private fun rollChoicesForPlayer(player: Player, desiredCount: Int): List<Mutation> {
        if (desiredCount <= 0) return emptyList()
        val chosen = mutableListOf<Mutation>()
        var attempts = 0
        while (chosen.size < desiredCount && attempts < desiredCount * 10) {
            attempts += 1
            val tier = rollMutationTierForLevel(player.level)
            val mutation = pickMutationForTier(tier, chosen.map { it.id }.toSet(), player)
            if (mutation != null) {
                chosen += mutation
            }
        }
        return chosen
    }

    private fun pickMutationForTier(tier: MutationTier, used: Set<String>, player: Player): Mutation? {
        val pool = mutationPool[tier].orEmpty()
        if (pool.isEmpty()) return null
        val fresh = pool.filterNot { used.contains(it.id) || player.mutationState.acquiredMutationIds.contains(it.id) }
        val candidatePool = if (fresh.isNotEmpty()) fresh else pool.filterNot { used.contains(it.id) }
        if (candidatePool.isEmpty()) return null
        val index = random.nextInt(candidatePool.size)
        return candidatePool[index]
    }

    private fun tierWeightsForLevel(level: Int): Map<MutationTier, Double> = when (level) {
        in 1..3 -> mapOf(
            MutationTier.TIER_1 to 0.90,
            MutationTier.TIER_2 to 0.10,
            MutationTier.TIER_3 to 0.02,
            MutationTier.TIER_4 to 0.001,
            MutationTier.TIER_5 to 0.0
        )

        in 4..7 -> mapOf(
            MutationTier.TIER_1 to 0.70,
            MutationTier.TIER_2 to 0.25,
            MutationTier.TIER_3 to 0.07,
            MutationTier.TIER_4 to 0.005,
            MutationTier.TIER_5 to 0.001
        )

        in 8..12 -> mapOf(
            MutationTier.TIER_1 to 0.50,
            MutationTier.TIER_2 to 0.35,
            MutationTier.TIER_3 to 0.12,
            MutationTier.TIER_4 to 0.02,
            MutationTier.TIER_5 to 0.002
        )

        in 13..17 -> mapOf(
            MutationTier.TIER_1 to 0.30,
            MutationTier.TIER_2 to 0.40,
            MutationTier.TIER_3 to 0.20,
            MutationTier.TIER_4 to 0.05,
            MutationTier.TIER_5 to 0.01
        )

        else -> mapOf(
            MutationTier.TIER_1 to 0.15,
            MutationTier.TIER_2 to 0.30,
            MutationTier.TIER_3 to 0.25,
            MutationTier.TIER_4 to 0.10,
            MutationTier.TIER_5 to 0.03
        )
    }
}
