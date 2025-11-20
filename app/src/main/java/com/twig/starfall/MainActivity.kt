package com.twig.starfall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starfall.app.game.DungeonScreen
import com.starfall.app.game.GameViewModel
import com.twig.starfall.ui.theme.StarfallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StarfallTheme {
                val gameViewModel: GameViewModel = viewModel()
                val uiState by gameViewModel.uiState

                Surface(color = MaterialTheme.colorScheme.background) {
                    DungeonScreen(
                        uiState = uiState,
                        onAction = gameViewModel::onPlayerAction,
                        onDismissDescendPrompt = gameViewModel::dismissDescendPrompt,
                        onStartNewGame = gameViewModel::startNewGame,
                        onRequestTarget = { gameViewModel.prepareTargetedItem(it.id) },
                        onTileTarget = gameViewModel::onTargetSelected
                    )
                }
            }
        }
    }
}
