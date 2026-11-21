package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.components.AppLockOverlay
import com.example.ui.components.EmergencyReserveWidget
import com.example.ui.components.HeroHeader
import com.example.ui.theme.MartinuFinancialsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityInstrumentedTest {

    private val composeTestRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(WakeDeviceRule()).around(composeTestRule)

    @Test
    fun testRealComponentsSemanticsTouchTargetsAndRoles() {
        val defaultState = FinancialEngine.calculate(SettingsEntity(), runMonteCarlo = false)
        var selectedReserveMode = ""
        var navToNetWorth = 0

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    HeroHeader(
                        state = defaultState,
                        onNavigateToNetWorth = { navToNetWorth++ }
                    )
                    EmergencyReserveWidget(
                        state = defaultState,
                        onSelectReserveMode = { selectedReserveMode = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("hero_header_card").assertExists()
        composeTestRule.onNodeWithTag("hero_stat_net_worth").performClick()
        assertTrue(navToNetWorth >= 1)
        composeTestRule.onNodeWithTag("hero_stat_fire_year").assertExists()
        composeTestRule.onNodeWithTag("hero_stat_freedom_goal").assertExists()
        composeTestRule.onNodeWithTag("hero_stat_savings_rate").assertExists()

        val hasTabRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        for (tag in listOf("reserve_chip_3m", "reserve_chip_6m", "reserve_chip_9m", "reserve_chip_12m")) {
            composeTestRule.onNodeWithTag(tag)
                .assertExists()
                .assert(hasTabRole)
                .assertHeightIsAtLeast(48.dp)
        }
        composeTestRule.onNodeWithTag("reserve_chip_6m").assertIsSelected()
        composeTestRule.onNodeWithTag("reserve_chip_9m").performClick()
        assertEquals("9M", selectedReserveMode)
    }

    @Test
    fun testAppLockOverlayUnlockSemanticsAndTouchTarget() {
        var unlockClicks = 0
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                AppLockOverlay(
                    onUnlockClick = { unlockClicks++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("app_lock_overlay").assertExists()
        composeTestRule.onNodeWithTag("unlock_app_button")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, unlockClicks)
    }
}
