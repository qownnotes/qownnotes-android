package org.qownnotes.mobile.backend.nextcloud

import com.nextcloud.android.sso.aidl.NextcloudRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaRequestTest {
    @Test
    fun audioUsesAttachmentsFolderAndAudioMimeTypeWithoutOverwriting() {
        val requests = mutableListOf<NextcloudRequest>()
        val client = MediaRequestClient { request ->
            requests += request
            null
        }
        assertEquals(
            "audio-unique.m4a",
            uploadImageWithClient(
                client,
                "/Notes",
                "audio-unique.m4a",
                "audio/mp4",
                byteArrayOf(1, 2)
            )
        )
        assertEquals(listOf("MKCOL", "PUT"), requests.map(NextcloudRequest::getMethod))
        val upload = requests.last()
        assertEquals("/Notes/attachments", requests.first().url)
        assertEquals("/Notes/attachments/audio-unique.m4a", upload.url)
        assertEquals(listOf("audio/mp4"), upload.header["Content-Type"])
        assertEquals(listOf("*"), upload.header["If-None-Match"])
        assertEquals(listOf<Byte>(1, 2), upload.bodyAsStream.readBytes().toList())
    }

    @Test
    fun uncertainUploadAcceptsOnlyIdenticalExistingImage() {
        val client = object : MediaRequestClient {
            override fun execute(request: NextcloudRequest): Int? =
                if (request.method == "MKCOL") 405 else 412

            override fun matches(url: String, content: ByteArray): Boolean {
                assertEquals("/Notes/media/image.jpg", url)
                return content.contentEquals(byteArrayOf(1, 2))
            }
        }
        assertEquals(
            "image.jpg",
            uploadImageWithClient(client, "/Notes", "image.jpg", "image/jpeg", byteArrayOf(1, 2))
        )
        assertThrows(org.qownnotes.mobile.core.BackendException.Protocol::class.java) {
            uploadImageWithClient(client, "/Notes", "image.jpg", "image/jpeg", byteArrayOf(3))
        }
    }

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
