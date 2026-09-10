package com.example

import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementParserEdgeCaseTest {

    @Test
    fun test1_normalizeDate_spacedDates_normalizedProperly() {
        assertEquals("2026-09-15", BankStatementImporter.normalizeDate("15. 09. 2026"))
        assertEquals("2026-09-05", BankStatementImporter.normalizeDate("5. 9. 2026"))
        assertEquals("2026-01-01", BankStatementImporter.normalizeDate(" 1.  1.  2026 "))
    }

    @Test
    fun test2_normalizeDate_slashFormat_normalizedProperly() {
        assertEquals("2026-09-15", BankStatementImporter.normalizeDate("15/09/2026"))
        assertEquals("2026-09-15", BankStatementImporter.normalizeDate("2026/09/15"))
        assertEquals("2026-05-08", BankStatementImporter.normalizeDate("8/5/2026"))
    }

    @Test
    fun test3_normalizeDate_dashFormat_normalizedProperly() {
        assertEquals("2026-09-15", BankStatementImporter.normalizeDate("15-09-2026"))
        assertEquals("2026-09-15", BankStatementImporter.normalizeDate("2026-09-15"))
        assertEquals("2026-04-02", BankStatementImporter.normalizeDate("02-04-2026"))
    }

    @Test
    fun test4_normalizeDate_invalidFormats_returnsEmptyString() {
        assertEquals("", BankStatementImporter.normalizeDate("invalid-date"))
        assertEquals("", BankStatementImporter.normalizeDate(""))
        assertEquals("", BankStatementImporter.normalizeDate("   "))
        assertEquals("", BankStatementImporter.normalizeDate("2026.09"))
        assertEquals("", BankStatementImporter.normalizeDate("15.09"))
    }

    @Test
    fun test5_parseCzechAmount_unicodeMinus_parsedAsNegative() {
        val amount = BankStatementImporter.parseCzechAmount("\u22121500,50 Kc")
        assertEquals(-1500.50, amount, 0.001)
    }

    @Test
    fun test6_parseCzechAmount_enDash_parsedAsNegative() {
        val amount = BankStatementImporter.parseCzechAmount("\u201325000 CZK")
        assertEquals(-25000.0, amount, 0.001)
    }

    @Test
    fun test7_parseCzechAmount_emDash_parsedAsNegative() {
        val amount = BankStatementImporter.parseCzechAmount("\u20143500,00")
        assertEquals(-3500.0, amount, 0.001)
    }

    @Test
    fun test8_parseCzechAmount_narrowNoBreakSpace_strippedCleanly() {
        val amount = BankStatementImporter.parseCzechAmount("1\u202F500,75 Kc")
        assertEquals(1500.75, amount, 0.001)
    }

    @Test
    fun test9_parseCzechAmount_accountingParentheses_parsedAsNegative() {
        val amount1 = BankStatementImporter.parseCzechAmount("(1 500,00)")
        assertEquals(-1500.0, amount1, 0.001)

        val amount2 = BankStatementImporter.parseCzechAmount("(25000)")
        assertEquals(-25000.0, amount2, 0.001)
    }

    @Test
    fun test10_parseCzechAmount_explicitPlus_parsedAsPositive() {
        val amount = BankStatementImporter.parseCzechAmount("+12 500,00 Kc")
        assertEquals(12500.0, amount, 0.001)
    }

    @Test
    fun test11_parseCzechAmount_czechComma_parsedProperly() {
        val amount = BankStatementImporter.parseCzechAmount("1 234 567,89 Kc")
        assertEquals(1234567.89, amount, 0.001)
    }

    @Test
    fun test12_parseCzechAmount_englishDot_parsedProperly() {
        val amount = BankStatementImporter.parseCzechAmount("1,234.56 CZK")
        assertEquals(1234.56, amount, 0.001)
    }

    @Test
    fun test13_parseCzechAmount_emptyAndGarbage_returnsZero() {
        assertEquals(0.0, BankStatementImporter.parseCzechAmount(""), 0.001)
        assertEquals(0.0, BankStatementImporter.parseCzechAmount("   "), 0.001)
        assertEquals(0.0, BankStatementImporter.parseCzechAmount("N/A"), 0.001)
        assertEquals(0.0, BankStatementImporter.parseCzechAmount("CZK"), 0.001)
    }

    @Test
    fun test14_splitCsvLines_multilineQuotedRecord_preservesSingleRecord() {
        val csvContent = "Datum;Castka;Popis\n2026-09-01;-500;\"First line\nSecond line\"\n2026-09-02;-300;Standard"
        val records = BankStatementImporter.splitCsvLines(csvContent)
        assertEquals(3, records.size)
        assertTrue(records[1].contains("First line\nSecond line"))
    }

    @Test
    fun test15_splitCsvLines_escapedQuotes_handledAccurately() {
        val csvContent = "Datum,Amount,Description\n2026-09-01,100,\"Item with \"\"quotes\"\" inside\"\n2026-09-02,200,Regular"
        val records = BankStatementImporter.splitCsvLines(csvContent)
        assertEquals(3, records.size)
        assertTrue(records[1].contains("\"\"quotes\"\""))
    }

    @Test
    fun test16_detectCsvDelimiter_semicolonInQuotedField_picksComma() {
        val sampleLines = listOf(
            "Datum,Castka,Popis",
            "2026-09-01,1000,\"Platba; najem\"",
            "2026-09-02,2000,\"Faktura; zaloha\"",
            "2026-09-03,3000,Dalsi"
        )
        val delimiter = BankStatementImporter.detectCsvDelimiter(sampleLines)
        assertEquals(',', delimiter)
    }

    @Test
    fun test17_detectCsvDelimiter_standardCzechSemicolon_picksSemicolon() {
        val sampleLines = listOf(
            "Datum;Castka;Popis",
            "2026-09-01;-1000;Najem",
            "2026-09-02;-500;Nakup",
            "2026-09-03;-350;Obed"
        )
        val delimiter = BankStatementImporter.detectCsvDelimiter(sampleLines)
        assertEquals(';', delimiter)
    }

    @Test
    fun test18_categorizeTransaction_standingOrderWithoutHousingKeyword_notClassifiedAsRent() {
        val cat = BankStatementImporter.categorizeTransaction(
            amount = -20000.0,
            counterpartyAcc = "123456/0800",
            counterpartyName = "Pravidelne sporeni",
            message = "Trvaly prikaz sporeni a investice",
            vs = "",
            familyAccounts = emptySet()
        )
        assertNotEquals(BankTransactionType.HOUSING_RENT, cat)
    }

    @Test
    fun test19_categorizeTransaction_standingOrderWithHousingKeyword_classifiedAsRent() {
        val cat = BankStatementImporter.categorizeTransaction(
            amount = -20000.0,
            counterpartyAcc = "123456/0800",
            counterpartyName = "Majitel Byty s.r.o.",
            message = "Trvaly prikaz najem zari",
            vs = "",
            familyAccounts = emptySet()
        )
        assertEquals(BankTransactionType.HOUSING_RENT, cat)
    }

    @Test
    fun test20_importStatement_fullCsvParseWithSpacedDatesAndUnicodeMinus() {
        val csvData = """
            Datum;Částka;Název protiúčtu;Zpráva
            15. 09. 2026;−1 500,50 Kč;Billa;Nákup potravin
            20. 09. 2026;–20 000,00 Kč;Pronajímatel;Nájemné za září
            25. 09. 2026;+65 000,00 Kč;Zaměstnavatel;Mzda za srpen
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csvData.toByteArray(Charsets.UTF_8))
        assertEquals(3, summary.transactions.size)
        assertEquals("2026-09-15", summary.transactions[0].date)
        assertEquals(-1500.50, summary.transactions[0].amount, 0.001)
        assertEquals(-20000.0, summary.transactions[1].amount, 0.001)
        assertEquals(65000.0, summary.transactions[2].amount, 0.001)
    }
}
