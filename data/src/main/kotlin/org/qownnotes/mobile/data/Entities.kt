package org.qownnotes.mobile.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.Note
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
    indices = [Index("accountId"), Index(value = ["accountId", "remoteId"], unique = true)]
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
