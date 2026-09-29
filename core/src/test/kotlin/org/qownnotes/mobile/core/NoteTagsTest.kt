package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTagsTest {
    private val note = NoteTagKey("Meeting", "Work")
    private val other = NoteTagKey("Other", "")
    private val base = NoteTagSnapshot(
        tags = listOf(
            NoteTag(1, "Work"),
            NoteTag(2, "Project", parentId = 1),
            NoteTag(3, "Home", priority = -1)
        ),
        links = setOf(NoteTagLink(1, note), NoteTagLink(3, other))
    )

    @Test
    fun `key uses the name the server confirmed`() {
        assertEquals(
            NoteTagKey("Old", "A/B"),
            NoteTags.keyOf("New", "C", lastSyncedTitle = "Old", lastSyncedCategory = "/A/B/")
        )
        assertEquals(NoteTagKey("Draft", ""), NoteTags.keyOf("Draft", "", null, null))
    }

    @Test
    fun `link resolves existing tags case-insensitively and creates missing path segments`() {
        val existing = NoteTags.applyAll(
            base,
            listOf(NoteTagOperation.Link(other, listOf("work", "PROJECT")))
        )
        assertEquals(base.tags, existing.tags)
        assertTrue(NoteTagLink(2, other) in existing.links)

        val created = NoteTags.applyAll(
            base,
            listOf(NoteTagOperation.Link(other, listOf("Work", "New", "Leaf")))
        )
        val newTag = created.tags.single { it.name == "New" }
        val leaf = created.tags.single { it.name == "Leaf" }
        assertEquals(1L, newTag.parentId)
        assertEquals(newTag.id, leaf.parentId)
        assertTrue(newTag.id < 0 && leaf.id < 0 && newTag.id != leaf.id)
        assertTrue(NoteTagLink(leaf.id, other) in created.links)
    }

    @Test
    fun `sqlite nocase equality folds only ascii letters`() {
        assertTrue(NoteTags.namesEqual("Work", "wORK"))
        assertFalse(NoteTags.namesEqual("Übung", "übung"))
        assertFalse(NoteTags.namesEqual("Work", "Works"))
    }

    @Test
    fun `link is idempotent and unlink ignores unknown tags`() {
        val op = NoteTagOperation.Link(note, listOf("Work"))
        assertEquals(base, NoteTags.applyAll(base, listOf(op, op)))
        assertEquals(
            base,
            NoteTags.applyAll(base, listOf(NoteTagOperation.Unlink(note, listOf("Missing"))))
        )
        val unlinked = NoteTags.applyAll(
            base,
            listOf(NoteTagOperation.Unlink(note, listOf("work")))
        )
        assertFalse(NoteTagLink(1, note) in unlinked.links)
        assertEquals(base.tags, unlinked.tags)
    }

    @Test
    fun `relink moves every link and merges duplicates`() {
        val renamed = NoteTagKey("Renamed", "Work")
        val snapshot = base.copy(links = base.links + NoteTagLink(1, renamed))
        val result = NoteTags.applyAll(snapshot, listOf(NoteTagOperation.Relink(note, renamed)))
        assertEquals(setOf(NoteTagLink(1, renamed), NoteTagLink(3, other)), result.links)
    }

    @Test
    fun `operations replay in order`() {
        val renamed = NoteTagKey("Renamed", "Work")
        val result = NoteTags.applyAll(
            base,
            listOf(
                NoteTagOperation.Link(note, listOf("Home")),
                NoteTagOperation.Relink(note, renamed),
                NoteTagOperation.Unlink(renamed, listOf("Work"))
            )
        )
        assertEquals(setOf(NoteTagLink(3, renamed), NoteTagLink(3, other)), result.links)
    }

    @Test
    fun `paths filters and ordering`() {
        val state = NoteTagState(
            availability = NoteTagAvailability.AVAILABLE,
            writable = true,
            tags = base.tags,
            tagIdsByNote = mapOf(note to setOf(1L, 2L, 3L))
        )
        assertEquals(listOf("Work", "Project"), state.path(2))
        assertEquals(listOf(3L, 2L, 1L), state.tagsOf(note).map(NoteTag::id))
        assertEquals(
            listOf("Home", "Work", "Work/Project"),
            NoteTags.withPaths(base.tags).map { it.second.joinToString("/") }
        )
        assertTrue(NoteTags.matches(setOf(1, 2), setOf(1, 2)))
        assertFalse(NoteTags.matches(setOf(1), setOf(1, 2)))
        assertTrue(NoteTags.matches(emptySet(), emptySet()))
        assertEquals(
            NoteTags.pathKey(listOf("Work", "Project")),
            NoteTags.pathKey(listOf("work", "PROJECT"))
        )
        assertNull(NoteTags.resolve(base.tags, listOf("Project")))
    }

    @Test
    fun `path encoding round trips and names are validated`() {
        val path = listOf("A b", "C")
        assertEquals(path, NoteTags.decodePath(NoteTags.encodePath(path)))
        assertEquals("Tag", NoteTags.normalizeName("  Tag "))
        assertNull(NoteTags.normalizeName("   "))
        assertNull(NoteTags.normalizeName("a\nb"))
        assertNull(NoteTags.normalizeName("x".repeat(256)))
    }

    @Test
    fun `cyclic parents do not loop`() {
        val byId = listOf(NoteTag(1, "A", parentId = 2), NoteTag(2, "B", parentId = 1))
            .associateBy(NoteTag::id)
        assertEquals(listOf("A", "B"), NoteTags.path(byId, 2))
    }
}
