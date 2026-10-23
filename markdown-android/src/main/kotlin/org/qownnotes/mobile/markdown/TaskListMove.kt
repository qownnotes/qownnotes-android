package org.qownnotes.mobile.markdown

/**
 * The task-list items of a note that can be reordered.
 *
 * Task indices count every task-list item in document order, exactly as [toggleTaskListItem] and
 * the rendered task spans do. Each group holds the indices of tasks that are siblings in one list;
 * only groups with at least two tasks are reported.
 */
internal class TaskListStructure(val taskCount: Int, val groups: List<List<Int>>)

internal fun taskListStructure(markdown: String): TaskListStructure {
    val parsed = parseTaskList(markdown)
    return TaskListStructure(
        parsed.taskCount,
        parsed.groups.map { group -> group.map(SourceTask::index) }
    )
}

/**
 * Moves the task with [taskIndex], together with its nested items and continuation lines, into
 * the position of its sibling [targetTaskIndex] in the same list.
 *
 * Only the order of the sibling task blocks changes. Everything between them, such as blank lines
 * and non-task items of the same list, stays where it is, and ordered lists keep their numbering.
 * Returns `null` when the tasks are not siblings in one list or the move would change nothing.
 */
fun moveTaskListItem(markdown: String, taskIndex: Int, targetTaskIndex: Int): String? {
    if (taskIndex == targetTaskIndex) return null
    val parsed = parseTaskList(markdown)
    val group = parsed.groups.firstOrNull { tasks -> tasks.any { it.index == taskIndex } }
        ?: return null
    val from = group.indexOfFirst { it.index == taskIndex }
    val to = group.indexOfFirst { it.index == targetTaskIndex }
    if (to < 0) return null
    val order = group.toMutableList().apply { add(to, removeAt(from)) }
    val lines = parsed.lines
    val output = ArrayList<String>(lines.size)
    output.addAll(lines.subList(0, group.first().line))
    group.forEachIndexed { slot, original ->
        val moved = order[slot]
        output.add(lines[moved.line].withListNumberOf(lines[original.line]))
        output.addAll(lines.subList(moved.line + 1, moved.end))
        val gapEnd = group.getOrNull(slot + 1)?.line ?: original.end
        output.addAll(lines.subList(original.end, gapEnd))
    }
    output.addAll(lines.subList(group.last().end, lines.size))
    return markdown.substring(0, parsed.bodyStart) + output.joinToString("\n")
}

private class SourceTask(val index: Int, val line: Int, val end: Int)

private class ParsedTaskList(
    val bodyStart: Int,
    val lines: List<String>,
    val taskCount: Int,
    val groups: List<List<SourceTask>>
)

private class ListItemLine(val indent: Int, val contentIndent: Int, val kind: String)

private fun parseTaskList(markdown: String): ParsedTaskList {
    val bodyStart = markdown.bodyStartAfterFrontmatter()
    val lines = markdown.substring(bodyStart).split('\n')
    val code = BooleanArray(lines.size)
    val taskAtLine = HashMap<Int, Int>()
    var fenceCharacter: Char? = null
    var fenceLength = 0
    // This mirrors `toggleTaskListItem` so that both agree on every task's index.
    lines.forEachIndexed { index, line ->
        val fence = line.fenceMarker()
        if (fenceCharacter != null) {
            code[index] = true
            if (fence != null && fence.first == fenceCharacter && fence.second >= fenceLength &&
                line.dropWhile { it == ' ' }.drop(fence.second).isBlank()
            ) {
                fenceCharacter = null
                fenceLength = 0
            }
        } else if (fence != null) {
            code[index] = true
            fenceCharacter = fence.first
            fenceLength = fence.second
        } else if (TASK_LIST_MARKER.containsMatchIn(line)) {
            taskAtLine[index] = taskAtLine.size
        }
    }

    val grouped = HashSet<Int>()
    val groups = mutableListOf<List<SourceTask>>()
    taskAtLine.keys.sorted().forEach { line ->
        if (line in grouped) return@forEach
        // Quoted tasks have no list item of their own here and are never moved.
        val first = lines[line].listItem() ?: return@forEach
        val firstEnd = lines.blockEnd(line, first.contentIndent, code)
        val group = mutableListOf(SourceTask(taskAtLine.getValue(line), line, firstEnd))
        var cursor = group.last().end
        while (true) {
            while (cursor < lines.size && lines[cursor].isBlank()) cursor++
            if (cursor >= lines.size || code[cursor]) break
            val item = lines[cursor].listItem() ?: break
            if (item.indent != first.indent || item.kind != first.kind) break
            val end = lines.blockEnd(cursor, item.contentIndent, code)
            taskAtLine[cursor]?.let { group += SourceTask(it, cursor, end) }
            cursor = end
        }
        group.forEach { grouped += it.line }
        if (group.size > 1) groups += group
    }
    return ParsedTaskList(bodyStart, lines, taskAtLine.size, groups)
}

/** The exclusive end line of the list item starting at [start], without trailing blank lines. */
private fun List<String>.blockEnd(start: Int, contentIndent: Int, code: BooleanArray): Int {
    var last = start
    var index = start + 1
    while (index < size) {
        val line = this[index]
        if (line.isBlank()) {
            index++
            continue
        }
        val continuesParagraph = last == index - 1 && !code[last] && !code[index] &&
            !this[last].trimStart().startsWith("#") && line.isLazyContinuation()
        if (line.leadingColumns() < contentIndent && !continuesParagraph) break
        last = index
        index++
    }
    return last + 1
}

private fun String.isLazyContinuation(): Boolean =
    listItem() == null && fenceMarker() == null && !BLOCK_START.containsMatchIn(this)

private fun String.listItem(): ListItemLine? {
    if (BLOCK_START.containsMatchIn(this)) return null
    val match = LIST_ITEM.find(this) ?: return null
    val indent = match.groupValues[1].leadingColumns()
    val marker = match.groupValues[2]
    val markerEnd = indent + marker.length
    val spacing = match.value.leadingColumnsAfter(markerEnd)
    val contentIndent = markerEnd + if (spacing in 1..4) spacing else 1
    val kind = if (marker.first().isDigit()) "ordered" + marker.last() else marker
    return ListItemLine(indent, contentIndent, kind)
}

/** Gives an ordered item the number of the slot it moves into, when that keeps its indentation. */
private fun String.withListNumberOf(slot: String): String {
    val number = ORDERED_NUMBER.find(this) ?: return this
    val slotNumber = ORDERED_NUMBER.find(slot) ?: return this
    val digits = number.groups[1]!!
    val slotDigits = slotNumber.groupValues[1]
    if (digits.value.length != slotDigits.length) return this
    return replaceRange(digits.range, slotDigits)
}

private fun String.leadingColumns(): Int {
    var columns = 0
    for (character in this) {
        columns = when (character) {
            ' ' -> columns + 1
            '\t' -> columns + TAB_WIDTH - columns % TAB_WIDTH
            else -> return columns
        }
    }
    return columns
}

/** Columns of whitespace that follow the column [start] in this whitespace-and-marker prefix. */
private fun String.leadingColumnsAfter(start: Int): Int {
    var columns = 0
    var spacing = 0
    for (character in this) {
        val next = when (character) {
            '\t' -> columns + TAB_WIDTH - columns % TAB_WIDTH
            else -> columns + 1
        }
        if (columns >= start) spacing += next - columns
        columns = next
    }
    return spacing
}

private const val TAB_WIDTH = 4
private val LIST_ITEM = Regex("^([ \\t]*)([-+*]|\\d{1,9}[.)])([ \\t]+|$)")
private val ORDERED_NUMBER = Regex("^[ \\t]*(\\d{1,9})[.)]")
private val BLOCK_START = Regex("^ {0,3}(?:#|>|=+[ \\t]*$|([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$)")
