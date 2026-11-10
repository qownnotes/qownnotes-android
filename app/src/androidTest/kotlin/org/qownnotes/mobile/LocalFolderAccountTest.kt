package org.qownnotes.mobile

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.SyncState
import org.qownnotes.mobile.core.isLocalFolderAccountId

/**
 * A folder on the device works as an account of its own: its Markdown files become notes, and
 * changes made in the app are written back to those files. The folder is an app-private directory
 * here, because the system folder picker cannot be driven from a test.
 */
@RunWith(AndroidJUnit4::class)
class LocalFolderAccountTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val application get() = composeRule.activity.application as TestQOwnNotesApplication
    private val component get() = application.component
    private lateinit var folder: File

    @Before
    fun reset() {
        runBlocking { application.reset() }
        folder = File(application.filesDir, "local-folder-test").apply {
            deleteRecursively()
            mkdirs()
        }
        composeRule.waitForTag("onboarding")
    }

    @Test
    fun onboardingOffersALocalFolderBelowNextcloud() {
        composeRule.onNodeWithTag("add-account").assertIsDisplayed()
        composeRule.onNodeWithTag("add-local-folder").assertIsDisplayed()
            .assertTextContains("Use a local folder")
    }

    @Test
    fun folderNotesAreListedAndServerOnlyActionsAreHidden() {
        write("Shopping.md", "# Shopping\n\n- milk")
        write("Work/Plan.md", "# Plan")
        write("media/ignored.md", "not a note")
        val account = addFolder()

        composeRule.waitForText("Shopping")
        assertTrue(isLocalFolderAccountId(account.id))
        composeRule.onNodeWithText("ignored").assertDoesNotExist()

        composeRule.onNodeWithText("Shopping").performClick()
        composeRule.waitForTag("note-menu")
        composeRule.onNodeWithTag("note-menu").performClick()
        composeRule.waitForTag("note-information")
        composeRule.onNodeWithTag("note-versions").assertDoesNotExist()
        composeRule.onNodeWithTag("share-note").assertDoesNotExist()
        composeRule.onNodeWithTag("browse-note-deck-cards").assertDoesNotExist()
    }

    @Test
    fun appChangesAreWrittenToTheFolderFiles() {
        write("Shopping.md", "# Shopping")
        val account = addFolder()
        composeRule.waitForText("Shopping")
        val shopping = runBlocking { notes(account).single() }

        runBlocking { component.replaceNoteContent(shopping.localId, "# Shopping\n\n- bread") }
        composeRule.waitUntil(10_000) { read("Shopping.md") == "# Shopping\n\n- bread" }

        runBlocking { component.createNamedNote(account.id, "Ideas", "Projects") }
        composeRule.waitUntil(10_000) { File(folder, "Projects/Ideas.md").isFile }

        runBlocking { component.renameNote(shopping.localId, "Groceries") }
        composeRule.waitUntil(10_000) { File(folder, "Groceries.md").isFile }
        assertFalse(File(folder, "Shopping.md").exists())
        assertEquals("# Shopping\n\n- bread", read("Groceries.md"))
    }

    @Test
    fun filesChangedOutsideTheAppAppearOnRefresh() {
        write("Shopping.md", "# Shopping")
        val account = addFolder()
        composeRule.waitForText("Shopping")

        write("Shopping.md", "# Shopping\n\n- changed on the desktop")
        write("New from desktop.md", "# New")
        File(folder, "Shopping.md").setLastModified(System.currentTimeMillis() + 60_000)
        runBlocking { component.refresh(account.id) }

        composeRule.waitForText("New from desktop")
        val shopping = runBlocking { notes(account).single { it.title == "Shopping" } }
        assertEquals("# Shopping\n\n- changed on the desktop", shopping.content)

        File(folder, "New from desktop.md").delete()
        runBlocking { component.refresh(account.id) }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("New from desktop").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun anExternalChangeToAnEditedNoteBecomesAConflict() {
        write("Shopping.md", "# Shopping")
        val account = addFolder()
        composeRule.waitForText("Shopping")
        val shopping = runBlocking { notes(account).single() }
        application.fakeSyncScheduler.pause()

        runBlocking { component.replaceNoteContent(shopping.localId, "# Shopping\n\n- local") }
        write("Shopping.md", "# Shopping\n\n- desktop")
        runBlocking { component.refresh(account.id) }

        val conflicted = runBlocking { component.noteRepository.get(shopping.localId)!! }
        assertEquals(SyncState.CONFLICT, conflicted.syncState)
        assertEquals("# Shopping\n\n- local", conflicted.content)
        assertEquals("# Shopping\n\n- desktop", read("Shopping.md"))
    }

    @Test
    fun deletedNotesCanBeRestoredFromTheTrash() {
        write("Shopping.md", "# Shopping")
        val account = addFolder()
        composeRule.waitForText("Shopping")
        val shopping = runBlocking { notes(account).single() }

        runBlocking { component.moveNotesToTrash(account.id, listOf(shopping.localId)) }
        composeRule.waitUntil(10_000) { !File(folder, "Shopping.md").exists() }

        val trashed = runBlocking { component.trashedNotes(account.id, emptySet()) }.single()
        assertEquals("Shopping", trashed.name)
        runBlocking { component.restoreTrashedNote(account.id, trashed) }

        assertEquals("# Shopping", read("Shopping.md"))
        composeRule.waitForText("Shopping")
    }

    @Test
    fun removingTheFolderKeepsItsFiles() {
        write("Shopping.md", "# Shopping")
        val account = addFolder()
        composeRule.waitForText("Shopping")

        composeRule.onNodeWithTag("account-menu").performClick()
        composeRule.onNodeWithTag("manage-accounts").performClick()
        composeRule.waitForTag("managed-account-${account.id}")
        composeRule.onNodeWithText(folder.path).assertIsDisplayed()
        composeRule.onNodeWithTag("account-settings-${account.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("add-managed-local-folder").assertIsDisplayed()

        composeRule.onNodeWithTag("remove-account-${account.id}").performClick()
        composeRule.onNodeWithText(
            "The note files in the folder will not be deleted",
            substring = true
        )
            .assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-remove-account").performClick()

        composeRule.waitForTag("onboarding")
        assertEquals("# Shopping", read("Shopping.md"))
    }

    @Test
    fun theSameFolderCannotBeAddedTwice() {
        addFolder()
        val error = runCatching {
            runBlocking { component.addLocalFolder(Uri.fromFile(folder)) }
        }.exceptionOrNull()
        assertEquals("This folder has already been added", error?.message)
    }

    private fun addFolder(): Account =
        runBlocking { component.addLocalFolder(Uri.fromFile(folder)) }.also {
            composeRule.waitForTag("note-list-menu")
        }

    private suspend fun notes(account: Account) = component.noteRepository
        .observeNotes(account.id).first()
        .mapNotNull { component.noteRepository.get(it.localId) }

    private fun write(path: String, content: String) {
        File(folder, path).apply { parentFile?.mkdirs() }.writeText(content)
    }

    private fun read(path: String): String? = File(folder, path).takeIf(File::isFile)?.readText()

    private fun AndroidComposeTestRule<*, *>.waitForTag(tag: String) = waitUntil(10_000) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun AndroidComposeTestRule<*, *>.waitForText(text: String) = waitUntil(10_000) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
