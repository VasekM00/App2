package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenNavigationInstrumentedTest {

    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(WakeDeviceRule()).around(composeTestRule)

    @Test
    fun mainScreen_bottomTabsSearchAndQuickGuide_navigateCleanly() {
        composeTestRule.waitForIdle()

        // Home tab surfaces both Hero Quick Guide pill and top-bar '?' action
        composeTestRule.onNodeWithTag("hero_header_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hero_quick_guide_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("action_quick_guide").assertIsDisplayed()
        composeTestRule.onNodeWithTag("action_quick_guide").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quick_guide_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("guide_btn_close").performClick()
        composeTestRule.waitForIdle()

        // Switch to Budget (CashFlow) -> top-bar Quick Guide '?' reappears
        composeTestRule.onNodeWithTag("nav_cashflow").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("action_quick_guide").assertIsDisplayed()

        // Switch to Wealth (Projections) -> open Goals subtab (index 4)
        composeTestRule.onNodeWithTag("nav_projections").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("projections_subtab_4").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Goals").assertIsDisplayed()

        // Switch to More (Settings)
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("action_quick_guide").assertIsDisplayed()

        // Expand global Search, filter results, and navigate back to Home Emergency Reserve
        composeTestRule.onNodeWithTag("action_toggle_search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_input_field").performTextInput("Emergency")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Emergency Cash Reserve & Runway").assertIsDisplayed()
        composeTestRule.onNodeWithText("Emergency Cash Reserve & Runway").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("emergency_reserve_widget").assertExists()
    }
}
