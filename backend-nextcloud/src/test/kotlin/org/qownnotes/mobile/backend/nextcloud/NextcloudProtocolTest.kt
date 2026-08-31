package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextcloudProtocolTest {
    @Test
    fun selectsHighestSupportedApiVersion() {
        val versions =
            NextcloudProtocol.parseVersions(
                JsonParser.parseString("[\"1.1\",\"1.2\",\"1.10\",\"2.0\"]")
            )

        assertEquals("1.10", NextcloudProtocol.selectSupportedVersion(versions))
    }

    @Test
    fun rejectsVersionsOlderThanOnePointTwo() {
        assertNull(NextcloudProtocol.selectSupportedVersion(listOf("0.2", "1.1", "2.0")))
    }

    @Test
    fun acceptsLegacySingleVersionValue() {
        assertEquals(
            listOf("1.2"),
            NextcloudProtocol.parseVersions(JsonParser.parseString("\"1.2\""))
        )
    }
}
