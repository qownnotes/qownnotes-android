package org.qownnotes.mobile.backend.nextcloud

import com.nextcloud.android.sso.aidl.NextcloudRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.NoteTagFileDownload

class TagFileRequestTest {
    private val endpoint = "/remote.php/dav/files/user/My%20Notes/"

    @Test
    fun downloadsTheNoteFolderDatabaseWithItsEtag() {
        val requests = mutableListOf<Pair<NextcloudRequest, Int>>()
        val client = WebDavRequestClient { request, limit ->
            requests += request to limit
            WebDavResponse("\"etag-2\"", byteArrayOf(1, 2, 3))
        }

        val result = downloadTagFileWithClient(client, endpoint, null)

        val downloaded = result as NoteTagFileDownload.Downloaded
        assertArrayEquals(byteArrayOf(1, 2, 3), downloaded.content)
        assertEquals("\"etag-2\"", downloaded.etag)
        val (request, limit) = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/remote.php/dav/files/user/My%20Notes/notes.sqlite", request.url)
        assertNull(request.header["If-None-Match"])
        assertEquals(MAX_TAG_FILE_BYTES, limit)
    }

    @Test
    fun conditionalDownloadReportsNotModifiedAndMissingFiles() {
        var status = 304
        val requests = mutableListOf<NextcloudRequest>()
        val client = WebDavRequestClient { request, _ ->
            requests += request
            throw WebDavStatusException(status)
        }

        assertSame(
            NoteTagFileDownload.NotModified,
            downloadTagFileWithClient(client, endpoint, "\"etag-1\"")
        )
        assertEquals(listOf("\"etag-1\""), requests.single().header["If-None-Match"])

        status = 404
        assertSame(NoteTagFileDownload.Missing, downloadTagFileWithClient(client, endpoint, null))
    }

    @Test
    fun downloadClassifiesFailures() {
        assertThrows<BackendException.Authentication> { download(401) }
        assertThrows<BackendException.Permission> { download(403) }
        assertThrows<BackendException.Retryable> { download(423) }
        assertThrows<BackendException.Retryable> { download(503) }
        assertThrows<BackendException.Protocol> { download(304, etag = null) }
        assertThrows<BackendException.Protocol> {
            downloadTagFileWithClient({ _, _ -> WebDavResponse(null, null) }, endpoint, null)
        }
        assertThrows<BackendException.Protocol> {
            downloadTagFileWithClient({ _, _ ->
                fail()
                WebDavResponse(null, null)
            }, endpoint, "a\nb")
        }
    }

    @Test
    fun uploadReplacesOnlyTheExpectedVersion() {
        val requests = mutableListOf<Pair<NextcloudRequest, Int>>()
        val client = WebDavRequestClient { request, limit ->
            requests += request to limit
            WebDavResponse("\"etag-3\"", null)
        }

        val etag = uploadTagFileWithClient(client, endpoint, byteArrayOf(9, 8), "\"etag-2\"")

        assertEquals("\"etag-3\"", etag)
        val (request, limit) = requests.single()
        assertEquals("PUT", request.method)
        assertEquals("/remote.php/dav/files/user/My%20Notes/notes.sqlite", request.url)
        assertEquals(listOf("\"etag-2\""), request.header["If-Match"])
        assertTrue(request.header["If-None-Match"] == null)
        assertEquals(listOf(9, 8), request.bodyAsStream.readBytes().map(Byte::toInt))
        assertEquals(0, limit)
    }

    @Test
    fun uploadClassifiesConcurrentChangesAndFailures() {
        assertThrows<BackendException.Conflict> { upload(412) }
        assertThrows<BackendException.RemoteMissing> { upload(404) }
        assertThrows<BackendException.InsufficientStorage> { upload(507) }
        assertThrows<BackendException.Authentication> { upload(401) }
        assertThrows<BackendException.Retryable> { upload(423) }
        assertNull(
            uploadTagFileWithClient({ _, _ ->
                WebDavResponse(null, null)
            }, endpoint, byteArrayOf(), "e")
        )
    }

    private fun download(status: Int, etag: String? = "\"e\"") =
        downloadTagFileWithClient({ _, _ -> throw WebDavStatusException(status) }, endpoint, etag)

    private fun upload(status: Int) = uploadTagFileWithClient(
        { _, _ -> throw WebDavStatusException(status) },
        endpoint,
        byteArrayOf(1),
        "\"e\""
    )

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return
            throw AssertionError("Expected ${T::class.simpleName}, got $error", error)
        }
        fail("Expected ${T::class.simpleName}")
    }
}
