package com.omb9.glucosehero.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.cgm.GlucoseFreshness
import com.omb9.glucosehero.data.health.HealthConnectStatus
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.ui.cgm.GlucoseFreshnessLabel
import com.omb9.glucosehero.util.Formatters

/**
 * Unified CGM ingest hub. Detailed xDrip identify-receiver copy, Nightscout
 * host ack / token / FGS disclosure, and Health Connect permissions stay on
 * their existing screens. LibreLinkUp is listed as unavailable only.
 *
 * FEATURE: cgm-direct-ingest
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesSettingsScreen(
    onBack: () -> Unit,
    onXdripSetup: () -> Unit,
    onNightscoutSetup: () -> Unit,
    onHealthConnect: () -> Unit,
    viewModel: DataSourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val xdripTest by viewModel.xdripTest.collectAsStateWithLifecycle()
    val nightscoutTest by viewModel.nightscoutTest.collectAsStateWithLifecycle()
    val hcTest by viewModel.hcTest.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_sources_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.data_sources_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        DataSourcesSection(
            state = state,
            xdripTest = xdripTest,
            nightscoutTest = nightscoutTest,
            hcTest = hcTest,
            onXdripEnabledChange = viewModel::setXdripEnabled,
            onNightscoutEnabledChange = viewModel::setNightscoutEnabled,
            onHcGlucoseImportChange = viewModel::setHcGlucoseImportEnabled,
            onTestXdrip = viewModel::testXdrip,
            onTestNightscout = viewModel::testNightscout,
            onTestHealthConnect = viewModel::testHealthConnect,
            onXdripSetup = onXdripSetup,
            onNightscoutSetup = onNightscoutSetup,
            onHealthConnect = onHealthConnect,
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
internal fun DataSourcesSection(
    state: DataSourcesUiState,
    xdripTest: XdripListenResult?,
    nightscoutTest: NightscoutTestUiState,
    hcTest: DataSourceTestUiState,
    onXdripEnabledChange: (Boolean) -> Unit,
    onNightscoutEnabledChange: (Boolean) -> Unit,
    onHcGlucoseImportChange: (Boolean) -> Unit,
    onTestXdrip: () -> Unit,
    onTestNightscout: () -> Unit,
    onTestHealthConnect: () -> Unit,
    onXdripSetup: () -> Unit,
    onNightscoutSetup: () -> Unit,
    onHealthConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.data_sources_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.data_sources_freshness_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GlucoseFreshnessLabel(freshness = state.freshness)
        if (state.freshness is GlucoseFreshness.Fresh) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.data_sources_freshness_fresh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.data_sources_priority_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.data_sources_priority_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        XdripHubCard(
            enabled = state.xdripEnabled,
            lastSuccessMillis = state.xdripLastSuccess,
            count24h = state.xdripCount24h,
            lastError = state.xdripLastError,
            use24HourTime = state.use24HourTime,
            testResult = xdripTest,
            onEnabledChange = onXdripEnabledChange,
            onTest = onTestXdrip,
            onSetup = onXdripSetup,
        )

        Spacer(Modifier.height(12.dp))
        NightscoutHubCard(
            enabled = state.nightscoutEnabled,
            lastSuccessMillis = state.nightscoutLastSuccess,
            count24h = state.nightscoutCount24h,
            lastError = state.nightscoutLastError,
            use24HourTime = state.use24HourTime,
            testState = nightscoutTest,
            onEnabledChange = onNightscoutEnabledChange,
            onTest = onTestNightscout,
            onSetup = onNightscoutSetup,
        )

        Spacer(Modifier.height(12.dp))
        HealthConnectHubCard(
            connected = state.hcConnected,
            status = state.hcStatus,
            glucoseImportEnabled = state.hcGlucoseImportEnabled,
            lastSyncMillis = state.hcLastSync,
            count24h = state.hcCount24h,
            errorCode = DataSourcesCopy.healthConnectErrorCode(
                status = state.hcStatus,
                revoked = state.hcRevoked,
                lastError = state.hcLastError,
            ),
            use24HourTime = state.use24HourTime,
            testState = hcTest,
            onGlucoseImportChange = onHcGlucoseImportChange,
            onTest = onTestHealthConnect,
            onOpen = onHealthConnect,
        )

        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.data_sources_libre_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.data_sources_libre_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun XdripHubCard(
    enabled: Boolean,
    lastSuccessMillis: Long?,
    count24h: Int,
    lastError: String?,
    use24HourTime: Boolean,
    testResult: XdripListenResult?,
    onEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    onSetup: () -> Unit,
) {
    HubCardSurface {
        Text(
            text = stringResource(R.string.xdrip_settings_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        SettingsToggleRow(
            label = stringResource(R.string.xdrip_enable_label),
            checked = enabled,
            onCheckedChange = onEnabledChange,
            description = stringResource(R.string.xdrip_enable_description),
        )
        Spacer(Modifier.height(12.dp))
        HubSyncAndCount(
            lastSyncMillis = lastSuccessMillis,
            count24h = count24h,
            use24HourTime = use24HourTime,
        )
        HubErrorText(
            DataSourcesCopy.errorMessageRes(GlucoseSampleSource.XDRIP_BROADCAST, lastError),
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_sources_xdrip_test))
        }
        xdripTestResultText(testResult)?.let { text ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (testResult is XdripListenResult.Received) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_sources_open_xdrip))
        }
    }
}

@Composable
private fun NightscoutHubCard(
    enabled: Boolean,
    lastSuccessMillis: Long?,
    count24h: Int,
    lastError: String?,
    use24HourTime: Boolean,
    testState: NightscoutTestUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    onSetup: () -> Unit,
) {
    HubCardSurface {
        Text(
            text = stringResource(R.string.nightscout_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        SettingsToggleRow(
            label = stringResource(R.string.nightscout_enable),
            checked = enabled,
            onCheckedChange = onEnabledChange,
            description = stringResource(R.string.nightscout_enable_description),
        )
        Spacer(Modifier.height(12.dp))
        HubSyncAndCount(
            lastSyncMillis = lastSuccessMillis,
            count24h = count24h,
            use24HourTime = use24HourTime,
        )
        HubErrorText(
            DataSourcesCopy.errorMessageRes(GlucoseSampleSource.NIGHTSCOUT, lastError),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onTest,
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
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_sources_open_nightscout))
        }
    }
}

@Composable
private fun HealthConnectHubCard(
    connected: Boolean,
    status: HealthConnectStatus,
    glucoseImportEnabled: Boolean,
    lastSyncMillis: Long?,
    count24h: Int,
    errorCode: String?,
    use24HourTime: Boolean,
    testState: DataSourceTestUiState,
    onGlucoseImportChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    onOpen: () -> Unit,
) {
    HubCardSurface {
        Text(
            text = stringResource(R.string.data_sources_hc_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.data_sources_hc_pointer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status == HealthConnectStatus.AVAILABLE && connected) {
            Spacer(Modifier.height(8.dp))
            SettingsToggleRow(
                label = stringResource(R.string.data_sources_hc_enable),
                checked = glucoseImportEnabled,
                onCheckedChange = onGlucoseImportChange,
            )
        }
        Spacer(Modifier.height(12.dp))
        HubSyncAndCount(
            lastSyncMillis = lastSyncMillis,
            count24h = count24h,
            use24HourTime = use24HourTime,
        )
        HubErrorText(
            DataSourcesCopy.errorMessageRes(GlucoseSampleSource.HEALTH_CONNECT, errorCode),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onTest,
            modifier = Modifier.fillMaxWidth(),
            enabled = testState !is DataSourceTestUiState.Running,
        ) {
            Text(stringResource(R.string.data_sources_hc_test))
        }
        when (val state = testState) {
            DataSourceTestUiState.Idle -> Unit
            DataSourceTestUiState.Running -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.data_sources_test_running),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is DataSourceTestUiState.Success -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(state.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            is DataSourceTestUiState.Failure -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(state.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.data_sources_open_hc))
        }
    }
}

@Composable
private fun HubCardSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp), content = { content() })
    }
}

@Composable
private fun HubSyncAndCount(
    lastSyncMillis: Long?,
    count24h: Int,
    use24HourTime: Boolean,
) {
    Text(
        text = stringResource(
            R.string.data_sources_last_sync,
            lastSyncMillis?.let {
                Formatters.dayHeader(Formatters.localDate(it)) +
                    " · " +
                    Formatters.time(it, use24HourTime)
            } ?: stringResource(R.string.data_sources_last_sync_never),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = pluralStringResource(R.plurals.data_sources_samples_24h, count24h, count24h),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HubErrorText(@StringRes resId: Int?) {
    if (resId == null) return
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun xdripTestResultText(result: XdripListenResult?): String? = when (result) {
    null -> null
    XdripListenResult.Disabled -> stringResource(R.string.data_sources_xdrip_test_disabled)
    XdripListenResult.NoAllowlistedApp -> stringResource(R.string.data_sources_xdrip_test_no_app)
    XdripListenResult.Waiting -> stringResource(R.string.data_sources_xdrip_test_waiting)
    is XdripListenResult.Received -> stringResource(R.string.data_sources_xdrip_test_received)
}
