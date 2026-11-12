package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskListBlockTest {
    @Test
    fun copiesParentWithAllNestedContentButNotSurroundingText() {
        val markdown = "# Tasks\n\n- [ ] parent\n  continuation\n  - plain child\n" +
            "  - [x] child\n    - [-] grandchild\n- [ ] sibling\n\nAfter\n"
        assertEquals(
            "- [ ] parent\n  continuation\n  - plain child\n  - [x] child\n    - [-] grandchild",
            taskListItemBlock(markdown, 0)
        )
    }

    @Test
    fun removesTheIndentationOfANestedTask() {
        val markdown = "- [ ] parent\n  - [ ] child\n    more\n" +
            "    - [x] grandchild\n  - [ ] sibling\n"
        assertEquals(
            "- [ ] child\n  more\n  - [x] grandchild",
            taskListItemBlock(markdown, 1)
        )
    }

    @Test
    fun skipsFrontmatterAndKeepsCodeOfTheBlock() {
        assertEquals(
            "- [-] only\n  detail",
            taskListItemBlock("---\ntitle: tasks\n---\n- [-] only\n  detail\n", 0)
        )
        assertEquals(
            "- [ ] parent\n  ```\n  - [ ] code\n  ```",
            taskListItemBlock("- [ ] parent\n  ```\n  - [ ] code\n  ```\n- [ ] next\n", 0)
        )
    }

    @Test
    fun rejectsUnsupportedOrMissingIndices() {
        assertNull(taskListItemBlock("- [ ] real\n", 1))
        assertNull(taskListItemBlock("- [ ] real\n", -1))
        assertNull(taskListItemBlock("> - [ ] quoted", 0))
    }
}
