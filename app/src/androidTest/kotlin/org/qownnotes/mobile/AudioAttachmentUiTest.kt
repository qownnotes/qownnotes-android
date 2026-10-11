package org.qownnotes.mobile

import android.view.InputDevice
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.android.sso.model.SingleSignOnAccount
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote

@RunWith(AndroidJUnit4::class)
class AudioAttachmentUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val application get() = composeRule.activity.application as TestQOwnNotesApplication

    @Before
    fun reset() {
        runBlocking { application.reset() }
        waitForTag("onboarding")
    }

    @Test
    fun audioLinkChooserRemembersInternalPlaybackUntilSettingsReset() {
        importAudioNote()
        tapAudioLink()
        waitForTag("audio-opening-dialog")
        composeRule.onNodeWithTag("audio-remember-opening").performClick()
        composeRule.onNodeWithTag("audio-open-internal").performClick()
        waitForTag("audio-player-dialog")
        assertEquals(AudioLinkOpening.INTERNAL, application.component.settings.audioLinkOpening())
        composeRule.onNodeWithTag("audio-player-close").performClick()
        waitUntilGone("audio-player-dialog")

        tapAudioLink()
        waitForTag("audio-player-dialog")
        composeRule.onNodeWithTag("audio-opening-dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("audio-player-close").performClick()
        waitUntilGone("audio-player-dialog")

        composeRule.onNodeWithTag("back-to-note-list").performClick()
        composeRule.onNodeWithTag("note-list-menu").performClick()
        composeRule.onNodeWithTag("settings").performClick()
        composeRule.onNodeWithTag("reset-audio-opening").performScrollTo().performClick()
        assertNull(application.component.settings.audioLinkOpening())
        composeRule.onNodeWithTag("close-settings").performClick()
        composeRule.onNodeWithText("Audio note").performClick()
        waitForTag("edit-note")
        tapAudioLink()
        waitForTag("audio-opening-dialog")
    }

    @Test
    fun unrememberedChoiceIsAskedAgainAndPreferencePersists() {
        importAudioNote()
        tapAudioLink()
        waitForTag("audio-opening-dialog")
        composeRule.onNodeWithTag("audio-open-internal").performClick()
        waitForTag("audio-player-dialog")
        assertNull(application.component.settings.audioLinkOpening())
        composeRule.onNodeWithTag("audio-player-close").performClick()
        waitUntilGone("audio-player-dialog")
        tapAudioLink()
        waitForTag("audio-opening-dialog")

        val settings = AppSettings(composeRule.activity, "audio-opening-test")
        settings.setAudioLinkOpening(AudioLinkOpening.EXTERNAL)
        val recreated = AppSettings(composeRule.activity, "audio-opening-test")
        assertEquals(AudioLinkOpening.EXTERNAL, recreated.audioLinkOpening())
        recreated.resetAudioLinkOpening()
        assertNull(AppSettings(composeRule.activity, "audio-opening-test").audioLinkOpening())
    }

    @Test
    fun audioAttachmentsAreRecognizedByExtension() {
        assertTrue(isAudioAttachment("../attachments/audio-1.m4a"))
        assertTrue(isAudioAttachment("attachments/Song.MP3"))
        assertTrue(isAudioAttachment("attachments/voice.opus"))
        assertFalse(isAudioAttachment("attachments/report.pdf"))
        assertFalse(isAudioAttachment("attachments/m4a"))
    }

    private fun importAudioNote() {
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
                listOf(RemoteNote(100, "etag-1", "Audio note", markdown, "", 10)),
                "etag-1",
                10
            )
        )
        composeRule.onNodeWithTag("add-account").performClick()
        waitForTag("note-list-menu")
        composeRule.onNodeWithText("Audio note").performClick()
        waitForTag("edit-note")
    }

    private fun tapAudioLink() {
        // Let a closing dialog's touch-blocking window go away before tapping the note.
        composeRule.waitForIdle()
        android.os.SystemClock.sleep(500)
        composeRule.waitUntil(10_000) {
            composeRule.runOnIdle { composeRule.activity.hasWindowFocus() }
        }
        composeRule.waitUntil(10_000) {
            composeRule.runOnIdle {
                composeRule.activity.findViewById<TextView>(R.id.markdown_view)
                    ?.text?.contains("Recording") == true
            }
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

    private val markdown = "[Recording](attachments/audio-1.m4a)"
}
