package com.omb9.glucosehero.crisis

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.theme.GlucoseHeroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Full-screen severe-hypo prompt. Dismissing cancels the caregiver SOS timeout.
 */
@AndroidEntryPoint
class HypoSosPromptActivity : ComponentActivity() {

    @Inject lateinit var manager: HypoSosManager
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        val settings = runBlocking { settingsRepository.settings.first() }
        setContent {
            GlucoseHeroTheme(settings = settings) {
                val pending by manager.pending.collectAsStateWithLifecycle()
                var hydrated by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    manager.hydrate()
                    hydrated = true
                }
                LaunchedEffect(hydrated, pending) {
                    if (hydrated && pending == null) finish()
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    pending?.let { snapshot ->
                        val scope = rememberCoroutineScope()
                        HypoSosPromptContent(
                            pending = snapshot,
                            onDismiss = {
                                scope.launch {
                                    manager.dismissPrompt()
                                    finish()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HypoSosPromptContent(
    pending: HypoSosPending,
    onDismiss: () -> Unit,
) {
    var remainingMs by remember { mutableLongStateOf(pending.timeoutAtMillis - System.currentTimeMillis()) }
    LaunchedEffect(pending.timeoutAtMillis) {
        while (true) {
            remainingMs = (pending.timeoutAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remainingMs <= 0L) return@LaunchedEffect
            delay(250L)
        }
    }
    val seconds = (remainingMs / 1000L).toInt()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.hypo_sos_prompt_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                R.string.hypo_sos_prompt_body,
                pending.glucoseMgdl.toInt(),
                pending.trendLabel,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(
                R.string.hypo_sos_prompt_countdown,
                seconds / 60,
                seconds % 60,
            ),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.notif_hypo_sos_im_ok))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.hypo_sos_prompt_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
