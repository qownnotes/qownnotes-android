package org.qownnotes.mobile.core

import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotes(accountId: String): Flow<List<Note>>

    fun searchNotes(accountId: String, query: String): Flow<List<Note>>

    fun observeNote(localId: String): Flow<Note?>

    suspend fun save(note: Note)
}

interface PullBackend {
    val capabilities: BackendCapabilities

    suspend fun validateAccount(account: Account): String

    suspend fun pull(account: Account, checkpoint: PullCheckpoint): PullResult
}

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>

    suspend fun get(accountId: String): Account?

    suspend fun save(account: Account)
}

interface PullStore {
    suspend fun applyPull(accountId: String, result: PullResult)
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
