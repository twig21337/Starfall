package com.starfall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starfall.app.game.DungeonScreen
import com.starfall.app.game.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: GameViewModel = viewModel()
            val uiState by viewModel.uiState

            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DungeonScreen(
                        uiState = uiState,
                        onAction = viewModel::onPlayerAction
                    )
                }
            }
        }
    }
}
