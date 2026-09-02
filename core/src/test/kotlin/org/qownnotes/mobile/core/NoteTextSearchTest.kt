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
}
