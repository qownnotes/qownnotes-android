package org.qownnotes.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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

    @Test
    fun largeEditorStaysAtViewportHeightAndScrollsQuickly() {
        showEditor()

        assertTrue("editor must expose a large internal range", maximumScroll.value > 2_000)

        composeRule.onNodeWithTag("editor-host").performTouchInput { swipeUp() }
        composeRule.waitUntil(timeoutMillis = 10_000) { scrollState.value > 1_000 }

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
                    AndroidView(
                        factory = { context ->
                            MarkdownEditText(context).also { view ->
                                editor.value = view
                                view.onVerticalScrollChanged = { value, range ->
                                    scrollState.value = value
                                    maximumScroll.value = range
                                }
                                view.setText(
                                    (1..300).joinToString("\n") { "Long editor line $it" }
                                )
                            }
                        },
                        modifier = Modifier.size(width = 320.dp, height = 480.dp)
                            .testTag("editor-host")
                    )
                    EditorFastScroller(
                        scrollValue = scrollState.value,
                        scrollRange = maximumScroll.value,
                        onScrollTo = { editor.value?.scrollVerticallyTo(it) },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { maximumScroll.value > 2_000 }
    }
}
