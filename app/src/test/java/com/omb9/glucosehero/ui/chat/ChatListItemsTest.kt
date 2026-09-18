package com.omb9.glucosehero.ui.chat

import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ChatTurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListItemsTest {

    @Test
    fun mergeUnionsByIdAndSortsOldestFirst() {
        val older = listOf(turn(1, ChatRole.USER, 10), turn(2, ChatRole.ASSISTANT, 20))
        val recent = listOf(turn(2, ChatRole.ASSISTANT, 20), turn(3, ChatRole.USER, 30))
        val merged = mergeChatPages(older, recent)
        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
    }

    @Test
    fun consecutiveSameRoleTightensGroupExceptAfterGap() {
        val messages = listOf(
            turn(1, ChatRole.USER, 1_000),
            turn(2, ChatRole.USER, 1_500),
            turn(3, ChatRole.ASSISTANT, 2_000),
            turn(4, ChatRole.ASSISTANT, 2_000 + TIMESTAMP_GAP_MILLIS + 1),
        )
        val rows = buildChatListRows(messages, use24HourTime = true, gapMillis = TIMESTAMP_GAP_MILLIS)
        val grouped = rows.filterIsInstance<ChatListRow.Message>()
        assertFalse(grouped[0].grouping.isLastInGroup)
        assertTrue(grouped[1].grouping.isLastInGroup)
        assertTrue(grouped[2].grouping.isFirstInGroup)
        assertTrue(grouped[2].grouping.isLastInGroup)
        assertTrue(grouped[3].grouping.isFirstInGroup)
        assertTrue(rows.any { it is ChatListRow.TimeGap })
    }

    @Test
    fun emptyListHasNoRows() {
        assertTrue(buildChatListRows(emptyList(), use24HourTime = false).isEmpty())
    }

    @Test
    fun crisisSupportCardDoesNotGroupWithAssistant() {
        val messages = listOf(
            ChatTurn(1, ChatRole.ASSISTANT, "hello", 1_000, ChatTurnKind.NORMAL),
            ChatTurn(2, ChatRole.ASSISTANT, "", 1_100, ChatTurnKind.CRISIS_SUPPORT),
        )
        val grouped = buildChatListRows(messages, use24HourTime = true)
            .filterIsInstance<ChatListRow.Message>()
        assertTrue(grouped[0].grouping.isLastInGroup)
        assertTrue(grouped[1].grouping.isFirstInGroup)
        assertTrue(grouped[1].grouping.isLastInGroup)
    }

    private fun turn(id: Long, role: ChatRole, timestamp: Long) = ChatTurn(
        id = id,
        role = role,
        content = "m$id",
        timestamp = timestamp,
    )
}
