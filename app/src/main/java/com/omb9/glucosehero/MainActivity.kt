package com.omb9.glucosehero

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
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
import com.omb9.glucosehero.ui.onboarding.FirstRunGlucoseUnitDialog
import com.omb9.glucosehero.ui.theme.GlucoseHeroTheme
import com.omb9.glucosehero.ui.theme.shouldKeepSplashScreen
import com.omb9.glucosehero.work.HealthConnectSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        val firstRunSeeded = AtomicBoolean(false)
        splashScreen.setKeepOnScreenCondition {
            shouldKeepSplashScreen(settingsFlow.value) ||
                !firstThemedFrameDrawn.get() ||
                !firstRunSeeded.get()
        }

        lifecycleScope.launch {
            try {
                settingsRepository.seedFirstRunDefaultsIfNeeded(
                    DateFormat.is24HourFormat(this@MainActivity),
                )
            } finally {
                firstRunSeeded.set(true)
            }
        }

        val needsUnitChoiceFlow = settingsRepository.needsGlucoseUnitChoice
            .stateIn(lifecycleScope, SharingStarted.Eagerly, false)

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(
                minActiveState = Lifecycle.State.CREATED,
            )
            val needsUnitChoice by needsUnitChoiceFlow.collectAsStateWithLifecycle(
                minActiveState = Lifecycle.State.CREATED,
            )
            val loadedSettings = settings ?: return@setContent
            GlucoseHeroTheme(settings = loadedSettings) {
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    firstThemedFrameDrawn.set(true)
                }

                // Destination ticks are handled inside GlucoseHeroNavHost: HOME
                // hosts the tabs in a pager, and Coach is omitted when Hero AI
                // is off. Popping to HOME then selecting a page avoids navigating
                // to a destination that is no longer registered.
                GlucoseHeroNavHost(
                    navController = navController,
                    isHeroAiEnabled = loadedSettings.isHeroAiEnabled,
                    heroTick = heroTick,
                    addGlucoseTick = addGlucoseTick,
                    addMealTick = addMealTick,
                    addBolusTick = addBolusTick,
                )

                if (needsUnitChoice) {
                    FirstRunGlucoseUnitDialog(
                        onConfirm = { unit ->
                            lifecycleScope.launch {
                                settingsRepository.completeFirstRun(unit)
                            }
                        },
                    )
                }
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
