package org.qownnotes.mobile.markdown

import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownEditorInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * Regression test for the editor that showed no cursor and opened no keyboard. A host theme
     * without the AppCompat widget styles leaves `Widget.AppCompat.EditText` unapplied, so the
     * interactive flags must come from the view itself.
     */
    @Test
    fun editorIsFocusableInTouchModeWithoutAnAppCompatTheme() {
        val frameworkThemed = ContextThemeWrapper(
            instrumentation.targetContext,
            android.R.style.Theme_Material_Light_NoActionBar
        )
        lateinit var view: MarkdownEditText

        instrumentation.runOnMainSync { view = MarkdownEditText(frameworkThemed) }

        assertTrue("editor must be focusable", view.isFocusable)
        assertTrue("editor must be focusable in touch mode", view.isFocusableInTouchMode)
        assertTrue("editor must be clickable", view.isClickable)
        assertTrue("editor must show the keyboard on focus", view.showSoftInputOnFocus)
    }

    @Test
    fun formattingReplacesOnlyTheChangedRange() {
        lateinit var view: MarkdownEditText
        val changes = mutableListOf<Triple<Int, Int, Int>>()

        instrumentation.runOnMainSync {
            view = MarkdownEditText(instrumentation.targetContext)
            view.setText("alpha beta gamma")
            view.setSelection(6, 10)
            view.addTextChangedListener(recordingWatcher(changes))
            view.applyFormat(MarkdownFormatAction.BOLD)
        }

        instrumentation.runOnMainSync {
            assertEquals("alpha **beta** gamma", view.text.toString())
            assertEquals(8, view.selectionStart)
            assertEquals(12, view.selectionEnd)
            // A whole-document replacement would report start 0 and before 16, destroying undo
            // history and any in-progress input-method composition.
            assertEquals(listOf(Triple(6, 4, 8)), changes)
        }
    }

    @Test
    fun formattingKeepsTheCaretWhenNothingIsSelected() {
        lateinit var view: MarkdownEditText

        instrumentation.runOnMainSync {
            view = MarkdownEditText(instrumentation.targetContext)
            view.setText("first\nsecond")
            view.setSelection(7)
            view.applyFormat(MarkdownFormatAction.BULLET)
        }

        instrumentation.runOnMainSync {
            assertEquals("first\n- second", view.text.toString())
            assertEquals(9, view.selectionStart)
            assertEquals(9, view.selectionEnd)
        }
    }

    private fun recordingWatcher(changes: MutableList<Triple<Int, Int, Int>>) =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                changes += Triple(start, before, count)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        }
}
