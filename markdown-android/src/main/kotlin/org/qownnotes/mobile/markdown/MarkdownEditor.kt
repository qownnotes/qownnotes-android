package org.qownnotes.mobile.markdown

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.text.LineBreaker
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import android.widget.Toast
import androidx.appcompat.widget.AppCompatEditText
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

fun insertMarkdownImage(
    source: String,
    selectionStart: Int,
    selectionEnd: Int,
    fallbackDescription: String,
    path: String
): MarkdownTextEdit {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, source.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(start, source.length)
    val description = source.substring(start, end).ifBlank { fallbackDescription }
        .replace("\r", " ")
        .replace("\n", " ")
        .replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")
    val replacement = "![$description]($path)"
    val caret = start + replacement.length
    return MarkdownTextEdit(source.replaceRange(start, end, replacement), caret, caret)
}

fun supportsMarkdownSourceHighlighting(sourceLength: Int): Boolean =
    sourceLength <= MAX_HIGHLIGHTED_SOURCE_LENGTH

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
    var onVerticalScrollChanged: ((value: Int, range: Int) -> Unit)? = null
        set(value) {
            field = value
            post(::reportVerticalScroll)
        }

    /** Set and read only on the thread that owns the view, through [onViewThread]. */
    private var inputFocusRequest: Runnable? = null

    // The view hierarchy may only be touched from the thread that created it, which is the thread
    // constructing this view.
    private val viewThread = Handler(Looper.myLooper() ?: Looper.getMainLooper())
    internal var loadLinkTitle: (String) -> FetchedLink = LinkTitleFetcher()::fetch
    internal var linkTitleTaskExecutor: Executor = linkTitleExecutor
    internal var clipboardWebUrlProvider: () -> String? = ::readClipboardWebUrl
    private var attachmentGeneration = 0
    private val verticalFling = OverScroller(context)
    private val flingDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onFling(
                    down: MotionEvent?,
                    up: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (kotlin.math.abs(velocityY) <= kotlin.math.abs(velocityX)) return false
                    val range = verticalScrollRange()
                    if (range == 0) return false
                    verticalFling.fling(
                        0,
                        scrollY,
                        0,
                        -velocityY.toInt(),
                        0,
                        0,
                        0,
                        range
                    )
                    postInvalidateOnAnimation()
                    return true
                }
            }
        )

    /**
     * Invoked before an edit the writer did not type, such as a formatting action, so that an undo
     * history can close the current group and make that edit a single step.
     */
    var onEditBoundary: (() -> Unit)? = null

    private val markdownLinkActionModeCallback =
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu): Boolean {
                if (clipboardWebUrlProvider() != null) {
                    menu.add(
                        Menu.NONE,
                        R.id.paste_as_markdown_link,
                        Menu.NONE,
                        R.string.paste_as_markdown_link
                    ).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem): Boolean {
                if (item.itemId != R.id.paste_as_markdown_link) return false
                val handled = pasteClipboardUrlAsMarkdownLink()
                if (handled) mode?.finish()
                return handled
            }

            override fun onDestroyActionMode(mode: ActionMode?) = Unit
        }

    init {
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        setHorizontallyScrolling(false)
        breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
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
        isVerticalScrollBarEnabled = true
        customSelectionActionModeCallback = markdownLinkActionModeCallback
        customInsertionActionModeCallback = markdownLinkActionModeCallback
    }

    /** Gives the editor input focus and asks the input method to open. */
    fun focusForInput() = onViewThread {
        cancelInputFocusRequest()
        val request = Runnable {
            inputFocusRequest = null
            if (isFocused || requestFocus()) {
                inputMethodManager()?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        if (isAttachedToWindow && hasWindowFocus()) {
            request.run()
        } else {
            // A detached view has no handler, so `post` holds the request until it is attached.
            inputFocusRequest = request
            post(request)
        }
    }

    /** Releases input focus and hides the input method when editing stops. */
    fun releaseInputFocus() = onViewThread {
        cancelInputFocusRequest()
        inputMethodManager()?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    private fun cancelInputFocusRequest() {
        inputFocusRequest?.let(::removeCallbacks)
        inputFocusRequest = null
    }

    /**
     * Runs [action] on the thread that owns this view, immediately when the caller is already on
     * it.
     *
     * Focus and input-method calls are made from coroutine callbacks, and a coroutine resumes on
     * whichever thread completed the call it awaited: Room finishes its queries and transactions
     * on its own executor. Touching a view from any other thread throws, so the view brings itself
     * back rather than trusting every caller to.
     */
    private fun onViewThread(action: () -> Unit) {
        if (Looper.myLooper() == viewThread.looper) action() else viewThread.post(action)
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

    fun insertImage(description: String, path: String) {
        val editable = text ?: return
        onEditBoundary?.invoke()
        val source = editable.toString()
        val edit = insertMarkdownImage(
            source,
            selectionStart.coerceAtLeast(0),
            selectionEnd.coerceAtLeast(0),
            description,
            path
        )
        replaceChangedRange(editable, source, edit.text)
        setSelection(edit.selectionStart)
    }

    internal fun pasteClipboardUrlAsMarkdownLink(): Boolean {
        val url = clipboardWebUrlProvider() ?: return false
        val source = text?.toString() ?: return false
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, source.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, source.length)
        val expectedAttachmentGeneration = attachmentGeneration
        linkTitleTaskExecutor.execute {
            val fetched = runCatching { loadLinkTitle(url) }
            viewThread.post {
                if (
                    attachmentGeneration != expectedAttachmentGeneration ||
                    text?.toString() != source
                ) {
                    return@post
                }
                fetched.onSuccess { link ->
                    val editable = text ?: return@onSuccess
                    onEditBoundary?.invoke()
                    val replacement = markdownLink(link.title, link.url)
                    editable.replace(start, end, replacement)
                    setSelection(start + replacement.length)
                    resetInputMethod()
                }.onFailure {
                    Toast.makeText(
                        context,
                        R.string.paste_markdown_link_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        return true
    }

    /**
     * Tells the input method to read the editor again, after the text moved underneath whatever it
     * was composing.
     */
    fun resetInputMethod() {
        inputMethodManager()?.restartInput(this)
    }

    fun scrollVerticallyTo(value: Int) {
        scrollTo(scrollX, value.coerceIn(0, verticalScrollRange()))
    }

    fun optimizeForLargeDocument() {
        inputType = inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        flingDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        attachmentGeneration++
        super.onDetachedFromWindow()
    }

    override fun computeScroll() {
        super.computeScroll()
        if (verticalFling.computeScrollOffset()) {
            scrollTo(scrollX, verticalFling.currY)
            postInvalidateOnAnimation()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        reportVerticalScroll()
    }

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        reportVerticalScroll()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChanged?.invoke(selStart, selEnd)
    }

    private fun inputMethodManager() =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    private fun readClipboardWebUrl(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount != 1) return null
        return clip.getItemAt(0).text?.toString()?.let(::canonicalSafeWebUrl)
    }

    private fun reportVerticalScroll() {
        onVerticalScrollChanged?.invoke(scrollY, verticalScrollRange())
    }

    private fun verticalScrollRange(): Int {
        val textLayout = layout ?: return 0
        val contentBottom = textLayout.getLineBottom(textLayout.lineCount - 1)
        return (contentBottom + totalPaddingTop + totalPaddingBottom - height).coerceAtLeast(0)
    }

    private companion object {
        val linkTitleExecutor = Executors.newFixedThreadPool(2)
    }
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

    // Full-document span updates are prohibitively expensive for large editable text. Preserve
    // responsive, exact source editing there instead of letting decoration block input.
    val sourceHighlightingEnabled = supportsMarkdownSourceHighlighting(editText.length())
    private val highlightWatcher = if (sourceHighlightingEnabled) {
        MarkwonEditorTextWatcher.withPreRender(
            MarkwonEditor.create(markwon),
            highlightExecutor,
            editText
        )
    } else {
        null
    }
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
    private val supplementalSyntaxWatcher = if (sourceHighlightingEnabled) {
        SupplementalSyntaxWatcher(editText, delayedSupplementalHighlightExecutor)
    } else {
        null
    }

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
        if (!sourceHighlightingEnabled) editText.optimizeForLargeDocument()
        // Recorded before the other watchers run, so the history holds the change even if
        // highlighting a pathological note fails.
        editText.addTextChangedListener(historyWatcher)
        editText.addTextChangedListener(listContinuationWatcher)
        highlightWatcher?.let(editText::addTextChangedListener)
        editText.addTextChangedListener(sourceWatcher)
        supplementalSyntaxWatcher?.let(editText::addTextChangedListener)
        editText.onEditBoundary = history::breakGroup
        // The app populates the view before attaching this binding, so the watchers do not see
        // that initial change. Highlight the existing source without creating an undo entry or
        // reporting it as a user edit.
        highlightWatcher?.afterTextChanged(editText.text)
        supplementalSyntaxWatcher?.afterTextChanged(editText.text)
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
        highlightWatcher?.let(editText::removeTextChangedListener)
        supplementalSyntaxWatcher?.let(editText::removeTextChangedListener)
        supplementalSyntaxWatcher?.close()
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
        val supplementalHighlightExecutor = Executors.newSingleThreadScheduledExecutor()
        val delayedSupplementalHighlightExecutor = Executor { task ->
            supplementalHighlightExecutor.schedule(task, 250, TimeUnit.MILLISECONDS)
        }
    }
}

private const val MAX_HIGHLIGHTED_SOURCE_LENGTH = 64 * 1024

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
    IMAGE,
    TABLE,
    WIKI_LINK,
    FRONTMATTER,
    COMMENT
}

internal class SupplementalSyntaxSpan(val syntax: MarkdownSyntax) :
    ForegroundColorSpan(Color.rgb(92, 107, 192))

internal data class SupplementalSyntaxRange(
    val syntax: MarkdownSyntax,
    val start: Int,
    val end: Int
)

private val supplementalSyntaxPatterns = listOf(
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
    MarkdownSyntax.CODE to Regex("(?m)^\\s*(?:`{3,}|~{3,})[^\\r\\n]*|`+[^`\\r\\n]+`+"),
    MarkdownSyntax.IMAGE to Regex("!\\[[^]\\r\\n]*]\\([^\\s)]+(?:\\s+[^)]*)?\\)"),
    MarkdownSyntax.LINK to
        Regex(
            "(?<!!)\\[[^]\\r\\n]*]\\([^\\s)]+(?:\\s+[^)]*)?\\)|" +
                "(?<!]\\()(?<![\\w@/])(?:" +
                "(?:https?://|www\\.)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?(?::\\d+)?" +
                "(?:/[^\\s<>()]*)?|" +
                "(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}(?::\\d+)?" +
                "(?:/[^\\s<>()]*)?)" +
                "(?<![.,!?;:'\"])",
            RegexOption.IGNORE_CASE
        ),
    MarkdownSyntax.TABLE to Regex("(?m)^\\s*\\|.*\\|\\s*$")
)

internal fun findSupplementalSyntax(source: String): List<SupplementalSyntaxRange> =
    supplementalSyntaxPatterns.flatMap { (syntax, pattern) ->
        pattern.findAll(source).map { match ->
            SupplementalSyntaxRange(syntax, match.range.first, match.range.last + 1)
        }
    }

internal class SupplementalSyntaxWatcher(
    private val editText: MarkdownEditText,
    private val executor: Executor
) : TextWatcher,
    AutoCloseable {
    private val generation = AtomicInteger()
    private val viewHandler = Handler(Looper.getMainLooper())

    override fun beforeTextChanged(source: CharSequence?, start: Int, count: Int, after: Int) = Unit

    override fun onTextChanged(source: CharSequence?, start: Int, before: Int, count: Int) = Unit

    override fun afterTextChanged(source: Editable?) {
        source ?: return
        val snapshot = source.toString()
        val requestedGeneration = generation.incrementAndGet()
        executor.execute {
            if (requestedGeneration != generation.get()) return@execute
            val ranges = findSupplementalSyntax(snapshot)
            if (requestedGeneration != generation.get()) return@execute
            viewHandler.post {
                val editable = editText.text ?: return@post
                if (requestedGeneration != generation.get()) return@post
                editText.beginBatchEdit()
                try {
                    editable.getSpans(0, editable.length, SupplementalSyntaxSpan::class.java)
                        .forEach(editable::removeSpan)
                    ranges.forEach { range ->
                        editable.setSpan(
                            SupplementalSyntaxSpan(range.syntax),
                            range.start,
                            range.end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                } finally {
                    editText.endBatchEdit()
                }
            }
        }
    }

    override fun close() {
        generation.incrementAndGet()
    }
}
