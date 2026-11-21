package com.example

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.CzechCapitalGainsEngine
import com.example.domain.DecumulationConfig
import com.example.domain.DecumulationStrategies
import com.example.domain.FinancialEngine
import com.example.domain.HouseholdMember
import com.example.domain.HouseholdProfile
import com.example.domain.HouseholdRole
import com.example.domain.InvestmentLot
import com.example.domain.LotSaleOrder
import com.example.domain.TimeTestStatus
import com.example.util.BackupManager
import com.example.util.BankTransactionType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.InvestmentLotManager
import com.example.util.PdfTextExtractor
import com.example.util.ReportShareHelper
import com.example.util.UserInvestmentLot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.time.LocalDate
import java.util.zip.Deflater

/**
 * Wave 3 adversarial, boundary, calendar, parsing, security, and regression tests
 * across data/util/domain layers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Wave3AdversarialDomainAndDataTest {

    // -------------------------------------------------------------------------
    // 1. PdfTextExtractor: genuine Windows-1250 Czech diacritics, truncated EOFs,
    //    corrupt FlateDecode streams, CMap decoding, and huge bfrange cap
    // -------------------------------------------------------------------------

    private fun buildPdfWithStream(dictionary: String, streamData: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write("%PDF-1.4\n1 0 obj\n$dictionary\nstream\n".toByteArray(Charsets.US_ASCII))
        baos.write(streamData)
        baos.write("\nendstream\nendobj\n%%EOF".toByteArray(Charsets.US_ASCII))
        return baos.toByteArray()
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()
        val buffer = ByteArray(1024)
        val baos = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            baos.write(buffer, 0, count)
        }
        deflater.end()
        return baos.toByteArray()
    }

    @Test
    fun pdfTextExtractor_realWindows1250CzechBytesAndTruncatedMalformedStreams_neverCrash() {
        val cp1250 = Charset.forName("windows-1250")
        val czechPhrase = "CSOB Vypis z uctu - Prilis zlutoucky kun upel dabelske ody"
        val realCzechSentence = "ČSOB Výpis účtu — Příliš žluťoučký kůň"
        val streamHeader = "BT /F1 12 Tf (".toByteArray(Charsets.US_ASCII)
        val streamFooter = ") Tj ET".toByteArray(Charsets.US_ASCII)
        val uncompressedStream = streamHeader + realCzechSentence.toByteArray(cp1250) + streamFooter
        val compressedStream = deflate(uncompressedStream)

        val pdfPlain = buildPdfWithStream("<< /Length ${uncompressedStream.size} >>", uncompressedStream)
        val pdfCompressed = buildPdfWithStream("<< /Filter /FlateDecode /Length ${compressedStream.size} >>", compressedStream)

        val extractedPlain = PdfTextExtractor.extractText(pdfPlain)
        val extractedCompressed = PdfTextExtractor.extractText(pdfCompressed)
        assertTrue("Expected real Windows-1250 Czech diacritics from plain stream: $extractedPlain", extractedPlain.contains("Výpis účtu"))
        assertTrue("Expected real Windows-1250 Czech diacritics from FlateDecode stream: $extractedCompressed", extractedCompressed.contains("žluťoučký"))

        // Truncated strings, hex strings, TJ arrays at stream EOF & corrupt zlib must not throw
        val truncatedCases = listOf(
            "BT (unclosed_at_eof",
            "BT <4142",
            "BT [ (unclosed_tj",
            "BT (ok $czechPhrase) Tj (dangling\\",
            "BT [ <AABBCC"
        )
        for (raw in truncatedCases) {
            val pdfTrunc = buildPdfWithStream("<< /Length ${raw.length} >>", raw.toByteArray(Charsets.US_ASCII))
            val out = PdfTextExtractor.extractText(pdfTrunc)
            assertNotNull(out)
        }

        // Corrupt FlateDecode bytes must hit finally { inflater.end() } cleanly and return non-null
        val corruptZlib = byteArrayOf(0x78, 0x9C.toByte(), 0xFF.toByte(), 0x00, 0x13, 0x37)
        val pdfCorruptZlib = buildPdfWithStream("<< /Filter /FlateDecode /Length ${corruptZlib.size} >>", corruptZlib)
        assertNotNull(PdfTextExtractor.extractText(pdfCorruptZlib))
    }

    @Test
    fun pdfTextExtractor_toUnicodeCMapAndHugeBfRangeSpan_boundedAndDecoded() {
        val cmap = """
            /CIDInit /ProcSet findresource begin
            12 dict begin
            begincmap
            1 beginbfrange
            <0000> <FFFF> <0020>
            endbfrange
            1 beginbfchar
            <0001> <010C>
            endbfchar
            endcmap
        """.trimIndent()
        val deflatedCmap = deflate(cmap.toByteArray(Charsets.US_ASCII))
        val content = deflate("BT /F1 12 Tf <0001> Tj ET".toByteArray(Charsets.US_ASCII))

        val baos = ByteArrayOutputStream()
        baos.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
        baos.write("1 0 obj\n<< /Type /Font /Subtype /Type0 /ToUnicode 2 0 R >>\nendobj\n".toByteArray(Charsets.US_ASCII))
        baos.write("2 0 obj\n<< /Filter /FlateDecode /Length ${deflatedCmap.size} >>\nstream\n".toByteArray(Charsets.US_ASCII))
        baos.write(deflatedCmap)
        baos.write("\nendstream\nendobj\n".toByteArray(Charsets.US_ASCII))
        baos.write("3 0 obj\n<< /Resources << /Font << /F1 1 0 R >> >> /Contents 4 0 R >>\nendobj\n".toByteArray(Charsets.US_ASCII))
        baos.write("4 0 obj\n<< /Filter /FlateDecode /Length ${content.size} >>\nstream\n".toByteArray(Charsets.US_ASCII))
        baos.write(content)
        baos.write("\nendstream\nendobj\n%%EOF".toByteArray(Charsets.US_ASCII))

        val text = PdfTextExtractor.extractText(baos.toByteArray())
        assertTrue("Expected CMap decoded Č (U+010C) in '$text'", text.contains("\u010C"))
    }

    // -------------------------------------------------------------------------
    // 2. InvestmentLotManager: corrupt-lot isolation, NaN/null, ZDP >3yr & leap-day
    // -------------------------------------------------------------------------

    @Test
    fun investmentLotManager_isolatesCorruptEntries_guardsNaN_andEnforcesLeapYearTimeTest() {
        val json = """
            [
              {"id":"lot1","batchName":"Alpha","instrument":"VWCE","purchaseDateStr":"2024-02-29","acquisitionCostCzk":10000.0,"currentValueCzk":15000.0},
              "not_an_object",
              null,
              {"id":"nanLot","batchName":"NaN","instrument":"IWDA","purchaseDateStr":"2022-01-01","acquisitionCostCzk":"NaN","currentValueCzk":"Infinity"},
              {"batchName":"Beta","instrument":"EUNL","purchaseDateStr":"2021-06-15","acquisitionCostCzk":20000,"currentValueCzk":30000}
            ]
        """.trimIndent()

        val lots = InvestmentLotManager.parseLotsFromJson(json)
        assertEquals("Should preserve the 3 JSONObject elements while skipping scalar/null elements", 3, lots.size)
        assertEquals("Alpha", lots[0].batchName)
        assertEquals(0.0, lots[1].acquisitionCostCzk, 0.001)
        assertEquals(0.0, lots[1].currentValueCzk, 0.001)
        assertTrue("Missing id should synthesize deterministic non-blank UUID", lots[2].id.isNotBlank())

        // ZDP § 4(1)(w) strictly > 3 years: leap-day 2024-02-29 + 3y = 2027-02-28
        val leapLot = lots[0]
        assertFalse("Exact 3-year mark (2027-02-28) is not yet strictly > 3 years", leapLot.isExempt(LocalDate.of(2027, 2, 28)))
        assertEquals(0.0, leapLot.taxSavedCzk(15.0, LocalDate.of(2027, 2, 28)), 0.001)
        assertTrue("One day after 3-year mark (2027-03-01) must be exempt", leapLot.isExempt(LocalDate.of(2027, 3, 1)))
        assertEquals(750.0, leapLot.taxSavedCzk(15.0, LocalDate.of(2027, 3, 1)), 0.001)

        // Direct UserInvestmentLot with NaN fields must produce finite 0 gain & tax
        val nanDirect = UserInvestmentLot("x", "N", "T", "bad-date", Double.NaN, Double.POSITIVE_INFINITY)
        assertFalse(nanDirect.isPurchaseDateValid)
        assertEquals(0.0, nanDirect.unrealizedGainCzk, 0.001)
        assertEquals(0.0, nanDirect.taxSavedCzk(Double.NaN), 0.001)
    }

    // -------------------------------------------------------------------------
    // 3. DecumulationStrategies: early depletion zero-fill & 100% drop + VPD bounds
    // -------------------------------------------------------------------------

    @Test
    fun decumulationStrategies_earlyDepletionZeroFillsAndReports100PctDrop() {
        val harshConfig = DecumulationConfig(
            initialPortfolio = 400_000.0,
            initialAnnualWithdrawal = 180_000.0,
            horizonYears = 20,
            annualInflationPct = 8.0,
            expectedAnnualReturnPct = -10.0,
            returnVolatilityPct = 5.0,
            simulationsCount = 60,
            randomSeed = 7L
        )
        val result = DecumulationStrategies.compareStrategies(harshConfig)
        assertTrue("Constant Real SWR must deplete under harsh withdrawal", result.bengenMetrics.survivalRatePct < 5.0)
        assertNotNull("Depletion year must be recorded", result.bengenMetrics.averageDepletionYear)
        assertEquals(100.0, result.bengenMetrics.maxAnnualIncomeDropPct, 0.01)
        assertTrue(result.variablePercentageMetrics.averageAnnualRealIncome.isFinite())
        assertTrue(result.executiveSummary.contains("Recommended strategy:"))

        // Extreme inflation 0% & 25% + NaN return fallback must produce finite metrics
        for (infl in listOf(0.0, 25.0)) {
            val extreme = DecumulationStrategies.compareStrategies(
                DecumulationConfig(
                    initialPortfolio = 5_000_000.0,
                    initialAnnualWithdrawal = 200_000.0,
                    horizonYears = 15,
                    annualInflationPct = infl,
                    expectedAnnualReturnPct = Double.NaN,
                    simulationsCount = 50
                )
            )
            assertTrue(extreme.guytonKlingerMetrics.survivalRatePct.isFinite())
            assertTrue(extreme.variablePercentageMetrics.medianEndingCapitalReal.isFinite())
        }
    }

    // -------------------------------------------------------------------------
    // 4. CzechCapitalGainsEngine: duplicate lot capping & Tier 3-5 harvesting
    // -------------------------------------------------------------------------

    @Test
    fun czechCapitalGainsEngine_duplicateSaleOrdersCapped_andHarvestRespectsTiers() {
        val asOf = LocalDate.of(2027, 3, 1)
        val exemptLeapLot = InvestmentLot(
            id = "exempt_leap",
            ticker = "VWCE",
            purchaseDate = LocalDate.of(2024, 2, 29), // plusYears(3)=2027-02-28 -> strictly >3y on 2027-03-01 (ZDP § 4(1)(u))
            units = 10.0,
            acquisitionPricePerUnitCzk = 2000.0,
            currentPricePerUnitCzk = 4000.0 // 40,000 value, +20,000 gain
        )
        assertEquals(TimeTestStatus.APPROACHING_EXEMPTION, exemptLeapLot.timeTestStatus(LocalDate.of(2027, 2, 28)))
        val lossLot = InvestmentLot(
            id = "loss_lot",
            ticker = "BOND",
            purchaseDate = LocalDate.of(2026, 6, 1), // TAXABLE
            units = 10.0,
            acquisitionPricePerUnitCzk = 3000.0,
            currentPricePerUnitCzk = 2000.0 // 20,000 value, -10,000 loss
        )
        val taxableGainLot = InvestmentLot(
            id = "taxable_gain",
            ticker = "CSPX",
            purchaseDate = LocalDate.of(2026, 6, 1), // TAXABLE
            units = 20.0,
            acquisitionPricePerUnitCzk = 5000.0,
            currentPricePerUnitCzk = 10000.0 // 200,000 value, +5,000/unit gain
        )
        val approachingGainLot = InvestmentLot(
            id = "approaching_lot",
            ticker = "EUNL",
            purchaseDate = LocalDate.of(2024, 5, 1), // within 6m of 2027-05-01 -> APPROACHING_EXEMPTION
            units = 10.0,
            acquisitionPricePerUnitCzk = 5000.0,
            currentPricePerUnitCzk = 10000.0 // 100,000 value
        )
        assertEquals(TimeTestStatus.EXEMPT_TIME_TEST, exemptLeapLot.timeTestStatus(asOf))
        assertEquals(TimeTestStatus.APPROACHING_EXEMPTION, approachingGainLot.timeTestStatus(asOf))

        // Duplicate order IDs must not sell > available units (8 + 5 capped at 10)
        val dupSim = CzechCapitalGainsEngine.simulateSale(
            lots = listOf(exemptLeapLot),
            saleOrders = listOf(LotSaleOrder("exempt_leap", 8.0), LotSaleOrder("exempt_leap", 5.0)),
            asOfDate = asOf
        )
        assertEquals(40000.0, dupSim.totalGrossProceedsCzk, 0.01)
        assertEquals(0.0, dupSim.taxableGainCzk, 0.01)

        // Harvest 140,000 CZK: Tier 1 sells exempt_leap (40k) -> Tier 2 sells loss_lot (20k, leaving 80k of 100k limit)
        // -> Tier 3 sells up to 80k of taxableGainLot (within 100k gross exemption, total non-exempt = 20k + 80k = 100k!)
        // avoiding approaching_lot completely!
        val harvest = CzechCapitalGainsEngine.recommendOptimalHarvest(
            lots = listOf(exemptLeapLot, lossLot, taxableGainLot, approachingGainLot),
            targetCashCzk = 140_000.0,
            asOfDate = asOf
        )
        assertEquals(140_000.0, harvest.totalCashRaisedCzk, 1.0)
        assertEquals("Taxable gain within 100k non-exempt ceiling must owe 0 CZK", 0.0, harvest.taxLiabilityCzk, 0.01)
        assertFalse(
            "Must NOT liquidate approaching-exemption lot when taxable headroom suffices",
            harvest.saleOrders.any { it.lotId == "approaching_lot" }
        )

        // Harvest 350,000 CZK forces Tier 4 (taxable beyond 100k) and Tier 5 (approaching)
        val deepHarvest = CzechCapitalGainsEngine.recommendOptimalHarvest(
            lots = listOf(exemptLeapLot, lossLot, taxableGainLot, approachingGainLot),
            targetCashCzk = 350_000.0,
            asOfDate = asOf
        )
        assertTrue(deepHarvest.totalCashRaisedCzk >= 349_999.0)
        assertTrue(deepHarvest.saleOrders.any { it.lotId == "approaching_lot" })
    }

    // -------------------------------------------------------------------------
    // 5. CrossStatementReconciliationEngine: false positives + Dec/Jan & leap boundaries
    // -------------------------------------------------------------------------

    @Test
    fun reconciliation_rejectsUnrelatedEqualAmounts_andMatchesYearRolloverAndLeapBoundaries() {
        // False-positive rejection: external grocery purchase vs external salary of exact same amount
        val grocery = ImportedBankTransactionEntity(
            id = 1, yearMonth = "2026-03", bankName = "CSOB", date = "2026-03-10",
            amount = -5000.0, counterpartyName = "Albert Supermarket", category = BankTransactionType.GROCERIES.name
        )
        val salary = ImportedBankTransactionEntity(
            id = 2, yearMonth = "2026-03", bankName = "AIRBANK", date = "2026-03-10",
            amount = 5000.0, counterpartyName = "ACME Employer s.r.o.", category = BankTransactionType.SALARY_VACLAV.name
        )
        val (unmatchedTxs, unmatchedReport) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(grocery, salary),
            yearMonth = "2026-03"
        )
        assertTrue("External grocery and external salary must NOT be falsely netted", unmatchedTxs.none { it.isNetted })
        assertEquals(0, unmatchedReport.matchedPairs.size)

        // Year rollover: 2025-12-30 debit in Raiffeisen boundary -> 2026-01-02 credit in Air Bank
        val decDebit = ImportedBankTransactionEntity(
            id = 10, yearMonth = "2025-12", bankName = "RAIFFEISEN", date = "2025-12-30",
            amount = -15000.0, counterpartyAccount = "999888/3030", counterpartyName = "Vlastni prevod Air Bank",
            category = BankTransactionType.INTERNAL_TRANSFER.name
        )
        val janCredit = ImportedBankTransactionEntity(
            id = 11, yearMonth = "2026-01", bankName = "AIRBANK", date = "2026-01-02",
            amount = 15000.0, counterpartyAccount = "777666/5500", counterpartyName = "Prevod z RB",
            category = BankTransactionType.INTERNAL_TRANSFER.name
        )
        val rolloverResult = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(janCredit),
            yearMonth = "2026-01",
            knownFamilyAccounts = setOf("999888/3030", "777666/5500"),
            boundaryTransactions = listOf(decDebit)
        )
        assertEquals(1, rolloverResult.auditReport.matchedPairs.size)
        assertEquals(3L, rolloverResult.auditReport.matchedPairs[0].daysApart)
        assertTrue(rolloverResult.currentMonthTxs.single().isNetted)

        // Leap year boundary: 2024-02-29 -> 2024-03-01 (1 day apart)
        val feb29 = decDebit.copy(id = 20, yearMonth = "2024-02", date = "2024-02-29")
        val mar01 = janCredit.copy(id = 21, yearMonth = "2024-03", date = "2024-03-01")
        val leapResult = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(mar01),
            yearMonth = "2024-03",
            knownFamilyAccounts = setOf("999888/3030", "777666/5500"),
            boundaryTransactions = listOf(feb29)
        )
        assertEquals(1, leapResult.auditReport.matchedPairs.size)
        assertEquals(1L, leapResult.auditReport.matchedPairs[0].daysApart)
    }

    // -------------------------------------------------------------------------
    // 6. BackupManager: explicit-null matchedTxId, NaN/Infinity strings, v35 guardrails
    // -------------------------------------------------------------------------

    @Test
    fun backupManager_preservesNullMatchedTxId_sanitizesNonFiniteStrings_andClampsV35Fields() {
        val rawBackup = """
            {
              "_format": "martinu-financials-full-backup",
              "_schema": 2,
              "settings": {
                "isSingleHousehold": true,
                "vSalary": "NaN",
                "cpiInflationPct": "Infinity",
                "guardrailsUpperThresholdPct": -50.0,
                "guardrailsFloorPct": 500.0,
                "baristaTransitionEnabled": true,
                "baristaStartYear": 1900,
                "baristaMonthlyIncome": "-Infinity",
                "baristaSavingsMonthly": "NaN",
                "monteCarloSeed": 987654
              },
              "ledgerEntries": [
                {"yearMonth": "", "incVaclav": 100},
                "invalid_element",
                {"yearMonth": "2026-04", "incVaclav": "NaN", "expRent": 20000.0}
              ],
              "importedTransactions": [
                {"yearMonth": "2026-04", "bankName": "CSOB", "date": "2026-04-01", "amount": "NaN"},
                {"id": 5, "yearMonth": "2026-04", "bankName": "CSOB", "date": "2026-04-02", "amount": -450.5, "matchedTxId": null}
              ],
              "preferences": {"dismissed_merchants": "[\"spotify\"]"}
            }
        """.trimIndent()

        val defaults = SettingsEntity(isSingleHousehold = false)
        val restored = BackupManager.deserializeFullBackupFromJson(rawBackup, defaults)
        assertNotNull(restored)
        restored!!

        assertTrue("isSingleHousehold=true must be preserved", restored.settings.isSingleHousehold)
        assertEquals(defaults.vSalary, restored.settings.vSalary, 0.001)
        assertEquals(defaults.cpiInflationPct, restored.settings.cpiInflationPct, 0.001)
        assertTrue(restored.settings.guardrailsUpperThresholdPct in 1.0..100.0)
        assertTrue(restored.settings.guardrailsFloorPct in 10.0..100.0)
        assertTrue(restored.settings.baristaStartYear >= restored.settings.baseYear)
        assertEquals(defaults.baristaMonthlyIncome, restored.settings.baristaMonthlyIncome, 0.001)
        assertEquals(0.0, restored.settings.baristaSavingsMonthly, 0.001)
        assertEquals(987654L, restored.settings.monteCarloSeed)

        assertEquals("Blank/non-object ledger entries must be filtered out", 1, restored.ledgerEntries.size)
        assertEquals(0.0, restored.ledgerEntries[0].incVaclav, 0.001)
        assertEquals(20000.0, restored.ledgerEntries[0].expRent, 0.001)

        assertEquals("Non-finite tx amount row must be skipped", 1, restored.importedTransactions.size)
        assertNull("Explicit JSON null matchedTxId must deserialize as null (not 0L)", restored.importedTransactions[0].matchedTxId)
        assertEquals("[\"spotify\"]", restored.preferences["dismissed_merchants"])
    }

    // -------------------------------------------------------------------------
    // 7. ReportShareHelper: CSV formula-injection neutralization & FileProvider sharing
    // -------------------------------------------------------------------------

    @Test
    fun reportShareHelper_neutralizesFormulaInjection_preservesSignedNumbers_andSharesViaFileProvider() {
        val csvLines = listOf(
            listOf("=CMD|'/C calc'!A0", "+HYPERLINK(\"http://evil\")", "\t=1+1", "@SUM(1,2)", "-1250,50"),
            listOf("-@DANGEROUS", "  +EXEC()", "\r=2+2", "-123.45")
        ).joinToString("\n") { cols ->
            cols.joinToString(",") { ReportShareHelper.escapeCsvCell(it) }
        }
        assertTrue("Neutralized leading '=' formula expected", csvLines.contains("'=CMD"))
        assertTrue("Neutralized leading '+' formula expected", csvLines.contains("'+HYPERLINK"))
        assertTrue("Neutralized leading tab+formula expected", csvLines.contains("'\t=1+1"))
        assertTrue("Neutralized leading '@' formula expected", csvLines.contains("'@SUM"))
        assertTrue("Neutralized '-@' formula expected", csvLines.contains("'-@DANGEROUS"))
        assertTrue("Neutralized whitespace+'+' formula expected", csvLines.contains("'  +EXEC()"))
        assertTrue("Neutralized leading CR formula expected", csvLines.contains("'\r=2+2"))
        // Real negative numeric amounts must remain un-prefixed
        assertTrue("Negative numeric amount must stay '-1250,50'", csvLines.contains("-1250,50"))
        assertFalse(csvLines.contains("'-1250,50"))
        assertEquals("-123.45", ReportShareHelper.escapeCsvCell("-123.45"))

        // FileProvider URI, path-traversal filename sanitization, and cache cleanup.
        // Note: androidx.core.content.FileProvider$SimplePathStrategy.belongsToRoot checks
        // filePath.startsWith(rootPath + '/') which only matches POSIX separators; on Windows JVM
        // Robolectric we still verify path sanitization/containment inside cache/exports and no file:// fallback.
        runCatching {
            val sCacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            sCacheField.isAccessible = true
            (sCacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
        val context = ApplicationProvider.getApplicationContext<Application>()
        val bytes = ReportShareHelper.getCsvBytesWithBom(csvLines)
        val exportDir = File(context.cacheDir, "exports")
        if (File.separatorChar == '/') {
            val intent = ReportShareHelper.createShareCsvIntent(context, bytes, "../../outside/../report?.csv")
            val uri = androidx.core.content.IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_STREAM,
                android.net.Uri::class.java
            )
            assertNotNull(uri)
            assertEquals("content", uri!!.scheme)
            assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        } else {
            val outcome = runCatching {
                ReportShareHelper.createShareCsvIntent(context, bytes, "../../outside/../report?.csv")
            }
            outcome.onSuccess { intent ->
                val uri = androidx.core.content.IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    android.net.Uri::class.java
                )
                assertNotNull(uri)
                assertEquals("content", uri!!.scheme)
            }.onFailure { err ->
                assertTrue(
                    "Unexpected exception type on Windows FileProvider: $err",
                    err is IllegalArgumentException && err.message?.contains("Failed to find configured root") == true
                )
            }
        }
        assertTrue(
            "Sanitized export file must reside strictly inside cache/exports",
            exportDir.listFiles()?.any { it.name == "report_.csv" } == true
        )
        ReportShareHelper.clearExportCache(context)
        assertTrue("Export cache must be cleared", exportDir.listFiles().isNullOrEmpty())
    }

    // -------------------------------------------------------------------------
    // 8. HouseholdDomainModel: dedup and single/dual household balance preservation
    // -------------------------------------------------------------------------

    @Test
    fun householdDomainModel_deduplicatesChildren_andSingleDualFlipPreservesBalances() {
        val base = SettingsEntity(
            isSingleHousehold = false,
            liquidPortfolioCurrent = 300_000.0,
            eLiquidPortfolioCurrent = 120_000.0
        )
        val dualProfile = HouseholdProfile.fromSettings(base)
        assertEquals(420_000.0, dualProfile.liquidPortfolioCurrent, 0.01)

        // Duplicate child_1 IDs must map deterministically without clobbering child_2
        val dupChild = HouseholdMember("child_1", "Child A", HouseholdRole.DEPENDENT_CHILD, 2024)
        val dupChildSecond = HouseholdMember("child_1", "Child B", HouseholdRole.DEPENDENT_CHILD, 2027)
        val edited = dualProfile.copy(children = listOf(dupChild, dupChildSecond))
        val backToDual = edited.toSettings(base)
        assertEquals("Child A", backToDual.child1Name)
        assertEquals("Child B", backToDual.child2Name)
        assertEquals(300_000.0, backToDual.liquidPortfolioCurrent, 0.01)
        assertEquals(120_000.0, backToDual.eLiquidPortfolioCurrent, 0.01)

        // Flip to single household: must preserve base.eLiquidPortfolioCurrent while adjusting primary liquid
        val singleSettings = dualProfile.copy(isSingleHousehold = true, liquidPortfolioCurrent = 250_000.0).toSettings(base)
        assertTrue(singleSettings.isSingleHousehold)
        assertEquals(250_000.0, singleSettings.liquidPortfolioCurrent, 0.01)
        assertEquals(120_000.0, singleSettings.eLiquidPortfolioCurrent, 0.01)
    }

    // -------------------------------------------------------------------------
    // 9. FinancialEngine extreme boundary conditions
    // -------------------------------------------------------------------------

    @Test
    fun financialEngine_extremeAgeAbovePension_zeroAnd25PctCpi_andNegativeSavingsRate_areFinite() {
        val scenarios = listOf(
            SettingsEntity(primaryAge = 67, vStatePensionAge = 67, cpiInflationPct = 0.0),
            SettingsEntity(primaryAge = 72, vStatePensionAge = 65, cpiInflationPct = 25.0, isSingleHousehold = true),
            SettingsEntity(
                vSalary = 25_000.0,
                eStartingSalary = 0.0,
                eParentalAllowanceMonthly = 0.0,
                eIncludeLecturing = false,
                vGiftsMonthly = 0.0,
                portuDcaMonthly = 0.0,
                dpsOwnContributionMonthly = 0.0,
                dipContributionMonthly = 0.0,
                employerRetirementMonthly = 0.0,
                ePortuDcaMonthly = 0.0,
                eDpsOwnContributionMonthly = 0.0,
                eDipContributionMonthly = 0.0,
                eEmployerRetirementMonthly = 0.0,
                rentMonthly = 35_000.0,
                groceriesMonthly = 12_000.0
            )
        )
        for ((idx, s) in scenarios.withIndex()) {
            val state = FinancialEngine.calculate(s, runMonteCarlo = true)
            assertTrue("Scenario $idx netWorthTotal must be finite", state.netWorthTotal.isFinite())
            assertTrue("Scenario $idx savingsRatePct must be finite", state.savingsRatePct.isFinite())
            assertTrue("Scenario $idx fireBaseTargetToday must be finite", state.fireBaseTargetToday.isFinite())
            assertTrue("Scenario $idx dualTrajectory must be non-empty", state.dualTrajectory.isNotEmpty())
            assertTrue("Scenario $idx all trajectory values finite", state.dualTrajectory.all { it.portfolio.isFinite() && it.target.isFinite() })
            assertTrue("Scenario $idx all MC fanPoints valid", state.monteCarlo.fanPoints.all { it.p5 <= it.p50 + 1e-6 && it.p50 <= it.p95 + 1e-6 })
        }
        val deficitState = FinancialEngine.calculate(scenarios[2], runMonteCarlo = false)
        assertTrue(
            "Deficit scenario must reflect expenses exceeding income",
            deficitState.totalLivingCostMonthly > deficitState.currentIncome.totalMonthly
        )
        assertEquals(
            "Savings rate under zero investment surplus must clamp cleanly to 0.0",
            0.0,
            deficitState.savingsRatePct,
            0.001
        )
    }
}
