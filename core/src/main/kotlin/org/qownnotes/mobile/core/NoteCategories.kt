package org.qownnotes.mobile.core

sealed interface NoteCategoryScope {
    data object Undefined : NoteCategoryScope

    data object All : NoteCategoryScope

    data class Category(val value: String) : NoteCategoryScope
}

object NoteCategories {
    fun selectable(notes: List<Note>): List<String> = notes.asSequence()
        .map(Note::category)
        .filter(String::isNotEmpty)
        .filterNot(::isInternal)
        .distinctBy(String::lowercase)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        .toList()

    fun matches(category: String, scope: NoteCategoryScope): Boolean = when (scope) {
        NoteCategoryScope.Undefined -> category.isEmpty()
        NoteCategoryScope.All -> true
        is NoteCategoryScope.Category -> category.equals(scope.value, ignoreCase = true)
    }

    /** QOwnNotes reserves these top-level trees for files referenced by notes. */
    fun isInternal(category: String): Boolean {
        val root = category.substringBefore('/')
        return root.equals("media", ignoreCase = true) ||
            root.equals("attachments", ignoreCase = true)
    }
}
