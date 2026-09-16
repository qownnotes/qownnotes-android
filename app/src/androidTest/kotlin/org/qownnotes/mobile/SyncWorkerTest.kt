package org.qownnotes.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.SyncState

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {
    private lateinit var application: TestQOwnNotesApplication

    @Before
    fun setUp() = runBlocking {
        application =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as
                TestQOwnNotesApplication
        application.reset()
        application.component.accountRepository.save(account())
    }

    @After
    fun tearDown() = runBlocking {
        application.reset()
    }

    @Test
    fun retryableFailureRequestsWorkManagerRetry() = runBlocking {
        application.fakeBackend.enqueueFailure(
            ACCOUNT_ID,
            BackendException.Retryable(IOException("offline"))
        )

        assertSameResult(ListenableWorker.Result.retry(), worker().doWork())
    }

    @Test
    fun userActionFailureDoesNotRetryAutomatically() = runBlocking {
        application.fakeBackend.enqueueFailure(ACCOUNT_ID, BackendException.Authentication())

        assertSameResult(ListenableWorker.Result.success(), worker().doWork())
    }

    @Test
    fun uncertainCreateFailureDoesNotRetryOrLoseTheLocalNote() = runBlocking {
        application.fakeBackend.enqueue(
            ACCOUNT_ID,
            PullResult(emptyList(), null, 0, notModified = true)
        )
        application.component.noteRepository.save(
            Note(
                localId = "local-note",
                accountId = ACCOUNT_ID,
                remoteId = null,
                title = "Local",
                content = "Local content",
                category = "",
                modifiedAtEpochSeconds = 1,
                syncState = SyncState.LOCALLY_CREATED
            )
        )
        application.fakeBackend.createFailure =
            BackendException.Retryable(IOException("response lost"))

        assertSameResult(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(
            SyncState.FAILED,
            application.component.noteRepository.get("local-note")?.syncState
        )
        assertNotNull(application.component.noteRepository.get("local-note"))
    }

    @Test
    fun scheduledWorkRequiresConnectivityAndCarriesItsAccount() {
        val request = WorkManagerSyncScheduler.syncRequest(ACCOUNT_ID)
        val trigger = WorkManagerSyncScheduler.triggerRequest(ACCOUNT_ID, 1_500)

        assertEquals(
            ACCOUNT_ID,
            request.workSpec.input.getString(WorkManagerSyncScheduler.ACCOUNT_ID)
        )
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(1_500L, trigger.workSpec.initialDelay)
    }

    @Test
    fun deletionIntentIsPersistedBeforeSynchronizationIsScheduled() = runBlocking {
        application.fakeSyncScheduler.pause()
        application.component.noteRepository.save(
            Note(
                localId = "delete-me",
                accountId = ACCOUNT_ID,
                remoteId = 42,
                title = "Delete me",
                content = "Content",
                category = "",
                modifiedAtEpochSeconds = 1,
                remoteEtag = "etag",
                syncState = SyncState.SYNCHRONIZED,
                lastSyncedTitle = "Delete me",
                lastSyncedContent = "Content",
                lastSyncedCategory = "",
                lastSyncedFavorite = false
            )
        )

        application.component.moveNotesToTrash(ACCOUNT_ID, listOf("delete-me"))

        assertEquals(
            SyncState.PENDING_DELETION,
            application.component.noteRepository.get("delete-me")?.syncState
        )
        assertEquals(ACCOUNT_ID to 0L, application.fakeSyncScheduler.scheduled.last())
    }

    private fun worker(): SyncWorker = TestListenableWorkerBuilder<SyncWorker>(application)
        .setInputData(
            Data.Builder().putString(WorkManagerSyncScheduler.ACCOUNT_ID, ACCOUNT_ID).build()
        )
        .build()

    private fun assertSameResult(
        expected: ListenableWorker.Result,
        actual: ListenableWorker.Result
    ) {
        assertEquals(expected.toString(), actual.toString())
    }

    private fun account() = Account(
        id = ACCOUNT_ID,
        displayName = "Account",
        serverUrl = "https://cloud.example",
        ssoAccountName = "sso",
        userId = "user",
        apiVersion = "1.4"
    )

    private companion object {
        const val ACCOUNT_ID = "worker-account"
    }
}
