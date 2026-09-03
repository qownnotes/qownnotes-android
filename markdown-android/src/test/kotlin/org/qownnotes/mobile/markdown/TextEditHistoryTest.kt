package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditHistoryTest {
    @Test
    fun `nothing can be undone before anything changes`() {
        val history = TextEditHistory()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo())
        assertNull(history.redo())
    }

    @Test
    fun `a burst of typing is one undo step`() {
        val history = TextEditHistory()

        history.type(0, "a")
        history.type(1, "b")
        history.type(2, "c")

        assertEquals(TextEdit(0, "", "abc"), history.undo())
        assertFalse(history.canUndo)
    }

    @Test
    fun `an input method rewriting its composing region stays one step`() {
        val history = TextEditHistory()

        history.record(TextEdit(5, "", "wor"))
        history.record(TextEdit(5, "wor", "word"))

        assertEquals(TextEdit(5, "", "word"), history.undo())
    }

    @Test
    fun `correcting what was just typed stays in the same step`() {
        val history = TextEditHistory()

        history.record(TextEdit(4, "", "abc"))
        history.record(TextEdit(6, "c", ""))
        history.type(6, "d")

        assertEquals(TextEdit(4, "", "abd"), history.undo())
    }

    @Test
    fun `typing away everything it typed leaves no step behind`() {
        val history = TextEditHistory()

        history.type(3, "x")
        history.record(TextEdit(3, "x", ""))

        assertFalse(history.canUndo)
    }

    @Test
    fun `a line break ends the group`() {
        val history = TextEditHistory()

        history.type(0, "first")
        history.type(5, "\n")
        history.type(6, "second")

        assertEquals(TextEdit(6, "", "second"), history.undo())
        assertEquals(TextEdit(5, "", "\n"), history.undo())
        assertEquals(TextEdit(0, "", "first"), history.undo())
    }

    @Test
    fun `consecutive backspaces are one step`() {
        val history = TextEditHistory()

        history.record(TextEdit(2, "c", ""))
        history.record(TextEdit(1, "b", ""))
        history.record(TextEdit(0, "a", ""))

        assertEquals(TextEdit(0, "abc", ""), history.undo())
    }

    @Test
    fun `consecutive forward deletions are one step`() {
        val history = TextEditHistory()

        history.record(TextEdit(0, "a", ""))
        history.record(TextEdit(0, "b", ""))

        assertEquals(TextEdit(0, "ab", ""), history.undo())
    }

    @Test
    fun `deleting a line break ends the group`() {
        val history = TextEditHistory()

        history.record(TextEdit(6, "d", ""))
        history.record(TextEdit(5, "\n", ""))

        assertEquals(TextEdit(5, "\n", ""), history.undo())
        assertEquals(TextEdit(6, "d", ""), history.undo())
    }

    @Test
    fun `typing somewhere else starts a new step`() {
        val history = TextEditHistory()

        history.type(0, "here")
        history.type(40, "there")

        assertEquals(TextEdit(40, "", "there"), history.undo())
        assertEquals(TextEdit(0, "", "here"), history.undo())
    }

    @Test
    fun `a formatting action is its own step`() {
        val history = TextEditHistory()

        history.type(0, "bold")
        history.breakGroup()
        history.record(TextEdit(0, "bold", "**bold**"))
        history.breakGroup()
        history.type(8, "!")

        assertEquals(TextEdit(8, "", "!"), history.undo())
        assertEquals(TextEdit(0, "bold", "**bold**"), history.undo())
        assertEquals(TextEdit(0, "", "bold"), history.undo())
    }

    @Test
    fun `undoing and redoing walks the same steps in both directions`() {
        val history = TextEditHistory()
        history.type(0, "one")
        history.breakGroup()
        history.type(3, "two")

        assertEquals(TextEdit(3, "", "two"), history.undo())
        assertEquals(TextEdit(0, "", "one"), history.undo())
        assertTrue(history.canRedo)
        assertEquals(TextEdit(0, "", "one"), history.redo())
        assertEquals(TextEdit(3, "", "two"), history.redo())
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)
    }

    @Test
    fun `changing anything after an undo drops the way forward`() {
        val history = TextEditHistory()
        history.type(0, "one")
        history.undo()

        history.type(0, "other")

        assertFalse(history.canRedo)
        assertEquals(TextEdit(0, "", "other"), history.undo())
    }

    @Test
    fun `typing after an undo does not join the step that was restored`() {
        val history = TextEditHistory()
        history.type(0, "one")
        history.breakGroup()
        history.type(3, "two")
        history.undo()

        history.type(3, "!")

        assertEquals(TextEdit(3, "", "!"), history.undo())
        assertEquals(TextEdit(0, "", "one"), history.undo())
    }

    @Test
    fun `the oldest steps are dropped once the history is full`() {
        val history = TextEditHistory(maxEntries = 2)

        history.type(0, "a")
        history.breakGroup()
        history.type(1, "b")
        history.breakGroup()
        history.type(2, "c")

        assertEquals(TextEdit(2, "", "c"), history.undo())
        assertEquals(TextEdit(1, "", "b"), history.undo())
        assertFalse(history.canUndo)
    }

    @Test
    fun `clearing forgets both directions`() {
        val history = TextEditHistory()
        history.type(0, "a")
        history.undo()

        history.clear()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    private fun TextEditHistory.type(start: Int, text: String) = record(TextEdit(start, "", text))
}
