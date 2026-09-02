package org.qownnotes.mobile

import android.app.Application
import androidx.room.Room
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.time.Clock
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.qownnotes.mobile.backend.nextcloud.NextcloudBackend
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteBackend
import org.qownnotes.mobile.core.NoteFactory
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.core.QOwnNotesNamingPolicy
import org.qownnotes.mobile.core.SyncState
import org.qownnotes.mobile.data.MIGRATION_1_2
import org.qownnotes.mobile.data.MIGRATION_2_3
import org.qownnotes.mobile.data.QOwnNotesDatabase
import org.qownnotes.mobile.data.RoomAccountRepository
import org.qownnotes.mobile.data.RoomNoteRepository
import org.qownnotes.mobile.data.RoomPullStore
import org.qownnotes.mobile.data.RoomPushStore
import org.qownnotes.mobile.markdown.MarkdownRenderer

open class QOwnNotesApplication : Application() {
    open fun createComponent() = ApplicationComponent(this)

    open fun createAccountImportGateway(): AccountImportGateway = NextcloudAccountImportGateway()

    val component by lazy(::createComponent)
    val accountImportGateway by lazy(::createAccountImportGateway)
}

sealed interface SyncUiState {
    data object Idle : SyncUiState

    data object Refreshing : SyncUiState

    data class Failed(val message: String) : SyncUiState

    data class AuthenticationRequired(val message: String) : SyncUiState

    data class AccountRemoved(val message: String) : SyncUiState
}

class ApplicationComponent(
    application: Application,
    database: QOwnNotesDatabase =
        Room.databaseBuilder(application, QOwnNotesDatabase::class.java, "qownnotes.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build(),
    private val backend: NoteBackend = NextcloudBackend(application),
    val settings: AppSettings = AppSettings(application)
) {
    val noteRepository = RoomNoteRepository(database.noteDao())
    val accountRepository = RoomAccountRepository(database.accountDao())
    val markdownRenderer = MarkdownRenderer(application)
    private val pullStore = RoomPullStore(database)
    private val pushStore = RoomPushStore(database)
    private val clock = Clock.systemDefaultZone()
    private val noteFactory =
        NoteFactory(QOwnNotesNamingPolicy(application.getString(R.string.note_label), clock), clock)
    private val mutableSyncStates = MutableStateFlow<Map<String, SyncUiState>>(emptyMap())
    val syncStates: StateFlow<Map<String, SyncUiState>> = mutableSyncStates.asStateFlow()
    private val mutableImportState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val importState: StateFlow<SyncUiState> = mutableImportState.asStateFlow()
    private val refreshMutexes = ConcurrentHashMap<String, Mutex>()
    private val editMutexes = ConcurrentHashMap<String, Mutex>()
    private val syncJobs = ConcurrentHashMap<String, Job>()
    private val editorDrafts = ConcurrentHashMap<String, String>()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun launchAccountImport(ssoAccount: SingleSignOnAccount, expectedAccountId: String? = null) {
        applicationScope.launch {
            try {
                importAccount(ssoAccount, expectedAccountId)
            } catch (error: CancellationException) {
                cancelAccountImport()
                throw error
            } catch (error: Exception) {
                reportImportError(error)
            }
        }
    }

    suspend fun importAccount(
        ssoAccount: SingleSignOnAccount,
        expectedAccountId: String? = null
    ): Account {
        mutableImportState.value = SyncUiState.Refreshing
        val id = ssoAccount.localAccountId()
        require(expectedAccountId == null || expectedAccountId == id) {
            "Select the same Nextcloud account to reconnect"
        }
        return accountMutex(id).withLock {
            importAccountLocked(id, ssoAccount, expectedAccountId != null)
        }
    }

    private suspend fun importAccountLocked(
        id: String,
        ssoAccount: SingleSignOnAccount,
        reconnecting: Boolean
    ): Account {
        val existing = accountRepository.get(id)
        if (existing != null) {
            require(existing.matches(ssoAccount)) {
                if (reconnecting) {
                    "Select the same Nextcloud account to reconnect"
                } else {
                    "A different Nextcloud account already uses this local identity"
                }
            }
        } else if (reconnecting) {
            error("The Nextcloud account is no longer configured in QOwnNotes Mobile")
        }
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
        val account = candidate.copy(apiVersion = backend.validateAccount(candidate))
        accountRepository.save(account)
        mutableImportState.value = SyncUiState.Idle
        updateSyncState(id, SyncUiState.Idle)
        if (existing != null) refreshLocked(id)
        return accountRepository.get(id) ?: account
    }

    suspend fun refresh(accountId: String) {
        accountMutex(accountId).withLock { refreshLocked(accountId) }
    }

    suspend fun removeLocalData(accountId: String) {
        accountMutex(accountId).withLock {
            accountRepository.remove(accountId)
            mutableSyncStates.update { it - accountId }
        }
    }

    suspend fun createNote(accountId: String): Note {
        val note = noteFactory.create(accountId)
        noteRepository.save(note)
        scheduleSync(accountId, 0)
        return note
    }

    suspend fun beginEditing(localId: String): Note? =
        editMutexes.getOrPut(localId, ::Mutex).withLock {
            noteRepository.beginEditing(localId)?.also { cacheDraft(localId, it.content) }
        }

    suspend fun saveDraft(localId: String, content: String): Boolean =
        editMutexes.getOrPut(localId, ::Mutex).withLock {
            val note = noteRepository.get(localId) ?: return@withLock false
            if (note.readOnly) return@withLock false
            if (note.content == content) {
                editorDrafts.remove(localId, content)
                if (note.syncState == SyncState.LOCALLY_MODIFIED ||
                    note.syncState == SyncState.LOCALLY_CREATED
                ) {
                    scheduleSync(note.accountId)
                }
                return@withLock true
            }
            val saved = noteRepository.updateDraft(localId, content, clock.instant().epochSecond)
            if (saved) {
                editorDrafts.remove(localId, content)
                scheduleSync(note.accountId)
            }
            saved
        }

    fun cacheDraft(localId: String, content: String) {
        editorDrafts[localId] = content
    }

    fun draft(localId: String, persistedContent: String): String =
        editorDrafts[localId] ?: persistedContent

    fun saveDraftInBackground(localId: String, content: String) {
        cacheDraft(localId, content)
        applicationScope.launch { saveDraft(localId, content) }
    }

    suspend fun retryNote(localId: String) {
        val note = noteRepository.get(localId) ?: return
        if (noteRepository.retry(localId)) scheduleSync(note.accountId, 0)
    }

    private fun scheduleSync(accountId: String, delayMillis: Long = 1_500) {
        lateinit var job: Job
        job = applicationScope.launch(start = CoroutineStart.LAZY) {
            delay(delayMillis)
            syncJobs.remove(accountId, job)
            refresh(accountId)
        }
        syncJobs.put(accountId, job)?.cancel()
        job.start()
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
            pushPending(account)
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

    private suspend fun pushPending(account: Account) {
        noteRepository.pending(account.id).forEach { note ->
            try {
                val remote =
                    if (note.remoteId == null) {
                        backend.create(account, note)
                    } else {
                        backend.update(account, note)
                    }
                pushStore.applySuccess(note.localId, note.localRevision, remote)
            } catch (error: BackendException.Conflict) {
                pushStore.recordFailure(note.localId, error.message.orEmpty(), conflict = true)
            } catch (error: BackendException.RemoteMissing) {
                pushStore.recordFailure(note.localId, error.message.orEmpty(), terminal = true)
            } catch (error: BackendException.Permission) {
                pushStore.recordFailure(note.localId, error.message.orEmpty(), terminal = true)
            } catch (error: BackendException.InsufficientStorage) {
                pushStore.recordFailure(note.localId, error.message.orEmpty())
            } catch (error: Exception) {
                val requestMayHaveCompleted =
                    error !is BackendException.Authentication &&
                        error !is BackendException.AuthorizationRequired &&
                        error !is BackendException.AccountRemoved
                pushStore.recordFailure(
                    note.localId,
                    error.message ?: "Synchronization failed",
                    terminal = note.remoteId == null && requestMayHaveCompleted
                )
                throw error
            }
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

internal fun SingleSignOnAccount.localAccountId(): String =
    UUID.nameUUIDFromBytes(name.toByteArray()).toString()

private fun Account.matches(ssoAccount: SingleSignOnAccount): Boolean =
    ssoAccountName == ssoAccount.name &&
        userId == ssoAccount.userId &&
        serverUrl.trimEnd('/') == ssoAccount.url.trimEnd('/')

private fun Exception.toSyncUiState(message: String): SyncUiState = when (this) {
    is BackendException.Authentication, is BackendException.AuthorizationRequired ->
        SyncUiState.AuthenticationRequired(message)
    is BackendException.AccountRemoved -> SyncUiState.AccountRemoved(message)
    else -> SyncUiState.Failed(message)
}
