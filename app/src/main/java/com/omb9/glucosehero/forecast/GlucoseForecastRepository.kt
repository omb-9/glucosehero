package com.omb9.glucosehero.forecast

import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.domain.model.DosingProfileLoad
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.CarbAbsorptionCalculator
import com.omb9.glucosehero.util.IobCalculator
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString

/**
 * Loads recent CGM/manual glucose, boluses, and meals from Room, runs
 * [GlucoseForecastEngine], and persists the latest snapshot in DataStore so
 * the home/stats UI can render a 30-60 minute projection without recomputing
 * on every frame.
 */
@Singleton
class GlucoseForecastRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val settingsDataStore: SettingsDataStore,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun observeForecast(): Flow<GlucoseForecastSnapshot?> = combine(
        entryDao.observeGlucoseReadingsPoints(System.currentTimeMillis() - LOOKBACK_MILLIS),
        settingsDataStore.dosingProfile,
        ticker(),
    ) { _, _, _ ->
        compute(clock.instant())
    }

    suspend fun refresh(now: Instant = clock.instant()): GlucoseForecastSnapshot {
        val snapshot = compute(now)
        settingsDataStore.setGlucoseForecastJson(AppJson.encodeToString(snapshot))
        return snapshot
    }

    suspend fun latestPersisted(): GlucoseForecastSnapshot? {
        val raw = settingsDataStore.glucoseForecastJson.first()
        if (raw.isNullOrBlank()) return null
        return runCatching { AppJson.decodeFromString<GlucoseForecastSnapshot>(raw) }.getOrNull()
    }

    private suspend fun compute(now: Instant): GlucoseForecastSnapshot {
        val since = now.toEpochMilli() - LOOKBACK_MILLIS
        val points = entryDao.glucoseReadingPointsSince(since)
        val entries = entryDao.entriesSince(since)
        val load = settingsDataStore.dosingProfileSnapshot()
        val zoneId = ZoneId.systemDefault()
        when (load) {
            is DosingProfileLoad.Invalid -> {
                return GlucoseForecastEngine.forecast(
                    GlucoseForecastInput(
                        samples = points.map {
                            GlucoseForecastInput.GlucoseSample(it.timestamp, it.glucoseMgdl)
                        },
                        boluses = emptyList(),
                        meals = emptyList(),
                        diaHours = load.diaHours.toDouble(),
                        cirRatio = 0.0,
                        isfMgdl = 0.0,
                        dosingProfile = com.omb9.glucosehero.domain.model.DosingProfile(
                            diaHours = load.diaHours,
                            segments = emptyList(),
                        ),
                        zoneId = zoneId,
                    ),
                    now,
                )
            }
            is DosingProfileLoad.Valid -> {
                val resolved = load.profile.toBolusSettings(now, zoneId)
                val input = GlucoseForecastInput(
                    samples = points.map {
                        GlucoseForecastInput.GlucoseSample(it.timestamp, it.glucoseMgdl)
                    },
                    boluses = entries.mapNotNull { entry ->
                        val units = entry.insulinBolusUnits ?: return@mapNotNull null
                        IobCalculator.BolusEntry(entry.timestamp, units)
                    },
                    meals = entries.mapNotNull { entry ->
                        val grams = entry.carbsGrams ?: return@mapNotNull null
                        CarbAbsorptionCalculator.CarbEntry(entry.timestamp, grams.toDouble())
                    },
                    diaHours = load.profile.diaHours.toDouble(),
                    cirRatio = resolved.cirRatio.toDouble(),
                    isfMgdl = resolved.isfMgdl.toDouble(),
                    dosingProfile = load.profile,
                    zoneId = zoneId,
                )
                return GlucoseForecastEngine.forecast(input, now)
            }
        }
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(TICK_MILLIS)
        }
    }

    companion object {
        private val LOOKBACK_MILLIS: Long =
            6L * IobCalculator.MILLIS_PER_HOUR
        private const val TICK_MILLIS: Long = 60_000L
    }
}
