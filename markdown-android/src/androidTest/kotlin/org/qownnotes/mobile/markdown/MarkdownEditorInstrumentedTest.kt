package org.qownnotes.mobile.markdown

import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun existingSourceIsHighlightedWhenBindingAttaches() {
        lateinit var view: MarkdownEditText
        lateinit var binding: MarkdownEditorBinding
        var sourceChanges = 0

        instrumentation.runOnMainSync {
            view = editor()
            view.setText("# Existing heading")
            binding = MarkdownEditorBinding(view.context, view) { sourceChanges++ }
        }

        instrumentation.runOnMainSync {
            val syntax = view.text!!.getSpans(
                0,
                view.length(),
                SupplementalSyntaxSpan::class.java
            ).map { it.syntax }
            assertTrue(MarkdownSyntax.HEADING in syntax)
            assertEquals("attaching the binding is not a source edit", 0, sourceChanges)
            assertFalse("initial source must not be undoable", binding.canUndo)
            binding.close()
        }
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

    @Test
    fun undoStepsBackOverTypingAndRedoBringsItBack() {
        lateinit var view: MarkdownEditText
        lateinit var binding: MarkdownEditorBinding

        instrumentation.runOnMainSync {
            view = editor()
            view.setText("note")
            binding = MarkdownEditorBinding(view.context, view) {}
            view.setSelection(4)
            "!?".forEach { view.text!!.append(it) }
        }

        instrumentation.runOnMainSync {
            assertEquals("note!?", view.text.toString())
            assertTrue("typing must be undoable", binding.canUndo)

            assertTrue(binding.undo())
            assertEquals("note", view.text.toString())
            assertEquals("the caret belongs where the undone text was", 4, view.selectionStart)
            assertFalse("one burst of typing is one step", binding.canUndo)

            assertTrue(binding.redo())
            assertEquals("note!?", view.text.toString())
            assertEquals(6, view.selectionStart)
            assertFalse(binding.canRedo)
            binding.close()
        }
    }

    @Test
    fun aFormattingActionIsASingleUndoStep() {
        lateinit var view: MarkdownEditText
        lateinit var binding: MarkdownEditorBinding

        instrumentation.runOnMainSync {
            view = editor()
            view.setText("alpha beta gamma")
            binding = MarkdownEditorBinding(view.context, view) {}
            view.setSelection(6, 10)
            view.applyFormat(MarkdownFormatAction.BOLD)
        }

        instrumentation.runOnMainSync {
            assertEquals("alpha **beta** gamma", view.text.toString())

            assertTrue(binding.undo())

            assertEquals("alpha beta gamma", view.text.toString())
            assertFalse("formatting must not leave a half-undone step", binding.canUndo)
            binding.close()
        }
    }

    @Test
    fun historyAvailabilityIsReportedToTheToolbar() {
        val reported = mutableListOf<Pair<Boolean, Boolean>>()
        lateinit var view: MarkdownEditText
        lateinit var binding: MarkdownEditorBinding

        instrumentation.runOnMainSync {
            view = editor()
            binding = MarkdownEditorBinding(
                view.context,
                view,
                onHistoryChanged = { canUndo, canRedo -> reported += canUndo to canRedo }
            ) {}
            view.text!!.append("x")
            binding.undo()
        }

        instrumentation.runOnMainSync {
            assertEquals(
                listOf(false to false, true to false, false to true),
                reported
            )
            binding.close()
        }
    }

    /** A binding builds Markwon, which resolves its styles from AppCompat theme attributes. */
    private fun editor() = MarkdownEditText(
        ContextThemeWrapper(
            instrumentation.targetContext,
            androidx.appcompat.R.style.Theme_AppCompat
        )
    )

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
