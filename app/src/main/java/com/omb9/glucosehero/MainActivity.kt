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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.navigation.GlucoseHeroNavHost
import com.omb9.glucosehero.ui.navigation.Routes
import com.omb9.glucosehero.ui.theme.GlucoseHeroTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

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
                LaunchedEffect(settings.isHeroAiEnabled) {
                    if (intent?.getStringExtra(EXTRA_DESTINATION) == DESTINATION_HERO
                        && settings.isHeroAiEnabled
                    ) {
                        navController.navigate(Routes.HERO) { launchSingleTop = true }
                    }
                }

                GlucoseHeroNavHost(
                    navController = navController,
                    isHeroAiEnabled = settings.isHeroAiEnabled,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The activity is recreated on most notification taps; for the warm
        // case the LaunchedEffect above handles the fresh intent on recompose.
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
    }
}
