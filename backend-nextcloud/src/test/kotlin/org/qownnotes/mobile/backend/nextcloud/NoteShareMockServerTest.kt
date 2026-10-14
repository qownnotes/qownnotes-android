package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.nextcloud.android.sso.api.ParsedResponse
import io.reactivex.Observable
import java.net.HttpURLConnection
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteShare
import org.qownnotes.mobile.core.NoteShareChange
import org.qownnotes.mobile.core.NoteSharingPolicy
import org.qownnotes.mobile.core.SyncState
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NoteShareMockServerTest {
    private lateinit var server: MockWebServer
    private lateinit var notesApi: NotesApi
    private lateinit var shareApi: ShareApi

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        notesApi = retrofit("/index.php/apps/notes/api/v1/").create(NotesApi::class.java)
        shareApi = retrofit(SHARE_ENDPOINT).create(ShareApi::class.java)
    }

    @After
    fun stopServer() = server.close()

    @Test
    fun loadsThePublicLinkOfTheNoteFile() {
        server.enqueue(notePath("/My Notes/Work/Plan & ideas.md"))
        server.enqueue(
            jsonResponse(
                """{"ocs":{"meta":{"status":"ok","statuscode":200},"data":[
                  {"id":"8","share_type":0,"item_type":"file","share_with":"bob",
                   "url":null,"permissions":19},
                  {"id":"12","share_type":3,"item_type":"file","url":"https://cloud.example/s/b",
                   "permissions":17,"password":null,"share_with":null,"expiration":null},
                  {"id":"9","share_type":3,"item_type":"file","url":"https://cloud.example/s/a",
                   "permissions":19,"password":"hash","share_with":"hash",
                   "expiration":"2026-11-01 00:00:00"}
                ]}}"""
            )
        )

        val status = loadNoteShareWithApis(capabilities(LINK_SHARING), notesApi, shareApi, note())

        assertEquals(NoteSharingPolicy(true, passwordEnforced = true, 10), status.policy)
        assertEquals(
            NoteShare("9", "https://cloud.example/s/a", 19, true, "2026-11-01"),
            status.share
        )
        assertTrue(status.share!!.editAllowed)
        assertEquals("/index.php/apps/notes/api/v1/notes/7", server.takeRequest().path)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/ocs/v2.php/apps/files_sharing/api/v1/shares",
            request.requestUrl!!.encodedPath
        )
        assertEquals("/My Notes/Work/Plan & ideas.md", request.requestUrl!!.queryParameter("path"))
        assertEquals("json", request.requestUrl!!.queryParameter("format"))
        assertEquals("false", request.requestUrl!!.queryParameter("reshares"))
        assertNull(request.getHeader("OCS-APIRequest"))
    }

    @Test
    fun aNoteWithoutALinkHasNoShare() {
        server.enqueue(notePath("/Notes/Plan.md"))
        server.enqueue(jsonResponse("""{"ocs":{"meta":{"status":"ok"},"data":[]}}"""))

        val status = loadNoteShareWithApis(
            capabilities("""{"files_sharing":{"api_enabled":true,"public":{"enabled":false}}}"""),
            notesApi,
            shareApi,
            note()
        )

        assertEquals(NoteSharingPolicy(linkSharingEnabled = false), status.policy)
        assertNull(status.share)
    }

    @Test
    fun missingSharingAppDisablesLinks() {
        assertFalse(noteSharingPolicy(capabilityResponse("""{"notes":{}}""")).linkSharingEnabled)
        assertFalse(
            noteSharingPolicy(
                capabilityResponse(
                    """{"files_sharing":{"api_enabled":false,"public":{"enabled":true}}}"""
                )
            ).linkSharingEnabled
        )
    }

    @Test
    fun createsAPasswordProtectedLinkForTheNotePath() {
        server.enqueue(notePath("/Notes/Plan.md"))
        server.enqueue(jsonResponse("""{"ocs":{"data":[]}}"""))
        server.enqueue(jsonResponse(shareJson(id = 21, permissions = 17, password = "hash")))

        val share = createNoteShareWithApis(notesApi, shareApi, note(), "s3cret pass")

        assertEquals(NoteShare("21", "https://cloud.example/s/x", 17, true), share)
        server.takeRequest()
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            "/ocs/v2.php/apps/files_sharing/api/v1/shares",
            request.requestUrl!!.encodedPath
        )
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("/Notes/Plan.md", body["path"].asString)
        assertEquals(3, body["shareType"].asInt)
        assertEquals("s3cret pass", body["password"].asString)
    }

    @Test
    fun createsAnUnprotectedLinkWithoutAPasswordField() {
        server.enqueue(notePath("/Notes/Plan.md"))
        server.enqueue(jsonResponse("""{"ocs":{"data":[]}}"""))
        server.enqueue(jsonResponse(shareJson(id = 22, permissions = 1)))

        val share = createNoteShareWithApis(notesApi, shareApi, note(), null)

        assertFalse(share.passwordProtected)
        server.takeRequest()
        server.takeRequest()
        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertFalse(body.has("password"))
    }

    @Test
    fun creatingReusesAnExistingLinkAndAppliesTheRequestedPassword() {
        server.enqueue(notePath("/Notes/Plan.md"))
        server.enqueue(
            jsonResponse(
                """{"ocs":{"data":[${shareJson(id = 5, permissions = 1, wrapped = false)}]}}"""
            )
        )
        server.enqueue(jsonResponse(shareJson(id = 5, permissions = 1, password = "hash")))

        val share = createNoteShareWithApis(notesApi, shareApi, note(), "another")

        assertEquals("5", share.id)
        assertTrue(share.passwordProtected)
        server.takeRequest()
        server.takeRequest()
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals(
            "/ocs/v2.php/apps/files_sharing/api/v1/shares/5",
            update.requestUrl!!.encodedPath
        )
        assertEquals(
            "another",
            JsonParser.parseString(update.body.readUtf8()).asJsonObject["password"].asString
        )
        assertEquals(3, server.requestCount)
    }

    @Test
    fun enforcedPasswordRejectionKeepsTheServerMessage() {
        server.enqueue(notePath("/Notes/Plan.md"))
        server.enqueue(jsonResponse("""{"ocs":{"data":[]}}"""))
        server.enqueue(
            jsonResponse(
                """{"ocs":{"meta":{"status":"failure","statuscode":400,
                "message":"Passwords are enforced for link and mail shares"},"data":[]}}"""
            ).setResponseCode(HttpURLConnection.HTTP_BAD_REQUEST)
        )

        val error = assertThrows(BackendException.Rejected::class.java) {
            createNoteShareWithApis(notesApi, shareApi, note(), null)
        }
        assertEquals("Passwords are enforced for link and mail shares", error.serverMessage)
    }

    @Test
    fun forbiddenWithoutAMessageIsAPermissionError() {
        server.enqueue(notePath("/Notes/Plan.md"))
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_FORBIDDEN))

        assertThrows(BackendException.Permission::class.java) {
            loadNoteShareWithApis(capabilities(LINK_SHARING), notesApi, shareApi, note())
        }
    }

    @Test
    fun aNoteMissingOnTheServerCannotBeShared() {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND))

        assertThrows(BackendException.RemoteMissing::class.java) {
            createNoteShareWithApis(notesApi, shareApi, note(), null)
        }
    }

    @Test
    fun anUnsyncedNoteCannotBeShared() {
        assertThrows(BackendException.FeatureUnavailable::class.java) {
            createNoteShareWithApis(notesApi, shareApi, note().copy(remoteId = null), null)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun anUnsafeNotePathIsRejected() {
        server.enqueue(notePath("/Notes/../secret.md"))

        assertThrows(BackendException.Protocol::class.java) {
            createNoteShareWithApis(notesApi, shareApi, note(), null)
        }
    }

    @Test
    fun allowingEditingSetsTheUpdatePermission() {
        server.enqueue(jsonResponse(shareJson(id = 9, permissions = 19)))

        val share = updateNoteShareWithApi(
            shareApi,
            NoteShare("9", "https://cloud.example/s/x", 17, false),
            NoteShareChange.EditAllowed(true)
        )

        assertTrue(share.editAllowed)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            19,
            JsonParser.parseString(request.body.readUtf8()).asJsonObject["permissions"].asInt
        )
    }

    @Test
    fun forbiddingEditingClearsOnlyTheUpdatePermission() {
        server.enqueue(jsonResponse(shareJson(id = 9, permissions = 17)))

        updateNoteShareWithApi(
            shareApi,
            NoteShare("9", "https://cloud.example/s/x", 19, false),
            NoteShareChange.EditAllowed(false)
        )

        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(17, body["permissions"].asInt)
        assertFalse(body.has("password"))
    }

    @Test
    fun removingThePasswordSendsAnEmptyPassword() {
        server.enqueue(jsonResponse(shareJson(id = 9, permissions = 17)))

        val share = updateNoteShareWithApi(
            shareApi,
            NoteShare("9", "https://cloud.example/s/x", 17, true),
            NoteShareChange.RemovePassword
        )

        assertFalse(share.passwordProtected)
        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("", body["password"].asString)
    }

    @Test
    fun anUpdateReturningAnotherShareIsAProtocolError() {
        server.enqueue(jsonResponse(shareJson(id = 10, permissions = 17)))

        assertThrows(BackendException.Protocol::class.java) {
            updateNoteShareWithApi(
                shareApi,
                NoteShare("9", "https://cloud.example/s/x", 17, false),
                NoteShareChange.EditAllowed(false)
            )
        }
    }

    @Test
    fun deletingIsIdempotent() {
        server.enqueue(jsonResponse("""{"ocs":{"meta":{"status":"ok"},"data":[]}}"""))
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND))
        val share = NoteShare("9", "https://cloud.example/s/x", 17, false)

        deleteNoteShareWithApi(shareApi, share)
        deleteNoteShareWithApi(shareApi, share)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/ocs/v2.php/apps/files_sharing/api/v1/shares/9",
            request.requestUrl!!.encodedPath
        )
    }

    @Test
    fun invalidShareIdsAreNeverSent() {
        assertThrows(BackendException.Protocol::class.java) {
            deleteNoteShareWithApi(
                shareApi,
                NoteShare("../9", "https://cloud.example/s/x", 1, false)
            )
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aShareWithoutAWebLinkIsAProtocolError() {
        server.enqueue(notePath("/Notes/Plan.md"))
        server.enqueue(jsonResponse("""{"ocs":{"data":[]}}"""))
        server.enqueue(
            jsonResponse("""{"ocs":{"data":{"id":"3","share_type":3,"url":"javascript:x"}}}""")
        )

        assertThrows(BackendException.Protocol::class.java) {
            createNoteShareWithApis(notesApi, shareApi, note(), null)
        }
    }

    @Test
    fun extractsTheOcsMessageFromAnSsoErrorBody() {
        assertEquals(
            "Password needs to be at least 10 characters long",
            ocsErrorMessage(
                "java.lang.IllegalStateException: {\"ocs\":{\"meta\":{\"status\":\"failure\"," +
                    "\"message\":\"Password needs to be at least 10 characters long\"}}}"
            )
        )
        assertNull(ocsErrorMessage("java.lang.IllegalStateException: <html>Bad gateway</html>"))
        assertNull(ocsErrorMessage(null))
    }

    private fun note() = Note(
        localId = "local",
        accountId = "account",
        remoteId = 7,
        title = "Plan",
        content = "# Plan",
        modifiedAtEpochSeconds = 1,
        syncState = SyncState.SYNCHRONIZED
    )

    private fun notePath(path: String) =
        jsonResponse(Gson().toJson(mapOf("id" to 7, "internalPath" to path)))

    private fun shareJson(
        id: Int,
        permissions: Int,
        password: String? = null,
        wrapped: Boolean = true
    ): String {
        val share = Gson().toJson(
            mapOf(
                "id" to id.toString(),
                "share_type" to 3,
                "item_type" to "file",
                "url" to "https://cloud.example/s/x",
                "permissions" to permissions,
                "password" to password
            )
        )
        return if (wrapped) """{"ocs":{"meta":{"status":"ok"},"data":$share}}""" else share
    }

    private fun capabilities(json: String) = object : CapabilitiesApi {
        override fun getCapabilities() = capabilityResponse(json)
    }

    private fun capabilityResponse(json: String): Observable<ParsedResponse<OcsResponse>> =
        Observable.just(
            ParsedResponse.of(
                Gson().fromJson(
                    """{"ocs":{"data":{"capabilities":$json}}}""",
                    OcsResponse::class.java
                )
            )
        )

    private fun retrofit(path: String) = Retrofit.Builder()
        .baseUrl(server.url(path))
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()

    private fun jsonResponse(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val LINK_SHARING =
            """{"files_sharing":{"api_enabled":true,"public":{"enabled":true,
            "password":{"enforced":true,"askForOptionalPassword":false}}},
            "password_policy":{"minLength":10}}"""
    }
}
