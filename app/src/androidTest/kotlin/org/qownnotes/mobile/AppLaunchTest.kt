package org.qownnotes.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesNoteListAndCreatesOfflineNote() {
        composeRule.onNodeWithText("QOwnNotes").assertIsDisplayed()
        composeRule.onNodeWithText("+").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodesWithText("# Note ", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        pressBack()
        composeRule.onNodeWithText("QOwnNotes").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }
}
