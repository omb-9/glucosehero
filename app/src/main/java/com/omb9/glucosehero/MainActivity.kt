package com.omb9.glucosehero

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingWorkPolicy
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.navigation.GlucoseHeroNavHost
import com.omb9.glucosehero.ui.navigation.Routes
import com.omb9.glucosehero.ui.theme.GlucoseHeroTheme
import com.omb9.glucosehero.ui.theme.shouldKeepSplashScreen
import com.omb9.glucosehero.work.HealthConnectSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** Monotonic triggers for notification deep-links (cold + warm start). */
    private var heroTick by mutableIntStateOf(0)
    private var addGlucoseTick by mutableIntStateOf(0)
    private var addMealTick by mutableIntStateOf(0)
    private var addBolusTick by mutableIntStateOf(0)

    override fun onStart() {
        super.onStart()
        HealthConnectSyncWorker.enqueueExpedited(this, ExistingWorkPolicy.KEEP)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDestination(intent)

        // Null seed is the "not loaded" sentinel. Seeding UserSettings() would
        // paint LIGHT (the data-class default) before DataStore resolves, which
        // flashes white for AMOLED users. DataStore's resilient read fail-opens
        // to defaults, so this becomes non-null even when the file is unreadable.
        val settingsFlow = settingsRepository.settings
            .stateIn(lifecycleScope, SharingStarted.Eagerly, null)

        val firstThemedFrameDrawn = AtomicBoolean(false)
        splashScreen.setKeepOnScreenCondition {
            shouldKeepSplashScreen(settingsFlow.value) || !firstThemedFrameDrawn.get()
        }

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(
                minActiveState = Lifecycle.State.CREATED,
            )
            val loadedSettings = settings ?: return@setContent
            GlucoseHeroTheme(settings = loadedSettings) {
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    firstThemedFrameDrawn.set(true)
                }

                // Notification tap → land on the Hero tab.
                // Guard: on a fresh install (or when Hero AI is disabled) the
                // HERO destination is filtered from the bottom bar. Navigating
                // to a disabled destination would throw IllegalArgumentException
                // ("Navigation destination ... cannot be found") and crash on
                // launch when the deep-link Intent is present.
                LaunchedEffect(heroTick, loadedSettings.isHeroAiEnabled) {
                    if (heroTick > 0 && loadedSettings.isHeroAiEnabled) {
                        navController.navigate(Routes.HERO) { launchSingleTop = true }
                    }
                }

                // Notification tap → open Log and let LogScreen open the
                // AddEntrySheet with the Glucose tab pre-selected.
                LaunchedEffect(addGlucoseTick) {
                    if (addGlucoseTick > 0) {
                        navController.navigate(Routes.LOG) { launchSingleTop = true }
                    }
                }

                // Widget quick action → open Log and let LogScreen open the
                // AddEntrySheet with the Meal tab pre-selected.
                LaunchedEffect(addMealTick) {
                    if (addMealTick > 0) {
                        navController.navigate(Routes.LOG) { launchSingleTop = true }
                    }
                }

                // Widget quick action → open Log and let LogScreen open the
                // AddEntrySheet with the Insulin (bolus) tab pre-selected.
                LaunchedEffect(addBolusTick) {
                    if (addBolusTick > 0) {
                        navController.navigate(Routes.LOG) { launchSingleTop = true }
                    }
                }

                GlucoseHeroNavHost(
                    navController = navController,
                    isHeroAiEnabled = loadedSettings.isHeroAiEnabled,
                    addGlucoseTick = addGlucoseTick,
                    addMealTick = addMealTick,
                    addBolusTick = addBolusTick,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDestination(intent)
    }

    private fun handleDestination(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_DESTINATION)) {
            DESTINATION_HERO -> heroTick++
            DESTINATION_ADD_GLUCOSE -> addGlucoseTick++
            DESTINATION_ADD_MEAL -> addMealTick++
            DESTINATION_ADD_BOLUS -> addBolusTick++
        }
        // Consume the one-shot extra so a later rotation doesn't re-fire it.
        intent?.removeExtra(EXTRA_DESTINATION)
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val DESTINATION_HERO = "hero"
        const val DESTINATION_ADD_GLUCOSE = "add_glucose"
        const val DESTINATION_ADD_MEAL = "add_meal"
        const val DESTINATION_ADD_BOLUS = "add_bolus"
    }
}
