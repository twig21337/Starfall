package com.starfall.core.save

import com.google.gson.GsonBuilder
import com.starfall.core.progression.MetaProfile
import com.starfall.core.progression.toMetaProfile
import com.starfall.core.progression.toSave
import java.io.File

/**
 * Central entry point for persisting and restoring meta progression and the active run.
 * Uses simple JSON files written to a dedicated saves directory so it can be swapped out for
 * platform storage later without touching callers.
 */
object SaveManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val storageDir: File = File(System.getProperty("user.dir"), "saves").apply { mkdirs() }
    private val metaProfileFile = File(storageDir, "meta_profile.json")
    private val currentRunFile = File(storageDir, "current_run.json")

    fun loadMetaProfile(): MetaProfileSave {
        val defaultProfile = MetaProfileSave()
        if (!metaProfileFile.exists()) return defaultProfile
        return runCatching {
            gson.fromJson(metaProfileFile.readText(), MetaProfileSave::class.java) ?: defaultProfile
        }.getOrDefault(defaultProfile)
    }

    fun loadMetaProfileModel(): MetaProfile = loadMetaProfile().toMetaProfile()

    fun saveMetaProfile(profile: MetaProfileSave) {
        runCatching {
            metaProfileFile.writeText(gson.toJson(profile))
        }
    }

    fun saveMetaProfile(profile: MetaProfile) {
        saveMetaProfile(profile.toSave())
    }

    fun loadRun(): RunSaveSnapshot? {
        if (!currentRunFile.exists()) return null
        return runCatching {
            gson.fromJson(currentRunFile.readText(), RunSaveSnapshot::class.java)
        }.getOrNull()
    }

    fun saveRun(snapshot: RunSaveSnapshot) {
        runCatching {
            currentRunFile.writeText(gson.toJson(snapshot))
        }
    }

    fun clearRun() {
        if (currentRunFile.exists()) {
            currentRunFile.delete()
        }
    }
}
