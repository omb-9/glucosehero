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
import com.omb9.glucosehero.clinical.ClinicalTestScreen
import com.omb9.glucosehero.crisis.EmergencySosSettingsScreen
import com.omb9.glucosehero.ui.chat.ChatScreen
import com.omb9.glucosehero.ui.entrydetail.EntryDetailScreen
import com.omb9.glucosehero.ui.foods.FoodLibraryScreen
import com.omb9.glucosehero.ui.insights.FoodImpactScreen
import com.omb9.glucosehero.ui.log.LogScreen
import com.omb9.glucosehero.ui.settings.AiSettingsScreen
import com.omb9.glucosehero.ui.settings.AppearanceSettingsScreen
import com.omb9.glucosehero.ui.settings.BackupSettingsScreen
import com.omb9.glucosehero.ui.settings.DataSourcesSettingsScreen
import com.omb9.glucosehero.ui.settings.GlucoseTargetsSettingsScreen
import com.omb9.glucosehero.ui.settings.HealthConnectSettingsScreen
import com.omb9.glucosehero.ui.settings.MealLoggingSettingsScreen
import com.omb9.glucosehero.ui.settings.NightscoutSettingsScreen
import com.omb9.glucosehero.ui.settings.ProfileSettingsScreen
import com.omb9.glucosehero.ui.settings.SettingsScreen
import com.omb9.glucosehero.ui.settings.XdripSettingsScreen
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
    const val FOOD_LIBRARY = "food_library"
    const val FOOD_IMPACT = "food_impact"
    const val AI_SETTINGS = "ai_settings"
    const val PROFILE_SETTINGS = "profile_settings"
    const val GLUCOSE_TARGETS_SETTINGS = "glucose_targets_settings"
    const val MEAL_LOGGING_SETTINGS = "meal_logging_settings"
    const val HEALTH_CONNECT_SETTINGS = "health_connect_settings"
    const val DATA_SOURCES = "data_sources"
    const val NIGHTSCOUT_SETTINGS = "nightscout_settings"
    const val XDRIP_SETTINGS = "xdrip_settings"
    const val BACKUP_SETTINGS = "backup_settings"
    const val APPEARANCE_SETTINGS = "appearance_settings"
    const val CLINICAL_TESTS = "clinical_tests"
    const val EMERGENCY_SOS = "emergency_sos"

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
    TopLevelDestination(Routes.HERO, Icons.Filled.AutoAwesome, "Coach"),
    TopLevelDestination(Routes.SETTINGS, Icons.Filled.Settings, "Settings"),
)

@Composable
fun GlucoseHeroNavHost(
    navController: NavHostController,
    isHeroAiEnabled: Boolean,
    addGlucoseTick: Int = 0,
    addMealTick: Int = 0,
    addBolusTick: Int = 0,
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
                    onManageFoods = { navController.navigate(Routes.FOOD_LIBRARY) },
                    onOpenDosingProfile = { navController.navigate(Routes.GLUCOSE_TARGETS_SETTINGS) },
                    addGlucoseTick = addGlucoseTick,
                    addMealTick = addMealTick,
                    addBolusTick = addBolusTick,
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    onEntryClick = { id -> navController.navigate(Routes.entryDetail(id)) },
                    onSeeAllFoodImpact = { navController.navigate(Routes.FOOD_IMPACT) },
                    onOpenDosingProfile = { navController.navigate(Routes.GLUCOSE_TARGETS_SETTINGS) },
                )
            }
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
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onManageFoods = { navController.navigate(Routes.FOOD_LIBRARY) },
                    onHeroAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                    onProfile = { navController.navigate(Routes.PROFILE_SETTINGS) },
                    onGlucoseTargets = { navController.navigate(Routes.GLUCOSE_TARGETS_SETTINGS) },
                    onMealLogging = { navController.navigate(Routes.MEAL_LOGGING_SETTINGS) },
                    onHealthConnect = { navController.navigate(Routes.HEALTH_CONNECT_SETTINGS) },
                    onDataSources = { navController.navigate(Routes.DATA_SOURCES) },
                    onBackup = { navController.navigate(Routes.BACKUP_SETTINGS) },
                    onAppearance = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
                    onClinicalTests = { navController.navigate(Routes.CLINICAL_TESTS) },
                    onEmergencySos = { navController.navigate(Routes.EMERGENCY_SOS) },
                )
            }
            composable(Routes.AI_SETTINGS) {
                AiSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PROFILE_SETTINGS) {
                ProfileSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GLUCOSE_TARGETS_SETTINGS) {
                GlucoseTargetsSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.MEAL_LOGGING_SETTINGS) {
                MealLoggingSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HEALTH_CONNECT_SETTINGS) {
                HealthConnectSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DATA_SOURCES) {
                DataSourcesSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onXdripSetup = { navController.navigate(Routes.XDRIP_SETTINGS) },
                    onNightscoutSetup = { navController.navigate(Routes.NIGHTSCOUT_SETTINGS) },
                    onHealthConnect = { navController.navigate(Routes.HEALTH_CONNECT_SETTINGS) },
                )
            }
            composable(Routes.NIGHTSCOUT_SETTINGS) {
                NightscoutSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.XDRIP_SETTINGS) {
                XdripSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.BACKUP_SETTINGS) {
                BackupSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.APPEARANCE_SETTINGS) {
                AppearanceSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CLINICAL_TESTS) {
                ClinicalTestScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EMERGENCY_SOS) {
                EmergencySosSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.FOOD_LIBRARY) {
                FoodLibraryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.FOOD_IMPACT) {
                FoodImpactScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.ENTRY_DETAIL,
                arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
            ) {
                EntryDetailScreen(
                    onDone = { navController.popBackStack() },
                    onOpenDosingProfile = { navController.navigate(Routes.GLUCOSE_TARGETS_SETTINGS) },
                )
            }
        }
    }
}
