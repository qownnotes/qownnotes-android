package org.qownnotes.mobile

import android.view.InputDevice
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.android.sso.model.SingleSignOnAccount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.DeckCardDetails
import org.qownnotes.mobile.core.DeckCardDraft
import org.qownnotes.mobile.core.DeckStackTarget
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote

@RunWith(AndroidJUnit4::class)
class DeckCardUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val application get() = composeRule.activity.application as TestQOwnNotesApplication
    private val card = DeckCardDetails(
        42, 2, 11, "Call Alice", "Report", null, "alice", 7, "plain", false,
        editable = true
    )

    @Before
    fun reset() {
        runBlocking { application.reset() }
        waitForTag("onboarding")
    }

    @Test
    fun linkedCardChooserRemembersPerAccountAndSettingsResetRestoresIt() {
        val account = importLinkedNote()
        tapCardLink()
        waitForTag("deck-opening-dialog")
        composeRule.onNodeWithTag("deck-remember-opening").performClick()
        composeRule.onNodeWithTag("deck-open-in-app").performClick()
        waitForTag("deck-edit-title")
        composeRule.onNodeWithTag("deck-edit-title").assertTextContains("Call Alice")
        composeRule.onNodeWithTag("deck-edit-close").performClick()
        assertEquals(
            DeckLinkOpening.QOWNNOTES,
            application.component.settings.deckLinkOpening(account.localAccountId())
        )
        assertNull(application.component.settings.deckLinkOpening("another-account"))
        tapCardLink()
        waitForTag("deck-edit-title")
        composeRule.onNodeWithTag("deck-opening-dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("deck-edit-close").performClick()
        composeRule.onNodeWithTag("back-to-note-list").performClick()
        application.component.settings.setDeckLinkOpening("another-account", DeckLinkOpening.DECK)
        composeRule.onNodeWithTag("note-list-menu").performClick()
        composeRule.onNodeWithTag("settings").performClick()
        composeRule.onNodeWithTag("reset-deck-opening").performScrollTo().performClick()
        assertNull(application.component.settings.deckLinkOpening(account.localAccountId()))
        assertNull(application.component.settings.deckLinkOpening("another-account"))
        composeRule.onNodeWithTag("close-settings").performClick()
        composeRule.onNodeWithText("Linked note").performClick()
        tapCardLink()
        waitForTag("deck-opening-dialog")
    }

    @Test
    fun cardSaveFailureKeepsInputAndRetryUpdatesOnlyTheCard() {
        val account = importLinkedNote()
        openEditor()
        composeRule.onNodeWithTag("deck-edit-title").performTextReplacement("Edited card")
        composeRule.onNodeWithTag("deck-edit-description").performTextReplacement("New description")
        application.fakeBackend.updateDeckCardFailure = BackendException.Permission()
        composeRule.onNodeWithTag("deck-edit-save").performClick()
        waitForTag("deck-edit-error")
        composeRule.onNodeWithTag("deck-edit-title").assertTextContains("Edited card")
        assertTrue(application.fakeBackend.updatedDeckCards.isEmpty())
        composeRule.onNodeWithTag("deck-edit-save").performClick()
        waitUntilGone("deck-edit-dialog")
        assertEquals(
            listOf(DeckCardDraft("Edited card", "New description", null)),
            application.fakeBackend.updatedDeckCards
        )
        runBlocking {
            val note = application.component.noteRepository.get(
                application.component.noteRepository.observeNotes(
                    account.localAccountId()
                ).first().single().localId
            )!!
            assertEquals(linkMarkdown, note.content)
        }
        tapCardLink()
        waitForTag("deck-opening-dialog") // An unremembered choice is asked again.
    }

    @Test
    fun concurrentCardChangeKeepsDraftUntilConfirmedReload() {
        importLinkedNote()
        openEditor()
        composeRule.onNodeWithTag("deck-edit-title").performTextReplacement("My edit")
        application.fakeBackend.existingDeckCards[42] = card.copy(title = "Server edit")
        composeRule.activityRule.scenario.recreate()
        waitForTag("deck-edit-title")
        composeRule.onNodeWithTag("deck-edit-title").assertTextContains("My edit")
        composeRule.onNodeWithTag("deck-edit-save").performClick()
        waitForTag("deck-edit-error")
        composeRule.onNodeWithTag("deck-edit-title").assertTextContains("My edit")
        composeRule.onNodeWithTag("deck-edit-reload").performScrollTo().performClick()
        composeRule.onNodeWithTag("deck-edit-confirm-reload").performClick()
        waitForTag("deck-edit-title")
        composeRule.onNodeWithTag("deck-edit-title").assertTextContains("Server edit")
        assertTrue(application.fakeBackend.updatedDeckCards.isEmpty())
    }

    @Test
    fun readOnlyCardCanBeViewedButNotSaved() {
        importLinkedNote()
        application.fakeBackend.existingDeckCards[42] = card.copy(editable = false)
        openEditor()
        composeRule.onNodeWithTag("deck-edit-title").assertIsNotEnabled()
        composeRule.onNodeWithTag("deck-edit-save").assertIsNotEnabled()
    }

    @Test
    fun missingCardReportsACardSpecificErrorAndCanRetry() {
        importLinkedNote()
        application.fakeBackend.loadDeckCardFailure = BackendException.RemoteMissing()
        tapCardLink()
        composeRule.onNodeWithTag("deck-open-in-app").performClick()
        waitForTag("deck-edit-load-error")
        composeRule.onNodeWithTag(
            "deck-edit-load-error"
        ).assertTextContains("This card no longer exists", substring = true)
        composeRule.onNodeWithTag("deck-edit-retry").performClick()
        waitForTag("deck-edit-title")
    }

    @Test
    fun preferencesPersistAndAccountRemovalDoesNotClearAnotherAccount() {
        val context = composeRule.activity
        val settings = AppSettings(context, "deck-opening-test")
        settings.resetDeckLinkOpening()
        settings.setNextcloudDeckTarget("alice", DeckStackTarget(2, 11))
        settings.setDeckLinkOpening("alice", DeckLinkOpening.DECK)
        settings.setDeckLinkOpening("bob", DeckLinkOpening.QOWNNOTES)
        val recreated = AppSettings(context, "deck-opening-test")
        assertEquals(DeckLinkOpening.DECK, recreated.deckLinkOpening("alice"))
        recreated.removeNextcloudDeck("alice")
        assertNull(recreated.deckLinkOpening("alice"))
        assertEquals(DeckLinkOpening.QOWNNOTES, recreated.deckLinkOpening("bob"))
        recreated.setNextcloudDeckTarget("bob", DeckStackTarget(3, 31))
        recreated.resetDeckLinkOpening()
        assertEquals(DeckStackTarget(3, 31), recreated.nextcloudDeckTarget("bob"))
    }

    private fun importLinkedNote(): SingleSignOnAccount {
        val account =
            SingleSignOnAccount(
                "alice",
                "alice",
                "test-token",
                "https://cloud.example",
                "nextcloud"
            )
        application.fakeBackend.existingDeckCards[42] = card
        application.fakeAccountImporter.enqueue(account)
        application.fakeBackend.enqueue(
            account,
            PullResult(
                listOf(RemoteNote(100, "etag-1", "Linked note", linkMarkdown, "", 10)),
                "etag-1",
                10
            )
        )
        composeRule.onNodeWithTag("add-account").performClick()
        waitForTag("note-list-menu")
        composeRule.onNodeWithText("Linked note").performClick()
        waitForTag("edit-note")
        return account
    }

    private fun openEditor() {
        tapCardLink()
        waitForTag("deck-open-in-app")
        composeRule.onNodeWithTag("deck-open-in-app").performClick()
        waitForTag("deck-edit-title")
    }

    private fun tapCardLink() {
        // Dialog semantics disappear before the platform finishes removing its touch-blocking
        // window. Let its exit animation finish before injecting a new tap into the note.
        composeRule.waitForIdle()
        android.os.SystemClock.sleep(500)
        composeRule.waitUntil(10_000) {
            composeRule.runOnIdle { composeRule.activity.hasWindowFocus() }
        }
        composeRule.waitUntil(10_000) {
            composeRule.runOnIdle {
                composeRule.activity.findViewById<TextView>(
                    R.id.markdown_view
                )?.text?.contains("Call Alice") ==
                    true
            }
        }
        onView(withId(R.id.markdown_view)).check { view, _ ->
            val text = view as TextView
            assertTrue(
                "Unexpected selection ${text.selectionStart}..${text.selectionEnd}",
                !text.hasSelection()
            )
            assertTrue(
                "Unexpected movement method ${text.movementMethod}",
                text.movementMethod.javaClass.simpleName == "SelectableLinkMovementMethod"
            )
        }
        onView(withId(R.id.markdown_view)).perform(
            GeneralClickAction(Tap.SINGLE, { view ->
                val text = view as TextView
                val layout = text.layout
                val location = IntArray(2).also(view::getLocationOnScreen)
                floatArrayOf(
                    location[0] + text.totalPaddingLeft + layout.getPrimaryHorizontal(3),
                    location[1] + text.totalPaddingTop +
                        (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
                )
            }, Press.FINGER, InputDevice.SOURCE_UNKNOWN, MotionEvent.BUTTON_PRIMARY)
        )
    }

    private fun waitForTag(tag: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(tag: String) = composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
    }

    private val linkMarkdown = "[Call Alice](https://cloud.example/apps/deck/#/board/2/card/42)"
}
