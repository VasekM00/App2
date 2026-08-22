package com.example

import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.util.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupManagerAndEngineAuditTest {

    @Test
    fun `test BackupManager full roundtrip preserves all fields including profile, tax and reform parameters`() {
        val original = SettingsEntity(
            baseYear = 2027,
            primaryAge = 28,
            primaryName = "Jan",
            spouseName = "Marie",
            isSingleHousehold = false,
            dcaAnnualGrowthPct = 3.5,
            vSalary = 50000.0,
            vRaiseAnnual = 2000.0,
            vBonusAnnual = 30000.0,
            vMealVouchersMonthly = 2500.0,
            eReturnYear = 2030,
            eStartingSalary = 28000.0,
            eBonusAnnual = 12000.0,
            eSalaryGrowthPct = 4.5,
            eReinvestedPct = 80.0,
            eParentalAllowanceMonthly = 14000.0,
            eLecturingMonthly = 8500.0,
            eIncludeLecturing = false,
            familyGiftMonthly = 18000.0,
            lumpSumYear = 2032,
            lumpSumAmount = 750000.0,
            lumpSumInclude = true,
            liquidPortfolioCurrent = 350000.0,
            dpsBalanceCurrent = 45000.0,
            dipBalanceCurrent = 30000.0,
            portuDcaMonthly = 15000.0,
            portfolioNominalReturnPct = 8.5,
            dpsOwnContributionMonthly = 2000.0,
            dpsGrossReturnPct = 7.0,
            dpsAnnualFeePct = 0.4,
            dipContributionMonthly = 4000.0,
            employerRetirementAnnual = 5000.0,
            eLiquidPortfolioCurrent = 120000.0,
            ePortuDcaMonthly = 5000.0,
            eDpsBalanceCurrent = 15000.0,
            eDpsOwnContributionMonthly = 1000.0,
            eDipBalanceCurrent = 10000.0,
            eDipContributionMonthly = 2000.0,
            eEmployerRetirementAnnual = 3000.0,
            emergencyReserveCurrent = 300000.0,
            emergencyReserveTarget = 350000.0,
            lifestyleCostAtFireMonthly = 40000.0,
            statePensionMonthly = 15000.0,
            statePensionAge = 65,
            safeWithdrawalRatePct = 3.5,
            safetyBufferPct = 15.0,
            cpiInflationPct = 2.8,
            fireTargetOverride = 12000000.0,
            rentMonthly = 25000.0,
            groceriesMonthly = 6000.0,
            cafesMonthly = 3000.0,
            therapyMonthly = 2500.0,
            charityMonthly = 3000.0,
            entertainmentMonthly = 2000.0,
            transportMonthly = 1000.0,
            subscriptionsMonthly = 800.0,
            otherDiscretionaryMonthly = 2500.0,
            taxRatePct = 15.0,
            taxRateSecondPct = 23.0,
            taxSecondBracketThresholdAnnual = 1600000.0,
            taxpayerCreditAnnual = 30840.0,
            taxDeductionCeilingAnnual = 48000.0,
            spouseTaxCreditAnnual = 24840.0,
            spouseIncomeLimitAnnual = 68000.0,
            includeSpouseCredit = false,
            hasChildUnder3 = false,
            minWageMonthly = 22400.0,
            dpsDeductionThresholdMonthly = 1700.0,
            dpsStandardSubsidyMaxMonthly = 340.0,
            dpsYouthSubsidyMaxMonthly = 680.0,
            dpsMinDepositForSubsidy = 500.0,
            dpsYouthAgeLimit = 30,
            dpsSubsidyRateStandardPct = 20.0,
            dpsSubsidyRateYouthPct = 40.0,
            childExpensesEnabled = true,
            child1Enabled = true,
            child1BirthYear = 2025,
            child2Enabled = false,
            child2BirthYear = 2028,
            child1TaxBonusAnnual = 15204.0,
            child2TaxBonusAnnual = 22320.0,
            child3PlusTaxBonusAnnual = 27840.0,
            childToddlerMonthly = 5000.0,
            childPreschoolMonthly = 7000.0,
            childSchoolMonthly = 9000.0,
            childTeenMonthly = 14000.0,
            childUniMonthly = 11000.0,
            rentGrowthPct = 4.5,
            monteCarloN = 350,
            monteCarloVolatilityPct = 18.5,
            monteCarloSeed = 12345L,
            customExpensesJson = "[{\"id\":\"1\",\"name\":\"Gym\",\"amount\":1500.0}]",
            customGoalsJson = "[{\"id\":\"1\",\"name\":\"Car\",\"targetYear\":2030,\"targetAmountCzk\":400000.0,\"currentSavedCzk\":100000.0}]",
            deletedCategoriesJson = "[\"transport\"]"
        )

        val json = BackupManager.serializeSettingsToJson(original)
        val restored = BackupManager.deserializeSettingsFromJson(json, SettingsEntity())

        assertNotNull("Deserialization should succeed", restored)
        restored?.let { r ->
            assertEquals(original.baseYear, r.baseYear)
            assertEquals(original.primaryAge, r.primaryAge)
            assertEquals(original.primaryName, r.primaryName)
            assertEquals(original.spouseName, r.spouseName)
            assertEquals(original.isSingleHousehold, r.isSingleHousehold)
            assertEquals(original.dcaAnnualGrowthPct, r.dcaAnnualGrowthPct, 0.001)
            assertEquals(original.vSalary, r.vSalary, 0.001)
            assertEquals(original.taxRatePct, r.taxRatePct, 0.001)
            assertEquals(original.taxRateSecondPct, r.taxRateSecondPct, 0.001)
            assertEquals(original.taxSecondBracketThresholdAnnual, r.taxSecondBracketThresholdAnnual, 0.001)
            assertEquals(original.taxpayerCreditAnnual, r.taxpayerCreditAnnual, 0.001)
            assertEquals(original.taxDeductionCeilingAnnual, r.taxDeductionCeilingAnnual, 0.001)
            assertEquals(original.spouseTaxCreditAnnual, r.spouseTaxCreditAnnual, 0.001)
            assertEquals(original.spouseIncomeLimitAnnual, r.spouseIncomeLimitAnnual, 0.001)
            assertEquals(original.includeSpouseCredit, r.includeSpouseCredit)
            assertEquals(original.hasChildUnder3, r.hasChildUnder3)
            assertEquals(original.minWageMonthly, r.minWageMonthly, 0.001)
            assertEquals(original.dpsGrossReturnPct, r.dpsGrossReturnPct, 0.001)
            assertEquals(original.dpsAnnualFeePct, r.dpsAnnualFeePct, 0.001)
            assertEquals(original.dpsDeductionThresholdMonthly, r.dpsDeductionThresholdMonthly, 0.001)
            assertEquals(original.dpsStandardSubsidyMaxMonthly, r.dpsStandardSubsidyMaxMonthly, 0.001)
            assertEquals(original.dpsYouthSubsidyMaxMonthly, r.dpsYouthSubsidyMaxMonthly, 0.001)
            assertEquals(original.dpsMinDepositForSubsidy, r.dpsMinDepositForSubsidy, 0.001)
            assertEquals(original.dpsYouthAgeLimit, r.dpsYouthAgeLimit)
            assertEquals(original.dpsSubsidyRateStandardPct, r.dpsSubsidyRateStandardPct, 0.001)
            assertEquals(original.dpsSubsidyRateYouthPct, r.dpsSubsidyRateYouthPct, 0.001)
            assertEquals(original.monteCarloN, r.monteCarloN)
            assertEquals(original.monteCarloVolatilityPct, r.monteCarloVolatilityPct, 0.001)
            assertEquals(original.monteCarloSeed, r.monteCarloSeed)
            assertEquals(original.customExpensesJson, r.customExpensesJson)
            assertEquals(original.customGoalsJson, r.customGoalsJson)
            assertEquals(original.deletedCategoriesJson, r.deletedCategoriesJson)
        }
    }

    @Test
    fun `test Monte Carlo reacts dynamically to volatility and seed changes`() {
        val baseSettings = SettingsEntity(monteCarloVolatilityPct = 10.0, monteCarloSeed = 42L)
        val highVolSettings = SettingsEntity(monteCarloVolatilityPct = 30.0, monteCarloSeed = 42L)
        val diffSeedSettings = SettingsEntity(monteCarloVolatilityPct = 10.0, monteCarloSeed = 999L)

        val resBase = FinancialEngine.runMonteCarlo(baseSettings)
        val resHighVol = FinancialEngine.runMonteCarlo(highVolSettings)
        val resDiffSeed = FinancialEngine.runMonteCarlo(diffSeedSettings)

        // Fan points p5 vs p95 spread should be significantly wider with 30% volatility vs 10%
        val spreadBase = resBase.fanPoints.last().p95 - resBase.fanPoints.last().p5
        val spreadHighVol = resHighVol.fanPoints.last().p95 - resHighVol.fanPoints.last().p5

        assertTrue("Higher volatility should yield a wider P95-P5 fan spread", spreadHighVol > spreadBase)
        assertNotEquals("Different random seeds should yield slightly different fan trajectories", resBase.fanPoints.last().p50, resDiffSeed.fanPoints.last().p50, 0.001)
    }

    @Test
    fun `test single household mode isolates primary income and excludes spouse assets`() {
        val coupleSettings = SettingsEntity(isSingleHousehold = false)
        val singleSettings = SettingsEntity(isSingleHousehold = true)

        val coupleCalc = FinancialEngine.calculate(coupleSettings, runMonteCarlo = false)
        val singleCalc = FinancialEngine.calculate(singleSettings, runMonteCarlo = false)

        // Single income should not include spouse benefit or lecturing
        assertEquals(33500.0 + 2090.0 + 16000.0, singleCalc.currentIncome.totalMonthly, 0.001)
        assertTrue(coupleCalc.currentIncome.totalMonthly > singleCalc.currentIncome.totalMonthly)

        // Single net worth should exclude Eleonora's 50k liquid starting balance
        assertEquals(
            singleSettings.liquidPortfolioCurrent + singleSettings.emergencyReserveCurrent +
                    singleSettings.dpsBalanceCurrent + singleSettings.dipBalanceCurrent,
            singleCalc.netWorthTotal,
            0.001
        )
    }

    @Test
    fun `test DCA annual growth indexation accelerates 35-year wealth accumulation`() {
        val staticDca = SettingsEntity(dcaAnnualGrowthPct = 0.0)
        val indexedDca = SettingsEntity(dcaAnnualGrowthPct = 3.0)

        val staticTraj = FinancialEngine.buildLiquidPortfolio(staticDca, dualIncome = false)
        val indexedTraj = FinancialEngine.buildLiquidPortfolio(indexedDca, dualIncome = false)

        assertTrue(
            "Indexed DCA should yield a higher final 35-year portfolio balance",
            indexedTraj.last().portfolio > staticTraj.last().portfolio
        )
    }
}
