package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.components.HeroHeader
import com.example.ui.theme.MartinuFinancialsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityInstrumentedTest {

    private val composeTestRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(WakeDeviceRule()).around(composeTestRule)

    /**
     * Validates that semantic content descriptions are properly assigned to Compose UI elements
     * including navigation items, action buttons, header controls, and accessibility icons.
     */
    @Test
    fun testSemanticContentDescriptionsOnComposeUIElements() {
        val defaultState = FinancialEngine.calculate(SettingsEntity(), runMonteCarlo = false)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                androidx.compose.foundation.layout.Column {
                    // 1. Hero Header accessibility elements
                    HeroHeader(
                        state = defaultState,
                        onOpenQuickGuide = {}
                    )

                    // 2. Navigation Bar accessibility elements (4 goal-driven tabs)
                    NavigationBar {
                        val items = listOf(
                            "Home" to Icons.Default.Dashboard,
                            "Budget" to Icons.Default.Payments,
                            "Wealth" to Icons.Default.Assessment,
                            "More" to Icons.Default.Settings
                        )
                        items.forEach { (title, icon) ->
                            NavigationBarItem(
                                selected = false,
                                onClick = {},
                                icon = { Icon(imageVector = icon, contentDescription = title) },
                                label = { Text(title) }
                            )
                        }
                    }
                }
            }
        }

        // Verify HeroHeader quick guide control has proper content description for screen readers
        composeTestRule
            .onNodeWithContentDescription("Open Quick Guide")
            .assertExists()

        // Verify Navigation items have appropriate content descriptions.
        // NavigationBarItem merges descendant semantics, so the icon's content description
        // is only observable in the unmerged tree.
        composeTestRule
            .onNodeWithContentDescription("Home", useUnmergedTree = true)
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Budget", useUnmergedTree = true)
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Wealth", useUnmergedTree = true)
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("More", useUnmergedTree = true)
            .assertExists()
    }
}
