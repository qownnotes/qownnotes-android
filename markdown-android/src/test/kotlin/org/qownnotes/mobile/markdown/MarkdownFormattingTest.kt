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
    fun headingLevelPrefixesTheCurrentLineAndKeepsTheCaretInItsText() {
        val edit = applyMarkdownHeading("intro\ntitle\nrest", 8, 8, 3)

        assertEquals("intro\n### title\nrest", edit.text)
        assertEquals(12, edit.selectionStart)
        assertEquals(12, edit.selectionEnd)
    }

    @Test
    fun headingLevelReplacesAnExistingMarkerInsteadOfStackingIt() {
        assertEquals("## Title", applyMarkdownHeading("# Title", 4, 4, 2).text)
        assertEquals("###### Title", applyMarkdownHeading("  ## Title", 0, 0, 6).text)
        assertEquals("# ", applyMarkdownHeading("###", 3, 3, 1).text)
    }

    @Test
    fun headingLevelZeroTurnsHeadingsBackIntoText() {
        val edit = applyMarkdownHeading("## One\nplain\n#### Three", 0, 23, 0)

        assertEquals("One\nplain\nThree", edit.text)
        assertEquals(0, edit.selectionStart)
        assertEquals(edit.text.length, edit.selectionEnd)
    }

    @Test
    fun headingLevelAppliesToEverySelectedLine() {
        val edit = applyMarkdownHeading("a\n# b\nc", 0, 7, 2)

        assertEquals("## a\n## b\n## c", edit.text)
        assertEquals("## a\n## b\n## c".length, edit.selectionEnd)
    }

    @Test
    fun headingLevelMovesACaretInsideTheOldMarkerBehindTheNewOne() {
        val edit = applyMarkdownHeading("### Title", 1, 1, 1)

        assertEquals("# Title", edit.text)
        assertEquals(2, edit.selectionStart)
    }

    @Test
    fun hashTagsAreNotMistakenForHeadings() {
        assertEquals("# #tag", applyMarkdownHeading("#tag", 0, 0, 1).text)
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
    fun enterBeforeAnExistingListItemInsertsAnEmptyItemAboveIt() {
        for ((item, prefix) in listOf(
            "- item" to "- ",
            "  * nested" to "  * ",
            "+ item" to "+ ",
            "3. item" to "3. ",
            "12) item" to "12) ",
            "- [x] done" to "- [ ] ",
            "  - [ ] task" to "  - [ ] "
        )) {
            for (before in listOf("", "- previous\n", "paragraph\n")) {
                val source = "$before\n$item\nfollowing"
                val expected = "$before$prefix\n$item\nfollowing"
                val caret = before.length + prefix.length
                assertEquals(
                    MarkdownTextEdit(expected, caret, caret),
                    applyMarkdownNewline(source, before.length)
                )
            }
        }
    }

    @Test
    fun enterBeforePlainTextOrFencedListsDoesNotInsertAnItem() {
        assertNull(applyMarkdownNewline("\nplain", 0))
        assertNull(applyMarkdownNewline("```\n\n- code\n```", 4))
        assertNull(applyMarkdownNewline("~~~\n\n1. code\n~~~", 4))
    }

    @Test
    fun indentMovesTheCaretWithTheCurrentLine() {
        assertEquals(
            MarkdownTextEdit("one\n    two", 10, 10),
            applyMarkdownFormat("one\ntwo", 6, 6, MarkdownFormatAction.INDENT)
        )
        assertEquals(
            MarkdownTextEdit("    ", 4, 4),
            applyMarkdownFormat("", 0, 0, MarkdownFormatAction.INDENT)
        )
        assertEquals(
            MarkdownTextEdit("    \nnext", 4, 4),
            applyMarkdownFormat("\nnext", 0, 0, MarkdownFormatAction.INDENT)
        )
    }

    @Test
    fun indentExcludesTheLineAtTheSelectionEndAndPreservesLineEndings() {
        assertEquals(
            MarkdownTextEdit("    one\r\n    two\r\nthree", 5, 18),
            applyMarkdownFormat("one\r\ntwo\r\nthree", 1, 10, MarkdownFormatAction.INDENT)
        )
    }

    @Test
    fun outdentRemovesOnlyUpToFourLeadingSpacesOnEachSelectedLine() {
        val source = "      one\n  two\nthree\n\tfour"
        assertEquals(
            MarkdownTextEdit("  one\ntwo\nthree\n\tfour", 0, source.length - 6),
            applyMarkdownFormat(source, 0, source.length, MarkdownFormatAction.OUTDENT)
        )
        assertEquals(
            MarkdownTextEdit("text", 0, 0),
            applyMarkdownFormat("  text", 1, 1, MarkdownFormatAction.OUTDENT)
        )
    }

    @Test
    fun indentAndOutdentRestoreAMultilineSelection() {
        val source = "one\ntwo\nthree"
        val indented = applyMarkdownFormat(source, 2, 6, MarkdownFormatAction.INDENT)
        assertEquals("    one\n    two\nthree", indented.text)
        assertEquals(
            MarkdownTextEdit(source, 2, 6),
            applyMarkdownFormat(
                indented.text,
                indented.selectionStart,
                indented.selectionEnd,
                MarkdownFormatAction.OUTDENT
            )
        )
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

    @Test
    fun newlineRemovesOnlyASingleTrailingSpaceAndKeepsTheCaretOnTheNewLine() {
        assertEquals(MarkdownTextEdit("text\n", 5, 5), applyMarkdownNewline("text \n", 5))
        assertEquals(
            MarkdownTextEdit("first \ntext\nrest", 12, 12),
            applyMarkdownNewline("first \ntext \nrest", 12)
        )
        assertEquals(MarkdownTextEdit("\n", 1, 1), applyMarkdownNewline(" \n", 1))
        for (source in listOf("text\n", "text  \n", "text   \n", "text\t\n")) {
            assertNull(applyMarkdownNewline(source, source.lastIndex))
        }
        assertNull(applyMarkdownNewline("text ", 5))
    }

    @Test
    fun trailingSpaceCleanupWorksWithListContinuationAndEmptyItems() {
        for ((source, expected) in listOf(
            "- item \n" to "- item\n- ",
            "3. item \n" to "3. item\n4. ",
            "- [x] done \n" to "- [x] done\n- [ ] ",
            "- item  \n" to "- item  \n- ",
            "- \n" to "\n",
            "- [ ] \n" to "\n"
        )) {
            assertEquals(
                MarkdownTextEdit(expected, expected.length, expected.length),
                applyMarkdownNewline(source, source.lastIndex)
            )
        }
    }

    private fun assertContinuation(source: String, expected: String) {
        val edit = requireNotNull(continueMarkdownList(source, source.lastIndex))
        assertEquals(expected, edit.text)
        assertEquals(expected.length, edit.selectionStart)
        assertEquals(expected.length, edit.selectionEnd)
    }
}
