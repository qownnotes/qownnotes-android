package org.qownnotes.mobile.core

/**
 * An inline image, `![description](destination "title")`, as it is written in a note. All
 * offsets index the note's Markdown source; ends are exclusive.
 */
data class MarkdownImageSyntax(
    val start: Int,
    val end: Int,
    val descriptionStart: Int,
    val descriptionEnd: Int,
    val destinationStart: Int,
    val destinationEnd: Int
)

/**
 * Every place in [markdown] written like an inline image, in source order.
 *
 * This is a lexical scan: it also finds image syntax inside code, where nothing is rendered. A
 * caller that acts on a rendered image must first match it to one of these through the renderer's
 * own parser rather than by counting.
 */
fun inlineMarkdownImages(markdown: String): List<MarkdownImageSyntax> {
    val images = mutableListOf<MarkdownImageSyntax>()
    var index = 0
    while (index < markdown.length - 1) {
        when {
            markdown[index] == '\\' -> index += 2
            markdown[index] == '!' && markdown[index + 1] == '[' -> {
                val image = markdown.imageAt(index)
                if (image != null) {
                    images += image
                    index = image.end
                } else {
                    index += 2
                }
            }
            else -> index++
        }
    }
    return images
}

/**
 * Points the image [imageIndex] of [inlineMarkdownImages] at [destination]. Returns null when
 * that image is no longer written as [expected], so a note changed in the meantime is never
 * edited in the wrong place.
 */
fun replaceMarkdownImage(
    markdown: String,
    imageIndex: Int,
    expected: String,
    destination: String
): String? {
    val image = markdown.expectedImage(imageIndex, expected) ?: return null
    return markdown.replaceRange(image.destinationStart, image.destinationEnd, destination)
}

/**
 * Adds an image showing [destination] with the description of image [imageIndex] after that
 * image. An image on a line of its own gets the new image as a paragraph below it, inside the
 * same list item or quote; an image within text, or in a table cell, gets it right beside it.
 * Returns null under the same conditions as [replaceMarkdownImage].
 */
fun insertMarkdownImageAfter(
    markdown: String,
    imageIndex: Int,
    expected: String,
    destination: String
): String? {
    val image = markdown.expectedImage(imageIndex, expected) ?: return null
    val added = "![" + markdown.substring(image.descriptionStart, image.descriptionEnd) + "](" +
        destination + ")"
    val lineStart = markdown.lastIndexOf('\n', image.start - 1) + 1
    val lineEnd = markdown.indexOf('\n', image.end).let { if (it < 0) markdown.length else it }
    val before = markdown.substring(lineStart, image.start)
    if (!LINE_PREFIX.matches(before) || markdown.substring(image.end, lineEnd).isNotBlank()) {
        return markdown.substring(0, image.end) + " " + added + markdown.substring(image.end)
    }
    // List markers become indentation, so the new paragraph continues the same list item.
    val prefix = before.map { if (it == '>' || it == ' ' || it == '\t') it else ' ' }
        .joinToString("")
    val blank = prefix.trimEnd()
    val followedByText = lineEnd < markdown.length &&
        markdown.substring(lineEnd + 1).substringBefore('\n').isNotBlank()
    return buildString {
        append(markdown, 0, lineEnd)
        append('\n').append(blank).append('\n').append(prefix).append(added)
        if (followedByText) append('\n').append(blank)
        append(markdown, lineEnd, markdown.length)
    }
}

private fun String.expectedImage(imageIndex: Int, expected: String): MarkdownImageSyntax? =
    inlineMarkdownImages(this).getOrNull(imageIndex)
        ?.takeIf { substring(it.start, it.end) == expected }

private fun String.imageAt(start: Int): MarkdownImageSyntax? {
    val descriptionStart = start + 2
    var index = descriptionStart
    var depth = 1
    while (index < length) {
        when (this[index]) {
            '\\' -> index++
            '[' -> depth++
            ']' -> if (--depth == 0) break
            '\n' -> if (isBlankLineAfter(index)) return null
        }
        index++
    }
    if (index >= length) return null
    val descriptionEnd = index
    if (getOrNull(descriptionEnd + 1) != '(') return null
    index = skipSpace(descriptionEnd + 2) ?: return null

    val destinationStart: Int
    val destinationEnd: Int
    if (getOrNull(index) == '<') {
        destinationStart = index + 1
        index = destinationStart
        while (index < length && this[index] != '>') {
            if (this[index] == '\n' || this[index] == '<') return null
            if (this[index] == '\\') index++
            index++
        }
        if (index >= length) return null
        destinationEnd = index
        index++
    } else {
        destinationStart = index
        var parentheses = 0
        while (index < length) {
            val character = this[index]
            when {
                character == '\\' -> index++
                character == '(' -> parentheses++
                character == ')' -> if (parentheses-- == 0) break
                character.isWhitespace() || character.isISOControl() -> break
            }
            index++
        }
        if (parentheses > 0) return null
        destinationEnd = index.coerceAtMost(length)
    }

    val afterDestination = skipSpace(index) ?: return null
    index = afterDestination
    if (index > destinationEnd && getOrNull(index) in TITLE_CLOSERS.keys) {
        val closer = TITLE_CLOSERS.getValue(this[index])
        index++
        while (index < length && this[index] != closer) {
            if (this[index] == '\\') {
                index++
            } else if (this[index] == '\n' && isBlankLineAfter(index)) {
                return null
            }
            index++
        }
        if (index >= length) return null
        index = skipSpace(index + 1) ?: return null
    }
    if (getOrNull(index) != ')') return null
    return MarkdownImageSyntax(
        start,
        index + 1,
        descriptionStart,
        descriptionEnd,
        destinationStart,
        destinationEnd
    )
}

/** Skips spaces, tabs, and at most one line break; null where a blank line ends the image. */
private fun String.skipSpace(from: Int): Int? {
    var index = from
    var lineBreaks = 0
    while (index < length && (this[index] == ' ' || this[index] == '\t' || this[index] == '\n')) {
        if (this[index] == '\n' && ++lineBreaks > 1) return null
        index++
    }
    return index
}

private fun String.isBlankLineAfter(lineBreak: Int): Boolean {
    val next = indexOf('\n', lineBreak + 1).let { if (it < 0) length else it }
    return substring(lineBreak + 1, next).isBlank()
}

private val TITLE_CLOSERS = mapOf('"' to '"', '\'' to '\'', '(' to ')')

/** Indentation, quote markers, and a list marker: what may precede an image on its own line. */
private val LINE_PREFIX =
    Regex("""(?:[ \t]*>)*[ \t]*(?:(?:[-+*]|\d{1,9}[.)])[ \t]+(?:\[[ xX-]][ \t]+)?)?""")
