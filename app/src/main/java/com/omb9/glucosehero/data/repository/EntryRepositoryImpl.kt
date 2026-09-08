package com.omb9.glucosehero.data.repository

import com.omb9.glucosehero.data.health.HealthConnectRepository
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.toDomain
import com.omb9.glucosehero.data.local.entity.toEntity
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.DailyGlucoseSummary
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseStats
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.StreakCalculator
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Singleton
class EntryRepositoryImpl @Inject constructor(
    private val entryDao: EntryDao,
    private val settingsDataStore: SettingsDataStore,
    @Named("webhook") private val webhookClient: OkHttpClient,
    private val healthConnectRepository: HealthConnectRepository? = null,
) : EntryRepository {

    override fun observeEntries(sinceMillis: Long): Flow<List<LogEvent>> =
        entryDao.observeEventsSince(sinceMillis).map { list -> list.map { it.toLogEvent() } }

    override fun observeGlucose(sinceMillis: Long): Flow<List<LogEvent>> =
        entryDao.observeGlucoseEventsSince(sinceMillis).map { list -> list.map { it.toLogEvent() } }

    override fun observeGlucoseStats(sinceMillis: Long): Flow<GlucoseStats> =
        entryDao.observeGlucoseStatsSince(sinceMillis)

    override fun observeCurrentStreak(): Flow<Int> =
        entryDao.observeLoggedDays(STREAK_SINCE_MILLIS).map { rows ->
            StreakCalculator.currentStreak(rows.map { it.day })
        }

    override suspend fun distinctLoggedDays(): Set<LocalDate> =
        entryDao.loggedDaysSince(0L)
            .mapNotNull { row -> runCatching { LocalDate.parse(row.day) }.getOrNull() }
            .toSet()

    override suspend fun currentStreak(): Int =
        entryDao.loggedDaysSince(STREAK_SINCE_MILLIS).let { rows ->
            StreakCalculator.currentStreak(rows.map { it.day })
        }

    override fun observeEntry(id: Long): Flow<LogEvent?> =
        entryDao.observeById(id).map { it?.toLogEvent() }

    override suspend fun add(event: LogEvent): Long {
        val entity = event.toEntity()
        val id = entryDao.insert(entity)
        val entityWithId = entity.copy(id = id)
        healthConnectRepository?.writeEntry(entityWithId, updatedAtMillis = System.currentTimeMillis())
        broadcastWebhook(entityWithId)
        return id
    }

    override suspend fun update(event: LogEvent) {
        val entity = event.toEntity()
        entryDao.update(entity)
        healthConnectRepository?.writeEntry(entity, updatedAtMillis = System.currentTimeMillis())
    }

    override suspend fun delete(id: Long) {
        entryDao.deleteById(id)
        healthConnectRepository?.deleteEntry(id)
    }

    override suspend fun timeInRangeSince(
        sinceMillis: Long,
        lowMgdl: Double,
        highMgdl: Double,
    ): Double? = entryDao.timeInRangeSince(sinceMillis, lowMgdl, highMgdl)

    override suspend fun dailySummaries(sinceMillis: Long, limit: Int): List<DailyGlucoseSummary> =
        entryDao.dailySummaries(sinceMillis, limit)

    override suspend fun recentEntries(limit: Int): List<LogEvent> =
        entryDao.recentEntries(limit).map { it.toLogEvent() }

    override suspend fun entriesSince(sinceMillis: Long): List<LogEvent> =
        entryDao.entriesSince(sinceMillis).map { it.toLogEvent() }

    override suspend fun latestGlucoseReading(): GlucosePointRow? =
        entryDao.latestGlucoseReading()

    /**
     * Best-effort local-automation broadcast. Fires only for a freshly saved
     * manual glucose entry with a configured URL; failures (bad URL, no
     * connectivity, unreachable endpoint) are swallowed so a log entry is
     * never blocked by an automation target being offline.
     */
    private suspend fun broadcastWebhook(entity: EntryEntity) {
        if (entity.source != EntrySource.MANUAL || entity.glucoseMgdl == null) return

        val url = settingsDataStore.webhookUrlSnapshot().takeIf { it.isNotBlank() } ?: return
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val json = runCatching {
            AppJson.encodeToString(WebhookEntryPayload.serializer(), entity.toWebhookPayload())
        }.getOrNull() ?: return

        val request = Request.Builder()
            .url(httpUrl)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        webhookClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) = response.close()
        })
    }

    private companion object {
        const val STREAK_SINCE_MILLIS = 0L
    }
}

private fun EntryEntity.toLogEvent(): LogEvent = toDomain().copy(source = source)

@Serializable
internal data class WebhookEntryPayload(
    val id: Long,
    val timestamp: Long,
    val glucoseMgdl: Double? = null,
    val mealContext: MealContext? = null,
    val insulinBasalUnits: Double? = null,
    val insulinBolusUnits: Double? = null,
    val carbsGrams: Int? = null,
    val proteinGrams: Int? = null,
    val fatGrams: Int? = null,
    val mealDescription: String? = null,
    val exerciseMinutes: Int? = null,
    val exerciseIntensity: ActivityIntensity? = null,
    val note: String? = null,
    val moodScore: Int? = null,
    val moodLabel: String? = null,
    val source: EntrySource = EntrySource.MANUAL,
)

private fun EntryEntity.toWebhookPayload() = WebhookEntryPayload(
    id = id,
    timestamp = timestamp,
    glucoseMgdl = glucoseMgdl,
    mealContext = mealContext,
    insulinBasalUnits = insulinBasalUnits,
    insulinBolusUnits = insulinBolusUnits,
    carbsGrams = carbsGrams,
    proteinGrams = proteinGrams,
    fatGrams = fatGrams,
    mealDescription = mealDescription,
    exerciseMinutes = exerciseMinutes,
    exerciseIntensity = exerciseIntensity,
    note = note,
    moodScore = moodScore,
    moodLabel = moodLabel,
    source = source,
)
