package org.qownnotes.mobile

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.markdown.NoteImage

@RunWith(AndroidJUnit4::class)
class NoteImageViewerTest {
    @get:Rule val composeRule = createComposeRule()

    private var dismissed = false

    private fun showViewer(description: String = "Whiteboard") {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }
        composeRule.setContent {
            MaterialTheme {
                if (!dismissed) {
                    NoteImageViewer(NoteImage(bitmap, description)) { dismissed = true }
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
    fun swipingAFittedImageDownCloses() {
        showViewer(description = "")
        composeRule.onNodeWithContentDescription("Image").assertIsDisplayed()
        composeRule.onNodeWithTag("image-viewer-image").performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) { dismissed }
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
