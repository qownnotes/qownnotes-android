package org.qownnotes.mobile.core

/**
 * The part of an account's notes that the note list shows, modeled on QOwnNotes note subfolders.
 *
 * [path] is a category as the server spells it, where the empty string is the notes root. With
 * [includeSubfolders] the scope also contains every folder below [path], so the root with
 * subfolders is the whole account and the root without them is the notes that have no category.
 */
data class NoteFolderScope(val path: String = "", val includeSubfolders: Boolean = false) {
    val isRoot: Boolean get() = path.isEmpty()

    /** Whether the scope covers every note of the account, so no category filter applies. */
    val isWholeAccount: Boolean get() = isRoot && includeSubfolders

    companion object {
        /**
         * Reads a scope stored by the earlier flat selector. [includeSubfolders] is the separately
         * stored subfolder choice, or `null` before one was stored, in which case "All categories"
         * becomes the root with subfolders and every other choice keeps showing the same notes.
         */
        fun fromCategoryScope(scope: NoteCategoryScope, includeSubfolders: Boolean?) =
            when (scope) {
                NoteCategoryScope.Undefined -> NoteFolderScope("", includeSubfolders ?: false)
                NoteCategoryScope.All -> NoteFolderScope("", includeSubfolders ?: true)
                is NoteCategoryScope.Category ->
                    NoteFolderScope(scope.value, includeSubfolders ?: false)
            }
    }
}

/** A folder derived from the categories of cached notes. */
data class NoteFolder(
    /** Category path as first seen in the notes, with `/` between nested folders. */
    val path: String,
    /** Last path segment. */
    val name: String,
    /** Zero for a top-level folder. */
    val depth: Int,
    /** Notes whose category is exactly this folder. */
    val noteCount: Int,
    /** Notes in this folder and every folder below it. */
    val subtreeNoteCount: Int,
    val children: List<NoteFolder>
)

/**
 * Folder tree of one account. Folders exist while at least one note is inside them, and folders
 * created in the app stay listed without notes until a note is put into them.
 */
data class NoteFolderTree(
    /** Notes without a category. */
    val rootNoteCount: Int,
    /** Every note, which is also the count of the root including its subfolders. */
    val totalNoteCount: Int,
    val folders: List<NoteFolder>
) {
    /** Every folder in display order, parents before their children. */
    fun flatten(): List<NoteFolder> = visible { true }

    /** Folders in display order, descending only into folders for which [expanded] is true. */
    fun visible(expanded: (NoteFolder) -> Boolean): List<NoteFolder> = buildList {
        fun visit(folder: NoteFolder) {
            add(folder)
            if (expanded(folder)) folder.children.forEach(::visit)
        }
        folders.forEach(::visit)
    }

    /** Finds a folder case-insensitively, as the server's storage may be case-insensitive. */
    fun find(path: String): NoteFolder? {
        val key = NoteFolders.key(path)
        return flatten().firstOrNull { NoteFolders.key(it.path) == key }
    }

    /** Whether [path] still exists; the root always does. */
    fun contains(path: String): Boolean = path.isEmpty() || find(path) != null

    /** Number of notes [scope] shows. */
    fun count(scope: NoteFolderScope): Int = when {
        scope.isRoot -> if (scope.includeSubfolders) totalNoteCount else rootNoteCount
        else -> find(scope.path)?.let {
            if (scope.includeSubfolders) it.subtreeNoteCount else it.noteCount
        } ?: 0
    }
}

/**
 * Folder policy for categories.
 *
 * Folders compare case-insensitively for ASCII letters only, which is what SQLite's `NOCASE`
 * collation and `LIKE` do, so the derived tree and the database query always agree.
 */
object NoteFolders {
    /** Escape character of [subfolderPattern], for an `ESCAPE '\'` clause. */
    const val LIKE_ESCAPE = '\\'

    /**
     * Builds the folder tree from the category of every note. With [nested] false the backend has
     * no hierarchy, so each category is a top-level folder even when it contains a `/`.
     * [emptyFolders] are folders created in the app that are listed even without notes.
     */
    fun tree(
        categories: Iterable<String>,
        nested: Boolean = true,
        emptyFolders: Iterable<String> = emptyList()
    ): NoteFolderTree {
        class Node(val path: String, val name: String, val depth: Int) {
            var count = 0
            val children = linkedMapOf<String, Node>()
        }

        val top = linkedMapOf<String, Node>()
        var rootCount = 0
        var total = 0

        fun nodeOf(category: String): Node? {
            val segments = if (nested) {
                category.split('/').filter(String::isNotEmpty)
            } else {
                listOf(category)
            }
            var level = top
            var node: Node? = null
            segments.forEachIndexed { index, segment ->
                // Children extend the spelling their parent was first seen with.
                val path = node?.let { "${it.path}/$segment" } ?: segment
                node = level.getOrPut(key(segment)) { Node(path, segment, index) }
                level = node!!.children
            }
            return node
        }

        categories.forEach { category ->
            if (category.isEmpty()) {
                rootCount++
                total++
                return@forEach
            }
            if (NoteCategories.isInternal(category)) return@forEach
            total++
            val node = nodeOf(category)
            if (node == null) rootCount++ else node.count++
        }
        emptyFolders.forEach { folder ->
            if (folder.isNotEmpty() && !NoteCategories.isInternal(folder)) nodeOf(folder)
        }

        fun build(node: Node): NoteFolder {
            val children = node.children.values.map(::build)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, NoteFolder::name))
            return NoteFolder(
                path = node.path,
                name = node.name,
                depth = node.depth,
                noteCount = node.count,
                subtreeNoteCount = node.count + children.sumOf(NoteFolder::subtreeNoteCount),
                children = children
            )
        }
        return NoteFolderTree(
            rootNoteCount = rootCount,
            totalNoteCount = total,
            folders = top.values.map(::build)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, NoteFolder::name))
        )
    }

    /** Whether a note with [category] belongs to [scope]. */
    fun matches(category: String, scope: NoteFolderScope, nested: Boolean = true): Boolean {
        if (scope.isRoot) return scope.includeSubfolders || category.isEmpty()
        val categoryKey = key(category)
        val scopeKey = key(scope.path)
        return categoryKey == scopeKey ||
            nested && scope.includeSubfolders && categoryKey.startsWith("$scopeKey/")
    }

    /** Whether [path] is [folder] itself or a folder below it. The root contains every path. */
    fun isWithin(path: String, folder: String): Boolean {
        if (folder.isEmpty()) return true
        val pathKey = key(path)
        val folderKey = key(folder)
        return pathKey == folderKey || pathKey.startsWith("$folderKey/")
    }

    /**
     * Where [path] ends up when folder [from] becomes [to]: [to] itself, the same subpath below
     * [to], or `null` when [path] is outside [from].
     */
    fun moved(path: String, from: String, to: String): String? = when {
        from.isEmpty() -> null
        key(path) == key(from) -> to
        isWithin(path, from) -> to + path.substring(from.length)
        else -> null
    }

    /** The folder containing [path], or the empty root. */
    fun parent(path: String): String = path.substringBeforeLast('/', "")

    /** Joins a parent folder and a relative folder path, either of which may be the root. */
    fun join(parent: String, child: String): String = when {
        parent.isEmpty() -> child
        child.isEmpty() -> parent
        else -> "$parent/$child"
    }

    /**
     * `LIKE` pattern for every folder strictly below [path], escaping the `%` and `_` that are
     * legal in folder names. Use it with [LIKE_ESCAPE]; the root needs no pattern.
     */
    fun subfolderPattern(path: String): String = buildString {
        path.forEach { character ->
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                append(LIKE_ESCAPE)
            }
            append(character)
        }
        append("/%")
    }

    /** Comparison key that folds ASCII letters only, like SQLite `NOCASE`. */
    fun key(path: String): String = buildString(path.length) {
        path.forEach { append(if (it in 'A'..'Z') it + ('a' - 'A') else it) }
    }
}
