package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.core.RemoteNote

class NextcloudProtocolTest {
    @Test
    fun selectsHighestSupportedApiVersion() {
        val versions =
            NextcloudProtocol.parseVersions(
                JsonParser.parseString("[\"1.1\",\"1.2\",\"1.10\",\"2.0\"]")
            )

        assertEquals("1.10", NextcloudProtocol.selectSupportedVersion(versions))
    }

    @Test
    fun rejectsVersionsOlderThanOnePointTwo() {
        assertNull(NextcloudProtocol.selectSupportedVersion(listOf("0.2", "1.1", "2.0")))
    }

    @Test
    fun acceptsLegacySingleVersionValue() {
        assertEquals(
            listOf("1.2"),
            NextcloudProtocol.parseVersions(JsonParser.parseString("\"1.2\""))
        )
    }

    @Test
    fun followsNumericChunkPendingHeaderUntilCursorIsAbsent() {
        val requestedCursors = mutableListOf<String?>()

        val result = collectPull(PullCheckpoint("old-etag", 10)) { cursor ->
            requestedCursors += cursor
            when (cursor) {
                null -> NotesPage(listOf(note(1)), "etag-1", 20, "next", "1")
                "next" -> NotesPage(listOf(note(2)), "etag-2", 30, pending = "0")
                else -> error("Unexpected cursor: $cursor")
            }
        }

        assertEquals(listOf(null, "next"), requestedCursors)
        assertEquals(listOf(1L, 2L), result.notes.map { it.id })
        assertEquals("etag-2", result.collectionEtag)
        assertEquals(30, result.lastModifiedEpochSeconds)
    }

    @Test
    fun followsCursorWhenPendingHeaderIsAbsent() {
        var requests = 0

        val result = collectPull(PullCheckpoint()) { cursor ->
            requests++
            if (cursor == null) {
                NotesPage(listOf(note(1)), cursor = "next")
            } else {
                NotesPage(listOf(note(2)))
            }
        }

        assertEquals(2, requests)
        assertEquals(listOf(1L, 2L), result.notes.map { it.id })
    }

    @Test
    fun rejectsPendingNotesWithoutCursor() {
        assertThrows(BackendException.Protocol::class.java) {
            collectPull(PullCheckpoint()) { NotesPage(emptyList(), pending = "4") }
        }
    }

    @Test
    fun rejectsRepeatedCursor() {
        assertThrows(BackendException.Protocol::class.java) {
            collectPull(PullCheckpoint()) { NotesPage(emptyList(), cursor = "same", pending = "1") }
        }
    }

    @Test
    fun interruptionDoesNotReturnPartialPull() {
        var requests = 0

        assertThrows(IllegalStateException::class.java) {
            collectPull(PullCheckpoint()) { cursor ->
                requests++
                if (cursor == null) {
                    NotesPage(listOf(note(1)), cursor = "next", pending = "1")
                } else {
                    error("network interrupted")
                }
            }
        }
        assertEquals(2, requests)
    }

    private fun note(id: Long) = RemoteNote(id, null, "Note $id", null, null, 1)
}
