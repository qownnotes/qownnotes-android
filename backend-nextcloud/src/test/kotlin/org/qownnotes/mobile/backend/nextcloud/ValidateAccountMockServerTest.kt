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
    fun discoversQOwnNotesWithoutNotesApp() {
        val response = parseCapabilities(
            """{"ocs":{"data":{"capabilities":{"qownnotes":{"notes_api_version":["1.4"],"api_base":"/index.php/apps/qownnotes/api/v1/"}}}}}"""
        )
        assertEquals(
            DiscoveredNotesApi("1.4", "/index.php/apps/qownnotes/api/v1/"),
            resolveCapabilities(Observable.just(response))
        )
    }

    @Test
    fun prefersQOwnNotesAndFallsBackWhenItsVersionIsUnsupported() {
        for ((version, expected) in listOf(
            "1.4" to "/index.php/apps/qownnotes/api/v1/",
            "2.0" to null
        )) {
            val response = parseCapabilities(
                """{"ocs":{"data":{"capabilities":{"notes":{"api_version":["1.3"]},"qownnotes":{"notes_api_version":["$version"],"api_base":"/index.php/apps/qownnotes/api/v1/"}}}}}"""
            )
            assertEquals(expected, resolveCapabilities(Observable.just(response)).basePath)
        }
    }

    @Test
    fun rejectsUnsafeQOwnNotesBases() {
        for (base in listOf(
            "https://evil.test/",
            "//evil.test/",
            "/../apps/qownnotes/api/v1/",
            "/index.php/apps/qownnotes/api/v1/?token=secret"
        )) {
            val response = parseCapabilities(
                """{"ocs":{"data":{"capabilities":{"qownnotes":{"notes_api_version":["1.4"],"api_base":"$base"}}}}}"""
            )
            assertThrows(BackendException.Protocol::class.java) {
                resolveCapabilities(Observable.just(response))
            }
        }
    }

    @Test
    fun reportsNotesAppMissingWhenCapabilitiesAbsent() {
        val response = parseCapabilities("""{"ocs":{"data":{"capabilities":{}}}}""")

        assertThrows(BackendException.NotesAppMissing::class.java) {
            validateCapabilities(Observable.just(response))
        }
    }

    @Test
    fun reportsProtocolErrorWhenOcsEnvelopeMalformed() {
        listOf(
            "{}",
            """{"ocs":null}""",
            """{"ocs":{}}""",
            """{"ocs":{"data":null}}""",
            """{"ocs":{"data":{}}}"""
        ).forEach { json ->
            assertThrows(BackendException.Protocol::class.java) {
                validateCapabilities(Observable.just(parseCapabilities(json)))
            }
        }
    }

    @Test
    fun reportsProtocolErrorWhenNotesCapabilityMalformed() {
        val response = parseCapabilities(
            """{"ocs":{"data":{"capabilities":{"notes":null}}}}"""
        )

        assertThrows(BackendException.Protocol::class.java) {
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
