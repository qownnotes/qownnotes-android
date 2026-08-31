package org.qownnotes.mobile

import android.app.Application
import androidx.room.Room
import java.time.Clock
import org.qownnotes.mobile.core.NoteFactory
import org.qownnotes.mobile.core.QOwnNotesNamingPolicy
import org.qownnotes.mobile.data.AccountEntity
import org.qownnotes.mobile.data.QOwnNotesDatabase
import org.qownnotes.mobile.data.RoomNoteRepository

class QOwnNotesApplication : Application() {
    val component by lazy { ApplicationComponent(this) }
}

class ApplicationComponent(application: Application) {
    private val database =
        Room
            .databaseBuilder(
                application,
                QOwnNotesDatabase::class.java,
                "qownnotes.db"
            ).build()
    val repository = RoomNoteRepository(database.noteDao())
    private val clock = Clock.systemDefaultZone()
    val noteFactory =
        NoteFactory(QOwnNotesNamingPolicy(application.getString(R.string.note_label), clock), clock)

    suspend fun ensureLocalAccount() {
        database.accountDao().upsert(
            AccountEntity(LOCAL_ACCOUNT_ID, "On this device", "local://bootstrap")
        )
    }

    companion object {
        const val LOCAL_ACCOUNT_ID = "local-bootstrap"
    }
}
