package org.qownnotes.mobile

import android.app.Activity
import android.content.Intent
import androidx.room.Room
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendCapabilities
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteBackend
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.data.MIGRATION_1_2
import org.qownnotes.mobile.data.MIGRATION_2_3
import org.qownnotes.mobile.data.QOwnNotesDatabase

class TestQOwnNotesApplication : QOwnNotesApplication() {
    val fakeBackend = FakePullBackend()
    val fakeAccountImporter = FakeAccountImportGateway()

    override fun createComponent(): ApplicationComponent {
        val database =
            Room.databaseBuilder(this, QOwnNotesDatabase::class.java, TEST_DATABASE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
        // A dedicated preference file keeps device tests from reading or writing real user
        // settings, and lets `reset` restore defaults without touching the production store.
        return ApplicationComponent(this, database, fakeBackend, AppSettings(this, TEST_SETTINGS))
    }

    override fun createAccountImportGateway(): AccountImportGateway = fakeAccountImporter

    suspend fun reset() {
        component.accountRepository.observeAccounts().first().forEach { account ->
            component.removeLocalData(account.id)
        }
        component.cancelAccountImport()
        component.settings.resetNoteTextSize()
        fakeBackend.reset()
        fakeAccountImporter.reset()
    }

    private companion object {
        const val TEST_DATABASE = "qownnotes-device-test.db"
        const val TEST_SETTINGS = "qownnotes-device-test-settings"
    }
}

class FakeAccountImportGateway : AccountImportGateway {
    private val results = ArrayDeque<Result<SingleSignOnAccount>>()

    fun enqueue(account: SingleSignOnAccount) {
        results.add(Result.success(account))
    }

    fun enqueueFailure(error: Throwable) {
        results.add(Result.failure(error))
    }

    fun reset() = results.clear()

    override fun begin(activity: Activity, onAccount: (SingleSignOnAccount) -> Unit) {
        check(results.isNotEmpty()) { "No account import result was queued" }
        results.removeFirst().fold(onAccount) { throw it }
    }

    override fun handleActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        onAccount: (SingleSignOnAccount) -> Unit
    ) = Unit
}

class FakePullBackend : NoteBackend {
    override val capabilities = BackendCapabilities(categories = true, readOnlyNotes = true)
    private val pulls = mutableMapOf<String, ArrayDeque<Result<PullResult>>>()
    val checkpoints = mutableListOf<Pair<String, PullCheckpoint>>()
    val validatedAccountIds = mutableListOf<String>()
    var validationGate: CompletableDeferred<Unit>? = null

    override suspend fun validateAccount(account: Account): String {
        validatedAccountIds += account.id
        validationGate?.await()
        return "1.4"
    }

    override suspend fun pull(account: Account, checkpoint: PullCheckpoint): PullResult {
        checkpoints += account.id to checkpoint
        val result = pulls[account.id]?.pollFirst()
        return result?.getOrThrow()
            ?: PullResult(
                emptyList(),
                checkpoint.collectionEtag,
                checkpoint.lastModifiedEpochSeconds,
                notModified = true
            )
    }

    override suspend fun create(account: Account, note: Note): RemoteNote =
        canonical(note, remoteId = nextRemoteId++)

    override suspend fun update(account: Account, note: Note): RemoteNote =
        canonical(note, remoteId = requireNotNull(note.remoteId))

    fun enqueue(account: SingleSignOnAccount, result: PullResult) {
        queue(account).add(Result.success(result))
    }

    fun enqueueFailure(account: SingleSignOnAccount, error: Throwable) {
        queue(account).add(Result.failure(error))
    }

    fun reset() {
        pulls.clear()
        checkpoints.clear()
        validatedAccountIds.clear()
        validationGate?.cancel()
        validationGate = null
    }

    private fun queue(account: SingleSignOnAccount) =
        pulls.getOrPut(account.localAccountId()) { ArrayDeque() }

    private fun canonical(note: Note, remoteId: Long) = RemoteNote(
        id = remoteId,
        etag = "write-etag-${note.localRevision}",
        title = note.title,
        content = note.content,
        category = note.category,
        modifiedAtEpochSeconds = note.modifiedAtEpochSeconds,
        readOnly = false
    )

    private var nextRemoteId = 1_000L
}
