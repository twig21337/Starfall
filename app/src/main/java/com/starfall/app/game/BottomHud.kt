package com.starfall.app.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Bottom HUD with tabs for the main dungeon screen.
 *
 * Adding a new tab requires:
 * 1. Extending [BottomHudTab].
 * 2. Exposing the desired state on [HudUiState].
 * 3. Rendering a new panel in the when-branch below.
 * Panel-specific contents live in the panel composables at the bottom of this file.
 */
@Composable
fun BottomHud(
    uiState: HudUiState,
    onTabSelected: (BottomHudTab) -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                when (uiState.selectedTab) {
                    BottomHudTab.STATS -> StatsPanel(uiState.statsPanel)
                    BottomHudTab.MUTATIONS -> MutationsPanel(uiState.mutationsPanel)
                    BottomHudTab.XP -> XpPanel(uiState.xpPanel)
                    BottomHudTab.MAP -> MapPanel(uiState.mapPanel)
                }
            }

            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HP ${uiState.currentHp}/${uiState.maxHp}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress =
                        if (uiState.maxHp > 0) uiState.currentHp / uiState.maxHp.toFloat() else 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Floor ${uiState.currentFloor} / ${uiState.maxFloor}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BottomHudTab.values().forEach { tab ->
                        val selected = tab == uiState.selectedTab
                        val colors = if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        } else {
                            Modifier
                        }
                        TextButton(
                            onClick = { onTabSelected(tab) },
                            modifier = colors
                        ) {
                            Text(
                                text = tab.name.lowercase().replaceFirstChar { it.titlecase() },
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsPanel(state: StatsPanelState) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Attack: ${state.attack}")
        Text(text = "Defense: ${state.defense}")
        Text(text = "Crit: ${state.critChance}%")
        Text(text = "Dodge: ${state.dodgeChance}%")
        if (state.statusEffects.isNotEmpty()) {
            Text(text = "Status Effects:", fontWeight = FontWeight.SemiBold)
            state.statusEffects.forEach { effect -> Text(text = effect) }
        } else {
            Text(text = "No active status effects")
        }
    }
}

@Composable
fun MutationsPanel(state: MutationsPanelState) {
    if (state.mutations.isEmpty()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("No mutations acquired yet.")
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.mutations) { mutation ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = mutation.name, fontWeight = FontWeight.SemiBold)
                    Text(text = "Tier ${mutation.tier}", style = MaterialTheme.typography.labelMedium)
                    Text(text = mutation.shortDescription)
                }
                Divider(modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun XpPanel(state: XpPanelState) {
    val maxXp = (state.xp + state.xpToNext).coerceAtLeast(1)
    val progress = if (maxXp > 0) state.xp / maxXp.toFloat() else 0f
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "Level ${state.level}", fontWeight = FontWeight.SemiBold)
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        Text(text = "XP: ${state.xp} / ${state.xp + state.xpToNext}")
        Text(text = "To next level: ${state.xpToNext}")
    }
}

@Composable
fun MapPanel(state: MapPanelState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Floor ${state.floorNumber} / ${state.maxFloor}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Start
        )
        Text(text = "Explored: ${state.discoveredPercentage}%")
    }
}
