package org.qownnotes.mobile.markdown

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun preservesInternalLinkSyntaxInsideMultilineCodeSpan() {
        val markdown = "`code starts\n[[Still code]]\ncode ends` and [[Linked]]"

        val rendered = markdown.withInternalLinks()

        assertTrue(rendered.startsWith("`code starts\n[[Still code]]\ncode ends` and [Linked]("))
    }

    @Test
    fun preservesEscapedWikiLink() {
        val markdown = "\\[[Not a link]]"

        assertEquals(markdown, markdown.withInternalLinks())
    }

    @Test
    fun normalizesSupportedFenceLanguagesAndRejectsUnknownOnes() {
        assertEquals("cpp", normalizeFenceLanguage(" C++ title=Example "))
        assertEquals("yaml", normalizeFenceLanguage("yml"))
        assertEquals("markup", normalizeFenceLanguage("HTML"))
        assertNull(normalizeFenceLanguage("unknown-language"))
        assertNull(normalizeFenceLanguage(null))
    }

    @Test
    fun acceptsOnlyCredentialFreeHttpsImageUrls() {
        assertEquals("https", requireSafeHttpsUrl("https://example.com/image.png").scheme)
        assertThrows(IllegalArgumentException::class.java) {
            requireSafeHttpsUrl("http://example.com/image.png")
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireSafeHttpsUrl("https://user:secret@example.com/image.png")
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireSafeHttpsUrl("file:///sdcard/image.png")
        }
    }

    @Test
    fun blocksNonPublicImageAddresses() {
        assertTrue(isPublicImageAddress(InetAddress.getByName("8.8.8.8")))
        assertFalse(isPublicImageAddress(InetAddress.getByName("127.0.0.1")))
        assertFalse(isPublicImageAddress(InetAddress.getByName("10.0.0.1")))
        assertFalse(isPublicImageAddress(InetAddress.getByName("169.254.1.1")))
        assertFalse(isPublicImageAddress(InetAddress.getByName("::1")))
        assertFalse(isPublicImageAddress(InetAddress.getByName("fc00::1")))
    }
}
