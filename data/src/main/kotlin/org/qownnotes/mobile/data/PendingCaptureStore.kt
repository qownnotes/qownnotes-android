package org.qownnotes.mobile.data

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private, durable media bytes. Room retains the note and its synchronization intent. */
class PendingCaptureStore(private val directory: File) {
    suspend fun save(localId: String, open: () -> InputStream): String = save(localId, false, open)

    suspend fun save(localId: String, audio: Boolean, open: () -> InputStream): String =
        save(localId, if (audio) AUDIO_MIME_TYPE else JPEG_MIME_TYPE, open)

    /**
     * Saves a captured image or recording of [mimeType] once. A capture that was already saved for
     * the note is kept, whatever its type, so repeating the save after a restart is harmless.
     */
    suspend fun save(localId: String, mimeType: String, open: () -> InputStream): String =
        withContext(Dispatchers.IO) {
            existing(localId)?.let { return@withContext it.name }
            val extension = EXTENSIONS[mimeType.lowercase()]
                ?: throw IllegalArgumentException("Unsupported captured media type")
            val file = file(localId, extension)
            check(directory.isDirectory || directory.mkdirs())
            val temporary = File(directory, "${file.name}.tmp")
            try {
                open().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                require(temporary.length() > 0) { "The captured media is empty" }
                check(temporary.renameTo(file)) { "Could not save the captured media" }
            } finally {
                temporary.delete()
            }
            file.name
        }

    suspend fun read(localId: String): ByteArray? = withContext(Dispatchers.IO) {
        file(localId).takeIf { it.exists() && !uploaded(localId).exists() }?.readBytes()
    }

    /** Includes uploaded bytes retained while the note itself is still pending. */
    suspend fun readAudio(localId: String): ByteArray? = withContext(Dispatchers.IO) {
        file(localId, AUDIO_EXTENSION).takeIf(File::exists)?.readBytes()
    }

    /** The captured photo, kept until its note is synchronized, even after it was uploaded. */
    fun image(localId: String): File? =
        existing(localId)?.takeIf { it.extension != AUDIO_EXTENSION && it.isFile }

    suspend fun markUploaded(localId: String) = withContext(Dispatchers.IO) {
        check(uploaded(localId).createNewFile() || uploaded(localId).exists())
    }

    suspend fun remove(localId: String) = withContext(Dispatchers.IO) {
        val file = file(localId)
        val checkpoint = uploaded(localId)
        check(!file.exists() || file.delete()) { "Could not remove the uploaded image" }
        checkpoint.delete()
    }

    fun fileName(localId: String): String = file(localId).name

    fun mimeType(localId: String): String = file(localId).extension.let { extension ->
        EXTENSIONS.entries.firstOrNull { it.value == extension }?.key ?: JPEG_MIME_TYPE
    }

    private fun uploaded(localId: String) = File(directory, "${file(localId).name}.uploaded")

    private fun file(localId: String): File = existing(localId) ?: file(localId, JPEG_EXTENSION)

    private fun existing(localId: String): File? =
        EXTENSIONS.values.map { file(localId, it) }.firstOrNull(File::exists)

    private fun file(localId: String, extension: String): File {
        require(localId.matches(Regex("[a-zA-Z0-9-]+")))
        val kind = if (extension == AUDIO_EXTENSION) "audio" else "image"
        return File(directory, "$kind-$localId.$extension")
    }

    private companion object {
        const val AUDIO_MIME_TYPE = "audio/mp4"
        const val JPEG_MIME_TYPE = "image/jpeg"
        const val AUDIO_EXTENSION = "m4a"
        const val JPEG_EXTENSION = "jpg"

        /** Media types a capture can have; earlier entries win when several files exist. */
        val EXTENSIONS = linkedMapOf(
            AUDIO_MIME_TYPE to AUDIO_EXTENSION,
            "image/webp" to "webp",
            JPEG_MIME_TYPE to JPEG_EXTENSION
        )
    }
}
