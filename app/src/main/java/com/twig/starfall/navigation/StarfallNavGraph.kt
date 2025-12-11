package com.twig.starfall.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.starfall.app.game.DungeonScreen
import com.starfall.app.game.GameViewModel
import com.starfall.app.startup.OptionsScreen
import com.starfall.app.startup.OverworldScreen
import com.starfall.app.startup.StartupScreen
import com.starfall.app.startup.StartupViewModel
import com.starfall.app.startup.StatsScreen
import com.starfall.core.overworld.OverworldManager
import com.starfall.core.save.SaveManager

object StarfallRoutes {
    const val STARTUP = "startup"
    const val OVERWORLD = "overworld"
    const val RUN = "run"
    const val STATS = "stats"
    const val OPTIONS = "options"
}

@Composable
fun StarfallNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = StarfallRoutes.STARTUP) {
        composable(StarfallRoutes.STARTUP) {
            val startupViewModel: StartupViewModel = viewModel()
            val uiState by startupViewModel.uiState.collectAsState()

            StartupScreen(
                uiState = uiState,
                onNewGameClick = { navController.navigate(StarfallRoutes.OVERWORLD) },
                onContinueClick = {
                    startupViewModel.continueRunIfAvailable {
                        navController.navigate(StarfallRoutes.RUN)
                    }
                },
                onStatsClick = { navController.navigate(StarfallRoutes.STATS) },
                onOptionsClick = { navController.navigate(StarfallRoutes.OPTIONS) }
            )
        }

        composable(StarfallRoutes.OVERWORLD) {
            OverworldScreen { region ->
                val profile = SaveManager.loadMetaProfileModel()
                OverworldManager.startRunInRegion(profile, region)
                navController.navigate(StarfallRoutes.RUN)
            }
        }

        composable(StarfallRoutes.STATS) {
            StatsScreen()
        }

        composable(StarfallRoutes.OPTIONS) {
            OptionsScreen()
        }

        composable(StarfallRoutes.RUN) {
            val gameViewModel: GameViewModel = viewModel()
            val uiState by gameViewModel.uiState

            DungeonScreen(
                uiState = uiState,
                onAction = gameViewModel::onPlayerAction,
                onDismissDescendPrompt = gameViewModel::dismissDescendPrompt,
                onStartNewGame = gameViewModel::startNewGame,
                onRequestTarget = { gameViewModel.prepareTargetedItem(it.id) },
                onTileTarget = gameViewModel::onTargetSelected,
                onMutationSelected = gameViewModel::onMutationSelected
            )
        }
    }
}
