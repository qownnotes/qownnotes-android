package org.qownnotes.mobile

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qownnotes.mobile.core.BackendException

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
}
