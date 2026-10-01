package com.example.ui

import androidx.lifecycle.SavedStateHandle
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.ParsedBankTransaction
import com.example.util.StatementParseSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedStateHandleTest {

    @Test
    fun testStatementParseSummarySerializationInSavedStateHandle() {
        val tx = ParsedBankTransaction(
            date = "2026-09-15",
            amount = -1500.0,
            counterpartyName = "Lidl Praha",
            category = BankTransactionType.GROCERIES,
            isNetted = false
        )

        val summary = StatementParseSummary(
            detectedBank = BankType.MONETA,
            yearMonth = "2026-09",
            totalInflows = 50000.0,
            incVaclav = 50000.0,
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
            transactions = listOf(tx),
            isPdfSource = true
        )

        val handle = SavedStateHandle()
        val key = "pending_statement_import"

        // Save
        handle[key] = summary

        // Retrieve
        val restored = handle.get<StatementParseSummary>(key)
        assertNotNull(restored)
        assertEquals(BankType.MONETA, restored!!.detectedBank)
        assertEquals("2026-09", restored.yearMonth)
        assertEquals(50000.0, restored.totalInflows, 0.001)
        assertEquals(1500.0, restored.totalExpenses, 0.001)
        assertTrue(restored.isPdfSource)
        assertEquals(1, restored.transactions.size)
        assertEquals("Lidl Praha", restored.transactions[0].counterpartyName)
        assertEquals(BankTransactionType.GROCERIES, restored.transactions[0].category)

        // Remove
        handle.remove<StatementParseSummary>(key)
        val removed = handle.get<StatementParseSummary>(key)
        assertEquals(null, removed)
    }
}
