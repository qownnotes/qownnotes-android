package org.qownnotes.mobile.markdown

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkTitleFetcherTest {
    @Test
    fun fetchesTitleAndUsesTheRedirectDestination() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(302).addHeader("Location", "/article")
            )
            server.enqueue(
                MockResponse()
                    .addHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<html><head><title>Example &amp; Notes</title></head></html>")
            )
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val fetched = LinkTitleFetcher(client).fetch(server.url("/start").toString())

            assertEquals(server.url("/article").toString(), fetched.url)
            assertEquals("Example & Notes", fetched.title)
            assertEquals("/start", server.takeRequest().path)
            assertEquals("/article", server.takeRequest().path)
        }
    }
}
