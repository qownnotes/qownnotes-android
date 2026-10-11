package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiDiscoveryTest {
    private val account =
        Account(
            "id",
            "Account",
            "https://cloud.test",
            "sso",
            "user",
            collectionEtag = "old",
            lastModifiedEpochSeconds = 42
        )

    @Test
    fun baseChangesForceFullPullAndKeepAccountIdentity() {
        val switched = account.withDiscoveredApi(
            account.copy(apiBasePath = "/index.php/apps/qownnotes/api/v1/", apiVersion = "1.4")
        )
        assertNull(switched.collectionEtag)
        assertEquals(0L, switched.lastModifiedEpochSeconds)
        assertEquals(account.id, switched.id)
        assertEquals("/index.php/apps/qownnotes/api/v1/attachment/", switched.attachmentApiPath())
        val fallback = switched.withDiscoveredApi(switched.copy(apiBasePath = null))
        assertNull(fallback.collectionEtag)
        assertEquals("/index.php/apps/notes/api/v1.4/attachment/", fallback.attachmentApiPath())
    }

    @Test
    fun unchangedBasePreservesCheckpoint() {
        val refreshed = account.withDiscoveredApi(account.copy(apiVersion = "1.4"))
        assertEquals("old", refreshed.collectionEtag)
        assertEquals(42L, refreshed.lastModifiedEpochSeconds)
    }
}
