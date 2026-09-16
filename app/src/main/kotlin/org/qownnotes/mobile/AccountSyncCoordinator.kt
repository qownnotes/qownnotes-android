package org.qownnotes.mobile

import java.util.concurrent.CancellationException
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.AccountRepository
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteBackend
import org.qownnotes.mobile.core.NoteRepository
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.core.PullStore
import org.qownnotes.mobile.core.PushStore
import org.qownnotes.mobile.core.SyncCoordinator
import org.qownnotes.mobile.core.SyncOutcome
import org.qownnotes.mobile.core.SyncState

internal class AccountSyncCoordinator(
    private val accountRepository: AccountRepository,
    private val noteRepository: NoteRepository,
    private val pullStore: PullStore,
    private val pushStore: PushStore,
    private val backend: NoteBackend,
    private val onNoteFailure: (String, Throwable) -> Unit,
    private val onNoteSuccess: (String) -> Unit
) : SyncCoordinator {
    override suspend fun synchronize(accountId: String): SyncOutcome {
        var account = accountRepository.get(accountId) ?: return SyncOutcome.Success
        return try {
            val apiVersion = account.apiVersion ?: backend.validateAccount(account)
            account = account.copy(apiVersion = apiVersion, lastSyncError = null)
            accountRepository.save(account)
            val result =
                backend.pull(
                    account,
                    PullCheckpoint(account.collectionEtag, account.lastModifiedEpochSeconds)
                )
            pullStore.applyPull(accountId, result)
            pushPendingDeletions(account)
            pushPending(account)?.also { outcome ->
                accountRepository.updateSyncError(
                    accountId,
                    outcome.error.message ?: "Synchronization needs attention"
                )
            } ?: SyncOutcome.Success
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            accountRepository.updateSyncError(accountId, error.message ?: "Synchronization failed")
            error.toSyncOutcome()
        }
    }

    private suspend fun pushPending(account: Account): SyncOutcome.UserActionRequired? {
        var issue: SyncOutcome.UserActionRequired? = null
        noteRepository.pending(account.id).forEach { note ->
            try {
                val remote =
                    if (note.remoteId == null) {
                        backend.create(account, note)
                    } else {
                        backend.update(account, note)
                    }
                pushStore.applySuccess(note.localId, note.localRevision, remote)
                onNoteSuccess(note.localId)
            } catch (error: BackendException.Conflict) {
                recordFailure(note, error, SyncState.CONFLICT)
                issue = issue ?: SyncOutcome.UserActionRequired(error)
            } catch (error: BackendException.RemoteMissing) {
                recordFailure(note, error, SyncState.REMOTE_MISSING)
                issue = issue ?: SyncOutcome.UserActionRequired(error)
            } catch (error: BackendException.Permission) {
                recordFailure(note, error, SyncState.FAILED)
                issue = issue ?: SyncOutcome.UserActionRequired(error)
            } catch (error: BackendException.InsufficientStorage) {
                recordFailure(note, error)
                issue = issue ?: SyncOutcome.UserActionRequired(error)
            } catch (error: Exception) {
                val requestMayHaveCompleted =
                    error !is BackendException.Authentication &&
                        error !is BackendException.AuthorizationRequired &&
                        error !is BackendException.AccountRemoved
                val uncertainCreate = note.remoteId == null && requestMayHaveCompleted
                recordFailure(note, error, if (uncertainCreate) SyncState.FAILED else null)
                if (uncertainCreate) {
                    issue = issue ?: SyncOutcome.UserActionRequired(error)
                } else {
                    throw error
                }
            }
        }
        return issue
    }

    private suspend fun recordFailure(
        note: Note,
        error: Throwable,
        failureState: SyncState? = null
    ) {
        onNoteFailure(note.localId, error)
        pushStore.recordFailure(
            note.localId,
            note.localRevision,
            error.message ?: "Synchronization failed",
            failureState
        )
    }

    private suspend fun pushPendingDeletions(account: Account) {
        noteRepository.pendingDeletions(account.id).forEach { note ->
            val remoteId = note.remoteId
            if (remoteId != null) backend.delete(account, remoteId)
            noteRepository.remove(note.localId)
        }
    }
}

internal fun Throwable.toSyncOutcome(): SyncOutcome = when (this) {
    is BackendException.Retryable -> SyncOutcome.RetryableFailure(this)
    is BackendException.Authentication,
    is BackendException.AuthorizationRequired,
    is BackendException.AccountRemoved,
    is BackendException.Permission,
    is BackendException.InsufficientStorage,
    is BackendException.Conflict,
    is BackendException.RemoteMissing -> SyncOutcome.UserActionRequired(this)
    else -> SyncOutcome.PermanentFailure(this)
}
