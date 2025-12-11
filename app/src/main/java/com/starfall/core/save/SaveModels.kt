package com.starfall.core.save

import com.starfall.core.dungeon.SimpleDungeonGenerator
import com.starfall.core.engine.GameConfig
import com.starfall.core.items.ArmorSlot
import com.starfall.core.model.Item
import com.starfall.core.model.Level
import com.starfall.core.model.Player
import com.starfall.core.model.Position
import com.starfall.core.model.Stats
import com.starfall.core.mutation.MutationState
import com.starfall.core.overworld.OverworldRegions
import com.starfall.core.progression.MetaProgressionState
import com.starfall.core.progression.PlayerProfile
import com.starfall.core.run.RunState

/**
 * Serializable snapshots for meta-progression and active runs. The intent is to keep these
 * data-only so serialization is straightforward and future migrations can add optional fields
 * without breaking existing saves.
 */
data class MetaProfileSave(
    var totalTitanShards: Int = 0,
    var spentTitanShards: Int = 0,
    var ownedUpgrades: Map<String, Int> = emptyMap(),
    var lifetimeRuns: Int = 0,
    var lifetimeVictories: Int = 0,
    var lifetimeFloorsCleared: Int = 0,
    var lifetimeEnemiesKilled: Int = 0,
    var lifetimeBossesKilled: Int = 0,
    var lifetimeKills: Int = 0,
    var lastRunId: String? = null,
    var unlockedMutations: List<String> = emptyList(),
    var unlockedRegions: List<String> = listOf(OverworldRegions.FALLEN_TITAN.id),
    var lastSelectedRegionId: String? = OverworldRegions.FALLEN_TITAN.id
) {
    companion object {
        fun fromProfile(
            profile: PlayerProfile,
            lifetimeRuns: Int = 0,
            lifetimeKills: Int = 0,
            lastRunId: String? = null
        ): MetaProfileSave = MetaProfileSave(
            totalTitanShards = profile.metaProgressionState.metaCurrency,
            spentTitanShards = 0,
            ownedUpgrades = emptyMap(),
            lifetimeRuns = lifetimeRuns,
            lifetimeVictories = 0,
            lifetimeFloorsCleared = 0,
            lifetimeEnemiesKilled = lifetimeKills,
            lifetimeBossesKilled = 0,
            lifetimeKills = lifetimeKills,
            lastRunId = lastRunId,
            unlockedMutations = emptyList(),
            unlockedRegions = listOf(OverworldRegions.FALLEN_TITAN.id),
            lastSelectedRegionId = OverworldRegions.FALLEN_TITAN.id
        )
    }

    fun toPlayerProfile(): PlayerProfile = PlayerProfile(
        id = "profile-default",
        name = "Starfarer",
        metaProgressionState = MetaProgressionState(metaCurrency = totalTitanShards - spentTitanShards)
    )
}

data class RunSaveSnapshot(
    val runState: RunStateSave,
    val player: PlayerSave,
    val dungeon: DungeonSave
)

data class RunStateSave(
    val runId: String = "",
    val regionId: String = OverworldRegions.FALLEN_TITAN.id,
    val seed: Long = 0L,
    val maxFloor: Int = 1,
    val currentFloor: Int = 1,
    val floorsCleared: Int = 0,
    val enemiesKilled: Int = 0,
    val elitesKilled: Int = 0,
    val bossesKilled: Int = 0,
    val miniBossesKilled: Int = 0,
    val metaCurrencyEarned: Int = 0,
    val mutationsChosen: List<String> = emptyList(),
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long? = null,
    val isFinished: Boolean = false,
    val isVictory: Boolean = false
) {
    companion object {
        fun fromRunState(runState: RunState): RunStateSave = RunStateSave(
            runId = runState.runId,
            regionId = runState.regionId,
            seed = runState.seed,
            maxFloor = runState.maxFloor,
            currentFloor = runState.currentFloor,
            floorsCleared = runState.floorsCleared,
            enemiesKilled = runState.enemiesKilled,
            elitesKilled = runState.elitesKilled,
            bossesKilled = runState.bossesKilled,
            miniBossesKilled = runState.miniBossesKilled,
            metaCurrencyEarned = runState.metaCurrencyEarned,
            mutationsChosen = runState.mutationsChosen.toList(),
            startTimeMillis = runState.startTimeMillis,
            endTimeMillis = runState.endTimeMillis,
            isFinished = runState.isFinished,
            isVictory = runState.isVictory
        )
    }

    fun toRunState(): RunState = RunState(
        runId = runId,
        regionId = regionId,
        seed = seed,
        maxFloor = maxFloor,
        currentFloor = currentFloor,
        floorsCleared = floorsCleared,
        enemiesKilled = enemiesKilled,
        elitesKilled = elitesKilled,
        bossesKilled = bossesKilled,
        miniBossesKilled = miniBossesKilled,
        metaCurrencyEarned = metaCurrencyEarned,
        mutationsChosen = mutationsChosen.toMutableList(),
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        isFinished = isFinished,
        isVictory = isVictory
    )
}

data class PlayerSave(
    val id: Int,
    val name: String,
    val glyph: Char,
    val positionX: Int,
    val positionY: Int,
    val stats: StatsSnapshot,
    val level: Int,
    val experience: Int,
    val inventory: List<Item>,
    val equippedWeaponId: Int?,
    val equippedArmorBySlot: Map<ArmorSlot, Int>,
    val mutationState: MutationState
) {
    data class StatsSnapshot(
        val maxHp: Int,
        val hp: Int,
        val attack: Int,
        val defense: Int,
        val maxArmor: Int,
        val armor: Int
    )

    companion object {
        fun fromPlayer(player: Player): PlayerSave = PlayerSave(
            id = player.id,
            name = player.name,
            glyph = player.glyph,
            positionX = player.position.x,
            positionY = player.position.y,
            stats = StatsSnapshot(
                maxHp = player.stats.maxHp,
                hp = player.stats.hp,
                attack = player.stats.attack,
                defense = player.stats.defense,
                maxArmor = player.stats.maxArmor,
                armor = player.stats.armor
            ),
            level = player.level,
            experience = player.experience,
            inventory = player.inventorySnapshot(),
            equippedWeaponId = player.equippedWeaponId,
            equippedArmorBySlot = player.equippedArmorBySlot.toMap(),
            mutationState = player.mutationState.copy(
                acquiredMutationIds = player.mutationState.acquiredMutationIds.toMutableSet()
            )
        )
    }

    fun toPlayer(): Player {
        val statsSnapshot = stats
        val stats = Stats(
            maxHp = statsSnapshot.maxHp,
            hp = statsSnapshot.hp,
            attack = statsSnapshot.attack,
            defense = statsSnapshot.defense,
            maxArmor = statsSnapshot.maxArmor,
            armor = statsSnapshot.armor
        )
        val player = Player(
            id = id,
            name = name,
            position = Position(positionX, positionY),
            glyph = glyph,
            stats = stats,
            level = level,
            experience = experience,
            inventory = inventory.map { it.copy() }.toMutableList(),
            equippedWeaponId = equippedWeaponId,
            equippedArmorBySlot = equippedArmorBySlot.toMutableMap(),
            mutationState = mutationState.copy(
                acquiredMutationIds = mutationState.acquiredMutationIds.toMutableSet()
            )
        )
        val equippedArmorIds = equippedArmorBySlot.values.toSet()
        player.inventory.replaceAll { item ->
            val isEquipped = item.id == equippedWeaponId || equippedArmorIds.contains(item.id)
            item.copy(isEquipped = isEquipped)
        }
        return player
    }
}

/**
 * Dungeon serialization currently assumes we only persist at floor boundaries. The level layout
 * is regenerated from the run seed and depth using the existing [SimpleDungeonGenerator].
 */
data class DungeonSave(
    val floorNumber: Int = 1,
    val isBossFloor: Boolean = false,
    val isFinalFloor: Boolean = false
) {
    companion object {
        fun fromDungeon(currentDungeon: Level): DungeonSave = DungeonSave(
            floorNumber = currentDungeon.depth,
            isBossFloor = currentDungeon.isBossFloor,
            isFinalFloor = currentDungeon.isFinalFloor
        )
    }

    fun toDungeon(seed: Long): Level {
        // The generator already uses randomness internally; for now we simply rebuild by depth
        // and documented defaults. If deterministic generation is added later, we can thread the
        // seed through SimpleDungeonGenerator.
        val generator = SimpleDungeonGenerator()
        val regenerated = generator.generate(
            GameConfig.DEFAULT_LEVEL_WIDTH,
            GameConfig.DEFAULT_LEVEL_HEIGHT,
            floorNumber
        )
        regenerated.isBossFloor = isBossFloor
        regenerated.isFinalFloor = isFinalFloor
        return regenerated
    }
}
