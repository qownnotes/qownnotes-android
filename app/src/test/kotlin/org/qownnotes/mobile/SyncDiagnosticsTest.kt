package org.qownnotes.mobile

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.SyncDiagnostic
import org.qownnotes.mobile.core.SyncDiagnosticSource

class SyncDiagnosticsTest {
    @Test
    fun diagnosticKeepsTheCauseChainAndRedactsSecrets() {
        val error = BackendException.Retryable(
            IOException(
                "connection refused access_token=token-value " +
                    "Authorization: Bearer bearer-value password=hunter2"
            )
        )

        val diagnostic = error.toSyncDiagnosticText()

        assertTrue(diagnostic.contains("BackendException\$Retryable"))
        assertTrue(diagnostic.contains("Caused by: java.io.IOException: connection refused"))
        assertTrue(diagnostic.contains("access_token=<redacted>"))
        assertTrue(diagnostic.contains("Authorization: <redacted>"))
        assertTrue(diagnostic.contains("password=<redacted>"))
        assertFalse(diagnostic.contains("token-value"))
        assertFalse(diagnostic.contains("bearer-value"))
        assertFalse(diagnostic.contains("hunter2"))
    }

    @Test
    fun reportContainsEnvironmentAndDiagnosticsWithoutInternalAccountIdentity() {
        val report = buildSyncDiagnosticReport(
            appVersion = "1.2.3",
            commit = "1234567890",
            androidVersion = "16",
            androidApi = 36,
            device = "Example Phone",
            notesApiVersions = listOf("1.3"),
            diagnostics = listOf(
                SyncDiagnostic(
                    accountId = "private-account-id",
                    occurredAtEpochSeconds = 1,
                    source = SyncDiagnosticSource.NOTE,
                    category = "Connectivity",
                    details = "java.io.IOException"
                )
            )
        )

        assertTrue(report.contains("App: 1.2.3 (1234567)"))
        assertTrue(report.contains("Android: 16 (API 36)"))
        assertTrue(report.contains("Notes API: 1.3"))
        assertTrue(report.contains("Scope: note"))
        assertTrue(report.contains("java.io.IOException"))
        assertFalse(report.contains("private-account-id"))
    }

    @Test
    fun durableDiagnosticStoresExceptionTypesWithoutMessages() {
        val diagnostic = BackendException.Retryable(
            IOException("alice https://private.example note contents")
        ).toSyncDiagnosticTypeChain()

        assertFalse(diagnostic.contains("alice"))
        assertFalse(diagnostic.contains("private.example"))
        assertFalse(diagnostic.contains("note contents"))
        assertTrue(diagnostic.contains("BackendException\$Retryable"))
        assertTrue(diagnostic.contains("java.io.IOException"))
    }
}
