package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.TagKind

/** A single normalised tag produced by [TagExtractor]. */
data class ExtractedTag(
    val tag: String,
    val kind: TagKind,
    val foodId: Long? = null,
)

/**
 * Pure extraction of analytics tags from the fields of a single log entry.
 *
 * Tags come from four sources:
 *
 * 1. A food id (strongest signal: exact identity, no user discipline
 *    required) using the food's name, lowercased.
 * 2. Hashtags in the free-text note, via [HashtagExtractor].
 * 3. Tokens in the meal description, and only when no food id is present, so
 *    the same meal is never double-counted from both its exact food and its
 *    prose description.
 * 4. The mood label, as an *independent* source: an entry can carry both a
 *    food tag and a mood tag, and mood neither suppresses nor is suppressed
 *    by [foodId]. Mood tags are namespaced with [MOOD_TAG_PREFIX] so a mood
 *    label ("Low") can never collide with a description token ("low carb
 *    wrap") under the `tag_analytics` unique index on `tag` alone.
 *
 * Priority ordering applies only among the first three sources (food >
 * hashtag > description), which share an un-namespaced tag space and are
 * deduplicated by the normalised string, keeping the highest-priority
 * [TagKind]. Mood lives in its own `mood:` namespace and therefore never
 * participates in that deduplication. Its ordering relative to the other
 * sources is irrelevant.
 *
 * Display floors live here too: [FOOD_MIN_OCCURRENCES] for food, hashtag,
 * mood, and windowed tags, and the higher [DESCRIPTION_MIN_OCCURRENCES] for
 * inferred description tokens.
 *
 * This object has no Android imports and performs no database access.
 */
object TagExtractor {

    /** Prefix namespacing mood tags so they cannot collide with description/hashtag tags. */
    const val MOOD_TAG_PREFIX = "mood:"

    /** Fixed note markers written by Health Connect sleep/cycle imports. */
    const val SLEEP_TAG = "#sleep"
    const val CYCLE_TAG = "#cycle"

    /**
     * Minimum occurrences before a food-identity, hashtag, mood, or windowed
     * tag is treated as a pattern. Exact food identity is a strong signal, so
     * a handful of repeats is enough.
     */
    const val FOOD_MIN_OCCURRENCES = 3

    /**
     * Description tokens are inferred from prose, so they need more repeats
     * than an exact food id before they count as a pattern.
     */
    const val DESCRIPTION_MIN_OCCURRENCES = 5

    /** Kind-specific floor used by analytics display and chat context. */
    fun minOccurrences(kind: TagKind): Int = when (kind) {
        TagKind.DESCRIPTION -> DESCRIPTION_MIN_OCCURRENCES
        else -> FOOD_MIN_OCCURRENCES
    }

    fun meetsOccurrenceThreshold(kind: TagKind, occurrences: Int): Boolean =
        occurrences >= minOccurrences(kind)

    /**
     * Articles, common prepositions, and the handful of glue words that would
     * otherwise flood the tag space from a prose meal description.
     */
    private val DESCRIPTION_STOPWORDS = setOf(
        "a", "an", "the",
        "of", "in", "on", "at", "to", "for", "from", "by",
        "with", "and", "some", "my",
    )

    /** Splits on anything that is not a Unicode letter or digit. */
    private val DESCRIPTION_TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    private const val MIN_TOKEN_LENGTH = 3
    private const val MAX_DESCRIPTION_TOKENS = 3

    fun extract(
        note: String?,
        mealDescription: String?,
        foodId: Long?,
        foodName: String?,
        mood: String? = null,
    ): List<ExtractedTag> {
        val result = LinkedHashMap<String, ExtractedTag>()

        // Food id is emitted first so a lower-priority source can never
        // overwrite it during deduplication.
        if (foodId != null) {
            val name = foodName?.trim().orEmpty()
            if (name.isNotBlank()) {
                putIfAbsent(result, ExtractedTag(name.lowercase(), TagKind.FOOD, foodId))
            }
        }

        val hashtags = HashtagExtractor.extract(note)

        // Health Connect sleep/cycle imports carry a fixed marker in the note.
        // Classify them under their own tag kinds before the generic hashtag
        // pass so the first-insert-wins dedup keeps the specialized kind.
        if (SLEEP_TAG in hashtags) {
            putIfAbsent(result, ExtractedTag(SLEEP_TAG, TagKind.SLEEP))
        }
        if (CYCLE_TAG in hashtags) {
            putIfAbsent(result, ExtractedTag(CYCLE_TAG, TagKind.CYCLE))
        }

        for (tag in hashtags) {
            putIfAbsent(result, ExtractedTag(tag, TagKind.HASHTAG))
        }

        if (foodId == null) {
            for (token in descriptionTokens(mealDescription)) {
                putIfAbsent(result, ExtractedTag(token, TagKind.DESCRIPTION))
            }
        }

        // Mood is an independent source, namespaced so it never collides with
        // the food/hashtag/description tag space.
        val moodValue = mood?.trim().orEmpty()
        if (moodValue.isNotBlank()) {
            putIfAbsent(
                result,
                ExtractedTag(MOOD_TAG_PREFIX + moodValue.lowercase(), TagKind.MOOD),
            )
        }

        return result.values.toList()
    }

    private fun descriptionTokens(mealDescription: String?): List<String> {
        if (mealDescription.isNullOrBlank()) return emptyList()
        return mealDescription
            .split(DESCRIPTION_TOKEN_SPLIT)
            .map { it.lowercase() }
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in DESCRIPTION_STOPWORDS }
            .take(MAX_DESCRIPTION_TOKENS)
    }

    private fun putIfAbsent(map: LinkedHashMap<String, ExtractedTag>, tag: ExtractedTag) {
        if (tag.tag.isBlank()) return
        if (!map.containsKey(tag.tag)) {
            map[tag.tag] = tag
        }
    }
}
