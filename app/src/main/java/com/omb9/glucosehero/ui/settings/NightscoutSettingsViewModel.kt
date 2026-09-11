package com.omb9.glucosehero.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.data.cgm.CgmIngestService
import com.omb9.glucosehero.data.cgm.NightscoutCgmSource
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutAuthMode
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutEndpointGuard
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutErrorCodes
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutLimits
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutPollCoordinator
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutUrls
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.data.remote.TrustedHosts
import com.omb9.glucosehero.data.security.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for a Nightscout connection test. [errorCode] is a
 * [NightscoutErrorCodes] token, never a URL or credential.
 *
 * FEATURE: cgm-direct-ingest
 */
sealed interface NightscoutTestUiState {
    data object Idle : NightscoutTestUiState
    data object Running : NightscoutTestUiState
    data class Success(val parsedCount: Int) : NightscoutTestUiState
    data class Failure(val errorCode: String) : NightscoutTestUiState
}

/**
 * Settings for the Nightscout REST poller. Credentials are encrypted with
 * [KeystoreManager] before DataStore; plaintext never persists.
 *
 * FEATURE: cgm-direct-ingest
 */
@HiltViewModel
class NightscoutSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val keystoreManager: KeystoreManager,
    private val nightscoutSource: NightscoutCgmSource,
    private val ingestService: CgmIngestService,
    private val pollCoordinator: NightscoutPollCoordinator,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settingsDataStore.nightscoutEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val url: StateFlow<String> = settingsDataStore.nightscoutUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val authMode: StateFlow<NightscoutAuthMode> = settingsDataStore.nightscoutAuthMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightscoutAuthMode.TOKEN)

    val hasCredential: StateFlow<Boolean> = settingsDataStore.hasNightscoutCredential
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val backfillHours: StateFlow<Int> = settingsDataStore.nightscoutBackfillHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NightscoutLimits.DEFAULT_BACKFILL_HOURS)

    val foregroundService: StateFlow<Boolean> = settingsDataStore.nightscoutForegroundService
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val lastSuccessMillis: StateFlow<Long?> =
        settingsDataStore.cgmLastIngestSuccess(GlucoseSampleSource.NIGHTSCOUT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastError: StateFlow<String?> =
        settingsDataStore.cgmLastIngestError(GlucoseSampleSource.NIGHTSCOUT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val use24HourTime: StateFlow<Boolean> = settingsDataStore.settings
        .map { it.use24HourTime }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val endpointStatus: StateFlow<NightscoutEndpointGuard.Status> = combine(
        settingsDataStore.nightscoutUrl,
        settingsDataStore.acknowledgedNightscoutHosts,
    ) { raw, hosts ->
        if (raw.isBlank()) NightscoutEndpointGuard.Status.InvalidUrl
        else NightscoutEndpointGuard.evaluate(raw.trim(), hosts)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        NightscoutEndpointGuard.Status.InvalidUrl,
    )

    private val _testState = MutableStateFlow<NightscoutTestUiState>(NightscoutTestUiState.Idle)
    val testState: StateFlow<NightscoutTestUiState> = _testState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setNightscoutEnabled(enabled)
            pollCoordinator.applyFlags()
        }
    }

    fun setUrl(url: String) {
        viewModelScope.launch { settingsDataStore.setNightscoutUrl(url) }
    }

    fun setAuthMode(mode: NightscoutAuthMode) {
        viewModelScope.launch { settingsDataStore.setNightscoutAuthMode(mode) }
    }

    fun saveCredential(plain: String) {
        val trimmed = plain.trim()
        viewModelScope.launch {
            if (trimmed.isEmpty()) {
                settingsDataStore.setEncryptedNightscoutCredential(null)
            } else {
                settingsDataStore.setEncryptedNightscoutCredential(
                    keystoreManager.encrypt(trimmed),
                )
            }
        }
    }

    fun acknowledgeHost() {
        val host = NightscoutUrls.parseBase(url.value)?.host
            ?: TrustedHosts.hostOf(url.value)
            ?: return
        viewModelScope.launch { settingsDataStore.acknowledgeNightscoutHost(host) }
    }

    fun setBackfillHours(hours: Int) {
        viewModelScope.launch {
            settingsDataStore.setNightscoutBackfillHours(hours)
        }
    }

    fun setForegroundService(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setNightscoutForegroundService(enabled)
            pollCoordinator.applyFlags()
        }
    }

    fun testConnection() {
        if (_testState.value is NightscoutTestUiState.Running) return
        viewModelScope.launch {
            _testState.value = NightscoutTestUiState.Running
            try {
                val since = System.currentTimeMillis() - 15 * 60_000L
                val samples = nightscoutSource.fetch(since)
                if (settingsDataStore.nightscoutEnabledSnapshot()) {
                    ingestService.ingest(samples)
                }
                _testState.value = NightscoutTestUiState.Success(samples.size)
            } catch (e: CancellationException) {
                _testState.value = NightscoutTestUiState.Idle
                throw e
            } catch (e: NightscoutException) {
                _testState.value = NightscoutTestUiState.Failure(e.publicErrorCode)
            } catch (_: Exception) {
                _testState.value = NightscoutTestUiState.Failure(NightscoutErrorCodes.NETWORK)
            }
        }
    }
}
