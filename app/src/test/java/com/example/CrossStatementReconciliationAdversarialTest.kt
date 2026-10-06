package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.util.BankTransactionType
import com.example.util.CrossStatementReconciliationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossStatementReconciliationAdversarialTest {

    private fun createTx(
        id: Long = 0L,
        date: String = "2026-09-15",
        bank: String = "CSOB",
        amount: Double = -1000.0,
        counterpartyAccount: String = "",
        counterpartyName: String = "",
        message: String = "",
        vs: String = "",
        category: String = BankTransactionType.UNCATEGORIZED.name
    ): ImportedBankTransactionEntity {
        return ImportedBankTransactionEntity(
            id = id,
            yearMonth = "2026-09",
            date = date,
            bankName = bank,
            amount = amount,
            counterpartyAccount = counterpartyAccount,
            counterpartyName = counterpartyName,
            message = message,
            variableSymbol = vs,
            category = category
        )
    }

    @Test
    fun testTwoIdenticalAmountPairsOnSameDay_bothPairedCleanly() {
        // Two independent transfers on the same day:
        // Transfer 1: 5000 CZK from CSOB to MONETA
        // Transfer 2: 5000 CZK from CSOB to MONETA (or another bank)
        val debit1 = createTx(id = 1L, date = "2026-09-10", bank = "CSOB", amount = -5000.0, counterpartyAccount = "111/0800")
        val debit2 = createTx(id = 2L, date = "2026-09-10", bank = "CSOB", amount = -5000.0, counterpartyAccount = "222/0800")
        val credit1 = createTx(id = 3L, date = "2026-09-10", bank = "MONETA", amount = 5000.0, counterpartyAccount = "333/0300")
        val credit2 = createTx(id = 4L, date = "2026-09-10", bank = "MONETA", amount = 5000.0, counterpartyAccount = "444/0300")

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit1, debit2, credit1, credit2),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertEquals(2, result.auditReport.matchedPairs.size)
        assertTrue(result.currentMonthTxs.all { it.isNetted })
        assertEquals(0, result.auditReport.singleSideNettedTransfers.size)
        assertEquals(20000.0, result.auditReport.totalNettedAmount, 0.001)
    }

    @Test
    fun testSingleSideTransferCoincidingWithMatchedPairAmount_singleSideNotDropped() {
        // 1 matched pair of 2000 CZK: CSOB -> MONETA
        // 1 single-side transfer of 2000 CZK on the SAME day: CSOB -> Revolut (unlinked external account)
        val debitPair = createTx(id = 1L, date = "2026-09-12", bank = "CSOB", amount = -2000.0, counterpartyName = "Vaclav Martinu")
        val creditPair = createTx(id = 2L, date = "2026-09-12", bank = "MONETA", amount = 2000.0, counterpartyName = "Vaclav Martinu")
        val singleDebit = createTx(id = 3L, date = "2026-09-12", bank = "CSOB", amount = -2000.0, message = "Vlastní převod na jiný účet")

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debitPair, creditPair, singleDebit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertEquals(1, result.auditReport.matchedPairs.size)
        assertEquals(1, result.auditReport.singleSideNettedTransfers.size)
        assertEquals(3L, result.auditReport.singleSideNettedTransfers[0].id)
        // Total netted: (2000 * 2) from pair + 2000 from single = 6000
        assertEquals(6000.0, result.auditReport.totalNettedAmount, 0.001)
    }

    @Test
    fun testCrossMonthBoundaryClearingWithinTolerance_matchedAndRecorded() {
        // Debit on 30.09 in CSOB, Credit on 02.10 (2 days later) in MONETA
        val debitMonthEnd = createTx(id = 10L, date = "2026-09-30", bank = "CSOB", amount = -15000.0, counterpartyName = "Vaclav Martinu")
        val creditBoundary = createTx(id = 11L, date = "2026-10-02", bank = "MONETA", amount = 15000.0, counterpartyName = "Vaclav Martinu")

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debitMonthEnd),
            yearMonth = "2026-09",
            boundaryTransactions = listOf(creditBoundary)
        )

        assertEquals(1, result.auditReport.matchedPairs.size)
        val pair = result.auditReport.matchedPairs[0]
        assertEquals(2L, pair.daysApart)
        assertEquals(15000.0, pair.amount, 0.001)
        assertTrue(result.currentMonthTxs[0].isNetted)
        assertTrue(result.updatedBoundaryTxs[0].isNetted)
    }

    @Test
    fun testExternalExpenseNotNettedUnlessSelfFlagged() {
        val groceryExpense = createTx(id = 1L, date = "2026-09-05", bank = "CSOB", amount = -1200.0, category = BankTransactionType.GROCERIES.name)
        val unknownCredit = createTx(id = 2L, date = "2026-09-06", bank = "MONETA", amount = 1200.0, category = BankTransactionType.UNCATEGORIZED.name)

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(groceryExpense, unknownCredit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        // Neither is flagged as self/family transfer, so the grocery expense must NOT be netted
        assertEquals(0, result.auditReport.matchedPairs.size)
        assertEquals(0, result.auditReport.singleSideNettedTransfers.size)
        assertEquals(1200.0, result.auditReport.reconciledExpenses, 0.001)
    }
}
