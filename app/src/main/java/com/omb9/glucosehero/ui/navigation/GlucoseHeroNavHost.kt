package com.omb9.glucosehero.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.omb9.glucosehero.ui.chat.ChatScreen
import com.omb9.glucosehero.ui.entrydetail.EntryDetailScreen
import com.omb9.glucosehero.ui.log.LogScreen
import com.omb9.glucosehero.ui.settings.SettingsScreen
import com.omb9.glucosehero.ui.stats.StatsScreen

/**
 * State-driven navigation per spec: routes carry primitive IDs only
 * (entry/{entryId}); destination ViewModels fetch the fresh entity from Room
 * via StateFlow. No Parcelables, no serialized objects in routes.
 */
object Routes {
    const val LOG = "log"
    const val STATS = "stats"
    const val HERO = "hero"
    const val SETTINGS = "settings"
    const val ENTRY_DETAIL = "entry/{entryId}"

    fun entryDetail(entryId: Long) = "entry/$entryId"
}

private data class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.LOG, Icons.AutoMirrored.Filled.List, "Log"),
    TopLevelDestination(Routes.STATS, Icons.AutoMirrored.Filled.ShowChart, "Stats"),
    TopLevelDestination(Routes.HERO, Icons.Filled.AutoAwesome, "Hero"),
    TopLevelDestination(Routes.SETTINGS, Icons.Filled.Settings, "Settings"),
)

@Composable
fun GlucoseHeroNavHost(
    navController: NavHostController,
    isHeroAiEnabled: Boolean,
    addGlucoseTick: Int = 0,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val destinations = remember(isHeroAiEnabled) {
        topLevelDestinations.filter { it.route != Routes.HERO || isHeroAiEnabled }
    }
    // When the HERO destination is disabled the composable is not registered
    // in the NavGraph. Showing the bottom bar for a route that has no
    // matching NavHost entry would leave the scaffold empty and, worse, any
    // pending navigation to "hero" (e.g. a cold-start notification Intent
    // that was not cleared) would throw IllegalArgumentException at
    // NavController.navigate and crash the app on launch — even on a fresh
    // install. So the bottom bar is only shown for known visible destinations.
    val showBottomBar = destinations.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOG,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOG) {
                LogScreen(
                    onEntryClick = { id -> navController.navigate(Routes.entryDetail(id)) },
                    addGlucoseTick = addGlucoseTick,
                )
            }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.HERO) {
                ChatScreen(
                    onOpenLog = {
                        navController.navigate(Routes.LOG) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                route = Routes.ENTRY_DETAIL,
                arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
            ) {
                EntryDetailScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
