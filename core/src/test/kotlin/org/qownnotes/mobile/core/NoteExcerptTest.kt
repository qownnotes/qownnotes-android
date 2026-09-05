package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteExcerptTest {

    @Test
    fun blankContentReturnsEmpty() {
        assertEquals("", NoteExcerpt.of(""))
        assertEquals("", NoteExcerpt.of("   "))
        assertEquals("", NoteExcerpt.of("\n\n\n"))
    }

    @Test
    fun plainTextReturnedAsIs() {
        assertEquals("Hello world", NoteExcerpt.of("Hello world"))
    }

    @Test
    fun headingsStripped() {
        assertEquals("Title", NoteExcerpt.of("# Title"))
        assertEquals("Sub", NoteExcerpt.of("## Sub"))
        assertEquals("Deep", NoteExcerpt.of("###### Deep"))
    }

    @Test
    fun unorderedListMarkersStripped() {
        assertEquals("Item one", NoteExcerpt.of("- Item one"))
        assertEquals("Item two", NoteExcerpt.of("* Item two"))
        assertEquals("Item three", NoteExcerpt.of("+ Item three"))
    }

    @Test
    fun orderedListMarkersStripped() {
        assertEquals("First", NoteExcerpt.of("1. First"))
        assertEquals("Second", NoteExcerpt.of("42. Second"))
    }

    @Test
    fun taskListMarkersStripped() {
        assertEquals("Done task", NoteExcerpt.of("- [x] Done task"))
        assertEquals("Open task", NoteExcerpt.of("- [ ] Open task"))
        assertEquals("Star task", NoteExcerpt.of("* [x] Star task"))
    }

    @Test
    fun boldAndItalicStripped() {
        assertEquals("bold", NoteExcerpt.of("**bold**"))
        assertEquals("italic", NoteExcerpt.of("*italic*"))
        assertEquals("both", NoteExcerpt.of("***both***"))
    }

    @Test
    fun strikethroughStripped() {
        assertEquals("deleted", NoteExcerpt.of("~~deleted~~"))
    }

    @Test
    fun inlineCodeStripped() {
        assertEquals("func", NoteExcerpt.of("`func`"))
    }

    @Test
    fun fencedCodeBlocksRemoved() {
        val content = """
            Some text
            ```kotlin
            val x = 1
            ```
            More text
        """.trimIndent()
        val excerpt = NoteExcerpt.of(content)
        assert(!excerpt.contains("val x = 1"))
        assert(excerpt.contains("Some text"))
        assert(excerpt.contains("More text"))
    }

    @Test
    fun linksReducedToAnchorText() {
        assertEquals("Click here", NoteExcerpt.of("[Click here](https://example.com)"))
    }

    @Test
    fun imagesRemoved() {
        assertEquals("Before After", NoteExcerpt.of("Before ![alt](img.png) After"))
    }

    @Test
    fun blockquoteMarkersStripped() {
        assertEquals("quoted text", NoteExcerpt.of("> quoted text"))
    }

    @Test
    fun horizontalRulesRemoved() {
        assertEquals("before\nafter", NoteExcerpt.of("before\n---\nafter"))
    }

    @Test
    fun blankLinesCollapsed() {
        val content = "Line one\n\n\n\nLine two"
        assertEquals("Line one\nLine two", NoteExcerpt.of(content))
    }

    @Test
    fun leadingTitleStripped() {
        val content = "# My Note\nSome content here"
        val excerpt = NoteExcerpt.of(content, "My Note")
        assertEquals("Some content here", excerpt)
    }

    @Test
    fun leadingTitleStrippedCaseInsensitive() {
        val content = "# MY NOTE\nDetails"
        val excerpt = NoteExcerpt.of(content, "my note")
        assertEquals("Details", excerpt)
    }

    @Test
    fun titleNotInContentLeftAlone() {
        val content = "Some other content"
        val excerpt = NoteExcerpt.of(content, "Different Title")
        assertEquals("Some other content", excerpt)
    }

    @Test
    fun truncationWithEllipsis() {
        val content = "A".repeat(150)
        val excerpt = NoteExcerpt.of(content)
        assertEquals(101, excerpt.length) // 100 chars + ellipsis
        assert(excerpt.endsWith("\u2026"))
    }

    @Test
    fun contentExactlyMaxLengthNotTruncated() {
        val content = "A".repeat(100)
        val excerpt = NoteExcerpt.of(content)
        assertEquals(100, excerpt.length)
        assert(!excerpt.endsWith("\u2026"))
    }

    @Test
    fun mixedMarkdownStrippedCleanly() {
        val content = """
            # Meeting Notes

            - **Action item**: Follow up with team
            - [Link to doc](https://example.com)
            > Important quote

            ```code block```
            Regular text at the end.
        """.trimIndent()
        val excerpt = NoteExcerpt.of(content)
        assert(!excerpt.contains("#"))
        assert(!excerpt.contains("**"))
        assert(!excerpt.contains("["))
        assert(!excerpt.contains("]"))
        assert(!excerpt.contains(">"))
        assert(!excerpt.contains("```"))
        assert(excerpt.contains("Meeting Notes"))
        assert(excerpt.contains("Action item"))
    }
}
