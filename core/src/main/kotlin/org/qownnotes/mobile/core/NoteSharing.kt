package org.qownnotes.mobile.core

/**
 * A public link to a note file, as created by the QOwnNotes desktop share dialog. The password
 * itself is never part of this model; the server only reports whether one is set.
 */
data class NoteShare(
    val id: String,
    val url: String,
    val permissions: Int,
    val passwordProtected: Boolean,
    /** The day the link stops working as `yyyy-MM-dd`, or `null` when it does not expire. */
    val expiresOn: String? = null
) {
    val editAllowed: Boolean
        get() = permissions and NoteSharing.PERMISSION_UPDATE != 0
}

/** What the server allows for public note links. */
data class NoteSharingPolicy(
    val linkSharingEnabled: Boolean,
    val passwordEnforced: Boolean = false,
    /** The server's minimum password length, when its password policy reports one. */
    val passwordMinLength: Int? = null
)

/** A note's public link, if it has one, with the server's policy for creating and changing it. */
data class NoteShareStatus(val policy: NoteSharingPolicy, val share: NoteShare?)

sealed interface NoteShareChange {
    data class EditAllowed(val allowed: Boolean) : NoteShareChange

    /** Sets or replaces the link's password. */
    data class Password(val password: String) : NoteShareChange

    data object RemovePassword : NoteShareChange
}

/** Optional public link sharing of note files, matching the desktop share dialog. */
interface NoteShareBackend {
    /** Loads the note's public link. Only notes that exist on the server can be shared. */
    suspend fun noteShare(account: Account, note: Note): NoteShareStatus

    /** Creates a read-only public link, protected by [password] when it is not `null`. */
    suspend fun createNoteShare(account: Account, note: Note, password: String?): NoteShare

    suspend fun updateNoteShare(
        account: Account,
        share: NoteShare,
        change: NoteShareChange
    ): NoteShare

    /** Removes the link; it stops working immediately and cannot be restored. */
    suspend fun deleteNoteShare(account: Account, share: NoteShare)
}

object NoteSharing {
    const val PERMISSION_READ = 1

    /** The bit the desktop application toggles to allow editing through the link. */
    const val PERMISSION_UPDATE = 2

    fun permissionsWithEdit(permissions: Int, allowed: Boolean): Int = if (allowed) {
        permissions or PERMISSION_UPDATE or PERMISSION_READ
    } else {
        permissions and PERMISSION_UPDATE.inv()
    }

    /** Whether [password] may be submitted under [policy]; the server still has the final say. */
    fun isAcceptablePassword(password: String, policy: NoteSharingPolicy): Boolean =
        password.isNotEmpty() && password.length >= (policy.passwordMinLength ?: 1) &&
            password.none { it == '\r' || it == '\n' }
}
