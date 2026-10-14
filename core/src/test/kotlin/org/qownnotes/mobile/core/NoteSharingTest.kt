package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSharingTest {
    @Test
    fun editingTogglesOnlyTheUpdateBit() {
        assertEquals(19, NoteSharing.permissionsWithEdit(17, allowed = true))
        assertEquals(17, NoteSharing.permissionsWithEdit(19, allowed = false))
        assertEquals(3, NoteSharing.permissionsWithEdit(1, allowed = true))
        assertEquals(1, NoteSharing.permissionsWithEdit(1, allowed = false))
    }

    @Test
    fun editAllowedFollowsTheUpdateBit() {
        assertTrue(NoteShare("1", "https://x/s/a", 19, false).editAllowed)
        assertFalse(NoteShare("1", "https://x/s/a", 17, false).editAllowed)
    }

    @Test
    fun passwordsRespectTheServerMinimumLength() {
        val policy = NoteSharingPolicy(linkSharingEnabled = true, passwordMinLength = 8)

        assertFalse(NoteSharing.isAcceptablePassword("", NoteSharingPolicy(true)))
        assertTrue(NoteSharing.isAcceptablePassword("a", NoteSharingPolicy(true)))
        assertFalse(NoteSharing.isAcceptablePassword("short", policy))
        assertTrue(NoteSharing.isAcceptablePassword("long enough", policy))
        assertFalse(NoteSharing.isAcceptablePassword("line\nbreak", NoteSharingPolicy(true)))
    }
}
