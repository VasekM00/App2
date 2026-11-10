package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.CzechMerchantCatalog
import com.example.util.SubscriptionAuditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankStatementAuditAndReconciliationExpertTest {

    @Test
    fun testCzechIbanExtractionAndNormalization() {
        val standardIban = "CZ6508000000001234567890"
        val extracted1 = BankStatementImporter.extractAccount("Platba na účet $standardIban majitel Novák")
        assertEquals("1234567890/0800", extracted1)

        val spacedIban = "CZ65 0800 0000 0012 3456 7890"
        val extracted2 = BankStatementImporter.extractAccount("Protiúčet: $spacedIban")
        assertEquals("1234567890/0800", extracted2)

        val prefixIban = "CZ0808000000190012345678" // prefix 19, account 12345678, bank 0800
        val extracted3 = BankStatementImporter.extractAccount(prefixIban)
        assertEquals("19-12345678/0800", extracted3)
    }

    @Test
    fun testSeparateDebitCreditColumnPrecedenceInUniversalCsv() {
        val csv = """
            Datum,Protiúčet,Popis,Na vrub (debet),Ve prospěch (kredit)
            10.10.2026,111/0800,Nákup potravin,1 500.00,
            12.10.2026,222/0300,Mzda zaměstnavatel,,65 000.00
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csv.byteInputStream(Charsets.UTF_8))
        assertEquals(2, summary.transactions.size)

        val debitTx = summary.transactions.first { it.date == "2026-10-10" }
        assertEquals(-1500.0, debitTx.amount, 0.01)

        val creditTx = summary.transactions.first { it.date == "2026-10-12" }
        assertEquals(65000.0, creditTx.amount, 0.01)
    }

    @Test
    fun testDirectionDkParsingInUniversalCsv() {
        val csv = """
            Datum;Číslo účtu;Název;Částka;D/K
            05.10.2026;123/0100;Restaurace;850,00;D
            08.10.2026;456/0800;Klient;12 000,00;K
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csv.byteInputStream(Charsets.UTF_8))
        assertEquals(2, summary.transactions.size)

        val txOutflow = summary.transactions.first { it.date == "2026-10-05" }
        assertEquals(-850.0, txOutflow.amount, 0.01)

        val txInflow = summary.transactions.first { it.date == "2026-10-08" }
        assertEquals(12000.0, txInflow.amount, 0.01)
    }

    @Test
    fun testSeparateBankCodeColumnStitching() {
        val csv = """
            Datum;Účet příjemce;Kód banky;Popis;Částka
            15.10.2026;987654321;0300;Platba nájmu;-25000,00
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csv.byteInputStream(Charsets.UTF_8))
        assertEquals(1, summary.transactions.size)
        assertEquals("987654321/0300", summary.transactions[0].counterpartyAccount)
        assertEquals(-25000.0, summary.transactions[0].amount, 0.01)
    }

    @Test
    fun testMonetaOwnAccountNameGuard() {
        val csv = """
            Datum zaúčtování;Číslo účtu;Název účtu;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce
            15.09.2026;111111/0600;Václav Martinů - Běžný;222222/0800;Zaměstnavatel s.r.o.;75000,00;CZK;Mzda
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csv.byteInputStream(Charsets.UTF_8))
        assertEquals(1, summary.transactions.size)
        assertEquals("Zaměstnavatel s.r.o.", summary.transactions[0].counterpartyName)
    }

    @Test
    fun testManualExclusionsNeverOverwrittenInReconciliation() {
        val manuallyExcludedDebit = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-08",
            bankName = "Moneta",
            date = "2026-08-10",
            amount = -15000.0,
            counterpartyAccount = "111/0300",
            counterpartyName = "Vlastní převod",
            message = "Spoření",
            category = BankTransactionType.GENERAL_EXPENSE.name,
            isNetted = true,
            nettingReason = "Manually excluded from budget"
        )

        val matchingCredit = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-08",
            bankName = "CSOB",
            date = "2026-08-11",
            amount = 15000.0,
            counterpartyAccount = "222/0600",
            counterpartyName = "Václav Martinů",
            message = "Převod z Monety",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val (reconciled, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(manuallyExcludedDebit, matchingCredit),
            yearMonth = "2026-08"
        )

        val excluded = reconciled.first { it.id == 1L }
        // Must preserve the user's manual exclusion and NOT overwrite with "Cross-account transfer: Moneta -> CSOB"
        assertEquals("Manually excluded from budget", excluded.nettingReason)
        assertTrue(excluded.isNetted)
        assertEquals(0, auditReport.matchedPairs.size)
    }

    @Test
    fun testClearingWindowDirectionalityStrictTolerance() {
        // Credit arrives 3 days BEFORE debit is sent -> physically impossible for cross-bank transfer
        val debitTx = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-08",
            bankName = "Moneta",
            date = "2026-08-15",
            amount = -20000.0,
            counterpartyAccount = "111/0300",
            counterpartyName = "",
            message = "",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val prematureCredit = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-08",
            bankName = "CSOB",
            date = "2026-08-12", // 3 days BEFORE debit!
            amount = 20000.0,
            counterpartyAccount = "222/0600",
            counterpartyName = "",
            message = "",
            category = BankTransactionType.UNCATEGORIZED.name
        )

        val (_, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(debitTx, prematureCredit),
            yearMonth = "2026-08"
        )

        assertEquals(0, auditReport.matchedPairs.size)

        // Valid forward transfer: debit on 15th, credit on 17th -> matches
        val validCredit = prematureCredit.copy(id = 3L, date = "2026-08-17")
        val (_, validReport) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(debitTx, validCredit),
            yearMonth = "2026-08"
        )
        assertEquals(1, validReport.matchedPairs.size)
        assertEquals(2L, validReport.matchedPairs.first().daysApart)
    }

    @Test
    fun testCzechMerchantSearchQueryStoreNumberAndDistrictStripping() {
        val q1 = CzechMerchantCatalog.suggestMerchantSearchQuery("ALBERT 1421 PRAHA 5")
        assertEquals("ALBERT", q1)

        val q2 = CzechMerchantCatalog.suggestMerchantSearchQuery("LIDL BRNO 2 842")
        assertEquals("LIDL", q2)

        val q3 = CzechMerchantCatalog.suggestMerchantSearchQuery("KAUFLAND OSTRAVA3 5521")
        assertEquals("KAUFLAND", q3)
    }

    @Test
    fun testSubscriptionAuditor1st31stBillingDriftDetection() {
        val tx1 = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-05",
            bankName = "mBank",
            date = "2026-05-01",
            amount = -249.0,
            counterpartyAccount = "999/6210",
            counterpartyName = "Spotify",
            message = "Spotify Premium",
            category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
        )

        val tx2 = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-05",
            bankName = "mBank",
            date = "2026-05-31", // Drift: second charge in same calendar month, 30 days later
            amount = -249.0,
            counterpartyAccount = "999/6210",
            counterpartyName = "Spotify",
            message = "Spotify Premium",
            category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
        )

        val summary = SubscriptionAuditor.auditSubscriptions(listOf(tx1, tx2))
        assertEquals(1, summary.items.size)
        val spotify = summary.items.first()
        // Must normalize to 249 CZK monthly burn rather than 498 CZK
        assertEquals(249.0, spotify.latestMonthlyAmount, 0.01)
        assertEquals(249.0, spotify.normalizedMonthlyAmount, 0.01)
        assertEquals(0, summary.priceCreepCount)
    }

    @Test
    fun testSubscriptionAuditorSemiAnnualDetection() {
        val tx = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-06",
            bankName = "CSOB",
            date = "2026-06-15",
            amount = -3600.0,
            counterpartyAccount = "123/0300",
            counterpartyName = "Pillow Pojistovna",
            message = "Pololetní platba pojištění",
            category = BankTransactionType.SERVICES_UTILITIES.name
        )

        val summary = SubscriptionAuditor.auditSubscriptions(listOf(tx))
        assertEquals(1, summary.items.size)
        val item = summary.items.first()
        assertEquals("SEMI_ANNUAL", item.billingCadence)
        assertEquals(600.0, item.normalizedMonthlyAmount, 0.01) // 3600 / 6
    }
}
