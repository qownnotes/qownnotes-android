package org.qownnotes.mobile.core

enum class SyncDiagnosticSource {
    ACCOUNT,
    NOTE
}

data class SyncDiagnostic(
    val id: Long = 0,
    val accountId: String,
    val occurredAtEpochSeconds: Long,
    val source: SyncDiagnosticSource,
    val category: String,
    val details: String
)

interface SyncDiagnosticRepository {
    suspend fun record(diagnostic: SyncDiagnostic)

    suspend fun list(): List<SyncDiagnostic>

    suspend fun clear()
}
