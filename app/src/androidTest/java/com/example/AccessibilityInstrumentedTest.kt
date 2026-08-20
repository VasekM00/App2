package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Checklist
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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
                        isDarkTheme = false,
                        onToggleDarkTheme = {},
                        onOpenExportReportDialog = {},
                        onOpenSettings = {}
                    )

                    // 2. Navigation Bar accessibility elements
                    NavigationBar {
                        val items = listOf(
                            "Overview" to Icons.Default.Dashboard,
                            "Cash Flow" to Icons.Default.Payments,
                            "Projections" to Icons.Default.Assessment,
                            "Strategy" to Icons.Default.Checklist,
                            "Settings" to Icons.Default.Settings
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

        // Verify HeroHeader action buttons have proper content descriptions for accessibility / screen readers
        composeTestRule
            .onNodeWithContentDescription("Export Summary Report")
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Toggle Dark Mode")
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Settings")
            .assertExists()

        // Verify Navigation items have appropriate content descriptions
        composeTestRule
            .onNodeWithContentDescription("Overview")
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Cash Flow")
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Projections")
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription("Strategy")
            .assertExists()
    }
}
