package com.omb9.glucosehero.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutAuthMode
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutEndpointGuard
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutLimits
import com.omb9.glucosehero.util.Formatters

/**
 * Nightscout REST poller settings. Off by default. API v1 only.
 *
 * FEATURE: cgm-direct-ingest
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightscoutSettingsScreen(
    onBack: () -> Unit,
    viewModel: NightscoutSettingsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val storedUrl by viewModel.url.collectAsStateWithLifecycle()
    val authMode by viewModel.authMode.collectAsStateWithLifecycle()
    val hasCredential by viewModel.hasCredential.collectAsStateWithLifecycle()
    val backfillHours by viewModel.backfillHours.collectAsStateWithLifecycle()
    val foregroundService by viewModel.foregroundService.collectAsStateWithLifecycle()
    val lastSuccess by viewModel.lastSuccessMillis.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val endpointStatus by viewModel.endpointStatus.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()
    val use24HourTime by viewModel.use24HourTime.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nightscout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nightscout_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        NightscoutSection(
            enabled = enabled,
            storedUrl = storedUrl,
            authMode = authMode,
            hasCredential = hasCredential,
            backfillHours = backfillHours,
            foregroundService = foregroundService,
            lastSuccessMillis = lastSuccess,
            lastErrorCode = lastError,
            endpointStatus = endpointStatus,
            testState = testState,
            use24HourTime = use24HourTime,
            onEnabledChange = viewModel::setEnabled,
            onUrlChange = viewModel::setUrl,
            onAuthModeChange = viewModel::setAuthMode,
            onSaveCredential = viewModel::saveCredential,
            onAcknowledgeHost = viewModel::acknowledgeHost,
            onBackfillHoursChange = viewModel::setBackfillHours,
            onForegroundServiceChange = viewModel::setForegroundService,
            onTestConnection = viewModel::testConnection,
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
internal fun NightscoutSection(
    enabled: Boolean,
    storedUrl: String,
    authMode: NightscoutAuthMode,
    hasCredential: Boolean,
    backfillHours: Int,
    foregroundService: Boolean,
    lastSuccessMillis: Long?,
    lastErrorCode: String?,
    endpointStatus: NightscoutEndpointGuard.Status,
    testState: NightscoutTestUiState,
    use24HourTime: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onUrlChange: (String) -> Unit,
    onAuthModeChange: (NightscoutAuthMode) -> Unit,
    onSaveCredential: (String) -> Unit,
    onAcknowledgeHost: () -> Unit,
    onBackfillHoursChange: (Int) -> Unit,
    onForegroundServiceChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var urlInput by remember(storedUrl) { mutableStateOf(storedUrl) }
    var credentialInput by remember { mutableStateOf("") }
    var ackDialogDismissed by remember(storedUrl) { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onForegroundServiceChange(true)
    }

    val backfillChoices = listOf(12, 24, 48, NightscoutLimits.MAX_BACKFILL_HOURS)

    Column(modifier = modifier) {
        SettingsToggleRow(
            label = stringResource(R.string.nightscout_enable),
            checked = enabled,
            onCheckedChange = onEnabledChange,
            description = stringResource(R.string.nightscout_enable_description),
        )

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.nightscout_v1_only),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                onUrlChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.nightscout_url_label)) },
            placeholder = { Text(stringResource(R.string.nightscout_url_placeholder)) },
            singleLine = true,
        )

        when (val status = endpointStatus) {
            NightscoutEndpointGuard.Status.HttpsRequired -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.nightscout_cleartext_refused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is NightscoutEndpointGuard.Status.NeedsAcknowledgment -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.nightscout_host_untrusted, status.host),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onAcknowledgeHost,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.nightscout_host_ack_confirm))
                }
            }
            NightscoutEndpointGuard.Status.InvalidUrl -> {
                if (urlInput.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.nightscout_url_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            NightscoutEndpointGuard.Status.Allowed -> Unit
        }

        if (endpointStatus is NightscoutEndpointGuard.Status.NeedsAcknowledgment &&
            !ackDialogDismissed
        ) {
            AlertDialog(
                onDismissRequest = { ackDialogDismissed = true },
                title = { Text(stringResource(R.string.nightscout_host_ack_title)) },
                text = { Text(stringResource(R.string.nightscout_host_ack_body, urlInput)) },
                confirmButton = {
                    TextButton(onClick = onAcknowledgeHost) {
                        Text(stringResource(R.string.nightscout_host_ack_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { ackDialogDismissed = true }) {
                        Text(stringResource(R.string.nightscout_host_ack_cancel))
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.nightscout_auth_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            NightscoutAuthMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = authMode == mode,
                    onClick = { onAuthModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = NightscoutAuthMode.entries.size,
                    ),
                ) {
                    Text(
                        if (mode == NightscoutAuthMode.TOKEN) {
                            stringResource(R.string.nightscout_auth_token)
                        } else {
                            stringResource(R.string.nightscout_auth_api_secret)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.nightscout_auth_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = credentialInput,
            onValueChange = { credentialInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    if (authMode == NightscoutAuthMode.TOKEN) {
                        stringResource(R.string.nightscout_token_label)
                    } else {
                        stringResource(R.string.nightscout_api_secret_label)
                    },
                )
            },
            placeholder = {
                Text(
                    if (hasCredential) {
                        stringResource(R.string.nightscout_credential_stored)
                    } else {
                        stringResource(R.string.nightscout_credential_placeholder)
                    },
                )
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                onSaveCredential(credentialInput)
                credentialInput = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = credentialInput.isNotBlank() || hasCredential,
        ) {
            Text(
                if (credentialInput.isBlank() && hasCredential) {
                    stringResource(R.string.nightscout_credential_clear)
                } else {
                    stringResource(R.string.nightscout_credential_save)
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.nightscout_backfill_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.nightscout_backfill_help, NightscoutLimits.MAX_BACKFILL_HOURS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            backfillChoices.forEachIndexed { index, hours ->
                SegmentedButton(
                    selected = backfillHours == hours,
                    onClick = { onBackfillHoursChange(hours) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = backfillChoices.size,
                    ),
                ) {
                    Text(stringResource(R.string.nightscout_backfill_hours, hours))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsToggleRow(
            label = stringResource(R.string.nightscout_fgs_enable),
            checked = foregroundService,
            onCheckedChange = { wantOn ->
                if (wantOn) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onForegroundServiceChange(true)
                    }
                } else {
                    onForegroundServiceChange(false)
                }
            },
            description = stringResource(R.string.nightscout_fgs_description),
        )

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(
                R.string.nightscout_last_sync,
                lastSuccessMillis?.let {
                    Formatters.dayHeader(Formatters.localDate(it)) +
                        " · " +
                        Formatters.time(it, use24HourTime)
                } ?: stringResource(R.string.nightscout_last_sync_never),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lastErrorCode?.let { code ->
            Spacer(Modifier.height(4.dp))
            Text(
                nightscoutErrorMessage(code),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onTestConnection,
            modifier = Modifier.fillMaxWidth(),
            enabled = testState !is NightscoutTestUiState.Running,
        ) {
            Text(stringResource(R.string.nightscout_test_connection))
        }
        Spacer(Modifier.height(8.dp))
        when (val state = testState) {
            NightscoutTestUiState.Idle -> Unit
            NightscoutTestUiState.Running -> Text(
                stringResource(R.string.nightscout_test_running),
                style = MaterialTheme.typography.bodyMedium,
            )
            is NightscoutTestUiState.Success -> Text(
                stringResource(R.string.nightscout_test_success, state.parsedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            is NightscoutTestUiState.Failure -> Text(
                nightscoutErrorMessage(state.errorCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun nightscoutErrorMessage(code: String): String =
    stringResource(DataSourcesCopy.nightscoutErrorStringRes(code))
