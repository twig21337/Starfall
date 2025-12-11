package com.starfall.core.save

import com.google.gson.GsonBuilder
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

    fun saveMetaProfile(profile: MetaProfileSave) {
        runCatching {
            metaProfileFile.writeText(gson.toJson(profile))
        }
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
