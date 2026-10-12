package com.example

import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.HouseholdProfile
import com.example.domain.HouseholdRole
import com.example.domain.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HouseholdDomainModelAndBurnRateTest {

    @Test
    fun testTrailingBurnRateCalculationFromLedgerEntries() {
        val ledger = listOf(
            LedgerEntryEntity(yearMonth = "2026-09", expRent = 20000.0, expGroceries = 10000.0, expOther = 5000.0), // 35 000
            LedgerEntryEntity(yearMonth = "2026-08", expRent = 20000.0, expGroceries = 12000.0, expOther = 6000.0), // 38 000
            LedgerEntryEntity(yearMonth = "2026-07", expRent = 20000.0, expGroceries = 8000.0, expOther = 5000.0),  // 33 000
            LedgerEntryEntity(yearMonth = "2026-06", expRent = 20000.0, expGroceries = 9000.0, expOther = 4000.0)   // 33 000
        )

        // Trailing 3 months: Sep (35k), Aug (38k), Jul (33k) -> avg = 35 333.33
        val burn3m = FinancialEngine.computeTrailingBurnRateMonthly(ledger, 3)
        assertNotNull(burn3m)
        assertEquals((35000.0 + 38000.0 + 33000.0) / 3.0, burn3m!!, 0.01)

        // Trailing 12 months (only 4 entries exist): avg of all 4
        val burn12m = FinancialEngine.computeTrailingBurnRateMonthly(ledger, 12)
        assertNotNull(burn12m)
        assertEquals((35000.0 + 38000.0 + 33000.0 + 33000.0) / 4.0, burn12m!!, 0.01)

        // Empty ledger
        val burnEmpty = FinancialEngine.computeTrailingBurnRateMonthly(emptyList(), 3)
        assertEquals(null, burnEmpty)
    }

    @Test
    fun testHouseholdDomainModelRoundTripMapping() {
        val settings = SettingsEntity(
            isSingleHousehold = false,
            baseYear = 2026,
            vSalary = 85000.0,
            vSalaryGrowthPct = 3.5,
            eStartingSalary = 55000.0,
            eSalaryGrowthPct = 3.0,
            child1Enabled = true,
            child1BirthYear = 2024,
            child2Enabled = true,
            child2BirthYear = 2027,
            childSupportUntilAge = 26,
            emergencyReserveCurrent = 150000.0,
            dpsBalanceCurrent = 45000.0,
            dpsOwnContributionMonthly = 1700.0,
            dipBalanceCurrent = 80000.0,
            dipContributionMonthly = 4000.0,
            employerRetirementMonthly = 4166.0,
            eDpsBalanceCurrent = 30000.0,
            eDpsOwnContributionMonthly = 1000.0,
            eDipBalanceCurrent = 40000.0,
            eDipContributionMonthly = 2000.0,
            eEmployerRetirementMonthly = 2000.0
        )

        val profile = HouseholdProfile.fromSettings(settings)

        assertEquals("Vaclav", profile.primaryEarner.name)
        assertEquals(HouseholdRole.PRIMARY_EARNER, profile.primaryEarner.role)
        assertEquals(85000.0, profile.primaryEarner.currentMonthlyGrossSalary, 0.01)
        assertEquals(3.5, profile.primaryEarner.salaryGrowthPct, 0.01)
        assertEquals(4000.0, profile.primaryEarner.pensionProfile.dipMonthlyContribution, 0.01)

        assertNotNull(profile.spouseEarner)
        assertEquals("Eleonora", profile.spouseEarner!!.name)
        assertEquals(HouseholdRole.SPOUSE_EARNER, profile.spouseEarner!!.role)
        assertEquals(55000.0, profile.spouseEarner!!.currentMonthlyGrossSalary, 0.01)

        assertEquals(2, profile.children.size)
        assertEquals(2024, profile.children[0].birthYear)
        assertEquals(2027, profile.children[1].birthYear)

        assertTrue(profile.timelineEvents.any { it.type == TimelineEventType.CHILD_BIRTH && it.targetYear == 2024 })
        assertTrue(profile.timelineEvents.any { it.type == TimelineEventType.CHILD_INDEPENDENCE && it.targetYear == 2050 })

        val mappedBack = profile.toSettings(settings)
        assertEquals(settings.vSalary, mappedBack.vSalary, 0.01)
        assertEquals(settings.eStartingSalary, mappedBack.eStartingSalary, 0.01)
        assertEquals(settings.child1BirthYear, mappedBack.child1BirthYear)
        assertEquals(settings.child2BirthYear, mappedBack.child2BirthYear)
        assertEquals(settings.dpsBalanceCurrent, mappedBack.dpsBalanceCurrent, 0.01)
        assertEquals(settings.dipBalanceCurrent, mappedBack.dipBalanceCurrent, 0.01)
        assertEquals(settings.eDpsBalanceCurrent, mappedBack.eDpsBalanceCurrent, 0.01)
        assertEquals(settings.eDipBalanceCurrent, mappedBack.eDipBalanceCurrent, 0.01)
    }

    @Test
    fun testTrailingBurnRateBudgetVarianceLogic() {
        val actualBurnUnder = 45000.0
        val budgetedCost = 50000.0
        val varianceUnderPct = ((actualBurnUnder - budgetedCost) / budgetedCost) * 100.0
        assertEquals(-10.0, varianceUnderPct, 0.01)

        val actualBurnOver = 55000.0
        val varianceOverPct = ((actualBurnOver - budgetedCost) / budgetedCost) * 100.0
        assertEquals(10.0, varianceOverPct, 0.01)
    }

    @Test
    fun testHouseholdTimelineEventGenerationComprehensive() {
        val settings = SettingsEntity(
            isSingleHousehold = false,
            baseYear = 2026,
            eReturnYear = 2028,
            eStartingSalary = 48000.0,
            child1Enabled = true,
            child1BirthYear = 2024,
            child2Enabled = true,
            child2BirthYear = 2027,
            childSupportUntilAge = 26
        )

        val profile = HouseholdProfile.fromSettings(settings)
        val events = profile.timelineEvents

        val returnEvent = events.firstOrNull { it.type == TimelineEventType.CAREER_RETURN }
        assertNotNull(returnEvent)
        assertEquals(2028, returnEvent!!.targetYear)
        assertEquals(48000.0, returnEvent.monthlyCashFlowImpact, 0.01)

        val child1Independence = events.firstOrNull { it.id == "event_child1_independence" }
        assertNotNull(child1Independence)
        assertEquals(2050, child1Independence!!.targetYear)

        val child2Independence = events.firstOrNull { it.id == "event_child2_independence" }
        assertNotNull(child2Independence)
        assertEquals(2053, child2Independence!!.targetYear)
    }
}
