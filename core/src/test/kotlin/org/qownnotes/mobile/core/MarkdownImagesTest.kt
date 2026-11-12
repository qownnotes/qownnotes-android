package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownImagesTest {
    private fun images(markdown: String) =
        inlineMarkdownImages(markdown).map { markdown.substring(it.start, it.end) }

    private fun destinations(markdown: String) = inlineMarkdownImages(markdown).map {
        markdown.substring(it.destinationStart, it.destinationEnd)
    }

    @Test
    fun findsInlineImagesWithTitlesAngleBracketsAndNestedBrackets() {
        val markdown = "Intro ![a](x.png) and [![b [c]](y.png \"Title\")](https://example.com)\n" +
            "![spaced](<../media/my image.png>) ![paren](z(1).png 'T') ![](empty.png)"
        assertEquals(
            listOf(
                "![a](x.png)",
                "![b [c]](y.png \"Title\")",
                "![spaced](<../media/my image.png>)",
                "![paren](z(1).png 'T')",
                "![](empty.png)"
            ),
            images(markdown)
        )
        assertEquals(
            listOf("x.png", "y.png", "../media/my image.png", "z(1).png", "empty.png"),
            destinations(markdown)
        )
    }

    @Test
    fun skipsEscapedReferenceAndBrokenImages() {
        assertEquals(
            emptyList<String>(),
            images("\\![a](x.png) ![ref][r] ![a] (x.png) ![a](x.png ![a](\n\nx.png)")
        )
    }

    @Test
    fun replacesOnlyTheDestinationOfTheExpectedImage() {
        val markdown = "![a](one.png) ![b](<two.png> \"T\")"
        assertEquals(
            "![a](one.png) ![b](<new.png> \"T\")",
            replaceMarkdownImage(markdown, 1, "![b](<two.png> \"T\")", "new.png")
        )
        assertNull(replaceMarkdownImage(markdown, 1, "![a](one.png)", "new.png"))
        assertNull(replaceMarkdownImage(markdown, 2, "![b](<two.png> \"T\")", "new.png"))
    }

    @Test
    fun insertsAnImageOnItsOwnLineAsTheNextParagraph() {
        assertEquals(
            "Text\n\n![a](x.png)\n\n![a](new.png)\n",
            insertMarkdownImageAfter("Text\n\n![a](x.png)\n", 0, "![a](x.png)", "new.png")
        )
        assertEquals(
            "![a](x.png)\n\n![a](new.png)",
            insertMarkdownImageAfter("![a](x.png)", 0, "![a](x.png)", "new.png")
        )
    }

    @Test
    fun keepsAnInsertedImageInsideItsListItemOrQuote() {
        assertEquals(
            "- [x] ![a](x.png)\n\n      ![a](new.png)\n\n- next",
            insertMarkdownImageAfter("- [x] ![a](x.png)\n- next", 0, "![a](x.png)", "new.png")
        )
        assertEquals(
            "> ![a](x.png)\n>\n> ![a](new.png)\n>\n> More",
            insertMarkdownImageAfter("> ![a](x.png)\n> More", 0, "![a](x.png)", "new.png")
        )
    }

    @Test
    fun insertsAnImageWithinTextOrTableRightBesideIt() {
        assertEquals(
            "See ![a](x.png) ![a](new.png) here",
            insertMarkdownImageAfter("See ![a](x.png) here", 0, "![a](x.png)", "new.png")
        )
        assertEquals(
            "| ![a](x.png) ![a](new.png) |",
            insertMarkdownImageAfter("| ![a](x.png) |", 0, "![a](x.png)", "new.png")
        )
    }

    @Test
    fun separatesFollowingTextFromAnInsertedImage() {
        assertEquals(
            "![a](x.png)\n\n![a](new.png)\n\nCaption",
            insertMarkdownImageAfter("![a](x.png)\nCaption", 0, "![a](x.png)", "new.png")
        )
    }
}
