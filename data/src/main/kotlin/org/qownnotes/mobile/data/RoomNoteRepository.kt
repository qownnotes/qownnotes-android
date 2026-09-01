package org.qownnotes.mobile.data

import kotlinx.coroutines.flow.map
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteRepository

class RoomNoteRepository(private val noteDao: NoteDao) : NoteRepository {
    override fun observeNotes(accountId: String) =
        noteDao.observeAll(accountId).map { notes -> notes.map(NoteEntity::toDomain) }

    override fun searchNotes(accountId: String, query: String) =
        noteDao.search(accountId, query.trim()).map { notes -> notes.map(NoteEntity::toDomain) }

    override fun observeNote(localId: String) = noteDao.observe(localId).map { it?.toDomain() }

    override suspend fun get(localId: String) = noteDao.get(localId)?.toDomain()

    override suspend fun pending(accountId: String) =
        noteDao.getPending(accountId).map(NoteEntity::toDomain)

    override suspend fun save(note: Note) = noteDao.upsert(note.toEntity())

    override suspend fun beginEditing(localId: String): Note? {
        if (noteDao.beginEditing(localId) == 0) return null
        return noteDao.get(localId)?.toDomain()
    }

    override suspend fun updateDraft(
        localId: String,
        content: String,
        modifiedAtEpochSeconds: Long
    ): Boolean = noteDao.updateDraft(localId, content, modifiedAtEpochSeconds) > 0

    override suspend fun retry(localId: String): Boolean = noteDao.retry(localId) > 0
}
