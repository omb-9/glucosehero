package com.omb9.glucosehero.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.domain.model.DiabetesType
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.UnitSystem
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var backInFlight by remember { mutableStateOf(false) }

    fun handleBack() {
        if (backInFlight) return
        backInFlight = true
        scope.launch {
            runCatching { viewModel.savePendingChanges() }
            onBack()
        }
    }

    BackHandler { handleBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileSection(
    profile: UserProfile,
    unitSystem: UnitSystem,
    onProfileTargetChange: (ProfileTarget) -> Unit,
    onNameChange: (String) -> Unit,
    onAgeChange: (Int?) -> Unit,
    onDiabetesTypeChange: (String?) -> Unit,
    onUnitSystemChange: (UnitSystem) -> Unit,
    onHeightMetricChange: (String) -> Unit,
    onHeightImperialChange: (String, String) -> Unit,
    onWeightMetricChange: (String) -> Unit,
    onWeightImperialChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
                            onProfileTargetChange(target)
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
                onNameChange(it)
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
                onAgeChange(it.toIntOrNull())
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Age") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Spacer(Modifier.height(12.dp))

        val accent = MaterialTheme.colorScheme.primary
        var diabetesTypeExpanded by remember { mutableStateOf(false) }
        val diabetesType = DiabetesType.fromStored(profile.diabetesType)
        ExposedDropdownMenuBox(
            expanded = diabetesTypeExpanded,
            onExpandedChange = { diabetesTypeExpanded = it },
        ) {
            OutlinedTextField(
                value = diabetesType?.label ?: profile.diabetesType.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Diabetes Type") },
                placeholder = { Text("Select diabetes type") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = diabetesTypeExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = diabetesTypeExpanded,
                onDismissRequest = { diabetesTypeExpanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                DiabetesType.entries.forEach { type ->
                    val selected = type == diabetesType
                    DropdownMenuItem(
                        text = { Text(type.label) },
                        onClick = {
                            onDiabetesTypeChange(type.label)
                            diabetesTypeExpanded = false
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = accent,
                                )
                            }
                        } else {
                            null
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = accent,
                        ),
                    )
                }
            }
        }

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
                    selected = unitSystem == system,
                    onClick = { onUnitSystemChange(system) },
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

        if (unitSystem == UnitSystem.METRIC) {
            var heightText by remember(profile.heightCm, unitSystem) {
                mutableStateOf(Formatters.formatHeight(profile.heightCm, UnitSystem.METRIC))
            }
            OutlinedTextField(
                value = heightText,
                onValueChange = {
                    heightText = it
                    onHeightMetricChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Height (cm)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        } else {
            var heightFeetText by remember(unitSystem) { mutableStateOf("") }
            var heightInchesText by remember(unitSystem) { mutableStateOf("") }
            var feetFocused by remember { mutableStateOf(false) }
            var inchesFocused by remember { mutableStateOf(false) }

            // Seed from canonical cm. Skipping a focused field stops the debounced
            // save's round-trip (cm -> here) from clobbering an in-progress edit with a
            // derived "0": typing 5 ft then 11 in would otherwise flash "0" in the
            // inches box before the user finishes.
            LaunchedEffect(profile.heightCm, unitSystem) {
                val pair = profile.heightCm?.takeIf { it != 0f }
                    ?.let(Formatters::cmToFeetInches)
                if (!feetFocused) heightFeetText = pair?.first?.toString() ?: ""
                if (!inchesFocused) heightInchesText = pair?.second?.toString() ?: ""
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = heightFeetText,
                    onValueChange = {
                        heightFeetText = it
                        onHeightImperialChange(it, heightInchesText)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            feetFocused = state.isFocused
                        },
                    label = { Text("Height (ft)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = heightInchesText,
                    onValueChange = {
                        heightInchesText = it
                        onHeightImperialChange(heightFeetText, it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            inchesFocused = state.isFocused
                        },
                    label = { Text("Height (in)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        var weightText by remember(profile.weightKg, unitSystem) {
            mutableStateOf(Formatters.formatWeight(profile.weightKg, unitSystem))
        }
        OutlinedTextField(
            value = weightText,
            onValueChange = {
                weightText = it
                if (unitSystem == UnitSystem.METRIC) {
                    onWeightMetricChange(it)
                } else {
                    onWeightImperialChange(it)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(if (unitSystem == UnitSystem.METRIC) "Weight (kg)" else "Weight (lb)")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}
