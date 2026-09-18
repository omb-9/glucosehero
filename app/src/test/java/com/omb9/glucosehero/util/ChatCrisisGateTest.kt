package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ChatTurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCrisisGateTest {

    @Test
    fun matchingComposerTextIsLocalOnlyAndNeverExportable() {
        val crisis = "I want to die"
        assertTrue(ChatCrisisGate.matches(crisis))
        assertTrue(ChatCrisisGate.isLocalOnlyTurn(ChatTurnKind.NORMAL, crisis))
        assertFalse(ChatCrisisGate.isBackupExportable("NORMAL", crisis))
        assertFalse(
            ChatCrisisGate.isBackupExportable(
                ChatTurnKind.CRISIS_SUPPORT.name,
                ChatCrisisGate.SUPPORT_PLACEHOLDER,
            ),
        )
        assertTrue(ChatCrisisGate.isBackupExportable("NORMAL", "what was my average?"))
    }

    @Test
    fun historyForModelDropsSupportRowsAndMatchingUserText() {
        val history = listOf(
            ChatTurn(1, ChatRole.USER, "avg?", 1L, ChatTurnKind.NORMAL),
            ChatTurn(2, ChatRole.ASSISTANT, "110", 2L, ChatTurnKind.NORMAL),
            ChatTurn(3, ChatRole.USER, "I want to die", 3L, ChatTurnKind.NORMAL),
            ChatTurn(
                4,
                ChatRole.ASSISTANT,
                ChatCrisisGate.SUPPORT_PLACEHOLDER,
                4L,
                ChatTurnKind.CRISIS_SUPPORT,
            ),
        )
        val forModel = ChatCrisisGate.historyForModel(history)
        assertEquals(listOf(1L, 2L), forModel.map { it.id })
        assertTrue(ChatCrisisGate.blocksModelDispatch(history))
        assertFalse(ChatCrisisGate.blocksModelDispatch(forModel))
        assertFalse(
            ChatCrisisGate.blocksModelDispatch(
                listOf(
                    ChatTurn(1, ChatRole.USER, "I want to die", 1L),
                    ChatTurn(2, ChatRole.USER, "avg?", 2L),
                ),
            ),
        )
    }

    @Test
    fun matchingJournalsAreOmittedFromPromptText() {
        assertNull(ChatCrisisGate.promptSafeText("I want to die"))
        assertEquals("felt tired", ChatCrisisGate.promptSafeText("felt tired"))
        assertTrue(
            ChatCrisisGate.isCrisisLogText(
                note = null,
                mealDescription = "I want to die",
                moodLabel = null,
            ),
        )
        assertFalse(
            ChatCrisisGate.isCrisisLogText(
                note = "walk",
                mealDescription = "oats",
                moodLabel = "steady",
            ),
        )
    }
}
