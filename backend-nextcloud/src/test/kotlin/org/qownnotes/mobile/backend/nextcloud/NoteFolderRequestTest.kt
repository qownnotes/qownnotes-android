package org.qownnotes.mobile.backend.nextcloud

import com.nextcloud.android.sso.aidl.NextcloudRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.qownnotes.mobile.core.BackendException

class NoteFolderRequestTest {
    private val notes = "/remote.php/dav/files/user/Notes/"

    private fun endpoint(category: String) =
        notes + category.split('/').joinToString("") { "${it.replace(" ", "%20")}/" }

    @Test
    fun createsEachMissingFolderInTurnAndAcceptsExistingOnes() {
        val requests = mutableListOf<NextcloudRequest>()
        val client = WebDavRequestClient { request, limit ->
            requests += request
            assertEquals(0, limit)
            // The parent already exists.
            if (request.url == "${notes}Work/") throw WebDavStatusException(405)
            WebDavResponse(null, null)
        }

        createFolderWithClient(client, "Work/New Ideas", ::endpoint)

        assertEquals(listOf("MKCOL", "MKCOL"), requests.map(NextcloudRequest::getMethod))
        assertEquals(listOf("${notes}Work/", "${notes}Work/New%20Ideas/"), requests.map { it.url })
    }

    @Test
    fun creationFailuresAreClassified() {
        val client = WebDavRequestClient { _, _ -> throw WebDavStatusException(403) }
        try {
            createFolderWithClient(client, "Work", ::endpoint)
            fail()
        } catch (_: BackendException.Permission) {
        }
    }

    @Test
    fun deletesOnlyAnEmptyFolder() {
        val requests = mutableListOf<NextcloudRequest>()
        val client = WebDavRequestClient { request, _ ->
            requests += request
            when (request.method) {
                "PROPFIND" -> WebDavResponse(null, listing(request.url))
                else -> WebDavResponse(null, null)
            }
        }

        assertTrue(removeEmptyFolderWithClient(client, "Work/Old", ::endpoint))

        assertEquals(listOf("PROPFIND", "DELETE"), requests.map(NextcloudRequest::getMethod))
        assertEquals(listOf("1"), requests.first().header["Depth"])
        assertEquals("${notes}Work/Old/", requests.last().url)
    }

    @Test
    fun keepsAFolderWithAnythingInside() {
        val requests = mutableListOf<NextcloudRequest>()
        val client = WebDavRequestClient { request, _ ->
            requests += request
            WebDavResponse(null, listing(request.url, "${request.url}photo.jpg"))
        }

        assertFalse(removeEmptyFolderWithClient(client, "Work", ::endpoint))
        assertEquals(listOf("PROPFIND"), requests.map(NextcloudRequest::getMethod))
    }

    @Test
    fun aMissingFolderCountsAsRemoved() {
        val client = WebDavRequestClient { _, _ -> throw WebDavStatusException(404) }

        assertTrue(removeEmptyFolderWithClient(client, "Gone", ::endpoint))
    }

    @Test
    fun neverTouchesTheNotesFolderParentsOrMediaFolders() {
        val client = WebDavRequestClient { _, _ ->
            fail()
            WebDavResponse(null, null)
        }
        for (path in listOf("", "../Documents", "media", "Attachments/x", "/Work")) {
            try {
                removeEmptyFolderWithClient(client, path, ::endpoint)
                fail(path)
            } catch (_: IllegalArgumentException) {
            }
            try {
                createFolderWithClient(client, path, ::endpoint)
                fail(path)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    private fun listing(own: String, vararg children: String): ByteArray = buildString {
        append("""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">""")
        (listOf(own to true) + children.map { it to false }).forEach { (href, collection) ->
            append("<d:response><d:href>").append(href).append("</d:href>")
            append("<d:propstat><d:prop><d:resourcetype>")
            if (collection) append("<d:collection/>")
            append("</d:resourcetype></d:prop></d:propstat></d:response>")
        }
        append("</d:multistatus>")
    }.toByteArray()
}
