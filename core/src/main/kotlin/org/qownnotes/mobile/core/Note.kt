package org.qownnotes.mobile.core

data class Note(
    override val localId: String,
    override val accountId: String,
    val remoteId: Long? = null,
    override val title: String,
    val content: String,
    override val category: String = "",
    override val modifiedAtEpochSeconds: Long,
    val remoteEtag: String? = null,
    val readOnly: Boolean = false,
    val favorite: Boolean = false,
    val syncState: SyncState,
    val lastSyncedTitle: String? = null,
    val lastSyncedContent: String? = null,
    val lastSyncedCategory: String? = null,
    val lastSyncedFavorite: Boolean? = null,
    val lastSyncError: String? = null,
    val localRevision: Long = 0
) : NoteListEntry

enum class SyncState {
    SYNCHRONIZED,
    LOCALLY_CREATED,
    LOCALLY_MODIFIED,
    PENDING_DELETION,
    SYNCHRONIZING,
    CONFLICT,
    FAILED
}
