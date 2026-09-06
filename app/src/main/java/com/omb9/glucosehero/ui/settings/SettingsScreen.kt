package com.omb9.glucosehero.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsScreenDataStoreEntryPoint {
    fun settingsDataStore(): SettingsDataStore
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageFoods: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val bolus by viewModel.bolusSettings.collectAsStateWithLifecycle()
    val healthConnectStatus = viewModel.healthConnectAvailabilityStatus
    val isHealthConnectConnected by viewModel.isHealthConnectConnected.collectAsStateWithLifecycle()
    val glucoseImportEnabled by viewModel.glucoseImportEnabled.collectAsStateWithLifecycle()
    val nutritionImportEnabled by viewModel.nutritionImportEnabled.collectAsStateWithLifecycle()
    val exerciseImportEnabled by viewModel.exerciseImportEnabled.collectAsStateWithLifecycle()
    val healthConnectInitialImportRange by viewModel.healthConnectInitialImportRange.collectAsStateWithLifecycle()
    val healthConnectLastSync by viewModel.healthConnectLastSync.collectAsStateWithLifecycle()
    val healthConnectSampleCount by viewModel.healthConnectSampleCount.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsScreenDataStoreEntryPoint::class.java,
        ).settingsDataStore()
    }
    val barcodeLookupEnabled by settingsDataStore.barcodeLookupEnabled
        .collectAsStateWithLifecycle(initialValue = true)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            ProfileSection(
                profile = profile,
                unitSystem = settings.unitSystem,
                onProfileTargetChange = viewModel::setProfileTarget,
                onNameChange = viewModel::setProfileName,
                onAgeChange = viewModel::setProfileAge,
                onDiabetesTypeChange = viewModel::setProfileDiabetesType,
                onUnitSystemChange = viewModel::setUnitSystem,
                onHeightMetricChange = viewModel::setProfileHeightMetric,
                onHeightImperialChange = viewModel::setProfileHeightImperial,
                onWeightMetricChange = viewModel::setProfileWeightMetric,
                onWeightImperialChange = viewModel::setProfileWeightImperial,
            )

            GlucoseTargetsSection(
                unit = settings.unit,
                targetLowMgdl = settings.targetLowMgdl,
                targetHighMgdl = settings.targetHighMgdl,
                bolus = bolus,
                onUnitChange = viewModel::setUnit,
                onTargetRangeChange = viewModel::setTargetRange,
                onDiaChange = viewModel::setDiaHours,
                onCirChange = viewModel::setCirRatio,
                onIsfChange = viewModel::setIsfMgdl,
                onTargetGlucoseChange = viewModel::setTargetGlucoseMgdl,
            )

            HealthConnectSection(
                status = healthConnectStatus,
                isConnected = isHealthConnectConnected,
                glucoseImportEnabled = glucoseImportEnabled,
                nutritionImportEnabled = nutritionImportEnabled,
                exerciseImportEnabled = exerciseImportEnabled,
                initialImportRange = healthConnectInitialImportRange,
                lastSync = healthConnectLastSync,
                sampleCount = healthConnectSampleCount,
                use24HourTime = settings.use24HourTime,
                readPermissions = viewModel.readPermissions,
                onPermissionsResult = viewModel::onPermissionsResult,
                onGlucoseImportEnabledChange = viewModel::setGlucoseImportEnabled,
                onNutritionImportEnabledChange = viewModel::setNutritionImportEnabled,
                onExerciseImportEnabledChange = viewModel::setExerciseImportEnabled,
                onInitialImportRangeChange = viewModel::setHealthConnectInitialImportRange,
                onClearImportedData = viewModel::clearImportedGlucoseData,
            )

            MealLoggingSection(
                postMealRemindersEnabled = settings.postMealRemindersEnabled,
                showAdvancedMacros = settings.showAdvancedMacros,
                barcodeLookupEnabled = barcodeLookupEnabled,
                onPostMealRemindersEnabledChange = viewModel::setPostMealRemindersEnabled,
                onShowAdvancedMacrosChange = viewModel::setShowAdvancedMacros,
                onBarcodeLookupChange = { enabled ->
                    scope.launch { settingsDataStore.setBarcodeLookupEnabled(enabled) }
                },
                onManageFoods = onManageFoods,
            )

            AiAssistantSection(
                isEnabled = settings.isHeroAiEnabled,
                aiConfig = aiConfig,
                onEnabledChange = viewModel::setIsHeroAiEnabled,
                onProviderChange = viewModel::setAiProvider,
                onBaseUrlChange = viewModel::setAiBaseUrl,
                onModelChange = viewModel::setAiModel,
                onSaveApiKey = viewModel::saveApiKey,
            )

            SectionHeader("Data & Backup")
            BackupSection(
                state = backupState,
                onBackupNow = viewModel::exportBackup,
                onExportMarkdown = viewModel::exportMarkdown,
                onImportPicked = viewModel::previewImport,
                onChooseFolder = viewModel::setBackupFolder,
                onDismissPreview = viewModel::dismissImportPreview,
                onImport = viewModel::importBackup,
                onAutoBackupToggle = viewModel::setAutoBackupEnabled,
            )

            AppearanceSection(
                themeMode = settings.themeMode,
                accent = settings.accent,
                use24HourTime = settings.use24HourTime,
                onThemeModeChange = viewModel::setThemeMode,
                onAccentChange = viewModel::setAccent,
                onUse24HourTimeChange = viewModel::setUse24HourTime,
            )

            Spacer(Modifier.height(24.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    "GlucoseHero is a logging tool, not a medical device. Hero's " +
                        "answers are informational — always confirm treatment " +
                        "decisions with your care team.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
