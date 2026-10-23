package org.qownnotes.mobile.markdown

import android.text.Spanned
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatTextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.ext.tasklist.TaskListSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderedTaskSourcesInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * Each of these notes has task-like lines that are not rendered as tasks. They must neither
     * shift which line a checkbox toggles nor hide the handles of the real list.
     */
    @Test
    fun checkboxesAndHandlesFollowTheRenderedTasks() {
        val notes = listOf(
            "- [ ]\n- [ ] alpha\n- [ ] beta",
            "- [ ] \n- [ ] alpha\n- [ ] beta",
            "- [ ]text\n- [ ] alpha\n- [ ] beta",
            "-     [ ] code\n\n- [ ] alpha\n- [ ] beta",
            "<!--\n- [ ] hidden\n-->\n- [ ] alpha\n- [ ] beta",
            "<div>\n- [ ] hidden\n</div>\n\n- [ ] alpha\n- [ ] beta",
            "Text\n\n    - [ ] code\n\n- [ ] alpha\n- [ ] beta",
            "- x\n  - [ ] alpha\n    ```\n    - [ ] code\n    ```\n  - [ ] beta",
            "---\ntitle: x\n---\n- [ ] [[alpha]]\n- [ ] **beta**",
            "> - [ ] quoted\n\n- [ ] alpha\n  - [x] nested\n- [ ] beta"
        )
        notes.forEach { markdown ->
            lateinit var view: AppCompatTextView
            val toggled = mutableListOf<Int>()
            instrumentation.runOnMainSync {
                view = AppCompatTextView(
                    ContextThemeWrapper(
                        instrumentation.targetContext,
                        androidx.appcompat.R.style.Theme_AppCompat
                    )
                )
                MarkdownRenderer(view.context).render(
                    view = view,
                    markdown = markdown,
                    onTaskToggle = { toggled += it },
                    onTaskMove = { _, _ -> }
                )
            }
            val text = view.text as Spanned
            val toggles = text.getSpans(0, text.length, TaskToggleSpan::class.java)
            assertEquals(
                markdown,
                text.getSpans(0, text.length, TaskListSpan::class.java).size,
                toggles.size
            )
            toggles.forEach { span ->
                val start = text.getSpanStart(span)
                val end = text.indexOf('\n', start).takeIf { it >= 0 } ?: text.length
                val label = text.substring(start, end).trim()
                val changed = toggleTaskListItem(markdown, span.index)!!.lines()
                    .zip(markdown.lines())
                    .single { (after, before) -> after != before }
                    .second
                assertTrue("$label toggles '$changed' in $markdown", changed.contains(label))
            }
            assertTrue("handles in $markdown", view.paddingEnd > 0)
        }
    }
}
