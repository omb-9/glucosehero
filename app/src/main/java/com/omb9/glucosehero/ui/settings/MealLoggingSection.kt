package com.omb9.glucosehero.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealLoggingSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val barcodeLookupEnabled by viewModel.barcodeLookupEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meal Logging") },
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
        MealLoggingSection(
            postMealRemindersEnabled = settings.postMealRemindersEnabled,
            showAdvancedMacros = settings.showAdvancedMacros,
            barcodeLookupEnabled = barcodeLookupEnabled,
            sendMealPhotosToHeroAi = settings.sendMealPhotosToHeroAi,
            onPostMealRemindersEnabledChange = viewModel::setPostMealRemindersEnabled,
            onShowAdvancedMacrosChange = viewModel::setShowAdvancedMacros,
            onBarcodeLookupChange = viewModel::setBarcodeLookupEnabled,
            onSendMealPhotosToHeroAiChange = viewModel::setSendMealPhotosToHeroAi,
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
internal fun MealLoggingSection(
    postMealRemindersEnabled: Boolean,
    showAdvancedMacros: Boolean,
    barcodeLookupEnabled: Boolean,
    sendMealPhotosToHeroAi: Boolean,
    onPostMealRemindersEnabledChange: (Boolean) -> Unit,
    onShowAdvancedMacrosChange: (Boolean) -> Unit,
    onBarcodeLookupChange: (Boolean) -> Unit,
    onSendMealPhotosToHeroAiChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val context = LocalContext.current
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) onPostMealRemindersEnabledChange(true)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Enable Post-Meal Reminders (+2h)",
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = postMealRemindersEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    } else {
                        onPostMealRemindersEnabledChange(enabled)
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Show Advanced Macros (Protein & Fat)",
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = showAdvancedMacros,
                onCheckedChange = onShowAdvancedMacrosChange,
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Barcode lookup (Open Food Facts)",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = barcodeLookupEnabled,
                    onCheckedChange = onBarcodeLookupChange,
                )
            }
            Text(
                "When you scan a barcode, only the barcode number is sent to Open Food Facts. " +
                    "No log data ever leaves the device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Send Meal Photos to Hero AI",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = sendMealPhotosToHeroAi,
                    onCheckedChange = onSendMealPhotosToHeroAiChange,
                )
            }
            Text(
                "When on, a meal photo you capture is sent to your AI provider for a one-time " +
                    "nutrition estimate. The image is kept in memory only — it is never saved " +
                    "to the app or your log.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
