package com.omb9.glucosehero.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.data.health.HealthConnectStatus
import com.omb9.glucosehero.data.local.datastore.InitialImportRange
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UnitSystem
import com.omb9.glucosehero.util.Formatters
import androidx.compose.runtime.rememberCoroutineScope
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
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setPostMealRemindersEnabled(true)
    }
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onPermissionsResult(granted) }
    var showClearHealthConnectDialog by remember { mutableStateOf(false) }

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
            // ============ Profile & Persona ============
            SectionHeader("Profile & Persona")

            var targetExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = targetExpanded,
                onExpandedChange = { targetExpanded = it },
            ) {
                OutlinedTextField(
                    value = profile.profileTarget.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Who are you logging for?") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = targetExpanded,
                    onDismissRequest = { targetExpanded = false },
                ) {
                    ProfileTarget.entries.forEach { target ->
                        DropdownMenuItem(
                            text = { Text(target.displayName) },
                            onClick = {
                                viewModel.setProfileTarget(target)
                                targetExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            var nameText by remember(profile.name) { mutableStateOf(profile.name) }
            OutlinedTextField(
                value = nameText,
                onValueChange = {
                    nameText = it
                    viewModel.setProfileName(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name / Nickname") },
                singleLine = true,
            )

            Spacer(Modifier.height(12.dp))

            var ageText by remember(profile.age) { mutableStateOf(profile.age?.toString() ?: "") }
            OutlinedTextField(
                value = ageText,
                onValueChange = {
                    ageText = it
                    viewModel.setProfileAge(it.toIntOrNull())
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Age") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Spacer(Modifier.height(12.dp))

            var diabetesTypeText by remember(profile.diabetesType) {
                mutableStateOf(profile.diabetesType.orEmpty())
            }
            OutlinedTextField(
                value = diabetesTypeText,
                onValueChange = {
                    diabetesTypeText = it
                    viewModel.setProfileDiabetesType(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Diabetes Type") },
                singleLine = true,
                supportingText = {
                    Text("e.g. Type 1, Type 2, Gestational, LADA, Prediabetes")
                },
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Units",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                UnitSystem.entries.forEachIndexed { index, system ->
                    SegmentedButton(
                        selected = settings.unitSystem == system,
                        onClick = { viewModel.setUnitSystem(system) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = UnitSystem.entries.size,
                        ),
                    ) {
                        Text(system.label)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (settings.unitSystem == UnitSystem.METRIC) {
                var heightText by remember(profile.heightCm, settings.unitSystem) {
                    mutableStateOf(Formatters.formatHeight(profile.heightCm, UnitSystem.METRIC))
                }
                OutlinedTextField(
                    value = heightText,
                    onValueChange = {
                        heightText = it
                        viewModel.setProfileHeightMetric(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Height (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            } else {
                val imperialHeight = profile.heightCm?.let { Formatters.cmToFeetInches(it) }
                var heightFeetText by remember(profile.heightCm, settings.unitSystem) {
                    mutableStateOf(imperialHeight?.first?.toString() ?: "")
                }
                var heightInchesText by remember(profile.heightCm, settings.unitSystem) {
                    mutableStateOf(imperialHeight?.second?.toString() ?: "")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = heightFeetText,
                        onValueChange = {
                            heightFeetText = it
                            viewModel.setProfileHeightImperial(it, heightInchesText)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Height (ft)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = heightInchesText,
                        onValueChange = {
                            heightInchesText = it
                            viewModel.setProfileHeightImperial(heightFeetText, it)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Height (in)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            var weightText by remember(profile.weightKg, settings.unitSystem) {
                mutableStateOf(Formatters.formatWeight(profile.weightKg, settings.unitSystem))
            }
            OutlinedTextField(
                value = weightText,
                onValueChange = {
                    weightText = it
                    if (settings.unitSystem == UnitSystem.METRIC) {
                        viewModel.setProfileWeightMetric(it)
                    } else {
                        viewModel.setProfileWeightImperial(it)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(if (settings.unitSystem == UnitSystem.METRIC) "Weight (kg)" else "Weight (lb)")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )

            // ============ Appearance ============
            SectionHeader("Appearance")

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size,
                        ),
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.AMOLED -> "AMOLED"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Accent color",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColor.entries.forEach { accent ->
                    val selected = settings.accent == accent
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(accent.argb), CircleShape)
                            .clickable { viewModel.setAccent(accent) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = accent.label,
                                tint = Color.Black,
                            )
                        }
                    }
                }
            }

            // ============ Glucose ============
            SectionHeader("Glucose")

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GlucoseUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = settings.unit == unit,
                        onClick = { viewModel.setUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = GlucoseUnit.entries.size,
                        ),
                    ) {
                        Text(unit.label)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            var rangeValue by remember(settings.targetLowMgdl, settings.targetHighMgdl) {
                mutableStateOf(settings.targetLowMgdl..settings.targetHighMgdl)
            }
            Text(
                "Target range: " +
                    Formatters.glucose(rangeValue.start.toDouble(), settings.unit) +
                    " – " +
                    Formatters.glucose(rangeValue.endInclusive.toDouble(), settings.unit) +
                    " ${settings.unit.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RangeSlider(
                value = rangeValue,
                onValueChange = { rangeValue = it },
                onValueChangeFinished = {
                    viewModel.setTargetRange(rangeValue.start, rangeValue.endInclusive)
                },
                valueRange = 40f..300f,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("24-hour time", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.use24HourTime,
                    onCheckedChange = viewModel::setUse24HourTime,
                )
            }

            // ============ Smart Bolus ============
            SectionHeader("Smart Bolus")

            SmartBolusSlider(
                label = "Duration of Insulin Action (DIA)",
                value = bolus.diaHours,
                valueRange = 2f..8f,
                steps = 11,
                displayText = { v ->
                    if (v % 1f == 0f) "${v.toInt()} hr" else "%.1f hr".format(v)
                },
                onValueChangeFinished = viewModel::setDiaHours,
            )

            SmartBolusSlider(
                label = "Carb-to-Insulin Ratio (CIR)",
                value = bolus.cirRatio,
                valueRange = 1f..50f,
                steps = 48,
                displayText = { v -> "${v.toInt()} g/U" },
                onValueChangeFinished = viewModel::setCirRatio,
            )

            SmartBolusSlider(
                label = "Insulin Sensitivity Factor (ISF)",
                value = bolus.isfMgdl,
                valueRange = 10f..150f,
                steps = 139,
                displayText = { v -> "${v.toInt()} mg/dL per U" },
                onValueChangeFinished = viewModel::setIsfMgdl,
            )

            SmartBolusSlider(
                label = "Target Glucose",
                value = bolus.targetGlucoseMgdl,
                valueRange = 60f..180f,
                steps = 119,
                displayText = { v ->
                    "${Formatters.glucose(v.toDouble(), settings.unit)} ${settings.unit.label}"
                },
                onValueChangeFinished = viewModel::setTargetGlucoseMgdl,
            )

            // ============ Meal Logging ============
            SectionHeader("Meal Logging")

            NavigationRow(
                title = "Manage foods",
                onClick = onManageFoods,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Enable Post-Meal Reminders (+2h)",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = settings.postMealRemindersEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS,
                            )
                        } else {
                            viewModel.setPostMealRemindersEnabled(enabled)
                        }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show Advanced Macros (Protein & Fat)",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = settings.showAdvancedMacros,
                    onCheckedChange = viewModel::setShowAdvancedMacros,
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Barcode lookup (Open Food Facts)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = barcodeLookupEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsDataStore.setBarcodeLookupEnabled(enabled) }
                        },
                    )
                }
                Text(
                    "When you scan a barcode, only the barcode number is sent to Open Food Facts. " +
                        "No log data ever leaves the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ============ Health Connect ============
            SectionHeader("Health Connect")

            when (healthConnectStatus) {
                HealthConnectStatus.AVAILABLE -> {
                    if (isHealthConnectConnected) {
                        Text(
                            "Connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))

                        HealthConnectToggleRow(
                            label = "Import glucose",
                            checked = glucoseImportEnabled,
                            onCheckedChange = viewModel::setGlucoseImportEnabled,
                        )
                        HealthConnectToggleRow(
                            label = "Import nutrition",
                            checked = nutritionImportEnabled,
                            onCheckedChange = viewModel::setNutritionImportEnabled,
                        )
                        HealthConnectToggleRow(
                            label = "Import exercise",
                            checked = exerciseImportEnabled,
                            onCheckedChange = viewModel::setExerciseImportEnabled,
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Initial import range",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            InitialImportRange.entries.forEachIndexed { index, range ->
                                SegmentedButton(
                                    selected = healthConnectInitialImportRange == range,
                                    onClick = { viewModel.setHealthConnectInitialImportRange(range) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = InitialImportRange.entries.size,
                                    ),
                                ) {
                                    Text(range.label)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Last sync: " + (healthConnectLastSync?.let {
                                Formatters.dayHeader(Formatters.localDate(it)) +
                                    " · " +
                                    Formatters.time(it, settings.use24HourTime)
                            } ?: "Never"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Imported samples: $healthConnectSampleCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { showClearHealthConnectDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Remove imported data",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                healthConnectPermissionLauncher.launch(viewModel.readPermissions)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Connect Health Connect")
                        }
                    }
                }

                else -> {
                    Text(
                        "Health Connect isn't installed on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { context.startActivity(healthConnectInstallIntent()) },
                        ) {
                            Text("Install Health Connect")
                        }
                    }
                }
            }

            // ============ Hero AI ============
            SectionHeader("Hero AI")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enable Hero AI", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.isHeroAiEnabled,
                    onCheckedChange = viewModel::setIsHeroAiEnabled,
                )
            }

            if (settings.isHeroAiEnabled) {
                var providerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it },
                ) {
                    OutlinedTextField(
                        value = aiConfig.provider.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false },
                    ) {
                        AiProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.label) },
                                onClick = {
                                    viewModel.setAiProvider(provider)
                                    providerExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Local text state avoids cursor jumps; DataStore follows each edit,
                // and the dynamic interceptor reads the latest value per request.
                var baseUrl by remember(aiConfig.provider) { mutableStateOf(aiConfig.baseUrl) }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        viewModel.setAiBaseUrl(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    enabled = aiConfig.provider == AiProvider.CUSTOM,
                    singleLine = true,
                    supportingText = if (aiConfig.provider == AiProvider.CUSTOM) {
                        { Text("Any OpenAI-compatible endpoint, e.g. your Ollama box's /v1/") }
                    } else {
                        null
                    },
                )

                Spacer(Modifier.height(12.dp))

                var model by remember(aiConfig.provider) { mutableStateOf(aiConfig.model) }
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        viewModel.setAiModel(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    singleLine = true,
                )

                Spacer(Modifier.height(12.dp))

                var apiKeyInput by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(if (aiConfig.hasApiKey) "API key (saved)" else "API key")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        Text(
                            if (aiConfig.hasApiKey) {
                                "A key is stored, encrypted on-device via Android KeyStore. " +
                                    "Enter a new one to replace it."
                            } else {
                                "Encrypted on-device via Android KeyStore before it's stored. " +
                                    "It never leaves your phone except to call your provider."
                            }
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveApiKey(apiKeyInput.trim())
                        apiKeyInput = ""
                    },
                    enabled = apiKeyInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save API key")
                }
            }

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

    if (showClearHealthConnectDialog) {
        AlertDialog(
            onDismissRequest = { showClearHealthConnectDialog = false },
            title = { Text("Remove imported data?") },
            text = {
                Text(
                    "This deletes all imported Health Connect glucose samples. " +
                        "Your manual log entries are not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHealthConnectDialog = false
                        viewModel.clearImportedGlucoseData()
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHealthConnectDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun NavigationRow(
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HealthConnectToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun healthConnectInstallIntent(): Intent =
    Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"),
    )

@Composable
private fun SmartBolusSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayText: (Float) -> String,
    onValueChangeFinished: (Float) -> Unit,
) {
    // Local drag state keyed on the persisted value: the thumb follows the
    // finger immediately, and the slider re-syncs if DataStore emits changes
    // from elsewhere. The commit happens only on release, so DataStore isn't
    // written on every drag frame.
    var sliderValue by remember(value) { mutableStateOf(value) }
    Text(
        text = "$label: ${displayText(sliderValue)}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onValueChangeFinished(sliderValue) },
        valueRange = valueRange,
        steps = steps,
    )
    Spacer(Modifier.height(8.dp))
}
