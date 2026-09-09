package com.omb9.glucosehero.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.wear.WearAvailability
import com.omb9.glucosehero.wear.WearConnectionStatus
import com.omb9.glucosehero.wear.WearSyncSettingsStore
import com.omb9.glucosehero.wear.WearSyncWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WearOsSettingsEntryPoint {
    fun wearSyncSettingsStore(): WearSyncSettingsStore
}

@Composable
fun WearOsSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WearOsSettingsEntryPoint::class.java,
        ).wearSyncSettingsStore()
    }
    val enabled by store.syncEnabled.collectAsStateWithLifecycle(initialValue = true)
    var status by remember { mutableStateOf(WearConnectionStatus.NO_WATCH) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { status = WearAvailability.status(context) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            HealthConnectToggleRow(
                label = "Sync to Wear OS",
                checked = enabled,
                onCheckedChange = { checked ->
                    scope.launch {
                        store.setSyncEnabled(checked)
                        if (checked) {
                            WearSyncWorker.enqueue(context)
                        }
                        status = WearAvailability.status(context)
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusMessage(status, enabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusMessage(status: WearConnectionStatus, enabled: Boolean): String {
    if (!enabled) return "Watch sync is off. Latest glucose stays on this phone."
    return when (status) {
        WearConnectionStatus.PLAY_SERVICES_MISSING ->
            "Google Play services is not available, so the watch cannot be reached."
        WearConnectionStatus.NO_WATCH ->
            "No Wear OS watch is paired or nearby. Pair a watch in system settings to sync."
        WearConnectionStatus.READY ->
            "Connected. Latest glucose, trend, and quick-entry tiles sync with the watch."
    }
}
