package com.omb9.glucosehero.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.cgm.CgmIngestService
import com.omb9.glucosehero.data.cgm.GlucoseFreshness
import com.omb9.glucosehero.data.cgm.GlucoseFreshnessRepository
import com.omb9.glucosehero.data.cgm.NightscoutCgmSource
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutErrorCodes
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutPollCoordinator
import com.omb9.glucosehero.data.cgm.xdrip.XdripBroadcastAllowlist
import com.omb9.glucosehero.data.health.HealthConnectAvailability
import com.omb9.glucosehero.data.health.HealthConnectRepository
import com.omb9.glucosehero.data.health.HealthConnectStatus
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.work.HealthConnectSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Snapshot for the Data Sources hub. Enable flags keep their DataStore
 * defaults (xDrip and Nightscout off; Health Connect glucose import stays
 * on if it already was). [counts] are trailing 24 h sample rows per source,
 * not last-poll time.
 *
 * FEATURE: cgm-direct-ingest
 */
data class DataSourcesUiState(
    val xdripEnabled: Boolean = false,
    val nightscoutEnabled: Boolean = false,
    val hcGlucoseImportEnabled: Boolean = true,
    val use24HourTime: Boolean = false,
    val hcRevoked: Boolean = false,
    val hcConnected: Boolean = false,
    val hcStatus: HealthConnectStatus = HealthConnectStatus.UNAVAILABLE,
    val xdripLastSuccess: Long? = null,
    val nightscoutLastSuccess: Long? = null,
    val hcLastSync: Long? = null,
    val xdripLastError: String? = null,
    val nightscoutLastError: String? = null,
    val hcLastError: String? = null,
    val xdripCount24h: Int = 0,
    val nightscoutCount24h: Int = 0,
    val hcCount24h: Int = 0,
    val freshness: GlucoseFreshness? = null,
)

/**
 * Hub ViewModel for CGM ingest sources. Detailed setup stays on the
 * existing xDrip, Nightscout, and Health Connect screens.
 *
 * FEATURE: cgm-direct-ingest
 */
@HiltViewModel
class DataSourcesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val glucoseSampleDao: GlucoseSampleDao,
    private val nightscoutSource: NightscoutCgmSource,
    private val ingestService: CgmIngestService,
    private val pollCoordinator: NightscoutPollCoordinator,
    private val healthConnectAvailability: HealthConnectAvailability,
    private val healthConnectRepository: HealthConnectRepository,
    private val freshnessRepository: GlucoseFreshnessRepository,
) : ViewModel() {

    private val hcStatus: HealthConnectStatus = healthConnectAvailability.status()

    private val _hcConnected = MutableStateFlow(false)
    private val _counts = MutableStateFlow(Counts())
    private val _xdripTest = MutableStateFlow<XdripListenResult?>(null)
    private val _hcTest = MutableStateFlow<DataSourceTestUiState>(DataSourceTestUiState.Idle)
    private val _nightscoutTest = MutableStateFlow<NightscoutTestUiState>(NightscoutTestUiState.Idle)

    val xdripTest: StateFlow<XdripListenResult?> = _xdripTest.asStateFlow()
    val hcTest: StateFlow<DataSourceTestUiState> = _hcTest.asStateFlow()
    val nightscoutTest: StateFlow<NightscoutTestUiState> = _nightscoutTest.asStateFlow()

    private data class Flags(
        val xdripEnabled: Boolean,
        val nightscoutEnabled: Boolean,
        val hcGlucoseImportEnabled: Boolean,
        val use24HourTime: Boolean,
        val hcRevoked: Boolean,
    )

    private data class Times(
        val xdripLastSuccess: Long?,
        val nightscoutLastSuccess: Long?,
        val hcLastSync: Long?,
        val xdripLastError: String?,
        val nightscoutLastError: String?,
        val hcLastError: String?,
    )

    private data class Counts(
        val xdrip: Int = 0,
        val nightscout: Int = 0,
        val healthConnect: Int = 0,
    )

    private val flags = combine(
        settingsDataStore.xdripBroadcastEnabled,
        settingsDataStore.nightscoutEnabled,
        settingsDataStore.glucoseImportEnabled,
        settingsDataStore.settings.map { it.use24HourTime },
        settingsDataStore.healthConnectRevoked,
    ) { xdrip, nightscout, hcGlucose, use24, revoked ->
        Flags(xdrip, nightscout, hcGlucose, use24, revoked)
    }

    private val times = combine(
        combine(
            settingsDataStore.cgmLastIngestSuccess(GlucoseSampleSource.XDRIP_BROADCAST),
            settingsDataStore.cgmLastIngestSuccess(GlucoseSampleSource.NIGHTSCOUT),
            settingsDataStore.healthConnectLastSync,
        ) { xdrip, nightscout, hc -> Triple(xdrip, nightscout, hc) },
        combine(
            settingsDataStore.cgmLastIngestError(GlucoseSampleSource.XDRIP_BROADCAST),
            settingsDataStore.cgmLastIngestError(GlucoseSampleSource.NIGHTSCOUT),
            settingsDataStore.cgmLastIngestError(GlucoseSampleSource.HEALTH_CONNECT),
        ) { xdrip, nightscout, hc -> Triple(xdrip, nightscout, hc) },
    ) { success, errors ->
        Times(
            xdripLastSuccess = success.first,
            nightscoutLastSuccess = success.second,
            hcLastSync = success.third,
            xdripLastError = errors.first,
            nightscoutLastError = errors.second,
            hcLastError = errors.third,
        )
    }

    val uiState: StateFlow<DataSourcesUiState> = combine(
        flags,
        times,
        _counts,
        _hcConnected,
        freshnessRepository.observe(),
    ) { flag, time, counts, connected, freshness ->
        DataSourcesUiState(
            xdripEnabled = flag.xdripEnabled,
            nightscoutEnabled = flag.nightscoutEnabled,
            hcGlucoseImportEnabled = flag.hcGlucoseImportEnabled,
            use24HourTime = flag.use24HourTime,
            hcRevoked = flag.hcRevoked,
            hcConnected = connected,
            hcStatus = hcStatus,
            xdripLastSuccess = time.xdripLastSuccess,
            nightscoutLastSuccess = time.nightscoutLastSuccess,
            hcLastSync = time.hcLastSync,
            xdripLastError = time.xdripLastError,
            nightscoutLastError = time.nightscoutLastError,
            hcLastError = time.hcLastError,
            xdripCount24h = counts.xdrip,
            nightscoutCount24h = counts.nightscout,
            hcCount24h = counts.healthConnect,
            freshness = freshness,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DataSourcesUiState(hcStatus = hcStatus),
    )

    init {
        refresh()
    }

    /** Re-reads 24 h counts and Health Connect permission state. */
    fun refresh() {
        viewModelScope.launch {
            refreshCounts()
            val granted = healthConnectRepository.grantedPermissions()
            _hcConnected.value = granted.containsAll(healthConnectRepository.readPermissions)
        }
    }

    fun setXdripEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setXdripBroadcastEnabled(enabled) }
    }

    fun setNightscoutEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setNightscoutEnabled(enabled)
            pollCoordinator.applyFlags()
        }
    }

    fun setHcGlucoseImportEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setGlucoseImportEnabled(enabled) }
    }

    /**
     * xDrip is local push. The result is last successful ingest for
     * [GlucoseSampleSource.XDRIP_BROADCAST], or waiting / no sender app.
     */
    fun testXdrip() {
        viewModelScope.launch {
            val enabled = settingsDataStore.xdripBroadcastEnabledSnapshot()
            val last = settingsDataStore.cgmLastIngestSuccessSnapshot(
                GlucoseSampleSource.XDRIP_BROADCAST,
            )
            _xdripTest.value = DataSourcesCopy.classifyXdripListen(
                enabled = enabled,
                allowlistedInstalled = XdripBroadcastAllowlist.isAnyAllowlistedAppInstalled(context),
                lastSuccessMillis = last,
            )
        }
    }

    /**
     * Nightscout v1 GET test. Same path as [NightscoutSettingsViewModel]:
     * token stays in the query string inside the source; this method never
     * logs the URL. A v3-only host surfaces [NightscoutErrorCodes.V1_ONLY].
     */
    fun testNightscout() {
        if (_nightscoutTest.value is NightscoutTestUiState.Running) return
        viewModelScope.launch {
            _nightscoutTest.value = NightscoutTestUiState.Running
            try {
                val since = System.currentTimeMillis() - 15 * 60_000L
                val samples = nightscoutSource.fetch(since)
                if (settingsDataStore.nightscoutEnabledSnapshot()) {
                    ingestService.ingest(samples)
                }
                _nightscoutTest.value = NightscoutTestUiState.Success(samples.size)
                refreshCounts()
            } catch (e: CancellationException) {
                _nightscoutTest.value = NightscoutTestUiState.Idle
                throw e
            } catch (e: NightscoutException) {
                _nightscoutTest.value = NightscoutTestUiState.Failure(e.publicErrorCode)
            } catch (_: Exception) {
                _nightscoutTest.value = NightscoutTestUiState.Failure(NightscoutErrorCodes.NETWORK)
            }
        }
    }

    /**
     * Health Connect availability + granted read permissions. When connected,
     * enqueues an expedited sync so last-sync can move.
     */
    fun testHealthConnect() {
        if (_hcTest.value is DataSourceTestUiState.Running) return
        viewModelScope.launch {
            _hcTest.value = DataSourceTestUiState.Running
            when (hcStatus) {
                HealthConnectStatus.UNAVAILABLE -> {
                    _hcTest.value = DataSourceTestUiState.Failure(
                        R.string.data_sources_hc_unavailable,
                    )
                }
                HealthConnectStatus.UPDATE_REQUIRED -> {
                    _hcTest.value = DataSourceTestUiState.Failure(
                        R.string.data_sources_hc_update,
                    )
                }
                HealthConnectStatus.AVAILABLE -> {
                    val granted = healthConnectRepository.grantedPermissions()
                    val connected = granted.containsAll(healthConnectRepository.readPermissions)
                    _hcConnected.value = connected
                    if (!connected) {
                        val revoked = settingsDataStore.healthConnectRevoked.first()
                        _hcTest.value = DataSourceTestUiState.Failure(
                            if (revoked) {
                                R.string.data_sources_hc_revoked
                            } else {
                                R.string.data_sources_hc_not_connected
                            },
                        )
                    } else {
                        HealthConnectSyncWorker.enqueueExpedited(
                            context,
                            ExistingWorkPolicy.REPLACE,
                        )
                        refreshCounts()
                        _hcTest.value = DataSourceTestUiState.Success(
                            R.string.data_sources_hc_test_ok,
                        )
                    }
                }
            }
        }
    }

    private suspend fun refreshCounts() {
        val since = DataSourcesCopy.sampleWindowStart(System.currentTimeMillis())
        _counts.value = Counts(
            xdrip = glucoseSampleDao.countSince(GlucoseSampleSource.XDRIP_BROADCAST, since),
            nightscout = glucoseSampleDao.countSince(GlucoseSampleSource.NIGHTSCOUT, since),
            healthConnect = glucoseSampleDao.countSince(GlucoseSampleSource.HEALTH_CONNECT, since),
        )
    }
}
