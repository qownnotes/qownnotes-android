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
        setHorizontallyScrolling(false)
    }

    fun applyFormat(action: MarkdownFormatAction) {
        val edit =
            applyMarkdownFormat(text?.toString().orEmpty(), selectionStart, selectionEnd, action)
        text?.replace(0, text?.length ?: 0, edit.text)
        setSelection(edit.selectionStart, edit.selectionEnd)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChanged?.invoke(selStart, selEnd)
    }
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
