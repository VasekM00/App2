package com.example

import com.example.data.ActionMeta
import com.example.data.SettingsEntity
import com.example.domain.CustomExpenseItem
import com.example.domain.CustomLifeGoalItem
import com.example.domain.FinancialEngine
import com.example.domain.parseCustomExpenses
import com.example.domain.parseCustomLifeGoals
import com.example.domain.parseDeletedCategories
import com.example.domain.serializeCustomExpenses
import com.example.domain.serializeCustomLifeGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

/**
 * Exhaustive audit test suite covering every single input field, variable,
 * tax rule, trajectory projection, milestone hierarchy, and dynamic description
 * in the financial engine.
 */
@RunWith(RobolectricTestRunner::class)
class FinancialEngineExhaustiveAuditTest {

    private val defaultSettings = SettingsEntity()

    @Test
    fun `test 1 - income calculations with variable modifications`() {
        // Base case (2026: Parental leave + lecturing)
        val income2026 = FinancialEngine.householdIncome(2026, defaultSettings)
        assertEquals(33500.0, income2026.vaclavNet, 0.001)
        assertEquals(0.0, income2026.eleonoraSalary, 0.001)
        assertEquals(13000.0, income2026.benefit, 0.001) // Direct monthly parental allowance
        assertEquals(6900.0, income2026.lecturing, 0.001)
        assertEquals(2090.0, income2026.vouchers, 0.001)
        assertEquals(16000.0, income2026.gift, 0.001)
        assertEquals(71490.0, income2026.totalMonthly, 0.001)

        // Base case (2029: Eleonora returns to work -> Allowance & Lecturing vanish, replaced by Salary)
        val income2029 = FinancialEngine.householdIncome(2029, defaultSettings)
        assertEquals(33500.0, income2029.vaclavNet, 0.001)
        assertEquals(22000.0, income2029.eleonoraSalary, 0.001) // 22k starting salary
        assertEquals(0.0, income2029.benefit, 0.001) // Vanished
        assertEquals(0.0, income2029.lecturing, 0.001) // Vanished
        assertEquals(2090.0, income2029.vouchers, 0.001)
        assertEquals(16000.0, income2029.gift, 0.001)
        assertEquals(73590.0, income2029.totalMonthly, 0.001)

        // Mutate Vaclav salary, other inflows
        val modifiedSettings = defaultSettings.copy(
            vSalary = 45000.0,
            vOtherInflowsMonthly = 5000.0,
            vMealVouchersMonthly = 3000.0,
            familyGiftMonthly = 20000.0,
            eReturnYear = 2028,
            eStartingSalary = 30000.0,
            eOtherInflowsMonthly = 2000.0,
            eSalaryGrowthPct = 5.0,
            eParentalAllowanceMonthly = 15000.0,
            eLecturingMonthly = 0.0
        )

        // Year 2026 (before Eleonora returns, with no lecturing)
        val inc2026Mod = FinancialEngine.householdIncome(2026, modifiedSettings)
        assertEquals(50000.0, inc2026Mod.vaclavNet, 0.001) // 45000 + 5000 other inflows
        assertEquals(0.0, inc2026Mod.eleonoraSalary, 0.001)
        assertEquals(15000.0, inc2026Mod.benefit, 0.001) // Direct monthly parental allowance at 15k
        assertEquals(0.0, inc2026Mod.lecturing, 0.001)
        assertEquals(3000.0, inc2026Mod.vouchers, 0.001)
        assertEquals(20000.0, inc2026Mod.gift, 0.001)
        assertEquals(90000.0, inc2026Mod.totalMonthly, 0.001) // 50k + 15k + 0 + 3k + 20k + 2k (eOther)

        // Year 2028 (Eleonora returns)
        val inc2028Mod = FinancialEngine.householdIncome(2028, modifiedSettings)
        assertEquals(50000.0, inc2028Mod.vaclavNet, 0.001) // 45000 + 5000 other inflows
        assertEquals(30000.0, inc2028Mod.eleonoraSalary, 0.001)
        assertEquals(0.0, inc2028Mod.benefit, 0.001) // Parental benefit ends on return
        assertEquals(0.0, inc2028Mod.lecturing, 0.001)
        assertEquals(105000.0, inc2028Mod.totalMonthly, 0.001) // 50k + 30k + 0 + 3k + 20k + 2k (eOther)

        // Year 2029 (Eleonora with 5% salary growth)
        val inc2029Mod = FinancialEngine.householdIncome(2029, modifiedSettings)
        val expectedESalary2029 = 30000.0 * 1.05 // 31500
        assertEquals(expectedESalary2029, inc2029Mod.eleonoraSalary, 0.01)
    }

    @Test
    fun `test 2 - living expenses, child age brackets, and custom categories`() {
        val baseCost = FinancialEngine.totalLivingCostMonthly(defaultSettings, 2026)
        // Rent (21770) + Groceries (4800) + Cafes (2250) + Therapy (2000) + Charity (2000) +
        // Entertainment (1200) + Transport (650) + Subscriptions (584) + Other (1500)
        // Child 1 (born 2024, age 2 in 2026 -> Toddler = 4800)
        // Child 2 (born 2027, age -1 in 2026 -> not born = 0)
        val expectedBase = 21770.0 + 4800.0 + 2250.0 + 2000.0 + 2000.0 + 1200.0 + 650.0 + 584.0 + 1500.0 + 4800.0
        assertEquals(expectedBase, baseCost, 0.001)

        // Test Child 1 through all age stages:
        // Age 0-2 Toddler (2026), Age 3-5 Preschool (2027-2029), Age 6-14 School (2030-2038), Age 15-18 Teen (2039-2042), Age 19-25 Uni (2043-2049)
        val cost2027 = FinancialEngine.childMonthlyExpense(2024, 2027, defaultSettings) // Age 3: Preschool
        assertEquals(defaultSettings.childPreschoolMonthly, cost2027, 0.001)

        val cost2030 = FinancialEngine.childMonthlyExpense(2024, 2030, defaultSettings) // Age 6: School
        assertEquals(defaultSettings.childSchoolMonthly, cost2030, 0.001)

        val cost2039 = FinancialEngine.childMonthlyExpense(2024, 2039, defaultSettings) // Age 15: Teen
        assertEquals(defaultSettings.childTeenMonthly, cost2039, 0.001)

        val cost2043 = FinancialEngine.childMonthlyExpense(2024, 2043, defaultSettings) // Age 19: Uni
        assertEquals(defaultSettings.childUniMonthly, cost2043, 0.001)

        val cost2050 = FinancialEngine.childMonthlyExpense(2024, 2050, defaultSettings) // Age 26: Graduated -> 0
        assertEquals(0.0, cost2050, 0.001)

        // Test Custom Categories & Deleted Categories
        val customExpense = listOf(
            CustomExpenseItem(id = "c1", name = "Gym membership", amount = 1500.0),
            CustomExpenseItem(id = "c2", name = "Pet food", amount = 800.0)
        )
        val jsonExpenses = serializeCustomExpenses(customExpense)
        val deletedJson = "[\"therapy\", \"charity\"]"

        val modifiedSettings = defaultSettings.copy(
            customExpensesJson = jsonExpenses,
            deletedCategoriesJson = deletedJson,
            rentMonthly = 25000.0,
            groceriesMonthly = 6000.0
        )

        val parsedExpenses = parseCustomExpenses(jsonExpenses)
        assertEquals(2, parsedExpenses.size)
        assertEquals("Gym membership", parsedExpenses[0].name)
        assertEquals(1500.0, parsedExpenses[0].amount, 0.001)

        val parsedDeleted = parseDeletedCategories(deletedJson)
        assertTrue(parsedDeleted.contains("therapy"))
        assertTrue(parsedDeleted.contains("charity"))

        val newTotalLiving = FinancialEngine.totalLivingCostMonthly(modifiedSettings, 2026)
        // 25000 (rent) + 6000 (groceries) + 2250 (cafes) + 0 (therapy deleted) + 0 (charity deleted) +
        // 1200 (entertainment) + 650 (transport) + 584 (subscriptions) + 1500 (other) + 2300 (custom) + 4800 (child 1)
        val expectedNewTotal = 25000.0 + 6000.0 + 2250.0 + 1200.0 + 650.0 + 584.0 + 1500.0 + 2300.0 + 4800.0
        assertEquals(expectedNewTotal, newTotalLiving, 0.001)
    }

    @Test
    fun `test 3 - fire target calculation and state pension bridge`() {
        // Base calculation
        val fireBase = FinancialEngine.fireTargetBase(defaultSettings)
        assertTrue("FIRE base target must be positive", fireBase > 0)

        // Test with explicit override
        val overriddenSettings = defaultSettings.copy(fireTargetOverride = 12500000.0)
        val overriddenTarget = FinancialEngine.fireTargetBase(overriddenSettings)
        assertEquals(12500000.0, overriddenTarget, 0.001)

        // Test inflation indexing
        val targetIn5Years = FinancialEngine.fireTargetYear(2031, defaultSettings, age = defaultSettings.primaryAge)
        val expectedInflated = fireBase * Math.pow(1.0 + defaultSettings.cpiInflationPct / 100.0, 5.0)
        assertEquals(expectedInflated, targetIn5Years, 0.01)

        val targetIn5YearsWithAging = FinancialEngine.fireTargetYear(2031, defaultSettings)
        val expectedWithAging = FinancialEngine.fireTargetBase(defaultSettings, age = 31) * Math.pow(1.0 + defaultSettings.cpiInflationPct / 100.0, 5.0)
        assertEquals(expectedWithAging, targetIn5YearsWithAging, 0.01)

        // Test state pension bridge years
        val bridgeYearsAt26 = FinancialEngine.statePensionBridgeYears(26, defaultSettings)
        assertEquals(39, bridgeYearsAt26) // 65 - 26

        val bridgeYearsAt70 = FinancialEngine.statePensionBridgeYears(70, defaultSettings)
        assertEquals(0, bridgeYearsAt70) // max(0, 65 - 70)
    }

    @Test
    fun `test 4 - lepsi penzijko DPS reform dynamic subsidy rates and limits`() {
        // Under 30 (youth active) -> 40% up to 680 CZK
        val youthSubsidy1000 = FinancialEngine.dpsSubsidy(1000.0, 26, defaultSettings)
        assertEquals(400.0, youthSubsidy1000, 0.001) // 1000 * 0.40 = 400 <= 680

        val youthSubsidy1700 = FinancialEngine.dpsSubsidy(1700.0, 26, defaultSettings)
        assertEquals(680.0, youthSubsidy1700, 0.001) // 1700 * 0.40 = 680 (capped at 680)

        val youthSubsidy2500 = FinancialEngine.dpsSubsidy(2500.0, 26, defaultSettings)
        assertEquals(680.0, youthSubsidy2500, 0.001) // Capped at 680

        // Over 30 (standard rate) -> 20% up to 340 CZK
        val stdSubsidy1000 = FinancialEngine.dpsSubsidy(1000.0, 31, defaultSettings)
        assertEquals(200.0, stdSubsidy1000, 0.001) // 1000 * 0.20 = 200 <= 340

        val stdSubsidy1700 = FinancialEngine.dpsSubsidy(1700.0, 31, defaultSettings)
        assertEquals(340.0, stdSubsidy1700, 0.001) // 1700 * 0.20 = 340 (capped at 340)

        // Below minimum deposit for subsidy (< 500 CZK) -> 0 CZK
        val belowMin = FinancialEngine.dpsSubsidy(400.0, 26, defaultSettings)
        assertEquals(0.0, belowMin, 0.001)

        // Mutate custom youth parameters: Youth limit = 35, youth rate = 50%, youth max = 1000, min deposit = 600
        val customDpsSettings = defaultSettings.copy(
            dpsYouthAgeLimit = 35,
            dpsSubsidyRateYouthPct = 50.0,
            dpsYouthSubsidyMaxMonthly = 1000.0,
            dpsMinDepositForSubsidy = 600.0
        )
        val customYouthAt32 = FinancialEngine.dpsSubsidy(1800.0, 32, customDpsSettings)
        assertEquals(900.0, customYouthAt32, 0.001) // 1800 * 0.50 = 900 <= 1000

        val customBelowMin = FinancialEngine.dpsSubsidy(500.0, 32, customDpsSettings)
        assertEquals(0.0, customBelowMin, 0.001) // 500 < 600
    }

    @Test
    fun `test 5 - tax return helper, spouse credit, and child bonuses`() {
        val state = FinancialEngine.calculate(defaultSettings)
        val tax = state.taxReturnHelper

        assertEquals(30840.0, tax.taxpayerCredit, 0.001)
        // With default eLecturing = 6900/mo (82800/yr > 68000/yr limit), spouse credit is properly disallowed
        assertFalse("Spouse earning 82.8k/yr from lecturing exceeds 68k threshold", tax.spouseEligible)
        assertEquals(0.0, tax.spouseCredit, 0.001)
        assertEquals(15204.0, tax.childBonus, 0.001) // Child 1 only: Child 2 (born 2027) is not yet born in baseYear

        // DIP Tax Saving on default 1700/mo
        // Annual deduction = 1700 * 12 = 20400. Tax saving = 20400 * 0.15 = 3060
        assertEquals(3060.0, tax.dipSaving, 0.001)

        // Eligible Spouse: When spouse income <= 68k and has child under 3
        val eligibleSpouseSettings = defaultSettings.copy(
            eLecturingMonthly = 0.0,
            hasChildUnder3 = true,
            includeSpouseCredit = true
        )
        val eligibleState = FinancialEngine.calculate(eligibleSpouseSettings)
        assertTrue("Spouse with 0 income and child under 3 is eligible", eligibleState.taxReturnHelper.spouseEligible)
        assertEquals(24840.0, eligibleState.taxReturnHelper.spouseCredit, 0.001)

        // Mutate: Exceed spouse income limit -> spouse credit should become 0
        val richSpouseSettings = defaultSettings.copy(
            eStartingSalary = 40000.0,
            eReturnYear = 2026 // Active in 2026 -> annual income = 40000*12 = 480000 > 68000
        )
        val richSpouseState = FinancialEngine.calculate(richSpouseSettings)
        assertFalse("Spouse earning > 68k/yr should not be eligible", richSpouseState.taxReturnHelper.spouseEligible)
        assertEquals(0.0, richSpouseState.taxReturnHelper.spouseCredit, 0.001)

        // Mutate: Disable all children -> no child bonus (expense toggle alone no longer gates § 35c bonus)
        val noChildSettings = defaultSettings.copy(child1Enabled = false, child2Enabled = false)
        val noChildState = FinancialEngine.calculate(noChildSettings)
        assertEquals(0.0, noChildState.taxReturnHelper.childBonus, 0.001)
    }

    @Test
    fun `test 6 - fire milestones hierarchy and dynamic descriptions`() {
        val state = FinancialEngine.calculate(defaultSettings)
        val ms = state.fireMilestones

        // Check Milestone Targets Ordering: Coast <= Lean <= Standard <= Fat
        assertTrue(ms.coastFire.targetAmountToday <= ms.standardFire.targetAmountToday)
        assertTrue(ms.leanFire.targetAmountToday < ms.standardFire.targetAmountToday)
        assertTrue(ms.standardFire.targetAmountToday < ms.fatFire.targetAmountToday)

        // Check Lean FIRE target is 75% of Standard FIRE
        val leanExpected = kotlin.math.round((state.fireBaseTargetToday * 0.75) / 10000.0) * 10000.0
        assertEquals(leanExpected, ms.leanFire.targetAmountToday, 0.001)

        // Check Fat FIRE target is 130% of Standard FIRE
        val fatExpected = kotlin.math.round((state.fireBaseTargetToday * 1.30) / 10000.0) * 10000.0
        assertEquals(fatExpected, ms.fatFire.targetAmountToday, 0.001)

        // Check Coast description contains dynamic retirement age
        assertTrue(ms.coastFire.description.contains("age ${defaultSettings.vStatePensionAge}"))

        // Check if mutating vStatePensionAge changes Coast description
        val laterRetirementSettings = defaultSettings.copy(vStatePensionAge = 70)
        val laterState = FinancialEngine.calculate(laterRetirementSettings)
        assertTrue(laterState.fireMilestones.coastFire.description.contains("age 70"))

        // Check milestone achieved status when net worth is huge
        val wealthySettings = defaultSettings.copy(liquidPortfolioCurrent = 20000000.0)
        val wealthyState = FinancialEngine.calculate(wealthySettings)
        assertTrue(wealthyState.fireMilestones.coastFire.isAchieved)
        assertTrue(wealthyState.fireMilestones.leanFire.isAchieved)
        assertTrue(wealthyState.fireMilestones.standardFire.isAchieved)
        assertTrue(wealthyState.fireMilestones.fatFire.isAchieved)
        assertEquals(100.0, wealthyState.fireMilestones.standardFire.progressPct, 0.001)

        // SWR Reactivity Test: Verify Lean FIRE target and monthly SWR flow react to safeWithdrawalRatePct
        val conservativeSwrSettings = defaultSettings.copy(safeWithdrawalRatePct = 3.0)
        val conservativeState = FinancialEngine.calculate(conservativeSwrSettings)
        assertTrue(
            "Lean FIRE target at 3.0% SWR must be higher than at 4.0% SWR",
            conservativeState.fireMilestones.leanFire.targetAmountToday > state.fireMilestones.leanFire.targetAmountToday
        )
        val expectedLean3PctIncome = kotlin.math.round(((conservativeState.fireMilestones.leanFire.targetAmountToday * 0.03) / 12.0) / 1000.0) * 1000.0
        assertEquals(expectedLean3PctIncome, conservativeState.fireMilestones.leanFire.monthlyPassiveIncome, 0.001)

        val aggressiveSwrSettings = defaultSettings.copy(safeWithdrawalRatePct = 5.0)
        val aggressiveState = FinancialEngine.calculate(aggressiveSwrSettings)
        assertTrue(
            "Lean FIRE target at 5.0% SWR must be lower than at 4.0% SWR",
            aggressiveState.fireMilestones.leanFire.targetAmountToday < state.fireMilestones.leanFire.targetAmountToday
        )
        val expectedLean5PctIncome = kotlin.math.round(((aggressiveState.fireMilestones.leanFire.targetAmountToday * 0.05) / 12.0) / 1000.0) * 1000.0
        assertEquals(expectedLean5PctIncome, aggressiveState.fireMilestones.leanFire.monthlyPassiveIncome, 0.001)
    }

    @Test
    fun `test 7 - stress scenarios calculations`() {
        val scenarios = FinancialEngine.calculateStressScenarios(defaultSettings)
        assertEquals(5, scenarios.size)

        val baseline = scenarios.first { it.id == "baseline" }
        val bull = scenarios.first { it.id == "bull" }
        val stagflation = scenarios.first { it.id == "stagflation" }
        val crash = scenarios.first { it.id == "crash" }
        val inflationShock = scenarios.first { it.id == "inflation_shock" }

        assertEquals(9.0, bull.nominalReturnPct, 0.001)
        assertEquals(2.0, bull.cpiInflationPct, 0.001)

        assertEquals(4.5, stagflation.nominalReturnPct, 0.001)
        assertEquals(5.0, stagflation.cpiInflationPct, 0.001)

        assertEquals(6.5, inflationShock.nominalReturnPct, 0.001)
        assertEquals(6.0, inflationShock.cpiInflationPct, 0.001)

        // Crash scenario should have lower initial portfolio in year 0
        val normalStart = baseline.trajectory[0].portfolio
        val crashStart = crash.trajectory[0].portfolio
        assertEquals(normalStart * 0.75, crashStart, 0.01)
    }

    @Test
    fun `test 8 - monte carlo deterministic simulation and caching`() {
        val mc1 = FinancialEngine.runMonteCarlo(defaultSettings)
        val mc2 = FinancialEngine.runMonteCarlo(defaultSettings)

        // Determinism test with identical seed
        assertEquals(mc1.successRatePct, mc2.successRatePct, 0.001)
        assertEquals(mc1.medianFireAge, mc2.medianFireAge)
        assertEquals(mc1.fanPoints.size, mc2.fanPoints.size)

        // Check fan point bounds: p5 <= p50 <= p95
        mc1.fanPoints.forEach { pt ->
            assertTrue("p5 must be <= p50", pt.p5 <= pt.p50)
            assertTrue("p50 must be <= p95", pt.p50 <= pt.p95)
        }
    }

    @Test
    fun `test 9 - action impacts dynamic calculations`() {
        val state = FinancialEngine.calculate(defaultSettings)
        val impacts = state.actionsImpacts

        // ac1: lecturing annual
        val expectedAc1 = defaultSettings.eLecturingMonthly * 12.0
        assertEquals(expectedAc1, impacts["ac1"] ?: 0.0, 0.001)

        // ac2: tax return helper incremental value
        assertEquals(state.taxReturnHelper.totalIncrementalValue, impacts["ac2"] ?: 0.0, 0.001)

        // ac3: 1% equity optimization on liquid portfolio
        val expectedAc3 = (defaultSettings.liquidPortfolioCurrent + defaultSettings.eLiquidPortfolioCurrent) * 0.01
        assertEquals(expectedAc3, impacts["ac3"] ?: 0.0, 0.001)

        // ac4: 4% yield on emergency reserve
        val expectedAc4 = defaultSettings.emergencyReserveCurrent * 0.04
        assertEquals(expectedAc4, impacts["ac4"] ?: 0.0, 0.001)

        // ac5: DIP tax saving
        assertEquals(state.dip.taxSavedYear, impacts["ac5"] ?: 0.0, 0.001)

        // ac8: subscriptions annual
        val expectedAc8 = defaultSettings.subscriptionsMonthly * 12.0
        assertEquals(expectedAc8, impacts["ac8"] ?: 0.0, 0.001)
    }

    @Test
    fun `test 10 - custom life goals parser and serializer robustness`() {
        val goals = listOf(
            CustomLifeGoalItem("g1", "Family Van", "DirectionsCar", 2028, 600000.0, 150000.0),
            CustomLifeGoalItem("g2", "Down Payment", "Home", 2032, 2000000.0, 400000.0)
        )
        val serialized = serializeCustomLifeGoals(goals)
        val parsed = parseCustomLifeGoals(serialized)

        assertEquals(2, parsed.size)
        assertEquals("Family Van", parsed[0].name)
        assertEquals(600000.0, parsed[0].targetAmountCzk, 0.001)
        assertEquals(150000.0, parsed[0].currentSavedCzk, 0.001)

        // Fallback on empty or malformed JSON (3 default starter goals)
        val emptyParsed = parseCustomLifeGoals("")
        assertEquals(3, emptyParsed.size)

        val invalidParsed = parseCustomLifeGoals("invalid-json{}}")
        assertEquals(3, invalidParsed.size)
    }
}
