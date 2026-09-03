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
            // A response only describes the revision that was submitted. Adopting its name for a
            // newer revision would undo a rename the reader made while the push was in flight.
            val unchanged = current.localRevision == submittedRevision
            database.noteDao().upsert(
                current.copy(
                    remoteId = remote.id,
                    title = if (unchanged) title else current.title,
                    content = if (unchanged) content else current.content,
                    category = category,
                    modifiedAtEpochSeconds =
                    if (unchanged) modified else current.modifiedAtEpochSeconds,
                    remoteEtag = etag,
                    readOnly = remote.readOnly,
                    favorite = if (unchanged) remote.favorite else current.favorite,
                    syncState =
                    if (unchanged) SyncState.SYNCHRONIZED else SyncState.LOCALLY_MODIFIED,
                    lastSyncedTitle = title,
                    lastSyncedContent = content,
                    lastSyncedCategory = category,
                    lastSyncedFavorite = remote.favorite,
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
