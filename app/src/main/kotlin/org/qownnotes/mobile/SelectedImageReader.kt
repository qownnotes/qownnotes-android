package org.qownnotes.mobile

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SelectedImage(
    val description: String,
    val mimeType: String,
    val content: ByteArray
)

internal data class ImportedImage(val description: String, val markdownPath: String)

internal class SelectedImageReader(private val contentResolver: ContentResolver) {
    suspend fun read(uri: Uri): SelectedImage = withContext(Dispatchers.IO) {
        val mimeType = contentResolver.getType(uri).orEmpty().lowercase()
        require(mimeType in SUPPORTED_IMAGE_TYPES) { "Choose a JPEG, PNG, or WebP image" }
        val metadata = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)
            val size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            name to size
        }
        require(metadata?.second == null || metadata.second!! <= MAX_IMAGE_BYTES) {
            "Images must be 5 MB or smaller"
        }
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_IMAGE_BYTES) { "Images must be 5 MB or smaller" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("The selected image could not be opened")
        require(bytes.isNotEmpty()) { "The selected image is empty" }
        SelectedImage(
            description = metadata?.first?.substringBeforeLast('.')?.takeIf(String::isNotBlank)
                ?: "image",
            mimeType = mimeType,
            content = bytes
        )
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
        val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
