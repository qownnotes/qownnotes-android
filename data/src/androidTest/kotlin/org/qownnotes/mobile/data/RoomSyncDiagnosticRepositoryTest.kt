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
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.SyncDiagnostic
import org.qownnotes.mobile.core.SyncDiagnosticSource

@RunWith(AndroidJUnit4::class)
class RoomSyncDiagnosticRepositoryTest {
    private lateinit var database: QOwnNotesDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            QOwnNotesDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun diagnosticsAreBoundedNewestFirstAndRemovedWithAccount() = runBlocking {
        val accounts = RoomAccountRepository(database.accountDao())
        val diagnostics = RoomSyncDiagnosticRepository(database.syncDiagnosticDao(), limit = 2)
        accounts.save(
            Account("account", "Account", "https://cloud.example", "sso", "user")
        )

        repeat(3) { index ->
            diagnostics.record(
                SyncDiagnostic(
                    accountId = "account",
                    occurredAtEpochSeconds = index.toLong(),
                    source = SyncDiagnosticSource.ACCOUNT,
                    category = "Test",
                    details = "diagnostic-$index"
                )
            )
        }

        assertEquals(listOf("diagnostic-2", "diagnostic-1"), diagnostics.list().map { it.details })

        accounts.remove("account")

        assertEquals(emptyList<SyncDiagnostic>(), diagnostics.list())
    }
}
