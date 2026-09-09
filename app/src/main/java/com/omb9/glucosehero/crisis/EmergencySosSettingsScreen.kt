package com.omb9.glucosehero.crisis

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.util.CrisisDetector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySosSettingsScreen(
    onBack: () -> Unit,
    viewModel: EmergencySosViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.emergency_sos_title)) },
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
                text = stringResource(R.string.emergency_sos_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.emergency_sos_enable),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.emergency_sos_timeout, state.timeoutMinutes),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = state.timeoutMinutes.toFloat(),
                onValueChange = { viewModel.setTimeoutMinutes(it.toInt()) },
                valueRange = CrisisDetector.MIN_SOS_TIMEOUT_MINUTES.toFloat()..
                    CrisisDetector.MAX_SOS_TIMEOUT_MINUTES.toFloat(),
                steps = CrisisDetector.MAX_SOS_TIMEOUT_MINUTES -
                    CrisisDetector.MIN_SOS_TIMEOUT_MINUTES - 1,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val needed = buildList {
                        add(Manifest.permission.SEND_SMS)
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }.toTypedArray()
                    permissionLauncher.launch(needed)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.emergency_sos_grant_permissions))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.emergency_sos_permission_status,
                    if (state.hasSmsPermission) "yes" else "no",
                    if (state.hasLocationPermission) "yes" else "no",
                    if (state.hasNotificationPermission) "yes" else "no",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.emergency_sos_caregivers),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            state.caregivers.forEach { contact ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                contact.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.removeCaregiver(contact.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
            OutlinedTextField(
                value = state.draftName,
                onValueChange = viewModel::onDraftName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.emergency_sos_caregiver_name)) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.draftPhone,
                onValueChange = viewModel::onDraftPhone,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.emergency_sos_caregiver_phone)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::addCaregiver,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.draftPhone.isNotBlank(),
            ) {
                Text(stringResource(R.string.emergency_sos_add_caregiver))
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = stringResource(R.string.emergency_sos_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}
