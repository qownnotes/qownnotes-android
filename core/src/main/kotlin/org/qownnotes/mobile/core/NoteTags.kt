package org.qownnotes.mobile.core

import kotlinx.coroutines.flow.Flow

/**
 * A tag from the QOwnNotes desktop `notes.sqlite` note-folder database.
 *
 * Tags are hierarchical: [parentId] is `0` for a top-level tag. Identifiers belong to the file they
 * were read from. Tags that were created on this device and are not uploaded yet use negative
 * identifiers, so operations and saved selections must refer to tags by [NoteTags.path] instead.
 */
data class NoteTag(
    val id: Long,
    val name: String,
    val parentId: Long = 0,
    val color: String? = null,
    val priority: Int = 0
)

/**
 * How QOwnNotes desktop identifies a note in `noteTagLink`: the file name without its suffix and
 * the subfolder path relative to the note folder, separated by `/` and empty for the root.
 */
data class NoteTagKey(val fileName: String, val subFolderPath: String)

data class NoteTagLink(val tagId: Long, val key: NoteTagKey)

data class NoteTagSnapshot(val tags: List<NoteTag>, val links: Set<NoteTagLink>) {
    companion object {
        val EMPTY = NoteTagSnapshot(emptyList(), emptySet())
    }
}

/** A durable tag change that is replayed on the newest `notes.sqlite` before it is uploaded. */
sealed interface NoteTagOperation {
    /** Links the note to the tag at [tagPath], creating missing tags along the path. */
    data class Link(val key: NoteTagKey, val tagPath: List<String>) : NoteTagOperation

    /** Removes the link between the note and the tag at [tagPath], if both exist. */
    data class Unlink(val key: NoteTagKey, val tagPath: List<String>) : NoteTagOperation

    /** Moves every link of a renamed or moved note to its new name. */
    data class Relink(val from: NoteTagKey, val to: NoteTagKey) : NoteTagOperation
}

data class PendingNoteTagOperation(val id: Long, val operation: NoteTagOperation)

enum class NoteTagAvailability {
    /** No tag file has been checked for this account yet. */
    UNKNOWN,

    AVAILABLE,

    /** The Notes folder has no `notes.sqlite`. Tagging stays off; the file is never created. */
    MISSING,

    /** The file exists but is damaged or uses a layout this version cannot safely read. */
    INCOMPATIBLE
}

data class NoteTagState(
    val availability: NoteTagAvailability = NoteTagAvailability.UNKNOWN,
    /** Whether the file's schema version is one this version may modify. */
    val writable: Boolean = false,
    val tags: List<NoteTag> = emptyList(),
    val tagIdsByNote: Map<NoteTagKey, Set<Long>> = emptyMap(),
    val message: String? = null
) {
    val editable: Boolean
        get() = availability == NoteTagAvailability.AVAILABLE && writable

    private val tagsById by lazy { tags.associateBy(NoteTag::id) }

    fun tagsOf(key: NoteTagKey): List<NoteTag> =
        NoteTags.sorted(tagIdsByNote[key].orEmpty().mapNotNull(tagsById::get))

    fun path(tagId: Long): List<String> = NoteTags.path(tagsById, tagId)
}

data class NoteTagFileState(
    val availability: NoteTagAvailability,
    val etag: String?,
    val writable: Boolean,
    val message: String?
)

/** Tag reads and user changes for the screens. */
interface NoteTagRepository {
    fun observe(accountId: String): Flow<NoteTagState>

    /** Records a durable link and applies it to the local mirror. Returns false if nothing changed. */
    suspend fun link(accountId: String, key: NoteTagKey, tagPath: List<String>): Boolean

    suspend fun unlink(accountId: String, key: NoteTagKey, tagPath: List<String>): Boolean
}

/** Synchronization side of the tag mirror. */
interface NoteTagStore {
    suspend fun fileState(accountId: String): NoteTagFileState?

    suspend fun pendingOperations(accountId: String): List<PendingNoteTagOperation>

    /**
     * Replaces the mirror with [base] from the server file, removes [completedOperationIds], and
     * replays every remaining pending operation on top, all in one transaction.
     */
    suspend fun applyTagFile(
        accountId: String,
        base: NoteTagSnapshot,
        etag: String?,
        writable: Boolean,
        completedOperationIds: List<Long> = emptyList()
    )

    /** Records that tagging is unavailable and clears the mirror. Pending operations are kept. */
    suspend fun markUnavailable(
        accountId: String,
        availability: NoteTagAvailability,
        message: String?
    )

    suspend fun recordError(accountId: String, message: String?)

    /** Forgets the mirror, the file checkpoint, and pending operations of an account. */
    suspend fun reset(accountId: String)
}

sealed interface NoteTagFileDownload {
    data object NotModified : NoteTagFileDownload

    data object Missing : NoteTagFileDownload

    class Downloaded(val content: ByteArray, val etag: String?) : NoteTagFileDownload
}

/** Transfers the note folder's `notes.sqlite` as an opaque file. */
interface NoteTagFileBackend {
    suspend fun downloadTagFile(account: Account, etag: String?): NoteTagFileDownload

    /**
     * Replaces the file only if its current version still has [etag]. Throws
     * [BackendException.Conflict] when it changed and [BackendException.RemoteMissing] when it is
     * gone. Returns the new version's ETag when the server reports one.
     */
    suspend fun uploadTagFile(account: Account, content: ByteArray, etag: String): String?
}

object NoteTags {
    const val MAX_NAME_LENGTH = 255
    private const val PATH_SEPARATOR = '\u001F'

    /**
     * The key under which a note's tags are stored. It is the name the server knows, so a local
     * rename that has not synchronized yet keeps showing the note's tags.
     */
    fun keyOf(
        title: String,
        category: String,
        lastSyncedTitle: String?,
        lastSyncedCategory: String?
    ): NoteTagKey = NoteTagKey(
        lastSyncedTitle ?: title,
        (lastSyncedCategory ?: category).trim('/')
    )

    fun keyOf(note: Note): NoteTagKey =
        keyOf(note.title, note.category, note.lastSyncedTitle, note.lastSyncedCategory)

    fun keyOf(note: NoteListItem): NoteTagKey =
        keyOf(note.title, note.category, note.syncedTitle, note.syncedCategory)

    /** Returns the trimmed name, or null if it cannot be stored as a tag name. */
    fun normalizeName(name: String): String? = name.trim().takeIf { normalized ->
        normalized.isNotEmpty() &&
            normalized.length <= MAX_NAME_LENGTH &&
            normalized.none { Character.isISOControl(it) }
    }

    /** SQLite `NOCASE` equality, which folds only ASCII letters. */
    fun namesEqual(left: String, right: String): Boolean =
        left.length == right.length && left.indices.all { index ->
            asciiLower(left[index]) == asciiLower(right[index])
        }

    /** The names from the top-level tag down to [tagId], or an empty list for an unknown tag. */
    fun path(tagsById: Map<Long, NoteTag>, tagId: Long): List<String> {
        val names = ArrayDeque<String>()
        val seen = mutableSetOf<Long>()
        var current = tagsById[tagId]
        while (current != null && seen.add(current.id)) {
            names.addFirst(current.name)
            current = if (current.parentId == 0L) null else tagsById[current.parentId]
        }
        return names.toList()
    }

    /** A stable, case-insensitive identity for a tag path, suitable for saved selections. */
    fun pathKey(path: List<String>): String =
        path.joinToString(PATH_SEPARATOR.toString()) { it.map(::asciiLower).joinToString("") }

    fun encodePath(path: List<String>): String {
        require(path.isNotEmpty() && path.none { PATH_SEPARATOR in it }) { "Invalid tag path" }
        return path.joinToString(PATH_SEPARATOR.toString())
    }

    fun decodePath(encoded: String): List<String> = encoded.split(PATH_SEPARATOR)

    fun displayPath(path: List<String>): String = path.joinToString(" › ")

    /** Desktop order for the tags of a note: priority, then name. */
    fun sorted(tags: List<NoteTag>): List<NoteTag> = tags.sortedWith(
        compareBy<NoteTag> { it.priority }.thenBy(String.CASE_INSENSITIVE_ORDER) {
            it.name
        }
    )

    /** Every tag with its full path, sorted by path for pickers. */
    fun withPaths(tags: List<NoteTag>): List<Pair<NoteTag, List<String>>> {
        val byId = tags.associateBy(NoteTag::id)
        return tags.map { it to path(byId, it.id) }
            .sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { (_, path) ->
                    path.joinToString("\u0000")
                }
            )
    }

    /** AND filter: the note must carry every selected tag. An empty selection matches all notes. */
    fun matches(noteTagIds: Set<Long>, selectedTagIds: Set<Long>): Boolean =
        noteTagIds.containsAll(selectedTagIds)

    fun resolve(tags: List<NoteTag>, path: List<String>): NoteTag? {
        var parentId = 0L
        var found: NoteTag? = null
        for (name in path) {
            found = tags.firstOrNull { it.parentId == parentId && namesEqual(it.name, name) }
                ?: return null
            parentId = found.id
        }
        return found
    }

    /**
     * Applies [operation] with the same semantics as the `notes.sqlite` writer. [newTagId] supplies
     * identifiers for tags the operation has to create.
     */
    fun apply(
        snapshot: NoteTagSnapshot,
        operation: NoteTagOperation,
        newTagId: () -> Long
    ): NoteTagSnapshot = when (operation) {
        is NoteTagOperation.Link -> if (operation.tagPath.isEmpty()) {
            snapshot
        } else {
            val tags = snapshot.tags.toMutableList()
            var parentId = 0L
            operation.tagPath.forEach { name ->
                val existing = tags.firstOrNull {
                    it.parentId == parentId && namesEqual(it.name, name)
                }
                parentId = existing?.id ?: NoteTag(newTagId(), name, parentId).also(tags::add).id
            }
            NoteTagSnapshot(tags, snapshot.links + NoteTagLink(parentId, operation.key))
        }
        is NoteTagOperation.Unlink -> {
            val tag = resolve(snapshot.tags, operation.tagPath)
            if (tag == null) {
                snapshot
            } else {
                snapshot.copy(links = snapshot.links - NoteTagLink(tag.id, operation.key))
            }
        }
        is NoteTagOperation.Relink -> {
            if (operation.from == operation.to) {
                snapshot
            } else {
                snapshot.copy(
                    links = snapshot.links.mapTo(mutableSetOf()) { link ->
                        if (link.key == operation.from) link.copy(key = operation.to) else link
                    }
                )
            }
        }
    }

    /** Replays [operations] in order, giving created tags negative identifiers. */
    fun applyAll(snapshot: NoteTagSnapshot, operations: List<NoteTagOperation>): NoteTagSnapshot {
        var nextId = minOf(0L, snapshot.tags.minOfOrNull(NoteTag::id) ?: 0L)
        return operations.fold(snapshot) { current, operation ->
            apply(current, operation) { --nextId }
        }
    }

    private fun asciiLower(char: Char): Char = if (char in 'A'..'Z') char + 32 else char
}
