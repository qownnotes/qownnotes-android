package org.qownnotes.mobile

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.ImageShrinking
import org.qownnotes.mobile.markdown.NoteImage

@RunWith(AndroidJUnit4::class)
class NoteImageViewerTest {
    @get:Rule val composeRule = createComposeRule()

    private var dismissed = false

    private fun showViewer(
        description: String = "Whiteboard",
        onSaveAnnotation: (suspend (Bitmap, Boolean) -> Unit)? = null
    ) {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        composeRule.setContent {
            MaterialTheme {
                if (!dismissed) {
                    NoteImageViewer(NoteImage(bitmap, description), onSaveAnnotation) {
                        dismissed = true
                    }
                }
            }
        }
        composeRule.onNodeWithTag("image-viewer-image").assertIsDisplayed()
    }

    @Test
    fun showsDescriptionZoomsWithDoubleTapAndPinchAndFitsAgain() {
        showViewer()
        composeRule.onNodeWithTag("image-viewer-title").assertTextEquals("Whiteboard")
        composeRule.onNodeWithContentDescription("Whiteboard").assertIsDisplayed()
        assertGone("image-viewer-zoom")
        assertGone("image-viewer-fit")
        // A single image has nowhere to move to.
        assertGone("image-viewer-position")

        composeRule.onNodeWithTag("image-viewer-image").performTouchInput { doubleClick(center) }
        waitForTag("image-viewer-zoom")
        composeRule.waitUntil(5_000) { zoomText() == "250%" }

        composeRule.onNodeWithTag("image-viewer-fit").performClick()
        waitUntilGone("image-viewer-zoom")

        composeRule.onNodeWithTag("image-viewer-image").performTouchInput {
            pinch(
                center - Offset(20f, 0f),
                center - Offset(250f, 0f),
                center + Offset(20f, 0f),
                center + Offset(250f, 0f)
            )
        }
        waitForTag("image-viewer-zoom")
        assertFalse(dismissed)

        composeRule.onNodeWithTag("image-viewer-close").performClick()
        composeRule.waitUntil(5_000) { dismissed }
    }

    @Test
    fun swipingSidewaysMovesBetweenTheNotesImagesUnlessZoomed() {
        val gallery = NoteImage.gallery(
            listOf("First", "Second", "Third").mapIndexed { index, description ->
                val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(listOf(Color.RED, Color.GREEN, Color.BLUE)[index])
                NoteImage(bitmap, description, index)
            }
        )
        val shown = mutableStateOf(gallery[1])
        composeRule.setContent {
            MaterialTheme {
                if (!dismissed) {
                    val image = shown.value
                    NoteImageViewer(
                        image,
                        shrinkActions = NoteImageShrinkActions(
                            defaults = ImageShrinking(2560, webp = true),
                            loadOriginal = { error("Not needed") },
                            shrink = { _, _ -> null },
                            save = { _, _ -> }
                        ).takeIf { image.description != "Third" },
                        onImageChange = { shown.value = it }
                    ) { dismissed = true }
                }
            }
        }
        composeRule.onNodeWithTag("image-viewer-title").assertTextEquals("Second")
        composeRule.onNodeWithTag("image-viewer-position")
            .assertContentDescriptionEquals("Image 2 of 3")

        composeRule.onNodeWithTag("image-viewer-image").performTouchInput { swipeLeft() }
        composeRule.waitUntil(5_000) { shown.value === gallery[2] }
        composeRule.onNodeWithTag("image-viewer-title").assertTextEquals("Third")
        composeRule.onNodeWithTag("image-viewer-position")
            .assertContentDescriptionEquals("Image 3 of 3")
        // Actions follow the shown image; this one cannot be shrunk.
        assertGone("image-viewer-shrink")

        repeat(2) {
            composeRule.onNodeWithTag("image-viewer-image").performTouchInput { swipeRight() }
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(5_000) { shown.value === gallery[0] }
        composeRule.onNodeWithTag("image-viewer-title").assertTextEquals("First")
        composeRule.onNodeWithTag("image-viewer-shrink").assertIsDisplayed()
        // There is nothing before the first image.
        composeRule.onNodeWithTag("image-viewer-image").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("image-viewer-title").assertTextEquals("First")

        // A zoomed image pans instead of moving to the next one.
        composeRule.onNodeWithTag("image-viewer-image").performTouchInput { doubleClick(center) }
        waitForTag("image-viewer-zoom")
        composeRule.onNodeWithTag("image-viewer-image").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertTrue(shown.value === gallery[0])

        // Back at fit, swiping down still closes the viewer.
        composeRule.onNodeWithTag("image-viewer-fit").performClick()
        waitUntilGone("image-viewer-zoom")
        composeRule.onNodeWithTag("image-viewer-image").performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) { dismissed }
    }

    @Test
    fun swipingAFittedImageDownCloses() {
        showViewer(description = "")
        composeRule.onNodeWithContentDescription("Image").assertIsDisplayed()
        composeRule.onNodeWithTag("image-viewer-image").performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) { dismissed }
    }

    @Test
    fun copyButtonPutsTheImageOnTheClipboardAsAPng() {
        showViewer()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        composeRule.runOnUiThread { clipboard.clearPrimaryClip() }

        composeRule.onNodeWithTag("image-viewer-copy").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.runOnUiThread { clipboard.primaryClip?.getItemAt(0)?.uri } != null
        }

        val clip = composeRule.runOnUiThread { clipboard.primaryClip!! }
        assertEquals("Whiteboard", clip.description.label)
        assertTrue(clip.description.hasMimeType("image/png"))
        val uri = clip.getItemAt(0).uri
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        val pasted = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
        assertEquals(300, pasted.width)
        assertEquals(200, pasted.height)
        assertFalse(dismissed)

        // A second copy replaces the first, so copied images do not pile up in the cache.
        composeRule.onNodeWithTag("image-viewer-copy").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.runOnUiThread { clipboard.primaryClip?.getItemAt(0)?.uri } != uri
        }
        assertEquals(1, File(context.cacheDir, "copied-images").listFiles()!!.size)
    }

    @Test
    fun annotatingDrawsUndoesRedoesAndSavesAsANewImage() {
        var saved: Pair<Bitmap, Boolean>? = null
        showViewer { annotated, replace -> saved = annotated to replace }
        composeRule.onNodeWithTag("image-viewer-annotate").performClick()
        composeRule.onNodeWithTag("image-annotation-done").assertIsNotEnabled()
        composeRule.onNodeWithTag("image-annotation-tool-pen").assertIsOn()
        // Two fingers zoom rather than draw; zooming back out keeps the screen-to-image mapping.
        composeRule.onNodeWithTag("image-annotation-canvas").performTouchInput {
            pinch(
                center - Offset(20f, 0f),
                center - Offset(250f, 0f),
                center + Offset(20f, 0f),
                center + Offset(250f, 0f)
            )
        }
        composeRule.onNodeWithTag("image-annotation-done").assertIsNotEnabled()
        composeRule.onNodeWithTag("image-annotation-canvas").performTouchInput {
            pinch(
                center - Offset(250f, 0f),
                center - Offset(20f, 0f),
                center + Offset(250f, 0f),
                center + Offset(20f, 0f)
            )
        }
        composeRule.onNodeWithTag("image-annotation-done").assertIsNotEnabled()

        // The image fills the screen width, so its middle row runs across the screen's middle.
        drawAcrossTheMiddle()
        composeRule.onNodeWithTag("image-annotation-done").assertIsEnabled()
        composeRule.onNodeWithTag("image-annotation-undo").performClick()
        composeRule.onNodeWithTag("image-annotation-done").assertIsNotEnabled()
        composeRule.onNodeWithTag("image-annotation-redo").performClick()

        composeRule.onNodeWithTag("image-annotation-done").performClick()
        composeRule.onNodeWithTag("image-annotation-add").performClick()
        composeRule.waitUntil(5_000) { dismissed }

        val (annotated, replace) = saved!!
        assertFalse(replace)
        assertEquals(300, annotated.width)
        val marked = annotated.getPixel(150, 100)
        assertTrue(Color.red(marked) > 200 && Color.green(marked) < 100)
        // Away from the stroke the image is unchanged.
        assertEquals(Color.GREEN, annotated.getPixel(150, 20))
    }

    @Test
    fun annotationsCanBeErasedAndClosingAsksBeforeDiscardingThem() {
        showViewer()
        composeRule.onNodeWithTag("image-viewer-annotate").performClick()
        drawAcrossTheMiddle()
        composeRule.onNodeWithTag("image-annotation-tool-eraser").performClick()
        composeRule.onNodeWithTag("image-annotation-canvas").performTouchInput { click(center) }
        composeRule.onNodeWithTag("image-annotation-done").assertIsNotEnabled()
        composeRule.onNodeWithTag("image-annotation-undo").performClick()

        composeRule.onNodeWithTag("image-annotation-close").performClick()
        composeRule.onNodeWithTag("image-annotation-discard").performClick()
        waitUntilGone("image-annotation-editor")
        composeRule.onNodeWithTag("image-viewer-image").assertIsDisplayed()
        assertFalse(dismissed)
    }

    @Test
    fun textToolAddsALabelAndImagesOutsideEditableNotesCanOnlyBeCopied() {
        showViewer()
        composeRule.onNodeWithTag("image-viewer-annotate").performClick()
        composeRule.onNodeWithTag("image-annotation-tool-text").performClick()
        composeRule.onNodeWithTag("image-annotation-canvas").performTouchInput { click(center) }
        composeRule.onNodeWithTag("image-annotation-text-field").performTextInput("Look here")
        composeRule.onNodeWithTag("image-annotation-text-done").performClick()
        composeRule.onNodeWithTag("image-annotation-done").assertIsEnabled().performClick()

        composeRule.onNodeWithTag("image-annotation-add").assertIsNotEnabled()
        composeRule.onNodeWithTag("image-annotation-replace").assertIsNotEnabled()
        composeRule.onNodeWithTag("image-annotation-copy").assertIsEnabled()
    }

    private fun drawAcrossTheMiddle() =
        composeRule.onNodeWithTag("image-annotation-canvas").performTouchInput {
            swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY), 300)
        }

    private fun zoomText(): String? = composeRule.onAllNodesWithTag("image-viewer-zoom")
        .fetchSemanticsNodes().firstOrNull()?.config
        ?.getOrElseNullable(SemanticsProperties.Text) { null }
        ?.joinToString { it.text }

    private fun waitForTag(tag: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(tag: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }

    private fun assertGone(tag: String) = composeRule.onNodeWithTag(tag).assertDoesNotExist()
}
