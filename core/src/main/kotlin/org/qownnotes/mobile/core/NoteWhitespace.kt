package org.qownnotes.mobile.core

/** Save-time cleanup that preserves Markdown hard breaks and the original line endings. */
object NoteWhitespace {
    private val singleTrailingSpace = Regex("(?<! ) (?=\\r\\n|\\r|\\n|$)")
    private val line = Regex("[^\\r\\n]*(?:\\r\\n|\\r|\\n|$)")

    /**
     * Preserve every line that still matches the editing baseline, even if it moved. Matching
     * duplicate lines are deliberately preserved too: ambiguous text must not be rewritten.
     */
    fun removeSingleTrailingSpaces(original: String, content: String): String {
        val originalLines = line.findAll(original).map { it.value.trimEnd('\r', '\n') }.toHashSet()
        return line.replace(content) { match ->
            if (match.value.trimEnd('\r', '\n') in originalLines) {
                match.value
            } else {
                singleTrailingSpace.replace(match.value, "")
            }
        }
    }
}
