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
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryTtl
import com.omb9.glucosehero.data.local.entity.toDomain
import com.omb9.glucosehero.data.remote.AiApi
import com.omb9.glucosehero.data.remote.dto.ApiChatMessage
import com.omb9.glucosehero.data.remote.dto.ApiFunction
import com.omb9.glucosehero.data.remote.dto.ApiTool
import com.omb9.glucosehero.data.remote.dto.ChatCompletionRequest
import com.omb9.glucosehero.data.remote.dto.OpenRouterProviderConfig
import com.omb9.glucosehero.data.remote.dto.ReasoningConfig
import com.omb9.glucosehero.data.nlp.QuickLogNlp
import com.omb9.glucosehero.data.remote.sse.SseChatClient
import com.omb9.glucosehero.data.vision.MealPhotoCapture
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.ApiKeyMissingException
import com.omb9.glucosehero.domain.model.ChatContextSummary
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ChatTurnKind
import com.omb9.glucosehero.domain.model.CrisisInterceptedException
import com.omb9.glucosehero.domain.model.ChatPipelineCopy
import com.omb9.glucosehero.domain.model.ChatPipelineStatus
import com.omb9.glucosehero.domain.model.MealPhotoAnalysis
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.ProviderHttpException
import com.omb9.glucosehero.domain.model.QuotaExhaustedException
import com.omb9.glucosehero.domain.model.QuickLogParseResult
import com.omb9.glucosehero.domain.model.ReasoningEffort
import com.omb9.glucosehero.domain.model.StreamEvent
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.repository.ChatRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.util.AiQuota
import com.omb9.glucosehero.util.AiTier
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.ChatCrisisGate
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.util.IobCalculator
import com.omb9.glucosehero.util.TagImpactCopy
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
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatMessageDao: ChatMessageDao,
    private val pendingAiQueryDao: PendingAiQueryDao,
    private val entryDao: EntryDao,
    private val analyticsRepository: AnalyticsRepository,
    private val settingsDataStore: SettingsDataStore,
    private val settingsRepository: SettingsRepository,
    private val billingRepository: BillingRepository,
    private val sseChatClient: SseChatClient,
    private val aiApi: AiApi,
) : ChatRepository {

    override fun observeHistory(): Flow<List<ChatTurn>> =
        chatMessageDao.observeAll().map { list -> list.asReversed().map { it.toDomain() } }

    override fun observeRecentHistory(limit: Int): Flow<List<ChatTurn>> =
        chatMessageDao.observeRecent(limit).map { list -> list.asReversed().map { it.toDomain() } }

    override suspend fun loadOlderHistory(
        beforeTimestamp: Long,
        beforeId: Long,
        limit: Int,
    ): List<ChatTurn> =
        chatMessageDao.pageOlder(beforeTimestamp, beforeId, limit)
            .asReversed()
            .map { it.toDomain() }

    override fun observePendingCount(): Flow<Int> = pendingAiQueryDao.observeCount()

    override fun observePendingUserMessageIds(): Flow<List<Long>> =
        pendingAiQueryDao.observePendingUserMessageIds()

    override fun observeGlucosePointCount(): Flow<Int> = entryDao.observeGlucosePointCount()

    override suspend fun appendUserMessage(text: String): Long =
        chatMessageDao.insert(
            ChatMessageEntity(
                role = ChatRole.USER,
                content = text,
                timestamp = System.currentTimeMillis(),
            )
        )

    override suspend fun appendAssistantMessage(
        text: String,
        kind: ChatTurnKind,
    ): Long =
        chatMessageDao.insert(
            ChatMessageEntity(
                role = ChatRole.ASSISTANT,
                content = text,
                timestamp = System.currentTimeMillis(),
                messageKind = kind,
            )
        )

    override suspend fun deleteMessage(id: Long) = chatMessageDao.deleteById(id)

    override suspend fun clearHistory() = chatMessageDao.clear()

    override fun streamReply(history: List<ChatTurn>): Flow<StreamEvent> = flow {
        if (ChatCrisisGate.blocksModelDispatch(history)) {
            return@flow
        }
        val safeHistory = ChatCrisisGate.historyForModel(history)
        if (safeHistory.none { it.role == ChatRole.USER }) {
            return@flow
        }
        emit(StreamEvent.Phase(ChatPipelineStatus.Preparing))
        // Resolved fresh per request: provider/key changes apply immediately.
        // KeyStore decrypt + Base64 decode are I/O-bound; message assembly is CPU-bound.
        val config = withContext(Dispatchers.IO) { settingsRepository.resolveAiConfig() }
        val aiConfig = withContext(Dispatchers.IO) { settingsRepository.aiConfigSnapshot() }
        val effort = settingsRepository.settings.first().reasoningEffort
        val tier = resolveTier(aiConfig)
        val reasoning = reasoningConfig(aiConfig.provider, effort)

        if (tier != AiTier.BYOK) {
            val used = settingsDataStore.aiQuotaUsedTodaySnapshot()
            if (AiQuota.isExhausted(tier, used)) {
                val error = QuotaExhaustedException(quotaExhaustedMessage(tier))
                emit(StreamEvent.Phase(ChatPipelineStatus.Failed(error.message)))
                emit(StreamEvent.Failure(error))
                return@flow
            }
        }

        val messages = buildApiMessages(safeHistory) { status ->
            emit(StreamEvent.Phase(status))
        }
        emit(StreamEvent.Phase(ChatPipelineStatus.Connecting))
        emit(StreamEvent.Phase(ChatPipelineStatus.Waiting))
        var succeeded = false
        var announcedStreaming = false
        sseChatClient.stream(
            config = config,
            messages = messages,
            tools = buildPrefillTools(),
            maxTokens = managedMaxTokens(tier, effort),
            openRouterDataCollectionDeny = aiConfig.provider == AiProvider.OPENROUTER,
            reasoning = reasoning,
        )
            .buffer(Channel.UNLIMITED)
            .collect { event ->
                if (!announcedStreaming &&
                    (event is StreamEvent.Token || event is StreamEvent.ReasoningToken)
                ) {
                    emit(StreamEvent.Phase(ChatPipelineStatus.Streaming))
                    announcedStreaming = true
                }
                if (event is StreamEvent.Done) succeeded = true
                if (event is StreamEvent.Failure) {
                    emit(StreamEvent.Phase(ChatPipelineStatus.Failed(event.throwable.message)))
                }
                emit(event)
            }

        if (succeeded) {
            emit(StreamEvent.Phase(ChatPipelineStatus.Done))
            if (tier != AiTier.BYOK) {
                settingsDataStore.incrementAiQuota()
            }
        }
    }

    override suspend fun completeReply(history: List<ChatTurn>): String {
        if (ChatCrisisGate.blocksModelDispatch(history)) {
            throw CrisisInterceptedException()
        }
        val safeHistory = ChatCrisisGate.historyForModel(history)
        if (safeHistory.none { it.role == ChatRole.USER }) {
            throw CrisisInterceptedException()
        }
        val aiConfig = settingsRepository.aiConfigSnapshot()
        val effort = settingsRepository.settings.first().reasoningEffort
        val tier = resolveTier(aiConfig)

        if (tier != AiTier.BYOK) {
            val used = settingsDataStore.aiQuotaUsedTodaySnapshot()
            if (AiQuota.isExhausted(tier, used)) {
                throw QuotaExhaustedException(quotaExhaustedMessage(tier))
            }
        }

        val messages = buildApiMessages(safeHistory)
        val response = aiApi.complete(
            ChatCompletionRequest(
                model = aiConfig.model,
                messages = messages,
                stream = false,
                maxTokens = managedMaxTokens(tier, effort),
                provider = if (aiConfig.provider == AiProvider.OPENROUTER) OpenRouterProviderConfig() else null,
                reasoning = reasoningConfig(aiConfig.provider, effort),
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
        val raw = completeStructured(
            systemPrompt = MealPhotoCapture.SYSTEM_PROMPT,
            userMessage = ApiChatMessage.multimodal(
                role = "user",
                prompt = MealPhotoCapture.USER_PROMPT,
                imageDataUri = imageDataUri,
            ),
        )
        return MealPhotoCapture.parseAnalysis(raw)
    }

    override suspend fun parseQuickLog(utterance: String): QuickLogParseResult {
        val raw = completeStructured(
            systemPrompt = QuickLogNlp.SYSTEM_PROMPT,
            userMessage = ApiChatMessage.text(
                role = "user",
                content = QuickLogNlp.USER_PROMPT_PREFIX + utterance.trim(),
            ),
        )
        return QuickLogNlp.parseResponse(raw)
    }

    /**
     * Shared path for meal-photo and quick-log: quota, missing-key, HTTP, and
     * empty-body handling. Callers parse the returned text with their own
     * strict JSON decoder.
     */
    private suspend fun completeStructured(
        systemPrompt: String,
        userMessage: ApiChatMessage,
    ): String {
        val aiConfig = settingsRepository.aiConfigSnapshot()
        val tier = resolveTier(aiConfig)
        ensureStructuredCallAllowed(aiConfig, tier)

        val response = try {
            aiApi.complete(
                ChatCompletionRequest(
                    model = aiConfig.model,
                    messages = listOf(
                        ApiChatMessage.text(role = "system", content = systemPrompt),
                        userMessage,
                    ),
                    stream = false,
                    temperature = 0.0,
                    maxTokens = structuredMaxTokens(tier),
                    provider = if (aiConfig.provider == AiProvider.OPENROUTER) {
                        OpenRouterProviderConfig()
                    } else {
                        null
                    },
                ),
            )
        } catch (e: ApiKeyMissingException) {
            throw e
        } catch (e: HttpException) {
            val detail = e.message().orEmpty()
            throw ProviderHttpException(
                if (detail.isBlank()) "HTTP ${e.code()}" else "HTTP ${e.code()}: $detail",
            )
        }

        val raw = response.choices.firstOrNull()?.message?.contentText()
            ?.takeIf { it.isNotBlank() }
            ?: throw ProviderHttpException("Empty completion response")

        if (tier != AiTier.BYOK) {
            settingsDataStore.incrementAiQuota()
        }
        return raw
    }

    private suspend fun ensureStructuredCallAllowed(aiConfig: AiConfig, tier: AiTier) {
        if (tier != AiTier.BYOK) {
            val used = settingsDataStore.aiQuotaUsedTodaySnapshot()
            if (AiQuota.isExhausted(tier, used)) {
                throw QuotaExhaustedException(quotaExhaustedMessage(tier))
            }
            return
        }
        if (aiConfig.provider != AiProvider.CUSTOM && !aiConfig.hasApiKey) {
            throw ApiKeyMissingException()
        }
    }

    override suspend fun queueOffline(userMessageId: Long, prompt: String, ttlSeconds: Int) {
        if (ChatCrisisGate.matches(prompt)) return
        pendingAiQueryDao.insert(
            PendingAiQueryEntity(
                userMessageId = userMessageId,
                prompt = prompt,
                createdAt = System.currentTimeMillis(),
                ttlSeconds = PendingAiQueryTtl.normalizeForInsert(ttlSeconds),
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

    private fun managedMaxTokens(tier: AiTier, effort: ReasoningEffort = ReasoningEffort.OFF): Int? {
        if (tier == AiTier.BYOK) return null
        return when (effort) {
            ReasoningEffort.OFF -> MAX_MANAGED_REPLY_TOKENS
            ReasoningEffort.LOW -> 500
            ReasoningEffort.MEDIUM -> 800
            ReasoningEffort.HIGH -> 1200
        }
    }

    /**
     * OpenRouter: always send an explicit `reasoning` object so DeepSeek V4 Flash
     * does not think by default (tokens are billed). Other providers: omit the
     * field when off so unknown-field 400s are avoided.
     */
    private fun reasoningConfig(provider: AiProvider, effort: ReasoningEffort): ReasoningConfig? =
        when {
            provider == AiProvider.OPENROUTER && effort == ReasoningEffort.OFF ->
                ReasoningConfig(enabled = false)
            effort == ReasoningEffort.OFF -> null
            else -> ReasoningConfig(effort = effort.apiEffort, enabled = true)
        }

    private fun structuredMaxTokens(tier: AiTier): Int? =
        if (tier == AiTier.BYOK) STRUCTURED_MAX_TOKENS else STRUCTURED_MANAGED_MAX_TOKENS

    private fun quotaExhaustedMessage(tier: AiTier): String = when (tier) {
        AiTier.FREE -> "You've used all 10 free AI calls today. They reset at midnight. " +
            "Upgrade to Pro for 50 calls a day, or add your own API key for unlimited calls."
        AiTier.PRO -> "You've used all 50 Pro AI calls today. They reset at midnight. " +
            "Add your own API key for unlimited calls."
        AiTier.BYOK -> error("BYOK is never quota-exhausted")
    }

    private suspend fun buildApiMessages(
        history: List<ChatTurn>,
        onStatus: suspend (ChatPipelineStatus) -> Unit = {},
    ): List<ApiChatMessage> {
        val assembled = assembleSystemPrompt(onStatus)
        val lastUserId = history.lastOrNull { it.role == ChatRole.USER }?.id
        if (lastUserId != null && lastUserId > 0L) {
            val summary = assembled.summary.copy(
                conversationTurns = history.takeLast(MAX_HISTORY_TURNS).size,
            )
            chatMessageDao.updateContextSummary(lastUserId, AppJson.encodeToString(summary))
        }
        val system = ApiChatMessage.text(role = "system", content = assembled.systemPrompt)
        val turns = ChatCrisisGate.historyForModel(history)
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
    override suspend fun buildSystemPrompt(): String = assembleSystemPrompt().systemPrompt

    private data class AssembledChatContext(
        val systemPrompt: String,
        val summary: ChatContextSummary,
    )

    private suspend fun assembleSystemPrompt(
        onStatus: suspend (ChatPipelineStatus) -> Unit = {},
    ): AssembledChatContext {
        return coroutineScope {
            onStatus(
                ChatPipelineStatus.GatheringContext(ChatPipelineCopy.readingGlucose(CONTEXT_DAYS)),
            )
            val settingsDeferred = async { settingsRepository.settings.first() }
            val profileDeferred = async { settingsRepository.profile.first() }
            val dosingDeferred = async { settingsRepository.dosingProfileSnapshot() }
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
                analyticsRepository.topFoodPatternsForPrompt(excludedTags = dismissed)
            }

            val settings = settingsDeferred.await()
            val unit = settings.unit
            val profile = profileDeferred.await()
            val cgmCount = cgmCountDeferred.await()
            val manualCount = manualCountDeferred.await()
            onStatus(
                ChatPipelineStatus.GatheringContext(
                    ChatPipelineCopy.readingGlucoseCounted(CONTEXT_DAYS, cgmCount + manualCount),
                ),
            )
            onStatus(
                ChatPipelineStatus.GatheringContext(ChatPipelineCopy.computingAverages()),
            )
            val avg7 = avg7Deferred.await()
            val avg14 = avg14Deferred.await()
            val avg30 = avg30Deferred.await()
            onStatus(
                ChatPipelineStatus.GatheringContext(ChatPipelineCopy.computingTimeInRange(CONTEXT_DAYS)),
            )
            val tir14 = tir14Deferred.await()
            val daily = dailyDeferred.await()
            onStatus(
                ChatPipelineStatus.GatheringContext(ChatPipelineCopy.loadingRecentEntries(30)),
            )
            val recent = recentDeferred.await()
            onStatus(
                ChatPipelineStatus.GatheringContext(ChatPipelineCopy.computingIob()),
            )
            val dosing = dosingDeferred.await()
            val diaHours = dosing.diaHoursOrDefault
            val iobLookback = IobCalculator.lookbackMillis(diaHours.toDouble())
            val iobEntries = entryDao.entriesSince(System.currentTimeMillis() - iobLookback)
            val iobUnits = IobCalculator.activeInsulinOnBoard(
                boluses = iobEntries.mapNotNull { entry ->
                    entry.insulinBolusUnits?.let { units ->
                        IobCalculator.BolusEntry(entry.timestamp, units)
                    }
                },
                diaHours = diaHours.toDouble(),
                now = Instant.now(),
            )
            onStatus(
                ChatPipelineStatus.GatheringContext(ChatPipelineCopy.loadingFoodPatterns()),
            )
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
                    // Medication names/doses and feelingSick are withheld from
                    // this off-device prompt (managed OpenRouter). A bare
                    // "medication" token is included so the timeline is not
                    // silently empty; the name itself is not sent.
                    val metrics = listOfNotNull(
                        e.glucoseMgdl?.let { "glucose ${Formatters.glucoseWithUnit(it, unit)}" },
                        e.insulinBasalUnits?.let { "basal insulin ${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)} u" },
                        e.insulinBolusUnits?.let { "bolus insulin ${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)} u" },
                        e.carbsGrams?.let { "carbs $it g" },
                        e.proteinGrams?.let { "protein $it g" },
                        e.fatGrams?.let { "fat $it g" },
                        ChatCrisisGate.promptSafeText(e.mealDescription),
                        e.exerciseMinutes?.let { "exercise $it min" },
                        e.medicationName?.takeIf { it.isNotBlank() }?.let { "medication" },
                    ).joinToString(", ").ifBlank { "note" }
                    val note = ChatCrisisGate.promptSafeText(e.note)?.let { ", $it" } ?: ""
                    "$date $time $metrics$note"
                }

            val targetLow = Formatters.glucose(settings.targetLowMgdl.toDouble(), unit)
            val targetHigh = Formatters.glucose(settings.targetHighMgdl.toDouble(), unit)
            val tirPct = tir14?.let { "%.0f%%".format(it * 100) } ?: "n/a"
            val glucoseSource =
                "Glucose source: CGM ($cgmCount samples, 14d) · Manual entries: $manualCount"
            val iobLine =
                "Estimated insulin on board from logged boluses: " +
                    "${Formatters.oneDecimal(iobUnits)} U " +
                    "(DIA ${Formatters.oneDecimal(diaHours.toDouble())} h). " +
                    "This is an on-device estimate from doses already logged, not a dosing recommendation."

            val foodBlock = buildString {
                append("--- Food patterns (observational median 2h glucose change after tagged meals) ---")
                append("\nThese are observations about tagged meals, including logged insulin, not claims that a food causes a glucose change.")
                if (foodTags.isEmpty()) {
                    append("\nNone meet the occurrence threshold yet.")
                } else {
                    foodTags.forEach { tag ->
                        append('\n')
                        append(
                            TagImpactCopy.observation(
                                tag = tag.tag,
                                medianDeltaMgdl = tag.medianDeltaMgdl,
                                occurrences = tag.occurrences,
                                avgBolusUnits = tag.avgBolusUnits,
                                unit = unit,
                            ),
                        )
                        append(" Avg carbs: ")
                        append(tag.avgCarbsGrams?.let { Formatters.carbs(it) } ?: "n/a")
                        append(".")
                    }
                }
            }

            val persona = buildPersonaSection(profile)
            val includedProfile = profile.name.trim().isNotEmpty() ||
                profile.age != null ||
                !profile.diabetesType.isNullOrBlank() ||
                profile.heightCm != null ||
                profile.weightKg != null
            val systemPrompt = persona + "\n\n" + """
                |You are Hero, the in-app assistant of GlucoseHero, a personal glucose logging app.
                |Be concise, warm, and concrete. Ground every answer in the user's data below.
                |Keep replies under roughly 100 words.
                |You are not a medical professional. Never tell the user how much insulin to take,
                |never calculate or suggest an insulin dose, and never diagnose. GlucoseHero logs
                |and projects; it does not advise on dosing. Remind the user to confirm treatment
                |decisions with their care team when the topic calls for it.
                |Any internal reasoning is a working note, not a clinical assessment.
                |When discussing food patterns, phrase them as observations about tagged meals
                |(sample size and logged bolus included). Never say a food causes a spike or
                |raises glucose.
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
                |$iobLine
                |
                |--- Daily summaries, last 14 days ---
                |$dailyBlock
                |
                |--- Most recent 30 entries ---
                |$recentBlock
            """.trimMargin() + "\n\n" + foodBlock
            AssembledChatContext(
                systemPrompt = systemPrompt,
                summary = ChatContextSummary(
                    historyDays = CONTEXT_DAYS,
                    cgmReadingCount = cgmCount,
                    manualReadingCount = manualCount,
                    recentEntryCount = recent.size,
                    includedIob = true,
                    includedCarbs = recent.any { it.carbsGrams != null },
                    includedInsulin = recent.any {
                        it.insulinBasalUnits != null || it.insulinBolusUnits != null
                    },
                    includedMeals = recent.any { !it.mealDescription.isNullOrBlank() },
                    foodPatternCount = foodTags.size,
                    includedProfile = includedProfile,
                    medicationNamesWithheld = true,
                    feelingSickWithheld = true,
                ),
            )
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
        const val CONTEXT_DAYS = 14
        const val MAX_MANAGED_REPLY_TOKENS = 200
        const val STRUCTURED_MAX_TOKENS = 800
        const val STRUCTURED_MANAGED_MAX_TOKENS = 700
    }
}
