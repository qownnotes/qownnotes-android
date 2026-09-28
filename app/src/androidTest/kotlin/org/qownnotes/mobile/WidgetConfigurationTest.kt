package org.qownnotes.mobile

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.NoteCategoryScope

@RunWith(AndroidJUnit4::class)
class WidgetConfigurationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun categoryChoicesOfferAllUncategorizedAndEachCategory() {
        val chosen = mutableListOf<NoteCategoryScope>()
        composeRule.setContent {
            LazyColumn { widgetCategoryChoices(listOf("Personal", "Work/Projects"), chosen::add) }
        }

        composeRule.onNodeWithText("All categories").assertIsDisplayed()
        composeRule.onNodeWithText("Uncategorized").assertIsDisplayed()
        composeRule.onNodeWithTag("widget-category-all").performClick()
        composeRule.onNodeWithTag("widget-category-undefined").performClick()
        composeRule.onNodeWithTag("widget-category-Work/Projects").performClick()

        assertEquals(
            listOf(
                NoteCategoryScope.All,
                NoteCategoryScope.Undefined,
                NoteCategoryScope.Category("Work/Projects")
            ),
            chosen
        )
    }
}
