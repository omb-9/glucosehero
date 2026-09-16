package com.omb9.glucosehero.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.BuildConfig
import com.omb9.glucosehero.R
import kotlinx.coroutines.launch

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageFoods: () -> Unit,
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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val connectedDataSourceCount by viewModel.connectedDataSourceCount.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setNotificationsEnabled(true)
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Notifications are off. Enable them in system settings to receive alerts.",
                    actionLabel = "Settings",
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openNotificationSettings(context)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineMedium, maxLines = 2) },
                expandedHeight = settingsTopBarExpandedHeight(),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader("You")
            NavigationRow(
                title = "Profile",
                subtitle = SettingsOverviewCopy.profileSubtitle(profile.name),
                leadingIcon = Icons.Filled.Person,
                onClick = onProfile,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Glucose & Targets",
                subtitle = SettingsOverviewCopy.glucoseTargetsSubtitle(
                    unit = settings.unit,
                    lowMgdl = settings.targetLowMgdl,
                    highMgdl = settings.targetHighMgdl,
                ),
                leadingIcon = Icons.Filled.Bloodtype,
                onClick = onGlucoseTargets,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Meal Logging",
                leadingIcon = Icons.Filled.Restaurant,
                onClick = onMealLogging,
            )
            Spacer(Modifier.height(12.dp))
            SettingsToggleRow(
                label = "Allow notifications",
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        } else {
                            viewModel.setNotificationsEnabled(true)
                        }
                    } else {
                        viewModel.setNotificationsEnabled(false)
                    }
                },
                description = "Receive Hero AI insights, post-meal reminders, and exercise alerts.",
            )

            SectionHeader("Hero AI")
            NavigationRow(
                title = "Hero AI",
                subtitle = SettingsOverviewCopy.heroAiSubtitle(settings.isHeroAiEnabled),
                leadingIcon = Icons.Filled.AutoAwesome,
                onClick = onHeroAiSettings,
            )

            SectionHeader("Integrations")
            NavigationRow(
                title = "Health Connect",
                leadingIcon = Icons.Filled.HealthAndSafety,
                onClick = onHealthConnect,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = stringResource(R.string.data_sources_title),
                subtitle = SettingsOverviewCopy.dataSourcesSubtitle(connectedDataSourceCount),
                leadingIcon = Icons.Filled.Sensors,
                onClick = onDataSources,
            )
            Spacer(Modifier.height(12.dp))
            WearOsSettingsSection()
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Advanced",
                subtitle = "Local webhook",
                leadingIcon = Icons.Filled.Tune,
                onClick = onAdvanced,
            )

            SectionHeader("Clinical tools")
            NavigationRow(
                title = "Basal and carb-ratio tests",
                leadingIcon = Icons.Filled.Science,
                onClick = onClinicalTests,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Emergency hypo SOS",
                leadingIcon = Icons.Filled.Warning,
                onClick = onEmergencySos,
            )

            SectionHeader("App")
            NavigationRow(
                title = "Food Library",
                leadingIcon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onManageFoods,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Data & Backup",
                subtitle = SettingsOverviewCopy.backupSubtitle(
                    SettingsOverviewCopy.latestBackupMillis(
                        backupState.lastBackup,
                        backupState.lastCloudBackup,
                    ),
                ),
                leadingIcon = Icons.Filled.Backup,
                onClick = onBackup,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Appearance",
                subtitle = SettingsOverviewCopy.appearanceSubtitle(
                    themeMode = settings.themeMode,
                    accent = settings.accent,
                ),
                leadingIcon = Icons.Filled.Palette,
                onClick = onAppearance,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "About",
                subtitle = "Version ${BuildConfig.VERSION_NAME}",
                leadingIcon = Icons.Filled.Info,
                onClick = onAbout,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
