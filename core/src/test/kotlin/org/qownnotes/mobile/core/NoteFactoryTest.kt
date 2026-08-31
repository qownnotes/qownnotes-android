package org.qownnotes.mobile.core

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NoteFactoryTest {
    private val clock =
        Clock.fixed(
            Instant.parse("2026-08-31T12:08:27Z"),
            ZoneId.of("Europe/Berlin")
        )

    @Test
    fun `uses QOwnNotes local date naming and matching heading`() {
        val note =
            NoteFactory(
                namingPolicy = QOwnNotesNamingPolicy("Note", clock),
                clock = clock,
                newId = { "local-id" }
            ).create("account")

        assertEquals("Note 2026-08-31 14h08s27", note.title)
        assertEquals("# Note 2026-08-31 14h08s27\n", note.content)
        assertEquals(SyncState.LOCALLY_CREATED, note.syncState)
    }

    @Test
    fun `same-second notes retain distinct local identities`() {
        var nextId = 0
        val factory =
            NoteFactory(
                namingPolicy = QOwnNotesNamingPolicy("Note", clock),
                clock = clock,
                newId = { (++nextId).toString() }
            )

        val first = factory.create("account")
        val second = factory.create("account")

        assertEquals(first.title, second.title)
        assertNotEquals(first.localId, second.localId)
    }
}
