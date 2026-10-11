package org.qownnotes.mobile.data

import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private, durable photo bytes. Room retains the note and its synchronization intent. */
class PendingCaptureStore(private val directory: File) {
    suspend fun save(localId: String, open: () -> InputStream): String =
        withContext(Dispatchers.IO) {
            val file = file(localId)
            if (!file.exists()) {
                check(directory.isDirectory || directory.mkdirs())
                val temporary = File(directory, "${file.name}.tmp")
                try {
                    open().use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    require(temporary.length() > 0) { "The captured image is empty" }
                    check(temporary.renameTo(file)) { "Could not save the captured image" }
                } finally {
                    temporary.delete()
                }
            }
            file.name
        }

    suspend fun read(localId: String): ByteArray? = withContext(Dispatchers.IO) {
        file(localId).takeIf { it.exists() && !uploaded(localId).exists() }?.readBytes()
    }

    suspend fun markUploaded(localId: String) = withContext(Dispatchers.IO) {
        check(uploaded(localId).createNewFile() || uploaded(localId).exists())
    }

    suspend fun remove(localId: String) = withContext(Dispatchers.IO) {
        val file = file(localId)
        check(!file.exists() || file.delete()) { "Could not remove the uploaded image" }
        uploaded(localId).delete()
    }

    fun fileName(localId: String): String = file(localId).name

    private fun uploaded(localId: String) = File(directory, "${file(localId).name}.uploaded")

    private fun file(localId: String): File {
        require(localId.matches(Regex("[a-zA-Z0-9-]+")))
        return File(directory, "image-$localId.jpg")
    }
}
