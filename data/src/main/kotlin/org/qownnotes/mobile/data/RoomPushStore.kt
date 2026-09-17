package org.qownnotes.mobile.data

import androidx.room.withTransaction
import org.qownnotes.mobile.core.MergedNoteFields
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteConflict
import org.qownnotes.mobile.core.PushStore
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.SyncState

class RoomPushStore(private val database: QOwnNotesDatabase) : PushStore {
    override suspend fun applySuccess(
        localId: String,
        submittedRevision: Long,
        remote: RemoteNote
    ) {
        database.withTransaction {
            val current = database.noteDao().get(localId) ?: return@withTransaction
            val title = requireNotNull(remote.title) { "Nextcloud response is missing its title" }
            val content =
                requireNotNull(remote.content) { "Nextcloud response is missing its content" }
            val category =
                requireNotNull(remote.category) { "Nextcloud response is missing its category" }
            val modified = requireNotNull(remote.modifiedAtEpochSeconds) {
                "Nextcloud response is missing its modified timestamp"
            }
            val etag = requireNotNull(remote.etag) { "Nextcloud response is missing its etag" }
            // A response only describes the revision that was submitted. Adopting its name for a
            // newer revision would undo a rename the reader made while the push was in flight.
            val unchanged = current.localRevision == submittedRevision
            database.noteDao().upsert(
                current.copy(
                    remoteId = remote.id,
                    title = if (unchanged) title else current.title,
                    content = if (unchanged) content else current.content,
                    category = if (unchanged) category else current.category,
                    modifiedAtEpochSeconds =
                    if (unchanged) modified else current.modifiedAtEpochSeconds,
                    remoteEtag = etag,
                    readOnly = remote.readOnly,
                    favorite = if (unchanged) remote.favorite else current.favorite,
                    syncState = when {
                        unchanged -> SyncState.SYNCHRONIZED
                        current.syncState == SyncState.PENDING_DELETION ->
                            SyncState.PENDING_DELETION
                        else -> SyncState.LOCALLY_MODIFIED
                    },
                    lastSyncedTitle = title,
                    lastSyncedContent = content,
                    lastSyncedCategory = category,
                    lastSyncedFavorite = remote.favorite,
                    lastSyncError = null
                )
            )
            if (unchanged) database.noteConflictDao().delete(localId)
        }
    }

    override suspend fun recordFailure(
        localId: String,
        submittedRevision: Long,
        message: String,
        failureState: SyncState?
    ) {
        database.withTransaction {
            val current = database.noteDao().get(localId) ?: return@withTransaction
            if (current.localRevision != submittedRevision) return@withTransaction
            database.noteDao().upsert(
                current.copy(
                    syncState = failureState ?: current.syncState,
                    lastSyncError = message
                )
            )
        }
    }

    override suspend fun conflict(localId: String): NoteConflict? = database.withTransaction {
        val note = database.noteDao().get(localId) ?: return@withTransaction null
        database.noteConflictDao().get(localId)?.toDomain(note)
    }

    override suspend fun captureConflict(
        localId: String,
        expectedRevision: Long,
        remote: RemoteNote
    ): Boolean = database.withTransaction {
        val current = database.noteDao().get(localId) ?: return@withTransaction false
        if (
            current.syncState !in setOf(SyncState.CONFLICT, SyncState.READ_ONLY_CONFLICT) ||
            current.localRevision != expectedRevision ||
            current.remoteId != remote.id
        ) {
            return@withTransaction false
        }
        database.noteConflictDao().upsert(current.toConflictEntity(remote))
        val nextState = if (remote.readOnly) {
            SyncState.READ_ONLY_CONFLICT
        } else {
            SyncState.CONFLICT
        }
        if (current.syncState != nextState || current.readOnly != remote.readOnly) {
            database.noteDao().upsert(
                current.copy(
                    readOnly = remote.readOnly,
                    syncState = nextState,
                    lastSyncError = if (remote.readOnly) {
                        "The note became read-only while local changes were pending"
                    } else {
                        "The note changed on the server"
                    }
                )
            )
        }
        true
    }

    override suspend fun resolveConflict(
        localId: String,
        expectedRevision: Long,
        expectedRemoteEtag: String,
        resolvedAtEpochSeconds: Long,
        merged: MergedNoteFields?,
        localCopy: Note?
    ): Boolean = database.withTransaction {
        val current = database.noteDao().get(localId) ?: return@withTransaction false
        val conflict = database.noteConflictDao().get(localId) ?: return@withTransaction false
        if (
            current.syncState !in setOf(SyncState.CONFLICT, SyncState.READ_ONLY_CONFLICT) ||
            current.localRevision != expectedRevision ||
            current.remoteId != conflict.remoteId ||
            conflict.remoteEtag != expectedRemoteEtag
        ) {
            return@withTransaction false
        }
        if (conflict.remoteReadOnly && merged != null && localCopy == null) {
            return@withTransaction false
        }
        localCopy?.let { database.noteDao().upsert(it.toEntity()) }
        val writableMerge = merged.takeUnless { conflict.remoteReadOnly }
        val mergedDiffersFromRemote = writableMerge != null && (
            writableMerge.title != conflict.remoteTitle ||
                writableMerge.content != conflict.remoteContent ||
                writableMerge.category != conflict.remoteCategory ||
                writableMerge.favorite != conflict.remoteFavorite
            )
        database.noteDao().upsert(
            current.copy(
                title = writableMerge?.title ?: conflict.remoteTitle,
                content = writableMerge?.content ?: conflict.remoteContent,
                category = writableMerge?.category ?: conflict.remoteCategory,
                modifiedAtEpochSeconds = if (mergedDiffersFromRemote) {
                    maxOf(
                        current.modifiedAtEpochSeconds,
                        conflict.remoteModifiedAtEpochSeconds,
                        resolvedAtEpochSeconds
                    )
                } else {
                    conflict.remoteModifiedAtEpochSeconds
                },
                remoteEtag = conflict.remoteEtag,
                readOnly = conflict.remoteReadOnly,
                favorite = writableMerge?.favorite ?: conflict.remoteFavorite,
                syncState = if (mergedDiffersFromRemote) {
                    SyncState.LOCALLY_MODIFIED
                } else {
                    SyncState.SYNCHRONIZED
                },
                lastSyncedTitle = conflict.remoteTitle,
                lastSyncedContent = conflict.remoteContent,
                lastSyncedCategory = conflict.remoteCategory,
                lastSyncedFavorite = conflict.remoteFavorite,
                lastSyncError = null,
                localRevision = current.localRevision + 1
            )
        )
        database.noteConflictDao().delete(localId)
        true
    }

    override suspend fun resolveRemoteMissing(
        localId: String,
        expectedRevision: Long,
        recreate: Boolean
    ): Boolean = database.withTransaction {
        val current = database.noteDao().get(localId) ?: return@withTransaction false
        if (
            current.syncState != SyncState.REMOTE_MISSING ||
            current.localRevision != expectedRevision
        ) {
            return@withTransaction false
        }
        if (recreate) {
            database.noteDao().upsert(
                current.copy(
                    remoteId = null,
                    remoteEtag = null,
                    readOnly = false,
                    syncState = SyncState.LOCALLY_CREATED,
                    lastSyncedTitle = null,
                    lastSyncedContent = null,
                    lastSyncedCategory = null,
                    lastSyncedFavorite = null,
                    lastSyncError = null,
                    localRevision = current.localRevision + 1
                )
            )
            database.noteConflictDao().delete(localId)
        } else {
            database.noteDao().deleteByLocalId(localId)
        }
        true
    }
}

internal fun NoteEntity.toConflictEntity(remote: RemoteNote): NoteConflictEntity =
    NoteConflictEntity(
        localId = localId,
        remoteId = remote.id,
        remoteTitle = requireNotNull(remote.title) { "Nextcloud response is missing its title" },
        remoteContent = requireNotNull(remote.content) {
            "Nextcloud response is missing its content"
        },
        remoteCategory = requireNotNull(remote.category) {
            "Nextcloud response is missing its category"
        },
        remoteModifiedAtEpochSeconds = requireNotNull(remote.modifiedAtEpochSeconds) {
            "Nextcloud response is missing its modified timestamp"
        },
        remoteEtag = requireNotNull(remote.etag) { "Nextcloud response is missing its etag" },
        remoteReadOnly = remote.readOnly,
        remoteFavorite = remote.favorite
    )
