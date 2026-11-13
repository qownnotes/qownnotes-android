package org.qownnotes.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.ImageShrinkSettings
import org.qownnotes.mobile.core.ImageShrinking
import org.qownnotes.mobile.markdown.NoteImage

@RunWith(AndroidJUnit4::class)
class ImageShrinkTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var application: TestQOwnNotesApplication
    private lateinit var directory: File

    @Before
    fun setUp() = runBlocking<Unit> {
        application = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as TestQOwnNotesApplication
        application.reset()
        application.fakeSyncScheduler.pause()
        application.component.accountRepository.save(
            Account("image-account", "Images", "https://example.com", "user", "user", "1.3")
        )
        // Shared with the camera through the app's file provider, so chosen images can use it too.
        directory = File(application.filesDir, "camera").apply { mkdirs() }
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        application.reset()
        directory.listFiles().orEmpty().filter { it.name.startsWith("shrink-test-") }
            .forEach(File::delete)
    }

    @Test
    fun turnsPngGraphicsIntoLosslessWebp() {
        val source = writeImage(800, 600, Bitmap.CompressFormat.PNG, "png") { canvas ->
            val paint = Paint().apply { color = Color.BLACK }
            for (x in 0 until 800 step 8) canvas.drawRect(x.toFloat(), 0f, x + 3f, 600f, paint)
        }
        val target = File(directory, "shrink-test-${UUID.randomUUID()}.webp")

        val shrunk = ImageShrinker.shrinkTo(source, ImageShrinking(webp = true), target)

        assertNotNull(shrunk)
        assertEquals(ImageFileInfo("image/webp", 800, 600, target.length()), shrunk)
        assertTrue(target.length() < source.length())
        val before = BitmapFactory.decodeFile(source.path)
        val after = BitmapFactory.decodeFile(target.path)
        for (x in listOf(0, 3, 4, 8, 401, 799)) {
            assertEquals(before.getPixel(x, 300), after.getPixel(x, 300))
        }
    }

    @Test
    fun keepsTheOriginalWhenShrinkingWouldNotSaveSpace() {
        val random = Random(42)
        val source = writeImage(256, 256, Bitmap.CompressFormat.JPEG, "jpg", quality = 5) {
            val paint = Paint()
            for (y in 0 until 256) {
                for (x in 0 until 256) {
                    paint.color =
                        Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                    it.drawPoint(x.toFloat(), y.toFloat(), paint)
                }
            }
        }
        val target = File(directory, "shrink-test-${UUID.randomUUID()}.webp")

        assertNull(ImageShrinker.shrinkTo(source, ImageShrinking(webp = true), target))
        assertFalse(target.exists())
        assertNull(ImageShrinker.shrinkTo(source, ImageShrinking(maxDimension = 1920), target))
        assertNull(ImageShrinker.shrinkTo(source, ImageShrinking.NONE, target))
    }

    @Test
    fun chosenImagesAreAttachedAsTheyAreByDefault() = runBlocking<Unit> {
        val localId = application.component.createNote("image-account").localId
        val source = writeImage(4000, 3000, Bitmap.CompressFormat.JPEG, "jpg")

        application.component.importImage(localId, uriFor(source))

        assertEquals("image/jpeg", application.fakeBackend.uploadedMediaTypes.single())
        assertArrayEquals(
            source.readBytes(),
            application.fakeBackend.uploadedImages.single().second
        )
    }

    @Test
    fun chosenImagesAreShrunkToWebpWhenTurnedOn() = runBlocking<Unit> {
        application.component.settings.setImageShrink(ImageShrinkSettings(chosenImages = true))
        val localId = application.component.createNote("image-account").localId
        val source = writeImage(4000, 3000, Bitmap.CompressFormat.JPEG, "jpg")

        val imported = application.component.importImage(localId, uriFor(source))

        val (name, content) = application.fakeBackend.uploadedImages.single()
        assertEquals("image/webp", application.fakeBackend.uploadedMediaTypes.single())
        assertTrue(name.endsWith(".webp"))
        assertTrue(imported.markdownPath.endsWith(name))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(content, 0, content.size, bounds)
        assertEquals(2560, bounds.outWidth)
        assertEquals(1920, bounds.outHeight)
        assertTrue(content.size < source.length())
        assertTrue(
            File(application.cacheDir, "shrinking-images").listFiles().orEmpty().isEmpty()
        )
    }

    @Test
    fun noteImageIsShrunkFromItsOriginalAndReplacedWithoutTouchingTheOriginal() =
        runBlocking<Unit> {
            val component = application.component
            val localId = UUID.randomUUID().toString()
            val photo = writeImage(4000, 3000, Bitmap.CompressFormat.JPEG, "jpg")
            val note = component.createCapturedNote("image-account", localId, uriFor(photo))
            val path = "media/image-$localId.jpg"
            assertEquals(path, component.shrinkableImagePath(localId, note.content, 0))

            val original = component.originalNoteImage(localId, path)
            val originalBytes = original.readBytes()
            val shrunk = component.shrinkNoteImage(original, ImageShrinking(1920, webp = true))

            assertNotNull(shrunk)
            assertEquals("image/webp", shrunk!!.info.mimeType)
            assertEquals(1920, shrunk.info.width)
            component.saveShrunkImage(localId, note.content, 0, shrunk, replace = true)

            val (name, content) = application.fakeBackend.uploadedImages.single()
            assertTrue(name.endsWith(".webp"))
            assertArrayEquals(shrunk.file.readBytes(), content)
            assertEquals(
                note.content.replace(path, "media/$name"),
                component.noteRepository.get(localId)!!.content
            )
            assertArrayEquals(originalBytes, original.readBytes())
        }

    @Test
    fun onlyLocalMediaImagesCanBeShrunk() = runBlocking<Unit> {
        val component = application.component
        val localId = component.createNote("image-account").localId
        val content = "![a](media/a.png) ![b](https://example.com/b.jpg) ![c](media/c.gif)"

        assertEquals("media/a.png", component.shrinkableImagePath(localId, content, 0))
        assertNull(component.shrinkableImagePath(localId, content, 1))
        assertNull(component.shrinkableImagePath(localId, content, 2))
    }

    @Test
    fun viewerComparesSizesAndReplacesTheImageWithTheShrunkCopy() {
        val original = writeImage(3000, 2000, Bitmap.CompressFormat.JPEG, "jpg")
        var saved: Pair<ShrunkNoteImage, Boolean>? = null
        var dismissed = false
        val actions = NoteImageShrinkActions(
            defaults = ImageShrinking(2560, webp = true),
            loadOriginal = { original },
            shrink = { file, shrinking ->
                val target = File(directory, "shrink-test-${UUID.randomUUID()}")
                ImageShrinker.shrinkTo(file, shrinking, target)?.let { ShrunkNoteImage(target, it) }
            },
            save = { shrunk, replace -> saved = shrunk to replace }
        )
        composeRule.setContent {
            MaterialTheme {
                if (!dismissed) {
                    NoteImageViewer(
                        NoteImage(BitmapFactory.decodeFile(original.path), "Board", 0),
                        shrinkActions = actions
                    ) { dismissed = true }
                }
            }
        }

        composeRule.onNodeWithTag("image-viewer-shrink").performClick()
        waitForTag("image-shrink-saved")
        waitForResult("2,560 × 1,707")
        composeRule.onNodeWithTag("image-shrink-size-2560").assertIsSelected()
        // The image already fits the large size, so choosing it would not change anything.
        composeRule.onNodeWithTag("image-shrink-size-3840").assertIsNotEnabled()

        composeRule.onNodeWithTag("image-shrink-size-original").performScrollTo().performClick()
        waitForResult("3,000 × 2,000")
        composeRule.onNodeWithTag("image-shrink-size-1920").performScrollTo().performClick()
        waitForResult("1,920 × 1,280")
        composeRule.onNodeWithTag("image-shrink-replace").performScrollTo().assertIsEnabled()
            .performClick()

        composeRule.waitUntil(10_000) { dismissed }
        val (shrunk, replace) = saved!!
        assertTrue(replace)
        assertEquals(ImageFileInfo("image/webp", 1920, 1280, shrunk.file.length()), shrunk.info)
    }

    @Test
    fun viewerExplainsWhenTheOriginalCannotBeLoaded() {
        val actions = NoteImageShrinkActions(
            defaults = ImageShrinking(2560, webp = true),
            loadOriginal = { error("The original image could not be loaded.") },
            shrink = { _, _ -> null },
            save = { _, _ -> error("Nothing to save") }
        )
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            MaterialTheme {
                NoteImageViewer(NoteImage(bitmap, "Board", 0), shrinkActions = actions) {}
            }
        }

        composeRule.onNodeWithTag("image-viewer-shrink").performClick()
        waitForTag("image-shrink-error")
        composeRule.onNodeWithTag("image-shrink-error")
            .assertTextContains("could not be loaded", substring = true)
        composeRule.onNodeWithTag("image-shrink-close").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("image-shrink-panel").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun writeImage(
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
        extension: String,
        quality: Int = 90,
        draw: (Canvas) -> Unit = { canvas ->
            canvas.drawRect(
                0f,
                0f,
                width / 8f,
                height / 8f,
                Paint().apply { color = Color.RED }
            )
        }
    ): File {
        val file = File(directory, "shrink-test-${UUID.randomUUID()}.$extension")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(40, 90, 200))
        draw(Canvas(bitmap))
        file.outputStream().use { bitmap.compress(format, quality, it) }
        bitmap.recycle()
        return file
    }

    private fun uriFor(file: File) =
        FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)

    private fun waitForResult(text: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodes(
            hasTestTag("image-shrink-result") and hasText(text, substring = true)
        ).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}
