package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFolderChangesTest {
    @Test
    fun `a created folder is listed and created once`() {
        val changes = NoteFolderChanges().created("Ideas").created("ideas")

        assertEquals(listOf("Ideas"), changes.emptyFolders)
        assertEquals(listOf("Ideas"), changes.pendingCreations)
        assertTrue(changes.pendingRemovals.isEmpty())
    }

    @Test
    fun `creating a folder again cancels its removal and the removal of its parents`() {
        val changes = NoteFolderChanges(pendingRemovals = listOf("Work", "Work/Old", "Other"))
            .created("work/Old/New")

        assertEquals(listOf("Other"), changes.pendingRemovals)
    }

    @Test
    fun `renaming moves created folders and removes the old ones once empty`() {
        val changes = NoteFolderChanges(
            emptyFolders = listOf("Work/Empty", "Private"),
            pendingCreations = listOf("Work/Empty"),
            pendingRemovals = listOf("Job/Stale")
        ).renamed("Work", "Job", listOf("Work", "Work/Archive", "Work/Empty"))

        assertEquals(listOf("Job/Empty", "Private"), changes.emptyFolders)
        assertEquals(listOf("Job/Empty"), changes.pendingCreations)
        // The new name is in use again, and every old folder goes once its notes have moved.
        assertEquals(listOf("Work", "Work/Archive", "Work/Empty"), changes.pendingRemovals)
    }

    @Test
    fun `renaming an empty folder that was already created requests the new one`() {
        val changes = NoteFolderChanges(emptyFolders = listOf("Ideas"))
            .renamed("Ideas", "Plans", listOf("Ideas"))

        assertEquals(listOf("Plans"), changes.emptyFolders)
        assertEquals(listOf("Plans"), changes.pendingCreations)
        assertEquals(listOf("Ideas"), changes.pendingRemovals)
    }

    @Test
    fun `deleting drops created folders and outstanding creations below it`() {
        val changes = NoteFolderChanges(
            emptyFolders = listOf("Work/Empty", "Private"),
            pendingCreations = listOf("Work/Empty", "Private")
        ).deleted("Work", listOf("Work", "Work/Empty"))

        assertEquals(listOf("Private"), changes.emptyFolders)
        assertEquals(listOf("Private"), changes.pendingCreations)
        assertEquals(listOf("Work", "Work/Empty"), changes.pendingRemovals)
    }

    @Test
    fun `created folders are forgotten once notes are in them or below them`() {
        val changes = NoteFolderChanges(emptyFolders = listOf("Ideas", "Work", "Plans"))

        assertEquals(
            listOf("Plans"),
            changes.withNotes(listOf("ideas", "Work/Archive", "Planning")).emptyFolders
        )
        assertSame(changes, changes.withNotes(listOf("", "Other")))
    }

    @Test
    fun `removals start with the deepest folder and creations with the parents`() {
        val changes = NoteFolderChanges(
            pendingCreations = listOf("A/B/C", "A", "A/B"),
            pendingRemovals = listOf("X", "X/Y/Z", "X/Y")
        )

        assertEquals(listOf("A", "A/B", "A/B/C"), changes.creationOrder())
        assertEquals(listOf("X/Y/Z", "X/Y", "X"), changes.removalOrder())
    }
}
