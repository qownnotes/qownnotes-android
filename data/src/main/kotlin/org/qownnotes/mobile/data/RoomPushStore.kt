package org.qownnotes.mobile.data

import androidx.room.withTransaction
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
            val unchanged = current.localRevision == submittedRevision
            database.noteDao().upsert(
                current.copy(
                    remoteId = remote.id,
                    title = title,
                    content = if (unchanged) content else current.content,
                    category = category,
                    modifiedAtEpochSeconds =
                    if (unchanged) modified else current.modifiedAtEpochSeconds,
                    remoteEtag = etag,
                    readOnly = remote.readOnly,
                    syncState =
                    if (unchanged) SyncState.SYNCHRONIZED else SyncState.LOCALLY_MODIFIED,
                    lastSyncedTitle = title,
                    lastSyncedContent = content,
                    lastSyncedCategory = category,
                    lastSyncError = null
                )
            )
        }
    }

    override suspend fun recordFailure(
        localId: String,
        message: String,
        conflict: Boolean,
        terminal: Boolean
    ) {
        database.withTransaction {
            val current = database.noteDao().get(localId) ?: return@withTransaction
            database.noteDao().upsert(
                current.copy(
                    syncState = when {
                        conflict -> SyncState.CONFLICT
                        terminal -> SyncState.FAILED
                        else -> current.syncState
                    },
                    lastSyncError = message
                )
            )
        }
    }
}
