package org.qownnotes.mobile.core

/**
 * Finds every occurrence of [query] in [text], as half-open ranges suitable for highlighting.
 *
 * The query is matched literally, so a reader can search for Markdown punctuation without it being
 * interpreted as a pattern. Matching is case-insensitive and folds single characters only, which
 * keeps every match exactly as long as the query: a highlight can therefore never drift away from
 * the text it marks. Accents are not folded, so `cafe` does not match `café`.
 *
 * Matches do not overlap. Searching `aa` in `aaa` reports one match, because reporting two would
 * highlight the same character twice and would make cycling through matches confusing.
 *
 * A blank query matches nothing, so an empty or whitespace-only find field does not mark up the
 * whole note.
 */
fun findTextMatches(text: CharSequence, query: String): List<IntRange> {
    if (query.isBlank()) return emptyList()
    val haystack = text.toString()
    val matches = mutableListOf<IntRange>()
    var start = haystack.indexOf(query, startIndex = 0, ignoreCase = true)
    while (start >= 0) {
        matches += start until start + query.length
        start = haystack.indexOf(query, startIndex = start + query.length, ignoreCase = true)
    }
    return matches
}
