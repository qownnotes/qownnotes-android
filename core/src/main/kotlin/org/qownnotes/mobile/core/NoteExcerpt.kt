package org.qownnotes.mobile.core

object NoteExcerpt {
    private const val MAX_LENGTH = 100

    private val HEADING = Regex("""#{1,6}\s+""")
    private val TASK_LIST = Regex("""[-*+]\s+\[[ x]\]\s+""")
    private val UNORDERED_LIST = Regex("""[-*+]\s+""")
    private val ORDERED_LIST = Regex("""\d+\.\s+""")
    private val INLINE_CODE = Regex("""`([^`]+)`""")
    private val FENCED_CODE = Regex("""```[\s\S]*?```""")
    private val LINK = Regex("""\[([^\]]*)\]\([^)]*\)""")
    private val IMAGE = Regex("""!\[([^\]]*)\]\([^)]*\)""")
    private val BOLD_ITALIC = Regex("""\*{1,3}([^*]+)\*{1,3}""")
    private val STRIKETHROUGH = Regex("""~~([^~]+)~~""")
    private val BLOCKQUOTE = Regex("""^>{1,}\s+""", RegexOption.MULTILINE)
    private val HORIZONTAL_RULE = Regex("""-{3,}""")
    private val BLANK_LINES = Regex("""\n{2,}""")
    private val EXTRA_SPACES = Regex("""[^\S\n]+""")
    private val NEWLINES_AROUND_SPACES = Regex("""\n +\n""")

    fun of(content: String, title: String = ""): String {
        if (content.isBlank()) return ""

        var text = content
            .replace(FENCED_CODE, "")
            .replace(INLINE_CODE, "$1")
            .replace(HEADING, "")
            .replace(TASK_LIST, "")
            .replace(HORIZONTAL_RULE, "")
            .replace(UNORDERED_LIST, "")
            .replace(ORDERED_LIST, "")
            .replace(IMAGE, "")
            .replace(LINK, "$1")
            .replace(BOLD_ITALIC, "$1")
            .replace(STRIKETHROUGH, "$1")
            .replace(BLOCKQUOTE, "")
            .replace(BLANK_LINES, "\n")
            .replace(NEWLINES_AROUND_SPACES, "\n")
            .replace(EXTRA_SPACES, " ")
            .trim()

        if (title.isNotBlank() && text.lowercase().startsWith(title.lowercase())) {
            text = text.removeRange(0, title.length).trimStart('\n', ' ')
        }

        return if (text.length <= MAX_LENGTH) {
            text
        } else {
            text.take(MAX_LENGTH).trimEnd() + "\u2026"
        }
    }
}
