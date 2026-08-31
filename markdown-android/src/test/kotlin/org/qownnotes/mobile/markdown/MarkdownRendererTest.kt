package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun convertsWikiAndBareLegacyLinksForRendering() {
        val rendered = "See [[folder/Note#Heading|Label]] and note://Other_Note".withInternalLinks()

        assertTrue(rendered.startsWith("See [Label](qon-internal:wiki:"))
        assertTrue(rendered.endsWith(" and <note://Other_Note>"))
    }

    @Test
    fun preservesInternalLinkSyntaxInsideCode() {
        val markdown = "`[[Inline]]`\n```text\n[[Fenced]]\nnote://Fenced\n```\n"

        assertEquals(markdown, markdown.withInternalLinks())
    }

    @Test
    fun preservesEscapedWikiLink() {
        val markdown = "\\[[Not a link]]"

        assertEquals(markdown, markdown.withInternalLinks())
    }
}
