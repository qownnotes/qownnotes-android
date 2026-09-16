package org.qownnotes.mobile

import android.app.Activity
import android.content.Intent
import androidx.room.Room
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendCapabilities
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteArchiveBackend
import org.qownnotes.mobile.core.NoteBackend
import org.qownnotes.mobile.core.NoteSettings
import org.qownnotes.mobile.core.NoteSettingsBackend
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.RemoteNoteVersion
import org.qownnotes.mobile.core.TrashedNote
import org.qownnotes.mobile.data.MIGRATION_1_2
import org.qownnotes.mobile.data.MIGRATION_2_3
import org.qownnotes.mobile.data.MIGRATION_3_4
import org.qownnotes.mobile.data.QOwnNotesDatabase

class TestQOwnNotesApplication : QOwnNotesApplication() {
    val fakeBackend = FakePullBackend()
    val fakeAccountImporter = FakeAccountImportGateway()
    val fakeSyncScheduler = FakeSyncScheduler()

    override fun createComponent(): ApplicationComponent {
        val database =
            Room.databaseBuilder(this, QOwnNotesDatabase::class.java, TEST_DATABASE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .allowMainThreadQueries()
                .build()
        // A dedicated preference file keeps device tests from reading or writing real user
        // settings, and lets `reset` restore defaults without touching the production store.
        val component = ApplicationComponent(
            this,
            database,
            fakeBackend,
            settings = AppSettings(this, TEST_SETTINGS),
            syncScheduler = fakeSyncScheduler,
            draftCheckpointIntervalMillis = 100,
            avatarFetcher = { null }
        )
        fakeSyncScheduler.bind(component::refresh)
        return component
    }

    override fun createAccountImportGateway(): AccountImportGateway = fakeAccountImporter

    suspend fun reset() {
        component.accountRepository.observeAccounts().first().forEach { account ->
            component.removeLocalData(account.id)
        }
        component.cancelAccountImport()
        // A share left waiting by a previous test would become a note in the next test's account.
        component.takePendingShare()
        component.settings.resetNoteTextSize()
        component.settings.setShowNotePreview(true)
        component.settings.setSwipeNoteActions(false)
        fakeBackend.reset()
        fakeAccountImporter.reset()
        fakeSyncScheduler.reset()
    }

    private companion object {
        const val TEST_DATABASE = "qownnotes-device-test.db"
        const val TEST_SETTINGS = "qownnotes-device-test-settings"
    }
}

class FakeSyncScheduler : SyncScheduler {
    val scheduled = mutableListOf<Pair<String, Long>>()
    val ensured = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()
    private var synchronize: suspend (String) -> Unit = {}
    private var executeScheduledWork = true

    fun bind(synchronize: suspend (String) -> Unit) {
        this.synchronize = synchronize
    }

    override fun schedule(accountId: String, delayMillis: Long) {
        scheduled += accountId to delayMillis
        if (!executeScheduledWork) return
        jobs.remove(accountId)?.cancel()
        jobs[accountId] = scope.launch {
            delay(delayMillis)
            jobs.remove(accountId)
            synchronize(accountId)
        }
    }

    override fun ensureScheduled(accountId: String) {
        ensured += accountId
        if (executeScheduledWork && jobs[accountId] == null) schedule(accountId, 0)
    }

    override fun cancel(accountId: String) {
        cancelled += accountId
        jobs.remove(accountId)?.cancel()
    }

    fun reset() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        scheduled.clear()
        ensured.clear()
        cancelled.clear()
        executeScheduledWork = true
    }

    fun pause() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        executeScheduledWork = false
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

class FakePullBackend :
    NoteBackend,
    NoteArchiveBackend,
    NoteSettingsBackend {
    override val capabilities =
        BackendCapabilities(categories = true, favorites = true, readOnlyNotes = true)
    private val pulls = mutableMapOf<String, ArrayDeque<Result<PullResult>>>()
    val checkpoints = mutableListOf<Pair<String, PullCheckpoint>>()
    val validatedAccountIds = mutableListOf<String>()
    val pushedNotes = mutableListOf<Note>()
    val deletedRemoteIds = mutableListOf<Long>()
    var noteVersions = emptyList<RemoteNoteVersion>()
    var trash = emptyList<TrashedNote>()
    val trashCategoryRequests = mutableListOf<Set<String>>()
    val restoredTrash = mutableListOf<TrashedNote>()
    val settingsByAccount = mutableMapOf<String, NoteSettings>()
    val settingsUpdates = mutableListOf<Pair<String, NoteSettings>>()
    var validationGate: CompletableDeferred<Unit>? = null
    var createFailure: Throwable? = null
    var getFailure: Throwable? = null
    var updateFailure: Throwable? = null
    var nextCanonicalTitle: String? = null
    val remoteNotes = mutableMapOf<Long, RemoteNote>()

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

    override suspend fun get(account: Account, remoteId: Long): RemoteNote {
        getFailure?.let {
            getFailure = null
            throw it
        }
        return remoteNotes[remoteId] ?: error("No remote note $remoteId was configured")
    }

    override suspend fun create(account: Account, note: Note): RemoteNote {
        createFailure?.let {
            createFailure = null
            throw it
        }
        pushedNotes += note
        return canonical(note, remoteId = nextRemoteId++)
    }

    override suspend fun update(account: Account, note: Note): RemoteNote {
        updateFailure?.let {
            updateFailure = null
            throw it
        }
        pushedNotes += note
        return canonical(note, remoteId = requireNotNull(note.remoteId))
    }

    override suspend fun delete(account: Account, remoteId: Long) {
        deletedRemoteIds += remoteId
    }

    override suspend fun settings(account: Account): NoteSettings =
        settingsByAccount.getOrPut(account.id) { NoteSettings("Notes", ".md") }

    override suspend fun updateSettings(
        account: Account,
        notesPath: String?,
        fileSuffix: String?
    ): NoteSettings {
        val current = settings(account)
        val updated = current.copy(
            notesPath = notesPath ?: current.notesPath,
            fileSuffix = fileSuffix ?: current.fileSuffix
        )
        settingsByAccount[account.id] = updated
        settingsUpdates += account.id to updated
        return updated
    }

    override suspend fun versions(account: Account, note: Note): List<RemoteNoteVersion> =
        noteVersions

    override suspend fun trashedNotes(
        account: Account,
        categories: Set<String>
    ): List<TrashedNote> {
        trashCategoryRequests += categories
        return trash
    }

    override suspend fun restoreTrashedNote(account: Account, note: TrashedNote) {
        restoredTrash += note
        trash = trash - note
    }

    fun enqueue(account: SingleSignOnAccount, result: PullResult) {
        queue(account).add(Result.success(result))
    }

    fun enqueue(accountId: String, result: PullResult) {
        pulls.getOrPut(accountId) { ArrayDeque() }.add(Result.success(result))
    }

    fun enqueueFailure(account: SingleSignOnAccount, error: Throwable) {
        queue(account).add(Result.failure(error))
    }

    fun enqueueFailure(accountId: String, error: Throwable) {
        pulls.getOrPut(accountId) { ArrayDeque() }.add(Result.failure(error))
    }

    fun reset() {
        pulls.clear()
        checkpoints.clear()
        validatedAccountIds.clear()
        pushedNotes.clear()
        deletedRemoteIds.clear()
        noteVersions = emptyList()
        trash = emptyList()
        trashCategoryRequests.clear()
        restoredTrash.clear()
        settingsByAccount.clear()
        settingsUpdates.clear()
        validationGate?.cancel()
        validationGate = null
        createFailure = null
        getFailure = null
        updateFailure = null
        nextCanonicalTitle = null
        remoteNotes.clear()
    }

    private fun queue(account: SingleSignOnAccount) =
        pulls.getOrPut(account.localAccountId()) { ArrayDeque() }

    private fun canonical(note: Note, remoteId: Long): RemoteNote {
        val title = nextCanonicalTitle ?: note.title
        nextCanonicalTitle = null
        return RemoteNote(
            id = remoteId,
            etag = "write-etag-${note.localRevision}",
            title = title,
            content = note.content,
            category = note.category,
            modifiedAtEpochSeconds = note.modifiedAtEpochSeconds,
            readOnly = false,
            favorite = note.favorite
        )
    }

    private var nextRemoteId = 1_000L
}
