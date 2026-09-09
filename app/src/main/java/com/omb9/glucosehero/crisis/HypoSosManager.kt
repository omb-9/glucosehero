package com.omb9.glucosehero.crisis

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.forecast.GlucoseForecastEngine
import com.omb9.glucosehero.forecast.GlucoseForecastInput
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.CrisisDetector
import dagger.hilt.android.qualifiers.ApplicationContext
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
        var sentAny = false
        if (hasSmsPermission() && contacts.isNotEmpty()) {
            contacts.forEach { contact ->
                sentAny = sendSms(contact.phone, body) || sentAny
            }
        }
        settingsDataStore.setHypoSosLastSentMillis(System.currentTimeMillis())
        cancelLocked()
        notifier.notifyDispatchResult(sent = sentAny, contactCount = contacts.size)
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
            diaHours = 4.0,
            cirRatio = 10.0,
            isfMgdl = 50.0,
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
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
    }

    private fun cancelTimeoutAlarm() {
        context.getSystemService(AlarmManager::class.java).cancel(timeoutPendingIntent())
    }

    private fun timeoutPendingIntent(): PendingIntent {
        val intent = Intent(context, HypoSosAlarmReceiver::class.java).apply {
            action = ACTION_TIMEOUT
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_TIMEOUT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startService() {
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
        return providers.firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
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

    private fun sendSms(phone: String, body: String): Boolean {
        val normalized = phone.filter { it.isDigit() || it == '+' }
        if (normalized.isBlank()) return false
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(normalized, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(normalized, null, body, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

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

    companion object {
        const val ACTION_TIMEOUT = "com.omb9.glucosehero.action.HYPO_SOS_TIMEOUT"
        const val ACTION_DISMISS = "com.omb9.glucosehero.action.HYPO_SOS_DISMISS"
        private const val REQUEST_TIMEOUT = 4301
        const val DISMISS_COOLDOWN_MILLIS: Long = 30L * 60_000L
        const val DISPATCH_COOLDOWN_MILLIS: Long = 15L * 60_000L
    }
}
