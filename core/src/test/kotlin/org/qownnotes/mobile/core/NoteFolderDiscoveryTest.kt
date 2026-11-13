package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFolderDiscoveryTest {
    @Test
    fun recognizesLocalizedNextcloudNotesFolderNames() {
        assertTrue(NoteFolderCandidates.isDefaultFolderName("Notes"))
        assertTrue(NoteFolderCandidates.isDefaultFolderName("notizen"))
        assertTrue(NoteFolderCandidates.isDefaultFolderName("ЗАМЕТКИ"))
        assertTrue(NoteFolderCandidates.isDefaultFolderName("یادداشت\u200cها"))
        assertFalse(NoteFolderCandidates.isDefaultFolderName("Documents"))
        assertFalse(NoteFolderCandidates.isDefaultFolderName("Notes old"))
    }

    @Test
    fun countsServerNoteExtensionsAndTheConfiguredSuffix() {
        assertTrue(NoteFolderCandidates.isNoteFile("Todo.md", ".md"))
        assertTrue(NoteFolderCandidates.isNoteFile("Todo.TXT", ".md"))
        assertTrue(NoteFolderCandidates.isNoteFile("Todo.org", ".md"))
        assertTrue(NoteFolderCandidates.isNoteFile("Todo.qon", ".qon"))
        assertFalse(NoteFolderCandidates.isNoteFile("Todo.qon", ".md"))
        assertFalse(NoteFolderCandidates.isNoteFile("notes.sqlite", ".md"))
        assertFalse(NoteFolderCandidates.isNoteFile(".hidden.md", ".md"))
        assertFalse(NoteFolderCandidates.isNoteFile(".qon", ".qon"))
    }

    @Test
    fun offersOnlyUnrelatedFoldersThatHoldNotesWithDesktopFoldersFirst() {
        val offered = NoteFolderCandidates.offered(
            "Notizen",
            listOf(
                folder("Notizen", notes = 3),
                folder("Notizen/Archive", notes = 9),
                folder("Notas", notes = 12),
                folder("Notes", notes = 2, desktop = true),
                folder("Note"),
                folder("Notater", subfolders = 1)
            )
        )

        assertEquals(listOf("Notes", "Notas", "Notater"), offered.map(RemoteNoteFolder::path))
    }

    @Test
    fun neverOffersAFolderTheCurrentFolderLiesIn() {
        val offered = NoteFolderCandidates.offered(
            "/Notes/Work/",
            listOf(folder("Notes", notes = 4), folder("Notizen", notes = 1))
        )

        assertEquals(listOf("Notizen"), offered.map(RemoteNoteFolder::path))
    }

    @Test
    fun normalizesPathsAndNavigates() {
        assertEquals("Work/Notes", NoteFolderCandidates.normalize("/Work//Notes/"))
        assertEquals("Work", NoteFolderCandidates.parent("Work/Notes"))
        assertEquals("", NoteFolderCandidates.parent("Notes"))
        assertEquals("Work/Notes", NoteFolderCandidates.child("Work", "Notes"))
        assertEquals("Notes", NoteFolderCandidates.child("", "Notes"))
    }

    private fun folder(
        path: String,
        notes: Int = 0,
        subfolders: Int = 0,
        desktop: Boolean = false
    ) = RemoteNoteFolder(path, notes, subfolders, desktop)
}
