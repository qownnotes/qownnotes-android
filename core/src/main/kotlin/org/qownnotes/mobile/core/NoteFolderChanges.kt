package org.qownnotes.mobile.core

/**
 * Folder changes of one account that notes alone cannot express.
 *
 * Folders are otherwise derived from note categories. A folder created in the app has no notes
 * yet, so it is remembered in [emptyFolders] until a note is put into it, and it is created in the
 * note folder through [pendingCreations], so QOwnNotes desktop sees it as well. Renaming or deleting
 * a folder moves or deletes its notes; the folders left behind are removed through
 * [pendingRemovals] once they are empty.
 *
 * Every list compares folders as [NoteFolders.key] does and holds no duplicates.
 */
data class NoteFolderChanges(
    /** Folders created in the app that hold no notes yet. */
    val emptyFolders: List<String> = emptyList(),
    /** Folders still to be created in the note folder. */
    val pendingCreations: List<String> = emptyList(),
    /** Folders still to be removed from the note folder, unless something is left in them. */
    val pendingRemovals: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = emptyFolders.isEmpty() && pendingCreations.isEmpty() && pendingRemovals.isEmpty()

    /** Adds a new folder, which also cancels an outstanding removal of it or of its parents. */
    fun created(path: String): NoteFolderChanges = copy(
        emptyFolders = emptyFolders.with(path),
        pendingCreations = pendingCreations.with(path),
        pendingRemovals = pendingRemovals.filterNot { NoteFolders.isWithin(path, it) }
    )

    /**
     * Records that folder [from] became [to]. [oldFolders] are the folders that existed at [from]
     * and below it, which are removed from the note folder once their notes have moved.
     */
    fun renamed(from: String, to: String, oldFolders: List<String>): NoteFolderChanges {
        fun List<String>.moved() = map { NoteFolders.moved(it, from, to) ?: it }.distinctFolders()
        val movedEmpty = emptyFolders.filter { NoteFolders.isWithin(it, from) }
            .map { NoteFolders.moved(it, from, to)!! }
        return copy(
            emptyFolders = emptyFolders.moved(),
            // Folders without notes are only created by request, so they are requested again.
            pendingCreations = (pendingCreations.moved() + movedEmpty).distinctFolders(),
            pendingRemovals = (
                pendingRemovals.filterNot { NoteFolders.isWithin(it, to) } +
                    oldFolders.filter { NoteFolders.isWithin(it, from) } + from
                ).distinctFolders()
        )
    }

    /** Records that folder [path] was deleted with [oldFolders], the folders at and below it. */
    fun deleted(path: String, oldFolders: List<String>): NoteFolderChanges = copy(
        emptyFolders = emptyFolders.filterNot { NoteFolders.isWithin(it, path) },
        pendingCreations = pendingCreations.filterNot { NoteFolders.isWithin(it, path) },
        pendingRemovals = (
            pendingRemovals + oldFolders.filter { NoteFolders.isWithin(it, path) } + path
            ).distinctFolders()
    )

    /**
     * Forgets the empty folders that now hold notes, directly or below them. Such a folder is part
     * of the derived tree, and like every other folder it disappears once its last note leaves.
     */
    fun withNotes(categories: Collection<String>): NoteFolderChanges {
        val keys = categories.mapTo(mutableSetOf(), NoteFolders::key)
        val kept = emptyFolders.filterNot { folder ->
            keys.any { NoteFolders.isWithin(it, folder) }
        }
        return if (kept.size == emptyFolders.size) this else copy(emptyFolders = kept)
    }

    /** The removals to try, deepest first, so each parent is checked after its children. */
    fun removalOrder(): List<String> = pendingRemovals.sortedByDescending {
        it.count { c -> c == '/' }
    }

    /** The creations to make, parents first. */
    fun creationOrder(): List<String> = pendingCreations.sortedBy { it.count { c -> c == '/' } }

    private fun List<String>.with(path: String) = (this + path).distinctFolders()

    private fun List<String>.distinctFolders() =
        filter(String::isNotEmpty).distinctBy(NoteFolders::key)
}
