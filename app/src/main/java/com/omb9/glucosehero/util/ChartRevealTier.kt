package com.omb9.glucosehero.util

/**
 * Range-based marker-detail tiers for the Stats chart's foreground markers.
 *
 * This replaces the previous zoom-keyed tiers. With pinch-to-zoom removed, how much marker detail
 * to show is decided by the selected window, not the zoom factor: short windows are sparse enough
 * to label, while long windows are too dense for markers at all.
 */
enum class ChartRevealTier {
    /** Background line only; no foreground markers at all. */
    LINE_ONLY,

    /** A small range-colored dot at each logged entry. */
    DOT,

    /** The dot plus the glucose value. */
    DOT_PLUS_VALUE;

    companion object {
        /**
         * Maps the selected range (in days) to its marker-detail tier.
         *
         * - 24h and 7d label each marker with its value.
         * - 14d shows dots without labels.
         * - 30d and 90d show no markers — the line is the story.
         */
        fun forRangeDays(rangeDays: Int): ChartRevealTier = when {
            rangeDays <= 7 -> DOT_PLUS_VALUE
            rangeDays <= 14 -> DOT
            else -> LINE_ONLY
        }
    }
}
