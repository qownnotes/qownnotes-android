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
    fun webpPhotoKeepsItsTypeAcrossRecreationAndUpload() = runBlocking {
        val directory = temporary.newFolder()
        val store = PendingCaptureStore(directory)
        val content = byteArrayOf(7, 8, 9)
        assertEquals(
            "image-local-note.webp",
            store.save("local-note", "image/webp") { content.inputStream() }
        )
        // Saving again after a restart keeps the WebP photo rather than adding a JPEG one.
        assertEquals(
            "image-local-note.webp",
            store.save("local-note") { throw IOException("Original camera file is gone") }
        )
        val recreated = PendingCaptureStore(directory)
        assertEquals("image/webp", recreated.mimeType("local-note"))
        assertEquals("image-local-note.webp", recreated.fileName("local-note"))
        assertEquals(directory.resolve("image-local-note.webp"), recreated.image("local-note"))
        assertArrayEquals(content, recreated.read("local-note"))
        assertNull(recreated.readAudio("local-note"))
        recreated.markUploaded("local-note")
        assertNull(recreated.read("local-note"))
        recreated.remove("local-note")
        assertEquals(emptyList<String>(), directory.list()!!.toList())
    }

    @Test
    fun rejectsUnsupportedCaptureTypes() = runBlocking {
        val directory = temporary.newFolder()
        try {
            PendingCaptureStore(directory).save("local-note", "image/gif") {
                byteArrayOf(1).inputStream()
            }
            throw AssertionError("A GIF capture was accepted")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
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
