package com.example

import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class FinancialEngineInvariantStressTest {

    @Test
    fun test1_1_TaxBracketBoundary() {
        val settingsBelow = SettingsEntity(vSalary = 100000.0) 
        val settingsAbove = SettingsEntity(vSalary = 200000.0) 
        
        val resultBelow = FinancialEngine.calculate(settingsBelow, runMonteCarlo = false)
        val resultAbove = FinancialEngine.calculate(settingsAbove, runMonteCarlo = false)
        
        assertTrue(resultBelow.fireBaseTargetToday.isFinite())
        assertTrue(resultAbove.fireBaseTargetToday.isFinite())
    }

    @Test
    fun test1_2_ChildTaxBonus() {
        val settings = SettingsEntity(child1Enabled = true, child2Enabled = true)
        val result = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertTrue(result.taxReturnHelper.childBonus > 0)
    }

    @Test
    fun test1_3_SpouseCreditCutoff() {
        val settingsBelow = SettingsEntity(
            spouseIncomeLimitAnnual = 68000.0,
            eStartingSalary = 1000.0, 
            eLecturingMonthly = 0.0,
            isSingleHousehold = false,
            eReturnYear = 2026,
            eIncludeLecturing = false,
            baseYear = 2026
        )
        val resultBelow = FinancialEngine.calculate(settingsBelow, runMonteCarlo = false)
        
        val settingsAbove = SettingsEntity(
            spouseIncomeLimitAnnual = 68000.0,
            eStartingSalary = 100000.0,
            isSingleHousehold = false,
            eReturnYear = 2026,
            baseYear = 2026
        )
        val resultAbove = FinancialEngine.calculate(settingsAbove, runMonteCarlo = false)
        
        assertTrue(resultBelow.taxReturnHelper.spouseCredit > 0)
        assertEquals(0.0, resultAbove.taxReturnHelper.spouseCredit, 1e-9)
    }

    @Test
    fun test1_4_Hyperinflation() {
        val settings = SettingsEntity(cpiInflationPct = 50.0)
        val result = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertTrue(result.fireBaseTargetToday.isFinite())
        assertTrue(result.netWorthTotal.isFinite())
        assertTrue(result.totalLivingCostMonthly.isFinite())
    }

    @Test
    fun test1_5_Deflation() {
        val settings = SettingsEntity(cpiInflationPct = -5.0)
        val result = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertTrue(result.fireBaseTargetToday.isFinite())
        assertTrue(result.fireBaseTargetToday > 0)
    }

    @Test
    fun test1_6_MarketCrash() {
        val settings = SettingsEntity(portfolioNominalReturnPct = -90.0)
        val result = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertTrue(result.fireBaseTargetToday.isFinite())
        for (point in result.dualTrajectory) {
            assertTrue("Portfolio should be >= 0", point.portfolio >= 0.0)
        }
    }

    @Test
    fun test1_7_Stagnation() {
        val settings = SettingsEntity(portfolioNominalReturnPct = 0.0, cpiInflationPct = 0.0)
        val result = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertTrue(result.fireBaseTargetToday.isFinite())
        assertTrue(result.dualTrajectory.size >= 2)
        assertTrue(result.dualTrajectory.last().portfolio >= result.dualTrajectory.first().portfolio)
    }

    @Test
    fun test1_8_ZeroLivingExpenses() {
        val settingsZero = SettingsEntity(lifestyleCostAtFireMonthly = 0.0)
        val result = FinancialEngine.calculate(settingsZero, runMonteCarlo = false)
        assertEquals(0.0, result.fireMilestones.standardFire.targetAmountToday, 1e-9)
        val target = FinancialEngine.fireTargetBase(settingsZero)
        assertEquals(0.0, target, 1e-9)
    }

    @Test
    fun test1_9_ZeroPortfolioZeroDCA() {
        val settings = SettingsEntity(
            liquidPortfolioCurrent = 0.0, eLiquidPortfolioCurrent = 0.0,
            portuDcaMonthly = 0.0, ePortuDcaMonthly = 0.0,
            lumpSumInclude = false, eReinvestedPct = 0.0,
            dpsBalanceCurrent = 0.0, dipBalanceCurrent = 0.0,
            dpsOwnContributionMonthly = 0.0, dipContributionMonthly = 0.0,
            employerRetirementAnnual = 0.0,
            eDpsBalanceCurrent = 0.0, eDipBalanceCurrent = 0.0,
            eDpsOwnContributionMonthly = 0.0, eDipContributionMonthly = 0.0,
            eEmployerRetirementAnnual = 0.0
        )
        val trajectory = FinancialEngine.buildLiquidPortfolio(settings, dualIncome = true)
        for (point in trajectory) {
            assertEquals(0.0, point.portfolio, 1e-9)
        }
    }

    @Test
    fun test1_10_DualSWRFloor() {
        val settings = SettingsEntity(safeWithdrawalRatePct = 0.0, lifestyleCostAtFireMonthly = 50000.0)
        val target = FinancialEngine.fireTargetBase(settings)
        assertTrue(target.isFinite())
        assertTrue(target > 0)
    }

    @Test
    fun test1_11_ZeroHorizon() {
        val settings = SettingsEntity(primaryAge = 67, statePensionAge = 67)
        val bridgeYears = FinancialEngine.statePensionBridgeYears(settings.primaryAge, settings)
        assertEquals(0, bridgeYears)
    }

    @Test
    fun test1_12_MonteCarloInvariant() {
        val settings = SettingsEntity()
        val result = FinancialEngine.runMonteCarlo(settings)
        for (point in result.fanPoints) {
            assertTrue("p5 <= p50 <= p95", point.p5 <= point.p50 && point.p50 <= point.p95)
        }
    }

    @Test
    fun test1_13_MCDeterminism() {
        val settings = SettingsEntity(monteCarloSeed = 42L)
        val result1 = FinancialEngine.runMonteCarlo(settings)
        val result2 = FinancialEngine.runMonteCarlo(settings)
        
        for (i in result1.fanPoints.indices) {
            assertEquals(result1.fanPoints[i].p50, result2.fanPoints[i].p50, 1e-9)
        }
    }

    @Test
    fun test1_14_MCSeedVariation() {
        val settings42 = SettingsEntity(monteCarloSeed = 42L)
        val result42 = FinancialEngine.runMonteCarlo(settings42)
        
        val settings99 = SettingsEntity(monteCarloSeed = 99L)
        val result99 = FinancialEngine.runMonteCarlo(settings99)
        
        var diffFound = false
        for (i in result42.fanPoints.indices) {
            if (abs(result42.fanPoints[i].p50 - result99.fanPoints[i].p50) > 1e-9) {
                diffFound = true
                break
            }
        }
        assertTrue("Different seeds should produce different results", diffFound)
    }

    @Test
    fun test1_15_SingleHousehold() {
        val settings = SettingsEntity(isSingleHousehold = true)
        val result = FinancialEngine.calculate(settings, runMonteCarlo = false)
        assertEquals(0.0, result.currentIncome.eleonoraSalary, 1e-9)
        assertEquals(0.0, result.currentIncome.benefit, 1e-9)
    }

    @Test
    fun test1_16_FIREMilestoneOrdering() {
        val settings = SettingsEntity()
        val result = FinancialEngine.calculate(settings, runMonteCarlo = false)
        val coast = result.fireMilestones.coastFire.targetAmountToday
        val lean = result.fireMilestones.leanFire.targetAmountToday
        val standard = result.fireMilestones.standardFire.targetAmountToday
        val fat = result.fireMilestones.fatFire.targetAmountToday
        
        assertTrue("coast <= lean", coast <= lean)
        assertTrue("lean <= standard", lean <= standard)
        assertTrue("standard <= fat", standard <= fat)
    }

    @Test
    fun test1_17_AnnuityFactor_ZeroRate() {
        assertEquals(30.0, FinancialEngine.annuityFactor(0.0, 30), 1e-9)
    }

    @Test
    fun test1_18_AnnuityFactor_ZeroYears() {
        assertEquals(0.0, FinancialEngine.annuityFactor(0.05, 0), 1e-9)
    }

    @Test
    fun test1_19_VaclavSalaryMonthly_PastYear() {
        val settings = SettingsEntity(baseYear = 2026)
        assertEquals(0.0, FinancialEngine.vaclavSalaryMonthly(2020, settings), 1e-9)
    }

    @Test
    fun test1_20_ChildMonthlyExpense_NotBorn() {
        val settings = SettingsEntity()
        assertEquals(0.0, FinancialEngine.childMonthlyExpense(2030, 2025, settings), 1e-9)
    }
}
