package org.qownnotes.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.qownnotes.mobile.markdown.MarkdownEditText

class EditorFastScrollerTest {
    @get:Rule val composeRule = createComposeRule()

    private val scrollState = mutableStateOf(0)
    private val maximumScroll = mutableStateOf(0)

    /**
     * A flick with the thumb, which is what reading through a long note actually uses. An
     * `EditText` scrolls its own text without any fling, so this only travels far when the editor
     * is hosted in a scrolling container.
     */
    @Test
    fun flingingTheEditorCoastsThroughTheNote() {
        showEditor()

        composeRule.onNodeWithTag("editor-host").performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        // A drag of roughly one viewport that only moved the text by a few lines has no fling.
        assertTrue(
            "expected the flick to coast, but it stopped at ${scrollState.value}",
            scrollState.value > 1_000
        )
    }

    @Test
    fun draggingTheRailMovesThroughMostOfTheNote() {
        showEditor()

        composeRule.onNodeWithTag("editor-fast-scroll").performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertTrue(
            "expected the rail to move most of the note, but it reached ${scrollState.value}",
            scrollState.value > maximumScroll.value / 2
        )
    }

    private fun showEditor() {
        val editor = mutableStateOf<MarkdownEditText?>(null)
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 480.dp)) {
                    val editorScrollState = rememberScrollState()
                    scrollState.value = editorScrollState.value
                    maximumScroll.value = editorScrollState.maxValue
                    Column(
                        modifier = Modifier.size(width = 320.dp, height = 480.dp)
                            .verticalScroll(editorScrollState)
                            .testTag("editor-host")
                    ) {
                        AndroidView(
                            factory = { context ->
                                MarkdownEditText(context).also { view ->
                                    editor.value = view
                                    view.setText(
                                        (1..300).joinToString("\n") { "Long editor line $it" }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    EditorFastScroller(
                        scrollState = editorScrollState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { maximumScroll.value > 2_000 }
    }
}
