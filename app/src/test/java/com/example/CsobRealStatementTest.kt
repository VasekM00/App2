package com.example

import com.example.util.BankStatementImporter
import com.example.util.PdfTextExtractor
import org.junit.Test
import java.io.File

class CsobRealStatementTest {
    @Test
    fun testRealCsobStatement() {
        val file = File("plus_konto_2026_08.pdf")
        println("File exists: ${file.exists()}, length: ${file.length()}")
        val bytes = file.readBytes()
        println("Is PDF: ${PdfTextExtractor.isPdf(bytes)}")
        val text = PdfTextExtractor.extractText(bytes)
        println("Extracted text length: ${text.length}")
        
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        println("Total non-blank lines: ${lines.size}")
        
        val summary = BankStatementImporter.parseStatement(
            bytes,
            knownFamilyAccounts = setOf("670100-2217615726/6210", "2217615726/6210")
        )
        println("Summary detectedBank: ${summary.detectedBank}")
        println("Summary yearMonth: ${summary.yearMonth}")
        println("Summary tx count: ${summary.transactions.size}")
        println("Summary closing balance: ${summary.monthEndBalance}")
        println("Summary total inflows: ${summary.totalInflows}")
        println("Summary total expenses: ${summary.totalExpenses}")
        println("Summary internal transfers count: ${summary.internalTransfersCount}")

        summary.transactions.forEachIndexed { idx, it ->
            println("Tx[$idx]: ${it.date} | amt=${it.amount} | counterparty='${it.counterpartyName}' | msg='${it.message}' | cat=${it.category} | netted=${it.isNetted}")
        }

        org.junit.Assert.assertEquals(com.example.util.BankType.CSOB, summary.detectedBank)
        org.junit.Assert.assertEquals("2026-08", summary.yearMonth)
        org.junit.Assert.assertEquals(57, summary.transactions.size)
        org.junit.Assert.assertEquals(390.43, summary.monthEndBalance ?: 0.0, 0.01)

        // All dates must be in August 2026
        summary.transactions.forEach { tx ->
            org.junit.Assert.assertTrue("Date must start with 2026-08: ${tx.date}", tx.date.startsWith("2026-08"))
        }

        // Check specific key transactions
        val parentalTx = summary.transactions.find { it.amount == 15000.0 }
        org.junit.Assert.assertNotNull("Parental allowance 15k should be found", parentalTx)
        org.junit.Assert.assertEquals(com.example.util.BankTransactionType.PARENTAL_BENEFIT, parentalTx!!.category)

        val billaTx = summary.transactions.find { it.counterpartyName.contains("Billa", ignoreCase = true) }
        org.junit.Assert.assertNotNull("Billa purchase should be found", billaTx)
        org.junit.Assert.assertEquals(com.example.util.BankTransactionType.GROCERIES, billaTx!!.category)

        val mbankTransfer = summary.transactions.find { it.amount == -5000.0 }
        org.junit.Assert.assertNotNull("5000 CZK transfer to mBank should be found", mbankTransfer)
        org.junit.Assert.assertTrue("Transfer to family mBank should be netted", mbankTransfer!!.isNetted)
    }
}

