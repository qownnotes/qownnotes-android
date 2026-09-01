package org.qownnotes.mobile.core

data class RedactedEncryptedMarkdown(
    val markdown: String,
    val blockCount: Int,
    val malformed: Boolean
)

fun redactEncryptedMarkdown(markdown: String, replacement: String): RedactedEncryptedMarkdown {
    val output = StringBuilder(markdown.length.coerceAtMost(MAX_REDACTED_CAPACITY))
    var cursor = 0
    var blockCount = 0
    while (true) {
        val start = markdown.indexOf(ENCRYPTED_BLOCK_START, cursor)
        if (start < 0) {
            output.append(markdown, cursor, markdown.length)
            return RedactedEncryptedMarkdown(output.toString(), blockCount, malformed = false)
        }

        output.append(markdown, cursor, start).append(replacement)
        blockCount++
        val end = markdown.indexOf(ENCRYPTED_BLOCK_END, start + ENCRYPTED_BLOCK_START.length)
        if (end < 0) {
            return RedactedEncryptedMarkdown(output.toString(), blockCount, malformed = true)
        }
        cursor = end + ENCRYPTED_BLOCK_END.length
    }
}

private const val ENCRYPTED_BLOCK_START = "<!-- BEGIN ENCRYPTED TEXT --"
private const val ENCRYPTED_BLOCK_END = "-- END ENCRYPTED TEXT -->"
private const val MAX_REDACTED_CAPACITY = 64 * 1024
