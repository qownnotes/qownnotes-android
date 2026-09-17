package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteConflictTest {
    @Test
    fun mergesIndependentLineAndMetadataChanges() {
        val conflict = conflict(
            baseContent = "one\ntwo\nthree",
            localContent = "one\nlocal two\nthree",
            remoteContent = "one\ntwo\nremote three",
            localTitle = "Local title",
            remoteCategory = "Remote category"
        )

        val result = mergeNoteConflict(conflict)

        assertTrue(result.isClean)
        assertEquals("Local title", result.merged.title)
        assertEquals("Remote category", result.merged.category)
        assertEquals("one\nlocal two\nremote three", result.merged.content)
    }

    @Test
    fun reportsOverlappingLineChangesWithoutDiscardingLocalText() {
        val conflict = conflict(
            baseContent = "one\ntwo\nthree",
            localContent = "one\nlocal\nthree",
            remoteContent = "one\nremote\nthree"
        )

        val result = mergeNoteConflict(conflict)

        assertFalse(result.isClean)
        assertEquals(setOf(NoteMergeField.CONTENT), result.conflicts)
        assertEquals(conflict.local.content, result.merged.content)
    }

    @Test
    fun preservesTrailingNewlineWhenMergingInsertions() {
        val result = mergeNoteConflict(
            conflict(
                baseContent = "one\ntwo\n",
                localContent = "zero\none\ntwo\n",
                remoteContent = "one\ntwo\nthree\n"
            )
        )

        assertTrue(result.isClean)
        assertEquals("zero\none\ntwo\nthree\n", result.merged.content)
    }

    private fun conflict(
        baseContent: String,
        localContent: String,
        remoteContent: String,
        localTitle: String = "Title",
        remoteCategory: String = "Category"
    ): NoteConflict {
        val base = snapshot("Title", baseContent, "Category")
        return NoteConflict(
            localId = "local",
            localRevision = 2,
            remoteId = 42,
            base = base,
            local = snapshot(localTitle, localContent, "Category"),
            remote = snapshot("Title", remoteContent, remoteCategory)
        )
    }

    private fun snapshot(title: String, content: String, category: String) = NoteVersionSnapshot(
        title = title,
        content = content,
        category = category,
        modifiedAtEpochSeconds = 10,
        etag = "etag",
        readOnly = false,
        favorite = false
    )
}
