package org.qownnotes.mobile.data

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.map
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.AccountRepository
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
            val remoteIds = result.notes.mapTo(mutableSetOf()) { it.id }
            result.notes.filterNot { it.isPruned }.forEach { remote ->
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
                            syncState = SyncState.SYNCHRONIZED,
                            lastSyncedTitle = remote.title.orEmpty(),
                            lastSyncedContent = remote.content.orEmpty(),
                            lastSyncedCategory = remote.category.orEmpty(),
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
                            lastSyncedTitle = remote.title.orEmpty(),
                            lastSyncedContent = remote.content.orEmpty(),
                            lastSyncedCategory = remote.category.orEmpty(),
                            lastSyncError = null
                        )
                    )
                }
            }

            dao.getRemoteNotes(accountId)
                .filter { it.syncState == SyncState.SYNCHRONIZED && it.remoteId !in remoteIds }
                .forEach { dao.delete(it) }

            database.accountDao().upsert(
                account.copy(
                    collectionEtag = result.collectionEtag,
                    lastModifiedEpochSeconds = result.lastModifiedEpochSeconds,
                    lastSyncError = null
                )
            )
        }
    }
}
