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
import org.qownnotes.mobile.core.SyncState
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
        composeRule.onNodeWithText("alice @ cloud.example").assertIsDisplayed()
        composeRule.onNodeWithText("Alice note").assertIsDisplayed()
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
            BackendException.Retryable(IOException("offline"))
        )

        composeRule.activityRule.scenario.recreate()

        composeRule.waitForText("The server could not be reached")
        composeRule.onNodeWithText("Cached note").assertIsDisplayed()
    }

    @Test
    fun reconnectPreservesCachedDataAndCheckpoint() {
        val account = importAccount("alice", "Cached note", "etag-1", 10)
        application.fakeBackend.enqueueFailure(account, BackendException.Authentication())
        composeRule.activityRule.scenario.recreate()
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
        composeRule.activityRule.scenario.recreate()
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
        importAccount("alice", "Alice note", "etag-a", 10)
        val bob = testAccount("bob")
        application.fakeAccountImporter.enqueue(bob)
        application.fakeBackend.enqueue(bob, pull("Bob note", "etag-b", 20))
        accountAction("add-account")
        // Switching only becomes possible once the second account has finished being imported,
        // which is what opening the menu waits for.
        accountAction("switch-account")
        composeRule.waitForText("Bob note")
        composeRule.onNodeWithText("Alice note").assertDoesNotExist()

        accountAction("remove-account")
        composeRule.onNodeWithText("server notes will not be deleted", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-remove-account").performClick()
        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Bob note").assertDoesNotExist()

        accountAction("remove-account")
        composeRule.onNodeWithTag("confirm-remove-account").performClick()
        composeRule.waitForText("Your Nextcloud notes, offline")
    }

    /** An action has to say what it acts on: a note is made here, the others change the account. */
    @Test
    fun theNoteListNamesWhatItsActionsActOn() {
        importAccount("alice", "Existing note", "etag-1", 10)

        composeRule.onNodeWithText("New note").assertIsDisplayed()

        composeRule.onNodeWithTag("account-menu").performClick()
        composeRule.waitForText("Add account")
        composeRule.onNodeWithText("Add account").assertIsDisplayed()
        composeRule.onNodeWithText("Remove account").assertIsDisplayed()
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
        composeRule.onNodeWithText("First note").assertDoesNotExist()
        composeRule.onNodeWithText("Second note").assertDoesNotExist()
    }

    @Test
    fun noteViewMovesTheNoteToTrashAfterConfirmation() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.waitForTag("delete-note")

        composeRule.onNodeWithTag("delete-note").performClick()
        composeRule.onNodeWithText("Move note to trash?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-delete-note").performClick()

        composeRule.waitForTag("note-list")
        composeRule.onNodeWithText("Existing note").assertDoesNotExist()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.deletedRemoteIds == listOf(42L)
        }
    }

    @Test
    fun createsAndEditsANoteOfflineFirst() {
        importAccount("alice", "Existing note", "etag-1", 10)

        composeRule.onNodeWithTag("create-note").performClick()
        composeRule.waitForTag("markdown-editor")
        composeRule.onNodeWithTag("finish-editing").assertIsDisplayed()
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
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()

        composeRule.onNodeWithTag("cancel-editing").performClick()

        composeRule.waitForTag("markdown-view")
        composeRule.onNodeWithTag("confirm-discard-changes").assertDoesNotExist()
        composeRule.onNodeWithTag("edit-note").assertIsDisplayed()
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
    }

    @Test
    fun toolbarFormattingKeepsInputFocusInTheEditor() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.enterEditMode()

        onView(withId(R.id.markdown_editor)).perform(click(), typeText("bold me"))
        // Format the fully typed text, not whatever part of it the input method has committed.
        awaitEditorText("bold me")
        composeRule.onNodeWithText("B").performClick()

        onView(withId(R.id.markdown_editor))
            .check(matches(withText(containsString("**"))))
            .check(matches(hasFocus()))
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

        // Clicking past the smallest step must saturate rather than shrink without bound.
        repeat(NoteTextSize.steps.size + 2) {
            composeRule.onNodeWithTag("decrease-note-text-size").performClick()
        }

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
        composeRule.onNodeWithTag("rename-note").assertDoesNotExist()
    }

    /** The name of a note is the name of the file holding it, so a rename has to be uploaded. */
    @Test
    fun renamingANoteShowsAndUploadsTheNewFileName() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()

        // Opening a note loads it from the repository, so its actions appear a recomposition later.
        composeRule.waitForTag("rename-note")
        composeRule.onNodeWithTag("rename-note").performClick()
        composeRule.waitForTag("note-name-field")
        composeRule.onNodeWithTag("note-name-field").performTextReplacement("Grocery list")
        composeRule.onNodeWithTag("confirm-rename-note").performClick()

        composeRule.waitForText("Grocery list")
        composeRule.onNodeWithTag("back-to-note-list").performClick()
        composeRule.waitForText("Grocery list")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            application.fakeBackend.pushedNotes.any { it.title == "Grocery list" }
        }
        assertTrue(
            runBlocking {
                application.component.noteRepository
                    .observeNotes(testAccount("alice").localAccountId())
                    .first()
                    .all { it.content == "# Existing note" }
            }
        )
    }

    /** A name that holds nothing a file system accepts would leave the note unreachable. */
    @Test
    fun renamingRefusesANameThatNoFileCanCarry() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()

        composeRule.waitForTag("rename-note")
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
        text: String
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
