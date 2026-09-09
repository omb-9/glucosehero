package com.omb9.glucosehero.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.remote.AiEndpointGuard
import com.omb9.glucosehero.data.remote.TrustedHosts
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import kotlinx.coroutines.launch

/**
 * Dedicated screen for configuring Hero AI (issue 103). Reached from the
 * Settings list via a navigation row. Owns the same controls that previously
 * lived inline in [AiAssistantSection], plus a proper top app bar and back
 * navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val heroAiRemainingCalls by viewModel.heroAiRemainingCalls.collectAsStateWithLifecycle()
    val aiEndpointNeedsAck by viewModel.aiEndpointNeedsAck.collectAsStateWithLifecycle()
    val aiEndpointStatus by viewModel.aiEndpointStatus.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiKeyError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.apiKeySaveMessages.collect { message ->
            if (message.isError) {
                apiKeyError = message.text
            } else {
                apiKeyInput = ""
            }
            snackbarHostState.showSnackbar(message.text)
        }
    }

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
                title = { Text("Hero AI") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
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
                ProviderDropdown(
                    aiConfig = aiConfig,
                    onProviderChange = viewModel::setAiProvider,
                )

                if (heroAiRemainingCalls != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (heroAiRemainingCalls) {
                            0 -> "No AI calls left today — resets at midnight."
                            1 -> "1 AI call left today."
                            else -> "$heroAiRemainingCalls AI calls left today."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Local text state avoids cursor jumps; DataStore follows each edit,
                // and the dynamic interceptor reads the latest value per request.
                var baseUrl by remember(aiConfig.provider) { mutableStateOf(aiConfig.baseUrl) }
                var ackDialogDismissed by remember(TrustedHosts.hostOf(baseUrl) ?: baseUrl) {
                    mutableStateOf(false)
                }
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

                when (val status = aiEndpointStatus) {
                    AiEndpointGuard.Status.HttpsRequired -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.ai_endpoint_https_required, baseUrl),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is AiEndpointGuard.Status.NeedsAcknowledgment -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.ai_endpoint_untrusted, status.host),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::acknowledgeAiEndpoint,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ai_endpoint_ack_confirm))
                        }
                    }
                    AiEndpointGuard.Status.InvalidUrl -> {
                        if (aiConfig.provider == AiProvider.CUSTOM && baseUrl.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.ai_endpoint_invalid, baseUrl),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    AiEndpointGuard.Status.Allowed -> Unit
                }

                if (aiEndpointNeedsAck && !ackDialogDismissed) {
                    AlertDialog(
                        onDismissRequest = { ackDialogDismissed = true },
                        title = { Text(stringResource(R.string.ai_endpoint_ack_title)) },
                        text = {
                            Text(stringResource(R.string.ai_endpoint_ack_body, baseUrl))
                        },
                        confirmButton = {
                            TextButton(onClick = viewModel::acknowledgeAiEndpoint) {
                                Text(stringResource(R.string.ai_endpoint_ack_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { ackDialogDismissed = true }) {
                                Text(stringResource(R.string.ai_endpoint_ack_cancel))
                            }
                        },
                    )
                }

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

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        apiKeyError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(if (aiConfig.hasApiKey) "API key (saved)" else "API key")
                    },
                    singleLine = true,
                    isError = apiKeyError != null,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        Text(
                            apiKeyError ?: if (aiConfig.hasApiKey) {
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
                    onClick = { viewModel.saveApiKey(apiKeyInput.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save API key")
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    aiConfig: AiConfig,
    onProviderChange: (AiProvider) -> Unit,
) {
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
                        onProviderChange(provider)
                        providerExpanded = false
                    },
                )
            }
        }
    }
}
