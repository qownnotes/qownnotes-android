package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTextSearchTest {
    @Test
    fun findsEveryOccurrenceRegardlessOfCase() {
        val text = "Salt the water, then add salt and more SALT."

        val matches = findTextMatches(text, "salt")

        assertEquals(3, matches.size)
        matches.forEach { assertEquals("salt", text.substring(it.first, it.last + 1).lowercase()) }
    }

    @Test
    fun reportsMatchesWithoutOverlapping() {
        assertEquals(listOf(0 until 2, 2 until 4), findTextMatches("aaaa", "aa"))
        assertEquals(listOf(0 until 2), findTextMatches("aaa", "aa"))
    }

    @Test
    fun matchesPunctuationLiterallyInsteadOfAsAPattern() {
        assertEquals(listOf(6 until 9), findTextMatches("plain a.c text", "a.c"))
        assertEquals(emptyList<IntRange>(), findTextMatches("plain abc text", "a.c"))
    }

    @Test
    fun blankOrAbsentQueriesMatchNothing() {
        assertEquals(emptyList<IntRange>(), findTextMatches("some note text", ""))
        assertEquals(emptyList<IntRange>(), findTextMatches("some note text", "   "))
        assertEquals(emptyList<IntRange>(), findTextMatches("some note text", "missing"))
    }

    @Test
    fun keepsAccentsDistinctSoHighlightsCannotDrift() {
        assertEquals(emptyList<IntRange>(), findTextMatches("café", "cafe"))
        assertEquals(listOf(0 until 4), findTextMatches("café", "CAFÉ"))
    }

    @Test
    fun snippetOfAShortTextKeepsItWhole() {
        val text = "Buy salt\nand pepper"

        val snippet = textMatchSnippet(text, findTextMatches(text, "SALT").single())

        assertEquals("Buy salt and pepper", snippet.text)
        assertEquals("salt", snippet.text.substring(snippet.match.first, snippet.match.last + 1))
    }

    @Test
    fun snippetTrimsLongTextAtWordBoundariesWithEllipses() {
        val text = "alpha bravo charlie delta echo needle foxtrot golf hotel india juliet"
        val match = findTextMatches(text, "needle").single()

        val snippet = textMatchSnippet(text, match, context = 12)

        assertEquals("\u2026delta echo needle foxtrot\u2026", snippet.text)
        assertEquals("needle", snippet.text.substring(snippet.match.first, snippet.match.last + 1))
    }

    @Test
    fun snippetCollapsesWhitespaceInsideTheMatch() {
        val text = "first line\n\n   second line"
        val match = findTextMatches(text, "line\n\n   second").single()

        val snippet = textMatchSnippet(text, match)

        assertEquals("first line second line", snippet.text)
        assertEquals(6 until 17, snippet.match)
    }
}
