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

    @Test
    fun cardLinksOnTheAccountServerAreRecognized() {
        val server = "https://cloud.example.com/nextcloud/"
        val expected = DeckCardLink(boardId = 4, cardId = 17)
        assertEquals(
            expected,
            NextcloudDeck.parseCardLink(NextcloudDeck.cardUrl(server, 4, 17), server)
        )
        assertEquals(
            expected,
            NextcloudDeck.parseCardLink(
                "https://cloud.example.com/nextcloud/index.php/apps/deck/#/board/4/card/17",
                server
            )
        )
        assertEquals(
            expected,
            NextcloudDeck.parseCardLink(
                "https://cloud.example.com/nextcloud/apps/deck/board/4/card/17/details",
                server
            )
        )
    }

    @Test
    fun otherLinksAreNotDeckCardLinks() {
        val server = "https://cloud.example.com"
        listOf(
            "https://other.example.com/apps/deck/#/board/4/card/17",
            "https://cloud.example.com.evil.test/apps/deck/#/board/4/card/17",
            "https://cloud.example.com/apps/deck/#/board/4",
            "https://cloud.example.com/apps/deck/#/board/0/card/17",
            "https://cloud.example.com/apps/notes/#/board/4/card/17",
            "https://cloud.example.com/apps/deck/#/board/4/card/x"
        ).forEach { assertNull(it, NextcloudDeck.parseCardLink(it, server)) }
        assertNull(NextcloudDeck.parseCardLink("https://cloud.example.com/apps/deck/", ""))
    }
}
