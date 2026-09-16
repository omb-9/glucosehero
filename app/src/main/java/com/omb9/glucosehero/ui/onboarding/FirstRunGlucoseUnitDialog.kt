package com.omb9.glucosehero.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.ui.theme.Spacing

/**
 * Blocking first-run unit question. Dismissing without a choice is disabled;
 * Continue persists the (locale-preselected) unit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstRunGlucoseUnitDialog(
    onConfirm: (GlucoseUnit) -> Unit,
) {
    var selected by rememberSaveable {
        mutableStateOf(GlucoseUnit.defaultForLocale())
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text(stringResource(R.string.first_run_unit_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.first_run_unit_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.md))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    GlucoseUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = selected == unit,
                            onClick = { selected = unit },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = GlucoseUnit.entries.size,
                            ),
                        ) {
                            Text(unit.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.first_run_unit_continue))
            }
        },
    )
}
