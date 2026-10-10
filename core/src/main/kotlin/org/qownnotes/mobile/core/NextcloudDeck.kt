package org.qownnotes.mobile.core

/** A Nextcloud Deck board with its lists in display order and the account's edit permission. */
data class DeckBoard(
    val id: Long,
    val title: String,
    val stacks: List<DeckStack>,
    val editable: Boolean = true
)

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

/** A server snapshot used to edit an existing card without resetting its other fields. */
data class DeckCardDetails(
    val id: Long,
    val boardId: Long,
    val stackId: Long,
    val title: String,
    val description: String,
    val dueAtEpochSeconds: Long?,
    val owner: String,
    val order: Int,
    val type: String,
    val archived: Boolean,
    val startDate: String? = null,
    val etag: String? = null,
    val editable: Boolean = false
)

/** Optional Nextcloud Deck access for linking notes to cards. */
interface NoteDeckBackend {
    /**
     * Whether the account's server has the Deck app with the REST API version used here. Deck
     * support is detected rather than configured, so the editor offers it only where it works.
     */
    suspend fun supportsDeck(account: Account): Boolean

    /** Boards that are neither archived nor deleted, each with its lists that are not deleted. */
    suspend fun deckBoards(account: Account): List<DeckBoard>

    suspend fun createDeckCard(
        account: Account,
        target: DeckStackTarget,
        card: DeckCardDraft
    ): DeckCard

    /** Resolves the current list, including archived cards, from a note's board/card link. */
    suspend fun deckCard(account: Account, link: DeckCardLink): DeckCardDetails

    /** Active, readable boards, including shared read-only boards. */
    suspend fun deckBrowseBoards(account: Account): List<DeckBoard>

    /** Cards of one list, optionally including archived cards from that same list. */
    suspend fun deckCards(
        account: Account,
        target: DeckStackTarget,
        includeArchived: Boolean
    ): List<DeckCardDetails>

    suspend fun archiveDeckCard(account: Account, original: DeckCardDetails)

    /** Checks [original] against the server before updating. Deck has no atomic If-Match writes. */
    suspend fun updateDeckCard(
        account: Account,
        original: DeckCardDetails,
        draft: DeckCardDraft
    ): DeckCardDetails
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

    /**
     * The card that [url] links to on [serverUrl], or `null` when it is not a Deck card link on
     * that server. Both the web route and its `index.php` variant are recognized.
     */
    fun parseCardLink(url: String, serverUrl: String): DeckCardLink? {
        val base = serverUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        val candidate = url.trim()
        if (!candidate.startsWith(base, ignoreCase = true)) return null
        val match = CARD_ROUTE.matchEntire(candidate.substring(base.length)) ?: return null
        val boardId = match.groupValues[1].toLongOrNull() ?: return null
        val cardId = match.groupValues[2].toLongOrNull() ?: return null
        if (boardId < 1 || cardId < 1) return null
        return DeckCardLink(boardId, cardId)
    }

    private val LINE_BREAKS = Regex("[\\r\\n]+")
    private val CARD_ROUTE =
        Regex("(?:/index\\.php)?/apps/deck/(?:#/)?board/(\\d{1,18})/card/(\\d{1,18})(?:[/?#].*)?")
}

/** A Deck card addressed by a link in a note. */
data class DeckCardLink(val boardId: Long, val cardId: Long)
