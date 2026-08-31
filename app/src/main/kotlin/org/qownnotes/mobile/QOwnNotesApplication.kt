package org.qownnotes.mobile

import android.app.Application
import androidx.room.Room
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.qownnotes.mobile.backend.nextcloud.NextcloudBackend
import org.qownnotes.mobile.core.Account
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
    private val mutableSyncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = mutableSyncState.asStateFlow()

    suspend fun importAccount(ssoAccount: SingleSignOnAccount): Account {
        val account =
            Account(
                id = UUID.nameUUIDFromBytes(ssoAccount.name.toByteArray()).toString(),
                displayName = "${ssoAccount.userId} @ ${ssoAccount.url.removePrefix(
                    "https://"
                ).trimEnd('/')}",
                serverUrl = ssoAccount.url,
                ssoAccountName = ssoAccount.name,
                userId = ssoAccount.userId
            )
        accountRepository.save(account)
        refresh(account.id)
        return account
    }

    suspend fun refresh(accountId: String) {
        var account = accountRepository.get(accountId) ?: return
        mutableSyncState.value = SyncUiState.Refreshing
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
            mutableSyncState.value = SyncUiState.Idle
        } catch (error: Exception) {
            val message = error.message ?: "Synchronization failed"
            accountRepository.save(account.copy(lastSyncError = message))
            mutableSyncState.value = SyncUiState.Failed(message)
        }
    }

    fun reportImportError(error: Throwable) {
        mutableSyncState.value = SyncUiState.Failed(error.message ?: "Account import failed")
    }
}
