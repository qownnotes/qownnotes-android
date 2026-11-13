package org.qownnotes.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import android.widget.TextView
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.exifinterface.media.ExifInterface
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.ImageOrigin
import org.qownnotes.mobile.core.ImageShrinkSettings
import org.qownnotes.mobile.core.ImageShrinking
import org.qownnotes.mobile.core.PhotoResolution
import org.qownnotes.mobile.core.PhotoSize
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote

@RunWith(AndroidJUnit4::class)
class CameraPhotoTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val application get() = composeRule.activity.application as TestQOwnNotesApplication
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var monitor: Instrumentation.ActivityMonitor? = null
    private lateinit var directory: File

    @Before
    fun reset() {
        runBlocking { application.reset() }
        directory = File(application.cacheDir, "camera-photo-test").apply { mkdirs() }
        waitForTag("onboarding")
    }

    @After
    fun tearDown() {
        monitor?.let(instrumentation::removeMonitor)
        directory.deleteRecursively()
    }

    @Test
    fun reducesALargePhotoUprightAndKeepsItsCaptureTime() = runBlocking {
        // Cameras commonly store portrait photos sideways and record the turn in Exif.
        val file = writePhoto(4000, 3000, ExifInterface.ORIENTATION_ROTATE_90)

        val mimeType = ImageShrinker.shrinkInPlace(
            file,
            ImageShrinking(PhotoSize.MEDIUM.maxDimension)
        )

        assertEquals("image/jpeg", mimeType)
        val bounds = bounds(file)
        assertEquals(1920, bounds.outWidth)
        assertEquals(2560, bounds.outHeight)
        assertEquals("image/jpeg", bounds.outMimeType)
        val exif = ExifInterface(file.path)
        assertEquals(
            ExifInterface.ORIENTATION_UNDEFINED,
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        )
        assertEquals(CAPTURE_TIME, exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        // The red marker in the original's top-left corner is now at the top-right.
        val upright = BitmapFactory.decodeFile(file.path)
        assertTrue(Color.red(upright.getPixel(upright.width - 20, 20)) > 200)
        assertTrue(Color.red(upright.getPixel(20, 20)) < 80)
        assertFalse(File(file.parentFile, "${file.name}.shrunk").exists())
    }

    @Test
    fun storesAShrunkPhotoAsWebpUprightWithItsCaptureTime() = runBlocking {
        val file = writePhoto(4000, 3000, ExifInterface.ORIENTATION_ROTATE_90)
        val originalBytes = file.length()

        val mimeType = ImageShrinker.shrinkInPlace(
            file,
            ImageShrinking(PhotoSize.MEDIUM.maxDimension, webp = true)
        )

        assertEquals("image/webp", mimeType)
        val bounds = bounds(file)
        assertEquals("image/webp", bounds.outMimeType)
        assertEquals(1920, bounds.outWidth)
        assertEquals(2560, bounds.outHeight)
        assertTrue(file.length() < originalBytes)
        assertEquals(
            CAPTURE_TIME,
            ExifInterface(file.path).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        )
        val upright = BitmapFactory.decodeFile(file.path)
        assertTrue(Color.red(upright.getPixel(upright.width - 20, 20)) > 200)
        // Shrinking again is harmless: the photo already fits and is kept as it is.
        val shrunkBytes = file.readBytes()
        assertEquals(
            "image/webp",
            ImageShrinker.shrinkInPlace(
                file,
                ImageShrinking(PhotoSize.MEDIUM.maxDimension, webp = true)
            )
        )
        assertArrayEquals(shrunkBytes, file.readBytes())
    }

    @Test
    fun keepsTheCameraFileWhenItFitsOrFullResolutionIsChosen() = runBlocking {
        val small = writePhoto(1600, 1200, ExifInterface.ORIENTATION_ROTATE_90)
        val smallBytes = small.readBytes()
        ImageShrinker.shrinkInPlace(small, ImageShrinking(PhotoSize.MEDIUM.maxDimension))
        assertArrayEquals(smallBytes, small.readBytes())

        val large = writePhoto(4000, 3000, ExifInterface.ORIENTATION_NORMAL)
        val largeBytes = large.readBytes()
        ImageShrinker.shrinkInPlace(large, ImageShrinking.NONE)
        assertArrayEquals(largeBytes, large.readBytes())
    }

    @Test
    fun photoResolutionSettingPersistsAndKeepsTheChosenSizeWhileOff() {
        val name = "photo-resolution-test"
        val settings = AppSettings(composeRule.activity, name)
        assertEquals(PhotoResolution(), settings.photoResolution.value)
        settings.setPhotoResolution(PhotoResolution(reduce = false, size = PhotoSize.LARGE))
        val recreated = AppSettings(composeRule.activity, name)
        assertEquals(PhotoResolution(false, PhotoSize.LARGE), recreated.photoResolution.value)
        assertEquals(null, recreated.photoResolution.value.maxDimension)
        recreated.setPhotoResolution(PhotoResolution())
    }

    @Test
    fun settingsChooseThePhotoSizeAndTurnReductionOff() {
        importNote()
        composeRule.onNodeWithTag("back-to-note-list").performClick()
        composeRule.onNodeWithTag("note-list-menu").performClick()
        composeRule.onNodeWithTag("settings").performClick()

        composeRule.onNodeWithTag("toggle-reduce-photo-resolution").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("photo-size-medium").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag(
            "photo-size-summary"
        ).assertTextContains("2,560", substring = true)

        composeRule.onNodeWithTag("photo-size-small").performScrollTo().performClick()
        assertEquals(
            PhotoResolution(size = PhotoSize.SMALL),
            application.component.settings.photoResolution.value
        )
        composeRule.onNodeWithTag(
            "photo-size-summary"
        ).assertTextContains("1,920", substring = true)

        composeRule.onNodeWithTag("toggle-reduce-photo-resolution").performScrollTo().performClick()
            .assertIsOff()
        waitUntilGone("photo-size")
        assertEquals(
            PhotoResolution(reduce = false, size = PhotoSize.SMALL),
            application.component.settings.photoResolution.value
        )
        composeRule.onNodeWithTag("close-settings").performClick()
    }

    @Test
    fun settingsChooseWebpAndWhichImagesAreShrunk() {
        importNote()
        composeRule.onNodeWithTag("back-to-note-list").performClick()
        composeRule.onNodeWithTag("note-list-menu").performClick()
        composeRule.onNodeWithTag("settings").performClick()
        val settings = application.component.settings

        composeRule.onNodeWithTag("toggle-save-as-webp").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("toggle-shrink-camera").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("toggle-shrink-chosen").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("toggle-shrink-annotated").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("image-shrink-saved").performScrollTo()
            .assertTextContains("79", substring = true)

        composeRule.onNodeWithTag("toggle-save-as-webp").performScrollTo().performClick()
            .assertIsOff()
        assertEquals(ImageShrinkSettings(saveAsWebp = false), settings.imageShrink.value)
        composeRule.onNodeWithTag("image-shrink-saved").assertTextContains("70", substring = true)

        composeRule.onNodeWithTag("toggle-shrink-chosen").performScrollTo().performClick()
            .assertIsOn()
        composeRule.onNodeWithTag("toggle-shrink-camera").performScrollTo().performClick()
            .assertIsOff()
        assertEquals(
            ImageShrinkSettings(saveAsWebp = false, cameraPhotos = false, chosenImages = true),
            settings.imageShrink.value
        )
        assertEquals(ImageShrinking.NONE, settings.imageShrinkingFor(ImageOrigin.CAMERA))
        assertEquals(
            ImageShrinking(PhotoSize.MEDIUM.maxDimension, webp = false),
            settings.imageShrinkingFor(ImageOrigin.CHOSEN)
        )

        composeRule.onNodeWithTag("toggle-shrink-chosen").performScrollTo().performClick()
        composeRule.onNodeWithTag("toggle-shrink-annotated").performScrollTo().performClick()
        waitUntilGone("image-shrink-saved")
        composeRule.onNodeWithTag("image-shrink-estimate-text").performScrollTo()
            .assertTextContains("exactly as they are", substring = true)
        composeRule.onNodeWithTag("close-settings").performClick()
    }

    @Test
    fun imageShrinkSettingsPersist() {
        val name = "image-shrink-test"
        val settings = AppSettings(composeRule.activity, name)
        assertEquals(ImageShrinkSettings(), settings.imageShrink.value)
        val changed = ImageShrinkSettings(
            saveAsWebp = false,
            cameraPhotos = false,
            chosenImages = true,
            annotatedImages = false
        )
        settings.setImageShrink(changed)
        assertEquals(changed, AppSettings(composeRule.activity, name).imageShrink.value)
        settings.setImageShrink(ImageShrinkSettings())
    }

    @Test
    fun toolbarCameraAttachesAReducedWebpPhotoAtTheCursor() {
        assumeTrue(
            composeRule.activity.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_CAMERA_ANY
            )
        )
        respondToCamera { output ->
            output.writeBytes(writePhoto(4000, 3000, ExifInterface.ORIENTATION_NORMAL).readBytes())
        }
        importNote()
        enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click(), replaceText("Look: "))
        composeRule.runOnUiThread {
            composeRule.activity.findViewById<org.qownnotes.mobile.markdown.MarkdownEditText>(
                R.id.markdown_editor
            ).setSelection(6)
        }

        composeRule.onNodeWithTag("take-photo").performScrollTo().performClick()

        composeRule.waitUntil(10_000) { application.fakeBackend.uploadedImages.isNotEmpty() }
        val (name, content) = application.fakeBackend.uploadedImages.single()
        assertEquals("image/webp", application.fakeBackend.uploadedMediaTypes.single())
        assertTrue(name.endsWith(".webp"))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(content, 0, content.size, bounds)
        assertEquals("image/webp", bounds.outMimeType)
        assertEquals(2560, bounds.outWidth)
        assertEquals(1920, bounds.outHeight)
        awaitEditorText("Look: ![photo](media/$name)")
        waitUntilGone("editor-attachment-progress")
        // The camera's private file is not kept once the photo is attached.
        assertTrue(
            File(application.filesDir, "camera").listFiles().orEmpty().none {
                it.name.startsWith("note-")
            }
        )
    }

    @Test
    fun cancelledCameraLeavesTheNoteUnchanged() {
        assumeTrue(
            composeRule.activity.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_CAMERA_ANY
            )
        )
        respondToCamera(resultCode = Activity.RESULT_CANCELED) {}
        importNote()
        enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click(), replaceText("Unchanged"))

        composeRule.onNodeWithTag("take-photo").performScrollTo().performClick()

        composeRule.waitForIdle()
        assertTrue(application.fakeBackend.uploadedImages.isEmpty())
        awaitEditorText("![", present = false)
        assertTrue(
            File(application.filesDir, "camera").listFiles().orEmpty().none {
                it.name.startsWith("note-")
            }
        )
    }

    private fun respondToCamera(resultCode: Int = Activity.RESULT_OK, write: (File) -> Unit) {
        monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != MediaStore.ACTION_IMAGE_CAPTURE) return null
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
                if (resultCode == Activity.RESULT_OK) {
                    val photo = File(directory, "camera-output.jpg").also(write)
                    application.contentResolver.openOutputStream(uri)!!.use {
                        it.write(photo.readBytes())
                    }
                }
                return Instrumentation.ActivityResult(resultCode, null)
            }
        }.also(instrumentation::addMonitor)
    }

    /** A JPEG with a red marker in its top-left corner and the given Exif orientation. */
    private fun writePhoto(width: Int, height: Int, orientation: Int): File {
        val file = File(directory, "photo-${width}x$height-$orientation-${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        android.graphics.Canvas(bitmap).drawRect(
            0f,
            0f,
            width / 8f,
            height / 8f,
            android.graphics.Paint().apply { color = Color.RED }
        )
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, CAPTURE_TIME)
            saveAttributes()
        }
        return file
    }

    private fun bounds(file: File) = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.path, this)
    }

    private fun importNote() {
        val account = SingleSignOnAccount(
            "alice",
            "alice",
            "test-token",
            "https://cloud.example",
            "nextcloud"
        )
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(listOf(RemoteNote(100, "etag-1", "Photo note", "", "", 10)), "etag-1", 10)
        )
        composeRule.onNodeWithTag("add-account").performClick()
        waitForTag("note-list-menu")
        composeRule.onNodeWithText("Photo note").performClick()
        waitForTag("edit-note")
    }

    private fun enterEditMode() {
        composeRule.onNodeWithTag("edit-note").performClick()
        waitForTag("markdown-editor")
    }

    private fun awaitEditorText(substring: String, present: Boolean = true) =
        composeRule.waitUntil(10_000) {
            composeRule.runOnIdle {
                composeRule.activity.findViewById<TextView>(R.id.markdown_editor)
                    ?.text
                    ?.contains(substring) == present
            }
        }

    private fun waitForTag(tag: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(tag: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }

    private companion object {
        const val CAPTURE_TIME = "2026:11:12 09:30:00"
    }
}
