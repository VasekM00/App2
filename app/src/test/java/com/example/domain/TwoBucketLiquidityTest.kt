package com.example.domain

import com.example.data.SettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.pow

class TwoBucketLiquidityTest {

    @Test
    fun testLiquidBridgeTo60PresentValueCalculation() {
        val settings = SettingsEntity(
            primaryAge = 40,
            rentMonthly = 20000.0,
            groceriesMonthly = 15000.0,
            cafesMonthly = 5000.0,
            transportMonthly = 3000.0,
            subscriptionsMonthly = 1000.0,
            otherDiscretionaryMonthly = 5000.0,
            child1Enabled = false,
            child2Enabled = false,
            portfolioNominalReturnPct = 8.0,
            cpiInflationPct = 3.0,
            isSingleHousehold = true,
            liquidPortfolioCurrent = 2000000.0,
            dpsBalanceCurrent = 5000000.0,
            dipBalanceCurrent = 5000000.0
        )

        val fullState = FinancialEngine.calculate(settings)
        val annualLiving = fullState.totalLivingCostMonthly * 12.0
        val yearsTo60 = 20
        val rReal = (settings.portfolioNominalReturnPct - settings.cpiInflationPct) / 100.0 // 0.05
        val expectedPv = annualLiving * ((1.0 - (1.0 + rReal).pow(-yearsTo60)) / rReal)

        assertEquals(expectedPv, fullState.liquidBridgeTo60RequiredToday, 1.0)
        // Liquid portfolio is 2M, expected Pv is ~7.4M, so liquid bridge is not funded
        assertFalse(fullState.isLiquidBridgeFundedToday)
        assertEquals(expectedPv - 2000000.0, fullState.liquidBridgeDeficitToday, 1.0)
    }

    @Test
    fun testBridgeConstrainedStatusWhenTotalWealthReachesTargetButLiquidDeficit() {
        // High pension balance (DIP+DPS) so total net worth reaches FIRE target, but liquid portfolio is low
        val settings = SettingsEntity(
            primaryAge = 45,
            rentMonthly = 10000.0,
            groceriesMonthly = 5000.0,
            child1Enabled = false,
            child2Enabled = false,
            portfolioNominalReturnPct = 8.0,
            cpiInflationPct = 3.0,
            safeWithdrawalRatePct = 3.5,
            liquidPortfolioCurrent = 100000.0, // Low liquid
            dpsBalanceCurrent = 5000000.0,
            dipBalanceCurrent = 5000000.0 // 10M locked pension
        )

        val points = FinancialEngine.buildLiquidPortfolio(settings, dualIncome = true)
        val initialPoint = points.first()

        // Total portfolio at year 0 is 10.1M, which exceeds annual living / 0.035
        assertTrue(initialPoint.totalPortfolio >= initialPoint.target)
        // Liquid is only 100k, burn to age 60 requires several million
        assertFalse(initialPoint.isLiquidBridgeFunded)
        assertEquals("Bridge Constrained", initialPoint.status)
    }

    @Test
    fun testFireOkStatusWhenLiquidBridgeFunded() {
        val settings = SettingsEntity(
            primaryAge = 55,
            rentMonthly = 10000.0,
            groceriesMonthly = 5000.0,
            child1Enabled = false,
            child2Enabled = false,
            portfolioNominalReturnPct = 8.0,
            cpiInflationPct = 3.0,
            safeWithdrawalRatePct = 3.5,
            liquidPortfolioCurrent = 10000000.0, // Plentiful liquid capital
            dpsBalanceCurrent = 1000000.0,
            dipBalanceCurrent = 1000000.0
        )

        val points = FinancialEngine.buildLiquidPortfolio(settings, dualIncome = true)
        val initialPoint = points.first()

        assertTrue(initialPoint.totalPortfolio >= initialPoint.target)
        assertTrue(initialPoint.isLiquidBridgeFunded)
        assertEquals("FIRE OK", initialPoint.status)
    }

    @Test
    fun testAge60OrAboveAlwaysBypassesBridgeConstraint() {
        val settings = SettingsEntity(
            primaryAge = 60,
            rentMonthly = 10000.0,
            groceriesMonthly = 5000.0,
            child1Enabled = false,
            child2Enabled = false,
            portfolioNominalReturnPct = 8.0,
            cpiInflationPct = 3.0,
            safeWithdrawalRatePct = 3.5,
            liquidPortfolioCurrent = 0.0, // Zero liquid, but already 60
            dpsBalanceCurrent = 6000000.0,
            dipBalanceCurrent = 6000000.0
        )

        val points = FinancialEngine.buildLiquidPortfolio(settings, dualIncome = true)
        val initialPoint = points.first()

        // At age 60, locked pension is fully unlocked under Rule 60+10
        assertTrue(initialPoint.isLiquidBridgeFunded)
        assertEquals(0.0, initialPoint.liquidBridgeTo60Required, 0.01)
        assertEquals("FIRE OK", initialPoint.status)
    }

    @Test
    fun testFireLiquidBridgePointIdentifiedInFullCalculationState() {
        val settings = SettingsEntity(
            primaryAge = 35,
            rentMonthly = 15000.0,
            groceriesMonthly = 10000.0,
            vSalary = 80000.0,
            child1Enabled = false,
            child2Enabled = false,
            portfolioNominalReturnPct = 8.0,
            cpiInflationPct = 3.0,
            safeWithdrawalRatePct = 3.5,
            liquidPortfolioCurrent = 500000.0,
            dpsBalanceCurrent = 200000.0,
            dipBalanceCurrent = 200000.0
        )

        val fullState = FinancialEngine.calculate(settings)
        val bridgePoint = fullState.fireLiquidBridgePoint
        assertNotNull(bridgePoint)
        assertTrue(bridgePoint!!.totalPortfolio >= bridgePoint.target)
        assertTrue(bridgePoint.isLiquidBridgeFunded)
    }

    @Test
    fun testSingleHouseholdBridgePointUsesSingleTrajectory() {
        val settings = SettingsEntity(
            primaryAge = 35,
            rentMonthly = 15000.0,
            groceriesMonthly = 10000.0,
            vSalary = 80000.0,
            isSingleHousehold = true,
            child1Enabled = false,
            child2Enabled = false,
            portfolioNominalReturnPct = 8.0,
            cpiInflationPct = 3.0,
            safeWithdrawalRatePct = 3.5,
            liquidPortfolioCurrent = 500000.0,
            dpsBalanceCurrent = 200000.0,
            dipBalanceCurrent = 200000.0
        )

        val fullState = FinancialEngine.calculate(settings)
        val bridgePoint = fullState.fireLiquidBridgePoint
        val singlePoint = fullState.fireSinglePoint
        assertNotNull(bridgePoint)
        assertNotNull(singlePoint)
        assertTrue(bridgePoint!!.year >= singlePoint!!.year)
        // Verify bridgePoint matches a point from singleTrajectory
        assertTrue(fullState.singleTrajectory.any { it.year == bridgePoint.year && it.age == bridgePoint.age })
    }

    @Test
    fun testActiveLedgerSnapshotFlowsIntoBridgeBalances() {
        val settings = SettingsEntity(
            primaryAge = 40,
            rentMonthly = 20000.0,
            groceriesMonthly = 15000.0,
            child1Enabled = false,
            child2Enabled = false,
            portfolioNominalReturnPct = 8.0,
            cpiInflationPct = 3.0,
            isSingleHousehold = true,
            liquidPortfolioCurrent = 1000000.0,
            dpsBalanceCurrent = 500000.0,
            dipBalanceCurrent = 500000.0
        )

        val activeLedger = com.example.data.LedgerEntryEntity(
            yearMonth = "2026-09",
            portfolioBalanceAtMonthEnd = 3000000.0,
            pensionBalanceAtMonthEnd = 1500000.0
        )

        val fullState = FinancialEngine.calculate(settings, activeLedgerEntry = activeLedger)
        assertEquals(3000000.0, fullState.currentLiquidPortfolio, 0.01)
        assertEquals(1500000.0, fullState.currentPensionPortfolio, 0.01)
    }

    @Test
    fun testLiquidBridgeRequiredAtFutureYearAccountsForInflation() {
        val settings = SettingsEntity(
            primaryAge = 40,
            rentMonthly = 20000.0,
            groceriesMonthly = 15000.0,
            child1Enabled = false,
            child2Enabled = false,
            portfolioNominalReturnPct = 8.0,
            cpiInflationPct = 3.0,
            isSingleHousehold = true
        )

        val points = FinancialEngine.buildLiquidPortfolio(settings, dualIncome = false)
        // Point at index 5 (year = baseYear + 5, age = 45)
        val p5 = points[5]
        assertEquals(settings.baseYear + 5, p5.year)
        assertEquals(45, p5.age)

        val yearsTo60 = 60 - p5.age // 15 years
        val yearsElapsed = p5.year - settings.baseYear // 5 years
        val inflationFactor = (1.0 + settings.cpiInflationPct / 100.0).pow(yearsElapsed)
        val expectedLivingAtP5 = FinancialEngine.totalLivingCostMonthly(settings, p5.year) * 12.0 * inflationFactor
        val rReal = (settings.portfolioNominalReturnPct - settings.cpiInflationPct) / 100.0
        val expectedPv = expectedLivingAtP5 * ((1.0 - (1.0 + rReal).pow(-yearsTo60)) / rReal)

        assertEquals(expectedPv, p5.liquidBridgeTo60Required, 1.0)
        assertTrue(p5.liquidBridgeTo60Required > 0.0)
    }
}

