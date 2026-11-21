package com.example

import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.CzechCapitalGainsEngine
import com.example.domain.CzechYearEndTaxChecklist
import com.example.domain.DpsDipEngine
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.InvestmentLot
import com.example.domain.LotSaleOrder
import com.example.ui.components.sankey.buildCentralHubNode
import com.example.ui.components.sankey.buildInflowNodes
import com.example.ui.components.sankey.buildOutflowNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossScreenNumericalParityTest {

    private fun verifyStateParity(
        label: String,
        state: FullCalculationState,
        ledgerEntries: List<LedgerEntryEntity> = emptyList()
    ) {
        val snap = state.snapshot

        // 1. Net Worth & Component Breakdown
        val componentSum = snap.investedLiquidTotal + snap.pensionTotal + snap.emergencyCashTotal
        assertEquals("$label: snapshot netWorth must equal liquid + pension + cash", componentSum, snap.netWorthTotal, 0.01)
        assertEquals("$label: state netWorthTotal must match snapshot", snap.netWorthTotal, state.netWorthTotal, 0.01)
        assertEquals("$label: state liquid must match snapshot", snap.investedLiquidTotal, state.currentLiquidPortfolio, 0.01)
        assertEquals("$label: state pension must match snapshot", snap.pensionTotal, state.currentPensionPortfolio, 0.01)
        assertEquals("$label: state cash reserve must match snapshot", snap.emergencyCashTotal, state.currentEmergencyReserve, 0.01)

        // 2. Active FIRE Target & Year across Hero, Roadmap, Milestones, Stress Baseline
        assertEquals("$label: state.fireBaseTargetToday must match snapshot", snap.activeFireTargetToday, state.fireBaseTargetToday, 0.01)
        assertEquals(
            "$label: Standard FIRE milestone target must match exact canonical FIRE target",
            snap.activeFireTargetToday,
            state.fireMilestones.standardFire.targetAmountToday,
            0.01
        )
        assertEquals(
            "$label: Standard FIRE milestone estimated year must match canonical activeFireYear",
            snap.activeFireYear,
            state.fireMilestones.standardFire.estimatedYear
        )
        assertEquals(
            "$label: Standard FIRE milestone estimated age must match canonical activeFireAge",
            snap.activeFireAge,
            state.fireMilestones.standardFire.estimatedAge
        )

        val baselineStress = state.stressScenarios.first { it.id == "baseline" }
        assertEquals("$label: stress baseline target must match canonical target", snap.activeFireTargetToday, baselineStress.fireTargetToday, 0.01)
        if (!snap.isFireAchievedToday) {
            assertEquals("$label: stress baseline FIRE year must match canonical activeFireYear", snap.activeFireYear, baselineStress.fireYear)
        }

        // 3. Monthly Inflows, Investing (Own + Match), Living Costs, Savings Rate
        assertEquals("$label: state investMonthlyTotal must match snapshot total investing", snap.monthlyTotalInvesting, state.investMonthlyTotal, 0.01)
        assertEquals(
            "$label: snapshot total investing must equal own + employer match",
            snap.monthlyOwnInvesting + snap.monthlyEmployerMatch,
            snap.monthlyTotalInvesting,
            0.01
        )
        assertEquals("$label: state totalLivingCostMonthly must match snapshot", snap.monthlyLivingCosts, state.totalLivingCostMonthly, 0.01)
        assertEquals("$label: state savingsRatePct must match snapshot", snap.savingsRatePct, state.savingsRatePct, 0.001)
        assertEquals("$label: state currentIncome.totalMonthly must match snapshot take-home inflows", snap.monthlyTakeHomeInflows, state.currentIncome.totalMonthly, 0.01)
        assertEquals("$label: state perpetualFireMultiplier must match snapshot", snap.perpetualFireMultiplier, state.perpetualFireMultiplier, 0.001)

        // 4. Sankey 3-Pillar (viewMode = 0) and Granular (viewMode = 1) Parity
        for (mode in listOf(0, 1)) {
            val inflows = buildInflowNodes(state, mode).sumOf { it.amount }
            val hub = buildCentralHubNode(state, viewMode = mode).amount
            val outflows = buildOutflowNodes(state, mode).sumOf { it.amount }

            assertEquals("$label (Sankey mode $mode): inflow nodes sum must equal total inflows + match", snap.monthlyTotalInflowsWithMatch, inflows, 0.01)
            assertEquals("$label (Sankey mode $mode): central hub must equal total inflows + match", snap.monthlyTotalInflowsWithMatch, hub, 0.01)
            if (snap.monthlyTakeHomeInflows - snap.monthlyLivingCosts - snap.monthlyOwnInvesting >= 0.0) {
                assertEquals("$label (Sankey mode $mode): outflow nodes sum must conserve total inflows + match", snap.monthlyTotalInflowsWithMatch, outflows, 0.01)
            }
        }

        // 5. What-If Sandbox Zero-Delta Parity
        val sandboxZeroDelta = FinancialEngine.calculate(
            settings = state.effectiveSettings,
            ledgerEntries = ledgerEntries,
            runMonteCarlo = false
        )
        assertEquals("$label: sandbox 0-delta FIRE target must match baseline", snap.activeFireTargetToday, sandboxZeroDelta.snapshot.activeFireTargetToday, 0.01)
        assertEquals("$label: sandbox 0-delta active FIRE year must match baseline", snap.activeFireYear, sandboxZeroDelta.snapshot.activeFireYear)
        assertEquals("$label: sandbox 0-delta total living cost must match baseline", snap.monthlyLivingCosts, sandboxZeroDelta.snapshot.monthlyLivingCosts, 0.01)
        assertEquals("$label: sandbox 0-delta total investing must match baseline", snap.monthlyTotalInvesting, sandboxZeroDelta.snapshot.monthlyTotalInvesting, 0.01)
    }

    @Test
    fun parityAcrossDefaultSingleLedgerOverrideAndAlreadyFireProfiles() {
        val defaultDual = SettingsEntity.freshDefaults()
        verifyStateParity("DefaultDual", FinancialEngine.calculate(defaultDual, runMonteCarlo = true))

        val singleEarner = SettingsEntity.freshDefaults().copy(
            isSingleHousehold = true,
            vSalary = 78_000.0,
            liquidPortfolioCurrent = 650_000.0,
            portuDcaMonthly = 25_000.0,
            child1Enabled = false,
            child2Enabled = false
        )
        verifyStateParity("SingleEarner", FinancialEngine.calculate(singleEarner, runMonteCarlo = false))

        val activeLedger = LedgerEntryEntity(
            yearMonth = "2026-08",
            incVaclav = 92_000.0,
            portfolioBalanceAtMonthEnd = 900_000.0,
            pensionBalanceAtMonthEnd = 300_000.0,
            emergencyReserveAtMonthEnd = 420_000.0
        )
        verifyStateParity(
            label = "LedgerOverride",
            state = FinancialEngine.calculate(
                settings = defaultDual,
                activeLedgerEntry = activeLedger,
                activeMonth = 8,
                ledgerEntries = listOf(activeLedger),
                runMonteCarlo = false
            ),
            ledgerEntries = listOf(activeLedger)
        )

        val wealthyAlreadyFire = SettingsEntity.freshDefaults().copy(
            isSingleHousehold = true,
            primaryAge = 45,
            liquidPortfolioCurrent = 28_000_000.0,
            dipBalanceCurrent = 5_000_000.0,
            emergencyReserveCurrent = 800_000.0,
            rentMonthly = 18_000.0,
            groceriesMonthly = 8_000.0,
            child1Enabled = false,
            child2Enabled = false
        )
        val wealthyState = FinancialEngine.calculate(wealthyAlreadyFire, runMonteCarlo = false)
        assertTrue(wealthyState.snapshot.isFireAchievedToday)
        assertEquals(wealthyAlreadyFire.baseYear, wealthyState.snapshot.activeFireYear)
        verifyStateParity("WealthyAlreadyFire", wealthyState)
    }

    @Test
    fun statutoryCzechRulesEnforcedAcrossEngines() {
        // A. Act No. 117/1995 Coll. § 30(1): birth of younger child forfeits unspent Child 1 parental pot
        val familyWithTwoChildren = SettingsEntity.freshDefaults().copy(
            baseYear = 2026,
            isSingleHousehold = false,
            eReturnYear = 2030,
            eParentalAllowanceMonthly = 13_000.0,
            child1Enabled = true,
            child1BirthYear = 2025,
            child2Enabled = true,
            child2BirthYear = 2027,
            parentalAllowanceTotalBeforeCutoff = 350_000.0,
            parentalAllowanceTotalFromCutoff = 350_000.0
        )
        val potAtChild2BirthYear = FinancialEngine.remainingParentalPotAtYearStart(2027, familyWithTwoChildren)
        assertEquals(
            "Child 2 birth must reset pot to 350,000 CZK (forfeiting unspent Child 1 balance)",
            350_000.0,
            potAtChild2BirthYear,
            0.01
        )
        // B. Act No. 407/2023 Coll.: cutoff at age 3 (born >= 2024)
        val potAfterCutoffAge3 = FinancialEngine.remainingParentalPotAtYearStart(2030, familyWithTwoChildren)
        assertEquals(0.0, potAfterCutoffAge3, 0.01)

        // C. DPS deduction floor drops to 0 CZK at state pension age in checklist, YTD tracker & pension snapshot
        val retireeSettings = SettingsEntity.freshDefaults().copy(
            isSingleHousehold = false,
            primaryAge = 67,
            vStatePensionAge = 67,
            eStatePensionAge = 67,
            vSalary = 60_000.0,
            dpsOwnContributionMonthly = 1_700.0,
            eDpsOwnContributionMonthly = 1_700.0,
            dipContributionMonthly = 0.0,
            eDipContributionMonthly = 0.0
        )
        val retireeState = FinancialEngine.calculate(retireeSettings, activeMonth = 12, runMonteCarlo = false)
        assertEquals(0.0, retireeState.pensionOptimization.primaryDpsDeductionThresholdMonthly, 0.001)
        assertEquals(0.0, retireeState.pensionOptimization.partnerDpsDeductionThresholdMonthly, 0.001)
        assertEquals(0.0, retireeState.pensionOptimization.primarySubsidyMonthly, 0.001)
        assertEquals(1_700.0 * 12.0, retireeState.ytdTaxShieldStatus!!.ytdTotalContributed, 0.01)
        val partnerChecklistItem = retireeState.yearEndTaxChecklist!!.items.first { it.id == "ret_dip_dps_partner" }
        assertEquals(1_700.0 * 12.0, partnerChecklistItem.currentValueCzk, 0.01)

        // D. ZDP § 35ba(1)(b) non-refundable spouse credit capped by remaining primary tax liability
        val lowIncomeSpouseCredit = SettingsEntity.freshDefaults().copy(
            isSingleHousehold = false,
            hasChildUnder3 = true,
            vSalary = 20_000.0,
            eIncludeLecturing = false,
            eLecturingMonthly = 0.0,
            eReturnYear = 2030
        )
        val lowIncomeChecklist = CzechYearEndTaxChecklist.generateChecklist(
            settings = lowIncomeSpouseCredit,
            taxYear = 2026,
            spouseOwnIncomeAnnual = 0.0
        )
        val spouseItem = lowIncomeChecklist.items.first { it.id == "spouse_tax_credit" }
        assertEquals(
            FinancialEngine.annualPartnerTaxCredit(lowIncomeSpouseCredit, 2026),
            spouseItem.potentialTaxSavingCzk,
            0.01
        )
        assertTrue(spouseItem.potentialTaxSavingCzk < 24_840.0)

        // E. ZDP § 6(9)(m) employer match routes to DIP when user contributes only to DIP (dpsOwn == 0 && dip > 0)
        val dipOnlyWithMatch = SettingsEntity.freshDefaults().copy(
            primaryAge = 35,
            isSingleHousehold = true,
            dpsOwnContributionMonthly = 0.0,
            dpsBalanceCurrent = 0.0,
            dipContributionMonthly = 4_000.0,
            dipBalanceCurrent = 0.0,
            employerRetirementMonthly = 3_000.0
        )
        val dipOnlyNoMatch = dipOnlyWithMatch.copy(employerRetirementMonthly = 0.0)
        val dipProjWithMatch = DpsDipEngine.buildDipProjection(dipOnlyWithMatch)
        val dipProjNoMatch = DpsDipEngine.buildDipProjection(dipOnlyNoMatch)
        assertTrue(
            "Employer match must boost DIP balance when user contributes only to DIP",
            dipProjWithMatch.dipBalanceAt60 > dipProjNoMatch.dipBalanceAt60 + 500_000.0
        )

        // F. ZDP § 4(1)(u) & § 16: 23% capital gains copy reflects passed rate
        val lot = InvestmentLot("l1", "VWCE", LocalDate.of(2024, 1, 10), 100.0, 2_000.0, 4_000.0)
        val sim23 = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(lot),
            saleOrders = listOf(LotSaleOrder("l1", 100.0)),
            asOfDate = LocalDate.of(2026, 1, 10),
            taxRatePct = 23.0
        )
        assertTrue(sim23.recommendation.contains("23%"))
    }
}
