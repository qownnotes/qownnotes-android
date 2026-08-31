package org.qownnotes.mobile.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote

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

    private fun testAccount() = org.qownnotes.mobile.core.Account(
        "account",
        "Account",
        "https://cloud.example",
        "sso",
        "user"
    )

    private fun pull(title: String, etag: String, modified: Long) = PullResult(
        notes = listOf(RemoteNote(42, etag, title, "# $title", "", modified)),
        collectionEtag = etag,
        lastModifiedEpochSeconds = modified
    )
}
