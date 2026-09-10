package com.example

import com.example.util.BankStatementImporter
import com.example.util.BankType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

/**
 * Universal Czech-bank statement coverage: header inference, delimiters, encodings,
 * signed amounts, separate debit/credit columns, ISO datetimes, 2-digit years and summary rows.
 */
class UniversalBankImportTest {

    private fun parse(text: String) = BankStatementImporter.parseStatement(text.byteInputStream(Charsets.UTF_8))

    @Test
    fun ceskaSporitelnaSemicolonWithDirectionColumn() {
        val csv = """
            Česká spořitelna, a.s.
            Výpis z účtu
            Datum;Směr;Částka;Měna;Číslo účtu;Název účtu;Zpráva
            15.09.2026;Příchozí;75 000,00;CZK;123456789/0100;Zaměstnavatel s.r.o.;Mzda 08/2026
            18.09.2026;Odchozí;-25 000,00;CZK;987654321/0300;Pronajímatel Novák;Nájem září
            22.09.2026;Odchozí;-2 500,50;CZK;333444/0100;Albert Česká republika;Nákup potravin
        """.trimIndent()

        val summary = parse(csv)
        assertEquals(BankType.CESKA_SPORITELNA, summary.detectedBank)
        assertEquals("2026-09", summary.yearMonth)
        assertEquals(3, summary.transactions.size)
        assertEquals(75_000.0, summary.incVaclav, 0.01)
        assertEquals(25_000.0, summary.expRent, 0.01)
        assertEquals(2_500.50, summary.expGroceries, 0.01)
    }

    @Test
    fun komercniBankaCounterpartyColumns() {
        val csv = """
            Komerční banka, a.s.
            Datum;Číslo účtu protistrany;Název protistrany;Částka;Měna;Poznámka
            10.09.2026;123456/0800;Zaměstnavatel s.r.o.;75 000,00;CZK;Mzda 08/2026
            12.09.2026;987654/0300;Pronajímatel Novák;-25 000,00;CZK;Nájem září
            20.09.2026;111222/2010;WOOD Retail Solutions;-10 000,00;CZK;Portu DCA
        """.trimIndent()

        val summary = parse(csv)
        assertEquals(BankType.KOMERCNI_BANKA, summary.detectedBank)
        assertEquals(75_000.0, summary.incVaclav, 0.01)
        assertEquals(25_000.0, summary.expRent, 0.01)
        assertEquals(10_000.0, summary.invPortu, 0.01)
    }

    @Test
    fun fioBankaObjemColumnAndDps() {
        val csv = """
            Fio banka
            Datum;Objem;Měna;Protiúčet;Název protiúčtu;VS;Poznámka
            16.09.2026;75000,00;CZK;123456/0800;Zaměstnavatel s.r.o.;;Mzda
            17.09.2026;-25000,00;CZK;987654/0300;Pronajímatel Novák;1234;Nájem
            18.09.2026;-1700,00;CZK;5005004433/0300;Generali Penzijní; ;DPS Penzijko
        """.trimIndent()

        val summary = parse(csv)
        assertEquals(BankType.FIO, summary.detectedBank)
        assertEquals(75_000.0, summary.incVaclav, 0.01)
        assertEquals(25_000.0, summary.expRent, 0.01)
        assertEquals(1_700.0, summary.invDps, 0.01)
    }

    @Test
    fun airBankUnsignedAmountsWithDirectionColumn() {
        val csv = """
            Air Bank
            Datum provedení;Typ;Částka;Měna;Název protistrany;Zpráva
            15.09.2026;Příchozí;75000,00;CZK;Zaměstnavatel s.r.o.;Mzda 08/2026
            18.09.2026;Odchozí;25000,00;CZK;Pronajímatel Novák;Nájem září
            22.09.2026;Odchozí;4800,00;CZK;Albert;Potraviny
        """.trimIndent()

        val summary = parse(csv)
        assertEquals(BankType.AIR_BANK, summary.detectedBank)
        assertEquals(75_000.0, summary.incVaclav, 0.01)
        assertEquals(25_000.0, summary.expRent, 0.01)
        assertEquals(4_800.0, summary.expGroceries, 0.01)
    }

    @Test
    fun raiffeisenbankSeparateDebitAndCreditColumns() {
        val csv = """
            Raiffeisenbank a.s.
            Datum;Název protistrany;Protiúčet;Odepsáno;Připsáno;Měna
            15.09.2026;Zaměstnavatel s.r.o.;123456/0800;;75000,00;CZK
            18.09.2026;Pronajímatel Novák;987654/0300;25000,00;;CZK
            22.09.2026;Albert;333444/0100;4800,00;;CZK
        """.trimIndent()

        val summary = parse(csv)
        assertEquals(BankType.RAIFFEISENBANK, summary.detectedBank)
        assertEquals(75_000.0, summary.incVaclav, 0.01)
        assertEquals(25_000.0, summary.expRent, 0.01)
        assertEquals(4_800.0, summary.expGroceries, 0.01)
    }

    @Test
    fun tabDelimitedCreditasWithClosingBalance() {
        val csv = listOf(
            "Banka Creditas",
            "Datum\tČástka\tMěna\tProtiúčet\tNázev\tZpráva\tZůstatek",
            "15.09.2026\t75000,00\tCZK\t123456/0800\tZaměstnavatel s.r.o.\tMzda\t250000,00",
            "18.09.2026\t-25000,00\tCZK\t987654/0300\tPronajímatel Novák\tNájem\t225000,00"
        ).joinToString("\n")

        val summary = parse(csv)
        assertEquals(BankType.CREDITAS, summary.detectedBank)
        assertEquals(75_000.0, summary.incVaclav, 0.01)
        assertEquals(25_000.0, summary.expRent, 0.01)
        assertEquals(225_000.0, summary.monthEndBalance ?: 0.0, 0.01)
    }

    @Test
    fun revolutStyleEnglishCsvWithIsoDates() {
        val csv = """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            CARD_PAYMENT,Current,2026-09-15 10:00:00,2026-09-15 10:00:01,Albert Supermarket,-2500.50,0.00,CZK,COMPLETED,10000.00
            TRANSFER,Current,2026-09-16 08:00:00,2026-09-16 08:00:01,Mzda 08/2026,75000.00,0.00,CZK,COMPLETED,85000.00
            TOPUP,Current,2026-09-17 09:00:00,2026-09-17 09:00:01,Portu DCA,-10000.00,0.00,CZK,COMPLETED,75000.00
        """.trimIndent()

        val summary = parse(csv)
        assertEquals(3, summary.transactions.size)
        assertEquals("2026-09", summary.yearMonth)
        assertEquals(75_000.0, summary.incVaclav, 0.01)
        assertEquals(2_500.50, summary.expGroceries, 0.01)
        assertEquals(10_000.0, summary.invPortu, 0.01)
        assertEquals(75_000.0, summary.monthEndBalance ?: 0.0, 0.01)
    }

    @Test
    fun windows1250EncodedStatementIsDecoded() {
        val csv = """
            Česká spořitelna, a.s.
            Datum;Částka;Název protistrany;Zpráva
            15.09.2026;75 000,00;Zaměstnavatel s.r.o.;Mzda 08/2026
        """.trimIndent()
        val bytes = csv.toByteArray(Charset.forName("windows-1250"))

        val summary = BankStatementImporter.parseStatement(bytes)
        assertEquals(BankType.CESKA_SPORITELNA, summary.detectedBank)
        assertEquals(75_000.0, summary.incVaclav, 0.01)
    }

    @Test
    fun pipeDelimitedTwoDigitYearAndSummaryRowSkipped() {
        val csv = """
            Datum|Částka|Název|Poznámka
            15.09.26|75000,00|Zaměstnavatel|Mzda
            30.09.26|250000,00|Konečný zůstatek|
        """.trimIndent()

        val summary = parse(csv)
        assertEquals(1, summary.transactions.size)
        assertEquals("2026-09-15", summary.transactions.first().date)
        assertEquals(75_000.0, summary.incVaclav, 0.01)
    }

    @Test
    fun detectionUsesAccountCodesWhenNoNamePresent() {
        val csv = """
            Datum;Částka;Název;Poznámka
            15.09.2026;-25000,00;Pronajímatel;Nájem
            16.09.2026;500,00;Neznámý vklad;Převod z 123456/5500
        """.trimIndent()

        val summary = parse(csv)
        assertEquals(BankType.RAIFFEISENBANK, summary.detectedBank)
        assertTrue(summary.transactions.isNotEmpty())
    }

    @Test
    fun pdfWholeKorunaAmountsAreExtracted() {
        val outgoing = BankStatementImporter.extractAllAmountsPublic("Trvalý příkaz odchozí Nájem 25 000 Kč")
        assertTrue("Whole-koruna amount with currency suffix must be found", outgoing.isNotEmpty())
        assertEquals(-25_000.0, outgoing.first(), 0.01)
    }
}
