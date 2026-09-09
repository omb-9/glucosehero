package com.omb9.glucosehero.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.omb9.glucosehero.wear.data.WearGlucoseStore
import com.omb9.glucosehero.wear.data.WearPhoneMessenger
import com.omb9.glucosehero.wear.protocol.WearQuickEntryType
import com.omb9.glucosehero.wear.protocol.WearSyncProtocol
import kotlinx.coroutines.launch

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

private val Accent = Color(0xFFFF5252)

@Composable
fun WearApp() {
    val context = LocalContext.current
    val store = remember(context) { WearGlucoseStore.get(context) }
    val snapshot by store.snapshot.collectAsStateWithLifecycle(
        initialValue = com.omb9.glucosehero.wear.data.WearGlucoseSnapshot(),
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { WearPhoneMessenger.requestGlucose(context) }
    }

    MaterialTheme {
        AppScaffold {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Glucose",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = snapshot.displayValue,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                    )
                    if (snapshot.trend.arrow.isNotEmpty()) {
                        Text(
                            text = " ${snapshot.trend.arrow}",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    text = snapshot.unitLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = snapshot.ageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (snapshot.lastQuickEntry.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = snapshot.lastQuickEntry,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                WearPhoneMessenger.sendQuickEntry(
                                    context,
                                    WearQuickEntryType.WATER,
                                    WearSyncProtocol.DEFAULT_WATER_ML,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("H2O", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                WearPhoneMessenger.sendQuickEntry(
                                    context,
                                    WearQuickEntryType.CARBS,
                                    WearSyncProtocol.DEFAULT_CARBS_G,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Carb", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                WearPhoneMessenger.sendQuickEntry(
                                    context,
                                    WearQuickEntryType.INSULIN,
                                    WearSyncProtocol.DEFAULT_INSULIN_U,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Ins", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
