package com.omb9.glucosehero.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsScreenDataStoreEntryPoint {
    fun settingsDataStore(): SettingsDataStore
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageFoods: () -> Unit,
    onHeroAiSettings: () -> Unit,
    onProfile: () -> Unit,
    onGlucoseTargets: () -> Unit,
    onMealLogging: () -> Unit,
    onHealthConnect: () -> Unit,
    onBackup: () -> Unit,
    onAppearance: () -> Unit,
    onClinicalTests: () -> Unit,
    onEmergencySos: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsDataStore = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsScreenDataStoreEntryPoint::class.java,
        ).settingsDataStore()
    }
    val webhookUrl by settingsDataStore.webhookUrl
        .collectAsStateWithLifecycle(initialValue = "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineMedium) },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Profile",
                onClick = onProfile,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Glucose & Targets",
                onClick = onGlucoseTargets,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Meal Logging",
                onClick = onMealLogging,
            )

            SectionHeader("Foods")
            NavigationRow(
                title = "Food Library",
                onClick = onManageFoods,
            )

            SectionHeader("Hero AI")
            NavigationRow(
                title = "Hero AI",
                onClick = onHeroAiSettings,
            )

            SectionHeader("Integrations")
            NavigationRow(
                title = "Health Connect",
                onClick = onHealthConnect,
            )
            Spacer(Modifier.height(12.dp))
            WearOsSettingsSection()
            Spacer(Modifier.height(12.dp))
            var webhookInput by remember(webhookUrl) { mutableStateOf(webhookUrl) }
            OutlinedTextField(
                value = webhookInput,
                onValueChange = { newUrl ->
                    webhookInput = newUrl
                    scope.launch { settingsDataStore.setWebhookUrl(newUrl) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Local webhook URL") },
                placeholder = { Text("http://192.168.1.10:8123/webhook") },
                singleLine = true,
                supportingText = {
                    Text("A JSON payload is POSTed here whenever a new glucose entry is saved.")
                },
            )

            SectionHeader("Clinical tools")
            NavigationRow(
                title = "Basal and carb-ratio tests",
                onClick = onClinicalTests,
            )
            Spacer(Modifier.height(8.dp))
            NavigationRow(
                title = "Emergency hypo SOS",
                onClick = onEmergencySos,
            )

            SectionHeader("Data & Backup")
            NavigationRow(
                title = "Data & Backup",
                onClick = onBackup,
            )

            SectionHeader("Appearance")
            NavigationRow(
                title = "Appearance",
                onClick = onAppearance,
            )

            Spacer(Modifier.height(24.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    "GlucoseHero is a logging tool, not a medical device. Hero's " +
                        "answers are informational. Always confirm treatment " +
                        "decisions with your care team.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
