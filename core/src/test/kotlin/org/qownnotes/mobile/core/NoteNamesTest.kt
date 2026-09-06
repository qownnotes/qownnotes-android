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

    @Test
    fun `replaces the first heading with the new title`() {
        val content = "# Old title\n\nSome body text.\n"
        val result = NoteNames.replaceFirstHeading(content, "New title")
        assertEquals("# New title\n\nSome body text.\n", result)
    }

    @Test
    fun `replaces heading when body is empty`() {
        val content = "# Old title\n\n"
        val result = NoteNames.replaceFirstHeading(content, "Renamed")
        assertEquals("# Renamed\n\n", result)
    }

    @Test
    fun `leaves content unchanged when no heading exists`() {
        val content = "Just some text without a heading.\n"
        val result = NoteNames.replaceFirstHeading(content, "New title")
        assertEquals(content, result)
    }

    @Test
    fun `leaves blank content unchanged`() {
        assertEquals("", NoteNames.replaceFirstHeading("", "New title"))
        assertEquals("  \n", NoteNames.replaceFirstHeading("  \n", "New title"))
    }

    @Test
    fun `replaces only the first heading`() {
        val content = "# First\n## Second\n### Third\n"
        val result = NoteNames.replaceFirstHeading(content, "Replaced")
        assertEquals("# Replaced\n## Second\n### Third\n", result)
    }

    @Test
    fun `preserves content after the heading`() {
        val content = "# Title\n\nLine 1\nLine 2\nLine 3\n"
        val result = NoteNames.replaceFirstHeading(content, "Updated")
        assertEquals("# Updated\n\nLine 1\nLine 2\nLine 3\n", result)
    }
}
