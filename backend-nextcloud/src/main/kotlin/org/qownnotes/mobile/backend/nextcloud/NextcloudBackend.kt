package org.qownnotes.mobile.backend.nextcloud

import org.qownnotes.mobile.core.BackendCapabilities
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteBackend

/** Phase 1 boundary; SSO and API behavior are implemented in Phase 2. */
class NextcloudBackend : NoteBackend {
    override val capabilities =
        BackendCapabilities(categories = true, favorites = true, readOnlyNotes = true)

    override suspend fun pull(accountId: String): List<Note> =
        error("Nextcloud synchronization is not implemented yet")

    override suspend fun push(note: Note): Note =
        error("Nextcloud synchronization is not implemented yet")
}
