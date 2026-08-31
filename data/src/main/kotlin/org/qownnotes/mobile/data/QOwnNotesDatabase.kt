package org.qownnotes.mobile.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.qownnotes.mobile.core.SyncState

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE accountId = :accountId ORDER BY modifiedAtEpochSeconds DESC")
    fun observeAll(accountId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE localId = :localId")
    fun observe(localId: String): Flow<NoteEntity?>

    @Upsert
    suspend fun upsert(note: NoteEntity)
}

@Dao
interface AccountDao {
    @Upsert
    suspend fun upsert(account: AccountEntity)
}

class DatabaseConverters {
    @TypeConverter fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)
}

@Database(entities = [AccountEntity::class, NoteEntity::class], version = 1, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class QOwnNotesDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun noteDao(): NoteDao
}
