package org.qownnotes.mobile.data

import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.noteMediaFolder

/** Private bytes behind Markdown links, retained across restarts until the note push succeeds. */
class PendingSharedAttachments(private val root: File) {
    class Entry internal constructor(
        private val directory: File,
        val file: File,
        val mimeType: String
    ) {
        private val checkpoint get() = directory.resolve("uploaded")
        val uploaded get() = checkpoint.isFile

        val path get() = "${noteMediaFolder(mimeType)}/${file.name}"

        fun isLinkedIn(content: String): Boolean = "$path)" in content

        fun markUploaded() {
            check(checkpoint.createNewFile() || checkpoint.isFile)
        }

        fun remove() {
            directory.deleteRecursively()
        }
    }

    suspend fun save(
        accountId: String,
        id: UUID,
        fileName: String,
        mimeType: String,
        limit: Long,
        open: () -> InputStream
    ): Entry = withContext(Dispatchers.IO) {
        require(fileName.matches(Regex("[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9]+)?")))
        val account = directory(accountId)
        check(account.isDirectory || account.mkdirs())
        val temporary = account.resolve(".$id")
        val target = account.resolve(id.toString())
        check(temporary.mkdir())
        try {
            val file = temporary.resolve(fileName)
            open().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > limit) throw SharedAttachmentTooLargeException()
                        output.write(buffer, 0, count)
                    }
                }
            }
            temporary.resolve("metadata").writeText("$fileName\n$mimeType")
            check(temporary.renameTo(target))
            Entry(target, target.resolve(fileName), mimeType)
        } finally {
            temporary.deleteRecursively()
        }
    }

    fun entries(accountId: String): List<Entry> = directory(accountId).listFiles().orEmpty()
        .filter { it.isDirectory && !it.name.startsWith('.') }
        .mapNotNull { entry ->
            val metadata = runCatching { entry.resolve("metadata").readLines() }.getOrNull()
                ?: return@mapNotNull null
            if (metadata.size != 2) return@mapNotNull null
            val file = entry.resolve(metadata[0])
            Entry(entry, file, metadata[1]).takeIf { file.isFile }
        }

    /** Called under the same account's staging lock, after any active copy has completed. */
    fun discardIncomplete(accountId: String) {
        directory(accountId).listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith('.') }
            .forEach { it.deleteRecursively() }
    }

    suspend fun clear(accountId: String) = withContext(Dispatchers.IO) {
        directory(accountId).deleteRecursively()
    }

    private fun directory(accountId: String) =
        root.resolve(UUID.nameUUIDFromBytes(accountId.toByteArray()).toString())
}

class SharedAttachmentTooLargeException : Exception()
