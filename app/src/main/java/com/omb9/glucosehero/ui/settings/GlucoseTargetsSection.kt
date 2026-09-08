package com.omb9.glucosehero.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.util.Formatters

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
    onCirChange: (Float) -> Unit,
    onIsfChange: (Float) -> Unit,
    onTargetGlucoseChange: (Float) -> Unit,
) {
    Column {
        SectionHeader("Glucose & Targets")

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

        SmartBolusSlider(
            label = "Duration of Insulin Action (DIA)",
            value = bolus.diaHours,
            valueRange = 2f..8f,
            steps = 11,
            displayText = { v ->
                if (v % 1f == 0f) "${v.toInt()} hr" else "%.1f hr".format(v)
            },
            onValueChangeFinished = onDiaChange,
            glossaryTerm = "Duration of Insulin Action (DIA)",
            glossaryDefinition = SettingsGlossary.DURATION_OF_INSULIN_ACTION,
            glossaryContentDescription = "About duration of insulin action",
        )

        SmartBolusSlider(
            label = "Carb-to-Insulin Ratio (CIR)",
            value = bolus.cirRatio,
            valueRange = 1f..50f,
            steps = 48,
            displayText = { v -> "${v.toInt()} g/U" },
            onValueChangeFinished = onCirChange,
            glossaryTerm = "Carb-to-Insulin Ratio (CIR)",
            glossaryDefinition = SettingsGlossary.CARB_RATIO,
            glossaryContentDescription = "About carb-to-insulin ratio",
        )

        SmartBolusSlider(
            label = "Insulin Sensitivity Factor (ISF)",
            value = bolus.isfMgdl,
            valueRange = 10f..150f,
            steps = 139,
            displayText = { v -> "${v.toInt()} mg/dL per U" },
            onValueChangeFinished = onIsfChange,
            glossaryTerm = "Insulin Sensitivity Factor (ISF)",
            glossaryDefinition = SettingsGlossary.INSULIN_SENSITIVITY_FACTOR,
            glossaryContentDescription = "About insulin sensitivity factor",
        )

        SmartBolusSlider(
            label = "Target Glucose",
            value = bolus.targetGlucoseMgdl,
            valueRange = 60f..180f,
            steps = 119,
            displayText = { v ->
                "${Formatters.glucose(v.toDouble(), unit)} ${unit.label}"
            },
            onValueChangeFinished = onTargetGlucoseChange,
            glossaryTerm = "Target Glucose",
            glossaryDefinition = SettingsGlossary.TARGET_GLUCOSE,
            glossaryContentDescription = "About target glucose",
        )
    }
}
