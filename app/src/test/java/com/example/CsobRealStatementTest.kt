package com.example

import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Self-contained ČSOB statement verification test (no gitignored local PDFs or personal account numbers).
 */
class CsobRealStatementTest {
    @Test
    fun testCsobStatementParsingAndFamilyTransferNetting() {
        val syntheticCsobCsv = """
            Datum zaúčtování;Částka v měně účtu;Měna;IBAN / Číslo účtu protistrany;Název účtu protistrany;Zpráva pro příjemce;Variabilní symbol;Zůstatek po transakci
            05.08.2026;15 000,00;CZK;111111/0710;Úřad práce ČR;Rodičovský příspěvek;202608;25 000,00
            10.08.2026;-1 250,50;CZK;;BILLA spol. s r.o.;Platba kartou BILLA Brno;;23 749,50
            15.08.2026;-5 000,00;CZK;670100-0000001234/6210;Rodinný účet;Převod na spoření;;18 749,50
            20.08.2026;-340,00;CZK;;Zaplatím obratem s.r.o.;Faktura celkem za služby;;18 409,50
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(
            syntheticCsobCsv.toByteArray(Charsets.UTF_8),
            knownFamilyAccounts = setOf("670100-0000001234/6210", "0000001234/6210")
        )

        assertEquals(BankType.CSOB, summary.detectedBank)
        assertEquals("2026-08", summary.yearMonth)
        assertEquals(4, summary.transactions.size)
        assertEquals(18409.50, summary.monthEndBalance ?: 0.0, 0.01)

        summary.transactions.forEach { tx ->
            assertTrue("Date must start with 2026-08: ${tx.date}", tx.date.startsWith("2026-08"))
        }

        val parentalTx = summary.transactions.find { it.amount == 15000.0 }
        assertNotNull("Parental allowance 15k should be found", parentalTx)
        assertEquals(BankTransactionType.PARENTAL_BENEFIT, parentalTx!!.category)

        val billaTx = summary.transactions.find { it.counterpartyName.contains("BILLA", ignoreCase = true) }
        assertNotNull("Billa purchase should be found", billaTx)
        assertEquals(BankTransactionType.GROCERIES, billaTx!!.category)

        val mbankTransfer = summary.transactions.find { it.amount == -5000.0 }
        assertNotNull("5000 CZK transfer to family account should be found", mbankTransfer)
        assertTrue("Transfer to known family account should be netted", mbankTransfer!!.isNetted)

        val obratemTx = summary.transactions.find { it.amount == -340.0 }
        assertNotNull("Legitimate Czech transaction containing 'obratem' / 'celkem' must not be dropped", obratemTx)
    }
}

