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
import org.qownnotes.mobile.core.NoteTag
import org.qownnotes.mobile.core.NoteTagAvailability
import org.qownnotes.mobile.core.NoteTagKey
import org.qownnotes.mobile.core.NoteTagLink
import org.qownnotes.mobile.core.NoteTagOperation
import org.qownnotes.mobile.core.NoteTagSnapshot
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.SyncState

@RunWith(AndroidJUnit4::class)
class RoomNoteTagRepositoryTest {
    private lateinit var database: QOwnNotesDatabase
    private lateinit var tags: RoomNoteTagRepository
    private val key = NoteTagKey("Meeting", "Work")
    private val base = NoteTagSnapshot(
        tags = listOf(NoteTag(1, "Work"), NoteTag(2, "Home")),
        links = setOf(NoteTagLink(1, key))
    )

    @Before
    fun createDatabase() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            QOwnNotesDatabase::class.java
        ).addCallback(QOwnNotesDatabase.CALLBACK).allowMainThreadQueries().build()
        tags = RoomNoteTagRepository(database)
        RoomAccountRepository(database.accountDao()).save(
            Account("account", "Account", "https://cloud.example", "sso", "user")
        )
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun changesRequireAWritableAvailableFile() = runBlocking {
        assertEquals(NoteTagAvailability.UNKNOWN, tags.observe("account").first().availability)
        assertFalse(tags.link("account", key, listOf("Home")))

        tags.applyTagFile("account", base, "\"e1\"", writable = false)
        assertFalse(tags.link("account", key, listOf("Home")))
        assertFalse(tags.observe("account").first().editable)

        tags.markUnavailable("account", NoteTagAvailability.MISSING, "missing")
        val missing = tags.observe("account").first()
        assertEquals(NoteTagAvailability.MISSING, missing.availability)
        assertTrue(missing.tags.isEmpty())
        assertFalse(tags.link("account", key, listOf("Home")))
    }

    @Test
    fun userChangesUpdateTheMirrorAndQueueOperations() = runBlocking {
        tags.applyTagFile("account", base, "\"e1\"", writable = true)

        assertTrue(tags.link("account", key, listOf("home")))
        assertFalse(tags.link("account", key, listOf("Home")))
        assertTrue(tags.link("account", key, listOf("New")))
        assertTrue(tags.unlink("account", key, listOf("Work")))
        assertFalse(tags.unlink("account", key, listOf("Missing")))

        val state = tags.observe("account").first()
        assertEquals(listOf("Home", "New"), state.tagsOf(key).map(NoteTag::name))
        assertTrue(state.tags.single { it.name == "New" }.id < 0)
        assertEquals(
            listOf(
                NoteTagOperation.Link(key, listOf("home")),
                NoteTagOperation.Link(key, listOf("New")),
                NoteTagOperation.Unlink(key, listOf("Work"))
            ),
            tags.pendingOperations("account").map { it.operation }
        )
        assertEquals("\"e1\"", tags.fileState("account")!!.etag)
    }

    @Test
    fun newFileKeepsPendingOperationsUntilTheyAreCompleted() = runBlocking {
        tags.applyTagFile("account", base, "\"e1\"", writable = true)
        tags.link("account", key, listOf("New"))
        val pending = tags.pendingOperations("account")

        // A newer server file that already has another tag keeps the local change on top.
        val newer = base.copy(links = base.links + NoteTagLink(2, key))
        tags.applyTagFile("account", newer, "\"e2\"", writable = true)
        assertEquals(
            listOf("Home", "New", "Work"),
            tags.observe("account").first().tagsOf(key).map(NoteTag::name)
        )

        val uploaded = NoteTagSnapshot(
            newer.tags + NoteTag(3, "New"),
            newer.links + NoteTagLink(3, key)
        )
        tags.applyTagFile("account", uploaded, "\"e3\"", true, pending.map { it.id })
        val state = tags.observe("account").first()
        assertEquals(3L, state.tags.single { it.name == "New" }.id)
        assertTrue(tags.pendingOperations("account").isEmpty())
        assertEquals("\"e3\"", tags.fileState("account")!!.etag)
    }

    @Test
    fun renamingATaggedNoteRelinksItsTagsWhateverChangesTheName() = runBlocking {
        tags.applyTagFile("account", base, "\"e1\"", writable = true)
        val accounts = RoomAccountRepository(database.accountDao())
        val pull = RoomPullStore(database)
        pull.applyPull("account", pullOf("Meeting", "Work"))
        val localId = database.noteDao().getByRemoteId("account", 42)!!.localId
        val notes = RoomNoteRepository(database.noteDao())

        // A local rename keeps the server name until the server confirms it.
        assertTrue(notes.updateTitle(localId, "Standup", 20))
        assertEquals(listOf(1L), tags.observe("account").first().tagIdsByNote[key]?.toList())

        RoomPushStore(database).applySuccess(
            localId,
            database.noteDao().get(localId)!!.localRevision,
            RemoteNote(42, "n2", "Standup", "Body", "Work", 30)
        )
        val renamed = NoteTagKey("Standup", "Work")
        assertEquals(setOf(1L), tags.observe("account").first().tagIdsByNote[renamed])
        assertNull(tags.observe("account").first().tagIdsByNote[key])

        // A rename by another client arrives through a pull.
        pull.applyPull("account", pullOf("Daily", "Team", etag = "c2"))
        val moved = NoteTagKey("Daily", "Team")
        assertEquals(setOf(1L), tags.observe("account").first().tagIdsByNote[moved])
        assertEquals(
            listOf(
                NoteTagOperation.Relink(key, renamed),
                NoteTagOperation.Relink(renamed, moved)
            ),
            tags.pendingOperations("account").map { it.operation }
        )
        assertEquals(1, accounts.observeAccounts().first().size)
    }

    @Test
    fun untaggedRenamesAndUnsyncedNotesRecordNothingUntilTagged() = runBlocking {
        tags.applyTagFile("account", base, "\"e1\"", writable = true)
        val notes = RoomNoteRepository(database.noteDao())
        notes.save(
            Note(
                localId = "draft",
                accountId = "account",
                title = "Draft",
                content = "",
                modifiedAtEpochSeconds = 1,
                syncState = SyncState.LOCALLY_CREATED
            )
        )
        notes.updateTitle("draft", "Draft 2", 2)
        assertTrue(tags.pendingOperations("account").isEmpty())

        val draftKey = NoteTagKey("Draft 2", "")
        tags.link("account", draftKey, listOf("Home"))
        notes.updateCategory("draft", "Ideas")
        assertEquals(
            setOf(2L),
            tags.observe("account").first().tagIdsByNote[NoteTagKey("Draft 2", "Ideas")]
        )
        assertEquals(
            NoteTagOperation.Relink(draftKey, NoteTagKey("Draft 2", "Ideas")),
            tags.pendingOperations("account").last().operation
        )
    }

    @Test
    fun resetAndAccountRemovalForgetTags() = runBlocking {
        tags.applyTagFile("account", base, "\"e1\"", writable = true)
        tags.link("account", key, listOf("Home"))
        tags.reset("account")
        assertNull(tags.fileState("account"))
        assertTrue(tags.pendingOperations("account").isEmpty())
        assertTrue(tags.observe("account").first().tags.isEmpty())

        tags.applyTagFile("account", base, "\"e1\"", writable = true)
        RoomAccountRepository(database.accountDao()).remove("account")
        assertTrue(database.noteTagDao().links("account").isEmpty())
        tags.applyTagFile("account", base, "\"e1\"", writable = true)
        assertNull(tags.fileState("account"))
    }

    private fun pullOf(title: String, category: String, etag: String = "c1") = PullResult(
        notes = listOf(RemoteNote(42, "n-$etag", title, "Body", category, 10)),
        collectionEtag = etag,
        lastModifiedEpochSeconds = 10
    )
}
