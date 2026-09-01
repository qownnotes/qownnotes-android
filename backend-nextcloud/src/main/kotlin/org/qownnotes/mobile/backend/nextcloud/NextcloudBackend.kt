package org.qownnotes.mobile.backend.nextcloud

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.api.ParsedResponse
import com.nextcloud.android.sso.exceptions.NextcloudApiNotRespondingException
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountPermissionNotGrantedException
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import com.nextcloud.android.sso.exceptions.NextcloudNetworkException
import com.nextcloud.android.sso.exceptions.TokenMismatchException
import io.reactivex.Observable
import java.io.IOException
import java.net.HttpURLConnection
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendCapabilities
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.PullBackend
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import retrofit2.Call
import retrofit2.NextcloudRetrofitApiBuilder
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

class NextcloudBackend(context: Context) : PullBackend {
    private val applicationContext = context.applicationContext
    private val gson = GsonBuilder().create()

    override val capabilities =
        BackendCapabilities(categories = true, favorites = true, readOnlyNotes = true)

    override suspend fun validateAccount(account: Account): String = withContext(Dispatchers.IO) {
        try {
            withApis(account) { capabilitiesApi, _ ->
                val response = capabilitiesApi.getCapabilities().blockingSingle().response
                val notes = response.ocs.data.capabilities?.getAsJsonObject("notes")
                    ?: throw BackendException.NotesAppMissing()
                val versions = NextcloudProtocol.parseVersions(notes.get("api_version"))
                NextcloudProtocol.selectSupportedVersion(versions)
                    ?: throw BackendException.UnsupportedApi(versions)
            }
        } catch (error: Throwable) {
            throw error.asBackendException()
        }
    }

    override suspend fun pull(account: Account, checkpoint: PullCheckpoint): PullResult =
        withContext(Dispatchers.IO) {
            try {
                withApis(account) { _, notesApi -> pullFromApi(notesApi, checkpoint) }
            } catch (error: Throwable) {
                throw error.asBackendException()
            }
        }

    private fun <T> withApis(account: Account, block: (CapabilitiesApi, NotesApi) -> T): T {
        if (AccountImporter.getAccountForName(applicationContext, account.ssoAccountName) == null) {
            throw BackendException.AccountRemoved()
        }
        val ssoAccount = try {
            AccountImporter.getSingleSignOnAccount(applicationContext, account.ssoAccountName)
        } catch (error: NextcloudFilesAppAccountNotFoundException) {
            throw BackendException.AuthorizationRequired(error)
        }
        val api = NextcloudAPI(applicationContext, ssoAccount, gson)
        return try {
            block(
                NextcloudRetrofitApiBuilder(api, OCS_ENDPOINT).create(CapabilitiesApi::class.java),
                NextcloudRetrofitApiBuilder(api, NOTES_ENDPOINT).create(NotesApi::class.java)
            )
        } finally {
            api.close()
        }
    }

    private companion object {
        const val OCS_ENDPOINT = "/ocs/v2.php/cloud/"
        const val NOTES_ENDPOINT = "/index.php/apps/notes/api/v1/"
    }
}

internal object NextcloudProtocol {
    fun parseVersions(element: JsonElement?): List<String> = when {
        element == null || element.isJsonNull -> emptyList()
        element.isJsonArray -> element.asJsonArray.mapNotNull {
            it.takeIf(JsonElement::isJsonPrimitive)?.asString
        }
        element.isJsonPrimitive -> listOf(element.asString)
        else -> emptyList()
    }

    fun selectSupportedVersion(versions: List<String>): String? =
        versions.filter(::isSupported).maxWithOrNull(::compareVersions)

    private fun isSupported(version: String): Boolean {
        val parts = versionParts(version)
        return parts.firstOrNull() == 1 && parts.getOrElse(1) { 0 } >= 2
    }

    private fun versionParts(version: String) = version.split('.').map { it.toIntOrNull() ?: 0 }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        repeat(maxOf(leftParts.size, rightParts.size)) { index ->
            val comparison =
                leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}

internal data class NotesPage(
    val notes: List<RemoteNote>,
    val etag: String? = null,
    val lastModifiedEpochSeconds: Long? = null,
    val cursor: String? = null,
    val pending: String? = null
)

internal fun collectPull(
    checkpoint: PullCheckpoint,
    loadPage: (cursor: String?) -> NotesPage
): PullResult {
    val notes = mutableListOf<RemoteNote>()
    val seenCursors = mutableSetOf<String>()
    var cursor: String? = null
    var etag = checkpoint.collectionEtag
    var modified = checkpoint.lastModifiedEpochSeconds

    do {
        val page = loadPage(cursor)
        notes += page.notes
        etag = page.etag ?: etag
        modified = page.lastModifiedEpochSeconds ?: modified

        val nextCursor = page.cursor?.takeUnless(String::isBlank)
        val pending = page.pending?.toLongOrNull()
        if (page.pending != null && pending == null) {
            throw BackendException.Protocol("Invalid X-Notes-Chunk-Pending header")
        }
        if (nextCursor == null && pending != null && pending > 0) {
            throw BackendException.Protocol("Chunked response is missing its continuation cursor")
        }
        if (nextCursor != null && pending != null && pending <= 0) {
            throw BackendException.Protocol("Chunked response has an inconsistent pending count")
        }
        if (nextCursor != null && !seenCursors.add(nextCursor)) {
            throw BackendException.Protocol("Chunked response repeated a continuation cursor")
        }
        cursor = nextCursor
    } while (cursor != null)

    return PullResult(notes, etag, modified)
}

internal fun pullFromApi(notesApi: NotesApi, checkpoint: PullCheckpoint): PullResult {
    var pageRequests = 0
    return try {
        collectPull(checkpoint) { cursor ->
            pageRequests++
            val response =
                if (cursor == null) {
                    notesApi.getNotes(
                        checkpoint.lastModifiedEpochSeconds,
                        checkpoint.collectionEtag,
                        NOTES_CHUNK_SIZE
                    ).execute()
                } else {
                    notesApi.getNotesChunk(
                        checkpoint.lastModifiedEpochSeconds,
                        checkpoint.collectionEtag,
                        NOTES_CHUNK_SIZE,
                        cursor
                    ).execute()
                }
            response.toNotesPage()
        }
    } catch (error: Throwable) {
        val cause = error.unwrap()
        if (cause is CancellationException) throw cause
        if (cause is NotesHttpException &&
            cause.statusCode == HttpURLConnection.HTTP_NOT_MODIFIED &&
            pageRequests == 1
        ) {
            PullResult(
                emptyList(),
                checkpoint.collectionEtag,
                checkpoint.lastModifiedEpochSeconds,
                true
            )
        } else {
            throw cause.toBackendException()
        }
    }
}

private fun Response<List<RemoteNoteDto>>.toNotesPage(): NotesPage {
    if (!isSuccessful) throw NotesHttpException(code())
    val notes = body() ?: throw BackendException.Protocol("Nextcloud returned an empty response")
    return NotesPage(
        notes = notes.map(RemoteNoteDto::toDomain),
        etag = headers()["ETag"],
        lastModifiedEpochSeconds = headers()["Last-Modified"]?.toEpochSeconds(),
        cursor = headers()["X-Notes-Chunk-Cursor"],
        pending = headers()["X-Notes-Chunk-Pending"]
    )
}

private fun String.toEpochSeconds(): Long = try {
    ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
} catch (error: RuntimeException) {
    throw BackendException.Protocol("Invalid Last-Modified header", error)
}

private tailrec fun Throwable.unwrap(): Throwable =
    if (this::class == RuntimeException::class && cause != null) {
        cause!!.unwrap()
    } else {
        this
    }

private fun Throwable.asBackendException(): BackendException {
    val cause = unwrap()
    if (cause is CancellationException) throw cause
    return cause.toBackendException()
}

internal fun Throwable.toBackendException(): BackendException = when (this) {
    is BackendException -> this
    is NotesHttpException -> backendExceptionForHttpStatus(statusCode, this)
    is NextcloudHttpRequestFailedException -> backendExceptionForHttpStatus(statusCode, this)
    is TokenMismatchException -> BackendException.Authentication(this)
    is NextcloudFilesAppAccountPermissionNotGrantedException ->
        BackendException.AuthorizationRequired(this)
    is NextcloudFilesAppAccountNotFoundException -> BackendException.AccountRemoved(this)
    is NextcloudApiNotRespondingException, is NextcloudNetworkException ->
        BackendException.Retryable(this)
    is JsonParseException -> BackendException.Protocol("Nextcloud returned malformed JSON", this)
    is MalformedJsonException -> BackendException.Protocol(
        "Nextcloud returned malformed JSON",
        this
    )
    is IOException -> BackendException.Retryable(this)
    else -> BackendException.Protocol(message ?: "Nextcloud request failed", this)
}

internal fun backendExceptionForHttpStatus(statusCode: Int, cause: Throwable): BackendException =
    when (statusCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> BackendException.Authentication(cause)
        HttpURLConnection.HTTP_FORBIDDEN -> BackendException.Permission(cause)
        SSO_TRANSPORT_ERROR, in 500..599 -> BackendException.Retryable(cause)
        else -> BackendException.Protocol("Nextcloud returned HTTP $statusCode", cause)
    }

private class NotesHttpException(val statusCode: Int) :
    Exception("Nextcloud returned HTTP $statusCode")

private const val NOTES_CHUNK_SIZE = 200
private const val SSO_TRANSPORT_ERROR = 900

private interface CapabilitiesApi {
    @GET("capabilities?format=json")
    fun getCapabilities(
        @Header("OCS-APIRequest") ocsRequest: String = "true"
    ): Observable<ParsedResponse<OcsResponse>>
}

internal interface NotesApi {
    @GET("notes")
    fun getNotes(
        @Query("pruneBefore") pruneBefore: Long,
        @Header("If-None-Match") etag: String?,
        @Query("chunkSize") chunkSize: Int
    ): Call<List<RemoteNoteDto>>

    @GET("notes")
    fun getNotesChunk(
        @Query("pruneBefore") pruneBefore: Long,
        @Header("If-None-Match") etag: String?,
        @Query("chunkSize") chunkSize: Int,
        @Query("chunkCursor") chunkCursor: String
    ): Call<List<RemoteNoteDto>>
}

private data class OcsResponse(val ocs: OcsEnvelope)

private data class OcsEnvelope(val data: CapabilitiesData)

private data class CapabilitiesData(val capabilities: com.google.gson.JsonObject?)

internal data class RemoteNoteDto(
    val id: Long?,
    val etag: String? = null,
    val readonly: Boolean = false,
    val content: String? = null,
    val title: String? = null,
    val category: String? = null,
    val modified: Long? = null
) {
    fun toDomain() = RemoteNote(
        id = id ?: throw BackendException.Protocol("Nextcloud note is missing its id"),
        etag = etag,
        title = title,
        content = content,
        category = category,
        modifiedAtEpochSeconds = modified,
        readOnly = readonly
    )
}

private fun ParsedResponse<*>.header(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
