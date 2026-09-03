package org.qownnotes.mobile.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QOwnNotesDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            QOwnNotesDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )

    @Test
    fun migrationOneToTwoPreservesDataAndAddsSyncDefaults() {
        helper.createDatabase(DATABASE_NAME, 1).use { database ->
            database.execSQL(
                "INSERT INTO accounts (id, displayName, serverUrl) VALUES (?, ?, ?)",
                arrayOf("account", "Account", "https://cloud.example")
            )
            database.execSQL(
                """INSERT INTO notes (
                    localId, accountId, remoteId, title, content, category,
                    modifiedAtEpochSeconds, remoteEtag, readOnly, syncState,
                    lastSyncedTitle, lastSyncedContent, lastSyncedCategory, lastSyncError
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                arrayOf<Any?>(
                    "local",
                    "account",
                    42,
                    "Title",
                    "Content",
                    "Category",
                    10,
                    "note-etag",
                    0L,
                    "SYNCHRONIZED",
                    "Title",
                    "Content",
                    "Category",
                    null
                )
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 2, true, MIGRATION_1_2).use { database ->
            database.query("SELECT * FROM accounts WHERE id = 'account'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Account", cursor.string("displayName"))
                assertEquals("https://cloud.example", cursor.string("serverUrl"))
                assertEquals("", cursor.string("ssoAccountName"))
                assertEquals("", cursor.string("userId"))
                assertNull(cursor.nullableString("apiVersion"))
                assertNull(cursor.nullableString("collectionEtag"))
                assertEquals(
                    0L,
                    cursor.getLong(cursor.getColumnIndexOrThrow("lastModifiedEpochSeconds"))
                )
                assertNull(cursor.nullableString("lastSyncError"))
            }
            database.query("SELECT * FROM notes WHERE localId = 'local'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Title", cursor.string("title"))
                assertEquals("Content", cursor.string("content"))
                assertEquals(42L, cursor.getLong(cursor.getColumnIndexOrThrow("remoteId")))
            }
        }
    }

    @Test
    fun migrationTwoToThreePreservesNotesAndAddsLocalRevision() {
        helper.createDatabase(DATABASE_NAME, 2).use { database ->
            database.execSQL(
                """INSERT INTO accounts (
                    id, displayName, serverUrl, ssoAccountName, userId,
                    lastModifiedEpochSeconds
                ) VALUES (?, ?, ?, ?, ?, ?)""",
                arrayOf("account", "Account", "https://cloud.example", "sso", "user", 0)
            )
            database.execSQL(
                """INSERT INTO notes (
                    localId, accountId, remoteId, title, content, category,
                    modifiedAtEpochSeconds, remoteEtag, readOnly, syncState,
                    lastSyncedTitle, lastSyncedContent, lastSyncedCategory, lastSyncError
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                arrayOf<Any?>(
                    "local", "account", 42, "Title", "Content", "", 10, "etag", 0,
                    "SYNCHRONIZED", "Title", "Content", "", null
                )
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 3, true, MIGRATION_2_3).use { database ->
            database.query("SELECT * FROM notes WHERE localId = 'local'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Content", cursor.string("content"))
                assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("localRevision")))
            }
        }
    }

    @Test
    fun migrationThreeToFourPreservesNotesAndAddsFavoriteDefaults() {
        helper.createDatabase(DATABASE_NAME, 3).use { database ->
            database.execSQL(
                """INSERT INTO accounts (
                    id, displayName, serverUrl, ssoAccountName, userId,
                    lastModifiedEpochSeconds
                ) VALUES (?, ?, ?, ?, ?, ?)""",
                arrayOf("account", "Account", "https://cloud.example", "sso", "user", 0)
            )
            database.execSQL(
                """INSERT INTO notes (
                    localId, accountId, remoteId, title, content, category,
                    modifiedAtEpochSeconds, remoteEtag, readOnly, syncState,
                    lastSyncedTitle, lastSyncedContent, lastSyncedCategory, lastSyncError,
                    localRevision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                arrayOf<Any?>(
                    "local", "account", 42, "Title", "Content", "", 10, "etag", 0,
                    "SYNCHRONIZED", "Title", "Content", "", null, 3
                )
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 4, true, MIGRATION_3_4).use { database ->
            database.query("SELECT * FROM notes WHERE localId = 'local'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Content", cursor.string("content"))
                assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("favorite")))
                assertNull(cursor.nullableString("lastSyncedFavorite"))
                assertEquals(3L, cursor.getLong(cursor.getColumnIndexOrThrow("localRevision")))
            }
        }
    }

    private fun android.database.Cursor.string(column: String) =
        getString(getColumnIndexOrThrow(column))

    private fun android.database.Cursor.nullableString(column: String): String? =
        getString(getColumnIndexOrThrow(column))

    private companion object {
        const val DATABASE_NAME = "migration-test"
    }
}
