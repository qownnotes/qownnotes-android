package org.qownnotes.mobile.markdown

/**
 * One reversible change to the editor text: [before] occupied the text from [start] onwards until
 * [after] replaced it.
 */
internal data class TextEdit(val start: Int, val before: String, val after: String)

/**
 * Undo and redo history for the Markdown source editor.
 *
 * The framework `EditText` keeps an undo buffer of its own, but it can only be reached with a
 * hardware keyboard, so the editor carries this history and drives it from the toolbar instead.
 *
 * Every entry is a range replacement, which is both what the editor's `TextWatcher` reports and
 * what replaying a change in either direction needs. Consecutive changes are grouped so that one
 * undo steps back over a burst of typing rather than over a single character:
 *
 * - A change that only rewrites text the previous change produced is merged into it. That covers
 *   continued typing, an input method rewriting its composing region, and a correction made inside
 *   what was just typed.
 * - Consecutive deletions are merged, whether the caret stays put or moves backwards.
 * - A line break always ends a group, so undo steps through a note line by line.
 * - [breakGroup] ends a group explicitly. The editor calls it before a formatting action so that
 *   one toolbar tap is one undo step.
 *
 * The rules are deliberately structural rather than time-based, so they behave identically on a
 * fast and a slow device and can be tested without a clock.
 */
internal class TextEditHistory(private val maxEntries: Int = MAX_ENTRIES) {
    private val undoable = ArrayDeque<TextEdit>()
    private val redoable = ArrayDeque<TextEdit>()
    private var groupOpen = false

    val canUndo: Boolean get() = undoable.isNotEmpty()

    val canRedo: Boolean get() = redoable.isNotEmpty()

    /** Records a change that has already been applied to the text. */
    fun record(edit: TextEdit) {
        if (edit.before.isEmpty() && edit.after.isEmpty()) return
        // Anything the writer does after stepping back invalidates the way forward.
        redoable.clear()
        val merged = if (groupOpen) undoable.lastOrNull()?.let { mergeEdits(it, edit) } else null
        when {
            merged == null -> {
                undoable.addLast(edit)
                while (undoable.size > maxEntries) undoable.removeFirst()
                groupOpen = true
            }
            merged.before.isEmpty() && merged.after.isEmpty() -> {
                // The group has undone itself, so there is nothing left to step back over.
                undoable.removeLast()
                groupOpen = false
            }
            else -> {
                undoable.removeLast()
                undoable.addLast(merged)
                groupOpen = true
            }
        }
    }

    /** Ends the current group, so the next recorded change starts a new undo step. */
    fun breakGroup() {
        groupOpen = false
    }

    /** Returns the change to reverse, or null when there is nothing left to undo. */
    fun undo(): TextEdit? {
        val edit = undoable.removeLastOrNull() ?: return null
        redoable.addLast(edit)
        groupOpen = false
        return edit
    }

    /** Returns the change to reapply, or null when there is nothing left to redo. */
    fun redo(): TextEdit? {
        val edit = redoable.removeLastOrNull() ?: return null
        undoable.addLast(edit)
        groupOpen = false
        return edit
    }

    /** Forgets everything, for when the text has moved in a way the history cannot replay. */
    fun clear() {
        undoable.clear()
        redoable.clear()
        groupOpen = false
    }

    private companion object {
        /** Bounds what a long editing session can hold, which matters for a large note. */
        const val MAX_ENTRIES = 200
    }
}

/** Returns [previous] and [next] as a single change, or null when they belong to separate steps. */
internal fun mergeEdits(previous: TextEdit, next: TextEdit): TextEdit? {
    if (previous.spansLines || next.spansLines) return null
    val offset = next.start - previous.start
    if (offset >= 0 && offset + next.before.length <= previous.after.length) {
        return TextEdit(
            previous.start,
            previous.before,
            previous.after.replaceRange(offset, offset + next.before.length, next.after)
        )
    }
    if (previous.after.isNotEmpty() || next.after.isNotEmpty()) return null
    return when (next.start) {
        previous.start - next.before.length ->
            TextEdit(next.start, next.before + previous.before, "")
        previous.start -> TextEdit(previous.start, previous.before + next.before, "")
        else -> null
    }
}

private val TextEdit.spansLines: Boolean get() = '\n' in before || '\n' in after
