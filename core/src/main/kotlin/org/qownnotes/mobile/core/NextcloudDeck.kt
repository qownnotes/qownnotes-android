package org.qownnotes.mobile.core

/** A Nextcloud Deck board the user can add cards to, with its lists in display order. */
data class DeckBoard(val id: Long, val title: String, val stacks: List<DeckStack>)

/** A Deck list, which the Deck API calls a stack. */
data class DeckStack(val id: Long, val title: String)

/** Where new cards are created: one list of one board. */
data class DeckStackTarget(val boardId: Long, val stackId: Long)

/** A card to create. [dueAtEpochSeconds] is an absolute instant, or `null` for no due date. */
data class DeckCardDraft(
    val title: String,
    val description: String = "",
    val dueAtEpochSeconds: Long? = null
)

/** A card the server created. */
data class DeckCard(val id: Long, val boardId: Long, val stackId: Long, val title: String)

/** Optional Nextcloud Deck access for linking notes to cards. */
interface NoteDeckBackend {
    /** Boards that are neither archived nor deleted, each with its lists that are not deleted. */
    suspend fun deckBoards(account: Account): List<DeckBoard>

    suspend fun createDeckCard(
        account: Account,
        target: DeckStackTarget,
        card: DeckCardDraft
    ): DeckCard
}

/**
 * Card titles and links compatible with the QOwnNotes desktop Deck integration.
 *
 * The desktop application links cards with the Deck web route without `index.php`, because that
 * prefix breaks Deck deep links. The desktop application recognizes card links by this route.
 */
object NextcloudDeck {
    /** Deck rejects longer card titles with HTTP 400. */
    const val MAX_CARD_TITLE_LENGTH = 255

    /** A single-line, trimmed title, or `null` when nothing usable remains. */
    fun cardTitle(value: String): String? = value
        .replace(LINE_BREAKS, " ")
        .trim()
        .takeIf(String::isNotEmpty)

    fun isValidCardTitle(value: String): Boolean =
        cardTitle(value)?.let { it.length <= MAX_CARD_TITLE_LENGTH } ?: false

    fun cardUrl(serverUrl: String, boardId: Long, cardId: Long): String =
        "${serverUrl.trimEnd('/')}/apps/deck/#/board/$boardId/card/$cardId"

    /** The Markdown link inserted into a note, `[title](card URL)`. */
    fun cardMarkdownLink(serverUrl: String, card: DeckCard): String {
        val label = (cardTitle(card.title) ?: card.id.toString())
            .replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("]", "\\]")
        return "[$label](${cardUrl(serverUrl, card.boardId, card.id)})"
    }

    private val LINE_BREAKS = Regex("[\\r\\n]+")
}
