package com.omb9.glucosehero.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.health.connect.HealthConnectManager
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SectionHeader(
    title: String,
    glossaryTerm: String? = null,
    glossaryDefinition: String? = null,
    glossaryContentDescription: String? = null,
) {
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (glossaryTerm != null && glossaryDefinition != null && glossaryContentDescription != null) {
            GlossaryIcon(
                term = glossaryTerm,
                definition = glossaryDefinition,
                contentDescription = glossaryContentDescription,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
internal fun NavigationRow(
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun HealthConnectToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (description != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun healthConnectInstallIntent(): Intent =
    Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"),
    )

/**
 * Creates a deep link Intent to manage Health Connect permissions.
 * On Android 14+ (API 34+), directly targets this application's permission management
 * page in Health Connect. On earlier Android versions, routes to the Health Connect
 * settings screen.
 */
internal fun healthConnectManagePermissionsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS).apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        }
    } else {
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
    }

/**
 * Safely launches the Health Connect permission management flow with graceful fallbacks.
 */
internal fun openHealthConnectPermissions(context: Context) {
    val intent = healthConnectManagePermissionsIntent(context)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(healthConnectInstallIntent())
            } catch (_: ActivityNotFoundException) {
                // Device does not have Health Connect or Play Store available
            }
        }
    }
}

/**
 * A small, muted, tappable info icon that sits after a setting's label and
 * opens a dialog explaining the term. [definition] is the text shown in the
 * dialog body; [contentDescription] identifies the term for screen readers.
 */
@Composable
internal fun GlossaryIcon(
    term: String,
    definition: String,
    contentDescription: String,
) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(
        onClick = { showDialog = true },
        modifier = Modifier.size(28.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(term) },
            text = { Text(definition) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
internal fun SmartBolusSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayText: (Float) -> String,
    onValueChangeFinished: (Float) -> Unit,
    glossaryTerm: String? = null,
    glossaryDefinition: String? = null,
    glossaryContentDescription: String? = null,
) {
    // Local drag state keyed on the persisted value: the thumb follows the
    // finger immediately, and the slider re-syncs if DataStore emits changes
    // from elsewhere. The commit happens only on release, so DataStore isn't
    // written on every drag frame.
    var sliderValue by remember(value) { mutableStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ${displayText(sliderValue)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (glossaryTerm != null && glossaryDefinition != null && glossaryContentDescription != null) {
            GlossaryIcon(
                term = glossaryTerm,
                definition = glossaryDefinition,
                contentDescription = glossaryContentDescription,
            )
        }
    }
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onValueChangeFinished(sliderValue) },
        valueRange = valueRange,
        steps = steps,
    )
    Spacer(Modifier.height(8.dp))
}
