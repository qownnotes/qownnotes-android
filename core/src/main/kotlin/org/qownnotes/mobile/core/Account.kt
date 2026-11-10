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
    val apiBasePath: String? = null,
    /** The granted folder of a local-folder account; null for a Nextcloud account. */
    val localFolderUri: String? = null
)

/** A local-folder account keeps its notes as files in a folder on this device. */
val Account.isLocalFolder: Boolean
    get() = localFolderUri != null

/**
 * Local-folder account identifiers carry this prefix, so code that only knows an account ID, such
 * as the synchronization scheduler, can tell that the account needs no network.
 */
const val LOCAL_FOLDER_ACCOUNT_ID_PREFIX = "local-folder-"

fun isLocalFolderAccountId(accountId: String): Boolean =
    accountId.startsWith(LOCAL_FOLDER_ACCOUNT_ID_PREFIX)

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

    class AuthorizationRequired(
        cause: Throwable? = null,
        message: String = "Nextcloud account authorization required"
    ) : BackendException(message, cause)

    class AccountRemoved(cause: Throwable? = null) :
        BackendException("The Nextcloud account is no longer available", cause)

    class Permission(cause: Throwable? = null, message: String = "Permission denied") :
        BackendException(message, cause)

    class NotesAppMissing :
        BackendException(
            "No supported notes app was found on your Nextcloud server. " +
                "Ask your administrator to install and enable QOwnNotes or Notes for your account."
        )

    class UnsupportedApi(val versions: List<String>) :
        BackendException("Nextcloud Notes API 1.2 or newer is required")

    class Retryable(cause: Throwable, message: String = "The server could not be reached") :
        BackendException(message, cause)

    class FilesAppUnavailable(cause: Throwable) :
        BackendException("Can't reach Nextcloud Files. Open the Files app and retry sync.", cause)

    class Conflict(cause: Throwable? = null, message: String = "The note changed on the server") :
        BackendException(message, cause)

    class RemoteMissing(
        cause: Throwable? = null,
        message: String = "The note no longer exists on the server"
    ) : BackendException(message, cause)

    class InsufficientStorage(
        cause: Throwable? = null,
        message: String = "The server does not have enough storage"
    ) : BackendException(message, cause)

    class FeatureUnavailable(message: String) : BackendException(message)

    /**
     * The server understood the request but refused it, for example because of a sharing policy.
     * [serverMessage] is the server's own explanation when it gave one.
     */
    class Rejected(val serverMessage: String?, cause: Throwable? = null) :
        BackendException(serverMessage ?: "The server rejected the request", cause)

    class Protocol(message: String, cause: Throwable? = null) : BackendException(message, cause)
}
