package com.omb9.glucosehero.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.omb9.glucosehero.data.billing.BillingRepository
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.ChatMessageDao
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import com.omb9.glucosehero.data.local.entity.toDomain
import com.omb9.glucosehero.data.remote.AiApi
import com.omb9.glucosehero.data.remote.dto.ApiChatMessage
import com.omb9.glucosehero.data.remote.dto.ApiFunction
import com.omb9.glucosehero.data.remote.dto.ApiTool
import com.omb9.glucosehero.data.remote.dto.ChatCompletionRequest
import com.omb9.glucosehero.data.remote.dto.OpenRouterProviderConfig
import com.omb9.glucosehero.data.remote.sse.SseChatClient
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.MealPhotoAnalysis
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.QuotaExhaustedException
import com.omb9.glucosehero.domain.model.StreamEvent
import com.omb9.glucosehero.domain.model.TagKind
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.repository.ChatRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.util.AiQuota
import com.omb9.glucosehero.util.AiTier
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.work.PendingQueryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.roundToInt
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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
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
    private val database: GlucoseHeroDatabase,
    private val settingsDataStore: SettingsDataStore,
    private val settingsRepository: SettingsRepository,
    private val billingRepository: BillingRepository,
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
        val aiConfig = withContext(Dispatchers.IO) { settingsRepository.aiConfigSnapshot() }
        val tier = resolveTier(aiConfig)

        if (tier != AiTier.BYOK) {
            val used = settingsDataStore.aiQuotaUsedTodaySnapshot()
            if (AiQuota.isExhausted(tier, used)) {
                emit(StreamEvent.Failure(QuotaExhaustedException(quotaExhaustedMessage(tier))))
                return@flow
            }
        }

        val messages = withContext(Dispatchers.Default) { buildApiMessages(history) }
        var succeeded = false
        sseChatClient.stream(
            config = config,
            messages = messages,
            tools = buildPrefillTools(),
            maxTokens = managedMaxTokens(tier),
            openRouterDataCollectionDeny = aiConfig.provider == AiProvider.OPENROUTER,
        )
            .buffer(Channel.UNLIMITED)
            .collect { event ->
                if (event is StreamEvent.Done) succeeded = true
                emit(event)
            }

        if (succeeded && tier != AiTier.BYOK) {
            settingsDataStore.incrementAiQuota()
        }
    }

    override suspend fun completeReply(history: List<ChatTurn>): String {
        val aiConfig = settingsRepository.aiConfigSnapshot()
        val tier = resolveTier(aiConfig)

        if (tier != AiTier.BYOK) {
            val used = settingsDataStore.aiQuotaUsedTodaySnapshot()
            if (AiQuota.isExhausted(tier, used)) {
                throw QuotaExhaustedException(quotaExhaustedMessage(tier))
            }
        }

        val messages = buildApiMessages(history)
        val response = aiApi.complete(
            ChatCompletionRequest(
                model = aiConfig.model,
                messages = messages,
                stream = false,
                maxTokens = managedMaxTokens(tier),
                provider = if (aiConfig.provider == AiProvider.OPENROUTER) OpenRouterProviderConfig() else null,
            )
        )
        val content = response.choices.firstOrNull()?.message?.contentText()
            ?: error("Empty completion response")

        if (tier != AiTier.BYOK) {
            settingsDataStore.incrementAiQuota()
        }
        return content
    }

    override suspend fun analyzeMealPhoto(imageDataUri: String): MealPhotoAnalysis {
        val aiConfig = settingsRepository.aiConfigSnapshot()
        val tier = resolveTier(aiConfig)

        if (tier != AiTier.BYOK) {
            val used = settingsDataStore.aiQuotaUsedTodaySnapshot()
            if (AiQuota.isExhausted(tier, used)) {
                throw QuotaExhaustedException(quotaExhaustedMessage(tier))
            }
        }

        val system = ApiChatMessage.text(
            role = "system",
            content = MEAL_PHOTO_SYSTEM_PROMPT,
        )
        val user = ApiChatMessage.multimodal(
            role = "user",
            prompt = "Estimate the nutrition in this meal photo.",
            imageDataUri = imageDataUri,
        )

        val response = aiApi.complete(
            ChatCompletionRequest(
                model = aiConfig.model,
                messages = listOf(system, user),
                stream = false,
                temperature = 0.0,
                maxTokens = managedMaxTokens(tier),
                provider = if (aiConfig.provider == AiProvider.OPENROUTER) OpenRouterProviderConfig() else null,
            )
        )
        val raw = response.choices.firstOrNull()?.message?.contentText()
            ?: error("Empty meal-photo response")

        if (tier != AiTier.BYOK) {
            settingsDataStore.incrementAiQuota()
        }

        return parseMealPhotoAnalysis(raw)
    }

    /** Parses the model's JSON estimate, tolerating prose around the object. */
    private fun parseMealPhotoAnalysis(raw: String): MealPhotoAnalysis {
        val trimmed = raw.trim()
        // Try the whole body first, then fall back to the first {...} block so
        // a model that wraps the JSON in an explanation still parses.
        val candidates = buildList {
            add(trimmed)
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start >= 0 && end > start) add(trimmed.substring(start, end + 1))
        }
        for (candidate in candidates) {
            val parsed = runCatching {
                AppJson.decodeFromString(MealPhotoAnalysis.serializer(), candidate)
            }.getOrNull()
            if (parsed != null) return parsed
        }
        // No valid JSON: still surface whatever free-text description we got.
        return MealPhotoAnalysis(description = trimmed.takeIf { it.isNotBlank() })
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

    private suspend fun resolveTier(config: AiConfig): AiTier {
        if (config.provider == AiProvider.OPENROUTER && !config.hasApiKey) {
            return if (billingRepository.isPremium.first()) AiTier.PRO else AiTier.FREE
        }
        return AiTier.BYOK
    }

    private fun managedMaxTokens(tier: AiTier): Int? =
        if (tier == AiTier.BYOK) null else MAX_MANAGED_REPLY_TOKENS

    private fun quotaExhaustedMessage(tier: AiTier): String = when (tier) {
        AiTier.FREE -> "You've used all 10 free AI calls today. They reset at midnight. " +
            "Upgrade to Pro for 50 calls a day, or add your own API key for unlimited calls."
        AiTier.PRO -> "You've used all 50 Pro AI calls today. They reset at midnight. " +
            "Add your own API key for unlimited calls."
        AiTier.BYOK -> error("BYOK is never quota-exhausted")
    }

    private suspend fun buildApiMessages(history: List<ChatTurn>): List<ApiChatMessage> {
        val system = ApiChatMessage.text(role = "system", content = buildSystemPrompt())
        val turns = history
            .takeLast(MAX_HISTORY_TURNS)
            .map { turn ->
                ApiChatMessage.text(
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
            val avg7Deferred = async { entryDao.averageGlucoseReadingsSince(Formatters.daysAgoMillis(7)) }
            val avg14Deferred = async { entryDao.averageGlucoseReadingsSince(Formatters.daysAgoMillis(14)) }
            val avg30Deferred = async { entryDao.averageGlucoseReadingsSince(Formatters.daysAgoMillis(30)) }
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
            val cgmCountDeferred = async {
                entryDao.cgmReadingCountSince(Formatters.daysAgoMillis(14))
            }
            val manualCountDeferred = async {
                entryDao.manualReadingCountSince(Formatters.daysAgoMillis(14))
            }
            val foodDeferred = async {
                val dismissed = settingsDataStore.dismissedFoodTags.first()
                database.tagAnalyticDao().observeAll().first()
                    .filter {
                        it.kind != TagKind.MOOD &&
                            it.occurrences >= FOOD_PATTERN_MIN_OCCURRENCES && it.tag !in dismissed
                    }
                    .take(FOOD_PATTERN_LIMIT)
            }

            val settings = settingsDeferred.await()
            val unit = settings.unit
            val profile = profileDeferred.await()
            val avg7 = avg7Deferred.await()
            val avg14 = avg14Deferred.await()
            val avg30 = avg30Deferred.await()
            val tir14 = tir14Deferred.await()
            val daily = dailyDeferred.await()
            val recent = recentDeferred.await()
            val cgmCount = cgmCountDeferred.await()
            val manualCount = manualCountDeferred.await()
            val foodTags = foodDeferred.await()

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
            val glucoseSource =
                "Glucose source: CGM ($cgmCount samples, 14d) · Manual entries: $manualCount"

            val persona = buildPersonaSection(profile)

            val foodBlock = buildString {
                append("--- Food patterns (median 2h glucose change, from the user's log) ---")
                if (foodTags.isEmpty()) {
                    append("\nNone meet the ${FOOD_PATTERN_MIN_OCCURRENCES}-occurrence threshold yet.")
                } else {
                    foodTags.forEach { tag ->
                        append('\n')
                        append(tag.tag)
                        append(": ")
                        append(Formatters.signedGlucoseWithUnit(tag.medianDeltaMgdl, unit))
                        append(" over ")
                        append(tag.occurrences)
                        append(" occurrences (avg ")
                        append(tag.avgCarbsGrams?.roundToInt()?.toString() ?: "n/a")
                        append("g carbs, ")
                        append(tag.avgBolusUnits?.let { "%.1f".format(it) } ?: "n/a")
                        append("u bolus)")
                    }
                }
            }

            persona + "\n\n" + """
                |You are Hero, the in-app assistant of GlucoseHero, a personal glucose logging app.
                |Be concise, warm, and concrete. Ground every answer in the user's data below.
                |Keep replies under roughly 100 words.
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
                |$glucoseSource
                |
                |--- Daily summaries, last 14 days ---
                |$dailyBlock
                |
                |--- Most recent 30 entries ---
                |$recentBlock
            """.trimMargin() + "\n\n" + foodBlock
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
                    "from the user's message so the app can pre-fill a new log entry. " +
                    "Report glucose exactly as the user stated it with its unit, and never convert units.",
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
                                "glucose_value",
                                buildJsonObject {
                                    put("type", "number")
                                    put(
                                        "description",
                                        "The glucose value exactly as the user stated it, without converting units.",
                                    )
                                },
                            )
                            put(
                                "glucose_unit",
                                buildJsonObject {
                                    put("type", "string")
                                    put(
                                        "enum",
                                        buildJsonArray {
                                            add("mg/dL")
                                            add("mmol/L")
                                        },
                                    )
                                    put(
                                        "description",
                                        "The unit the user stated the value in. If the user did not say, " +
                                            "use the unit their log is displayed in.",
                                    )
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
        const val MAX_MANAGED_REPLY_TOKENS = 200
        const val FOOD_PATTERN_MIN_OCCURRENCES = 3
        const val FOOD_PATTERN_LIMIT = 3

        const val MEAL_PHOTO_SYSTEM_PROMPT =
            "You are Hero, the nutrition assistant inside GlucoseHero. Estimate the " +
                "nutritional macros of the meal in the photo. Return ONLY a JSON object " +
                "with exactly these keys: \"carbs_grams\" (integer), \"protein_grams\" " +
                "(integer), \"fat_grams\" (integer), and \"description\" (a short phrase " +
                "naming the meal). Do not add any text outside the JSON object."
    }
}
