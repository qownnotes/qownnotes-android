package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteWhitespaceTest {
    @Test
    fun removesSingleSpacesAtLineEndsAndEndOfNote() {
        assertEquals("one\ntwo\n\nlast", clean("one \ntwo \n \nlast "))
        assertEquals("", clean(" "))
        assertEquals("", clean(""))
    }

    @Test
    fun preservesHardBreaksIndentationAndOtherWhitespace() {
        val content = "  indented  \nthree   \ninside a line\ntab\t\nnbsp\u00a0\n  "
        assertEquals(content, clean(content))
    }

    @Test
    fun preservesMixedLineEndingsAndFinalNewline() {
        assertEquals("one\r\ntwo\rthree\n", clean("one \r\ntwo \rthree \n"))
    }

    @Test
    fun cleanupIsIdempotent() {
        val content = "one \ntwo  \nlast "
        assertEquals(clean(content), clean(clean(content)))
    }

    private fun clean(content: String): String = NoteWhitespace.removeSingleTrailingSpaces(content)
}
