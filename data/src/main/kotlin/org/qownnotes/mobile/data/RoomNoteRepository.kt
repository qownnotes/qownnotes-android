package org.qownnotes.mobile.data

import kotlinx.coroutines.flow.map
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteRepository

class RoomNoteRepository(private val noteDao: NoteDao) : NoteRepository {
    override fun observeNotes(accountId: String) =
        noteDao.observeAll(accountId).map { notes -> notes.map(NoteEntity::toDomain) }

    override fun observeNote(localId: String) = noteDao.observe(localId).map { it?.toDomain() }

    override suspend fun save(note: Note) = noteDao.upsert(note.toEntity())
}
