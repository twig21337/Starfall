package com.starfall.core.progression

import com.starfall.core.model.Player
import com.starfall.core.mutation.MutationManager
import com.starfall.core.mutation.Mutation

/**
 * Handles player XP and level progression. The XP curve follows:
 *   XP_to_Level(n) = 30 * n^2 + 90 * n
 * where the value represents the total XP required to reach level n from level 1.
 */
class XpManager(
    private val player: Player,
    private val mutationManager: MutationManager? = null,
    private val levelUpHandler: LevelUpHandler = DefaultLevelUpHandler()
) {
    fun gainXp(amount: Int): List<LevelUpResult> {
        if (amount <= 0) return emptyList()
        val levelUps = mutableListOf<LevelUpResult>()
        player.experience += amount
        var required = xpNeededForNextLevel()
        while (player.experience >= required) {
            player.experience -= required
            player.level += 1
            levelUpHandler.onLevelUp(player)
            mutationManager?.onPlayerLevelUp(player)
            levelUps += LevelUpResult(player.level, mutationManager?.getPendingChoices().orEmpty())
            required = xpNeededForNextLevel()
        }
        return levelUps
    }

    fun getCurrentXp(): Int = player.experience

    fun getRequiredXpForNextLevel(): Int = xpNeededForNextLevel()

    fun getLevel(): Int = player.level

    fun totalXpForLevel(level: Int): Int = 30 * level * level + 90 * level

    private fun xpNeededForNextLevel(): Int {
        val currentTotal = totalXpForLevel(player.level)
        val nextTotal = totalXpForLevel(player.level + 1)
        return nextTotal - currentTotal
    }
}

/** Callback for injecting stat tuning at each level-up. */
fun interface LevelUpHandler {
    fun onLevelUp(player: Player)
}

/** Default stat growth used when no other handler is provided. */
class DefaultLevelUpHandler : LevelUpHandler {
    override fun onLevelUp(player: Player) {
        player.stats.maxHp += 5
        player.stats.hp = player.stats.maxHp
        player.stats.attack += 1
    }
}

/** Carries information about a single level-up event. */
data class LevelUpResult(val newLevel: Int, val mutationChoices: List<Mutation>)
