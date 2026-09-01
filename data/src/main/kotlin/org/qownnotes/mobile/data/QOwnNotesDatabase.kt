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
    @Query("SELECT * FROM notes WHERE accountId = :accountId ORDER BY modifiedAtEpochSeconds DESC")
    fun observeAll(accountId: String): Flow<List<NoteEntity>>

    @Query(
        """SELECT * FROM notes WHERE accountId = :accountId AND
           (:query = '' OR title LIKE '%' || :query || '%' COLLATE NOCASE OR
           content LIKE '%' || :query || '%' COLLATE NOCASE)
           ORDER BY modifiedAtEpochSeconds DESC"""
    )
    fun search(accountId: String, query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE localId = :localId")
    fun observe(localId: String): Flow<NoteEntity?>

    @Upsert
    suspend fun upsert(note: NoteEntity)

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

    @Query("UPDATE accounts SET lastSyncError = :message WHERE id = :accountId")
    suspend fun updateSyncError(accountId: String, message: String?)
}

class DatabaseConverters {
    @TypeConverter fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)
}

@Database(entities = [AccountEntity::class, NoteEntity::class], version = 2, exportSchema = true)
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
