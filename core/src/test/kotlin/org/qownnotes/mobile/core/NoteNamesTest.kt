package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteNamesTest {
    @Test
    fun `keeps a name that is already a valid file name`() {
        assertEquals("Shopping list", NoteNames.sanitize("Shopping list"))
    }

    @Test
    fun `replaces characters that file systems reject`() {
        assertEquals("a b", NoteNames.sanitize("""a/\:*?"<>|b"""))
    }

    @Test
    fun `trims surrounding whitespace and trailing dots`() {
        assertEquals("Meeting notes", NoteNames.sanitize("  Meeting notes ...  "))
    }

    @Test
    fun `collapses runs of whitespace including newlines`() {
        assertEquals("Two words", NoteNames.sanitize("Two \n\t words"))
    }

    @Test
    fun `shortens names that would exceed the file name limit`() {
        val sanitized = NoteNames.sanitize("x".repeat(200))

        assertEquals(120, sanitized.length)
    }

    @Test
    fun `rejects names that hold nothing usable`() {
        assertFalse(NoteNames.isValid("   "))
        assertFalse(NoteNames.isValid("/"))
        assertTrue(NoteNames.isValid("Note"))
    }
}
