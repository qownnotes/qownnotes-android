package org.qownnotes.mobile.core

import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotes(accountId: String): Flow<List<Note>>

    fun searchNotes(accountId: String, query: String): Flow<List<Note>>

    fun observeNote(localId: String): Flow<Note?>

    suspend fun get(localId: String): Note?

    suspend fun pending(accountId: String): List<Note>

    suspend fun pendingDeletions(accountId: String): List<Note>

    suspend fun save(note: Note)

    suspend fun beginEditing(localId: String): Note?

    suspend fun releaseEditReservation(
        localId: String,
        expectedRevision: Long,
        restoredSyncState: SyncState
    ): Boolean

    suspend fun updateDraft(localId: String, content: String, modifiedAtEpochSeconds: Long): Boolean

    suspend fun updateTitle(localId: String, title: String, modifiedAtEpochSeconds: Long): Boolean

    suspend fun updateTitleAndContent(
        localId: String,
        title: String,
        content: String,
        modifiedAtEpochSeconds: Long
    ): Boolean

    suspend fun updateFavorite(localId: String, favorite: Boolean): Boolean

    suspend fun updateCategory(localId: String, category: String): Boolean

    suspend fun retry(localId: String): Boolean

    suspend fun moveToTrash(accountId: String, localIds: List<String>)

    suspend fun remove(localId: String)
}

interface NoteBackend {
    val capabilities: BackendCapabilities

    suspend fun validateAccount(account: Account): String

    suspend fun pull(account: Account, checkpoint: PullCheckpoint): PullResult

    suspend fun get(account: Account, remoteId: Long): RemoteNote

    suspend fun create(account: Account, note: Note): RemoteNote

    suspend fun update(account: Account, note: Note): RemoteNote

    suspend fun delete(account: Account, remoteId: Long)
}

interface NoteArchiveBackend {
    suspend fun versions(account: Account, note: Note): List<RemoteNoteVersion>

    suspend fun trashedNotes(account: Account, categories: Set<String>): List<TrashedNote>

    suspend fun restoreTrashedNote(account: Account, note: TrashedNote)
}

interface NoteSettingsBackend {
    suspend fun settings(account: Account): NoteSettings

    suspend fun updateSettings(
        account: Account,
        notesPath: String? = null,
        fileSuffix: String? = null
    ): NoteSettings
}

data class RemoteNoteVersion(val timestamp: Long, val displayTimestamp: String, val content: String)

data class TrashedNote(
    val name: String,
    val fileName: String,
    val timestamp: Long,
    val displayTimestamp: String,
    val content: String,
    val remotePath: String
)

interface AccountRepository {
    fun observeAccounts(): Flow<List<Account>>

    suspend fun get(accountId: String): Account?

    suspend fun save(account: Account)

    suspend fun remove(accountId: String)

    suspend fun updateSyncError(accountId: String, message: String?)
}

interface PullStore {
    suspend fun applyPull(accountId: String, result: PullResult)

    suspend fun resetCollection(accountId: String)
}

interface PushStore {
    suspend fun applySuccess(localId: String, submittedRevision: Long, remote: RemoteNote)

    suspend fun recordFailure(
        localId: String,
        message: String,
        conflict: Boolean = false,
        terminal: Boolean = false
    )

    suspend fun resolveConflict(localId: String, remote: RemoteNote, localCopy: Note?): Boolean
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
