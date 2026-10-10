package org.qownnotes.mobile.core

/** Save-time cleanup that preserves Markdown hard breaks and the original line endings. */
object NoteWhitespace {
    private val singleTrailingSpace = Regex("(?<! ) (?=\\r\\n|\\r|\\n|$)")

    fun removeSingleTrailingSpaces(content: String): String =
        singleTrailingSpace.replace(content, "")
}
