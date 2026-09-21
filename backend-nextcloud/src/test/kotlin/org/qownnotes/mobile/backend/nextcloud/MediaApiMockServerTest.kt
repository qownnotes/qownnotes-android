package org.qownnotes.mobile.backend.nextcloud

import java.net.HttpURLConnection
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class MediaApiMockServerTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MediaApi

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/remote.php/dav/files/user/Notes/"))
            .build()
            .create(MediaApi::class.java)
    }

    @After
    fun stopServer() = server.close()

    @Test
    fun createsMediaFolderAndUploadsWithoutOverwriting() {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_CREATED))
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_CREATED))

        val fileName = uploadImageWithApi(api, "image-unique.png", "image/png", byteArrayOf(1, 2))

        assertEquals("image-unique.png", fileName)
        val create = server.takeRequest()
        assertEquals("MKCOL", create.method)
        assertEquals("/remote.php/dav/files/user/Notes/media", create.requestUrl!!.encodedPath)
        val upload = server.takeRequest()
        assertEquals("PUT", upload.method)
        assertEquals(
            "/remote.php/dav/files/user/Notes/media/image-unique.png",
            upload.requestUrl!!.encodedPath
        )
        assertEquals("image/png", upload.getHeader("Content-Type"))
        assertEquals("*", upload.getHeader("If-None-Match"))
        assertEquals(byteArrayOf(1, 2).toList(), upload.body.readByteArray().toList())
    }

    @Test
    fun uploadsWhenMediaFolderAlreadyExists() {
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_BAD_METHOD))
        server.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_NO_CONTENT))

        uploadImageWithApi(api, "image-unique.webp", "image/webp", byteArrayOf(3))

        assertEquals("MKCOL", server.takeRequest().method)
        assertEquals("PUT", server.takeRequest().method)
    }
}
