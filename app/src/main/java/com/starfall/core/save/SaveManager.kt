package com.starfall.core.save

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.starfall.core.engine.RunResult
import com.starfall.core.progression.MetaProfile
import com.starfall.core.progression.toMetaProfile
import com.starfall.core.progression.toSave
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central entry point for persisting and restoring meta progression and the active run.
 * Uses simple JSON files written to a dedicated saves directory so it can be swapped out for
 * platform storage later without touching callers.
 */
object SaveManager {
    private const val TAG = "SaveManager"
    private val gson = GsonBuilder().setPrettyPrinting().create()
    @Volatile
    private var overrideStorageDir: File? = null
    private val fallbackStorageDir: File by lazy {
        File(System.getProperty("user.dir"), "saves").apply { mkdirs() }
    }

    private val storageDir: File
        get() = overrideStorageDir ?: fallbackStorageDir

    private val metaProfileFile: File
        get() = File(storageDir, "meta_profile.json")
    private val currentRunFile: File
        get() = File(storageDir, "current_run.json")
    private val lastRunResultFile: File
        get() = File(storageDir, "last_run_result.json")

    private val metaProfileState: MutableStateFlow<MetaProfile> by lazy {
        MutableStateFlow(readMetaProfile().toMetaProfile())
    }

    fun initialize(context: Context) {
        val dir = File(context.filesDir, "saves").apply { mkdirs() }
        overrideStorageDir = dir
        reloadMetaProfile()
    }

    fun metaProfileFlow(): StateFlow<MetaProfile> = metaProfileState.asStateFlow()

    fun loadMetaProfile(): MetaProfileSave = readMetaProfile()

    private fun readMetaProfile(): MetaProfileSave {
        val defaultProfile = MetaProfileSave()
        if (!metaProfileFile.exists()) return defaultProfile
        return runCatching {
            gson.fromJson(metaProfileFile.readText(), MetaProfileSave::class.java) ?: defaultProfile
        }.onFailure { error ->
            Log.e(TAG, "Failed to read meta profile", error)
        }.getOrDefault(defaultProfile)
    }

    fun loadMetaProfileModel(): MetaProfile = metaProfileState.value

    fun reloadMetaProfile() {
        metaProfileState.value = readMetaProfile().toMetaProfile()
    }

    fun saveMetaProfile(profile: MetaProfileSave) {
        runCatching {
            metaProfileFile.writeText(gson.toJson(profile))
        }.onFailure { error ->
            Log.e(TAG, "Failed to save meta profile", error)
        }
        metaProfileState.value = profile.toMetaProfile()
    }

    fun saveMetaProfile(profile: MetaProfile) {
        saveMetaProfile(profile.toSave())
    }

    fun loadRun(): RunSaveSnapshot? {
        if (!currentRunFile.exists()) return null
        return runCatching {
            gson.fromJson(currentRunFile.readText(), RunSaveSnapshot::class.java)
        }.onFailure { error ->
            Log.e(TAG, "Failed to load run snapshot", error)
        }.getOrNull()
    }

    fun saveRun(snapshot: RunSaveSnapshot) {
        runCatching {
            currentRunFile.writeText(gson.toJson(snapshot))
        }.onFailure { error ->
            Log.e(TAG, "Failed to save run snapshot", error)
        }
    }

    fun clearRun() {
        if (currentRunFile.exists()) {
            currentRunFile.delete()
        }
    }

    fun saveLastRunResult(result: RunResult) {
        runCatching {
            lastRunResultFile.writeText(gson.toJson(result))
        }.onFailure { error ->
            Log.e(TAG, "Failed to save last run result", error)
        }
    }

    fun loadLastRunResult(): RunResult? {
        if (!lastRunResultFile.exists()) return null
        return runCatching {
            gson.fromJson(lastRunResultFile.readText(), RunResult::class.java)
        }.onFailure { error ->
            Log.e(TAG, "Failed to load last run result", error)
        }.getOrNull()
    }
}
