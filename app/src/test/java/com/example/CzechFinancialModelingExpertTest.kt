package com.example

import com.example.data.SettingsEntity
import com.example.domain.ChecklistStatus
import com.example.domain.CzechCapitalGainsEngine
import com.example.domain.CzechYearEndTaxChecklist
import com.example.domain.DecumulationConfig
import com.example.domain.DecumulationStrategies
import com.example.domain.DecumulationStrategyType
import com.example.domain.FinancialEngine
import com.example.domain.InvestmentLot
import com.example.domain.LotSaleOrder
import com.example.domain.TimeTestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Comprehensive verification of Domain 2: Advanced Czech Financial Modeling & New Planning Tools:
 * 1. DecumulationStrategies (Bengen 4% + CPI, Guyton-Klinger Guardrails, Variable Percentage Decumulation)
 * 2. CzechCapitalGainsEngine (3-year time-test under ZDP § 4(1)(w), 100k CZK annual limit under § 4(1)(v), lot tracking)
 * 3. CzechYearEndTaxChecklist (DIP/DPS headroom, employer exemption, spouse credit, mortgage certificate)
 * 4. Integration into FinancialEngine and FullCalculationState
 */
class CzechFinancialModelingExpertTest {

    // =========================================================================
    // 1. DECUMULATION STRATEGIES TESTS
    // =========================================================================

    @Test
    fun testBengenConstantRealSwrInflationPreservation() {
        val initialPortfolio = 10_000_000.0
        val initialNeed = 400_000.0 // 4% SWR
        val flatReturn = listOf(0.06, 0.06, 0.06, 0.06, 0.06)
        val cpiPct = 2.8

        val path = DecumulationStrategies.simulateSinglePath(
            type = DecumulationStrategyType.CONSTANT_REAL_SWR,
            initialPortfolio = initialPortfolio,
            initialAnnualWithdrawal = initialNeed,
            annualReturns = flatReturn,
            annualInflationPct = cpiPct
        )

        assertEquals(5, path.size)
        // In real terms, each year's withdrawal must exactly match initial real purchasing power (400,000 CZK)
        for (step in path) {
            assertEquals(initialNeed, step.withdrawalReal, 0.01)
            assertFalse(step.isDepleted)
        }
        // Nominal withdrawal in year 2 must be 400,000 * 1.028 = 411,200 CZK
        assertEquals(411_200.0, path[1].withdrawalNominal, 0.01)
    }

    @Test
    fun testGuytonKlingerCapitalPreservationRuleCut() {
        val initialPortfolio = 10_000_000.0
        val initialNeed = 400_000.0 // 4.0% initial SWR
        // Severe market crash in year 1: -30%
        val crashReturns = listOf(-0.30, 0.02)

        val path = DecumulationStrategies.simulateSinglePath(
            type = DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS,
            initialPortfolio = initialPortfolio,
            initialAnnualWithdrawal = initialNeed,
            annualReturns = crashReturns,
            annualInflationPct = 2.8,
            gkUpperThresholdPct = 20.0,
            gkCutPct = 10.0
        )

        // In year 2, portfolio is down to ~ (10M - 400k) * 0.70 = 6.72M.
        // Tentative withdrawal is 400k * 1.028 = 411,200 CZK.
        // Current withdrawal rate = 411,200 / 6,720,000 = 6.12%, which exceeds 4.0% * 1.20 = 4.8%.
        // Capital Preservation rule must trigger 10% cut!
        val year2 = path[1]
        assertTrue("Year 2 must trigger guardrail cut", year2.isGuardrailCut)
        val expectedNominalAfterCut = (initialNeed * 1.028) * 0.90 // 411,200 * 0.9 = 370,080 CZK
        assertEquals(expectedNominalAfterCut, year2.withdrawalNominal, 1.0)
    }

    @Test
    fun testGuytonKlingerCapitalProsperityRuleBoost() {
        val initialPortfolio = 10_000_000.0
        val initialNeed = 400_000.0 // 4.0% initial SWR
        // Massive market bull run in year 1: +40%
        val bullReturns = listOf(0.40, 0.05)

        val path = DecumulationStrategies.simulateSinglePath(
            type = DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS,
            initialPortfolio = initialPortfolio,
            initialAnnualWithdrawal = initialNeed,
            annualReturns = bullReturns,
            annualInflationPct = 2.8,
            gkLowerThresholdPct = 20.0,
            gkBoostPct = 10.0
        )

        // Portfolio grew to (10M - 400k) * 1.40 = 13.44M.
        // Current withdrawal rate = 411,200 / 13,440,000 = 3.06%, which is below 4.0% * 0.80 = 3.2%.
        // Capital Prosperity rule must trigger 10% boost!
        val year2 = path[1]
        assertTrue("Year 2 must trigger guardrail boost", year2.isGuardrailBoost)
        val expectedNominalAfterBoost = (initialNeed * 1.028) * 1.10
        assertEquals(expectedNominalAfterBoost, year2.withdrawalNominal, 1.0)
    }

    @Test
    fun testVariablePercentageFloorAndCeilingConstraints() {
        val initialPortfolio = 10_000_000.0
        val initialNeed = 400_000.0
        val cpi = 0.028
        val flatReturns = listOf(0.05, 0.05)

        // VPD with 4.5% rate, 70% floor, 130% ceiling
        val path = DecumulationStrategies.simulateSinglePath(
            type = DecumulationStrategyType.VARIABLE_PERCENTAGE,
            initialPortfolio = initialPortfolio,
            initialAnnualWithdrawal = initialNeed,
            annualReturns = flatReturns,
            annualInflationPct = 2.8,
            vpdRatePct = 4.5,
            vpdFloorPctOfInitial = 70.0,
            vpdCeilingPctOfInitial = 130.0
        )

        // Year 1: 10,000,000 * 0.045 = 450,000 CZK.
        // Floor = 400,000 * 1.028 * 0.70 = 287,840. Ceiling = 400,000 * 1.028 * 1.30 = 534,560.
        // 450,000 is within [287,840, 534,560]
        assertEquals(450_000.0, path[0].withdrawalNominal, 0.01)
    }

    @Test
    fun testDecumulationComparativeMonteCarlo() {
        val config = DecumulationConfig(
            initialPortfolio = 10_000_000.0,
            initialAnnualWithdrawal = 400_000.0,
            horizonYears = 30,
            annualInflationPct = 2.8,
            expectedAnnualReturnPct = 7.0,
            returnVolatilityPct = 15.0,
            simulationsCount = 200,
            randomSeed = 12345L
        )

        val result = DecumulationStrategies.compareStrategies(config)

        assertNotNull(result.bengenMetrics)
        assertNotNull(result.guytonKlingerMetrics)
        assertNotNull(result.variablePercentageMetrics)

        assertTrue("Bengen survival rate must be valid", result.bengenMetrics.survivalRatePct in 0.0..100.0)
        assertTrue("Guyton-Klinger survival rate must be valid", result.guytonKlingerMetrics.survivalRatePct in 0.0..100.0)
        assertTrue("VPD survival rate must be valid", result.variablePercentageMetrics.survivalRatePct in 0.0..100.0)

        // Guyton-Klinger dynamic rules adapt to crashes and typically achieve higher survival than rigid Bengen
        assertTrue(result.guytonKlingerMetrics.survivalRatePct >= result.bengenMetrics.survivalRatePct - 1.0)

        // Median real ending capital must be finite and non-negative
        assertTrue(result.bengenMetrics.medianEndingCapitalReal >= 0.0)
        assertTrue(result.guytonKlingerMetrics.medianEndingCapitalReal >= 0.0)

        // Coefficient of variation (CV) for Bengen real withdrawals is lower than VPD (which adjusts with markets)
        assertTrue(result.variablePercentageMetrics.incomeVolatilityCv >= result.bengenMetrics.incomeVolatilityCv)
        assertNotNull(result.recommendedStrategy)
        assertTrue(result.executiveSummary.isNotBlank())
    }

    // =========================================================================
    // 3. CZECH CAPITAL GAINS ENGINE & 3-YEAR TIME-TEST TESTS
    // =========================================================================

    @Test
    fun testTimeTestStatusClassification() {
        val asOfDate = LocalDate.of(2026, 6, 1)

        // 1. Purchased 3.5 years ago (2022-12-01) -> EXEMPT_TIME_TEST
        val lotExempt = InvestmentLot.fromIsoDate("lot1", "VWCE", "2022-12-01", 10.0, 2000.0, 3000.0)
        assertEquals(TimeTestStatus.EXEMPT_TIME_TEST, lotExempt.timeTestStatus(asOfDate))
        assertEquals(0L, lotExempt.daysUntilExemption(asOfDate))

        // 2. Purchased 2023-08-01 (exempt on 2026-08-01, exactly 2 months away -> within 6 months) -> APPROACHING_EXEMPTION
        val lotApproaching = InvestmentLot.fromIsoDate("lot2", "VWCE", "2023-08-01", 10.0, 2200.0, 3000.0)
        assertEquals(TimeTestStatus.APPROACHING_EXEMPTION, lotApproaching.timeTestStatus(asOfDate))
        assertTrue(lotApproaching.daysUntilExemption(asOfDate) in 1..70)

        // 3. Purchased 2025-01-01 (1.5 years ago) -> TAXABLE
        val lotTaxable = InvestmentLot.fromIsoDate("lot3", "VWCE", "2025-01-01", 10.0, 2500.0, 3000.0)
        assertEquals(TimeTestStatus.TAXABLE, lotTaxable.timeTestStatus(asOfDate))
        assertTrue(lotTaxable.daysUntilExemption(asOfDate) > 200)
    }

    @Test
    fun testAnnual100kGrossRevenueExemptionUnderZDP() {
        // Under ZDP § 4 odst. 1 písm. v): if gross proceeds from securities sale <= 100,000 CZK, gains are exempt.
        val asOfDate = LocalDate.of(2026, 6, 1)

        val lotTaxable = InvestmentLot.fromIsoDate(
            id = "taxable_1",
            ticker = "SXR8",
            purchaseDateIso = "2025-06-01", // 1 year ago -> taxable
            units = 20.0,
            acquisitionPricePerUnitCzk = 3000.0, // cost basis = 60,000 CZK
            currentPricePerUnitCzk = 4000.0      // total value = 80,000 CZK (gain = 20,000 CZK)
        )

        // Case A: Sell all 20 units (gross proceeds = 80,000 CZK <= 100,000 CZK)
        val orderA = listOf(LotSaleOrder("taxable_1", 20.0))
        val resultA = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(lotTaxable),
            saleOrders = orderA,
            asOfDate = asOfDate,
            priorNonExemptGrossSalesAnnualCzk = 0.0
        )

        assertEquals(80_000.0, resultA.totalGrossProceedsCzk, 0.01)
        assertTrue(resultA.qualifiesFor100kExemption)
        assertEquals(0.0, resultA.taxableGainCzk, 0.001)
        assertEquals(0.0, resultA.estimatedTaxLiability15Pct, 0.001)

        // Case B: Prior sales this year were 40,000 CZK. Now 80,000 + 40,000 = 120,000 CZK > 100,000 CZK!
        val resultB = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(lotTaxable),
            saleOrders = orderA,
            asOfDate = asOfDate,
            priorNonExemptGrossSalesAnnualCzk = 40_000.0
        )

        assertFalse(resultB.qualifiesFor100kExemption)
        assertEquals(20_000.0, resultB.taxableGainCzk, 0.001) // 80k proceeds - 60k cost basis
        assertEquals(3_000.0, resultB.estimatedTaxLiability15Pct, 0.001) // 15% of 20,000 = 3,000 CZK
        assertEquals(4_600.0, resultB.estimatedTaxLiability23Pct, 0.001) // 23% of 20,000 = 4,600 CZK
    }

    @Test
    fun testMixedLotSaleWith3YearTimeTestExemption() {
        val asOfDate = LocalDate.of(2026, 6, 1)

        val exemptLot = InvestmentLot.fromIsoDate(
            id = "exempt_lot",
            ticker = "VWCE",
            purchaseDateIso = "2022-01-10", // > 3 years -> EXEMPT
            units = 100.0,
            acquisitionPricePerUnitCzk = 2000.0,
            currentPricePerUnitCzk = 3500.0 // Value 350,000 CZK, Gain 150,000 CZK
        )

        val taxableLot = InvestmentLot.fromIsoDate(
            id = "taxable_lot",
            ticker = "VWCE",
            purchaseDateIso = "2025-01-10", // 1.5 years -> TAXABLE
            units = 20.0,
            acquisitionPricePerUnitCzk = 2500.0,
            currentPricePerUnitCzk = 3500.0 // Value 70,000 CZK, Gain 20,000 CZK
        )

        val orders = listOf(
            LotSaleOrder("exempt_lot", 50.0), // 50 * 3500 = 175,000 CZK (exempt via 3y test)
            LotSaleOrder("taxable_lot", 10.0) // 10 * 3500 = 35,000 CZK (under 100k limit)
        )

        val result = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(exemptLot, taxableLot),
            saleOrders = orders,
            asOfDate = asOfDate,
            priorNonExemptGrossSalesAnnualCzk = 0.0
        )

        assertEquals(210_000.0, result.totalGrossProceedsCzk, 0.01)
        assertEquals(175_000.0, result.exemptGrossProceedsCzk, 0.01)
        assertEquals(35_000.0, result.nonExemptGrossProceedsCzk, 0.01)
        assertTrue(result.qualifiesFor100kExemption) // 35k non-exempt <= 100k limit
        assertEquals(0.0, result.taxableGainCzk, 0.001)
        assertEquals(0.0, result.estimatedTaxLiability15Pct, 0.001)
        assertTrue("Tax saved by 3y time test must reflect exempt gain", result.taxSavedBy3yTimeTestCzk > 10_000.0)
    }

    @Test
    fun testPortfolioAnalysisAndTaxOptimalHarvest() {
        val asOfDate = LocalDate.of(2026, 6, 1)

        val lots = listOf(
            InvestmentLot.fromIsoDate("lot1", "VWCE", "2021-05-01", 50.0, 2000.0, 3000.0), // Exempt (val 150k)
            InvestmentLot.fromIsoDate("lot2", "VWCE", "2023-08-01", 50.0, 2400.0, 3000.0), // Approaching (val 150k)
            InvestmentLot.fromIsoDate("lot3", "VWCE", "2025-01-01", 50.0, 2800.0, 3000.0)  // Taxable (val 150k)
        )

        val summary = CzechCapitalGainsEngine.analyzePortfolio(lots, asOfDate)
        assertEquals(450_000.0, summary.totalPortfolioValueCzk, 0.01)
        assertEquals(150_000.0, summary.exemptValueCzk, 0.01)
        assertEquals(150_000.0, summary.approachingExemptionValueCzk, 0.01)
        assertEquals(150_000.0, summary.taxableValueCzk, 0.01)
        assertEquals(100_000.0, summary.annualExemptionLimitRemainingCzk, 0.01)

        // Harvest 150,000 CZK cash: Should completely choose lot1 (exempt) with 0 tax liability
        val rec = CzechCapitalGainsEngine.recommendOptimalHarvest(lots, 150_000.0, asOfDate)
        assertEquals(150_000.0, rec.totalCashRaisedCzk, 0.01)
        assertEquals(1, rec.saleOrders.size)
        assertEquals("lot1", rec.saleOrders[0].lotId)
        assertEquals(0.0, rec.taxLiabilityCzk, 0.001)
    }

    // =========================================================================
    // 4. CZECH YEAR-END TAX CHECKLIST TESTS
    // =========================================================================

    @Test
    fun testYearEndChecklistDipDpsOptimizationHeadroom() {
        // Václav pays 1,700 CZK/mo into DIP (20,400 CZK/year) and 1,700 CZK/mo into DPS (0 CZK above 1,700 threshold).
        // Total qualifying = 20,400 CZK. Remaining headroom to 48,000 cap = 27,600 CZK.
        val settings = SettingsEntity(
            baseYear = 2026,
            dipContributionMonthly = 1700.0,
            dpsOwnContributionMonthly = 1700.0,
            dpsDeductionThresholdMonthly = 1700.0,
            taxDeductionCeilingAnnual = 48000.0
        )

        val report = CzechYearEndTaxChecklist.generateChecklist(settings, taxYear = 2026)
        val dipItem = report.items.first { it.id == "ret_dip_dps_deduction" }

        assertEquals(ChecklistStatus.ACTION_REQUIRED, dipItem.status)
        assertEquals(20_400.0, dipItem.currentValueCzk, 0.01)
        assertEquals(27_600.0, dipItem.remainingHeadroomCzk, 0.01)
        // 15% guaranteed tax saving on 27,600 CZK = 4,140 CZK
        assertEquals(4_140.0, dipItem.potentialTaxSavingCzk, 0.01)

        // Test fully optimized case: 4,000 CZK/mo into DIP = 48,000 CZK/year
        val maxedSettings = settings.copy(dipContributionMonthly = 4000.0)
        val maxedReport = CzechYearEndTaxChecklist.generateChecklist(maxedSettings, taxYear = 2026)
        val maxedDipItem = maxedReport.items.first { it.id == "ret_dip_dps_deduction" }

        assertEquals(ChecklistStatus.FULLY_OPTIMIZED, maxedDipItem.status)
        assertEquals(48_000.0, maxedDipItem.currentValueCzk, 0.01)
        assertEquals(0.0, maxedDipItem.remainingHeadroomCzk, 0.01)
    }

    @Test
    fun testYearEndChecklistEmployerMatchUtilization() {
        // Employer contributes 2,500 CZK/mo = 30,000 CZK/year. Statutory exemption limit is 50,000 CZK/year.
        val settings = SettingsEntity(
            baseYear = 2026,
            employerRetirementMonthly = 2500.0
        )
        val report = CzechYearEndTaxChecklist.generateChecklist(settings, taxYear = 2026)
        val empItem = report.items.first { it.id == "employer_contribution_exemption" }

        assertEquals(ChecklistStatus.ATTENTION_NEEDED, empItem.status)
        assertEquals(30_000.0, empItem.currentValueCzk, 0.01)
        assertEquals(20_000.0, empItem.remainingHeadroomCzk, 0.01)
    }

    @Test
    fun testYearEndChecklistSpouseTaxCreditRules() {
        // Case 1: Caring for child under 3, spouse income 0 CZK (eligible for 24,840 CZK discount)
        val s1 = SettingsEntity(
            baseYear = 2026,
            isSingleHousehold = false,
            hasChildUnder3 = true,
            eReturnYear = 2030 // not yet returned to work
        )
        val report1 = CzechYearEndTaxChecklist.generateChecklist(s1, taxYear = 2026, spouseOwnIncomeAnnual = 0.0)
        val spouseItem1 = report1.items.first { it.id == "spouse_tax_credit" }

        assertEquals(ChecklistStatus.FULLY_OPTIMIZED, spouseItem1.status)
        assertEquals(24_840.0, spouseItem1.potentialTaxSavingCzk, 0.01)

        // Case 2: Spouse income exceeds 68,000 CZK limit (e.g. 150,000 CZK)
        val report2 = CzechYearEndTaxChecklist.generateChecklist(s1, taxYear = 2026, spouseOwnIncomeAnnual = 150_000.0)
        val spouseItem2 = report2.items.first { it.id == "spouse_tax_credit" }

        assertEquals(ChecklistStatus.ATTENTION_NEEDED, spouseItem2.status)
        assertEquals(0.0, spouseItem2.potentialTaxSavingCzk, 0.001)

        // Case 3: No child under 3 (post-2024 consolidation package condition not met)
        val s3 = s1.copy(hasChildUnder3 = false)
        val report3 = CzechYearEndTaxChecklist.generateChecklist(s3, taxYear = 2026)
        val spouseItem3 = report3.items.first { it.id == "spouse_tax_credit" }

        assertEquals(ChecklistStatus.NOT_APPLICABLE, spouseItem3.status)
    }

    @Test
    fun testYearEndChecklistMortgageInterestCertificate() {
        val settings = SettingsEntity(baseYear = 2026)
        // Taxpayer paid 100,000 CZK in mortgage interest during the year
        val report = CzechYearEndTaxChecklist.generateChecklist(
            settings = settings,
            taxYear = 2026,
            mortgageInterestPaidAnnual = 100_000.0
        )
        val mortgageItem = report.items.first { it.id == "mortgage_interest_certificate" }

        assertEquals(ChecklistStatus.ACTION_REQUIRED, mortgageItem.status)
        assertEquals(100_000.0, mortgageItem.currentValueCzk, 0.01)
        assertEquals(150_000.0, mortgageItem.ceilingOrThresholdCzk, 0.01)
        assertEquals(15_000.0, mortgageItem.potentialTaxSavingCzk, 0.01) // 100,000 * 0.15 = 15,000 CZK
        assertTrue(mortgageItem.detailedAdvice.contains("15. února"))
    }

    // =========================================================================
    // 5. FINANCIAL ENGINE INTEGRATION TESTS
    // =========================================================================

    @Test
    fun testFinancialEngineCalculateIntegratesChecklistWithoutBreakingState() {
        val settings = SettingsEntity(baseYear = 2026)
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)

        assertNotNull(state)
        assertNotNull(state.yearEndTaxChecklist)
        assertTrue(state.yearEndTaxChecklist!!.items.isNotEmpty())
        assertTrue(state.yearEndTaxChecklist!!.totalPotentialTaxSavingsCzk >= 0.0)

        // Helper fields default to null when uncomputed, preserving immutability and memory
        assertNull(state.decumulationComparison)
        assertNull(state.capitalGainsSummary)
    }

    @Test
    fun testDomainModelingEnginesExecuteSmoothly() {
        val settings = SettingsEntity(baseYear = 2026)

        // 1. Decumulation comparison
        val decumulation = DecumulationStrategies.compareStrategies(
            DecumulationConfig(
                initialPortfolio = 8_000_000.0,
                initialAnnualWithdrawal = 320_000.0,
                horizonYears = 25,
                simulationsCount = 100
            )
        )
        assertNotNull(decumulation)
        assertTrue(decumulation.strategies.size == 3)

        // 2. Year-end tax checklist
        val checklist = CzechYearEndTaxChecklist.generateChecklist(settings)
        assertNotNull(checklist)
        assertTrue(checklist.items.isNotEmpty())

        // 3. Capital gains analysis
        val lots = listOf(
            InvestmentLot.fromIsoDate("l1", "VWCE", "2021-01-01", 10.0, 2000.0, 3000.0)
        )
        val cgSummary = CzechCapitalGainsEngine.analyzePortfolio(lots)
        assertNotNull(cgSummary)
        assertEquals(30_000.0, cgSummary.totalPortfolioValueCzk, 0.01)
    }
}
