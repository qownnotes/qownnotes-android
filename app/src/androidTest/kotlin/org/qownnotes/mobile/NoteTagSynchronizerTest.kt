package org.qownnotes.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.NoteTag
import org.qownnotes.mobile.core.NoteTagAvailability
import org.qownnotes.mobile.core.NoteTagKey
import org.qownnotes.mobile.core.NoteTagLink
import org.qownnotes.mobile.data.QOwnNotesDatabase
import org.qownnotes.mobile.data.RoomAccountRepository
import org.qownnotes.mobile.data.RoomNoteTagRepository
import org.qownnotes.mobile.notefolder.NoteFolderTagDatabase

@RunWith(AndroidJUnit4::class)
class NoteTagSynchronizerTest {
    private val account = Account("account", "Account", "https://cloud.example", "sso", "user")
    private val key = NoteTagKey("Meeting", "Work")
    private lateinit var database: QOwnNotesDatabase
    private lateinit var directory: File
    private lateinit var tags: RoomNoteTagRepository
    private lateinit var server: FakeTagFileBackend
    private lateinit var synchronizer: NoteTagSynchronizer

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, QOwnNotesDatabase::class.java)
            .addCallback(QOwnNotesDatabase.CALLBACK)
            .build()
        RoomAccountRepository(database.accountDao()).save(account)
        directory = File(context.cacheDir, "tag-sync-${System.nanoTime()}").apply { mkdirs() }
        tags = RoomNoteTagRepository(database)
        server = FakeTagFileBackend(File(directory, "server"))
        synchronizer = NoteTagSynchronizer(tags, server, File(directory, "cache"))
    }

    @After
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun missingFileDisablesTagging() = runBlocking {
        synchronizer.synchronize(account)

        val state = tags.observe(account.id).first()
        assertEquals(NoteTagAvailability.MISSING, state.availability)
        assertTrue(state.message!!.contains("notes.sqlite"))
    }

    @Test
    fun downloadsOnlyWhenTheFileChanged() = runBlocking {
        server.create()
        synchronizer.synchronize(account)
        assertEquals(listOf(null), server.downloadEtags)
        assertEquals(
            listOf("Work"),
            tags.observe(account.id).first().tagsOf(key).map(NoteTag::name)
        )

        synchronizer.synchronize(account)
        assertEquals(listOf(null, "\"1\""), server.downloadEtags)

        server.edit("INSERT INTO tag (id, name) VALUES (5, 'Desktop')")
        server.edit(
            "INSERT INTO noteTagLink (tag_id, note_file_name, note_sub_folder_path) " +
                "VALUES (5, 'Meeting', 'Work')"
        )
        synchronizer.synchronize(account)
        assertEquals(
            listOf("Desktop", "Work"),
            tags.observe(account.id).first().tagsOf(key).map(NoteTag::name)
        )
        assertTrue(server.uploads.isEmpty())
    }

    @Test
    fun uploadsPendingChangesWithIfMatch() = runBlocking {
        server.create()
        synchronizer.synchronize(account)
        tags.link(account.id, key, listOf("Mobile"))
        tags.unlink(account.id, key, listOf("Work"))

        synchronizer.synchronize(account)

        assertEquals(listOf("\"1\""), server.uploads)
        val remote = server.read()
        val mobile = remote.tags.single { it.name == "Mobile" }
        assertEquals(setOf(NoteTagLink(mobile.id, key)), remote.links)
        assertTrue(tags.pendingOperations(account.id).isEmpty())
        assertEquals("\"2\"", tags.fileState(account.id)!!.etag)
        assertEquals(mobile.id, tags.observe(account.id).first().tagsOf(key).single().id)
    }

    @Test
    fun concurrentDesktopChangeIsKeptAndTheUploadRetried() = runBlocking {
        server.create()
        synchronizer.synchronize(account)
        tags.link(account.id, key, listOf("Mobile"))
        server.beforeUpload = {
            server.beforeUpload = null
            server.edit(
                "INSERT INTO noteTagLink (tag_id, note_file_name, note_sub_folder_path) " +
                    "VALUES (1, 'Desktop note', '')"
            )
        }

        synchronizer.synchronize(account)

        assertEquals(listOf("\"1\"", "\"2\""), server.uploads)
        val remote = server.read()
        assertTrue(NoteTagLink(1, NoteTagKey("Desktop note", "")) in remote.links)
        assertTrue(remote.links.any { it.key == key && it.tagId != 1L })
        assertTrue(tags.pendingOperations(account.id).isEmpty())
    }

    @Test
    fun operationsThatAreAlreadyAppliedAreNotUploaded() = runBlocking {
        server.create()
        synchronizer.synchronize(account)
        tags.link(account.id, key, listOf("Mobile"))
        server.edit("INSERT INTO tag (id, name) VALUES (9, 'mobile')")
        server.edit(
            "INSERT INTO noteTagLink (tag_id, note_file_name, note_sub_folder_path) " +
                "VALUES (9, 'Meeting', 'Work')"
        )

        synchronizer.synchronize(account)

        assertTrue(server.uploads.isEmpty())
        assertTrue(tags.pendingOperations(account.id).isEmpty())
        assertEquals(
            setOf(1L, 9L),
            tags.observe(account.id).first().tagIdsByNote[key]
        )
    }

    @Test
    fun incompatibleFileIsNeverWritten() = runBlocking {
        server.create()
        synchronizer.synchronize(account)
        tags.link(account.id, key, listOf("Mobile"))
        server.edit("UPDATE appData SET value = '99'")

        synchronizer.synchronize(account)

        assertTrue(server.uploads.isEmpty())
        val state = tags.observe(account.id).first()
        assertEquals(NoteTagAvailability.AVAILABLE, state.availability)
        assertEquals(false, state.writable)
        assertEquals(1, tags.pendingOperations(account.id).size)

        server.file.writeText("garbage")
        server.etag++
        synchronizer.synchronize(account)
        assertEquals(
            NoteTagAvailability.INCOMPATIBLE,
            tags.observe(account.id).first().availability
        )
        assertNull(tags.fileState(account.id)!!.etag)
    }

    private class FakeTagFileBackend(val file: File) :
        org.qownnotes.mobile.core.NoteTagFileBackend {
        var etag = 0
        val downloadEtags = mutableListOf<String?>()
        val uploads = mutableListOf<String>()
        var beforeUpload: (() -> Unit)? = null

        fun create() {
            file.parentFile!!.mkdirs()
            val params = SQLiteDatabase.OpenParams.Builder()
                .setOpenFlags(
                    SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
                .setJournalMode("DELETE")
                .build()
            SQLiteDatabase.openDatabase(file, params).use { database ->
                DESKTOP_SCHEMA.forEach(database::execSQL)
            }
            etag = 1
        }

        fun edit(sql: String) {
            SQLiteDatabase.openDatabase(
                file,
                SQLiteDatabase.OpenParams.Builder()
                    .setOpenFlags(SQLiteDatabase.NO_LOCALIZED_COLLATORS)
                    .setJournalMode("DELETE")
                    .build()
            ).use { it.execSQL(sql) }
            etag++
        }

        fun read() = NoteFolderTagDatabase.read(file).snapshot

        override suspend fun downloadTagFile(
            account: Account,
            etag: String?
        ): org.qownnotes.mobile.core.NoteTagFileDownload {
            downloadEtags += etag
            return when {
                !file.exists() -> org.qownnotes.mobile.core.NoteTagFileDownload.Missing
                etag == "\"${this.etag}\"" ->
                    org.qownnotes.mobile.core.NoteTagFileDownload.NotModified
                else -> org.qownnotes.mobile.core.NoteTagFileDownload.Downloaded(
                    file.readBytes(),
                    "\"${this.etag}\""
                )
            }
        }

        override suspend fun uploadTagFile(
            account: Account,
            content: ByteArray,
            etag: String
        ): String {
            uploads += etag
            beforeUpload?.invoke()
            if (etag != "\"${this.etag}\"") {
                throw org.qownnotes.mobile.core.BackendException.Conflict()
            }
            file.writeBytes(content)
            this.etag++
            return "\"${this.etag}\""
        }
    }

    private companion object {
        val DESKTOP_SCHEMA = listOf(
            "CREATE TABLE appData (name VARCHAR(255) PRIMARY KEY, value VARCHAR(255))",
            """CREATE TABLE tag (id INTEGER PRIMARY KEY, name VARCHAR(255) COLLATE NOCASE,
               priority INTEGER DEFAULT 0, created DATETIME DEFAULT current_timestamp,
               parent_id INTEGER DEFAULT 0, color VARCHAR(20), dark_color VARCHAR(20),
               updated DATETIME DEFAULT current_timestamp)""",
            "CREATE UNIQUE INDEX idxUniqueTag ON tag (name, parent_id)",
            """CREATE TABLE noteTagLink (id INTEGER PRIMARY KEY, tag_id INTEGER,
               note_file_name VARCHAR(255) DEFAULT '', note_sub_folder_path TEXT DEFAULT '',
               created DATETIME DEFAULT current_timestamp, stale_date DATETIME DEFAULT NULL)""",
            """CREATE UNIQUE INDEX idxUniqueTagNoteLink
               ON noteTagLink (tag_id, note_file_name, note_sub_folder_path)""",
            "INSERT INTO appData (name, value) VALUES ('database_version', '16')",
            "INSERT INTO tag (id, name) VALUES (1, 'Work')",
            """INSERT INTO noteTagLink (tag_id, note_file_name, note_sub_folder_path)
               VALUES (1, 'Meeting', 'Work')"""
        )
    }
}
