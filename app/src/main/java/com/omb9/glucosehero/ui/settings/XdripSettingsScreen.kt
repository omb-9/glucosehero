package com.omb9.glucosehero.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface XdripSettingsEntryPoint {
    fun settingsDataStore(): SettingsDataStore
    fun glucoseSampleDao(): GlucoseSampleDao
}

/**
 * xDrip+ / AAPS local-broadcast opt-in. Toggle defaults off (DataStore).
 *
 * FEATURE: cgm-direct-ingest
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XdripSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            XdripSettingsEntryPoint::class.java,
        ).settingsDataStore()
    }
    val enabled by store.xdripBroadcastEnabled.collectAsStateWithLifecycle(initialValue = false)
    val lastSuccess by store.cgmLastIngestSuccess(GlucoseSampleSource.XDRIP_BROADCAST)
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.xdrip_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.xdrip_back_cd),
                        )
                    }
                },
            )
        },
    ) { padding ->
        XdripSettingsSection(
            enabled = enabled,
            lastSuccessMillis = lastSuccess,
            onEnabledChange = { checked ->
                scope.launch { store.setXdripBroadcastEnabled(checked) }
            },
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
internal fun XdripSettingsSection(
    enabled: Boolean,
    lastSuccessMillis: Long?,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val receiverPackage = stringResource(R.string.xdrip_identify_receiver_package)
    Column(modifier = modifier) {
        SettingsToggleRow(
            label = stringResource(R.string.xdrip_enable_label),
            checked = enabled,
            onCheckedChange = onEnabledChange,
            description = stringResource(R.string.xdrip_enable_description),
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.xdrip_setup_body, receiverPackage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.xdrip_identify_receiver_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = receiverPackage,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.xdrip_setup_aaps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.xdrip_setup_identify_why),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.xdrip_api_pre34_sender_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = xdripLastReceivedText(lastSuccessMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun xdripLastReceivedText(lastSuccessMillis: Long?): String {
    if (lastSuccessMillis == null) {
        return stringResource(R.string.xdrip_last_received_never)
    }
    val now by produceState(initialValue = System.currentTimeMillis(), lastSuccessMillis) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((now - lastSuccessMillis) / 1_000L)
        .coerceIn(0L, Int.MAX_VALUE.toLong())
        .toInt()
    return pluralStringResource(R.plurals.xdrip_last_received_seconds, seconds, seconds)
}
