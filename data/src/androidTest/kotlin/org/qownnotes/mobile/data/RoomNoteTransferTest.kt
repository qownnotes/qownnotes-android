package org.qownnotes.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.SyncState

@RunWith(AndroidJUnit4::class)
class RoomNoteTransferTest {
    private lateinit var database: QOwnNotesDatabase
    private lateinit var notes: RoomNoteRepository

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            QOwnNotesDatabase::class.java
        ).addCallback(QOwnNotesDatabase.CALLBACK).build()
        notes = RoomNoteRepository(database.noteDao())
        for (id in listOf("account", "other")) {
            RoomAccountRepository(database.accountDao()).save(
                Account(id, id, "https://cloud.example", id, id)
            )
        }
    }

    @After
    fun teardown() = database.close()

    @Test
    fun movesSelectionWithoutChangingContentIdentityOrEtag() = runBlocking {
        val first = source("first")
        val second = source("second").copy(remoteId = null, syncState = SyncState.LOCALLY_CREATED)
        notes.save(first)
        notes.save(second)

        assertEquals(2, transfer(listOf("first", "second"), " Projects / Android? "))
        assertEquals(
            first.copy(
                category = "Projects/Android",
                localRevision = 8,
                syncState = SyncState.LOCALLY_MODIFIED
            ),
            notes.get("first")
        )
        assertEquals(
            second.copy(category = "Projects/Android", localRevision = 8),
            notes.get("second")
        )
    }

    @Test
    fun copiesSelectionWithFreshIdentityAndNoRemoteOrConflictState() = runBlocking {
        val first = source("first").copy(readOnly = true, syncState = SyncState.READ_ONLY_CONFLICT)
        val second = source(
            "second"
        ).copy(syncState = SyncState.CONFLICT, lastSyncError = "conflict")
        notes.save(first)
        notes.save(second)

        assertEquals(2, transfer(listOf("first", "second", "first"), "Work", copy = true))
        val copies = notes.observeNotes("account").first().filter {
            it.localId !in
                setOf("first", "second")
        }
        assertEquals(setOf("first", "second"), copies.map { it.title }.toSet())
        for (item in copies) {
            val note = notes.get(item.localId)!!
            assertEquals("# Source\n\nExact markdown  \n", note.content)
            assertEquals("Work", note.category)
            assertEquals(100L, note.modifiedAtEpochSeconds)
            assertEquals(true, note.favorite)
            assertFalse(note.readOnly)
            assertNull(note.remoteId)
            assertNull(note.remoteEtag)
            assertNull(note.lastSyncedTitle)
            assertNull(note.lastSyncedContent)
            assertNull(note.lastSyncedCategory)
            assertNull(note.lastSyncedFavorite)
            assertNull(note.lastSyncError)
            assertEquals(0L, note.localRevision)
            assertEquals(SyncState.LOCALLY_CREATED, note.syncState)
        }
        assertEquals(first, notes.get("first"))
        assertEquals(second, notes.get("second"))
    }

    @Test
    fun duplicatesInSameFolderUseUniqueNamesWithoutChangingMarkdown() = runBlocking {
        notes.save(source("first"))
        notes.save(source("occupied").copy(title = "FIRST (2)"))

        assertEquals(1, transfer(listOf("first"), "", copy = true))
        assertEquals(1, transfer(listOf("first"), "", copy = true))
        assertEquals(
            setOf("first", "FIRST (2)", "first (3)", "first (4)"),
            notes.observeNotes("account").first().map { it.title }.toSet()
        )
    }

    @Test
    fun protectedSelectionIsRejectedWithoutMovingAnyNotes() = runBlocking {
        notes.save(source("first"))
        val protected = listOf(
            source("protected").copy(readOnly = true),
            source("protected").copy(syncState = SyncState.CONFLICT),
            source("protected").copy(syncState = SyncState.REMOTE_MISSING),
            source("protected").copy(syncState = SyncState.READ_ONLY_CONFLICT),
            source("protected").copy(syncState = SyncState.PENDING_DELETION),
            source("protected").copy(remoteId = null, syncState = SyncState.FAILED)
        )
        for (note in protected) {
            notes.save(note)
            assertEquals(0, transfer(listOf("first", "protected"), "Work"))
            assertEquals(source("first"), notes.get("first"))
            assertEquals(note, notes.get("protected"))
        }
    }

    @Test
    fun missingCrossAccountDeletedAndInternalDestinationsDoNotChangeSelection() = runBlocking {
        notes.save(source("first"))
        notes.save(source("other").copy(accountId = "other"))
        notes.save(source("deleted").copy(syncState = SyncState.PENDING_DELETION))
        for (copy in listOf(false, true)) {
            assertEquals(0, transfer(listOf("first", "missing"), "Work", copy))
            assertEquals(0, transfer(listOf("first", "other"), "Work", copy))
            assertEquals(0, transfer(listOf("first", "deleted"), "Work", copy))
            assertEquals(0, transfer(listOf("first"), "media/private", copy))
            assertEquals(0, transfer(listOf("first"), "attachments", copy))
        }
        assertEquals(source("first"), notes.get("first"))
        assertEquals(1, notes.observeNotes("account").first().size)
    }

    @Test
    fun stalePushCannotUndoAMove() = runBlocking {
        val source = source("first")
        notes.save(source)
        transfer(listOf("first"), "Work")
        RoomPushStore(database).applySuccess(
            "first",
            source.localRevision,
            RemoteNote(
                source.remoteId!!,
                "new-etag",
                source.title,
                source.content,
                "",
                10
            )
        )
        val moved = notes.get("first")!!
        assertEquals("Work", moved.category)
        assertEquals(SyncState.LOCALLY_MODIFIED, moved.syncState)
    }

    @Test
    fun storageFailureRollsBackTheEntireCopySelection() = runBlocking {
        notes.save(source("first"))
        notes.save(source("second"))
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER reject_second_copy BEFORE INSERT ON notes
               WHEN NEW.title = 'second' AND NEW.category = 'Work'
               BEGIN SELECT RAISE(ABORT, 'simulated storage failure'); END"""
        )
        var failed = false
        try {
            transfer(listOf("first", "second"), "Work", copy = true)
        } catch (_: android.database.sqlite.SQLiteException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(
            setOf("first", "second"),
            notes.observeNotes("account").first().map {
                it.localId
            }.toSet()
        )
    }

    private suspend fun transfer(ids: List<String>, category: String, copy: Boolean = false) =
        notes.transferNotes("account", ids, category, copy, 100)

    private fun source(id: String) = Note(
        localId = id, accountId = "account", remoteId = id.hashCode().toLong(), title = id,
        content = "# Source\n\nExact markdown  \n", modifiedAtEpochSeconds = 10,
        remoteEtag = "etag", favorite = true, syncState = SyncState.SYNCHRONIZED,
        lastSyncedTitle = id, lastSyncedContent = "base", lastSyncedCategory = "",
        lastSyncedFavorite = true, localRevision = 7
    )
}
