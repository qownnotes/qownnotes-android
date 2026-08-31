package org.qownnotes.mobile.markdown

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.widget.AppCompatTextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.WeakHashMap
import org.qownnotes.mobile.core.InternalNoteLink
import org.qownnotes.mobile.core.isSafeExternalUrl
import org.qownnotes.mobile.core.parseLegacyNoteLink
import org.qownnotes.mobile.core.parseWikiLink

class MarkdownRenderer(context: Context) {
    private val applicationContext = context.applicationContext
    private val linkHandlers = WeakHashMap<AppCompatTextView, (InternalNoteLink) -> Unit>()
    private val markwon =
        Markwon.builder(applicationContext)
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                        builder.linkResolver { view, destination ->
                            when {
                                destination.startsWith(WIKI_SCHEME) -> {
                                    decodeWikiLink(destination)?.let {
                                        (view as? AppCompatTextView)?.let(
                                            linkHandlers::get
                                        )?.invoke(it)
                                    }
                                }
                                parseLegacyNoteLink(destination) != null -> {
                                    (view as? AppCompatTextView)?.let(linkHandlers::get)
                                        ?.invoke(parseLegacyNoteLink(destination)!!)
                                }
                                isSafeExternalUrl(destination) -> openExternal(destination)
                            }
                        }
                    }
                }
            )
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(applicationContext))
            .usePlugin(TaskListPlugin.create(applicationContext))
            .build()

    fun render(
        view: AppCompatTextView,
        markdown: String,
        onInternalLink: (InternalNoteLink) -> Unit = {}
    ) {
        linkHandlers[view] = onInternalLink
        markwon.setMarkdown(view, markdown.withoutFrontmatter().withInternalLinks())
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
}

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
}

private fun String.withInlineInternalLinks(): String {
    val output = StringBuilder(length)
    var index = 0
    var codeDelimiterLength = 0
    var insideHtml = false
    while (index < length) {
        val character = this[index]
        if (character == '`') {
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
