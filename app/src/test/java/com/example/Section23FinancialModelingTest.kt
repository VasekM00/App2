package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppPreferencesRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.ChecklistCategory
import com.example.domain.CzechCapitalGainsEngine
import com.example.domain.CzechTaxEngine
import com.example.domain.CzechYearEndTaxChecklist
import com.example.domain.DecumulationConfig
import com.example.domain.DecumulationStrategies
import com.example.domain.DecumulationStrategyType
import com.example.domain.DpsDipEngine
import com.example.domain.FinancialEngine
import com.example.domain.FireTargetAndTrajectoryEngine
import com.example.domain.HouseholdCashFlowEngine
import com.example.domain.InvestmentLot
import com.example.domain.LotSaleOrder
import com.example.domain.MonteCarloEngine
import com.example.domain.PresetProfiles
import com.example.domain.RegulatoryConstants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class Section23FinancialModelingTest {

    @Test
    fun childTaxCreditAlreadyInNetSalaryIsNotDoubleCountedInSpringRefundOrSavings() {
        val baseSettings = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 32,
            vSalary = 60_000.0,
            childExpensesEnabled = true,
            child1Enabled = true,
            child1BirthYear = 2024,
            child1TaxBonusAnnual = 15_204.0,
            dipContributionMonthly = 2_000.0,
            dpsOwnContributionMonthly = 0.0
        )
        val noChildSettings = baseSettings.copy(child1Enabled = false, childExpensesEnabled = false)

        val stateWithChild = FinancialEngine.calculate(baseSettings, runMonteCarlo = false)
        val stateNoChild = FinancialEngine.calculate(noChildSettings, runMonteCarlo = false)

        // Child relief display keeps statutory amount, but totalIncrementalValue (spring refund)
        // must NOT double-count the monthly paycheck child credit on top of DIP tax relief.
        assertEquals(15_204.0, stateWithChild.taxReturnHelper.childBonus, 1.0)
        assertEquals(
            stateWithChild.taxReturnHelper.dipSaving,
            stateWithChild.taxReturnHelper.totalIncrementalValue,
            1.0
        )
        assertTrue(
            "Spring refund must not exceed DIP tax relief + spouse credit",
            abs(stateWithChild.taxReturnHelper.totalIncrementalValue - stateNoChild.taxReturnHelper.totalIncrementalValue) < 1.0
        )
    }

    @Test
    fun cashFlowSolvencyWaterfallCapsUnfundedInvestingAndAccumulatesSurplusInEmergencyCash() {
        val insolventSettings = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 35,
            vSalary = 30_000.0,
            rentMonthly = 35_000.0,
            groceriesMonthly = 15_000.0,
            portuDcaMonthly = 20_000.0,
            liquidPortfolioCurrent = 100_000.0,
            emergencyReserveCurrent = 50_000.0,
            portfolioNominalReturnPct = 0.0,
            dividendYieldPct = 0.0
        )
        val insolventTraj = FinancialEngine.buildLiquidPortfolio(insolventSettings, dualIncome = false)
        assertTrue(insolventTraj.size > 1)
        assertTrue(
            "Insolvent household must not accumulate 240k/yr of fantasy DCA contributions",
            insolventTraj[1].investedAnnual < 10_000.0
        )

        val surplusSettings = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 35,
            vSalary = 120_000.0,
            rentMonthly = 20_000.0,
            groceriesMonthly = 10_000.0,
            portuDcaMonthly = 20_000.0,
            emergencyReserveCurrent = 200_000.0,
            emergencyReserveMode = "CUSTOM",
            emergencyReserveTarget = 200_000.0,
            emergencyReserveMonthlyOverride = 0.0
        )
        val surplusTraj = FinancialEngine.buildLiquidPortfolio(surplusSettings, dualIncome = false)
        assertTrue(
            "Unallocated net income surplus must accumulate in cash buffer instead of vanishing",
            surplusTraj[1].emergencyCash > surplusTraj[0].emergencyCash
        )
    }

    @Test
    fun postFireTrajectoryStopsNewDcaContributionsAndUsesDynamicHorizon() {
        val nearFireSettings = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 24,
            vSalary = 90_000.0,
            portuDcaMonthly = 30_000.0,
            dipContributionMonthly = 0.0,
            dpsOwnContributionMonthly = 0.0,
            employerRetirementMonthly = 0.0,
            childExpensesEnabled = false,
            child1Enabled = false,
            child2Enabled = false,
            rentMonthly = 20_000.0,
            groceriesMonthly = 10_000.0,
            cafesMonthly = 0.0,
            therapyMonthly = 0.0,
            charityMonthly = 0.0,
            otherDiscretionaryMonthly = 0.0,
            entertainmentMonthly = 0.0,
            transportMonthly = 0.0,
            subscriptionsMonthly = 0.0,
            liquidPortfolioCurrent = 10_500_000.0,
            safeWithdrawalRatePct = 4.0
        )
        val horizon = FireTargetAndTrajectoryEngine.projectionHorizonYears(nearFireSettings)
        assertTrue("Young 24yo earner horizon ($horizon) must exceed old fixed 35y cap", horizon > 35)

        val traj = FinancialEngine.buildLiquidPortfolio(nearFireSettings, dualIncome = false)
        assertEquals(horizon + 1, traj.size)
        val fireIdx = traj.indexOfFirst { it.totalPortfolio >= it.target }
        assertTrue("Household near target should hit FIRE early", fireIdx in 0..2)
        assertEquals(
            "After achieving FIRE, new savings inflows must stop (no post-FIRE career salary compounding)",
            0.0,
            traj[fireIdx + 1].investedAnnual,
            1.0
        )
    }

    @Test
    fun rentGrowthAboveCpiIncreasesFutureFireTargetAndStagflationBridgeIsCapped() {
        val baseSettings = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 30,
            rentMonthly = 25_000.0,
            groceriesMonthly = 15_000.0,
            cpiInflationPct = 2.5,
            rentGrowthPct = 6.0,
            safeWithdrawalRatePct = 4.0
        )
        val target2026 = FinancialEngine.fireTargetYear(2026, baseSettings, age = 30)
        val target2036 = FinancialEngine.fireTargetYear(2036, baseSettings, age = 40)
        assertTrue(
            "Positive rentGrowthPct > cpiInflationPct must raise real FIRE target over time ($target2036 vs $target2026)",
            target2036 > target2026
        )

        val cappedBridge = FireTargetAndTrajectoryEngine.computeBridgeTo60Required(
            annualLiving = 600_000.0,
            yearsTo60 = 34,
            rReal = -0.02,
            totalFireTarget = 18_000_000.0
        )
        assertEquals(
            "Negative real-return stagflation bridge must be capped by lifetime FIRE target",
            18_000_000.0,
            cappedBridge,
            1.0
        )
    }

    @Test
    fun fireTargetBaseReflectsPartnerAgeOffsetAndAgeTieredChildBridge() {
        val coupled = SettingsEntity(
            isSingleHousehold = false,
            baseYear = 2026,
            primaryAge = 35,
            vStatePensionMonthly = 0.0,
            eStatePensionMonthly = 25_000.0,
            eStatePensionAge = 65,
            rentMonthly = 20_000.0,
            groceriesMonthly = 15_000.0
        )
        val targetOlderPartner = FinancialEngine.fireTargetBase(coupled, age = 35, partnerBirthYearOverride = 1975)
        val targetYoungerPartner = FinancialEngine.fireTargetBase(coupled, age = 35, partnerBirthYearOverride = 2000)
        assertTrue(
            "Older partner starting state pension sooner requires smaller lifetime capital ($targetOlderPartner vs $targetYoungerPartner)",
            targetOlderPartner < targetYoungerPartner
        )

        val baseFamily = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 35,
            childExpensesEnabled = true,
            currentChildCostsInBaseline = false,
            child1Enabled = true,
            child1BirthYear = 2026,
            childToddlerMonthly = 5_000.0,
            childPreschoolMonthly = 7_000.0,
            childSchoolMonthly = 10_000.0,
            childTeenMonthly = 14_000.0,
            childUniMonthly = 22_000.0,
            childSupportUntilAge = 24
        )
        val toddlerTarget = FinancialEngine.fireTargetBase(baseFamily, age = 35)
        val uniTarget = FinancialEngine.fireTargetBase(baseFamily.copy(child1BirthYear = 2006), age = 35)
        assertTrue(
            "20-year-old child with 4y remaining should require smaller child bridge than newborn with 24y remaining",
            uniTarget < toddlerTarget
        )
    }

    @Test
    fun earlyAccessBridgeAtFireReflectsFireAgeRatherThanTodayAge() {
        val youngEarner = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 26,
            vSalary = 120_000.0,
            portuDcaMonthly = 50_000.0,
            dipContributionMonthly = 4_000.0,
            dpsOwnContributionMonthly = 0.0,
            employerRetirementMonthly = 0.0,
            childExpensesEnabled = false,
            child1Enabled = false,
            child2Enabled = false,
            cpiInflationPct = 0.0,
            rentGrowthPct = 0.0,
            liquidPortfolioCurrent = 1_000_000.0,
            rentMonthly = 20_000.0,
            groceriesMonthly = 10_000.0,
            cafesMonthly = 0.0,
            therapyMonthly = 0.0,
            charityMonthly = 0.0,
            otherDiscretionaryMonthly = 0.0,
            entertainmentMonthly = 0.0,
            transportMonthly = 0.0,
            subscriptionsMonthly = 0.0
        )
        val state = FinancialEngine.calculate(youngEarner, runMonteCarlo = false)
        val fireAge = state.snapshot.activeFireAge!!
        assertTrue(fireAge > 26 && fireAge < 60)
        assertEquals(60 - fireAge, state.bridgeYearsFromFireTo60)
        assertEquals(state.bridgeYearsFromFireTo60, state.snapshot.bridgeYearsFromFireTo60)
        assertTrue(state.bridgeRequiredAtFire < state.liquidBridgeTo60RequiredToday)
        assertTrue(state.isBridgeFundedAtFire)
    }

    @Test
    fun baristaStartYearBoundaryAndSpouseInclusionWorkAccurately() {
        val s = SettingsEntity(
            isSingleHousehold = false,
            baseYear = 2026,
            primaryAge = 30,
            vSalary = 70_000.0,
            eStartingSalary = 45_000.0,
            eReturnYear = 2026,
            eReturnMonth = 1,
            rentMonthly = 22_000.0,
            groceriesMonthly = 12_000.0,
            portuDcaMonthly = 20_000.0,
            ePortuDcaMonthly = 10_000.0,
            baristaTransitionEnabled = true,
            baristaStartYear = 2032,
            baristaMonthlyIncome = 25_000.0,
            liquidPortfolioCurrent = 500_000.0
        )
        val noBarista = FinancialEngine.buildLiquidPortfolio(s.copy(baristaTransitionEnabled = false), dualIncome = true)
        val withBarista = FinancialEngine.buildLiquidPortfolio(s, dualIncome = true)

        // Prior to baristaStartYear (2032): 2031 cash flow is unaffected
        val p2031Base = noBarista.first { it.year == 2031 }
        val p2031Barista = withBarista.first { it.year == 2031 }
        assertEquals(p2031Base.portfolio, p2031Barista.portfolio, 1.0)
        // At baristaStartYear=2032, career cut starts in sy=2032, visible at end-of-year point 2033
        val p2033Base = noBarista.first { it.year == 2033 }
        val p2033Barista = withBarista.first { it.year == 2033 }
        assertTrue(p2033Barista.portfolio < p2033Base.portfolio)
    }

    @Test
    fun monteCarloSurvivalRespectsPre60BridgeHistoricalInflationStochasticDpsAndConfidence() {
        assertEquals(56, MonteCarloEngine.HISTORICAL_ANNUAL_INFLATION_PCT.size)

        // Pre-60 liquid bridge gate: household with tiny liquid bucket & huge locked pension
        // must remain "Bridge Constrained" rather than retiring prematurely at age 50;
        // survival simulation defers FIRE until age 60 unlocks the pension assets.
        val lockedHeavy = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 50,
            childExpensesEnabled = false,
            child1Enabled = false,
            child2Enabled = false,
            liquidPortfolioCurrent = 100_000.0,
            dipBalanceCurrent = 15_000_000.0,
            portuDcaMonthly = 0.0,
            dipContributionMonthly = 0.0,
            dpsOwnContributionMonthly = 0.0,
            employerRetirementMonthly = 0.0,
            rentMonthly = 35_000.0,
            groceriesMonthly = 15_000.0,
            vStatePensionMonthly = 0.0,
            monteCarloN = 100,
            monteCarloSeed = 42L
        )
        val lockedTraj = FinancialEngine.buildLiquidPortfolio(lockedHeavy, dualIncome = false)
        assertTrue(
            "Locked-only pre-60 household must remain Bridge Constrained prior to age 60",
            lockedTraj.filter { it.age < 60 && it.totalPortfolio >= it.target }.all { it.status == "Bridge Constrained" }
        )
        val lockedSurv = FinancialEngine.runRetirementSurvival(lockedHeavy, useHistoricalBootstrap = false)
        assertTrue(
            "With pre-60 bridge gate deferring FIRE to age 60 unlock, locked-only household avoids year-1 suicide depletion",
            lockedSurv.successRatePct > 50.0
        )

        // Compare coupled 1973/2022 historical inflation shocks vs calm 1% static CPI
        val liquidHeavy = lockedHeavy.copy(
            liquidPortfolioCurrent = 18_000_000.0,
            dipBalanceCurrent = 0.0,
            cpiInflationPct = 1.0
        )
        val histSurv = FinancialEngine.runRetirementSurvival(liquidHeavy, useHistoricalBootstrap = true)
        assertTrue(histSurv.medianEndBalanceToday >= 0.0)

        // Prolong child costs into retirement reduces survival median at identical FIRE entry trigger
        val equalTriggerBase = liquidHeavy.copy(
            liquidPortfolioCurrent = 25_000_000.0,
            cpiInflationPct = 2.8,
            fireTargetOverride = 17_000_000.0
        )
        val withChildCosts = equalTriggerBase.copy(
            childExpensesEnabled = true,
            child1Enabled = true,
            child1BirthYear = 2026,
            prolongChildSupportInFire = true,
            childSupportUntilAge = 24,
            childSchoolMonthly = 15_000.0,
            childTeenMonthly = 18_000.0,
            childUniMonthly = 25_000.0
        )
        val noChildSurv = FinancialEngine.runRetirementSurvival(equalTriggerBase)
        val childSurv = FinancialEngine.runRetirementSurvival(withChildCosts)
        assertTrue(
            "Prolonged retirement child costs must reduce survival ending capital (${childSurv.medianEndBalanceToday} vs ${noChildSurv.medianEndBalanceToday})",
            childSurv.medianEndBalanceToday < noChildSurv.medianEndBalanceToday
        )

        // Low-confidence sample tracking when almost no path hits an unreachable FIRE target
        val unreachable = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 35,
            vSalary = 40_000.0,
            rentMonthly = 28_000.0,
            portuDcaMonthly = 5_000.0,
            liquidPortfolioCurrent = 100_000.0,
            safeWithdrawalRatePct = 2.0,
            monteCarloN = 60
        )
        val unreachableSurv = FinancialEngine.runRetirementSurvival(unreachable, useHistoricalBootstrap = true)
        assertTrue(unreachableSurv.fireReachedCount <= 60)
        if (unreachableSurv.fireReachedCount in 1 until 30) {
            assertTrue(unreachableSurv.isLowConfidenceSample)
        }
    }

    @Test
    fun decumulationStrategiesGkBaselineInflationSkipFinalYearAndPensions() {
        // 1) Inflation-skip after negative return year when withdrawal rate > initial SWR
        val returnsWithBear = listOf(-0.15, 0.06, 0.06)
        val gkPath = DecumulationStrategies.simulateSinglePath(
            type = DecumulationStrategyType.GUYTON_KLINGER_GUARDRAILS,
            initialPortfolio = 10_000_000.0,
            initialAnnualWithdrawal = 400_000.0,
            annualReturns = returnsWithBear,
            annualInflationPct = 5.0,
            gkUpperThresholdPct = 50.0, // isolate Inflation-Skip from Prosperity/Preservation triggers
            gkLowerThresholdPct = 50.0
        )
        // Year 1 withdraws 400k, balance ends at 9.6M * 0.85 = 8.16M (400k / 8.16M = 4.9% > 4.0% initial SWR).
        // Year 2 must skip CPI raise and remain 400k nominal (instead of 420k).
        assertEquals(400_000.0, gkPath[1].withdrawalNominal, 1.0)

        // 2) Exact final-year funding boundary: balance == withdrawal does not flag depleted
        val exactFinish = DecumulationStrategies.simulateSinglePath(
            type = DecumulationStrategyType.CONSTANT_REAL_SWR,
            initialPortfolio = 100_000.0,
            initialAnnualWithdrawal = 100_000.0,
            annualReturns = listOf(0.0),
            annualInflationPct = 0.0
        )
        assertFalse("Exact final-year funding must succeed without depletion flag", exactFinish[0].isDepleted)

        // 3) Pension streams offset portfolio withdrawals
        val comparisonWithPension = DecumulationStrategies.compareStrategies(
            DecumulationConfig(
                initialPortfolio = 10_000_000.0,
                initialAnnualWithdrawal = 500_000.0,
                horizonYears = 25,
                simulationsCount = 60,
                pensionStreams = listOf(5 to 300_000.0)
            )
        )
        val comparisonNoPension = DecumulationStrategies.compareStrategies(
            DecumulationConfig(
                initialPortfolio = 10_000_000.0,
                initialAnnualWithdrawal = 500_000.0,
                horizonYears = 25,
                simulationsCount = 60,
                pensionStreams = emptyList()
            )
        )
        assertTrue(
            "State pension streams must improve decumulation survival rate",
            comparisonWithPension.bengenMetrics.survivalRatePct >= comparisonNoPension.bengenMetrics.survivalRatePct
        )
        assertTrue(
            "Dynamic strategy label should reflect actual config cut/boost percentages",
            comparisonWithPension.guytonKlingerMetrics.strategyName.contains("10%")
        )
    }

    @Test
    fun parentalAllowanceSingleParentNoForfeitureTwinsAndPriorYearPace() {
        // 1) Single parent receives parental allowance when child enabled
        val singleParent = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            childExpensesEnabled = true,
            child1Enabled = true,
            child1BirthYear = 2026,
            eParentalAllowanceMonthly = 15_000.0
        )
        val spIncome = FinancialEngine.householdIncome(2026, singleParent)
        assertEquals(15_000.0, spIncome.benefit, 1.0)

        // 2) Returning to work before pot exhaustion does NOT forfeit remaining parental allowance
        val workingParent = SettingsEntity(
            isSingleHousehold = false,
            baseYear = 2026,
            child1Enabled = true,
            child1BirthYear = 2026,
            eReturnYear = 2027,
            eReturnMonth = 1,
            eStartingSalary = 45_000.0,
            eParentalAllowanceMonthly = 12_000.0
        )
        assertEquals(12_000.0, FinancialEngine.eleonoraBenefitMonthly(2027, workingParent), 1.0)

        // 3) Twins born in 2026+ double the statutory pot
        val singleChildPot = HouseholdCashFlowEngine.remainingParentalPotAtYearStart(2026, workingParent)
        val twinsPot = HouseholdCashFlowEngine.remainingParentalPotAtYearStart(
            2026,
            workingParent.copy(child2Enabled = true, child2BirthYear = 2026)
        )
        assertEquals(singleChildPot * 2.0, twinsPot, 1.0)

        // 4) Prior-year child deducts at realistic pace so pot isn't prematurely wiped out at extreme draw
        val highDrawExistingChild = SettingsEntity(
            isSingleHousehold = false,
            baseYear = 2026,
            child1Enabled = true,
            child1BirthYear = 2025,
            eParentalAllowanceMonthly = 15_000.0
        )
        assertTrue(
            "Existing 2025 child drawing 15k/mo must still have positive pot in 2026",
            HouseholdCashFlowEngine.remainingParentalPotAtYearStart(2026, highDrawExistingChild) > 100_000.0
        )
    }

    @Test
    fun partnerBirthYearPersistsInPreferencesAndOverridesEngineCalculations() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val repo1 = AppPreferencesRepository(context, prefsName = "test_partner_birth_year_23")
        repo1.clearAllUserData()
        repo1.hydrate()
        assertNull(repo1.snapshot.value.partnerBirthYear)

        repo1.setPartnerBirthYear(1988)
        assertEquals(1988, repo1.snapshot.value.partnerBirthYear)

        val repo2 = AppPreferencesRepository(context, prefsName = "test_partner_birth_year_23")
        repo2.hydrate()
        assertEquals(1988, repo2.snapshot.value.partnerBirthYear)

        val stateOlder = FinancialEngine.calculate(
            SettingsEntity(isSingleHousehold = false, baseYear = 2026, primaryAge = 30),
            runMonteCarlo = false,
            partnerBirthYearOverride = 1978
        )
        assertEquals(48, stateOlder.pensionOptimization.partnerAge)
        repo2.clearAllUserData()
    }

    @Test
    fun spouseCreditProrationFutureChildUnblockedAndDppGrossUp() {
        val baseWithoutLecturing = SettingsEntity(
            isSingleHousehold = false,
            baseYear = 2026,
            includeSpouseCredit = true,
            hasChildUnder3 = false,
            child1Enabled = false,
            child2Enabled = true,
            child2BirthYear = 2027,
            eIncludeLecturing = false,
            eLecturingMonthly = 8_500.0,
            eReturnYear = 2028
        )
        // Future Child 2 (birthYear 2027 > baseYear 2026) must NOT be blocked by hasChildUnder3 = false
        val monthsFutureChild = CzechTaxEngine.spouseCreditEligibleMonths(baseWithoutLecturing, year = 2027, child2BirthMonth = 7)
        assertEquals(6, monthsFutureChild)
        val creditHalfYear = CzechTaxEngine.eligibleSpouseCreditAnnual(
            settings = baseWithoutLecturing,
            year = 2027,
            child2BirthMonth = 7
        )
        assertEquals(24_840.0 * 0.5, creditHalfYear, 1.0)

        // DPP lecturing 8,500 Kč net without signed declaration grosses up by 1/(1-0.15) = 10,000 Kč/mo = 120,000 Kč/yr > 68,000 limit
        val withDppLecturing = baseWithoutLecturing.copy(eIncludeLecturing = true)
        val spouseIncomeGrossed = CzechTaxEngine.spouseOwnIncomeAnnual(2027, withDppLecturing, dppSignedTaxDeclaration = false)
        assertEquals(120_000.0, spouseIncomeGrossed, 50.0)
        assertEquals(0.0, CzechTaxEngine.eligibleSpouseCreditAnnual(withDppLecturing, year = 2027, child2BirthMonth = 7), 1.0)
    }

    @Test
    fun dpsExitTaxPerformanceFeeLockInAge36GateAndMarginalBracketStacking() {
        val settings = SettingsEntity(
            baseYear = 2026,
            primaryAge = 55,
            dpsOwnContributionMonthly = 2_500.0,
            dpsBalanceCurrent = 0.0,
            dpsAnnualFeePct = 1.0,
            dpsGrossReturnPct = 7.0
        )
        // Performance fee (15% of 6% net after 1% mgmt fee) => 6.0 * 0.85 = 5.1%
        assertEquals(5.1, DpsDipEngine.effectiveDpsNetReturnPct(settings), 0.001)

        val dpsProj = FinancialEngine.buildDpsProjection(settings)
        // Contract opened at age 55 with 0 balance is locked 10 years -> unlockAge 65
        assertEquals(65, dpsProj.unlockAge)
        // 15% lump-sum exit tax applies on gains + employer contributions
        assertTrue(dpsProj.lumpSumExitTax > 0.0)
        assertEquals(dpsProj.dpsGrossBalance - dpsProj.lumpSumExitTax, dpsProj.dpsBalance, 1.0)

        // Age >= 36 never gets age-36 1/3 early grant
        val age37Dps = FinancialEngine.buildDpsProjection(settings.copy(primaryAge = 37))
        assertEquals(0.0, age37Dps.earlyWithdrawalLimitAt36, 1.0)

        // 23% marginal tax bracket stacking: DIP fills lower base first so additional DPS gets taxed/relieved consistently
        val highEarner = SettingsEntity(
            isSingleHousehold = true,
            baseYear = 2026,
            primaryAge = 35,
            vSalary = 150_000.0,
            dipContributionMonthly = 2_000.0,
            dpsOwnContributionMonthly = 2_500.0
        )
        val highTaxSaved = FinancialEngine.annualRetirementTaxSaved(highEarner)
        assertTrue(highTaxSaved > 33_600.0 * 0.15)
    }

    @Test
    fun ytdSignedOutflowsPerAccountGroupingChecklistWiringAndCharityCategory() {
        val settings = SettingsEntity(
            isSingleHousehold = false,
            baseYear = 2026,
            primaryAge = 30,
            vSalary = 80_000.0,
            dipContributionMonthly = 2_000.0,
            dpsOwnContributionMonthly = 2_700.0,
            eDipContributionMonthly = 1_500.0,
            charityMonthly = 500.0 // 6,000 Kč/yr >= 1,000 statutory minimum
        )

        val txs = listOf(
            // DIP signed outflow & reversal: -5000 + +1000 = 4000 net contributed
            ImportedBankTransactionEntity(
                yearMonth = "2026-03",
                bankName = "PORTU",
                date = "2026-03-05",
                amount = -5_000.0,
                category = "INVESTMENT_DIP",
                accountKey = "DIP_ACC_1"
            ),
            ImportedBankTransactionEntity(
                yearMonth = "2026-03",
                bankName = "PORTU",
                date = "2026-03-10",
                amount = 1_000.0,
                category = "INVESTMENT_DIP",
                accountKey = "DIP_ACC_1"
            ),
            // Two separate DPS accounts in the same month: each applies 1,700 threshold independently
            ImportedBankTransactionEntity(
                yearMonth = "2026-03",
                bankName = "CSOB",
                date = "2026-03-15",
                amount = -2_000.0,
                category = "INVESTMENT_DPS",
                accountKey = "DPS_A"
            ),
            ImportedBankTransactionEntity(
                yearMonth = "2026-03",
                bankName = "KB",
                date = "2026-03-15",
                amount = -2_200.0,
                category = "INVESTMENT_DPS",
                accountKey = "DPS_B"
            )
        )
        val ytd = FinancialEngine.computeYtdTaxShieldStatus(
            settings = settings,
            currentYear = 2026,
            currentMonth = 3,
            importedTransactions = txs
        )
        assertTrue(ytd.isFromImportedTransactions)
        assertEquals(4_000.0, ytd.ytdDipContributed, 1.0)
        // (2000 - 1700) + (2200 - 1700) = 300 + 500 = 800
        assertEquals(800.0, ytd.ytdDpsContributed, 1.0)
        assertEquals(9, ytd.monthsRemainingInYear)
        assertEquals(96_000.0, ytd.householdCeilingAnnual, 1.0)

        val state = FinancialEngine.calculate(
            settings = settings,
            runMonteCarlo = false,
            activeMonth = 3,
            importedTransactions = txs
        )
        val checklist = state.yearEndTaxChecklist!!
        val dipItem = checklist.items.first { it.category == ChecklistCategory.RETIREMENT_DIP_DPS }
        assertEquals(4_800.0, dipItem.currentValueCzk, 1.0)
        assertTrue(checklist.items.any { it.category == ChecklistCategory.CHARITY_DONATIONS })
    }

    @Test
    fun capitalGainsProgressiveStackingSection38vAndLossPreservation() {
        val asOf = LocalDate.of(2026, 6, 1)
        val taxableGainLot = InvestmentLot(
            id = "taxable_gain",
            ticker = "VWCE",
            purchaseDate = LocalDate.of(2025, 1, 10),
            units = 10.0,
            acquisitionPricePerUnitCzk = 2_000.0,
            currentPricePerUnitCzk = 8_000.0 // 80k gross (< 100k limit), 60k gain
        )
        val taxableLossLot = InvestmentLot(
            id = "taxable_loss",
            ticker = "IWDA",
            purchaseDate = LocalDate.of(2025, 3, 10),
            units = 10.0,
            acquisitionPricePerUnitCzk = 9_000.0,
            currentPricePerUnitCzk = 6_000.0 // 30k loss
        )
        val harvest = CzechCapitalGainsEngine.recommendOptimalHarvest(
            lots = listOf(taxableLossLot, taxableGainLot),
            targetCashCzk = 70_000.0,
            asOfDate = asOf
        )
        assertEquals(
            "Inside 100k gross exemption, engine must liquidate taxable gain lot first rather than wasting a tax loss",
            "taxable_gain",
            harvest.saleOrders.first().lotId
        )

        // Progressive § 6 stacking pushes capital gains above threshold into 23% bracket
        val saleHighSalary = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(
                InvestmentLot(
                    id = "large_short",
                    ticker = "VWCE",
                    purchaseDate = LocalDate.of(2025, 6, 1),
                    units = 100.0,
                    acquisitionPricePerUnitCzk = 1_000.0,
                    currentPricePerUnitCzk = 3_000.0 // 200k gain
                )
            ),
            saleOrders = listOf(LotSaleOrder("large_short", 100.0)),
            asOfDate = asOf,
            otherTaxableIncomeAnnualCzk = RegulatoryConstants.STATUTORY_TAX_BRACKET_THRESHOLD_ANNUAL_2026
        )
        assertEquals(200_000.0 * 0.23, saleHighSalary.progressiveTaxLiabilityCzk, 1.0)

        // ZDP § 38v mandatory notice when 3-year time-test exempt proceeds exceed 5,000,000 CZK
        val hugeExemptSale = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(
                InvestmentLot(
                    id = "old_exempt",
                    ticker = "VWCE",
                    purchaseDate = LocalDate.of(2020, 1, 1),
                    units = 1_000.0,
                    acquisitionPricePerUnitCzk = 2_000.0,
                    currentPricePerUnitCzk = 6_000.0 // 6M exempt proceeds
                )
            ),
            saleOrders = listOf(LotSaleOrder("old_exempt", 1_000.0)),
            asOfDate = asOf
        )
        assertTrue(hugeExemptSale.requiresSection38vNotification)
        assertNotNull(hugeExemptSale.section38vWarning)
    }

    @Test
    fun laterRetirementPresetShiftsPensionAgeUpAndDoesNotLowerTarget() {
        assertEquals("State Pension +3 Years", PresetProfiles.LATER_RETIREMENT.title)
        val base = SettingsEntity(
            isSingleHousehold = false,
            vStatePensionAge = 65,
            eStatePensionAge = 65,
            vStatePensionMonthly = 22_000.0,
            eStatePensionMonthly = 20_000.0
        )
        val shifted = PresetProfiles.LATER_RETIREMENT.transform(base)
        assertEquals(68, shifted.vStatePensionAge)
        assertEquals(68, shifted.eStatePensionAge)
        assertTrue(
            FinancialEngine.fireTargetBase(shifted) >= FinancialEngine.fireTargetBase(base)
        )
    }
}
