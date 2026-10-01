package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextcloudDeckTest {
    @Test
    fun cardUrlUsesTheDesktopDeckRouteWithoutIndexPhp() {
        assertEquals(
            "https://cloud.example/apps/deck/#/board/3/card/42",
            NextcloudDeck.cardUrl("https://cloud.example/", 3, 42)
        )
        assertEquals(
            "https://example.org/nextcloud/apps/deck/#/board/1/card/2",
            NextcloudDeck.cardUrl("https://example.org/nextcloud", 1, 2)
        )
    }

    @Test
    fun markdownLinkEscapesTheLabel() {
        assertEquals(
            "[Fix \\[urgent\\] C:\\\\temp](https://cloud.example/apps/deck/#/board/3/card/42)",
            NextcloudDeck.cardMarkdownLink(
                "https://cloud.example",
                DeckCard(id = 42, boardId = 3, stackId = 9, title = "Fix [urgent] C:\\temp")
            )
        )
    }

    @Test
    fun markdownLinkKeepsTheLinkOnOneLine() {
        assertEquals(
            "[First line second line](https://cloud.example/apps/deck/#/board/1/card/5)",
            NextcloudDeck.cardMarkdownLink(
                "https://cloud.example",
                DeckCard(id = 5, boardId = 1, stackId = 2, title = "First line\r\nsecond line")
            )
        )
    }

    @Test
    fun cardTitleIsTrimmedSingleLineText() {
        assertEquals("Call Alice back", NextcloudDeck.cardTitle("  Call Alice\nback \n"))
        assertNull(NextcloudDeck.cardTitle(" \n\t "))
    }

    @Test
    fun cardTitleValidationFollowsTheDeckLengthLimit() {
        assertTrue(NextcloudDeck.isValidCardTitle("a".repeat(255)))
        assertFalse(NextcloudDeck.isValidCardTitle("a".repeat(256)))
        assertFalse(NextcloudDeck.isValidCardTitle("   "))
    }
}
