package org.qownnotes.mobile.core

import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.UUID

interface NoteNamingPolicy {
    fun createName(): String
}

class QOwnNotesNamingPolicy(private val noteLabel: String, private val clock: Clock) :
    NoteNamingPolicy {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH'h'mm's'ss")

    override fun createName(): String =
        "$noteLabel ${formatter.format(clock.instant().atZone(clock.zone))}"
}

class NoteFactory(
    private val namingPolicy: NoteNamingPolicy,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    fun create(accountId: String): Note {
        val title = namingPolicy.createName()
        return Note(
            localId = newId(),
            accountId = accountId,
            title = title,
            content = "# $title\n",
            modifiedAtEpochSeconds = clock.instant().epochSecond,
            syncState = SyncState.LOCALLY_CREATED
        )
    }
}
