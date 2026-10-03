package org.qownnotes.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.NoteTagKey
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.notefolder.NoteFolderTagDatabase

@RunWith(AndroidJUnit4::class)
class NoteTagsUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val application
        get() = composeRule.activity.application as TestQOwnNotesApplication

    @Before
    fun resetApplication() {
        runBlocking { application.reset() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("onboarding").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun showsFiltersAndAssignsDesktopTags() {
        application.fakeBackend.tagFile = desktopTagFile()
        importAccount()

        // Card rows merge their content into one clickable node, so the tag line's own test tag
        // only exists in the unmerged tree.
        composeRule.waitForTag("note-tags-$meetingLocalId", useUnmergedTree = true)
        composeRule.waitForText("#Work")
        composeRule.onNodeWithText("Groceries").assertExists()

        // Filtering keeps only notes that carry every selected tag.
        listAction("tag-filter-selector")
        composeRule.waitForTag("tag-filter-dialog")
        composeRule.onNodeWithTag("tag-filter-option-Work").performClick()
        composeRule.onNodeWithTag("close-tag-filter").performClick()
        composeRule.waitForTextToGo("Groceries")
        composeRule.onNodeWithText("Meeting").assertExists()
        listAction("tag-filter-selector")
        composeRule.waitForTag("clear-tag-filter")
        composeRule.onNodeWithTag("clear-tag-filter").performClick()
        composeRule.onNodeWithTag("close-tag-filter").performClick()
        composeRule.waitForText("Groceries")

        // Assign an existing and a new tag to a note.
        composeRule.onNodeWithText("Groceries").performClick()
        composeRule.waitForTag("note-menu")
        composeRule.onNodeWithTag("note-menu").performClick()
        composeRule.waitForTag("edit-note-tags")
        composeRule.onNodeWithTag("edit-note-tags").performClick()
        composeRule.waitForTag("note-tag-option-Work")
        composeRule.onNodeWithTag("search-note-tags").performTextInput(" wOrK ")
        composeRule.onNodeWithTag("note-tag-option-Personal").assertDoesNotExist()
        // A matching parent path also finds its child tags.
        composeRule.onNodeWithTag("note-tag-option-Planning").assertExists()
        composeRule.onNodeWithTag("note-tag-option-Work").assertIsOff()
        composeRule.onNodeWithTag("note-tag-option-Work").performClick()
        composeRule.waitUntil(10_000) {
            runCatching { composeRule.onNodeWithTag("note-tag-option-Work").assertIsOn() }.isSuccess
        }
        composeRule.onNodeWithTag("search-note-tags").performTextClearance()
        composeRule.onNodeWithTag("search-note-tags").performTextInput("no-such-tag")
        composeRule.onNodeWithText("No matching tags.").assertExists()
        composeRule.onNodeWithTag("note-tag-option-Work").assertDoesNotExist()
        composeRule.onNodeWithTag("search-note-tags").performTextClearance()
        composeRule.onNodeWithTag("note-tag-option-Personal").assertExists()
        composeRule.onNodeWithTag("note-tag-option-Work").assertIsOn()
        composeRule.onNodeWithTag("new-note-tag").performTextInput("Shopping")
        composeRule.onNodeWithTag("add-note-tag").performClick()
        composeRule.waitForTag("note-tag-option-Shopping")
        composeRule.onNodeWithTag("close-note-tags").performClick()
        composeRule.waitForTag("note-tags")
        composeRule.waitForText("#Shopping  #Work")

        // Each change is synchronized on its own, so an upload with only the first link may
        // arrive before the one that carries both.
        val expected = listOf("Work", "Shopping")
        composeRule.waitUntil(15_000) {
            latestUploadedTags(NoteTagKey("Groceries", ""))?.containsAll(expected) == true
        }
        assertTrue(latestUploadedTags(NoteTagKey("Groceries", ""))!!.containsAll(expected))
    }

    /** Tag names linked to [key] in the most recently uploaded `notes.sqlite`, if any. */
    private fun latestUploadedTags(key: NoteTagKey): List<String>? {
        // Synchronization records uploads on the main thread.
        val content = composeRule.runOnUiThread {
            application.fakeBackend.tagFileUploads.lastOrNull()
        } ?: return null
        val uploaded = File(composeRule.activity.cacheDir, "uploaded-notes.sqlite")
        return try {
            uploaded.writeBytes(content)
            val snapshot = NoteFolderTagDatabase.read(uploaded).snapshot
            snapshot.links.filter { it.key == key }.map { link ->
                snapshot.tags.single { it.id == link.tagId }.name
            }
        } finally {
            uploaded.delete()
        }
    }

    @Test
    fun explainsMissingNotesDatabase() {
        importAccount()
        composeRule.onNodeWithText("Groceries").performClick()
        composeRule.waitForTag("note-menu")
        composeRule.waitUntil(10_000) {
            composeRule.onNodeWithTag("note-menu").performClick()
            composeRule.onAllNodesWithTag("edit-note-tags").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("edit-note-tags").performClick()
        composeRule.waitForTag("note-tags-unavailable")
        composeRule.onNodeWithText("notes.sqlite", substring = true).assertExists()
        composeRule.onNodeWithTag("close-note-tags").performClick()
        composeRule.onNodeWithTag("note-tags").assertDoesNotExist()
    }

    private fun importAccount() {
        val account = SingleSignOnAccount(
            "alice",
            "alice",
            "test-token",
            "https://cloud.example",
            "nextcloud"
        )
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(1, "e1", "Meeting", "# Meeting", "", 20),
                    RemoteNote(2, "e2", "Groceries", "# Groceries", "", 10)
                ),
                collectionEtag = "collection",
                lastModifiedEpochSeconds = 20
            )
        )
        composeRule.onNodeWithTag("add-account").performClick()
        composeRule.waitForText("Groceries")
        composeRule.waitForText("Meeting")
        meetingLocalId = runBlocking {
            application.component.noteRepository.observeNotes(account.localAccountId()).first()
                .single { it.title == "Meeting" }.localId
        }
    }

    private var meetingLocalId = ""

    private fun AndroidComposeTestRule<*, *>.waitForTag(
        tag: String,
        useUnmergedTree: Boolean = false
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(tag, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun AndroidComposeTestRule<*, *>.waitForText(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun AndroidComposeTestRule<*, *>.waitForTextToGo(text: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun listAction(tag: String) {
        composeRule.onNodeWithTag("note-list-menu").performClick()
        composeRule.waitForTag(tag)
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun desktopTagFile(): ByteArray {
        val file = File(composeRule.activity.cacheDir, "desktop-notes.sqlite")
        file.delete()
        val params = SQLiteDatabase.OpenParams.Builder()
            .setOpenFlags(
                SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            .setJournalMode("DELETE")
            .build()
        SQLiteDatabase.openDatabase(file, params).use { database ->
            listOf(
                "CREATE TABLE appData (name VARCHAR(255) PRIMARY KEY, value VARCHAR(255))",
                """CREATE TABLE tag (id INTEGER PRIMARY KEY, name VARCHAR(255) COLLATE NOCASE,
                   priority INTEGER DEFAULT 0, created DATETIME DEFAULT current_timestamp,
                   parent_id INTEGER DEFAULT 0, color VARCHAR(20), dark_color VARCHAR(20),
                   updated DATETIME DEFAULT current_timestamp)""",
                "CREATE UNIQUE INDEX idxUniqueTag ON tag (name, parent_id)",
                """CREATE TABLE noteTagLink (id INTEGER PRIMARY KEY, tag_id INTEGER,
                   note_file_name VARCHAR(255) DEFAULT '', note_sub_folder_path TEXT DEFAULT '',
                   created DATETIME DEFAULT current_timestamp, stale_date DATETIME DEFAULT NULL)""",
                """CREATE UNIQUE INDEX idxUniqueTagNoteLink
                   ON noteTagLink (tag_id, note_file_name, note_sub_folder_path)""",
                "INSERT INTO appData (name, value) VALUES ('database_version', '16')",
                "INSERT INTO tag (id, name) VALUES (1, 'Work')",
                "INSERT INTO tag (id, name) VALUES (2, 'Personal')",
                "INSERT INTO tag (id, name, parent_id) VALUES (3, 'Planning', 1)",
                "INSERT INTO noteTagLink (tag_id, note_file_name) VALUES (1, 'Meeting')"
            ).forEach(database::execSQL)
        }
        return file.readBytes().also { file.delete() }
    }
}
