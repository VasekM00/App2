package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.util.BankTransactionType
import com.example.util.CrossStatementReconciliationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossStatementReconciliationDeepTest {

    @Test
    fun test1_merchantShield_groceriesDebitNotNettedAgainstCredit() {
        val debit = createTx(
            id = 1L,
            date = "2026-09-10",
            bank = "CSOB",
            amount = -1500.0,
            counterpartyName = "Billa",
            category = BankTransactionType.GROCERIES.name
        )
        val credit = createTx(
            id = 2L,
            date = "2026-09-11",
            bank = "MONETA",
            amount = 1500.0,
            counterpartyName = "Jan Novak",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        val resDebit = result.currentMonthTxs.first { it.id == 1L }
        val resCredit = result.currentMonthTxs.first { it.id == 2L }
        assertFalse("Groceries debit must not be netted against unrelated credit", resDebit.isNetted)
        assertFalse("Credit must not be netted against groceries merchant debit", resCredit.isNetted)
    }

    @Test
    fun test2_merchantShield_healthDrugstoreNotNetted() {
        val debit = createTx(
            id = 1L,
            date = "2026-09-10",
            bank = "MONETA",
            amount = -850.0,
            counterpartyName = "Dr. Max",
            category = BankTransactionType.HEALTH_DRUGSTORE.name
        )
        val credit = createTx(
            id = 2L,
            date = "2026-09-12",
            bank = "CSOB",
            amount = 850.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertFalse(result.currentMonthTxs.first { it.id == 1L }.isNetted)
        assertFalse(result.currentMonthTxs.first { it.id == 2L }.isNetted)
    }

    @Test
    fun test3_merchantShield_shoppingGoodsNotNetted() {
        val debit = createTx(
            id = 1L,
            date = "2026-09-05",
            bank = "MBANK",
            amount = -3200.0,
            counterpartyName = "Alza.cz",
            category = BankTransactionType.SHOPPING_GOODS.name
        )
        val credit = createTx(
            id = 2L,
            date = "2026-09-06",
            bank = "CSOB",
            amount = 3200.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertFalse(result.currentMonthTxs.first { it.id == 1L }.isNetted)
    }

    @Test
    fun test4_merchantShield_restaurantDiningNotNetted() {
        val debit = createTx(
            id = 1L,
            date = "2026-09-15",
            bank = "CSOB",
            amount = -650.0,
            counterpartyName = "Restaurace U Cerneho Vola",
            category = BankTransactionType.DINING_RESTAURANT.name
        )
        val credit = createTx(
            id = 2L,
            date = "2026-09-16",
            bank = "MONETA",
            amount = 650.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertFalse(result.currentMonthTxs.first { it.id == 1L }.isNetted)
    }

    @Test
    fun test5_merchantShield_salaryCreditNotNettedAgainstDebit() {
        val credit = createTx(
            id = 10L,
            date = "2026-09-12",
            bank = "CSOB",
            amount = 65000.0,
            counterpartyName = "Zamestnavatel a.s.",
            category = BankTransactionType.SALARY_VACLAV.name
        )
        val debit = createTx(
            id = 11L,
            date = "2026-09-12",
            bank = "MONETA",
            amount = -65000.0,
            counterpartyName = "Auto ESA",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(credit, debit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertFalse("Salary credit must not be netted against car purchase debit", result.currentMonthTxs.first { it.id == 10L }.isNetted)
    }

    @Test
    fun test6_merchantShield_parentalBenefitNotNettedAgainstDebit() {
        val credit = createTx(
            id = 20L,
            date = "2026-09-08",
            bank = "CSOB",
            amount = 10000.0,
            counterpartyName = "Urad prace CR",
            category = BankTransactionType.PARENTAL_BENEFIT.name
        )
        val debit = createTx(
            id = 21L,
            date = "2026-09-09",
            bank = "MONETA",
            amount = -10000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(credit, debit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertFalse(result.currentMonthTxs.first { it.id == 20L }.isNetted)
    }

    @Test
    fun test7_clearingWindow_within5Days_matchedAsTransfer() {
        val debit = createTx(
            id = 1L,
            date = "2026-09-01",
            bank = "CSOB",
            amount = -5000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )
        val credit = createTx(
            id = 2L,
            date = "2026-09-06",
            bank = "MONETA",
            amount = 5000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertTrue(result.currentMonthTxs.first { it.id == 1L }.isNetted)
        assertTrue(result.currentMonthTxs.first { it.id == 2L }.isNetted)
    }

    @Test
    fun test8_clearingWindow_beyond5Days_rejected() {
        val debit = createTx(
            id = 1L,
            date = "2026-09-01",
            bank = "CSOB",
            amount = -5000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )
        val credit = createTx(
            id = 2L,
            date = "2026-09-07", // 6 days apart
            bank = "MONETA",
            amount = 5000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertFalse(result.currentMonthTxs.first { it.id == 1L }.isNetted)
        assertFalse(result.currentMonthTxs.first { it.id == 2L }.isNetted)
    }

    @Test
    fun test9_crossBankPairing_matchingAmountsDifferentBanks_netted() {
        val debit = createTx(
            id = 1L,
            date = "2026-09-10",
            bank = "CSOB",
            amount = -12000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )
        val credit = createTx(
            id = 2L,
            date = "2026-09-11",
            bank = "MONETA",
            amount = 12000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertEquals(1, result.auditReport.matchedPairs.size)
        assertTrue(result.currentMonthTxs.all { it.isNetted })
    }

    @Test
    fun test10_sameBankTransfer_onlyMatchesIfSelfFlagged() {
        val debit = createTx(
            id = 1L,
            date = "2026-09-10",
            bank = "CSOB",
            amount = -4000.0,
            counterpartyName = "Jan Novak",
            category = BankTransactionType.UNCATEGORIZED.name
        )
        val credit = createTx(
            id = 2L,
            date = "2026-09-10",
            bank = "CSOB",
            amount = 4000.0,
            counterpartyName = "Petr Svoboda",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertFalse("Unrelated transactions within the same bank must not net", result.currentMonthTxs.first { it.id == 1L }.isNetted)
    }

    @Test
    fun test11_knownFamilyAccounts_explicitMatch_nettedRegardlessOfBank() {
        val familyAcc = "111222333/0800"
        val debit = createTx(
            id = 1L,
            date = "2026-09-10",
            bank = "CSOB",
            amount = -3000.0,
            counterpartyAccount = familyAcc,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit),
            yearMonth = "2026-09",
            knownFamilyAccounts = setOf(familyAcc),
            boundaryTransactions = emptyList()
        )

        assertTrue(result.currentMonthTxs.first().isNetted)
    }

    @Test
    fun test12_crossMonthBoundary_lastDayDebitFirstDayCredit_netted() {
        val augustDebit = createTx(
            id = 100L,
            date = "2026-08-31",
            bank = "CSOB",
            amount = -7000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )
        val septemberCredit = createTx(
            id = 101L,
            date = "2026-09-02",
            bank = "MONETA",
            amount = 7000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(septemberCredit),
            yearMonth = "2026-09",
            boundaryTransactions = listOf(augustDebit)
        )

        assertTrue(result.currentMonthTxs.first { it.id == 101L }.isNetted)
        assertTrue(result.updatedBoundaryTxs.first { it.id == 100L }.isNetted)
    }

    @Test
    fun test13_crossMonthBoundary_beyond5Days_notNetted() {
        val augustDebit = createTx(
            id = 100L,
            date = "2026-08-25",
            bank = "CSOB",
            amount = -7000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )
        val septemberCredit = createTx(
            id = 101L,
            date = "2026-09-02", // 8 days apart
            bank = "MONETA",
            amount = 7000.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(septemberCredit),
            yearMonth = "2026-09",
            boundaryTransactions = listOf(augustDebit)
        )

        assertFalse(result.currentMonthTxs.first { it.id == 101L }.isNetted)
    }

    @Test
    fun test14_deduplication_identicalFingerprint_deduplicated() {
        val tx1 = createTx(
            id = 1L,
            date = "2026-09-15",
            bank = "CSOB",
            amount = -500.0,
            counterpartyName = "Billa",
            category = BankTransactionType.GROCERIES.name
        )
        val tx2 = createTx(
            id = 2L,
            date = "2026-09-15",
            bank = "CSOB",
            amount = -500.0,
            counterpartyName = "Billa",
            category = BankTransactionType.GROCERIES.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(tx1, tx2),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertEquals(1, result.currentMonthTxs.size)
    }

    @Test
    fun test15_circularTransfers_multipleIdenticalAmounts_pairedOneToOne() {
        val debit1 = createTx(id = 1L, date = "2026-09-10", bank = "CSOB", amount = -2000.0)
        val debit2 = createTx(id = 2L, date = "2026-09-10", bank = "CSOB", amount = -2000.0, counterpartyName = "Different Party")
        val credit1 = createTx(id = 3L, date = "2026-09-11", bank = "MONETA", amount = 2000.0)

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit1, debit2, credit1),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        val nettedDebits = result.currentMonthTxs.filter { it.amount < 0 && it.isNetted }
        val unnettedDebits = result.currentMonthTxs.filter { it.amount < 0 && !it.isNetted }
        assertEquals(1, nettedDebits.size)
        assertEquals(1, unnettedDebits.size)
    }

    @Test
    fun test16_housingRent_explicitExclusionFromNetting() {
        val rentDebit = createTx(
            id = 99L,
            date = "2026-09-01",
            bank = "CSOB",
            amount = -18950.0,
            counterpartyName = "Majitel Byty",
            message = "Najemne za zari",
            category = BankTransactionType.HOUSING_RENT.name
        )
        val credit = createTx(
            id = 100L,
            date = "2026-09-02",
            bank = "MONETA",
            amount = 18950.0,
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(rentDebit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertFalse("Rent payment must never be netted out", result.currentMonthTxs.first { it.id == 99L }.isNetted)
    }

    @Test
    fun test17_nettingReason_descriptiveAuditTrail() {
        val debit = createTx(id = 1L, date = "2026-09-05", bank = "CSOB", amount = -3500.0)
        val credit = createTx(id = 2L, date = "2026-09-07", bank = "MONETA", amount = 3500.0)

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(debit, credit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        val reason = result.currentMonthTxs.first { it.id == 1L }.nettingReason
        assertTrue(reason.contains("CSOB") && reason.contains("MONETA") && reason.contains("2 d apart"))
    }

    @Test
    fun test18_auditReport_reconciledTotalsAccuracy() {
        val salary = createTx(id = 1L, date = "2026-09-10", bank = "CSOB", amount = 50000.0, category = BankTransactionType.SALARY_VACLAV.name)
        val grocery = createTx(id = 2L, date = "2026-09-12", bank = "CSOB", amount = -4000.0, category = BankTransactionType.GROCERIES.name)
        val transferDebit = createTx(id = 3L, date = "2026-09-15", bank = "CSOB", amount = -10000.0)
        val transferCredit = createTx(id = 4L, date = "2026-09-16", bank = "MONETA", amount = 10000.0)

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(salary, grocery, transferDebit, transferCredit),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )

        assertEquals(50000.0, result.auditReport.reconciledInflows, 0.001)
        assertEquals(4000.0, result.auditReport.reconciledExpenses, 0.001)
        assertEquals(46000.0, result.auditReport.reconciledNetCashFlow, 0.001)
    }

    @Test
    fun test19_emptyTransactionsList_safeReturns() {
        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = emptyList(),
            yearMonth = "2026-09",
            boundaryTransactions = emptyList()
        )
        assertTrue(result.currentMonthTxs.isEmpty())
        assertTrue(result.updatedBoundaryTxs.isEmpty())
        assertEquals(0.0, result.auditReport.reconciledInflows, 0.001)
        assertEquals(0.0, result.auditReport.reconciledExpenses, 0.001)
    }

    @Test
    fun test20_mbankInternalGoalAndMsporeni_detectedAsInternalTransfer() {
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("Prevod z cile", ""))
        assertTrue(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("", "Prevod na mSporeni"))
        assertFalse(CrossStatementReconciliationEngine.isMbankGoalOrMsporeni("Billa", "Nakup"))
    }

    private fun createTx(
        id: Long,
        date: String,
        bank: String,
        amount: Double,
        counterpartyAccount: String = "",
        counterpartyName: String = "",
        message: String = "",
        category: String = BankTransactionType.UNCATEGORIZED.name
    ): ImportedBankTransactionEntity {
        return ImportedBankTransactionEntity(
            id = id,
            yearMonth = date.take(7),
            date = date,
            bankName = bank,
            amount = amount,
            counterpartyAccount = counterpartyAccount,
            counterpartyName = counterpartyName,
            message = message,
            variableSymbol = "",
            category = category,
            isNetted = false,
            nettingReason = ""
        )
    }
}
