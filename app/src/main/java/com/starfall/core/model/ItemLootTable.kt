package com.starfall.core.model

import kotlin.random.Random

/** Weighted pools for consumable loot by dungeon depth. */
object ItemLootTable {

    fun randomConsumableForDepth(depth: Int, rng: Random = Random.Default): ItemType {
        val pool = consumablePool(depth)
        return pool.random(rng)
    }

    private fun consumablePool(depth: Int): List<ItemType> {
        val pool = mutableListOf<ItemType>()

        repeat(3) { pool += ItemType.HEALING_POTION }
        pool += listOf(
            ItemType.PHASESTEP_DUST,
            ItemType.BLINKSTONE_SHARD,
            ItemType.VOIDBANE_SALTS,
            ItemType.VEILKEY_PICK,
            ItemType.STARGAZERS_INK,
            ItemType.ECHO_TUNED_COMPASS,
            ItemType.HOLLOWSIGHT_VIAL,
            ItemType.LUMENVEIL_ELIXIR
        )

        if (depth >= 3) {
            pool += listOf(
                ItemType.TITANBLOOD_TONIC,
                ItemType.IRONBARK_INFUSION,
                ItemType.SLIPSHADOW_OIL,
                ItemType.VOIDFLARE_ORB,
                ItemType.FROSTSHARD_ORB,
                ItemType.STARSPIKE_DART,
                ItemType.GLOOMSMOKE_VESSEL,
                ItemType.MINDWARD_CAPSULE
            )
        }

        if (depth >= 5) {
            pool += listOf(
                ItemType.ASTRAL_SURGE_DRAUGHT,
                ItemType.VOIDRAGE_AMPOULE,
                ItemType.STARSEERS_PHIAL,
                ItemType.VEILBREAKER_DRAUGHT,
                ItemType.LANTERN_OF_ECHOES,
                ItemType.HOLLOWGUARD_INFUSION,
                ItemType.STARBOUND_HOOK,
                ItemType.ASTRAL_RESIDUE_PHIAL
            )
        }

        if (depth >= 8) {
            pool += listOf(
                ItemType.HOLLOW_SHARD_FRAGMENT,
                ItemType.TITAN_EMBER_GRANULE,
                ItemType.LUNAR_ECHO_VIAL
            )
        }

        return pool
    }
}
