package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownRendererTest {
    @Test
    fun removesCompleteYamlFrontmatter() {
        assertEquals("# Note\n", "---\ntags: [one]\n---\n# Note\n".withoutFrontmatter())
    }

    @Test
    fun preservesIncompleteFrontmatter() {
        val markdown = "---\ntags: [one]\n# Note\n"
        assertEquals(markdown, markdown.withoutFrontmatter())
    }
}
