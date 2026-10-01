package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.Gson
import com.nextcloud.android.sso.api.ParsedResponse
import io.reactivex.Observable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qownnotes.mobile.core.BackendException

class DeckCapabilityTest {
    @Test
    fun deckWithApiVersion11IsSupported() {
        assertTrue(
            supports(
                """{"notes":{"api_version":["1.3"]},
                    "deck":{"version":"1.14.0","canCreateBoards":true,"apiVersions":["1.0","1.1"]}}"""
            )
        )
    }

    @Test
    fun missingOrOlderDeckIsNotSupported() {
        assertFalse(supports("""{"notes":{"api_version":["1.3"]}}"""))
        assertFalse(supports("""{"deck":{"version":"1.2.0","apiVersions":["1.0"]}}"""))
        assertFalse(supports("""{"deck":{"version":"1.0.0"}}"""))
        assertFalse(supports("""{"deck":null}"""))
        assertFalse(supports("""{"deck":{"apiVersions":"1.1"}}"""))
        assertFalse(supports("""{"deck":{"apiVersions":[1.1]}}"""))
    }

    @Test
    fun malformedCapabilitiesAreAProtocolError() {
        assertThrows(BackendException.Protocol::class.java) {
            supportsDeckApi(
                Observable.just(
                    ParsedResponse.of(Gson().fromJson("""{"ocs":{}}""", OcsResponse::class.java))
                )
            )
        }
    }

    private fun supports(capabilities: String): Boolean = supportsDeckApi(
        Observable.just(
            ParsedResponse.of(
                Gson().fromJson(
                    """{"ocs":{"data":{"capabilities":$capabilities}}}""",
                    OcsResponse::class.java
                )
            )
        )
    )
}
