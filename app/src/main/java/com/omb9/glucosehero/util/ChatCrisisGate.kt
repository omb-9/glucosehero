package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ChatTurnKind

/**
 * Chat-path crisis policy used by the composer, the last network gate, and
 * exporters.
 *
 * Persistence: never store the user's crisis-matching text. Persist a
 * local-only [ChatTurnKind.CRISIS_SUPPORT] row so [com.omb9.glucosehero.ui.components.CrisisSupportCard]
 * still shows after process death. That row is excluded from model history,
 * encrypted backups, pending-query exports, and AGP/Markdown/CSV (those
 * clinical exports never include chat at all).
 */
object ChatCrisisGate {

    /** Empty on purpose: the UI renders CrisisSupportCard from [ChatTurnKind], not copy. */
    const val SUPPORT_PLACEHOLDER: String = ""

    fun matches(text: String?): Boolean = CrisisDetector.isCrisis(text)

    fun isLocalOnlyTurn(kind: ChatTurnKind, content: String): Boolean =
        kind == ChatTurnKind.CRISIS_SUPPORT || CrisisDetector.isCrisis(content)

    fun isLocalOnlyTurn(turn: ChatTurn): Boolean =
        isLocalOnlyTurn(turn.kind, turn.content)

    fun isBackupExportable(kindName: String, content: String): Boolean {
        if (kindName == ChatTurnKind.CRISIS_SUPPORT.name) return false
        return !CrisisDetector.isCrisis(content)
    }

    fun historyForModel(history: List<ChatTurn>): List<ChatTurn> =
        history.filterNot { isLocalOnlyTurn(it) }

    /**
     * True when the latest user turn must not be dispatched. Older crisis
     * turns are stripped by [historyForModel] instead of blocking later
     * safe questions.
     */
    fun blocksModelDispatch(history: List<ChatTurn>): Boolean {
        val latestUser = history.lastOrNull { turn ->
            turn.role == ChatRole.USER && turn.kind != ChatTurnKind.CRISIS_SUPPORT
        } ?: return false
        return CrisisDetector.isCrisis(latestUser.content)
    }

    /** Omits crisis-matching free text from the Hero system prompt. */
    fun promptSafeText(text: String?): String? {
        val trimmed = text?.takeIf { it.isNotBlank() } ?: return null
        return if (CrisisDetector.isCrisis(trimmed)) null else trimmed
    }

    fun isCrisisLogText(note: String?, mealDescription: String?, moodLabel: String?): Boolean =
        CrisisDetector.isCrisis(note) ||
            CrisisDetector.isCrisis(mealDescription) ||
            CrisisDetector.isCrisis(moodLabel)
}
