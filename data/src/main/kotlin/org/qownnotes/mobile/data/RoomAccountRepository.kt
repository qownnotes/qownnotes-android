package org.qownnotes.mobile.data

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.map
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.AccountRepository
import org.qownnotes.mobile.core.NoteCategories
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.PullStore
import org.qownnotes.mobile.core.SyncState

class RoomAccountRepository(private val accountDao: AccountDao) : AccountRepository {
    override fun observeAccounts() =
        accountDao.observeAll().map { accounts -> accounts.map(AccountEntity::toDomain) }

    override suspend fun get(accountId: String) = accountDao.get(accountId)?.toDomain()

    override suspend fun save(account: Account) = accountDao.upsert(account.toEntity())

    override suspend fun remove(accountId: String) = accountDao.delete(accountId)

    override suspend fun updateSyncError(accountId: String, message: String?) =
        accountDao.updateSyncError(accountId, message)
}

class RoomPullStore(private val database: QOwnNotesDatabase) : PullStore {
    override suspend fun applyPull(accountId: String, result: PullResult) {
        if (result.notModified) return

        database.withTransaction {
            val account = requireNotNull(database.accountDao().get(accountId)) {
                "Cannot apply a pull for an unknown account"
            }
            val dao = database.noteDao()
            val notes = result.notes.filterNot { remote ->
                remote.category?.let(NoteCategories::isInternal) == true
            }
            val remoteIds = notes.mapTo(mutableSetOf()) { it.id }
            notes.filterNot { it.isPruned }.forEach { remote ->
                val existing = dao.getByRemoteId(accountId, remote.id)
                if (existing == null) {
                    dao.upsert(
                        NoteEntity(
                            localId = UUID.randomUUID().toString(),
                            accountId = accountId,
                            remoteId = remote.id,
                            title = remote.title.orEmpty(),
                            content = remote.content.orEmpty(),
                            category = remote.category.orEmpty(),
                            modifiedAtEpochSeconds = remote.modifiedAtEpochSeconds ?: 0,
                            remoteEtag = remote.etag,
                            readOnly = remote.readOnly,
                            favorite = remote.favorite,
                            syncState = SyncState.SYNCHRONIZED,
                            lastSyncedTitle = remote.title.orEmpty(),
                            lastSyncedContent = remote.content.orEmpty(),
                            lastSyncedCategory = remote.category.orEmpty(),
                            lastSyncedFavorite = remote.favorite,
                            lastSyncError = null
                        )
                    )
                } else if (existing.syncState == SyncState.SYNCHRONIZED) {
                    dao.upsert(
                        existing.copy(
                            title = remote.title.orEmpty(),
                            content = remote.content.orEmpty(),
                            category = remote.category.orEmpty(),
                            modifiedAtEpochSeconds = remote.modifiedAtEpochSeconds ?: 0,
                            remoteEtag = remote.etag,
                            readOnly = remote.readOnly,
                            favorite = remote.favorite,
                            lastSyncedTitle = remote.title.orEmpty(),
                            lastSyncedContent = remote.content.orEmpty(),
                            lastSyncedCategory = remote.category.orEmpty(),
                            lastSyncedFavorite = remote.favorite,
                            lastSyncError = null
                        )
                    )
                    database.noteConflictDao().delete(existing.localId)
                } else if (
                    existing.syncState in setOf(SyncState.CONFLICT, SyncState.READ_ONLY_CONFLICT)
                ) {
                    val nextState = if (remote.readOnly) {
                        SyncState.READ_ONLY_CONFLICT
                    } else {
                        SyncState.CONFLICT
                    }
                    database.noteConflictDao().upsert(existing.toConflictEntity(remote))
                    dao.upsert(
                        existing.copy(
                            readOnly = remote.readOnly,
                            syncState = nextState,
                            lastSyncError = if (nextState == existing.syncState) {
                                existing.lastSyncError
                            } else {
                                if (remote.readOnly) {
                                    READ_ONLY_CONFLICT_MESSAGE
                                } else {
                                    REMOTE_CHANGED_MESSAGE
                                }
                            }
                        )
                    )
                } else if (
                    remote.readOnly &&
                    existing.syncState != SyncState.PENDING_DELETION &&
                    existing.hasProtectedLocalChanges()
                ) {
                    database.noteConflictDao().upsert(existing.toConflictEntity(remote))
                    dao.upsert(
                        existing.copy(
                            readOnly = true,
                            syncState = SyncState.READ_ONLY_CONFLICT,
                            lastSyncError = READ_ONLY_CONFLICT_MESSAGE
                        )
                    )
                } else if (
                    !remote.readOnly &&
                    existing.syncState == SyncState.REMOTE_MISSING
                ) {
                    database.noteConflictDao().upsert(existing.toConflictEntity(remote))
                    dao.upsert(
                        existing.copy(
                            readOnly = false,
                            syncState = SyncState.CONFLICT,
                            lastSyncError = REMOTE_CHANGED_MESSAGE
                        )
                    )
                } else if (existing.readOnly != remote.readOnly) {
                    dao.upsert(existing.copy(readOnly = remote.readOnly))
                }
            }

            dao.getRemoteNoteReferences(accountId).forEach { note ->
                val internal = NoteCategories.isInternal(note.category)
                when {
                    internal && note.syncState == SyncState.SYNCHRONIZED ->
                        dao.deleteByLocalId(note.localId)
                    internal || note.remoteId in remoteIds -> Unit
                    note.syncState == SyncState.SYNCHRONIZED -> dao.deleteByLocalId(note.localId)
                    note.syncState in setOf(
                        SyncState.PENDING_DELETION,
                        SyncState.LOCALLY_CREATED
                    ) -> Unit
                    else -> {
                        database.noteConflictDao().delete(note.localId)
                        dao.markServerIssue(
                            note.localId,
                            readOnly = false,
                            SyncState.REMOTE_MISSING,
                            REMOTE_MISSING_MESSAGE
                        )
                    }
                }
            }

            database.accountDao().upsert(
                account.copy(
                    collectionEtag = result.collectionEtag,
                    lastModifiedEpochSeconds = result.lastModifiedEpochSeconds,
                    lastSyncError = null
                )
            )
        }
    }

    override suspend fun resetCollection(accountId: String) {
        database.withTransaction {
            val account = requireNotNull(database.accountDao().get(accountId)) {
                "Cannot reset the collection for an unknown account"
            }
            database.noteDao().deleteSynchronized(accountId)
            database.accountDao().upsert(
                account.copy(
                    collectionEtag = null,
                    lastModifiedEpochSeconds = 0,
                    lastSyncError = null
                )
            )
        }
    }
}

private fun NoteEntity.hasProtectedLocalChanges(): Boolean =
    title != lastSyncedTitle || content != lastSyncedContent || category != lastSyncedCategory

private const val READ_ONLY_CONFLICT_MESSAGE =
    "The note became read-only while local changes were pending"
private const val REMOTE_MISSING_MESSAGE = "The note no longer exists on the server"
private const val REMOTE_CHANGED_MESSAGE = "The note changed on the server"
