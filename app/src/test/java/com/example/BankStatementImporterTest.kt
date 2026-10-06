package com.example

import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankStatementImporterTest {

    @Test
    fun testMonetaStatementParsing() {
        val monetaCsv = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce;Variabilní symbol
            15.09.2026;123456/0800;Zaměstnavatel s.r.o.;75 000,00;CZK;Mzda za 08/2026;
            18.09.2026;987654/0300;Pronajímatel Novák;-25 000,00;CZK;Nájem září 2026;1234
            20.09.2026;111222/2010;WOOD Retail Solutions a.s.;-10 000,00;CZK;Portu DCA;
            22.09.2026;333444/0100;Albert Česká republika s.r.o.;-2 500,50;CZK;Nákup potravin;
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(monetaCsv.byteInputStream(Charsets.UTF_8))

        assertEquals(BankType.MONETA, summary.detectedBank)
        assertEquals("2026-09", summary.yearMonth)
        assertEquals(4, summary.transactions.size)
        assertEquals(75000.0, summary.incVaclav, 0.01)
        assertEquals(25000.0, summary.expRent, 0.01)
        assertEquals(10000.0, summary.invPortu, 0.01)
        assertEquals(2500.50, summary.expGroceries, 0.01)
        assertTrue(summary.totalInflows >= 75000.0)

        val entry = summary.toLedgerEntry()
        assertEquals("2026-09", entry.yearMonth)
        assertEquals(75000.0, entry.incVaclav, 0.01)
        assertEquals(25000.0, entry.expRent, 0.01)
        assertEquals(2500.50, entry.expGroceries, 0.01)
    }

    @Test
    fun testCsobStatementParsing() {
        val csobCsv = """
            Datum zaúčtování;Číslo účtu protistrany;Název protistrany;Částka;Měna;Zpráva pro příjemce
            12.10.2026;999888/0300;ACME Corp s.r.o.;45 000,00;CZK;Mzda Eleonora
            15.10.2026;555666/0800;Billa s.r.o.;-1 850,00;CZK;Nákup
            20.10.2026;777888/2010;Generali Penzijní společnost;-1 700,00;CZK;DPS Penzijko
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csobCsv.byteInputStream(Charsets.UTF_8))

        assertEquals(BankType.CSOB, summary.detectedBank)
        assertEquals("2026-10", summary.yearMonth)
        assertEquals(3, summary.transactions.size)
        assertEquals(45000.0, summary.incEleonora, 0.01)
        assertEquals(1850.0, summary.expGroceries, 0.01)
        assertEquals(1700.0, summary.invDps, 0.01)
    }

    @Test
    fun testMbankStatementParsingWithBalance() {
        val mbankCsv = """
            #Datum operace;#Popis transakce;#Částka;#Účetní zůstatek po operaci
            05.11.2026;Platba kartou Lidl;-1 200,00;85 400,50
            10.11.2026;Platba kartou Rohlik.cz;-2 300,00;83 100,50
            15.11.2026;Restaurace U Cerneho Vola;-1 500,00;81 600,50
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(mbankCsv.byteInputStream(Charsets.UTF_8))

        assertEquals(BankType.MBANK, summary.detectedBank)
        assertEquals("2026-11", summary.yearMonth)
        assertEquals(3, summary.transactions.size)
        assertEquals(3500.0, summary.expGroceries, 0.01)
        assertEquals(1500.0, summary.expOther, 0.01)
        assertEquals(5000.0, summary.totalExpenses, 0.01)
        assertNotNull(summary.monthEndBalance)
        assertEquals(81600.50, summary.monthEndBalance!!, 0.01)

        val entry = summary.toLedgerEntry()
        assertEquals(81600.50, entry.emergencyReserveAtMonthEnd, 0.01)
    }

    @Test
    fun testInternalTransferNetting() {
        val familyAccounts = setOf("123456789/6210", "987654321/0800")
        val statementCsv = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce
            01.09.2026;123456789/6210;Václav a Eleonora sdílený;-30 000,00;CZK;Převod na společný účet
            15.09.2026;111222/0100;Zaměstnavatel;80 000,00;CZK;Plat
            20.09.2026;444555/0300;DM Drogerie;-1 100,00;CZK;Nákup drogerie
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(
            statementCsv.byteInputStream(Charsets.UTF_8),
            knownFamilyAccounts = familyAccounts
        )

        assertEquals(1, summary.internalTransfersCount)
        assertEquals(1100.0, summary.totalExpenses, 0.01)
        assertEquals(80000.0, summary.incVaclav, 0.01)
        val transferTx = summary.transactions.first { it.amount == -30000.0 }
        assertEquals(BankTransactionType.INTERNAL_TRANSFER, transferTx.category)
    }

    @Test
    fun testEmptyOrInvalidCsvHandling() {
        val invalidCsv = "col1,col2,col3`nval1,val2,val3"
        val summary = BankStatementImporter.parseStatement(invalidCsv.byteInputStream(Charsets.UTF_8))
        assertTrue(summary.transactions.isEmpty())
        assertEquals(0.0, summary.totalInflows, 0.001)
        assertEquals(0.0, summary.totalExpenses, 0.001)
    }

    // =========================================================================
    // PDF Statement Parsing Tests (Moneta, CSOB, mBank)
    // =========================================================================

    private fun createSyntheticPdf(contentLines: List<String>): ByteArray {
        val streamText = buildString {
            append("BT\n")
            append("/F1 12 Tf\n")
            append("50 750 Td\n")
            for (line in contentLines) {
                val safe = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                append("($safe) Tj\n")
                append("T*\n")
            }
            append("ET\n")
        }

        val streamBytes = streamText.toByteArray(Charsets.UTF_8)
        val deflater = java.util.zip.Deflater()
        deflater.setInput(streamBytes)
        deflater.finish()
        val bos = java.io.ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            bos.write(buf, 0, count)
        }
        deflater.end()
        val compressedBytes = bos.toByteArray()

        val header = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length ${compressedBytes.size} /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.US_ASCII)
        val footer = "\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f \ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n100\n%%EOF\n".toByteArray(Charsets.US_ASCII)

        return header + compressedBytes + footer
    }

    @Test
    fun testMonetaPdfStatementParsing() {
        val pdfLines = listOf(
            "MONETA Money Bank, a.s. - VÝPIS Z BĚŽNÉHO ÚČTU",
            "Číslo účtu: 123456789/0600  Majitel: Václav Martínek",
            "Období: 01.09.2026 - 30.09.2026",
            "15.09.2026 123456/0800 Zaměstnavatel s.r.o. 75 000,00 CZK Mzda za 08/2026",
            "18.09.2026 987654/0300 Pronajímatel Novák -25 000,00 CZK Nájem září 2026 VS: 1234",
            "20.09.2026 111222/2010 WOOD Retail Solutions a.s. -10 000,00 CZK Portu DCA",
            "22.09.2026 333444/0100 Albert Česká republika s.r.o. -2 500,50 CZK Nákup potravin",
            "Konečný zůstatek k 30.09.2026: 45 120,50 CZK"
        )

        val pdfBytes = createSyntheticPdf(pdfLines)
        val summary = BankStatementImporter.parseStatement(pdfBytes.inputStream())

        assertEquals(BankType.MONETA, summary.detectedBank)
        assertEquals("2026-09", summary.yearMonth)
        assertEquals(4, summary.transactions.size)
        assertEquals(75000.0, summary.incVaclav, 0.01)
        assertEquals(25000.0, summary.expRent, 0.01)
        assertEquals(10000.0, summary.invPortu, 0.01)
        assertEquals(2500.50, summary.expGroceries, 0.01)
        assertNotNull(summary.monthEndBalance)
        assertEquals(45120.50, summary.monthEndBalance!!, 0.01)

        val entry = summary.toLedgerEntry()
        assertEquals("2026-09", entry.yearMonth)
        assertEquals(75000.0, entry.incVaclav, 0.01)
        assertEquals(25000.0, entry.expRent, 0.01)
        assertEquals(45120.50, entry.emergencyReserveAtMonthEnd, 0.01)
    }

    @Test
    fun testCsobPdfStatementParsing() {
        val pdfLines = listOf(
            "Československá obchodní banka, a. s. - VÝPIS Z ÚČTU",
            "Číslo účtu: 987654321/0300  Majitel: Eleonora Martínková",
            "12.10.2026 999888/0300 ACME Corp s.r.o. 45 000,00 CZK Mzda Eleonora",
            "15.10.2026 555666/0800 Billa s.r.o. -1 850,00 CZK Nákup potravin",
            "20.10.2026 777888/2010 Generali Penzijní společnost -1 700,00 CZK DPS Penzijko",
            "Zůstatek ke konci období: 38 450,00 CZK"
        )

        val pdfBytes = createSyntheticPdf(pdfLines)
        val summary = BankStatementImporter.parseStatement(pdfBytes.inputStream())

        assertEquals(BankType.CSOB, summary.detectedBank)
        assertEquals("2026-10", summary.yearMonth)
        assertEquals(3, summary.transactions.size)
        assertEquals(45000.0, summary.incEleonora, 0.01)
        assertEquals(1850.0, summary.expGroceries, 0.01)
        assertEquals(1700.0, summary.invDps, 0.01)
        assertNotNull(summary.monthEndBalance)
        assertEquals(38450.00, summary.monthEndBalance!!, 0.01)
    }

    @Test
    fun testMbankPdfStatementParsingWithBalance() {
        val pdfLines = listOf(
            "mBank S.A. organizační složka - výpis z účtu mKonto",
            "Číslo účtu: 123456789/6210  Majitel: Václav Martínek a Eleonora Martínková",
            "05.11.2026 Platba kartou Lidl -1 200,00 CZK 85 400,50 CZK",
            "10.11.2026 Platba kartou Rohlik.cz -2 300,00 CZK 83 100,50 CZK",
            "15.11.2026 Restaurace U Cerneho Vola -1 500,00 CZK 81 600,50 CZK",
            "Konečný zůstatek na účtu: 81 600,50 CZK"
        )

        val pdfBytes = createSyntheticPdf(pdfLines)
        val summary = BankStatementImporter.parseStatement(pdfBytes.inputStream())

        assertEquals(BankType.MBANK, summary.detectedBank)
        assertEquals("2026-11", summary.yearMonth)
        assertEquals(3, summary.transactions.size)
        assertEquals(3500.0, summary.expGroceries, 0.01)
        assertEquals(1500.0, summary.expOther, 0.01)
        assertEquals(5000.0, summary.totalExpenses, 0.01)
        assertEquals(81600.50, summary.monthEndBalance!!, 0.01)
    }

    @Test
    fun testMbankPdfStatementParsingShortDates() {
        val pdfLines = listOf(
            "mBank S.A. organizační složka - výpis z účtu mKonto",
            "Číslo účtu: 123456789/6210  Majitel: Václav Martinů a Eleonora Martinů",
            "Za období: 01.11.2026 - 30.11.2026",
            "05.11. Platba kartou Lidl -1 200,00 CZK 85 400,50 CZK",
            "10.11. Platba kartou Rohlik.cz -2 300,00 CZK 83 100,50 CZK",
            "15.11. Restaurace U Cerneho Vola -1 500,00 CZK 81 600,50 CZK",
            "Konečný zůstatek na účtu: 81 600,50 CZK"
        )

        val pdfBytes = createSyntheticPdf(pdfLines)
        val summary = BankStatementImporter.parseStatement(pdfBytes.inputStream())

        assertEquals(BankType.MBANK, summary.detectedBank)
        assertEquals("2026-11", summary.yearMonth)
        assertEquals(3, summary.transactions.size)
        assertTrue(summary.transactions.all { it.date.startsWith("2026-11-") })
        assertEquals(3500.0, summary.expGroceries, 0.01)
        assertEquals(1500.0, summary.expOther, 0.01)
        assertNotNull(summary.monthEndBalance)
        assertEquals(81600.50, summary.monthEndBalance!!, 0.01)
    }

    @Test
    fun testPdfInternalTransferNetting() {
        val familyAccounts = setOf("123456789/6210", "987654321/0800")
        val pdfLines = listOf(
            "MONETA Money Bank",
            "01.09.2026 123456789/6210 Václav a Eleonora sdílený -30 000,00 CZK Převod na společný účet",
            "15.09.2026 111222/0100 Zaměstnavatel 80 000,00 CZK Plat",
            "20.09.2026 444555/0300 DM Drogerie -1 100,00 CZK Nákup drogerie"
        )

        val pdfBytes = createSyntheticPdf(pdfLines)
        val summary = BankStatementImporter.parseStatement(
            pdfBytes.inputStream(),
            knownFamilyAccounts = familyAccounts
        )

        assertEquals(1, summary.internalTransfersCount)
        assertEquals(1100.0, summary.totalExpenses, 0.01)
        assertEquals(80000.0, summary.incVaclav, 0.01)
        val transferTx = summary.transactions.first { it.amount == -30000.0 }
        assertEquals(BankTransactionType.INTERNAL_TRANSFER, transferTx.category)
    }

    @Test
    fun testRealMonetaPdfStatement() {
        val file = listOf(
            java.io.File("sample_statement.pdf"),
            java.io.File("../sample_statement.pdf"),
            java.io.File("C:/Users/Ela/.gemini/antigravity/scratch/App2/sample_statement.pdf")
        ).firstOrNull { it.exists() } ?: return

        val bytes = file.readBytes()
        assertTrue(com.example.util.PdfTextExtractor.isPdf(bytes))

        val summary = BankStatementImporter.parseStatement(bytes.inputStream())

        assertEquals(BankType.MONETA, summary.detectedBank)
        assertEquals("2026-07", summary.yearMonth)
        assertTrue("Expected ~79 transactions, got ${summary.transactions.size}", summary.transactions.size >= 75)
        assertTrue("Total expenses should be > 20 000 CZK, was ${summary.totalExpenses}", summary.totalExpenses > 20000.0)
        assertTrue("Internal self-transfers should be netted out, was ${summary.internalTransfersCount}", summary.internalTransfersCount >= 1)
        assertTrue("Total netted amount should be > 0, was ${summary.totalNettedAmount}", summary.totalNettedAmount > 0.0)
        assertNotNull(summary.monthEndBalance)
        assertEquals(35429.76, summary.monthEndBalance!!, 0.01)

        // Verify key categorized transactions
        assertTrue("Portu investment should be parsed", summary.invPortu >= 5000.0)
        assertTrue("Vaclav salary should be parsed", summary.incVaclav >= 30000.0)
        assertTrue("Groceries should be parsed", summary.expGroceries > 0.0)

        val entry = summary.toLedgerEntry()
        assertEquals("2026-07", entry.yearMonth)
        assertEquals(35429.76, entry.emergencyReserveAtMonthEnd, 0.01)
    }

    @Test
    fun testMbankInternalGoalsAndMsporeniAreExcludedFromLedger() {
        val mbankCsv = """
            #Datum operace;#Popis transakce;#Částka;#Účetní zůstatek po operaci
            01.11.2026;Převod na mSpoření;-500,00;85 000,00
            05.11.2026;Platba kartou Albert;-1 200,00;83 800,00
            10.11.2026;Převod z cíle: Rezerva;2 500,00;86 300,00
            15.11.2026;Nákup knih Kosmas;-450,00;85 850,00
            20.11.2026;Prevod na cil - Nove auto;-1 000,00;84 850,00
            25.11.2026;Prevod z msporeni;300,00;85 150,00
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(mbankCsv.byteInputStream(Charsets.UTF_8))

        assertEquals(BankType.MBANK, summary.detectedBank)
        assertEquals("2026-11", summary.yearMonth)
        assertEquals(6, summary.transactions.size)

        // Verify the 4 internal transfers (mSpoření and Cíle)
        val msporeniOut = summary.transactions[0]
        assertEquals(BankTransactionType.INTERNAL_TRANSFER, msporeniOut.category)
        assertTrue(msporeniOut.isNetted)
        assertEquals("mBank internal transfer (mSpoření / Cíl)", msporeniOut.nettingReason)

        val cilIn = summary.transactions[2]
        assertEquals(BankTransactionType.INTERNAL_TRANSFER, cilIn.category)
        assertTrue(cilIn.isNetted)
        assertEquals("mBank internal transfer (mSpoření / Cíl)", cilIn.nettingReason)

        val cilOut = summary.transactions[4]
        assertEquals(BankTransactionType.INTERNAL_TRANSFER, cilOut.category)
        assertTrue(cilOut.isNetted)
        assertEquals("mBank internal transfer (mSpoření / Cíl)", cilOut.nettingReason)

        val msporeniIn = summary.transactions[5]
        assertEquals(BankTransactionType.INTERNAL_TRANSFER, msporeniIn.category)
        assertTrue(msporeniIn.isNetted)
        assertEquals("mBank internal transfer (mSpoření / Cíl)", msporeniIn.nettingReason)

        // Internal transfers must be 4, and netted amount 500 + 2500 + 1000 + 300 = 4300
        assertEquals(4, summary.internalTransfersCount)
        assertEquals(4300.0, summary.totalNettedAmount, 0.01)

        // Neither mSpoření nor Cíle should alter expenses or inflows!
        // Expenses are ONLY Albert (1200) + Kosmas (450) = 1650
        assertEquals(1200.0, summary.expGroceries, 0.01)
        assertEquals(450.0, summary.expOther, 0.01)
        assertEquals(1650.0, summary.totalExpenses, 0.01)

        // Inflows must be 0 (cíl is NOT counted as salary or unforeseen income!)
        assertEquals(0.0, summary.totalInflows, 0.01)
        assertEquals(0.0, summary.incVaclav, 0.01)
        assertEquals(0.0, summary.incEleonora, 0.01)
        assertEquals(0.0, summary.incOther, 0.01)

        // When converting to ledger entry, internal movements are strictly excluded
        val entry = summary.toLedgerEntry()
        assertEquals(0.0, entry.incVaclav, 0.01)
        assertEquals(0.0, entry.incEleonora, 0.01)
        assertEquals(0.0, entry.incUnforeseen, 0.01)
        assertEquals(1200.0, entry.expGroceries, 0.01)
        assertEquals(450.0, entry.expOther, 0.01)
        assertEquals(0.0, entry.expRent, 0.01)
    }

    @Test
    fun testStandingOrderRentCategorization() {
        val statementCsv = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce
            20.07.2026;123456/0300;Trvalý příkaz k úhradě Příkazce: Martinů Václav;-18 950,00;CZK;Platba
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(statementCsv.byteInputStream(Charsets.UTF_8))

        assertEquals(1, summary.transactions.size)
        val tx = summary.transactions[0]
        assertEquals(BankTransactionType.HOUSING_RENT, tx.category)
        assertEquals(false, tx.isNetted)
        assertEquals(18950.0, summary.expRent, 0.01)
        assertEquals(0, summary.internalTransfersCount)
    }

    @Test
    fun testMultiMonthStatementDateSplitting() {
        val multiMonthCsv = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce
            10.07.2026;123456/0800;Albert Česká republika s.r.o.;-1 200,00;CZK;Potraviny
            20.07.2026;987654/0300;Trvalý příkaz k úhradě Příkazce: Martinů Václav;-18 950,00;CZK;Platba
            02.08.2026;555666/0800;Billa s.r.o.;-800,00;CZK;Nákup
            05.08.2026;777888/2010;Rohlik.cz;-1 500,00;CZK;Online potraviny
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(multiMonthCsv.byteInputStream(Charsets.UTF_8))

        assertEquals(4, summary.transactions.size)
        val byMonth = summary.transactions.groupBy { it.date.take(7) }
        assertEquals(setOf("2026-07", "2026-08"), byMonth.keys)

        val julyTxs = byMonth["2026-07"]!!
        assertEquals(2, julyTxs.size)
        assertEquals(BankTransactionType.GROCERIES, julyTxs[0].category)
        assertEquals(BankTransactionType.HOUSING_RENT, julyTxs[1].category)

        val augustTxs = byMonth["2026-08"]!!
        assertEquals(2, augustTxs.size)
        assertEquals(BankTransactionType.GROCERIES, augustTxs[0].category)
        assertEquals(BankTransactionType.GROCERIES, augustTxs[1].category)
    }

    @Test
    fun testCardRefundsAndExpenseOffsets() {
        // Albert groceries purchase: 3 000 CZK, Albert refund: 600 CZK
        // General shopping purchase: 2 500 CZK, Alza refund: 1 000 CZK
        val csvWithRefunds = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce
            10.09.2026;123456/0800;Albert Česká republika s.r.o.;-3 000,00;CZK;Platba kartou Albert
            12.09.2026;123456/0800;Albert Česká republika s.r.o.;600,00;CZK;Platba kartou - vrácení částky
            15.09.2026;987654/0300;Alza.cz a.s.;-2 500,00;CZK;Nákup zboží
            18.09.2026;987654/0300;Alza.cz a.s.;1 000,00;CZK;Vratka storno nákupu
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csvWithRefunds.byteInputStream(Charsets.UTF_8))

        assertEquals(4, summary.transactions.size)
        assertEquals(BankTransactionType.GROCERIES, summary.transactions[0].category)
        assertEquals(-3000.0, summary.transactions[0].amount, 0.01)

        assertEquals(BankTransactionType.GROCERIES, summary.transactions[1].category)
        assertEquals(600.0, summary.transactions[1].amount, 0.01)

        assertEquals(BankTransactionType.SHOPPING_GOODS, summary.transactions[2].category)
        assertEquals(-2500.0, summary.transactions[2].amount, 0.01)

        assertEquals(BankTransactionType.SHOPPING_GOODS, summary.transactions[3].category)
        assertEquals(1000.0, summary.transactions[3].amount, 0.01)

        // Groceries should net to 3000 - 600 = 2400
        assertEquals(2400.0, summary.expGroceries, 0.01)
        // Other expenses should net to 2500 - 1000 = 1500
        assertEquals(1500.0, summary.expOther, 0.01)
        assertEquals(3900.0, summary.totalExpenses, 0.01)
    }

    @Test
    fun testStandaloneRefundExceedingExpensesFlowsToOtherInflows() {
        // Month where only an Alza refund of 1 500 CZK occurred without other expenses
        val csvRefundOnly = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce
            05.09.2026;987654/0300;Alza.cz a.s.;1 500,00;CZK;Vratka nákupu
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csvRefundOnly.byteInputStream(Charsets.UTF_8))

        assertEquals(1, summary.transactions.size)
        assertEquals(1500.0, summary.transactions[0].amount, 0.01)
        assertEquals(0.0, summary.expOther, 0.01)
        assertEquals(0.0, summary.totalExpenses, 0.01)
        assertEquals(1500.0, summary.incOther, 0.01)
        assertEquals(1500.0, summary.totalInflows, 0.01)
    }
}
