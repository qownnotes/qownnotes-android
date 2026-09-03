package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownLinksTest {
    @Test
    fun parsesWikiAliasesHeadingsAndQualifiedPaths() {
        assertEquals(
            InternalNoteLink(
                InternalNoteLinkKind.WIKI,
                noteName = "Note",
                category = "folder/subfolder",
                heading = "Heading",
                label = "Label"
            ),
            parseWikiLink(" folder/subfolder/Note#Heading | Label ")
        )
    }

    @Test
    fun rejectsUnsafeWikiPathsAndMalformedTargets() {
        assertNull(parseWikiLink(""))
        assertNull(parseWikiLink("/Note"))
        assertNull(parseWikiLink("../Note"))
        assertNull(parseWikiLink("folder//Note"))
        assertNull(parseWikiLink("Note\nOther"))
    }

    @Test
    fun parsesLegacyLinksAndEncodedHeadings() {
        assertEquals(
            InternalNoteLink(
                InternalNoteLinkKind.LEGACY,
                noteName = "My_Note",
                heading = "A heading"
            ),
            parseLegacyNoteLink("note://My%5FNote@#A%20heading")
        )
    }

    @Test
    fun parsesRelativeMarkdownNoteLinksAndEncodedHeadings() {
        assertEquals(
            InternalNoteLink(
                InternalNoteLinkKind.MARKDOWN,
                noteName = "QOwnNotes Todo Backlog",
                heading = "Important information"
            ),
            parseMarkdownNoteLink("QOwnNotes%20Todo%20Backlog.md#Important%20information")
        )
        assertEquals(
            InternalNoteLink(
                InternalNoteLinkKind.MARKDOWN,
                noteName = "QOwnNotes",
                category = "archive"
            ),
            parseMarkdownNoteLink("./archive/QOwnNotes.md")
        )
    }

    @Test
    fun rejectsExternalAndUnsafeMarkdownNoteLinks() {
        assertNull(parseMarkdownNoteLink("https://example.com/QOwnNotes.md"))
        assertNull(parseMarkdownNoteLink("/QOwnNotes.md"))
        assertNull(parseMarkdownNoteLink("../QOwnNotes.md"))
        assertNull(parseMarkdownNoteLink("QOwnNotes.txt"))
        assertNull(parseMarkdownNoteLink("QOwnNotes.md?download=true"))
    }

    @Test
    fun allowsOnlyHttpAndHttpsExternalUrlsWithHosts() {
        assertTrue(isSafeExternalUrl("https://example.com/path"))
        assertTrue(isSafeExternalUrl("HTTP://example.com"))
        assertFalse(isSafeExternalUrl("javascript:alert(1)"))
        assertFalse(isSafeExternalUrl("file:///etc/passwd"))
        assertFalse(isSafeExternalUrl("content://provider/item"))
        assertFalse(isSafeExternalUrl("example.com/path"))
    }

    @Test
    fun resolvesWikiLinksWithinAccountAndPrefersCurrentCategory() {
        val source = note("source", "Source", "projects", 1)
        val otherAccount = note("other", "Target", "projects", 100, accountId = "other")
        val root = note("root", "Target", "", 50)
        val nearby = note("nearby", "target", "projects", 10)

        val result = resolveInternalNoteLink(
            source,
            listOf(otherAccount, root, nearby),
            parseWikiLink("Target#Section")!!
        )

        assertEquals(ResolvedNoteLink("nearby", "Section"), result)
    }

    @Test
    fun resolvesQualifiedWikiLinksRelativeToCurrentCategory() {
        val source = note("source", "Source", "projects", 1)
        val target = note("target", "Design", "projects/android", 1)

        assertEquals(
            ResolvedNoteLink("target", null),
            resolveInternalNoteLink(source, listOf(target), parseWikiLink("android/Design")!!)
        )
    }

    @Test
    fun resolvesMarkdownLinksWithinTheCurrentCategory() {
        val source = note("source", "Source", "projects", 1)
        val root = note("root", "QOwnNotes", "", 20)
        val nearby = note("nearby", "QOwnNotes", "projects", 10)

        assertEquals(
            ResolvedNoteLink("nearby", "Information"),
            resolveInternalNoteLink(
                source,
                listOf(root, nearby),
                parseMarkdownNoteLink("QOwnNotes.md#Information")!!
            )
        )
    }

    @Test
    fun legacyWildcardsResolveDeterministically() {
        val source = note("source", "Source", "work", 1)
        val older = note("older", "My Note", "work", 10)
        val newer = note("newer", "My-Note", "work", 20)

        assertEquals(
            ResolvedNoteLink("newer", null),
            resolveInternalNoteLink(
                source,
                listOf(older, newer),
                parseLegacyNoteLink("note://My_Note")!!
            )
        )
    }

    private fun note(
        localId: String,
        title: String,
        category: String,
        modified: Long,
        accountId: String = "account"
    ) = Note(
        localId = localId,
        accountId = accountId,
        title = title,
        content = "",
        category = category,
        modifiedAtEpochSeconds = modified,
        syncState = SyncState.SYNCHRONIZED
    )
}
