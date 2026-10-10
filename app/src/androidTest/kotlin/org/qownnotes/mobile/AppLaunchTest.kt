package org.qownnotes.mobile

import android.content.ClipboardManager
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click as touchClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.android.sso.model.SingleSignOnAccount
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.DeckBoard
import org.qownnotes.mobile.core.DeckCardDraft
import org.qownnotes.mobile.core.DeckStack
import org.qownnotes.mobile.core.DeckStackTarget
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteFolderScope
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote
import org.qownnotes.mobile.core.RemoteNoteVersion
import org.qownnotes.mobile.core.SyncState
import org.qownnotes.mobile.core.TrashedNote
import org.qownnotes.mobile.markdown.NoteTextSize

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
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
    fun onboardingImportsAccountAndDisplaysInitialPull() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(account, pull("Alice note", "etag-1", 10))

        composeRule.onNodeWithTag("onboarding").assertIsDisplayed()
        accountAction("add-account")

        composeRule.waitForText("Alice note")
        composeRule.onNodeWithTag("account-menu").assertIsDisplayed()
        composeRule.onNodeWithText("alice @ cloud.example").assertDoesNotExist()
        composeRule.onNodeWithText("Alice note").assertIsDisplayed()
    }

    @Test
    fun browsesSearchesAndFiltersDesktopStyleBookmarks() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(
                        42,
                        "etag-bookmarks",
                        "Bookmarks",
                        """# Bookmarks

                        |- [Zulu](https://z.example/docs) #docs #work Reference
                        |- [Alpha](https://a.example) #docs Personal
                        |[Ignored](https://ignored.example)
                        |
                        """.trimMargin(),
                        "",
                        10
                    )
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 10
            )
        )
        accountAction("add-account")
        composeRule.waitForText("Bookmarks")

        listAction("bookmarks-menu")

        composeRule.waitForTag("bookmarks-page")
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Zulu").assertIsDisplayed()
        composeRule.onNodeWithText("Ignored").assertDoesNotExist()
        composeRule.onNodeWithTag("bookmarks-search").performTextInput("z.example reference")
        composeRule.waitForTextToGo("Alpha")
        composeRule.onNodeWithText("Zulu").assertIsDisplayed()
        composeRule.onNodeWithTag("bookmarks-search").performTextReplacement("")
        composeRule.onNodeWithTag("bookmarks-filter-work").performClick()
        composeRule.waitForTextToGo("Alpha")

        composeRule.onNodeWithTag("bookmarks-open-source").performClick()
        composeRule.waitForTag("back-to-note-list")
        composeRule.onNodeWithTag("back-to-note-list").performClick()

        composeRule.waitForTag("bookmarks-page")
        composeRule.onNodeWithTag("bookmarks-filter-work").assertIsSelected()
        composeRule.onNodeWithText("Zulu").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun configuresABookmarksFileInANestedCategory() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(
                        42,
                        "etag-bookmarks",
                        "Bookmarks",
                        "- [Nested bookmark](https://example.com)",
                        "Work",
                        10
                    )
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 10
            )
        )
        accountAction("add-account")
        composeRule.waitForTag("note-list-menu")

        listAction("settings")
        composeRule.onNodeWithTag("bookmarks-path").performScrollTo()
            .performTextReplacement("Work/Bookmarks.md")
        composeRule.onNodeWithTag("close-settings").performClick()
        listAction("bookmarks-menu")

        composeRule.waitForText("Nested bookmark")
        composeRule.onNodeWithText("Nested bookmark").assertIsDisplayed()
    }

    @Test
    fun folderDrawerDefaultsToTheRootAndExcludesInternalFiles() {
        val account = testAccount("alice")
        application.component.settings.setUseSubfolders(account.localAccountId(), true)
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(41, "etag-root", "Root note", "# Root", "", 10),
                    RemoteNote(42, "etag-work", "Work note", "# Work", "Work", 11),
                    RemoteNote(43, "etag-media", "Media note", "# Media", "media", 12),
                    RemoteNote(
                        44,
                        "etag-attachments",
                        "Attachment note",
                        "# Attachment",
                        "attachments/archive",
                        13
                    )
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 13
            )
        )

        accountAction("add-account")

        composeRule.waitForText("Root note")
        composeRule.onNodeWithText("Work note").assertDoesNotExist()
        listAction("category-selector")
        composeRule.waitForTag("folder-root")
        composeRule.onNodeWithTag("folder-root").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag("folder-Work").assertIsDisplayed()
        composeRule.onNodeWithTag("folder-media").assertDoesNotExist()
        composeRule.onNodeWithTag("folder-attachments").assertDoesNotExist()

        composeRule.onNodeWithTag("folder-show-subfolders").performClick()
        composeRule.onNodeWithTag("folder-root").performClick()
        composeRule.waitForTagToGo("folder-root")
        composeRule.waitForText("Work note")
        composeRule.onNodeWithText("Root note").assertIsDisplayed()
        composeRule.onNodeWithText("Media note").assertDoesNotExist()
        composeRule.onNodeWithText("Attachment note").assertDoesNotExist()

        composeRule.onNodeWithTag("folder-navigation").performClick()
        composeRule.waitForTag("folder-Work")
        composeRule.onNodeWithTag("folder-Work").performClick()

        composeRule.waitForTextToGo("Root note")
        composeRule.onNodeWithText("Work note").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("Work note")
        composeRule.onNodeWithText("Root note").assertDoesNotExist()
        assertEquals(
            NoteFolderScope("Work", includeSubfolders = true),
            application.component.settings.noteFolderScope(account.localAccountId())
        )

        val existingIds = runBlocking { notesOf("alice").map(Note::localId).toSet() }
        composeRule.onNodeWithTag("create-note").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                notesOf("alice").any { it.localId !in existingIds && it.category == "Work" }
            }
        }
    }

    @Test
    fun folderTreeNestsFoldersCountsNotesAndScopesSearch() {
        val account = testAccount("alice")
        application.component.settings.setUseSubfolders(account.localAccountId(), true)
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

        accountAction("add-account")
        composeRule.waitForText("Root note")
        composeRule.onNodeWithTag("folder-navigation").performClick()
        composeRule.waitForTag("folder-Work")
        composeRule.onNodeWithTag("folder-root-count", useUnmergedTree = true)
            .assertTextEquals("1")
        composeRule.onNodeWithTag("folder-Work-count", useUnmergedTree = true)
            .assertTextEquals("1")
        composeRule.onNodeWithTag("folder-Work/Archive").assertDoesNotExist()
        composeRule.onNodeWithTag("folder-Work-expand").performClick()
        composeRule.waitForTag("folder-Work/Archive")
        composeRule.onNodeWithTag("folder-Private-expand").assertDoesNotExist()

        composeRule.onNodeWithTag("folder-show-subfolders").performClick()
        composeRule.onNodeWithTag("folder-Work-count", useUnmergedTree = true)
            .assertTextEquals("2")
        composeRule.onNodeWithTag("folder-root-count", useUnmergedTree = true)
            .assertTextEquals("4")
        composeRule.onNodeWithTag("folder-Work").performClick()
        composeRule.waitForTagToGo("folder-Work")
        composeRule.waitForText("Archive note")
        composeRule.onNodeWithText("Work note").assertIsDisplayed()
        composeRule.onNodeWithText("Private note").assertDoesNotExist()
        composeRule.onNodeWithText("Root note").assertDoesNotExist()

        // Search stays in the folder until it is widened to every folder.
        composeRule.onNodeWithTag("note-search").performTextInput("Private")
        composeRule.waitForText("No matching notes")
        composeRule.onNodeWithTag("note-search-filter").performClick()
        composeRule.waitForTag("search-all-folders")
        composeRule.onNodeWithTag("search-all-folders").performClick()
        composeRule.waitForText("Private note")
        composeRule.onNodeWithText("Work note").assertDoesNotExist()
    }

    @Test
    fun turningSubfoldersOffListsAndCreatesOnlyRootNotes() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(41, "etag-root", "Root note", "# Root", "", 10),
                    RemoteNote(42, "etag-work", "Work note", "# Work", "Work", 11)
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 11
            )
        )
        accountAction("add-account")
        composeRule.waitForText("Root note")
        composeRule.onNodeWithTag("folder-navigation").assertDoesNotExist()
        listAction("settings")
        composeRule.onNodeWithTag("toggle-use-subfolders").assertIsOff().performClick()
        composeRule.onNodeWithTag("close-settings").performClick()
        composeRule.onNodeWithTag("folder-navigation").performClick()
        composeRule.waitForTag("folder-Work")
        composeRule.onNodeWithTag("folder-Work").performClick()
        composeRule.waitForTextToGo("Root note")

        listAction("settings")
        composeRule.onNodeWithTag("toggle-use-subfolders").performClick()
        composeRule.onNodeWithTag("toggle-category").assertDoesNotExist()
        composeRule.onNodeWithTag("close-settings").performClick()

        composeRule.waitForText("Root note")
        composeRule.onNodeWithText("Work note").assertDoesNotExist()
        composeRule.onNodeWithTag("folder-navigation").assertDoesNotExist()
        val existingIds = runBlocking { notesOf("alice").map(Note::localId).toSet() }
        composeRule.onNodeWithTag("create-note").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                notesOf("alice").any { it.localId !in existingIds && it.category.isEmpty() }
            }
        }
        // The subfolder chosen before is kept for when subfolders are turned on again.
        assertEquals(
            NoteFolderScope("Work", includeSubfolders = false),
            application.component.settings.noteFolderScope(account.localAccountId())
        )
    }

    @Test
    fun aRememberedFolderWithoutNotesFallsBackToTheRoot() {
        val account = testAccount("alice")
        application.component.settings.setUseSubfolders(account.localAccountId(), true)
        application.component.settings.setNoteFolderScope(
            account.localAccountId(),
            NoteFolderScope("Gone", includeSubfolders = false)
        )
        importAccount("alice", "Root note", "etag-1", 10)

        composeRule.waitForText("Root note")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.component.settings.noteFolderScope(account.localAccountId()) ==
                NoteFolderScope("", includeSubfolders = false)
        }
    }

    @Test
    fun favoriteStarMovesANoteAboveNewerNotesAndQueuesItForUpload() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(42, "etag-old", "Older", "# Older", "", 10),
                    RemoteNote(43, "etag-new", "Newer", "# Newer", "", 20)
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 20
            )
        )
        accountAction("add-account")
        composeRule.waitForText("Older")
        val older = runBlocking { notesOf("alice").first { it.title == "Older" } }

        composeRule.onNodeWithTag("favorite-${older.localId}").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                val notes = notesOf("alice")
                notes.first().title == "Older" && notes.first().favorite
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.pushedNotes.any { it.localId == older.localId && it.favorite }
        }
    }

    @Test
    fun noteListCanBeSortedByTitleInEitherDirection() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(42, "etag-alpha", "Alpha", "# Alpha", "", 30),
                    RemoteNote(43, "etag-charlie", "Charlie", "# Charlie", "", 20),
                    RemoteNote(44, "etag-bravo", "Bravo", "# Bravo", "", 10)
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 30
            )
        )
        accountAction("add-account")
        composeRule.waitForText("Alpha")

        fun notesAppearInOrder(vararg titles: String): Boolean {
            val tops = titles.map {
                composeRule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top
            }
            return tops.zipWithNext().all { (first, second) -> first < second }
        }

        composeRule.waitUntil { notesAppearInOrder("Alpha", "Charlie", "Bravo") }
        listAction("sort-selector")
        composeRule.onNodeWithTag("sort-option-title-ascending").performClick()
        composeRule.waitUntil { notesAppearInOrder("Alpha", "Bravo", "Charlie") }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil { notesAppearInOrder("Alpha", "Bravo", "Charlie") }
        listAction("sort-selector")
        composeRule.onNodeWithTag("sort-option-title-descending").performClick()
        composeRule.waitUntil { notesAppearInOrder("Charlie", "Bravo", "Alpha") }
    }

    @Test
    fun enabledSwipeActionsToggleFavoriteAndMoveNotesToTrash() {
        val account = importAccount("alice", "First note", "etag-1", 10)
        val first = runBlocking { notesOf("alice").single() }
        runBlocking {
            application.component.noteRepository.save(
                Note(
                    localId = "second-local",
                    accountId = account.localAccountId(),
                    remoteId = 43,
                    title = "Second note",
                    content = "# Second note",
                    modifiedAtEpochSeconds = 20,
                    remoteEtag = "etag-2",
                    syncState = SyncState.SYNCHRONIZED
                )
            )
        }
        composeRule.waitForText("Second note")

        listAction("settings")
        composeRule.onNodeWithTag("toggle-swipe-note-actions").assertIsOff().performClick()
        composeRule.onNodeWithTag("toggle-swipe-note-actions").assertIsOn().performClick()
        composeRule.onNodeWithTag("toggle-swipe-note-actions").assertIsOff().performClick()
        composeRule.onNodeWithTag("toggle-swipe-note-actions").assertIsOn()
        composeRule.onNodeWithTag("close-settings").performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("First note")

        composeRule.onNodeWithTag("swipe-note-${first.localId}").performTouchInput { swipeRight() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                notesOf("alice").first().let { it.localId == first.localId && it.favorite }
            }
        }
        composeRule.onNodeWithTag("swipe-note-${first.localId}").performTouchInput { swipeRight() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { !notesOf("alice").first { it.localId == first.localId }.favorite }
        }
        composeRule.onNodeWithTag("swipe-note-second-local").performTouchInput { swipeLeft() }

        composeRule.waitForTextToGo("Second note")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.deletedRemoteIds == listOf(43L)
        }
    }

    @Test
    fun showsAccountImportFailureOnOnboarding() {
        application.fakeAccountImporter.enqueueFailure(IllegalStateException("Import unavailable"))

        accountAction("add-account")

        composeRule.waitForText("Import unavailable")
        composeRule.onNodeWithText("Import unavailable").assertIsDisplayed()
    }

    @Test
    fun accountImportSurvivesActivityRecreationDuringValidation() {
        val account = testAccount("alice")
        val validationGate = CompletableDeferred<Unit>()
        application.fakeBackend.validationGate = validationGate
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(account, pull("Alice note", "etag-1", 10))
        accountAction("add-account")
        composeRule.waitUntil {
            application.fakeBackend.validatedAccountIds.isNotEmpty()
        }

        composeRule.activityRule.scenario.recreate()
        validationGate.complete(Unit)

        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Alice note").assertIsDisplayed()
    }

    @Test
    fun cachedNotesRemainVisibleAfterOfflineActivityRestart() {
        val account = importAccount("alice", "Cached note", "etag-1", 10)
        application.fakeBackend.enqueueFailure(
            account,
            BackendException.Retryable(IOException("offline access_token=top-secret"))
        )

        composeRule.activityRule.scenario.recreate()

        composeRule.waitForText("The server could not be reached")
        composeRule.onNodeWithTag("account-sync-error-details").assertDoesNotExist()
        composeRule.onNodeWithText("top-secret", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("account-sync-error-toggle").performClick()
        composeRule.waitForText("phone's Wi-Fi or VPN", substring = true)
        composeRule.waitForText(
            "java.io.IOException: offline access_token=<redacted>",
            substring = true
        )
        composeRule.onNodeWithText("top-secret", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("account-sync-error-copy").performClick()
        val copied = clipboardText()
        assertTrue(copied.contains("java.io.IOException: offline access_token=<redacted>"))
        assertTrue(!copied.contains("top-secret"))
        composeRule.onNodeWithText("Cached note").assertIsDisplayed()
    }

    @Test
    fun noteSyncErrorCanBeExplainedWhileEditing() {
        importAccount("alice", "Existing note", "etag-1", 10)
        application.fakeBackend.updateFailure =
            BackendException.Retryable(IOException("connection refused"))
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click(), replaceText("# Changed locally"))
        composeRule.onNodeWithTag("finish-editing").performClick()
        composeRule.waitForText("The server could not be reached")
        composeRule.enterEditMode()

        composeRule.onNodeWithTag("note-sync-error-details").assertDoesNotExist()
        composeRule.onNodeWithTag("note-sync-error-toggle").performClick()

        composeRule.waitForText("Local edits remain saved", substring = true)
        composeRule.waitForText("java.io.IOException: connection refused", substring = true)
        composeRule.onNodeWithTag("note-sync-error-copy").performClick()
        assertTrue(clipboardText().contains("java.io.IOException: connection refused"))
    }

    @Test
    fun reconnectPreservesCachedDataAndCheckpoint() {
        val account = importAccount("alice", "Cached note", "etag-1", 10)
        application.fakeBackend.enqueueFailure(account, BackendException.Authentication())
        runBlocking { application.component.refresh(account.localAccountId()) }
        composeRule.waitForText("Reconnect")

        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(account, pull("Updated note", "etag-2", 20))
        composeRule.onNodeWithText("Reconnect").performClick()

        composeRule.waitForText("Updated note")
        composeRule.onNodeWithText("Cached note").assertDoesNotExist()
        val reconnectCheckpoint = application.fakeBackend.checkpoints.last().second
        assertEquals("etag-1", reconnectCheckpoint.collectionEtag)
        assertEquals(10L, reconnectCheckpoint.lastModifiedEpochSeconds)
        assertEquals(2, application.fakeBackend.validatedAccountIds.size)
    }

    @Test
    fun reconnectRejectsADifferentAccount() {
        val account = importAccount("alice", "Cached note", "etag-1", 10)
        application.fakeBackend.enqueueFailure(account, BackendException.Authentication())
        runBlocking { application.component.refresh(account.localAccountId()) }
        composeRule.waitForText("Reconnect")

        application.fakeAccountImporter.enqueue(testAccount("bob"))
        composeRule.onNodeWithText("Reconnect").performClick()

        composeRule.waitForText("Select the same Nextcloud account to reconnect")
        composeRule.onNodeWithText("Cached note").assertIsDisplayed()
    }

    @Test
    fun pullingTheNoteListDownFetchesFromTheServer() {
        val account = importAccount("alice", "Cached note", "etag-1", 10)
        application.fakeBackend.enqueue(account, pull("Updated note", "etag-2", 20))

        composeRule.pullToRefresh()

        composeRule.waitForText("Updated note")
    }

    @Test
    fun addRejectsAConflictingLocalIdentity() {
        importAccount("alice", "Alice note", "etag-1", 10)
        application.fakeAccountImporter.enqueue(
            SingleSignOnAccount(
                "alice",
                "someone-else",
                "test-token",
                "https://other.example",
                "nextcloud"
            )
        )

        accountAction("add-account")

        composeRule.waitForText("A different Nextcloud account already uses this local identity")
        composeRule.onNodeWithText("Alice note").assertIsDisplayed()
    }

    @Test
    fun switchingAndRemovingAccountsKeepsDataAccountScoped() {
        val alice = importAccount("alice", "Alice note", "etag-a", 10)
        val bob = testAccount("bob")
        application.fakeAccountImporter.enqueue(bob)
        application.fakeBackend.enqueue(bob, pull("Bob note", "etag-b", 20))
        accountAction("add-account")

        composeRule.waitForText("Bob note")
        composeRule.onNodeWithText("Alice note").assertDoesNotExist()

        accountAction("account-choice-${alice.localAccountId()}")
        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Bob note").assertDoesNotExist()

        accountAction("account-choice-${bob.localAccountId()}")
        composeRule.waitForText("Bob note")
        composeRule.onNodeWithText("Alice note").assertDoesNotExist()

        accountAction("manage-accounts")
        composeRule.onNodeWithTag("remove-account-${bob.localAccountId()}").performClick()
        composeRule.onNodeWithText("server notes will not be deleted", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-remove-account").performClick()
        composeRule.onNodeWithTag("close-manage-accounts").performClick()
        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Bob note").assertDoesNotExist()

        accountAction("manage-accounts")
        composeRule.onNodeWithTag("remove-account-${alice.localAccountId()}").performClick()
        composeRule.onNodeWithTag("confirm-remove-account").performClick()
        composeRule.waitForText("Your Nextcloud notes, offline")
    }

    @Test
    fun showCategorySettingIsStoredPerAccount() {
        val alice = importAccount("alice", "Alice note", "etag-a", 10)
        listAction("settings")
        composeRule.onNodeWithTag("toggle-use-subfolders").performClick()
        composeRule.onNodeWithTag("toggle-category").performClick()
        composeRule.onNodeWithTag("close-settings").performClick()
        composeRule.waitForText("Root folder")

        importAccount("bob", "Bob note", "etag-b", 20)
        composeRule.onNodeWithText("Root folder").assertDoesNotExist()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("Bob note")
        composeRule.onNodeWithText("Root folder").assertDoesNotExist()
        accountAction("account-choice-${alice.localAccountId()}")
        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Root folder").assertIsDisplayed()
    }

    @Test
    fun compactNoteListReducesRowHeightAndPersists() {
        val account = importAccount(
            "alice",
            "Spacious note",
            "etag-a",
            10,
            content = "# Spacious note\nFirst preview line with enough words to wrap. " +
                "Second preview sentence that keeps going across the row width."
        )
        val localId = runBlocking {
            application.component.noteRepository.observeNotes(account.localAccountId())
                .first().single().localId
        }
        fun rowHeight() =
            composeRule.onNodeWithTag("note-$localId").fetchSemanticsNode().boundsInRoot.height
        val regularHeight = rowHeight()

        listAction("settings")
        composeRule.onNodeWithTag("toggle-compact-note-list").assertIsOff().performClick()
        composeRule.onNodeWithTag("toggle-compact-note-list").assertIsOn()
        composeRule.onNodeWithTag("close-settings").performClick()
        composeRule.waitForIdle()
        val compactHeight = rowHeight()
        assertTrue("regular=$regularHeight, compact=$compactHeight", compactHeight < regularHeight)
        assertTrue(application.component.settings.compactNoteList.value)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("Spacious note")
        assertEquals(compactHeight, rowHeight())
    }

    @Test
    fun appearanceColorsTheListHeaderRowsAndCategoriesAndPersists() {
        val account = importAccount("alice", "Colored note", "etag-a", 10)
        val localId = runBlocking {
            application.component.noteRepository.observeNotes(account.localAccountId())
                .first().single().localId
        }
        val displayName = runBlocking {
            application.component.accountRepository.observeAccounts().first().single().displayName
        }
        composeRule.onNodeWithTag("note-list-header").assertDoesNotExist()

        listAction("settings")
        composeRule.onNodeWithTag("toggle-use-subfolders").performClick()
        composeRule.onNodeWithTag("toggle-category").performClick()
        composeRule.onNodeWithTag("open-appearance").performScrollTo().performClick()
        composeRule.onNodeWithTag("appearance-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle-list-header").performClick()
        composeRule.onNodeWithTag("app-header-blue").performScrollTo().performClick()
        composeRule.onNodeWithTag("app-note-background-black").performScrollTo().performClick()
        composeRule.onNodeWithTag("app-category-highlight-default").assertDoesNotExist()
        composeRule.onNodeWithTag("toggle-highlight-categories").performScrollTo().performClick()
        composeRule.onNodeWithTag("app-category-highlight-amber").performScrollTo().performClick()
        composeRule.onNodeWithTag("close-appearance").performClick()
        composeRule.waitForIdle()

        fun assertAppearance() {
            composeRule.onNodeWithTag("note-list-header-title")
                .assertTextEquals("Root folder")
            composeRule.onNodeWithTag("note-list-header-account").assertTextEquals(displayName)
            assertEquals(0xFF1565C0.toInt(), pixel("note-list-header"))
            // Sample the row's start padding: card corners are clipped and outlined.
            val row = composeRule.onNodeWithTag("note-$localId").captureToImage().asAndroidBitmap()
            assertEquals(0xFF000000.toInt(), row.getPixel(row.width / 50, row.height / 2))
            assertEquals(
                0xFFFFB300.toInt(),
                pixel("note-category-$localId", useUnmergedTree = true)
            )
        }
        assertAppearance()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("Colored note")
        assertAppearance()

        listAction("settings")
        composeRule.onNodeWithTag("open-appearance").performScrollTo().performClick()
        composeRule.onNodeWithTag("reset-appearance").performClick()
        composeRule.onNodeWithTag("close-appearance").performClick()
        composeRule.onNodeWithTag("note-list-header").assertDoesNotExist()
        assertEquals(AppAppearance(), application.component.settings.appearance.value)
    }

    @Test
    fun notesAreInsetCardsByDefaultAndFlatRowsPersist() {
        val account = importAccount("alice", "Card note", "etag-a", 10)
        val localId = runBlocking {
            application.component.noteRepository.observeNotes(account.localAccountId())
                .first().single().localId
        }
        fun itemBounds(): androidx.compose.ui.geometry.Rect {
            // The taller "Refreshing" status of a running synchronization moves the list down.
            composeRule.waitForText("Available offline")
            return composeRule.onNodeWithTag("swipe-note-$localId")
                .fetchSemanticsNode().boundsInRoot
        }
        fun rowBounds() = composeRule.onNodeWithTag("note-$localId")
            .fetchSemanticsNode().boundsInRoot
        val card = itemBounds()
        assertEquals(card, rowBounds())

        listAction("settings")
        composeRule.onNodeWithTag("open-appearance").performScrollTo().performClick()
        composeRule.onNodeWithTag("toggle-note-cards").performClick()
        composeRule.onNodeWithTag("close-appearance").performClick()
        composeRule.waitForIdle()

        fun assertFlat() {
            val flat = itemBounds()
            assertTrue("flat=$flat, card=$card", card.left > flat.left)
            assertTrue("flat=$flat, card=$card", card.right < flat.right)
            assertTrue("flat=$flat, card=$card", card.top > flat.top)
            assertEquals(flat, rowBounds())
        }
        assertFlat()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("Card note")
        assertFlat()
        assertFalse(application.component.settings.appearance.value.noteCards)

        listAction("settings")
        composeRule.onNodeWithTag("open-appearance").performScrollTo().performClick()
        composeRule.onNodeWithTag("reset-appearance").performClick()
        composeRule.onNodeWithTag("close-appearance").performClick()
        composeRule.waitForIdle()
        assertEquals(card, itemBounds())

        // The whole card is the tap target, including its start padding beside the text.
        composeRule.onNodeWithTag("note-$localId").performTouchInput {
            touchClick(androidx.compose.ui.geometry.Offset(4f, centerY))
        }
        composeRule.onNodeWithTag("markdown-view").assertIsDisplayed()
    }

    /** Color near the top-left corner of a node, clear of text glyphs. */
    private fun pixel(tag: String, useUnmergedTree: Boolean = false): Int =
        composeRule.onNodeWithTag(tag, useUnmergedTree).captureToImage().asAndroidBitmap()
            .getPixel(1, 1)

    @Test
    fun theAccountMenuListsAccountsAboveTheAccountActions() {
        val alice = importAccount("alice", "Alice note", "etag-a", 10)
        val bob = importAccount("bob", "Bob note", "etag-b", 20)
        val charlie = importAccount("charlie", "Charlie note", "etag-c", 30)

        composeRule.onNodeWithTag("account-menu").performClick()
        composeRule.waitForTag("add-account")
        composeRule.onNodeWithText("Switch account").assertDoesNotExist()
        val choices = listOf(alice, bob, charlie).map {
            composeRule.onNodeWithTag("account-choice-${it.localAccountId()}")
                .fetchSemanticsNode().boundsInRoot
        }
        composeRule.onNodeWithTag("account-choice-${charlie.localAccountId()}").assertIsSelected()
        composeRule.onNodeWithTag("account-choice-${alice.localAccountId()}").assertIsNotSelected()
        val divider = composeRule.onNodeWithTag("account-menu-divider")
            .fetchSemanticsNode().boundsInRoot
        val add = composeRule.onNodeWithTag("add-account").fetchSemanticsNode().boundsInRoot
        assertTrue(choices.all { it.bottom <= divider.top })
        assertTrue(divider.bottom <= add.top)

        composeRule.onNodeWithTag("account-choice-${alice.localAccountId()}").performClick()
        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Bob note").assertDoesNotExist()
        composeRule.onNodeWithText("Charlie note").assertDoesNotExist()
        composeRule.onNodeWithTag("add-account").assertDoesNotExist()
    }

    /** The account avatar contains account actions and excludes general application actions. */
    @Test
    fun theAccountAvatarOpensAccountActions() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)

        composeRule.onNodeWithTag("account-menu").assertIsDisplayed()
        composeRule.onNodeWithTag("note-search").assertIsDisplayed()
        composeRule.onNodeWithText("Search notes").assertIsDisplayed()
        composeRule.onNodeWithTag("note-list-menu").assertIsDisplayed()
        val accountBounds = composeRule.onNodeWithTag(
            "account-menu"
        ).fetchSemanticsNode().boundsInRoot
        val searchBounds = composeRule.onNodeWithTag(
            "note-search"
        ).fetchSemanticsNode().boundsInRoot
        val menuBounds = composeRule.onNodeWithTag(
            "note-list-menu"
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(accountBounds.right <= searchBounds.left)
        assertTrue(searchBounds.right <= menuBounds.left)
        assertTrue(
            "search=${searchBounds.height}, menu=${menuBounds.height}",
            searchBounds.height <= menuBounds.height
        )

        composeRule.onNodeWithTag("account-menu").performClick()
        composeRule.waitForText("Add account")
        composeRule.onNodeWithText("Add account").assertIsDisplayed()
        composeRule.onNodeWithText("Manage accounts").assertIsDisplayed()
        composeRule.onNodeWithText("Remove account").assertDoesNotExist()
        // A menu item merges its icon into itself, so the icon is only a node of its own in the
        // unmerged tree, and its bounds belong to a popup that is still animating into place.
        composeRule.onNodeWithTag("add-account-icon", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("manage-accounts-icon", useUnmergedTree = true).assertExists()
        val addBounds = composeRule.onNodeWithTag("add-account").fetchSemanticsNode().boundsInRoot
        val manageBounds = composeRule.onNodeWithTag(
            "manage-accounts"
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(addBounds.bottom <= manageBounds.top)
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
        composeRule.onNodeWithText("About").assertDoesNotExist()
    }

    /**
     * Searching takes the whole top bar, because a query is easier to read and correct when the
     * account and note actions step aside for it. Leaving search restores those actions without
     * throwing the query away, so a filtered list can still be acted on.
     */
    @Test
    fun focusedNoteSearchUsesTheAvailableTopBarWidthAndHasABackAction() {
        importAccount("alice", "Existing note", "etag-1", 10)
        val compactWidth = searchFieldWidth()

        composeRule.onNodeWithTag("note-search").performClick()

        composeRule.waitUntilDisplayed("close-note-search")
        composeRule.onNodeWithTag("account-menu").assertDoesNotExist()
        composeRule.onNodeWithTag("note-list-menu").assertDoesNotExist()
        val focusedWidth = searchFieldWidth()
        assertTrue("compact=$compactWidth, focused=$focusedWidth", focusedWidth > compactWidth)

        composeRule.onNodeWithTag("note-search").performTextInput("missing")
        composeRule.waitForTextToGo("Existing note")
        composeRule.onNodeWithTag("close-note-search").performClick()

        composeRule.waitUntilDisplayed("account-menu")
        composeRule.waitUntilDisplayed("note-list-menu")
        composeRule.onNodeWithTag("close-note-search").assertDoesNotExist()
        composeRule.onNodeWithText("Existing note").assertDoesNotExist()

        composeRule.onNodeWithTag("clear-note-search").performClick()

        composeRule.waitForText("Existing note")
    }

    @Test
    fun noteSearchRemainsActiveAfterOpeningANoteAndReturning() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(42, "etag-match", "Matching note", "# Match", "", 10),
                    RemoteNote(43, "etag-other", "Other note", "# Other", "", 11)
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 11
            )
        )
        accountAction("add-account")
        composeRule.waitForText("Matching note")

        composeRule.onNodeWithTag("note-search").performTextInput("Matching")
        composeRule.waitForTextToGo("Other note")
        composeRule.onNodeWithText("Matching note").performClick()
        composeRule.waitUntilDisplayed("back-to-note-list")
        composeRule.onNodeWithTag("back-to-note-list").performClick()

        composeRule.waitForText("Matching note")
        composeRule.onNodeWithText("Other note").assertDoesNotExist()
        composeRule.onNodeWithTag("clear-note-search").assertIsDisplayed()
    }

    @Test
    fun createsANoteFromTheSearchTextInTheSelectedAccount() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithTag("note-search").performTextInput("  New: idea  ")
        composeRule.waitUntilDisplayed("create-note-from-search")
        composeRule.onNodeWithTag("create-note-from-search").performClick()

        composeRule.waitUntilDisplayed("markdown-editor")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                notesOf("alice").any {
                    it.title == "New idea" && it.content == "# New idea\n\n"
                }
            }
        }
        composeRule.onNodeWithTag("finish-editing").performClick()
        composeRule.onNodeWithTag("back-to-note-list").performClick()
        composeRule.onNodeWithTag("clear-note-search").performClick()
        composeRule.waitForText("New idea")
    }

    @Test
    fun noteSearchCanBeLimitedToTitles() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(42, "etag-title", "Needle title", "# Unrelated", "", 10),
                    RemoteNote(43, "etag-content", "Body match", "Contains the needle", "", 11)
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 11
            )
        )
        accountAction("add-account")
        composeRule.waitForText("Needle title")

        composeRule.onNodeWithTag("note-search").performTextInput("needle")
        composeRule.waitForText("Body match")
        composeRule.onNodeWithTag("note-search-filter", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("search-filter-title").performClick()

        composeRule.waitForTextToGo("Body match")
        composeRule.onNodeWithText("Needle title").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("Needle title")
        composeRule.onNodeWithText("Body match").assertDoesNotExist()

        composeRule.onNodeWithTag("note-search-filter", useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag("search-filter-title-content").performClick()
        composeRule.waitForText("Body match")
    }

    @Test
    fun managesRemoteSettingsAndRemovesAConnectedAccount() {
        val alice = importAccount("alice", "Alice note", "etag-a", 10)
        val bob = importAccount("bob", "Bob note", "etag-b", 20)
        val aliceId = alice.localAccountId()
        val bobId = bob.localAccountId()

        accountAction("manage-accounts")

        composeRule.onNodeWithTag("account-management").assertIsDisplayed()
        composeRule.onNodeWithTag("managed-account-$aliceId").assertIsDisplayed()
        composeRule.onNodeWithTag("managed-account-$bobId").assertIsDisplayed()
        composeRule.onNodeWithTag("account-settings-$aliceId").performClick()
        composeRule.waitForTag("notes-path")
        composeRule.onNodeWithTag("notes-path").performTextReplacement("Work/Notes")
        composeRule.onNodeWithTag("file-extension").performTextReplacement("txt")
        composeRule.onNodeWithTag("save-account-settings").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.settingsUpdates.lastOrNull()?.second?.let {
                it.notesPath == "Work/Notes" && it.fileSuffix == ".txt"
            } == true
        }
        // The dialog closes only after the refresh that follows the settings update.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("account-settings-dialog").fetchSemanticsNodes()
                .isEmpty()
        }

        composeRule.onNodeWithTag("remove-account-$aliceId").performClick()
        composeRule.onNodeWithText("server notes will not be deleted", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-remove-account").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { application.component.accountRepository.get(aliceId) == null }
        }
        composeRule.onNodeWithTag("managed-account-$aliceId").assertDoesNotExist()
        composeRule.onNodeWithTag("managed-account-$bobId").assertIsDisplayed()
    }

    @Test
    fun synchronizationStatusStaysAboveTheScrollingNoteList() {
        val account = importAccount("alice", "First note", "etag-1", 10)
        runBlocking {
            repeat(20) { index ->
                application.component.noteRepository.save(
                    Note(
                        localId = "scroll-note-$index",
                        accountId = account.localAccountId(),
                        remoteId = 100L + index,
                        title = "Scroll note $index",
                        content = "# Scroll note $index",
                        modifiedAtEpochSeconds = 100L + index,
                        remoteEtag = "etag-scroll-$index",
                        syncState = SyncState.SYNCHRONIZED
                    )
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice").size == 21 }
        }
        val statusTop = composeRule.onNodeWithTag(
            "sync-status"
        ).fetchSemanticsNode().boundsInRoot.top

        composeRule.onNodeWithTag("note-list").performTouchInput { swipeUp() }

        composeRule.onNodeWithTag("sync-status").assertIsDisplayed()
        val scrolledStatusTop =
            composeRule.onNodeWithTag("sync-status").fetchSemanticsNode().boundsInRoot.top
        assertEquals(statusTop, scrolledStatusTop)
    }

    @Test
    fun createButtonHidesOnScrollByDefaultAndCanBeDisabled() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = (0..20).map { index ->
                    RemoteNote(
                        id = 100L + index,
                        etag = "etag-scroll-$index",
                        title = if (index == 0) "First note" else "Scroll note $index",
                        content = "# Scroll note $index",
                        category = "",
                        modifiedAtEpochSeconds = 10L + index
                    )
                },
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 30
            )
        )
        accountAction("add-account")
        composeRule.waitForText("Scroll note 20")

        composeRule.onNodeWithTag("create-note").assertIsDisplayed()
        composeRule.onNodeWithTag("note-list").performTouchInput { swipeUp() }
        composeRule.waitForTextToGo("Scroll note 20")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("create-note").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("note-list").performTouchInput { swipeDown() }
        composeRule.waitForTag("create-note")

        listAction("settings")
        composeRule.onNodeWithTag("toggle-hide-create-button-on-scroll").assertIsOn()
            .performClick()
        composeRule.onNodeWithTag("toggle-hide-create-button-on-scroll").assertIsOff()
        composeRule.onNodeWithTag("close-settings").performClick()

        composeRule.onNodeWithTag("note-list").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("create-note").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForTag("note-list")
        listAction("settings")
        composeRule.onNodeWithTag("toggle-hide-create-button-on-scroll").assertIsOff()
    }

    @Test
    fun longPressSelectsMultipleNotesAndMovesThemToTrash() {
        val account = importAccount("alice", "First note", "etag-1", 10)
        val accountId = account.localAccountId()
        val first = runBlocking { notesOf("alice").single() }
        runBlocking {
            application.component.noteRepository.save(
                Note(
                    localId = "second-local",
                    accountId = accountId,
                    remoteId = 43,
                    title = "Second note",
                    content = "# Second note",
                    modifiedAtEpochSeconds = 20,
                    remoteEtag = "etag-2",
                    syncState = SyncState.SYNCHRONIZED
                )
            )
        }
        composeRule.waitForText("Second note")

        composeRule.onNodeWithTag("note-${first.localId}").performTouchInput { longClick() }
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("note-second-local").performClick()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("note-selection-menu").performClick()
        composeRule.onNodeWithTag("move-notes-to-trash").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.deletedRemoteIds.toSet() == setOf(42L, 43L)
        }
        composeRule.waitForTextToGo("First note")
        composeRule.waitForTextToGo("Second note")
    }

    @Test
    fun noteViewMovesTheNoteToTrashAfterConfirmation() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.openNoteMenu()

        composeRule.onNodeWithTag("delete-note").performClick()
        composeRule.onNodeWithText("Move note to trash?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-delete-note").performClick()

        composeRule.waitForTag("note-list")
        composeRule.waitForTextToGo("Existing note")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.deletedRemoteIds == listOf(42L)
        }
    }

    @Test
    fun viewsAndRestoresANoteVersion() {
        importAccount("alice", "Existing note", "etag-1", 10, "Current content")
        application.fakeBackend.noteVersions = listOf(
            RemoteNoteVersion(5, "Yesterday", "Historical content")
        )
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.openNoteMenu()

        composeRule.onNodeWithTag("note-versions").performClick()
        composeRule.waitForText("Historical content")
        composeRule.onNodeWithTag("restore-note-version").performClick()
        composeRule.onNodeWithTag("confirm-restore-note-version").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice").single().content == "Historical content" }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.pushedNotes.any { it.content == "Historical content" }
        }
    }

    @Test
    fun viewsAndRestoresRemoteTrash() {
        importAccount("alice", "Existing note", "etag-1", 10)
        val trashed = TrashedNote(
            name = "Deleted note",
            fileName = "Deleted note.md",
            timestamp = 5,
            displayTimestamp = "Yesterday",
            content = "Deleted content",
            remotePath = "/Notes/Deleted note.md"
        )
        application.fakeBackend.trash = listOf(trashed)

        listAction("remote-trash")
        composeRule.waitForText("Deleted content")
        composeRule.onNodeWithTag("trash-search").assertIsDisplayed()
        composeRule.onNodeWithTag("restore-trashed-note").performClick()
        composeRule.onNodeWithTag("confirm-restore-trashed-note").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.restoredTrash == listOf(trashed)
        }
        composeRule.waitForText("No trashed notes were found on the server.")
    }

    @Test
    fun searchesRemoteTrashByNoteName() {
        importAccount("alice", "Existing note", "etag-1", 10)
        application.fakeBackend.trash = listOf(
            TrashedNote(
                name = "Alpha note",
                fileName = "Alpha note.md",
                timestamp = 1,
                displayTimestamp = "Monday",
                content = "Alpha content",
                remotePath = "/Notes/Alpha note.md"
            ),
            TrashedNote(
                name = "Beta note",
                fileName = "Beta note.md",
                timestamp = 2,
                displayTimestamp = "Tuesday",
                content = "Beta content",
                remotePath = "/Notes/Beta note.md"
            )
        )

        listAction("remote-trash")
        composeRule.waitForText("Alpha content")
        composeRule.onNodeWithTag("trash-search").performTextInput("Beta")
        composeRule.waitForTextToGo("Alpha content")
        composeRule.waitForText("Beta content")

        composeRule.onNodeWithTag("clear-trash-search").performClick()
        composeRule.waitForText("Alpha content")
    }

    @Test
    fun remoteTrashUsesAllCachedFoldersWhileSearchIsActive() {
        val account = importAccount("alice", "Visible note", "etag-1", 10)
        application.component.settings.setUseSubfolders(account.localAccountId(), true)
        runBlocking {
            application.component.noteRepository.save(
                Note(
                    localId = "work-note",
                    accountId = account.localAccountId(),
                    remoteId = 43,
                    title = "Filtered note",
                    content = "Not in the search",
                    category = "Work",
                    modifiedAtEpochSeconds = 20,
                    remoteEtag = "etag-2",
                    syncState = SyncState.SYNCHRONIZED
                )
            )
        }
        listAction("category-selector")
        composeRule.waitForTag("folder-show-subfolders")
        composeRule.onNodeWithTag("folder-show-subfolders").performClick()
        composeRule.onNodeWithTag("folder-root").performClick()
        composeRule.waitForText("Filtered note")
        composeRule.onNodeWithTag("note-search").performTextInput("Visible")
        composeRule.waitForTextToGo("Filtered note")

        listAction("remote-trash")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.trashCategoryRequests.lastOrNull() == setOf("", "Work")
        }
    }

    @Test
    fun trashRestoreReportsWhenTheRequiredRefreshFails() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)
        application.fakeBackend.trash = listOf(
            TrashedNote(
                "Deleted note",
                "Deleted note.md",
                5,
                "Yesterday",
                "Deleted content",
                "/Notes/Deleted note.md"
            )
        )
        application.fakeBackend.enqueueFailure(
            account,
            BackendException.Retryable(IOException("offline"))
        )

        listAction("remote-trash")
        composeRule.waitForText("Deleted content")
        composeRule.onNodeWithTag("restore-trashed-note").performClick()
        composeRule.onNodeWithTag("confirm-restore-trashed-note").performClick()

        composeRule.waitForText("The server could not be reached")
        assertEquals(1, application.fakeBackend.restoredTrash.size)
    }

    @Test
    fun createsAndEditsANoteOfflineFirst() {
        importAccount("alice", "Existing note", "etag-1", 10)

        composeRule.onNodeWithTag("create-note").assertIsDisplayed().performClick()
        composeRule.waitForTag("markdown-editor")
        composeRule.onNodeWithTag("finish-editing").assertIsDisplayed()
        onView(withId(R.id.markdown_editor)).check { view, _ ->
            val editor = view as TextView
            assertTrue(editor.text.toString().startsWith("# Note "))
            assertTrue(editor.text.toString().endsWith("\n\n"))
            assertEquals(editor.length(), editor.selectionStart)
            assertEquals(editor.length(), editor.selectionEnd)
        }
        onView(withId(R.id.markdown_editor)).perform(
            click(),
            replaceText("# Edited\n\nDraft text")
        )
            .check(matches(withText(containsString("Draft text"))))
        composeRule.onNodeWithTag("finish-editing").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice").any { it.content.contains("Draft text") } }
        }
    }

    @Test
    fun askingForANewNoteNameSuggestsTheAutomaticNameAndUsesTheChosenOne() {
        importAccount("alice", "Existing note", "etag-1", 10)

        listAction("settings")
        composeRule.onNodeWithTag("toggle-ask-for-new-note-name").performScrollTo().assertIsOff()
            .performClick()
        composeRule.onNodeWithTag("toggle-ask-for-new-note-name").assertIsOn()
        composeRule.onNodeWithTag("close-settings").performClick()

        composeRule.onNodeWithTag("create-note").performClick()
        composeRule.waitForTag("new-note-name-field")
        composeRule.onNodeWithTag(
            "new-note-name-field"
        ).assertTextContains("Note ", substring = true)
        composeRule.onNodeWithTag("cancel-new-note").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("new-note-name-dialog").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(1, runBlocking { notesOf("alice").size })

        composeRule.onNodeWithTag("create-note").performClick()
        composeRule.waitForTag("new-note-name-field")
        // The suggested name is selected, so typing replaces it.
        composeRule.onNodeWithTag("new-note-name-field").performTextInput("Meeting: Monday")
        composeRule.onNodeWithTag("confirm-new-note").performClick()

        composeRule.waitForTag("markdown-editor")
        onView(withId(R.id.markdown_editor)).check { view, _ ->
            assertEquals("# Meeting Monday\n\n", (view as TextView).text.toString())
        }
        assertTrue(runBlocking { notesOf("alice").any { it.title == "Meeting Monday" } })
    }

    @Test
    fun creationAdoptsTheCanonicalServerTitle() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)
        application.fakeBackend.nextCanonicalTitle = "Canonical server title"

        val localId = runBlocking {
            application.component.createNote(account.localAccountId()).localId
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                application.component.noteRepository.get(localId)?.title == "Canonical server title"
            }
        }
        val created = runBlocking { application.component.noteRepository.get(localId)!! }
        assertEquals(SyncState.SYNCHRONIZED, created.syncState)
        assertEquals("Canonical server title", created.lastSyncedTitle)
        assertTrue(created.remoteId != null)
    }

    @Test
    fun updateConflictKeepsTheLocalContentAndMarksTheNote() {
        val account = importAccount(
            "alice",
            "Existing note",
            "etag-1",
            10,
            "# Existing note\n\nBase content"
        )
        val note = runBlocking { notesOf("alice").single() }
        application.fakeBackend.updateFailure = BackendException.Conflict()

        runBlocking {
            application.component.replaceNoteContent(
                note.localId,
                "# Existing note\n\nLocal content"
            )
            application.component.refresh(account.localAccountId())
        }

        val conflicted = runBlocking { application.component.noteRepository.get(note.localId)!! }
        assertEquals("# Existing note\n\nLocal content", conflicted.content)
        assertEquals(SyncState.CONFLICT, conflicted.syncState)
        assertEquals("The note changed on the server", conflicted.lastSyncError)
        assertEquals("# Existing note\n\nBase content", conflicted.lastSyncedContent)

        listAction("settings")
        composeRule.onNodeWithTag("open-diagnostics").performScrollTo().performClick()
        composeRule.waitForText("Category: Conflict", substring = true)
    }

    @Test
    fun remotelyMissingNoteCanBeRecreatedWithoutLosingItsLocalIdentity() {
        val account = importAccount(
            "alice",
            "Existing note",
            "etag-1",
            10,
            "# Existing note\n\nBase content"
        )
        val note = runBlocking { notesOf("alice").single() }
        application.fakeSyncScheduler.pause()
        application.fakeBackend.updateFailure = BackendException.RemoteMissing()

        runBlocking {
            application.component.replaceNoteContent(
                note.localId,
                "# Existing note\n\nLocal content"
            )
            application.component.refresh(account.localAccountId())
        }

        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.waitForTag("resolve-remote-missing")
        composeRule.onNodeWithTag("resolve-remote-missing").performClick()
        composeRule.onNodeWithTag("recreate-remote-missing").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                application.component.noteRepository.get(note.localId)?.syncState ==
                    SyncState.LOCALLY_CREATED
            }
        }
        val recreated = runBlocking { application.component.noteRepository.get(note.localId)!! }
        assertEquals(note.localId, recreated.localId)
        assertEquals("# Existing note\n\nLocal content", recreated.content)
        assertEquals(null, recreated.remoteId)
        assertEquals(null, recreated.remoteEtag)
    }

    @Test
    fun readOnlyTransitionCanPreserveLocalChangesAsAWritableCopy() {
        val account = importAccount(
            "alice",
            "Existing note",
            "etag-1",
            10,
            "# Existing note\n\nBase content"
        )
        val localId = runBlocking {
            val note = notesOf("alice").single()
            application.component.noteRepository.save(
                note.copy(
                    content = "# Existing note\n\nLocal content",
                    syncState = SyncState.LOCALLY_MODIFIED,
                    localRevision = note.localRevision + 1
                )
            )
            note.localId
        }
        application.fakeBackend.enqueue(
            account.localAccountId(),
            PullResult(
                listOf(
                    RemoteNote(
                        42,
                        "etag-2",
                        "Existing note",
                        "# Existing note\n\nServer content",
                        "",
                        20,
                        readOnly = true
                    )
                ),
                "collection-etag-2",
                20
            )
        )
        application.fakeBackend.remoteNotes[42] = RemoteNote(
            42,
            "etag-2",
            "Existing note",
            "# Existing note\n\nServer content",
            "",
            20,
            readOnly = true
        )

        runBlocking { application.component.refresh(account.localAccountId()) }

        val conflicted = runBlocking { application.component.noteRepository.get(localId)!! }
        assertEquals(SyncState.READ_ONLY_CONFLICT, conflicted.syncState)
        assertEquals("# Existing note\n\nLocal content", conflicted.content)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.waitForTag("resolve-note-conflict")
        composeRule.onNodeWithTag("resolve-note-conflict").performClick()
        composeRule.waitForTag("keep-local-conflict-copy")
        composeRule.onNodeWithTag("keep-local-conflict-copy").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                val notes = notesOf("alice")
                notes.size == 2 &&
                    notes.single { it.localId == localId }.readOnly &&
                    notes.single { it.localId != localId }.content.contains("Local content")
            }
        }
        val notes = runBlocking { notesOf("alice") }
        assertEquals(
            "# Existing note\n\nServer content",
            notes.single {
                it.localId == localId
            }.content
        )
        assertFalse(notes.single { it.localId != localId }.readOnly)
    }

    @Test
    fun conflictMessageRemainsVisibleWhileReadingALongNote() {
        importAccount(
            "alice",
            "Long conflict",
            "etag-1",
            10,
            (1..100).joinToString("\n\n") { "Paragraph $it with local content." }
        )
        runBlocking {
            val note = notesOf("alice").single()
            application.component.noteRepository.save(
                note.copy(
                    syncState = SyncState.CONFLICT,
                    lastSyncError = "The note changed on the server"
                )
            )
        }

        composeRule.onNodeWithText("Long conflict").performClick()
        composeRule.waitForTag("note-sync-error")
        val before = composeRule.onNodeWithTag(
            "note-sync-error"
        ).fetchSemanticsNode().boundsInRoot.top

        composeRule.onNodeWithTag("markdown-view").performTouchInput { swipeUp() }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onNodeWithTag("note-sync-error").fetchSemanticsNode().boundsInRoot.top ==
                before
        }
        composeRule.onNodeWithTag("note-sync-error").assertIsDisplayed()
    }

    @Test
    fun uncertainInitialUploadKeepsItsIdentityUntilExplicitRetry() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)
        application.fakeBackend.createFailure = BackendException.Retryable(IOException("offline"))

        val localId = runBlocking {
            application.component.createNote(account.localAccountId()).localId
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                application.component.noteRepository.get(localId)?.syncState == SyncState.FAILED
            }
        }
        val failed = runBlocking { application.component.noteRepository.get(localId)!! }
        composeRule.onNodeWithText(failed.title).performClick()
        composeRule.waitForTag("markdown-view")
        composeRule.onNodeWithTag("note-menu").performClick()
        composeRule.waitForTag("retry-note")
        assertTrue(failed.content.startsWith("# ${failed.title}\n\n"))
        assertEquals(SyncState.FAILED, failed.syncState)
        assertEquals(null, failed.remoteId)

        composeRule.onNodeWithTag("retry-note").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                application.component.noteRepository.get(failed.localId)?.syncState ==
                    SyncState.SYNCHRONIZED
            }
        }
        val notes = runBlocking { notesOf("alice") }
        val synchronized = notes.single { it.localId == failed.localId }
        assertEquals(failed.localId, synchronized.localId)
        assertTrue(synchronized.remoteId != null)
        assertEquals(failed.content, synchronized.content)
    }

    @Test
    fun editorConflictOpensRecoveryWithTheLatestTypedDraft() {
        importAccount("alice", "Existing note", "etag-1", 10, "# Existing note\n\nBase content")
        application.fakeBackend.remoteNotes[42] = RemoteNote(
            42,
            "etag-2",
            "Existing note",
            "# Existing note\n\nServer content",
            "",
            20
        )
        application.fakeSyncScheduler.pause()
        val localId = runBlocking { notesOf("alice").single().localId }
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click(), typeText(" latest edit"))
        awaitEditorText("latest edit")

        // A background sync can report a conflict while the editor still owns a live draft.
        application.fakeBackend.updateFailure = BackendException.Conflict()
        runBlocking {
            application.component.saveDraft(localId, application.component.draft(localId, ""))
            application.component.refresh(notesOf("alice").single().accountId)
        }
        composeRule.waitForTag("resolve-note-conflict")
        composeRule.onNodeWithTag("markdown-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("note-sync-error-toggle").assertDoesNotExist()

        onView(withId(R.id.markdown_editor)).perform(click(), typeText(" newest text"))
        awaitEditorText("newest text")
        composeRule.onNodeWithTag("resolve-note-conflict").performClick()
        composeRule.waitForTag("conflict-local-version")
        composeRule.waitForText("newest text", substring = true)
        composeRule.onNodeWithTag("markdown-editor").assertDoesNotExist()
        composeRule.onNodeWithTag("keep-local-conflict-copy").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                notesOf("alice").any {
                    it.localId != localId && it.content.contains("newest text")
                }
            }
        }
        assertEquals(
            SyncState.SYNCHRONIZED,
            runBlocking { application.component.noteRepository.get(localId)!!.syncState }
        )
    }

    @Test
    fun resolvesConflictWhilePreservingLocalChangesAsANewNote() {
        importAccount(
            "alice",
            "Existing note",
            "etag-1",
            10,
            "# Existing note\n\nBase content"
        )
        val localId = runBlocking {
            val note = notesOf("alice").single()
            application.component.noteRepository.save(
                note.copy(
                    content = "# Existing note\n\nLocal content",
                    syncState = SyncState.CONFLICT,
                    lastSyncError = "The note changed on the server"
                )
            )
            note.localId
        }
        application.fakeBackend.remoteNotes[42] = RemoteNote(
            42,
            "etag-2",
            "Existing note",
            "# Existing note\n\nServer content",
            "",
            20
        )

        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.waitForTag("resolve-note-conflict")
        composeRule.onNodeWithTag("resolve-note-conflict").performClick()
        composeRule.waitForText("Server content", substring = true)
        composeRule.onNodeWithTag("conflict-local-version").assertIsDisplayed()
        composeRule.onNodeWithTag("conflict-server-version").assertIsDisplayed()
        composeRule.onNodeWithTag("conflict-base-version").assertIsDisplayed()
        composeRule.onNodeWithTag("keep-local-conflict-copy").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                val notes = notesOf("alice")
                notes.size == 2 &&
                    notes.single { it.localId == localId }.content.contains("Server content") &&
                    notes.single { it.localId != localId }.content.contains("Local content") &&
                    application.fakeBackend.pushedNotes.any { it.content.contains("Local content") }
            }
        }
        val notes = runBlocking { notesOf("alice") }
        assertEquals(SyncState.SYNCHRONIZED, notes.single { it.localId == localId }.syncState)
        assertTrue(notes.single { it.localId != localId }.title.endsWith("(local conflict copy)"))
        assertTrue(application.fakeBackend.pushedNotes.any { it.content.contains("Local content") })
    }

    @Test
    fun reviewsAndMergesIndependentConflictChanges() {
        val base = "# Existing note\n\none\ntwo\nthree"
        importAccount("alice", "Existing note", "etag-1", 10, base)
        val localId = runBlocking {
            val note = notesOf("alice").single()
            application.component.noteRepository.save(
                note.copy(
                    content = "# Existing note\n\none\nlocal two\nthree",
                    syncState = SyncState.CONFLICT,
                    lastSyncError = "The note changed on the server"
                )
            )
            note.localId
        }
        application.fakeBackend.remoteNotes[42] = RemoteNote(
            42,
            "etag-2",
            "Existing note",
            "# Existing note\n\none\ntwo\nremote three",
            "",
            20
        )

        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.waitForTag("resolve-note-conflict")
        composeRule.onNodeWithTag("resolve-note-conflict").performClick()
        composeRule.waitForTag("merge-conflict-versions")
        composeRule.waitForText("remote three", substring = true)
        composeRule.onNodeWithTag("merge-conflict-versions").assertIsEnabled().performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.pushedNotes.any {
                it.localId == localId && it.content.contains("local two\nremote three")
            } && runBlocking {
                application.component.noteRepository.get(localId)?.syncState ==
                    SyncState.SYNCHRONIZED
            }
        }
        val resolved = runBlocking { application.component.noteRepository.get(localId)!! }
        assertEquals(SyncState.SYNCHRONIZED, resolved.syncState)
        assertTrue(resolved.content.contains("local two\nremote three"))
    }

    @Test
    fun failedConflictFetchLeavesTheConflictUnchanged() {
        importAccount(
            "alice",
            "Existing note",
            "etag-1",
            10,
            "# Existing note\n\nBase content"
        )
        val before = runBlocking {
            val note = notesOf("alice").single()
            application.component.noteRepository.save(
                note.copy(
                    content = "# Existing note\n\nLocal content",
                    syncState = SyncState.CONFLICT,
                    lastSyncError = "The note changed on the server"
                )
            )
            application.component.noteRepository.get(note.localId)!!
        }
        application.fakeBackend.getFailure = BackendException.Retryable(IOException("offline"))

        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.waitForTag("resolve-note-conflict")
        composeRule.onNodeWithTag("resolve-note-conflict").performClick()

        composeRule.waitForText("The server could not be reached")
        val notes = runBlocking { notesOf("alice") }
        val after = notes.single()
        assertEquals(before.localId, after.localId)
        assertEquals(before.content, after.content)
        assertEquals(before.remoteEtag, after.remoteEtag)
        assertEquals(before.localRevision, after.localRevision)
        assertEquals(SyncState.CONFLICT, after.syncState)
        assertEquals("The note changed on the server", after.lastSyncError)
        assertTrue(application.fakeBackend.pushedNotes.isEmpty())
    }

    @Test
    fun widgetRequestOpensTheSelectedCachedNote() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)
        val note = runBlocking {
            application.component.noteRepository.observeNotes(account.localAccountId()).first()
                .single()
        }

        application.component.receiveWidgetRequest(WidgetRequest.OpenNote(note.localId))

        composeRule.waitForTag("back-to-note-list")
        composeRule.waitForTag("markdown-view")
    }

    @Test
    fun noteListWidgetHeaderOpensItsAccountInsteadOfThePreviouslyOpenNote() {
        val alice = importAccount("alice", "Alice note", "etag-alice", 10)
        val account = runBlocking {
            application.component.accountRepository.observeAccounts().first().single()
        }
        importAccount("bob", "Bob note", "etag-bob", 10)
        composeRule.onNodeWithText("Bob note").performClick()
        composeRule.waitForTag("markdown-view")

        val widgetId = 401
        val launchIntent = Intent(composeRule.activity.intent)
        try {
            WidgetPreferences.saveAccount(application, widgetId, account)
            composeRule.runOnUiThread {
                val views = NoteListWidgetProvider.remoteViews(application, widgetId)!!
                    .apply(composeRule.activity, FrameLayout(composeRule.activity))
                assertTrue(views.findViewById<View>(R.id.widget_camera).isClickable)
                assertTrue(views.findViewById<View>(R.id.widget_create).isClickable)
                assertTrue(views.findViewById<View>(R.id.widget_header).performClick())
            }

            composeRule.waitForTag("note-search")
            composeRule.onNodeWithText("Alice note").assertIsDisplayed()
            composeRule.onNodeWithText("Bob note").assertDoesNotExist()
            composeRule.onNodeWithTag("markdown-view").assertDoesNotExist()
            composeRule.onNodeWithTag("note-search").performTextInput("Alice")
            composeRule.onNodeWithText("Alice note").assertIsDisplayed()
            assertEquals(1, runBlocking { notesOf("alice") }.size)
            assertEquals(alice.localAccountId(), account.id)
        } finally {
            // The trampoline delivers a new intent to the single-task activity. Restore the
            // scenario's launch intent so its lifecycle monitor still recognizes teardown.
            composeRule.runOnUiThread { composeRule.activity.intent = launchIntent }
            WidgetPreferences.remove(application, widgetId)
        }
    }

    /**
     * Sharing text from another application. The intent is sent for real, so this covers the
     * manifest filter, the single-task delivery into the running activity, and the note it makes.
     */
    @Test
    fun sharedTextBecomesANewNoteAndOpensIt() {
        importAccount("alice", "Existing note", "etag-1", 10)

        share(text = "https://example.com/article", subject = "An article")

        composeRule.waitForText("An article")
        composeRule.waitForTag("markdown-view")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice") }.any {
                it.title == "An article" &&
                    it.content == "# An article\n\nhttps://example.com/article\n"
            }
        }
        // The shared note is uploaded like any other locally created note.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.pushedNotes.any { it.title == "An article" }
        }
    }

    /** Sharing again has to add a note rather than replace the one shared before. */
    @Test
    fun sharingTwiceMakesTwoNotes() {
        importAccount("alice", "Existing note", "etag-1", 10)

        share(text = "First", subject = "First share")
        composeRule.waitForText("First share")
        share(text = "Second", subject = "Second share")

        composeRule.waitForText("Second share")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice") }.count { it.title.endsWith("share") } == 2
        }
    }

    /**
     * A share that arrives before an account exists must not be dropped: the sharer is told why
     * there is no note yet, and the text becomes one as soon as onboarding produces an account.
     */
    @Test
    fun sharedTextWaitsForAnAccountAndIsNotLost() {
        share(text = "Remember this", subject = "Kept for later")

        composeRule.waitForTag("shared-text-waiting")
        composeRule.onNodeWithTag("onboarding").assertIsDisplayed()

        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(account, pull("Existing note", "etag-1", 10))
        accountAction("add-account")

        // The waiting text becomes a note as soon as there is an account, and that note opens,
        // so the note list is never what the sharer is left looking at.
        composeRule.waitForText("Kept for later")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice") }.any { it.content.contains("Remember this") }
        }
    }

    /** Rotating or restarting while the shared note is open must not make a second copy of it. */
    @Test
    fun sharedTextIsNotTurnedIntoASecondNoteAfterRecreation() {
        importAccount("alice", "Existing note", "etag-1", 10)
        share(text = "Only once", subject = "Only once")
        composeRule.waitForText("Only once")

        composeRule.activityRule.scenario.recreate()

        composeRule.waitForTag("markdown-view")
        assertEquals(1, runBlocking { notesOf("alice") }.count { it.title == "Only once" })
    }

    @Test
    fun backButtonReturnsToNoteListFromViewAndEditModes() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()

        composeRule.onNodeWithTag("back-to-note-list").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("note-list").assertIsDisplayed()

        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        composeRule.onNodeWithTag("back-to-note-list").assertIsDisplayed().performClick()
        composeRule.waitForTag("note-list")
    }

    /**
     * Editing writes continuously, so leaving without keeping the changes has to restore the note
     * rather than merely drop what has not been written yet.
     */
    @Test
    fun cancellingEditingRestoresTheNoteAfterConfirmation() {
        importAccount("alice", "Existing note", "etag-1", 10, "# Existing note\n\nOriginal body")
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(
            click(),
            replaceText("# Existing note\n\nAbandoned body")
        )
        awaitEditorText("Abandoned body")

        composeRule.onNodeWithTag("cancel-editing").performClick()
        composeRule.onNodeWithText("This note was modified", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-discard-changes").performClick()

        composeRule.waitForTag("markdown-view")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice").any { it.content.contains("Original body") } }
        }
        assertTrue(
            runBlocking { notesOf("alice").none { it.content.contains("Abandoned body") } }
        )
    }

    /** Leaving an unchanged note must not interrupt the writer with a question. */
    @Test
    fun cancellingWithoutChangesLeavesEditingImmediately() {
        importAccount("alice", "Existing note", "etag-1", 10)
        val note = runBlocking { notesOf("alice").single() }
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        awaitEditorFocus(focused = true)

        composeRule.onNodeWithTag("cancel-editing").performClick()

        composeRule.waitForTag("markdown-view")
        awaitEditorFocus(focused = false)
        composeRule.onNodeWithTag("confirm-discard-changes").assertDoesNotExist()
        composeRule.onNodeWithTag("edit-note").assertIsDisplayed()
        val unchanged = runBlocking { application.component.noteRepository.get(note.localId)!! }
        assertEquals(1L, unchanged.localRevision)
        assertEquals(SyncState.SYNCHRONIZED, unchanged.syncState)
    }

    @Test
    fun finishingAnUnchangedEditDoesNotUploadTheNote() {
        importAccount("alice", "Existing note", "etag-1", 10)
        val note = runBlocking { notesOf("alice").single() }
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()

        composeRule.onNodeWithTag("finish-editing").performClick()

        composeRule.waitForTag("markdown-view")
        Thread.sleep(SYNC_DELAY_MILLIS * 2)
        val unchanged = runBlocking { application.component.noteRepository.get(note.localId)!! }
        assertEquals(1L, unchanged.localRevision)
        assertEquals(SyncState.SYNCHRONIZED, unchanged.syncState)
        assertTrue(application.fakeBackend.pushedNotes.isEmpty())
    }

    /**
     * Regression test for the editor that could be displayed but never typed into. Espresso's
     * `typeText` taps the view and then injects key events into whichever view holds input focus,
     * so it fails unless the editor is genuinely focusable in touch mode. `replaceText` sets the
     * text directly and therefore cannot detect that defect.
     */
    @Test
    fun editorAcceptsTypedInputAfterBeingTapped() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()

        onView(withId(R.id.markdown_editor)).perform(click())
        onView(withId(R.id.markdown_editor)).check(matches(hasFocus()))
        onView(withId(R.id.markdown_editor)).perform(typeText(" typed by hand"))
        awaitEditorText("typed by hand")

        composeRule.onNodeWithTag("finish-editing").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice").any { it.content.contains("typed by hand") } }
        }
    }

    @Test
    fun returnContinuesAMarkdownListItem() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click(), replaceText("- item"))

        onView(withId(R.id.markdown_editor)).perform(typeText("\n"))

        awaitEditorText("- item\n- ")

        onView(withId(R.id.markdown_editor)).perform(typeText("\n"))

        awaitEditorText("- item\n\n")
    }

    @Test
    fun toolbarFormattingKeepsInputFocusInTheEditor() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()

        onView(withId(R.id.markdown_editor)).perform(click(), typeText("bold me"))
        // Format the fully typed text, not whatever part of it the input method has committed.
        awaitEditorText("bold me")
        composeRule.onNodeWithTag("format-bold").performClick()

        awaitEditorText("**")
        onView(withId(R.id.markdown_editor)).check(matches(hasFocus()))
    }

    @Test
    fun toolbarInsertsLocalDateAtTheSelectionAndCanUndo() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click(), replaceText("before after"))
        composeRule.runOnUiThread {
            composeRule.activity.findViewById<org.qownnotes.mobile.markdown.MarkdownEditText>(
                R.id.markdown_editor
            ).setSelection(7, 12)
        }

        val today = LocalDateTime.now().toLocalDate().toString()
        composeRule.onNodeWithTag("insert-date").performScrollTo().performClick()
        awaitEditorText("before $today")
        onView(withId(R.id.markdown_editor)).check(matches(hasFocus()))
        // The date action scrolls the horizontal toolbar away from Undo.
        composeRule.onNodeWithTag("undo-edit").performScrollTo().assertIsEnabled().performClick()
        awaitEditorText("before after")
    }

    @Test
    fun detectedDeckSupportCreatesACardFromTheSelectionAndLinksIt() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)
        application.fakeBackend.deckBoards = listOf(
            DeckBoard(2, "Work", listOf(DeckStack(11, "To do"), DeckStack(12, "Done"))),
            DeckBoard(3, "Home", listOf(DeckStack(31, "Inbox")))
        )
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor))
            .perform(click(), replaceText("Call Alice about the report"))
        composeRule.runOnUiThread {
            composeRule.activity.findViewById<org.qownnotes.mobile.markdown.MarkdownEditText>(
                R.id.markdown_editor
            ).setSelection(0, 10)
        }
        // The server reports Deck support, so the editor offers the action without any setting.
        composeRule.waitForTag("create-deck-card-link")
        composeRule.onNodeWithTag("create-deck-card-link").performScrollTo().performClick()

        composeRule.waitForTag("deck-card-target")
        composeRule.onNodeWithTag("deck-card-title").assertTextContains("Call Alice")
        composeRule.onNodeWithTag("deck-card-target").assertTextContains("Work / To do")
        composeRule.onNodeWithTag("deck-card-target").performClick()
        composeRule.waitForTag("deck-target-3-31")
        composeRule.onNodeWithTag("deck-target-3-31").performClick()
        composeRule.onNodeWithTag("deck-card-target").assertTextContains("Home / Inbox")
        composeRule.onNodeWithTag("deck-card-description").performTextInput("Ask about Q3")
        // A due date is optional and offered only once it is asked for.
        composeRule.onNodeWithTag("deck-card-due-toggle").assertIsOff().performClick()
        composeRule.onNodeWithTag("deck-card-due-toggle").assertIsOn()
        composeRule.waitForTag("deck-card-due-date")
        composeRule.onNodeWithTag("deck-card-due-time").assertIsDisplayed()
        composeRule.onNodeWithTag("deck-card-due-toggle").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("deck-card-due-date").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("create-deck-card").performClick()

        awaitEditorText(
            "[Call Alice](https://cloud.example/apps/deck/#/board/3/card/500) about the report"
        )
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("deck-card-dialog").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(
            listOf(DeckStackTarget(3, 31) to DeckCardDraft("Call Alice", "Ask about Q3", null)),
            application.fakeBackend.createdDeckCards
        )
        assertEquals(
            DeckStackTarget(3, 31),
            application.component.settings.nextcloudDeckTarget(account.localAccountId())
        )
    }

    @Test
    fun deckActionFollowsTheDetectedServerSupport() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)
        val accountId = account.localAccountId()
        // Offline, the last detected support still applies.
        application.component.settings.setNextcloudDeckAvailable(accountId, true)
        application.fakeBackend.deckSupportFailure =
            BackendException.Retryable(Exception("offline"))
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        composeRule.waitForTag("create-deck-card-link")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.deckSupportChecks.size == 1
        }
        composeRule.onNodeWithTag("create-deck-card-link").assertExists()
        composeRule.onNodeWithTag("finish-editing").performClick()
        composeRule.onNodeWithTag("back-to-note-list").performClick()

        // A failed check is repeated, and a server without Deck hides the action.
        application.fakeBackend.deckSupported = false
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        composeRule.waitForTag("insert-datetime")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("create-deck-card-link").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(listOf(accountId, accountId), application.fakeBackend.deckSupportChecks)
        assertFalse(application.component.settings.nextcloudDeckAvailable(accountId).value)
        composeRule.onNodeWithTag("finish-editing").performClick()
        composeRule.onNodeWithTag("back-to-note-list").performClick()

        // A completed check is not repeated in the same run.
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        composeRule.waitForTag("insert-datetime")
        assertEquals(2, application.fakeBackend.deckSupportChecks.size)
    }

    @Test
    fun toolbarLinkDialogUsesClipboardAndReplacesSelectionWithUndo() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click(), replaceText("Read this"))
        composeRule.runOnUiThread {
            composeRule.activity.findViewById<org.qownnotes.mobile.markdown.MarkdownEditText>(
                R.id.markdown_editor
            ).setSelection(5, 9)
            composeRule.activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                android.content.ClipData.newPlainText("URL", "https://example.com/article")
            )
        }
        composeRule.onNodeWithTag("format-link").performScrollTo().performClick()
        composeRule.onNodeWithTag("link-url").assertTextContains("https://example.com/article")
        composeRule.onNodeWithTag("link-text").assertTextContains("this")
        composeRule.onNodeWithTag("insert-link-confirm").performClick()
        awaitEditorText("Read [this](https://example.com/article)")
        composeRule.onNodeWithTag("undo-edit").performScrollTo().performClick()
        awaitEditorText("Read this")
        composeRule.onNodeWithTag("redo-edit").performClick()
        awaitEditorText("Read [this](https://example.com/article)")
    }

    @Test
    fun failedDeckCardCreationKeepsTheDialogAndTheNoteUnchanged() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)
        val accountId = account.localAccountId()
        application.component.settings.setNextcloudDeckAvailable(accountId, true)
        application.component.settings.setNextcloudDeckTarget(accountId, DeckStackTarget(3, 31))
        application.fakeBackend.deckBoards = listOf(
            DeckBoard(2, "Work", listOf(DeckStack(11, "To do"))),
            DeckBoard(3, "Home", listOf(DeckStack(31, "Inbox")))
        )
        application.fakeBackend.createDeckCardFailure = BackendException.Permission()
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click(), replaceText("Text"))
        composeRule.runOnUiThread {
            composeRule.activity.findViewById<org.qownnotes.mobile.markdown.MarkdownEditText>(
                R.id.markdown_editor
            ).setSelection(4)
        }

        val earliestDue = java.time.Instant.now().plusSeconds(3_540).epochSecond
        composeRule.onNodeWithTag("create-deck-card-link").performScrollTo().performClick()
        composeRule.waitForTag("deck-card-target")
        // The list that received the previous card is offered again.
        composeRule.onNodeWithTag("deck-card-target").assertTextContains("Home / Inbox")
        composeRule.onNodeWithTag("create-deck-card").assertIsNotEnabled()
        composeRule.onNodeWithTag("deck-card-title").performTextInput("Buy milk")
        composeRule.onNodeWithTag("create-deck-card").performClick()

        composeRule.waitForTag("deck-card-error")
        composeRule.onNodeWithTag("deck-card-error").assertTextContains("Permission denied")
        composeRule.onNodeWithTag("deck-card-title").assertTextContains("Buy milk")
        awaitEditorText("deck", present = false)
        assertTrue(application.fakeBackend.createdDeckCards.isEmpty())

        // The default due date is one hour after the dialog opened, on the minute.
        composeRule.onNodeWithTag("deck-card-due-toggle").performClick()
        composeRule.waitForTag("deck-card-due-date")
        composeRule.onNodeWithTag("create-deck-card").performClick()
        awaitEditorText("Text[Buy milk](https://cloud.example/apps/deck/#/board/3/card/500)")
        val dueAt = application.fakeBackend.createdDeckCards.single().second.dueAtEpochSeconds
        assertTrue("due date $dueAt", dueAt != null && dueAt >= earliestDue && dueAt % 60 == 0L)
        assertTrue(dueAt!! <= java.time.Instant.now().plusSeconds(3_600).epochSecond)
    }

    @Test
    fun toolbarInsertsLocalDateTimeAtTheSelection() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click(), replaceText("before after"))
        composeRule.runOnUiThread {
            composeRule.activity.findViewById<org.qownnotes.mobile.markdown.MarkdownEditText>(
                R.id.markdown_editor
            ).setSelection(7, 12)
        }

        val before = LocalDateTime.now()
        composeRule.onNodeWithTag("insert-datetime").performScrollTo().performClick()
        val after = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val text = composeRule.runOnIdle {
                composeRule.activity.findViewById<TextView>(R.id.markdown_editor).text.toString()
            }
            text == "before ${before.format(formatter)}" ||
                text == "before ${after.format(formatter)}"
        }
        onView(withId(R.id.markdown_editor)).check(matches(hasFocus()))
    }

    @Test
    fun enterRemovesSingleTrailingSpaceAndPreservesMarkdownHardBreaks() {
        importAccount("alice", "Existing note", "etag-1", 10, "text ")
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click())
        composeRule.runOnUiThread {
            composeRule.activity.findViewById<org.qownnotes.mobile.markdown.MarkdownEditText>(
                R.id.markdown_editor
            ).setSelection(5)
        }

        // Let the input method commit, newline cleanup, and the posted caret restoration finish
        // before the next key. Digits keep IME capitalization and word autocorrection out of this
        // whitespace test.
        fun pressAndCheck(keyCode: Int, expected: String) {
            onView(withId(R.id.markdown_editor)).perform(pressKey(keyCode))
            awaitEditorState(expected, expected.length)
        }

        pressAndCheck(KeyEvent.KEYCODE_ENTER, "text\n")
        var expected = "text\n"
        for (character in "1234  \n5678") {
            val keyCode = when (character) {
                ' ' -> KeyEvent.KEYCODE_SPACE
                '\n' -> KeyEvent.KEYCODE_ENTER
                else -> KeyEvent.keyCodeFromString("KEYCODE_${character.uppercaseChar()}")
            }
            expected += character
            pressAndCheck(keyCode, expected)
        }
    }

    @Test
    fun toolbarCreatesListAndCheckboxListItems() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()

        onView(withId(R.id.markdown_editor)).perform(click(), replaceText("item"))
        composeRule.onNodeWithTag("format-list").performClick()
        awaitEditorText("- item")

        onView(withId(R.id.markdown_editor)).perform(replaceText("task"))
        composeRule.onNodeWithTag("format-checkbox-list").performClick()
        awaitEditorText("- [ ] task")

        // Returning on an empty item ends the list. The editor applies that after the key event
        // has been delivered, so wait for the marker to go before reading the remaining text.
        onView(withId(R.id.markdown_editor)).perform(replaceText(""))
        composeRule.onNodeWithTag("format-list").performClick()
        awaitEditorText("- ")
        onView(withId(R.id.markdown_editor)).perform(typeText("\n"))
        awaitEditorText("- ", present = false)
        onView(withId(R.id.markdown_editor)).check(matches(withText("\n")))

        onView(withId(R.id.markdown_editor)).perform(replaceText(""))
        composeRule.onNodeWithTag("format-checkbox-list").performClick()
        awaitEditorText("- [ ] ")
        onView(withId(R.id.markdown_editor)).perform(typeText("\n"))
        awaitEditorText("- [ ] ", present = false)
        onView(withId(R.id.markdown_editor)).check(matches(withText("\n")))
    }

    @Test
    fun enterBeforeExistingListItemsCreatesItemsReadyForTyping() {
        val items = listOf(
            "- bullet" to "- ",
            "  - [x] done" to "  - [ ] ",
            "3. numbered" to "3. "
        )
        var expected = items.joinToString("\n") { it.first }
        importAccount("alice", "Lists", "etag-1", 10, expected)
        composeRule.onNodeWithText("Lists").performClick()
        composeRule.enterEditMode()

        for ((item, prefix) in items) {
            onView(withId(R.id.markdown_editor)).perform(click())
            val caret = expected.indexOf(item)
            composeRule.runOnUiThread {
                composeRule.activity.findViewById<org.qownnotes.mobile.markdown.MarkdownEditText>(
                    R.id.markdown_editor
                ).setSelection(caret)
            }
            onView(withId(R.id.markdown_editor)).perform(pressKey(KeyEvent.KEYCODE_ENTER))
            expected = expected.replaceRange(caret, caret, "$prefix\n")
            awaitEditorState(expected, caret + prefix.length)

            onView(withId(R.id.markdown_editor)).perform(pressKey(KeyEvent.KEYCODE_X))
            expected = expected.replaceRange(caret + prefix.length, caret + prefix.length, "x")
            awaitEditorState(expected, caret + prefix.length + 1)
        }
    }

    /**
     * The framework editor has an undo buffer of its own, but only a hardware keyboard can reach
     * it, so the toolbar controls are what makes undo usable on a phone at all.
     */
    @Test
    fun toolbarUndoAndRedoStepThroughEditing() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        composeRule.onNodeWithTag("undo-edit").assertIsNotEnabled()

        onView(withId(R.id.markdown_editor)).perform(click(), typeText("regretted"))
        awaitEditorText("regretted")
        composeRule.onNodeWithTag("undo-edit").performClick()
        awaitEditorText("regretted", present = false)

        composeRule.onNodeWithTag("redo-edit").performClick()
        awaitEditorText("regretted")
        // Undoing must not take the note away from the writer. Replaying a step rewrites a range
        // of the document, so read the note back once that rewrite has landed.
        awaitEditorText("Existing note")
        onView(withId(R.id.markdown_editor)).check(matches(hasFocus()))
    }

    @Test
    fun findInEditorSearchesMarkdownSourceAndRestoresEditing() {
        val source = "# Recipe\n\nAdd **salt**, then more salt."
        importAccount("alice", "Recipe", "etag-1", 10, source)
        composeRule.onNodeWithText("Recipe").performClick()
        composeRule.enterEditMode()

        composeRule.onNodeWithTag("find-in-note").performClick()
        composeRule.onNodeWithTag("format-toolbar").assertDoesNotExist()
        composeRule.onNodeWithTag("note-find-field").performTextInput("**salt**")
        composeRule.waitForText("1 of 1")

        composeRule.onNodeWithTag("note-find-field").performTextReplacement("salt")
        composeRule.waitForText("1 of 2")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            editorSelectionStart() == source.indexOf("salt")
        }
        composeRule.onNodeWithTag("find-next").performClick()
        composeRule.onNodeWithText("2 of 2").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            editorSelectionStart() == source.lastIndexOf("salt")
        }

        composeRule.onNodeWithTag("close-find").performClick()
        composeRule.onNodeWithTag("note-find-field").assertDoesNotExist()
        composeRule.onNodeWithTag("format-toolbar").assertIsDisplayed()
        awaitEditorFocus(true)
        onView(withId(R.id.markdown_editor)).check(matches(hasFocus()))
    }

    @Test
    fun typingAfterFindingInViewModeKeepsTheChosenCaret() {
        val source = "# Recipe\n\nAdd **salt**, then more salt.\n\nWrite here."
        importAccount("alice", "Recipe", "etag-1", 10, source)
        composeRule.onNodeWithText("Recipe").performClick()
        composeRule.onNodeWithTag("find-in-note").performClick()
        composeRule.onNodeWithTag("note-find-field").performTextInput("salt")
        composeRule.waitForText("1 of 2")
        composeRule.enterEditMode()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            editorSelectionStart() == source.indexOf("salt")
        }

        // Tap for real input focus, then choose a caret away from either search result. Inject
        // one key at a time so each draft/highlight update can run before the next character.
        onView(withId(R.id.markdown_editor)).perform(click())
        val caret = source.indexOf("here")
        composeRule.runOnUiThread {
            composeRule.activity.findViewById<org.qownnotes.mobile.markdown.MarkdownEditText>(
                R.id.markdown_editor
            ).setSelection(caret)
        }
        val inserted = "xyz"
        inserted.forEachIndexed { index, character ->
            onView(withId(R.id.markdown_editor)).perform(
                pressKey(KeyEvent.KEYCODE_X + (character - 'x'))
            )
            awaitEditorText("Write ${inserted.take(index + 1)}here.")
            composeRule.waitForIdle()
            assertEquals(caret + index + 1, editorSelectionStart())
            onView(withId(R.id.markdown_editor)).check(
                matches(withText(source.replaceRange(caret, caret, inserted.take(index + 1))))
            )
        }
        composeRule.onNodeWithTag("note-find-field").assertExists()
        composeRule.onNodeWithText("1 of 2").assertIsDisplayed()
        composeRule.onNodeWithTag("find-next").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            editorSelectionStart() == source.lastIndexOf("salt")
        }
    }

    @Test
    fun noteTextSizeCanBeIncreasedAndSurvivesRecreation() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.waitForTag("markdown-view")

        val initial = textSizeOf(R.id.markdown_view)
        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("increase-note-text-size").performClick()
        val increased = textSizeOf(R.id.markdown_view)
        assertTrue("expected $increased to exceed $initial", increased > initial)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForTag("markdown-view")
        assertEquals(increased, textSizeOf(R.id.markdown_view), 0.5f)
    }

    @Test
    fun noteTextSizeAlsoAppliesToTheEditor() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()

        val initial = textSizeOf(R.id.markdown_editor)
        composeRule.onNodeWithTag("increase-note-text-size").performClick()
        assertTrue(textSizeOf(R.id.markdown_editor) > initial)
    }

    @Test
    fun editingStartsNearTheCurrentReadingPosition() {
        val content = (1..80).joinToString("\n\n") {
            "Paragraph $it of a note that is longer than a screen."
        }
        importAccount("alice", "Long note", "etag-1", 10, content)
        composeRule.onNodeWithText("Long note").performClick()
        composeRule.waitForTag("markdown-view")
        val renderedTopBeforeScroll = screenTopOf(R.id.markdown_view)
        composeRule.onNodeWithTag("markdown-view").performTouchInput {
            swipeUp(durationMillis = 1_000)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            screenTopOf(R.id.markdown_view) < renderedTopBeforeScroll
        }

        composeRule.enterEditMode()

        var selection = 0
        onView(withId(R.id.markdown_editor)).check { view, _ ->
            selection = (view as TextView).selectionStart
        }
        assertTrue("expected selection after the start of the note", selection > 0)
        assertTrue("expected selection before the end of the note", selection < content.length)

        lateinit var editor: org.qownnotes.mobile.markdown.MarkdownEditText
        onView(withId(R.id.markdown_editor)).check { view, _ ->
            editor = view as org.qownnotes.mobile.markdown.MarkdownEditText
        }
        val before = editor.scrollY
        composeRule.onNodeWithTag("editor-fast-scroll").assertIsDisplayed()
            .performTouchInput { swipeDown() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            editor.scrollY > before
        }
    }

    @Test
    fun editorScrollsToKeepTheTypingCursorVisible() {
        val content = (1..80).joinToString("\n") { "Line $it" }
        importAccount("alice", "Long note", "etag-1", 10, content)
        composeRule.onNodeWithText("Long note").performClick()
        composeRule.enterEditMode()
        lateinit var editor: org.qownnotes.mobile.markdown.MarkdownEditText
        onView(withId(R.id.markdown_editor)).check { view, _ ->
            editor = view as org.qownnotes.mobile.markdown.MarkdownEditText
        }

        onView(withId(R.id.markdown_editor)).check(matches(hasFocus()))
        composeRule.runOnUiThread { editor.setSelection(editor.length()) }
        onView(withId(R.id.markdown_editor)).perform(pressKey(KeyEvent.KEYCODE_X))

        composeRule.waitUntil(timeoutMillis = 10_000) { editor.length() > content.length }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            editor.scrollY > 0
        }
    }

    @Test
    fun noteTextSizeStopsAtItsSmallestStep() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.waitForTag("markdown-view")

        // Step down to the minimum and verify the menu prevents shrinking any further.
        repeat(NoteTextSize.steps.indexOf(NoteTextSize.DEFAULT_SP)) {
            composeRule.openNoteMenu()
            composeRule.onNodeWithTag("decrease-note-text-size").performClick()
        }

        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("decrease-note-text-size").assertIsNotEnabled()
        // Reading stays possible at the smallest step, and enlarging is still offered.
        composeRule.onNodeWithTag("increase-note-text-size").assertIsEnabled()
    }

    @Test
    fun readOnlyNoteCannotEnterEditingMode() {
        val account = testAccount("alice")
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(
                        id = 42,
                        etag = "etag-1",
                        title = "Shared note",
                        content = "Read only content",
                        category = "",
                        modifiedAtEpochSeconds = 10,
                        readOnly = true
                    )
                ),
                collectionEtag = "etag-1",
                lastModifiedEpochSeconds = 10
            )
        )
        accountAction("add-account")
        composeRule.waitForText("Shared note")

        composeRule.onNodeWithText("Shared note").performClick()

        composeRule.waitForText("Read only")
        composeRule.onNodeWithTag("edit-note").assertDoesNotExist()
        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("rename-note").assertDoesNotExist()
        composeRule.onNodeWithTag("change-note-category").assertDoesNotExist()
    }

    @Test
    fun noteMenuHidesMoveToFolderWhenSubfoldersAreOffByDefault() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()

        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("note-information").assertIsDisplayed()
        composeRule.onNodeWithTag("rename-note").assertIsDisplayed()
        composeRule.onNodeWithTag("change-note-category").assertDoesNotExist()
        composeRule.onNodeWithTag("delete-note").assertIsDisplayed()
    }

    @Test
    fun noteViewKeepsFindAndEditVisibleAndSecondaryActionsInTheMenu() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)
        application.component.settings.setUseSubfolders(account.localAccountId(), true)
        composeRule.onNodeWithText("Existing note").performClick()

        composeRule.waitForTag("edit-note")
        composeRule.onNodeWithTag("find-in-note").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-note").assertIsDisplayed()
        composeRule.onNodeWithTag("rename-note").assertDoesNotExist()
        composeRule.onNodeWithTag("note-information").assertDoesNotExist()

        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("note-information").assertIsDisplayed()
        composeRule.onNodeWithTag("rename-note").assertIsDisplayed()
        composeRule.onNodeWithTag("change-note-category").assertIsDisplayed()
        composeRule.onNodeWithTag("delete-note").assertIsDisplayed()
    }

    @Test
    fun noteInformationShowsAvailableMetadata() {
        importAccount(
            "alice",
            "Existing note",
            "etag-1",
            1_788_177_600,
            content = "one two\nπ"
        )
        composeRule.onNodeWithText("Existing note").performClick()

        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("note-information").performClick()

        composeRule.onNodeWithTag("note-information-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Modified").assertIsDisplayed()
        composeRule.onNodeWithText("Markdown size").assertIsDisplayed()
        composeRule.onNodeWithText("10 B").assertIsDisplayed()
        composeRule.onNodeWithText("Words").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("Characters").assertIsDisplayed()
        composeRule.onNodeWithText("9").assertIsDisplayed()
        composeRule.onNodeWithText("Lines").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("Subfolder").assertIsDisplayed()
        composeRule.onNodeWithText("Root folder").assertIsDisplayed()
        composeRule.onNodeWithText("Account").assertIsDisplayed()
        composeRule.onNodeWithText("alice @ cloud.example").assertIsDisplayed()
        composeRule.onNodeWithText("Synchronized").assertIsDisplayed()
        composeRule.onNodeWithText("Writable").assertIsDisplayed()
        composeRule.onNodeWithText("42").assertIsDisplayed()

        composeRule.onNodeWithTag("close-note-information").performClick()
        composeRule.onNodeWithTag("note-information-dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("markdown-view").assertIsDisplayed()
    }

    @Test
    fun noteCanCreateAndMoveToANewCategory() {
        val account = importAccount("alice", "Existing note", "etag-1", 10)
        application.component.settings.setUseSubfolders(account.localAccountId(), true)
        composeRule.onNodeWithText("Existing note").performClick()

        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("change-note-category").performClick()
        composeRule.onNodeWithTag("new-note-category-field")
            .performTextInput(" Projects / Android? ")
        composeRule.onNodeWithTag("confirm-note-category").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice").single().category } == "Projects/Android"
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.pushedNotes.any { it.category == "Projects/Android" }
        }
    }

    @Test
    fun noteCanMoveToAnExistingCategory() {
        val account = testAccount("alice")
        application.component.settings.setUseSubfolders(account.localAccountId(), true)
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                notes = listOf(
                    RemoteNote(42, "etag-1", "Root note", "# Root note", "", 10),
                    RemoteNote(43, "etag-2", "Work note", "# Work note", "Work", 10)
                ),
                collectionEtag = "collection-etag",
                lastModifiedEpochSeconds = 10
            )
        )
        accountAction("add-account")
        composeRule.waitForText("Root note")
        composeRule.onNodeWithText("Root note").performClick()

        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("change-note-category").performClick()
        composeRule.onNodeWithTag("note-category-Work").performClick()
        composeRule.onNodeWithTag("confirm-note-category").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { notesOf("alice").first { it.title == "Root note" }.category } == "Work"
        }
    }

    /**
     * The name of a note is the name of the file holding it, so a rename has to be uploaded.
     * Renaming without the heading option touches nothing but that name.
     */
    @Test
    fun renamingANoteShowsAndUploadsTheNewFileName() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()

        renameNote("Grocery list", updateHeading = false)

        composeRule.waitForText("Grocery list")
        composeRule.onNodeWithTag("back-to-note-list").performClick()
        composeRule.waitForText("Grocery list")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.pushedNotes.any { it.title == "Grocery list" }
        }
        assertEquals(listOf("# Existing note"), noteContents("alice"))
    }

    /**
     * The heading a note opens with usually repeats its file name, so the rename dialog offers to
     * carry the new name into that heading, and does so unless the writer says otherwise.
     */
    @Test
    fun renamingANoteAlsoRewritesTheFirstHeadingByDefault() {
        importAccount("alice", "Existing note", "etag-1", 10, content = "# Existing note\n\nBody")
        composeRule.onNodeWithText("Existing note").performClick()

        renameNote("Grocery list")

        composeRule.waitForText("Grocery list")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.pushedNotes.any {
                it.title == "Grocery list" && it.content == "# Grocery list\n\nBody"
            }
        }
        assertEquals(listOf("# Grocery list\n\nBody"), noteContents("alice"))
    }

    /** Typing immediately replaces the selected current name instead of appending to it. */
    @Test
    fun renameDialogSelectsTheCurrentName() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()

        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("rename-note").performClick()
        composeRule.waitForTag("note-name-field")
        composeRule.onNodeWithTag("note-name-field").performTextInput("Replacement")
        composeRule.onNodeWithTag("confirm-rename-note").performClick()

        composeRule.waitForText("Replacement")
        composeRule.onNodeWithText("Existing noteReplacement").assertDoesNotExist()
    }

    /** A name that holds nothing a file system accepts would leave the note unreachable. */
    @Test
    fun renamingRefusesANameThatNoFileCanCarry() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()

        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("rename-note").performClick()
        composeRule.waitForTag("note-name-field")
        composeRule.onNodeWithTag("note-name-field").performTextReplacement(" / ")
        composeRule.onNodeWithTag("confirm-rename-note").assertIsNotEnabled()

        composeRule.onNodeWithTag("note-name-field").performTextReplacement("Usable name")
        composeRule.onNodeWithTag("confirm-rename-note").assertIsEnabled()
    }

    @Test
    fun renderedNoteTextCanBeSelectedAndCopied() {
        importAccount(
            "alice",
            "Recipe",
            "etag-1",
            10,
            "# Recipe\n\nAdd salt, then taste it.\n"
        )
        composeRule.onNodeWithText("Recipe").performClick()
        composeRule.waitForTag("markdown-view")

        val copied = selectAllAndCopy(R.id.markdown_view)

        // The rendered text is copied, so the Markdown heading marker is not part of it.
        assertTrue("unexpected clipboard content: $copied", copied.contains("Add salt, then taste"))
        assertTrue("unexpected clipboard content: $copied", copied.startsWith("Recipe"))
    }

    /**
     * The gesture a reader actually uses. Selection only starts when the note view is selectable
     * and its movement method allows arbitrary selection, so this covers the whole path rather
     * than the flags behind it.
     */
    @Test
    fun longPressingTheRenderedNoteSelectsAWord() {
        importAccount(
            "alice",
            "Recipe",
            "etag-1",
            10,
            "Add salt, then taste it and add more salt because salt makes it tasty.\n"
        )
        composeRule.onNodeWithText("Recipe").performClick()
        composeRule.waitForTag("markdown-view")

        onView(withId(R.id.markdown_view)).perform(longPressOnRenderedText())

        composeRule.waitUntil(timeoutMillis = 10_000) {
            selectionOf(R.id.markdown_view).isNotBlank()
        }
    }

    /**
     * A selectable `TextView` consumes touches that a read-only one ignores. The note must still
     * scroll inside its Compose container, which is what a reader does far more often than
     * selecting.
     */
    @Test
    fun theRenderedNoteStillScrollsWhileItsTextIsSelectable() {
        importAccount(
            "alice",
            "Long note",
            "etag-1",
            10,
            (1..80).joinToString("\n\n") { "Paragraph $it of a note that is longer than a screen." }
        )
        composeRule.onNodeWithText("Long note").performClick()
        composeRule.waitForTag("markdown-view")
        val before = screenTopOf(R.id.markdown_view)

        composeRule.onNodeWithTag("markdown-view").performTouchInput { swipeUp() }

        composeRule.waitUntil(timeoutMillis = 10_000) { screenTopOf(R.id.markdown_view) < before }
    }

    @Test
    fun longRenderedNoteHasAFastScroller() {
        importAccount(
            "alice",
            "Long note",
            "etag-1",
            10,
            (1..80).joinToString("\n\n") { "Paragraph $it of a note that is longer than a screen." }
        )
        composeRule.onNodeWithText("Long note").performClick()
        composeRule.onNodeWithTag("note-fast-scroll").assertIsDisplayed()
        val before = screenTopOf(R.id.markdown_view)

        composeRule.onNodeWithTag("note-fast-scroll").performTouchInput { swipeDown() }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            screenTopOf(R.id.markdown_view) < before - 1_000
        }
    }

    @Test
    fun shortRenderedNoteDoesNotShowAFastScroller() {
        importAccount("alice", "Short note", "etag-1", 10, "A short note.")
        composeRule.onNodeWithText("Short note").performClick()
        composeRule.waitForTag("markdown-view")

        composeRule.onNodeWithTag("note-fast-scroll").assertDoesNotExist()
    }

    @Test
    fun findInNoteCountsCyclesAndClearsMatches() {
        importAccount(
            "alice",
            "Recipe",
            "etag-1",
            10,
            "# Recipe\n\nAdd salt, then more salt, and finally taste the **salt**.\n"
        )
        composeRule.onNodeWithText("Recipe").performClick()
        composeRule.waitForTag("markdown-view")

        composeRule.onNodeWithTag("find-in-note").performClick()
        composeRule.onNodeWithTag("note-find-field").performTextInput("SALT")

        // The rendered note is searched, so the emphasized occurrence counts once and its
        // surrounding source markers are not part of the text.
        composeRule.waitForText("1 of 3")
        composeRule.onNodeWithTag("find-next").performClick()
        composeRule.onNodeWithText("2 of 3").assertIsDisplayed()
        composeRule.onNodeWithTag("find-previous").performClick()
        composeRule.onNodeWithText("1 of 3").assertIsDisplayed()
        // Moving back past the first match wraps around to the last one.
        composeRule.onNodeWithTag("find-previous").performClick()
        composeRule.onNodeWithText("3 of 3").assertIsDisplayed()

        composeRule.onNodeWithTag("note-find-field").performTextReplacement("pepper")
        composeRule.waitForText("No matches")
        composeRule.onNodeWithTag("find-next").assertIsNotEnabled()

        composeRule.onNodeWithTag("close-find").performClick()
        composeRule.onNodeWithTag("note-find-field").assertDoesNotExist()
    }

    @Test
    fun noteOpenedFromAnActiveSearchFindsTheSearchTextWithoutTakingFocus() {
        importAccount(
            "alice",
            "Recipe",
            "etag-1",
            10,
            "# Recipe\n\nAdd salt, then more salt.\n"
        )
        composeRule.onNodeWithTag("note-search").performTextInput("  SALT ")
        composeRule.waitForTag("clear-note-search")
        composeRule.onNodeWithText("Recipe").performClick()
        composeRule.waitForTag("markdown-view")

        composeRule.onNodeWithTag("note-find-field").assertTextContains("SALT")
        composeRule.waitForText("1 of 2")
        // The keyboard must not cover the note merely because it was opened from a search.
        composeRule.onNodeWithTag("note-find-field").assertIsNotFocused()
        composeRule.onNodeWithTag("find-next").performClick()
        composeRule.onNodeWithText("2 of 2").assertIsDisplayed()

        composeRule.onNodeWithTag("close-find").performClick()
        composeRule.onNodeWithTag("note-find-field").assertDoesNotExist()
        composeRule.onNodeWithTag("back-to-note-list").performClick()

        // Without a search, a note opens without finding anything.
        composeRule.waitForTag("clear-note-search")
        composeRule.onNodeWithTag("clear-note-search").performClick()
        composeRule.onNodeWithText("Recipe").performClick()
        composeRule.waitForTag("markdown-view")
        composeRule.onNodeWithTag("note-find-field").assertDoesNotExist()
    }

    /**
     * An editing session that never pauses must still reach storage. Every round of typing
     * restarts the idle save, so it never completes and whatever the database ends up holding was
     * written by the periodic checkpoint. What is stored is only checked for the shape of the
     * typed text, not for the newest of it, because a checkpoint is a snapshot of a moment that
     * the writer has already typed past by the time it can be read back.
     */
    @Test
    fun editorDraftIsPeriodicallyCheckpointedWhileTypingNeverPauses() {
        importAccount("alice", "Existing note", "etag-1", 10)
        val note = runBlocking { notesOf("alice").single() }
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(click())

        var round = 0
        composeRule.waitUntil(timeoutMillis = 30_000) {
            onView(withId(R.id.markdown_editor))
                .perform(replaceText("# Edited\n\nPeriodic checkpoint ${round++}"))
            runBlocking { application.component.noteRepository.get(note.localId)?.content }
                ?.startsWith("# Edited\n\nPeriodic checkpoint") == true
        }
    }

    /**
     * A checkpoint is a local safety net, not an edit the writer finished, so it must not start
     * network work of its own. Waiting past the synchronization delay is what makes the absence of
     * an upload mean anything.
     */
    @Test
    fun checkpointingADraftStartsNoNetworkWork() {
        importAccount("alice", "Existing note", "etag-1", 10)
        val note = runBlocking { notesOf("alice").single() }
        val pullsBefore = application.fakeBackend.checkpoints.size

        runBlocking {
            application.component.checkpointDraft(note.localId, "# Edited\n\nCheckpoint")
        }

        assertEquals(
            "# Edited\n\nCheckpoint",
            runBlocking { application.component.noteRepository.get(note.localId)?.content }
        )
        Thread.sleep(SYNC_DELAY_MILLIS * 2)
        assertTrue(
            "a checkpoint must not upload the note",
            application.fakeBackend.pushedNotes.isEmpty()
        )
        assertEquals(
            "a checkpoint must not refresh the account",
            pullsBefore,
            application.fakeBackend.checkpoints.size
        )
    }

    @Test
    fun anOlderEditorSaveCannotReplaceANewerDraft() {
        importAccount("alice", "Existing note", "etag-1", 10)
        val note = runBlocking { notesOf("alice").single() }
        application.component.cacheDraft(note.localId, "older")
        application.component.cacheDraft(note.localId, "newer")

        runBlocking { application.component.saveDraft(note.localId, "older") }

        assertEquals(
            "# Existing note",
            runBlocking { application.component.noteRepository.get(note.localId)?.content }
        )

        runBlocking { application.component.saveDraft(note.localId, "newer") }
        runBlocking { application.component.saveDraft(note.localId, "older") }

        assertEquals(
            "newer",
            runBlocking { application.component.noteRepository.get(note.localId)?.content }
        )
    }

    @Test
    fun editorDraftIsSavedWhenTheEditorLosesFocus() {
        importAccount("alice", "Existing note", "etag-1", 10)
        val note = runBlocking { notesOf("alice").single() }
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(
            click(),
            replaceText("# Edited\n\nSaved on focus loss")
        )

        onView(withId(R.id.markdown_editor)).check { view, _ -> view.clearFocus() }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { application.component.noteRepository.get(note.localId)?.content } ==
                "# Edited\n\nSaved on focus loss"
        }
    }

    @Test
    fun editorDraftSurvivesActivityRecreation() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()
        onView(withId(R.id.markdown_editor)).perform(
            click(),
            replaceText("# Edited\n\nUnsaved draft")
        )

        composeRule.activityRule.scenario.recreate()

        // A recreated activity composes the editor again, so the view only exists a moment later.
        awaitEditorText("Unsaved draft")
    }

    private fun importAccount(
        user: String,
        title: String,
        etag: String,
        modified: Long,
        content: String = "# $title"
    ): SingleSignOnAccount {
        val account = testAccount(user)
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(account, pull(title, etag, modified, content))
        accountAction("add-account")
        composeRule.waitForText(title)
        return account
    }

    /**
     * Account actions live in a menu on the account they act on, so they have to be opened first.
     * Onboarding has no account yet and offers the only action it has directly. Waiting for the
     * item covers actions that appear once another account has finished being imported.
     */
    private fun accountAction(tag: String) {
        if (composeRule.onAllNodesWithTag("account-menu").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("account-menu").performClick()
            composeRule.waitForTag(tag)
        }
        composeRule.onNodeWithTag(tag).performClick()
    }

    /**
     * General note-list actions are reached from the overflow menu beside search. Searching takes
     * the whole top bar, so leave search first when it holds the focus. That keeps the query, and
     * with it the filtered list the action is meant to run against.
     */
    private fun listAction(tag: String) {
        if (composeRule.onAllNodesWithTag("close-note-search").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("close-note-search").performClick()
            composeRule.waitForTag("note-list-menu")
        }
        composeRule.onNodeWithTag("note-list-menu").performClick()
        composeRule.waitForTag(tag)
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun searchFieldWidth() =
        composeRule.onNodeWithTag("note-search").fetchSemanticsNode().boundsInRoot.width

    private fun testAccount(user: String) =
        SingleSignOnAccount(user, user, "test-token", "https://cloud.example", "nextcloud")

    /**
     * Hands the running activity the intent a sharing application sends.
     *
     * The intent is not started here. `ActivityScenario` owns the activity it launched, and
     * starting the single-task activity again behind its back leaves it unable to shut that
     * activity down afterwards. That the system delivers such an intent into the one running
     * instance is a property of the manifest, which `ShareIntentTest` asserts instead.
     */
    private fun share(text: String, subject: String? = null) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        composeRule.runOnUiThread { composeRule.activity.acceptShare(intent) }
    }

    private suspend fun notesOf(user: String): List<Note> {
        val repository = application.component.noteRepository
        val accountId = testAccount(user).localAccountId()
        return repository.observeNotes(accountId).first().map {
            repository.get(it.localId)!!
        }
    }

    private fun pull(title: String, etag: String, modified: Long, content: String = "# $title") =
        PullResult(
            notes = listOf(RemoteNote(42, etag, title, content, "", modified)),
            collectionEtag = etag,
            lastModifiedEpochSeconds = modified
        )

    /**
     * Opening a note loads it from the repository, and the edit action appears only once that flow
     * has emitted. Entering edit mode then loads the editable note asynchronously as well, so the
     * editor view only exists after a later recomposition. Espresso does not observe Compose work,
     * so wait for both steps explicitly.
     */
    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.enterEditMode() {
        waitForTag("edit-note")
        onNodeWithTag("edit-note").performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag("markdown-editor").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.openNoteMenu() {
        waitForTag("note-menu")
        onNodeWithTag("note-menu").performClick()
    }

    /**
     * Renames the open note. Opening a note loads it from the repository, so its actions appear a
     * recomposition later, and the dialog is only reachable through the note menu.
     */
    private fun renameNote(name: String, updateHeading: Boolean = true) {
        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("rename-note").performClick()
        composeRule.waitForTag("note-name-field")
        composeRule.onNodeWithTag("note-name-field").performTextReplacement(name)
        composeRule.onNodeWithTag("update-heading-checkbox").assertIsOn()
        if (!updateHeading) {
            composeRule.onNodeWithTag("update-heading-checkbox").performClick()
            composeRule.onNodeWithTag("update-heading-checkbox").assertIsOff()
        }
        composeRule.onNodeWithTag("confirm-rename-note").performClick()
    }

    /** What the notes of an account hold, read from the repository rather than from the screen. */
    private fun noteContents(user: String): List<String> = runBlocking {
        notesOf(user).map { it.content }
    }

    /**
     * Pull to refresh reacts to how far a drag has travelled by the time it is released. The
     * injected gesture and the state that measures it advance on separate coroutines, so on a
     * loaded machine a swipe can be released before the pull has been accounted for and then
     * refreshes nothing. Drag slowly, start below the search field so that the field cannot take
     * the gesture for text selection, and repeat until the backend has actually been asked.
     */
    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.pullToRefresh() {
        val pullsBefore = application.fakeBackend.checkpoints.size
        repeat(3) {
            onNodeWithTag("pull-to-refresh").performTouchInput {
                swipeDown(startY = centerY, endY = height * 0.95f, durationMillis = 600)
            }
            val reachedTheBackend = runCatching {
                waitUntil(timeoutMillis = 3_000) {
                    application.fakeBackend.checkpoints.size > pullsBefore
                }
            }.isSuccess
            if (reachedTheBackend) return
        }
        throw AssertionError("pulling the note list down never reached the backend")
    }

    /**
     * Waits for the editor to hold or release input focus. Focus is requested once the view has
     * been attached and released as edit mode is left, so neither is true the moment the action
     * that causes it returns. A released editor is gone from the hierarchy and holds no focus.
     */
    private fun awaitEditorFocus(focused: Boolean) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.runOnIdle {
                composeRule.activity.findViewById<TextView>(R.id.markdown_editor)
                    ?.hasFocus() == true
            } == focused
        }
    }

    /**
     * Espresso injects key events into the input method, which commits the resulting characters
     * back through an asynchronous input connection. Looping the main thread until it is idle does
     * not cover that cross-process round trip, so the last characters of [typeText] can still be in
     * flight when the action returns. Poll the editor instead of asserting once.
     */
    private fun awaitEditorText(substring: String, present: Boolean = true) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.runOnIdle {
                composeRule.activity.findViewById<TextView>(R.id.markdown_editor)
                    ?.text
                    ?.contains(substring) == present
            }
        }
    }

    /**
     * Waits until the editor holds exactly [text] with the caret at [caret]. The input method
     * receives injected key events first and commits their text asynchronously, which neither
     * Espresso nor Compose idleness covers, so an immediate assertion can observe the editor
     * before the key arrives.
     */
    private fun awaitEditorState(text: String, caret: Int) {
        var actual: Pair<String, Int>? = null
        try {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                actual = composeRule.runOnIdle {
                    composeRule.activity.findViewById<TextView>(R.id.markdown_editor)?.let {
                        it.text.toString() to it.selectionStart
                    }
                }
                actual == (text to caret)
            }
        } catch (timeout: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError(
                "Expected editor text ${text.quoted()} with caret $caret, but was " +
                    "${actual?.first?.quoted()} with caret ${actual?.second}",
                timeout
            )
        }
    }

    private fun String.quoted() = "\"" + replace("\n", "\\n") + "\""

    private fun editorSelectionStart(): Int = composeRule.runOnIdle {
        composeRule.activity.findViewById<TextView>(R.id.markdown_editor)?.selectionStart ?: -1
    }

    /**
     * Selects the whole rendered note and copies it through the same `TextView` actions the
     * selection toolbar uses, which only work when the note view is genuinely selectable.
     */
    private fun selectAllAndCopy(viewId: Int): String {
        lateinit var view: TextView
        onView(withId(viewId)).check { found, _ -> view = found as TextView }
        composeRule.runOnUiThread {
            assertTrue("the rendered note is not selectable", view.isTextSelectable)
            view.onTextContextMenuItem(android.R.id.selectAll)
            assertTrue("selecting the note produced no selection", view.hasSelection())
            view.onTextContextMenuItem(android.R.id.copy)
        }
        return clipboardText()
    }

    private fun clipboardText(): String {
        val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
        return clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(composeRule.activity)
            ?.toString()
            .orEmpty()
    }

    /**
     * Presses in the middle of a rendered line instead of in the middle of the view, because the
     * center of a note can fall on the blank line between two blocks, where there is no word to
     * select.
     */
    private fun longPressOnRenderedText(): ViewAction = GeneralClickAction(
        Tap.LONG,
        { view ->
            val text = view as TextView
            val layout = text.layout
            val line = layout.lineCount / 2
            val offset = (layout.getLineStart(line) + layout.getLineEnd(line)) / 2
            val location = IntArray(2).also(view::getLocationOnScreen)
            floatArrayOf(
                location[0] + text.totalPaddingLeft + layout.getPrimaryHorizontal(offset),
                location[1] + text.totalPaddingTop +
                    (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
            )
        },
        Press.FINGER,
        InputDevice.SOURCE_UNKNOWN,
        MotionEvent.BUTTON_PRIMARY
    )

    private fun selectionOf(viewId: Int): String {
        var selected = ""
        onView(withId(viewId)).check { view, _ ->
            val text = view as TextView
            if (text.hasSelection()) {
                selected = text.text.substring(text.selectionStart, text.selectionEnd)
            }
        }
        return selected
    }

    private fun screenTopOf(viewId: Int): Int {
        var top = 0
        onView(withId(viewId)).check { view, _ ->
            top = IntArray(2).also(view::getLocationOnScreen)[1]
        }
        return top
    }

    private fun textSizeOf(viewId: Int): Float {
        var size = 0f
        onView(withId(viewId)).check { view, _ -> size = (view as TextView).textSize }
        return size
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTag(
        tag: String
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTagToGo(
        tag: String
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Waits for a node to be on screen rather than merely present. A node appears before the bar,
     * popup, or keyboard change around it has settled, so asserting display straight after an
     * interaction reports a layout that is still moving.
     */
    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitUntilDisplayed(
        tag: String
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().any { node ->
                val bounds = node.boundsInRoot
                bounds.width > 0f && bounds.height > 0f
            }
        }
        onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForText(
        text: String,
        substring: Boolean = false
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Waits for text to go, for lists that are answered by a query rather than filtered in place.
     * Searching starts a new database flow and the previous result stays on screen until that flow
     * emits, so the screen is idle while it still shows what the search is about to replace.
     */
    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTextToGo(
        text: String
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private companion object {
        /** How long `ApplicationComponent` lets a scheduled synchronization wait before it runs. */
        const val SYNC_DELAY_MILLIS = 1_500L
    }
}
