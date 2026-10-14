package org.qownnotes.mobile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.android.sso.model.SingleSignOnAccount
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.NoteSharingPolicy
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote

@RunWith(AndroidJUnit4::class)
class NoteShareUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val application get() = composeRule.activity.application as TestQOwnNotesApplication

    @Before
    fun reset() {
        runBlocking { application.reset() }
        waitForTag("onboarding")
    }

    @Test
    fun createsAPasswordProtectedLinkAndManagesIt() {
        openShareDialog()
        waitForTag("note-share-create")
        composeRule.onNodeWithTag("note-share-protect").assertIsOff().performClick()
        composeRule.onNodeWithTag("note-share-create").assertIsNotEnabled()
        composeRule.onNodeWithTag("note-share-password").performTextInput("secret")
        composeRule.onNodeWithTag("note-share-create").performClick()

        waitForTag("note-share-url")
        composeRule.onNodeWithTag(
            "note-share-url"
        ).assertTextContains("https://cloud.example/s/share40")
        assertEquals("secret", application.fakeBackend.sharePasswords["40"])
        composeRule.onNodeWithTag("note-share-password-protection").assertIsOn()

        composeRule.onNodeWithTag("note-share-allow-editing").assertIsOff().performClick()
        composeRule.waitUntil(10_000) {
            application.fakeBackend.noteShares[100]?.editAllowed == true
        }
        composeRule.onNodeWithTag("note-share-allow-editing").assertIsOn()

        composeRule.onNodeWithTag("note-share-password-protection").performClick()
        composeRule.waitUntil(10_000) {
            application.fakeBackend.noteShares[100]?.passwordProtected == false
        }
        assertEquals(null, application.fakeBackend.sharePasswords["40"])

        composeRule.onNodeWithTag("note-share-stop").performClick()
        composeRule.onNodeWithTag("note-share-confirm-stop").performClick()
        waitForTag("note-share-create")
        assertTrue(application.fakeBackend.noteShares.isEmpty())
    }

    @Test
    fun anExistingLinkIsShownWhenTheDialogOpens() {
        application.fakeBackend.noteShares[100] =
            org.qownnotes.mobile.core.NoteShare("7", "https://cloud.example/s/old", 19, true)
        openShareDialog()
        waitForTag("note-share-url")
        composeRule.onNodeWithTag(
            "note-share-url"
        ).assertTextContains("https://cloud.example/s/old")
        composeRule.onNodeWithTag("note-share-allow-editing").assertIsOn()
        composeRule.onNodeWithTag("note-share-change-password").assertIsEnabled()
    }

    @Test
    fun enforcedPasswordsRequireAPasswordBeforeCreating() {
        application.fakeBackend.sharingPolicy =
            NoteSharingPolicy(true, passwordEnforced = true, passwordMinLength = 8)
        openShareDialog()
        waitForTag("note-share-create")
        composeRule.onNodeWithTag("note-share-protect").assertIsOn().assertIsNotEnabled()
        composeRule.onNodeWithTag("note-share-password").performTextInput("short")
        composeRule.onNodeWithTag("note-share-create").assertIsNotEnabled()
        composeRule.onNodeWithTag("note-share-generate-password").performClick()
        composeRule.onNodeWithTag("note-share-create").assertIsEnabled().performClick()
        waitForTag("note-share-url")
        assertTrue(application.fakeBackend.sharePasswords["40"]!!.length >= 16)
    }

    @Test
    fun aRejectedLinkOffersThePasswordField() {
        application.fakeBackend.createShareFailure =
            BackendException.Rejected("Passwords are enforced for link and mail shares")
        openShareDialog()
        waitForTag("note-share-create")
        composeRule.onNodeWithTag("note-share-create").performClick()
        waitForTag("note-share-error")
        composeRule.onNodeWithTag("note-share-error")
            .assertTextContains("Passwords are enforced", substring = true)
        composeRule.onNodeWithTag("note-share-protect").assertIsOn()
        composeRule.onNodeWithTag("note-share-password").performScrollTo()
        assertTrue(application.fakeBackend.noteShares.isEmpty())
    }

    @Test
    fun disabledLinkSharingExplainsWhy() {
        application.fakeBackend.sharingPolicy = NoteSharingPolicy(linkSharingEnabled = false)
        openShareDialog()
        waitForTag("note-share-disabled")
        composeRule.onNodeWithTag("note-share-create").assertDoesNotExist()
        composeRule.onNodeWithTag("note-share-done").performClick()
        waitUntilGone("note-share-dialog")
    }

    private fun openShareDialog() {
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
                listOf(RemoteNote(100, "etag-1", "Shared note", "Text", "", 10)),
                "etag-1",
                10
            )
        )
        composeRule.onNodeWithTag("add-account").performClick()
        waitForTag("note-list-menu")
        composeRule.onNodeWithText("Shared note").performClick()
        waitForTag("note-menu")
        composeRule.onNodeWithTag("note-menu").performClick()
        composeRule.onNodeWithTag("share-note").performClick()
        waitForTag("note-share-dialog")
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(tag: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }
}
