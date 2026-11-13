package org.qownnotes.mobile

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.text.NumberFormat
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import org.qownnotes.mobile.markdown.NoteImage

@RunWith(AndroidJUnit4::class)
class ImageInfoTest {
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
        directory = File(application.filesDir, "camera").apply { mkdirs() }
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        application.reset()
        directory.listFiles().orEmpty().filter { it.name.startsWith("info-test-") }
            .forEach(File::delete)
    }

    @Test
    fun readsWhatTheCameraRecorded() {
        val file = writePhoto(4000, 3000, withExif = true)

        val details = ImageDetailsReader.read(file)!!

        assertEquals(ImageFileInfo("image/jpeg", 3000, 4000, file.length()), details.info)
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 30), details.takenAt)
        assertEquals("Google Pixel 8", details.camera)
        assertEquals("Pixel 8 back camera 6.9mm f/1.68", details.lens)
        assertEquals(1.8, details.fNumber!!, 0.001)
        assertEquals(1 / 120.0, details.exposureSeconds!!, 0.0001)
        assertEquals(100, details.iso)
        assertEquals(4.3, details.focalLength!!, 0.001)
        assertEquals(48.2082, details.latitude!!, 0.0001)
        assertEquals(16.3738, details.longitude!!, 0.0001)
        assertEquals("HDR+ 1.0", details.software)
        assertTrue(details.hasPhotoDetails)
    }

    @Test
    fun imagesWithoutExifHaveOnlyTheirFileDetails() {
        val file = writePhoto(640, 480, withExif = false)

        val details = ImageDetailsReader.read(file)!!

        assertEquals(ImageFileInfo("image/jpeg", 640, 480, file.length()), details.info)
        assertFalse(details.hasPhotoDetails)
        assertNull(ImageDetailsReader.read(File(directory, "info-test-missing.jpg")))
    }

    @Test
    fun noteImageDetailsComeFromTheOriginalFileButNotFromTheWeb() = runBlocking<Unit> {
        val component = application.component
        val localId = UUID.randomUUID().toString()
        val photo = writePhoto(4000, 3000, withExif = true)
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            photo
        )
        val note = component.createCapturedNote("image-account", localId, uri)
        val link = component.noteImageLink(note.content, 0)!!

        val details = component.noteImageDetails(localId, link)

        assertNotNull(details)
        assertEquals("Google Pixel 8", details!!.camera)
        assertEquals(4000, details.info.height)
        assertNull(component.noteImageDetails(localId, "https://example.com/web.jpg"))
        assertNull(component.noteImageDetails(localId, "media/missing.jpg"))
    }

    @Test
    fun infoPanelShowsTheFileTheCameraAndTheLinkAndOffersShrinking() {
        val file = writePhoto(4000, 3000, withExif = true)
        val link = "media/info-photo.jpg"
        composeRule.setContent {
            MaterialTheme {
                NoteImageViewer(
                    NoteImage(BitmapFactory.decodeFile(file.path), "Whiteboard", 0),
                    shrinkActions = NoteImageShrinkActions(
                        defaults = org.qownnotes.mobile.core.ImageShrinking(2560, webp = true),
                        loadOriginal = { file },
                        shrink = { _, _ -> null },
                        save = { _, _ -> }
                    ),
                    infoSource = NoteImageInfoSource(link) { ImageDetailsReader.read(file) }
                ) {}
            }
        }

        composeRule.onNodeWithTag("image-viewer-info").performClick()
        waitForTag("image-info-camera")
        composeRule.onNodeWithTag("image-info-file").assertTextContains("info-photo.jpg")
            .assertTextContains(dimensions(3000, 4000), substring = true)
        composeRule.onNodeWithTag("image-info-resolution").assertTextContains("12 MP")
        composeRule.onNodeWithTag("image-info-aspect-ratio").assertTextContains("3:4")
        composeRule.onNodeWithTag("image-info-file-size").assertTextContains("JPEG")
        composeRule.onNodeWithTag("image-info-camera").assertTextContains("Google Pixel 8")
        composeRule.onNodeWithTag("image-info-exposure").performScrollTo()
            .assertTextContains("1/120 s", substring = true)
            .assertTextContains("ISO", substring = true)
        composeRule.onNodeWithTag("image-info-location").performScrollTo()
            .assertTextContains("° N", substring = true)
        composeRule.onNodeWithTag("image-info-description").performScrollTo()
            .assertTextContains("Whiteboard")
        composeRule.onNodeWithTag("image-info-link").performScrollTo().assertTextContains(link)

        val clipboard = application.getSystemService(ClipboardManager::class.java)
        composeRule.onNodeWithTag("image-info-copy-link").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.runOnUiThread { clipboard.primaryClip?.getItemAt(0)?.text } == link
        }

        composeRule.onNodeWithTag("image-info-shrink").performScrollTo().performClick()
        waitForTag("image-shrink-panel")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("image-info-panel").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun infoPanelExplainsWebImagesWithTheirLoadedSize() {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            MaterialTheme {
                NoteImageViewer(
                    NoteImage(bitmap, "", 0),
                    infoSource = NoteImageInfoSource("https://example.com/banner.png")
                ) {}
            }
        }

        composeRule.onNodeWithTag("image-viewer-info").performClick()
        waitForTag("image-info-unavailable")
        composeRule.onNodeWithTag("image-info-file").assertTextContains("banner.png")
            .assertTextContains(dimensions(1920, 1080), substring = true)
        composeRule.onNodeWithTag("image-info-aspect-ratio").assertTextContains("16:9")
        composeRule.onNodeWithTag("image-info-unavailable")
            .assertTextContains("web", substring = true)
        assertTrue(
            composeRule.onAllNodesWithTag("image-info-file-size").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithTag("image-info-shrink").fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("image-info-description").performScrollTo()
            .assertTextContains("No description")
        composeRule.onNodeWithTag("image-info-close").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("image-info-panel").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun writePhoto(width: Int, height: Int, withExif: Boolean): File {
        val file = File(directory, "info-test-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30, 120, 90))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        if (withExif) {
            ExifInterface(file.path).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    "${ExifInterface.ORIENTATION_ROTATE_90}"
                )
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:12 09:30:00")
                setAttribute(ExifInterface.TAG_MAKE, "Google")
                setAttribute(ExifInterface.TAG_MODEL, "Pixel 8")
                setAttribute(ExifInterface.TAG_LENS_MODEL, "Pixel 8 back camera 6.9mm f/1.68")
                setAttribute(ExifInterface.TAG_F_NUMBER, "18/10")
                setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/120")
                setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "100")
                setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "43/10")
                setAttribute(ExifInterface.TAG_SOFTWARE, "HDR+ 1.0")
                setLatLong(48.2082, 16.3738)
                saveAttributes()
            }
        }
        return file
    }

    private fun dimensions(width: Int, height: Int) = NumberFormat.getIntegerInstance().let {
        "${it.format(width)} × ${it.format(height)}"
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
}
