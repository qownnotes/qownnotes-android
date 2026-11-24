package org.qownnotes.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.IntentCompat
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.SyncState

internal data class SharedFile(
    val name: String,
    val storageName: String,
    val mimeType: String,
    val size: Long?,
    val open: () -> InputStream
)

internal sealed interface FileShareState {
    data class Loading(val id: UUID) : FileShareState
    data class Unavailable(val message: Int) : FileShareState
    data class Ready(
        val id: UUID,
        val file: SharedFile,
        val saving: Boolean = false,
        val error: Int? = null
    ) : FileShareState
    data class Saved(val note: Note) : FileShareState
}

internal const val SHARED_FILE_LIMIT = 32L * 1024 * 1024
internal val SHARED_FILE_WRITABLE_STATES =
    setOf(SyncState.SYNCHRONIZED, SyncState.LOCALLY_CREATED, SyncState.LOCALLY_MODIFIED)

internal class ShareDestinationUnavailable : Exception()

internal fun sharedFileUri(intent: Intent?): Uri? {
    if (intent?.action != Intent.ACTION_SEND) return null
    return runCatching {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }.getOrNull()
        ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
            ?.takeIf { it.scheme == "content" || it.scheme == "file" }
}

internal suspend fun readSharedFile(context: Context, uri: Uri, intentType: String?): SharedFile =
    withContext(Dispatchers.IO) {
        require(uri.scheme == "content" && !uri.authority.isNullOrEmpty())
        // Require a URI grant, not just access the receiving app happens to have. In particular an
        // exported activity must not be usable to copy files from one of our own private providers.
        check(uri.authority?.substringAfter('@') != "${context.packageName}.fileprovider")
        check(
            context.checkUriPermission(
                uri,
                Process.myPid(),
                Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        )
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null
        try {
            val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            resolver.query(uri, columns, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameColumn >= 0) name = cursor.getString(nameColumn)
                        if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                            size = cursor.getLong(sizeColumn).takeIf { it >= 0 }
                        }
                    }
                }
        } catch (_: UnsupportedOperationException) {
            // Some sharing providers only implement openFile and cannot supply metadata.
        } catch (_: IllegalArgumentException) {
            // Some providers reject the OpenableColumns projection.
        }
        val displayName = name.orEmpty().filterNot {
            it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() || it in "/\\"
        }.trim().takeUnless { it.isBlank() || it == "." || it == ".." }
        val mimeTypes = MimeTypeMap.getSingleton()
        val fromName = mimeTypes.getMimeTypeFromExtension(
            displayName.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        )
        val mimeType = sequenceOf(resolver.getType(uri), intentType, fromName)
            .mapNotNull { it?.lowercase(Locale.ROOT) }
            .firstOrNull { it.matches(MIME_TYPE) && it != "application/octet-stream" }
            ?: "application/octet-stream"
        val suppliedExtension = displayName?.substringAfterLast('.', "")
            ?.takeIf { it.matches(EXTENSION) && !displayName.startsWith('.') }
        val extension = suppliedExtension ?: mimeTypes.getExtensionFromMimeType(mimeType)
        val base = displayName?.let {
            if (suppliedExtension == null) it else it.substringBeforeLast('.')
        }?.take(160) ?: context.getString(R.string.shared_file_fallback)
        val storageName = base + extension?.let { ".$it" }.orEmpty()
        val nameForDisplay = if (displayName == null) {
            storageName
        } else {
            base + suppliedExtension?.let { ".$it" }.orEmpty()
        }
        SharedFile(nameForDisplay, storageName, mimeType, size) {
            resolver.openInputStream(uri) ?: error("Shared file is no longer available")
        }
    }

private val MIME_TYPE = Regex("[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+_-]*")
private val EXTENSION = Regex("[a-zA-Z0-9]{1,16}")
