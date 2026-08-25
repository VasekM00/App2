package com.example

import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.parseCustomExpenses
import com.example.domain.parseCustomLifeGoals
import com.example.domain.parseCustomLumpSums
import com.example.domain.parseDeletedCategories
import com.example.util.BackupManager
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Exhaustive Property-Based and Fuzzing Stress Test Suite.
 * Covers extreme parameter spaces, mathematical singularity boundaries,
 * multi-iteration fuzzing across 1,000+ random configurations, and extreme edge conditions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AbsoluteStressFuzzingTest {

    @Test
    fun test1_fuzzing1000RandomPermutations_allCalculationsFiniteAndSafe() {
        val rng = Random(12345)

        for (iter in 1..1000) {
            val baseYear = rng.nextInt(1990, 2150)
            val primaryAge = rng.nextInt(15, 85)
            val isSingle = rng.nextBoolean()
            val statePensionAge = rng.nextInt(55, 75)

            val vSalary = rng.nextDouble(0.0, 500_000.0)
            val vBonus = rng.nextDouble(0.0, 1_000_000.0)
            val vMeal = rng.nextDouble(0.0, 10_000.0)
            val vOther = rng.nextDouble(0.0, 100_000.0)

            val eReturnYear = rng.nextInt(1990, 2150)
            val eReturnMonth = rng.nextInt(-2, 15)
            val eSalary = rng.nextDouble(0.0, 500_000.0)
            val eBonus = rng.nextDouble(0.0, 1_000_000.0)
            val eGrowth = rng.nextDouble(-10.0, 50.0)
            val eReinvest = rng.nextDouble(0.0, 100.0)
            val eParental = rng.nextDouble(0.0, 100_000.0)
            val eLecturing = rng.nextDouble(0.0, 50_000.0)
            val eOther = rng.nextDouble(0.0, 100_000.0)

            val nominalRet = rng.nextDouble(-50.0, 50.0)
            val cpi = rng.nextDouble(-20.0, 50.0)
            val swr = rng.nextDouble(-5.0, 25.0)
            val buffer = rng.nextDouble(-50.0, 100.0)

            val liquid = rng.nextDouble(0.0, 50_000_000.0)
            val eLiquid = rng.nextDouble(0.0, 50_000_000.0)
            val dca = rng.nextDouble(0.0, 200_000.0)
            val eDca = rng.nextDouble(0.0, 200_000.0)
            val dcaGrowth = rng.nextDouble(0.0, 20.0)

            val settings = SettingsEntity(
                baseYear = baseYear,
                primaryAge = primaryAge,
                isSingleHousehold = isSingle,
                statePensionAge = statePensionAge,
                vSalary = vSalary,
                vBonusAnnual = vBonus,
                vMealVouchersMonthly = vMeal,
                vOtherInflowsMonthly = vOther,
                eReturnYear = eReturnYear,
                eReturnMonth = eReturnMonth,
                eStartingSalary = eSalary,
                eBonusAnnual = eBonus,
                eSalaryGrowthPct = eGrowth,
                eReinvestedPct = eReinvest,
                eParentalAllowanceMonthly = eParental,
                eLecturingMonthly = eLecturing,
                eIncludeLecturing = rng.nextBoolean(),
                eOtherInflowsMonthly = eOther,
                portfolioNominalReturnPct = nominalRet,
                cpiInflationPct = cpi,
                safeWithdrawalRatePct = swr,
                safetyBufferPct = buffer,
                liquidPortfolioCurrent = liquid,
                eLiquidPortfolioCurrent = eLiquid,
                portuDcaMonthly = dca,
                ePortuDcaMonthly = eDca,
                dcaAnnualGrowthPct = dcaGrowth,
                monteCarloN = 100, // fast fuzzing
                monteCarloSeed = rng.nextLong(1L, 10000L)
            )

            // 1. Calculate without throwing
            val state = FinancialEngine.calculate(settings, runMonteCarlo = true)

            // 2. Invariant assertions
            assertTrue("netWorthTotal must be finite in iter $iter", state.netWorthTotal.isFinite())
            assertTrue("savingsRatePct must be finite in iter $iter", state.savingsRatePct.isFinite())
            assertTrue("totalLivingCostMonthly must be finite in iter $iter", state.totalLivingCostMonthly.isFinite())
            assertTrue("fireBaseTargetToday must be finite in iter $iter", state.fireBaseTargetToday.isFinite())
            assertTrue("fireBaseTargetToday must be non-negative in iter $iter", state.fireBaseTargetToday >= 0.0)

            for (pt in state.dualTrajectory) {
                assertTrue("Trajectory portfolio value must be finite in iter $iter", pt.portfolio.isFinite())
                assertTrue("Trajectory portfolio value must be non-negative in iter $iter", pt.portfolio >= 0.0)
                assertTrue("Trajectory target value must be finite in iter $iter", pt.target.isFinite())
                assertTrue("Trajectory target value must be non-negative in iter $iter", pt.target >= 0.0)
            }

            for (mc in state.monteCarlo.fanPoints) {
                assertTrue("MC P5 <= P50 in iter $iter", mc.p5 <= mc.p50 + 1e-6)
                assertTrue("MC P50 <= P95 in iter $iter", mc.p50 <= mc.p95 + 1e-6)
                assertTrue("MC P5 must be non-negative in iter $iter", mc.p5 >= 0.0)
            }

            // 3. Formatters safety check
            val strCzk = Formatters.fmtCZK(state.netWorthTotal)
            val strCompact = Formatters.fmtCompact(state.netWorthTotal)
            val strPct = Formatters.fmtPct(state.savingsRatePct)
            assertFalse("Formatted string cannot be empty", strCzk.isEmpty())
            assertFalse("Formatted string cannot be empty", strCompact.isEmpty())
            assertFalse("Formatted string cannot be empty", strPct.isEmpty())

            // 4. Backup round-trip safety
            val json = BackupManager.serializeSettingsToJson(settings)
            val restored = BackupManager.deserializeSettingsFromJson(json, SettingsEntity())
            assertNotNull("Restored settings must not be null in iter $iter", restored)
        }
    }

    @Test
    fun test2_theZeroEconomy_allZeroSettings() {
        val zero = SettingsEntity(
            vSalary = 0.0,
            vBonusAnnual = 0.0,
            vMealVouchersMonthly = 0.0,
            vOtherInflowsMonthly = 0.0,
            eStartingSalary = 0.0,
            eBonusAnnual = 0.0,
            eParentalAllowanceMonthly = 0.0,
            eLecturingMonthly = 0.0,
            eOtherInflowsMonthly = 0.0,
            familyGiftMonthly = 0.0,
            annualOtherGifts = 0.0,
            lumpSumAmount = 0.0,
            liquidPortfolioCurrent = 0.0,
            eLiquidPortfolioCurrent = 0.0,
            portuDcaMonthly = 0.0,
            ePortuDcaMonthly = 0.0,
            dpsBalanceCurrent = 0.0,
            eDpsBalanceCurrent = 0.0,
            dipBalanceCurrent = 0.0,
            eDipBalanceCurrent = 0.0,
            emergencyReserveCurrent = 0.0,
            emergencyReserveTarget = 0.0,
            lifestyleCostAtFireMonthly = 0.0,
            rentMonthly = 0.0,
            groceriesMonthly = 0.0,
            cafesMonthly = 0.0,
            therapyMonthly = 0.0,
            charityMonthly = 0.0,
            entertainmentMonthly = 0.0,
            transportMonthly = 0.0,
            subscriptionsMonthly = 0.0,
            otherDiscretionaryMonthly = 0.0,
            childExpensesEnabled = false,
            childToddlerMonthly = 0.0,
            childPreschoolMonthly = 0.0,
            childSchoolMonthly = 0.0,
            childTeenMonthly = 0.0,
            childUniMonthly = 0.0
        )

        val state = FinancialEngine.calculate(zero, runMonteCarlo = false)
        assertEquals(0.0, state.netWorthTotal, 0.001)
        assertEquals(0.0, state.totalLivingCostMonthly, 0.001)
        assertEquals(0.0, state.fireBaseTargetToday, 0.001)
        assertEquals(0.0, state.savingsRatePct, 0.001)
    }

    @Test
    fun test3_theHyperinflationWarEconomy() {
        val hyper = SettingsEntity(
            cpiInflationPct = 250.0,
            portfolioNominalReturnPct = -50.0,
            lifestyleCostAtFireMonthly = 100_000.0,
            rentMonthly = 50_000.0
        )

        val state = FinancialEngine.calculate(hyper, runMonteCarlo = false)
        assertTrue(state.fireBaseTargetToday.isFinite())
        for (pt in state.dualTrajectory) {
            assertTrue(pt.portfolio >= 0.0)
            assertTrue(pt.target.isFinite())
        }
    }

    @Test
    fun test4_theGreatDepressionDeflation() {
        val depression = SettingsEntity(
            cpiInflationPct = -25.0,
            portfolioNominalReturnPct = -90.0,
            safeWithdrawalRatePct = 2.0
        )

        val state = FinancialEngine.calculate(depression, runMonteCarlo = false)
        assertTrue(state.fireBaseTargetToday.isFinite())
        assertTrue(state.fireBaseTargetToday >= 0.0)
        for (pt in state.dualTrajectory) {
            assertTrue(pt.portfolio >= 0.0)
            assertTrue(pt.target >= 0.0)
        }
    }

    @Test
    fun test5_theTrillionaireBoundary() {
        val trillionaire = SettingsEntity(
            liquidPortfolioCurrent = 500_000_000_000.0,
            eLiquidPortfolioCurrent = 500_000_000_000.0,
            emergencyReserveCurrent = 0.0,
            dpsBalanceCurrent = 0.0,
            eDpsBalanceCurrent = 0.0,
            dipBalanceCurrent = 0.0,
            eDipBalanceCurrent = 0.0,
            vSalary = 50_000_000.0,
            lifestyleCostAtFireMonthly = 10_000_000.0
        )

        val state = FinancialEngine.calculate(trillionaire, runMonteCarlo = false)
        assertEquals(1_000_000_000_000.0, state.netWorthTotal, 1.0)
        val compactStr = Formatters.fmtCompact(state.netWorthTotal)
        assertTrue("Formatted trillionaire should contain 'B': $compactStr", compactStr.contains("B"))
    }

    @Test
    fun test6_instantRetirementAtAge18() {
        val young = SettingsEntity(
            primaryAge = 18,
            statePensionAge = 65,
            lifestyleCostAtFireMonthly = 40_000.0,
            statePensionMonthly = 20_000.0
        )

        val state = FinancialEngine.calculate(young, runMonteCarlo = false)
        assertTrue(state.fireBaseTargetToday > 0.0)
        assertEquals(47, FinancialEngine.statePensionBridgeYears(18, young))
    }

    @Test
    fun test7_postPensionRetirementAtAge75() {
        val senior = SettingsEntity(
            primaryAge = 75,
            statePensionAge = 65,
            lifestyleCostAtFireMonthly = 20_000.0,
            statePensionMonthly = 25_000.0 // pension exceeds lifestyle cost
        )

        val state = FinancialEngine.calculate(senior, runMonteCarlo = false)
        assertEquals(0, FinancialEngine.statePensionBridgeYears(75, senior))
        assertEquals(0.0, state.fireBaseTargetToday, 0.001)
    }

    @Test
    fun test8_eleonoraMonthMatrixAllProportions() {
        for (month in 1..12) {
            val settings = SettingsEntity(
                baseYear = 2029,
                eReturnYear = 2029,
                eReturnMonth = month,
                eStartingSalary = 24_000.0,
                eBonusAnnual = 0.0,
                eParentalAllowanceMonthly = 12_000.0,
                eLecturingMonthly = 6_000.0
            )

            val inc = FinancialEngine.householdIncome(2029, settings)
            val expectedWorkFraction = (13 - month) / 12.0
            val expectedLeaveFraction = (month - 1) / 12.0

            assertEquals(24_000.0 * expectedWorkFraction, inc.eleonoraSalary, 0.001)
            assertEquals(12_000.0 * expectedLeaveFraction, inc.benefit, 0.001)
            assertEquals(6_000.0 * expectedLeaveFraction, inc.lecturing, 0.001)
        }
    }

    @Test
    fun test9_corruptedAndInjectionJsonSafety() {
        val corruptedJsonInputs = listOf(
            "",
            "   ",
            "null",
            "{broken",
            "[{\"name\": \"<script>alert(1)</script>\", \"amount\": \"not_a_number\"}]",
            "DROP TABLE app_settings;--",
            "{\"custom\": 123}",
            "\uD83D\uDE00\uD83D\uDE80", // emoji surrogates
            "[[[[[[[]]]]]]]"
        )

        for (input in corruptedJsonInputs) {
            val expenses = parseCustomExpenses(input)
            val goals = parseCustomLifeGoals(input)
            val lumps = parseCustomLumpSums(input)
            val deleted = parseDeletedCategories(input)

            assertNotNull(expenses)
            assertNotNull(goals)
            assertNotNull(lumps)
            assertNotNull(deleted)
        }
    }

    @Test
    fun test10_formatterEdgeUniverse() {
        val edgeDoubles = listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            -0.0,
            0.0,
            1e-15,
            -1e-15,
            99.99,
            100_000.0,
            1_000_000.0,
            1_500_000.0,
            1_000_000_000.0,
            1e15,
            -1e15
        )

        for (v in edgeDoubles) {
            val czk = Formatters.fmtCZK(v)
            val compact = Formatters.fmtCompact(v)
            val pct = Formatters.fmtPct(v)
            val num = Formatters.fmtNum(v)
            val r1k = Formatters.roundTo1k(v)
            val r10k = Formatters.roundTo10k(v)
            val rDisp = Formatters.roundToDisplay(v)

            assertFalse("fmtCZK for $v should not be empty", czk.isEmpty())
            assertFalse("fmtCompact for $v should not be empty", compact.isEmpty())
            assertFalse("fmtPct for $v should not be empty", pct.isEmpty())
            assertFalse("fmtNum for $v should not be empty", num.isEmpty())
            assertTrue("roundTo1k for $v must be finite", r1k.isFinite())
            assertTrue("roundTo10k for $v must be finite", r10k.isFinite())
            assertTrue("roundToDisplay for $v must be finite", rDisp.isFinite())
        }
    }
}
