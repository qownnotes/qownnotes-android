package org.qownnotes.mobile.core

/** What the server's file listing tells about one folder in the user's files. */
data class RemoteNoteFolder(
    /** Path relative to the user's files root, with `/` between segments. */
    val path: String,
    /** Note files directly in the folder. */
    val noteCount: Int,
    /** Folders directly in the folder, which may hold more notes. */
    val subfolderCount: Int,
    /** Whether the folder has a QOwnNotes desktop `notes.sqlite`. */
    val hasTagDatabase: Boolean
) {
    /** Last path segment. */
    val name: String get() = path.substringAfterLast('/')

    val isEmpty: Boolean get() = noteCount == 0 && subfolderCount == 0 && !hasTagDatabase
}

/**
 * The account's current note folder next to other folders that may already hold the user's notes,
 * for example the localized folder Nextcloud Notes created next to the `Notes` folder of QOwnNotes
 * desktop. Nothing is ever switched or moved because of it; the user decides.
 */
data class NoteFolderDiscovery(
    val currentPath: String,
    /** The current folder, or `null` while it does not exist on the server yet. */
    val current: RemoteNoteFolder?,
    val candidates: List<RemoteNoteFolder>
)

/** One folder of the user's files, opened in the note folder picker. */
data class RemoteFolderListing(
    val folder: RemoteNoteFolder,
    /** Names of the visible folders directly inside, sorted for display. */
    val subfolders: List<String>
)

/** Finds and browses note folders, for a backend that can list the user's files. */
interface NoteFolderDiscoveryBackend {
    suspend fun discoverNoteFolders(account: Account): NoteFolderDiscovery

    /**
     * Lists the folder at [path], relative to the user's files root, where the empty path is the
     * root. Counts notes by the server's extensions and [fileSuffix]. Returns `null` when the
     * folder does not exist.
     */
    suspend fun browseFolder(
        account: Account,
        path: String,
        fileSuffix: String
    ): RemoteFolderListing?
}

/** Which folders count as possible note folders, and in which order they are offered. */
object NoteFolderCandidates {
    /**
     * Names Nextcloud Notes gives the folder it creates, which follow the user's language, and
     * `Notes`, the folder QOwnNotes desktop suggests. Compared without regard to case.
     */
    val DEFAULT_FOLDER_NAMES: Set<String> = setOf(
        "Notes", "Notizen", "Notas", "Note", "Notities", "Noter", "Notater", "Notat", "Notatki",
        "Anteckningar", "Muistiinpanot", "Jegyzetek", "Poznámky", "Notlar", "Notițe", "Bilješke",
        "Beležka", "Catatan", "Ghi chép", "Märkmed", "Piezīmes", "Užrašai", "Oharrak", "Notoj",
        "Nòtas", "Nótaí", "Notennoù", "Nòtaichean", "Nodiadau", "Minnispunktar", "Shënime",
        "Qeydlər", "Nota", "Mazingatio", "Tizmilin", "Σημειώσεις", "Заметки", "Нотатки",
        "Бележки", "Белешке", "Белешки", "Нататкі", "Тэмдэглэл", "Նոթեր", "הערות", "الملاحظات",
        "ئىزاھاتلار", "یادداشت\u200cها", "নোট", "குறிப்புகள்", "සටහන්", "โน้ต", "ჩანაწერები",
        "កំណត់\u200bចំណាំ", "ノート", "笔记", "筆記", "메모"
    )

    /** Extensions the Notes server treats as notes, besides the user's configured suffix. */
    private val NOTE_EXTENSIONS = setOf("txt", "org", "markdown", "md", "note")

    private val defaultNames = DEFAULT_FOLDER_NAMES.mapTo(HashSet()) { it.lowercase() }

    fun isDefaultFolderName(name: String): Boolean = name.trim().lowercase() in defaultNames

    fun isNoteFile(fileName: String, fileSuffix: String): Boolean {
        val lower = fileName.lowercase()
        if (lower.startsWith('.')) return false
        val suffix = fileSuffix.lowercase().removePrefix(".")
        val extension = lower.substringAfterLast('.', missingDelimiterValue = "")
        return extension in NOTE_EXTENSIONS ||
            (suffix.isNotEmpty() && lower.endsWith(".$suffix") && lower.length > suffix.length + 1)
    }

    /**
     * The folders worth offering instead of [currentPath]: never the current folder, a folder
     * inside it, or a folder it lies in, and never an empty one. Folders QOwnNotes desktop uses
     * come first, then the fuller ones.
     */
    fun offered(currentPath: String, folders: List<RemoteNoteFolder>): List<RemoteNoteFolder> {
        val current = normalize(currentPath)
        return folders
            .filter { folder ->
                val path = normalize(folder.path)
                path.isNotEmpty() && !folder.isEmpty && !related(path, current)
            }
            .distinctBy { normalize(it.path) }
            .sortedWith(
                compareByDescending<RemoteNoteFolder> { it.hasTagDatabase }
                    .thenByDescending { it.noteCount }
                    .thenByDescending { it.subfolderCount }
                    .thenBy { it.path.lowercase() }
            )
    }

    /** A path relative to the user's files root, without empty segments or outer slashes. */
    fun normalize(path: String): String =
        path.split('/', '\\').filter(String::isNotBlank).joinToString("/")

    /** The folder containing [path], or the empty root path for a top-level folder. */
    fun parent(path: String): String = normalize(path).substringBeforeLast('/', "")

    /** [name] inside the folder [path]. */
    fun child(path: String, name: String): String = normalize("$path/$name")

    private fun related(path: String, current: String): Boolean =
        current.isEmpty() || path == current ||
            path.startsWith("$current/") || current.startsWith("$path/")
}
