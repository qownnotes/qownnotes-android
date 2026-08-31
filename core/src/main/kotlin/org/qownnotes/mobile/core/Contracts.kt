package org.qownnotes.mobile.core

import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotes(accountId: String): Flow<List<Note>>

    fun observeNote(localId: String): Flow<Note?>

    suspend fun save(note: Note)
}

interface NoteBackend {
    val capabilities: BackendCapabilities

    suspend fun pull(accountId: String): List<Note>

    suspend fun push(note: Note): Note
}

data class BackendCapabilities(
    val categories: Boolean = false,
    val favorites: Boolean = false,
    val attachments: Boolean = false,
    val readOnlyNotes: Boolean = false
)

interface SyncCoordinator {
    suspend fun synchronize(accountId: String)
}

interface MarkdownLinkResolver {
    suspend fun resolve(sourceNoteId: String, target: String): String?
}
