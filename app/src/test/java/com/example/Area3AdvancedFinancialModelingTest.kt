package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
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
class Area3AdvancedFinancialModelingTest {

    @Test
    fun testGuytonKlingerCustomThresholdsAndSurvival() {
        val s = SettingsEntity.freshDefaults().copy(
            guardrailsEnabled = true,
            guardrailsUpperThresholdPct = 25.0,
            guardrailsLowerThresholdPct = 15.0,
            guardrailsCutPct = 12.0,
            guardrailsBoostPct = 8.0,
            guardrailsFloorPct = 65.0,
            monteCarloN = 100
        )

        val survival = FinancialEngine.runRetirementSurvival(
            settings = s,
            useHistoricalBootstrap = false
        )

        assertTrue("Guardrails must be reported active when enabled", survival.guardrailsActive)
        assertTrue("Survival success rate must be valid percentage", survival.successRatePct in 0.0..100.0)
    }

    @Test
    fun testBaristaPreRetirementRamp() {
        val baseYear = 2026
        val baristaStartYear = 2032
        val s = SettingsEntity.freshDefaults().copy(
            baseYear = baseYear,
            primaryAge = 26,
            portuDcaMonthly = 20000.0,
            baristaTransitionEnabled = true,
            baristaStartYear = baristaStartYear,
            baristaMonthlyIncome = 35000.0,
            baristaSavingsMonthly = 4000.0
        )

        val trajectory = FinancialEngine.buildLiquidPortfolio(s, dualIncome = false)
        assertTrue("Trajectory must span 35 years", trajectory.size >= 30)

        // Prior to baristaStartYear, investedAnnual should reflect full DCA (20,000 * 12 = 240,000 + tax refund)
        val preBaristaPoint = trajectory.first { it.year > baseYear && it.year < baristaStartYear }
        assertTrue("Pre-barista invested annual must exceed 200,000", preBaristaPoint.investedAnnual > 200000.0)

        // During and after baristaStartYear (sy >= baristaStartYear -> end-of-year point.year > baristaStartYear),
        // pre-pension points before FIRE target must be tagged "Barista Active"
        val baristaPoints = trajectory.filter {
            it.year > baristaStartYear && it.age < s.vStatePensionAge && it.totalPortfolio < it.target
        }
        if (baristaPoints.isNotEmpty()) {
            for (p in baristaPoints) {
                assertEquals("Barista Active", p.status)
            }
        }
    }

    @Test
    fun testYtdTaxShieldTrackerFromTransactions() {
        val s = SettingsEntity.freshDefaults().copy(
            baseYear = 2026,
            taxRatePct = 15.0,
            taxRateSecondPct = 23.0,
            taxDeductionCeilingAnnual = 48000.0,
            dpsDeductionThresholdMonthly = 1700.0
        )

        val currentYear = 2026
        val txs = listOf(
            // Month 1: DIP 2 000 CZK
            ImportedBankTransactionEntity(
                yearMonth = "2026-01",
                bankName = "MONETA",
                date = "2026-01-15",
                amount = -2000.0,
                category = "INVESTMENT_DIP"
            ),
            // Month 1: DPS 3 700 CZK (3 700 - 1 700 threshold = 2 000 qualifying deduction)
            ImportedBankTransactionEntity(
                yearMonth = "2026-01",
                bankName = "MONETA",
                date = "2026-01-20",
                amount = -3700.0,
                category = "INVESTMENT_DPS"
            ),
            // Month 2: DIP 3 000 CZK
            ImportedBankTransactionEntity(
                yearMonth = "2026-02",
                bankName = "MONETA",
                date = "2026-02-15",
                amount = -3000.0,
                category = "INVESTMENT_DIP"
            ),
            // Month 2: DPS 1 700 CZK (1 700 - 1 700 threshold = 0 qualifying deduction)
            ImportedBankTransactionEntity(
                yearMonth = "2026-02",
                bankName = "MONETA",
                date = "2026-02-20",
                amount = -1700.0,
                category = "INVESTMENT_DPS"
            )
        )

        // Run calculation for Month 2 (February)
        val status = FinancialEngine.computeYtdTaxShieldStatus(
            settings = s,
            currentYear = currentYear,
            currentMonth = 2,
            importedTransactions = txs
        )

        assertEquals(2026, status.currentYear)
        assertEquals(48000.0, status.statutoryCeilingAnnual, 0.001)
        assertEquals(5000.0, status.ytdDipContributed, 0.001) // 2000 + 3000
        assertEquals(2000.0, status.ytdDpsContributed, 0.001) // (3700 - 1700) + max(0, 1700 - 1700) = 2000
        assertEquals(7000.0, status.ytdTotalContributed, 0.001)
        assertEquals((7000.0 / 48000.0) * 100.0, status.ytdProgressPct, 0.01)

        // 15% bracket refund secured: 7000 * 0.15 = 1050 CZK
        assertEquals(1050.0, status.taxRefundSecured15Pct, 0.001)
        // 23% bracket refund secured: 7000 * 0.23 = 1610 CZK
        assertEquals(1610.0, status.taxRefundSecured23Pct, 0.001)

        // Remaining headroom: 48000 - 7000 = 41000 CZK
        assertEquals(41000.0, status.remainingHeadroomAnnual, 0.001)
        // Months remaining: 12 - 2 = 10 remaining full months (Mar..Dec)
        assertEquals(10, status.monthsRemainingInYear)
        assertEquals(41000.0 / 10.0, status.recommendedMonthlyPace, 0.01)
        assertFalse(status.isCeilingReached)
    }

    @Test
    fun testYtdTaxShieldTrackerMaxedOut() {
        val s = SettingsEntity.freshDefaults().copy(
            baseYear = 2026,
            taxRatePct = 15.0,
            taxRateSecondPct = 23.0,
            taxDeductionCeilingAnnual = 48000.0
        )

        val txs = listOf(
            ImportedBankTransactionEntity(
                yearMonth = "2026-06",
                bankName = "MONETA",
                date = "2026-06-15",
                amount = -50000.0,
                category = "INVESTMENT_DIP"
            )
        )

        val status = FinancialEngine.computeYtdTaxShieldStatus(
            settings = s,
            currentYear = 2026,
            currentMonth = 6,
            importedTransactions = txs
        )

        assertTrue(status.isCeilingReached)
        assertEquals(0.0, status.remainingHeadroomAnnual, 0.001)
        assertEquals(0.0, status.recommendedMonthlyPace, 0.001)
        assertEquals(48000.0 * 0.15, status.taxRefundSecured15Pct, 0.001) // 7 200 CZK
        assertEquals(48000.0 * 0.23, status.taxRefundSecured23Pct, 0.001) // 11 040 CZK
    }

    @Test
    fun testSettingsBackupRoundTripForArea3Fields() {
        val original = SettingsEntity.freshDefaults().copy(
            guardrailsEnabled = true,
            guardrailsUpperThresholdPct = 30.0,
            guardrailsLowerThresholdPct = 25.0,
            guardrailsCutPct = 15.0,
            guardrailsBoostPct = 12.0,
            guardrailsFloorPct = 60.0,
            baristaTransitionEnabled = true,
            baristaStartYear = 2037,
            baristaMonthlyIncome = 42000.0,
            baristaSavingsMonthly = 5000.0
        )

        val payload = BackupManager.FullBackupPayload(settings = original)
        val json = BackupManager.serializeFullBackupToJson(payload)
        val restored = BackupManager.deserializeFullBackupFromJson(json, fallback = original)?.settings

        assertNotNull(restored)
        assertTrue(restored!!.guardrailsEnabled)
        assertEquals(30.0, restored.guardrailsUpperThresholdPct, 0.001)
        assertEquals(25.0, restored.guardrailsLowerThresholdPct, 0.001)
        assertEquals(15.0, restored.guardrailsCutPct, 0.001)
        assertEquals(12.0, restored.guardrailsBoostPct, 0.001)
        assertEquals(60.0, restored.guardrailsFloorPct, 0.001)
        assertTrue(restored.baristaTransitionEnabled)
        assertEquals(2037, restored.baristaStartYear)
        assertEquals(42000.0, restored.baristaMonthlyIncome, 0.001)
        assertEquals(5000.0, restored.baristaSavingsMonthly, 0.001)
    }
}
