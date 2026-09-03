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

/**
 * Text another application handed over, such as a page from a browser or a message from a chat.
 *
 * The subject is what the sharing application calls that text. It is not part of the text itself,
 * most applications send one, and some do not.
 */
data class SharedText(val text: String, val subject: String? = null)

class NoteFactory(
    private val namingPolicy: NoteNamingPolicy,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    fun create(accountId: String): Note = create(accountId, namingPolicy.createName(), body = "")

    /**
     * Creates a note holding text another application shared.
     *
     * The sharing application's subject names the note when it sent one, because a shared page or
     * message already carries a name its reader recognizes, and a dated name would hide it. The
     * name is the first heading and the shared text follows it, so the note reads like any other
     * note created here.
     */
    fun createFromSharedText(accountId: String, shared: SharedText): Note {
        val name = shared.subject?.let(NoteNames::sanitize)?.takeIf(String::isNotEmpty)
            ?: namingPolicy.createName()
        return create(accountId, name, shared.text.trim())
    }

    private fun create(accountId: String, title: String, body: String): Note = Note(
        localId = newId(),
        accountId = accountId,
        title = title,
        // Applications that have no subject to send sometimes send the text as one. Repeating it
        // under a heading that already says it would add nothing.
        content = if (body.isEmpty() || body == title) "# $title\n" else "# $title\n\n$body\n",
        modifiedAtEpochSeconds = clock.instant().epochSecond,
        syncState = SyncState.LOCALLY_CREATED
    )
}
