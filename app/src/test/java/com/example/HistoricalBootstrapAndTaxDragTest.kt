package com.example

import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying:
 * 1. Dividend tax drag calculation under Czech ZDP Section 8 on taxable brokerage assets.
 * 2. Historical block-bootstrap Monte Carlo simulation (1970-2025 empirical returns).
 * 3. Guyton-Klinger dynamic spending guardrails during retirement drawdown.
 * 4. Determinism, fan point ordering, and sequence resilience.
 */
class HistoricalBootstrapAndTaxDragTest {

    @Test
    fun test1_dividendTaxDrag_calculation_accurate() {
        val settingsDefault = SettingsEntity(
            dividendYieldPct = 1.8,
            dividendTaxRatePct = 15.0,
            portfolioNominalReturnPct = 7.0
        )

        // 1.8% * 15% = 0.27%
        val drag = FinancialEngine.dividendTaxDragPct(settingsDefault)
        assertEquals(0.27, drag, 0.001)

        // Net nominal return = 7.0% - 0.27% = 6.73%
        val netNominal = FinancialEngine.netTaxableNominalReturnPct(settingsDefault)
        assertEquals(6.73, netNominal, 0.001)

        // Custom yield and tax rate
        val settingsCustom = SettingsEntity(
            dividendYieldPct = 3.0,
            dividendTaxRatePct = 15.0,
            portfolioNominalReturnPct = 8.0
        )
        // 3.0% * 15% = 0.45%
        assertEquals(0.45, FinancialEngine.dividendTaxDragPct(settingsCustom), 0.001)
        // 8.0% - 0.45% = 7.55%
        assertEquals(7.55, FinancialEngine.netTaxableNominalReturnPct(settingsCustom), 0.001)
    }

    @Test
    fun test2_historicalBootstrap_datasetIntegrity() {
        val dataset = FinancialEngine.HISTORICAL_GLOBAL_EQUITY_RETURNS_PCT
        // 1970 to 2025 inclusive = 56 annual observations
        assertEquals(56, dataset.size)

        // Verify known market drawdowns exist in dataset
        assertTrue("1974 oil crisis drawdown must exist in dataset", dataset.any { it <= -25.0 })
        assertTrue("2002 dot-com bust drawdown must exist in dataset", dataset.any { it in -25.0..-15.0 })
        assertTrue("2008 Great Financial Crisis drawdown must exist in dataset", dataset.any { it <= -35.0 })
        assertTrue("2024 recent bull run return must exist in dataset", dataset.any { it >= 18.0 })
    }

    @Test
    fun test3_historicalBootstrap_generatesValidMonotonicFanPoints() {
        val settings = SettingsEntity(
            monteCarloN = 200,
            liquidPortfolioCurrent = 500000.0,
            portuDcaMonthly = 15000.0,
            useHistoricalBootstrap = true
        )

        val result = FinancialEngine.runHistoricalMonteCarlo(
            settings = settings,
            horizonYears = 35,
            initialCrashPct = 0.0,
            blockSize = 3
        )

        assertNotNull(result)
        assertTrue("Success rate must be between 0 and 100", result.successRatePct in 0.0..100.0)
        assertEquals("Fan points must span from year 0 to 35", 36, result.fanPoints.size)

        // Verify percentile ordering: P5 <= P50 <= P95 at all years
        for (point in result.fanPoints) {
            assertTrue("P5 <= P50 at year ${point.year}", point.p5 <= point.p50 + 0.01)
            assertTrue("P50 <= P95 at year ${point.year}", point.p50 <= point.p95 + 0.01)
        }
    }

    @Test
    fun test4_historicalBootstrap_deterministicWithSameSeed() {
        val settings1 = SettingsEntity(
            monteCarloN = 150,
            monteCarloSeed = 12345L,
            liquidPortfolioCurrent = 300000.0,
            portuDcaMonthly = 10000.0
        )
        val settings2 = SettingsEntity(
            monteCarloN = 150,
            monteCarloSeed = 12345L,
            liquidPortfolioCurrent = 300000.0,
            portuDcaMonthly = 10000.0
        )

        val run1 = FinancialEngine.runHistoricalMonteCarlo(settings1, horizonYears = 30)
        val run2 = FinancialEngine.runHistoricalMonteCarlo(settings2, horizonYears = 30)

        assertEquals("Success rate must match with identical seed", run1.successRatePct, run2.successRatePct, 0.0001)
        assertEquals("Median FIRE age must match", run1.medianFireAge, run2.medianFireAge)
        assertEquals("Worst case age must match", run1.worstCaseAge, run2.worstCaseAge)
        for (i in run1.fanPoints.indices) {
            assertEquals("P50 must match at year $i", run1.fanPoints[i].p50, run2.fanPoints[i].p50, 0.01)
        }
    }

    @Test
    fun test5_historicalBootstrap_initialCrash_reducesConfidence() {
        val settingsNoCrash = SettingsEntity(
            monteCarloN = 250,
            monteCarloSeed = 99L,
            liquidPortfolioCurrent = 2000000.0,
            portuDcaMonthly = 5000.0
        )
        val settingsCrash = settingsNoCrash.copy()

        val noCrash = FinancialEngine.runHistoricalMonteCarlo(settingsNoCrash, horizonYears = 30, initialCrashPct = 0.0)
        val severeCrash = FinancialEngine.runHistoricalMonteCarlo(settingsCrash, horizonYears = 30, initialCrashPct = 35.0)

        assertTrue(
            "Initial 35% crash should produce lower or equal median end wealth: ${severeCrash.fanPoints.last().p50} vs ${noCrash.fanPoints.last().p50}",
            severeCrash.fanPoints.last().p50 < noCrash.fanPoints.last().p50
        )
    }

    @Test
    fun test6_guytonKlingerGuardrails_survivalBehavior() {
        val settingsWithGuardrails = SettingsEntity(
            guardrailsEnabled = true,
            monteCarloN = 200,
            rentMonthly = 18950.0,
            groceriesMonthly = 12000.0,
            cafesMonthly = 3500.0
        )

        val survival = FinancialEngine.runRetirementSurvival(settingsWithGuardrails)

        assertTrue("Guardrails active flag should reflect settings", survival.guardrailsActive)
        assertTrue("Guardrail triggered percentage must be valid", survival.guardrailTriggeredPct in 0.0..100.0)

        // Test with guardrails disabled
        val settingsWithoutGuardrails = settingsWithGuardrails.copy(guardrailsEnabled = false)
        val survivalNoGuardrails = FinancialEngine.runRetirementSurvival(settingsWithoutGuardrails)
        assertTrue("Guardrails active flag must be false when disabled", !survivalNoGuardrails.guardrailsActive)
        assertEquals(0.0, survivalNoGuardrails.guardrailTriggeredPct, 0.001)
    }

    @Test
    fun test7_fullCalculationState_populatesBothEngines() {
        val settings = SettingsEntity(
            monteCarloN = 100,
            useHistoricalBootstrap = false
        )

        val state = FinancialEngine.calculate(settings, runMonteCarlo = true)

        assertNotNull("Parametric Monte Carlo must be populated", state.monteCarlo)
        assertNotNull("Historical Monte Carlo must be populated", state.historicalMonteCarlo)
        assertTrue("Parametric fan points must be populated", state.monteCarlo.fanPoints.isNotEmpty())
        assertTrue("Historical fan points must be populated", state.historicalMonteCarlo.fanPoints.isNotEmpty())
        assertEquals(36, state.monteCarlo.fanPoints.size)
        assertEquals(36, state.historicalMonteCarlo.fanPoints.size)
    }
}
