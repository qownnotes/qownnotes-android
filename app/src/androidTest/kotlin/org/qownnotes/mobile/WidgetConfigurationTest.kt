package org.qownnotes.mobile

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

        composeRule.onNodeWithText("All subfolders").assertIsDisplayed()
        composeRule.onNodeWithText("Root folder").assertIsDisplayed()
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

    @Test
    fun appearanceStepEditsColorsAndFrameThenSaves() {
        var appearance by mutableStateOf(WidgetAppearance.DEFAULT)
        var saved: WidgetAppearance? = null
        composeRule.setContent {
            WidgetAppearanceStep(
                appearance = appearance,
                singleNote = false,
                onChange = { appearance = it },
                onSave = { saved = appearance }
            )
        }

        composeRule.onNodeWithTag("widget-background-default").assertIsSelected()
        composeRule.onNodeWithTag("widget-background-blue").performScrollTo().performClick()
        composeRule.onNodeWithTag("widget-background-blue").assertIsSelected()
        composeRule.onNodeWithTag("widget-row-light-green").performScrollTo().performClick()
        composeRule.onNodeWithTag("widget-frame-color-default").assertDoesNotExist()
        composeRule.onNodeWithTag("widget-frame").performScrollTo().performClick()
        composeRule.onNodeWithTag("widget-frame-color-black").performScrollTo().performClick()
        composeRule.onNodeWithTag("widget-appearance-save").performClick()

        assertEquals(
            WidgetAppearance(
                background = 0xFF1565C0.toInt(),
                row = 0xFFDCEDC8.toInt(),
                frame = true,
                frameColor = 0xFF000000.toInt()
            ),
            saved
        )

        composeRule.onNodeWithTag("widget-appearance-reset").performClick()
        assertEquals(WidgetAppearance.DEFAULT, appearance)
    }

    @Test
    fun singleNoteAppearanceHasNoRowColors() {
        composeRule.setContent {
            WidgetAppearanceStep(WidgetAppearance.DEFAULT, singleNote = true, {}, {})
        }

        composeRule.onNodeWithTag("widget-background-default").assertExists()
        composeRule.onNodeWithTag("widget-row-default").assertDoesNotExist()
    }
}
