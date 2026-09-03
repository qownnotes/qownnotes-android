package org.qownnotes.mobile.core

data class Note(
    val localId: String,
    val accountId: String,
    val remoteId: Long? = null,
    val title: String,
    val content: String,
    val category: String = "",
    val modifiedAtEpochSeconds: Long,
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
)

enum class SyncState {
    SYNCHRONIZED,
    LOCALLY_CREATED,
    LOCALLY_MODIFIED,
    PENDING_DELETION,
    SYNCHRONIZING,
    CONFLICT,
    FAILED
}
