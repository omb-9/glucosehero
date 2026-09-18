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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.ReasoningEffort

/**
 * Hero AI overview: enable, data-use copy, remaining managed-tier calls, and a
 * nested row into bring-your-own-key settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    onByokSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfig.collectAsStateWithLifecycle()
    val heroAiRemainingCalls by viewModel.heroAiRemainingCalls.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hero AI", maxLines = 2) },
                expandedHeight = settingsTopBarExpandedHeight(),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
        ) {
            SettingsToggleRow(
                label = "Enable Hero AI",
                checked = settings.isHeroAiEnabled,
                onCheckedChange = viewModel::setIsHeroAiEnabled,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = HeroAiSettingsCopy.DATA_USE_DESCRIPTION,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "Model reasoning",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ReasoningEffort.entries.forEachIndexed { index, effort ->
                    SegmentedButton(
                        selected = settings.reasoningEffort == effort,
                        onClick = { viewModel.setReasoningEffort(effort) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ReasoningEffort.entries.size,
                        ),
                    ) {
                        Text(effort.label)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = HeroAiSettingsCopy.REASONING_DESCRIPTION,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            heroAiRemainingCalls?.let { remaining ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = HeroAiSettingsCopy.remainingCallsSettings(remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            NavigationRow(
                title = "Use your own API key",
                subtitle = HeroAiSettingsCopy.byokSubtitle(aiConfig),
                onClick = onByokSettings,
            )
        }
    }
}
