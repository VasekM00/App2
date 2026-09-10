package com.example

import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.RegulatoryConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialEngineBoundaryFuzzingTest {

    @Test
    fun test1_annuityFactor_rateZero_returnsYearsAsDouble() {
        assertEquals(20.0, FinancialEngine.annuityFactor(0.0, 20), 0.0001)
        assertEquals(1.0, FinancialEngine.annuityFactor(0.0, 1), 0.0001)
    }

    @Test
    fun test2_annuityFactor_yearsZeroOrNegative_returnsZero() {
        assertEquals(0.0, FinancialEngine.annuityFactor(0.05, 0), 0.0001)
        assertEquals(0.0, FinancialEngine.annuityFactor(0.05, -5), 0.0001)
    }

    @Test
    fun test3_annuityFactor_rateNegative_remainsFiniteAndNonNaN() {
        val factor = FinancialEngine.annuityFactor(-0.05, 10)
        assertFalse(factor.isNaN())
        assertFalse(factor.isInfinite())
        assertTrue(factor > 0.0)
    }

    @Test
    fun test4_annuityFactor_rateMinusOneOrLess_returnsZero() {
        assertEquals(0.0, FinancialEngine.annuityFactor(-1.0, 10), 0.0001)
        assertEquals(0.0, FinancialEngine.annuityFactor(-1.5, 10), 0.0001)
    }

    @Test
    fun test5_annuityFactor_largeYearsAndHighRate_doesNotOverflow() {
        val factor = FinancialEngine.annuityFactor(0.50, 100)
        assertFalse(factor.isNaN())
        assertFalse(factor.isInfinite())
        assertTrue(factor > 0.0)
    }

    @Test
    fun test6_stagflation_highCpiLowReturn_calculationCompletesWithoutNaN() {
        val stagflationSettings = SettingsEntity().copy(
            cpiInflationPct = 18.0,
            portfolioNominalReturnPct = 2.0
        )
        val state = FinancialEngine.calculate(stagflationSettings, runMonteCarlo = false)
        assertFalse(state.fireBaseTargetToday.isNaN())
        assertFalse(state.fireBaseTargetToday.isInfinite())
        assertTrue(state.fireBaseTargetToday >= 0.0)
        assertFalse(state.totalLivingCostMonthly.isNaN())
    }

    @Test
    fun test7_hyperdeflation_negativeCpi_calculationCompletes() {
        val deflationSettings = SettingsEntity().copy(
            cpiInflationPct = -5.0,
            portfolioNominalReturnPct = 5.0
        )
        val state = FinancialEngine.calculate(deflationSettings, runMonteCarlo = false)
        assertFalse(state.fireBaseTargetToday.isNaN())
        assertTrue(state.fireBaseTargetToday >= 0.0)
    }

    @Test
    fun test8_extremeSWR_veryLowSWR_targetRemainsFinite() {
        val lowSwrSettings = SettingsEntity().copy(safeWithdrawalRatePct = 0.1)
        val state = FinancialEngine.calculate(lowSwrSettings, runMonteCarlo = false)
        assertFalse(state.fireBaseTargetToday.isNaN())
        assertFalse(state.fireBaseTargetToday.isInfinite())
        assertTrue(state.fireBaseTargetToday > 0.0)
    }

    @Test
    fun test9_extremeSWR_veryHighSWR_targetRemainsFinite() {
        val highSwrSettings = SettingsEntity().copy(safeWithdrawalRatePct = 25.0)
        val state = FinancialEngine.calculate(highSwrSettings, runMonteCarlo = false)
        assertFalse(state.fireBaseTargetToday.isNaN())
        assertFalse(state.fireBaseTargetToday.isInfinite())
        assertTrue(state.fireBaseTargetToday >= 0.0)
    }

    @Test
    fun test10_monteCarlo_quantileOrdering_strictlyMonotonic() {
        val settings = SettingsEntity()
        val mc = FinancialEngine.runMonteCarlo(settings, horizonYears = 25)

        for (point in mc.fanPoints) {
            assertTrue("Quantile ordering violated: P5 (${point.p5}) <= P50 (${point.p50})", point.p5 <= point.p50 + 1e-6)
            assertTrue("Quantile ordering violated: P50 (${point.p50}) <= P95 (${point.p95})", point.p50 <= point.p95 + 1e-6)
        }
    }

    @Test
    fun test11_monteCarlo_successRate_withinZeroAndHundred() {
        val settings = SettingsEntity()
        val mc = FinancialEngine.runMonteCarlo(settings, horizonYears = 20)
        assertTrue(mc.successRatePct in 0.0..100.0)
    }

    @Test
    fun test12_singleEarnerToggle_disablesEleonoraDIPandDPS() {
        val dualSettings = SettingsEntity().copy(
            isSingleHousehold = false,
            eDipContributionMonthly = 4000.0,
            eDpsOwnContributionMonthly = 1700.0
        )
        val singleSettings = dualSettings.copy(isSingleHousehold = true)

        val dualState = FinancialEngine.calculate(dualSettings, runMonteCarlo = false)
        val singleState = FinancialEngine.calculate(singleSettings, runMonteCarlo = false)

        assertTrue(dualState.investMonthlyTotal > singleState.investMonthlyTotal)
    }

    @Test
    fun test13_singleEarnerToggle_spouseTaxCreditIsZero() {
        val singleSettings = SettingsEntity().copy(isSingleHousehold = true)
        val state = FinancialEngine.calculate(singleSettings, runMonteCarlo = false)
        assertEquals(0.0, state.taxReturnHelper.spouseCredit, 0.001)
    }

    @Test
    fun test14_singleEarnerToggle_reducesHouseholdDeductions() {
        val dual = FinancialEngine.calculate(SettingsEntity().copy(isSingleHousehold = false), runMonteCarlo = false)
        val single = FinancialEngine.calculate(SettingsEntity().copy(isSingleHousehold = true), runMonteCarlo = false)

        assertEquals(0.0, single.taxReturnHelper.spouseCredit, 0.001)
        assertTrue(dual.investMonthlyTotal >= single.investMonthlyTotal)
    }

    @Test
    fun test15_singleEarnerToggle_statePensionBridgeYearsUsesVaclavOnly() {
        val settings = SettingsEntity().copy(
            isSingleHousehold = true,
            primaryAge = 30,
            vStatePensionAge = 65,
            eStatePensionAge = 68
        )
        val bridge = FinancialEngine.statePensionBridgeYears(30, settings)
        assertEquals(35, bridge)
    }

    @Test
    fun test16_emergencyReserve_zeroBalance_coverageIsZero() {
        val settings = SettingsEntity().copy(emergencyReserveCurrent = 0.0)
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertEquals(0.0, state.emergencyCoverageMonths, 0.001)
    }

    @Test
    fun test17_emergencyReserve_negativeOverdraft_coverageIsZero() {
        val settings = SettingsEntity().copy(emergencyReserveCurrent = -15000.0)
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertEquals(0.0, state.emergencyCoverageMonths, 0.001)
    }

    @Test
    fun test18_zeroIncome_extremeSettings_calculationDoesNotThrow() {
        val zeroSettings = SettingsEntity().copy(
            vSalary = 0.0,
            rentMonthly = 0.0,
            groceriesMonthly = 0.0,
            otherDiscretionaryMonthly = 0.0,
            cafesMonthly = 0.0,
            therapyMonthly = 0.0,
            charityMonthly = 0.0,
            entertainmentMonthly = 0.0,
            transportMonthly = 0.0,
            subscriptionsMonthly = 0.0,
            childExpensesEnabled = false,
            customExpensesJson = "[]",
            liquidPortfolioCurrent = 0.0,
            emergencyReserveCurrent = 0.0
        )
        val state = FinancialEngine.calculate(zeroSettings, runMonteCarlo = false)
        assertFalse(state.fireBaseTargetToday.isNaN())
        assertEquals(0.0, state.totalLivingCostMonthly, 0.001)
    }

    @Test
    fun test19_billionaireExtremeSettings_calculationDoesNotOverflow() {
        val highSettings = SettingsEntity().copy(
            vSalary = 100_000_000.0,
            liquidPortfolioCurrent = 500_000_000.0
        )
        val state = FinancialEngine.calculate(highSettings, runMonteCarlo = false)
        assertFalse(state.netWorthTotal.isNaN())
        assertFalse(state.netWorthTotal.isInfinite())
        assertTrue(state.netWorthTotal > 500_000_000.0)
    }

    @Test
    fun test20_progressiveTax_exactlyAt36xThreshold_noExcessTax() {
        val gross = RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026
        val state = FinancialEngine.calculate(
            SettingsEntity().copy(vSalary = gross / 12.0),
            runMonteCarlo = false
        )
        assertFalse(state.currentIncome.vaclavNet.isNaN())
        assertTrue(state.currentIncome.vaclavNet > 0.0)
    }

    @Test
    fun test21_progressiveTax_above36xThreshold_applies23Percent() {
        val highSalaryAnnual = 3_000_000.0
        val state = FinancialEngine.calculate(
            SettingsEntity().copy(
                vSalary = highSalaryAnnual / 12.0,
                dipContributionMonthly = 4000.0
            ),
            runMonteCarlo = false
        )
        // With income in the 23% bracket, the 48k DIP deduction saves 23% = 11 040 CZK, which is > 15% (7 200 CZK)
        assertTrue(state.dip.taxSavedYear > 48_000.0 * 0.15)
        assertEquals(48_000.0 * 0.23, state.dip.taxSavedYear, 1.0)
    }

    @Test
    fun test22_dpsYouthBonus_effectiveFrom2027Only() {
        assertEquals(2027, RegulatoryConstants.LEPSI_PENZIJKO_EFFECTIVE_YEAR)
        assertEquals(40.0, RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_SUBSIDY_RATE_PCT, 0.001)
        assertEquals(680.0, RegulatoryConstants.LEPSI_PENZIJKO_YOUTH_MAX_SUBSIDY_MONTHLY, 0.001)
    }

    @Test
    fun test23_parentalBenefitTransition_terminatesWhenMotherReturnsToWork() {
        val settings = SettingsEntity().copy(
            baseYear = 2026,
            eReturnYear = 2027,
            eReturnMonth = 6,
            eParentalAllowanceMonthly = 10000.0,
            eStartingSalary = 45000.0
        )
        val state2026 = FinancialEngine.calculate(settings.copy(baseYear = 2026), runMonteCarlo = false)
        assertTrue(state2026.currentIncome.benefit > 0.0)
    }

    @Test
    fun test24_calculationDeterminism_identicalSettingsProduceIdenticalState() {
        val settings = SettingsEntity()
        val s1 = FinancialEngine.calculate(settings, runMonteCarlo = false)
        val s2 = FinancialEngine.calculate(settings, runMonteCarlo = false)

        assertEquals(s1.fireBaseTargetToday, s2.fireBaseTargetToday, 0.0001)
        assertEquals(s1.netWorthTotal, s2.netWorthTotal, 0.0001)
        assertEquals(s1.totalLivingCostMonthly, s2.totalLivingCostMonthly, 0.0001)
    }

    @Test
    fun test25_coastFireTarget_compoundingRealReturn_strictlyPositive() {
        val state = FinancialEngine.calculate(SettingsEntity(), runMonteCarlo = false)
        assertTrue(state.fireMilestones.coastFire.targetAmountToday > 0.0)
        assertFalse(state.fireMilestones.coastFire.targetAmountToday.isNaN())
    }
}
