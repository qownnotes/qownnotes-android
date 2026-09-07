package org.qownnotes.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDraftCacheTest {
    @Test
    fun olderSnapshotStaysStaleAfterNewerSnapshotIsPersisted() {
        val cache = EditorDraftCache()

        cache.cache("note", "older")
        cache.cache("note", "newer")
        cache.markPersisted("note", "newer")

        assertFalse(cache.current("note", "older"))
        assertTrue(cache.current("note", "newer"))
        assertEquals("database value", cache.restore("note", "database value"))
    }

    @Test
    fun anUnpersistedDraftIsRestoredUntilThatExactContentIsSaved() {
        val cache = EditorDraftCache()

        cache.cache("note", "newer")
        cache.markPersisted("note", "older")

        assertEquals("newer", cache.restore("note", "database value"))

        cache.markPersisted("note", "newer")

        assertEquals("database value", cache.restore("note", "database value"))
    }

    @Test
    fun directReplacementSupersedesAnEditorDraft() {
        val cache = EditorDraftCache()
        cache.cache("note", "discarded edit")

        cache.replaceWithPersisted("note", "restored content")

        assertFalse(cache.current("note", "discarded edit"))
        assertEquals("database value", cache.restore("note", "database value"))
    }
}
