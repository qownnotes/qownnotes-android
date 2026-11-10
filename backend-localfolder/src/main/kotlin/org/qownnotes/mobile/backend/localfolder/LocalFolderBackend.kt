package org.qownnotes.mobile.backend.localfolder

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendCapabilities
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteArchiveBackend
import org.qownnotes.mobile.core.NoteBackend
import org.qownnotes.mobile.core.NoteCategories
import org.qownnotes.mobile.core.NoteMediaBackend
import org.qownnotes.mobile.core.NoteNames
import org.qownnotes.mobile.core.NoteTagFileBackend
import org.qownnotes.mobile.core.NoteTagFileDownload
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.RemoteNoteVersion
import org.qownnotes.mobile.core.TrashedNote
import org.qownnotes.mobile.core.noteMediaFolder

/**
 * Keeps notes as Markdown files in a folder on the device, laid out like a QOwnNotes desktop note
 * folder: subfolders are categories, a file name is the note name, and `media/`, `attachments/`,
 * and `notes.sqlite` sit at the top.
 *
 * The folder plays the part of the server. Room stays the source of truth for the screens, and the
 * existing synchronization engine moves changes between the two:
 *
 * - A note's remote ID is a stable hash of its relative path. A rename made in the app returns the
 *   new ID, which the push store adopts. A rename made elsewhere looks like a delete and a create,
 *   as it does for QOwnNotes desktop.
 * - A note's ETag is a hash of its bytes. An update first compares it with the file on disk, so a
 *   change that arrived through a file synchronizer becomes an explicit conflict.
 * - A pull lists the folder and only reads files whose size or modification time changed since
 *   the last pull that the database applied in this process.
 * - Deleting moves the note into an app-private trash, from which it can be restored.
 * - Favorites have no place in the folder, so they are kept in app-private storage by path.
 */
class LocalFolderBackend(
    private val stateDirectory: File,
    private val filesFor: (Account) -> NoteFolderFiles,
    private val clock: Clock = Clock.systemDefaultZone()
) : NoteBackend,
    NoteMediaBackend,
    NoteTagFileBackend,
    NoteArchiveBackend {
    override val capabilities = BackendCapabilities(
        categories = true,
        nestedCategories = true,
        favorites = true,
        attachments = true,
        readOnlyNotes = true
    )

    private val session = UUID.randomUUID().toString()
    private val pullCounter = AtomicLong()
    private val folders = ConcurrentHashMap<String, FolderState>()

    override suspend fun validateAccount(account: Account): String = io(account) { files, _ ->
        files.checkAccess()
        API_VERSION
    }

    override suspend fun discoverAccount(account: Account): Account = io(account) { files, _ ->
        files.checkAccess()
        account
    }

    override suspend fun pull(account: Account, checkpoint: PullCheckpoint): PullResult =
        io(account) { files, state ->
            files.checkAccess()
            val listed = files.list(::isListedDirectory).filter { isNoteFile(it.path) }
            state.index(listed)
            val stamps = listed.associate { it.path to FileStamp(it) }
            val previous = state.snapshot?.takeIf { it.token == checkpoint.collectionEtag }
            if (previous != null && previous.stamps == stamps) {
                return@io PullResult(
                    notes = emptyList(),
                    collectionEtag = checkpoint.collectionEtag,
                    lastModifiedEpochSeconds = checkpoint.lastModifiedEpochSeconds,
                    notModified = true
                )
            }
            val favorites = favorites(account.id).read()
            val notes = listed.mapNotNull { file ->
                if (previous?.stamps?.get(file.path) == stamps.getValue(file.path)) {
                    // The database applied this exact file version and still holds it.
                    RemoteNote(idOf(file.path), null, null, null, null, null)
                } else {
                    try {
                        noteOf(file, files.read(file.path), file.path in favorites)
                    } catch (_: FileNotFoundException) {
                        // Removed while listing; the next pull confirms the deletion.
                        null
                    }
                }
            }
            val token = "$session:${pullCounter.incrementAndGet()}"
            state.snapshot = Snapshot(token, stamps)
            PullResult(notes, token, clock.instant().epochSecond)
        }

    override suspend fun get(account: Account, remoteId: Long): RemoteNote =
        io(account) { files, state ->
            val path = state.pathOf(files, remoteId) ?: throw noteMissing()
            val file = files.stat(path) ?: throw noteMissing()
            val content = try {
                files.read(path)
            } catch (missing: FileNotFoundException) {
                throw noteMissing(missing)
            }
            noteOf(file, content, path in favorites(account.id).read())
        }

    override suspend fun create(account: Account, note: Note): RemoteNote =
        io(account) { files, state ->
            val title = NoteNames.sanitize(note.title).ifEmpty { DEFAULT_TITLE }
            val path = uniqueNotePath(files, categoryOf(note), title, DEFAULT_EXTENSION)
            val content = note.content.toByteArray(StandardCharsets.UTF_8)
            val created = files.create(path, content)
            state.paths[idOf(created)] = created
            favorites(account.id).update { if (note.favorite) it + created else it - created }
            written(files, created, content, note.favorite)
        }

    override suspend fun update(account: Account, note: Note): RemoteNote =
        io(account) { files, state ->
            val remoteId = requireNotNull(note.remoteId) { "Only stored notes can be updated" }
            val path = state.pathOf(files, remoteId) ?: throw noteMissing()
            val current = try {
                files.read(path)
            } catch (missing: FileNotFoundException) {
                throw noteMissing(missing)
            }
            if (files.stat(path)?.writable == false) {
                throw BackendException.Permission(message = READ_ONLY_MESSAGE)
            }
            if (note.remoteEtag != null && etagOf(current) != note.remoteEtag) {
                throw BackendException.Conflict(message = CONFLICT_MESSAGE)
            }
            val content = note.content.toByteArray(StandardCharsets.UTF_8)
            val extension = path.substringAfterLast('.', DEFAULT_EXTENSION)
            val category = categoryOf(note)
            val title = NoteNames.sanitize(note.title).ifEmpty { titleOf(path) }
            val desired = joinPath(category, "$title.$extension")
            // Shared storage on Android ignores letter case, so a rename that only changes case
            // would collide with the file itself. Such a note keeps its current name.
            val target = if (desired.equals(path, ignoreCase = true)) {
                path
            } else {
                uniqueNotePath(files, category, title, extension)
            }
            val stored = if (target == path) {
                if (!content.contentEquals(current)) files.write(path, content)
                path
            } else {
                // Write the new file before removing the old one, so an interruption can leave a
                // duplicate behind but never lose the note.
                files.create(target, content).also { created ->
                    try {
                        files.delete(path)
                    } catch (_: IOException) {
                        // The old file reappears as a separate note on the next pull.
                    }
                    state.paths.remove(remoteId)
                    state.paths[idOf(created)] = created
                }
            }
            favorites(account.id).update {
                val others = it - path
                if (note.favorite) others + stored else others - stored
            }
            written(files, stored, content, note.favorite)
        }

    override suspend fun delete(account: Account, remoteId: Long) = io(account) { files, state ->
        val path = state.pathOf(files, remoteId) ?: return@io
        val content = try {
            files.read(path)
        } catch (_: FileNotFoundException) {
            return@io
        }
        trash(account.id).add(path, content, clock.instant())
        files.delete(path)
        state.paths.remove(remoteId)
        favorites(account.id).update { it - path }
    }

    override suspend fun versions(account: Account, note: Note): List<RemoteNoteVersion> =
        throw BackendException.FeatureUnavailable(VERSIONS_UNAVAILABLE_MESSAGE)

    override suspend fun trashedNotes(
        account: Account,
        categories: Set<String>
    ): List<TrashedNote> = io(account) { _, _ ->
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        trash(account.id).entries().map { entry ->
            TrashedNote(
                name = titleOf(entry.path),
                fileName = entry.path.substringAfterLast('/'),
                timestamp = entry.deletedAt.epochSecond,
                displayTimestamp = formatter.format(entry.deletedAt),
                content = decode(
                    entry.content
                ) ?: String(entry.content, StandardCharsets.UTF_8),
                remotePath = entry.id
            )
        }
    }

    override suspend fun restoreTrashedNote(account: Account, note: TrashedNote) =
        io(account) { files, state ->
            val trash = trash(account.id)
            val entry = trash.entry(note.remotePath) ?: throw noteMissing()
            val category = entry.path.substringBeforeLast('/', "")
            val extension = entry.path.substringAfterLast('.', DEFAULT_EXTENSION)
            val path = uniqueNotePath(files, category, titleOf(entry.path), extension)
            val created = files.create(path, entry.content)
            state.paths[idOf(created)] = created
            trash.remove(entry.id)
        }

    override suspend fun uploadMedia(
        account: Account,
        notesPath: String,
        fileName: String,
        mimeType: String,
        content: ByteArray
    ): String = io(account) { files, _ ->
        require(fileName.isNotBlank() && '/' !in fileName && '\\' !in fileName) {
            "Invalid media file name"
        }
        val folder = noteMediaFolder(mimeType)
        val base = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', "").let {
            if (it.isEmpty()) "" else ".$it"
        }
        var name = fileName
        var counter = 1
        while (files.exists("$folder/$name")) name = "$base-${counter++}$extension"
        files.create("$folder/$name", content).substringAfterLast('/')
    }

    override suspend fun downloadTagFile(account: Account, etag: String?): NoteTagFileDownload =
        io(account) { files, state ->
            val file = files.stat(TAG_FILE) ?: return@io NoteTagFileDownload.Missing
            val cached = state.tagFile
            if (etag != null && cached?.etag == etag && cached.stamp == FileStamp(file)) {
                return@io NoteTagFileDownload.NotModified
            }
            val content = try {
                files.read(TAG_FILE)
            } catch (_: FileNotFoundException) {
                return@io NoteTagFileDownload.Missing
            }
            val currentEtag = etagOf(content)
            state.tagFile = TagFileStamp(currentEtag, FileStamp(file))
            if (currentEtag == etag) {
                NoteTagFileDownload.NotModified
            } else {
                NoteTagFileDownload.Downloaded(content, currentEtag)
            }
        }

    override suspend fun uploadTagFile(account: Account, content: ByteArray, etag: String): String =
        io(account) { files, state ->
            val current = try {
                files.read(TAG_FILE)
            } catch (missing: FileNotFoundException) {
                throw BackendException.RemoteMissing(missing, TAG_FILE_MISSING_MESSAGE)
            }
            if (etagOf(current) != etag) {
                throw BackendException.Conflict(message = TAG_FILE_CHANGED_MESSAGE)
            }
            files.write(TAG_FILE, content)
            val written = etagOf(content)
            state.tagFile = files.stat(TAG_FILE)?.let { TagFileStamp(written, FileStamp(it)) }
            written
        }

    /**
     * Reads an attachment or image that a note links to, relative to the note's own folder. Only
     * files inside the top-level `media` and `attachments` folders can be read. Blocks.
     */
    fun readAttachment(account: Account, remoteId: Long, link: String): ByteArray? = try {
        val state = state(account)
        val files = state.files(account)
        val notePath = state.pathOf(files, remoteId)
        val path = notePath?.let { resolveAttachment(it, link) }
        path?.let(files::read)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /** Forgets everything stored for an account, including its private trash and favorites. */
    fun forget(accountId: String) {
        folders.remove(accountId)
        accountDirectory(accountId).deleteRecursively()
    }

    private suspend fun <T> io(account: Account, block: (NoteFolderFiles, FolderState) -> T): T =
        withContext(Dispatchers.IO) {
            val state = state(account)
            try {
                block(state.files(account), state)
            } catch (error: BackendException) {
                throw error
            } catch (error: FolderAccessException) {
                throw BackendException.AuthorizationRequired(error, FOLDER_ACCESS_MESSAGE)
            } catch (error: SecurityException) {
                throw BackendException.AuthorizationRequired(error, FOLDER_ACCESS_MESSAGE)
            } catch (error: IOException) {
                throw BackendException.Retryable(error, STORAGE_FAILURE_MESSAGE)
            }
        }

    private fun state(account: Account): FolderState = folders.compute(account.id) { _, existing ->
        existing?.takeIf { it.folderUri == account.localFolderUri }
            ?: FolderState(account.localFolderUri)
    }!!

    private fun FolderState.files(account: Account): NoteFolderFiles =
        files ?: filesFor(account).also { files = it }

    private fun noteOf(file: NoteFolderFile, content: ByteArray, favorite: Boolean): RemoteNote {
        val text = decode(content)
        return RemoteNote(
            id = idOf(file.path),
            etag = etagOf(content),
            title = titleOf(file.path),
            content = text ?: String(content, StandardCharsets.UTF_8),
            category = file.path.substringBeforeLast('/', ""),
            modifiedAtEpochSeconds = file.lastModifiedMillis.takeIf { it > 0 }?.div(1000)
                ?: clock.instant().epochSecond,
            // Text that is not valid UTF-8 could not be written back unchanged.
            readOnly = !file.writable || text == null,
            favorite = favorite
        )
    }

    private fun written(
        files: NoteFolderFiles,
        path: String,
        content: ByteArray,
        favorite: Boolean
    ): RemoteNote {
        val file = files.stat(path) ?: NoteFolderFile(path, content.size.toLong(), 0)
        return noteOf(file.copy(writable = true), content, favorite)
    }

    private fun uniqueNotePath(
        files: NoteFolderFiles,
        category: String,
        title: String,
        extension: String
    ): String {
        var path = joinPath(category, "$title.$extension")
        var counter = 2
        while (files.exists(path)) path = joinPath(category, "$title (${counter++}).$extension")
        return path
    }

    private fun favorites(accountId: String) =
        PathSetFile(File(accountDirectory(accountId), "favorites.txt"))

    private fun trash(accountId: String) = PrivateTrash(File(accountDirectory(accountId), "trash"))

    private fun accountDirectory(accountId: String) =
        File(stateDirectory, accountId.filter { it.isLetterOrDigit() || it == '-' })

    private class FolderState(val folderUri: String?) {
        @Volatile var files: NoteFolderFiles? = null

        @Volatile var snapshot: Snapshot? = null

        @Volatile var tagFile: TagFileStamp? = null

        val paths = ConcurrentHashMap<Long, String>()

        fun index(listed: List<NoteFolderFile>) {
            val current = listed.associate { idOf(it.path) to it.path }
            paths.keys.retainAll(current.keys)
            paths.putAll(current)
        }

        fun pathOf(files: NoteFolderFiles, remoteId: Long): String? = paths[remoteId] ?: run {
            index(files.list(::isListedDirectory).filter { isNoteFile(it.path) })
            paths[remoteId]
        }
    }

    private data class FileStamp(
        val size: Long,
        val lastModifiedMillis: Long,
        val writable: Boolean
    ) {
        constructor(file: NoteFolderFile) : this(file.size, file.lastModifiedMillis, file.writable)
    }

    private class Snapshot(val token: String, val stamps: Map<String, FileStamp>)

    private class TagFileStamp(val etag: String, val stamp: FileStamp)

    companion object {
        const val API_VERSION = "local-folder"
        const val DEFAULT_EXTENSION = "md"
        private const val DEFAULT_TITLE = "Note"
        private const val TAG_FILE = "notes.sqlite"
        private val NOTE_EXTENSIONS = setOf("md", "txt")
        private val ATTACHMENT_FOLDERS = setOf("media", "attachments")

        const val FOLDER_ACCESS_MESSAGE =
            "QOwnNotes can no longer access the note folder. Select the folder again to reconnect."
        const val NOTE_MISSING_MESSAGE = "The note file no longer exists in the folder"
        const val CONFLICT_MESSAGE = "The note file changed in the folder"
        const val READ_ONLY_MESSAGE = "The note file is read-only"
        const val STORAGE_FAILURE_MESSAGE = "The note folder could not be read or written"
        private const val TAG_FILE_MISSING_MESSAGE = "notes.sqlite no longer exists in the folder"
        private const val TAG_FILE_CHANGED_MESSAGE = "notes.sqlite changed in the folder"
        private const val VERSIONS_UNAVAILABLE_MESSAGE =
            "Note versions are not available for local folders"

        /** A stable, positive identifier for a note path. */
        fun idOf(path: String): Long {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(path.toByteArray(StandardCharsets.UTF_8))
            return (ByteBuffer.wrap(digest).long and Long.MAX_VALUE).takeIf { it != 0L } ?: 1L
        }

        fun etagOf(content: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(content)
            .take(16)
            .joinToString("") { "%02x".format(it) }

        /** Hidden folders and the reserved top-level media folders never hold notes. */
        internal fun isListedDirectory(path: String): Boolean {
            val name = path.substringAfterLast('/')
            if (name.startsWith('.')) return false
            return '/' in path || name.lowercase(Locale.ROOT) !in ATTACHMENT_FOLDERS
        }

        internal fun isNoteFile(path: String): Boolean {
            val name = path.substringAfterLast('/')
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return !name.startsWith('.') && extension in NOTE_EXTENSIONS &&
                name.length > extension.length + 1
        }

        internal fun titleOf(path: String): String =
            path.substringAfterLast('/').substringBeforeLast('.')

        /**
         * Resolves [link] against the folder of the note at [notePath]. Returns null unless the
         * result stays inside the note folder's top-level `media` or `attachments` folder.
         */
        internal fun resolveAttachment(notePath: String, link: String): String? {
            val decoded = runCatching { URI(link).path }.getOrNull() ?: link
            if (decoded.startsWith('/') || '\\' in decoded || '\u0000' in decoded) return null
            val segments = notePath.split('/').dropLast(1).toMutableList()
            for (segment in decoded.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (segments.isEmpty()) {
                        return null
                    } else {
                        segments.removeAt(
                            segments.lastIndex
                        )
                    }
                    else -> segments += segment
                }
            }
            if (segments.size < 2) return null
            if (segments.first().lowercase(Locale.ROOT) !in ATTACHMENT_FOLDERS) return null
            return segments.joinToString("/")
        }

        private fun categoryOf(note: Note): String = NoteCategories.normalize(note.category)

        private fun joinPath(category: String, name: String) =
            if (category.isEmpty()) name else "$category/$name"

        private fun decode(content: ByteArray): String? = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

        private fun noteMissing(cause: Throwable? = null) =
            BackendException.RemoteMissing(cause, NOTE_MISSING_MESSAGE)
    }
}

/** A set of note paths stored one per line in app-private storage. */
private class PathSetFile(private val file: File) {
    fun read(): Set<String> = synchronized(LOCK) {
        if (file.isFile) file.readLines().filter(String::isNotEmpty).toSet() else emptySet()
    }

    fun update(change: (Set<String>) -> Set<String>): Unit = synchronized(LOCK) {
        val current = read()
        val updated = change(current)
        if (updated == current) return
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(updated.sorted().joinToString("\n"))
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private companion object {
        val LOCK = Any()
    }
}

/**
 * Notes deleted from a local folder. Android has no trash for shared folders, so a deleted note is
 * kept in app-private storage until it is restored or its account is removed.
 */
private class PrivateTrash(private val directory: File) {
    class Entry(val id: String, val path: String, val deletedAt: Instant, val content: ByteArray)

    fun add(path: String, content: ByteArray, deletedAt: Instant) {
        directory.mkdirs()
        val id = "${deletedAt.toEpochMilli()}-${UUID.randomUUID().toString().take(8)}"
        File(directory, "$id$CONTENT").writeBytes(content)
        // The metadata is written last; an entry without it is incomplete and ignored.
        File(directory, "$id$METADATA").writeText("${deletedAt.toEpochMilli()}\n$path")
    }

    fun entries(): List<Entry> = directory.listFiles { file -> file.name.endsWith(METADATA) }
        .orEmpty()
        .mapNotNull { entry(it.name.removeSuffix(METADATA)) }
        .sortedByDescending(Entry::deletedAt)

    fun entry(id: String): Entry? {
        if (id.isEmpty() || id.any { !it.isLetterOrDigit() && it != '-' }) return null
        val metadata = File(directory, "$id$METADATA").takeIf(File::isFile) ?: return null
        val content = File(directory, "$id$CONTENT").takeIf(File::isFile) ?: return null
        val lines = metadata.readText().split('\n', limit = 2)
        val deletedAt = lines.firstOrNull()?.toLongOrNull() ?: return null
        val path = lines.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        return Entry(id, path, Instant.ofEpochMilli(deletedAt), content.readBytes())
    }

    fun remove(id: String) {
        File(directory, "$id$METADATA").delete()
        File(directory, "$id$CONTENT").delete()
    }

    private companion object {
        const val METADATA = ".entry"
        const val CONTENT = ".content"
    }
}
