package com.omb9.glucosehero.clinical

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalTestScreen(
    onBack: () -> Unit,
    viewModel: ClinicalTestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clinical_test_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.clinical_test_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            val kinds = ClinicalTestKind.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { index, kind ->
                    SegmentedButton(
                        selected = state.kind == kind,
                        onClick = { viewModel.selectKind(kind) },
                        shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                        enabled = state.session?.status != ClinicalTestStatus.RUNNING,
                    ) {
                        Text(
                            if (kind == ClinicalTestKind.OVERNIGHT_BASAL) {
                                stringResource(R.string.clinical_test_basal)
                            } else {
                                stringResource(R.string.clinical_test_icr)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state.kind == ClinicalTestKind.MEAL_CARB_RATIO &&
                state.session?.status != ClinicalTestStatus.RUNNING
            ) {
                OutlinedTextField(
                    value = state.mealCarbsInput,
                    onValueChange = viewModel::onMealCarbsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.clinical_test_carbs_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
            }
            val running = state.session?.status == ClinicalTestStatus.RUNNING
            if (running) {
                Button(onClick = viewModel::cancelTest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.clinical_test_cancel))
                }
            } else {
                Button(onClick = viewModel::startTest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.clinical_test_start))
                }
            }
            state.statusMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            val session = state.session
            if (session != null) {
                Spacer(Modifier.height(16.dp))
                SessionCard(session = session, unit = state.unit)
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = stringResource(R.string.clinical_test_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionCard(session: ClinicalTestSession, unit: com.omb9.glucosehero.domain.model.GlucoseUnit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (session.status) {
                    ClinicalTestStatus.RUNNING -> stringResource(R.string.clinical_test_running)
                    ClinicalTestStatus.INVALID -> stringResource(R.string.clinical_test_invalid)
                    ClinicalTestStatus.COMPLETE -> stringResource(R.string.clinical_test_complete)
                    ClinicalTestStatus.IDLE -> stringResource(R.string.clinical_test_idle)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.clinical_test_window,
                    Formatters.time(session.startedAtMillis, use24Hour = false),
                    Formatters.time(session.plannedEndMillis, use24Hour = false),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            session.invalidReason?.let { reason ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = invalidCopy(reason),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            session.result?.let { result ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.clinical_test_range,
                        Formatters.glucoseWithUnit(result.startGlucoseMgdl, unit),
                        Formatters.glucoseWithUnit(result.endGlucoseMgdl, unit),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(result.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun invalidCopy(reason: String): String = when (reason) {
    "food" -> stringResource(R.string.clinical_test_invalid_food)
    "bolus" -> stringResource(R.string.clinical_test_invalid_bolus)
    "food_and_bolus" -> stringResource(R.string.clinical_test_invalid_food_bolus)
    "not_enough_readings" -> stringResource(R.string.clinical_test_invalid_readings)
    else -> stringResource(R.string.clinical_test_invalid)
}
