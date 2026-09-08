package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.util.BankTransactionType
import com.example.util.CrossStatementReconciliationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossStatementReconciliationEngineTest {

    @Test
    fun testSelfOrFamilyTransferKeywordDetection() {
        assertTrue(CrossStatementReconciliationEngine.isSelfOrFamilyTransfer("MARTINU VACLAV", "", "Platba"))
        assertTrue(CrossStatementReconciliationEngine.isSelfOrFamilyTransfer("Eleonora Martinu", "", "Prevod"))
        assertTrue(CrossStatementReconciliationEngine.isSelfOrFamilyTransfer("", "", "Sent from Revolut Vaclav Martinu"))
        assertTrue(CrossStatementReconciliationEngine.isSelfOrFamilyTransfer("Neznamy", "", "vlastni prevod na sporici ucet"))
        assertTrue(CrossStatementReconciliationEngine.isSelfOrFamilyTransfer("Neznamy", "123456789/0800", "Platba", setOf("123456789/0800")))

        // Legitimate third-party transactions should NOT be flagged as self-transfers
        assertFalse(CrossStatementReconciliationEngine.isSelfOrFamilyTransfer("Albert Ceska republika s.r.o.", "", "Nakup potravin"))
        assertFalse(CrossStatementReconciliationEngine.isSelfOrFamilyTransfer("Pronajimatel Novak", "", "Najem byt Praha"))
        assertFalse(CrossStatementReconciliationEngine.isSelfOrFamilyTransfer("Zamestnavatel a.s.", "", "Mzda za 07/2026"))
    }

    @Test
    fun testFamilyGiftContributionDetection() {
        assertTrue(CrossStatementReconciliationEngine.isFamilyGiftContribution("Andrea Martinu", "Dar"))
        assertTrue(CrossStatementReconciliationEngine.isFamilyGiftContribution("Petr Martinu", "Prispevek na bydleni"))
        assertTrue(CrossStatementReconciliationEngine.isFamilyGiftContribution("Babicka", "dar od rodiny"))

        assertFalse(CrossStatementReconciliationEngine.isFamilyGiftContribution("Zamestnavatel", "Bonus"))
    }

    @Test
    fun testPairwiseMatchingStrict5DayWindowExact() {
        // Debit on Moneta on 2026-07-10 of -15 000 CZK
        val debitTx = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-07",
            bankName = "Moneta",
            date = "2026-07-10",
            amount = -15000.0,
            counterpartyAccount = "111222333/0300",
            counterpartyName = "Vaclav Martinu",
            message = "Prevod na sporeni CSOB",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        // Credit on CSOB on 2026-07-15 of +15 000 CZK (exactly 5 days apart)
        val creditTx = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-07",
            bankName = "CSOB",
            date = "2026-07-15",
            amount = 15000.0,
            counterpartyAccount = "999888777/0600",
            counterpartyName = "Vaclav Martinu",
            message = "Vklad z Moneta",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val (reconciled, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(debitTx, creditTx),
            yearMonth = "2026-07"
        )

        assertEquals(1, auditReport.matchedPairs.size)
        val pair = auditReport.matchedPairs.first()
        assertEquals(5L, pair.daysApart)
        assertEquals(15000.0, pair.amount, 0.01)

        // Both transactions must be marked as netted internal transfers
        assertTrue(reconciled.all { it.isNetted })
        assertTrue(reconciled.all { it.category == BankTransactionType.INTERNAL_TRANSFER.name })
        assertEquals(0.0, auditReport.reconciledInflows, 0.01)
        assertEquals(0.0, auditReport.reconciledExpenses, 0.01)
        assertEquals(0.0, auditReport.reconciledNetCashFlow, 0.01)
    }

    @Test
    fun testPairwiseMatchingExceeding5DayWindowFailsPairing() {
        // Debit on Moneta on 2026-07-10
        val debitTx = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-07",
            bankName = "Moneta",
            date = "2026-07-10",
            amount = -10000.0,
            counterpartyAccount = "111/0300",
            counterpartyName = "Inzenyr",
            message = "Platba za sluzby",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        // Credit on CSOB on 2026-07-16 (6 days apart -> exceeds CLEARING_WINDOW_DAYS of 5)
        val creditTx = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-07",
            bankName = "CSOB",
            date = "2026-07-16",
            amount = 10000.0,
            counterpartyAccount = "222/0100",
            counterpartyName = "Klient",
            message = "Platba faktury",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val (reconciled, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(debitTx, creditTx),
            yearMonth = "2026-07"
        )

        // Must NOT match as a pairwise transfer
        assertEquals(0, auditReport.matchedPairs.size)
        // Since neither has self-transfer names, neither should be netted
        assertFalse(reconciled.any { it.isNetted })
        assertEquals(10000.0, auditReport.reconciledInflows, 0.01)
        assertEquals(10000.0, auditReport.reconciledExpenses, 0.01)
    }

    @Test
    fun testSingleSideSelfTransferNetted() {
        // Only Moneta statement imported: user received 21 998.62 CZK from Revolut
        val revolutDeposit = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-07",
            bankName = "Moneta",
            date = "2026-07-31",
            amount = 21998.62,
            counterpartyAccount = "Revolut",
            counterpartyName = "Revolut Ltd",
            message = "Sent from Revolut ... Vaclav Martinu",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val salaryTx = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-07",
            bankName = "Moneta",
            date = "2026-07-15",
            amount = 75000.0,
            counterpartyAccount = "12345/0100",
            counterpartyName = "Zamestnavatel",
            message = "Mzda",
            category = BankTransactionType.SALARY_VACLAV.name
        )

        val (reconciled, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(revolutDeposit, salaryTx),
            yearMonth = "2026-07"
        )

        // Revolut deposit is netted as single-side self-transfer
        val reconciledRevolut = reconciled.find { it.id == 1L }!!
        assertTrue(reconciledRevolut.isNetted)
        assertEquals(BankTransactionType.INTERNAL_TRANSFER.name, reconciledRevolut.category)
        assertEquals("Own Revolut account transfer", reconciledRevolut.nettingReason)

        // Inflows should ONLY reflect true salary (75 000 CZK), NOT 75 000 + 21 998.62
        assertEquals(75000.0, auditReport.reconciledInflows, 0.01)
        assertEquals(21998.62, auditReport.totalNettedAmount, 0.01)
    }

    @Test
    fun testMultiBankReconciliationPreservesLegitimateSalariesAndExpenses() {
        val monetaSalary = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-08",
            bankName = "Moneta",
            date = "2026-08-15",
            amount = 75000.0,
            counterpartyAccount = "123/0100",
            counterpartyName = "Employer A",
            message = "Mzda Vaclav",
            category = BankTransactionType.SALARY_VACLAV.name
        )
        val csobSalary = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-08",
            bankName = "CSOB",
            date = "2026-08-14",
            amount = 45000.0,
            counterpartyAccount = "456/0300",
            counterpartyName = "Employer B",
            message = "Mzda Eleonora",
            category = BankTransactionType.SALARY_ELEONORA.name
        )
        val monetaRent = ImportedBankTransactionEntity(
            id = 3L,
            yearMonth = "2026-08",
            bankName = "Moneta",
            date = "2026-08-18",
            amount = -25000.0,
            counterpartyAccount = "789/0800",
            counterpartyName = "Landlord",
            message = "Najem",
            category = BankTransactionType.HOUSING_RENT.name
        )
        val csobGroceries = ImportedBankTransactionEntity(
            id = 4L,
            yearMonth = "2026-08",
            bankName = "CSOB",
            date = "2026-08-20",
            amount = -5000.0,
            counterpartyAccount = "999/0100",
            counterpartyName = "Albert",
            message = "Potraviny",
            category = BankTransactionType.GROCERIES.name
        )

        // Internal transfer: Vaclav sends 12 000 CZK from Moneta to CSOB on 2026-08-16, arrives on 2026-08-17
        val transferDebit = ImportedBankTransactionEntity(
            id = 5L,
            yearMonth = "2026-08",
            bankName = "Moneta",
            date = "2026-08-16",
            amount = -12000.0,
            counterpartyAccount = "CSOB-Acc",
            counterpartyName = "Eleonora Martinu",
            message = "Prevod na domacnost",
            category = BankTransactionType.UNCATEGORIZED.name
        )
        val transferCredit = ImportedBankTransactionEntity(
            id = 6L,
            yearMonth = "2026-08",
            bankName = "CSOB",
            date = "2026-08-17",
            amount = 12000.0,
            counterpartyAccount = "Moneta-Acc",
            counterpartyName = "Vaclav Martinu",
            message = "Prevod od Vaclava",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val transactions = listOf(monetaSalary, csobSalary, monetaRent, csobGroceries, transferDebit, transferCredit)
        val (reconciled, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(transactions, "2026-08")

        // 1. Matched transfer pair found
        assertEquals(1, auditReport.matchedPairs.size)
        assertEquals(12000.0, auditReport.matchedPairs[0].amount, 0.01)
        assertEquals(1L, auditReport.matchedPairs[0].daysApart)

        // 2. Inflows are clean (75k + 45k = 120k, NOT 132k)
        assertEquals(120000.0, auditReport.reconciledInflows, 0.01)

        // 3. Expenses are clean (25k + 5k = 30k, NOT 42k)
        assertEquals(30000.0, auditReport.reconciledExpenses, 0.01)

        // 4. Net cash flow is 90 000 CZK
        assertEquals(90000.0, auditReport.reconciledNetCashFlow, 0.01)

        // 5. Participating banks
        assertTrue(auditReport.participatingBanks.contains("Moneta"))
        assertTrue(auditReport.participatingBanks.contains("CSOB"))
    }

    @Test
    fun testRealMonetaPdfSampleStatementSelfTransfers() {
        // Moneta PDF sample statement transaction: 2026-07-20 QR payment to MARTINU VACLAV
        val qrSelfDebit = ImportedBankTransactionEntity(
            id = 10L,
            yearMonth = "2026-07",
            bankName = "MONETA",
            date = "2026-07-20",
            amount = -18946.0,
            counterpartyAccount = "240123456/2010",
            counterpartyName = "MARTINU VACLAV",
            message = "QR Platba",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        // Moneta PDF sample statement transaction: 2026-07-31 Sent from Revolut
        val revolutCredit = ImportedBankTransactionEntity(
            id = 11L,
            yearMonth = "2026-07",
            bankName = "MONETA",
            date = "2026-07-31",
            amount = 21998.62,
            counterpartyAccount = "Revolut",
            counterpartyName = "Revolut Bank UAB",
            message = "Sent from Revolut ... Vaclav Martinu",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val (reconciled, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(qrSelfDebit, revolutCredit),
            yearMonth = "2026-07"
        )

        // Both are netted
        assertTrue(reconciled[0].isNetted)
        assertTrue(reconciled[1].isNetted)
        assertEquals(0.0, auditReport.reconciledInflows, 0.01)
        assertEquals(0.0, auditReport.reconciledExpenses, 0.01)
        assertEquals(40944.62, auditReport.totalNettedAmount, 0.01)
    }

    @Test
    fun testMbankInternalGoalsAndMsporeniDetection() {
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("mBank", "Prevod z cile"))
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("mBank", "Převod z cíle: Dovolená"))
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("mBank", "Prevod na msporeni"))
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("mBank", "Převod na mSpoření"))
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("mBank", "mSpoření"))
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("mBank", "Převod na cíl"))
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("mBank", "Převod z mSpoření"))
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("mBank", "Cíl - Nové auto, převod"))

        // Also test reconcileTransactions assigns correct reason
        val msporeniTx = ImportedBankTransactionEntity(
            id = 20L,
            yearMonth = "2026-08",
            bankName = "mBank",
            date = "2026-08-05",
            amount = -500.0,
            counterpartyAccount = "",
            counterpartyName = "mBank",
            message = "Převod na mSpoření",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val cilTx = ImportedBankTransactionEntity(
            id = 21L,
            yearMonth = "2026-08",
            bankName = "mBank",
            date = "2026-08-10",
            amount = 2500.0,
            counterpartyAccount = "",
            counterpartyName = "mBank",
            message = "Převod z cíle: Rezerva",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val (reconciled, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(msporeniTx, cilTx),
            yearMonth = "2026-08"
        )

        assertTrue(reconciled[0].isNetted)
        assertEquals(BankTransactionType.INTERNAL_TRANSFER.name, reconciled[0].category)
        assertEquals("mBank internal transfer (mSpoření / Cíl)", reconciled[0].nettingReason)

        assertTrue(reconciled[1].isNetted)
        assertEquals(BankTransactionType.INTERNAL_TRANSFER.name, reconciled[1].category)
        assertEquals("mBank internal transfer (mSpoření / Cíl)", reconciled[1].nettingReason)

        assertEquals(0.0, auditReport.reconciledInflows, 0.01)
        assertEquals(0.0, auditReport.reconciledExpenses, 0.01)
        assertEquals(0.0, auditReport.reconciledNetCashFlow, 0.01)
    }
}
