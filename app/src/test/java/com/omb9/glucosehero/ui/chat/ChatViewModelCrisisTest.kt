package com.omb9.glucosehero.ui.chat

import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.ChatPipelineStatus
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ChatTurnKind
import com.omb9.glucosehero.domain.model.DosingProfile
import com.omb9.glucosehero.domain.model.DosingProfileLoad
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.MealPhotoAnalysis
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.QuickLogParseResult
import com.omb9.glucosehero.domain.model.ReasoningEffort
import com.omb9.glucosehero.domain.model.ResolvedAiConfig
import com.omb9.glucosehero.domain.model.StreamEvent
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.ChatRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.log.HeroAiPrefillCoordinator
import com.omb9.glucosehero.util.ChatCrisisGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCrisisTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun crisisComposerTextNeverReachesStreamReplyAndShowsSupportRow() {
        val repo = RecordingChatRepository()
        val viewModel = chatViewModel(repo)
        val uiScope = CoroutineScope(dispatcher + SupervisorJob())
        uiScope.launch { viewModel.uiState.collect { } }

        viewModel.send("I want to die")

        assertTrue(repo.streamReplyHistories.isEmpty())
        assertTrue(repo.queued.isEmpty())
        assertTrue(repo.userContents.isEmpty())
        assertEquals(listOf(ChatTurnKind.CRISIS_SUPPORT), repo.turns.value.map { it.kind })
        assertTrue(viewModel.uiState.value.messages.any { it.kind == ChatTurnKind.CRISIS_SUPPORT })
        assertEquals(ChatPipelineStatus.Idle, viewModel.pipeline.value)
        uiScope.cancel()
    }

    @Test
    fun nonCrisisTextStillStreams() {
        val repo = RecordingChatRepository()
        val viewModel = chatViewModel(repo)

        viewModel.send("what was my average?")

        assertEquals(listOf("what was my average?"), repo.userContents)
        assertEquals(1, repo.streamReplyHistories.size)
        assertTrue(repo.streamReplyHistories.single().any { it.content == "what was my average?" })
        assertTrue(repo.turns.value.none { it.kind == ChatTurnKind.CRISIS_SUPPORT })
    }

    @Test
    fun retryOfCrisisSupportRowDoesNotCallTheModel() {
        val repo = RecordingChatRepository()
        repo.seed(
            ChatTurn(
                id = 9L,
                role = ChatRole.ASSISTANT,
                content = ChatCrisisGate.SUPPORT_PLACEHOLDER,
                timestamp = 1L,
                kind = ChatTurnKind.CRISIS_SUPPORT,
            ),
        )
        val viewModel = chatViewModel(repo)

        viewModel.retry(9L)

        assertTrue(repo.streamReplyHistories.isEmpty())
        assertTrue(repo.queued.isEmpty())
    }

    @Test
    fun retryOfLegacyCrisisUserTextDoesNotCallTheModel() {
        val repo = RecordingChatRepository()
        repo.seed(
            ChatTurn(
                id = 4L,
                role = ChatRole.USER,
                content = "I want to die",
                timestamp = 1L,
            ),
        )
        val viewModel = chatViewModel(repo)

        viewModel.retry(4L)

        assertTrue(repo.streamReplyHistories.isEmpty())
        assertTrue(repo.queued.isEmpty())
        assertTrue(repo.turns.value.any { it.kind == ChatTurnKind.CRISIS_SUPPORT })
        assertEquals(1, repo.turns.value.count { it.role == ChatRole.USER })
    }

    private fun chatViewModel(repo: RecordingChatRepository): ChatViewModel {
        val settings = FakeChatSettingsRepository()
        return ChatViewModel(
            chatRepository = repo,
            isOnline = { true },
            heroAiPrefillCoordinator = HeroAiPrefillCoordinator(settings),
            premium = MutableStateFlow(false),
            quotaUsedToday = MutableStateFlow(0),
            settingsRepository = settings,
        )
    }
}

private class RecordingChatRepository : ChatRepository {
    val turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val streamReplyHistories = mutableListOf<List<ChatTurn>>()
    val queued = mutableListOf<Pair<Long, String>>()
    val userContents: List<String>
        get() = turns.value.filter { it.role == ChatRole.USER }.map { it.content }

    private var nextId = 1L

    fun seed(vararg messages: ChatTurn) {
        turns.value = messages.toList()
        nextId = (messages.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    override fun observeHistory(): Flow<List<ChatTurn>> = turns

    override fun observeRecentHistory(limit: Int): Flow<List<ChatTurn>> =
        turns.map { it.takeLast(limit) }

    override suspend fun loadOlderHistory(
        beforeTimestamp: Long,
        beforeId: Long,
        limit: Int,
    ): List<ChatTurn> = emptyList()

    override fun observePendingCount(): Flow<Int> = flowOf(0)

    override fun observePendingUserMessageIds(): Flow<List<Long>> = flowOf(emptyList())

    override fun observeGlucosePointCount(): Flow<Int> = flowOf(10)

    override suspend fun appendUserMessage(text: String): Long {
        val id = nextId++
        turns.value = turns.value + ChatTurn(
            id = id,
            role = ChatRole.USER,
            content = text,
            timestamp = id,
        )
        return id
    }

    override suspend fun appendAssistantMessage(
        text: String,
        kind: ChatTurnKind,
    ): Long {
        val id = nextId++
        turns.value = turns.value + ChatTurn(
            id = id,
            role = ChatRole.ASSISTANT,
            content = text,
            timestamp = id,
            kind = kind,
        )
        return id
    }

    override suspend fun deleteMessage(id: Long) {
        turns.value = turns.value.filterNot { it.id == id }
    }

    override suspend fun clearHistory() {
        turns.value = emptyList()
    }

    override fun streamReply(history: List<ChatTurn>): Flow<StreamEvent> {
        streamReplyHistories += history
        return flowOf(StreamEvent.Done("ok"))
    }

    override suspend fun completeReply(history: List<ChatTurn>): String {
        error("completeReply should not run in ChatViewModel tests")
    }

    override suspend fun analyzeMealPhoto(imageDataUri: String): MealPhotoAnalysis {
        error("unused")
    }

    override suspend fun parseQuickLog(utterance: String): QuickLogParseResult {
        error("unused")
    }

    override suspend fun queueOffline(userMessageId: Long, prompt: String, ttlSeconds: Int) {
        queued += userMessageId to prompt
    }

    override suspend fun buildSystemPrompt(): String = error("unused")
}

private class FakeChatSettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(UserSettings())
    override val needsGlucoseUnitChoice = MutableStateFlow(false)
    override val aiConfig = MutableStateFlow(AiConfig(provider = AiProvider.OPENROUTER))
    override val profile = MutableStateFlow(UserProfile())
    override val bolusSettings = MutableStateFlow(BolusSettings())
    override val dosingProfile = MutableStateFlow(
        DosingProfileLoad.Invalid(issues = emptyList(), diaHours = 4f),
    )

    override suspend fun profileSnapshot(): UserProfile = profile.value
    override suspend fun aiConfigSnapshot(): AiConfig = aiConfig.value
    override suspend fun bolusSettingsSnapshot(): BolusSettings = bolusSettings.value
    override suspend fun dosingProfileSnapshot(): DosingProfileLoad = dosingProfile.value

    override suspend fun setThemeMode(mode: ThemeMode) = unused()
    override suspend fun setAccent(accent: AccentColor) = unused()
    override suspend fun setUnit(unit: GlucoseUnit) = unused()
    override suspend fun setUse24HourTime(enabled: Boolean) = unused()
    override suspend fun seedFirstRunDefaultsIfNeeded(use24HourTime: Boolean) = unused()
    override suspend fun completeFirstRun(unit: GlucoseUnit) = unused()
    override suspend fun setIsHeroAiEnabled(enabled: Boolean) = unused()
    override suspend fun setShowAdvancedMacros(enabled: Boolean) = unused()
    override suspend fun setPostMealRemindersEnabled(enabled: Boolean) = unused()
    override suspend fun setNotificationsEnabled(enabled: Boolean) = unused()
    override suspend fun setSendMealPhotosToHeroAi(enabled: Boolean) = unused()
    override suspend fun setReasoningEffort(effort: ReasoningEffort) = unused()
    override suspend fun setProfileTarget(target: ProfileTarget) = unused()
    override suspend fun setProfileName(name: String) = unused()
    override suspend fun setProfileAge(age: Int?) = unused()
    override suspend fun setProfileDiabetesType(type: String?) = unused()
    override suspend fun setProfileHeightCm(heightCm: Float?) = unused()
    override suspend fun setProfileWeightKg(weightKg: Float?) = unused()
    override suspend fun setTargetRange(lowMgdl: Float, highMgdl: Float) = unused()
    override suspend fun setDiaHours(diaHours: Float) = unused()
    override suspend fun setCirRatio(ratio: Float) = unused()
    override suspend fun setIsfMgdl(isf: Float) = unused()
    override suspend fun setTargetGlucoseMgdl(target: Float) = unused()
    override suspend fun setDosingProfile(profile: DosingProfile) = unused()
    override suspend fun setAiProvider(provider: AiProvider) = unused()
    override suspend fun setAiBaseUrl(url: String) = unused()
    override suspend fun setAiModel(model: String) = unused()
    override suspend fun setApiKey(plainKey: String) = unused()
    override suspend fun resolveAiConfig(): ResolvedAiConfig = unused()

    private fun unused(): Nothing = error("unused")
}
