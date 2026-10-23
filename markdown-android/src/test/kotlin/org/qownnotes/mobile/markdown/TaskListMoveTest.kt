package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskListMoveTest {
    @Test
    fun movesATaskDownTogetherWithItsNestedItems() {
        val markdown = """
            # List
            - [ ] one
              - [x] one child
                more child text
            - [ ] two
            - [ ] three

            After
        """.trimIndent()

        assertEquals(
            """
                # List
                - [ ] two
                - [ ] three
                - [ ] one
                  - [x] one child
                    more child text

                After
            """.trimIndent(),
            moveTaskListItem(markdown, 0, 3)
        )
    }

    @Test
    fun movesATaskUpAndKeepsTheTrailingNewline() {
        val markdown = "- [ ] one\n- [x] two\n  - [ ] child\n- [ ] three\n"

        assertEquals(
            "- [x] two\n  - [ ] child\n- [ ] one\n- [ ] three\n",
            moveTaskListItem(markdown, 1, 0)
        )
    }

    @Test
    fun movesNestedTasksOnlyAmongTheirSiblings() {
        val markdown = "- [ ] parent\n  - [ ] a\n  - [ ] b\n- [ ] other\n  - [ ] c\n"

        assertEquals(
            "- [ ] parent\n  - [ ] b\n  - [ ] a\n- [ ] other\n  - [ ] c\n",
            moveTaskListItem(markdown, 1, 2)
        )
        assertNull(moveTaskListItem(markdown, 1, 4))
        assertNull(moveTaskListItem(markdown, 1, 0))
        assertEquals(
            listOf(listOf(0, 3), listOf(1, 2)),
            taskListStructure(markdown).groups.sortedBy { it.first() }
        )
    }

    @Test
    fun keepsBlankLinesAndNonTaskItemsInPlace() {
        val markdown = "- [ ] one\n\n- plain\n\n- [ ] two\n  continued\n\n- [ ] three"

        assertEquals(
            "- [ ] three\n\n- plain\n\n- [ ] one\n\n- [ ] two\n  continued",
            moveTaskListItem(markdown, 2, 0)
        )
    }

    @Test
    fun keepsOrderedListNumbering() {
        val markdown = "1. [ ] one\n2. [ ] two\n3. [ ] three"

        assertEquals("1. [ ] three\n2. [ ] one\n3. [ ] two", moveTaskListItem(markdown, 2, 0))
    }

    @Test
    fun treatsDifferentListsAsSeparate() {
        val markdown = "- [ ] a\n- [ ] b\n\nText\n\n- [ ] c\n* [ ] d\n- - -\n- [ ] e"

        assertEquals(listOf(listOf(0, 1)), taskListStructure(markdown).groups)
        assertEquals(5, taskListStructure(markdown).taskCount)
        assertNull(moveTaskListItem(markdown, 1, 2))
    }

    @Test
    fun skipsListItemsThatAreNotRenderedAsTasks() {
        val markdown = "- [ ]\n- [ ] \n- [ ]text\n-     [ ] code\n- [ ] a\n- [x] b"

        assertEquals(2, taskListStructure(markdown).taskCount)
        assertEquals(listOf(4, 5), taskListStructure(markdown).taskLines)
        assertEquals(markdown.replace("- [ ] a", "- [x] a"), toggleTaskListItem(markdown, 0))
        assertEquals(
            "- [ ]\n- [ ] \n- [ ]text\n-     [ ] code\n- [x] b\n- [ ] a",
            moveTaskListItem(markdown, 1, 0)
        )
    }

    @Test
    fun ignoresTasksInsideHtmlBlocks() {
        val markdown = "<!--\n- [ ] hidden\n-->\n<div>\n- [ ] raw\n</div>\n\n- [ ] a\n- [ ] b"

        assertEquals(listOf(7, 8), taskListStructure(markdown).taskLines)
        assertEquals(listOf(listOf(0, 1)), taskListStructure(markdown).groups)
        assertEquals(markdown.replace("- [ ] a", "- [x] a"), toggleTaskListItem(markdown, 0))
    }

    @Test
    fun endsATaskBeforeAnHtmlBlock() {
        val markdown = "- [ ] a\n- [ ] b\n<!-- note -->"

        assertEquals("- [ ] b\n- [ ] a\n<!-- note -->", moveTaskListItem(markdown, 0, 1))
    }

    @Test
    fun keepsLazyContinuationLinesWithTheirTask() {
        val markdown = "- [ ] a\nlazy\n- [ ] b"

        assertEquals("- [ ] b\n- [ ] a\nlazy", moveTaskListItem(markdown, 0, 1))
    }

    @Test
    fun countsButNeverMovesQuotedOrFencedTasks() {
        val markdown = """
            ---
            title: Tasks
            ---
            > - [ ] quoted
            > - [ ] quoted too
            ```
            - [ ] code
            ```
            - [ ] a
            - [ ] b
        """.trimIndent()

        assertEquals(4, taskListStructure(markdown).taskCount)
        assertEquals(listOf(listOf(2, 3)), taskListStructure(markdown).groups)
        assertNull(moveTaskListItem(markdown, 0, 1))
        assertEquals(
            markdown.replace("- [ ] a\n- [ ] b", "- [ ] b\n- [ ] a"),
            moveTaskListItem(markdown, 3, 2)
        )
    }
}
