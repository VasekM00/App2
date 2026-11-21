package com.example.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.CustomExpenseItem
import com.example.domain.FinancialEngine
import com.example.domain.serializeCustomExpenses
import com.example.ui.components.EditInvestmentAllocationsDialog
import com.example.ui.components.MerchantLeaderboardCard
import com.example.ui.components.UpcomingBillsCalendarCard
import com.example.ui.tabs.BudgetOverviewCard
import com.example.ui.theme.MartinuFinancialsTheme
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class CashFlowCardsInteractionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun upcomingBillsCalendar_displaysBudgetAndCustomBillsWithAddControl() {
        val settings = SettingsEntity(
            rentMonthly = 22_000.0,
            portuDcaMonthly = 10_000.0,
            customExpensesJson = serializeCustomExpenses(
                listOf(CustomExpenseItem(id = "c1", name = "Internet Fiber", amount = 890.0, dayOfMonth = 15))
            )
        )

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                UpcomingBillsCalendarCard(
                    transactions = emptyList(),
                    settings = settings,
                    onUpdateSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Upcoming Bills").assertIsDisplayed()
        composeTestRule.onNodeWithText("30-day bills", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_add_upcoming_bill")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText("Internet Fiber", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("upcoming_bill_item").onFirst().assertIsDisplayed()
    }

    @Test
    fun merchantLeaderboard_budgetModeUsesAuthenticCategoriesAndNoFakeMerchants() {
        val settings = SettingsEntity(
            rentMonthly = 25_000.0,
            groceriesMonthly = 12_000.0,
            cafesMonthly = 5_000.0
        )

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                MerchantLeaderboardCard(
                    transactions = emptyList(),
                    settings = settings,
                    onUpdateSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithText("BUDGET ESTIMATE", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Planned", useUnmergedTree = true).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Housing & Rent", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Groceries & Supermarket", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Rohlik.cz / Kosik.cz", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun merchantLeaderboard_statementModeShowsRealDrift() {
        val txs = listOf(
            ImportedBankTransactionEntity(
                id = 1L,
                yearMonth = "2026-07",
                bankName = "CSOB",
                date = "2026-07-05",
                amount = -2_000.0,
                counterpartyName = "Lidl Praha",
                category = BankTransactionType.GROCERIES.name
            ),
            ImportedBankTransactionEntity(
                id = 2L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-10",
                amount = -3_000.0,
                counterpartyName = "Lidl Praha",
                category = BankTransactionType.GROCERIES.name
            )
        )

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                MerchantLeaderboardCard(
                    transactions = txs,
                    settings = SettingsEntity(),
                    onUpdateImportedTransactionCategory = { _, _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("STATEMENTS", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("+50.0%", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun budgetOverview_savingsTotalIncludesEmergencyReserveAndEditButtonAlignsCleanly() {
        val settings = SettingsEntity(
            isSingleHousehold = false,
            portuDcaMonthly = 10_000.0,
            ePortuDcaMonthly = 2_000.0,
            dipContributionMonthly = 4_000.0,
            eDipContributionMonthly = 1_000.0,
            dpsOwnContributionMonthly = 1_700.0,
            eDpsOwnContributionMonthly = 1_300.0,
            employerRetirementMonthly = 2_000.0,
            eEmployerRetirementMonthly = 500.0,
            emergencyReserveMode = "Target",
            emergencyReserveTarget = 240_000.0,
            emergencyReserveCurrent = 120_000.0
        )
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)
        val expectedTotal = FinancialEngine.monthlySavingsAndInvestmentTotal(settings)
        val expectedPillText = "${fmtCZK(expectedTotal)} monthly"

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                BudgetOverviewCard(
                    state = state,
                    ledgerEntries = emptyList(),
                    onUpdateSettings = {}
                )
            }
        }

        val editNode = composeTestRule.onNodeWithTag("btn_edit_investments")
        editNode.performScrollTo()
        editNode.assertIsDisplayed()
        editNode.assertHeightIsAtLeast(48.dp)

        val pillNode = composeTestRule.onNodeWithText(expectedPillText)
        pillNode.assertIsDisplayed()

        val editBounds = editNode.fetchSemanticsNode().boundsInRoot
        val pillBounds = pillNode.fetchSemanticsNode().boundsInRoot
        val editCenterY = (editBounds.top + editBounds.bottom) / 2f
        val pillCenterY = (pillBounds.top + pillBounds.bottom) / 2f
        assertTrue(
            "Expected Edit control vertical center ($editCenterY) to align with '$expectedPillText' center ($pillCenterY)",
            abs(editCenterY - pillCenterY) < 8f
        )
    }

    @Test
    fun emergencyReserveContribution_formulaVisibleInBudgetOverviewCard() {
        val settings = SettingsEntity(
            emergencyReserveMode = "Target",
            emergencyReserveTarget = 240_000.0,
            emergencyReserveCurrent = 120_000.0,
            emergencyReserveMonthlyOverride = -1.0
        )
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                BudgetOverviewCard(
                    state = state,
                    ledgerEntries = emptyList(),
                    onUpdateSettings = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("savings_investment_allocations_card").performScrollTo()
        composeTestRule.onNodeWithText(fmtCompact(240_000.0), substring = true).assertExists()
        composeTestRule.onNodeWithText("÷ 24 mo", substring = true).assertExists()
    }
}
