package com.omb9.glucosehero.crisis

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.forecast.GlucoseForecastEngine
import com.omb9.glucosehero.forecast.GlucoseForecastInput
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.CrisisDetector
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

/**
 * Severe-hypo SOS: prompt, 5-minute dismiss window, then caregiver SMS
 * with GPS and trend. Journal-text crisis matching stays on [CrisisDetector].
 */
@Singleton
class HypoSosManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val settingsDataStore: SettingsDataStore,
    private val notifier: HypoSosNotifier,
    private val smsSender: HypoSosSmsSender,
) {

    private val mutex = Mutex()
    private val _pending = MutableStateFlow<HypoSosPending?>(null)
    val pending: StateFlow<HypoSosPending?> = _pending.asStateFlow()

    val caregivers: Flow<List<CaregiverContact>> = settingsDataStore.caregiverContacts

    suspend fun hydrate() {
        _pending.value = settingsDataStore.hypoSosPending.first()
    }

    suspend fun evaluateLatest(now: Instant = Instant.now()) {
        if (!settingsDataStore.hypoSosEnabled.first()) return
        val latest = entryDao.latestGlucoseReading() ?: return
        val trend = trendSnapshot(latest.glucoseMgdl, latest.timestamp, now)
        mutex.withLock {
            val current = settingsDataStore.hypoSosPending.first()
            if (current != null) {
                if (CrisisDetector.isHypoRecovered(latest.glucoseMgdl)) {
                    cancelLocked()
                }
                return
            }
            if (!CrisisDetector.isSevereHypoglycemia(latest.glucoseMgdl)) return
            if (inPostDismissCooldown(now.toEpochMilli())) return
            startCountdownLocked(trend, now)
        }
    }

    suspend fun dismissPrompt() {
        mutex.withLock {
            cancelLocked()
            settingsDataStore.setHypoSosLastDismissMillis(System.currentTimeMillis())
        }
        notifier.cancelPrompt()
        stopService()
    }

    suspend fun onTimeout() {
        mutex.withLock {
            val pending = settingsDataStore.hypoSosPending.first() ?: return
            if (System.currentTimeMillis() + 1_000L < pending.timeoutAtMillis) return
            testOnTimeout?.invoke()
            if (testSkipDispatch) {
                cancelLocked()
                return
            }
            dispatchLocked(pending)
        }
    }

    fun launchPromptUi() {
        val intent = Intent(context, HypoSosPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private suspend fun startCountdownLocked(trend: GlucoseTrendSnapshot, now: Instant) {
        val timeoutMinutes = CrisisDetector.clampSosTimeoutMinutes(
            settingsDataStore.hypoSosTimeoutMinutes.first(),
        )
        val pending = HypoSosPending(
            startedAtMillis = now.toEpochMilli(),
            timeoutAtMillis = now.toEpochMilli() + timeoutMinutes * 60_000L,
            glucoseMgdl = trend.glucoseMgdl,
            velocityMgdlPerMin = trend.velocityMgdlPerMin,
            trendLabel = trendLabel(trend.velocityMgdlPerMin),
        )
        settingsDataStore.setHypoSosPendingJson(AppJson.encodeToString(pending))
        _pending.value = pending
        scheduleTimeoutAlarm(pending.timeoutAtMillis)
        notifier.notifyPrompt(pending)
        startService()
        // Full-screen intent is suppressed while the device is unlocked and this
        // process is in the foreground, so start the prompt activity ourselves.
        // When the keyguard is locked, leave FSI as the only launcher.
        if (shouldLaunchPromptUiDirectly()) {
            runCatching { launchPromptUi() }
        }
    }

    /**
     * True when [launchPromptUi] should open [HypoSosPromptActivity] because
     * Android will not deliver the notification full-screen intent.
     */
    private fun shouldLaunchPromptUiDirectly(): Boolean {
        val appForegrounded = ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
        if (!appForegrounded) return false
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return keyguard?.isKeyguardLocked != true
    }

    private suspend fun cancelLocked() {
        settingsDataStore.setHypoSosPendingJson(null)
        _pending.value = null
        cancelTimeoutAlarm()
    }

    private suspend fun dispatchLocked(pending: HypoSosPending) {
        val lastSent = settingsDataStore.hypoSosLastSentMillis.first() ?: 0L
        if (System.currentTimeMillis() - lastSent < DISPATCH_COOLDOWN_MILLIS) {
            cancelLocked()
            return
        }
        val contacts = settingsDataStore.caregiverContacts.first()
            .filter { it.phone.isNotBlank() }
        val location = lastKnownLocation()
        val body = sosMessage(pending, location)
        val hasSms = hasSmsPermission()
        val results = contacts.map { contact ->
            val status = smsSender.dispatch(contact.phone, body, hasSms)
            RecipientSmsResult(
                label = contact.name.ifBlank { contact.phone },
                status = status,
            )
        }
        settingsDataStore.setHypoSosLastSentMillis(System.currentTimeMillis())
        cancelLocked()
        notifier.notifyDispatchResult(results)
        stopService()
    }

    private suspend fun trendSnapshot(
        glucoseMgdl: Double,
        timestamp: Long,
        now: Instant,
    ): GlucoseTrendSnapshot {
        val since = now.toEpochMilli() - 90L * 60_000L
        val points = entryDao.glucoseReadingPointsSince(since)
        val input = GlucoseForecastInput(
            samples = points.map {
                GlucoseForecastInput.GlucoseSample(it.timestamp, it.glucoseMgdl)
            },
            boluses = emptyList(),
            meals = emptyList(),
            diaHours = UNUSED_SOS_FORECAST_DIA_HOURS,
            cirRatio = UNUSED_SOS_FORECAST_CIR_RATIO,
            isfMgdl = UNUSED_SOS_FORECAST_ISF_MGDL,
        )
        val forecast = GlucoseForecastEngine.forecast(input, now)
        return GlucoseTrendSnapshot(
            glucoseMgdl = glucoseMgdl,
            timestampMillis = timestamp,
            velocityMgdlPerMin = forecast.velocityMgdlPerMin,
        )
    }

    private fun scheduleTimeoutAlarm(atMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = timeoutPendingIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(
                TAG,
                "Exact SOS timeout alarm denied; falling back to inexact setAndAllowWhileIdle",
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
    }

    /**
     * Cancels the AlarmManager entry and the [PendingIntent] token.
     * AlarmManager.cancel() alone leaves the token alive; dumpsys "Recent"
     * / "Alarm Stats" can still list HYPO_SOS_TIMEOUT after that, which is
     * history, not a live batch. Cancelling the token as well prevents a
     * stale fire from re-entering [onTimeout] if an OEM still held the alarm.
     */
    private fun cancelTimeoutAlarm() {
        val existing = existingTimeoutPendingIntent() ?: return
        context.getSystemService(AlarmManager::class.java).cancel(existing)
        existing.cancel()
    }

    private fun timeoutIntent(): Intent =
        Intent(context, HypoSosAlarmReceiver::class.java).apply {
            action = ACTION_TIMEOUT
        }

    private fun timeoutPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_TIMEOUT,
            timeoutIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun existingTimeoutPendingIntent(): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_TIMEOUT,
            timeoutIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun startService() {
        if (testSkipForegroundService) return
        val intent = Intent(context, HypoSosForegroundService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private fun stopService() {
        context.stopService(Intent(context, HypoSosForegroundService::class.java))
    }

    private suspend fun inPostDismissCooldown(nowMillis: Long): Boolean {
        val last = settingsDataStore.hypoSosLastDismissMillis.first() ?: return false
        return nowMillis - last < DISMISS_COOLDOWN_MILLIS
    }

    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        @SuppressLint("MissingPermission")
        fun read(provider: String): Location? =
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        return providers.firstNotNullOfOrNull { read(it) }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS,
        ) == PackageManager.PERMISSION_GRANTED

    private fun sosMessage(pending: HypoSosPending, location: Location?): String {
        val maps = if (location != null) {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "location unavailable"
        }
        return "GlucoseHero SOS: severe low glucose ${pending.glucoseMgdl.toInt()} mg/dL, " +
            "trend ${pending.trendLabel}. $maps"
    }

    private fun trendLabel(velocity: Double): String = when {
        velocity < -0.5 -> "falling fast"
        velocity < -0.15 -> "falling"
        velocity > 0.15 -> "rising"
        else -> "steady"
    }

    /**
     * Instrumentation: persist a pending SOS and schedule the AlarmManager
     * backstop without starting [HypoSosForegroundService] or notifying.
     */
    internal suspend fun armTimeoutBackstopForTest(timeoutAtMillis: Long) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val pending = HypoSosPending(
                startedAtMillis = now,
                timeoutAtMillis = timeoutAtMillis,
                glucoseMgdl = 40.0,
                velocityMgdlPerMin = -1.0,
                trendLabel = "falling",
            )
            settingsDataStore.setHypoSosPendingJson(AppJson.encodeToString(pending))
            _pending.value = pending
            scheduleTimeoutAlarm(timeoutAtMillis)
        }
    }

    internal suspend fun cancelPendingForTest() {
        mutex.withLock { cancelLocked() }
        stopService()
    }

    internal fun timeoutAlarmTokenExists(): Boolean = existingTimeoutPendingIntent() != null

    companion object {
        const val ACTION_TIMEOUT = "com.omb9.glucosehero.action.HYPO_SOS_TIMEOUT"
        const val ACTION_DISMISS = "com.omb9.glucosehero.action.HYPO_SOS_DISMISS"
        private const val TAG = "HypoSosManager"
        private const val REQUEST_TIMEOUT = 4301
        const val DISMISS_COOLDOWN_MILLIS: Long = 30L * 60_000L
        const val DISPATCH_COOLDOWN_MILLIS: Long = 15L * 60_000L

        /**
         * Instrumentation-only. Null in production. Lets tests observe
         * [onTimeout] without relying on SMS side effects.
         */
        @Volatile
        internal var testOnTimeout: (() -> Unit)? = null

        /**
         * Instrumentation-only. When true, [onTimeout] clears pending state
         * without sending caregiver SMS.
         */
        @Volatile
        internal var testSkipDispatch: Boolean = false

        /**
         * Instrumentation-only. When true, [startService] is a no-op so the
         * AlarmManager path can be proven with the poll loop stopped.
         */
        @Volatile
        internal var testSkipForegroundService: Boolean = false

        /**
         * Unused on this path: [trendSnapshot] currently passes empty bolus and
         * meal lists, so DIA/CIR/ISF never affect the SOS trend (only CGM
         * velocity). If dose lists are ever populated, replace these with a
         * real [com.omb9.glucosehero.domain.model.DosingProfile] lookup and
         * refuse when that profile is invalid.
         *
         * FEATURE: dosing-profiles
         */
        const val UNUSED_SOS_FORECAST_DIA_HOURS: Double = 4.0
        const val UNUSED_SOS_FORECAST_CIR_RATIO: Double = 10.0
        const val UNUSED_SOS_FORECAST_ISF_MGDL: Double = 50.0
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HypoSosEntryPoint {
    fun hypoSosManager(): HypoSosManager
    fun settingsDataStore(): SettingsDataStore
}
