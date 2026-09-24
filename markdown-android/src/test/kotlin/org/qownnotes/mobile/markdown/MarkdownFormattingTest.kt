package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownFormattingTest {
    @Test
    fun insertsAnImageAtTheSelection() {
        assertEquals(
            MarkdownTextEdit("Before ![selected](media/image-unique.png) after", 42, 42),
            insertMarkdownImage(
                "Before selected after",
                7,
                15,
                "photo",
                "media/image-unique.png"
            )
        )
    }

    @Test
    fun escapesTheFallbackImageDescription() {
        assertEquals(
            "![photo \\[one\\] two](../media/image.png)",
            insertMarkdownImage("", 0, 0, "photo [one]\ntwo", "../media/image.png").text
        )
    }

    @Test
    fun wrapsSelectedTextWithoutChangingTheSelectionContents() {
        val edit = applyMarkdownFormat("some text", 5, 9, MarkdownFormatAction.BOLD)

        assertEquals("some **text**", edit.text)
        assertEquals("text", edit.text.substring(edit.selectionStart, edit.selectionEnd))
    }

    @Test
    fun insertsLinkAndSelectsUrlPlaceholder() {
        val edit = applyMarkdownFormat("label", 0, 5, MarkdownFormatAction.LINK)

        assertEquals("[label](url)", edit.text)
        assertEquals("url", edit.text.substring(edit.selectionStart, edit.selectionEnd))
    }

    @Test
    fun extractsAndEscapesAnHtmlTitleForAMarkdownLink() {
        val title = extractHtmlTitle(
            "<html><head><TITLE> One &amp; Two &#91;notes&#93; </TITLE></head></html>"
        )

        assertEquals("One & Two [notes]", title)
        assertEquals(
            "[One & Two \\[notes\\]](https://example.com/article)",
            markdownLink(requireNotNull(title), "https://example.com/article")
        )
    }

    @Test
    fun ignoresMissingAndEmptyHtmlTitles() {
        assertNull(extractHtmlTitle("<html><body>No title</body></html>"))
        assertNull(extractHtmlTitle("<title>  \n </title>"))
    }

    @Test
    fun acceptsOnlyCredentialFreeWebUrls() {
        assertEquals("https://example.com/path", canonicalSafeWebUrl(" https://example.com/path "))
        assertEquals("http://example.com/", canonicalSafeWebUrl("http://example.com"))
        assertNull(canonicalSafeWebUrl("file:///tmp/private"))
        assertNull(canonicalSafeWebUrl("https://user:secret@example.com"))
        assertNull(canonicalSafeWebUrl("not a URL"))
    }

    @Test
    fun prefixesEverySelectedLine() {
        val edit = applyMarkdownFormat("one\ntwo\nthree", 1, 7, MarkdownFormatAction.TASK)

        assertEquals("- [ ] one\n- [ ] two\nthree", edit.text)
    }

    @Test
    fun prefixingPreservesCrLfLineEndings() {
        val edit = applyMarkdownFormat("one\r\ntwo\r\nthree", 0, 8, MarkdownFormatAction.QUOTE)

        assertEquals("> one\r\n> two\r\nthree", edit.text)
    }

    @Test
    fun continuesBulletsNumberedListsAndTasks() {
        assertContinuation("  - item\n", "  - item\n  - ")
        assertContinuation("3. item\n", "3. item\n4. ")
        assertContinuation("- [x] done\n", "- [x] done\n- [ ] ")
    }

    @Test
    fun returnOnAnEmptyItemEndsTheList() {
        assertContinuation("- \n", "\n")
        assertContinuation("- [ ] \n", "\n")
        assertContinuation("first\n  - \n", "first\n\n")
        assertContinuation("1. \n", "\n")
    }

    @Test
    fun doesNotContinuePlainTextOrListsInsideCodeFences() {
        assertEquals(null, continueMarkdownList("plain\n", 5))
        assertEquals(null, continueMarkdownList("```\n- code\n", 10))
    }

    private fun assertContinuation(source: String, expected: String) {
        val edit = requireNotNull(continueMarkdownList(source, source.lastIndex))
        assertEquals(expected, edit.text)
        assertEquals(expected.length, edit.selectionStart)
        assertEquals(expected.length, edit.selectionEnd)
    }
}
