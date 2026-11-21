package com.example.audit2

import com.example.data.SettingsEntity
import com.example.domain.CzechTaxEngine
import com.example.domain.DpsDipEngine
import com.example.domain.FinancialEngine
import com.example.domain.PresetProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.pow

class EngineAudit2Test {

    private val baseSettings = SettingsEntity()

    // =========================================================================
    // 1. CZECH TAX ENGINE & STATUTORY REFORMS (2024-2026)
    // =========================================================================

    @Test
    fun ok_dpsSubsidy_statutoryTiersAndCaps() {
        // Under 2024 reform: 20% between 500 and 1,700 CZK, max 340 CZK/mo
        assertEquals(0.0, CzechTaxEngine.dpsSubsidy(0.0, age = 30), 0.001)
        assertEquals(0.0, CzechTaxEngine.dpsSubsidy(499.0, age = 30), 0.001)
        assertEquals(100.0, CzechTaxEngine.dpsSubsidy(500.0, age = 30), 0.001)
        assertEquals(200.0, CzechTaxEngine.dpsSubsidy(1000.0, age = 30), 0.001)
        assertEquals(300.0, CzechTaxEngine.dpsSubsidy(1500.0, age = 30), 0.001)
        assertEquals(340.0, CzechTaxEngine.dpsSubsidy(1700.0, age = 30), 0.001)
        assertEquals(340.0, CzechTaxEngine.dpsSubsidy(2000.0, age = 30), 0.001)
        assertEquals(340.0, CzechTaxEngine.dpsSubsidy(5000.0, age = 30), 0.001)
    }

    @Test
    fun ok_annualRetirementDeduction_statutoryCeiling48k() {
        // DIP + qualifying DPS (above 1,700 CZK/mo) capped at 48,000 CZK/yr per earner
        val s = baseSettings.copy(
            isSingleHousehold = true,
            dipContributionMonthly = 4000.0, // 4,000 * 12 = 48,000 CZK
            dpsOwnContributionMonthly = 1700.0 // no excess above 1700 threshold
        )
        assertEquals(48000.0, CzechTaxEngine.annualRetirementDeduction(s), 0.001)

        val sExcess = s.copy(
            dipContributionMonthly = 6000.0, // 72,000 CZK -> should be capped at 48,000 CZK
            dpsOwnContributionMonthly = 3700.0 // 2,000 excess * 12 = 24,000 CZK
        )
        assertEquals(48000.0, CzechTaxEngine.annualRetirementDeduction(sExcess), 0.001)

        // Dual household: 48,000 * 2 = 96,000 CZK
        val sDual = sExcess.copy(
            isSingleHousehold = false,
            eDipContributionMonthly = 5000.0
        )
        assertEquals(96000.0, CzechTaxEngine.annualRetirementDeduction(sDual), 0.001)
    }

    @Test
    fun ok_netToGrossConversion_socialHealthAndProgressiveTax() {
        val netMonthly = 50000.0
        val grossAnnual = CzechTaxEngine.netToGrossAnnual(
            netMonthly = netMonthly,
            taxpayerCreditAnnual = 30840.0,
            highBracketThresholdAnnual = 1582812.0,
            baseTaxRatePct = 15.0,
            highTaxRatePct = 23.0
        )
        assertTrue("Gross annual should exceed net annual (600,000 CZK)", grossAnnual > 600000.0)
        val grossMonthly = grossAnnual / 12.0
        val socialHealth = grossMonthly * 0.116
        val taxBase = grossMonthly
        val taxGross = taxBase * 0.15
        val taxNet = max(0.0, taxGross - 2570.0)
        val computedNet = grossMonthly - socialHealth - taxNet
        assertEquals(netMonthly, computedNet, 1.0)
    }

    @Test
    fun ok_partnerTaxCredit_statutoryEligibility() {
        // Under Czech ZDP § 35ba(1)(b):
        // Eligible only if caring for a child under 3 AND partner own gross income <= 68,000 CZK/yr
        val sEligible = baseSettings.copy(
            isSingleHousehold = false,
            includeSpouseCredit = true,
            child1Enabled = true,
            child1BirthYear = 2025, // Age 1 in 2026 -> under 3
            child2Enabled = false,
            eStartingSalary = 0.0,
            eOtherInflowsMonthly = 0.0,
            eLecturingMonthly = 0.0,
            eIncludeLecturing = false
        )
        val credit2026 = FinancialEngine.annualPartnerTaxCredit(sEligible, year = 2026)
        assertEquals(24840.0, credit2026, 0.001)

        // In 2029, child1 is age 4 -> no child under 3 -> credit is 0
        val credit2029 = FinancialEngine.annualPartnerTaxCredit(sEligible, year = 2029)
        assertEquals(0.0, credit2029, 0.001)

        // When partner has lecturing (> 68k gross/yr) -> credit is 0
        val sPartnerWithIncome = sEligible.copy(eLecturingMonthly = 6900.0, eIncludeLecturing = true)
        val creditDisallowed = FinancialEngine.annualPartnerTaxCredit(sPartnerWithIncome, year = 2026)
        assertEquals(0.0, creditDisallowed, 0.001)
    }

    // =========================================================================
    // 2. FINANCIAL ENGINE & ACTUARIAL MATHEMATICS
    // =========================================================================

    @Test
    fun ok_fisherEquation_exactNetRealReturnRate() {
        val s = baseSettings.copy(
            portfolioNominalReturnPct = 7.0,
            dividendYieldPct = 1.8,
            dividendTaxRatePct = 15.0,
            cpiInflationPct = 2.8
        )
        val rReal = FinancialEngine.netRealReturnRate(s)
        val expected = (1.0 + 0.0673) / (1.0 + 0.028) - 1.0
        assertEquals(expected, rReal, 1e-6)
    }

    @Test
    fun ok_annuityFactor_positiveZeroAndNegativeRates() {
        val rPos = 0.04
        val n = 20
        val expectedPos = (1.0 - (1.0 + rPos).pow(-n)) / rPos
        assertEquals(expectedPos, FinancialEngine.annuityFactor(rPos, n), 1e-6)

        assertEquals(20.0, FinancialEngine.annuityFactor(0.0, 20), 1e-6)
        assertEquals(0.0, FinancialEngine.annuityFactor(0.04, 0), 1e-6)

        val rNeg = -0.02
        val expectedNeg = (1.0 - (1.0 + rNeg).pow(-n)) / rNeg
        assertEquals(expectedNeg, FinancialEngine.annuityFactor(rNeg, n), 1e-6)
        assertTrue(FinancialEngine.annuityFactor(rNeg, n) > n)
    }

    @Test
    fun ok_bridgeTo60Required_consistencyAcrossYears() {
        val s = baseSettings.copy(primaryAge = 30) // 30 years to 60
        val calc = FinancialEngine.calculate(s, runMonteCarlo = false)
        val annualLiving = calc.totalLivingCostMonthly * 12.0
        val rReal = FinancialEngine.netRealReturnRate(s)
        val expectedBridgeToday = annualLiving * FinancialEngine.annuityFactor(rReal, 30)
        assertEquals(expectedBridgeToday, calc.liquidBridgeTo60RequiredToday, 1.0)
    }

    @Test
    fun ok_netWorthTotal_strictlyAdditiveAcrossComponents() {
        val s = baseSettings.copy(
            liquidPortfolioCurrent = 500000.0,
            emergencyReserveCurrent = 200000.0,
            dpsBalanceCurrent = 150000.0,
            dipBalanceCurrent = 100000.0,
            isSingleHousehold = false,
            eLiquidPortfolioCurrent = 300000.0,
            eDpsBalanceCurrent = 50000.0,
            eDipBalanceCurrent = 25000.0
        )
        val calc = FinancialEngine.calculate(s, runMonteCarlo = false)
        val totalExpected = 500000.0 + 300000.0 + 200000.0 + (150000.0 + 100000.0 + 50000.0 + 25000.0)
        assertEquals(totalExpected, calc.netWorthTotal, 0.01)
        assertEquals(500000.0 + 300000.0, calc.currentLiquidPortfolio, 0.01)
        assertEquals(200000.0, calc.currentEmergencyReserve, 0.01)
        assertEquals(150000.0 + 100000.0 + 50000.0 + 25000.0, calc.currentPensionPortfolio, 0.01)
        assertEquals(calc.netWorthTotal, calc.currentLiquidPortfolio + calc.currentPensionPortfolio + calc.currentEmergencyReserve, 0.01)
    }

    @Test
    fun ok_singleHouseholdMode_completelyExcludesPartner() {
        val sSingle = baseSettings.copy(
            isSingleHousehold = true,
            vSalary = 80000.0,
            eStartingSalary = 50000.0,
            eLiquidPortfolioCurrent = 500000.0,
            eDpsBalanceCurrent = 100000.0,
            eDipBalanceCurrent = 100000.0,
            ePortuDcaMonthly = 10000.0,
            eDpsOwnContributionMonthly = 3000.0
        )
        val calc = FinancialEngine.calculate(sSingle, runMonteCarlo = false)
        assertEquals(0.0, calc.currentIncome.eleonoraSalary, 0.001)
        assertEquals(0.0, calc.currentIncome.eleonoraOther, 0.001)
        assertEquals(0.0, calc.currentIncome.benefit, 0.001)
        assertEquals(0.0, calc.currentIncome.lecturing, 0.001)
        assertEquals(sSingle.liquidPortfolioCurrent, calc.currentLiquidPortfolio, 0.001)
        assertEquals(sSingle.dpsBalanceCurrent + sSingle.dipBalanceCurrent, calc.currentPensionPortfolio, 0.001)
    }

    @Test
    fun ok_monteCarlo_deterministicWithExplicitSeed() {
        val s = baseSettings.copy(
            monteCarloSeed = 424242L,
            monteCarloN = 200
        )
        val run1 = FinancialEngine.runMonteCarlo(s)
        val run2 = FinancialEngine.runMonteCarlo(s)
        assertEquals(run1.successRatePct, run2.successRatePct, 0.001)
        assertEquals(run1.medianFireAge, run2.medianFireAge)
        assertEquals(run1.bestCaseAge, run2.bestCaseAge)
        assertEquals(run1.worstCaseAge, run2.worstCaseAge)
    }

    @Test
    fun ok_allPresets_loadValidFiniteStates() {
        val presets = PresetProfiles.ALL_PRESETS
        assertTrue(presets.isNotEmpty())
        for (profile in presets) {
            val transformedSettings = profile.transform(baseSettings)
            val calc = FinancialEngine.calculate(transformedSettings, runMonteCarlo = false)
            assertTrue("Net worth must be finite for ${profile.title}", calc.netWorthTotal.isFinite())
            assertTrue("Total living cost must be positive for ${profile.title}", calc.totalLivingCostMonthly > 0.0)
            assertTrue("Total income must be non-negative for ${profile.title}", calc.currentIncome.totalMonthly >= 0.0)
            assertTrue("FIRE target must be positive for ${profile.title}", calc.fireBaseTargetToday > 0.0)
        }
    }

    @Test
    fun ok_emergencyReserveTargetModes_behaveCorrectly() {
        val s = baseSettings.copy(
            rentMonthly = 25000.0,
            groceriesMonthly = 15000.0,
            otherDiscretionaryMonthly = 10000.0
        )
        val livingMonthly = FinancialEngine.totalLivingCostMonthly(s)
        assertTrue(livingMonthly >= 50000.0)

        val s3M = s.copy(emergencyReserveMode = "3M")
        val target3M = FinancialEngine.effectiveEmergencyReserveTarget(s3M)
        assertEquals(kotlin.math.round(livingMonthly * 3.0 / 1000.0) * 1000.0, target3M, 1.0)

        val s6M = s.copy(emergencyReserveMode = "6M")
        val target6M = FinancialEngine.effectiveEmergencyReserveTarget(s6M)
        assertEquals(kotlin.math.round(livingMonthly * 6.0 / 1000.0) * 1000.0, target6M, 1.0)

        val sFixed = s.copy(emergencyReserveMode = "Target", emergencyReserveTarget = 350000.0)
        assertEquals(350000.0, FinancialEngine.effectiveEmergencyReserveTarget(sFixed), 1.0)
    }

    @Test
    fun ok_extremeInputs_gracefulDegradationNoNaNs() {
        val sZero = baseSettings.copy(
            vSalary = 0.0,
            eStartingSalary = 0.0,
            liquidPortfolioCurrent = 0.0,
            emergencyReserveCurrent = 0.0,
            dpsBalanceCurrent = 0.0,
            dipBalanceCurrent = 0.0,
            portuDcaMonthly = 0.0,
            vMealVouchersMonthly = 0.0,
            vGiftsMonthly = 0.0,
            eParentalAllowanceMonthly = 0.0,
            eLecturingMonthly = 0.0,
            dipContributionMonthly = 0.0,
            dpsOwnContributionMonthly = 0.0,
            employerRetirementMonthly = 0.0,
            ePortuDcaMonthly = 0.0,
            eDipContributionMonthly = 0.0,
            eDpsOwnContributionMonthly = 0.0
        )
        val calc = FinancialEngine.calculate(sZero, runMonteCarlo = false)
        assertFalse(calc.netWorthTotal.isNaN())
        assertFalse(calc.fireBaseTargetToday.isNaN())
        assertFalse(calc.savingsRatePct.isNaN())
        assertEquals(0.0, calc.savingsRatePct, 0.001)

        val sHighInflation = baseSettings.copy(cpiInflationPct = 30.0, portfolioNominalReturnPct = 5.0)
        val calcInfl = FinancialEngine.calculate(sHighInflation, runMonteCarlo = false)
        assertFalse(calcInfl.liquidBridgeTo60RequiredToday.isNaN())
        assertTrue(calcInfl.liquidBridgeTo60RequiredToday > 0.0)
    }

    @Test
    fun ok_investMonthlyTotal_sumsAllMonthlyChannels() {
        val s = baseSettings.copy(
            isSingleHousehold = false,
            portuDcaMonthly = 15000.0,
            ePortuDcaMonthly = 5000.0,
            dipContributionMonthly = 4000.0,
            eDipContributionMonthly = 2000.0,
            dpsOwnContributionMonthly = 1700.0,
            eDpsOwnContributionMonthly = 1700.0,
            employerRetirementMonthly = 3000.0,
            eEmployerRetirementMonthly = 1000.0
        )
        val calc = FinancialEngine.calculate(s, runMonteCarlo = false)
        val expected = 15000.0 + 5000.0 + 4000.0 + 2000.0 + 1700.0 + 1700.0 + 3000.0 + 1000.0
        assertEquals(expected, calc.investMonthlyTotal, 0.001)
    }

    @Test
    fun ok_dpsDipProjection_marginCalculation() {
        val s = baseSettings.copy(primaryAge = 30)
        val proj = DpsDipEngine.buildDpsProjection(s)
        assertTrue("DPS balance at 60 must be positive", proj.dpsBalance > 0.0)
        assertTrue("ETF benchmark balance must be positive", proj.etfBalance > 0.0)
        assertEquals(proj.dpsBalance - proj.etfBalance, proj.margin, 0.01)
    }
}
