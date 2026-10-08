package org.qownnotes.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.qownnotes.mobile.markdown.FetchedLink

class InsertLinkDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun prefilledUrlGetsPageTitleAndInsertsEscapedMarkdown() {
        var inserted = ""
        showDialog(onInsert = { inserted = it }) {
            FetchedLink(it, "Example [page]")
        }

        composeRule.onNodeWithTag("link-url").assertTextContains("https://example.com/page")
        awaitTitle("Example [page]")
        composeRule.onNodeWithTag("insert-link-confirm").performClick()
        assertEquals("[Example \\[page\\]](https://example.com/page)", inserted)
    }

    @Test
    fun lookupFailureAllowsManualInsertionAndRetry() {
        var fail = true
        var inserted = ""
        showDialog(onInsert = { inserted = it }) {
            if (fail) throw java.io.IOException("offline")
            FetchedLink(it, "Online title")
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("link-title-error").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("insert-link-confirm").assertIsEnabled().performClick()
        assertEquals("[https://example.com/page](https://example.com/page)", inserted)

        fail = false
        composeRule.onNodeWithTag("link-fetch-title").performClick()
        awaitTitle("Online title")
    }

    @Test
    fun lateTitleDoesNotOverwriteManualLabel() {
        val result = CompletableDeferred<FetchedLink>()
        var requested = false
        showDialog {
            requested = true
            result.await()
        }
        composeRule.waitUntil(10_000) { requested }
        composeRule.onNodeWithTag("link-text").performTextReplacement("My label")
        result.complete(FetchedLink("https://example.com/page", "Late title"))
        awaitTitle("My label")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("link-text").assertTextContains("My label")
    }

    @Test
    fun changedUrlDiscardsOldTitleAndInvalidUrlsCannotBeInserted() {
        val result = CompletableDeferred<FetchedLink>()
        var requested = false
        showDialog {
            requested = true
            result.await()
        }
        composeRule.waitUntil(10_000) { requested }
        composeRule.onNodeWithTag("link-url").performTextReplacement("javascript:alert(1)")
        result.complete(FetchedLink("https://example.com/page", "Old title"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("insert-link-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("link-text").assert(hasText("Old title").not())
    }

    @Test
    fun selectedLabelIsPreservedUntilExplicitTitleRefresh() {
        var requests = 0
        showDialog(initialTitle = "Selected text") {
            requests++
            FetchedLink(it, "Fetched title")
        }
        composeRule.waitForIdle()
        assertEquals(0, requests)
        composeRule.onNodeWithTag("link-text").assertTextContains("Selected text")
        composeRule.onNodeWithTag("link-fetch-title").performClick()
        awaitTitle("Fetched title")
        assertEquals(1, requests)
    }

    private fun showDialog(
        initialTitle: String = "",
        onInsert: (String) -> Unit = {},
        fetchTitle: suspend (String) -> FetchedLink
    ) {
        composeRule.setContent {
            MaterialTheme {
                InsertLinkDialog(
                    initialUrl = "https://example.com/page",
                    initialTitle = initialTitle,
                    onDismiss = {},
                    onInsert = onInsert,
                    fetchTitle = fetchTitle
                )
            }
        }
    }

    private fun awaitTitle(title: String) {
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag("link-text").assertTextContains(title)
            }.isSuccess
        }
    }
}
