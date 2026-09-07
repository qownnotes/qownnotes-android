package org.qownnotes.mobile

import java.util.concurrent.ConcurrentHashMap

internal class EditorDraftCache {
    private data class Draft(val content: String, val persisted: Boolean)

    private val drafts = ConcurrentHashMap<String, Draft>()

    fun cache(localId: String, content: String) {
        drafts[localId] = Draft(content, persisted = false)
    }

    fun current(localId: String, content: String): Boolean =
        drafts[localId]?.content?.let { it == content } ?: true

    fun markPersisted(localId: String, content: String) {
        drafts.computeIfPresent(localId) { _, current ->
            if (current.content == content) current.copy(persisted = true) else current
        }
    }

    fun replaceWithPersisted(localId: String, content: String) {
        drafts[localId] = Draft(content, persisted = true)
    }

    fun restore(localId: String, persistedContent: String): String =
        drafts[localId]?.takeUnless(Draft::persisted)?.content ?: persistedContent

    fun remove(localIds: Collection<String>) {
        localIds.forEach(drafts::remove)
    }
}
