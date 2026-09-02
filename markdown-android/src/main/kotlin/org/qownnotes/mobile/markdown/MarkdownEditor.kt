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

class MarkdownEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatEditText(context, attrs) {
    var onSelectionChanged: ((Int, Int) -> Unit)? = null

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
    onSourceChanged: (String) -> Unit
) : AutoCloseable {
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

    init {
        editText.addTextChangedListener(highlightWatcher)
        editText.addTextChangedListener(sourceWatcher)
        editText.addTextChangedListener(SupplementalSyntaxWatcher)
    }

    override fun close() {
        editText.removeTextChangedListener(sourceWatcher)
        editText.removeTextChangedListener(highlightWatcher)
        editText.removeTextChangedListener(SupplementalSyntaxWatcher)
    }

    private companion object {
        val highlightExecutor = Executors.newFixedThreadPool(2)
    }
}

internal class SupplementalSyntaxSpan : ForegroundColorSpan(Color.rgb(92, 107, 192))

private object SupplementalSyntaxWatcher : TextWatcher {
    private val patterns = listOf(
        Regex("\\A---(?:\\r?\\n)[\\s\\S]*?(?:\\r?\\n)---(?=\\r?\\n|$)"),
        Regex("\\[\\[[^]\\r\\n]+]]"),
        Regex("<!--[\\s\\S]*?-->")
    )

    override fun beforeTextChanged(source: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(source: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(source: Editable?) {
        source ?: return
        source.getSpans(0, source.length, SupplementalSyntaxSpan::class.java)
            .forEach(source::removeSpan)
        patterns.forEach { pattern ->
            pattern.findAll(source).forEach { match ->
                source.setSpan(
                    SupplementalSyntaxSpan(),
                    match.range.first,
                    match.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
}
