package org.qownnotes.mobile.backend.localfolder

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** A file below a note folder. [path] is relative to the folder and uses `/` separators. */
data class NoteFolderFile(
    val path: String,
    val size: Long,
    val lastModifiedMillis: Long,
    val writable: Boolean = true
)

/**
 * File access within one selected note folder. Every path is relative to that folder, uses `/`
 * separators, and has already been validated by the caller to stay inside it.
 *
 * Implementations block and must be called off the main thread. A missing file is reported as
 * [FileNotFoundException]; a folder that can no longer be accessed as [FolderAccessException].
 */
interface NoteFolderFiles {
    /** Throws [FolderAccessException] when the folder itself can no longer be read. */
    fun checkAccess()

    /** Lists every file below the folder, descending only into directories [descend] accepts. */
    fun list(descend: (directoryPath: String) -> Boolean): List<NoteFolderFile>

    /** Returns the file, or null when it does not exist. */
    fun stat(path: String): NoteFolderFile?

    /**
     * Whether a file or directory with this name exists, ignoring letter case. Android shared
     * storage is case-insensitive, so a name differing only in case is already taken.
     */
    fun exists(path: String): Boolean

    fun read(path: String): ByteArray

    /** Replaces the content of an existing file. */
    fun write(path: String, content: ByteArray)

    /**
     * Creates a new file, and any missing parent directories, and returns its actual path. A
     * storage provider may adjust the name it stores.
     */
    fun create(path: String, content: ByteArray): String

    fun delete(path: String)
}

/** The selected folder is gone or its access grant was revoked. */
class FolderAccessException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** Direct file-system access, used for app-private folders and tests. */
class DirectoryNoteFolderFiles(private val root: File) : NoteFolderFiles {
    override fun checkAccess() {
        if (!root.isDirectory || !root.canRead()) {
            throw FolderAccessException("The note folder cannot be read")
        }
    }

    override fun list(descend: (String) -> Boolean): List<NoteFolderFile> = buildList {
        fun visit(directory: File, prefix: String) {
            directory.listFiles().orEmpty().sortedBy(File::getName).forEach { file ->
                val path = prefix + file.name
                if (file.isDirectory) {
                    if (descend(path)) visit(file, "$path/")
                } else if (file.isFile) {
                    add(file.toNoteFolderFile(path))
                }
            }
        }
        checkAccess()
        visit(root, "")
    }

    override fun stat(path: String): NoteFolderFile? =
        resolve(path).takeIf(File::isFile)?.toNoteFolderFile(path)

    override fun exists(path: String): Boolean {
        val file = resolve(path)
        if (file.exists()) return true
        return file.parentFile?.list().orEmpty().any { it.equals(file.name, ignoreCase = true) }
    }

    override fun read(path: String): ByteArray {
        val file = resolve(path)
        if (!file.isFile) throw FileNotFoundException(path)
        return file.readBytes()
    }

    override fun write(path: String, content: ByteArray) {
        val file = resolve(path)
        if (!file.isFile) throw FileNotFoundException(path)
        file.writeBytes(content)
    }

    override fun create(path: String, content: ByteArray): String {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        if (!file.createNewFile()) throw IOException("$path already exists")
        file.writeBytes(content)
        return path
    }

    override fun delete(path: String) {
        val file = resolve(path)
        if (file.exists() && !file.delete()) throw IOException("$path could not be deleted")
    }

    private fun resolve(path: String) = File(root, path)

    private fun File.toNoteFolderFile(path: String) =
        NoteFolderFile(path, length(), lastModified(), canWrite())
}
