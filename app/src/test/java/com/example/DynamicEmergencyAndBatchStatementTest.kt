package com.example

import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.ParsedBankTransaction
import com.example.util.StatementParseSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicEmergencyAndBatchStatementTest {

    @Test
    fun testDynamicEmergencyReserve_allModes() {
        val baseSettings = SettingsEntity(
            rentMonthly = 30000.0,
            groceriesMonthly = 15000.0,
            emergencyReserveTarget = 300000.0
        )
        val monthlyCost = FinancialEngine.totalLivingCostMonthly(baseSettings)

        fun roundTo1k(amount: Double) = (kotlin.math.round(amount / 1_000.0) * 1_000.0).coerceAtLeast(1_000.0)

        // 3M
        val settings3M = baseSettings.copy(emergencyReserveMode = "3M")
        val target3M = FinancialEngine.effectiveEmergencyReserveTarget(settings3M)
        assertEquals(roundTo1k(monthlyCost * 3.0), target3M, 0.01)

        // 6M
        val settings6M = baseSettings.copy(emergencyReserveMode = "6M")
        val target6M = FinancialEngine.effectiveEmergencyReserveTarget(settings6M)
        assertEquals(roundTo1k(monthlyCost * 6.0), target6M, 0.01)

        // 9M
        val settings9M = baseSettings.copy(emergencyReserveMode = "9M")
        val target9M = FinancialEngine.effectiveEmergencyReserveTarget(settings9M)
        assertEquals(roundTo1k(monthlyCost * 9.0), target9M, 0.01)

        // 12M
        val settings12M = baseSettings.copy(emergencyReserveMode = "12M")
        val target12M = FinancialEngine.effectiveEmergencyReserveTarget(settings12M)
        assertEquals(roundTo1k(monthlyCost * 12.0), target12M, 0.01)

        // Manual Target
        val settingsTarget = baseSettings.copy(emergencyReserveMode = "Target", emergencyReserveTarget = 250000.0)
        val targetManual = FinancialEngine.effectiveEmergencyReserveTarget(settingsTarget)
        assertEquals(250000.0, targetManual, 0.01)

        // Fallback for unknown mode
        val settingsUnknown = baseSettings.copy(emergencyReserveMode = "UnknownCustom", emergencyReserveTarget = 400000.0)
        val targetUnknown = FinancialEngine.effectiveEmergencyReserveTarget(settingsUnknown)
        assertEquals(400000.0, targetUnknown, 0.01)
    }

    @Test
    fun testDynamicEmergencyReserve_fullCalculationStateIntegration() {
        val settings = SettingsEntity(
            rentMonthly = 20000.0,
            groceriesMonthly = 10000.0,
            emergencyReserveTarget = 150000.0,
            emergencyReserveCurrent = 300000.0,
            emergencyReserveMode = "6M"
        )
        val calc = FinancialEngine.calculate(settings, runMonteCarlo = false)
        val expectedTarget = FinancialEngine.effectiveEmergencyReserveTarget(settings)

        assertEquals(expectedTarget, calc.effectiveEmergencyReserveTargetToday, 0.01)
        assertTrue(calc.isEmergencyReserveFundedToday)
        assertEquals(300000.0 - expectedTarget, calc.emergencyReserveSurplusOrDeficitToday, 0.01)
        assertTrue(calc.actionsImpacts.containsKey("ac9"))
    }

    @Test
    fun testBatchMultiStatement_mergeSummariesEmptyAndSingle() {
        val emptyMerged = BankStatementImporter.mergeSummaries(emptyList())
        assertEquals(BankType.GENERIC, emptyMerged.detectedBank)
        assertTrue(emptyMerged.transactions.isEmpty())

        val tx1 = ParsedBankTransaction(
            date = "2026-09-01",
            amount = -1500.0,
            counterpartyAccount = "12345/0300",
            counterpartyName = "Albert",
            message = "Groceries",
            variableSymbol = "",
            category = BankTransactionType.GROCERIES
        )
        val summary1 = StatementParseSummary(
            detectedBank = BankType.CSOB,
            yearMonth = "2026-09",
            totalInflows = 0.0,
            incVaclav = 0.0,
            incEleonora = 0.0,
            incOther = 0.0,
            totalExpenses = 1500.0,
            expRent = 0.0,
            expGroceries = 1500.0,
            expOther = 0.0,
            totalInvested = 0.0,
            invPortu = 0.0,
            invDip = 0.0,
            invDps = 0.0,
            internalTransfersCount = 0,
            monthEndBalance = 45000.0,
            transactions = listOf(tx1),
            isPdfSource = true
        )

        val singleMerged = BankStatementImporter.mergeSummaries(listOf(summary1))
        assertEquals(BankType.CSOB, singleMerged.detectedBank)
        assertEquals(1, singleMerged.transactions.size)
        assertTrue(singleMerged.isPdfSource)
    }

    @Test
    fun testBatchMultiStatement_mergeSummariesMultipleBanksAndDeduplication() {
        val tx1 = ParsedBankTransaction(
            date = "2026-09-05",
            amount = -1200.0,
            counterpartyAccount = "111/0800",
            counterpartyName = "Billa",
            message = "Shopping",
            variableSymbol = "123",
            category = BankTransactionType.GROCERIES
        )
        val tx2 = ParsedBankTransaction(
            date = "2026-09-10",
            amount = -25000.0,
            counterpartyAccount = "222/0300",
            counterpartyName = "Landlord",
            message = "Rent",
            variableSymbol = "456",
            category = BankTransactionType.HOUSING_RENT
        )
        // Duplicate of tx1 in a second file
        val txDuplicate = ParsedBankTransaction(
            date = "2026-09-05",
            amount = -1200.0,
            counterpartyAccount = "111/0800",
            counterpartyName = "Billa",
            message = "Shopping",
            variableSymbol = "123",
            category = BankTransactionType.GROCERIES
        )
        val tx3 = ParsedBankTransaction(
            date = "2026-09-15",
            amount = 90000.0,
            counterpartyAccount = "333/2010",
            counterpartyName = "Employer",
            message = "Salary",
            variableSymbol = "",
            category = BankTransactionType.SALARY_VACLAV
        )

        val summaryBankA = StatementParseSummary(
            detectedBank = BankType.CSOB,
            yearMonth = "2026-09",
            totalInflows = 0.0,
            incVaclav = 0.0,
            incEleonora = 0.0,
            incOther = 0.0,
            totalExpenses = 26200.0,
            expRent = 25000.0,
            expGroceries = 1200.0,
            expOther = 0.0,
            totalInvested = 0.0,
            invPortu = 0.0,
            invDip = 0.0,
            invDps = 0.0,
            internalTransfersCount = 0,
            monthEndBalance = 30000.0,
            transactions = listOf(tx1, tx2),
            isPdfSource = true
        )

        val summaryBankB = StatementParseSummary(
            detectedBank = BankType.MBANK,
            yearMonth = "2026-09",
            totalInflows = 90000.0,
            incVaclav = 90000.0,
            incEleonora = 0.0,
            incOther = 0.0,
            totalExpenses = 1200.0,
            expRent = 0.0,
            expGroceries = 1200.0,
            expOther = 0.0,
            totalInvested = 0.0,
            invPortu = 0.0,
            invDip = 0.0,
            invDps = 0.0,
            internalTransfersCount = 0,
            monthEndBalance = 110000.0,
            transactions = listOf(txDuplicate, tx3),
            isPdfSource = false
        )

        val merged = BankStatementImporter.mergeSummaries(listOf(summaryBankA, summaryBankB))

        // Multi-bank batch detectedBank becomes GENERIC
        assertEquals(BankType.GENERIC, merged.detectedBank)
        // txDuplicate is deduplicated; should have exactly 3 unique transactions
        assertEquals(3, merged.transactions.size)
        // PDF source flag is preserved if any input is PDF
        assertTrue(merged.isPdfSource)
        // Correctly rolled up inflows and expenses
        assertEquals(90000.0, merged.totalInflows, 0.01)
        assertEquals(26200.0, merged.totalExpenses, 0.01)
        assertEquals(25000.0, merged.expRent, 0.01)
        assertEquals(1200.0, merged.expGroceries, 0.01)
    }

    @Test
    fun testStatementSizeLimits() {
        assertFalse(BankStatementImporter.isWithinSizeLimit(0))
        assertFalse(BankStatementImporter.isWithinSizeLimit(-1))
        assertTrue(BankStatementImporter.isWithinSizeLimit(100))
        assertTrue(BankStatementImporter.isWithinSizeLimit(BankStatementImporter.MAX_STATEMENT_BYTES))
        assertFalse(BankStatementImporter.isWithinSizeLimit(BankStatementImporter.MAX_STATEMENT_BYTES + 1))
    }
}
