package org.qownnotes.mobile

import android.content.ClipboardManager
import android.content.Intent
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.Note
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
    fun categorySelectorDefaultsToUndefinedAndHidesInternalCategories() {
        val account = testAccount("alice")
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
        composeRule.onNodeWithTag("category-option-undefined").assertIsDisplayed()
        composeRule.onNodeWithTag("category-option-all").assertIsDisplayed()
        composeRule.onNodeWithTag("category-option-Work").assertIsDisplayed()
        composeRule.onNodeWithTag("category-option-media").assertDoesNotExist()
        composeRule.onNodeWithTag("category-option-attachments/archive").assertDoesNotExist()

        composeRule.onNodeWithTag("category-option-all").performClick()
        composeRule.waitForText("Work note")
        composeRule.onNodeWithText("Root note").assertIsDisplayed()
        composeRule.onNodeWithText("Media note").assertIsDisplayed()
        listAction("category-selector")

        composeRule.onNodeWithTag("category-option-Work").performClick()

        composeRule.waitForText("Work note")
        composeRule.onNodeWithText("Root note").assertDoesNotExist()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("Work note")
        composeRule.onNodeWithText("Root note").assertDoesNotExist()

        val existingIds = runBlocking { notesOf("alice").map(Note::localId).toSet() }
        listAction("create-note")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                notesOf("alice").any { it.localId !in existingIds && it.category == "Work" }
            }
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

        accountAction("switch-account")
        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Bob note").assertDoesNotExist()

        accountAction("switch-account")
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
        importAccount("alice", "Alice note", "etag-a", 10)
        listAction("settings")
        composeRule.onNodeWithTag("toggle-category").performClick()
        composeRule.onNodeWithTag("close-settings").performClick()
        composeRule.waitForText("Uncategorized")

        importAccount("bob", "Bob note", "etag-b", 20)
        composeRule.onNodeWithText("Uncategorized").assertDoesNotExist()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForText("Bob note")
        composeRule.onNodeWithText("Uncategorized").assertDoesNotExist()
        accountAction("switch-account")
        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Uncategorized").assertIsDisplayed()
    }

    @Test
    fun switchingAmongThreeAccountsLetsTheUserChooseTheAccount() {
        val alice = importAccount("alice", "Alice note", "etag-a", 10)
        importAccount("bob", "Bob note", "etag-b", 20)
        importAccount("charlie", "Charlie note", "etag-c", 30)

        accountAction("switch-account")

        composeRule.onNodeWithTag("account-chooser").assertIsDisplayed()
        composeRule.onNodeWithTag("account-choice-${alice.localAccountId()}").performClick()
        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Bob note").assertDoesNotExist()
        composeRule.onNodeWithText("Charlie note").assertDoesNotExist()
        composeRule.onNodeWithTag("account-chooser").assertDoesNotExist()
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
        composeRule.onNodeWithTag("add-account-icon").fetchSemanticsNode()
        composeRule.onNodeWithTag("manage-accounts-icon").fetchSemanticsNode()
        val addBounds = composeRule.onNodeWithTag("add-account").fetchSemanticsNode().boundsInRoot
        val manageBounds = composeRule.onNodeWithTag(
            "manage-accounts"
        ).fetchSemanticsNode().boundsInRoot
        assertTrue(addBounds.bottom <= manageBounds.top)
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
        composeRule.onNodeWithText("About").assertDoesNotExist()
    }

    @Test
    fun focusedNoteSearchUsesTheAvailableTopBarWidthAndHasABackAction() {
        importAccount("alice", "Existing note", "etag-1", 10)
        val compactWidth = composeRule.onNodeWithTag(
            "note-search"
        ).fetchSemanticsNode().boundsInRoot.width

        composeRule.onNodeWithTag("note-search").performClick()

        composeRule.onNodeWithTag("close-note-search").assertIsDisplayed()
        composeRule.onNodeWithTag("account-menu").assertDoesNotExist()
        composeRule.onNodeWithTag("note-list-menu").assertDoesNotExist()
        val focusedWidth = composeRule.onNodeWithTag(
            "note-search"
        ).fetchSemanticsNode().boundsInRoot.width
        assertTrue("compact=$compactWidth, focused=$focusedWidth", focusedWidth > compactWidth)

        composeRule.onNodeWithTag("note-search").performTextInput("missing")
        composeRule.waitForTextToGo("Existing note")
        composeRule.onNodeWithTag("close-note-search").performClick()

        composeRule.onNodeWithTag("account-menu").assertIsDisplayed()
        composeRule.onNodeWithTag("note-list-menu").assertIsDisplayed()
        composeRule.waitForText("Existing note")
        composeRule.onNodeWithText("Search notes").assertIsDisplayed()
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
        composeRule.onNodeWithTag("account-settings-dialog").assertDoesNotExist()

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
        composeRule.onNodeWithTag("restore-trashed-note").performClick()
        composeRule.onNodeWithTag("confirm-restore-trashed-note").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.restoredTrash == listOf(trashed)
        }
        composeRule.waitForText("No trashed notes were found on the server.")
    }

    @Test
    fun remoteTrashUsesAllCachedFoldersWhileSearchIsActive() {
        val account = importAccount("alice", "Visible note", "etag-1", 10)
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
        composeRule.onNodeWithTag("category-option-all").performClick()
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

        listAction("create-note")
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
            runBlocking {
                application.component.noteRepository
                    .observeNotes(testAccount("alice").localAccountId())
                    .first()
                    .any { it.content.contains("Draft text") }
            }
        }
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
        composeRule.onNodeWithTag("keep-local-conflict-copy").performClick()

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
            runBlocking {
                application.component.noteRepository
                    .observeNotes(testAccount("alice").localAccountId())
                    .first()
                    .any { it.content.contains("Original body") }
            }
        }
        assertTrue(
            runBlocking {
                application.component.noteRepository
                    .observeNotes(testAccount("alice").localAccountId())
                    .first()
                    .none { it.content.contains("Abandoned body") }
            }
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
            runBlocking {
                application.component.noteRepository
                    .observeNotes(testAccount("alice").localAccountId())
                    .first()
                    .any { it.content.contains("typed by hand") }
            }
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

        onView(withId(R.id.markdown_editor))
            .check(matches(withText(containsString("**"))))
            .check(matches(hasFocus()))
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

        onView(withId(R.id.markdown_editor)).perform(replaceText(""))
        composeRule.onNodeWithTag("format-list").performClick()
        awaitEditorText("- ")
        onView(withId(R.id.markdown_editor)).perform(typeText("\n"))
        onView(withId(R.id.markdown_editor)).check(matches(withText("\n")))

        onView(withId(R.id.markdown_editor)).perform(replaceText(""))
        composeRule.onNodeWithTag("format-checkbox-list").performClick()
        awaitEditorText("- [ ] ")
        onView(withId(R.id.markdown_editor)).perform(typeText("\n"))
        onView(withId(R.id.markdown_editor)).check(matches(withText("\n")))
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
        // Undoing must not take the note away from the writer.
        onView(withId(R.id.markdown_editor))
            .check(matches(withText(containsString("Existing note"))))
            .check(matches(hasFocus()))
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
        composeRule.onNodeWithTag("markdown-view").performTouchInput { swipeUp() }

        composeRule.enterEditMode()

        var selection = 0
        onView(withId(R.id.markdown_editor)).check { view, _ ->
            selection = (view as TextView).selectionStart
        }
        assertTrue("expected selection after the start of the note", selection > 0)
        assertTrue("expected selection before the end of the note", selection < content.length)

        // The editor is taller than its scrolling container, so dragging the rail moves the
        // editor itself upwards rather than scrolling text inside a fixed view.
        val before = screenTopOf(R.id.markdown_editor)
        composeRule.onNodeWithTag("editor-fast-scroll").assertIsDisplayed()
            .performTouchInput { swipeDown() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            screenTopOf(R.id.markdown_editor) < before
        }
    }

    @Test
    fun editorScrollsToKeepTheTypingCursorVisible() {
        val content = (1..80).joinToString("\n") { "Line $it" }
        importAccount("alice", "Long note", "etag-1", 10, content)
        composeRule.onNodeWithText("Long note").performClick()
        composeRule.enterEditMode()
        val before = screenTopOf(R.id.markdown_editor)
        lateinit var editor: org.qownnotes.mobile.markdown.MarkdownEditText
        onView(withId(R.id.markdown_editor)).check { view, _ ->
            editor = view as org.qownnotes.mobile.markdown.MarkdownEditText
        }

        onView(withId(R.id.markdown_editor)).check(matches(hasFocus()))
        composeRule.runOnUiThread { editor.setSelection(editor.length()) }
        onView(withId(R.id.markdown_editor)).perform(pressKey(KeyEvent.KEYCODE_X))

        composeRule.waitUntil(timeoutMillis = 10_000) { editor.length() > content.length }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            screenTopOf(R.id.markdown_editor) < before
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
    fun noteViewKeepsFindAndEditVisibleAndSecondaryActionsInTheMenu() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()

        composeRule.waitForTag("edit-note")
        composeRule.onNodeWithTag("find-in-note").assertIsDisplayed()
        composeRule.onNodeWithTag("edit-note").assertIsDisplayed()
        composeRule.onNodeWithTag("rename-note").assertDoesNotExist()

        composeRule.openNoteMenu()
        composeRule.onNodeWithTag("rename-note").assertIsDisplayed()
        composeRule.onNodeWithTag("change-note-category").assertIsDisplayed()
        composeRule.onNodeWithTag("delete-note").assertIsDisplayed()
    }

    @Test
    fun noteCanCreateAndMoveToANewCategory() {
        importAccount("alice", "Existing note", "etag-1", 10)
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

    /** General note-list actions are reached from the overflow menu beside search. */
    private fun listAction(tag: String) {
        composeRule.onNodeWithTag("note-list-menu").performClick()
        composeRule.waitForTag(tag)
        composeRule.onNodeWithTag(tag).performClick()
    }

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

    private suspend fun notesOf(user: String) = application.component.noteRepository
        .observeNotes(testAccount(user).localAccountId())
        .first()

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
        application.component.noteRepository
            .observeNotes(testAccount(user).localAccountId())
            .first()
            .map { it.content }
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
