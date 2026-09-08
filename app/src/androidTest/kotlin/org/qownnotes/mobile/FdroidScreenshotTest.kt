package org.qownnotes.mobile

import android.graphics.Bitmap
import android.widget.TextView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.captureToBitmap
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.nextcloud.android.sso.model.SingleSignOnAccount
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.PullResult
import org.qownnotes.mobile.core.RemoteNote

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
        val account =
            SingleSignOnAccount(
                "demo",
                "demo",
                "test-token",
                "https://cloud.example",
                "nextcloud"
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
    }

    private fun capture(name: String) {
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
