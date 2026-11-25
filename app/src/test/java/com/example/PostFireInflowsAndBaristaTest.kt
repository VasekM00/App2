package com.example

import com.example.data.SettingsEntity
import com.example.domain.CustomLumpSumItem
import com.example.domain.DecumulationConfig
import com.example.domain.DecumulationStrategies
import com.example.domain.DecumulationStrategyType
import com.example.domain.FinancialEngine
import com.example.domain.serializeCustomLumpSums
import com.example.util.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PostFireInflowsAndBaristaTest {

    private fun cleanSettings(
        baseYear: Int = 2026,
        primaryAge: Int = 55
    ): SettingsEntity = SettingsEntity.freshDefaults().copy(
        baseYear = baseYear,
        primaryAge = primaryAge,
        isSingleHousehold = true,
        childExpensesEnabled = false,
        customGoalsJson = "[]",
        customLumpSumsJson = "[]",
        lumpSumInclude = false,
        lumpSumAmount = 0.0,
        vGiftsMonthly = 0.0,
        eGiftsMonthly = 0.0,
        vStatePensionMonthly = 0.0,
        eStatePensionMonthly = 0.0,
        dividendYieldPct = 0.0,
        dividendTaxRatePct = 0.0,
        cpiInflationPct = 0.0,
        rentGrowthPct = 0.0,
        portfolioNominalReturnPct = 6.0,
        emergencyReserveCurrent = 0.0,
        emergencyReserveTarget = 0.0,
        dpsBalanceCurrent = 0.0,
        dipBalanceCurrent = 0.0,
        dpsOwnContributionMonthly = 0.0,
        dipContributionMonthly = 0.0,
        employerRetirementMonthly = 0.0
    )

    @Test
    fun postFireLumpSum_surplusDepositsIntoLiquidPortfolioAndCompounds() {
        val base = cleanSettings(baseYear = 2026, primaryAge = 60).copy(
            liquidPortfolioCurrent = 12_000_000.0,
            fireTargetOverride = 10_000_000.0,
            lifestyleCostAtFireMonthly = 35_000.0 // 420,000 / year
        )
        val withLump = base.copy(
            lumpSumInclude = true,
            lumpSumYear = 2028,
            lumpSumAmount = 5_000_000.0
        )

        val noLumpTraj = FinancialEngine.buildLiquidPortfolio(base, dualIncome = false)
        val lumpTraj = FinancialEngine.buildLiquidPortfolio(withLump, dualIncome = false)

        val pt2029NoLump = noLumpTraj.first { it.year == 2029 }
        val pt2029WithLump = lumpTraj.first { it.year == 2029 }
        val diff = pt2029WithLump.portfolio - pt2029NoLump.portfolio

        // True surplus (5,000,000 - 420,000 net + return) must land inside portfolio rather than vanishing at 420k
        assertTrue(
            "Expected post-FIRE lump sum surplus > 4,500,000 CZK in portfolio, got diff=$diff",
            diff > 4_500_000.0
        )
    }

    @Test
    fun postFireLumpSum_revivesDepletedPortfolioInTrajectory() {
        val lumpJson = serializeCustomLumpSums(
            listOf(CustomLumpSumItem(id = "inh", name = "Inheritance", year = 2029, amount = 4_000_000.0, enabled = true))
        )
        val s = cleanSettings(baseYear = 2026, primaryAge = 60).copy(
            liquidPortfolioCurrent = 600_000.0,
            fireTargetOverride = 100_000.0, // Already retired at year 0
            lifestyleCostAtFireMonthly = 50_000.0, // 600,000/yr -> depletes completely by year 2027
            portfolioNominalReturnPct = 5.0,
            customLumpSumsJson = lumpJson
        )

        val traj = FinancialEngine.buildLiquidPortfolio(s, dualIncome = false)
        val pt2028 = traj.first { it.year == 2028 }
        val pt2030 = traj.first { it.year == 2030 } // End of sy=2029 when 4M inheritance arrives

        assertEquals(0.0, pt2028.portfolio, 0.01)
        assertEquals("Depleted", pt2028.status)
        assertTrue("4M lump sum must revive depleted portfolio above 3M, got ${pt2030.portfolio}", pt2030.portfolio > 3_000_000.0)
        assertEquals("FIRE OK", pt2030.status)
    }

    @Test
    fun postFireLumpSum_raisesSurvivalOddsInParametricAndHistoricalSimulations() {
        val base = cleanSettings(baseYear = 2026, primaryAge = 60).copy(
            liquidPortfolioCurrent = 8_500_000.0,
            fireTargetOverride = 8_000_000.0,
            lifestyleCostAtFireMonthly = 55_000.0, // ~7.8% withdrawal rate -> frequent depletion without help
            portfolioNominalReturnPct = 6.5,
            cpiInflationPct = 2.8,
            monteCarloN = 250,
            retirementHorizonYears = 35
        )
        val withInheritance = base.copy(
            lumpSumInclude = true,
            lumpSumYear = 2028, // 2 years post-FIRE
            lumpSumAmount = 12_000_000.0
        )

        val paramWithout = FinancialEngine.runRetirementSurvival(base, useHistoricalBootstrap = false)
        val paramWith = FinancialEngine.runRetirementSurvival(withInheritance, useHistoricalBootstrap = false)
        assertTrue(
            "Parametric survival odds must improve (${paramWithout.successRatePct}% -> ${paramWith.successRatePct}%)",
            paramWith.successRatePct > paramWithout.successRatePct
        )
        assertTrue(
            "Parametric median end balance must increase (${paramWithout.medianEndBalanceToday} -> ${paramWith.medianEndBalanceToday})",
            paramWith.medianEndBalanceToday > paramWithout.medianEndBalanceToday
        )

        val histWithout = FinancialEngine.runRetirementSurvival(base, useHistoricalBootstrap = true)
        val histWith = FinancialEngine.runRetirementSurvival(withInheritance, useHistoricalBootstrap = true)
        assertTrue(
            "Historical survival odds must improve (${histWithout.successRatePct}% -> ${histWith.successRatePct}%)",
            histWith.successRatePct > histWithout.successRatePct
        )
        assertTrue(
            "Historical median end balance must increase (${histWithout.medianEndBalanceToday} -> ${histWith.medianEndBalanceToday})",
            histWith.medianEndBalanceToday > histWithout.medianEndBalanceToday
        )
    }

    @Test
    fun postFireLumpSum_afterEarlyDepletionDoesNotReviveFailedSurvivalPath() {
        val s = cleanSettings(baseYear = 2026, primaryAge = 60).copy(
            liquidPortfolioCurrent = 400_000.0,
            fireTargetOverride = 100_000.0,
            lifestyleCostAtFireMonthly = 60_000.0, // 720,000/yr > 400,000 starting portfolio -> immediate year-1 depletion
            monteCarloN = 100,
            retirementHorizonYears = 25,
            lumpSumInclude = true,
            lumpSumYear = 2035,
            lumpSumAmount = 50_000_000.0
        )

        val res = FinancialEngine.runRetirementSurvival(s, useHistoricalBootstrap = false)
        assertEquals("Path already depleted in 2026 must remain failed despite 2035 inflow", 0.0, res.successRatePct, 0.001)
    }

    @Test
    fun decumulationStrategies_countsScheduledLumpSumsAndBaristaStreams() {
        val baseConfig = DecumulationConfig(
            initialPortfolio = 8_000_000.0,
            initialAnnualWithdrawal = 600_000.0, // 7.5% high withdrawal rate
            horizonYears = 30,
            annualInflationPct = 2.8,
            expectedAnnualReturnPct = 6.5,
            returnVolatilityPct = 15.0,
            simulationsCount = 200,
            randomSeed = 42L
        )
        val withLump = baseConfig.copy(scheduledLumpSums = listOf(3 to 8_000_000.0))
        val withBarista = baseConfig.copy(baristaStreams = (0 until 15).map { it to 360_000.0 })

        val baseRes = DecumulationStrategies.compareStrategies(baseConfig)
        val lumpRes = DecumulationStrategies.compareStrategies(withLump)
        val baristaRes = DecumulationStrategies.compareStrategies(withBarista)

        assertTrue(
            "Scheduled lump sum must improve Bengen survival",
            lumpRes.bengenMetrics.survivalRatePct > baseRes.bengenMetrics.survivalRatePct
        )
        assertTrue(
            "Barista income stream must improve Bengen survival",
            baristaRes.bengenMetrics.survivalRatePct > baseRes.bengenMetrics.survivalRatePct
        )
    }

    @Test
    fun baristaContinuePostFire_continuesUntilEndAgeAndStopsAtFireWhenDisabled() {
        val base = cleanSettings(baseYear = 2026, primaryAge = 35).copy(
            liquidPortfolioCurrent = 12_000_000.0,
            fireTargetOverride = 10_000_000.0,
            lifestyleCostAtFireMonthly = 35_000.0,
            portfolioNominalReturnPct = 6.0,
            baristaTransitionEnabled = true,
            baristaStartYear = 2026,
            baristaMonthlyIncome = 30_000.0,
            baristaStartAtFire = false,
            baristaEndAge = 50
        )

        val continuing = FinancialEngine.buildLiquidPortfolio(base.copy(baristaContinuePostFire = true), dualIncome = false)
        val stoppingAtFire = FinancialEngine.buildLiquidPortfolio(base.copy(baristaContinuePostFire = false), dualIncome = false)

        val postFireActiveYears = continuing.filter { it.year > 2026 && it.age <= 50 }
        val postEndYears = continuing.filter { it.age > 50 }

        assertTrue("Post-FIRE years before end age 50 must show FIRE · Barista", postFireActiveYears.all { it.status == "FIRE · Barista" })
        assertTrue("Years after end age 50 must revert to FIRE OK", postEndYears.all { it.status == "FIRE OK" })
        assertFalse("With baristaContinuePostFire=false, no post-FIRE point stays Barista", stoppingAtFire.any { it.status == "FIRE · Barista" })

        val balContinuing50 = continuing.first { it.age == 50 }.totalPortfolio
        val balStopping50 = stoppingAtFire.first { it.age == 50 }.totalPortfolio
        assertTrue("Continuing Barista income must preserve much higher balance at age 50", balContinuing50 > balStopping50 + 2_000_000.0)
    }

    @Test
    fun baristaStartAtFire_staysFullTimePreFireAndStartsBaristaPostFire() {
        val s = cleanSettings(baseYear = 2026, primaryAge = 35).copy(
            liquidPortfolioCurrent = 4_000_000.0,
            fireTargetOverride = 8_000_000.0,
            vSalary = 70_000.0,
            portuDcaMonthly = 30_000.0,
            lifestyleCostAtFireMonthly = 35_000.0,
            portfolioNominalReturnPct = 7.0,
            baristaTransitionEnabled = true,
            baristaStartAtFire = true,
            baristaStartYear = 2026,
            baristaMonthlyIncome = 30_000.0,
            baristaEndAge = 55
        )

        val traj = FinancialEngine.buildLiquidPortfolio(s, dualIncome = false)
        assertFalse("Pre-FIRE accumulation must NOT activate Barista when baristaStartAtFire=true", traj.any { it.status == "Barista Active" })
        val postFireBarista = traj.filter { it.status == "FIRE · Barista" }
        assertTrue("Post-FIRE trajectory must enter FIRE · Barista before age 55", postFireBarista.isNotEmpty())
        assertTrue("All FIRE · Barista points must precede stop age", postFireBarista.all { it.age <= 55 })
    }

    @Test
    fun targetRetirementAge_keepsFullTimeSavingPastFireCrossover() {
        val base = cleanSettings(baseYear = 2026, primaryAge = 35).copy(
            liquidPortfolioCurrent = 7_500_000.0,
            fireTargetOverride = 8_000_000.0,
            vSalary = 75_000.0,
            portuDcaMonthly = 35_000.0,
            lifestyleCostAtFireMonthly = 35_000.0,
            portfolioNominalReturnPct = 7.0
        )
        val stopAtFireState = FinancialEngine.calculate(base.copy(targetRetirementAge = 0), runMonteCarlo = false)
        val workUntil50State = FinancialEngine.calculate(base.copy(targetRetirementAge = 50), runMonteCarlo = false)

        // Mathematical FIRE target crossover year stays the same
        assertNotNull(stopAtFireState.fireDualPoint)
        assertNotNull(workUntil50State.fireDualPoint)
        assertEquals(stopAtFireState.fireDualPoint!!.age, workUntil50State.fireDualPoint!!.age)

        val savingPastFirePoints = workUntil50State.singleTrajectory.filter { it.status == "FIRE · Saving" }
        assertTrue("Trajectory must tag post-FIRE working years before 50 as 'FIRE · Saving'", savingPastFirePoints.isNotEmpty())
        assertTrue("Full-time investing must continue during FIRE · Saving years", savingPastFirePoints.all { it.investedAnnual > 300_000.0 })

        val balAt50Stop = stopAtFireState.singleTrajectory.first { it.age == 50 }.totalPortfolio
        val balAt50Work = workUntil50State.singleTrajectory.first { it.age == 50 }.totalPortfolio
        assertTrue("Working and saving past FIRE to age 50 must build higher net worth", balAt50Work > balAt50Stop + 4_000_000.0)
    }

    @Test
    fun backupManager_roundTripsAndSanitizesV40BaristaAndRetirementFields() {
        val original = SettingsEntity.freshDefaults().copy(
            baristaContinuePostFire = false,
            baristaStartAtFire = true,
            baristaEndAge = 58,
            targetRetirementAge = 55
        )
        val json = BackupManager.serializeSettingsToJson(original)
        val restored = BackupManager.deserializeSettingsFromJson(json, SettingsEntity.freshDefaults())
        assertNotNull(restored)
        assertEquals(false, restored!!.baristaContinuePostFire)
        assertEquals(true, restored.baristaStartAtFire)
        assertEquals(58, restored.baristaEndAge)
        assertEquals(55, restored.targetRetirementAge)

        val sanitized = BackupManager.sanitizeSettings(
            original.copy(baristaEndAge = -10, targetRetirementAge = 120)
        )
        assertEquals(0, sanitized.baristaEndAge)
        assertEquals(85, sanitized.targetRetirementAge)
    }
}
