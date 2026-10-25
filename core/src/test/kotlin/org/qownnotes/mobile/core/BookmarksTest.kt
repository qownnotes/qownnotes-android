package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarksTest {
    @Test
    fun editsAllExactDuplicatesAndPreservesSurroundingsAndLineEndings() {
        val source = "# Links\r\n  * [One](https://one.example) #old\r\n" +
            "Text with [inline](https://one.example)\r\n" +
            "- [Duplicate](https://one.example) Old description\r\n" +
            "- [Keep](https://other.example)"
        val replacement = Bookmark("https://new.example", "New name", listOf("work"), "Reference")
        assertEquals(
            "# Links\r\n  * [New name](https://new.example) #work Reference\r\n" +
                "Text with [inline](https://one.example)\r\n" +
                "- [New name](https://new.example) #work Reference\r\n" +
                "- [Keep](https://other.example)",
            editBookmark(source, "https://one.example", replacement)
        )
    }

    @Test
    fun rejectsMissingUrlsCollisionsAndFieldsThatCannotRoundTrip() {
        val source = "- [One](https://one.example)\n- [Two](https://two.example)"
        val valid = Bookmark("https://new.example", "New", listOf("work"), "Reference")
        assertNull(editBookmark(source, "https://missing.example", valid))
        listOf(
            valid.copy(url = "https://two.example"),
            valid.copy(url = "not a URL"),
            valid.copy(url = "https://new.example) #tag"),
            valid.copy(name = ""),
            valid.copy(name = "[Nested]"),
            valid.copy(description = "Injected\n- [Note](https://bad.example)"),
            valid.copy(description = "Has #tag"),
            valid.copy(tags = listOf("two words")),
            valid.copy(tags = listOf("#tag"))
        ).forEach { assertNull(it.toString(), editBookmark(source, "https://one.example", it)) }
    }

    @Test
    fun editsFinalEntryWithoutAddingANewline() {
        assertEquals(
            "- [New](https://new.example)",
            editBookmark(
                "- [Old](https://old.example)",
                "https://old.example",
                Bookmark("https://new.example", "New")
            )
        )
    }

    @Test
    fun removesAllExactDuplicatesAndPreservesOtherMarkdown() {
        val source = "# Links\r\n\r\n  - [One](https://example.com) #work\r\n" +
            "Text with [inline](https://example.com)  \r\n" +
            "* [Duplicate](https://example.com) Description\r\n" +
            "- [Different case](HTTPS://example.com)\r\n" +
            "- [Keep](https://other.example)"
        assertEquals(
            "# Links\r\n\r\nText with [inline](https://example.com)  \r\n" +
                "- [Different case](HTTPS://example.com)\r\n" +
                "- [Keep](https://other.example)",
            removeBookmark(source, "https://example.com")
        )
    }

    @Test
    fun removesFinalEntryWithoutTrailingNewlineAndLeavesMissingUrlsAlone() {
        val source = "# Links\n- [One](https://example.com)"
        assertEquals("# Links\n", removeBookmark(source, "https://example.com"))
        assertEquals(source, removeBookmark(source, "https://missing.example"))
        assertEquals("", removeBookmark("- [One](https://example.com)\n", "https://example.com"))
    }

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
