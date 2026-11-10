package org.qownnotes.mobile.backend.localfolder

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteTagFileDownload
import org.qownnotes.mobile.core.PullCheckpoint
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.SyncState

class LocalFolderBackendTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var folder: File
    private lateinit var backend: LocalFolderBackend
    private val account = Account(
        id = "local-folder-test",
        displayName = "Notes",
        serverUrl = "",
        ssoAccountName = "",
        userId = "",
        localFolderUri = "file:///notes"
    )
    private val clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        folder = temporary.newFolder("notes")
        backend =
            LocalFolderBackend(temporary.newFolder("state"), {
                DirectoryNoteFolderFiles(folder)
            }, clock)
    }

    @Test
    fun pullListsNotesLikeTheDesktopNoteFolder() = runBlocking {
        write("Root.md", "# Root")
        write("Projects/Plan.txt", "plan")
        write("Projects/Deep/Idea.md", "idea")
        write(".hidden/Secret.md", "hidden")
        write(".Dotfile.md", "dot")
        write("media/image.md", "not a note")
        write("Attachments/file.md", "not a note")
        write("Projects/media/Kept.md", "nested media folders hold notes")
        write("notes.sqlite", "tags")
        write("Picture.png", "binary")

        val notes = backend.pull(account, PullCheckpoint()).notes.sortedBy { it.title }

        assertEquals(
            listOf(
                "Idea" to "Projects/Deep",
                "Kept" to "Projects/media",
                "Plan" to "Projects",
                "Root" to ""
            ),
            notes.map { it.title to it.category }
        )
        val root = notes.single { it.title == "Root" }
        assertEquals("# Root", root.content)
        assertEquals(LocalFolderBackend.idOf("Root.md"), root.id)
        assertEquals(LocalFolderBackend.etagOf("# Root".toByteArray()), root.etag)
        assertFalse(root.readOnly)
    }

    @Test
    fun pullReadsOnlyChangedFilesAfterAnAppliedPull() = runBlocking {
        write("A.md", "a")
        write("B.md", "b")
        val first = backend.pull(account, PullCheckpoint())

        val unchanged = backend.pull(account, first.checkpoint())
        assertTrue(unchanged.notModified)

        write("B.md", "changed b")
        File(folder, "B.md").setLastModified(1_000)
        write("C.md", "c")
        val second = backend.pull(account, first.checkpoint())

        assertFalse(second.notModified)
        assertTrue(second.notes.single { it.id == LocalFolderBackend.idOf("A.md") }.isPruned)
        assertEquals("changed b", second.notes.single { it.title == "B" }.content)
        assertEquals("c", second.notes.single { it.title == "C" }.content)
    }

    @Test
    fun pullReadsEverythingWhenTheLastPullWasNotApplied() = runBlocking {
        write("A.md", "a")
        backend.pull(account, PullCheckpoint())

        val unapplied = backend.pull(account, PullCheckpoint("older", 0))

        assertEquals("a", unapplied.notes.single().content)
    }

    @Test
    fun deletedFilesDisappearFromTheNextPull() = runBlocking {
        write("A.md", "a")
        write("B.md", "b")
        val first = backend.pull(account, PullCheckpoint())
        File(folder, "B.md").delete()

        val second = backend.pull(account, first.checkpoint())

        assertEquals(listOf(LocalFolderBackend.idOf("A.md")), second.notes.map(RemoteNote::id))
    }

    @Test
    fun createWritesAUniqueFileInTheCategoryFolder() = runBlocking {
        write("Projects/Plan.md", "existing")

        val created = backend.create(
            account,
            note(title = "Plan", content = "new", category = "Projects")
        )

        assertEquals("Plan (2)", created.title)
        assertEquals("Projects", created.category)
        assertEquals("new", read("Projects/Plan (2).md"))
        assertEquals("existing", read("Projects/Plan.md"))
        assertEquals(LocalFolderBackend.idOf("Projects/Plan (2).md"), created.id)
        assertEquals(LocalFolderBackend.etagOf("new".toByteArray()), created.etag)
    }

    @Test
    fun createSanitizesNamesAndFolders() = runBlocking {
        val created = backend.create(
            account,
            note(title = "a/b:c?", content = "x", category = "../Work/")
        )

        assertEquals("a b c", created.title)
        assertEquals("Work", created.category)
        assertEquals("x", read("Work/a b c.md"))
    }

    @Test
    fun updateWritesInPlaceAndRenamesOrMovesTheFile() = runBlocking {
        write("Plan.txt", "old")
        val pulled = backend.pull(account, PullCheckpoint()).notes.single()

        val edited = backend.update(account, pulled.toNote(content = "edited"))
        assertEquals(pulled.id, edited.id)
        assertEquals("edited", read("Plan.txt"))

        val moved = backend.update(account, edited.toNote(title = "Renamed", category = "Archive"))
        assertEquals("Renamed", moved.title)
        assertEquals("Archive", moved.category)
        assertEquals(LocalFolderBackend.idOf("Archive/Renamed.txt"), moved.id)
        assertEquals("edited", read("Archive/Renamed.txt"))
        assertFalse(File(folder, "Plan.txt").exists())
    }

    @Test
    fun updateRefusesToOverwriteAnExternalChange() = runBlocking {
        write("Plan.md", "base")
        val pulled = backend.pull(account, PullCheckpoint()).notes.single()
        write("Plan.md", "changed by a file synchronizer")

        try {
            backend.update(account, pulled.toNote(content = "local"))
            fail("Expected a conflict")
        } catch (_: BackendException.Conflict) {
        }
        assertEquals("changed by a file synchronizer", read("Plan.md"))
        assertEquals("changed by a file synchronizer", backend.get(account, pulled.id).content)
    }

    @Test
    fun updateOfAFileDeletedElsewhereReportsItMissing() = runBlocking {
        write("Plan.md", "base")
        val pulled = backend.pull(account, PullCheckpoint()).notes.single()
        File(folder, "Plan.md").delete()

        try {
            backend.update(account, pulled.toNote(content = "local"))
            fail("Expected the note to be missing")
        } catch (_: BackendException.RemoteMissing) {
        }
        assertFalse(File(folder, "Plan.md").exists())
    }

    @Test
    fun renameToATakenNameKeepsBothNotes() = runBlocking {
        write("A.md", "a")
        write("B.md", "b")
        val a = backend.pull(account, PullCheckpoint()).notes.single { it.title == "A" }

        val renamed = backend.update(account, a.toNote(title = "B"))

        assertEquals("B (2)", renamed.title)
        assertEquals("a", read("B (2).md"))
        assertEquals("b", read("B.md"))
    }

    @Test
    fun deleteMovesTheNoteToThePrivateTrashForRestoring() = runBlocking {
        write("Projects/Plan.md", "keep me")
        val pulled = backend.pull(account, PullCheckpoint()).notes.single()

        backend.delete(account, pulled.id)
        assertFalse(File(folder, "Projects/Plan.md").exists())
        backend.delete(account, pulled.id)

        val trashed = backend.trashedNotes(account, emptySet()).single()
        assertEquals("Plan", trashed.name)
        assertEquals("Plan.md", trashed.fileName)
        assertEquals("keep me", trashed.content)
        assertEquals(clock.instant().epochSecond, trashed.timestamp)

        backend.restoreTrashedNote(account, trashed)
        assertEquals("keep me", read("Projects/Plan.md"))
        assertTrue(backend.trashedNotes(account, emptySet()).isEmpty())
    }

    @Test
    fun favoritesArePrivateAndFollowRenames() = runBlocking {
        write("Plan.md", "plan")
        val pulled = backend.pull(account, PullCheckpoint()).notes.single()

        val favorite = backend.update(account, pulled.toNote(favorite = true))
        assertTrue(favorite.favorite)
        val renamed = backend.update(account, favorite.toNote(title = "Renamed", favorite = true))

        val notes = backend.pull(account, PullCheckpoint()).notes
        assertTrue(notes.single { it.id == renamed.id }.favorite)
        assertEquals("plan", read("Renamed.md"))
        assertFalse(File(folder, "Plan.md").exists())

        backend.update(account, renamed.toNote(favorite = false))
        assertFalse(backend.pull(account, PullCheckpoint()).notes.single().favorite)
    }

    @Test
    fun invalidTextIsReadOnly() = runBlocking {
        File(folder, "Binary.md").writeBytes(byteArrayOf(0xC3.toByte(), 0x28))

        assertTrue(backend.pull(account, PullCheckpoint()).notes.single().readOnly)
    }

    @Test
    fun mediaUploadsGetUniqueNamesAndNotesCanReadThem() = runBlocking {
        write("Projects/Plan.md", "![image](../media/image.png)")
        val note = backend.pull(account, PullCheckpoint()).notes.single()

        val first = backend.uploadMedia(account, "", "image.png", "image/png", byteArrayOf(1))
        val second = backend.uploadMedia(account, "", "image.png", "image/png", byteArrayOf(2))
        val audio = backend.uploadMedia(account, "", "voice.m4a", "audio/mp4", byteArrayOf(3))

        assertEquals("image.png", first)
        assertEquals("image-1.png", second)
        assertEquals("voice.m4a", audio)
        assertArrayEquals(byteArrayOf(3), File(folder, "attachments/voice.m4a").readBytes())
        assertArrayEquals(
            byteArrayOf(1),
            backend.readAttachment(account, note.id, "../media/image.png")
        )
        assertArrayEquals(
            byteArrayOf(2),
            backend.readAttachment(account, note.id, "../media/image-1.png")
        )
        assertNull(backend.readAttachment(account, note.id, "../../media/image.png"))
        assertNull(backend.readAttachment(account, note.id, "Plan.md"))
    }

    @Test
    fun attachmentPathsStayInsideTheReservedFolders() {
        assertEquals(
            "media/a b.png",
            LocalFolderBackend.resolveAttachment("Note.md", "media/a%20b.png")
        )
        assertEquals(
            "attachments/x.pdf",
            LocalFolderBackend.resolveAttachment("A/B/Note.md", "../../attachments/x.pdf")
        )
        assertNull(LocalFolderBackend.resolveAttachment("A/Note.md", "../../media/x.png"))
        assertNull(LocalFolderBackend.resolveAttachment("Note.md", "notes.sqlite"))
        assertNull(LocalFolderBackend.resolveAttachment("Note.md", "media/../Note.md"))
        assertNull(LocalFolderBackend.resolveAttachment("Note.md", "/media/x.png"))
    }

    @Test
    fun tagFileIsTransferredWithContentEtags() = runBlocking {
        assertEquals(NoteTagFileDownload.Missing, backend.downloadTagFile(account, null))
        File(folder, "notes.sqlite").writeBytes(byteArrayOf(1, 2))

        val download = backend.downloadTagFile(account, null) as NoteTagFileDownload.Downloaded
        val etag = requireNotNull(download.etag)
        assertEquals(NoteTagFileDownload.NotModified, backend.downloadTagFile(account, etag))

        val uploaded = backend.uploadTagFile(account, byteArrayOf(3), etag)
        assertNotEquals(etag, uploaded)
        assertArrayEquals(byteArrayOf(3), File(folder, "notes.sqlite").readBytes())

        try {
            backend.uploadTagFile(account, byteArrayOf(4), etag)
            fail("Expected a conflict")
        } catch (_: BackendException.Conflict) {
        }
    }

    @Test
    fun aMissingFolderNeedsReconnecting() = runBlocking {
        folder.deleteRecursively()

        try {
            backend.pull(account, PullCheckpoint())
            fail("Expected lost folder access")
        } catch (error: BackendException.AuthorizationRequired) {
            assertEquals(LocalFolderBackend.FOLDER_ACCESS_MESSAGE, error.message)
        }
    }

    @Test
    fun forgetRemovesPrivateState() = runBlocking {
        write("Plan.md", "plan")
        val pulled = backend.pull(account, PullCheckpoint()).notes.single()
        backend.delete(account, pulled.id)

        backend.forget(account.id)

        assertTrue(backend.trashedNotes(account, emptySet()).isEmpty())
    }

    private fun write(path: String, content: String) {
        File(folder, path).apply { parentFile?.mkdirs() }.writeText(content)
    }

    private fun read(path: String) = File(folder, path).readText()

    private fun PullResult.checkpoint() = PullCheckpoint(collectionEtag, lastModifiedEpochSeconds)

    private fun note(title: String, content: String, category: String = "") = Note(
        localId = "local",
        accountId = account.id,
        title = title,
        content = content,
        category = category,
        modifiedAtEpochSeconds = 0,
        syncState = SyncState.LOCALLY_CREATED
    )

    private fun RemoteNote.toNote(
        title: String = requireNotNull(this.title),
        content: String = requireNotNull(this.content),
        category: String = requireNotNull(this.category),
        favorite: Boolean = this.favorite
    ) = Note(
        localId = "local",
        accountId = account.id,
        remoteId = id,
        title = title,
        content = content,
        category = category,
        modifiedAtEpochSeconds = 0,
        remoteEtag = etag,
        favorite = favorite,
        syncState = SyncState.LOCALLY_MODIFIED
    )
}
