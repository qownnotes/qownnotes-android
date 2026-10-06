package org.qownnotes.mobile.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteConflict
import org.qownnotes.mobile.core.NoteListItem
import org.qownnotes.mobile.core.NoteVersionSnapshot
import org.qownnotes.mobile.core.SyncDiagnostic
import org.qownnotes.mobile.core.SyncDiagnosticSource
import org.qownnotes.mobile.core.SyncState

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val serverUrl: String,
    val ssoAccountName: String = "",
    val userId: String = "",
    val apiVersion: String? = null,
    val collectionEtag: String? = null,
    val lastModifiedEpochSeconds: Long = 0,
    val lastSyncError: String? = null
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("accountId"),
        Index(value = ["accountId", "remoteId"], unique = true),
        Index(value = ["accountId", "category"])
    ]
)
data class NoteEntity(
    @PrimaryKey val localId: String,
    val accountId: String,
    val remoteId: Long?,
    val title: String,
    val content: String,
    val category: String,
    val modifiedAtEpochSeconds: Long,
    val remoteEtag: String?,
    val readOnly: Boolean,
    val favorite: Boolean = false,
    val syncState: SyncState,
    val lastSyncedTitle: String?,
    val lastSyncedContent: String?,
    val lastSyncedCategory: String?,
    val lastSyncedFavorite: Boolean? = null,
    val lastSyncError: String?,
    val localRevision: Long = 0
)

@Entity(
    tableName = "sync_diagnostics",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class SyncDiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val occurredAtEpochSeconds: Long,
    val source: SyncDiagnosticSource,
    val category: String,
    val details: String
)

@Entity(
    tableName = "note_conflicts",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["localId"],
            childColumns = ["localId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteConflictEntity(
    @PrimaryKey val localId: String,
    val remoteId: Long,
    val remoteTitle: String,
    val remoteContent: String,
    val remoteCategory: String,
    val remoteModifiedAtEpochSeconds: Long,
    val remoteEtag: String,
    val remoteReadOnly: Boolean,
    val remoteFavorite: Boolean
)

/**
 * Lightweight projection of a note for list/search screens. It deliberately omits
 * [content] and [lastSyncedContent] so that a single large note cannot exceed the
 * Android CursorWindow limit when the UI reads many rows.
 */
data class NoteListItemEntity(
    val localId: String,
    val accountId: String,
    val remoteId: Long?,
    val title: String,
    val category: String,
    val modifiedAtEpochSeconds: Long,
    val favorite: Boolean,
    val syncState: SyncState,
    val excerpt: String,
    val lastSyncedTitle: String? = null,
    val lastSyncedCategory: String? = null
)

data class RemoteNoteReference(
    val localId: String,
    val remoteId: Long,
    val category: String,
    val syncState: SyncState
)

/** Mirror of a tag in the account's `notes.sqlite`, or a pending local tag with a negative id. */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["accountId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteTagEntity(
    val accountId: String,
    val tagId: Long,
    val name: String,
    val parentId: Long,
    val color: String?,
    val priority: Int
)

@Entity(
    tableName = "note_tag_links",
    primaryKeys = ["accountId", "tagId", "fileName", "subFolderPath"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId", "fileName", "subFolderPath"])]
)
data class NoteTagLinkEntity(
    val accountId: String,
    val tagId: Long,
    val fileName: String,
    val subFolderPath: String
)

/** A tag change that still has to be written to `notes.sqlite`, in replay order. */
@Entity(
    tableName = "pending_tag_operations",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class PendingTagOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val type: String,
    val fileName: String,
    val subFolderPath: String,
    val targetFileName: String? = null,
    val targetSubFolderPath: String? = null,
    val tagPath: String? = null
)

@Entity(
    tableName = "tag_files",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TagFileEntity(
    @PrimaryKey val accountId: String,
    val availability: String,
    val etag: String?,
    val writable: Boolean,
    val message: String?
)

fun NoteEntity.toDomain() = Note(
    localId = localId,
    accountId = accountId,
    remoteId = remoteId,
    title = title,
    content = content,
    category = category,
    modifiedAtEpochSeconds = modifiedAtEpochSeconds,
    remoteEtag = remoteEtag,
    readOnly = readOnly,
    favorite = favorite,
    syncState = syncState,
    lastSyncedTitle = lastSyncedTitle,
    lastSyncedContent = lastSyncedContent,
    lastSyncedCategory = lastSyncedCategory,
    lastSyncedFavorite = lastSyncedFavorite,
    lastSyncError = lastSyncError,
    localRevision = localRevision
)

fun Note.toEntity() = NoteEntity(
    localId = localId,
    accountId = accountId,
    remoteId = remoteId,
    title = title,
    content = content,
    category = category,
    modifiedAtEpochSeconds = modifiedAtEpochSeconds,
    remoteEtag = remoteEtag,
    readOnly = readOnly,
    favorite = favorite,
    syncState = syncState,
    lastSyncedTitle = lastSyncedTitle,
    lastSyncedContent = lastSyncedContent,
    lastSyncedCategory = lastSyncedCategory,
    lastSyncedFavorite = lastSyncedFavorite,
    lastSyncError = lastSyncError,
    localRevision = localRevision
)

fun NoteListItemEntity.toDomain() = NoteListItem(
    localId = localId,
    accountId = accountId,
    remoteId = remoteId,
    title = title,
    category = category,
    modifiedAtEpochSeconds = modifiedAtEpochSeconds,
    favorite = favorite,
    syncState = syncState,
    excerpt = excerpt,
    syncedTitle = lastSyncedTitle,
    syncedCategory = lastSyncedCategory
)

fun SyncDiagnosticEntity.toDomain() = SyncDiagnostic(
    id = id,
    accountId = accountId,
    occurredAtEpochSeconds = occurredAtEpochSeconds,
    source = source,
    category = category,
    details = details
)

fun SyncDiagnostic.toEntity() = SyncDiagnosticEntity(
    id = id,
    accountId = accountId,
    occurredAtEpochSeconds = occurredAtEpochSeconds,
    source = source,
    category = category,
    details = details
)

fun NoteConflictEntity.toDomain(note: NoteEntity) = NoteConflict(
    localId = localId,
    localRevision = note.localRevision,
    remoteId = remoteId,
    base = NoteVersionSnapshot(
        title = note.lastSyncedTitle ?: note.title,
        content = note.lastSyncedContent ?: note.content,
        category = note.lastSyncedCategory ?: note.category,
        modifiedAtEpochSeconds = null,
        etag = note.remoteEtag,
        readOnly = false,
        favorite = note.lastSyncedFavorite ?: note.favorite
    ),
    local = NoteVersionSnapshot(
        title = note.title,
        content = note.content,
        category = note.category,
        modifiedAtEpochSeconds = note.modifiedAtEpochSeconds,
        etag = note.remoteEtag,
        readOnly = note.readOnly,
        favorite = note.favorite
    ),
    remote = NoteVersionSnapshot(
        title = remoteTitle,
        content = remoteContent,
        category = remoteCategory,
        modifiedAtEpochSeconds = remoteModifiedAtEpochSeconds,
        etag = remoteEtag,
        readOnly = remoteReadOnly,
        favorite = remoteFavorite
    )
)

fun AccountEntity.toDomain() = Account(
    id = id,
    displayName = displayName,
    serverUrl = serverUrl,
    ssoAccountName = ssoAccountName,
    userId = userId,
    apiVersion = apiVersion,
    collectionEtag = collectionEtag,
    lastModifiedEpochSeconds = lastModifiedEpochSeconds,
    lastSyncError = lastSyncError
)

fun Account.toEntity() = AccountEntity(
    id = id,
    displayName = displayName,
    serverUrl = serverUrl,
    ssoAccountName = ssoAccountName,
    userId = userId,
    apiVersion = apiVersion,
    collectionEtag = collectionEtag,
    lastModifiedEpochSeconds = lastModifiedEpochSeconds,
    lastSyncError = lastSyncError
)
