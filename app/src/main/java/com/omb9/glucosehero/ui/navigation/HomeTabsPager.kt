package com.omb9.glucosehero.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import com.omb9.glucosehero.ui.chat.ChatScreen
import com.omb9.glucosehero.ui.log.LogScreen
import com.omb9.glucosehero.ui.settings.SettingsScreen
import com.omb9.glucosehero.ui.stats.StatsScreen

@Composable
internal fun HomeTabsPager(
    pagerState: PagerState,
    destinations: List<TopLevelDestination>,
    onEntryClick: (Long) -> Unit,
    onManageFoods: () -> Unit,
    onOpenDosingProfile: () -> Unit,
    onSeeAllFoodImpact: () -> Unit,
    onExpandGlucoseChart: () -> Unit,
    onOpenLogTab: () -> Unit,
    onHeroAiSettings: () -> Unit,
    onProfile: () -> Unit,
    onGlucoseTargets: () -> Unit,
    onMealLogging: () -> Unit,
    onHealthConnect: () -> Unit,
    onDataSources: () -> Unit,
    onAdvanced: () -> Unit,
    onBackup: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    onClinicalTests: () -> Unit,
    onEmergencySos: () -> Unit,
    addGlucoseTick: Int,
    addMealTick: Int,
    addBolusTick: Int,
    modifier: Modifier = Modifier,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 0,
        key = { page -> destinations[page].route },
    ) { page ->
        val destination = destinations.getOrNull(page) ?: return@HorizontalPager
        saveableStateHolder.SaveableStateProvider(destination.route) {
            when (destination.route) {
                Routes.LOG -> LogScreen(
                    onEntryClick = onEntryClick,
                    onManageFoods = onManageFoods,
                    onOpenDosingProfile = onOpenDosingProfile,
                    addGlucoseTick = addGlucoseTick,
                    addMealTick = addMealTick,
                    addBolusTick = addBolusTick,
                )
                Routes.STATS -> StatsScreen(
                    onEntryClick = onEntryClick,
                    onSeeAllFoodImpact = onSeeAllFoodImpact,
                    onOpenDosingProfile = onOpenDosingProfile,
                    onExpandGlucoseChart = onExpandGlucoseChart,
                )
                Routes.HERO -> ChatScreen(
                    onOpenLog = onOpenLogTab,
                    onHeroAiSettings = onHeroAiSettings,
                )
                Routes.SETTINGS -> SettingsScreen(
                    onManageFoods = onManageFoods,
                    onHeroAiSettings = onHeroAiSettings,
                    onProfile = onProfile,
                    onGlucoseTargets = onGlucoseTargets,
                    onMealLogging = onMealLogging,
                    onHealthConnect = onHealthConnect,
                    onDataSources = onDataSources,
                    onAdvanced = onAdvanced,
                    onBackup = onBackup,
                    onAppearance = onAppearance,
                    onAbout = onAbout,
                    onClinicalTests = onClinicalTests,
                    onEmergencySos = onEmergencySos,
                )
            }
        }
    }
}
