package com.example.audit2

import com.example.data.ImportedBankTransactionEntity
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.ParsedBankTransaction
import com.example.util.StatementParseSummary
import com.example.util.SubscriptionAuditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class BankingAudit2Test {

    // =========================================================================
    // 1. BANK STATEMENT PARSING & SYNTHETIC CSV IMPORT
    // =========================================================================

    @Test
    fun ok_universalCsv_czechHeaderAndAmountParsing() {
        val csv = """
            Datum;Částka;Měna;Protiúčet;Název protiúčtu;Zpráva;VS
            15.01.2026;-1 250,50;CZK;123456789/0800;LIDL BRNO;Nákup kartou;
            16.01.2026;85 000,00;CZK;987654321/0300;ACME CORP;Mzda 01/2026;1234
            17.01.2026;-21 770,00;CZK;111222333/0100;Pronajímatel;Nájemné leden 2026;
        """.trimIndent()

        val bytes = csv.toByteArray(StandardCharsets.UTF_8)
        val summary = BankStatementImporter.parseStatement(bytes)
        assertEquals(3, summary.transactions.size)
        assertEquals(85000.0, summary.totalInflows, 0.01)
        assertEquals(23020.50, summary.totalExpenses, 0.01)

        val t0 = summary.transactions[0]
        assertEquals("2026-01-15", t0.date)
        assertEquals(-1250.50, t0.amount, 0.01)
        assertEquals("LIDL BRNO", t0.counterpartyName)

        val t1 = summary.transactions[1]
        assertEquals("2026-01-16", t1.date)
        assertEquals(85000.0, t1.amount, 0.01)
        assertEquals("ACME CORP", t1.counterpartyName)

        val t2 = summary.transactions[2]
        assertEquals("2026-01-17", t2.date)
        assertEquals(-21770.0, t2.amount, 0.01)
    }

    @Test
    fun ok_universalCsv_commaSeparatedAndQuotedFields() {
        val csv = """
            "Datum","Částka","Název příjemce","Zpráva"
            "2026-02-01","-450.00","BILLA, spol. s r.o.","Potraviny nákup"
            "2026-02-02","-120.00","DPMB, a.s.","Jízdenka"
        """.trimIndent()

        val bytes = csv.toByteArray(StandardCharsets.UTF_8)
        val summary = BankStatementImporter.parseStatement(bytes)
        assertEquals(2, summary.transactions.size)
        assertEquals(-450.0, summary.transactions[0].amount, 0.01)
        assertTrue(summary.transactions[0].counterpartyName.contains("BILLA"))
    }

    @Test
    fun ok_mergeSummaries_preservesLegitimateSameDayDuplicates() {
        val tx1 = ParsedBankTransaction(
            date = "2026-01-20",
            amount = -65.0,
            counterpartyName = "Kavárna Brno",
            category = BankTransactionType.DINING_RESTAURANT,
            bankType = BankType.MONETA
        )
        val tx2 = ParsedBankTransaction(
            date = "2026-01-20",
            amount = -65.0,
            counterpartyName = "Kavárna Brno",
            category = BankTransactionType.DINING_RESTAURANT,
            bankType = BankType.MONETA
        )

        val summary = StatementParseSummary(
            detectedBank = BankType.MONETA,
            yearMonth = "2026-01",
            totalInflows = 0.0,
            incVaclav = 0.0,
            incEleonora = 0.0,
            incOther = 0.0,
            totalExpenses = 130.0,
            expRent = 0.0,
            expGroceries = 0.0,
            expOther = 130.0,
            totalInvested = 0.0,
            invPortu = 0.0,
            invDip = 0.0,
            invDps = 0.0,
            internalTransfersCount = 0,
            transactions = listOf(tx1, tx2)
        )

        val secondSummary = StatementParseSummary(
            detectedBank = BankType.CSOB,
            yearMonth = "2026-01",
            totalInflows = 0.0,
            incVaclav = 0.0,
            incEleonora = 0.0,
            incOther = 0.0,
            totalExpenses = 50.0,
            expRent = 0.0,
            expGroceries = 0.0,
            expOther = 50.0,
            totalInvested = 0.0,
            invPortu = 0.0,
            invDip = 0.0,
            invDps = 0.0,
            internalTransfersCount = 0,
            transactions = listOf(
                ParsedBankTransaction(
                    date = "2026-01-21",
                    amount = -50.0,
                    counterpartyName = "Pekárna",
                    category = BankTransactionType.GROCERIES,
                    bankType = BankType.CSOB
                )
            )
        )

        val merged = BankStatementImporter.mergeSummaries(listOf(summary, secondSummary))
        assertEquals("Both same-day transactions from first statement plus second statement tx must be preserved", 3, merged.transactions.size)
        assertEquals(180.0, merged.totalExpenses, 0.01)
    }

    @Test
    fun ok_mergeSummaries_stampsBankTypeWhenConsolidating() {
        val txMoneta = ParsedBankTransaction(
            date = "2026-01-10",
            amount = -500.0,
            counterpartyName = "Obchod",
            bankType = BankType.GENERIC
        )
        val summaryMoneta = StatementParseSummary(
            detectedBank = BankType.MONETA,
            yearMonth = "2026-01",
            totalInflows = 0.0,
            incVaclav = 0.0,
            incEleonora = 0.0,
            incOther = 0.0,
            totalExpenses = 500.0,
            expRent = 0.0,
            expGroceries = 0.0,
            expOther = 500.0,
            totalInvested = 0.0,
            invPortu = 0.0,
            invDip = 0.0,
            invDps = 0.0,
            internalTransfersCount = 0,
            transactions = listOf(txMoneta)
        )

        val txCsob = ParsedBankTransaction(
            date = "2026-01-12",
            amount = 1000.0,
            counterpartyName = "Klient",
            bankType = BankType.GENERIC
        )
        val summaryCsob = StatementParseSummary(
            detectedBank = BankType.CSOB,
            yearMonth = "2026-01",
            totalInflows = 1000.0,
            incVaclav = 1000.0,
            incEleonora = 0.0,
            incOther = 0.0,
            totalExpenses = 0.0,
            expRent = 0.0,
            expGroceries = 0.0,
            expOther = 0.0,
            totalInvested = 0.0,
            invPortu = 0.0,
            invDip = 0.0,
            invDps = 0.0,
            internalTransfersCount = 0,
            transactions = listOf(txCsob)
        )

        val consolidated = BankStatementImporter.mergeSummaries(listOf(summaryMoneta, summaryCsob))
        assertEquals(2, consolidated.transactions.size)
        val monetaTx = consolidated.transactions.first { it.amount < 0 }
        val csobTx = consolidated.transactions.first { it.amount > 0 }
        assertEquals(BankType.MONETA, monetaTx.bankType)
        assertEquals(BankType.CSOB, csobTx.bankType)
    }

    // =========================================================================
    // 2. SUBSCRIPTION AUDITOR & FALSE POSITIVE PREVENTION
    // =========================================================================

    @Test
    fun ok_subscriptionAuditor_detectsRecurringMonthlySubscriptions() {
        val txs = listOf(
            ImportedBankTransactionEntity(
                yearMonth = "2026-01",
                bankName = "MONETA",
                date = "2026-01-05",
                amount = -249.0,
                counterpartyName = "Spotify AB",
                message = "Spotify Premium",
                category = "HOUSING_SERVICES"
            ),
            ImportedBankTransactionEntity(
                yearMonth = "2026-02",
                bankName = "MONETA",
                date = "2026-02-05",
                amount = -249.0,
                counterpartyName = "Spotify AB",
                message = "Spotify Premium",
                category = "HOUSING_SERVICES"
            ),
            ImportedBankTransactionEntity(
                yearMonth = "2026-03",
                bankName = "MONETA",
                date = "2026-03-05",
                amount = -249.0,
                counterpartyName = "Spotify AB",
                message = "Spotify Premium",
                category = "HOUSING_SERVICES"
            )
        )

        val summary = SubscriptionAuditor.auditSubscriptions(txs, swrPct = 4.0)
        assertEquals(1, summary.items.size)
        val item = summary.items[0]
        assertEquals(249.0, item.latestMonthlyAmount, 0.01)
        assertEquals(3, item.transactionCount)
    }

    @Test
    fun ok_subscriptionAuditor_rejectsOneOffTransfersWithPrevodKeyword() {
        val txs = listOf(
            ImportedBankTransactionEntity(
                yearMonth = "2026-01",
                bankName = "MONETA",
                date = "2026-01-15",
                amount = -5000.0,
                counterpartyName = "Jan Novák",
                message = "Vnitrobankovní převod mezi účty",
                category = "GENERAL_EXPENSE"
            )
        )

        val summary = SubscriptionAuditor.auditSubscriptions(txs, swrPct = 4.0)
        assertTrue("One-off transfer with 'převod' must not be flagged as subscription", summary.items.isEmpty())
    }

    // =========================================================================
    // 3. CROSS-STATEMENT RECONCILIATION & INTERNAL TRANSFERS
    // =========================================================================

    @Test
    fun ok_crossStatementReconciliation_pairsMatchingInternalTransfers() {
        val debit = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-01",
            bankName = "MONETA",
            date = "2026-01-10",
            amount = -15000.0,
            counterpartyAccount = "123456789/0300",
            counterpartyName = "Václav Martinů",
            message = "Převod na spořicí účet",
            category = "INTERNAL_TRANSFER"
        )
        val credit = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-01",
            bankName = "CSOB",
            date = "2026-01-11", // 1 day difference
            amount = 15000.0,
            counterpartyAccount = "987654321/0600",
            counterpartyName = "Václav Martinů",
            message = "Příchozí úhrada",
            category = "INTERNAL_TRANSFER"
        )

        val (reconciledTxs, report) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(debit, credit),
            yearMonth = "2026-01"
        )

        val netted = reconciledTxs.filter { it.isNetted }
        assertEquals("Both legs of the internal transfer should be netted", 2, netted.size)
        assertTrue(netted.any { it.id == 1L })
        assertTrue(netted.any { it.id == 2L })
    }
}
