package com.starfall.core.progression

import com.starfall.core.model.Player
import com.starfall.core.mutation.Mutation
import com.starfall.core.mutation.MutationManager

/**
 * Lightweight helpers for driving XP and mutation flows from tests or debug tools.
 */
object ProgressionDebug {
    fun grantXpAndPreviewMutations(
        player: Player,
        xpManager: XpManager,
        mutationManager: MutationManager,
        amount: Int
    ): List<String> {
        val log = mutableListOf<String>()
        val beforeLevel = player.level
        xpManager.gainXp(amount)
        if (player.level == beforeLevel) {
            log += "Gained $amount XP. ${player.experience}/${xpManager.getRequiredXpForNextLevel()} toward next level."
            return log
        }

        log += "Leveled up to ${player.level}!"
        log += describeChoices(mutationManager.getPendingChoices())
        return log
    }

    private fun describeChoices(options: List<Mutation>): List<String> {
        if (options.isEmpty()) return listOf("No mutation choices generated.")
        val lines = mutableListOf("Choose one of the following mutations:")
        options.forEachIndexed { index, mutation ->
            lines += "${index + 1}. ${mutation.name} [${mutation.tier}] - ${mutation.description}"
        }
        return lines
    }
}
