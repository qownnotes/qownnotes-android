package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarksTest {
    @Test
    fun parsesDesktopListBookmarksWithTagsAndDescriptions() {
        assertEquals(
            listOf(
                Bookmark(
                    url = "https://example.com/path",
                    name = "Example",
                    tags = listOf("docs", "work"),
                    description = "Useful reference",
                    markdown = "- [Example](https://example.com/path) #docs #work Useful reference"
                ),
                Bookmark(
                    url = "custom-scheme://item",
                    name = "Other",
                    markdown = "* [Other](custom-scheme://item)"
                )
            ),
            parseBookmarks(
                """# Links
                |- [Example](https://example.com/path) #docs #work Useful reference
                |* [Other](custom-scheme://item)
                |
                """.trimMargin()
            )
        )
    }

    @Test
    fun ignoresBasicUrlsOutsideBookmarkListItems() {
        assertEquals(
            emptyList<Bookmark>(),
            parseBookmarks("[Named](https://example.com)\n<https://example.org>")
        )
    }

    @Test
    fun mergesExactDuplicateUrlsLikeDesktop() {
        val bookmarks = parseBookmarks(
            """- [One](https://example.com) #z First
            |- [A longer name](https://example.com) #a #z Second
            |- [Different case](HTTPS://example.com) Third
            |
            """.trimMargin()
        )

        assertEquals(2, bookmarks.size)
        assertEquals(
            Bookmark(
                url = "https://example.com",
                name = "A longer name",
                tags = listOf("a", "z"),
                description = "First, Second",
                markdown = "- [One](https://example.com) #z First"
            ),
            bookmarks.first()
        )
        assertEquals("HTTPS://example.com", bookmarks.last().url)
    }

    @Test
    fun parsesSafeRelativeMarkdownSourcePaths() {
        assertEquals(BookmarksSource("", "Bookmarks"), parseBookmarksSource(" Bookmarks.md "))
        assertEquals(
            BookmarksSource("Work/Reference", "Bookmarks"),
            parseBookmarksSource("Work/Reference/Bookmarks.MD")
        )
    }

    @Test
    fun rejectsInvalidSourcePaths() {
        listOf(
            "",
            "/Bookmarks.md",
            "../Bookmarks.md",
            "Work//Bookmarks.md",
            "Work\\Bookmarks.md",
            "Bookmarks.txt",
            ".md"
        )
            .forEach { assertNull(it, parseBookmarksSource(it)) }
    }

    @Test
    fun filtersWithTextAndAllSelectedTagsThenSortsByName() {
        val bookmarks = listOf(
            Bookmark("https://z.example/docs", "Zulu", listOf("docs", "work"), "Reference"),
            Bookmark("https://a.example", "Alpha", listOf("docs"), "Personal reference"),
            Bookmark("https://b.example", "Beta", listOf("docs", "work"), "Unrelated")
        )

        assertEquals(
            listOf("Beta", "Zulu"),
            filterBookmarks(bookmarks, query = "", selectedTags = setOf("docs", "work"))
                .map(Bookmark::name)
        )
        assertEquals(
            listOf("Zulu"),
            filterBookmarks(bookmarks, query = "z.example reference", selectedTags = emptySet())
                .map(Bookmark::name)
        )
    }
}
