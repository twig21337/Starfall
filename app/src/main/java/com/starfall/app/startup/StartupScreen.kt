package com.starfall.app.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Compose UI for the startup menu. This pairs with [StartupViewModel] to surface continue state and
 * meta progression at launch.
 */
@Composable
fun StartupScreen(
    uiState: StartupUiState,
    onNewGameClick: () -> Unit,
    onContinueClick: () -> Unit,
    onStatsClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Starfall",
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Titan Shards: ${uiState.titanShardsAvailable}",
                style = MaterialTheme.typography.titleMedium
            )

            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNewGameClick,
                enabled = !uiState.isLoading
            ) {
                Text(text = "New Game")
            }

            if (uiState.showContinue) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onContinueClick,
                    enabled = !uiState.isLoading
                ) {
                    Text(text = "Continue")
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStatsClick,
                enabled = !uiState.isLoading
            ) {
                Text(text = "Stats")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOptionsClick,
                enabled = !uiState.isLoading
            ) {
                Text(text = "Options")
            }
        }
    }
}
