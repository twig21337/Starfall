package com.starfall.app.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.starfall.core.engine.RunResult
import com.starfall.core.overworld.OverworldManager
import com.starfall.core.overworld.OverworldRegion
import com.starfall.core.progression.MetaProfile
import com.starfall.core.save.SaveManager

/**
 * Simple placeholder hub for choosing where a new run should start. Once region-specific art is
 * ready, this screen can evolve into a richer overworld selection using [OverworldManager] helpers.
 */
@Composable
fun OverworldScreen(onStartRun: (OverworldRegion) -> Unit) {
    val metaProfile by SaveManager.metaProfileFlow().collectAsState()
    val availableRegions = remember(metaProfile) { OverworldManager.getAvailableRegions(metaProfile) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Select a Destination",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose a region to begin your next descent.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        items(availableRegions) { region ->
            Button(onClick = { onStartRun(region) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = region.name)
            }
        }
    }
}

/**
 * Basic stats placeholder. Reads from the meta profile so we can later showcase richer achievements
 * and history.
 */
@Composable
fun StatsScreen() {
    val metaProfile by SaveManager.metaProfileFlow().collectAsState()
    val lastRunResult: RunResult? = remember { SaveManager.loadLastRunResult() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Stats", style = MaterialTheme.typography.headlineLarge)
        Text(text = "Titan Shards Available: ${metaProfile.availableTitanShards}")
        Text(text = "Titan Shards Earned: ${metaProfile.totalTitanShards}")
        Text(text = "Spent Shards: ${metaProfile.spentTitanShards}")
        Text(text = "Runs Completed: ${metaProfile.lifetimeRuns}")
        Text(text = "Victories: ${metaProfile.lifetimeVictories}")
        Text(text = "Floors Cleared: ${metaProfile.lifetimeFloorsCleared}")
        Text(text = "Enemies Defeated: ${metaProfile.lifetimeEnemiesKilled}")
        Text(text = "Bosses Defeated: ${metaProfile.lifetimeBossesKilled}")
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Last Run", style = MaterialTheme.typography.headlineSmall)
        lastRunResult?.let { result ->
            LastRunStatsSummary(result)
        } ?: Text(
            text = "Complete a run to see detailed stats here.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LastRunStatsSummary(result: RunResult) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatRow(label = "Floors Cleared", value = result.floorsCleared.toString())
        StatRow(label = "Bosses Defeated", value = result.bossesKilled.toString())
        StatRow(label = "Elites Defeated", value = result.elitesKilled.toString())
        StatRow(label = "Enemies Defeated", value = result.enemiesKilled.toString())
        StatRow(label = "Mutations Chosen", value = result.mutationsChosen.toString())
        StatRow(label = "Titan Shards Earned", value = "+${result.metaCurrencyEarned}")
        StatRow(label = "Run Time", value = formatDuration(result.timeInRunMs))
        StatRow(
            label = "Final Cause",
            value = result.cause.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

/**
 * Placeholder options surface. Future controls (audio, input remapping, accessibility) can hang off
 * this composable while keeping the navigation wiring intact today.
 */
@Composable
fun OptionsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Options Screen", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Audio, controls, and accessibility toggles will live here.")
    }
}
