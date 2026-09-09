package com.omb9.glucosehero.forecast

import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.CarbAbsorptionCalculator
import com.omb9.glucosehero.util.IobCalculator
import java.time.Instant
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
) {

    fun observeForecast(): Flow<GlucoseForecastSnapshot?> = combine(
        entryDao.observeGlucoseReadingsPoints(System.currentTimeMillis() - LOOKBACK_MILLIS),
        settingsDataStore.bolusSettings,
        ticker(),
    ) { _, _, _ ->
        compute(Instant.now())
    }

    suspend fun refresh(now: Instant = Instant.now()): GlucoseForecastSnapshot {
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
        val bolus = settingsDataStore.bolusSettingsSnapshot()
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
            diaHours = bolus.diaHours.toDouble(),
            cirRatio = bolus.cirRatio.toDouble(),
            isfMgdl = bolus.isfMgdl.toDouble(),
        )
        return GlucoseForecastEngine.forecast(input, now)
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
