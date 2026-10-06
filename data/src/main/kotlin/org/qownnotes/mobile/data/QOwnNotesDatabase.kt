package org.qownnotes.mobile.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import org.qownnotes.mobile.core.SyncDiagnosticSource
import org.qownnotes.mobile.core.SyncState

@Dao
interface NoteDao {
    @Query(
        "SELECT localId, accountId, remoteId, title, category, modifiedAtEpochSeconds, " +
            "favorite, syncState, substr(content, 1, 500) AS excerpt, " +
            "lastSyncedTitle, lastSyncedCategory " +
            "FROM notes WHERE accountId = :accountId " +
            "AND syncState != 'PENDING_DELETION' " +
            "ORDER BY favorite DESC, modifiedAtEpochSeconds DESC, localId ASC"
    )
    fun observeAll(accountId: String): Flow<List<NoteListItemEntity>>

    @Query(
        """SELECT localId, accountId, remoteId, title, category, modifiedAtEpochSeconds,
           favorite, syncState, substr(content, 1, 500) AS excerpt,
           lastSyncedTitle, lastSyncedCategory
           FROM notes WHERE accountId = :accountId
           AND syncState != 'PENDING_DELETION' AND
           (NOT :filterFolder OR category = :folder COLLATE NOCASE OR
             (:subfolderPattern IS NOT NULL AND category LIKE :subfolderPattern ESCAPE '\')) AND
           (:query = '' OR title LIKE '%' || :query || '%' COLLATE NOCASE OR
           (:includeContent AND (content LIKE '%' || :query || '%' COLLATE NOCASE OR
             category LIKE '%' || :query || '%' COLLATE NOCASE)))
           ORDER BY
           CASE WHEN :sortOrder = 0 THEN favorite END DESC,
           CASE WHEN :sortOrder = 0 THEN modifiedAtEpochSeconds END DESC,
           CASE WHEN :sortOrder = 1 THEN title END COLLATE NOCASE ASC,
           CASE WHEN :sortOrder = 2 THEN title END COLLATE NOCASE DESC,
           localId ASC"""
    )
    fun search(
        accountId: String,
        query: String,
        includeContent: Boolean,
        sortOrder: Int,
        /** False lists every folder, including the root. */
        filterFolder: Boolean,
        /** Folder compared case-insensitively; the empty string is the root. */
        folder: String,
        /** Escaped `LIKE` pattern of the folders below [folder], or `null` to exclude them. */
        subfolderPattern: String?
    ): Flow<List<NoteListItemEntity>>

    @Query("SELECT * FROM notes WHERE localId = :localId")
    fun observe(localId: String): Flow<NoteEntity?>

    @Query(
        "SELECT * FROM notes WHERE accountId = :accountId AND category = :category " +
            "AND title = :title AND syncState != 'PENDING_DELETION' " +
            "ORDER BY modifiedAtEpochSeconds DESC, localId ASC LIMIT 1"
    )
    fun observeAt(accountId: String, category: String, title: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE localId = :localId")
    suspend fun get(localId: String): NoteEntity?

    @Query(
        "SELECT * FROM notes WHERE accountId = :accountId AND " +
            "syncState IN ('LOCALLY_CREATED', 'LOCALLY_MODIFIED') ORDER BY localId"
    )
    suspend fun getPending(accountId: String): List<NoteEntity>

    @Query(
        "SELECT * FROM notes WHERE accountId = :accountId " +
            "AND syncState = 'PENDING_DELETION' ORDER BY localId"
    )
    suspend fun getPendingDeletions(accountId: String): List<NoteEntity>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query(
        """UPDATE notes SET localRevision = localRevision + 1,
           syncState = CASE WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END
           WHERE localId = :localId AND readOnly = 0
              AND syncState NOT IN ('CONFLICT', 'REMOTE_MISSING', 'READ_ONLY_CONFLICT')
              AND NOT (syncState = 'FAILED' AND remoteId IS NULL)"""
    )
    suspend fun beginEditing(localId: String): Int

    @Query(
        """UPDATE notes SET syncState = :restoredSyncState
           WHERE localId = :localId AND localRevision = :expectedRevision
             AND syncState IN ('LOCALLY_CREATED', 'LOCALLY_MODIFIED')"""
    )
    suspend fun releaseEditReservation(
        localId: String,
        expectedRevision: Long,
        restoredSyncState: SyncState
    ): Int

    @Query(
        """UPDATE notes SET content = :content,
           modifiedAtEpochSeconds = :modifiedAtEpochSeconds,
           localRevision = localRevision + 1,
           syncState = CASE
             WHEN syncState IN ('REMOTE_MISSING', 'READ_ONLY_CONFLICT')
               OR (syncState = 'FAILED' AND remoteId IS NULL) THEN syncState
             WHEN remoteId IS NULL THEN 'LOCALLY_CREATED'
             ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = CASE
             WHEN syncState IN ('REMOTE_MISSING', 'READ_ONLY_CONFLICT')
               OR (syncState = 'FAILED' AND remoteId IS NULL) THEN lastSyncError
             ELSE NULL END
           WHERE localId = :localId
             AND (readOnly = 0 OR syncState = 'READ_ONLY_CONFLICT')
             AND syncState != 'CONFLICT'
             AND content != :content"""
    )
    suspend fun updateDraft(localId: String, content: String, modifiedAtEpochSeconds: Long): Int

    @Query(
        """UPDATE notes SET title = :title,
           modifiedAtEpochSeconds = :modifiedAtEpochSeconds,
           localRevision = localRevision + 1,
           syncState = CASE
             WHEN syncState = 'FAILED' AND remoteId IS NULL THEN syncState
             WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = CASE WHEN syncState = 'FAILED' AND remoteId IS NULL
             THEN lastSyncError ELSE NULL END
           WHERE localId = :localId AND readOnly = 0
             AND syncState NOT IN ('CONFLICT', 'REMOTE_MISSING', 'READ_ONLY_CONFLICT')
             AND title != :title"""
    )
    suspend fun updateTitle(localId: String, title: String, modifiedAtEpochSeconds: Long): Int

    @Query(
        """UPDATE notes SET title = :title, content = :content,
           modifiedAtEpochSeconds = :modifiedAtEpochSeconds,
           localRevision = localRevision + 1,
           syncState = CASE
             WHEN syncState = 'FAILED' AND remoteId IS NULL THEN syncState
             WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = CASE WHEN syncState = 'FAILED' AND remoteId IS NULL
             THEN lastSyncError ELSE NULL END
           WHERE localId = :localId AND readOnly = 0
             AND syncState NOT IN ('CONFLICT', 'REMOTE_MISSING', 'READ_ONLY_CONFLICT')
             AND (title != :title OR content != :content)"""
    )
    suspend fun updateTitleAndContent(
        localId: String,
        title: String,
        content: String,
        modifiedAtEpochSeconds: Long
    ): Int

    @Query(
        """UPDATE notes SET favorite = :favorite,
           localRevision = localRevision + 1,
           syncState = CASE
             WHEN syncState = 'FAILED' AND remoteId IS NULL THEN syncState
             WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = CASE WHEN syncState = 'FAILED' AND remoteId IS NULL
             THEN lastSyncError ELSE NULL END
           WHERE localId = :localId
             AND syncState NOT IN ('CONFLICT', 'REMOTE_MISSING', 'READ_ONLY_CONFLICT')
             AND favorite != :favorite"""
    )
    suspend fun updateFavorite(localId: String, favorite: Boolean): Int

    @Query(
        """UPDATE notes SET category = :category,
           localRevision = localRevision + 1,
           syncState = CASE
             WHEN syncState = 'FAILED' AND remoteId IS NULL THEN syncState
             WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = CASE WHEN syncState = 'FAILED' AND remoteId IS NULL
             THEN lastSyncError ELSE NULL END
           WHERE localId = :localId AND readOnly = 0
             AND syncState NOT IN ('CONFLICT', 'REMOTE_MISSING', 'READ_ONLY_CONFLICT')
             AND category != :category"""
    )
    suspend fun updateCategory(localId: String, category: String): Int

    @Query(
        """UPDATE notes SET
           syncState = CASE WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = NULL
           WHERE localId = :localId AND syncState = 'FAILED'"""
    )
    suspend fun retry(localId: String): Int

    @Query(
        "UPDATE notes SET localRevision = localRevision + 1, " +
            "syncState = 'PENDING_DELETION', lastSyncError = NULL " +
            "WHERE accountId = :accountId AND localId IN (:localIds)"
    )
    suspend fun moveToTrash(accountId: String, localIds: List<String>)

    @Query("DELETE FROM notes WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("SELECT * FROM notes WHERE accountId = :accountId AND remoteId = :remoteId")
    suspend fun getByRemoteId(accountId: String, remoteId: Long): NoteEntity?

    @Query(
        "SELECT localId, remoteId, category, syncState FROM notes " +
            "WHERE accountId = :accountId AND remoteId IS NOT NULL"
    )
    suspend fun getRemoteNoteReferences(accountId: String): List<RemoteNoteReference>

    @Query(
        "UPDATE notes SET readOnly = :readOnly, syncState = :syncState, lastSyncError = :message " +
            "WHERE localId = :localId"
    )
    suspend fun markServerIssue(
        localId: String,
        readOnly: Boolean,
        syncState: SyncState,
        message: String
    )

    @Query("DELETE FROM notes WHERE accountId = :accountId AND syncState = 'SYNCHRONIZED'")
    suspend fun deleteSynchronized(accountId: String)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY displayName")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun delete(accountId: String)

    @Query("UPDATE accounts SET lastSyncError = :message WHERE id = :accountId")
    suspend fun updateSyncError(accountId: String, message: String?)
}

@Dao
interface SyncDiagnosticDao {
    @Query("SELECT * FROM sync_diagnostics ORDER BY occurredAtEpochSeconds DESC, id DESC")
    suspend fun list(): List<SyncDiagnosticEntity>

    @Insert
    suspend fun insert(diagnostic: SyncDiagnosticEntity)

    @Query(
        "DELETE FROM sync_diagnostics WHERE id NOT IN " +
            "(SELECT id FROM sync_diagnostics ORDER BY occurredAtEpochSeconds DESC, id DESC " +
            "LIMIT :limit)"
    )
    suspend fun trimTo(limit: Int)

    @Query("DELETE FROM sync_diagnostics")
    suspend fun clear()

    @Transaction
    suspend fun record(diagnostic: SyncDiagnosticEntity, limit: Int) {
        insert(diagnostic)
        trimTo(limit)
    }
}

@Dao
interface NoteConflictDao {
    @Query("SELECT * FROM note_conflicts WHERE localId = :localId")
    suspend fun get(localId: String): NoteConflictEntity?

    @Upsert
    suspend fun upsert(conflict: NoteConflictEntity)

    @Query("DELETE FROM note_conflicts WHERE localId = :localId")
    suspend fun delete(localId: String)
}

@Dao
interface NoteTagDao {
    @Query("SELECT * FROM tag_files WHERE accountId = :accountId")
    fun observeFile(accountId: String): Flow<TagFileEntity?>

    @Query("SELECT * FROM tag_files WHERE accountId = :accountId")
    suspend fun file(accountId: String): TagFileEntity?

    @Upsert
    suspend fun upsertFile(file: TagFileEntity)

    @Query("SELECT * FROM note_tags WHERE accountId = :accountId")
    fun observeTags(accountId: String): Flow<List<NoteTagEntity>>

    @Query("SELECT * FROM note_tags WHERE accountId = :accountId")
    suspend fun tags(accountId: String): List<NoteTagEntity>

    @Query("SELECT * FROM note_tag_links WHERE accountId = :accountId")
    fun observeLinks(accountId: String): Flow<List<NoteTagLinkEntity>>

    @Query("SELECT * FROM note_tag_links WHERE accountId = :accountId")
    suspend fun links(accountId: String): List<NoteTagLinkEntity>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<NoteTagEntity>)

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<NoteTagLinkEntity>)

    @androidx.room.Delete
    suspend fun deleteLinks(links: List<NoteTagLinkEntity>)

    @Query("DELETE FROM note_tags WHERE accountId = :accountId")
    suspend fun deleteTags(accountId: String)

    @Query("DELETE FROM note_tag_links WHERE accountId = :accountId")
    suspend fun deleteLinks(accountId: String)

    @Query("SELECT * FROM pending_tag_operations WHERE accountId = :accountId ORDER BY id")
    suspend fun pendingOperations(accountId: String): List<PendingTagOperationEntity>

    @Insert
    suspend fun insertOperation(operation: PendingTagOperationEntity): Long

    @Query("DELETE FROM pending_tag_operations WHERE id IN (:ids)")
    suspend fun deleteOperations(ids: List<Long>)

    @Query("DELETE FROM pending_tag_operations WHERE accountId = :accountId")
    suspend fun deleteOperations(accountId: String)

    @Query("DELETE FROM tag_files WHERE accountId = :accountId")
    suspend fun deleteFile(accountId: String)
}

class DatabaseConverters {
    @TypeConverter fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun syncDiagnosticSourceToString(value: SyncDiagnosticSource): String = value.name

    @TypeConverter
    fun stringToSyncDiagnosticSource(value: String): SyncDiagnosticSource =
        SyncDiagnosticSource.valueOf(value)
}

@Database(
    entities = [
        AccountEntity::class,
        NoteEntity::class,
        SyncDiagnosticEntity::class,
        NoteConflictEntity::class,
        NoteTagEntity::class,
        NoteTagLinkEntity::class,
        PendingTagOperationEntity::class,
        TagFileEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class QOwnNotesDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun noteDao(): NoteDao

    abstract fun syncDiagnosticDao(): SyncDiagnosticDao

    abstract fun noteConflictDao(): NoteConflictDao

    abstract fun noteTagDao(): NoteTagDao

    companion object {
        /**
         * Keeps tag links attached to a note whose server name changes, whichever code path changes
         * it. The key is the name the server last confirmed, or the local name before the first
         * upload. Only notes that have links in the mirror record an operation for `notes.sqlite`.
         *
         * Statements in the body avoid conflict clauses because Room updates notes with
         * `UPDATE OR ABORT`, whose clause would override them.
         */
        internal val NOTE_TAG_RELINK_TRIGGER =
            """CREATE TRIGGER IF NOT EXISTS `note_tag_relink`
               AFTER UPDATE OF `title`, `category`, `lastSyncedTitle`, `lastSyncedCategory`
               ON `notes`
               WHEN (COALESCE(OLD.lastSyncedTitle, OLD.title) IS NOT
                       COALESCE(NEW.lastSyncedTitle, NEW.title)
                     OR TRIM(COALESCE(OLD.lastSyncedCategory, OLD.category), '/') IS NOT
                       TRIM(COALESCE(NEW.lastSyncedCategory, NEW.category), '/'))
                 AND EXISTS (SELECT 1 FROM `note_tag_links`
                   WHERE `accountId` = OLD.accountId
                     AND `fileName` = COALESCE(OLD.lastSyncedTitle, OLD.title)
                     AND `subFolderPath` = TRIM(COALESCE(OLD.lastSyncedCategory, OLD.category), '/'))
               BEGIN
                 INSERT INTO `pending_tag_operations` (`accountId`, `type`, `fileName`,
                   `subFolderPath`, `targetFileName`, `targetSubFolderPath`, `tagPath`)
                 VALUES (OLD.accountId, 'RELINK', COALESCE(OLD.lastSyncedTitle, OLD.title),
                   TRIM(COALESCE(OLD.lastSyncedCategory, OLD.category), '/'),
                   COALESCE(NEW.lastSyncedTitle, NEW.title),
                   TRIM(COALESCE(NEW.lastSyncedCategory, NEW.category), '/'), NULL);
                 INSERT INTO `note_tag_links` (`accountId`, `tagId`, `fileName`,
                   `subFolderPath`)
                 SELECT source.accountId, source.tagId, COALESCE(NEW.lastSyncedTitle, NEW.title),
                   TRIM(COALESCE(NEW.lastSyncedCategory, NEW.category), '/')
                 FROM `note_tag_links` AS source
                 WHERE source.accountId = OLD.accountId
                   AND source.fileName = COALESCE(OLD.lastSyncedTitle, OLD.title)
                   AND source.subFolderPath =
                     TRIM(COALESCE(OLD.lastSyncedCategory, OLD.category), '/')
                   AND NOT EXISTS (SELECT 1 FROM `note_tag_links` AS target
                     WHERE target.accountId = source.accountId AND target.tagId = source.tagId
                       AND target.fileName = COALESCE(NEW.lastSyncedTitle, NEW.title)
                       AND target.subFolderPath =
                         TRIM(COALESCE(NEW.lastSyncedCategory, NEW.category), '/'));
                 DELETE FROM `note_tag_links`
                 WHERE `accountId` = OLD.accountId
                   AND `fileName` = COALESCE(OLD.lastSyncedTitle, OLD.title)
                   AND `subFolderPath` = TRIM(COALESCE(OLD.lastSyncedCategory, OLD.category), '/');
               END"""

        /** Must be added to every builder so that new databases get [NOTE_TAG_RELINK_TRIGGER]. */
        val CALLBACK = object : Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(NOTE_TAG_RELINK_TRIGGER)
            }
        }
    }
}

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN ssoAccountName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE accounts ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE accounts ADD COLUMN apiVersion TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN collectionEtag TEXT")
            db.execSQL(
                "ALTER TABLE accounts ADD COLUMN lastModifiedEpochSeconds INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL("ALTER TABLE accounts ADD COLUMN lastSyncError TEXT")
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN localRevision INTEGER NOT NULL DEFAULT 0")
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN lastSyncedFavorite INTEGER")
        }
    }

val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `sync_diagnostics` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `accountId` TEXT NOT NULL,
                    `occurredAtEpochSeconds` INTEGER NOT NULL,
                    `source` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `details` TEXT NOT NULL,
                    FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sync_diagnostics_accountId` " +
                    "ON `sync_diagnostics` (`accountId`)"
            )
        }
    }

val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `note_conflicts` (
                    `localId` TEXT NOT NULL,
                    `remoteId` INTEGER NOT NULL,
                    `remoteTitle` TEXT NOT NULL,
                    `remoteContent` TEXT NOT NULL,
                    `remoteCategory` TEXT NOT NULL,
                    `remoteModifiedAtEpochSeconds` INTEGER NOT NULL,
                    `remoteEtag` TEXT NOT NULL,
                    `remoteReadOnly` INTEGER NOT NULL,
                    `remoteFavorite` INTEGER NOT NULL,
                    PRIMARY KEY(`localId`),
                    FOREIGN KEY(`localId`) REFERENCES `notes`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )"""
            )
        }
    }

val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            // An early version-6 build stored duplicate base fields in this table. Rebuild it from
            // the columns shared by both version-6 layouts so those installations remain usable.
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `note_conflicts_new` (
                    `localId` TEXT NOT NULL,
                    `remoteId` INTEGER NOT NULL,
                    `remoteTitle` TEXT NOT NULL,
                    `remoteContent` TEXT NOT NULL,
                    `remoteCategory` TEXT NOT NULL,
                    `remoteModifiedAtEpochSeconds` INTEGER NOT NULL,
                    `remoteEtag` TEXT NOT NULL,
                    `remoteReadOnly` INTEGER NOT NULL,
                    `remoteFavorite` INTEGER NOT NULL,
                    PRIMARY KEY(`localId`),
                    FOREIGN KEY(`localId`) REFERENCES `notes`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )"""
            )
            db.execSQL(
                """INSERT INTO `note_conflicts_new` (
                    `localId`, `remoteId`, `remoteTitle`, `remoteContent`, `remoteCategory`,
                    `remoteModifiedAtEpochSeconds`, `remoteEtag`, `remoteReadOnly`, `remoteFavorite`
                ) SELECT
                    `localId`, `remoteId`, `remoteTitle`, `remoteContent`, `remoteCategory`,
                    `remoteModifiedAtEpochSeconds`, `remoteEtag`, `remoteReadOnly`, `remoteFavorite`
                FROM `note_conflicts`"""
            )
            db.execSQL("DROP TABLE `note_conflicts`")
            db.execSQL("ALTER TABLE `note_conflicts_new` RENAME TO `note_conflicts`")
        }
    }

val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `note_tags` (
                    `accountId` TEXT NOT NULL,
                    `tagId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `parentId` INTEGER NOT NULL,
                    `color` TEXT,
                    `priority` INTEGER NOT NULL,
                    PRIMARY KEY(`accountId`, `tagId`),
                    FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )"""
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `note_tag_links` (
                    `accountId` TEXT NOT NULL,
                    `tagId` INTEGER NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `subFolderPath` TEXT NOT NULL,
                    PRIMARY KEY(`accountId`, `tagId`, `fileName`, `subFolderPath`),
                    FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "`index_note_tag_links_accountId_fileName_subFolderPath` " +
                    "ON `note_tag_links` (`accountId`, `fileName`, `subFolderPath`)"
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `pending_tag_operations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `accountId` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `subFolderPath` TEXT NOT NULL,
                    `targetFileName` TEXT,
                    `targetSubFolderPath` TEXT,
                    `tagPath` TEXT,
                    FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )"""
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pending_tag_operations_accountId` " +
                    "ON `pending_tag_operations` (`accountId`)"
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `tag_files` (
                    `accountId` TEXT NOT NULL,
                    `availability` TEXT NOT NULL,
                    `etag` TEXT,
                    `writable` INTEGER NOT NULL,
                    `message` TEXT,
                    PRIMARY KEY(`accountId`),
                    FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )"""
            )
            db.execSQL(QOwnNotesDatabase.NOTE_TAG_RELINK_TRIGGER)
        }
    }

val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_notes_accountId_category` " +
                    "ON `notes` (`accountId`, `category`)"
            )
        }
    }
