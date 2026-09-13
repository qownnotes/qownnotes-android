package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.GsonBuilder
import com.nextcloud.android.sso.api.ParsedResponse
import io.reactivex.Observable
import java.util.ArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.qownnotes.mobile.core.BackendException

class ValidateAccountMockServerTest {
    private val gson = GsonBuilder().create()

    @Test
    fun selectsSupportedApiVersion() {
        val response = parseCapabilities(
            """{"ocs":{"data":{"capabilities":{"notes":{"api_version":["1.2","1.3"]}}}}}"""
        )

        val version = validateCapabilities(Observable.just(response))

        assertEquals("1.3", version)
    }

    @Test
    fun reportsNotesAppMissingWhenCapabilitiesAbsent() {
        val response = parseCapabilities("""{"ocs":{"data":{"capabilities":{}}}}""")

        assertThrows(BackendException.NotesAppMissing::class.java) {
            validateCapabilities(Observable.just(response))
        }
    }

    @Test
    fun reportsNotesAppMissingWhenOcsEnvelopeMalformed() {
        val response = parseCapabilities("""{"ocs":{}}""")

        assertThrows(BackendException.NotesAppMissing::class.java) {
            validateCapabilities(Observable.just(response))
        }
    }

    @Test
    fun reportsProtocolErrorWhenResponseBodyEmpty() {
        val response = ParsedResponse<OcsResponse>(null, ArrayList())

        val error = assertThrows(BackendException.Protocol::class.java) {
            validateCapabilities(Observable.just(response))
        }

        assertEquals("Nextcloud returned an empty capabilities response", error.message)
    }

    private fun parseCapabilities(json: String): ParsedResponse<OcsResponse> =
        ParsedResponse.of(gson.fromJson(json, OcsResponse::class.java))
}
