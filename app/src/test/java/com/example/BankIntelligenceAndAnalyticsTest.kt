package com.example

import com.example.data.ImportedBankTransactionEntity
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.FxRateService
import com.example.util.MerchantAnalyticsEngine
import com.example.util.MerchantAnalyticsEngine.DriftDirection
import com.example.util.ReportShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BankIntelligenceAndAnalyticsTest {

    @Before
    fun setUp() {
        FxRateService.resetRates()
    }

    // =========================================================================
    // 1. MERCHANT ANALYTICS ENGINE TESTS
    // =========================================================================

    @Test
    fun testTopMerchantsLeaderboardAndBudgetPercentage() {
        val transactions = listOf(
            // Rohlik: 3 purchases totaling 12 000 CZK
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-02", amount = -3_500.0, counterpartyName = "Rohlik.cz", category = "GROCERIES"),
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-10", amount = -4_500.0, counterpartyName = "Rohlik.cz", category = "GROCERIES"),
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-22", amount = -4_000.0, counterpartyName = "Rohlik.cz", category = "GROCERIES"),

            // Albert: 2 purchases totaling 6 000 CZK
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-05", amount = -3_200.0, counterpartyName = "Albert Hypermarket", category = "GROCERIES"),
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-18", amount = -2_800.0, counterpartyName = "Albert Supermarket", category = "GROCERIES"),

            // CEZ: 1 payment of 4 000 CZK
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-15", amount = -4_000.0, counterpartyName = "CEZ Prodej a.s.", category = "SERVICES_UTILITIES"),

            // Shell: 1 payment of 2 000 CZK
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-12", amount = -2_000.0, counterpartyName = "Shell Cerpaci Stanice", category = "TRANSPORTATION"),

            // Inflow and internal transfers: MUST BE EXCLUDED from spending leaderboard!
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-10", amount = 80_000.0, counterpartyName = "Zamestnavatel", category = "SALARY_VACLAV"),
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-11", amount = -15_000.0, counterpartyName = "CSOB sporici ucet", isNetted = true, category = "INTERNAL_TRANSFER")
        )

        val totalBudget = 48_000.0 // 48k CZK monthly budget
        val leaderboard = MerchantAnalyticsEngine.topMerchants(transactions, limit = 5, totalBudget = totalBudget)

        assertEquals(4, leaderboard.size)

        // Rank 1: Rohlik (12 000 CZK) -> 12k / 24k total spend = 50%, 12k / 48k budget = 25%
        val rank1 = leaderboard[0]
        assertEquals(1, rank1.rank)
        assertTrue(rank1.merchantKey.contains("rohlik"))
        assertEquals(12_000.0, rank1.totalSpend, 0.01)
        assertEquals(3, rank1.transactionCount)
        assertEquals(4_000.0, rank1.averageTransactionAmount, 0.01)
        assertEquals(50.0, rank1.percentageOfTotal, 0.01) // 12k out of 24k total
        assertEquals(25.0, rank1.percentageOfBudget!!, 0.01) // 12k out of 48k budget

        // Rank 2: Albert (6 000 CZK)
        val rank2 = leaderboard[1]
        assertEquals(2, rank2.rank)
        assertTrue(rank2.merchantKey.contains("albert"))
        assertEquals(6_000.0, rank2.totalSpend, 0.01)
        assertEquals(2, rank2.transactionCount)
        assertEquals(25.0, rank2.percentageOfTotal, 0.01)
        assertEquals(12.5, rank2.percentageOfBudget!!, 0.01)

        // Rank 3: CEZ (4 000 CZK)
        assertEquals(3, leaderboard[2].rank)
        assertEquals(4_000.0, leaderboard[2].totalSpend, 0.01)

        // Rank 4: Shell (2 000 CZK)
        assertEquals(4, leaderboard[3].rank)
        assertEquals(2_000.0, leaderboard[3].totalSpend, 0.01)
    }

    @Test
    fun testMonthOverMonthCategorySpendingDrift() {
        val txs = listOf(
            // August 2026
            ImportedBankTransactionEntity(yearMonth = "2026-08", bankName = "CSOB", date = "2026-08-10", amount = -10_000.0, counterpartyName = "Rohlik", category = "GROCERIES"),
            ImportedBankTransactionEntity(yearMonth = "2026-08", bankName = "CSOB", date = "2026-08-15", amount = -5_000.0, counterpartyName = "Restaurace", category = "DINING_RESTAURANT"),
            ImportedBankTransactionEntity(yearMonth = "2026-08", bankName = "CSOB", date = "2026-08-20", amount = -3_000.0, counterpartyName = "MHD / Shell", category = "TRANSPORTATION"),

            // September 2026: Groceries +12% (11 200), Dining -8% (4 600), Transport unchanged (3 000)
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-10", amount = -11_200.0, counterpartyName = "Rohlik", category = "GROCERIES"),
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-15", amount = -4_600.0, counterpartyName = "Restaurace", category = "DINING_RESTAURANT"),
            ImportedBankTransactionEntity(yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-20", amount = -3_000.0, counterpartyName = "MHD / Shell", category = "TRANSPORTATION")
        )

        val driftList = MerchantAnalyticsEngine.computeCategoryDrift(txs, baseYearMonth = "2026-08", targetYearMonth = "2026-09")

        val groceriesDrift = driftList.find { it.category == BankTransactionType.GROCERIES }
        assertNotNull(groceriesDrift)
        assertEquals(10_000.0, groceriesDrift!!.baseMonthAmount, 0.01)
        assertEquals(11_200.0, groceriesDrift.targetMonthAmount, 0.01)
        assertEquals(1_200.0, groceriesDrift.deltaAmount, 0.01)
        assertEquals(12.0, groceriesDrift.deltaPercentage, 0.01)
        assertEquals(DriftDirection.INCREASED, groceriesDrift.direction)

        val diningDrift = driftList.find { it.category == BankTransactionType.DINING_RESTAURANT }
        assertNotNull(diningDrift)
        assertEquals(5_000.0, diningDrift!!.baseMonthAmount, 0.01)
        assertEquals(4_600.0, diningDrift.targetMonthAmount, 0.01)
        assertEquals(-400.0, diningDrift.deltaAmount, 0.01)
        assertEquals(-8.0, diningDrift.deltaPercentage, 0.01)
        assertEquals(DriftDirection.DECREASED, diningDrift.direction)

        val transportDrift = driftList.find { it.category == BankTransactionType.TRANSPORTATION }
        assertNotNull(transportDrift)
        assertEquals(0.0, transportDrift!!.deltaAmount, 0.01)
        assertEquals(0.0, transportDrift.deltaPercentage, 0.01)
        assertEquals(DriftDirection.UNCHANGED, transportDrift.direction)
    }

    @Test
    fun testOutlierTransactionDetection() {
        val txs = listOf(
            // Alza: normal charges 500, 520, 480, 500 CZK (median = 500 CZK)
            ImportedBankTransactionEntity(id = 1, yearMonth = "2026-05", bankName = "CSOB", date = "2026-05-10", amount = -500.0, counterpartyName = "Alza.cz", category = "SHOPPING_GOODS"),
            ImportedBankTransactionEntity(id = 2, yearMonth = "2026-06", bankName = "CSOB", date = "2026-06-12", amount = -520.0, counterpartyName = "Alza.cz", category = "SHOPPING_GOODS"),
            ImportedBankTransactionEntity(id = 3, yearMonth = "2026-07", bankName = "CSOB", date = "2026-07-08", amount = -480.0, counterpartyName = "Alza.cz", category = "SHOPPING_GOODS"),
            ImportedBankTransactionEntity(id = 4, yearMonth = "2026-08", bankName = "CSOB", date = "2026-08-14", amount = -500.0, counterpartyName = "Alza.cz", category = "SHOPPING_GOODS"),
            // Sudden outlier: laptop purchase for 25 000 CZK (50x median > 3x threshold!)
            ImportedBankTransactionEntity(id = 5, yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-20", amount = -25_000.0, counterpartyName = "Alza.cz", category = "SHOPPING_GOODS"),

            // Rohlik: normal charges 1500, 1600 (no outlier)
            ImportedBankTransactionEntity(id = 6, yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-01", amount = -1_500.0, counterpartyName = "Rohlik.cz", category = "GROCERIES"),
            ImportedBankTransactionEntity(id = 7, yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-08", amount = -1_600.0, counterpartyName = "Rohlik.cz", category = "GROCERIES")
        )

        val outliers = MerchantAnalyticsEngine.detectOutliers(txs, multiplier = 3.0)

        assertEquals(1, outliers.size)
        val outlier = outliers[0]
        assertEquals(5L, outlier.transactionId)
        assertTrue(outlier.merchantKey.contains("alza"))
        assertEquals(25_000.0, outlier.amount, 0.01)
        assertEquals(500.0, outlier.merchantMedian, 0.01)
        assertEquals(50.0, outlier.ratioToMedian, 0.01)
        assertTrue(outlier.outlierReason.contains("50.0x above merchant median"))
    }

    @Test
    fun testMultiTokenFuzzyTransactionSearch() {
        val txs = listOf(
            ImportedBankTransactionEntity(id = 1, yearMonth = "2026-09", bankName = "CSOB", date = "2026-09-05", amount = -1_250.0, counterpartyName = "Rohlik.cz Praha", message = "Nakup potravin", category = "GROCERIES"),
            ImportedBankTransactionEntity(id = 2, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-10", amount = -540.0, counterpartyName = "Albert Supermarket Vinohrady", message = "Platba kartou", category = "GROCERIES"),
            ImportedBankTransactionEntity(id = 3, yearMonth = "2026-09", bankName = "MONETA", date = "2026-09-15", amount = -3_800.0, counterpartyName = "ČEZ Prodej", message = "Zaloha elektrina", category = "SERVICES_UTILITIES"),
            ImportedBankTransactionEntity(id = 4, yearMonth = "2026-09", bankName = "AIR_BANK", date = "2026-09-20", amount = 85_000.0, counterpartyName = "Zamestnavatel", message = "Mzda za srpen", category = "SALARY_VACLAV")
        )

        // 1. Multi-token search with diacritics removal: "cez zaloha" matches "ČEZ Prodej" with message "Zaloha elektrina"
        val r1 = MerchantAnalyticsEngine.searchTransactions(txs, "cez zaloha")
        assertEquals(1, r1.size)
        assertEquals(3L, r1[0].id)

        // 2. Multi-token search matching merchant and amount: "rohlik 1250"
        val r2 = MerchantAnalyticsEngine.searchTransactions(txs, "rohlik 1250")
        assertEquals(1, r2.size)
        assertEquals(1L, r2[0].id)

        // 3. Search with criteria: only expenses in MONETA
        val r3 = MerchantAnalyticsEngine.searchTransactions(
            txs,
            MerchantAnalyticsEngine.TransactionSearchCriteria(
                bankTypes = setOf("MONETA"),
                expensesOnly = true
            )
        )
        assertEquals(2, r3.size)
        assertTrue(r3.all { it.bankName == "MONETA" && it.amount < 0 })

        // 4. Fuzzy typo matching: "rohlyk" (with typo) matches "Rohlik"
        val r4 = MerchantAnalyticsEngine.searchTransactions(txs, "rohlyk")
        assertEquals(1, r4.size)
        assertEquals(1L, r4[0].id)
    }

    // =========================================================================
    // 3. MULTI-CURRENCY & FX SERVICE TESTS
    // =========================================================================

    @Test
    fun testFxRateServiceConversionsAndCustomRates() {
        // Default ČNB rates: 25.20 CZK/EUR, 23.50 CZK/USD
        assertEquals(25.20, FxRateService.getRate("EUR"), 0.001)
        assertEquals(23.50, FxRateService.getRate("USD"), 0.001)
        assertEquals(1.00, FxRateService.getRate("CZK"), 0.001)

        // Convert 100 EUR to CZK -> 2520.0 CZK
        val czkFromEur = FxRateService.convertToCzk(100.0, "EUR")
        assertEquals(2_520.0, czkFromEur, 0.01)

        // Convert 100 USD to CZK -> 2350.0 CZK
        val czkFromUsd = FxRateService.convertToCzk(100.0, "USD")
        assertEquals(2_350.0, czkFromUsd, 0.01)

        // Convert CZK to EUR -> 2520 CZK = 100 EUR
        val eurFromCzk = FxRateService.convert(2_520.0, "CZK", "EUR")
        assertEquals(100.0, eurFromCzk, 0.01)

        // Custom rate override
        FxRateService.setRate("EUR", 26.00)
        assertEquals(26.00, FxRateService.getRate("EUR"), 0.001)
        assertEquals(2_600.0, FxRateService.convertToCzk(100.0, "EUR"), 0.01)

        // Reset
        FxRateService.resetRates()
        assertEquals(25.20, FxRateService.getRate("EUR"), 0.001)
    }

    @Test
    fun testCnbDailyRatesParsingJsonAndTxt() {
        // 1. JSON format
        val json = """
            {
              "rates": [
                {"validFor": "2026-11-10", "currencyCode": "EUR", "rate": 25.45},
                {"validFor": "2026-11-10", "currencyCode": "USD", "rate": 23.85}
              ]
            }
        """.trimIndent()
        val jsonOk = FxRateService.parseCnbDailyRates(json)
        assertTrue(jsonOk)
        assertEquals(25.45, FxRateService.getRate("EUR"), 0.001)
        assertEquals(23.85, FxRateService.getRate("USD"), 0.001)

        // 2. Pipe-delimited daily TXT format
        val txt = """
            10.11.2026 #218
            země|měna|množství|kód|kurz
            EMU|euro|1|EUR|25,350
            USA|dolar|1|USD|23,650
        """.trimIndent()
        val txtOk = FxRateService.parseCnbDailyRates(txt)
        assertTrue(txtOk)
        assertEquals(25.35, FxRateService.getRate("EUR"), 0.001)
        assertEquals(23.65, FxRateService.getRate("USD"), 0.001)
    }

    @Test
    fun testBankStatementImporterMultiCurrencyCsvParsing() {
        FxRateService.resetRates()
        // CSV with EUR currency column
        val csvEur = """
            Datum;Částka;Měna;Protiúčet;Název protiúčtu;Zpráva
            15.09.2026;-50,00;EUR;DE89370400440532013000;GitHub Inc;Copilot subscription
        """.trimIndent()

        val summary = BankStatementImporter.parseStatement(csvEur.toByteArray(Charsets.UTF_8))
        assertEquals(1, summary.transactions.size)
        val tx = summary.transactions[0]

        // 50 EUR * 25.20 CZK = 1 260.00 CZK
        assertEquals(-1_260.0, tx.amount, 0.01)
        assertEquals("EUR", tx.originalCurrency)
        assertEquals(-50.0, tx.originalAmount!!, 0.01)
        assertEquals(25.20, tx.exchangeRate!!, 0.001)
    }

    @Test
    fun testBankStatementImporterCurrencySymbolDetection() {
        // Amounts with embedded € and $ symbols
        val parsedEur = BankStatementImporter.parseCzechAmountDetailed("- 20,00 €")
        assertEquals("EUR", parsedEur.originalCurrency)
        assertEquals(-20.0, parsedEur.originalAmount, 0.01)
        assertEquals(-504.0, parsedEur.amountInCzk, 0.01) // 20 * 25.20 = 504 CZK

        val parsedUsd = BankStatementImporter.parseCzechAmountDetailed("$ 100.00")
        assertEquals("USD", parsedUsd.originalCurrency)
        assertEquals(100.0, parsedUsd.originalAmount, 0.01)
        assertEquals(2_350.0, parsedUsd.amountInCzk, 0.01) // 100 * 23.50 = 2350 CZK

        val parsedCzk = BankStatementImporter.parseCzechAmountDetailed("1 500,00 Kč")
        assertEquals("CZK", parsedCzk.originalCurrency)
        assertEquals(1_500.0, parsedCzk.amountInCzk, 0.01)
    }

    // =========================================================================
    // 4. REPORT & CSV EXPORT GENERATION TESTS
    // =========================================================================

    @Test
    fun testCsvBytesWithBomAndCellEscaping() {
        val rawCsv = "Year,Label\n2026,${ReportShareHelper.escapeCsvCell("Rohlik.cz, \"BIO\"")}\n"
        val rawBytes = ReportShareHelper.getCsvBytesWithBom(rawCsv)
        assertEquals(0xEF.toByte(), rawBytes[0])
        assertEquals(0xBB.toByte(), rawBytes[1])
        assertEquals(0xBF.toByte(), rawBytes[2])
        assertTrue(rawCsv.contains("\"Rohlik.cz, \"\"BIO\"\"\""))
        assertEquals("'=1+1", ReportShareHelper.escapeCsvCell("=1+1"))
        assertEquals("-123.45", ReportShareHelper.escapeCsvCell("-123.45"))
        assertEquals("-1250,50", ReportShareHelper.escapeCsvCell("-1250,50", delimiter = ";"))
    }

    @Test
    fun testShareIntentCreation() {
        val intent = ReportShareHelper.createShareTextIntent("Financial Summary Text", "FIRE Report")
        assertEquals(android.content.Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("FIRE Report", intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT))
        assertEquals("Financial Summary Text", intent.getStringExtra(android.content.Intent.EXTRA_TEXT))
    }

    @Test
    fun testComputeCategoryDriftSingleMonthWithExplicitTargetDoesNotThrow() {
        val singleMonthTxs = listOf(
            ImportedBankTransactionEntity(
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-12",
                amount = -2_500.0,
                counterpartyName = "Rohlik.cz",
                category = BankTransactionType.GROCERIES.name
            )
        )

        val defaultBase = MerchantAnalyticsEngine.computeCategoryDrift(
            transactions = singleMonthTxs,
            baseYearMonth = null,
            targetYearMonth = "2026-08"
        )
        assertTrue(defaultBase.isEmpty())

        val blankMonths = listOf(
            ImportedBankTransactionEntity(
                yearMonth = "",
                bankName = "CSOB",
                date = "2026-08-12",
                amount = -1_000.0,
                counterpartyName = "Albert",
                category = BankTransactionType.GROCERIES.name
            )
        )
        val blankResult = MerchantAnalyticsEngine.computeCategoryDrift(
            transactions = blankMonths,
            baseYearMonth = "2026-07",
            targetYearMonth = null
        )
        assertTrue(blankResult.isEmpty())
    }

    @Test
    fun testComputeMerchantLeaderboardWithDrift_computesRealDriftAndExcludesInvestments() {
        val txs = listOf(
            // Prior 30-day window (roughly mid-June to mid-July relative to 2026-08-15)
            ImportedBankTransactionEntity(
                id = 1L,
                yearMonth = "2026-07",
                bankName = "CSOB",
                date = "2026-07-10",
                amount = -2_000.0,
                counterpartyName = "Rohlik.cz s.r.o.",
                category = BankTransactionType.GROCERIES.name
            ),
            ImportedBankTransactionEntity(
                id = 2L,
                yearMonth = "2026-07",
                bankName = "CSOB",
                date = "2026-07-08",
                amount = -4_000.0,
                counterpartyName = "Albert Hypermarket",
                category = BankTransactionType.GROCERIES.name
            ),
            // Current 30-day window (2026-07-17 .. 2026-08-15)
            ImportedBankTransactionEntity(
                id = 3L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-15",
                amount = -3_000.0,
                counterpartyName = "Rohlik.cz s.r.o.",
                category = BankTransactionType.GROCERIES.name
            ),
            ImportedBankTransactionEntity(
                id = 4L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-12",
                amount = -2_000.0,
                counterpartyName = "Albert Hypermarket",
                category = BankTransactionType.GROCERIES.name
            ),
            ImportedBankTransactionEntity(
                id = 5L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-10",
                amount = -1_500.0,
                counterpartyName = "New Cafe Praha",
                category = BankTransactionType.DINING_RESTAURANT.name
            ),
            // Investment debit MUST be excluded from expense merchant leaderboard
            ImportedBankTransactionEntity(
                id = 6L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-05",
                amount = -20_000.0,
                counterpartyName = "Portu ETF",
                category = BankTransactionType.INVESTMENT_PORTU.name
            )
        )

        val days30 = MerchantAnalyticsEngine.computeMerchantLeaderboardWithDrift(
            transactions = txs,
            timeframe = MerchantAnalyticsEngine.MerchantDriftTimeframe.DAYS_30,
            limit = 5
        )
        assertEquals(3, days30.size)
        assertFalse(days30.any { it.category == BankTransactionType.INVESTMENT_PORTU })

        val rohlik = days30.first { it.merchantKey == "rohlik" }
        assertEquals(3_000.0, rohlik.totalSpend, 0.01)
        assertNotNull(rohlik.driftPct)
        assertEquals(50.0, rohlik.driftPct!!, 0.01)
        assertTrue(rohlik.hasPriorPeriodData)
        assertEquals(listOf(3L), rohlik.representativeTxIdsByMonth)

        val albert = days30.first { it.displayName.contains("Albert") }
        assertEquals(2_000.0, albert.totalSpend, 0.01)
        assertNotNull(albert.driftPct)
        assertEquals(-50.0, albert.driftPct!!, 0.01)

        val cafe = days30.first { it.displayName.contains("Cafe") }
        assertNull(cafe.driftPct)
        assertTrue(cafe.hasPriorPeriodData)

        // 90-day window combines both July and August debits into current period (no prior baseline)
        val days90 = MerchantAnalyticsEngine.computeMerchantLeaderboardWithDrift(
            transactions = txs,
            timeframe = MerchantAnalyticsEngine.MerchantDriftTimeframe.DAYS_90,
            limit = 5
        )
        val albert90 = days90.first { it.displayName.contains("Albert") }
        assertEquals(6_000.0, albert90.totalSpend, 0.01)
        assertEquals(2, albert90.representativeTxIdsByMonth.size)
        assertNull(albert90.driftPct)
        assertFalse(albert90.hasPriorPeriodData)
    }

    @Test
    fun testComputeMerchantLeaderboardWithDrift_ytdVsPriorYear() {
        val txs = listOf(
            // Prior year YTD (2025-01-01 .. 2025-03-10)
            ImportedBankTransactionEntity(
                id = 10L,
                yearMonth = "2025-02",
                bankName = "CSOB",
                date = "2025-02-15",
                amount = -5_000.0,
                counterpartyName = "Lidl Cesko",
                category = BankTransactionType.GROCERIES.name
            ),
            // Current year YTD (2026-01-01 .. 2026-03-10)
            ImportedBankTransactionEntity(
                id = 11L,
                yearMonth = "2026-03",
                bankName = "CSOB",
                date = "2026-03-10",
                amount = -6_000.0,
                counterpartyName = "Lidl Cesko",
                category = BankTransactionType.GROCERIES.name
            )
        )

        val ytd = MerchantAnalyticsEngine.computeMerchantLeaderboardWithDrift(
            transactions = txs,
            timeframe = MerchantAnalyticsEngine.MerchantDriftTimeframe.YTD,
            limit = 5
        )
        assertEquals(1, ytd.size)
        assertEquals(6_000.0, ytd.first().totalSpend, 0.01)
        assertNotNull(ytd.first().driftPct)
        assertEquals(20.0, ytd.first().driftPct!!, 0.01)
    }
}
