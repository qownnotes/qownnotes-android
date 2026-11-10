package org.qownnotes.mobile

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.isLocalFolder
import org.qownnotes.mobile.core.isLocalFolderAccountId

/** Where an account keeps its notes: the server address, or the folder on this device. */
@Composable
internal fun accountLocation(account: Account): String {
    val folder = account.localFolderUri ?: return account.serverUrl
    return localFolderLocation(
        folder,
        internalStorage = stringResource(R.string.local_folder_internal_storage)
    ) ?: stringResource(R.string.local_folder_on_device)
}

/**
 * A readable location for a granted folder, such as `Internal storage/Documents/Notes`. Only the
 * device's own storage provider exposes a path; other providers return null.
 */
internal fun localFolderLocation(uri: String, internalStorage: String): String? {
    val parsed = Uri.parse(uri)
    if (parsed.scheme == ContentResolver.SCHEME_FILE) return parsed.path
    if (parsed.authority != EXTERNAL_STORAGE_AUTHORITY) return null
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(parsed) }.getOrNull()
        ?: return null
    val volume = documentId.substringBefore(':')
    val path = documentId.substringAfter(':', "").trim('/')
    val root = if (volume.equals("primary", ignoreCase = true)) internalStorage else volume
    return if (path.isEmpty()) root else "$root/$path"
}

/**
 * Chooses between a text written for a Nextcloud account and its counterpart for a local folder,
 * where there is no server and changes come from the folder.
 */
@Composable
internal fun backendText(
    accountId: String?,
    @StringRes nextcloud: Int,
    @StringRes localFolder: Int
): String = stringResource(
    if (accountId != null && isLocalFolderAccountId(accountId)) localFolder else nextcloud
)

@Composable
internal fun backendText(
    account: Account?,
    @StringRes nextcloud: Int,
    @StringRes localFolder: Int
): String = stringResource(if (account?.isLocalFolder == true) localFolder else nextcloud)

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
