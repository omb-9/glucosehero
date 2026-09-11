package com.omb9.glucosehero.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseTargetsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bolus by viewModel.bolusSettings.collectAsStateWithLifecycle()
    val dosingLoad by viewModel.dosingProfile.collectAsStateWithLifecycle()
    val explainerSeen by viewModel.dosingProfileExplainerSeen.collectAsStateWithLifecycle()

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
                title = { Text("Glucose & Targets") },
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
        GlucoseTargetsSection(
            unit = settings.unit,
            targetLowMgdl = settings.targetLowMgdl,
            targetHighMgdl = settings.targetHighMgdl,
            bolus = bolus,
            onUnitChange = viewModel::setUnit,
            onTargetRangeChange = viewModel::setTargetRange,
            onDiaChange = viewModel::setDiaHours,
            dosingLoad = dosingLoad,
            use24HourTime = settings.use24HourTime,
            onSaveProfile = { profile ->
                viewModel.setDosingProfile(profile.copy(diaHours = bolus.diaHours))
            },
            explainerSeen = explainerSeen,
            onExplainerDismiss = viewModel::markDosingProfileExplainerSeen,
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
internal fun GlucoseTargetsSection(
    unit: GlucoseUnit,
    targetLowMgdl: Float,
    targetHighMgdl: Float,
    bolus: BolusSettings,
    onUnitChange: (GlucoseUnit) -> Unit,
    onTargetRangeChange: (Float, Float) -> Unit,
    onDiaChange: (Float) -> Unit,
    dosingLoad: com.omb9.glucosehero.domain.model.DosingProfileLoad,
    use24HourTime: Boolean,
    onSaveProfile: (com.omb9.glucosehero.domain.model.DosingProfile) -> Unit,
    explainerSeen: Boolean,
    onExplainerDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Glucose unit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlossaryIcon(
                term = "Glucose unit",
                definition = SettingsGlossary.GLUCOSE_UNIT,
                contentDescription = "About glucose units",
            )
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            GlucoseUnit.entries.forEachIndexed { index, glucoseUnit ->
                SegmentedButton(
                    selected = unit == glucoseUnit,
                    onClick = { onUnitChange(glucoseUnit) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = GlucoseUnit.entries.size,
                    ),
                ) {
                    Text(glucoseUnit.label)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        var rangeValue by remember(targetLowMgdl, targetHighMgdl) {
            mutableStateOf(targetLowMgdl..targetHighMgdl)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Target range: " +
                    Formatters.glucose(rangeValue.start.toDouble(), unit) +
                    " – " +
                    Formatters.glucose(rangeValue.endInclusive.toDouble(), unit) +
                    " ${unit.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlossaryIcon(
                term = "Target range",
                definition = SettingsGlossary.TARGET_RANGE,
                contentDescription = "About target range",
            )
        }
        RangeSlider(
            value = rangeValue,
            onValueChange = { rangeValue = it },
            onValueChangeFinished = {
                onTargetRangeChange(rangeValue.start, rangeValue.endInclusive)
            },
            valueRange = 40f..300f,
        )

        Text(
            text = androidx.compose.ui.res.stringResource(com.omb9.glucosehero.R.string.dosing_profile_dia_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        SmartBolusSlider(
            label = "Duration of Insulin Action (DIA)",
            value = bolus.diaHours,
            valueRange = com.omb9.glucosehero.domain.model.DosingBounds.MIN_DIA_HOURS..com.omb9.glucosehero.domain.model.DosingBounds.MAX_DIA_HOURS,
            steps = 11,
            displayText = { v ->
                if (v % 1f == 0f) "${v.toInt()} hr" else "%.1f hr".format(v)
            },
            onValueChangeFinished = onDiaChange,
            glossaryTerm = "Duration of Insulin Action (DIA)",
            glossaryDefinition = SettingsGlossary.DURATION_OF_INSULIN_ACTION,
            glossaryContentDescription = "About duration of insulin action",
        )
        Text(
            text = androidx.compose.ui.res.stringResource(com.omb9.glucosehero.R.string.dosing_profile_dia_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        DosingProfileEditor(
            load = dosingLoad,
            use24HourTime = use24HourTime,
            onSave = onSaveProfile,
            explainerSeen = explainerSeen,
            onExplainerDismiss = onExplainerDismiss,
        )
    }
}
