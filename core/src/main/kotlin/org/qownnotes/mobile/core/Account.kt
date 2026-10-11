package org.qownnotes.mobile.core

data class Account(
    val id: String,
    val displayName: String,
    val serverUrl: String,
    val ssoAccountName: String,
    val userId: String,
    val apiVersion: String? = null,
    val collectionEtag: String? = null,
    val lastModifiedEpochSeconds: Long = 0,
    val lastSyncError: String? = null,
    /** Discovered server-app API path; null preserves the legacy Notes endpoint. */
    val apiBasePath: String? = null
)

/** A different server app has independent collection ETags and incremental checkpoints. */
fun Account.withDiscoveredApi(discovered: Account): Account = discovered.copy(
    collectionEtag = if (apiBasePath == discovered.apiBasePath) collectionEtag else null,
    lastModifiedEpochSeconds = if (apiBasePath ==
        discovered.apiBasePath
    ) {
        lastModifiedEpochSeconds
    } else {
        0
    }
)

fun Account.attachmentApiPath(): String =
    apiBasePath?.let { "${it}attachment/" } ?: "/index.php/apps/notes/api/v1.4/attachment/"

data class PullCheckpoint(
    val collectionEtag: String? = null,
    val lastModifiedEpochSeconds: Long = 0
)

data class NoteSettings(val notesPath: String, val fileSuffix: String)

data class RemoteNote(
    val id: Long,
    val etag: String?,
    val title: String?,
    val content: String?,
    val category: String?,
    val modifiedAtEpochSeconds: Long?,
    val readOnly: Boolean = false,
    val favorite: Boolean = false
) {
    val isPruned: Boolean
        get() = modifiedAtEpochSeconds == null
}

data class PullResult(
    val notes: List<RemoteNote>,
    val collectionEtag: String?,
    val lastModifiedEpochSeconds: Long,
    val notModified: Boolean = false
)

sealed class BackendException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class Authentication(cause: Throwable? = null) :
        BackendException("Authentication required", cause)

    class AuthorizationRequired(cause: Throwable? = null) :
        BackendException("Nextcloud account authorization required", cause)

    class AccountRemoved(cause: Throwable? = null) :
        BackendException("The Nextcloud account is no longer available", cause)

    class Permission(cause: Throwable? = null) : BackendException("Permission denied", cause)

    class NotesAppMissing : BackendException("The Nextcloud Notes app is not available")

    class UnsupportedApi(val versions: List<String>) :
        BackendException("Nextcloud Notes API 1.2 or newer is required")

    class Retryable(cause: Throwable) : BackendException("The server could not be reached", cause)

    class FilesAppUnavailable(cause: Throwable) :
        BackendException("Can't reach Nextcloud Files. Open the Files app and retry sync.", cause)

    class Conflict(cause: Throwable? = null) :
        BackendException("The note changed on the server", cause)

    class RemoteMissing(cause: Throwable? = null) :
        BackendException("The note no longer exists on the server", cause)

    class InsufficientStorage(cause: Throwable? = null) :
        BackendException("The server does not have enough storage", cause)

    class FeatureUnavailable(message: String) : BackendException(message)

    class Protocol(message: String, cause: Throwable? = null) : BackendException(message, cause)
}
