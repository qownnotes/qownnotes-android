package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.GsonBuilder
import java.net.HttpURLConnection
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.PullCheckpoint
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class NotesApiMockServerTest {
    private lateinit var server: MockWebServer
    private lateinit var api: NotesApi

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        api =
            Retrofit.Builder()
                .baseUrl(server.url("/index.php/apps/notes/api/v1/"))
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
                .build()
                .create(NotesApi::class.java)
    }

    @After
    fun stopServer() = server.close()

    @Test
    fun sendsIncrementalPullHeadersAndQueryParameters() {
        server.enqueue(notesResponse("[]"))

        pullFromApi(api, PullCheckpoint("collection-etag", 123))

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/index.php/apps/notes/api/v1/notes", request.requestUrl!!.encodedPath)
        assertEquals("123", request.requestUrl!!.queryParameter("pruneBefore"))
        assertEquals("200", request.requestUrl!!.queryParameter("chunkSize"))
        assertFalse(request.requestUrl!!.queryParameterNames.contains("chunkCursor"))
        assertEquals("collection-etag", request.getHeader("If-None-Match"))
    }

    @Test
    fun traversesChunksAndAcceptsUnknownJsonFields() {
        server.enqueue(
            notesResponse("""[{"id":1,"title":"First","future":"ignored"}]""")
                .addHeader("X-Notes-Chunk-Cursor", "next-page")
                .addHeader("X-Notes-Chunk-Pending", "1")
        )
        server.enqueue(
            notesResponse("""[{"id":2}]""")
                .addHeader("ETag", "new-etag")
                .addHeader("Last-Modified", "Mon, 31 Aug 2026 12:00:00 GMT")
                .addHeader("X-Notes-Chunk-Pending", "0")
        )

        val result = pullFromApi(api, PullCheckpoint("old-etag", 123))

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertFalse(first.requestUrl!!.queryParameterNames.contains("chunkCursor"))
        assertEquals("next-page", second.requestUrl!!.queryParameter("chunkCursor"))
        assertEquals("old-etag", second.getHeader("If-None-Match"))
        assertEquals(listOf(1L, 2L), result.notes.map { it.id })
        assertEquals("new-etag", result.collectionEtag)
        assertEquals(1788177600L, result.lastModifiedEpochSeconds)
    }

    @Test
    fun returnsNotModifiedOnlyForInitialRequest() {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED))

        val result = pullFromApi(api, PullCheckpoint("old-etag", 123))

        assertTrue(result.notModified)
        assertEquals("old-etag", result.collectionEtag)
        assertEquals(123, result.lastModifiedEpochSeconds)
    }

    @Test
    fun rejectsNotModifiedResponseAfterPartialPull() {
        server.enqueue(
            notesResponse("""[{"id":1}]""")
                .addHeader("X-Notes-Chunk-Cursor", "next-page")
                .addHeader("X-Notes-Chunk-Pending", "1")
        )
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED))

        assertThrows(BackendException.Protocol::class.java) {
            pullFromApi(api, PullCheckpoint())
        }
    }

    @Test
    fun rejectsMissingRequiredNoteId() {
        server.enqueue(notesResponse("""[{"title":"Missing ID"}]"""))

        assertThrows(BackendException.Protocol::class.java) {
            pullFromApi(api, PullCheckpoint())
        }
    }

    @Test
    fun rejectsMalformedJson() {
        server.enqueue(notesResponse("[{invalid-json}]"))

        assertThrows(BackendException.Protocol::class.java) {
            pullFromApi(api, PullCheckpoint())
        }
    }

    @Test
    fun classifiesHttpFailures() {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED))
        assertThrows(BackendException.Authentication::class.java) {
            pullFromApi(api, PullCheckpoint())
        }

        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_FORBIDDEN))
        assertThrows(BackendException.Permission::class.java) {
            pullFromApi(api, PullCheckpoint())
        }

        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_UNAVAILABLE))
        assertThrows(BackendException.Retryable::class.java) {
            pullFromApi(api, PullCheckpoint())
        }
    }

    @Test
    fun networkInterruptionBetweenChunksIsRetryable() {
        server.enqueue(
            notesResponse("""[{"id":1}]""")
                .addHeader("X-Notes-Chunk-Cursor", "next-page")
                .addHeader("X-Notes-Chunk-Pending", "1")
        )
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertThrows(BackendException.Retryable::class.java) {
            pullFromApi(api, PullCheckpoint())
        }
    }

    private fun notesResponse(body: String) = MockResponse()
        .setResponseCode(HttpURLConnection.HTTP_OK)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
