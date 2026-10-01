package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.annotations.SerializedName
import java.net.HttpURLConnection
import java.time.Instant
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.DeckBoard
import org.qownnotes.mobile.core.DeckCard
import org.qownnotes.mobile.core.DeckCardDraft
import org.qownnotes.mobile.core.DeckStack
import org.qownnotes.mobile.core.DeckStackTarget
import org.qownnotes.mobile.core.NextcloudDeck
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

internal const val DECK_ENDPOINT = "/index.php/apps/deck/api/v1.1/"

/**
 * The Deck REST API requires `OCS-APIRequest: true` and JSON bodies. Nextcloud Files adds that
 * header to every Single Sign-On request itself and rejects a request that already carries it, so
 * it must not be declared here.
 */
internal interface DeckApi {
    @Headers("Accept: application/json")
    @GET("boards?details=true")
    fun getBoards(): Call<List<DeckBoardDto>>

    @Headers("Accept: application/json")
    @GET("boards/{boardId}/stacks")
    fun getStacks(@Path("boardId") boardId: Long): Call<List<DeckStackDto>>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("boards/{boardId}/stacks/{stackId}/cards")
    fun createCard(
        @Path("boardId") boardId: Long,
        @Path("stackId") stackId: Long,
        @Body request: DeckCardWriteDto
    ): Call<DeckCardDto>
}

internal fun loadDeckBoardsWithApi(deckApi: DeckApi): List<DeckBoard> {
    val boards = deckApi.getBoards().execute().deckBody("boards", missingDeckIsUnavailable = true)
    return boards
        .filter { board ->
            !board.archived && board.deletedAt == 0L && board.permissions?.edit != false
        }
        .map { board ->
            val id = board.id ?: throw BackendException.Protocol("Deck board is missing its id")
            // Boards include their lists when details are requested; older servers may omit them.
            val stacks = board.stacks
                ?: deckApi.getStacks(
                    id
                ).execute().deckBody("lists", missingDeckIsUnavailable = true)
            DeckBoard(
                id = id,
                title = board.title?.takeIf(String::isNotBlank) ?: "Board $id",
                stacks = stacks
                    .filter { it.deletedAt == 0L }
                    .sortedWith(
                        compareBy<DeckStackDto> {
                            it.order ?: Int.MAX_VALUE
                        }.thenBy { it.id }
                    )
                    .map { stack ->
                        val stackId = stack.id
                            ?: throw BackendException.Protocol("Deck list is missing its id")
                        DeckStack(
                            stackId,
                            stack.title?.takeIf(String::isNotBlank) ?: "List $stackId"
                        )
                    }
            )
        }
        .sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, DeckBoard::title).thenBy(DeckBoard::id)
        )
}

internal fun createDeckCardWithApi(
    deckApi: DeckApi,
    target: DeckStackTarget,
    card: DeckCardDraft
): DeckCard {
    val title = NextcloudDeck.cardTitle(card.title)
        ?.takeIf { it.length <= NextcloudDeck.MAX_CARD_TITLE_LENGTH }
        ?: throw IllegalArgumentException(
            "Card titles must be 1 to ${NextcloudDeck.MAX_CARD_TITLE_LENGTH} characters"
        )
    val request = DeckCardWriteDto(
        title = title,
        type = "plain",
        order = 0,
        description = card.description,
        duedate = card.dueAtEpochSeconds?.let { Instant.ofEpochSecond(it).toString() }
    )
    val response = deckApi.createCard(target.boardId, target.stackId, request).execute()
    if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        throw BackendException.FeatureUnavailable(
            "The Deck list no longer exists, or the Deck app is not enabled on Nextcloud"
        )
    }
    val created = response.deckBody("card")
    return DeckCard(
        id = created.id ?: throw BackendException.Protocol("Deck card is missing its id"),
        boardId = target.boardId,
        stackId = created.stackId ?: target.stackId,
        title = created.title?.takeIf(String::isNotBlank) ?: title
    )
}

private fun <T> Response<T>.deckBody(
    description: String,
    missingDeckIsUnavailable: Boolean = false
): T {
    if (!isSuccessful) {
        val status = code()
        if (missingDeckIsUnavailable && status == HttpURLConnection.HTTP_NOT_FOUND) {
            throw BackendException.FeatureUnavailable(
                "Install and enable the Deck app on Nextcloud"
            )
        }
        if (status == HttpURLConnection.HTTP_BAD_REQUEST) {
            throw BackendException.Protocol("Nextcloud Deck rejected the $description")
        }
        throw backendExceptionForHttpStatus(status, DeckHttpException(status))
    }
    return body() ?: throw BackendException.Protocol("Nextcloud Deck returned empty $description")
}

private class DeckHttpException(statusCode: Int) :
    Exception("Nextcloud Deck returned HTTP $statusCode")

internal data class DeckBoardDto(
    val id: Long? = null,
    val title: String? = null,
    val archived: Boolean = false,
    val deletedAt: Long = 0,
    val permissions: DeckPermissionsDto? = null,
    val stacks: List<DeckStackDto>? = null
)

internal data class DeckPermissionsDto(@SerializedName("PERMISSION_EDIT") val edit: Boolean? = null)

internal data class DeckStackDto(
    val id: Long? = null,
    val title: String? = null,
    val order: Int? = null,
    val deletedAt: Long = 0
)

internal data class DeckCardWriteDto(
    val title: String,
    val type: String,
    val order: Int,
    val description: String,
    val duedate: String?
)

internal data class DeckCardDto(
    val id: Long? = null,
    val stackId: Long? = null,
    val title: String? = null
)
