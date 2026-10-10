package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.JsonParser
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
import org.qownnotes.mobile.core.DeckBoard
import org.qownnotes.mobile.core.DeckCard
import org.qownnotes.mobile.core.DeckCardDraft
import org.qownnotes.mobile.core.DeckCardLink
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
        assertNull(request.getHeader("OCS-APIRequest"))
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
        assertNull(stacksRequest.getHeader("OCS-APIRequest"))
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
        assertNull(request.getHeader("OCS-APIRequest"))
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

    @Test
    fun resolvesTheCardsCurrentListAndLoadsItsDetails() {
        enqueueCardLoad()
        val card = loadDeckCardWithApi(deckApi, DeckCardLink(2, 42))
        assertEquals(11L, card.stackId)
        assertEquals("alice", card.owner)
        assertEquals(7, card.order)
        assertEquals(1_790_000_000L, card.dueAtEpochSeconds)
        assertTrue(card.editable)
        assertEquals("2026-09-20T10:00:00Z", card.startDate)
        server.takeRequest()
        server.takeRequest()
        assertEquals(
            "/index.php/apps/deck/api/v1.1/boards/2/stacks/11/cards/42",
            server.takeRequest().path
        )
    }

    @Test
    fun findsArchivedCardsAndFailsClosedForUnknownPermissions() {
        server.enqueue(jsonResponse("""{"id":2}"""))
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("""[{"id":12,"cards":[{"id":42}]}]"""))
        server.enqueue(
            jsonResponse(
                fullCard.replace(
                    "\"stackId\":11",
                    "\"stackId\":12"
                ).replace("\"archived\":false", "\"archived\":true")
            )
        )
        val card = loadDeckCardWithApi(deckApi, DeckCardLink(2, 42))
        assertTrue(card.archived)
        assertFalse(card.editable)
        assertEquals(12L, card.stackId)
        assertThrows(BackendException.Permission::class.java) {
            updateDeckCardWithApi(deckApi, card, DeckCardDraft("No"))
        }
        assertEquals(4, server.requestCount)
    }

    @Test
    fun updatingPreservesMetadataAndExplicitlyClearsDueDate() {
        enqueueCardLoad()
        val original = loadDeckCardWithApi(deckApi, DeckCardLink(2, 42))
        repeat(3) { server.takeRequest() }
        enqueueCardLoad()
        server.enqueue(jsonResponse(fullCard))
        updateDeckCardWithApi(deckApi, original, DeckCardDraft("Edited", "New description"))
        repeat(3) { server.takeRequest() }
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/index.php/apps/deck/api/v1.1/boards/2/stacks/11/cards/42", request.path)
        assertNull(request.getHeader("OCS-APIRequest"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("Edited", body["title"].asString)
        assertEquals("New description", body["description"].asString)
        assertEquals("alice", body["owner"].asString)
        assertEquals(7, body["order"].asInt)
        assertEquals("plain", body["type"].asString)
        assertFalse(body["archived"].asBoolean)
        assertTrue(body["duedate"].isJsonNull)
        assertEquals("2026-09-20T10:00:00Z", body["startdate"].asString)
        assertFalse(body.has("done"))
        assertFalse(body.has("labels"))
    }

    @Test
    fun changedCardStopsBeforeWriting() {
        enqueueCardLoad()
        val original = loadDeckCardWithApi(deckApi, DeckCardLink(2, 42))
        enqueueCardLoad(etag = "new-etag")
        assertThrows(BackendException.Conflict::class.java) {
            updateDeckCardWithApi(deckApi, original, DeckCardDraft("Edited"))
        }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun missingCardsAreNotRecreatedAndForbiddenIsNotReportedAsMissing() {
        server.enqueue(jsonResponse("""{"id":2}"""))
        server.enqueue(jsonResponse("[]"))
        server.enqueue(jsonResponse("[]"))
        assertThrows(BackendException.RemoteMissing::class.java) {
            loadDeckCardWithApi(deckApi, DeckCardLink(2, 42))
        }
        server.enqueue(MockResponse().setResponseCode(403))
        assertThrows(BackendException.Permission::class.java) {
            loadDeckCardWithApi(deckApi, DeckCardLink(2, 42))
        }
    }

    private val fullCard = """{"id":42,"stackId":11,"title":"Call Alice","description":"Report",
        "owner":"alice","order":7,"type":"plain","archived":false,
        "duedate":"2026-09-21T14:13:20Z","startdate":"2026-09-20T10:00:00Z"}
    """.trimIndent()

    private fun enqueueCardLoad(etag: String = "card-etag") {
        server.enqueue(jsonResponse("""{"id":2,"permissions":{"PERMISSION_EDIT":true}}"""))
        server.enqueue(jsonResponse("""[{"id":11,"cards":[{"id":42}]}]"""))
        server.enqueue(jsonResponse(fullCard).setHeader("ETag", etag))
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_OK)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
