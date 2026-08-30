package com.omb9.glucosehero.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ============ Appearance ============
            SectionHeader("Appearance")

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size,
                        ),
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.AMOLED -> "AMOLED"
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Accent color",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentColor.entries.forEach { accent ->
                    val selected = settings.accent == accent
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(accent.argb), CircleShape)
                            .clickable { viewModel.setAccent(accent) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = accent.label,
                                tint = Color.Black,
                            )
                        }
                    }
                }
            }

            // ============ Glucose ============
            SectionHeader("Glucose")

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GlucoseUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = settings.unit == unit,
                        onClick = { viewModel.setUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = GlucoseUnit.entries.size,
                        ),
                    ) {
                        Text(unit.label)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            var rangeValue by remember(settings.targetLowMgdl, settings.targetHighMgdl) {
                mutableStateOf(settings.targetLowMgdl..settings.targetHighMgdl)
            }
            Text(
                "Target range: " +
                    Formatters.glucose(rangeValue.start.toDouble(), settings.unit) +
                    " – " +
                    Formatters.glucose(rangeValue.endInclusive.toDouble(), settings.unit) +
                    " ${settings.unit.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RangeSlider(
                value = rangeValue,
                onValueChange = { rangeValue = it },
                onValueChangeFinished = {
                    viewModel.setTargetRange(rangeValue.start, rangeValue.endInclusive)
                },
                valueRange = 40f..300f,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("24-hour time", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.use24HourTime,
                    onCheckedChange = viewModel::setUse24HourTime,
                )
            }

            // ============ Hero AI ============
            SectionHeader("Hero AI")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
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
                                    viewModel.setAiProvider(provider)
                                    providerExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Local text state avoids cursor jumps; DataStore follows each edit,
                // and the dynamic interceptor reads the latest value per request.
                var baseUrl by remember(aiConfig.provider) { mutableStateOf(aiConfig.baseUrl) }
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

                var apiKeyInput by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(if (aiConfig.hasApiKey) "API key (saved)" else "API key")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        Text(
                            if (aiConfig.hasApiKey) {
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
                    onClick = {
                        viewModel.saveApiKey(apiKeyInput.trim())
                        apiKeyInput = ""
                    },
                    enabled = apiKeyInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save API key")
                }
            }

            Spacer(Modifier.height(24.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    "GlucoseHero is a logging tool, not a medical device. Hero's " +
                        "answers are informational — always confirm treatment " +
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

@Composable
private fun SectionHeader(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(12.dp))
}
