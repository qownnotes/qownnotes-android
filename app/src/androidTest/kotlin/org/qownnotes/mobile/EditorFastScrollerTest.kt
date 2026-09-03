package org.qownnotes.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.qownnotes.mobile.markdown.MarkdownEditText

class EditorFastScrollerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun draggingTheRailMovesThroughMostOfALongEditor() {
        val editor = mutableStateOf<MarkdownEditText?>(null)
        val scrollY = mutableIntStateOf(0)
        val scrollRange = mutableIntStateOf(0)
        val viewportHeight = mutableIntStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 480.dp)) {
                    AndroidView(
                        factory = { context ->
                            MarkdownEditText(context).also { view ->
                                fun updateMetrics() {
                                    scrollY.intValue = view.scrollY
                                    scrollRange.intValue = editorMaximumScroll(view)
                                    viewportHeight.intValue = view.height
                                }
                                editor.value = view
                                view.setText((1..300).joinToString("\n") { "Long editor line $it" })
                                view.setOnScrollChangeListener { _, _, _, _, _ -> updateMetrics() }
                                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                    updateMetrics()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    EditorFastScroller(
                        editor = editor.value,
                        scrollY = scrollY.intValue,
                        scrollRange = scrollRange.intValue,
                        viewportHeight = viewportHeight.intValue,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { scrollRange.intValue > 2_000 }
        composeRule.onNodeWithTag("editor-fast-scroll").performTouchInput { swipeDown() }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            editor.value!!.scrollY > scrollRange.intValue / 2
        }
        assertTrue(editor.value!!.scrollY > scrollRange.intValue / 2)
    }
}
