package org.qownnotes.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote

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
        composeRule.onNodeWithTag("add-account").performClick()

        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("alice @ cloud.example").assertIsDisplayed()
        composeRule.onNodeWithText("Alice note").assertIsDisplayed()
    }

    @Test
    fun showsAccountImportFailureOnOnboarding() {
        application.fakeAccountImporter.enqueueFailure(IllegalStateException("Import unavailable"))

        composeRule.onNodeWithTag("add-account").performClick()

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
        composeRule.onNodeWithTag("add-account").performClick()
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
        composeRule.onNodeWithText("Refresh").performClick()
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
        composeRule.onNodeWithText("Refresh").performClick()
        composeRule.waitForText("Reconnect")

        application.fakeAccountImporter.enqueue(testAccount("bob"))
        composeRule.onNodeWithText("Reconnect").performClick()

        composeRule.waitForText("Select the same Nextcloud account to reconnect")
        composeRule.onNodeWithText("Cached note").assertIsDisplayed()
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

        composeRule.onNodeWithTag("add-account").performClick()

        composeRule.waitForText("A different Nextcloud account already uses this local identity")
        composeRule.onNodeWithText("Alice note").assertIsDisplayed()
    }

    @Test
    fun switchingAndRemovingAccountsKeepsDataAccountScoped() {
        importAccount("alice", "Alice note", "etag-a", 10)
        val bob = testAccount("bob")
        application.fakeAccountImporter.enqueue(bob)
        application.fakeBackend.enqueue(bob, pull("Bob note", "etag-b", 20))
        composeRule.onNodeWithTag("add-account").performClick()
        composeRule.waitForText("Switch")
        composeRule.onNodeWithTag("switch-account").performClick()
        composeRule.waitForText("Bob note")
        composeRule.onNodeWithText("Alice note").assertDoesNotExist()

        composeRule.onNodeWithTag("remove-account").performClick()
        composeRule.onNodeWithText("server notes will not be deleted", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-remove-account").performClick()
        composeRule.waitForText("Alice note")
        composeRule.onNodeWithText("Bob note").assertDoesNotExist()

        composeRule.onNodeWithTag("remove-account").performClick()
        composeRule.onNodeWithTag("confirm-remove-account").performClick()
        composeRule.waitForText("Your Nextcloud notes, offline")
    }

    @Test
    fun createsAndEditsANoteOfflineFirst() {
        importAccount("alice", "Existing note", "etag-1", 10)

        composeRule.onNodeWithTag("create-note").performClick()
        composeRule.waitUntil {
            composeRule.onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("edit-note").performClick()
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
        composeRule.onNodeWithTag("add-account").performClick()
        composeRule.waitForText("Shared note")

        composeRule.onNodeWithText("Shared note").performClick()

        composeRule.waitForText("Read only")
        composeRule.onNodeWithTag("edit-note").assertDoesNotExist()
    }

    @Test
    fun editorDraftSurvivesActivityRecreation() {
        importAccount("alice", "Existing note", "etag-1", 10)
        composeRule.onNodeWithText("Existing note").performClick()
        composeRule.onNodeWithTag("edit-note").performClick()
        onView(withId(R.id.markdown_editor)).perform(
            click(),
            replaceText("# Edited\n\nUnsaved draft")
        )

        composeRule.activityRule.scenario.recreate()

        onView(withId(R.id.markdown_editor))
            .check(matches(withText(containsString("Unsaved draft"))))
    }

    private fun importAccount(
        user: String,
        title: String,
        etag: String,
        modified: Long
    ): SingleSignOnAccount {
        val account = testAccount(user)
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(account, pull(title, etag, modified))
        composeRule.onNodeWithTag("add-account").performClick()
        composeRule.waitForText(title)
        return account
    }

    private fun testAccount(user: String) =
        SingleSignOnAccount(user, user, "test-token", "https://cloud.example", "nextcloud")

    private fun pull(title: String, etag: String, modified: Long) = PullResult(
        notes = listOf(RemoteNote(42, etag, title, "# $title", "", modified)),
        collectionEtag = etag,
        lastModifiedEpochSeconds = modified
    )

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForText(
        text: String
    ) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
