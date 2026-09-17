package com.omb9.glucosehero

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingWorkPolicy
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.LocalOnLogFirstContentReady
import com.omb9.glucosehero.ui.navigation.GlucoseHeroNavHost
import com.omb9.glucosehero.ui.onboarding.FirstRunGlucoseUnitDialog
import com.omb9.glucosehero.ui.theme.GlucoseHeroTheme
import com.omb9.glucosehero.ui.theme.shouldKeepSplashScreen
import com.omb9.glucosehero.util.FirstDrawProbe
import com.omb9.glucosehero.util.probeFirstDraw
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
        FirstDrawProbe.markOrigin("MainActivity.onCreate")
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

        // logFirstContentReady is set from LogScreen SideEffect when settings
        // are non-null and paging refresh is no longer Loading. That is data
        // plus composition, not draw: splash OnPreDraw skips content draws.
        val logFirstContentReady = AtomicBoolean(false)
        splashScreen.setKeepOnScreenCondition {
            if (settingsFlow.value != null) {
                FirstDrawProbe.logOnce("settings_resolved", "SETTINGS_RESOLVED")
            }
            val keep = shouldKeepSplashScreen(
                settingsFlow.value,
                logFirstContentReady.get(),
            )
            if (!keep) {
                FirstDrawProbe.logOnce(
                    "splash_dismiss",
                    "SPLASH_DISMISS keepOnScreenCondition=false",
                )
            }
            keep
        }

        lifecycleScope.launch {
            try {
                settingsRepository.seedFirstRunDefaultsIfNeeded(
                    DateFormat.is24HourFormat(this@MainActivity),
                )
            } finally {
                FirstDrawProbe.logOnce("first_run_seeded", "FIRST_RUN_SEEDED")
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
                    FirstDrawProbe.logOnce(
                        "themed_compose",
                        "FIRST_THEMED_CONTENT_COMPOSE",
                    )
                }

                // Destination ticks are handled inside GlucoseHeroNavHost: HOME
                // hosts the tabs in a pager, and Coach is omitted when Hero AI
                // is off. Popping to HOME then selecting a page avoids navigating
                // to a destination that is no longer registered.
                CompositionLocalProvider(
                    LocalOnLogFirstContentReady provides {
                        logFirstContentReady.set(true)
                        FirstDrawProbe.logOnce(
                            "log_content_ready",
                            "LOG_FIRST_CONTENT_READY",
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .probeFirstDraw(
                                "themed_draw",
                                "FIRST_THEMED_CONTENT_DRAW",
                            ),
                    ) {
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
        }

        findViewById<View>(android.R.id.content).viewTreeObserver
            .addOnDrawListener(
                ViewTreeObserver.OnDrawListener {
                    FirstDrawProbe.logOnce(
                        "content_view_draw",
                        "FIRST_CONTENT_VIEW_DRAW",
                    )
                },
            )
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
