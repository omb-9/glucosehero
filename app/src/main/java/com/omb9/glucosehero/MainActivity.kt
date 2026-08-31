package com.omb9.glucosehero

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.navigation.GlucoseHeroNavHost
import com.omb9.glucosehero.ui.navigation.Routes
import com.omb9.glucosehero.ui.theme.GlucoseHeroTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    /** Monotonic triggers for notification deep-links (cold + warm start). */
    private var heroTick by mutableIntStateOf(0)
    private var addGlucoseTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleDestination(intent)

        val settingsFlow = settingsRepository.settings
            .stateIn(lifecycleScope, SharingStarted.Eagerly, UserSettings())

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle()
            GlucoseHeroTheme(settings = settings) {
                val navController = rememberNavController()

                // Notification tap → land on the Hero tab.
                // Guard: on a fresh install (or when Hero AI is disabled) the
                // HERO destination is filtered from the bottom bar. Navigating
                // to a disabled destination would throw IllegalArgumentException
                // ("Navigation destination ... cannot be found") and crash on
                // launch when the deep-link Intent is present.
                LaunchedEffect(heroTick, settings.isHeroAiEnabled) {
                    if (heroTick > 0 && settings.isHeroAiEnabled) {
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

                GlucoseHeroNavHost(
                    navController = navController,
                    isHeroAiEnabled = settings.isHeroAiEnabled,
                    addGlucoseTick = addGlucoseTick,
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
        }
        // Consume the one-shot extra so a later rotation doesn't re-fire it.
        intent?.removeExtra(EXTRA_DESTINATION)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val DESTINATION_HERO = "hero"
        const val DESTINATION_ADD_GLUCOSE = "add_glucose"
    }
}
