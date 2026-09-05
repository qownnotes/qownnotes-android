package org.qownnotes.mobile.markdown

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.ext.tasklist.TaskListSpan
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.SchemeHandler
import io.noties.markwon.image.destination.ImageDestinationProcessor
import io.noties.markwon.movement.MovementMethodPlugin
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.parser.Parser
import org.qownnotes.mobile.core.InternalNoteLink
import org.qownnotes.mobile.core.ResolvedNoteLink
import org.qownnotes.mobile.core.isSafeExternalUrl
import org.qownnotes.mobile.core.parseLegacyNoteLink
import org.qownnotes.mobile.core.parseMarkdownNoteLink
import org.qownnotes.mobile.core.parseWikiLink
import org.qownnotes.mobile.core.redactEncryptedMarkdown

class MarkdownRenderer private constructor(
    context: Context,
    imageSchemeHandler: SchemeHandler,
    private val attachmentHttpClient: NextcloudAttachmentHttpClient?,
    private val attachmentSchemeHandler: NextcloudAttachmentSchemeHandler?
) {
    constructor(context: Context) : this(
        context,
        SafeHttpsImageSchemeHandler(context.applicationContext.resources),
        null,
        null
    )

    constructor(
        context: Context,
        httpClient: NextcloudAttachmentHttpClient
    ) : this(
        context,
        SafeHttpsImageSchemeHandler(context.applicationContext.resources),
        httpClient,
        NextcloudAttachmentSchemeHandler(context.applicationContext.resources, httpClient)
    )

    private val applicationContext = context.applicationContext
    private val linkHandlers = WeakHashMap<AppCompatTextView, InternalLinkHandler>()
    private val attachmentDestinationProcessor = AttachmentDestinationProcessor()
    private val textMarkwon = createMarkwon()
    private val imageMarkwon = createMarkwon(imageSchemeHandler)

    internal companion object {
        private val IMAGE_EXECUTOR = Executors.newFixedThreadPool(2)

        fun forTest(context: Context, imageSchemeHandler: SchemeHandler): MarkdownRenderer =
            MarkdownRenderer(context, imageSchemeHandler, null, null)
    }

    fun render(
        view: AppCompatTextView,
        markdown: String,
        resolveInternalLink: (InternalNoteLink) -> ResolvedNoteLink? = { null },
        onInternalLink: (ResolvedNoteLink) -> Unit = {},
        onTaskToggle: ((Int) -> Unit)? = null,
        heading: String? = null,
        onHeadingPositioned: (Int?) -> Unit = {},
        loadRemoteImages: Boolean = false,
        remoteId: Long? = null,
        accountName: String = ""
    ) {
        android.util.Log.d(
            "QOwnNotes",
            "render: loadRemoteImages=$loadRemoteImages, remoteId=$remoteId, " +
                "accountName=$accountName, hasAttachmentHandler=${attachmentSchemeHandler != null}"
        )
        attachmentDestinationProcessor.setNoteContext(remoteId)
        attachmentSchemeHandler?.accountName = accountName
        linkHandlers[view] = InternalLinkHandler(resolveInternalLink, onInternalLink)
        // Reading a note includes taking text out of it, and copying needs a selection. This is
        // applied before the Markdown because `setTextIsSelectable` re-sets both the text and the
        // movement method, which would otherwise discard what the renderer just installed.
        view.enableTextSelection()
        val lockedMessage = applicationContext.getString(R.string.encrypted_content_locked)
        val encryption = redactEncryptedMarkdown(markdown, replacement = "")
        val safeMarkdown = if (encryption.blockCount > 0) {
            "> **$lockedMessage**"
        } else {
            encryption.markdown
        }
        val markwon = if (loadRemoteImages) imageMarkwon else textMarkwon
        markwon.setMarkdown(view, safeMarkdown.withoutFrontmatter().withInternalLinks())
        styleBrokenInternalLinks(view, resolveInternalLink)
        attachTaskToggleSpans(view, onTaskToggle)
        if (heading != null) {
            view.post { onHeadingPositioned(findHeadingTop(view, heading)) }
        }
    }

    fun hasRemoteImages(markdown: String): Boolean {
        if (redactEncryptedMarkdown(markdown, replacement = "").blockCount > 0) return false
        var found = false
        val document = Parser.builder().build().parse(markdown)
        sanitizeMarkdownHtml(document)
        document.accept(
            object : AbstractVisitor() {
                override fun visit(image: Image) {
                    val destination = attachmentDestinationProcessor.process(image.destination)
                    if (destination != BLOCKED_IMAGE_DESTINATION) found = true
                }
            }
        )
        return found
    }

    fun hasEncryptedContent(markdown: String): Boolean =
        redactEncryptedMarkdown(markdown, replacement = "").blockCount > 0

    private fun createMarkwon(imageSchemeHandler: SchemeHandler? = null): Markwon {
        val builder = Markwon.builder(applicationContext)
            .fallbackToRawInputWhenEmpty(false)
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                        builder.syntaxHighlight(BoundedSyntaxHighlight(applicationContext))
                        builder.imageDestinationProcessor(attachmentDestinationProcessor)
                        builder.linkResolver { view, destination ->
                            when {
                                destination.startsWith(WIKI_SCHEME) -> {
                                    dispatchInternalLink(view, destination)
                                }
                                parseLegacyNoteLink(destination) != null -> {
                                    dispatchInternalLink(view, destination)
                                }
                                parseMarkdownNoteLink(destination) != null -> {
                                    dispatchInternalLink(view, destination)
                                }
                                isSafeExternalUrl(destination) -> openExternal(destination)
                            }
                        }
                    }
                }
            )
            .usePlugin(MovementMethodPlugin.create(SelectableLinkMovementMethod()))
            .usePlugin(MarkdownHtmlSanitizerPlugin())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(applicationContext))
            .usePlugin(IndeterminateTaskListPlugin(applicationContext))
            .usePlugin(TaskListPlugin.create(applicationContext))
        if (imageSchemeHandler != null) {
            builder.usePlugin(
                ImagesPlugin.create { images ->
                    images.removeSchemeHandler("http")
                    images.removeSchemeHandler("data")
                    images.addSchemeHandler(imageSchemeHandler)
                    attachmentSchemeHandler?.let { images.addSchemeHandler(it) }
                    images.executorService(IMAGE_EXECUTOR)
                    images.errorHandler { _, _ -> blockedImageDrawable() }
                }
            )
        }
        return builder.build()
    }

    private fun dispatchInternalLink(view: android.view.View, destination: String) {
        val textView = view as? AppCompatTextView ?: return
        val handler = linkHandlers[textView] ?: return
        parseInternalLink(destination)?.let(handler.resolve)?.let(handler.open)
    }

    private fun openExternal(destination: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destination))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            applicationContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }

    private fun blockedImageDrawable(): GradientDrawable {
        val size = (24 * applicationContext.resources.displayMetrics.density).toInt()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            setStroke((2 * applicationContext.resources.displayMetrics.density).toInt(), Color.RED)
            setSize(size, size)
        }
    }
}

/**
 * Rewrites image destinations for Markwon.
 *
 * HTTPS destinations pass through unchanged. Relative destinations (such as QOwnNotes `media/`
 * paths) are rewritten to `nextcloud-attachment:` URLs when a server URL and note ID are set,
 * allowing the [NextcloudAttachmentSchemeHandler] to fetch them via SSO authentication.
 */
private class AttachmentDestinationProcessor : ImageDestinationProcessor() {
    @Volatile private var remoteId: Long? = null

    fun setNoteContext(remoteId: Long?) {
        this.remoteId = remoteId
    }

    override fun process(destination: String): String {
        canonicalSafeImageDestination(destination)?.let { return it }
        val id = remoteId
        if (id != null && destination.isNotBlank() &&
            !destination.startsWith("http://") &&
            !destination.startsWith("https://") &&
            !destination.startsWith("file://") &&
            !destination.startsWith(ATTACHMENT_SCHEME)
        ) {
            val result = "$ATTACHMENT_SCHEME:/index.php/apps/notes/api/v1/attachment/$id" +
                "?path=" + URLEncoder.encode(destination, StandardCharsets.UTF_8.name())
            android.util.Log.d("QOwnNotes", "Rewrote image destination: $destination -> $result")
            return result
        }
        android.util.Log.d(
            "QOwnNotes",
            "Blocked image destination: $destination (remoteId=$remoteId)"
        )
        return BLOCKED_IMAGE_DESTINATION
    }
}

internal class TaskToggleSpan(val index: Int, val leadingMargin: Int, val toggle: (Int) -> Unit)

private fun attachTaskToggleSpans(view: AppCompatTextView, onTaskToggle: ((Int) -> Unit)?) {
    if (onTaskToggle == null) return
    val text = view.text as? Spannable ?: return
    text.getSpans(0, text.length, TaskListSpan::class.java)
        .sortedWith(compareBy({ text.getSpanStart(it) }, { text.getSpanEnd(it) }))
        .forEachIndexed { index, task ->
            text.setSpan(
                TaskToggleSpan(index, task.getLeadingMargin(true), onTaskToggle),
                text.getSpanStart(task),
                text.getSpanEnd(task),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
}

private const val BLOCKED_IMAGE_DESTINATION = "qon-blocked-image:blocked"

private data class InternalLinkHandler(
    val resolve: (InternalNoteLink) -> ResolvedNoteLink?,
    val open: (ResolvedNoteLink) -> Unit
)

/**
 * Turns the rendered note into selectable text once.
 *
 * `setTextIsSelectable` also makes the view focusable, clickable, and long-clickable and re-sets
 * its text, so it is applied only while the view is not selectable yet.
 */
private fun AppCompatTextView.enableTextSelection() {
    if (isTextSelectable) return
    setTextIsSelectable(true)
}

private fun styleBrokenInternalLinks(
    view: AppCompatTextView,
    resolveInternalLink: (InternalNoteLink) -> ResolvedNoteLink?
) {
    val text = view.text as? Spannable ?: return
    text.getSpans(0, text.length, LinkSpan::class.java).forEach { span ->
        val link = parseInternalLink(span.link) ?: return@forEach
        if (resolveInternalLink(link) != null) return@forEach
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        val flags = text.getSpanFlags(span)
        text.removeSpan(span)
        text.setSpan(BrokenInternalLinkSpan(view.brokenLinkColor()), start, end, flags)
    }
}

internal class BrokenInternalLinkSpan(private val color: Int) : CharacterStyle() {
    override fun updateDrawState(textPaint: TextPaint) {
        textPaint.color = color
        textPaint.isStrikeThruText = true
    }
}

internal fun findHeadingTop(view: AppCompatTextView, requestedHeading: String): Int? {
    val text = view.text as? Spanned ?: return null
    val requestedSlug = requestedHeading.headingSlug()
    val heading = text.getSpans(0, text.length, HeadingSpan::class.java).firstOrNull { span ->
        text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
            .headingSlug() == requestedSlug
    } ?: return null
    val layout = view.layout ?: return null
    return layout.getLineTop(layout.getLineForOffset(text.getSpanStart(heading)))
}

private fun parseInternalLink(destination: String): InternalNoteLink? =
    if (destination.startsWith(WIKI_SCHEME)) {
        decodeWikiLink(destination)
    } else {
        parseLegacyNoteLink(destination) ?: parseMarkdownNoteLink(destination)
    }

private fun AppCompatTextView.brokenLinkColor(): Int {
    val value = TypedValue()
    if (!context.theme.resolveAttribute(android.R.attr.colorError, value, true)) return Color.RED
    return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
}

private fun String.headingSlug(): String = lowercase(Locale.ROOT).trim()
    .replace(Regex("[^\\p{L}\\p{N}\\s-]"), "")
    .replace(Regex("[\\s-]+"), "-")

internal fun String.withInternalLinks(): String {
    val processor = InternalLinkPreprocessor()
    var start = 0
    while (start < length) {
        val newline = indexOf('\n', start)
        val end = if (newline < 0) length else newline + 1
        processor.append(substring(start, end))
        start = end
    }
    return processor.result()
}

private class InternalLinkPreprocessor {
    private val output = StringBuilder()
    private var fenceCharacter: Char? = null
    private var fenceLength = 0
    private var codeDelimiterLength = 0

    fun append(line: String): InternalLinkPreprocessor {
        val fence = line.fenceMarker()
        if (fenceCharacter != null) {
            output.append(line)
            if (fence != null && fence.first == fenceCharacter && fence.second >= fenceLength &&
                line.dropWhile { it == ' ' }.drop(fence.second).isBlank()
            ) {
                fenceCharacter = null
                fenceLength = 0
            }
        } else if (fence != null) {
            fenceCharacter = fence.first
            fenceLength = fence.second
            output.append(line)
        } else {
            output.append(line.withInlineInternalLinks())
        }
        return this
    }

    fun result(): String = output.toString()

    private fun String.withInlineInternalLinks(): String {
        val output = StringBuilder(length)
        var index = 0
        var insideHtml = false
        while (index < length) {
            val character = this[index]
            if (character == '`' && !isEscaped(index)) {
                val runLength = countRun(index, '`')
                if (codeDelimiterLength == 0) {
                    codeDelimiterLength = runLength
                } else if (runLength == codeDelimiterLength) {
                    codeDelimiterLength = 0
                }
                output.append(this, index, index + runLength)
                index += runLength
                continue
            }
            if (codeDelimiterLength == 0) {
                if (character == '<') insideHtml = true
                if (!insideHtml && !isEscaped(index) && startsWith("[[", index)) {
                    val end = indexOf("]]", index + 2)
                    if (end >= 0) {
                        val body = substring(index + 2, end)
                        val link = parseWikiLink(body)
                        if (link != null) {
                            val label = (
                                link.label ?: body.substringBefore(
                                    '|'
                                ).trim()
                                ).escapeMarkdownLabel()
                            output.append("[").append(label).append("](")
                                .append(encodeWikiLink(body)).append(")")
                            index = end + 2
                            continue
                        }
                    }
                }
                if (!insideHtml && startsWith("note://", index, ignoreCase = true) &&
                    (index == 0 || this[index - 1] !in "<(\"'")
                ) {
                    val end = indexOfFirst(index) { it.isWhitespace() || it in "<>\"')" }
                    val destination = substring(index, end)
                    if (parseLegacyNoteLink(destination) != null) {
                        output.append('<').append(destination).append('>')
                        index = end
                        continue
                    }
                }
                if (character == '>') insideHtml = false
            }
            output.append(character)
            index++
        }
        return output.toString()
    }
}

private fun String.fenceMarker(): Pair<Char, Int>? {
    val trimmed = dropWhile { it == ' ' }.takeIf { length - it.length <= 3 } ?: return null
    val marker = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
    val length = trimmed.countRun(0, marker)
    return if (length >= 3) marker to length else null
}

private fun String.countRun(start: Int, character: Char): Int {
    var end = start
    while (end < length && this[end] == character) end++
    return end - start
}

private fun String.isEscaped(index: Int): Boolean {
    var slashCount = 0
    var cursor = index - 1
    while (cursor >= 0 && this[cursor] == '\\') {
        slashCount++
        cursor--
    }
    return slashCount % 2 == 1
}

private fun String.indexOfFirst(start: Int, predicate: (Char) -> Boolean): Int {
    for (index in start until length) if (predicate(this[index])) return index
    return length
}

private fun String.escapeMarkdownLabel(): String =
    replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")

private fun encodeWikiLink(body: String): String =
    WIKI_SCHEME + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(body.toByteArray(StandardCharsets.UTF_8))

private fun decodeWikiLink(destination: String): InternalNoteLink? = runCatching {
    val body = String(
        Base64.getUrlDecoder().decode(destination.removePrefix(WIKI_SCHEME)),
        StandardCharsets.UTF_8
    )
    parseWikiLink(body)
}.getOrNull()

private const val WIKI_SCHEME = "qon-internal:wiki:"

internal fun String.withoutFrontmatter(): String {
    if (!startsWith("---\n")) return this
    val end = indexOf("\n---\n", startIndex = 4)
    return if (end < 0) this else substring(end + 5)
}

fun toggleTaskListItem(markdown: String, taskIndex: Int): String? {
    if (taskIndex < 0) return null
    var index = 0
    var lineStart = markdown.bodyStartAfterFrontmatter()
    var fenceCharacter: Char? = null
    var fenceLength = 0
    while (lineStart < markdown.length) {
        val newline = markdown.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) markdown.length else newline
        val line = markdown.substring(lineStart, lineEnd)
        val fence = line.fenceMarker()
        if (fenceCharacter != null) {
            if (fence != null && fence.first == fenceCharacter && fence.second >= fenceLength &&
                line.dropWhile { it == ' ' }.drop(fence.second).isBlank()
            ) {
                fenceCharacter = null
                fenceLength = 0
            }
        } else if (fence != null) {
            fenceCharacter = fence.first
            fenceLength = fence.second
        } else {
            val task = TASK_LIST_MARKER.find(line)
            if (task != null) {
                if (index == taskIndex) {
                    val marker = lineStart + task.groups[1]!!.range.first
                    val toggled = if (markdown[marker] == 'x' ||
                        markdown[marker] == 'X'
                    ) {
                        ' '
                    } else {
                        'x'
                    }
                    return markdown.replaceRange(marker, marker + 1, toggled.toString())
                }
                index++
            }
        }
        if (newline < 0) break
        lineStart = newline + 1
    }
    return null
}

private fun String.bodyStartAfterFrontmatter(): Int {
    if (!startsWith("---\n")) return 0
    val end = indexOf("\n---\n", startIndex = 4)
    return if (end < 0) 0 else end + 5
}

private val TASK_LIST_MARKER =
    Regex("^(?: {0,3}>[ \\t]?)*[ \\t]*(?:[-+*]|\\d+[.)])[ \\t]+\\[([ xX-])]")
