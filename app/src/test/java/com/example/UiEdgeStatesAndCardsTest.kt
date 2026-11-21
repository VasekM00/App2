package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.components.CapitalGainsTimeTestCard
import com.example.ui.components.CrossAccountNettingAuditDialog
import com.example.ui.components.DecumulationComparisonCard
import com.example.ui.components.EditImportedTransactionDialog
import com.example.ui.components.EditInvestmentLotDialog
import com.example.ui.components.ExportReportDialog
import com.example.ui.components.FundsAllocatorCard
import com.example.ui.components.HouseholdTimelineCard
import com.example.ui.tabs.CashFlowTab
import com.example.ui.tabs.OverviewTab
import com.example.ui.tabs.ProjectionsTab
import com.example.ui.tabs.SettingsTab
import com.example.ui.theme.MartinuFinancialsTheme
import com.example.util.BankTransactionType
import com.example.util.CrossStatementAuditReport
import com.example.util.MatchedTransferPair
import com.example.util.UserInvestmentLot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wave 3 Compose UI edge-state, accessibility (320dp + fontScale 2.0f), dark/light theme,
 * NaN/0-weight guard, and previously untested cards/dialogs rendering regression suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp-xxhdpi")
class UiEdgeStatesAndCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val edgeSettings = SettingsEntity(
        primaryAge = 70,
        vStatePensionAge = 65,
        isSingleHousehold = true,
        vSalary = 20_000.0,
        eStartingSalary = 0.0,
        eParentalAllowanceMonthly = 0.0,
        eIncludeLecturing = false,
        vGiftsMonthly = 0.0,
        rentMonthly = 30_000.0,
        groceriesMonthly = 8_000.0,
        portuDcaMonthly = 0.0,
        dpsOwnContributionMonthly = 0.0,
        dipContributionMonthly = 0.0,
        employerRetirementMonthly = 0.0,
        ePortuDcaMonthly = 0.0,
        eDpsOwnContributionMonthly = 0.0,
        eDipContributionMonthly = 0.0,
        eEmployerRetirementMonthly = 0.0,
        liquidPortfolioCurrent = 0.0,
        emergencyReserveCurrent = 0.0
    )

    private val edgeState = FinancialEngine.calculate(edgeSettings, runMonteCarlo = true)
    private val defaultState = FinancialEngine.calculate(SettingsEntity(useHistoricalBootstrap = true), runMonteCarlo = true)

    @Test
    fun tabsRenderAt320dpAndFontScale2_underDarkThemeAndNegativeSavingsAgeAbovePension() {
        composeTestRule.setContent {
            val extremeDensity = Density(density = LocalDensity.current.density, fontScale = 2.0f)
            CompositionLocalProvider(LocalDensity provides extremeDensity) {
                MartinuFinancialsTheme(darkTheme = true) {
                    Box(modifier = Modifier.width(320.dp).height(720.dp)) {
                        OverviewTab(state = edgeState)
                    }
                    Box(modifier = Modifier.width(320.dp).height(720.dp)) {
                        ProjectionsTab(state = edgeState, initialSubTab = 3)
                    }
                    Box(modifier = Modifier.width(320.dp).height(720.dp)) {
                        ProjectionsTab(state = edgeState, initialSubTab = 4)
                    }
                    Box(modifier = Modifier.width(320.dp).height(720.dp)) {
                        SettingsTab(
                            state = edgeState,
                            onUpdateSettings = {},
                            onResetDefaults = {},
                            onClearAllData = {},
                            liveRegulatoryData = null,
                            isSyncing = false,
                            onSyncLiveCzechData = {}
                        )
                    }
                    Box(modifier = Modifier.width(320.dp).height(720.dp)) {
                        CashFlowTab(
                            state = edgeState,
                            ledgerEntries = emptyList(),
                            onAddLedgerEntry = { _, _, _, _, _, _, _, _, _, _ -> },
                            onUpdateLedgerEntry = {},
                            onDeleteLedgerEntry = {},
                            onImportCsv = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overview_tab").assertExists()
        composeTestRule.onAllNodesWithTag("projections_tab").assertCountEquals(2)
        composeTestRule.onNodeWithTag("settings_tab").assertExists()
        composeTestRule.onNodeWithTag("cashflow_tab").assertExists()
    }

    @Test
    fun untestedCards_renderWithNaNZeroWeightsLightThemeAnd320dpFontScale2() {
        val trickyLots = listOf(
            UserInvestmentLot("nan1", "NaN Lot", "VWCE", "2024-02-29", Double.NaN, 0.0),
            UserInvestmentLot("ok2", "Exempt Lot", "EUNL", "2020-01-15", 100_000.0, 150_000.0)
        )
        composeTestRule.setContent {
            val extremeDensity = Density(density = LocalDensity.current.density, fontScale = 2.0f)
            CompositionLocalProvider(LocalDensity provides extremeDensity) {
                MartinuFinancialsTheme(darkTheme = false) {
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        DecumulationComparisonCard(state = defaultState)
                        CapitalGainsTimeTestCard(state = edgeState, customLots = trickyLots)
                        FundsAllocatorCard(state = edgeState)
                        HouseholdTimelineCard(
                            settings = edgeSettings,
                            state = edgeState,
                            onUpdateSettings = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tax_optimal_harvest_card").assertExists()
    }

    @Test
    fun untestedDialogs_renderInDarkAndLightThemesWithoutCrash() {
        val sampleTx = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-04",
            bankName = "CSOB",
            date = "2026-04-10",
            amount = -2500.0,
            counterpartyAccount = "123456/3030",
            counterpartyName = "Air Bank Self",
            message = "Prevod mezi ucty",
            variableSymbol = "202604",
            category = BankTransactionType.INTERNAL_TRANSFER.name,
            isNetted = true,
            nettingReason = "Matched pair"
        )
        val partnerTx = sampleTx.copy(id = 2L, bankName = "AIRBANK", amount = 2500.0)
        val auditReport = CrossStatementAuditReport(
            yearMonth = "2026-04",
            totalNettedAmount = 2500.0,
            matchedPairs = listOf(MatchedTransferPair(sampleTx, partnerTx, 1L, 2500.0)),
            singleSideNettedTransfers = emptyList(),
            participatingBanks = listOf("CSOB", "AIRBANK"),
            reconciledInflows = 80_000.0,
            reconciledExpenses = 35_000.0,
            reconciledNetCashFlow = 45_000.0,
            combinedEmergencyReserve = 250_000.0,
            bankBalances = mapOf("CSOB" to 150_000.0, "AIRBANK" to 100_000.0)
        )
        val sampleLot = UserInvestmentLot("lot1", "DCA 2024", "VWCE", "2024-02-29", 50_000.0, 62_000.0)

        composeTestRule.setContent {
            MartinuFinancialsTheme(darkTheme = true) {
                ExportReportDialog(state = defaultState, onDismiss = {})
                CrossAccountNettingAuditDialog(auditReport = auditReport, onDismiss = {})
                EditImportedTransactionDialog(
                    tx = sampleTx,
                    matchingCountInMonth = 2,
                    onConfirmCategoryChange = { _, _, _ -> },
                    onToggleExcluded = {},
                    onDismiss = {}
                )
                EditInvestmentLotDialog(
                    lot = null,
                    onDismiss = {},
                    onSave = {}
                )
                EditInvestmentLotDialog(
                    lot = sampleLot,
                    onDismiss = {},
                    onSave = {},
                    onDelete = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
