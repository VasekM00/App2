package com.example

import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.util.BackupManager
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis

/**
 * Performance Benchmark Test Suite validating execution speed requirements:
 * 8.1: 1,000x FinancialEngine.calculate(settings, runMonteCarlo = false) completes in < 2,000ms.
 * 8.2: Single FinancialEngine.runMonteCarlo(settings, horizonYears = 35) completes in < 1,000ms.
 * 8.3: FinancialEngine.buildLiquidPortfolio(settings, dualIncome = true) for 35 years completes in < 10ms.
 * 8.4: BackupManager serialization + deserialization 100 round-trips complete in < 50ms.
 * 8.5: Formatters.fmtCompact(value) called 10,000x in < 200ms.
 */
@RunWith(RobolectricTestRunner::class)
class PerformanceBenchmarkTest {

    @Test
    fun `test 8_1 - 1000x FinancialEngine calculate without Monte Carlo completes in under 2000ms`() {
        val settings = SettingsEntity()
        // JIT Warmup
        repeat(50) {
            FinancialEngine.calculate(settings, runMonteCarlo = false)
        }

        val elapsedMs = measureTimeMillis {
            repeat(1000) {
                val state = FinancialEngine.calculate(settings, runMonteCarlo = false)
                assertNotNull(state)
            }
        }
        println("8.1: 1,000x calculate(runMonteCarlo = false) completed in $elapsedMs ms")
        assertTrue(
            "1,000x calculate(runMonteCarlo = false) took $elapsedMs ms, expected < 2,000ms",
            elapsedMs < 2000
        )
    }

    @Test
    fun `test 8_2 - Single FinancialEngine runMonteCarlo completes in under 1000ms`() {
        val settings = SettingsEntity(monteCarloN = 400)
        // JIT Warmup with different seed
        FinancialEngine.runMonteCarlo(settings.copy(monteCarloSeed = 1001), horizonYears = 35)

        val freshSettings = settings.copy(monteCarloSeed = 2026)
        val startTime = System.currentTimeMillis()
        val result = FinancialEngine.runMonteCarlo(freshSettings, horizonYears = 35)
        val elapsedMs = System.currentTimeMillis() - startTime

        assertNotNull(result)
        assertEquals(36, result.fanPoints.size)
        println("8.2: Single runMonteCarlo completed in $elapsedMs ms")
        assertTrue(
            "Single runMonteCarlo took $elapsedMs ms, expected < 1000ms",
            elapsedMs < 1000
        )
    }

    @Test
    fun `test 8_3 - FinancialEngine buildLiquidPortfolio for 35 years completes in under 10ms`() {
        val settings = SettingsEntity()
        // JIT Warmup
        repeat(100) {
            FinancialEngine.buildLiquidPortfolio(settings, dualIncome = true)
        }

        val startTime = System.currentTimeMillis()
        val trajectory = FinancialEngine.buildLiquidPortfolio(settings, dualIncome = true)
        val elapsedMs = System.currentTimeMillis() - startTime

        assertEquals(36, trajectory.size) // Base year + 35 projection years
        println("8.3: buildLiquidPortfolio completed in $elapsedMs ms")
        assertTrue(
            "buildLiquidPortfolio took $elapsedMs ms, expected < 10ms",
            elapsedMs < 10
        )
    }

    @Test
    fun `test 8_4 - BackupManager 100 serialization and deserialization round-trips complete in under 50ms`() {
        val original = SettingsEntity(
            vSalary = 48000.0,
            portuDcaMonthly = 15000.0,
            liquidPortfolioCurrent = 350000.0
        )
        // JIT Warmup
        val warmupJson = BackupManager.serializeSettingsToJson(original)
        BackupManager.deserializeSettingsFromJson(warmupJson, original)

        val elapsedMs = measureTimeMillis {
            var current = original
            repeat(100) {
                val json = BackupManager.serializeSettingsToJson(current)
                val restored = BackupManager.deserializeSettingsFromJson(json, original)
                assertNotNull(restored)
                current = restored!!
            }
            assertEquals(original.vSalary, current.vSalary, 0.001)
        }
        println("8.4: 100 BackupManager round-trips completed in $elapsedMs ms")
        assertTrue(
            "100 BackupManager round-trips took $elapsedMs ms, expected < 50ms",
            elapsedMs < 50
        )
    }

    @Test
    fun `test 8_5 - Formatters fmtCompact called 10000x completes in under 200ms`() {
        val testValues = doubleArrayOf(
            0.0, 150.0, 999.0, 12500.0, 95000.0, 350000.0, 1200000.0, 45000000.0, -25000.0, -1500000.0
        )
        // JIT Warmup
        for (v in testValues) {
            Formatters.fmtCompact(v)
        }

        val elapsedMs = measureTimeMillis {
            var checksum = 0
            val len = testValues.size
            for (i in 0 until 10000) {
                val value = testValues[i % len] + (i * 10.0)
                val str = Formatters.fmtCompact(value)
                checksum += str.length
            }
            assertTrue(checksum > 0)
        }
        println("8.5: 10,000x fmtCompact completed in $elapsedMs ms")
        assertTrue(
            "10,000x fmtCompact took $elapsedMs ms, expected < 200ms",
            elapsedMs < 200
        )
    }
}
