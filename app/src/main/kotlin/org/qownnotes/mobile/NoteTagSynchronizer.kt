package org.qownnotes.mobile

import java.io.File
import java.util.concurrent.CancellationException
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.NoteTagAvailability
import org.qownnotes.mobile.core.NoteTagFileBackend
import org.qownnotes.mobile.core.NoteTagFileDownload
import org.qownnotes.mobile.core.NoteTagStore
import org.qownnotes.mobile.notefolder.IncompatibleNoteFolderDatabaseException
import org.qownnotes.mobile.notefolder.NoteFolderTagContent
import org.qownnotes.mobile.notefolder.NoteFolderTagDatabase

/**
 * Keeps an account's tag mirror in step with the note folder's `notes.sqlite`.
 *
 * The file is downloaded only when its ETag changes. Pending tag operations are replayed on the
 * newest server version and uploaded with `If-Match`, so a concurrent change by QOwnNotes desktop
 * is never overwritten: the upload is retried on the newer file instead.
 *
 * Callers serialize synchronization per account.
 */
internal class NoteTagSynchronizer(
    private val store: NoteTagStore,
    private val backend: NoteTagFileBackend,
    private val directory: File
) {
    suspend fun synchronize(account: Account) {
        try {
            synchronizeOrThrow(account)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IncompatibleNoteFolderDatabaseException) {
            cacheFile(account.id).delete()
            store.markUnavailable(account.id, NoteTagAvailability.INCOMPATIBLE, error.message)
        } catch (error: Exception) {
            // Tags never block note synchronization. The next synchronization retries.
            store.recordError(account.id, error.message ?: "Tags could not be synchronized")
        }
    }

    fun forget(accountId: String) {
        cacheFile(accountId).delete()
    }

    private suspend fun synchronizeOrThrow(account: Account) {
        directory.mkdirs()
        val cache = cacheFile(account.id)
        val state = store.fileState(account.id)
        var etag = state?.etag?.takeIf {
            state.availability == NoteTagAvailability.AVAILABLE && cache.isFile
        }
        repeat(MAX_UPLOAD_ATTEMPTS) {
            val downloaded = when (val download = backend.downloadTagFile(account, etag)) {
                NoteTagFileDownload.Missing -> {
                    cache.delete()
                    store.markUnavailable(account.id, NoteTagAvailability.MISSING, MISSING_MESSAGE)
                    return
                }
                NoteTagFileDownload.NotModified -> false
                is NoteTagFileDownload.Downloaded -> {
                    val incoming = temporaryFile(account.id)
                    try {
                        incoming.writeBytes(download.content)
                        NoteFolderTagDatabase.read(incoming)
                        replace(incoming, cache)
                    } finally {
                        incoming.delete()
                    }
                    etag = download.etag
                    true
                }
            }
            val pending = store.pendingOperations(account.id)
            if (
                !downloaded && pending.isEmpty() &&
                state?.availability == NoteTagAvailability.AVAILABLE && state.message == null
            ) {
                return
            }
            val content = NoteFolderTagDatabase.read(cache)
            val currentEtag = etag
            if (pending.isEmpty() || !content.writable || currentEtag == null) {
                // Without an ETag the file cannot be replaced safely; operations stay pending.
                apply(account.id, content, currentEtag)
                if (pending.isNotEmpty() && currentEtag == null) {
                    store.recordError(
                        account.id,
                        "notes.sqlite has no ETag; tag changes are waiting"
                    )
                }
                return
            }
            val work = temporaryFile(account.id)
            try {
                cache.copyTo(work, overwrite = true)
                val changed = NoteFolderTagDatabase.apply(work, pending.map { it.operation })
                if (!changed) {
                    apply(account.id, content, currentEtag, pending.map { it.id })
                    return
                }
                val uploadedEtag = try {
                    backend.uploadTagFile(account, work.readBytes(), currentEtag)
                } catch (_: BackendException.Conflict) {
                    // Someone else changed the file. Replay on their version.
                    etag = null
                    return@repeat
                }
                replace(work, cache)
                apply(
                    account.id,
                    NoteFolderTagDatabase.read(cache),
                    uploadedEtag,
                    pending.map { it.id }
                )
                return
            } finally {
                work.delete()
            }
        }
        store.recordError(account.id, "notes.sqlite kept changing; tag changes will be retried")
    }

    private suspend fun apply(
        accountId: String,
        content: NoteFolderTagContent,
        etag: String?,
        completed: List<Long> = emptyList()
    ) = store.applyTagFile(accountId, content.snapshot, etag, content.writable, completed)

    private fun replace(source: File, target: File) {
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
        }
    }

    private fun cacheFile(accountId: String) = File(directory, "${safeName(accountId)}.sqlite")

    private fun temporaryFile(accountId: String) =
        File.createTempFile(safeName(accountId), ".sqlite", directory)

    private fun safeName(accountId: String) = accountId.filter { it.isLetterOrDigit() || it == '-' }
        .ifEmpty { "account" }

    private companion object {
        const val MAX_UPLOAD_ATTEMPTS = 3
        const val MISSING_MESSAGE =
            "The Nextcloud notes folder has no notes.sqlite. Tags are available when QOwnNotes " +
                "desktop uses this folder and syncs its notes.sqlite file."
    }
}
