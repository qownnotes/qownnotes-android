package org.qownnotes.mobile.backend.nextcloud

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.nextcloud.android.sso.api.ParsedResponse
import io.reactivex.Observable
import java.net.HttpURLConnection
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteShare
import org.qownnotes.mobile.core.NoteShareChange
import org.qownnotes.mobile.core.NoteShareStatus
import org.qownnotes.mobile.core.NoteSharing
import org.qownnotes.mobile.core.NoteSharingPolicy
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

internal const val SHARE_ENDPOINT = "/ocs/v2.php/apps/files_sharing/api/v1/"

/**
 * The Nextcloud OCS Share API used by the desktop application's share dialog. Like Deck, it needs
 * `OCS-APIRequest: true`, which Nextcloud Files adds to every Single Sign-On request itself.
 * Bodies are JSON objects because SSO serializes `@Body` values with Gson.
 */
internal interface ShareApi {
    @Headers("Accept: application/json")
    @GET("shares?format=json&reshares=false&subfiles=false")
    fun getShares(@Query("path") path: String): Call<OcsSharesResponse>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("shares?format=json")
    fun createShare(@Body request: JsonObject): Call<OcsShareResponse>

    @Headers("Accept: application/json", "Content-Type: application/json")
    @PUT("shares/{id}?format=json")
    fun updateShare(@Path("id") id: String, @Body request: JsonObject): Call<OcsShareResponse>

    @Headers("Accept: application/json")
    @DELETE("shares/{id}?format=json")
    fun deleteShare(@Path("id") id: String): Call<JsonObject>
}

/** Public link sharing from the `files_sharing` and `password_policy` capabilities. */
internal fun noteSharingPolicy(
    observable: Observable<ParsedResponse<OcsResponse>>
): NoteSharingPolicy {
    val response = observable.blockingSingle().response
        ?: throw BackendException.Protocol("Nextcloud returned an empty capabilities response")
    val capabilities = response.ocs?.data?.capabilities
        ?: throw BackendException.Protocol("Nextcloud returned a malformed capabilities response")
    val sharing = capabilities.objectAt("files_sharing")
        ?: return NoteSharingPolicy(linkSharingEnabled = false)
    val public = sharing.objectAt("public")
    val password = public?.objectAt("password")
    return NoteSharingPolicy(
        linkSharingEnabled = sharing.booleanAt("api_enabled") != false &&
            public?.booleanAt("enabled") == true,
        passwordEnforced = password?.booleanAt("enforced") == true,
        passwordMinLength = capabilities.objectAt("password_policy")
            ?.get("minLength")
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asInt }.getOrNull() }
            ?.takeIf { it in 1..MAX_PASSWORD_LENGTH }
    )
}

internal fun loadNoteShareWithApis(
    capabilitiesApi: CapabilitiesApi,
    notesApi: NotesApi,
    shareApi: ShareApi,
    note: Note
): NoteShareStatus {
    val policy = noteSharingPolicy(capabilitiesApi.getCapabilities())
    return NoteShareStatus(policy, findLinkShare(shareApi, notePath(notesApi, note)))
}

/**
 * Creates a public link, or reuses the one the note already has so that retrying a request whose
 * response was lost does not leave a second link behind. A reused link gets the requested
 * password, so it is never less protected than asked for.
 */
internal fun createNoteShareWithApis(
    notesApi: NotesApi,
    shareApi: ShareApi,
    note: Note,
    password: String?
): NoteShare {
    password?.let(::validatedPassword)
    val path = notePath(notesApi, note)
    findLinkShare(shareApi, path)?.let { existing ->
        return if (password == null) {
            existing
        } else {
            updateNoteShareWithApi(shareApi, existing, NoteShareChange.Password(password))
        }
    }
    val request = JsonObject().apply {
        addProperty("path", path)
        addProperty("shareType", SHARE_TYPE_LINK)
        password?.let { addProperty("password", it) }
    }
    return shareApi.createShare(request).execute().ocsBody().ocs?.data?.toDomain()
        ?: throw BackendException.Protocol("Nextcloud returned an empty share")
}

internal fun updateNoteShareWithApi(
    shareApi: ShareApi,
    share: NoteShare,
    change: NoteShareChange
): NoteShare {
    val request = JsonObject().apply {
        when (change) {
            is NoteShareChange.EditAllowed -> addProperty(
                "permissions",
                NoteSharing.permissionsWithEdit(share.permissions, change.allowed)
            )
            is NoteShareChange.Password -> addProperty(
                "password",
                validatedPassword(change.password)
            )
            NoteShareChange.RemovePassword -> addProperty("password", "")
        }
    }
    val updated = shareApi.updateShare(validatedShareId(share.id), request).execute().ocsBody()
        .ocs?.data?.toDomain()
        ?: throw BackendException.Protocol("Nextcloud returned an empty share")
    if (updated.id != share.id) {
        throw BackendException.Protocol("Nextcloud returned a different share")
    }
    return updated
}

/** Deleting a link that is already gone succeeds, because the outcome is the same. */
internal fun deleteNoteShareWithApi(shareApi: ShareApi, share: NoteShare) {
    val response = shareApi.deleteShare(validatedShareId(share.id)).execute()
    if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) return
    response.ocsBody()
}

private fun findLinkShare(shareApi: ShareApi, path: String): NoteShare? =
    shareApi.getShares(path).execute().ocsBody().ocs?.data.orEmpty()
        .filter {
            it.shareType == SHARE_TYPE_LINK && (it.itemType == null || it.itemType == "file")
        }
        .map { it.toDomain() }
        .minWithOrNull(
            compareBy<NoteShare> {
                it.id.toLongOrNull() ?: Long.MAX_VALUE
            }.thenBy { it.id }
        )

/** The note file's path below the user's root folder, which the Share API addresses files by. */
private fun notePath(notesApi: NotesApi, note: Note): String {
    val remoteId = note.remoteId
        ?: throw BackendException.FeatureUnavailable("Notes can be shared after they are synced")
    val response = notesApi.getNote(remoteId).execute()
    if (response.code() == HttpURLConnection.HTTP_NOT_FOUND) throw BackendException.RemoteMissing()
    if (!response.isSuccessful) {
        throw backendExceptionForHttpStatus(response.code(), ShareHttpException(response.code()))
    }
    val path = response.body()?.internalPath
        ?: throw BackendException.Protocol("Nextcloud note is missing its internal path")
    if (!path.startsWith('/') || path.endsWith('/') ||
        path.split('/').drop(1).any { it.isBlank() || it == "." || it == ".." || '\\' in it }
    ) {
        throw BackendException.Protocol("Nextcloud returned an invalid note path")
    }
    return path
}

private fun validatedShareId(id: String): String {
    if (!SHARE_ID.matches(id)) throw BackendException.Protocol("Invalid Nextcloud share id")
    return id
}

private fun validatedPassword(password: String): String {
    require(password.isNotEmpty() && password.none { it == '\r' || it == '\n' }) {
        "Invalid share password"
    }
    return password
}

private fun ShareDto.toDomain(): NoteShare {
    val shareId = id?.trim()?.takeIf(SHARE_ID::matches)
        ?: throw BackendException.Protocol("Nextcloud share is missing its id")
    val link = url?.trim()?.takeIf { candidate ->
        (candidate.startsWith("https://") || candidate.startsWith("http://")) &&
            candidate.none { it.isWhitespace() || Character.isISOControl(it) }
    } ?: throw BackendException.Protocol("Nextcloud share is missing its link")
    return NoteShare(
        id = shareId,
        url = link,
        permissions = permissions ?: NoteSharing.PERMISSION_READ,
        // The server reports a link's password hash here and omits it for unprotected links.
        passwordProtected = !password.isNullOrEmpty() || !shareWith.isNullOrEmpty(),
        expiresOn = expiration?.trim()?.take(10)?.takeIf(EXPIRATION_DATE::matches)
    )
}

private fun <T> Response<T>.ocsBody(): T {
    if (isSuccessful) {
        return body() ?: throw BackendException.Protocol("Nextcloud returned an empty response")
    }
    val status = code()
    val detail = errorBody()?.string()
    // SSO reports JSON conversion failures as HTTP 900; see the Deck integration.
    if (status == 900 && detail != null && JSON_FAILURES.any(detail::contains)) {
        throw BackendException.Protocol("Nextcloud returned a malformed sharing response")
    }
    val message = ocsErrorMessage(detail)
    val cause = ShareHttpException(status)
    throw when {
        status == HttpURLConnection.HTTP_BAD_REQUEST -> BackendException.Rejected(message, cause)
        status == HttpURLConnection.HTTP_FORBIDDEN && message != null ->
            BackendException.Rejected(message, cause)
        status == HttpURLConnection.HTTP_NOT_FOUND -> BackendException.RemoteMissing(cause)
        else -> backendExceptionForHttpStatus(status, cause)
    }
}

/**
 * The `ocs.meta.message` of an OCS error. Through SSO the body arrives after an exception class
 * name, so parsing starts at the first brace. Only this message is shown, never the raw body.
 */
internal fun ocsErrorMessage(body: String?): String? {
    val start = body?.indexOf('{')?.takeIf { it >= 0 } ?: return null
    return try {
        JsonParser.parseString(body.substring(start)).asJsonObject
            .objectAt("ocs")?.objectAt("meta")?.get("message")
            ?.takeIf { it.isJsonPrimitive }?.asString
            ?.filterNot(Character::isISOControl)
            ?.trim()
            ?.take(MAX_SERVER_MESSAGE_LENGTH)
            ?.takeIf(String::isNotEmpty)
    } catch (_: RuntimeException) {
        null
    }
}

private fun JsonObject.objectAt(name: String): JsonObject? =
    get(name)?.takeIf(JsonElement::isJsonObject)?.asJsonObject

private fun JsonObject.booleanAt(name: String): Boolean? = get(name)
    ?.takeIf(JsonElement::isJsonPrimitive)
    ?.asJsonPrimitive
    ?.let { if (it.isBoolean) it.asBoolean else it.asString.toBooleanStrictOrNull() }

private class ShareHttpException(statusCode: Int) :
    Exception("Nextcloud sharing returned HTTP $statusCode")

internal data class OcsSharesResponse(val ocs: OcsShares? = null)

internal data class OcsShares(val data: List<ShareDto>? = null)

internal data class OcsShareResponse(val ocs: OcsShare? = null)

internal data class OcsShare(val data: ShareDto? = null)

internal data class ShareDto(
    val id: String? = null,
    @SerializedName("share_type") val shareType: Int? = null,
    @SerializedName("item_type") val itemType: String? = null,
    val url: String? = null,
    val permissions: Int? = null,
    val password: String? = null,
    @SerializedName("share_with") val shareWith: String? = null,
    val expiration: String? = null
)

private const val SHARE_TYPE_LINK = 3
private const val MAX_PASSWORD_LENGTH = 1_024
private const val MAX_SERVER_MESSAGE_LENGTH = 300
private val SHARE_ID = Regex("\\d{1,19}")
private val EXPIRATION_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
private val JSON_FAILURES =
    listOf("JsonSyntaxException", "MalformedJsonException", "JsonParseException")
