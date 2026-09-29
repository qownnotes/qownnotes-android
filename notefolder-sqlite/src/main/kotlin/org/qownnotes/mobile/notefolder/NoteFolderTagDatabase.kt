package org.qownnotes.mobile.notefolder

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File
import java.io.RandomAccessFile
import org.qownnotes.mobile.core.NoteTag
import org.qownnotes.mobile.core.NoteTagKey
import org.qownnotes.mobile.core.NoteTagLink
import org.qownnotes.mobile.core.NoteTagOperation
import org.qownnotes.mobile.core.NoteTagSnapshot

/** The tag data of a QOwnNotes desktop `notes.sqlite`, and whether this version may modify it. */
data class NoteFolderTagContent(
    val snapshot: NoteTagSnapshot,
    val schemaVersion: Int,
    val writable: Boolean
)

/** The file is damaged or uses a layout that cannot be read safely. */
class IncompatibleNoteFolderDatabaseException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Reads and modifies the tag tables of a QOwnNotes desktop note-folder database.
 *
 * Only a private copy is ever opened. Writes use the desktop's SQL conventions, leave every other
 * table and column alone, and keep the rollback-journal file format so that the desktop and
 * Nextcloud clients see an ordinary SQLite file.
 */
object NoteFolderTagDatabase {
    /** Desktop schema versions whose tag tables this version understands for writing. */
    val WRITABLE_SCHEMA_VERSIONS = 15..16

    /** The oldest desktop schema whose tag tables have every column this reader needs. */
    const val MINIMUM_SCHEMA_VERSION = 15

    fun read(file: File): NoteFolderTagContent {
        checkHeader(file)
        return open(file, writable = false).use { database ->
            val version = validate(database)
            NoteFolderTagContent(
                snapshot = readSnapshot(database),
                schemaVersion = version,
                writable = version in WRITABLE_SCHEMA_VERSIONS
            )
        }
    }

    /**
     * Applies [operations] to [file] in one transaction. Returns whether any row changed; an
     * unchanged file does not need to be uploaded.
     */
    fun apply(file: File, operations: List<NoteTagOperation>): Boolean {
        checkHeader(file)
        val changed = open(file, writable = true).use { database ->
            val version = validate(database)
            if (version !in WRITABLE_SCHEMA_VERSIONS) {
                throw IncompatibleNoteFolderDatabaseException(
                    "notes.sqlite uses database version $version, which cannot be modified"
                )
            }
            database.beginTransaction()
            try {
                var changes = 0
                operations.forEach { changes += applyOperation(database, it) }
                database.setTransactionSuccessful()
                changes > 0
            } finally {
                database.endTransaction()
            }
        }
        File(file.path + "-journal").delete()
        checkHeader(file)
        if (File(file.path + "-wal").exists()) {
            throw IncompatibleNoteFolderDatabaseException("notes.sqlite was left in WAL mode")
        }
        return changed
    }

    private fun open(file: File, writable: Boolean): SQLiteDatabase {
        // Localized collators would add an android_metadata table to the desktop's file, and an
        // explicit journal mode keeps Android's compatibility WAL from changing its format.
        val params = SQLiteDatabase.OpenParams.Builder()
            .setOpenFlags(
                SQLiteDatabase.NO_LOCALIZED_COLLATORS or
                    if (writable) SQLiteDatabase.OPEN_READWRITE else SQLiteDatabase.OPEN_READONLY
            )
            .apply { if (writable) setJournalMode("DELETE") }
            .build()
        return try {
            SQLiteDatabase.openDatabase(file, params)
        } catch (error: SQLiteException) {
            throw IncompatibleNoteFolderDatabaseException("notes.sqlite cannot be opened", error)
        }
    }

    private fun checkHeader(file: File) {
        val header = ByteArray(HEADER_SIZE)
        val complete = file.length() >= HEADER_SIZE && RandomAccessFile(file, "r").use {
            it.read(header) == HEADER_SIZE
        }
        if (!complete || !header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IncompatibleNoteFolderDatabaseException("notes.sqlite is not an SQLite database")
        }
        // Bytes 18 and 19 are the write and read format versions: 1 is a rollback journal, 2 is
        // WAL. A WAL file would need its sidecar files, which are never synchronized.
        if (header[18].toInt() != 1 || header[19].toInt() != 1) {
            throw IncompatibleNoteFolderDatabaseException(
                "notes.sqlite uses write-ahead logging, which is not supported"
            )
        }
    }

    private fun validate(database: SQLiteDatabase): Int {
        try {
            database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                    throw IncompatibleNoteFolderDatabaseException("notes.sqlite is damaged")
                }
            }
            REQUIRED_COLUMNS.forEach { (table, columns) ->
                val present = mutableSetOf<String>()
                database.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
                    val name = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) present += cursor.getString(name).lowercase()
                }
                val missing = columns.filterNot { it.lowercase() in present }
                if (missing.isNotEmpty()) {
                    throw IncompatibleNoteFolderDatabaseException(
                        "notes.sqlite is missing $table columns: ${missing.joinToString()}"
                    )
                }
            }
            val version = database.rawQuery(
                "SELECT value FROM appData WHERE name = 'database_version'",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.toIntOrNull() else null
            } ?: throw IncompatibleNoteFolderDatabaseException(
                "notes.sqlite has no database version"
            )
            if (version < MINIMUM_SCHEMA_VERSION) {
                throw IncompatibleNoteFolderDatabaseException(
                    "notes.sqlite uses database version $version. Open the note folder in a " +
                        "current QOwnNotes desktop version first."
                )
            }
            return version
        } catch (error: SQLiteException) {
            throw IncompatibleNoteFolderDatabaseException("notes.sqlite cannot be read", error)
        }
    }

    private fun readSnapshot(database: SQLiteDatabase): NoteTagSnapshot {
        val tags = mutableListOf<NoteTag>()
        database.rawQuery(
            "SELECT id, name, parent_id, priority, color FROM tag WHERE name IS NOT NULL",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tags += NoteTag(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    parentId = if (cursor.isNull(2)) 0 else cursor.getLong(2),
                    priority = if (cursor.isNull(3)) 0 else cursor.getInt(3),
                    color = cursor.getString(4)?.takeIf(String::isNotBlank)
                )
            }
        }
        val links = mutableSetOf<NoteTagLink>()
        database.rawQuery(
            """SELECT tag_id, note_file_name, note_sub_folder_path FROM noteTagLink
               WHERE tag_id IS NOT NULL AND note_file_name IS NOT NULL""",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                links += NoteTagLink(
                    cursor.getLong(0),
                    NoteTagKey(cursor.getString(1), cursor.getString(2).orEmpty())
                )
            }
        }
        return NoteTagSnapshot(tags, links)
    }

    private fun applyOperation(database: SQLiteDatabase, operation: NoteTagOperation): Int =
        when (operation) {
            is NoteTagOperation.Link -> link(database, operation)
            is NoteTagOperation.Unlink -> {
                val tagId = resolve(database, operation.tagPath, create = false).lastOrNull()
                if (tagId == null) {
                    0
                } else {
                    database.delete(
                        "noteTagLink",
                        "tag_id = ? AND note_file_name = ? AND note_sub_folder_path = ?",
                        arrayOf(
                            tagId.toString(),
                            operation.key.fileName,
                            operation.key.subFolderPath
                        )
                    )
                }
            }
            is NoteTagOperation.Relink -> relink(database, operation.from, operation.to)
        }

    private fun link(database: SQLiteDatabase, operation: NoteTagOperation.Link): Int {
        if (operation.tagPath.isEmpty()) return 0
        val before = totalChanges(database)
        val ids = resolve(database, operation.tagPath, create = true)
        val tagId = ids.last()
        database.execSQL(
            """INSERT INTO noteTagLink (tag_id, note_file_name, note_sub_folder_path)
               SELECT ?, ?, ? WHERE NOT EXISTS (
                 SELECT 1 FROM noteTagLink
                 WHERE tag_id = ? AND note_file_name = ? AND note_sub_folder_path = ?)""",
            arrayOf<Any>(
                tagId,
                operation.key.fileName,
                operation.key.subFolderPath,
                tagId,
                operation.key.fileName,
                operation.key.subFolderPath
            )
        )
        val linked = totalChanges(database) > before
        if (linked && ids.size > 1) {
            // Desktop sorts tags by their most recent use and touches every ancestor of a tag
            // that is linked to a note.
            ids.dropLast(1).forEach { parentId ->
                database.execSQL(
                    "UPDATE tag SET updated = datetime('now') WHERE id = ?",
                    arrayOf<Any>(parentId)
                )
            }
        }
        return totalChanges(database) - before
    }

    private fun relink(database: SQLiteDatabase, from: NoteTagKey, to: NoteTagKey): Int {
        if (from == to) return 0
        val before = totalChanges(database)
        database.execSQL(
            """INSERT INTO noteTagLink
                 (tag_id, note_file_name, note_sub_folder_path, created, stale_date)
               SELECT source.tag_id, ?, ?, MIN(source.created), NULL
               FROM noteTagLink source
               WHERE source.note_file_name = ? AND source.note_sub_folder_path = ?
                 AND NOT EXISTS (
                   SELECT 1 FROM noteTagLink target WHERE target.tag_id = source.tag_id
                     AND target.note_file_name = ? AND target.note_sub_folder_path = ?)
               GROUP BY source.tag_id""",
            arrayOf<Any>(
                to.fileName,
                to.subFolderPath,
                from.fileName,
                from.subFolderPath,
                to.fileName,
                to.subFolderPath
            )
        )
        database.execSQL(
            "DELETE FROM noteTagLink WHERE note_file_name = ? AND note_sub_folder_path = ?",
            arrayOf<Any>(from.fileName, from.subFolderPath)
        )
        return totalChanges(database) - before
    }

    /** Returns the tag ids along [path]; creates missing tags when [create] is true. */
    private fun resolve(database: SQLiteDatabase, path: List<String>, create: Boolean): List<Long> {
        val ids = mutableListOf<Long>()
        var parentId = 0L
        for (name in path) {
            val existing = database.rawQuery(
                "SELECT id FROM tag WHERE name = ? COLLATE NOCASE AND parent_id = ? ORDER BY id LIMIT 1",
                arrayOf(name, parentId.toString())
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            val id = existing ?: if (create) {
                database.execSQL(
                    "INSERT INTO tag (name, priority, parent_id) VALUES (?, 0, ?)",
                    arrayOf<Any>(name, parentId)
                )
                database.rawQuery("SELECT last_insert_rowid()", null).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(0)
                }
            } else {
                return emptyList()
            }
            ids += id
            parentId = id
        }
        return ids
    }

    private fun totalChanges(database: SQLiteDatabase): Int =
        database.rawQuery("SELECT total_changes()", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private const val HEADER_SIZE = 100
    private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val REQUIRED_COLUMNS = mapOf(
        "appData" to listOf("name", "value"),
        "tag" to listOf("id", "name", "priority", "parent_id", "color", "updated"),
        "noteTagLink" to listOf(
            "id",
            "tag_id",
            "note_file_name",
            "note_sub_folder_path",
            "created",
            "stale_date"
        )
    )
}
