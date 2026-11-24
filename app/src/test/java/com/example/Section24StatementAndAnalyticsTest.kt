package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.CzechEconomicSyncService
import com.example.util.FxRateService
import com.example.util.PdfTextExtractor
import com.example.util.SubscriptionAuditor
import com.example.util.statement.CzechValueParsers
import com.example.util.statement.StatementTextDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.Deflater

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Section24StatementAndAnalyticsTest {

    @Before
    fun setUp() {
        FxRateService.resetRates()
    }

    private fun deflateBytes(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun buildSimplePdfFromContent(contentStreamText: String): ByteArray {
        val streamBytes = deflateBytes(contentStreamText.toByteArray(Charsets.ISO_8859_1))
        val baos = ByteArrayOutputStream()
        baos.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
        baos.write("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n".toByteArray(Charsets.US_ASCII))
        baos.write("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n".toByteArray(Charsets.US_ASCII))
        baos.write("3 0 obj << /Type /Page /Parent 2 0 R /Contents 4 0 R >> endobj\n".toByteArray(Charsets.US_ASCII))
        baos.write("4 0 obj << /Length ${streamBytes.size} /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.US_ASCII))
        baos.write(streamBytes)
        baos.write("\nendstream\nendobj\n%%EOF\n".toByteArray(Charsets.US_ASCII))
        return baos.toByteArray()
    }

    private fun buildAbo074(
        ownAcc16: String,
        holderName20: String,
        closingHalere: Long,
        closingSign: Char,
        statementDdMmYy: String
    ): String {
        val sb = StringBuilder(128)
        sb.append("074")
        sb.append(ownAcc16.padStart(16, '0').take(16))
        sb.append(holderName20.padEnd(20, ' ').take(20))
        sb.append("010926") // old date [39..45)
        sb.append("00000001000000+") // old bal + sign [45..60)
        sb.append(String.format(Locale.US, "%014d", closingHalere))
        sb.append(closingSign) // [74]
        sb.append("000000001000000") // debit turnover + sign [75..90)
        sb.append("000000002000000") // credit turnover + sign [90..105)
        sb.append("001") // [105..108)
        sb.append(statementDdMmYy) // [108..114)
        sb.append("              ") // [114..128)
        return sb.toString()
    }

    private fun buildAbo075(
        ownAcc16: String,
        cpAcc16: String,
        amountHalere: Long,
        code: Char,
        bankCode4: String,
        note20: String,
        isoCurrency4: String,
        postingDdMmYy: String
    ): String {
        val sb = StringBuilder(128)
        sb.append("075")
        sb.append(ownAcc16.padStart(16, '0').take(16))
        sb.append(cpAcc16.padStart(16, '0').take(16))
        sb.append("1234567890123") // doc [35..48)
        sb.append(String.format(Locale.US, "%012d", amountHalere)) // [48..60)
        sb.append(code) // [60]
        sb.append("0000001234") // VS [61..71)
        sb.append("00") // KS prefix [71..73)
        sb.append(bankCode4.padStart(4, '0').take(4)) // bank code [73..77)
        sb.append("0000") // KS suffix [77..81)
        sb.append("0000000000") // SS [81..91)
        sb.append(postingDdMmYy) // valuta date [91..97)
        sb.append(note20.padEnd(20, ' ').take(20)) // note [97..117)
        sb.append("0") // [117]
        sb.append(isoCurrency4.padStart(4, '0').take(4)) // ISO currency [118..122)
        sb.append(postingDdMmYy) // posting date [122..128)
        return sb.toString()
    }

    // 1. ABO/GPC Fixed-Width Offsets, ISO Currency & Own-Bank Detection
    @Test
    fun test1_aboGpc_cba128OffsetsCurrencyAndNoCounterpartyBankGuess() {
        val header = buildAbo074(
            ownAcc16 = "0000001122334455",
            holderName20 = "VACLAV MARTINU",
            closingHalere = 1234567L, // 12,345.67
            closingSign = '+',
            statementDdMmYy = "310826"
        )
        val txCzk = buildAbo075(
            ownAcc16 = "0000001122334455",
            cpAcc16 = "0000000987654321",
            amountHalere = 150000L, // -1,500.00 CZK
            code = '1',
            bankCode4 = "0800",
            note20 = "Billa nakup potravin",
            isoCurrency4 = "0203",
            postingDdMmYy = "150826"
        )
        val txEur = buildAbo075(
            ownAcc16 = "0000001122334455",
            cpAcc16 = "0000000555666777",
            amountHalere = 10000L, // -100.00 EUR
            code = '1',
            bankCode4 = "2010",
            note20 = "Amazon.de order",
            isoCurrency4 = "0978",
            postingDdMmYy = "200826"
        )
        val txUnknown = buildAbo075(
            ownAcc16 = "0000001122334455",
            cpAcc16 = "0000000111222333",
            amountHalere = 5000L,
            code = '1',
            bankCode4 = "0800",
            note20 = "Exotic currency",
            isoCurrency4 = "0999",
            postingDdMmYy = "210826"
        )

        val gpc = listOf(header, txCzk, txEur, txUnknown).joinToString("\r\n")
        val summary = BankStatementImporter.parseStatement(gpc.toByteArray(Charsets.UTF_8))

        assertEquals("2026-08", summary.yearMonth)
        assertEquals(12345.67, summary.monthEndBalance ?: 0.0, 0.01)
        // Own bank was not in 074 header, so must stay GENERIC instead of guessing counterparty 0800/2010
        assertEquals(BankType.GENERIC, summary.detectedBank)
        // Unknown ISO 0999 transaction is skipped with a parse warning
        assertEquals(2, summary.transactions.size)
        assertNotNull(summary.parseWarning)
        assertTrue(summary.parseWarning!!.contains("ISO_0999"))

        val t0 = summary.transactions[0]
        assertEquals("987654321/0800", t0.counterpartyAccount)
        assertTrue(t0.counterpartyName.contains("Billa"))
        assertEquals("2026-08-15", t0.date)
        assertEquals(-1500.0, t0.amount, 0.01)

        val t1 = summary.transactions[1]
        assertEquals("555666777/2010", t1.counterpartyAccount)
        assertEquals("EUR", t1.originalCurrency)
        assertEquals(-100.0, t1.originalAmount ?: 0.0, 0.01)
        assertEquals(-100.0 * FxRateService.getRate("EUR"), t1.amount, 0.01)

        // All-EUR ABO statement also converts closingBalance to CZK
        val eurOnlyHeader = buildAbo074("0000001122334455", "EUR ACCOUNT", 100000L, '+', "310826")
        val eurOnlySummary = BankStatementImporter.parseStatement(
            listOf(eurOnlyHeader, txEur).joinToString("\r\n").toByteArray(Charsets.UTF_8)
        )
        assertEquals(1000.0 * FxRateService.getRate("EUR"), eurOnlySummary.monthEndBalance ?: 0.0, 0.01)
    }

    // 2. CSV Delimiter Detector & Mid-Field Quotes
    @Test
    fun test2_csvDelimiterAndMidFieldQuotes_czechPreambleAndInchMark() {
        val lines = mutableListOf(
            "Vypis z uctu;111222/0800",
            "Obdobi;01.09.2026 - 30.09.2026"
        )
        for (day in 1..17) {
            val d = String.format(Locale.US, "%02d.09.2026", day)
            val note = if (day == 3) "Monitor 27\" Dell" else "Nakup $day"
            lines.add("$d;-120,50;50000,00;$note")
        }
        val csv = lines.joinToString("\n")
        val split = StatementTextDecoder.splitCsvLines(csv)
        assertEquals(19, split.size)
        assertEquals(';', StatementTextDecoder.detectCsvDelimiter(split))

        // With explicit header + unquoted Czech decimal commas + mid-field inch quote
        val fullCsv = buildString {
            appendLine("Majitel;Vaclav")
            appendLine("Obdobi;09/2026")
            appendLine("Datum;Částka;Zůstatek;Popis")
            for (day in 1..17) {
                val d = String.format(Locale.US, "%02d.09.2026", day)
                val note = if (day == 3) "Monitor 27\" Dell" else "Albert $day"
                appendLine("$d;-120,50;50000,00;$note")
            }
        }
        val summary = BankStatementImporter.parseStatement(fullCsv.toByteArray(Charsets.UTF_8))
        assertEquals(17, summary.transactions.size)
        assertEquals("Monitor 27\" Dell", summary.transactions[2].message)
    }

    // 3. PDF Compact Operators, /ObjStm Resolution, and Encryption/ObjStm Diagnostics
    @Test
    fun test3_pdfExtractor_compactOperatorsObjStmAndDiagnostics() {
        val compactStream = "BT/F1 10 Tf 1 0 0 1 72 700 Tm(15.09.2026)Tj 1 0 0 1 72 680 Tm(Albert -250,00 Kc)Tj/F2 12 Tf(Bonus)'ET"
        val compactPdf = buildSimplePdfFromContent(compactStream)
        val extractedCompact = PdfTextExtractor.extractText(compactPdf)
        assertTrue("Expected compact date in '$extractedCompact'", extractedCompact.contains("15.09.2026"))
        assertTrue("Expected compact text in '$extractedCompact'", extractedCompact.contains("Albert -250,00 Kc"))
        assertTrue("Expected compact line operator text in '$extractedCompact'", extractedCompact.contains("Bonus"))
        assertTrue("Expected separate lines from Tm+Tj without whitespace", extractedCompact.lines().size >= 3)

        // PDF 1.5+ /ObjStm storing Font + Resources dictionaries with a ToUnicode CMap
        val cmap = """
            /CIDInit /ProcSet findresource begin
            12 dict begin
            begincmap
            1 beginbfchar
            <01> <010D>
            <02> <004B>
            endbfchar
            endcmap
        """.trimIndent().toByteArray(Charsets.US_ASCII)
        val cmapDeflated = deflateBytes(cmap)
        val contentDeflated = deflateBytes("BT /F1 10 Tf 72 700 Tm <0102> Tj ET".toByteArray(Charsets.US_ASCII))

        val obj3Body = "<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>"
        val obj5Body = "<< /Type /Font /Subtype /Type0 /ToUnicode 6 0 R >>"
        val headerPart = "3 0 5 ${obj3Body.length + 1} "
        val objStmPayload = (headerPart + obj3Body + " " + obj5Body).toByteArray(Charsets.US_ASCII)
        val objStmDeflated = deflateBytes(objStmPayload)

        val objStmPdf = ByteArrayOutputStream().apply {
            write("%PDF-1.5\n".toByteArray(Charsets.US_ASCII))
            write("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n".toByteArray(Charsets.US_ASCII))
            write("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n".toByteArray(Charsets.US_ASCII))
            write("4 0 obj << /Length ${contentDeflated.size} /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.US_ASCII))
            write(contentDeflated)
            write("\nendstream\nendobj\n".toByteArray(Charsets.US_ASCII))
            write("6 0 obj << /Length ${cmapDeflated.size} /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.US_ASCII))
            write(cmapDeflated)
            write("\nendstream\nendobj\n".toByteArray(Charsets.US_ASCII))
            write("7 0 obj << /Type /ObjStm /N 2 /First ${headerPart.length} /Length ${objStmDeflated.size} /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.US_ASCII))
            write(objStmDeflated)
            write("\nendstream\nendobj\n%%EOF\n".toByteArray(Charsets.US_ASCII))
        }.toByteArray()

        val objStmText = PdfTextExtractor.extractText(objStmPdf)
        assertTrue("Expected /ObjStm CMap decoded glyph 'čK' in '$objStmText'", objStmText.contains("čK"))

        // Encrypted PDF diagnostic
        val encryptedPdf = "%PDF-1.6\n1 0 obj << /Filter /Standard /V 4 /R 4 >> endobj\ntrailer << /Root 2 0 R /Encrypt 1 0 R >>\n%%EOF"
            .toByteArray(Charsets.US_ASCII)
        val encDetailed = PdfTextExtractor.extractTextDetailed(encryptedPdf)
        assertTrue(encDetailed.isEncrypted)
        assertEquals(PdfTextExtractor.PdfExtractionDiagnostic.ENCRYPTED_PDF, encDetailed.diagnostic)
        val encSummary = BankStatementImporter.parsePdfStatement(encryptedPdf)
        assertNotNull(encSummary.parseWarning)
        assertTrue(encSummary.parseWarning!!.contains("encrypted", ignoreCase = true))

        // Unreadable /ObjStm diagnostic
        val emptyObjStmPdf = "%PDF-1.5\n1 0 obj << /Type /ObjStm /N 1 /First 5 /Length 3 /Filter /LZWDecode >>\nstream\nabc\nendstream\nendobj\n%%EOF"
            .toByteArray(Charsets.US_ASCII)
        val stmDetailed = PdfTextExtractor.extractTextDetailed(emptyObjStmPdf)
        assertTrue(stmDetailed.hasObjectStreams)
        assertEquals(PdfTextExtractor.PdfExtractionDiagnostic.OBJECT_STREAM_OR_UNSUPPORTED_COMPRESSION, stmDetailed.diagnostic)
        val stmSummary = BankStatementImporter.parsePdfStatement(emptyObjStmPdf)
        assertNotNull(stmSummary.parseWarning)
    }

    // 4. PDF Post-2030 Years, Annual Short Dates, and Moneta Opening/Closing Checksum
    @Test
    fun test4_pdfParser_year2031AnnualPeriodAndBalanceChecksum() {
        val balancedStream = """
            BT /F1 10 Tf
            1 0 0 1 72 740 Tm (MONETA Money Bank, a.s.) Tj
            1 0 0 1 72 720 Tm (Obdobi: 01.01.2031 - 31.12.2031) Tj
            1 0 0 1 72 700 Tm (Pocatecni zustatek: 10 000,00 Kc) Tj
            1 0 0 1 72 680 Tm (15.01. Billa -1 500,00 Kc) Tj
            1 0 0 1 72 660 Tm (20.12. Albert -500,00 Kc) Tj
            1 0 0 1 72 640 Tm (Konecny zustatek: 8 000,00 Kc) Tj
            ET
        """.trimIndent()
        val balancedSummary = BankStatementImporter.parsePdfStatement(buildSimplePdfFromContent(balancedStream))
        assertEquals(BankType.MONETA, balancedSummary.detectedBank)
        assertEquals(2, balancedSummary.transactions.size)
        // Must stay in 2031 (not roll forward to 2032 on annual statement, and not break post-2029)
        assertEquals("2031-01-15", balancedSummary.transactions[0].date)
        assertEquals("2031-12-20", balancedSummary.transactions[1].date)
        assertEquals(10000.0, balancedSummary.openingBalance ?: 0.0, 0.01)
        assertEquals(8000.0, balancedSummary.monthEndBalance ?: 0.0, 0.01)
        assertEquals(true, balancedSummary.balanceCheckPassed)
        assertEquals(0.0, balancedSummary.balanceDiscrepancy ?: -1.0, 0.01)
        assertNull(balancedSummary.parseWarning)

        // Mismatched closing balance triggers checksum warning
        val mismatchStream = """
            BT /F1 10 Tf
            1 0 0 1 72 740 Tm (MONETA Money Bank, a.s.) Tj
            1 0 0 1 72 720 Tm (Obdobi: 01.01.2031 - 31.01.2031) Tj
            1 0 0 1 72 700 Tm (Pocatecni zustatek: 10 000,00 Kc) Tj
            1 0 0 1 72 680 Tm (15.01. Billa -1 500,00 Kc) Tj
            1 0 0 1 72 640 Tm (Konecny zustatek: 9 000,00 Kc) Tj
            ET
        """.trimIndent()
        val mismatchSummary = BankStatementImporter.parsePdfStatement(buildSimplePdfFromContent(mismatchStream))
        assertEquals(false, mismatchSummary.balanceCheckPassed)
        assertEquals(-500.0, mismatchSummary.balanceDiscrepancy ?: 0.0, 0.01)
        assertNotNull(mismatchSummary.parseWarning)
        assertTrue(mismatchSummary.parseWarning!!.contains("checksum", ignoreCase = true))
    }

    // 5. Positive Card Refund, Czech Pending States, and mBank '#' Delegation
    @Test
    fun test5_csvParser_positiveCardRefundCzechStatesAndMbankHashHeaders() {
        val csv = """
            Datum;Typ operace;Stav;Částka;Měna;Název protiúčtu;Zpráva
            10.09.2026;Platba kartou;ZAÚČTOVÁNO;-1 200,00;CZK;Alza.cz;Nakup
            12.09.2026;Platba kartou;COMPLETED;500,00;CZK;Alza.cz;Refundace
            13.09.2026;Platba kartou;ZAMÍTNUTO;-300,00;CZK;Shop;Denied
            14.09.2026;Platba kartou;STORNOVÁNO;-400,00;CZK;Shop;Cancelled
            15.09.2026;Platba kartou;ČEKAJÍCÍ;-500,00;CZK;Shop;Pending
            16.09.2026;Platba kartou;BLOKOVÁNO;-600,00;CZK;Shop;Blocked
            17.09.2026;Platba kartou;REZERVACE;-700,00;CZK;Shop;Reserved
            18.09.2026;Platba kartou;Čekající na zaúčtování;-800,00;CZK;Shop;Pending Czech
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csv.toByteArray(Charsets.UTF_8))
        assertEquals(2, summary.transactions.size)
        assertEquals(-1200.0, summary.transactions[0].amount, 0.01)
        assertEquals(500.0, summary.transactions[1].amount, 0.01)

        // mBank CSV with # headers and Typ operace / Stav columns delegated to parseUniversalCsv
        val mbankCsv = """
            mBank S.A.
            #Datum operace;#Typ operace;#Stav;#Popis transakce;#Částka;#Zůstatek
            05.11.2026;Platba kartou;Zaúčtováno;Lidl;-1 200,00;85 400,50
            06.11.2026;Platba kartou;Blokováno;Pending Lidl;-300,00;85 100,50
        """.trimIndent()
        val mbankSummary = BankStatementImporter.parseStatement(mbankCsv.toByteArray(Charsets.UTF_8))
        assertEquals(BankType.MBANK, mbankSummary.detectedBank)
        assertEquals(1, mbankSummary.transactions.size)
        assertEquals(-1200.0, mbankSummary.transactions[0].amount, 0.01)
    }

    // 6. Value Parsers & FX Safety
    @Test
    fun test6_valueParsersAndFx_creditCardMidDateOpeningKeyDecimalsAndCurrencies() {
        val creditCardAmounts = CzechValueParsers.extractAllAmounts("Platba kreditní kartou Albert 450,00 Kč")
        assertEquals(1, creditCardAmounts.size)
        assertEquals(-450.0, creditCardAmounts[0], 0.01)

        val midLineDateAmounts = CzechValueParsers.extractAllAmounts("Platba kartou 14.09 Albert -250,00 Kč")
        assertEquals(1, midLineDateAmounts.size)
        assertEquals(-250.0, midLineDateAmounts[0], 0.01)

        val openingAndClosing = listOf(
            "Počáteční účetní zůstatek: 10 000,00 Kč",
            "Konečný zůstatek: 25 000,00 Kč"
        )
        assertEquals(
            25000.0,
            CzechValueParsers.extractClosingBalance(openingAndClosing.joinToString("\n"), openingAndClosing) ?: 0.0,
            0.01
        )
        assertNull(
            CzechValueParsers.extractClosingBalance(
                "Počáteční účetní zůstatek: 10 000,00 Kč",
                listOf("Počáteční účetní zůstatek: 10 000,00 Kč")
            )
        )
        assertEquals(
            10000.0,
            CzechValueParsers.extractOpeningBalance(openingAndClosing.joinToString("\n"), openingAndClosing) ?: 0.0,
            0.01
        )

        assertNotEquals(
            CzechValueParsers.canonicalAccountKey("12-345678/0800"),
            CzechValueParsers.canonicalAccountKey("12345678/0800")
        )
        assertEquals(
            CzechValueParsers.canonicalAccountKey("000000-12345678/0800"),
            CzechValueParsers.canonicalAccountKey("12345678/0800")
        )

        assertEquals(25.2, BankStatementImporter.parseCzechAmount("25,200"), 0.0001)
        assertEquals(1_000_000.0, BankStatementImporter.parseCzechAmount("1,000,000"), 0.01)

        // Expanded ČNB currencies convert at non-1:1 rates
        for (curr in listOf("JPY", "SEK", "NOK", "DKK", "CAD", "AUD")) {
            val rate = FxRateService.getRateOrNull(curr)
            assertNotNull("Expected rate for $curr", rate)
            assertNotEquals(1.0, rate!!, 0.01)
            assertEquals(100.0 * rate, BankStatementImporter.parseCzechAmount("100,00 $curr"), 0.01)
        }

        // Unknown currency must NEVER convert 1:1 to CZK
        assertNull(FxRateService.getRateOrNull("XYZ"))
        assertTrue(FxRateService.getRate("XYZ").isNaN())
        assertTrue(FxRateService.convertToCzk(100.0, "XYZ").isNaN())
        val xyzDetails = CzechValueParsers.parseCzechAmountDetailed("100,00 XYZ")
        assertTrue(xyzDetails.isUnknownCurrency)
        assertTrue(xyzDetails.amountInCzk.isNaN())

        val unknownCsv = """
            Datum;Částka;Měna;Popis
            15.09.2026;-100,00;XYZ;Unknown Currency Tx
            16.09.2026;-200,00;CZK;Billa
        """.trimIndent()
        val unknownCsvSummary = BankStatementImporter.parseStatement(unknownCsv.toByteArray(Charsets.UTF_8))
        assertEquals(1, unknownCsvSummary.transactions.size)
        assertEquals(-200.0, unknownCsvSummary.transactions[0].amount, 0.01)
        assertNotNull(unknownCsvSummary.parseWarning)
        assertTrue(unknownCsvSummary.parseWarning!!.contains("XYZ"))
    }

    // 7. Reconciliation Double-Count Fix & Categorization Rules
    @Test
    fun test7_reconciliationAndCategorization_nettingOnceInsuranceStandingOrderOverridesAndSurname() {
        val debit = ImportedBankTransactionEntity(
            id = 1L, yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-10",
            amount = -10000.0, counterpartyAccount = "222/0600", counterpartyName = "Vaclav Martinu"
        )
        val credit = ImportedBankTransactionEntity(
            id = 2L, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-10",
            amount = 10000.0, counterpartyAccount = "111/0300", counterpartyName = "Vaclav Martinu"
        )
        val singleSide = ImportedBankTransactionEntity(
            id = 3L, yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-11",
            amount = -3000.0, message = "Vlastní převod", isNetted = true,
            category = BankTransactionType.INTERNAL_TRANSFER.name
        )
        val (_, report) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(debit, credit, singleSide),
            yearMonth = "2026-09",
            householdNames = listOf("Vaclav Martinu")
        )
        assertEquals(1, report.matchedPairs.size)
        // Matched pair counted ONCE (10,000) + single-side (3,000) = 13,000
        assertEquals(13000.0, report.totalNettedAmount, 0.01)

        // Generali / Allianz insurance must NOT be categorized as INVESTMENT_DPS
        assertEquals(
            BankTransactionType.SERVICES_UTILITIES,
            BankStatementImporter.categorizeTransaction(-1200.0, "123/0800", "Generali Česká pojišťovna", "Pojištění majetku", "", emptySet())
        )
        assertEquals(
            BankTransactionType.SERVICES_UTILITIES,
            BankStatementImporter.categorizeTransaction(-900.0, "123/0800", "Allianz pojišťovna", "Povinné ručení", "", emptySet())
        )
        assertEquals(
            BankTransactionType.INVESTMENT_DPS,
            BankStatementImporter.categorizeTransaction(-1700.0, "123/0800", "Generali penzijní společnost", "DPS příspěvek", "", emptySet())
        )

        // Standing order 20,000 without housing keyword must NOT become HOUSING_RENT
        val standingCat = BankStatementImporter.categorizeTransaction(
            amount = -20000.0,
            counterpartyAcc = "444/0600",
            counterpartyName = "Trvalý příkaz",
            message = "Pravidelná měsíční platba",
            vs = "",
            familyAccounts = emptySet()
        )
        assertNotEquals(BankTransactionType.HOUSING_RENT, standingCat)

        // Explicit user overrides must win over parental gift and self-transfer rules
        val overrideGift = BankStatementImporter.categorizeTransaction(
            amount = 5000.0,
            counterpartyAcc = "999/0800",
            counterpartyName = "Mamka",
            message = "Dar",
            vs = "",
            familyAccounts = emptySet(),
            userOverrides = mapOf("mamka" to BankTransactionType.GROCERIES),
            knownParentalGiftAccounts = setOf("999/0800")
        )
        assertEquals(BankTransactionType.GROCERIES, overrideGift)

        val overrideSelf = BankStatementImporter.categorizeTransaction(
            amount = -2500.0,
            counterpartyAcc = "888/0600",
            counterpartyName = "Vaclav Martinu",
            message = "Nakup",
            vs = "",
            familyAccounts = setOf("888/0600"),
            userOverrides = mapOf("vaclav martinu" to BankTransactionType.SHOPPING_GOODS),
            householdNames = listOf("Vaclav Martinu")
        )
        assertEquals(BankTransactionType.SHOPPING_GOODS, overrideSelf)

        // Shared surname must not hijack primary earner salary as spouse salary
        val names = listOf("Václav Martinů", "Eleonora Martinů")
        val vaclavSharedSurnameSalary = BankStatementImporter.categorizeTransaction(
            amount = 65000.0,
            counterpartyAcc = "111/0100",
            counterpartyName = "Zaměstnavatel s.r.o.",
            message = "Mzda Martinů",
            vs = "",
            familyAccounts = emptySet(),
            householdNames = names
        )
        assertEquals(BankTransactionType.SALARY_VACLAV, vaclavSharedSurnameSalary)

        val eleonoraSalary = BankStatementImporter.categorizeTransaction(
            amount = 40000.0,
            counterpartyAcc = "222/0100",
            counterpartyName = "Zaměstnavatel B",
            message = "Mzda Eleonora Martinů",
            vs = "",
            familyAccounts = emptySet(),
            householdNames = names
        )
        assertEquals(BankTransactionType.SALARY_ELEONORA, eleonoraSalary)
    }

    // 8. SubscriptionAuditor Fixes
    @Test
    fun test8_subscriptionAuditor_providersWeeklyPriceHikeNonContiguousAndExpiration() {
        val txs = listOf(
            // Verified transit / SIPO / gym memberships in previously blocked categories
            ImportedBankTransactionEntity(id = 1, yearMonth = "2026-08", bankName = "MONETA", date = "2026-08-05", amount = -550.0, counterpartyName = "PID Lítačka", category = BankTransactionType.TRANSPORTATION.name),
            ImportedBankTransactionEntity(id = 2, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-05", amount = -550.0, counterpartyName = "PID Lítačka", category = BankTransactionType.TRANSPORTATION.name),
            ImportedBankTransactionEntity(id = 3, yearMonth = "2026-08", bankName = "MONETA", date = "2026-08-10", amount = -3200.0, counterpartyName = "SIPO Česká pošta", message = "SIPO platba", category = BankTransactionType.HOUSING_RENT.name),
            ImportedBankTransactionEntity(id = 4, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-10", amount = -3200.0, counterpartyName = "SIPO Česká pošta", message = "SIPO platba", category = BankTransactionType.HOUSING_RENT.name),
            ImportedBankTransactionEntity(id = 5, yearMonth = "2026-08", bankName = "MONETA", date = "2026-08-03", amount = -1190.0, counterpartyName = "Form Factory Gym", message = "Fitness členství", category = BankTransactionType.HEALTH_DRUGSTORE.name),
            ImportedBankTransactionEntity(id = 6, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-03", amount = -1190.0, counterpartyName = "Form Factory Gym", message = "Fitness členství", category = BankTransactionType.HEALTH_DRUGSTORE.name),

            // GymBeam & Charity must remain excluded
            ImportedBankTransactionEntity(id = 7, yearMonth = "2026-08", bankName = "MONETA", date = "2026-08-04", amount = -890.0, counterpartyName = "GymBeam s.r.o.", category = BankTransactionType.HEALTH_DRUGSTORE.name),
            ImportedBankTransactionEntity(id = 8, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-04", amount = -890.0, counterpartyName = "GymBeam s.r.o.", category = BankTransactionType.HEALTH_DRUGSTORE.name),
            ImportedBankTransactionEntity(id = 9, yearMonth = "2026-08", bankName = "MONETA", date = "2026-08-06", amount = -500.0, counterpartyName = "Člověk v tísni", message = "Pravidelný dar", category = BankTransactionType.CHARITY_DONATION.name),
            ImportedBankTransactionEntity(id = 10, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-06", amount = -500.0, counterpartyName = "Člověk v tísni", message = "Pravidelný dar", category = BankTransactionType.CHARITY_DONATION.name),

            // Weekly subscription (4 charges 7 days apart in Sept)
            ImportedBankTransactionEntity(id = 11, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-01", amount = -150.0, counterpartyName = "MealPrep Club", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),
            ImportedBankTransactionEntity(id = 12, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-08", amount = -150.0, counterpartyName = "MealPrep Club", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),
            ImportedBankTransactionEntity(id = 13, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-15", amount = -150.0, counterpartyName = "MealPrep Club", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),
            ImportedBankTransactionEntity(id = 14, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-22", amount = -150.0, counterpartyName = "MealPrep Club", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),

            // Multi-sub Apple bill (>2 charges in one month) & >80% price hike
            ImportedBankTransactionEntity(id = 15, yearMonth = "2026-08", bankName = "MONETA", date = "2026-08-02", amount = -79.0, counterpartyName = "Apple.com/bill", message = "Předplatné iCloud", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),
            ImportedBankTransactionEntity(id = 16, yearMonth = "2026-08", bankName = "MONETA", date = "2026-08-10", amount = -169.0, counterpartyName = "Apple.com/bill", message = "Předplatné Music", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),
            ImportedBankTransactionEntity(id = 17, yearMonth = "2026-08", bankName = "MONETA", date = "2026-08-18", amount = -199.0, counterpartyName = "Apple.com/bill", message = "Předplatné TV+", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),
            ImportedBankTransactionEntity(id = 18, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-02", amount = -900.0, counterpartyName = "Apple.com/bill", message = "Předplatné Apple One", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name)
        )

        val audit = SubscriptionAuditor.auditSubscriptions(txs)
        val keys = audit.items.map { it.merchantKey }
        assertTrue("Expected litacka in $keys", keys.any { it.contains("litacka") })
        assertTrue("Expected sipo in $keys", keys.any { it.contains("sipo") })
        assertTrue("Expected form factory gym in $keys", keys.any { it.contains("gym") || it.contains("form factory") })
        assertFalse("GymBeam must be excluded", keys.any { it.contains("gymbeam") })
        assertFalse("Charity must be excluded", keys.any { it.contains("tisni") || it.contains("clovek") })

        val weekly = audit.items.first { it.merchantKey.contains("mealprep") }
        assertEquals("WEEKLY", weekly.billingCadence)
        assertEquals(150.0 * (52.0 / 12.0), weekly.normalizedMonthlyAmount, 0.01)

        val apple = audit.items.first { it.merchantKey.contains("apple") }
        assertTrue("Expected >80% hike to trigger price creep alert", apple.priceCreepDelta > 400.0)

        // Non-contiguous imports (Jan, Feb, Aug, Sep) must keep MONTHLY cadence
        val nonContiguousTxs = listOf("2026-01", "2026-02", "2026-08", "2026-09").mapIndexed { idx, ym ->
            ImportedBankTransactionEntity(
                id = 100L + idx, yearMonth = ym, bankName = "MONETA", date = "$ym-05",
                amount = -249.0, counterpartyName = "Spotify", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name
            )
        }
        val nonContiguousAudit = SubscriptionAuditor.auditSubscriptions(nonContiguousTxs)
        assertEquals("MONTHLY", nonContiguousAudit.items.single().billingCadence)

        // Canceled subscription billed in Jan-Feb disappears once Mar & Apr are imported without it
        val expiredTxs = listOf(
            ImportedBankTransactionEntity(id = 201L, yearMonth = "2026-01", bankName = "MONETA", date = "2026-01-05", amount = -249.0, counterpartyName = "Netflix", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),
            ImportedBankTransactionEntity(id = 202L, yearMonth = "2026-02", bankName = "MONETA", date = "2026-02-05", amount = -249.0, counterpartyName = "Netflix", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),
            ImportedBankTransactionEntity(id = 203L, yearMonth = "2026-03", bankName = "MONETA", date = "2026-03-05", amount = -169.0, counterpartyName = "Spotify", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name),
            ImportedBankTransactionEntity(id = 204L, yearMonth = "2026-04", bankName = "MONETA", date = "2026-04-05", amount = -169.0, counterpartyName = "Spotify", category = BankTransactionType.SUBSCRIPTIONS_MEDIA.name)
        )
        val expiredAudit = SubscriptionAuditor.auditSubscriptions(expiredTxs)
        assertFalse("Canceled Netflix must expire after 2 newer months", expiredAudit.items.any { it.merchantKey.contains("netflix") })
        assertTrue(expiredAudit.items.any { it.merchantKey.contains("spotify") })
    }

    // 9. CzechEconomicSyncService All 25 Fields & Exact CZK Formatting
    @Test
    fun test9_syncPreview_coversAll25FieldsAndExactCzkFormatting() {
        val baseSettings = SettingsEntity()
        val live = CzechRegulatoryData()
        val aligned = CzechEconomicSyncService.computeDifferences(baseSettings, live)
        assertEquals(25, aligned.size)
        assertTrue(aligned.all { !it.isDifferent })

        // Verify exact CZK formatting (15 204 Kč, not rounded to 15 200 Kč; 1 762 812 Kč, not 1 762 810 Kč)
        val child1 = aligned.first { it.label == "Child 1 Tax Bonus" }
        assertTrue("Expected exact 15 204 formatting, got ${child1.liveValueFormatted}", child1.liveValueFormatted.contains("204"))
        val threshold = aligned.first { it.label == "23% Tax Bracket Threshold" }
        assertTrue("Expected exact 1 762 812 formatting, got ${threshold.liveValueFormatted}", threshold.liveValueFormatted.contains("812"))

        // Mutate every single one of the 25 applied fields and verify computeDifferences detects each change
        val allChanged = baseSettings.copy(
            cpiInflationPct = live.csuCpiInflationPct + 1.0,
            taxRatePct = live.baseTaxRatePct + 1.0,
            taxRateSecondPct = live.progressiveTaxRatePct + 1.0,
            taxSecondBracketThresholdAnnual = live.progressive23ThresholdAnnual + 10_000.0,
            taxpayerCreditAnnual = live.taxpayerCreditAnnual + 1_000.0,
            spouseTaxCreditAnnual = live.spouseTaxCreditAnnual + 1_000.0,
            spouseIncomeLimitAnnual = live.spouseIncomeLimitAnnual + 1_000.0,
            child1TaxBonusAnnual = live.child1TaxBonusAnnual + 1_000.0,
            child2TaxBonusAnnual = live.child2TaxBonusAnnual + 1_000.0,
            child3PlusTaxBonusAnnual = live.child3PlusTaxBonusAnnual + 1_000.0,
            minWageMonthly = live.minWageMonthly + 1_000.0,
            childBonusMinWageMultiplier = live.childBonusMinWageMultiplier + 1.0,
            taxDeductionCeilingAnnual = live.dipDpsCombinedCeilingAnnual + 1_000.0,
            employerRetirementExemptionAnnual = live.employerRetirementExemptionAnnual + 1_000.0,
            dpsMinDepositForSubsidy = live.dpsMinDepositForSubsidy + 100.0,
            dpsDeductionThresholdMonthly = live.dpsDeductionThresholdMonthly + 100.0,
            dpsStandardSubsidyMaxMonthly = live.dpsStandardSubsidyMaxMonthly + 50.0,
            dpsYouthSubsidyMaxMonthly = live.dpsYouthSubsidyMaxMonthly + 50.0,
            dpsYouthAgeLimit = live.dpsYouthAgeLimit + 1,
            dpsSubsidyRateStandardPct = live.dpsSubsidyRateStandardPct + 5.0,
            dpsSubsidyRateYouthPct = live.dpsSubsidyRateYouthPct + 5.0,
            dpsStatutoryFeeCapPct = live.dpsStatutoryFeeCapPct + 0.5,
            parentalAllowanceTotalBeforeCutoff = live.parentalAllowanceTotalBeforeCutoff + 10_000.0,
            parentalAllowanceTotalFromCutoff = live.parentalAllowanceTotalFromCutoff + 10_000.0,
            parentalAllowanceCutoffYear = live.parentalAllowanceCutoffYear + 1
        )
        val diffs = CzechEconomicSyncService.computeDifferences(allChanged, live)
        assertEquals(25, diffs.size)
        assertEquals(25, diffs.count { it.isDifferent })
    }
}
