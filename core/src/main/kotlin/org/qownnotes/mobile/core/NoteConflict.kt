package org.qownnotes.mobile.core

data class NoteVersionSnapshot(
    val title: String,
    val content: String,
    val category: String,
    val modifiedAtEpochSeconds: Long?,
    val etag: String?,
    val readOnly: Boolean,
    val favorite: Boolean
)

data class NoteConflict(
    val localId: String,
    val localRevision: Long,
    val remoteId: Long,
    val base: NoteVersionSnapshot,
    val local: NoteVersionSnapshot,
    val remote: NoteVersionSnapshot
)

data class MergedNoteFields(
    val title: String,
    val content: String,
    val category: String,
    val favorite: Boolean
)

enum class NoteMergeField {
    TITLE,
    CONTENT,
    CATEGORY,
    FAVORITE
}

data class NoteMergeResult(val merged: MergedNoteFields, val conflicts: Set<NoteMergeField>) {
    val isClean: Boolean
        get() = conflicts.isEmpty()
}

fun mergeNoteConflict(conflict: NoteConflict): NoteMergeResult {
    val conflicts = mutableSetOf<NoteMergeField>()
    val title = mergeValue(conflict.base.title, conflict.local.title, conflict.remote.title)
        ?: conflict.local.title.also { conflicts += NoteMergeField.TITLE }
    val content = mergeText(
        conflict.base.content,
        conflict.local.content,
        conflict.remote.content
    ) ?: conflict.local.content.also { conflicts += NoteMergeField.CONTENT }
    val category = mergeValue(
        conflict.base.category,
        conflict.local.category,
        conflict.remote.category
    ) ?: conflict.local.category.also { conflicts += NoteMergeField.CATEGORY }
    val favorite = mergeValue(
        conflict.base.favorite,
        conflict.local.favorite,
        conflict.remote.favorite
    ) ?: conflict.local.favorite.also { conflicts += NoteMergeField.FAVORITE }
    return NoteMergeResult(MergedNoteFields(title, content, category, favorite), conflicts)
}

private fun <T> mergeValue(base: T, local: T, remote: T): T? = when {
    local == remote -> local
    local == base -> remote
    remote == base -> local
    else -> null
}

/** Safely merges one contiguous line edit from each side and refuses overlapping changes. */
private fun mergeText(base: String, local: String, remote: String): String? {
    mergeValue(base, local, remote)?.let { return it }
    val baseLines = base.split('\n')
    val localEdit = singleEdit(baseLines, local.split('\n'))
    val remoteEdit = singleEdit(baseLines, remote.split('\n'))
    if (localEdit.overlaps(remoteEdit)) return null

    val merged = baseLines.toMutableList()
    listOf(localEdit, remoteEdit).sortedByDescending(Edit::start).forEach { edit ->
        merged.subList(edit.start, edit.end).clear()
        merged.addAll(edit.start, edit.replacement)
    }
    return merged.joinToString("\n")
}

private data class Edit(val start: Int, val end: Int, val replacement: List<String>) {
    fun overlaps(other: Edit): Boolean {
        if (start == other.start) return true
        return if (start < other.start) end > other.start else other.end > start
    }
}

private fun singleEdit(base: List<String>, changed: List<String>): Edit {
    var prefix = 0
    while (prefix < base.size && prefix < changed.size && base[prefix] == changed[prefix]) prefix++
    var suffix = 0
    while (
        suffix < base.size - prefix &&
        suffix < changed.size - prefix &&
        base[base.lastIndex - suffix] == changed[changed.lastIndex - suffix]
    ) {
        suffix++
    }
    return Edit(
        start = prefix,
        end = base.size - suffix,
        replacement = changed.subList(prefix, changed.size - suffix)
    )
}
