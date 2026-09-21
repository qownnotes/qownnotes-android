package org.qownnotes.mobile.backend.nextcloud

import com.nextcloud.android.sso.aidl.NextcloudRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRequestTest {
    @Test
    fun createsMediaFolderAndStreamsImageWithoutOverwriting() {
        val requests = mutableListOf<NextcloudRequest>()
        val client = MediaRequestClient { request ->
            requests += request
            null
        }

        val fileName = uploadImageWithClient(
            client,
            "/remote.php/dav/files/user/Notes/",
            "image-unique.png",
            "image/png",
            byteArrayOf(1, 2)
        )

        assertEquals("image-unique.png", fileName)
        val create = requests[0]
        assertEquals("MKCOL", create.method)
        assertEquals("/remote.php/dav/files/user/Notes/media", create.url)
        assertNull(create.bodyAsStream)
        val upload = requests[1]
        assertEquals("PUT", upload.method)
        assertEquals("/remote.php/dav/files/user/Notes/media/image-unique.png", upload.url)
        assertEquals(listOf("image/png"), upload.header["Content-Type"])
        assertEquals(listOf("*"), upload.header["If-None-Match"])
        assertEquals(byteArrayOf(1, 2).toList(), upload.bodyAsStream.readBytes().toList())
        assertNull(upload.requestBody)
    }

    @Test
    fun uploadsWhenMediaFolderAlreadyExists() {
        val requests = mutableListOf<NextcloudRequest>()
        val client = MediaRequestClient { request ->
            requests += request
            if (requests.size == 1) 405 else null
        }

        uploadImageWithClient(
            client,
            "/remote.php/dav/files/user/Notes",
            "image-unique.webp",
            "image/webp",
            byteArrayOf(3)
        )

        assertEquals(listOf("MKCOL", "PUT"), requests.map(NextcloudRequest::getMethod))
    }
}
