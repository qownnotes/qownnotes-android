package org.qownnotes.mobile.data

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private, durable media bytes. Room retains the note and its synchronization intent. */
class PendingCaptureStore(private val directory: File) {
    suspend fun save(localId: String, open: () -> InputStream): String = save(localId, false, open)

    suspend fun save(localId: String, audio: Boolean, open: () -> InputStream): String =
        withContext(Dispatchers.IO) {
            val file = file(localId, audio)
            if (!file.exists()) {
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
            }
            file.name
        }

    suspend fun read(localId: String): ByteArray? = withContext(Dispatchers.IO) {
        file(localId).takeIf { it.exists() && !uploaded(localId).exists() }?.readBytes()
    }

    /** Includes uploaded bytes retained while the note itself is still pending. */
    suspend fun readAudio(localId: String): ByteArray? = withContext(Dispatchers.IO) {
        file(localId, true).takeIf(File::exists)?.readBytes()
    }

    /** The captured photo, kept until its note is synchronized, even after it was uploaded. */
    fun image(localId: String): File? = file(localId, false).takeIf(File::isFile)

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

    fun mimeType(localId: String): String =
        if (file(localId).extension == "m4a") "audio/mp4" else "image/jpeg"

    private fun uploaded(localId: String) = File(directory, "${file(localId).name}.uploaded")

    private fun file(localId: String): File =
        file(localId, true).takeIf(File::exists) ?: file(localId, false)

    private fun file(localId: String, audio: Boolean): File {
        require(localId.matches(Regex("[a-zA-Z0-9-]+")))
        return File(directory, if (audio) "audio-$localId.m4a" else "image-$localId.jpg")
    }
}
