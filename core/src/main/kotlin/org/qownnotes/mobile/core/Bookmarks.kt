package org.qownnotes.mobile.core

import java.util.Locale

const val DEFAULT_BOOKMARKS_PATH = "Bookmarks.md"

data class Bookmark(
    val url: String,
    val name: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val markdown: String = ""
)

data class BookmarksSource(val category: String, val title: String)

private val bookmarkPattern =
    Regex("""[-*]\s+\[([^\[\]]+?)]\(([\w-]+://.+?)\)(.*)$""", RegexOption.MULTILINE)
private val bookmarkTagPattern = Regex("""#([^\s#]+)""")

/** Parses the list-style bookmarks accepted by QOwnNotes Desktop's default parser mode. */
fun parseBookmarks(markdown: String): List<Bookmark> {
    val bookmarks = mutableListOf<Bookmark>()
    bookmarkPattern.findAll(markdown).forEach { match ->
        var additionalText = match.groupValues[3]
        val tags = mutableListOf<String>()
        bookmarkTagPattern.findAll(additionalText).map { it.groupValues[1].trim() }.forEach { tag ->
            if (tag !in tags) {
                tags += tag
                additionalText = additionalText.replace(Regex("#${Regex.escape(tag)}\\b"), "")
            }
        }
        mergeBookmark(
            bookmarks,
            Bookmark(
                url = match.groupValues[2],
                name = match.groupValues[1],
                tags = tags,
                description = additionalText.trim(),
                markdown = match.value
            )
        )
    }
    return bookmarks
}

fun parseBookmarksSource(path: String): BookmarksSource? {
    val normalized = path.trim()
    if (normalized.isEmpty() || normalized.startsWith('/') || normalized.contains('\\')) {
        return null
    }
    val parts = normalized.split('/')
    if (parts.any { it.isBlank() || it == "." || it == ".." }) return null
    val fileName = parts.last()
    if (!fileName.endsWith(".md", ignoreCase = true)) return null
    val title = fileName.dropLast(3)
    if (title.isBlank()) return null
    return BookmarksSource(category = parts.dropLast(1).joinToString("/"), title = title)
}

fun filterBookmarks(
    bookmarks: List<Bookmark>,
    query: String,
    selectedTags: Set<String>
): List<Bookmark> {
    val tokens = query.trim().lowercase(
        Locale.ROOT
    ).split(Regex("""\s+""")).filter(String::isNotEmpty)
    return bookmarks.asSequence()
        .filter { bookmark ->
            selectedTags.all(bookmark.tags::contains) && tokens.all { token ->
                bookmark.name.lowercase(Locale.ROOT).contains(token) ||
                    bookmark.url.lowercase(Locale.ROOT).contains(token) ||
                    bookmark.description.lowercase(Locale.ROOT).contains(token)
            }
        }
        .sortedWith(
            compareBy<Bookmark> { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.url.lowercase(Locale.ROOT) }
        )
        .toList()
}

private fun mergeBookmark(bookmarks: MutableList<Bookmark>, bookmark: Bookmark) {
    val index = bookmarks.indexOfFirst { it.url == bookmark.url }
    if (index == -1) {
        bookmarks += bookmark
        return
    }
    val existing = bookmarks[index]
    bookmarks[index] = existing.copy(
        name = if (
            existing.name.isEmpty() ||
            (bookmark.name.isNotEmpty() && bookmark.name.length > existing.name.length)
        ) {
            bookmark.name
        } else {
            existing.name
        },
        tags = (existing.tags + bookmark.tags).distinct().sorted(),
        description = when {
            existing.description.contains(bookmark.description) -> existing.description
            existing.description.isEmpty() -> bookmark.description
            else -> "${existing.description}, ${bookmark.description}"
        }
    )
}
