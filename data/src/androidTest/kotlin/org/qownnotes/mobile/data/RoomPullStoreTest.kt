package org.qownnotes.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.SyncState

@RunWith(AndroidJUnit4::class)
class RoomPullStoreTest {
    private lateinit var database: QOwnNotesDatabase

    @Before
    fun createDatabase() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                QOwnNotesDatabase::class.java
            ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun repeatedPullKeepsStableLocalIdentityAndUpdatesCheckpoint() = runBlocking {
        val accounts = RoomAccountRepository(database.accountDao())
        val store = RoomPullStore(database)
        accounts.save(testAccount())

        store.applyPull("account", pull("First", "etag-1", 10))
        val first = database.noteDao().getByRemoteId("account", 42)!!
        store.applyPull("account", pull("Updated", "etag-2", 20))
        val second = database.noteDao().getByRemoteId("account", 42)!!

        assertEquals(first.localId, second.localId)
        assertEquals("Updated", second.title)
        assertEquals("etag-2", accounts.get("account")!!.collectionEtag)
    }

    @Test
    fun prunedRecordPreservesCachedNoteAndUpdatesCheckpoint() = runBlocking {
        val accounts = RoomAccountRepository(database.accountDao())
        val store = RoomPullStore(database)
        accounts.save(testAccount())
        store.applyPull("account", pull("Cached", "etag-1", 10))

        store.applyPull(
            "account",
            PullResult(
                notes = listOf(RemoteNote(42, "etag-pruned", null, null, null, null)),
                collectionEtag = "etag-2",
                lastModifiedEpochSeconds = 20
            )
        )

        val note = database.noteDao().getByRemoteId("account", 42)!!
        assertEquals("Cached", note.title)
        assertEquals("etag-1", note.remoteEtag)
        assertEquals("etag-2", accounts.get("account")!!.collectionEtag)
    }

    @Test
    fun completedPullDeletesOnlyMissingSynchronizedNotes() = runBlocking {
        val accounts = RoomAccountRepository(database.accountDao())
        val store = RoomPullStore(database)
        accounts.save(testAccount())
        store.applyPull("account", pull("Remote", "etag-1", 10))
        database.noteDao().upsert(localNote(43, SyncState.LOCALLY_MODIFIED))

        store.applyPull("account", PullResult(emptyList(), "etag-2", 20))

        assertNull(database.noteDao().getByRemoteId("account", 42))
        assertNotNull(database.noteDao().getByRemoteId("account", 43))
    }

    @Test
    fun pullPreservesEveryUnsynchronizedState() = runBlocking {
        val accounts = RoomAccountRepository(database.accountDao())
        val store = RoomPullStore(database)
        accounts.save(testAccount())
        val pendingStates = SyncState.entries.filterNot { it == SyncState.SYNCHRONIZED }
        pendingStates.forEachIndexed { index, state ->
            database.noteDao().upsert(localNote(index.toLong(), state))
        }

        store.applyPull(
            "account",
            PullResult(
                notes = pendingStates.indices.map { index ->
                    RemoteNote(index.toLong(), "remote", "Remote", "Remote", "", 20)
                },
                collectionEtag = "etag-2",
                lastModifiedEpochSeconds = 20
            )
        )

        pendingStates.forEachIndexed { index, state ->
            val note = database.noteDao().getByRemoteId("account", index.toLong())!!
            assertEquals("Local", note.title)
            assertEquals(state, note.syncState)
            assertEquals("local error", note.lastSyncError)
        }
    }

    @Test
    fun notModifiedPullLeavesNotesAndCheckpointUntouched() = runBlocking {
        val accounts = RoomAccountRepository(database.accountDao())
        val store = RoomPullStore(database)
        accounts.save(testAccount().copy(collectionEtag = "etag-1", lastModifiedEpochSeconds = 10))
        database.noteDao().upsert(localNote(42, SyncState.SYNCHRONIZED))

        store.applyPull("account", PullResult(emptyList(), "etag-2", 20, notModified = true))

        assertNotNull(database.noteDao().getByRemoteId("account", 42))
        assertEquals("etag-1", accounts.get("account")!!.collectionEtag)
        assertEquals(10, accounts.get("account")!!.lastModifiedEpochSeconds)
    }

    @Test
    fun failedCheckpointWriteRollsBackEveryNoteMutation() = runBlocking {
        val accounts = RoomAccountRepository(database.accountDao())
        val store = RoomPullStore(database)
        accounts.save(
            testAccount().copy(collectionEtag = "etag-old", lastModifiedEpochSeconds = 10)
        )
        database.noteDao().upsert(localNote(42, SyncState.SYNCHRONIZED, title = "Old 42"))
        database.noteDao().upsert(localNote(43, SyncState.SYNCHRONIZED, title = "Old 43"))
        database.openHelper.writableDatabase.execSQL(
            """CREATE TRIGGER fail_checkpoint BEFORE UPDATE OF collectionEtag ON accounts
               BEGIN SELECT RAISE(ABORT, 'forced checkpoint failure'); END"""
        )

        val failure = runCatching {
            store.applyPull(
                "account",
                PullResult(
                    notes = listOf(
                        RemoteNote(42, "new-42", "Updated 42", "new", "", 20),
                        RemoteNote(44, "new-44", "Inserted 44", "new", "", 20)
                    ),
                    collectionEtag = "etag-new",
                    lastModifiedEpochSeconds = 20
                )
            )
        }

        assertTrue(failure.isFailure)
        assertEquals("Old 42", database.noteDao().getByRemoteId("account", 42)!!.title)
        assertEquals("Old 43", database.noteDao().getByRemoteId("account", 43)!!.title)
        assertNull(database.noteDao().getByRemoteId("account", 44))
        assertEquals("etag-old", accounts.get("account")!!.collectionEtag)
        assertEquals(10, accounts.get("account")!!.lastModifiedEpochSeconds)
    }

    @Test
    fun pullMutatesOnlyTheRequestedAccount() = runBlocking {
        val accounts = RoomAccountRepository(database.accountDao())
        val store = RoomPullStore(database)
        accounts.save(testAccount("account-a").copy(collectionEtag = "etag-a"))
        accounts.save(testAccount("account-b").copy(collectionEtag = "etag-b"))
        database.noteDao().upsert(
            localNote(42, SyncState.SYNCHRONIZED, accountId = "account-a", title = "A 42")
        )
        database.noteDao().upsert(
            localNote(43, SyncState.SYNCHRONIZED, accountId = "account-a", title = "A 43")
        )
        database.noteDao().upsert(
            localNote(42, SyncState.SYNCHRONIZED, accountId = "account-b", title = "B 42")
        )
        database.noteDao().upsert(
            localNote(43, SyncState.SYNCHRONIZED, accountId = "account-b", title = "B 43")
        )

        store.applyPull(
            "account-a",
            PullResult(
                notes = listOf(RemoteNote(42, "a-new", "A updated", "new", "", 20)),
                collectionEtag = "etag-a-new",
                lastModifiedEpochSeconds = 20
            )
        )

        assertEquals("A updated", database.noteDao().getByRemoteId("account-a", 42)!!.title)
        assertNull(database.noteDao().getByRemoteId("account-a", 43))
        assertEquals("B 42", database.noteDao().getByRemoteId("account-b", 42)!!.title)
        assertEquals("B 43", database.noteDao().getByRemoteId("account-b", 43)!!.title)
        assertEquals("etag-a-new", accounts.get("account-a")!!.collectionEtag)
        assertEquals("etag-b", accounts.get("account-b")!!.collectionEtag)
    }

    @Test
    fun unknownAccountPullFailsBeforeMutatingNotes() = runBlocking {
        val failure = runCatching {
            RoomPullStore(database).applyPull(
                "missing",
                PullResult(emptyList(), "etag", 20)
            )
        }

        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun targetedErrorUpdatePreservesSuccessfulCheckpoint() = runBlocking {
        val accounts = RoomAccountRepository(database.accountDao())
        accounts.save(
            testAccount().copy(collectionEtag = "etag-new", lastModifiedEpochSeconds = 20)
        )

        accounts.updateSyncError("account", "offline")

        val account = accounts.get("account")!!
        assertEquals("etag-new", account.collectionEtag)
        assertEquals(20, account.lastModifiedEpochSeconds)
        assertEquals("offline", account.lastSyncError)
    }

    private fun testAccount(id: String = "account") = org.qownnotes.mobile.core.Account(
        id,
        "Account $id",
        "https://cloud.example",
        "sso",
        "user"
    )

    private fun pull(title: String, etag: String, modified: Long) = PullResult(
        notes = listOf(RemoteNote(42, etag, title, "# $title", "", modified)),
        collectionEtag = etag,
        lastModifiedEpochSeconds = modified
    )

    private fun localNote(
        remoteId: Long,
        state: SyncState,
        accountId: String = "account",
        title: String = "Local"
    ) = NoteEntity(
        localId = "$accountId-local-$remoteId",
        accountId = accountId,
        remoteId = remoteId,
        title = title,
        content = "Local content",
        category = "Local category",
        modifiedAtEpochSeconds = 10,
        remoteEtag = "local-etag",
        readOnly = false,
        syncState = state,
        lastSyncedTitle = "Base",
        lastSyncedContent = "Base content",
        lastSyncedCategory = "Base category",
        lastSyncError = "local error"
    )
}
