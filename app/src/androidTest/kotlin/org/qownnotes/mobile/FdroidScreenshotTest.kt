package org.qownnotes.mobile

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.widget.TextView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.captureToBitmap
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.DeckBoard
import org.qownnotes.mobile.core.DeckCardDetails
import org.qownnotes.mobile.core.DeckStack
import org.qownnotes.mobile.core.NoteShare
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.RemoteNoteVersion
import org.qownnotes.mobile.core.TrashedNote

@RunWith(AndroidJUnit4::class)
class FdroidScreenshotTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val application
        get() = composeRule.activity.application as TestQOwnNotesApplication

    @Before
    fun resetApplication() {
        runBlocking { application.reset() }
        application.component.settings.setShowNotePreview(true)
        composeRule.waitForTag("onboarding")
    }

    @Test
    fun captureStoreScreenshots() {
        // The first screen offers a Nextcloud account or a local note folder.
        composeRule.waitForTag("add-local-folder")
        composeRule.waitForIdle()
        capture("4.png")

        val account =
            SingleSignOnAccount(
                "demo",
                "demo",
                "test-token",
                "https://cloud.example",
                "nextcloud"
            )
        application.fakeBackend.tagFile = desktopTagFile()
        application.fakeBackend.deckBoards = listOf(
            DeckBoard(2, "QOwnNotes project", listOf(DeckStack(11, "Ideas"), DeckStack(12, "Done")))
        )
        application.fakeBackend.existingDeckCards[42] = DeckCardDetails(
            42, 2, 11, "Plan the next release", "Review ideas from our Markdown notes.",
            null, "demo", 7, "plain", false, editable = true
        )
        application.fakeBackend.existingDeckCards[43] = DeckCardDetails(
            43, 2, 11, "Improve desktop integration", "Keep notes and tags working together.",
            null, "demo", 7, "plain", false, editable = true
        )
        application.fakeBackend.noteVersions = listOf(
            RemoteNoteVersion(
                3,
                "November 11, 2026, 14:30",
                "# Welcome to QOwnNotes\n\nKeep notes offline.\nReview the release offline."
            ),
            RemoteNoteVersion(
                2,
                "November 10, 2026, 09:15",
                "# Welcome to QOwnNotes\n\nYour Markdown notes stay available offline."
            ),
            RemoteNoteVersion(
                1,
                "November 9, 2026, 16:00",
                "# Welcome to QOwnNotes\n\nA first draft."
            )
        )
        application.fakeBackend.trash = listOf(
            TrashedNote(
                "Release checklist",
                "Release checklist.md",
                3,
                "November 11, 2026",
                "# Release checklist\n\n- [x] Review changes\n- [ ] Publish the release",
                "/Notes/Release checklist.md"
            ),
            TrashedNote(
                "Old project ideas",
                "Old project ideas.md",
                2,
                "November 10, 2026",
                "# Old project ideas\n\nIdeas are never lost: restore this note from the trash.",
                "/Notes/Old project ideas.md"
            )
        )
        application.fakeBackend.noteShares[101] = NoteShare(
            "7",
            "https://cloud.example/s/qownnotes-demo",
            1,
            true
        )
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes =
                listOf(
                    remoteNote(
                        id = 101,
                        title = "Welcome to QOwnNotes",
                        content = WELCOME_NOTE,
                        modified = 1_700_000_400,
                        favorite = true
                    ),
                    remoteNote(
                        id = 102,
                        title = "Project ideas",
                        content = "# Project ideas\n\n- Offline-first notes\n- Markdown everywhere",
                        modified = 1_700_000_300
                    ),
                    remoteNote(
                        id = 103,
                        title = "Shopping list",
                        content = "# Shopping list\n\n- [x] Coffee\n- [ ] Fresh fruit",
                        modified = 1_700_000_200
                    ),
                    remoteNote(
                        id = 104,
                        title = "Meeting notes",
                        content = "# Meeting notes\n\nReview the next release checklist.",
                        modified = 1_700_000_100
                    )
                ),
                collectionEtag = "screenshot-collection",
                lastModifiedEpochSeconds = 1_700_000_400
            )
        )

        composeRule.onNodeWithTag("add-account").performClick()
        composeRule.waitForTag("note-list")
        composeRule.waitForText("Shopping list")
        composeRule.waitForText("#Work")
        composeRule.waitForText("#Planning")
        capture("1.png")

        composeRule.onNodeWithText("Welcome to QOwnNotes").performClick()
        composeRule.waitForTag("markdown-view")
        waitForViewText(R.id.markdown_view, "Your notes stay available offline")
        capture("2.png")

        composeRule.waitForTag("edit-note")
        composeRule.onNodeWithTag("edit-note").performClick()
        composeRule.waitForTag("markdown-editor")
        waitForViewText(R.id.markdown_editor, "# Welcome to QOwnNotes")
        onView(withId(R.id.markdown_editor)).perform(closeSoftKeyboard())
        composeRule.waitForIdle()
        capture("3.png")

        composeRule.waitForTag("create-deck-card-link")
        composeRule.onNodeWithTag("create-deck-card-link").performScrollTo().performClick()
        composeRule.waitForTag("deck-card-target")
        composeRule.onNodeWithTag("deck-card-title").performTextReplacement("Plan the next release")
        composeRule.onNodeWithTag(
            "deck-card-description"
        ).performTextReplacement("Turn an idea from this note into a task.")
        hideKeyboard()
        capture("7.png")
        composeRule.onNodeWithTag("cancel-deck-card").performClick()
        composeRule.onNodeWithTag("finish-editing").performClick()
        composeRule.waitForTag("markdown-view")

        noteAction("browse-note-deck-cards")
        composeRule.waitForTag("deck-browser-card-42")
        capture("5.png")
        composeRule.onNodeWithTag("deck-browser-card-42").performClick()
        composeRule.waitForTag("deck-edit-title")
        capture("6.png")
        composeRule.onNodeWithTag("deck-edit-close").performClick()
        composeRule.onNodeWithTag("deck-browser-close").performClick()

        noteAction("note-versions")
        composeRule.waitForTag("note-version-3")
        composeRule.onNodeWithTag("note-version-search").performTextReplacement("offline")
        composeRule.waitForTag("note-version-search-summary")
        hideKeyboard()
        capture("9.png")
        composeRule.onNodeWithText("Close").performClick()

        noteAction("edit-note-tags")
        composeRule.waitForTag("note-tag-option-Work")
        capture("10.png")
        composeRule.onNodeWithTag("close-note-tags").performClick()

        noteAction("share-note")
        composeRule.waitForTag("note-share-url")
        capture("11.png")
        composeRule.onNodeWithTag("note-share-done").performClick()

        composeRule.onNodeWithTag("back-to-note-list").performClick()
        composeRule.waitForTag("note-list-menu")
        composeRule.onNodeWithTag("note-list-menu").performClick()
        composeRule.waitForTag("remote-trash")
        composeRule.onNodeWithTag("remote-trash").performClick()
        composeRule.waitForTag("trashed-note-3")
        capture("8.png")
    }

    private fun noteAction(tag: String) {
        composeRule.waitForTag("note-menu")
        composeRule.onNodeWithTag("note-menu").performClick()
        composeRule.waitForTag(tag)
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun hideKeyboard() {
        onView(isRoot()).perform(closeSoftKeyboard())
        composeRule.waitForIdle()
    }

    private fun desktopTagFile(): ByteArray {
        val file = File(composeRule.activity.cacheDir, "screenshot-notes.sqlite")
        file.delete()
        val params = SQLiteDatabase.OpenParams.Builder()
            .setOpenFlags(
                SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            .setJournalMode("DELETE")
            .build()
        try {
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
                    "INSERT INTO noteTagLink (tag_id, note_file_name) VALUES (1, 'Welcome to QOwnNotes')",
                    "INSERT INTO noteTagLink (tag_id, note_file_name) VALUES (3, 'Project ideas')"
                ).forEach(database::execSQL)
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        onView(isRoot()).perform(
            captureToBitmap { bitmap ->
                PlatformTestStorageRegistry.getInstance()
                    .openOutputFile("fdroid/$name")
                    .use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
            }
        )
    }

    private fun waitForViewText(viewId: Int, text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.runOnIdle {
                composeRule.activity.findViewById<TextView>(viewId)?.text?.contains(text) == true
            }
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(
        tag: String
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForText(
        text: String
    ) {
        waitUntil(timeoutMillis = 10_000) {
            runCatching { onNodeWithText(text).fetchSemanticsNode() }.isSuccess
        }
    }

    private fun remoteNote(
        id: Long,
        title: String,
        content: String,
        modified: Long,
        favorite: Boolean = false
    ) = RemoteNote(
        id = id,
        etag = "screenshot-etag-$id",
        title = title,
        content = content,
        category = "",
        modifiedAtEpochSeconds = modified,
        favorite = favorite
    )

    private companion object {
        val WELCOME_NOTE =
            """
            # Welcome to QOwnNotes

            Your notes stay available offline and synchronize safely with your Nextcloud server.

            ## Markdown made practical

            - [x] Write with familiar Markdown
            - [x] Organize notes with categories and favorites
            - [ ] Turn the next idea into a note

            > Your notes, your server, your choice.

            Learn more at [qownnotes.org](https://www.qownnotes.org/).
            """.trimIndent()
    }
}
