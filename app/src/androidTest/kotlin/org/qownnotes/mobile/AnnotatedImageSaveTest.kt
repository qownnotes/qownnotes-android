package org.qownnotes.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account

@RunWith(AndroidJUnit4::class)
class AnnotatedImageSaveTest {
    private lateinit var application: TestQOwnNotesApplication
    private lateinit var localId: String

    @Before
    fun setUp() = runBlocking<Unit> {
        application = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as TestQOwnNotesApplication
        application.reset()
        application.fakeSyncScheduler.pause()
        application.component.accountRepository.save(
            Account("image-account", "Images", "https://example.com", "user", "user", "1.3")
        )
        localId = application.component.createNote("image-account").localId
        application.component.replaceNoteContent(localId, CONTENT)
    }

    @After
    fun tearDown() = runBlocking<Unit> { application.reset() }

    @Test
    fun addsTheAnnotatedImageAfterTheOriginal() = runBlocking<Unit> {
        application.component.saveAnnotatedImage(localId, CONTENT, 1, annotated(), replace = false)

        val (name, bytes) = application.fakeBackend.uploadedImages.single()
        assertTrue(name.endsWith(".jpg"))
        assertEquals("image/jpeg", application.fakeBackend.uploadedMediaTypes.single())
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(120, decoded.width)
        assertTrue(Color.red(decoded.getPixel(60, 40)) > 200)
        assertEquals(
            "# Shots\n\n`![code](media/c.png)`\n\n![Whiteboard](media/board.jpg)\n\n" +
                "![Whiteboard](media/$name)\n\nAfter\n",
            content()
        )
    }

    @Test
    fun replacesTheImageLinkAndKeepsTheRestOfTheNote() = runBlocking<Unit> {
        application.component.saveAnnotatedImage(localId, CONTENT, 1, annotated(), replace = true)

        val name = application.fakeBackend.uploadedImages.single().first
        assertEquals(CONTENT.replace("media/board.jpg", "media/$name"), content())
    }

    @Test
    fun leavesANoteAloneWhoseImageChangedSinceItWasShown() = runBlocking<Unit> {
        application.component.replaceNoteContent(localId, CONTENT.replace("board", "other"))

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                application.component.saveAnnotatedImage(
                    localId,
                    CONTENT,
                    1,
                    annotated(),
                    replace = true
                )
            }
        }
        assertEquals(CONTENT.replace("board", "other"), content())
    }

    private fun annotated() = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.RED)
        setHasAlpha(false)
    }

    private suspend fun content() = application.component.noteRepository.get(localId)!!.content

    private companion object {
        const val CONTENT =
            "# Shots\n\n`![code](media/c.png)`\n\n![Whiteboard](media/board.jpg)\n\nAfter\n"
    }
}
