package org.qownnotes.mobile.markdown

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.util.concurrent.Executors

enum class MarkdownFormatAction {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    CODE,
    LINK,
    HEADING,
    BULLET,
    NUMBERED,
    TASK,
    QUOTE
}

data class MarkdownTextEdit(val text: String, val selectionStart: Int, val selectionEnd: Int)

fun applyMarkdownFormat(
    source: String,
    selectionStart: Int,
    selectionEnd: Int,
    action: MarkdownFormatAction
): MarkdownTextEdit {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, source.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(start, source.length)
    val selected = source.substring(start, end)
    return when (action) {
        MarkdownFormatAction.BOLD -> source.wrap(start, end, "**", "**")
        MarkdownFormatAction.ITALIC -> source.wrap(start, end, "_", "_")
        MarkdownFormatAction.STRIKETHROUGH -> source.wrap(start, end, "~~", "~~")
        MarkdownFormatAction.CODE -> source.wrap(start, end, "`", "`")
        MarkdownFormatAction.LINK -> {
            val label = selected.ifEmpty { "text" }
            val replacement = "[$label](url)"
            MarkdownTextEdit(
                source.replaceRange(start, end, replacement),
                start + replacement.length - 4,
                start + replacement.length - 1
            )
        }
        MarkdownFormatAction.HEADING -> source.prefixLines(start, end, "# ")
        MarkdownFormatAction.BULLET -> source.prefixLines(start, end, "- ")
        MarkdownFormatAction.NUMBERED -> source.prefixLines(start, end, "1. ")
        MarkdownFormatAction.TASK -> source.prefixLines(start, end, "- [ ] ")
        MarkdownFormatAction.QUOTE -> source.prefixLines(start, end, "> ")
    }
}

private fun String.wrap(start: Int, end: Int, before: String, after: String): MarkdownTextEdit {
    val replacement = before + substring(start, end) + after
    return MarkdownTextEdit(
        replaceRange(start, end, replacement),
        start + before.length,
        end + before.length
    )
}

private fun String.prefixLines(start: Int, end: Int, prefix: String): MarkdownTextEdit {
    val lineStart = lastIndexOf('\n', maxOf(0, start - 1)).let { if (it < 0) 0 else it + 1 }
    val lineEnd = indexOf('\n', end).let { if (it < 0) length else it }
    val lineStarts = buildList {
        add(lineStart)
        for (index in lineStart until lineEnd) {
            if (this@prefixLines[index] == '\n' && index + 1 < lineEnd) add(index + 1)
        }
    }
    val result = StringBuilder(this)
    lineStarts.asReversed().forEach { result.insert(it, prefix) }
    return MarkdownTextEdit(
        result.toString(),
        start + lineStarts.count { it <= start } * prefix.length,
        end + lineStarts.count { it <= end } * prefix.length
    )
}

fun continueMarkdownList(source: String, newlineOffset: Int): MarkdownTextEdit? {
    if (newlineOffset !in source.indices || source[newlineOffset] != '\n') return null
    val lineStart = source.lastIndexOf('\n', newlineOffset - 1).let { if (it < 0) 0 else it + 1 }
    if (source.isInsideFence(lineStart)) return null
    val line = source.substring(lineStart, newlineOffset).removeSuffix("\r")
    val unordered = UNORDERED_LIST.matchEntire(line)
    val ordered = ORDERED_LIST.matchEntire(line)
    val content = unordered?.groupValues?.get(6) ?: ordered?.groupValues?.get(5) ?: return null
    if (content.isBlank()) {
        val text = source.removeRange(lineStart, newlineOffset)
        return MarkdownTextEdit(text, lineStart + 1, lineStart + 1)
    }
    val prefix = if (unordered != null) {
        val task = unordered.groupValues[4]
        unordered.groupValues[1] + unordered.groupValues[2] + unordered.groupValues[3] +
            if (task.isEmpty()) "" else "[ ]${unordered.groupValues[5]}"
    } else {
        val match = requireNotNull(ordered)
        val nextNumber = match.groupValues[2].toLongOrNull()?.plus(1) ?: return null
        match.groupValues[1] + nextNumber + match.groupValues[3] + match.groupValues[4]
    }
    val insertionPoint = newlineOffset + 1
    val text = source.substring(0, insertionPoint) + prefix + source.substring(insertionPoint)
    val caret = insertionPoint + prefix.length
    return MarkdownTextEdit(text, caret, caret)
}

private fun String.isInsideFence(beforeOffset: Int): Boolean {
    var fence: Char? = null
    substring(0, beforeOffset).lineSequence().forEach { line ->
        val marker = FENCE.matchEntire(line)?.groupValues?.get(1) ?: return@forEach
        if (fence == null) {
            fence = marker.first()
        } else if (fence == marker.first()) {
            fence = null
        }
    }
    return fence != null
}

private val UNORDERED_LIST = Regex("^([ \\t]*)([-+*])([ \\t]+)(?:\\[([ xX-])]([ \\t]+))?(.*)$")
private val ORDERED_LIST = Regex("^([ \\t]*)(\\d+)([.)])([ \\t]+)(.*)$")
private val FENCE = Regex("^ {0,3}(`{3,}|~{3,})(?:[^`]*)$")

class MarkdownEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatEditText(context, attrs) {
    var onSelectionChanged: ((Int, Int) -> Unit)? = null

    /**
     * Invoked before an edit the writer did not type, such as a formatting action, so that an undo
     * history can close the current group and make that edit a single step.
     */
    var onEditBoundary: (() -> Unit)? = null

    init {
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        setHorizontallyScrolling(false)
        // `AppCompatEditText` takes its default style from the AppCompat `editTextStyle` theme
        // attribute. A host theme that is not an AppCompat descendant leaves that attribute
        // undefined, so `Widget.AppCompat.EditText` is never applied and the view stays
        // non-focusable in touch mode: tapping it would never show a cursor or the keyboard.
        // These flags make interactive editing independent of the hosting theme.
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isLongClickable = true
        isCursorVisible = true
        showSoftInputOnFocus = true
        // The editor fills a Compose surface that already supplies padding and background.
        background = null
    }

    /** Gives the editor input focus and asks the input method to open. */
    fun focusForInput() {
        val request = Runnable {
            if (isFocused || requestFocus()) {
                inputMethodManager()?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        if (isAttachedToWindow && hasWindowFocus()) request.run() else post(request)
    }

    /** Releases input focus and hides the input method when editing stops. */
    fun releaseInputFocus() {
        inputMethodManager()?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    fun applyFormat(action: MarkdownFormatAction) {
        val editable = text ?: return
        onEditBoundary?.invoke()
        val source = editable.toString()
        val edit = applyMarkdownFormat(
            source,
            selectionStart.coerceAtLeast(0),
            selectionEnd.coerceAtLeast(0),
            action
        )
        // Replace only the changed range so undo history, spans, and any in-progress input-method
        // composition outside that range survive the formatting action.
        replaceChangedRange(editable, source, edit.text)
        setSelection(
            edit.selectionStart.coerceIn(0, editable.length),
            edit.selectionEnd.coerceIn(0, editable.length)
        )
    }

    /**
     * Tells the input method to read the editor again, after the text moved underneath whatever it
     * was composing.
     */
    fun resetInputMethod() {
        inputMethodManager()?.restartInput(this)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChanged?.invoke(selStart, selEnd)
    }

    private fun inputMethodManager() =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
}

internal fun replaceChangedRange(target: Editable, before: String, after: String) {
    if (before == after) return
    val shortest = minOf(before.length, after.length)
    var prefix = 0
    while (prefix < shortest && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (
        suffix < shortest - prefix &&
        before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
    ) {
        suffix++
    }
    target.replace(prefix, before.length - suffix, after.substring(prefix, after.length - suffix))
}

class MarkdownEditorBinding(
    context: Context,
    private val editText: MarkdownEditText,
    private val onHistoryChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit = { _, _ -> },
    onSourceChanged: (String) -> Unit
) : AutoCloseable {
    private val history = TextEditHistory()
    private var replaying = false
    private var replaced = ""
    private val markwon =
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .build()
    private val highlightWatcher =
        MarkwonEditorTextWatcher.withPreRender(
            MarkwonEditor.create(markwon),
            highlightExecutor,
            editText
        )
    private val sourceWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                source: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(source: CharSequence?, start: Int, before: Int, count: Int) {
                onSourceChanged(source?.toString().orEmpty())
            }

            override fun afterTextChanged(source: Editable?) = Unit
        }
    private val listContinuationWatcher = ListContinuationWatcher(editText)

    /**
     * Records what the writer changes. Highlighting only adds spans, which does not reach a
     * `TextWatcher`, so the history sees genuine text changes and nothing else.
     */
    private val historyWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                source: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                if (!replaying) replaced = source.textAt(start, count)
            }

            override fun onTextChanged(source: CharSequence?, start: Int, before: Int, count: Int) {
                if (replaying) return
                history.record(TextEdit(start, replaced, source.textAt(start, count)))
                publishHistory()
            }

            override fun afterTextChanged(source: Editable?) = Unit
        }

    val canUndo: Boolean get() = history.canUndo

    val canRedo: Boolean get() = history.canRedo

    init {
        // Recorded before the other watchers run, so the history holds the change even if
        // highlighting a pathological note fails.
        editText.addTextChangedListener(historyWatcher)
        editText.addTextChangedListener(listContinuationWatcher)
        editText.addTextChangedListener(highlightWatcher)
        editText.addTextChangedListener(sourceWatcher)
        editText.addTextChangedListener(SupplementalSyntaxWatcher)
        editText.onEditBoundary = history::breakGroup
        // The app populates the view before attaching this binding, so the watchers do not see
        // that initial change. Highlight the existing source without creating an undo entry or
        // reporting it as a user edit.
        highlightWatcher.afterTextChanged(editText.text)
        SupplementalSyntaxWatcher.afterTextChanged(editText.text)
        publishHistory()
    }

    /** Reverses the last change, and reports whether there was one. */
    fun undo(): Boolean {
        val edit = history.undo() ?: return false
        return replay(edit.start, edit.after, edit.before)
    }

    /** Reapplies the last reversed change, and reports whether there was one. */
    fun redo(): Boolean {
        val edit = history.redo() ?: return false
        return replay(edit.start, edit.before, edit.after)
    }

    override fun close() {
        editText.onEditBoundary = null
        editText.removeTextChangedListener(historyWatcher)
        editText.removeTextChangedListener(listContinuationWatcher)
        editText.removeTextChangedListener(sourceWatcher)
        editText.removeTextChangedListener(highlightWatcher)
        editText.removeTextChangedListener(SupplementalSyntaxWatcher)
    }

    private fun replay(start: Int, remove: String, insert: String): Boolean {
        val editable = editText.text ?: return false
        val end = start + remove.length
        if (start < 0 || end > editable.length || editable.textAt(start, remove.length) != remove) {
            // The text no longer holds what the history says was there, and replacing a range that
            // now contains something else would destroy content. Forget the history instead.
            history.clear()
            publishHistory()
            return false
        }
        replaying = true
        try {
            // An input method composing over the replaced range would otherwise go on composing
            // over text that is no longer there.
            BaseInputConnection.removeComposingSpans(editable)
            editable.replace(start, end, insert)
            editText.setSelection((start + insert.length).coerceIn(0, editText.length()))
        } finally {
            replaying = false
        }
        editText.resetInputMethod()
        publishHistory()
        return true
    }

    private fun publishHistory() = onHistoryChanged(history.canUndo, history.canRedo)

    private fun CharSequence?.textAt(start: Int, count: Int): String {
        this ?: return ""
        val from = start.coerceIn(0, length)
        return subSequence(from, (from + count).coerceIn(from, length)).toString()
    }

    private companion object {
        val highlightExecutor = Executors.newFixedThreadPool(2)
    }
}

private class ListContinuationWatcher(private val editText: MarkdownEditText) : TextWatcher {
    private var newlineOffset: Int? = null
    private var applying = false

    override fun beforeTextChanged(source: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(source: CharSequence?, start: Int, before: Int, count: Int) {
        newlineOffset =
            if (!applying && before == 0 && count == 1 && source?.getOrNull(start) == '\n') {
                start
            } else {
                null
            }
    }

    override fun afterTextChanged(source: Editable?) {
        source ?: return
        val offset = newlineOffset ?: return
        newlineOffset = null
        val edit = continueMarkdownList(source.toString(), offset) ?: return
        applying = true
        try {
            replaceChangedRange(source, source.toString(), edit.text)
            editText.setSelection(edit.selectionStart, edit.selectionEnd)
            // Some input methods update the selection after TextWatchers return, using the
            // position where they inserted Return. Restore the caret after that update so a
            // second Return acts on the continued empty item rather than before its marker.
            if (editText.isAttachedToWindow) {
                editText.post {
                    if (editText.text?.toString() == edit.text) {
                        editText.setSelection(edit.selectionStart, edit.selectionEnd)
                    }
                }
            }
        } finally {
            applying = false
        }
    }
}

internal enum class MarkdownSyntax {
    HEADING,
    EMPHASIS,
    STRIKETHROUGH,
    LIST,
    TASK,
    BLOCKQUOTE,
    CODE,
    LINK,
    TABLE,
    WIKI_LINK,
    FRONTMATTER,
    COMMENT
}

internal class SupplementalSyntaxSpan(val syntax: MarkdownSyntax) :
    ForegroundColorSpan(Color.rgb(92, 107, 192))

private object SupplementalSyntaxWatcher : TextWatcher {
    private val patterns = listOf(
        MarkdownSyntax.FRONTMATTER to
            Regex("\\A---(?:\\r?\\n)[\\s\\S]*?(?:\\r?\\n)---(?=\\r?\\n|$)"),
        MarkdownSyntax.COMMENT to Regex("<!--[\\s\\S]*?-->"),
        MarkdownSyntax.WIKI_LINK to Regex("\\[\\[[^]\\r\\n]+]]"),
        MarkdownSyntax.HEADING to Regex("(?m)^ {0,3}(?:#{1,6}(?=\\s)|(?:=+|-+)\\s*$)"),
        MarkdownSyntax.EMPHASIS to
            Regex(
                "(?<!\\*)\\*{1,3}(?=\\S)|(?<=\\S)\\*{1,3}(?!\\*)|" +
                    "(?<!_)_{1,3}(?=\\S)|(?<=\\S)_{1,3}(?!_)"
            ),
        MarkdownSyntax.STRIKETHROUGH to Regex("~~"),
        MarkdownSyntax.LIST to Regex("(?m)^\\s*(?:[-+*]|\\d+[.)])(?=\\s)"),
        MarkdownSyntax.TASK to Regex("\\[[ xX-]]"),
        MarkdownSyntax.BLOCKQUOTE to Regex("(?m)^\\s*>+"),
        MarkdownSyntax.CODE to
            Regex("(?m)^\\s*(?:`{3,}|~{3,})[^\\r\\n]*|`+[^`\\r\\n]+`+"),
        MarkdownSyntax.LINK to Regex("!?\\[[^]\\r\\n]*]\\([^\\s)]+(?:\\s+[^)]*)?\\)"),
        MarkdownSyntax.TABLE to Regex("(?m)^\\s*\\|.*\\|\\s*$")
    )

    override fun beforeTextChanged(source: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(source: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(source: Editable?) {
        source ?: return
        source.getSpans(0, source.length, SupplementalSyntaxSpan::class.java)
            .forEach(source::removeSpan)
        patterns.forEach { (syntax, pattern) ->
            pattern.findAll(source).forEach { match ->
                source.setSpan(
                    SupplementalSyntaxSpan(syntax),
                    match.range.first,
                    match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
}
