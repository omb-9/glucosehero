package com.omb9.glucosehero.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.omb9.glucosehero.clinical.ClinicalTestScreen
import com.omb9.glucosehero.crisis.EmergencySosSettingsScreen
import com.omb9.glucosehero.ui.entrydetail.EntryDetailScreen
import com.omb9.glucosehero.ui.foods.FoodLibraryScreen
import com.omb9.glucosehero.ui.insights.FoodImpactScreen
import com.omb9.glucosehero.ui.settings.AboutSettingsScreen
import com.omb9.glucosehero.ui.settings.AdvancedSettingsScreen
import com.omb9.glucosehero.ui.settings.AiSettingsScreen
import com.omb9.glucosehero.ui.settings.AppearanceSettingsScreen
import com.omb9.glucosehero.ui.settings.BackupSettingsScreen
import com.omb9.glucosehero.ui.settings.ByokSettingsScreen
import com.omb9.glucosehero.ui.settings.DataSourcesSettingsScreen
import com.omb9.glucosehero.ui.settings.GlucoseTargetsSettingsScreen
import com.omb9.glucosehero.ui.settings.HealthConnectSettingsScreen
import com.omb9.glucosehero.ui.settings.MealLoggingSettingsScreen
import com.omb9.glucosehero.ui.settings.NightscoutSettingsScreen
import com.omb9.glucosehero.ui.settings.ProfileSettingsScreen
import com.omb9.glucosehero.ui.settings.XdripSettingsScreen
import com.omb9.glucosehero.ui.stats.GlucoseChartFullscreenScreen
import com.omb9.glucosehero.ui.stats.StatsViewModel
import kotlin.math.roundToInt

/**
 * State-driven navigation per spec: routes carry primitive IDs only
 * (entry/{entryId}); destination ViewModels fetch the fresh entity from Room
 * via StateFlow. No Parcelables, no serialized objects in routes.
 */
object Routes {
    const val HOME = "home"
    const val LOG = "log"
    const val STATS = "stats"
    const val GLUCOSE_CHART = "glucose_chart"
    const val HERO = "hero"
    const val SETTINGS = "settings"
    const val ENTRY_DETAIL = "entry/{entryId}"
    const val FOOD_LIBRARY = "food_library"
    const val FOOD_IMPACT = "food_impact"
    const val AI_SETTINGS = "ai_settings"
    const val BYOK_SETTINGS = "byok_settings"
    const val PROFILE_SETTINGS = "profile_settings"
    const val GLUCOSE_TARGETS_SETTINGS = "glucose_targets_settings"
    const val MEAL_LOGGING_SETTINGS = "meal_logging_settings"
    const val HEALTH_CONNECT_SETTINGS = "health_connect_settings"
    const val DATA_SOURCES = "data_sources"
    const val NIGHTSCOUT_SETTINGS = "nightscout_settings"
    const val XDRIP_SETTINGS = "xdrip_settings"
    const val BACKUP_SETTINGS = "backup_settings"
    const val APPEARANCE_SETTINGS = "appearance_settings"
    const val ADVANCED_SETTINGS = "advanced_settings"
    const val ABOUT_SETTINGS = "about_settings"
    const val CLINICAL_TESTS = "clinical_tests"
    const val EMERGENCY_SOS = "emergency_sos"

    fun entryDetail(entryId: Long) = "entry/$entryId"
}

internal data class TopLevelDestination(
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

private val topLevelRoutes = setOf(Routes.HOME)

// Material 3 emphasized easing (cubic-bezier(0.2, 0, 0, 1)). Shared-axis spatial
// motion is 300ms with a 30% container slide, not a full-width page wipe.
private const val SharedAxisDurationMs = 300
private const val SharedAxisFadeOutMs = 90
private const val SharedAxisFadeInMs = 210
private const val SharedAxisSlideFraction = 0.30f
private val SharedAxisEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
private const val TabFadeDurationMs = 250

private val bottomBarEnter = slideInVertically(
    animationSpec = tween(durationMillis = SharedAxisDurationMs, easing = SharedAxisEasing),
    initialOffsetY = { it },
) + expandVertically(
    animationSpec = tween(durationMillis = SharedAxisDurationMs, easing = SharedAxisEasing),
)

private val bottomBarExit = slideOutVertically(
    animationSpec = tween(durationMillis = SharedAxisDurationMs, easing = SharedAxisEasing),
    targetOffsetY = { it },
) + shrinkVertically(
    animationSpec = tween(durationMillis = SharedAxisDurationMs, easing = SharedAxisEasing),
)

@Composable
fun GlucoseHeroNavHost(
    navController: NavHostController,
    isHeroAiEnabled: Boolean,
    heroTick: Int = 0,
    addGlucoseTick: Int = 0,
    addMealTick: Int = 0,
    addBolusTick: Int = 0,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val destinations = remember(isHeroAiEnabled) {
        topLevelDestinations.filter { it.route != Routes.HERO || isHeroAiEnabled }
    }
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val pagerScope = rememberCoroutineScope()
    var selectedTabRoute by remember { mutableStateOf(Routes.LOG) }
    LaunchedEffect(pagerState.currentPage, destinations) {
        destinations.getOrNull(pagerState.currentPage)?.route?.let { selectedTabRoute = it }
    }
    LaunchedEffect(destinations) {
        val index = destinations.indexOfFirst { it.route == selectedTabRoute }
        val target = if (index >= 0) {
            index
        } else {
            destinations.indexOfFirst { it.route == Routes.STATS }.coerceAtLeast(0)
        }
        if (target != pagerState.currentPage && target < destinations.size) {
            pagerState.scrollToPage(target)
        }
    }
    // Bottom bar only on HOME. Coach is omitted from both the pager and the bar
    // when Hero AI is off, so page indices and destinations stay aligned.
    val showBottomBar = currentRoute == Routes.HOME

    LaunchedEffect(heroTick, isHeroAiEnabled) {
        if (heroTick > 0 && isHeroAiEnabled) {
            navController.popBackStack(Routes.HOME, inclusive = false)
            val index = destinations.indexOfFirst { it.route == Routes.HERO }
            if (index >= 0) pagerState.animateScrollToPage(index)
        }
    }
    LaunchedEffect(addGlucoseTick, addMealTick, addBolusTick) {
        if (addGlucoseTick > 0 || addMealTick > 0 || addBolusTick > 0) {
            navController.popBackStack(Routes.HOME, inclusive = false)
            val index = destinations.indexOfFirst { it.route == Routes.LOG }
            if (index >= 0) pagerState.animateScrollToPage(index)
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = bottomBarEnter,
                exit = bottomBarExit,
            ) {
                NavigationBar {
                    destinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                pagerScope.launch {
                                    pagerState.animateScrollToPage(index)
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
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { navEnterTransition() },
            exitTransition = { navExitTransition() },
            popEnterTransition = { navPopEnterTransition() },
            popExitTransition = { navPopExitTransition() },
        ) {
            composable(Routes.HOME) {
                CompositionLocalProvider(LocalHomePagerState provides pagerState) {
                    HomeTabsPager(
                        pagerState = pagerState,
                        destinations = destinations,
                        onEntryClick = { id -> navController.navigate(Routes.entryDetail(id)) },
                        onManageFoods = { navController.navigate(Routes.FOOD_LIBRARY) },
                        onOpenDosingProfile = { navController.navigate(Routes.GLUCOSE_TARGETS_SETTINGS) },
                        onSeeAllFoodImpact = { navController.navigate(Routes.FOOD_IMPACT) },
                        onExpandGlucoseChart = { navController.navigate(Routes.GLUCOSE_CHART) },
                        onOpenLogTab = {
                            val index = destinations.indexOfFirst { it.route == Routes.LOG }
                            if (index >= 0) {
                                pagerScope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        onHeroAiSettings = { navController.navigate(Routes.AI_SETTINGS) },
                        onProfile = { navController.navigate(Routes.PROFILE_SETTINGS) },
                        onGlucoseTargets = { navController.navigate(Routes.GLUCOSE_TARGETS_SETTINGS) },
                        onMealLogging = { navController.navigate(Routes.MEAL_LOGGING_SETTINGS) },
                        onHealthConnect = { navController.navigate(Routes.HEALTH_CONNECT_SETTINGS) },
                        onDataSources = { navController.navigate(Routes.DATA_SOURCES) },
                        onAdvanced = { navController.navigate(Routes.ADVANCED_SETTINGS) },
                        onBackup = { navController.navigate(Routes.BACKUP_SETTINGS) },
                        onAppearance = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
                        onAbout = { navController.navigate(Routes.ABOUT_SETTINGS) },
                        onClinicalTests = { navController.navigate(Routes.CLINICAL_TESTS) },
                        onEmergencySos = { navController.navigate(Routes.EMERGENCY_SOS) },
                        addGlucoseTick = addGlucoseTick,
                        addMealTick = addMealTick,
                        addBolusTick = addBolusTick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            composable(Routes.GLUCOSE_CHART) { chartEntry ->
                val homeEntry = remember(chartEntry) {
                    runCatching { navController.getBackStackEntry(Routes.HOME) }
                        .getOrDefault(chartEntry)
                }
                GlucoseChartFullscreenScreen(
                    onBack = { navController.popBackStack() },
                    onEntryClick = { id -> navController.navigate(Routes.entryDetail(id)) },
                    viewModel = hiltViewModel<StatsViewModel>(homeEntry),
                )
            }
            composable(Routes.AI_SETTINGS) {
                AiSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onByokSettings = { navController.navigate(Routes.BYOK_SETTINGS) },
                )
            }
            composable(Routes.BYOK_SETTINGS) {
                ByokSettingsScreen(onBack = { navController.popBackStack() })
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
            composable(Routes.ADVANCED_SETTINGS) {
                AdvancedSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT_SETTINGS) {
                AboutSettingsScreen(onBack = { navController.popBackStack() })
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
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isLateralTabSwitch(): Boolean {
    val from = initialState.destination.route
    val to = targetState.destination.route
    return from in topLevelRoutes && to in topLevelRoutes
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnterTransition(): EnterTransition =
    if (isLateralTabSwitch()) tabFadeIn() else sharedAxisXEnter(forward = true)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navExitTransition(): ExitTransition =
    if (isLateralTabSwitch()) tabFadeOut() else sharedAxisXExit(forward = true)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopEnterTransition(): EnterTransition =
    if (isLateralTabSwitch()) tabFadeIn() else sharedAxisXEnter(forward = false)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopExitTransition(): ExitTransition =
    if (isLateralTabSwitch()) tabFadeOut() else sharedAxisXExit(forward = false)

private fun tabFadeIn(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = TabFadeDurationMs, easing = SharedAxisEasing))

private fun tabFadeOut(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = TabFadeDurationMs, easing = SharedAxisEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisXEnter(
    forward: Boolean,
): EnterTransition {
    val towards = if (forward) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
    return slideIntoContainer(
        towards = towards,
        animationSpec = tween(durationMillis = SharedAxisDurationMs, easing = SharedAxisEasing),
        initialOffset = { fullDistance -> (fullDistance * SharedAxisSlideFraction).roundToInt() },
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = SharedAxisFadeInMs,
            delayMillis = SharedAxisFadeOutMs,
            easing = LinearOutSlowInEasing,
        ),
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisXExit(
    forward: Boolean,
): ExitTransition {
    val towards = if (forward) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
    return slideOutOfContainer(
        towards = towards,
        animationSpec = tween(durationMillis = SharedAxisDurationMs, easing = SharedAxisEasing),
        targetOffset = { fullDistance -> (fullDistance * SharedAxisSlideFraction).roundToInt() },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = SharedAxisFadeOutMs,
            easing = FastOutLinearInEasing,
        ),
    )
}
