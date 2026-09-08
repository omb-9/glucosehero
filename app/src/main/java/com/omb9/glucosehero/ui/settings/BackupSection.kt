package com.omb9.glucosehero.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.data.export.BackupCounts
import com.omb9.glucosehero.data.export.BackupPreview
import com.omb9.glucosehero.data.export.ImportMode
import com.omb9.glucosehero.data.export.suggestedBackupFileName
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(backupState.message) {
        backupState.message?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & Backup") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BackupSection(
            state = backupState,
            onBackupNow = viewModel::exportBackup,
            onExportMarkdown = viewModel::exportMarkdown,
            onImportPicked = viewModel::previewImport,
            onChooseFolder = viewModel::setBackupFolder,
            onDismissPreview = viewModel::dismissImportPreview,
            onImport = viewModel::importBackup,
            onAutoBackupToggle = viewModel::setAutoBackupEnabled,
            onRestoreSnapshot = viewModel::restoreLatestSnapshot,
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
fun BackupSection(
    state: BackupUiState,
    onBackupNow: (Uri) -> Unit,
    onExportMarkdown: (Uri) -> Unit,
    onImportPicked: (Uri) -> Unit,
    onChooseFolder: (Uri?) -> Unit,
    onDismissPreview: () -> Unit,
    onImport: (Uri, ImportMode) -> Unit,
    onAutoBackupToggle: (Boolean) -> Unit,
    onRestoreSnapshot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var confirmRestoreSnapshot by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onBackupNow) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importUri = uri
            onImportPicked(uri)
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> onChooseFolder(uri) }

    val markdownLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(onExportMarkdown) }

    Column(modifier = modifier) {
        Text(
            text = if (state.lastBackup != null) {
                "Last backed up ${relativeTime(state.lastBackup)}"
            } else {
                "No backup yet"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Automatic backups", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = state.backupEnabled,
                onCheckedChange = onAutoBackupToggle,
            )
        }
        Text(
            text = if (state.backupDirUri != null) {
                "Folder chosen. Backups run daily while charging and idle."
            } else {
                "Choose a folder to enable daily automatic backups."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { folderLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Choose backup folder")
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { backupLauncher.launch(suggestedBackupFileName()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back up now (JSON)")
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore from backup")
        }

        if (state.hasSnapshot) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmRestoreSnapshot = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restore last snapshot")
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { markdownLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export Markdown vault")
        }

        if (state.isWorking) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val preview = state.preview
    if (preview != null && importUri != null) {
        ImportPreviewDialog(
            preview = preview,
            onMerge = {
                onImport(importUri!!, ImportMode.MERGE)
                importUri = null
            },
            onReplace = {
                onImport(importUri!!, ImportMode.REPLACE)
                importUri = null
            },
            onCancel = {
                importUri = null
                onDismissPreview()
            },
        )
    }

    if (confirmRestoreSnapshot) {
        AlertDialog(
            onDismissRequest = { confirmRestoreSnapshot = false },
            title = { Text("Restore last snapshot?") },
            text = {
                Text(
                    "This replaces everything on this device with the backup saved " +
                        "just before the last import.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestoreSnapshot = false
                        onRestoreSnapshot()
                    },
                ) {
                    Text("Restore snapshot", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreSnapshot = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: BackupPreview,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Restore this backup?") },
        text = {
            Column {
                Text("In this file", style = MaterialTheme.typography.titleSmall)
                CountLines(preview.counts)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Date range: ${formatDate(preview.earliestEntry)} to " +
                        formatDate(preview.latestEntry),
                )
                Spacer(Modifier.height(12.dp))
                Text("On this device", style = MaterialTheme.typography.titleSmall)
                CountLines(preview.currentCounts)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Merge keeps what is already here and adds rows this file has that you don't.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Replace everything deletes all data on this device, then copies this file. " +
                        "Use this only if you mean to wipe the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMerge) { Text("Merge") }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onReplace) {
                    Text("Replace everything", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun CountLines(counts: BackupCounts) {
    Text("Entries: ${counts.entries}")
    Text("Glucose samples: ${counts.glucoseSamples}")
    Text("Foods: ${counts.foods}")
    Text("Supplies: ${counts.supplies}")
    Text("Insights: ${counts.insights}")
    Text("Chat messages: ${counts.chat}")
    Text("Pending queries: ${counts.pendingAiQueries}")
}

private fun relativeTime(millis: Long?): String {
    if (millis == null) return ""
    val duration = Duration.between(Instant.ofEpochMilli(millis), Instant.now())
    return when {
        duration.toMinutes() < 1 -> "just now"
        duration.toHours() < 1 -> "${duration.toMinutes()} minutes ago"
        duration.toDays() < 1 -> "${duration.toHours()} hours ago"
        else -> "${duration.toDays()} days ago"
    }
}

private fun formatDate(millis: Long?): String =
    if (millis == null) {
        "unknown"
    } else {
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_FORMATTER)
    }

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
