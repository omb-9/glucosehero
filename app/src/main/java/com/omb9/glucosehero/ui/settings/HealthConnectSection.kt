package com.omb9.glucosehero.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.data.health.HealthConnectStatus
import com.omb9.glucosehero.data.local.datastore.InitialImportRange
import com.omb9.glucosehero.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val healthConnectStatus = viewModel.healthConnectAvailabilityStatus
    val isHealthConnectConnected by viewModel.isHealthConnectConnected.collectAsStateWithLifecycle()
    val isHealthConnectRevoked by viewModel.isHealthConnectRevoked.collectAsStateWithLifecycle()
    val glucoseImportEnabled by viewModel.glucoseImportEnabled.collectAsStateWithLifecycle()
    val nutritionImportEnabled by viewModel.nutritionImportEnabled.collectAsStateWithLifecycle()
    val exerciseImportEnabled by viewModel.exerciseImportEnabled.collectAsStateWithLifecycle()
    val sleepImportEnabled by viewModel.sleepImportEnabled.collectAsStateWithLifecycle()
    val cycleImportEnabled by viewModel.cycleImportEnabled.collectAsStateWithLifecycle()
    val healthConnectInitialImportRange by viewModel.healthConnectInitialImportRange.collectAsStateWithLifecycle()
    val healthConnectLastSync by viewModel.healthConnectLastSync.collectAsStateWithLifecycle()
    val healthConnectSampleCount by viewModel.healthConnectSampleCount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshHealthConnectStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Health Connect")
                        GlossaryIcon(
                            term = "Health Connect",
                            definition = SettingsGlossary.HEALTH_CONNECT,
                            contentDescription = "About Health Connect",
                        )
                    }
                },
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
        HealthConnectSection(
            status = healthConnectStatus,
            isConnected = isHealthConnectConnected,
            glucoseImportEnabled = glucoseImportEnabled,
            nutritionImportEnabled = nutritionImportEnabled,
            exerciseImportEnabled = exerciseImportEnabled,
            sleepImportEnabled = sleepImportEnabled,
            cycleImportEnabled = cycleImportEnabled,
            initialImportRange = healthConnectInitialImportRange,
            lastSync = healthConnectLastSync,
            sampleCount = healthConnectSampleCount,
            use24HourTime = settings.use24HourTime,
            readPermissions = viewModel.allPermissions,
            onPermissionsResult = viewModel::onPermissionsResult,
            onGlucoseImportEnabledChange = viewModel::setGlucoseImportEnabled,
            onNutritionImportEnabledChange = viewModel::setNutritionImportEnabled,
            onExerciseImportEnabledChange = viewModel::setExerciseImportEnabled,
            onSleepImportEnabledChange = viewModel::setSleepImportEnabled,
            onCycleImportEnabledChange = viewModel::setCycleImportEnabled,
            onInitialImportRangeChange = viewModel::setHealthConnectInitialImportRange,
            onManagePermissions = { openHealthConnectPermissions(context) },
            onClearImportedData = viewModel::clearImportedGlucoseData,
            isRevoked = isHealthConnectRevoked,
            onDismissRevocationBanner = viewModel::dismissHealthConnectRevokedBanner,
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
internal fun HealthConnectSection(
    status: HealthConnectStatus,
    isConnected: Boolean,
    glucoseImportEnabled: Boolean,
    nutritionImportEnabled: Boolean,
    exerciseImportEnabled: Boolean,
    sleepImportEnabled: Boolean,
    cycleImportEnabled: Boolean,
    initialImportRange: InitialImportRange,
    lastSync: Long?,
    sampleCount: Int,
    use24HourTime: Boolean,
    readPermissions: Set<String>,
    onPermissionsResult: (Set<String>) -> Unit,
    onGlucoseImportEnabledChange: (Boolean) -> Unit,
    onNutritionImportEnabledChange: (Boolean) -> Unit,
    onExerciseImportEnabledChange: (Boolean) -> Unit,
    onSleepImportEnabledChange: (Boolean) -> Unit,
    onCycleImportEnabledChange: (Boolean) -> Unit,
    onInitialImportRangeChange: (InitialImportRange) -> Unit,
    onManagePermissions: () -> Unit,
    onClearImportedData: () -> Unit,
    isRevoked: Boolean = false,
    onDismissRevocationBanner: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> onPermissionsResult(granted) }
    var showClearHealthConnectDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        when (status) {
            HealthConnectStatus.AVAILABLE -> {
                if (isConnected) {
                    Text(
                        "Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    HealthConnectToggleRow(
                        label = "Import glucose",
                        checked = glucoseImportEnabled,
                        onCheckedChange = onGlucoseImportEnabledChange,
                    )
                    HealthConnectToggleRow(
                        label = "Import nutrition",
                        checked = nutritionImportEnabled,
                        onCheckedChange = onNutritionImportEnabledChange,
                    )
                    HealthConnectToggleRow(
                        label = "Import exercise",
                        checked = exerciseImportEnabled,
                        onCheckedChange = onExerciseImportEnabledChange,
                    )
                    HealthConnectToggleRow(
                        label = "Import sleep",
                        checked = sleepImportEnabled,
                        onCheckedChange = onSleepImportEnabledChange,
                    )
                    HealthConnectToggleRow(
                        label = "Import cycle",
                        checked = cycleImportEnabled,
                        onCheckedChange = onCycleImportEnabledChange,
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Initial import range",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        InitialImportRange.entries.forEachIndexed { index, range ->
                            SegmentedButton(
                                selected = initialImportRange == range,
                                onClick = { onInitialImportRangeChange(range) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = InitialImportRange.entries.size,
                                ),
                            ) {
                                Text(range.label)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Last sync: " + (lastSync?.let {
                            Formatters.dayHeader(Formatters.localDate(it)) +
                                " · " +
                                Formatters.time(it, use24HourTime)
                        } ?: "Never"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Imported samples: $sampleCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onManagePermissions,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Manage permissions in Health Connect")
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showClearHealthConnectDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Remove imported data",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    if (isRevoked) {
                        HealthConnectBanner(
                            text = "Health Connect permissions were revoked in system settings.",
                            onDismiss = onDismissRevocationBanner,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Button(
                        onClick = {
                            healthConnectPermissionLauncher.launch(readPermissions)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Connect Health Connect")
                    }
                }
            }

            HealthConnectStatus.UPDATE_REQUIRED -> {
                Text(
                    "Health Connect needs to be updated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { context.startActivity(healthConnectInstallIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Update Health Connect")
                }
            }

            HealthConnectStatus.UNAVAILABLE -> {
                Text(
                    "Health Connect isn't available on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { context.startActivity(healthConnectInstallIntent()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Install Health Connect")
                    }
                }
            }
        }
    }

    if (showClearHealthConnectDialog) {
        AlertDialog(
            onDismissRequest = { showClearHealthConnectDialog = false },
            title = { Text("Remove imported data?") },
            text = {
                Text(
                    "This deletes all imported Health Connect glucose samples. " +
                        "Your manual log entries are not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHealthConnectDialog = false
                        onClearImportedData()
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHealthConnectDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
internal fun HealthConnectBanner(
    text: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss banner",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
