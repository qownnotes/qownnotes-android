package org.qownnotes.mobile.backend.nextcloud

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.android.sso.exceptions.NextcloudApiNotRespondingException
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountPermissionNotGrantedException
import com.nextcloud.android.sso.exceptions.NextcloudNetworkException
import com.nextcloud.android.sso.exceptions.TokenMismatchException
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.BackendException

@RunWith(AndroidJUnit4::class)
class BackendExceptionClassificationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun tokenMismatchRequiresAuthentication() {
        assertTrue(
            TokenMismatchException(context).toBackendException() is BackendException.Authentication
        )
    }

    @Test
    fun revokedFilesPermissionRequiresAuthorization() {
        assertTrue(
            NextcloudFilesAppAccountPermissionNotGrantedException(context).toBackendException() is
                BackendException.AuthorizationRequired
        )
    }

    @Test
    fun missingFilesAccountIsNotRetryable() {
        assertTrue(
            NextcloudFilesAppAccountNotFoundException(context).toBackendException() is
                BackendException.AccountRemoved
        )
    }

    @Test
    fun serviceAndNetworkFailuresRemainRetryable() {
        assertTrue(
            NextcloudApiNotRespondingException(context).toBackendException() is
                BackendException.Retryable
        )
        assertTrue(
            NextcloudNetworkException(IllegalStateException()).toBackendException() is
                BackendException.Retryable
        )
    }

    @Test
    fun unexpectedFailuresAreNotReportedAsNetworkFailures() {
        val exception = IllegalStateException("SSO setup failed").toBackendException()

        assertTrue(exception is BackendException.Protocol)
        assertTrue(exception.message == "SSO setup failed")
    }
}
