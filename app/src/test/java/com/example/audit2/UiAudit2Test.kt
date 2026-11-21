package com.example.audit2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.components.EmergencyReserveWidget
import com.example.ui.components.HeroHeader
import com.example.ui.tabs.OverviewTab
import com.example.ui.theme.MartinuFinancialsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiAudit2Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun ok_heroHeader_displaysAccurateNetWorthTotal() {
        val settings = SettingsEntity(
            liquidPortfolioCurrent = 500000.0,
            emergencyReserveCurrent = 200000.0,
            dpsBalanceCurrent = 100000.0,
            dipBalanceCurrent = 50000.0,
            isSingleHousehold = true
        )
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertEquals(850000.0, state.netWorthTotal, 0.01)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                HeroHeader(
                    state = state
                )
            }
        }

        composeTestRule.waitForIdle()
        // Verify HeroHeader renders the test tag
        composeTestRule.onNodeWithTag("hero_header_card").assertExists()
    }

    @Test
    fun ok_emergencyReserveWidget_rendersFundedStatus() {
        val settings = SettingsEntity(
            emergencyReserveCurrent = 300000.0,
            emergencyReserveMode = "Target",
            emergencyReserveTarget = 200000.0
        )
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertTrue(state.isEmergencyReserveFundedToday)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                EmergencyReserveWidget(state = state)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("emergency_reserve_widget").assertExists()
    }

    @Test
    fun ok_singleHousehold_rendersOverviewTab() {
        val settings = SettingsEntity(
            isSingleHousehold = true,
            vSalary = 60000.0
        )
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                OverviewTab(
                    state = state,
                    ledgerEntries = emptyList()
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
    }
}
