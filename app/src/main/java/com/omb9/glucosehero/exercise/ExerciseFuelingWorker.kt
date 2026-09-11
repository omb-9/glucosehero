package com.omb9.glucosehero.exercise

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.omb9.glucosehero.data.health.HealthConnectRepository
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.util.IobCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Background check: high-intensity activity (Health Connect exercise, heart
 * rate, active calories) while IOB is elevated. Advises 15 g fast-acting
 * carbs. Respects notification permission and a cooldown to avoid spam.
 */
@HiltWorker
class ExerciseFuelingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthConnectRepository: HealthConnectRepository,
    private val entryDao: EntryDao,
    private val settingsDataStore: SettingsDataStore,
    private val notifier: ExerciseFuelingNotifier,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!settingsDataStore.exerciseFuelingAlertsEnabled.first()) return Result.success()
        return try {
            val now = clock.instant()
            val since = now.minusMillis(EXERCISE_LOOKBACK_MINUTES * 60_000L)
            val load = settingsDataStore.dosingProfileSnapshot()
            val diaHours = when (load) {
                is com.omb9.glucosehero.domain.model.DosingProfileLoad.Valid -> {
                    // Resolve at evaluation time. DIA is global; ISF/CIR are not
                    // inputs to [ExerciseFuelingEvaluator] (IOB uses DIA only).
                    load.profile.toBolusSettings(now, java.time.ZoneId.systemDefault())
                        .diaHours.toDouble()
                }
                is com.omb9.glucosehero.domain.model.DosingProfileLoad.Invalid -> {
                    return Result.success()
                }
            }
            val entries = entryDao.entriesSince(now.toEpochMilli() - 6 * IobCalculator.MILLIS_PER_HOUR)
            val iob = IobCalculator.activeInsulinOnBoard(
                boluses = entries.mapNotNull { entry ->
                    val units = entry.insulinBolusUnits ?: return@mapNotNull null
                    IobCalculator.BolusEntry(entry.timestamp, units)
                },
                diaHours = diaHours,
                now = now,
            )
            val profile = settingsDataStore.profileSnapshot()
            val input = ExerciseFuelingInput(
                nowMillis = now.toEpochMilli(),
                iobUnits = iob,
                sessions = healthConnectRepository.readRecentExerciseSessions(since),
                heartRate = healthConnectRepository.readRecentHeartRate(since),
                calories = healthConnectRepository.readRecentActiveCalories(since),
                userAgeYears = profile.age,
            )
            val decision = ExerciseFuelingEvaluator.evaluate(input)
            if (decision.shouldAlert && !inCooldown(now.toEpochMilli())) {
                notifier.notifyFuelingAdvice()
                settingsDataStore.setExerciseFuelingLastAlertMillis(now.toEpochMilli())
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun inCooldown(nowMillis: Long): Boolean {
        val last = settingsDataStore.exerciseFuelingLastAlertMillis.first() ?: return false
        return nowMillis - last < COOLDOWN_MILLIS
    }

    companion object {
        const val UNIQUE_NAME = "exercise_fueling_alerts"
        const val COOLDOWN_MILLIS: Long = 90L * 60_000L

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<ExerciseFuelingWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
