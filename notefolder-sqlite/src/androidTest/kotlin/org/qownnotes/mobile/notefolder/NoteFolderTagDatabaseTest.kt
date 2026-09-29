package org.qownnotes.mobile.notefolder

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.NoteTagKey
import org.qownnotes.mobile.core.NoteTagLink
import org.qownnotes.mobile.core.NoteTagOperation

@RunWith(AndroidJUnit4::class)
class NoteFolderTagDatabaseTest {
    private lateinit var directory: File
    private lateinit var file: File

    @Before
    fun createFile() {
        directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "note-folder-test-${System.nanoTime()}"
        ).apply { mkdirs() }
        file = File(directory, "notes.sqlite")
        createDesktopDatabase(file)
    }

    @After
    fun deleteFile() {
        directory.deleteRecursively()
    }

    @Test
    fun readsTagsHierarchyAndLinks() {
        val content = NoteFolderTagDatabase.read(file)

        assertEquals(16, content.schemaVersion)
        assertTrue(content.writable)
        assertEquals(
            setOf("Work", "Project", "Home"),
            content.snapshot.tags.map {
                it.name
            }.toSet()
        )
        val project = content.snapshot.tags.single { it.name == "Project" }
        assertEquals(1L, project.parentId)
        assertEquals("#ff0000", content.snapshot.tags.single { it.name == "Work" }.color)
        assertEquals(
            setOf(
                NoteTagLink(1, NoteTagKey("Meeting", "Work/Notes")),
                NoteTagLink(3, NoteTagKey("Groceries", ""))
            ),
            content.snapshot.links
        )
    }

    @Test
    fun linkCreatesMissingTagsWithDesktopConventions() {
        val key = NoteTagKey("Groceries", "")

        assertTrue(
            NoteFolderTagDatabase.apply(
                file,
                listOf(NoteTagOperation.Link(key, listOf("work", "Project", "Leaf")))
            )
        )

        val snapshot = NoteFolderTagDatabase.read(file).snapshot
        val leaf = snapshot.tags.single { it.name == "Leaf" }
        assertEquals(2L, leaf.parentId)
        assertTrue(NoteTagLink(leaf.id, key) in snapshot.links)
        assertEquals(3, snapshot.tags.count { it.name != "Leaf" })
        query("SELECT created, stale_date FROM noteTagLink WHERE tag_id = ${leaf.id}") {
            assertTrue(it.getString(0).matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
            assertTrue(it.isNull(1))
        }
        query("SELECT updated FROM tag WHERE id = 1") {
            assertFalse(it.getString(0) == "2020-01-01 00:00:00")
        }
    }

    @Test
    fun repeatedOperationsDoNotChangeTheFile() {
        val key = NoteTagKey("Meeting", "Work/Notes")
        assertFalse(
            NoteFolderTagDatabase.apply(file, listOf(NoteTagOperation.Link(key, listOf("WORK"))))
        )
        assertFalse(
            NoteFolderTagDatabase.apply(
                file,
                listOf(
                    NoteTagOperation.Unlink(key, listOf("Missing")),
                    NoteTagOperation.Relink(NoteTagKey("Absent", ""), NoteTagKey("Other", ""))
                )
            )
        )
    }

    @Test
    fun unlinkAndRelinkUpdateOnlyTheAffectedNote() {
        val meeting = NoteTagKey("Meeting", "Work/Notes")
        val renamed = NoteTagKey("Standup", "Work")

        assertTrue(
            NoteFolderTagDatabase.apply(
                file,
                listOf(
                    NoteTagOperation.Link(meeting, listOf("Home")),
                    NoteTagOperation.Relink(meeting, renamed),
                    NoteTagOperation.Unlink(renamed, listOf("Work"))
                )
            )
        )

        assertEquals(
            setOf(
                NoteTagLink(3, renamed),
                NoteTagLink(3, NoteTagKey("Groceries", ""))
            ),
            NoteFolderTagDatabase.read(file).snapshot.links
        )
    }

    @Test
    fun writingKeepsTheDesktopFileFormatAndOtherTables() {
        NoteFolderTagDatabase.apply(
            file,
            listOf(NoteTagOperation.Link(NoteTagKey("New", ""), listOf("Fresh")))
        )

        val header = file.readBytes()
        assertEquals(1, header[18].toInt())
        assertEquals(1, header[19].toInt())
        assertFalse(File(file.path + "-wal").exists())
        assertFalse(File(file.path + "-journal").exists())
        query("SELECT count(*) FROM sqlite_master WHERE name = 'android_metadata'") {
            assertEquals(0, it.getInt(0))
        }
        query("SELECT file_name FROM trashItem") { assertEquals("Deleted.md", it.getString(0)) }
        query("SELECT value FROM appData WHERE name = 'database_version'") {
            assertEquals("16", it.getString(0))
        }
    }

    @Test
    fun unknownNewerVersionIsReadOnly() {
        exec("UPDATE appData SET value = '17' WHERE name = 'database_version'")

        val content = NoteFolderTagDatabase.read(file)
        assertFalse(content.writable)
        assertIncompatible {
            NoteFolderTagDatabase.apply(
                file,
                listOf(NoteTagOperation.Link(NoteTagKey("A", ""), listOf("B")))
            )
        }
        query("SELECT count(*) FROM tag") { assertEquals(3, it.getInt(0)) }
    }

    @Test
    fun rejectsOldMissingColumnsWalAndDamagedFiles() {
        exec("UPDATE appData SET value = '14' WHERE name = 'database_version'")
        assertIncompatible { NoteFolderTagDatabase.read(file) }

        createDesktopDatabase(file)
        exec("CREATE TABLE tmp AS SELECT id, tag_id, note_file_name FROM noteTagLink")
        exec("DROP TABLE noteTagLink")
        exec("ALTER TABLE tmp RENAME TO noteTagLink")
        assertIncompatible { NoteFolderTagDatabase.read(file) }

        createDesktopDatabase(file)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.rawQuery("PRAGMA journal_mode=WAL", null).use { cursor -> cursor.moveToFirst() }
        }
        assertIncompatible { NoteFolderTagDatabase.read(file) }

        file.writeText("not a database")
        assertIncompatible { NoteFolderTagDatabase.read(file) }

        createDesktopDatabase(file)
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size / 2))
        assertIncompatible { NoteFolderTagDatabase.read(file) }
    }

    @Test
    fun missingVersionIsIncompatible() {
        exec("DELETE FROM appData")
        assertIncompatible { NoteFolderTagDatabase.read(file) }
    }

    private fun assertIncompatible(block: () -> Unit) {
        try {
            block()
            fail("Expected an incompatible database")
        } catch (_: IncompatibleNoteFolderDatabaseException) {
        }
    }

    private fun exec(sql: String) {
        SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).use { it.execSQL(sql) }
    }

    private fun query(sql: String, check: (android.database.Cursor) -> Unit) {
        SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        ).use { database ->
            database.rawQuery(sql, null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                check(cursor)
            }
        }
    }

    companion object {
        /** Creates a note-folder database with the QOwnNotes desktop version-16 schema. */
        fun createDesktopDatabase(file: File) {
            file.delete()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
            File(file.path + "-journal").delete()
            val params = SQLiteDatabase.OpenParams.Builder()
                .setOpenFlags(
                    SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
                .setJournalMode("DELETE")
                .build()
            SQLiteDatabase.openDatabase(file, params).use { database ->
                DESKTOP_SCHEMA.forEach(database::execSQL)
            }
        }

        private val DESKTOP_SCHEMA = listOf(
            "CREATE TABLE appData (name VARCHAR(255) PRIMARY KEY, value VARCHAR(255))",
            """CREATE TABLE tag (id INTEGER PRIMARY KEY, name VARCHAR(255) COLLATE NOCASE,
               priority INTEGER DEFAULT 0, created DATETIME DEFAULT current_timestamp,
               parent_id INTEGER DEFAULT 0, color VARCHAR(20), dark_color VARCHAR(20),
               updated DATETIME DEFAULT current_timestamp)""",
            "CREATE INDEX idxTagParent ON tag( parent_id )",
            "CREATE UNIQUE INDEX idxUniqueTag ON tag (name, parent_id)",
            """CREATE TABLE noteTagLink (id INTEGER PRIMARY KEY, tag_id INTEGER,
               note_file_name VARCHAR(255) DEFAULT '', note_sub_folder_path TEXT DEFAULT '',
               created DATETIME DEFAULT current_timestamp, stale_date DATETIME DEFAULT NULL)""",
            """CREATE UNIQUE INDEX idxUniqueTagNoteLink
               ON noteTagLink (tag_id, note_file_name, note_sub_folder_path)""",
            """CREATE TABLE trashItem (id INTEGER PRIMARY KEY, file_name VARCHAR(255),
               file_size INTEGER, note_sub_folder_path_data TEXT,
               created DATETIME DEFAULT current_timestamp)""",
            "INSERT INTO appData (name, value) VALUES ('database_version', '16')",
            """INSERT INTO tag (id, name, parent_id, color, updated)
               VALUES (1, 'Work', 0, '#ff0000', '2020-01-01 00:00:00')""",
            "INSERT INTO tag (id, name, parent_id) VALUES (2, 'Project', 1)",
            "INSERT INTO tag (id, name, parent_id, priority) VALUES (3, 'Home', 0, 1)",
            """INSERT INTO noteTagLink (tag_id, note_file_name, note_sub_folder_path)
               VALUES (1, 'Meeting', 'Work/Notes')""",
            """INSERT INTO noteTagLink (tag_id, note_file_name, note_sub_folder_path)
               VALUES (3, 'Groceries', '')""",
            "INSERT INTO trashItem (file_name, file_size) VALUES ('Deleted.md', 5)"
        )
    }
}
