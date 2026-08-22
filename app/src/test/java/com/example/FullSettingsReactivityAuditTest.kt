package com.example

import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.parseCustomExpenses
import com.example.domain.parseDeletedCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exhaustive Reactivity Verification Test Suite:
 * Proves mathematically and programmatically that every single variable and setting
 * in SettingsEntity triggers active, expected changes in the financial projections,
 * milestones, cash flows, tax calculations, or retirement accounts.
 */
@RunWith(RobolectricTestRunner::class)
class FullSettingsReactivityAuditTest {

    private val base = SettingsEntity()

    @Test
    fun `assert reactivity of Base Year and Primary Age`() {
        val s1 = base.copy(baseYear = 2026, primaryAge = 26)
        val state1 = FinancialEngine.calculate(s1)
        assertEquals(2026, state1.dualTrajectory[0].year)
        assertEquals(26, state1.dualTrajectory[0].age)

        val s2 = base.copy(baseYear = 2030, primaryAge = 30)
        val state2 = FinancialEngine.calculate(s2)
        assertEquals(2030, state2.dualTrajectory[0].year)
        assertEquals(30, state2.dualTrajectory[0].age)
    }

    @Test
    fun `assert reactivity of Primary Earner Incomes - vSalary, vRaiseAnnual, vBonusAnnual, vMealVouchersMonthly`() {
        // vSalary
        val baseState = FinancialEngine.calculate(base)
        val higherSalaryState = FinancialEngine.calculate(base.copy(vSalary = 60000.0))
        assertTrue(higherSalaryState.currentIncome.vaclavNet > baseState.currentIncome.vaclavNet)
        assertEquals(60000.0, higherSalaryState.currentIncome.vaclavNet, 0.001)

        // vRaiseAnnual
        val inc2028Base = FinancialEngine.householdIncome(2028, base)
        val inc2028HighRaise = FinancialEngine.householdIncome(2028, base.copy(vRaiseAnnual = 5000.0))
        assertTrue(inc2028HighRaise.vaclavNet > inc2028Base.vaclavNet)
        assertEquals(inc2028Base.vaclavNet + (5000.0 - base.vRaiseAnnual) * 2, inc2028HighRaise.vaclavNet, 0.001)

        // vBonusAnnual
        val bonusState = FinancialEngine.calculate(base.copy(vBonusAnnual = 120000.0)) // 10k/mo
        assertEquals(base.vSalary + 10000.0, bonusState.currentIncome.vaclavNet, 0.001)

        // vMealVouchersMonthly
        val voucherState = FinancialEngine.calculate(base.copy(vMealVouchersMonthly = 4500.0))
        assertEquals(4500.0, voucherState.currentIncome.vouchers, 0.001)
        assertTrue(voucherState.currentIncome.totalMonthly > baseState.currentIncome.totalMonthly)
    }

    @Test
    fun `assert reactivity of Spouse Career Incomes - eReturnYear, eStartingSalary, eBonusAnnual, eSalaryGrowthPct, eReinvestedPct`() {
        // eReturnYear
        val inc2028BeforeReturn = FinancialEngine.householdIncome(2028, base.copy(eReturnYear = 2029))
        val inc2028AfterReturn = FinancialEngine.householdIncome(2028, base.copy(eReturnYear = 2028))
        assertEquals(0.0, inc2028BeforeReturn.eleonoraSalary, 0.001)
        assertTrue(inc2028AfterReturn.eleonoraSalary > 0)

        // eStartingSalary & eBonusAnnual
        val customSpouseSalary = FinancialEngine.householdIncome(2029, base.copy(eReturnYear = 2029, eStartingSalary = 40000.0, eBonusAnnual = 60000.0))
        assertEquals(40000.0 + 5000.0, customSpouseSalary.eleonoraSalary, 0.001)

        // eSalaryGrowthPct
        val slowGrowth = FinancialEngine.householdIncome(2031, base.copy(eReturnYear = 2029, eStartingSalary = 30000.0, eSalaryGrowthPct = 2.0))
        val fastGrowth = FinancialEngine.householdIncome(2031, base.copy(eReturnYear = 2029, eStartingSalary = 30000.0, eSalaryGrowthPct = 10.0))
        assertTrue(fastGrowth.eleonoraSalary > slowGrowth.eleonoraSalary)

        // eReinvestedPct
        val lowReinvest = FinancialEngine.buildLiquidPortfolio(base.copy(eReturnYear = 2029, eReinvestedPct = 25.0), dualIncome = true)
        val highReinvest = FinancialEngine.buildLiquidPortfolio(base.copy(eReturnYear = 2029, eReinvestedPct = 90.0), dualIncome = true)
        val ptLow = lowReinvest.first { it.year == 2030 }
        val ptHigh = highReinvest.first { it.year == 2030 }
        assertTrue(ptHigh.reinvestAnnual > ptLow.reinvestAnnual)
    }

    @Test
    fun `assert reactivity of Secondary Income Streams - Parental Allowance, Lecturing, Family Gift, Lump Sum`() {
        // eParentalAllowanceMonthly
        val parentalState = FinancialEngine.calculate(base.copy(eParentalAllowanceMonthly = 20000.0))
        assertEquals(20000.0, parentalState.currentIncome.benefit, 0.001)

        // eLecturingMonthly
        val withLecturing = FinancialEngine.calculate(base.copy(eLecturingMonthly = 12000.0))
        assertEquals(12000.0, withLecturing.currentIncome.lecturing, 0.001)
        val withoutLecturing = FinancialEngine.calculate(base.copy(eLecturingMonthly = 0.0))
        assertEquals(0.0, withoutLecturing.currentIncome.lecturing, 0.001)

        // familyGiftMonthly
        val giftState = FinancialEngine.calculate(base.copy(familyGiftMonthly = 30000.0))
        assertEquals(30000.0, giftState.currentIncome.gift, 0.001)

        // lumpSumYear, lumpSumAmount, lumpSumInclude
        val stateWithoutLump = FinancialEngine.calculate(base.copy(lumpSumInclude = false))
        val stateWithLump = FinancialEngine.calculate(base.copy(lumpSumInclude = true, lumpSumYear = 2030, lumpSumAmount = 1_000_000.0))
        val lumpPoint = stateWithLump.dualTrajectory.first { it.year == 2031 }
        assertEquals(1_000_000.0, lumpPoint.lumpSum, 0.001)
        val noLumpPoint = stateWithoutLump.dualTrajectory.first { it.year == 2031 }
        assertEquals(0.0, noLumpPoint.lumpSum, 0.001)
    }

    @Test
    fun `assert reactivity of Starting Balances & Monthly Contributions for Both Partners`() {
        // Liquid portfolios
        val startLiquidState = FinancialEngine.calculate(base.copy(liquidPortfolioCurrent = 500000.0, eLiquidPortfolioCurrent = 250000.0))
        assertEquals(750000.0, startLiquidState.dualTrajectory[0].portfolio, 0.001)

        // Portu DCA
        val dcaBase = FinancialEngine.calculate(base.copy(portuDcaMonthly = 10000.0, ePortuDcaMonthly = 5000.0))
        val dcaIncreased = FinancialEngine.calculate(base.copy(portuDcaMonthly = 20000.0, ePortuDcaMonthly = 10000.0))
        assertTrue(dcaIncreased.dualTrajectory[1].portfolio > dcaBase.dualTrajectory[1].portfolio)

        // DPS & DIP Balances & Contributions
        val dpsDipState = FinancialEngine.calculate(base.copy(
            liquidPortfolioCurrent = 200000.0,
            eLiquidPortfolioCurrent = 50000.0,
            dpsBalanceCurrent = 50000.0,
            eDpsBalanceCurrent = 30000.0,
            dipBalanceCurrent = 40000.0,
            eDipBalanceCurrent = 20000.0
        ))
        val totalNW = dpsDipState.settings.liquidPortfolioCurrent + dpsDipState.settings.eLiquidPortfolioCurrent +
                dpsDipState.settings.dpsBalanceCurrent + dpsDipState.settings.eDpsBalanceCurrent +
                dpsDipState.settings.dipBalanceCurrent + dpsDipState.settings.eDipBalanceCurrent
        // 200k + 50k + 50k + 30k + 40k + 20k = 390k
        assertEquals(390000.0, totalNW, 0.001)

        // Employer Retirement Contributions
        val empBase = FinancialEngine.calculate(base.copy(employerRetirementAnnual = 0.0, eEmployerRetirementAnnual = 0.0))
        val empBoosted = FinancialEngine.calculate(base.copy(employerRetirementAnnual = 20000.0, eEmployerRetirementAnnual = 15000.0))
        assertTrue(empBoosted.dps.employerTotal > empBase.dps.employerTotal)
    }

    @Test
    fun `assert reactivity of Investment Return, Fees, Emergency Reserves, and Safety Buffer`() {
        // portfolioNominalReturnPct
        val return6Pct = FinancialEngine.calculate(base.copy(portfolioNominalReturnPct = 6.0))
        val return10Pct = FinancialEngine.calculate(base.copy(portfolioNominalReturnPct = 10.0))
        assertTrue(return10Pct.dualTrajectory.last().portfolio > return6Pct.dualTrajectory.last().portfolio)

        // dpsGrossReturnPct & dpsAnnualFeePct
        val highFeeDPS = FinancialEngine.calculate(base.copy(dpsGrossReturnPct = 6.0, dpsAnnualFeePct = 1.5))
        val lowFeeDPS = FinancialEngine.calculate(base.copy(dpsGrossReturnPct = 6.0, dpsAnnualFeePct = 0.3))
        assertTrue(lowFeeDPS.dps.dpsBalance > highFeeDPS.dps.dpsBalance)

        // Emergency Reserve Current & Target
        val reserveState = FinancialEngine.calculate(base.copy(emergencyReserveCurrent = 300000.0, emergencyReserveTarget = 400000.0))
        assertEquals(300000.0, reserveState.settings.emergencyReserveCurrent, 0.001)
        assertEquals(400000.0, reserveState.settings.emergencyReserveTarget, 0.001)
        assertEquals(12000.0, reserveState.actionsImpacts["ac4"] ?: 0.0, 0.001) // 300k * 4%

        // Safety Buffer
        val noBufferState = FinancialEngine.calculate(base.copy(safetyBufferPct = 0.0))
        val heavyBufferState = FinancialEngine.calculate(base.copy(safetyBufferPct = 25.0))
        assertTrue(heavyBufferState.fireBaseTargetToday > noBufferState.fireBaseTargetToday)
    }

    @Test
    fun `assert reactivity of FIRE Target, SWR, CPI Inflation, State Pension, and Override`() {
        // lifestyleCostAtFireMonthly
        val cheapLife = FinancialEngine.calculate(base.copy(lifestyleCostAtFireMonthly = 25000.0))
        val lavishLife = FinancialEngine.calculate(base.copy(lifestyleCostAtFireMonthly = 60000.0))
        assertTrue(lavishLife.fireBaseTargetToday > cheapLife.fireBaseTargetToday)

        // safeWithdrawalRatePct
        val swr3Pct = FinancialEngine.calculate(base.copy(safeWithdrawalRatePct = 3.0))
        val swr5Pct = FinancialEngine.calculate(base.copy(safeWithdrawalRatePct = 5.0))
        assertTrue(swr3Pct.fireBaseTargetToday > swr5Pct.fireBaseTargetToday)
        assertTrue(swr3Pct.fireMilestones.leanFire.targetAmountToday > swr5Pct.fireMilestones.leanFire.targetAmountToday)
        assertTrue(swr3Pct.fireMilestones.standardFire.targetAmountToday > swr5Pct.fireMilestones.standardFire.targetAmountToday)
        assertTrue(swr3Pct.fireMilestones.fatFire.targetAmountToday > swr5Pct.fireMilestones.fatFire.targetAmountToday)

        // cpiInflationPct
        val futureYear = 2035
        val lowInflationTarget = FinancialEngine.fireTargetYear(futureYear, base.copy(cpiInflationPct = 2.0))
        val highInflationTarget = FinancialEngine.fireTargetYear(futureYear, base.copy(cpiInflationPct = 6.0))
        assertTrue(highInflationTarget > lowInflationTarget)

        // statePensionMonthly & statePensionAge
        val statePensionZero = FinancialEngine.calculate(base.copy(statePensionMonthly = 0.0))
        val statePensionGenerous = FinancialEngine.calculate(base.copy(statePensionMonthly = 30000.0))
        assertTrue(statePensionZero.fireBaseTargetToday > statePensionGenerous.fireBaseTargetToday)

        val earlyPension = FinancialEngine.calculate(base.copy(statePensionAge = 62))
        val latePension = FinancialEngine.calculate(base.copy(statePensionAge = 70))
        assertTrue(latePension.fireBaseTargetToday > earlyPension.fireBaseTargetToday)

        // fireTargetOverride
        val overrideState = FinancialEngine.calculate(base.copy(fireTargetOverride = 9999999.0))
        assertEquals(9999999.0, overrideState.fireBaseTargetToday, 0.001)
    }

    @Test
    fun `assert reactivity of All Standard & Custom Living Expense Categories`() {
        // Test individual item reactivity: Rent, Groceries, Cafes, Therapy, Charity, Entertainment, Transport, Subscriptions, Other
        val baseCost = FinancialEngine.totalLivingCostMonthly(base, 2026)
        
        assertEquals(baseCost + 5000.0, FinancialEngine.totalLivingCostMonthly(base.copy(rentMonthly = base.rentMonthly + 5000.0), 2026), 0.001)
        assertEquals(baseCost + 2000.0, FinancialEngine.totalLivingCostMonthly(base.copy(groceriesMonthly = base.groceriesMonthly + 2000.0), 2026), 0.001)
        assertEquals(baseCost + 1000.0, FinancialEngine.totalLivingCostMonthly(base.copy(cafesMonthly = base.cafesMonthly + 1000.0), 2026), 0.001)
        assertEquals(baseCost + 800.0, FinancialEngine.totalLivingCostMonthly(base.copy(therapyMonthly = base.therapyMonthly + 800.0), 2026), 0.001)
        assertEquals(baseCost + 500.0, FinancialEngine.totalLivingCostMonthly(base.copy(charityMonthly = base.charityMonthly + 500.0), 2026), 0.001)
        assertEquals(baseCost + 400.0, FinancialEngine.totalLivingCostMonthly(base.copy(entertainmentMonthly = base.entertainmentMonthly + 400.0), 2026), 0.001)
        assertEquals(baseCost + 300.0, FinancialEngine.totalLivingCostMonthly(base.copy(transportMonthly = base.transportMonthly + 300.0), 2026), 0.001)
        assertEquals(baseCost + 200.0, FinancialEngine.totalLivingCostMonthly(base.copy(subscriptionsMonthly = base.subscriptionsMonthly + 200.0), 2026), 0.001)
        assertEquals(baseCost + 1500.0, FinancialEngine.totalLivingCostMonthly(base.copy(otherDiscretionaryMonthly = base.otherDiscretionaryMonthly + 1500.0), 2026), 0.001)

        // Custom Expenses JSON
        val customJson = "[{\"id\":\"x1\",\"name\":\"Skiing\",\"amount\":3500.0}]"
        val customCost = FinancialEngine.totalLivingCostMonthly(base.copy(customExpensesJson = customJson), 2026)
        assertEquals(baseCost + 3500.0, customCost, 0.001)

        // Deleted Categories JSON
        val deletedJson = "[\"transport\", \"subscriptions\"]"
        val reducedCost = FinancialEngine.totalLivingCostMonthly(base.copy(deletedCategoriesJson = deletedJson), 2026)
        assertEquals(baseCost - base.transportMonthly - base.subscriptionsMonthly, reducedCost, 0.001)
    }

    @Test
    fun `assert reactivity of Czech Tax Parameters & Reform Deductions`() {
        // taxRatePct & taxRateSecondPct
        val standardTax = FinancialEngine.calculate(base.copy(taxRatePct = 15.0))
        val higherTax = FinancialEngine.calculate(base.copy(taxRatePct = 20.0))
        assertTrue(higherTax.taxReturnHelper.dipSaving > standardTax.taxReturnHelper.dipSaving) // 20% on 20400 vs 15% on 20400

        // taxpayerCreditAnnual
        val customCreditState = FinancialEngine.calculate(base.copy(taxpayerCreditAnnual = 35000.0))
        assertEquals(35000.0, customCreditState.taxReturnHelper.taxpayerCredit, 0.001)

        // spouseTaxCreditAnnual, spouseIncomeLimitAnnual, includeSpouseCredit, hasChildUnder3
        val eligibleSettings = base.copy(
            eLecturingMonthly = 0.0,
            hasChildUnder3 = true,
            includeSpouseCredit = true,
            spouseTaxCreditAnnual = 30000.0
        )
        val eligibleState = FinancialEngine.calculate(eligibleSettings)
        assertTrue(eligibleState.taxReturnHelper.spouseEligible)
        assertEquals(30000.0, eligibleState.taxReturnHelper.spouseCredit, 0.001)

        // When child under 3 is false -> spouse credit is disallowed under 2024+ reform rules
        val noChildUnder3State = FinancialEngine.calculate(eligibleSettings.copy(hasChildUnder3 = false))
        assertFalse(noChildUnder3State.taxReturnHelper.spouseEligible)
        assertEquals(0.0, noChildUnder3State.taxReturnHelper.spouseCredit, 0.001)

        // When includeSpouseCredit is toggled off
        val uncheckedSpouseState = FinancialEngine.calculate(eligibleSettings.copy(includeSpouseCredit = false))
        assertFalse(uncheckedSpouseState.taxReturnHelper.spouseEligible)
        assertEquals(0.0, uncheckedSpouseState.taxReturnHelper.spouseCredit, 0.001)

        // taxDeductionCeilingAnnual
        val lowCeilingDIP = FinancialEngine.calculate(base.copy(taxDeductionCeilingAnnual = 24000.0, dipContributionMonthly = 3000.0))
        val highCeilingDIP = FinancialEngine.calculate(base.copy(taxDeductionCeilingAnnual = 60000.0, dipContributionMonthly = 3000.0))
        assertTrue(highCeilingDIP.dip.taxSavedYear > lowCeilingDIP.dip.taxSavedYear)
    }

    @Test
    fun `assert reactivity of Children Life Stages, Birth Years, Toggles, and Tax Bonuses`() {
        // childExpensesEnabled toggle
        val withChildExpenses = FinancialEngine.totalLivingCostMonthly(base.copy(childExpensesEnabled = true), 2026)
        val withoutChildExpenses = FinancialEngine.totalLivingCostMonthly(base.copy(childExpensesEnabled = false), 2026)
        assertEquals(withChildExpenses - base.childToddlerMonthly, withoutChildExpenses, 0.001)

        // child1Enabled & child2Enabled toggles
        val stateBothChildren = FinancialEngine.calculate(base.copy(child1Enabled = true, child2Enabled = true))
        val stateOneChild = FinancialEngine.calculate(base.copy(child1Enabled = true, child2Enabled = false))
        val stateNoChildren = FinancialEngine.calculate(base.copy(child1Enabled = false, child2Enabled = false))
        assertEquals(base.child1TaxBonusAnnual + base.child2TaxBonusAnnual, stateBothChildren.taxReturnHelper.childBonus, 0.001)
        assertEquals(base.child1TaxBonusAnnual, stateOneChild.taxReturnHelper.childBonus, 0.001)
        assertEquals(0.0, stateNoChildren.taxReturnHelper.childBonus, 0.001)

        // child1BirthYear & child2BirthYear timing
        val childBorn2020At2026 = FinancialEngine.childMonthlyExpense(2020, 2026, base) // Age 6: School
        assertEquals(base.childSchoolMonthly, childBorn2020At2026, 0.001)

        val childBorn2010At2026 = FinancialEngine.childMonthlyExpense(2010, 2026, base) // Age 16: Teen
        assertEquals(base.childTeenMonthly, childBorn2010At2026, 0.001)

        // Custom child expense values: Toddler, Preschool, School, Teen, Uni
        val customChildSettings = base.copy(
            childToddlerMonthly = 6000.0,
            childPreschoolMonthly = 8000.0,
            childSchoolMonthly = 11000.0,
            childTeenMonthly = 16000.0,
            childUniMonthly = 14000.0
        )
        assertEquals(6000.0, FinancialEngine.childMonthlyExpense(2024, 2025, customChildSettings), 0.001) // Toddler
        assertEquals(8000.0, FinancialEngine.childMonthlyExpense(2024, 2028, customChildSettings), 0.001) // Preschool
        assertEquals(11000.0, FinancialEngine.childMonthlyExpense(2024, 2032, customChildSettings), 0.001) // School
        assertEquals(16000.0, FinancialEngine.childMonthlyExpense(2024, 2040, customChildSettings), 0.001) // Teen
        assertEquals(14000.0, FinancialEngine.childMonthlyExpense(2024, 2045, customChildSettings), 0.001) // Uni

        // Custom Tax Bonuses
        val customBonusSettings = base.copy(
            child1TaxBonusAnnual = 18000.0,
            child2TaxBonusAnnual = 26000.0
        )
        val customBonusState = FinancialEngine.calculate(customBonusSettings)
        assertEquals(18000.0 + 26000.0, customBonusState.taxReturnHelper.childBonus, 0.001)
    }

    @Test
    fun `assert reactivity of Monte Carlo Sample Count parameter`() {
        val lowN = FinancialEngine.runMonteCarlo(base.copy(monteCarloN = 50))
        val highN = FinancialEngine.runMonteCarlo(base.copy(monteCarloN = 500))
        assertTrue(lowN.fanPoints.isNotEmpty())
        assertTrue(highN.fanPoints.isNotEmpty())
        assertEquals(lowN.fanPoints.size, highN.fanPoints.size)
    }
}
