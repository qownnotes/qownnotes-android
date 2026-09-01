package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFormattingTest {
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
    fun prefixesEverySelectedLine() {
        val edit = applyMarkdownFormat("one\ntwo\nthree", 1, 7, MarkdownFormatAction.TASK)

        assertEquals("- [ ] one\n- [ ] two\nthree", edit.text)
    }

    @Test
    fun prefixingPreservesCrLfLineEndings() {
        val edit = applyMarkdownFormat("one\r\ntwo\r\nthree", 0, 8, MarkdownFormatAction.QUOTE)

        assertEquals("> one\r\n> two\r\nthree", edit.text)
    }
}
