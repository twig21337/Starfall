package com.starfall.core.overworld

import com.starfall.core.progression.MetaProfile
import com.starfall.core.progression.toSave
import com.starfall.core.run.RunManager
import com.starfall.core.save.SaveManager

/**
 * Orchestrates overworld logic such as region availability, unlocks, and starting runs. The UI layer
 * should query here to populate the hub and delegate run starts to keep business rules centralized.
 */
object OverworldManager {

    fun getAvailableRegions(profile: MetaProfile): List<OverworldRegion> {
        return OverworldRegions.all.filter { region ->
            region.unlockCostTitanShards == 0 || profile.unlockedRegions.contains(region.id)
        }
    }

    fun canUnlockRegion(profile: MetaProfile, region: OverworldRegion): Boolean {
        if (region.unlockCostTitanShards <= 0) return false
        if (profile.unlockedRegions.contains(region.id)) return false
        if (!profile.unlockedRegions.containsAll(region.dependencies)) return false
        return profile.availableTitanShards >= region.unlockCostTitanShards
    }

    fun unlockRegion(profile: MetaProfile, region: OverworldRegion): Boolean {
        if (!canUnlockRegion(profile, region)) return false
        profile.spentTitanShards += region.unlockCostTitanShards
        profile.unlockedRegions.add(region.id)
        SaveManager.saveMetaProfile(profile)
        return true
    }

    fun startRunInRegion(profile: MetaProfile, region: OverworldRegion) {
        RunManager.startNewRun(
            profile = profile.toSave().toPlayerProfile(),
            regionId = region.id,
            minFloors = region.minFloors,
            maxFloors = region.maxFloors,
            metaProfile = profile
        )
        profile.lastSelectedRegionId = region.id
        SaveManager.saveMetaProfile(profile)
        // UI/gameplay layers should call RunManager.persistSnapshot(player, dungeon) once the
        // player avatar and first floor are initialized to ensure continue runs resume correctly.
    }

    /*
     * Overworld UI hooks:
     * - Main menu loads the player's MetaProfile via SaveManager, calls getAvailableRegions(profile)
     *   to show the list of destinations, and invokes startRunInRegion when the player taps Descend.
     * - If SaveManager.loadRun() returns a snapshot, surface a "Continue Run" shortcut that bypasses
     *   region selection and resumes through RunManager.continueRun(...).
     * - New regions are added in OverworldRegion.kt and registered in OverworldRegions.all; unlocks
     *   are tracked on MetaProfile.unlockedRegions with costs paid via Titan Shards through
     *   canUnlockRegion/unlockRegion.
     */
}
