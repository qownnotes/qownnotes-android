package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.DeckBoard
import org.qownnotes.mobile.core.DeckCard
import org.qownnotes.mobile.core.DeckCardDraft
import org.qownnotes.mobile.core.DeckStack
import org.qownnotes.mobile.core.DeckStackTarget
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DeckApiMockServerTest {
    private lateinit var server: MockWebServer
    private lateinit var deckApi: DeckApi

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        deckApi = Retrofit.Builder()
            .baseUrl(server.url(DECK_ENDPOINT))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeckApi::class.java)
    }

    @After
    fun stopServer() = server.close()

    @Test
    fun loadsWritableBoardsWithTheirListsInOrder() {
        server.enqueue(
            jsonResponse(
                """
                [
                  {"id":2,"title":"work","archived":false,"deletedAt":0,
                   "owner":{"uid":"alice"},
                   "permissions":{"PERMISSION_READ":true,"PERMISSION_EDIT":true},
                   "stacks":[
                     {"id":12,"title":"Done","order":2,"deletedAt":0},
                     {"id":11,"title":"To do","order":0,"deletedAt":0},
                     {"id":13,"title":"Old","order":1,"deletedAt":1700000000}
                   ]},
                  {"id":3,"title":"Archived","archived":true,"deletedAt":0,"stacks":[]},
                  {"id":4,"title":"Deleted","archived":false,"deletedAt":1700000000,"stacks":[]},
                  {"id":5,"title":"Read only","archived":false,"deletedAt":0,
                   "permissions":{"PERMISSION_READ":true,"PERMISSION_EDIT":false},"stacks":[]},
                  {"id":1,"title":"Alpha","archived":false,"deletedAt":0,"stacks":[]}
                ]
                """.trimIndent()
            )
        )

        val boards = loadDeckBoardsWithApi(deckApi)

        assertEquals(
            listOf(
                DeckBoard(1, "Alpha", emptyList()),
                DeckBoard(2, "work", listOf(DeckStack(11, "To do"), DeckStack(12, "Done")))
            ),
            boards
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/index.php/apps/deck/api/v1.1/boards", request.requestUrl!!.encodedPath)
        assertEquals("true", request.requestUrl!!.queryParameter("details"))
        assertEquals("true", request.getHeader("OCS-APIRequest"))
    }

    @Test
    fun loadsListsSeparatelyWhenABoardOmitsThem() {
        server.enqueue(jsonResponse("""[{"id":7,"title":"Plain"}]"""))
        server.enqueue(jsonResponse("""[{"id":70,"title":"Inbox","order":0,"deletedAt":0}]"""))

        val boards = loadDeckBoardsWithApi(deckApi)

        assertEquals(listOf(DeckBoard(7, "Plain", listOf(DeckStack(70, "Inbox")))), boards)
        server.takeRequest()
        val stacksRequest = server.takeRequest()
        assertEquals(
            "/index.php/apps/deck/api/v1.1/boards/7/stacks",
            stacksRequest.requestUrl!!.encodedPath
        )
        assertEquals("true", stacksRequest.getHeader("OCS-APIRequest"))
    }

    @Test
    fun missingDeckAppIsReportedAsUnavailable() {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND))

        val error = assertThrows(BackendException.FeatureUnavailable::class.java) {
            loadDeckBoardsWithApi(deckApi)
        }
        assertEquals("Install and enable the Deck app on Nextcloud", error.message)
    }

    @Test
    fun boardErrorsKeepTheirClassification() {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))
        assertThrows(BackendException.Authentication::class.java) {
            loadDeckBoardsWithApi(deckApi)
        }
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAVAILABLE))
        assertThrows(BackendException.Retryable::class.java) { loadDeckBoardsWithApi(deckApi) }
    }

    @Test
    fun createsAPlainCardWithDescriptionAndUtcDueDate() {
        server.enqueue(
            jsonResponse("""{"id":42,"stackId":11,"title":"Call Alice","owner":"alice"}""")
        )

        val card = createDeckCardWithApi(
            deckApi,
            DeckStackTarget(boardId = 2, stackId = 11),
            DeckCardDraft(
                title = "  Call Alice\n",
                description = "About the *report*",
                dueAtEpochSeconds = 1_790_000_000
            )
        )

        assertEquals(DeckCard(id = 42, boardId = 2, stackId = 11, title = "Call Alice"), card)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals(
            "/index.php/apps/deck/api/v1.1/boards/2/stacks/11/cards",
            request.requestUrl!!.encodedPath
        )
        assertEquals("true", request.getHeader("OCS-APIRequest"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("Call Alice", body["title"].asString)
        assertEquals("plain", body["type"].asString)
        assertEquals(0, body["order"].asInt)
        assertEquals("About the *report*", body["description"].asString)
        assertEquals("2026-09-21T14:13:20Z", body["duedate"].asString)
    }

    @Test
    fun createsACardWithoutDueDate() {
        server.enqueue(jsonResponse("""{"id":43}"""))

        val card = createDeckCardWithApi(
            deckApi,
            DeckStackTarget(boardId = 2, stackId = 11),
            DeckCardDraft(title = "Later")
        )

        assertEquals(DeckCard(id = 43, boardId = 2, stackId = 11, title = "Later"), card)
        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertFalse(body.has("duedate") && !body["duedate"].isJsonNull)
        assertEquals("", body["description"].asString)
    }

    @Test
    fun missingListIsReportedAsUnavailable() {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND))

        assertThrows(BackendException.FeatureUnavailable::class.java) {
            createDeckCardWithApi(deckApi, DeckStackTarget(2, 99), DeckCardDraft("Card"))
        }
    }

    @Test
    fun rejectedCardIsAProtocolErrorAndForbiddenIsAPermissionError() {
        server.enqueue(
            jsonResponse("""{"status":400,"message":"title must be provided"}""")
                .setResponseCode(HttpURLConnection.HTTP_BAD_REQUEST)
        )
        assertThrows(BackendException.Protocol::class.java) {
            createDeckCardWithApi(deckApi, DeckStackTarget(2, 11), DeckCardDraft("Card"))
        }
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_FORBIDDEN))
        assertThrows(BackendException.Permission::class.java) {
            createDeckCardWithApi(deckApi, DeckStackTarget(2, 11), DeckCardDraft("Card"))
        }
    }

    @Test
    fun blankTitleIsRejectedBeforeAnyRequest() {
        assertThrows(IllegalArgumentException::class.java) {
            createDeckCardWithApi(deckApi, DeckStackTarget(2, 11), DeckCardDraft(" \n "))
        }
        assertEquals(0, server.requestCount)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_OK)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
