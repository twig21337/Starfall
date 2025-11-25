package com.starfall.core.progression

import com.starfall.core.model.Player
import com.starfall.core.model.Position
import com.starfall.core.model.Stats
import com.starfall.core.mutation.MutationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class XpAndMutationTest {

    @Test
    fun `gain xp levels multiple times and triggers mutation rolls`() {
        val player = testPlayer()
        val mutationManager = MutationManager(random = Random(0))
        val xpManager = XpManager(player, mutationManager)

        xpManager.gainXp(800)

        assertEquals(3, xpManager.getLevel())
        assertTrue("Expected mutation choices after level ups", mutationManager.getPendingChoices().isNotEmpty())
        // 300 XP for level 2, 400 more for level 3, leaving 100 toward level 4 (cost 500)
        assertEquals(100, xpManager.getCurrentXp())
    }

    @Test
    fun `mutation choices avoid duplicates and owned entries`() {
        val player = testPlayer()
        player.mutationState.acquiredMutationIds += "reinforced_tendons"
        val mutationManager = MutationManager(random = Random(4))

        mutationManager.onPlayerLevelUp(player)
        val choices = mutationManager.getPendingChoices()

        assertEquals(2, choices.size)
        assertEquals(choices.size, choices.distinctBy { it.id }.size)
        assertFalse(choices.any { it.id == "reinforced_tendons" })
    }

    private fun testPlayer(): Player =
        Player(
            id = 1,
            name = "Tester",
            position = Position(0, 0),
            glyph = '@',
            stats = Stats(maxHp = 20, hp = 20, attack = 5, defense = 2)
        )
}
