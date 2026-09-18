package com.omb9.glucosehero.ui.chat

import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ChatTurnKind
import com.omb9.glucosehero.util.Formatters

data class MessageGrouping(
    val isFirstInGroup: Boolean,
    val isLastInGroup: Boolean,
)

internal sealed interface ChatListRow {
    val key: String

    data class TimeGap(
        val timestamp: Long,
        val label: String,
    ) : ChatListRow {
        override val key: String = "gap-$timestamp"
    }

    data class Message(
        val turn: ChatTurn,
        val grouping: MessageGrouping,
    ) : ChatListRow {
        override val key: String = "msg-${turn.id}"
    }
}

internal fun mergeChatPages(
    current: List<ChatTurn>,
    incomingRecent: List<ChatTurn>,
): List<ChatTurn> {
    if (current.isEmpty()) return incomingRecent
    val byId = LinkedHashMap<Long, ChatTurn>(current.size + incomingRecent.size)
    current.forEach { byId[it.id] = it }
    incomingRecent.forEach { byId[it.id] = it }
    return byId.values.sortedWith(compareBy({ it.timestamp }, { it.id }))
}

internal fun buildChatListRows(
    messages: List<ChatTurn>,
    use24HourTime: Boolean,
    gapMillis: Long = TIMESTAMP_GAP_MILLIS,
): List<ChatListRow> {
    if (messages.isEmpty()) return emptyList()
    val rows = ArrayList<ChatListRow>(messages.size * 2)
    messages.forEachIndexed { index, turn ->
        val previous = messages.getOrNull(index - 1)
        if (previous == null || turn.timestamp - previous.timestamp >= gapMillis) {
            rows += ChatListRow.TimeGap(
                timestamp = turn.timestamp,
                label = timestampSeparatorLabel(turn.timestamp, previous?.timestamp, use24HourTime),
            )
        }
        val next = messages.getOrNull(index + 1)
        val sameAsPrev = previous != null &&
            previous.role == turn.role &&
            previous.kind == turn.kind &&
            turn.kind != ChatTurnKind.CRISIS_SUPPORT &&
            turn.timestamp - previous.timestamp < gapMillis
        val sameAsNext = next != null &&
            next.role == turn.role &&
            next.kind == turn.kind &&
            turn.kind != ChatTurnKind.CRISIS_SUPPORT &&
            next.timestamp - turn.timestamp < gapMillis
        rows += ChatListRow.Message(
            turn = turn,
            grouping = MessageGrouping(
                isFirstInGroup = !sameAsPrev,
                isLastInGroup = !sameAsNext,
            ),
        )
    }
    return rows
}

internal fun timestampSeparatorLabel(
    timestamp: Long,
    previousTimestamp: Long?,
    use24HourTime: Boolean,
): String {
    val time = Formatters.time(timestamp, use24HourTime)
    val day = Formatters.dayHeader(Formatters.localDate(timestamp))
    if (previousTimestamp == null) return "$day · $time"
    val previousDay = Formatters.localDate(previousTimestamp)
    val currentDay = Formatters.localDate(timestamp)
    return if (previousDay != currentDay) "$day · $time" else time
}
