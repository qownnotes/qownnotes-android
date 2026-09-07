package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCategoriesTest {
    @Test
    fun `selectable categories exclude QOwnNotes internal folder trees`() {
        val categories = NoteCategories.selectable(
            listOf(
                note(""),
                note("Work"),
                note("work"),
                note("Projects/media"),
                note("media"),
                note("Media/images"),
                note("attachments"),
                note("attachments/archive")
            )
        )

        assertEquals(listOf("Projects/media", "Work"), categories)
    }

    @Test
    fun `scopes distinguish undefined all and a named category`() {
        assertTrue(NoteCategories.matches("", NoteCategoryScope.Undefined))
        assertFalse(NoteCategories.matches("Work", NoteCategoryScope.Undefined))
        assertTrue(NoteCategories.matches("", NoteCategoryScope.All))
        assertTrue(NoteCategories.matches("Work", NoteCategoryScope.All))
        assertTrue(NoteCategories.matches("work", NoteCategoryScope.Category("Work")))
        assertFalse(NoteCategories.matches("Work/Archive", NoteCategoryScope.Category("Work")))
    }

    private fun note(category: String) = Note(
        localId = category,
        accountId = "account",
        title = "Note",
        content = "",
        category = category,
        modifiedAtEpochSeconds = 0,
        syncState = SyncState.SYNCHRONIZED
    )
}
