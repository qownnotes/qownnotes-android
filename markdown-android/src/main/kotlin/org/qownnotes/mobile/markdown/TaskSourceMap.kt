package org.qownnotes.mobile.markdown

import io.noties.markwon.Markwon
import io.noties.markwon.ext.tasklist.TaskListItem
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Text

/**
 * Where the rendered tasks of a note come from in its Markdown source.
 *
 * Counting task-like source lines cannot tell which of them the renderer turns into checkboxes:
 * an empty `- [ ]`, an HTML comment, or a code block nested in a list are task-like lines that are
 * not rendered as tasks. The renderer's own parser is therefore asked which lines its tasks start
 * on.
 */
internal class RenderedTaskSources(
    /** For every rendered task, in rendering order, its source task index, or null if unknown. */
    val sourceIndices: List<Int?>,
    /** Source task indices of the tasks that are siblings in one rendered list. */
    val siblingGroups: Set<List<Int>>
)

internal fun Markwon.renderedTaskSources(markdown: String): RenderedTaskSources {
    val structure = taskListStructure(markdown)
    val sourceIndexAtLine = structure.taskLines.withIndex().associate { it.value to it.index }
    val lines = markdown.substring(markdown.bodyStartAfterFrontmatter()).split('\n')
    // Each task-like line is tagged at the end of its content. The tag never adds content to an
    // empty task, so it cannot turn a line into a task that the plain note does not render as one.
    val tagged = lines.mapIndexed { index, line ->
        if (!TASK_LIKE_LINE.containsMatchIn(line)) return@mapIndexed line
        val content = line.trimEnd()
        content + SOURCE_TAG_START + index + SOURCE_TAG_END + line.substring(content.length)
    }.joinToString("\n")

    val tasks = mutableListOf<Pair<TaskListItem, Int?>>()
    parse(tagged.withInternalLinks()).accept(
        object : AbstractVisitor() {
            override fun visit(customBlock: CustomBlock) {
                if (customBlock is TaskListItem) {
                    val line = customBlock.sourceLine()
                    tasks += customBlock to line?.let(sourceIndexAtLine::get)
                }
                visitChildren(customBlock)
            }
        }
    )
    val siblingGroups = tasks.groupBy({ it.first.parent }, { it.second })
        .values
        .filter { group -> group.all { it != null } }
        .map { group -> group.filterNotNull() }
        .toSet()
    return RenderedTaskSources(tasks.map { it.second }, siblingGroups)
}

/** The source line a task starts on, read from the first tag of its own content. */
private fun TaskListItem.sourceLine(): Int? {
    fun find(node: Node): Int? {
        if (node is BulletList || node is OrderedList) return null
        val literal = when (node) {
            is Text -> node.literal
            is Code -> node.literal
            else -> null
        }
        literal?.let(SOURCE_TAG::find)?.let { return it.groupValues[1].toInt() }
        var child = node.firstChild
        while (child != null) {
            find(child)?.let { return it }
            child = child.next
        }
        return null
    }
    var child = firstChild
    while (child != null) {
        find(child)?.let { return it }
        child = child.next
    }
    return null
}

private const val SOURCE_TAG_START = '\uE000'
private const val SOURCE_TAG_END = '\uE001'
private val SOURCE_TAG = Regex("$SOURCE_TAG_START(\\d+)$SOURCE_TAG_END")
private val TASK_LIKE_LINE = Regex("^(?: {0,3}>[ \\t]?)*[ \\t]*(?:[-+*]|\\d+[.)])[ \\t]+\\[[ xX-]]")
