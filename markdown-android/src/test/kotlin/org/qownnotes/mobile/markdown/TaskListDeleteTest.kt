package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskListDeleteTest {
    @Test
    fun deletesParentWithAllNestedContentButNotSurroundingText() {
        val markdown = "# Tasks\n\n- [ ] parent\n  continuation\n  - plain child\n" +
            "  - [x] child\n    - [-] grandchild\n- [ ] sibling\n\nAfter\n"
        assertEquals("# Tasks\n\n- [ ] sibling\n\nAfter\n", deleteTaskListItem(markdown, 0))
    }

    @Test
    fun deletesChildWithoutItsParentOrSibling() {
        val markdown = "- [ ] parent\n  - [ ] child\n    more\n" +
            "    - [x] grandchild\n  - [ ] sibling\n"
        assertEquals("- [ ] parent\n  - [ ] sibling\n", deleteTaskListItem(markdown, 1))
    }

    @Test
    fun deletesSingleTaskAndPreservesFrontmatter() {
        val markdown = "---\ntitle: tasks\n---\n- [-] only\n  detail\n"
        assertEquals("---\ntitle: tasks\n---\n", deleteTaskListItem(markdown, 0))
        assertEquals("", deleteTaskListItem("- [x] only", 0))
    }

    @Test
    fun keepsBlankLinesPlainSiblingsAndOrderedNumbering() {
        val markdown = "1. [ ] first\n\n2. plain\n\n3. [ ] last\n\nAfter"
        assertEquals("1. [ ] first\n\n2. plain\n\n\nAfter", deleteTaskListItem(markdown, 1))
    }

    @Test
    fun includesLazyContinuationButStopsAtSeparateParagraph() {
        val markdown = "- [ ] task\nlazy continuation\n\nSeparate paragraph\n"
        assertEquals("\nSeparate paragraph\n", deleteTaskListItem(markdown, 0))
    }

    @Test
    fun removesNestedCodeWithParentButKeepsNextTask() {
        val markdown = "- [ ] parent\n  ```\n  - [ ] code\n  ```\n- [ ] next\n"
        assertEquals("- [ ] next\n", deleteTaskListItem(markdown, 0))
    }

    @Test
    fun ignoresFakeTasksAndRejectsUnsupportedOrMissingIndices() {
        val markdown = "```\n- [ ] code\n```\n<!--\n- [ ] hidden\n-->\n\n- [ ] real\n"
        assertEquals(markdown.removeSuffix("- [ ] real\n"), deleteTaskListItem(markdown, 0))
        assertNull(deleteTaskListItem(markdown, -1))
        assertNull(deleteTaskListItem(markdown, 1))
        assertNull(deleteTaskListItem("> - [ ] quoted", 0))
    }
}
