package org.qownnotes.mobile.data

import org.qownnotes.mobile.core.SyncDiagnostic
import org.qownnotes.mobile.core.SyncDiagnosticRepository

class RoomSyncDiagnosticRepository(
    private val dao: SyncDiagnosticDao,
    private val limit: Int = 20
) : SyncDiagnosticRepository {
    override suspend fun record(diagnostic: SyncDiagnostic) {
        dao.record(diagnostic.toEntity(), limit)
    }

    override suspend fun list(): List<SyncDiagnostic> =
        dao.list().map(SyncDiagnosticEntity::toDomain)

    override suspend fun clear() = dao.clear()
}
