package org.qownnotes.mobile.core

interface NoteListEntry {
    val localId: String
    val accountId: String
    val title: String
    val category: String
    val modifiedAtEpochSeconds: Long
}

data class NoteListItem(
    override val localId: String,
    override val accountId: String,
    val remoteId: Long?,
    override val title: String,
    override val category: String,
    override val modifiedAtEpochSeconds: Long,
    val favorite: Boolean,
    val syncState: SyncState,
    val excerpt: String
) : NoteListEntry
