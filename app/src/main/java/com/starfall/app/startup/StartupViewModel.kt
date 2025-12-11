package com.starfall.app.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starfall.core.run.RunManager
import com.starfall.core.save.SaveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * StartupViewModel lives in [com.starfall.app.startup] alongside [StartupScreen] so UI code has a
 * single owner for loading the player's meta progression and any resumable run snapshot.
 *
 * The view model defers to [SaveManager] for loading persisted data and calls into
 * [RunManager.continueRun] when the player chooses to resume. This keeps all persistence wiring in
 * one place and lets the navigation host focus solely on routing the user to the right screen.
 */
class StartupViewModel : ViewModel() {
    private val _uiState: MutableStateFlow<StartupUiState> = MutableStateFlow(StartupUiState())
    val uiState: StateFlow<StartupUiState> = _uiState

    init {
        loadStartupData()
    }

    private fun loadStartupData() {
        viewModelScope.launch {
            val metaProfile = withContext(Dispatchers.IO) { SaveManager.loadMetaProfileModel() }
            val runSnapshot = withContext(Dispatchers.IO) { SaveManager.loadRun() }
            _uiState.value = StartupUiState(
                isLoading = false,
                showContinue = runSnapshot?.runState?.isFinished == false,
                titanShardsAvailable = metaProfile.availableTitanShards
            )
        }
    }

    fun continueRunIfAvailable(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { SaveManager.loadRun() }
            if (snapshot?.runState?.isFinished == false) {
                RunManager.continueRun(snapshot)
                onSuccess()
            }
        }
    }
}

/**
 * Lightweight state container for the startup menu. Future menu items like daily challenges can be
 * added here without disturbing the composable API.
 */
data class StartupUiState(
    val isLoading: Boolean = true,
    val showContinue: Boolean = false,
    val titanShardsAvailable: Int = 0
)
