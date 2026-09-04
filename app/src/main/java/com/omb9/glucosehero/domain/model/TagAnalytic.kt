package com.omb9.glucosehero.domain.model

/**
 * Per-hashtag analytics result produced by the analytics repository.
 *
 * @param tag The hashtag, including the leading `#` (e.g. `"#pizza"`).
 * @param count How many log entries carried this tag.
 * @param averageDelta The average glucose delta (mg/dL) observed ~2 hours
 *   after tagging events that have a baseline glucose reading. `0f` when none
 *   of the tagged events had a baseline glucose to compute a delta from.
 */
data class TagAnalytic(
    val tag: String,
    val count: Int,
    val averageDelta: Float,
)