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

/**
 * A note name doubles as the name of the file holding it, so characters that no common file
 * system accepts are replaced rather than rejected. The server sanitizes the name again and
 * answers with the name it actually stored, which the push store then adopts.
 */
object NoteNames {
    private const val MAXIMUM_LENGTH = 120
    private val forbidden = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    fun sanitize(name: String): String = name.replace(forbidden, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.')
        .trim()
        .take(MAXIMUM_LENGTH)
        .trim()

    fun isValid(name: String): Boolean = sanitize(name).isNotEmpty()
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
