package org.qownnotes.mobile.core

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class InternalNoteLinkKind {
    WIKI,
    LEGACY
}

data class InternalNoteLink(
    val kind: InternalNoteLinkKind,
    val noteName: String,
    val category: String? = null,
    val heading: String? = null,
    val label: String? = null
)

data class ResolvedNoteLink(val localId: String, val heading: String?)

fun parseWikiLink(body: String): InternalNoteLink? {
    if (body.any { it == '[' || it == ']' || it == '\n' || it == '\r' }) return null
    val targetAndHeading = body.substringBefore('|').trim()
    if (targetAndHeading.codePointCount(0, targetAndHeading.length) !in 1..100) return null

    val label = body.substringAfter('|', missingDelimiterValue = "").trim().ifEmpty { null }
    val target = targetAndHeading.substringBefore('#').trim()
    val heading = targetAndHeading.substringAfter('#', missingDelimiterValue = "").trim().ifEmpty {
        null
    }
    if (target.startsWith('/')) return null

    val noteName = target.substringAfterLast('/').trim()
    val category = target.substringBeforeLast('/', missingDelimiterValue = "").trim().ifEmpty {
        null
    }
    if (noteName.isEmpty() ||
        category?.split('/')?.any { it.isBlank() || it == "." || it == ".." } == true
    ) {
        return null
    }
    return InternalNoteLink(InternalNoteLinkKind.WIKI, noteName, category, heading, label)
}

fun parseLegacyNoteLink(destination: String): InternalNoteLink? {
    if (!destination.startsWith("note://", ignoreCase = true)) return null
    val encodedTarget = destination.substring(7).substringBefore('#').removeSuffix("@").decodeUrl()
    if (encodedTarget.isBlank() || encodedTarget.contains('/')) return null
    val heading = destination.substringAfter('#', missingDelimiterValue = "").decodeUrl().ifBlank {
        null
    }
    return InternalNoteLink(InternalNoteLinkKind.LEGACY, encodedTarget, heading = heading)
}

fun isSafeExternalUrl(destination: String): Boolean = runCatching {
    val uri = URI(destination)
    uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

fun resolveInternalNoteLink(
    source: Note,
    accountNotes: List<Note>,
    link: InternalNoteLink
): ResolvedNoteLink? {
    val candidates = accountNotes.filter { it.accountId == source.accountId }
    val matches = when (link.kind) {
        InternalNoteLinkKind.WIKI -> resolveWikiCandidates(source, candidates, link)
        InternalNoteLinkKind.LEGACY -> candidates.filter {
            legacyNameMatches(link.noteName, it.title)
        }
    }
    val preferred = matches.filter { it.category.equals(source.category, ignoreCase = true) }
        .ifEmpty { matches }
        .sortedWith(compareByDescending<Note> { it.modifiedAtEpochSeconds }.thenBy { it.localId })
        .firstOrNull()
        ?: return null
    return ResolvedNoteLink(preferred.localId, link.heading)
}

private fun resolveWikiCandidates(
    source: Note,
    notes: List<Note>,
    link: InternalNoteLink
): List<Note> {
    val titleMatches = notes.filter { it.title.equals(link.noteName, ignoreCase = true) }
    val category = link.category ?: return titleMatches
    val relativeCategory = listOf(
        source.category,
        category
    ).filter(String::isNotBlank).joinToString("/")
    return titleMatches.filter { it.category.equals(relativeCategory, ignoreCase = true) }
}

private fun legacyNameMatches(encodedName: String, title: String): Boolean {
    val pattern = encodedName.codePoints().toArray()
    val value = title.codePoints().toArray()
    if (pattern.size != value.size) return false
    return pattern.indices.all { index ->
        val expected = pattern[index]
        expected == '_'.code || expected == '-'.code ||
            String(Character.toChars(expected)).equals(
                String(Character.toChars(value[index])),
                ignoreCase = true
            )
    }
}

private fun String.decodeUrl(): String =
    runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrDefault(this)
