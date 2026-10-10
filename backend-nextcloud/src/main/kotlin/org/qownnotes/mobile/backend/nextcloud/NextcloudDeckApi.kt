package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.nextcloud.android.sso.api.ParsedResponse
import io.reactivex.Observable
import java.net.HttpURLConnection
import java.time.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.DeckBoard
import org.qownnotes.mobile.core.DeckCard
import org.qownnotes.mobile.core.DeckCardDetails
import org.qownnotes.mobile.core.DeckCardDraft
import org.qownnotes.mobile.core.DeckCardLink
import org.qownnotes.mobile.core.DeckStack
import org.qownnotes.mobile.core.DeckStackTarget
import org.qownnotes.mobile.core.NextcloudDeck
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @Headers("Accept: application/json")
    @GET("boards/{boardId}")
    fun getBoard(@Path("boardId") boardId: Long): Call<DeckBoardDto>

    @Headers("Accept: application/json")
    @GET("boards/{boardId}/stacks/archived")
    fun getArchivedStacks(@Path("boardId") boardId: Long): Call<List<DeckStackDto>>

    @Headers("Accept: application/json")
    @GET("boards/{boardId}/stacks/{stackId}/cards/{cardId}")
    fun getCard(
        @Path("boardId") boardId: Long,
        @Path("stackId") stackId: Long,
        @Path("cardId") cardId: Long
    ): Call<DeckCardDto>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @PUT("boards/{boardId}/stacks/{stackId}/cards/{cardId}")
    fun updateCard(
        @Path("boardId") boardId: Long,
        @Path("stackId") stackId: Long,
        @Path("cardId") cardId: Long,
        @Body request: RequestBody
    ): Call<DeckCardDto>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("boards/{boardId}/stacks/{stackId}/cards")
    fun createCard(
        @Path("boardId") boardId: Long,
        @Path("stackId") stackId: Long,
        @Body request: DeckCardWriteDto
    ): Call<DeckCardDto>
}

internal fun loadDeckCardWithApi(deckApi: DeckApi, link: DeckCardLink): DeckCardDetails {
    val board = deckApi.getBoard(link.boardId).execute().deckBody("board")
    if (board.id != link.boardId || board.deletedAt != 0L) throw BackendException.RemoteMissing()
    val stacks = deckApi.getStacks(link.boardId).execute().deckBody("lists")
    val stack = stacks.firstOrNull { stack ->
        stack.deletedAt == 0L && stack.cards.orEmpty().any { it.id == link.cardId }
    } ?: deckApi.getArchivedStacks(link.boardId).execute().deckBody("archived lists")
        .firstOrNull { stack ->
            stack.deletedAt == 0L && stack.cards.orEmpty().any { it.id == link.cardId }
        } ?: throw BackendException.RemoteMissing()
    val stackId = stack.id ?: throw BackendException.Protocol("Deck list is missing its id")
    val response = deckApi.getCard(link.boardId, stackId, link.cardId).execute()
    return response.deckBody("card").details(
        link.boardId,
        stackId,
        editable = !board.archived && board.permissions?.edit == true,
        etag = response.headers()["ETag"]
    ).also {
        if (it.id != link.cardId || it.stackId != stackId) throw BackendException.RemoteMissing()
    }
}

internal fun updateDeckCardWithApi(
    deckApi: DeckApi,
    original: DeckCardDetails,
    draft: DeckCardDraft
): DeckCardDetails {
    val title = NextcloudDeck.cardTitle(draft.title)
        ?.takeIf { it.length <= NextcloudDeck.MAX_CARD_TITLE_LENGTH }
        ?: throw IllegalArgumentException("Invalid Deck card title")
    if (!original.editable) throw BackendException.Permission()
    val current = loadDeckCardWithApi(deckApi, DeckCardLink(original.boardId, original.id))
    if (!current.editable) throw BackendException.Permission()
    if (current != original) throw BackendException.Conflict()
    val request = JsonObject().apply {
        addProperty("title", title)
        addProperty("description", draft.description)
        addProperty("type", current.type)
        addProperty("owner", current.owner)
        addProperty("order", current.order)
        addProperty("archived", current.archived)
        add(
            "duedate",
            draft.dueAtEpochSeconds?.let {
                com.google.gson.JsonPrimitive(Instant.ofEpochSecond(it).toString())
            } ?: JsonNull.INSTANCE
        )
        add(
            "startdate",
            current.startDate?.let { com.google.gson.JsonPrimitive(it) }
                ?: JsonNull.INSTANCE
        )
    }
    // Gson's default converter omits JSON nulls even from a JsonObject. Explicit serialization
    // makes removing a due date clear the server value instead of silently retaining it.
    val response = deckApi.updateCard(
        current.boardId,
        current.stackId,
        current.id,
        request.toString().toRequestBody("application/json".toMediaType())
    ).execute()
    return response.deckBody("card").details(
        current.boardId,
        current.stackId,
        current.editable,
        response.headers()["ETag"]
    ).also {
        if (it.id != current.id) throw BackendException.Protocol("Deck returned a different card")
    }
}

private fun DeckCardDto.details(
    boardId: Long,
    fallbackStackId: Long,
    editable: Boolean,
    etag: String?
): DeckCardDetails {
    if (deletedAt != 0L) throw BackendException.RemoteMissing()
    return DeckCardDetails(
        id = id ?: throw BackendException.Protocol("Deck card is missing its id"),
        boardId = boardId,
        stackId = stackId ?: fallbackStackId,
        title = title ?: throw BackendException.Protocol("Deck card is missing its title"),
        description = description.orEmpty(),
        dueAtEpochSeconds = duedate?.let {
            try {
                Instant.parse(it).epochSecond
            } catch (error: Exception) {
                throw BackendException.Protocol("Deck returned an invalid due date", error)
            }
        },
        owner = owner ?: throw BackendException.Protocol("Deck card is missing its owner"),
        order = order ?: throw BackendException.Protocol("Deck card is missing its order"),
        type = type ?: throw BackendException.Protocol("Deck card is missing its type"),
        archived = archived,
        startDate = startdate,
        etag = etag ?: this.etag,
        editable = editable
    )
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
        if (status == HttpURLConnection.HTTP_NOT_FOUND) throw BackendException.RemoteMissing()
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
    val deletedAt: Long = 0,
    val cards: List<DeckCardDto>? = null
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
    val title: String? = null,
    val description: String? = null,
    val duedate: String? = null,
    val startdate: String? = null,
    val owner: String? = null,
    val order: Int? = null,
    val type: String? = null,
    val archived: Boolean = false,
    val deletedAt: Long = 0,
    @SerializedName("ETag") val etag: String? = null
)

internal const val DECK_API_VERSION = "1.1"

/**
 * Whether the capabilities list the Deck app with [DECK_API_VERSION]. Nextcloud omits the Deck
 * capability when the app is not installed, disabled, or not enabled for the user.
 */
internal fun supportsDeckApi(observable: Observable<ParsedResponse<OcsResponse>>): Boolean {
    val response = observable.blockingSingle().response
        ?: throw BackendException.Protocol("Nextcloud returned an empty capabilities response")
    val capabilities = response.ocs?.data?.capabilities
        ?: throw BackendException.Protocol("Nextcloud returned a malformed capabilities response")
    val deck = capabilities.get("deck")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
    val versions = deck.get("apiVersions")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
    return versions.any {
        it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == DECK_API_VERSION
    }
}
