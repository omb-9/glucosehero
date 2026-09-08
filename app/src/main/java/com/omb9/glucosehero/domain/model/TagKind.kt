package com.omb9.glucosehero.domain.model

enum class TagKind { HASHTAG, FOOD, DESCRIPTION, MOOD, SLEEP, CYCLE }

/**
 * Tag kinds that are computed from a Health Connect interval (sleep session or
 * menstruation period) rather than a 2-hour post-event glucose delta.
 */
val TagKind.isWindowed: Boolean
    get() = this == TagKind.SLEEP || this == TagKind.CYCLE