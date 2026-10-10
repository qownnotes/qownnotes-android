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
        assertTrue(cache.persisted("note", "newer"))
        assertFalse(cache.persisted("note", "older"))
        assertEquals("database value", cache.restore("note", "database value"))
        cache.cache("note", "newer edit")
        assertFalse(cache.persisted("note", "newer edit"))
        assertEquals("newer edit", cache.restore("note", "database value"))
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
    fun editingBaselineSurvivesCheckpointsAndIsReplacedForANewSession() {
        val cache = EditorDraftCache()
        cache.beginEditing("note", "original ")
        cache.cache("note", "edited ")
        cache.markPersisted("note", "edited ")
        assertEquals("original ", cache.original("note", "checkpoint"))
        cache.beginEditing("note", "next session ")
        assertEquals("next session ", cache.original("note", "checkpoint"))
        cache.remove(listOf("note"))
        assertEquals("fallback", cache.original("note", "fallback"))
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
