package com.omb9.glucosehero.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.omb9.glucosehero.data.local.db.ChatMessageDao
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import com.omb9.glucosehero.data.local.entity.toDomain
import com.omb9.glucosehero.data.remote.AiApi
import com.omb9.glucosehero.data.remote.dto.ApiChatMessage
import com.omb9.glucosehero.data.remote.dto.ApiFunction
import com.omb9.glucosehero.data.remote.dto.ApiTool
import com.omb9.glucosehero.data.remote.sse.SseChatClient
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.StreamEvent
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.repository.ChatRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.work.PendingQueryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatMessageDao: ChatMessageDao,
    private val pendingAiQueryDao: PendingAiQueryDao,
    private val entryDao: EntryDao,
    private val settingsRepository: SettingsRepository,
    private val sseChatClient: SseChatClient,
    private val aiApi: AiApi,
) : ChatRepository {

    override fun observeHistory(): Flow<List<ChatTurn>> =
        chatMessageDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePendingCount(): Flow<Int> = pendingAiQueryDao.observeCount()

    override suspend fun appendUserMessage(text: String): Long =
        chatMessageDao.insert(
            ChatMessageEntity(
                role = ChatRole.USER,
                content = text,
                timestamp = System.currentTimeMillis(),
            )
        )

    override suspend fun appendAssistantMessage(text: String): Long =
        chatMessageDao.insert(
            ChatMessageEntity(
                role = ChatRole.ASSISTANT,
                content = text,
                timestamp = System.currentTimeMillis(),
            )
        )

    override suspend fun clearHistory() = chatMessageDao.clear()

    override fun streamReply(history: List<ChatTurn>): Flow<StreamEvent> = flow {
        // Resolved fresh per request: provider/key changes apply immediately.
        // KeyStore decrypt + Base64 decode are I/O-bound; message assembly is CPU-bound.
        val config = withContext(Dispatchers.IO) { settingsRepository.resolveAiConfig() }
        val messages = withContext(Dispatchers.Default) { buildApiMessages(history) }
        sseChatClient.stream(config, messages, buildPrefillTools())
            .buffer(Channel.UNLIMITED)
            .collect { emit(it) }
    }

    override suspend fun completeReply(history: List<ChatTurn>): String {
        val messages = buildApiMessages(history)
        val response = aiApi.complete(
            com.omb9.glucosehero.data.remote.dto.ChatCompletionRequest(
                model = settingsRepository.aiConfigSnapshot().model,
                messages = messages,
                stream = false,
            )
        )
        return response.choices.firstOrNull()?.message?.content
            ?: error("Empty completion response")
    }

    override suspend fun queueOffline(userMessageId: Long, prompt: String) {
        pendingAiQueryDao.insert(
            PendingAiQueryEntity(
                userMessageId = userMessageId,
                prompt = prompt,
                createdAt = System.currentTimeMillis(),
            )
        )
        val request = OneTimeWorkRequestBuilder<PendingQueryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PendingQueryWorker.UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private suspend fun buildApiMessages(history: List<ChatTurn>): List<ApiChatMessage> {
        val system = ApiChatMessage(role = "system", content = buildSystemPrompt())
        val turns = history
            .takeLast(MAX_HISTORY_TURNS)
            .map { turn ->
                ApiChatMessage(
                    role = if (turn.role == ChatRole.USER) "user" else "assistant",
                    content = turn.content,
                )
            }
        return listOf(system) + turns
    }

    /**
     * Context assembly offloaded to SQLite: rolling averages (7/14/30d), time in
     * range, and the 14-day GROUP BY day payload are computed at the database
     * level with native aggregates — never by iterating rows in Kotlin.
     */
    override suspend fun buildSystemPrompt(): String {
        return coroutineScope {
            val settingsDeferred = async { settingsRepository.settings.first() }
            val profileDeferred = async { settingsRepository.profile.first() }
            val avg7Deferred = async { entryDao.averageGlucoseSince(Formatters.daysAgoMillis(7)) }
            val avg14Deferred = async { entryDao.averageGlucoseSince(Formatters.daysAgoMillis(14)) }
            val avg30Deferred = async { entryDao.averageGlucoseSince(Formatters.daysAgoMillis(30)) }
            val tir14Deferred = async {
                val currentSettings = settingsDeferred.await()
                entryDao.timeInRangeSince(
                    Formatters.daysAgoMillis(14),
                    currentSettings.targetLowMgdl.toDouble(),
                    currentSettings.targetHighMgdl.toDouble(),
                )
            }
            val dailyDeferred = async {
                entryDao.dailySummaries(Formatters.daysAgoMillis(14), limit = 14)
            }
            val recentDeferred = async { entryDao.recentEntries(limit = 30) }

            val settings = settingsDeferred.await()
            val unit = settings.unit
            val profile = profileDeferred.await()
            val avg7 = avg7Deferred.await()
            val avg14 = avg14Deferred.await()
            val avg30 = avg30Deferred.await()
            val tir14 = tir14Deferred.await()
            val daily = dailyDeferred.await()
            val recent = recentDeferred.await()

            fun fmt(mgdl: Double?): String =
                mgdl?.let { Formatters.glucose(it, unit) } ?: "n/a"

            val dailyBlock = if (daily.isEmpty()) "No glucose readings yet." else
                daily.joinToString("\n") { d ->
                    "${d.day}: avg ${fmt(d.avgMgdl)}, min ${fmt(d.minMgdl)}, " +
                        "max ${fmt(d.maxMgdl)} (${d.readings} readings)"
                }

            val recentBlock = if (recent.isEmpty()) "No entries yet." else
                recent.joinToString("\n") { e ->
                    val date = Formatters.localDate(e.timestamp)
                    val time = Formatters.time(e.timestamp, settings.use24HourTime)
                    val metrics = listOfNotNull(
                        e.glucoseMgdl?.let { "glucose ${Formatters.glucoseWithUnit(it, unit)}" },
                        e.insulinBasalUnits?.let { "basal insulin ${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)} u" },
                        e.insulinBolusUnits?.let { "bolus insulin ${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)} u" },
                        e.carbsGrams?.let { "carbs $it g" },
                        e.proteinGrams?.let { "protein $it g" },
                        e.fatGrams?.let { "fat $it g" },
                        e.mealDescription?.takeIf { it.isNotBlank() },
                        e.exerciseMinutes?.let { "exercise $it min" },
                    ).joinToString(", ").ifBlank { "note" }
                    val note = e.note?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
                    "$date $time $metrics$note"
                }

            val targetLow = Formatters.glucose(settings.targetLowMgdl.toDouble(), unit)
            val targetHigh = Formatters.glucose(settings.targetHighMgdl.toDouble(), unit)
            val tirPct = tir14?.let { "%.0f%%".format(it * 100) } ?: "n/a"

            val persona = buildPersonaSection(profile)

            persona + "\n\n" + """
                |You are Hero, the in-app assistant of GlucoseHero, a personal glucose logging app.
                |Be concise, warm, and concrete. Ground every answer in the user's data below.
                |You are not a medical professional: never give insulin dosing instructions or
                |diagnoses, and remind the user to confirm treatment decisions with their care
                |team when the topic calls for it.
                |
                |If the user provides health metrics, dietary intake, or insulin doses, you MUST
                |use the `prefill_log_draft` tool to extract the data. Do not just reply with text.
                |
                |=== USER DATA (generated ${java.time.LocalDateTime.now()}) ===
                |Display unit: ${unit.label} (all values below are in this unit)
                |Target range: $targetLow – $targetHigh ${unit.label}
                |Rolling averages: 7d ${fmt(avg7)} · 14d ${fmt(avg14)} · 30d ${fmt(avg30)}
                |Time in range (14d): $tirPct
                |
                |--- Daily summaries, last 14 days ---
                |$dailyBlock
                |
                |--- Most recent 30 entries ---
                |$recentBlock
            """.trimMargin()
        }
    }

    private fun buildPersonaSection(profile: UserProfile): String {
        val name = profile.name.trim().takeIf { it.isNotEmpty() }
        val details = listOfNotNull(
            profile.age?.let { "Age: $it" },
            profile.diabetesType?.trim()?.takeIf { it.isNotEmpty() }?.let { "Type: $it" },
            profile.heightCm?.let { "Height: ${formatMetric(it)} cm" },
            profile.weightKg?.let { "Weight: ${formatMetric(it)} kg" },
        ).joinToString(", ")

        val identity = buildString {
            append(name ?: "the user")
            if (details.isNotEmpty()) append(" ($details)")
        }

        return if (profile.profileTarget == ProfileTarget.SELF) {
            "You are assisting $identity. Address the user directly using second-person pronouns ('you', 'your')."
        } else {
            val relationship = profile.profileTarget.displayName.removePrefix("My ").lowercase()
            val description = buildString {
                append(relationship)
                if (name != null) append(" named $name")
                if (details.isNotEmpty()) append(" ($details)")
            }
            val referred = name ?: "them"
            "You are assisting a caregiver/guardian managing diabetes data for their $description. " +
                "When providing insights, summaries, and meal/insulin discussions, refer to " +
                "$referred in the third person ('they', 'their', '$referred') and address " +
                "the logged user as their caregiver."
        }
    }

    private fun formatMetric(value: Float): String =
        if (value % 1.0f == 0.0f) value.toInt().toString() else "%.1f".format(value)

    /**
     * Function-calling schema for the `prefill_log_draft` tool. All parameters
     * are optional because users mention only the metrics they actually logged;
     * the app leaves untouched slots blank in the pre-filled AddEntrySheet.
     */
    private fun buildPrefillTools(): List<ApiTool> = listOf(
        ApiTool(
            type = "function",
            function = ApiFunction(
                name = "prefill_log_draft",
                description = "Extract health metrics, dietary intake, and insulin doses " +
                    "from the user's message so the app can pre-fill a new log entry.",
                parameters = buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "insulin_basal_units",
                                buildJsonObject {
                                    put("type", "number")
                                    put("description", "Long-acting basal insulin units")
                                },
                            )
                            put(
                                "insulin_bolus_units",
                                buildJsonObject {
                                    put("type", "number")
                                    put("description", "Rapid-acting bolus insulin units")
                                },
                            )
                            put(
                                "carbs_grams",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("description", "Grams of carbohydrates eaten")
                                },
                            )
                            put(
                                "glucose_mgdl",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("description", "Glucose reading in mg/dL")
                                },
                            )
                            put(
                                "meal_description",
                                buildJsonObject {
                                    put("type", "string")
                                    put("description", "Short description of the meal or food eaten")
                                },
                            )
                            put(
                                "exercise_minutes",
                                buildJsonObject {
                                    put("type", "integer")
                                    put("description", "Minutes of exercise")
                                },
                            )
                        },
                    )
                },
            ),
        ),
    )

    private companion object {
        const val MAX_HISTORY_TURNS = 20
    }
}
