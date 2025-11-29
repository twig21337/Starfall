package com.starfall.core.boss

import com.starfall.core.items.LootGenerator
import com.starfall.core.model.EnemyBehaviorType
import com.starfall.core.model.ItemType
import com.starfall.core.model.Stats
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** High-level manager for selecting, scaling, and rewarding dungeon bosses. */
object BossManager {

    data class BossDefinition(
        val id: String,
        val name: String,
        val glyph: Char,
        val baseStats: BossStats,
        val behaviorType: EnemyBehaviorType,
        val uniqueLootTableId: String,
        val tags: Set<String> = emptySet()
    )

    data class BossStats(
        val maxHp: Int,
        val attack: Int,
        val defense: Int
    )

    data class BossInstance(
        val definition: BossDefinition,
        val tier: Int,
        val stats: Stats,
        val xpReward: Int
    )

    data class BossLootEntry(
        val itemType: ItemType,
        val weight: Double,
        val minTier: Int = 1,
        val maxTier: Int? = null
    )

    data class BossLootTable(val entries: List<BossLootEntry>) {
        fun filteredEntries(tier: Int): List<BossLootEntry> = entries.filter { entry ->
            tier >= entry.minTier && (entry.maxTier == null || tier <= entry.maxTier)
        }
    }

    data class BossLootResult(
        val itemDrops: List<ItemType>,
        val equipmentDrops: List<LootGenerator.EquipmentDropResult>
    )

    private val bossPool = listOf(
        BossDefinition(
            id = "ember_tyrant",
            name = "Ember Tyrant",
            glyph = 'Φ',
            baseStats = BossStats(maxHp = 36, attack = 9, defense = 4),
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            uniqueLootTableId = "ember_tyrant_loot",
            tags = setOf("fire", "brute")
        ),
        BossDefinition(
            id = "venom_matriarch",
            name = "Venom Matriarch",
            glyph = 'Ψ',
            baseStats = BossStats(maxHp = 32, attack = 8, defense = 3),
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            uniqueLootTableId = "venom_matriarch_loot",
            tags = setOf("poison", "summoner")
        ),
        BossDefinition(
            id = "astral_warden",
            name = "Astral Warden",
            glyph = 'Ω',
            baseStats = BossStats(maxHp = 40, attack = 10, defense = 5),
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            uniqueLootTableId = "astral_warden_loot",
            tags = setOf("arcane", "guardian")
        ),
        BossDefinition(
            id = "fallen_astromancer",
            name = "Fallen Astromancer",
            glyph = '✦',
            baseStats = BossStats(maxHp = 34, attack = 10, defense = 4),
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            uniqueLootTableId = "loot_boss_fallen_astromancer",
            tags = setOf("magic", "ranged", "teleport", "starfall")
        ),
        BossDefinition(
            id = "bone_forged_colossus",
            name = "Bone-forged Colossus",
            glyph = '⚒',
            baseStats = BossStats(maxHp = 48, attack = 11, defense = 6),
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            uniqueLootTableId = "loot_boss_bone_forged_colossus",
            tags = setOf("melee", "heavy", "armored", "necrotic")
        ),
        BossDefinition(
            id = "blighted_hive_mind",
            name = "Blighted Hive-Mind",
            glyph = 'Φ',
            baseStats = BossStats(maxHp = 36, attack = 9, defense = 3),
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            uniqueLootTableId = "loot_boss_blighted_hive_mind",
            tags = setOf("poison", "summoner", "ranged", "aura")
        ),
        BossDefinition(
            id = "echo_knight_remnant",
            name = "Echo Knight Remnant",
            glyph = 'Ϟ',
            baseStats = BossStats(maxHp = 35, attack = 10, defense = 4),
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            uniqueLootTableId = "loot_boss_echo_knight_remnant",
            tags = setOf("melee", "dash", "clone", "temporal")
        ),
        BossDefinition(
            id = "heartstealer_wyrm",
            name = "Heartstealer Wyrm",
            glyph = 'Ѫ',
            baseStats = BossStats(maxHp = 42, attack = 11, defense = 5),
            behaviorType = EnemyBehaviorType.SIMPLE_CHASER,
            uniqueLootTableId = "loot_boss_heartstealer_wyrm",
            tags = setOf("lifesteal", "burrow", "pulse", "dragon")
        )
    )

    private val globalLootTable = BossLootTable(
        listOf(
            BossLootEntry(ItemType.TITANBLOOD_TONIC, weight = 6.0),
            BossLootEntry(ItemType.VOIDRAGE_AMPOULE, weight = 6.0),
            BossLootEntry(ItemType.IRONBARK_INFUSION, weight = 5.0),
            BossLootEntry(ItemType.ASTRAL_SURGE_DRAUGHT, weight = 4.0, minTier = 2),
            BossLootEntry(ItemType.LANTERN_OF_ECHOES, weight = 4.0, minTier = 2),
            BossLootEntry(ItemType.VEILBREAKER_DRAUGHT, weight = 3.0, minTier = 3),
            BossLootEntry(ItemType.STARBOUND_HOOK, weight = 2.0, minTier = 3),
            BossLootEntry(ItemType.HOLLOW_SHARD_FRAGMENT, weight = 2.0, minTier = 4)
        )
    )

    private val uniqueLootTables: Map<String, BossLootTable> = mapOf(
        "ember_tyrant_loot" to BossLootTable(
            listOf(
                BossLootEntry(ItemType.VOIDFLARE_ORB, weight = 6.0),
                BossLootEntry(ItemType.TITAN_EMBER_GRANULE, weight = 3.0, minTier = 2),
                BossLootEntry(ItemType.ASTRAL_RESIDUE_PHIAL, weight = 2.0, minTier = 3)
            )
        ),
        "venom_matriarch_loot" to BossLootTable(
            listOf(
                BossLootEntry(ItemType.MINDWARD_CAPSULE, weight = 5.0),
                BossLootEntry(ItemType.VOIDBANE_SALTS, weight = 5.0),
                BossLootEntry(ItemType.GLOOMSMOKE_VESSEL, weight = 3.0, minTier = 2)
            )
        ),
        "astral_warden_loot" to BossLootTable(
            listOf(
                BossLootEntry(ItemType.STARSEERS_PHIAL, weight = 6.0),
                BossLootEntry(ItemType.ECHO_TUNED_COMPASS, weight = 4.0),
                BossLootEntry(ItemType.LUNAR_ECHO_VIAL, weight = 3.0, minTier = 2)
            )
        ),
        "loot_boss_fallen_astromancer" to BossLootTable(
            listOf(
                BossLootEntry(ItemType.STARHEART_FOCUS, weight = 6.0),
                BossLootEntry(ItemType.CELESTIAL_PRISM, weight = 4.0),
                BossLootEntry(ItemType.COSMIC_ECHO_MUTATION, weight = 2.5, minTier = 2)
            )
        ),
        "loot_boss_bone_forged_colossus" to BossLootTable(
            listOf(
                BossLootEntry(ItemType.COLOSSUS_PLATE, weight = 5.0),
                BossLootEntry(ItemType.RIBBREAKER_MAUL, weight = 4.5),
                BossLootEntry(ItemType.GRAVE_RESILIENCE_MUTATION, weight = 3.0, minTier = 2)
            )
        ),
        "loot_boss_blighted_hive_mind" to BossLootTable(
            listOf(
                BossLootEntry(ItemType.HIVE_QUEEN_MANDIBLE, weight = 5.5),
                BossLootEntry(ItemType.INFECTED_SPINE, weight = 4.0),
                BossLootEntry(ItemType.VIRAL_MUTATION_MUTATION, weight = 3.0, minTier = 2)
            )
        ),
        "loot_boss_echo_knight_remnant" to BossLootTable(
            listOf(
                BossLootEntry(ItemType.BROKEN_ECHO_BLADE, weight = 5.5),
                BossLootEntry(ItemType.SHADOWGUARD_MANTLE, weight = 4.0),
                BossLootEntry(ItemType.TEMPORAL_BLUR_MUTATION, weight = 3.0, minTier = 2)
            )
        ),
        "loot_boss_heartstealer_wyrm" to BossLootTable(
            listOf(
                BossLootEntry(ItemType.WYRMFANG_DAGGER, weight = 5.5),
                BossLootEntry(ItemType.HEARTFORGE_CORE, weight = 4.0),
                BossLootEntry(ItemType.SANGUINE_BURST_MUTATION, weight = 3.0, minTier = 2)
            )
        )
    )

    fun isBossFloor(depth: Int): Boolean = depth % BossConfig.BOSS_FLOOR_INTERVAL == 0

    fun bossTierForDepth(depth: Int): Int = max(1, depth / BossConfig.BOSS_FLOOR_INTERVAL)

    fun selectBossForDepth(depth: Int, rng: Random = Random.Default): BossInstance {
        val tier = bossTierForDepth(depth)
        val definition = bossPool.random(rng)
        val scaled = scaleStats(definition.baseStats, tier)
        val xpReward = BossConfig.BASE_BOSS_XP + (tier - 1) * BossConfig.XP_PER_TIER
        return BossInstance(definition, tier, scaled, xpReward)
    }

    fun rollBossLoot(instance: BossInstance, rng: Random = Random.Default): BossLootResult {
        val tier = instance.tier
        val itemDrops = mutableListOf<ItemType>()

        val globalRolls = 1 + (tier / BossConfig.GLOBAL_LOOT_ROLL_INTERVAL)
        repeat(globalRolls) {
            rollFromTable(globalLootTable, tier, rng)?.let { itemDrops += it }
        }

        val uniqueTable = uniqueLootTables[instance.definition.uniqueLootTableId]
        if (uniqueTable != null) {
            val uniqueChance = BossConfig.BASE_UNIQUE_ROLL_CHANCE + ((tier - 1) * BossConfig.UNIQUE_ROLL_PER_TIER)
            if (rng.nextDouble() < uniqueChance.coerceAtMost(0.9)) {
                rollFromTable(uniqueTable, tier, rng)?.let { itemDrops += it }
            }
        }

        val equipmentDrops = mutableListOf<LootGenerator.EquipmentDropResult>()
        val equipmentRolls = 1 + (tier / BossConfig.EQUIPMENT_ROLL_INTERVAL)
        repeat(equipmentRolls) {
            val drop = LootGenerator.rollRandomEquipmentForDepth(depthForTier(tier), rng)
            if (drop != null) {
                equipmentDrops += drop
            }
        }

        return BossLootResult(itemDrops, equipmentDrops)
    }

    private fun rollFromTable(table: BossLootTable, tier: Int, rng: Random): ItemType? {
        val entries = table.filteredEntries(tier)
        if (entries.isEmpty()) return null
        val totalWeight = entries.sumOf { it.weight }
        val roll = rng.nextDouble() * totalWeight
        var cumulative = 0.0
        for (entry in entries) {
            cumulative += entry.weight
            if (roll <= cumulative) {
                return entry.itemType
            }
        }
        return entries.last().itemType
    }

    private fun scaleStats(base: BossStats, tier: Int): Stats {
        val hp = scaledValue(base.maxHp, BossConfig.HP_SCALE_PER_TIER, tier)
        val attack = scaledValue(base.attack, BossConfig.DAMAGE_SCALE_PER_TIER, tier)
        val defense = scaledValue(base.defense, BossConfig.DEFENSE_SCALE_PER_TIER, tier)
        return Stats(maxHp = hp, hp = hp, attack = attack, defense = defense)
    }

    private fun scaledValue(base: Int, scale: Double, tier: Int): Int {
        val multiplier = 1.0 + (tier * scale)
        return max(1, (base * multiplier).roundToInt())
    }

    private fun depthForTier(tier: Int): Int = tier * BossConfig.BOSS_FLOOR_INTERVAL
}
