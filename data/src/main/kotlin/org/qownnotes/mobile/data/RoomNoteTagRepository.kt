package org.qownnotes.mobile.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.qownnotes.mobile.core.NoteTag
import org.qownnotes.mobile.core.NoteTagAvailability
import org.qownnotes.mobile.core.NoteTagFileState
import org.qownnotes.mobile.core.NoteTagKey
import org.qownnotes.mobile.core.NoteTagLink
import org.qownnotes.mobile.core.NoteTagOperation
import org.qownnotes.mobile.core.NoteTagRepository
import org.qownnotes.mobile.core.NoteTagSnapshot
import org.qownnotes.mobile.core.NoteTagState
import org.qownnotes.mobile.core.NoteTagStore
import org.qownnotes.mobile.core.NoteTags
import org.qownnotes.mobile.core.PendingNoteTagOperation

/**
 * Room mirror of an account's `notes.sqlite` tags with every pending local operation applied.
 *
 * Replacing the mirror and recording a user change both replay operations inside one transaction,
 * so a change made while a download is being applied is never lost.
 */
class RoomNoteTagRepository(private val database: QOwnNotesDatabase) :
    NoteTagRepository,
    NoteTagStore {
    private val dao = database.noteTagDao()

    override fun observe(accountId: String): Flow<NoteTagState> = combine(
        dao.observeFile(accountId),
        dao.observeTags(accountId),
        dao.observeLinks(accountId)
    ) { file, tags, links ->
        NoteTagState(
            availability = file?.availability.toAvailability(),
            writable = file?.writable == true,
            tags = tags.map(NoteTagEntity::toDomain),
            tagIdsByNote = links.groupBy(
                { NoteTagKey(it.fileName, it.subFolderPath) },
                NoteTagLinkEntity::tagId
            ).mapValues { (_, ids) -> ids.toSet() },
            message = file?.message
        )
    }.distinctUntilChanged()

    override suspend fun link(accountId: String, key: NoteTagKey, tagPath: List<String>): Boolean =
        record(accountId, NoteTagOperation.Link(key, tagPath))

    override suspend fun unlink(
        accountId: String,
        key: NoteTagKey,
        tagPath: List<String>
    ): Boolean = record(accountId, NoteTagOperation.Unlink(key, tagPath))

    private suspend fun record(accountId: String, operation: NoteTagOperation): Boolean =
        database.withTransaction {
            val file = dao.file(accountId)
            if (file?.availability.toAvailability() != NoteTagAvailability.AVAILABLE ||
                !file!!.writable
            ) {
                return@withTransaction false
            }
            val before = snapshot(accountId)
            val after = NoteTags.applyAll(before, listOf(operation))
            if (after == before) return@withTransaction false
            val beforeTagIds = before.tags.mapTo(mutableSetOf(), NoteTag::id)
            dao.insertTags(
                after.tags.filterNot { it.id in beforeTagIds }.map { it.toEntity(accountId) }
            )
            dao.deleteLinks((before.links - after.links).map { it.toEntity(accountId) })
            dao.insertLinks((after.links - before.links).map { it.toEntity(accountId) })
            dao.insertOperation(operation.toEntity(accountId))
            true
        }

    override suspend fun fileState(accountId: String): NoteTagFileState? =
        dao.file(accountId)?.let { file ->
            NoteTagFileState(
                availability = file.availability.toAvailability(),
                etag = file.etag,
                writable = file.writable,
                message = file.message
            )
        }

    override suspend fun pendingOperations(accountId: String): List<PendingNoteTagOperation> =
        dao.pendingOperations(accountId).mapNotNull { entity ->
            entity.toDomain()?.let { PendingNoteTagOperation(entity.id, it) }
        }

    override suspend fun applyTagFile(
        accountId: String,
        base: NoteTagSnapshot,
        etag: String?,
        writable: Boolean,
        completedOperationIds: List<Long>
    ) {
        database.withTransaction {
            if (database.accountDao().get(accountId) == null) return@withTransaction
            completedOperationIds.chunked(MAX_BOUND_ARGUMENTS).forEach { dao.deleteOperations(it) }
            val effective = NoteTags.applyAll(
                base,
                dao.pendingOperations(accountId).mapNotNull(PendingTagOperationEntity::toDomain)
            )
            replaceMirror(accountId, effective)
            dao.upsertFile(
                TagFileEntity(
                    accountId = accountId,
                    availability = NoteTagAvailability.AVAILABLE.name,
                    etag = etag,
                    writable = writable,
                    message = null
                )
            )
        }
    }

    override suspend fun markUnavailable(
        accountId: String,
        availability: NoteTagAvailability,
        message: String?
    ) {
        database.withTransaction {
            if (database.accountDao().get(accountId) == null) return@withTransaction
            dao.deleteLinks(accountId)
            dao.deleteTags(accountId)
            dao.upsertFile(
                TagFileEntity(accountId, availability.name, etag = null, writable = false, message)
            )
        }
    }

    override suspend fun recordError(accountId: String, message: String?) {
        database.withTransaction {
            val file = dao.file(accountId) ?: return@withTransaction
            dao.upsertFile(file.copy(message = message))
        }
    }

    override suspend fun reset(accountId: String) {
        database.withTransaction {
            dao.deleteLinks(accountId)
            dao.deleteTags(accountId)
            dao.deleteOperations(accountId)
            dao.deleteFile(accountId)
        }
    }

    private suspend fun snapshot(accountId: String) = NoteTagSnapshot(
        tags = dao.tags(accountId).map(NoteTagEntity::toDomain),
        links = dao.links(accountId).mapTo(mutableSetOf()) {
            NoteTagLink(it.tagId, NoteTagKey(it.fileName, it.subFolderPath))
        }
    )

    private suspend fun replaceMirror(accountId: String, snapshot: NoteTagSnapshot) {
        dao.deleteLinks(accountId)
        dao.deleteTags(accountId)
        snapshot.tags.map { it.toEntity(accountId) }.chunked(INSERT_BATCH).forEach {
            dao.insertTags(it)
        }
        snapshot.links.map { it.toEntity(accountId) }.chunked(INSERT_BATCH).forEach {
            dao.insertLinks(it)
        }
    }

    private companion object {
        const val INSERT_BATCH = 500
        const val MAX_BOUND_ARGUMENTS = 900
    }
}

private fun String?.toAvailability(): NoteTagAvailability =
    NoteTagAvailability.entries.firstOrNull { it.name == this } ?: NoteTagAvailability.UNKNOWN

private fun NoteTagEntity.toDomain() = NoteTag(tagId, name, parentId, color, priority)

private fun NoteTag.toEntity(accountId: String) =
    NoteTagEntity(accountId, id, name, parentId, color, priority)

private fun NoteTagLink.toEntity(accountId: String) =
    NoteTagLinkEntity(accountId, tagId, key.fileName, key.subFolderPath)

private fun NoteTagOperation.toEntity(accountId: String) = when (this) {
    is NoteTagOperation.Link -> PendingTagOperationEntity(
        accountId = accountId,
        type = LINK,
        fileName = key.fileName,
        subFolderPath = key.subFolderPath,
        tagPath = NoteTags.encodePath(tagPath)
    )
    is NoteTagOperation.Unlink -> PendingTagOperationEntity(
        accountId = accountId,
        type = UNLINK,
        fileName = key.fileName,
        subFolderPath = key.subFolderPath,
        tagPath = NoteTags.encodePath(tagPath)
    )
    is NoteTagOperation.Relink -> PendingTagOperationEntity(
        accountId = accountId,
        type = RELINK,
        fileName = from.fileName,
        subFolderPath = from.subFolderPath,
        targetFileName = to.fileName,
        targetSubFolderPath = to.subFolderPath
    )
}

private fun PendingTagOperationEntity.toDomain(): NoteTagOperation? {
    val key = NoteTagKey(fileName, subFolderPath)
    return when (type) {
        LINK -> tagPath?.let { NoteTagOperation.Link(key, NoteTags.decodePath(it)) }
        UNLINK -> tagPath?.let { NoteTagOperation.Unlink(key, NoteTags.decodePath(it)) }
        RELINK -> if (targetFileName != null && targetSubFolderPath != null) {
            NoteTagOperation.Relink(key, NoteTagKey(targetFileName, targetSubFolderPath))
        } else {
            null
        }
        else -> null
    }
}

private const val LINK = "LINK"
private const val UNLINK = "UNLINK"
private const val RELINK = "RELINK"
