package org.qownnotes.mobile.backend.nextcloud

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.api.ParsedResponse
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import io.reactivex.Observable
import java.net.HttpURLConnection
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendCapabilities
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.PullBackend
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import retrofit2.NextcloudRetrofitApiBuilder
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

class NextcloudBackend(context: Context) : PullBackend {
    private val applicationContext = context.applicationContext
    private val gson = GsonBuilder().create()

    override val capabilities =
        BackendCapabilities(categories = true, favorites = true, readOnlyNotes = true)

    override suspend fun validateAccount(account: Account): String = withContext(Dispatchers.IO) {
        withApis(account) { capabilitiesApi, _ ->
            val response = capabilitiesApi.getCapabilities().blockingSingle().response
            val notes = response.ocs.data.capabilities?.getAsJsonObject("notes")
                ?: throw BackendException.NotesAppMissing()
            val versions = NextcloudProtocol.parseVersions(notes.get("api_version"))
            NextcloudProtocol.selectSupportedVersion(versions)
                ?: throw BackendException.UnsupportedApi(versions)
        }
    }

    override suspend fun pull(account: Account, checkpoint: PullCheckpoint): PullResult =
        withContext(Dispatchers.IO) {
            try {
                withApis(account) { _, notesApi ->
                    val notes = mutableListOf<RemoteNote>()
                    var cursor: String? = null
                    var etag = checkpoint.collectionEtag
                    var modified = checkpoint.lastModifiedEpochSeconds
                    do {
                        val response =
                            notesApi.getNotes(
                                checkpoint.lastModifiedEpochSeconds,
                                checkpoint.collectionEtag,
                                CHUNK_SIZE,
                                cursor
                            ).blockingSingle()
                        notes += response.response.map(RemoteNoteDto::toDomain)
                        etag = response.header("ETag") ?: etag
                        modified = response.header("Last-Modified")?.toEpochSeconds() ?: modified
                        cursor = response.header("X-Notes-Chunk-Cursor")
                        val pending = response.header("X-Notes-Chunk-Pending").toBoolean()
                    } while (pending && !cursor.isNullOrBlank())
                    PullResult(notes, etag, modified)
                }
            } catch (error: Throwable) {
                val cause = error.unwrap()
                if (cause is NextcloudHttpRequestFailedException &&
                    cause.statusCode == HttpURLConnection.HTTP_NOT_MODIFIED
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

    private fun <T> withApis(account: Account, block: (CapabilitiesApi, NotesApi) -> T): T {
        val ssoAccount = AccountImporter.getSingleSignOnAccount(
            applicationContext,
            account.ssoAccountName
        )
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

    private fun String.toEpochSeconds(): Long? = runCatching {
        ZonedDateTime.parse(this, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
    }.getOrNull()

    private fun Throwable.unwrap(): Throwable =
        if (this is RuntimeException && cause != null) cause!! else this

    private fun Throwable.toBackendException(): BackendException = when (this) {
        is BackendException -> this
        is NextcloudHttpRequestFailedException -> when (statusCode) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> BackendException.Authentication(this)
            HttpURLConnection.HTTP_FORBIDDEN -> BackendException.Permission(this)
            in 500..599 -> BackendException.Retryable(this)
            else -> BackendException.Protocol("Nextcloud returned HTTP $statusCode", this)
        }
        else -> BackendException.Retryable(this)
    }

    private companion object {
        const val OCS_ENDPOINT = "/ocs/v2.php/cloud/"
        const val NOTES_ENDPOINT = "/index.php/apps/notes/api/v1/"
        const val CHUNK_SIZE = 200
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

private interface CapabilitiesApi {
    @GET("capabilities?format=json")
    fun getCapabilities(
        @Header("OCS-APIRequest") ocsRequest: String = "true"
    ): Observable<ParsedResponse<OcsResponse>>
}

private interface NotesApi {
    @GET("notes")
    fun getNotes(
        @Query("pruneBefore") pruneBefore: Long,
        @Header("If-None-Match") etag: String?,
        @Query("chunkSize") chunkSize: Int,
        @Query("chunkCursor") chunkCursor: String?
    ): Observable<ParsedResponse<List<RemoteNoteDto>>>
}

private data class OcsResponse(val ocs: OcsEnvelope)

private data class OcsEnvelope(val data: CapabilitiesData)

private data class CapabilitiesData(val capabilities: com.google.gson.JsonObject?)

private data class RemoteNoteDto(
    val id: Long,
    val etag: String? = null,
    val readonly: Boolean = false,
    val content: String? = null,
    val title: String? = null,
    val category: String? = null,
    val modified: Long? = null
) {
    fun toDomain() = RemoteNote(id, etag, title, content, category, modified, readonly)
}

private fun ParsedResponse<*>.header(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
