package com.example

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.ActionMeta
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.PortfolioYearPoint
import com.example.domain.StressScenarioResult
import com.example.domain.YtdTaxShieldStatus
import com.example.domain.parseDeletedCategories
import com.example.domain.serializeDeletedCategories
import com.example.ui.components.DecumulationComparisonCard
import com.example.ui.components.SubscriptionAuditorCard
import com.example.ui.components.YtdTaxShieldTrackerCard
import com.example.ui.components.charts.StressComparisonChart
import com.example.ui.components.charts.deflateStressValue
import com.example.ui.components.sankey.buildCentralHubNode
import com.example.ui.components.sankey.buildInflowNodes
import com.example.ui.components.sankey.buildOutflowNodes
import com.example.ui.components.sankey.compute3PillarRibbonIndices
import com.example.ui.tabs.WhatIfSandboxSubTab
import com.example.ui.tabs.settingsHouseholdSubTab
import com.example.ui.theme.MartinuFinancialsTheme
import com.example.util.BankTransactionType
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtExactCZK
import com.example.util.SubscriptionAuditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class Section25UiChartsAndErgonomicsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun test1_fmtExactCzkPreservesExactAmountsInSubscriptionsAndTaxShield() {
        val subs = listOf("2026-01", "2026-02", "2026-03").mapIndexed { idx, ym ->
            ImportedBankTransactionEntity(
                id = idx + 1L,
                yearMonth = ym,
                bankName = "MONETA",
                date = "$ym-05",
                amount = -239.0,
                counterpartyName = "Netflix",
                category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
            )
        }
        val taxStatus = YtdTaxShieldStatus(
            currentYear = 2026,
            statutoryCeilingAnnual = 48_000.0,
            ytdDipContributed = 15_204.0,
            ytdDpsContributed = 233.0,
            ytdTotalContributed = 15_437.0,
            ytdProgressPct = 32.16,
            taxRefundSecured15Pct = 2_315.0,
            taxRefundSecured23Pct = 3_550.0,
            remainingHeadroomAnnual = 32_563.0,
            monthsRemainingInYear = 9,
            recommendedMonthlyPace = 3_618.0,
            isCeilingReached = false
        )

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    SubscriptionAuditorCard(
                        transactions = subs,
                        swrPct = 4.0,
                        safetyBufferPct = 0.0,
                        initiallyExpanded = true
                    )
                    YtdTaxShieldTrackerCard(status = taxStatus)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(fmtExactCZK(239.0), substring = true).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(fmtExactCZK(239.0)).onFirst().performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(fmtExactCZK(15_204.0)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(fmtExactCZK(233.0)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun test2_ytdTaxShieldTrackerHandlesNaNProgressWithoutCrash() {
        val nanStatus = YtdTaxShieldStatus(
            currentYear = 2026,
            statutoryCeilingAnnual = 0.0,
            ytdDipContributed = 0.0,
            ytdDpsContributed = 0.0,
            ytdTotalContributed = 0.0,
            ytdProgressPct = Double.NaN,
            taxRefundSecured15Pct = Double.NaN,
            taxRefundSecured23Pct = 0.0,
            remainingHeadroomAnnual = 0.0,
            monthsRemainingInYear = 6,
            recommendedMonthlyPace = Double.NaN,
            isCeilingReached = false
        )

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                YtdTaxShieldTrackerCard(status = nanStatus)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ytd_tax_shield_tracker_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("0%").assertIsDisplayed()
    }

    @Test
    fun test3_subscriptionAuditorPerRowFireCapitalMatchesHeaderWithSafetyBuffer() {
        val subs = listOf("2026-01", "2026-02", "2026-03").mapIndexed { idx, ym ->
            ImportedBankTransactionEntity(
                id = 10L + idx,
                yearMonth = ym,
                bankName = "CSOB",
                date = "$ym-10",
                amount = -500.0,
                counterpartyName = "Spotify",
                category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
            )
        }
        val audit = SubscriptionAuditor.auditSubscriptions(subs, swrPct = 4.0, safetyBufferPct = 15.0)
        assertEquals(1, audit.items.size)
        val expectedBufferedCapital = ((500.0 * 12.0) / 0.04) * 1.15 // 172,500
        assertEquals(expectedBufferedCapital, audit.fireCapitalRequired, 0.01)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                SubscriptionAuditorCard(
                    transactions = subs,
                    swrPct = 4.0,
                    safetyBufferPct = 15.0,
                    initiallyExpanded = true
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(fmtCZK(expectedBufferedCapital)).assertIsDisplayed()
        composeTestRule.onNodeWithText("FIRE: ${fmtCZK(expectedBufferedCapital)}").assertIsDisplayed()
    }

    @Test
    fun test4_sankeyOutflowsAlignWithUnallocatedCashAndConnectEmployerMatch() {
        val settings = SettingsEntity(
            isSingleHousehold = true,
            vSalary = 70_000.0,
            rentMonthly = 20_000.0,
            groceriesMonthly = 6_000.0,
            portuDcaMonthly = 10_000.0,
            dpsOwnContributionMonthly = 1_700.0,
            dipContributionMonthly = 2_000.0,
            employerRetirementMonthly = 1_000.0,
            emergencyReserveCurrent = 100_000.0,
            emergencyReserveTarget = 148_000.0,
            emergencyReserveMode = "Target"
        )
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertTrue(state.snapshot.monthlyReserveContribution > 0.0)

        val inflows3Pillar = buildInflowNodes(state, viewMode = 0)
        val outflows3Pillar = buildOutflowNodes(state, viewMode = 0)
        val hub = buildCentralHubNode(state)

        val reserveNode = outflows3Pillar.firstOrNull { it.id == "out_reserve" }
        val bufferNode = outflows3Pillar.firstOrNull { it.id == "out_buffer" }
        assertNotNull(reserveNode)
        assertEquals(state.snapshot.monthlyReserveContribution, reserveNode!!.amount, 0.01)
        assertNotNull(bufferNode)
        assertEquals(state.snapshot.monthlyUnallocatedCash, bufferNode!!.amount, 0.01)
        assertEquals(inflows3Pillar.sumOf { it.amount }, outflows3Pillar.sumOf { it.amount }, 0.01)
        assertEquals(hub.amount, outflows3Pillar.sumOf { it.amount }, 0.01)

        val ribbons = compute3PillarRibbonIndices(
            inNodeIds = inflows3Pillar.map { it.id },
            outNodeIds = outflows3Pillar.map { it.id }
        )
        val empInIdx = inflows3Pillar.indexOfFirst { it.id == "in_employer_match" }
        val empOutIdx = outflows3Pillar.indexOfFirst { it.id == "out_employer_match" }
        assertTrue(empInIdx >= 0 && empOutIdx >= 0)
        assertTrue(ribbons.contains(empInIdx to empOutIdx))
        inflows3Pillar.indices.forEach { idx ->
            assertTrue("Inflow index $idx must not be orphaned", ribbons.any { it.first == idx })
        }
    }

    @Test
    fun test5_stressChartDeflatesNominalSeriesByScenarioCpiAndRenders() {
        val nomYear10 = 1_000_000.0
        val realBaseline = deflateStressValue(nomYear10, cpiInflationPct = 2.8, year = 2036, baseYear = 2026)
        val realShock = deflateStressValue(nomYear10, cpiInflationPct = 6.0, year = 2036, baseYear = 2026)
        assertEquals(nomYear10 / 1.028.pow(10.0), realBaseline, 0.01)
        assertEquals(nomYear10 / 1.060.pow(10.0), realShock, 0.01)
        assertTrue(realBaseline > realShock)

        val scenarios = listOf(
            StressScenarioResult(
                id = "baseline",
                name = "Baseline Plan",
                iconEmoji = "",
                description = "Baseline",
                nominalReturnPct = 7.0,
                cpiInflationPct = 2.8,
                swrPct = 4.0,
                rentGrowthPct = 2.8,
                fireTargetToday = 10_000_000.0,
                fireAge = 45,
                fireYear = 2045,
                successRatePct = 90.0,
                emergencySurvivalMonths = 6.0,
                netWorthAt60 = 12_000_000.0,
                trajectory = listOf(
                    PortfolioYearPoint(2026, 26, 200_000.0, 10_000_000.0, 120_000.0, 0.0, 0.0, "Acc", pensionPortfolio = 50_000.0, totalPortfolio = 250_000.0),
                    PortfolioYearPoint(2036, 36, 1_600_000.0, 13_000_000.0, 120_000.0, 0.0, 0.0, "Acc", pensionPortfolio = 400_000.0, totalPortfolio = 2_000_000.0)
                )
            ),
            StressScenarioResult(
                id = "inflation_shock",
                name = "High Inflation Spike",
                iconEmoji = "",
                description = "Shock",
                nominalReturnPct = 6.5,
                cpiInflationPct = 6.0,
                swrPct = 4.0,
                rentGrowthPct = 7.0,
                fireTargetToday = 10_000_000.0,
                fireAge = 50,
                fireYear = 2050,
                successRatePct = 75.0,
                emergencySurvivalMonths = 5.0,
                netWorthAt60 = 9_000_000.0,
                trajectory = listOf(
                    PortfolioYearPoint(2026, 26, 200_000.0, 10_000_000.0, 120_000.0, 0.0, 0.0, "Acc", pensionPortfolio = 50_000.0, totalPortfolio = 250_000.0),
                    PortfolioYearPoint(2036, 36, 1_500_000.0, 17_000_000.0, 120_000.0, 0.0, 0.0, "Acc", pensionPortfolio = 350_000.0, totalPortfolio = 1_850_000.0)
                )
            )
        )

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                StressComparisonChart(scenarios = scenarios)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stress_comparison_chart_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Today's real Kč", substring = true).assertIsDisplayed()
    }

    @Test
    fun test6_whatIfSandboxUsesCleanSettingsBaselineWithActiveLedgerOverrides() {
        val baseSettings = SettingsEntity(
            baseYear = 2026,
            isSingleHousehold = true,
            liquidPortfolioCurrent = 200_000.0,
            emergencyReserveCurrent = 150_000.0
        )
        val ledger = listOf(
            LedgerEntryEntity(
                id = 1L,
                yearMonth = "2026-04",
                incVaclav = 50_000.0,
                expRent = 20_000.0,
                expGroceries = 6_000.0,
                expOther = 4_000.0,
                portfolioBalanceAtMonthEnd = 999_000.0,
                pensionBalanceAtMonthEnd = 88_000.0,
                emergencyReserveAtMonthEnd = 333_000.0
            )
        )
        val calcState = FinancialEngine.calculate(
            baseSettings,
            activeLedgerEntry = ledger.first(),
            ledgerEntries = ledger,
            runMonteCarlo = false
        )
        assertTrue(calcState.effectiveSettings.liquidPortfolioCurrent != calcState.settings.liquidPortfolioCurrent)

        var appliedSettings: SettingsEntity? = null
        composeTestRule.setContent {
            MartinuFinancialsTheme {
                WhatIfSandboxSubTab(
                    state = calcState,
                    ledgerEntries = ledger,
                    onApplySettings = { appliedSettings = it },
                    onShowInfo = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Should start clean despite ledger override modifying effectiveSettings
        composeTestRule.onNodeWithTag("sandbox_reset").assertDoesNotExist()
        composeTestRule.onNodeWithTag("sandbox_apply").assertDoesNotExist()

        // Selecting a preset dirties the sandbox; resetting returns cleanly to state.settings
        composeTestRule.onNodeWithTag("sandbox_preset_conservative").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sandbox_reset").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sandbox_reset").assertDoesNotExist()

        // Applying a preset must preserve clean base balances without baking temporary ledger overrides
        composeTestRule.onNodeWithTag("sandbox_preset_conservative").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sandbox_apply").performClick()
        composeTestRule.waitForIdle()
        assertNotNull(appliedSettings)
        assertEquals(200_000.0, appliedSettings!!.liquidPortfolioCurrent, 0.01)
        assertEquals(150_000.0, appliedSettings!!.emergencyReserveCurrent, 0.01)
    }

    @Test
    fun test7_settingsHouseholdRestoresBuiltInCategoriesAndShowsChild2StageCosts() {
        var currentSettings = SettingsEntity(
            deletedCategoriesJson = serializeDeletedCategories(setOf("rent", "cafes")),
            child1Enabled = false,
            child2Enabled = true,
            child2Name = "Matej"
        )
        val state = FinancialEngine.calculate(currentSettings, runMonteCarlo = false)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                LazyColumn(modifier = Modifier.testTag("test_settings_lazy_col")) {
                    settingsHouseholdSubTab(
                        state = state,
                        onUpdateSettings = { currentSettings = it },
                        expandTrigger = 1,
                        expandTargetState = true,
                        onShowAddCategory = {},
                        onShowAddLumpSum = {},
                        onShowInfo = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("test_settings_lazy_col")
            .performScrollToNode(hasTestTag("restore_builtin_category_rent"))
        composeTestRule.onNodeWithTag("restore_builtin_category_rent").performClick()
        composeTestRule.waitForIdle()
        val remaining = parseDeletedCategories(currentSettings.deletedCategoriesJson)
        assertFalse(remaining.contains("rent"))
        assertTrue(remaining.contains("cafes"))

        composeTestRule.onNodeWithTag("test_settings_lazy_col")
            .performScrollToNode(hasText("Stage Expense Estimates (per Child)"))
        composeTestRule.onNodeWithText("Stage Expense Estimates (per Child)").assertIsDisplayed()
    }

    @Test
    fun test8_actionMetaAdaptsToHouseholdAndAvoidsPersonalizationLeaks() {
        val single = SettingsEntity(isSingleHousehold = true, eIncludeLecturing = true, eLecturingMonthly = 8000.0)
        val singleItems = ActionMeta.itemsFor(single)
        assertFalse(singleItems.any { it.id == "ac1" })
        assertFalse(singleItems.any { it.id == "ac6" })

        val dual = SettingsEntity(
            isSingleHousehold = false,
            primaryName = "Karel",
            spouseName = "Jana",
            eIncludeLecturing = true,
            eLecturingMonthly = 5000.0
        )
        val dualItems = ActionMeta.itemsFor(dual)
        assertTrue(dualItems.any { it.id == "ac1" })
        assertTrue(dualItems.any { it.id == "ac6" && it.title.contains("Jana") })

        val allText = (ActionMeta.items + singleItems + dualItems)
            .joinToString("\n") { "${it.title} ${it.description}" }
        assertFalse("Must not leak Eleonora", allText.contains("Eleonora", ignoreCase = true))
        assertFalse("Must not hardcode Portu", allText.contains("Portu", ignoreCase = true))
    }

    @Test
    fun test9_decumulationComparisonCardRendersAsyncWhenPrecomputedIsNull() {
        val settings = SettingsEntity(isSingleHousehold = true, safeWithdrawalRatePct = 4.0)
        val fastState = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertEquals(null, fastState.decumulationComparison)

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                DecumulationComparisonCard(state = fastState)
            }
        }

        composeTestRule.onNodeWithTag("decumulation_comparison_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("decumulation_spend_value").assertIsDisplayed()
    }
}
