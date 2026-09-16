package com.omb9.glucosehero.ui.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.data.remote.WebhookUrlGuard

/**
 * Advanced integrations: local webhook URL, validation, and a test POST.
 * Reached from Settings → Integrations → Advanced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val storedUrl by viewModel.webhookUrl.collectAsStateWithLifecycle()
    val testState by viewModel.webhookTestState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        AdvancedSection(
            storedUrl = storedUrl,
            testState = testState,
            onUrlChange = viewModel::setWebhookUrl,
            onSendTestPayload = viewModel::sendTestWebhook,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        )
    }
}

@Composable
internal fun AdvancedSection(
    storedUrl: String,
    testState: WebhookTestUiState,
    onUrlChange: (String) -> Unit,
    onSendTestPayload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webhookInput by remember(storedUrl) { mutableStateOf(storedUrl) }
    val urlStatus = WebhookUrlGuard.evaluate(webhookInput)
    val isError = urlStatus is WebhookUrlGuard.Status.InvalidUrl ||
        urlStatus is WebhookUrlGuard.Status.HttpsRequired
    val canSendTest = urlStatus is WebhookUrlGuard.Status.Allowed &&
        testState !is WebhookTestUiState.Running

    Column(modifier = modifier) {
        OutlinedTextField(
            value = webhookInput,
            onValueChange = { newUrl ->
                webhookInput = newUrl
                onUrlChange(newUrl)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Local webhook URL") },
            placeholder = { Text("http://192.168.1.10:8123/webhook") },
            singleLine = true,
            isError = isError,
            supportingText = {
                Text(
                    when (urlStatus) {
                        WebhookUrlGuard.Status.InvalidUrl ->
                            "Enter a valid http or https URL."
                        WebhookUrlGuard.Status.HttpsRequired ->
                            "Plain HTTP is only allowed for local network addresses."
                        WebhookUrlGuard.Status.Empty,
                        WebhookUrlGuard.Status.Allowed,
                        -> "A JSON payload is POSTed here whenever a new glucose entry is saved."
                    },
                )
            },
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSendTestPayload(webhookInput) },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSendTest,
        ) {
            Text("Send test payload")
        }
        Spacer(Modifier.height(8.dp))
        when (val state = testState) {
            WebhookTestUiState.Idle -> Unit
            WebhookTestUiState.Running -> Text(
                "Sending test payload...",
                style = MaterialTheme.typography.bodyMedium,
            )
            is WebhookTestUiState.Success -> Text(
                "Webhook accepted the test payload (HTTP ${state.httpCode}).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            is WebhookTestUiState.Failure -> Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
