package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.parseCustomExpenses
import com.example.ui.components.MerchantLeaderboardCard
import com.example.ui.components.UpcomingBillsCalendarCard
import com.example.ui.tabs.BudgetOverviewCard
import com.example.ui.theme.MartinuFinancialsTheme
import com.example.util.Formatters.fmtCZK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CashFlowInstrumentedTest {

    private val composeTestRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(WakeDeviceRule()).around(composeTestRule)

    @Test
    fun upcomingBills_addCustomBillAndEditRent_onDevice() {
        var latestSettings: SettingsEntity? = null
        val settings = SettingsEntity(rentMonthly = 22_000.0, portuDcaMonthly = 10_000.0)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                UpcomingBillsCalendarCard(
                    transactions = emptyList(),
                    settings = settings,
                    onUpdateSettings = { latestSettings = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("btn_add_upcoming_bill").performClick()
        composeTestRule.onNodeWithTag("custom_bill_editor_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("custom_bill_name_input").performTextReplacement("Internet Fiber")
        composeTestRule.onNodeWithTag("custom_bill_amount_input").performTextReplacement("890")
        composeTestRule.onNodeWithTag("save_custom_bill_button").performClick()

        assertNotNull(latestSettings)
        val saved = parseCustomExpenses(latestSettings!!.customExpensesJson)
        assertEquals(1, saved.size)
        assertEquals("Internet Fiber", saved[0].name)
        assertEquals(890.0, saved[0].amount, 0.01)

        composeTestRule.onAllNodesWithTag("upcoming_bill_item").onFirst().performClick()
        composeTestRule.onNodeWithTag("edit_upcoming_bill_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun merchantLeaderboard_tapRowOpensBudgetEditDialog_onDevice() {
        var updatedSettings: SettingsEntity? = null
        val settings = SettingsEntity(rentMonthly = 25_000.0, groceriesMonthly = 12_000.0)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                MerchantLeaderboardCard(
                    transactions = emptyList(),
                    settings = settings,
                    onUpdateSettings = { updatedSettings = it }
                )
            }
        }

        composeTestRule.onNodeWithText("BUDGET ESTIMATE", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("merchant_leaderboard_item").onFirst().performClick()
        composeTestRule.onNodeWithTag("edit_budget_drift_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun budgetOverview_savingsTotalAndEditDialog_onDevice() {
        val settings = SettingsEntity(
            portuDcaMonthly = 10_000.0,
            emergencyReserveMode = "Target",
            emergencyReserveTarget = 240_000.0,
            emergencyReserveCurrent = 120_000.0
        )
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)
        val expectedTotal = FinancialEngine.monthlySavingsAndInvestmentTotal(settings)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                BudgetOverviewCard(
                    state = state,
                    ledgerEntries = emptyList(),
                    onUpdateSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("btn_edit_investments").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("${fmtCZK(expectedTotal)} monthly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_edit_investments").performClick()
        composeTestRule.onNodeWithTag("edit_investments_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
    }
}
