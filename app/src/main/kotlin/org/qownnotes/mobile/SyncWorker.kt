package org.qownnotes.mobile

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.qownnotes.mobile.core.SyncOutcome

interface SyncScheduler {
    fun schedule(accountId: String, delayMillis: Long = 0)

    fun ensureScheduled(accountId: String)

    fun cancel(accountId: String)
}

class WorkManagerSyncScheduler(context: Context) : SyncScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun schedule(accountId: String, delayMillis: Long) {
        workManager.enqueueUniqueWork(
            triggerWorkName(accountId),
            ExistingWorkPolicy.REPLACE,
            triggerRequest(accountId, delayMillis)
        )
    }

    override fun ensureScheduled(accountId: String) {
        enqueueSync(workManager, accountId, ExistingWorkPolicy.KEEP)
    }

    override fun cancel(accountId: String) {
        workManager.cancelUniqueWork(triggerWorkName(accountId))
        workManager.cancelUniqueWork(syncWorkName(accountId))
    }

    companion object {
        internal const val ACCOUNT_ID = "account_id"
        private const val SYNC_WORK_NAME_PREFIX = "note-sync:"
        private const val TRIGGER_WORK_NAME_PREFIX = "note-sync-trigger:"

        internal fun syncWorkName(accountId: String) = SYNC_WORK_NAME_PREFIX + accountId

        internal fun triggerWorkName(accountId: String) = TRIGGER_WORK_NAME_PREFIX + accountId

        internal fun triggerRequest(accountId: String, delayMillis: Long) =
            OneTimeWorkRequestBuilder<SyncEnqueueWorker>()
                .setInputData(Data.Builder().putString(ACCOUNT_ID, accountId).build())
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

        internal fun syncRequest(accountId: String) = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(Data.Builder().putString(ACCOUNT_ID, accountId).build())
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        internal fun enqueueSync(
            workManager: WorkManager,
            accountId: String,
            policy: ExistingWorkPolicy
        ) {
            workManager.enqueueUniqueWork(syncWorkName(accountId), policy, syncRequest(accountId))
        }
    }
}

class SyncEnqueueWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString(WorkManagerSyncScheduler.ACCOUNT_ID)
            ?: return Result.failure()
        WorkManagerSyncScheduler.enqueueSync(
            WorkManager.getInstance(applicationContext),
            accountId,
            ExistingWorkPolicy.APPEND_OR_REPLACE
        )
        return Result.success()
    }
}

class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString(WorkManagerSyncScheduler.ACCOUNT_ID)
            ?: return Result.failure()
        val application = applicationContext as? QOwnNotesApplication ?: return Result.failure()
        return when (application.component.synchronizeFromWorker(accountId)) {
            is SyncOutcome.RetryableFailure -> Result.retry()
            SyncOutcome.Success,
            is SyncOutcome.UserActionRequired,
            is SyncOutcome.PermanentFailure -> Result.success()
        }
    }
}
