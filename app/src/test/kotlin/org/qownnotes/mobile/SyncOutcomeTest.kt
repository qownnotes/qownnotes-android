package org.qownnotes.mobile

import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.SyncOutcome

class SyncOutcomeTest {
    @Test
    fun retryableBackendFailuresRequestARetry() {
        assertTrue(
            BackendException.Retryable(IOException("offline")).toSyncOutcome() is
                SyncOutcome.RetryableFailure
        )
    }

    @Test
    fun authenticationPermissionAndStorageFailuresRequireUserAction() {
        listOf(
            BackendException.Authentication(),
            BackendException.AuthorizationRequired(),
            BackendException.AccountRemoved(),
            BackendException.Permission(),
            BackendException.InsufficientStorage()
        ).forEach { error ->
            assertTrue(error.toSyncOutcome() is SyncOutcome.UserActionRequired)
        }
    }

    @Test
    fun protocolFailuresDoNotRetryForever() {
        assertTrue(
            BackendException.Protocol("Malformed response").toSyncOutcome() is
                SyncOutcome.PermanentFailure
        )
    }
}
