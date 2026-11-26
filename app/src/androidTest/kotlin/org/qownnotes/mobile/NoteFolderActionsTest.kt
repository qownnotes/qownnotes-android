package org.qownnotes.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.android.sso.model.SingleSignOnAccount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.SyncState

/** Creating, renaming, and deleting subfolders, and choosing the subfolder of a new note. */
@RunWith(AndroidJUnit4::class)
class NoteFolderActionsTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val application
        get() = composeRule.activity.application as TestQOwnNotesApplication

    private val account =
        SingleSignOnAccount("alice", "alice", "test-token", "https://cloud.example", "nextcloud")
    private val accountId get() = account.localAccountId()

    @Before
    fun resetApplication() {
        runBlocking { application.reset() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("onboarding").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun anEmptySubfolderIsCreatedOpenedAndFilledByTheNextNote() {
        importAccount()
        openDrawer()
        composeRule.onNodeWithTag("create-folder").performClick()
        composeRule.waitForTag("new-folder-dialog")
        composeRule.onNodeWithTag("confirm-new-folder").assertIsNotEnabled()
        // An existing folder is refused, whatever its letter case.
        composeRule.onNodeWithTag("new-folder-name").performTextInput("work")
        composeRule.onNodeWithText("A subfolder with this name already exists.").assertExists()
        composeRule.onNodeWithTag("confirm-new-folder").assertIsNotEnabled()
        composeRule.onNodeWithTag("new-folder-name").performTextClearance()
        composeRule.onNodeWithTag("new-folder-name").performTextInput("Ideas")
        composeRule.onNodeWithTag("confirm-new-folder").performClick()

        // The new folder is opened, ready for its first note.
        composeRule.waitForText("No notes in this subfolder")
        composeRule.waitUntil(10_000) {
            application.component.settings.noteFolderScope(accountId).path == "Ideas"
        }
        assertEquals(
            listOf("Ideas"),
            application.component.noteFolderChanges(accountId).value.emptyFolders
        )
        waitOnUiThread { application.fakeBackend.createdFolders == listOf("Ideas") }
        openDrawer()
        composeRule.onNodeWithTag("folder-Ideas").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("folder-Ideas-count", useUnmergedTree = true)
            .assertTextContains("0")
        composeRule.onNodeWithTag("folder-Ideas").performClick()
        composeRule.waitForTagToGo("folder-Ideas")

        composeRule.onNodeWithTag("create-note").performClick()
        composeRule.waitUntil(10_000) { notes().any { it.category == "Ideas" } }
        // Once a note is in it, the folder is part of the tree like any other.
        composeRule.waitUntil(10_000) {
            application.component.noteFolderChanges(accountId).value.isEmpty
        }
    }

    @Test
    fun renamingASubfolderMovesItsNotesAndRemovesTheOldFolder() {
        importAccount()
        openDrawer()
        composeRule.onNodeWithTag("folder-Work").performClick()
        composeRule.waitForTagToGo("folder-Work")
        openDrawer()
        folderAction("folder-Work", "rename")
        composeRule.waitForTag("rename-folder-dialog")
        composeRule.onNodeWithText("2 notes move with it.").assertExists()
        composeRule.onNodeWithTag("confirm-rename-folder").assertIsNotEnabled()
        composeRule.onNodeWithTag("rename-folder-name").performTextClearance()
        composeRule.onNodeWithTag("rename-folder-name").performTextInput("Private")
        composeRule.onNodeWithText("A subfolder with this name already exists.").assertExists()
        composeRule.onNodeWithTag("rename-folder-name").performTextClearance()
        composeRule.onNodeWithTag("rename-folder-name").performTextInput("a/b")
        composeRule.onNodeWithText("A subfolder name can't contain /.").assertExists()
        composeRule.onNodeWithTag("rename-folder-name").performTextClearance()
        composeRule.onNodeWithTag("rename-folder-name").performTextInput("Job")
        composeRule.onNodeWithTag("confirm-rename-folder").performClick()

        composeRule.waitUntil(10_000) {
            notes().map { it.title to it.category }.toSet() == setOf(
                "Root note" to "",
                "Work note" to "Job",
                "Archive note" to "Job/Archive",
                "Private note" to "Private"
            )
        }
        // The open folder follows its new name.
        composeRule.waitUntil(10_000) {
            application.component.settings.noteFolderScope(accountId).path == "Job"
        }
        composeRule.waitForText("Work note")
        waitOnUiThread {
            application.fakeBackend.pushedNotes.map { it.category }.containsAll(
                listOf("Job", "Job/Archive")
            ) &&
                application.fakeBackend.removedFolders == listOf("Work/Archive", "Work")
        }
        openDrawer()
        composeRule.onNodeWithTag("folder-Job").assertIsDisplayed()
        composeRule.onNodeWithTag("folder-Work").assertDoesNotExist()
    }

    @Test
    fun aSubfolderWithAnUnresolvedConflictKeepsItsName() {
        importAccount()
        val conflicted = notes().single { it.title == "Archive note" }
        runBlocking {
            application.component.noteRepository.save(
                conflicted.copy(syncState = SyncState.CONFLICT)
            )
        }
        openDrawer()
        folderAction("folder-Work", "rename")
        composeRule.waitForTag("rename-folder-name")
        composeRule.onNodeWithTag("rename-folder-name").performTextClearance()
        composeRule.onNodeWithTag("rename-folder-name").performTextInput("Job")
        composeRule.onNodeWithTag("confirm-rename-folder").performClick()

        composeRule.waitForTag("folder-rename-failure")
        composeRule.onNodeWithText("1 note in this subfolder", substring = true).assertExists()
        composeRule.onNodeWithTag("close-folder-rename-failure").performClick()
        assertEquals(
            setOf("Work", "Work/Archive"),
            notes().filter { it.title != "Root note" && it.title != "Private note" }
                .map { it.category }.toSet()
        )
    }

    @Test
    fun deletingASubfolderMovesItsNotesToTheTrash() {
        importAccount()
        openDrawer()
        composeRule.onNodeWithTag("folder-show-subfolders").performClick()
        folderAction("folder-Work", "delete")
        composeRule.waitForTag("delete-folder-dialog")
        composeRule.onNodeWithText(
            "2 notes in this subfolder and its subfolders will move to the trash."
        ).assertExists()
        composeRule.onNodeWithTag("delete-folder-unsynchronized").assertDoesNotExist()
        composeRule.onNodeWithTag("confirm-delete-folder").performClick()

        composeRule.waitUntil(10_000) {
            notes().map { it.title }.toSet() == setOf("Root note", "Private note")
        }
        composeRule.onNodeWithTag("folder-Work").assertDoesNotExist()
        waitOnUiThread {
            application.fakeBackend.deletedRemoteIds.toSet() == setOf(42L, 43L) &&
                application.fakeBackend.removedFolders == listOf("Work/Archive", "Work")
        }
    }

    @Test
    fun theNewNoteDialogChoosesTheSubfolderOfTheNote() {
        application.component.settings.setAskForNewNoteName(true)
        importAccount()

        composeRule.onNodeWithTag("create-note").performClick()
        composeRule.waitForTag("new-note-folder")
        composeRule.onNodeWithTag("new-note-folder").assertTextContains("Root folder")
        composeRule.onNodeWithTag("new-note-folder").performClick()
        composeRule.waitForTag("note-category-dialog")
        composeRule.onNodeWithTag("note-category-Work").performClick()
        composeRule.onNodeWithTag("confirm-note-category").performClick()
        composeRule.waitForTagToGo("note-category-dialog")
        composeRule.onNodeWithTag("new-note-folder").assertTextContains("Work")
        composeRule.onNodeWithTag("new-note-name-field").performTextClearance()
        composeRule.onNodeWithTag("new-note-name-field").performTextInput("Plan")
        composeRule.onNodeWithTag("confirm-new-note").performClick()

        composeRule.waitUntil(10_000) {
            notes().any { it.title == "Plan" && it.category == "Work" }
        }
    }

    @Test
    fun withoutSubfoldersTheNewNoteDialogOnlyAsksForTheName() {
        application.component.settings.setAskForNewNoteName(true)
        importAccount(useSubfolders = false)

        composeRule.onNodeWithTag("create-note").performClick()
        composeRule.waitForTag("new-note-name-field")
        composeRule.onNodeWithTag("new-note-folder").assertDoesNotExist()
    }

    private fun importAccount(useSubfolders: Boolean = true) {
        application.component.settings.setUseSubfolders(accountId, useSubfolders)
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(41, "etag-root", "Root note", "# Root", "", 10),
                    RemoteNote(42, "etag-work", "Work note", "# Work", "Work", 11),
                    RemoteNote(43, "etag-archive", "Archive note", "# Old", "Work/Archive", 12),
                    RemoteNote(44, "etag-private", "Private note", "# Private", "Private", 13)
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 13
            )
        )
        composeRule.onNodeWithTag("add-account").performClick()
        composeRule.waitForText("Root note")
    }

    private fun openDrawer() {
        composeRule.onNodeWithTag("folder-navigation").performClick()
        composeRule.waitForTag("folder-root")
    }

    /** Opens the long-press menu of a folder row and chooses one of its actions. */
    private fun folderAction(folderTag: String, action: String) {
        composeRule.waitForTag(folderTag)
        composeRule.onNodeWithTag(folderTag).performTouchInput { longClick() }
        composeRule.waitForTag("$folderTag-$action")
        composeRule.onNodeWithTag("$folderTag-$action").performClick()
    }

    private fun notes(): List<Note> = runBlocking {
        val repository = application.component.noteRepository
        repository.observeNotes(accountId).first().map { repository.get(it.localId)!! }
    }

    /** Synchronization records requests on the main thread. */
    private fun waitOnUiThread(condition: () -> Boolean) {
        composeRule.waitUntil(15_000) { composeRule.runOnUiThread(condition) }
        assertTrue(composeRule.runOnUiThread(condition))
    }

    private fun AndroidComposeTestRule<*, *>.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun AndroidComposeTestRule<*, *>.waitForTagToGo(tag: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun AndroidComposeTestRule<*, *>.waitForText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
