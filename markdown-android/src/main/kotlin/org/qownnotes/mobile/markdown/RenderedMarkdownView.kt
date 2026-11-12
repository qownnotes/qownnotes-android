package org.qownnotes.mobile.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import androidx.appcompat.widget.AppCompatTextView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TableRowSpan
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.Code
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text

/**
 * The text view of a rendered note.
 *
 * Markwon draws table cells itself and leaves only a placeholder character per row in the view's
 * text, so a plain copy of a selection across a table loses the cells. Copying here puts each
 * selected row's cells on the clipboard instead, separated by tabs, which also pastes into
 * spreadsheets. The cell text is looked up only when such a copy happens, so rendering is not
 * slowed down.
 */
class RenderedMarkdownView(context: Context) : AppCompatTextView(context) {
    /** The plain text of every rendered table row's cells, in document order. */
    internal var tableRows: (() -> List<List<String>>)? = null

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.copy && copyWithTableCells()) return true
        return super.onTextContextMenuItem(id)
    }

    private fun copyWithTableCells(): Boolean {
        val text = text as? Spannable ?: return false
        val copied = selectedTextWithTableCells(text) ?: return false
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(null, copied))
        // Ends the selection and its toolbar, as the platform's own copy does.
        Selection.setSelection(text, maxOf(selectionStart, selectionEnd))
        return true
    }

    /** The selected text with table cells, or `null` when no table row is selected. */
    internal fun selectedTextWithTableCells(text: Spanned): String? {
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        if (start < 0 || start == end) return null
        val spans = text.getSpans(0, text.length, TableRowSpan::class.java)
            .sortedBy(text::getSpanStart)
        val selected = spans.withIndex().filter { (_, span) ->
            text.getSpanStart(span) in start until end
        }
        if (selected.isEmpty()) return null
        // Rows are matched by position, so nothing is substituted when the counts disagree.
        val rows = tableRows?.invoke()?.takeIf { it.size == spans.size } ?: return null
        val copied = StringBuilder()
        var cursor = start
        selected.forEach { (index, span) ->
            copied.append(text, cursor, text.getSpanStart(span))
            copied.append(rows[index].joinToString("\t"))
            cursor = text.getSpanEnd(span).coerceAtMost(end)
        }
        copied.append(text, cursor, end)
        return copied.toString()
    }
}

/**
 * The plain text of each table row's cells, in the order Markwon renders a `TableRowSpan` for
 * them. [markdown] must be exactly what was rendered with [markwon].
 */
internal fun renderedTableRows(markwon: Markwon, markdown: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    fun visit(node: Node) {
        if (node is TableRow) {
            val cells = node.children().filterIsInstance<TableCell>().map { it.plainText().trim() }
            if (cells.isNotEmpty()) rows += cells
            return
        }
        node.children().forEach(::visit)
    }
    visit(markwon.parse(markdown))
    return rows
}

private fun Node.children(): List<Node> = generateSequence(firstChild) { it.next }.toList()

private fun Node.plainText(): String {
    val output = StringBuilder()
    fun collect(node: Node) {
        when (node) {
            is Text -> output.append(node.literal)
            is Code -> output.append(node.literal)
            is SoftLineBreak, is HardLineBreak -> output.append(' ')
            else -> node.children().forEach(::collect)
        }
    }
    collect(this)
    return output.toString()
}
