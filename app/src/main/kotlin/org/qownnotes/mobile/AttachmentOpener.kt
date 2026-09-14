package org.qownnotes.mobile

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AttachmentOpener(
    private val context: Context,
    private val fetch: (String, String) -> InputStream?
) {
    suspend fun open(remoteId: Long, path: String, accountName: String): AttachmentOpenResult {
        val file = withContext(Dispatchers.IO) {
            cacheAttachment(remoteId, path, accountName)
        } ?: return AttachmentOpenResult.FETCH_FAILED
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(file.name, uri) }
        return try {
            context.startActivity(intent)
            AttachmentOpenResult.OPENED
        } catch (_: ActivityNotFoundException) {
            AttachmentOpenResult.NO_VIEWER
        } catch (_: SecurityException) {
            AttachmentOpenResult.NO_VIEWER
        }
    }

    private fun cacheAttachment(remoteId: Long, path: String, accountName: String): File? {
        return runCatching {
            val requestPath = "/index.php/apps/notes/api/v1.4/attachment/$remoteId?path=" +
                URLEncoder.encode(path, StandardCharsets.UTF_8.name())
            val directory = File(context.cacheDir, "attachments").apply { mkdirs() }
            val originalName = path.substringAfterLast('/').safeFileName()
            val cacheKey = UUID.nameUUIDFromBytes(
                "$accountName\u0000$remoteId\u0000$path".toByteArray(StandardCharsets.UTF_8)
            )
            val target = File(directory, "$cacheKey-$originalName")
            fetch(requestPath, accountName)?.use { input ->
                target.outputStream().use(input::copyTo)
            } ?: return null
            target
        }.getOrNull()
    }
}

private fun String.safeFileName(): String {
    val sanitized = map { character ->
        if (character.isISOControl() || character == '/' || character == '\\') '_' else character
    }.joinToString("")
    return sanitized.take(120).ifBlank { "attachment" }
}

internal enum class AttachmentOpenResult {
    OPENED,
    FETCH_FAILED,
    NO_VIEWER
}
