package org.qownnotes.mobile.data

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingCaptureStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun audioAndUploadCheckpointSurviveRecreationAndAreRemovedTogether() = runBlocking {
        val directory = temporary.newFolder()
        val store = PendingCaptureStore(directory)
        val content = byteArrayOf(4, 5, 6)
        assertEquals(
            "audio-local-note.m4a",
            store.save("local-note", true) {
                content.inputStream()
            }
        )
        val recreated = PendingCaptureStore(directory)
        assertEquals("audio/mp4", recreated.mimeType("local-note"))
        assertArrayEquals(content, recreated.read("local-note"))
        recreated.save("local-note", true) { throw IOException("Recording source is gone") }
        recreated.markUploaded("local-note")
        assertNull(PendingCaptureStore(directory).read("local-note"))
        recreated.remove("local-note")
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }

    @Test
    fun photoAndUploadCheckpointSurviveRecreation() = runBlocking {
        val directory = temporary.newFolder()
        val store = PendingCaptureStore(directory)
        val content = byteArrayOf(1, 2, 3)
        assertEquals("image-local-note.jpg", store.save("local-note") { content.inputStream() })
        assertArrayEquals(content, PendingCaptureStore(directory).read("local-note"))
        store.markUploaded("local-note")
        assertNull(PendingCaptureStore(directory).read("local-note"))
        assertArrayEquals(content, directory.resolve(store.fileName("local-note")).readBytes())
        store.remove("local-note")
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }

    @Test
    fun resumedSaveDoesNotReplaceDurablePhoto() = runBlocking {
        val store = PendingCaptureStore(temporary.newFolder())
        val content = byteArrayOf(1)
        store.save("local-note") { content.inputStream() }
        store.save("local-note") { throw IOException("Original camera file is gone") }
        assertArrayEquals(content, store.read("local-note"))
    }

    @Test
    fun failedCopyDoesNotLeavePartialPhoto() = runBlocking {
        val directory = temporary.newFolder()
        val store = PendingCaptureStore(directory)
        try {
            store.save("local-note") {
                object : InputStream() {
                    private var reads = 0
                    override fun read(): Int =
                        if (reads++ < 3) 1 else throw IOException("Cannot read picture")
                }
            }
        } catch (_: IOException) {
            // The source is not deleted by the store; the next attempt can retry it.
        }
        assertNull(store.read("local-note"))
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }
}
