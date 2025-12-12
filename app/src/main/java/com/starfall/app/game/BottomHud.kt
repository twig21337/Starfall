package com.starfall.app.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import kotlin.math.max

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
    onTabSelected: (BottomHudTab) -> Unit,
    onInventoryItemTapped: (InventoryItemUiModel, Int, Int, Int) -> Unit,
    onInventoryItemLongPressed: (InventoryItemUiModel) -> Unit,
    onReturnToMainMenu: () -> Unit,
    onOpenOverworld: () -> Unit,
    onStartNewRun: () -> Unit,
    onSaveGame: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            uiState.selectedTab?.let { selectedTab ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    when (selectedTab) {
                        BottomHudTab.STATS -> StatsPanel(uiState.statsPanel)
                        BottomHudTab.MUTATIONS -> MutationsPanel(uiState.mutationsPanel)
                        BottomHudTab.XP -> XpPanel(uiState.xpPanel)
                        BottomHudTab.MAP -> MapPanel(uiState.mapPanel)
                        BottomHudTab.INVENTORY -> InventoryPanel(
                            state = uiState.inventoryPanel,
                            onItemTapped = onInventoryItemTapped,
                            onItemLongPressed = onInventoryItemLongPressed
                        )
                        BottomHudTab.MENU -> MenuPanel(
                            state = uiState.menuPanel,
                            onReturnToMainMenu = onReturnToMainMenu,
                            onOpenOverworld = onOpenOverworld,
                            onStartNewRun = onStartNewRun,
                            onSaveGame = onSaveGame
                        )
                    }
                }

                Divider()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BottomHudTab.values()
                        .filter { it != BottomHudTab.MENU }
                        .forEach { tab ->
                            HudTabButton(tab, selected = tab == uiState.selectedTab) {
                                onTabSelected(tab)
                            }
                        }
                }

                HudTabButton(
                    tab = BottomHudTab.MENU,
                    selected = uiState.selectedTab == BottomHudTab.MENU
                ) {
                    onTabSelected(BottomHudTab.MENU)
                }
            }
        }
    }
}

@Composable
private fun HudTabButton(tab: BottomHudTab, selected: Boolean, onClick: () -> Unit) {
    val modifier = if (selected) {
        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    } else {
        Modifier
    }
    TextButton(
        onClick = onClick,
        modifier = modifier.border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = MaterialTheme.shapes.extraSmall
        )
    ) {
        Text(
            text = tab.name.lowercase().replaceFirstChar { it.titlecase() },
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun StatsPanel(state: StatsPanelState) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "Attack: ${state.attack}")
        Text(text = "Defense: ${state.defense}")
        Text(text = "Armor: ${state.armor}/${state.maxArmor}")
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

@Composable
fun InventoryPanel(
    state: InventoryPanelState,
    onItemTapped: (InventoryItemUiModel, Int, Int, Int) -> Unit,
    onItemLongPressed: (InventoryItemUiModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Inventory",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (state.items.isEmpty()) {
            Text("Your pack is empty.", style = MaterialTheme.typography.bodySmall)
        } else {
            val slotCount = max(state.maxSlots, state.items.size).coerceAtLeast(1)
            val slots: List<InventoryItemUiModel?> =
                (0 until slotCount).map { index -> state.items.getOrNull(index) }

            val configuration = LocalConfiguration.current
            val maxRows = 3
            val columns = max(4, ceil(slotCount / maxRows.toDouble()).toInt())
            val rows = ceil(slotCount / columns.toDouble()).toInt()

            val screenWidth = configuration.screenWidthDp.dp
            val outerPadding = 24.dp
            val inventoryPadding = 12.dp
            val availableWidth = screenWidth - outerPadding - inventoryPadding
            val spacing = 8.dp

            val totalSpacing = spacing * (columns - 1).toFloat()
            val tileSizeFromWidth = (availableWidth - totalSpacing) / columns.toFloat()
            val tileSize = tileSizeFromWidth.coerceIn(32.dp, 58.dp)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.chunked(columns).forEachIndexed { rowIndex, rowItems ->
                    if (rowIndex < rows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEachIndexed { colIndex, item ->
                                val slotIndex = item?.slotIndex ?: (rowIndex * columns + colIndex)
                                if (item != null) {
                                    InventoryTile(
                                        item = item,
                                        tileSize = tileSize,
                                        onClick = { onItemTapped(item, rowIndex, colIndex, slotIndex) },
                                        onLongClick = { onItemLongPressed(item) }
                                    )
                                } else {
                                    EmptyInventoryTile(tileSize)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuPanel(
    state: MenuPanelState,
    onReturnToMainMenu: () -> Unit,
    onOpenOverworld: () -> Unit,
    onStartNewRun: () -> Unit,
    onSaveGame: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Game Menu",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Jump to other screens or manage your run.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = onReturnToMainMenu, modifier = Modifier.fillMaxWidth()) {
            Text("Return to Main Menu", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = onOpenOverworld, modifier = Modifier.fillMaxWidth()) {
            Text("Overworld Map", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = onStartNewRun, modifier = Modifier.fillMaxWidth()) {
            Text("Start New Run", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onSaveGame,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSave
        ) {
            Text("Save Current Game", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun InventoryTile(
    item: InventoryItemUiModel,
    tileSize: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val borderColor = if (item.isEquipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .size(tileSize)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .border(BorderStroke(1.dp, borderColor), MaterialTheme.shapes.small)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.icon,
                    style = MaterialTheme.typography.titleMedium
                )
                if (item.quantity > 1) {
                    Text(
                        text = "x ${item.quantity}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (item.isEquipped) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "E",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyInventoryTile(tileSize: Dp) {
    Column(
        modifier = Modifier
            .size(tileSize)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                MaterialTheme.shapes.small
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "–",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
