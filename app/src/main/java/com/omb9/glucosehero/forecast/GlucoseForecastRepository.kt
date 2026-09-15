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
        entryDao.observeGlucoseReadingsMaxTimestamp(),
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
        val nowMillis = now.toEpochMilli()
        val points = entryDao.glucoseReadingPointsSince(nowMillis - GLUCOSE_LOOKBACK_MILLIS)
        val samples = points.map {
            GlucoseForecastInput.GlucoseSample(it.timestamp, it.glucoseMgdl)
        }
        val load = settingsDataStore.dosingProfileSnapshot()
        val zoneId = ZoneId.systemDefault()
        when (load) {
            is DosingProfileLoad.Invalid -> {
                return GlucoseForecastEngine.forecast(
                    GlucoseForecastInput(
                        samples = samples,
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
                val diaHours = load.profile.diaHours.toDouble()
                val entriesSince = nowMillis - maxOf(
                    IobCalculator.lookbackMillis(diaHours),
                    MEAL_LOOKBACK_MILLIS,
                )
                val entries = entryDao.entriesSince(entriesSince)
                val resolved = load.profile.toBolusSettings(now, zoneId)
                val input = GlucoseForecastInput(
                    samples = samples,
                    boluses = entries.mapNotNull { entry ->
                        val units = entry.insulinBolusUnits ?: return@mapNotNull null
                        IobCalculator.BolusEntry(entry.timestamp, units)
                    },
                    meals = entries.mapNotNull { entry ->
                        val grams = entry.carbsGrams ?: return@mapNotNull null
                        CarbAbsorptionCalculator.CarbEntry(entry.timestamp, grams.toDouble())
                    },
                    diaHours = diaHours,
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
        /** Matches [GlucoseForecastEngine.LOOKBACK_MINUTES]; older CGM is unused. */
        private val GLUCOSE_LOOKBACK_MILLIS: Long =
            GlucoseForecastEngine.LOOKBACK_MINUTES * 60_000L

        /**
         * Matches [GlucoseForecastInput.carbActionHours] default
         * ([CarbAbsorptionCalculator.DEFAULT_ACTION_HOURS]).
         */
        private val MEAL_LOOKBACK_MILLIS: Long =
            (CarbAbsorptionCalculator.DEFAULT_ACTION_HOURS * IobCalculator.MILLIS_PER_HOUR).toLong()

        private const val TICK_MILLIS: Long = 60_000L
    }
}
