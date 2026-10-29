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

/** A single-line excerpt of a text around one match, with the match's range inside [text]. */
data class TextMatchSnippet(val text: String, val match: IntRange)

/**
 * Builds a single-line excerpt of [text] that shows [match] with up to [context] characters on
 * either side, for listing where a search matched.
 *
 * The excerpt starts and ends at word boundaries when the surrounding text allows it, collapses
 * runs of whitespace such as line breaks into single spaces, and marks omitted text at either end
 * with an ellipsis. The returned range locates the matched characters inside the excerpt, so they
 * can be emphasized without searching again.
 */
fun textMatchSnippet(text: String, match: IntRange, context: Int = 40): TextMatchSnippet {
    require(match.first >= 0 && match.last < text.length && !match.isEmpty()) {
        "Match $match is outside the text"
    }
    var start = (match.first - context).coerceAtLeast(0)
    if (start > 0 && !text[start - 1].isWhitespace()) {
        val wordEnd = (start until match.first).firstOrNull { text[it].isWhitespace() }
        if (wordEnd != null) start = wordEnd + 1
    }
    var end = (match.last + 1 + context).coerceAtMost(text.length)
    if (end < text.length && !text[end].isWhitespace()) {
        val wordStart = (match.last + 1 until end).lastOrNull { text[it].isWhitespace() }
        if (wordStart != null) end = wordStart
    }
    val before = collapseWhitespace(text.substring(start, match.first)).trimStart()
    val matched = collapseWhitespace(text.substring(match.first, match.last + 1))
    val after = collapseWhitespace(text.substring(match.last + 1, end)).trimEnd()
    val prefix = if (start > 0) "\u2026" else ""
    val suffix = if (end < text.length) "\u2026" else ""
    val matchStart = prefix.length + before.length
    return TextMatchSnippet(
        text = prefix + before + matched + after + suffix,
        match = matchStart until matchStart + matched.length
    )
}

private val WHITESPACE_RUN = Regex("""\s+""")

private fun collapseWhitespace(text: String): String = text.replace(WHITESPACE_RUN, " ")
