package com.omb9.glucosehero.util

/**
 * Pure, side-effect-free extraction of hashtags from a free-text note.
 *
 * A hashtag is a leading `#` immediately followed by one or more word
 * characters (letters, digits, underscore). Tags are normalised to lowercase
 * so `#Pizza` and `#pizza` group together during analytics, and duplicates
 * are collapsed so each tag appears exactly once in the result.
 *
 * Kept as an object (matching [StreakCalculator] and friends) so the same
 * rules can back both the repository layer and any future UI affordances
 * without duplicating the pattern.
 */
object HashtagExtractor {

    /** `#` + one-or-more word chars; the captured group excludes the `#`. */
    private val HASHTAG_REGEX = Regex("""#(\w+)""")

    /**
     * Returns the unique, lowercased hashtags (including the leading `#`)
     * found in [note], in order of first appearance. Returns an empty list
     * for null/blank input or when no valid hashtags are present.
     */
    fun extract(note: String?): List<String> {
        if (note.isNullOrBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        for (match in HASHTAG_REGEX.findAll(note)) {
            seen.add("#" + match.groupValues[1].lowercase())
        }
        return seen.toList()
    }
}
