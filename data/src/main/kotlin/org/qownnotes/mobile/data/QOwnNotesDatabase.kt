package org.qownnotes.mobile.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import org.qownnotes.mobile.core.SyncState

@Dao
interface NoteDao {
    @Query(
        "SELECT * FROM notes WHERE accountId = :accountId " +
            "AND syncState != 'PENDING_DELETION' " +
            "ORDER BY favorite DESC, modifiedAtEpochSeconds DESC, localId ASC"
    )
    fun observeAll(accountId: String): Flow<List<NoteEntity>>

    @Query(
        """SELECT * FROM notes WHERE accountId = :accountId
           AND syncState != 'PENDING_DELETION' AND
           (:query = '' OR title LIKE '%' || :query || '%' COLLATE NOCASE OR
           content LIKE '%' || :query || '%' COLLATE NOCASE)
           ORDER BY favorite DESC, modifiedAtEpochSeconds DESC, localId ASC"""
    )
    fun search(accountId: String, query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE localId = :localId")
    fun observe(localId: String): Flow<NoteEntity?>

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
             AND NOT (syncState = 'FAILED' AND remoteId IS NULL)"""
    )
    suspend fun beginEditing(localId: String): Int

    @Query(
        """UPDATE notes SET content = :content,
           modifiedAtEpochSeconds = :modifiedAtEpochSeconds,
           localRevision = localRevision + 1,
           syncState = CASE WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = NULL
           WHERE localId = :localId AND readOnly = 0 AND content != :content"""
    )
    suspend fun updateDraft(localId: String, content: String, modifiedAtEpochSeconds: Long): Int

    @Query(
        """UPDATE notes SET title = :title,
           modifiedAtEpochSeconds = :modifiedAtEpochSeconds,
           localRevision = localRevision + 1,
           syncState = CASE WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = NULL
           WHERE localId = :localId AND readOnly = 0 AND title != :title"""
    )
    suspend fun updateTitle(localId: String, title: String, modifiedAtEpochSeconds: Long): Int

    @Query(
        """UPDATE notes SET favorite = :favorite,
           localRevision = localRevision + 1,
           syncState = CASE WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = NULL
           WHERE localId = :localId AND favorite != :favorite"""
    )
    suspend fun updateFavorite(localId: String, favorite: Boolean): Int

    @Query(
        """UPDATE notes SET
           syncState = CASE WHEN remoteId IS NULL THEN 'LOCALLY_CREATED' ELSE 'LOCALLY_MODIFIED' END,
           lastSyncError = NULL
           WHERE localId = :localId AND syncState = 'FAILED'"""
    )
    suspend fun retry(localId: String): Int

    @Query(
        "UPDATE notes SET syncState = 'PENDING_DELETION', lastSyncError = NULL " +
            "WHERE accountId = :accountId AND localId IN (:localIds)"
    )
    suspend fun moveToTrash(accountId: String, localIds: List<String>)

    @Query("DELETE FROM notes WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("SELECT * FROM notes WHERE accountId = :accountId AND remoteId = :remoteId")
    suspend fun getByRemoteId(accountId: String, remoteId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE accountId = :accountId AND remoteId IS NOT NULL")
    suspend fun getRemoteNotes(accountId: String): List<NoteEntity>

    @Delete
    suspend fun delete(note: NoteEntity)
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

class DatabaseConverters {
    @TypeConverter fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)
}

@Database(entities = [AccountEntity::class, NoteEntity::class], version = 4, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class QOwnNotesDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun noteDao(): NoteDao
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
