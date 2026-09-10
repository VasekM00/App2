package com.example

import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Withdrawal-phase ("will the money last?") simulation tests, plus time-machine checks
 * for the 2027 Lepší penzijko reform that must not regress as calendar years advance.
 */
class RetirementSurvivalTest {

    private val baseline = SettingsEntity(monteCarloN = 200)

    @Test
    fun survivalIsDeterministicAndCached() {
        val first = FinancialEngine.runRetirementSurvival(baseline)
        val second = FinancialEngine.runRetirementSurvival(baseline)
        assertEquals(first, second)
    }

    @Test
    fun survivalRatesAreWithinBoundsAndFinite() {
        val result = FinancialEngine.runRetirementSurvival(baseline)
        assertTrue(result.successRatePct in 0.0..100.0)
        assertTrue(result.fireProbabilityPct in 0.0..100.0)
        assertTrue(result.medianEndBalanceToday.isFinite())
        assertTrue(result.medianEndBalanceToday >= 0.0)
        assertTrue(result.p5EndBalanceToday.isFinite())
        assertTrue(result.p5EndBalanceToday >= 0.0)
        assertEquals(200, result.sampleSize)
        assertEquals(baseline.retirementHorizonYears, result.horizonYears)
    }

    @Test
    fun moreStartingCapitalNeverReducesSurvival() {
        val poor = FinancialEngine.runRetirementSurvival(
            baseline.copy(liquidPortfolioCurrent = 100_000.0, eLiquidPortfolioCurrent = 0.0)
        )
        val rich = FinancialEngine.runRetirementSurvival(
            baseline.copy(liquidPortfolioCurrent = 5_000_000.0, eLiquidPortfolioCurrent = 0.0)
        )
        assertTrue(rich.fireProbabilityPct >= poor.fireProbabilityPct)
        assertTrue(rich.successRatePct >= poor.successRatePct)
    }

    @Test
    fun longerRetirementHorizonNeverImprovesSurvival() {
        val short = FinancialEngine.runRetirementSurvival(
            baseline.copy(retirementHorizonYears = 20)
        )
        val long = FinancialEngine.runRetirementSurvival(
            baseline.copy(retirementHorizonYears = 45)
        )
        assertEquals(20, short.horizonYears)
        assertEquals(45, long.horizonYears)
        assertTrue(long.successRatePct <= short.successRatePct + 0.0001)
    }

    @Test
    fun unreachableFireTargetYieldsZeroSurvival() {
        val impossible = FinancialEngine.runRetirementSurvival(
            baseline.copy(
                liquidPortfolioCurrent = 0.0,
                eLiquidPortfolioCurrent = 0.0,
                fireTargetOverride = 1.0e15
            )
        )
        assertEquals(0.0, impossible.fireProbabilityPct, 0.0001)
        assertEquals(0.0, impossible.successRatePct, 0.0001)
    }

    @Test
    fun horizonIsClampedToSaneBounds() {
        val tooShort = FinancialEngine.runRetirementSurvival(baseline.copy(retirementHorizonYears = 2))
        val tooLong = FinancialEngine.runRetirementSurvival(baseline.copy(retirementHorizonYears = 999))
        assertEquals(10, tooShort.horizonYears)
        assertEquals(60, tooLong.horizonYears)
    }

    @Test
    fun singleHouseholdSurvivalRunsWithoutSpouseStreams() {
        val single = SettingsEntity(isSingleHousehold = true, monteCarloN = 150)
        val result = FinancialEngine.runRetirementSurvival(single)
        assertTrue(result.successRatePct in 0.0..100.0)
        assertTrue(result.fireProbabilityPct in 0.0..100.0)
    }

    @Test
    fun calculateExposesSurvivalAndSkipsItWhenMonteCarloDisabled() {
        val withMc = FinancialEngine.calculate(SettingsEntity(monteCarloN = 100), runMonteCarlo = true)
        assertNotNull(withMc.retirementSurvival)
        assertEquals(100, withMc.retirementSurvival.sampleSize)

        val withoutMc = FinancialEngine.calculate(SettingsEntity(monteCarloN = 100), runMonteCarlo = false)
        assertEquals(0, withoutMc.retirementSurvival.sampleSize)
        assertEquals(0.0, withoutMc.retirementSurvival.successRatePct, 0.001)
    }

    @Test
    fun dpsYouthSubsidyOnlyAppliesFrom2027ReformYear() {
        val settings = SettingsEntity(dpsYouthAgeLimit = 30)
        // Age 25 (youth) in 2026: reform not yet effective -> standard 20% cap (340).
        assertEquals(340.0, FinancialEngine.dpsSubsidy(1700.0, 25, settings, year = 2026), 0.001)
        // From 2027 the youth 40% cap (680) applies.
        assertEquals(680.0, FinancialEngine.dpsSubsidy(1700.0, 25, settings, year = 2027), 0.001)
        // Age 30+ is always standard.
        assertEquals(340.0, FinancialEngine.dpsSubsidy(1700.0, 30, settings, year = 2030), 0.001)
        // Below minimum deposit: no subsidy.
        assertEquals(0.0, FinancialEngine.dpsSubsidy(499.0, 25, settings, year = 2030), 0.001)
    }

    @Test
    fun parentalAllowancePotReflects2027BirthCutoff() {
        val before2027 = SettingsEntity(
            child1Enabled = true,
            child1BirthYear = 2026,
            child2Enabled = false
        )
        val from2027 = SettingsEntity(
            child1Enabled = true,
            child1BirthYear = 2027,
            child2Enabled = false
        )
        // By 2028 the smaller (350k) pot is partially exhausted while the higher (400k) pot still
        // pays the full monthly allowance, so the 2027 child must draw a strictly higher benefit.
        val monthlyBefore = FinancialEngine.eleonoraBenefitMonthly(2028, before2027)
        val monthlyAfter = FinancialEngine.eleonoraBenefitMonthly(2028, from2027)
        assertTrue(monthlyAfter > monthlyBefore)
    }
}
