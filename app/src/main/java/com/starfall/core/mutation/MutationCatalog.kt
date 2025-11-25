package com.starfall.core.mutation

import com.starfall.core.model.Player
import com.starfall.core.model.PlayerEffect
import com.starfall.core.model.PlayerEffectType
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Holds the built-in mutation definitions. The mutations are intentionally
 * data-first so they can be rendered by UI layers or extended in content packs.
 */
object MutationCatalog {
    val all: List<Mutation> = listOf(
        Mutation(
            id = "reinforced_tendons",
            name = "Reinforced Tendons",
            description = "Small armor buffer that refreshes naturally over time.",
            tier = MutationTier.TIER_1,
            tags = setOf("defense")
        ) { player: Player ->
            player.stats.maxArmor += 1
            player.stats.armor = player.stats.maxArmor
        },
        Mutation(
            id = "thickened_skin",
            name = "Thickened Skin",
            description = "Small physical damage reduction as dermal layers toughen.",
            tier = MutationTier.TIER_1,
            tags = setOf("defense")
        ) { player ->
            player.mutationState.damageReduction += 0.05
        },
        Mutation(
            id = "sharp_nails",
            name = "Sharp Nails",
            description = "Adds a small melee damage boost from hardened claws.",
            tier = MutationTier.TIER_1,
            tags = setOf("offense")
        ) { player ->
            player.stats.attack += 2
        },
        Mutation(
            id = "adrenal_boost",
            name = "Adrenal Boost",
            description = "Slightly accelerates astral energy regeneration and reactions.",
            tier = MutationTier.TIER_1,
            tags = setOf("utility")
        ) { player ->
            player.mutationState.energyRegenBonus += 0.1
            player.mutationState.dodgeBonus += 0.02
        },
        Mutation(
            id = "spinal_reinforcement",
            name = "Spinal Reinforcement",
            description = "Reinforced spine grants +2 inventory capacity for carried gear.",
            tier = MutationTier.TIER_2,
            tags = setOf("utility")
        ) { player ->
            player.mutationState.bonusInventorySlots += 2
        },
        Mutation(
            id = "elastic_muscles",
            name = "Elastic Muscles",
            description = "Noticeable dodge improvement from flexible muscle fibers.",
            tier = MutationTier.TIER_2,
            tags = setOf("defense")
        ) { player ->
            player.mutationState.dodgeBonus += 0.08
        },
        Mutation(
            id = "light_carapace",
            name = "Light Carapace",
            description = "Chitinous plates offer moderate damage reduction.",
            tier = MutationTier.TIER_2,
            tags = setOf("defense")
        ) { player ->
            player.mutationState.damageReduction += 0.10
        },
        Mutation(
            id = "minor_venom_glands",
            name = "Minor Venom Glands",
            description = "Basic chance to infuse attacks with poison.",
            tier = MutationTier.TIER_2,
            tags = setOf("offense")
        ) { player ->
            player.mutationState.poisonChanceOnHit += 0.15
        },
        Mutation(
            id = "chitin_plating",
            name = "Chitin Plating",
            description = "Massive damage reduction from layered carapace.",
            tier = MutationTier.TIER_3,
            tags = setOf("defense")
        ) { player ->
            player.mutationState.damageReduction += 0.25
        },
        Mutation(
            id = "hollow_bones",
            name = "Hollow Bones",
            description = "Greatly improved evasiveness at the cost of some vitality.",
            tier = MutationTier.TIER_3,
            tags = setOf("defense", "tradeoff")
        ) { player ->
            player.mutationState.dodgeBonus += 0.15
            val reducedMax = max(1, (player.stats.maxHp * 0.9).roundToInt())
            val hpLoss = player.stats.maxHp - reducedMax
            player.stats.maxHp = reducedMax
            player.stats.hp = max(1, player.stats.hp - hpLoss)
        },
        Mutation(
            id = "adrenaline_overclock",
            name = "Adrenaline Overclock",
            description = "Chance each round to act twice due to metabolic surges.",
            tier = MutationTier.TIER_3,
            tags = setOf("speed")
        ) { player ->
            player.mutationState.extraActionChance += 0.05
        },
        Mutation(
            id = "bioplasma_gland",
            name = "Bioplasma Gland",
            description = "Unlocks a biological ranged spit attack with its own cooldown.",
            tier = MutationTier.TIER_3,
            tags = setOf("offense", "ability")
        ) { player ->
            player.mutationState.hasBioplasmaGland = true
        },
        Mutation(
            id = "regenerator_core",
            name = "Regenerator Core",
            description = "Once per encounter, automatically resurrects with partial health.",
            tier = MutationTier.TIER_4,
            tags = setOf("defense", "survival")
        ) { player ->
            player.mutationState.resurrectionCharges += 1
        },
        Mutation(
            id = "phase_flesh",
            name = "Phase Flesh",
            description = "Fleeting intangibility occasionally causes incoming attacks to miss outright.",
            tier = MutationTier.TIER_4,
            tags = setOf("defense")
        ) { player ->
            player.mutationState.phaseDodgeChance += 0.20
        },
        Mutation(
            id = "hyper_reflex_cortex",
            name = "Hyper-Reflex Cortex",
            description = "Guarantees the first strike is dodged and boosts ongoing evasion.",
            tier = MutationTier.TIER_4,
            tags = setOf("defense", "speed")
        ) { player ->
            player.mutationState.dodgeBonus += 0.10
            player.addEffect(
                PlayerEffect(
                    type = PlayerEffectType.MUTATION_BOON,
                    remainingTurns = Int.MAX_VALUE,
                    mutationBonus = 5
                )
            )
        },
        Mutation(
            id = "time_split_organ",
            name = "Time-Split Organ",
            description = "Rare organ enabling limited temporal rewinds.",
            tier = MutationTier.TIER_5,
            tags = setOf("utility", "mythic")
        ) { player ->
            player.mutationState.timeRewindCharges += 1
        },
        Mutation(
            id = "dragonheart_mutation",
            name = "Dragonheart Mutation",
            description = "Major vitality surge, minor fire immunity placeholder, and a breath attack hook.",
            tier = MutationTier.TIER_5,
            tags = setOf("offense", "defense", "mythic")
        ) { player ->
            val bonusHp = max(1, (player.stats.maxHp * 0.25).roundToInt())
            player.stats.maxHp += bonusHp
            player.stats.hp = player.stats.maxHp
            player.mutationState.damageReduction += 0.10
            player.addEffect(
                PlayerEffect(
                    type = PlayerEffectType.STATUS_IMMUNITY,
                    remainingTurns = Int.MAX_VALUE,
                    statusImmunity = true
                )
            )
        }
    )

    val byTier: Map<MutationTier, List<Mutation>> = all.groupBy { it.tier }
}
