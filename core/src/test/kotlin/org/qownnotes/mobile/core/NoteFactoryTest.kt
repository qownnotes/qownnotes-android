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

    private fun factory() = NoteFactory(
        namingPolicy = QOwnNotesNamingPolicy("Note", clock),
        clock = clock,
        newId = { "local-id" }
    )

    @Test
    fun `uses QOwnNotes local date naming and matching heading`() {
        val note = factory().create("account")

        assertEquals("Note 2026-08-31 14h08s27", note.title)
        assertEquals("# Note 2026-08-31 14h08s27\n", note.content)
        assertEquals(SyncState.LOCALLY_CREATED, note.syncState)
    }

    @Test
    fun `shared text is named by the sharing application and kept under that heading`() {
        val note = factory().createFromSharedText(
            "account",
            SharedText(text = "  https://example.com/article  ", subject = "An article")
        )

        assertEquals("An article", note.title)
        assertEquals("# An article\n\nhttps://example.com/article\n", note.content)
        assertEquals(SyncState.LOCALLY_CREATED, note.syncState)
    }

    @Test
    fun `shared text without a subject keeps the dated default name`() {
        val note = factory().createFromSharedText("account", SharedText(text = "Buy milk"))

        assertEquals("Note 2026-08-31 14h08s27", note.title)
        assertEquals("# Note 2026-08-31 14h08s27\n\nBuy milk\n", note.content)
    }

    /** The name is the name of the file, so it cannot hold what a file name cannot hold. */
    @Test
    fun `a shared subject is sanitized into a usable note name`() {
        val note = factory().createFromSharedText(
            "account",
            SharedText(text = "body", subject = "Report: 2026/08 <draft>")
        )

        assertEquals("Report 2026 08 draft", note.title)
        assertEquals("# Report 2026 08 draft\n\nbody\n", note.content)
    }

    @Test
    fun `a subject holding nothing usable falls back to the dated default name`() {
        val note = factory().createFromSharedText(
            "account",
            SharedText(text = "body", subject = " / ")
        )

        assertEquals("Note 2026-08-31 14h08s27", note.title)
    }

    /** Applications that have no subject sometimes send the shared text as one. */
    @Test
    fun `text repeated as its own subject is not written twice`() {
        val note = factory().createFromSharedText(
            "account",
            SharedText(text = "Buy milk", subject = "Buy milk")
        )

        assertEquals("Buy milk", note.title)
        assertEquals("# Buy milk\n", note.content)
    }

    @Test
    fun `a share holding only a subject becomes an empty named note`() {
        val note = factory().createFromSharedText(
            "account",
            SharedText(text = "   ", subject = "Reminder")
        )

        assertEquals("Reminder", note.title)
        assertEquals("# Reminder\n", note.content)
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
