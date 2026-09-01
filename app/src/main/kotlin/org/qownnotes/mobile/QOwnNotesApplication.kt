package org.qownnotes.mobile

import android.app.Application
import androidx.room.Room
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.qownnotes.mobile.backend.nextcloud.NextcloudBackend
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.data.MIGRATION_1_2
import org.qownnotes.mobile.data.QOwnNotesDatabase
import org.qownnotes.mobile.data.RoomAccountRepository
import org.qownnotes.mobile.data.RoomNoteRepository
import org.qownnotes.mobile.data.RoomPullStore
import org.qownnotes.mobile.markdown.MarkdownRenderer

class QOwnNotesApplication : Application() {
    val component by lazy { ApplicationComponent(this) }
}

sealed interface SyncUiState {
    data object Idle : SyncUiState

    data object Refreshing : SyncUiState

    data class Failed(val message: String) : SyncUiState

    data class AuthenticationRequired(val message: String) : SyncUiState

    data class AccountRemoved(val message: String) : SyncUiState
}

class ApplicationComponent(application: Application) {
    private val database =
        Room.databaseBuilder(application, QOwnNotesDatabase::class.java, "qownnotes.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    val noteRepository = RoomNoteRepository(database.noteDao())
    val accountRepository = RoomAccountRepository(database.accountDao())
    val markdownRenderer = MarkdownRenderer(application)
    private val pullStore = RoomPullStore(database)
    private val backend = NextcloudBackend(application)
    private val mutableSyncStates = MutableStateFlow<Map<String, SyncUiState>>(emptyMap())
    val syncStates: StateFlow<Map<String, SyncUiState>> = mutableSyncStates.asStateFlow()
    private val mutableImportState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val importState: StateFlow<SyncUiState> = mutableImportState.asStateFlow()
    private val refreshMutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun importAccount(ssoAccount: SingleSignOnAccount): Account {
        mutableImportState.value = SyncUiState.Refreshing
        val id = UUID.nameUUIDFromBytes(ssoAccount.name.toByteArray()).toString()
        return accountMutex(id).withLock {
            importAccountLocked(id, ssoAccount)
        }
    }

    private suspend fun importAccountLocked(id: String, ssoAccount: SingleSignOnAccount): Account {
        val existing = accountRepository.get(id)
        val candidate =
            existing?.copy(
                displayName = ssoAccount.displayName(),
                serverUrl = ssoAccount.url,
                ssoAccountName = ssoAccount.name,
                userId = ssoAccount.userId,
                lastSyncError = null
            ) ?: Account(
                id = id,
                displayName = ssoAccount.displayName(),
                serverUrl = ssoAccount.url,
                ssoAccountName = ssoAccount.name,
                userId = ssoAccount.userId
            )
        val account = if (existing == null) {
            candidate.copy(apiVersion = backend.validateAccount(candidate))
        } else {
            candidate
        }
        accountRepository.save(account)
        mutableImportState.value = SyncUiState.Idle
        updateSyncState(id, SyncUiState.Idle)
        if (existing != null) refreshLocked(id)
        return accountRepository.get(id) ?: account
    }

    suspend fun refresh(accountId: String) {
        accountMutex(accountId).withLock { refreshLocked(accountId) }
    }

    private suspend fun refreshLocked(accountId: String) {
        var account = accountRepository.get(accountId) ?: return
        updateSyncState(accountId, SyncUiState.Refreshing)
        try {
            val apiVersion = account.apiVersion ?: backend.validateAccount(account)
            account = account.copy(apiVersion = apiVersion, lastSyncError = null)
            accountRepository.save(account)
            val result =
                backend.pull(
                    account,
                    PullCheckpoint(account.collectionEtag, account.lastModifiedEpochSeconds)
                )
            pullStore.applyPull(accountId, result)
            updateSyncState(accountId, SyncUiState.Idle)
        } catch (error: CancellationException) {
            updateSyncState(accountId, SyncUiState.Idle)
            throw error
        } catch (error: Exception) {
            val message = error.message ?: "Synchronization failed"
            accountRepository.updateSyncError(accountId, message)
            updateSyncState(accountId, error.toSyncUiState(message))
        }
    }

    fun beginAccountImport() {
        mutableImportState.value = SyncUiState.Refreshing
    }

    fun reportImportError(error: Throwable) {
        mutableImportState.value = SyncUiState.Failed(error.message ?: "Account import failed")
    }

    fun cancelAccountImport() {
        mutableImportState.value = SyncUiState.Idle
    }

    private fun updateSyncState(accountId: String, state: SyncUiState) {
        mutableSyncStates.update { it + (accountId to state) }
    }

    private fun accountMutex(accountId: String): Mutex = refreshMutexes.getOrPut(accountId, ::Mutex)
}

private fun SingleSignOnAccount.displayName(): String =
    "$userId @ ${url.removePrefix("https://").trimEnd('/')}"

private fun Exception.toSyncUiState(message: String): SyncUiState = when (this) {
    is BackendException.Authentication, is BackendException.AuthorizationRequired ->
        SyncUiState.AuthenticationRequired(message)
    is BackendException.AccountRemoved -> SyncUiState.AccountRemoved(message)
    else -> SyncUiState.Failed(message)
}
