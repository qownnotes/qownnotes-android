package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFoldersTest {
    private val categories = listOf(
        "",
        "",
        "Work",
        "work/Archive",
        "Work/Archive",
        "Work/Projects/2026",
        "Private",
        "media",
        "attachments/x",
        "Projects/media"
    )

    @Test
    fun `tree nests folders case-insensitively and counts notes`() {
        val tree = NoteFolders.tree(categories)

        assertEquals(2, tree.rootNoteCount)
        assertEquals(8, tree.totalNoteCount)
        assertEquals(listOf("Private", "Projects", "Work"), tree.folders.map { it.name })
        val work = tree.find("WORK")!!
        assertEquals("Work", work.path)
        assertEquals(1, work.noteCount)
        assertEquals(4, work.subtreeNoteCount)
        assertEquals(listOf("Archive", "Projects"), work.children.map { it.name })
        val archive = work.children.first()
        assertEquals("Work/Archive", archive.path)
        assertEquals(1, archive.depth)
        assertEquals(2, archive.noteCount)
        // Intermediate folders exist without notes of their own.
        val projects = tree.find("Work/Projects")!!
        assertEquals(0, projects.noteCount)
        assertEquals(1, projects.subtreeNoteCount)
        assertEquals(
            listOf(
                "Private",
                "Projects",
                "Projects/media",
                "Work",
                "Work/Archive",
                "Work/Projects",
                "Work/Projects/2026"
            ),
            tree.flatten().map { it.path }
        )
    }

    @Test
    fun `tree excludes QOwnNotes internal trees but keeps nested segments with their names`() {
        val tree = NoteFolders.tree(categories)

        assertNull(tree.find("media"))
        assertNull(tree.find("attachments"))
        assertEquals(1, tree.find("Projects/media")!!.noteCount)
    }

    @Test
    fun `visible folders descend only into expanded folders`() {
        val tree = NoteFolders.tree(categories)

        assertEquals(
            listOf("Private", "Projects", "Work", "Work/Archive", "Work/Projects"),
            tree.visible { it.path == "Work" }.map { it.path }
        )
    }

    @Test
    fun `counts follow the scope and its subfolder choice`() {
        val tree = NoteFolders.tree(categories)

        assertEquals(2, tree.count(NoteFolderScope("", includeSubfolders = false)))
        assertEquals(8, tree.count(NoteFolderScope("", includeSubfolders = true)))
        assertEquals(1, tree.count(NoteFolderScope("Work", includeSubfolders = false)))
        assertEquals(4, tree.count(NoteFolderScope("work", includeSubfolders = true)))
        assertEquals(0, tree.count(NoteFolderScope("Missing", includeSubfolders = true)))
        assertTrue(tree.contains(""))
        assertTrue(tree.contains("work/projects"))
        assertFalse(tree.contains("Missing"))
    }

    @Test
    fun `flat backends keep slashes inside one folder name`() {
        val tree = NoteFolders.tree(listOf("Work", "Work/Archive"), nested = false)

        assertEquals(listOf("Work", "Work/Archive"), tree.folders.map { it.name })
        assertTrue(tree.folders.all { it.children.isEmpty() })
        assertFalse(
            NoteFolders.matches("Work/Archive", NoteFolderScope("Work", true), nested = false)
        )
    }

    @Test
    fun `scopes match the root, one folder, or a subtree`() {
        val root = NoteFolderScope("", includeSubfolders = false)
        val everything = NoteFolderScope("", includeSubfolders = true)
        val work = NoteFolderScope("Work", includeSubfolders = false)
        val workTree = NoteFolderScope("Work", includeSubfolders = true)

        assertTrue(NoteFolders.matches("", root))
        assertFalse(NoteFolders.matches("Work", root))
        assertTrue(NoteFolders.matches("Work/Archive", everything))
        assertTrue(everything.isWholeAccount)
        assertTrue(NoteFolders.matches("work", work))
        assertFalse(NoteFolders.matches("Work/Archive", work))
        assertTrue(NoteFolders.matches("WORK/Archive/2026", workTree))
        assertFalse(NoteFolders.matches("Workshop", workTree))
        assertFalse(NoteFolders.matches("", workTree))
    }

    @Test
    fun `case folding matches SQLite NOCASE and ignores non-ASCII letters`() {
        assertEquals("work/Ärger", NoteFolders.key("WORK/Ärger"))
        assertFalse(NoteFolders.matches("ärger", NoteFolderScope("Ärger")))
    }

    @Test
    fun `subfolder pattern escapes LIKE wildcards and the escape character`() {
        assertEquals("100\\% \\_done\\\\x/%", NoteFolders.subfolderPattern("100% _done\\x"))
        assertEquals("Work/Archive/%", NoteFolders.subfolderPattern("Work/Archive"))
    }

    @Test
    fun `earlier flat selections keep showing the same notes`() {
        assertEquals(
            NoteFolderScope("", includeSubfolders = false),
            NoteFolderScope.fromCategoryScope(NoteCategoryScope.Undefined, null)
        )
        assertEquals(
            NoteFolderScope("", includeSubfolders = true),
            NoteFolderScope.fromCategoryScope(NoteCategoryScope.All, null)
        )
        assertEquals(
            NoteFolderScope("Work", includeSubfolders = false),
            NoteFolderScope.fromCategoryScope(NoteCategoryScope.Category("Work"), null)
        )
        assertEquals(
            NoteFolderScope("Work", includeSubfolders = true),
            NoteFolderScope.fromCategoryScope(NoteCategoryScope.Category("Work"), true)
        )
        assertEquals(
            NoteFolderScope("", includeSubfolders = false),
            NoteFolderScope.fromCategoryScope(NoteCategoryScope.All, false)
        )
    }
}
