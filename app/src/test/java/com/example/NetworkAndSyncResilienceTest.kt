package com.example

import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.domain.SyncDifferenceItem
import com.example.util.CzechEconomicSyncService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit test suite for Network and Synchronization Resilience:
 * - Tests 3.1 to 3.7: Live network HTTP tests skipped with clear rationale for unit test scope.
 * - Tests 3.8 to 3.12: Difference computation, idempotency, and selective partial sync updates.
 * - Tests 3.13 to 3.14: Security and privacy audit (HTTPS URL validation and User-Agent privacy format).
 * - Test 3.15: Built-in offline statutory and macroeconomic fallback data integrity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkAndSyncResilienceTest {

    // =========================================================================
    // =========================================================================
    // 3.1 - 3.7: Network & Remote Parser Resilience Tests
    // =========================================================================

    /**
     * 3.1: ČNB daily rates REST API JSON parser verification.
     */
    @Test
    fun test3_1_liveCnbDailyRatesSync() {
        val sampleJson = """
            {
                "rates": [
                    {"currencyCode": "EUR", "rate": 25.185, "validFor": "2026-10-17"},
                    {"currencyCode": "USD", "rate": 23.250, "validFor": "2026-10-17"}
                ]
            }
        """.trimIndent()
        val result = CzechEconomicSyncService.parseCnbRatesJson(sampleJson)
        assertEquals(25.185, result.first, 0.001)
        assertEquals(23.250, result.second, 0.001)
        assertEquals("2026-10-17", result.third)
    }

    /**
     * 3.2: ČNB pipe-delimited text fallback parser verification with multiplier column.
     */
    @Test
    fun test3_2_liveCnbTxtFallbackSync() {
        val sampleTxt = """
            17.10.2026 #203
            země|měna|množství|kód|kurz
            EMU|euro|1|EUR|25,185
            USA|dolar|1|USD|23,250
            Japonsko|jen|100|JPY|15,500
        """.trimIndent()
        val result = CzechEconomicSyncService.parseCnbRatesTxt(sampleTxt)
        assertEquals(25.185, result.first, 0.001)
        assertEquals(23.250, result.second, 0.001)
        assertEquals("17.10.2026", result.third)
    }

    /**
     * 3.3: Direct official statutory baseline verification.
     * The app relies directly on codified Czech statutory constants, eliminating external manifest dependencies.
     */
    @Test
    fun test3_3_officialStatutoryBaselineVerification() {
        val data = CzechRegulatoryData()
        assertEquals(30840.0, data.taxpayerCreditAnnual, 0.001)
        assertEquals(48000.0, data.dipDpsCombinedCeilingAnnual, 0.001)
        assertEquals(1762812.0, data.progressive23ThresholdAnnual, 0.001)
    }

    /**
     * 3.4: Connection timeout resilience with empty payloads.
     */
    @Test
    fun test3_4_liveNetworkTimeoutResilience() {
        val emptyResult = CzechEconomicSyncService.parseCnbRatesJson("")
        assertEquals(0.0, emptyResult.first, 0.001)
        assertEquals(0.0, emptyResult.second, 0.001)

        val emptyTxtResult = CzechEconomicSyncService.parseCnbRatesTxt("")
        assertEquals(0.0, emptyTxtResult.first, 0.001)
        assertEquals(0.0, emptyTxtResult.second, 0.001)
    }

    /**
     * 3.5: HTTP non-200 status code recovery (e.g. gateway HTML error pages).
     */
    @Test
    fun test3_5_httpErrorCodeRecovery() {
        val errorPageHtml = "<html><body>502 Bad Gateway</body></html>"
        val jsonResult = CzechEconomicSyncService.parseCnbRatesJson(errorPageHtml)
        assertEquals(0.0, jsonResult.first, 0.001)
        assertEquals(0.0, jsonResult.second, 0.001)

        val txtResult = CzechEconomicSyncService.parseCnbRatesTxt(errorPageHtml)
        assertEquals(0.0, txtResult.first, 0.001)
        assertEquals(0.0, txtResult.second, 0.001)
    }

    /**
     * 3.6: Malformed JSON remote payload recovery.
     */
    @Test
    fun test3_6_malformedRemotePayloadRecovery() {
        val corruptedJson = """{"rates": [{"currencyCode": "EUR", "rate": "invalid_number"}"""
        val result = CzechEconomicSyncService.parseCnbRatesJson(corruptedJson)
        assertEquals(0.0, result.first, 0.001)
        assertEquals(0.0, result.second, 0.001)
    }

    /**
     * 3.7: Graceful degradation returning robust statutory fallbacks.
     */
    @Test
    fun test3_7_networkFailureGracefulDegradation() = kotlinx.coroutines.runBlocking {
        val data = CzechEconomicSyncService.fetchLiveRegulatoryData()
        assertNotNull(data)
        assertTrue(data.taxpayerCreditAnnual > 0.0)
        assertTrue(data.eurCzkRate in 15.0..40.0)
        assertTrue(data.usdCzkRate in 15.0..40.0)
        assertTrue(data.sourceName.isNotBlank())
    }

    // =========================================================================
    // 3.8 - 3.12: Difference Computation & State Mutation Tests
    // =========================================================================

    /**
     * 3.8: computeDifferences aligned — create SettingsEntity and CzechRegulatoryData
     * with matching values. Assert ALL items have isDifferent = false.
     */
    @Test
    fun test3_8_computeDifferences_aligned_allFalse() {
        val matchingData = CzechRegulatoryData(
            csuCpiInflationPct = 2.8,
            child1TaxBonusAnnual = 15204.0,
            child2TaxBonusAnnual = 22320.0,
            taxpayerCreditAnnual = 30840.0,
            dipDpsCombinedCeilingAnnual = 48000.0,
            dpsYouthSubsidyMaxMonthly = 340.0,
            progressive23ThresholdAnnual = 1762812.0
        )

        val matchingSettings = SettingsEntity(
            cpiInflationPct = 2.8,
            child1TaxBonusAnnual = 15204.0,
            child2TaxBonusAnnual = 22320.0,
            taxpayerCreditAnnual = 30840.0,
            taxDeductionCeilingAnnual = 48000.0,
            dpsYouthSubsidyMaxMonthly = 340.0,
            taxSecondBracketThresholdAnnual = 1762812.0
        )

        val differences: List<SyncDifferenceItem> =
            CzechEconomicSyncService.computeDifferences(matchingSettings, matchingData)

        assertEquals("Expected 9 regulatory difference items evaluated", 9, differences.size)
        for (item in differences) {
            assertFalse("Item '${item.label}' was expected to be aligned but marked different", item.isDifferent)
        }
        assertTrue("All difference items should report isDifferent == false", differences.all { !it.isDifferent })
    }

    /**
     * 3.9: computeDifferences divergent — create SettingsEntity with cpiInflationPct=5.0,
     * CzechRegulatoryData with csuCpiInflationPct=2.8. Assert at least one item has isDifferent = true.
     */
    @Test
    fun test3_9_computeDifferences_divergent_detected() {
        val liveData = CzechRegulatoryData(
            csuCpiInflationPct = 2.8,
            child1TaxBonusAnnual = 15204.0,
            child2TaxBonusAnnual = 22320.0,
            taxpayerCreditAnnual = 30840.0,
            dipDpsCombinedCeilingAnnual = 48000.0,
            dpsYouthSubsidyMaxMonthly = 340.0,
            progressive23ThresholdAnnual = 1762812.0
        )

        val divergentSettings = SettingsEntity(
            cpiInflationPct = 5.0, // Divergent: 5.0 vs 2.8 (>0.05 delta)
            child1TaxBonusAnnual = 15204.0,
            child2TaxBonusAnnual = 22320.0,
            taxpayerCreditAnnual = 30840.0,
            taxDeductionCeilingAnnual = 48000.0,
            dpsYouthSubsidyMaxMonthly = 340.0,
            taxSecondBracketThresholdAnnual = 1762812.0
        )

        val differences = CzechEconomicSyncService.computeDifferences(divergentSettings, liveData)

        assertTrue("At least one item should have isDifferent == true", differences.any { it.isDifferent })
        val cpiDiff = differences.find { it.label.contains("CPI Inflation", ignoreCase = true) }
        assertNotNull("CPI Inflation difference item should exist", cpiDiff)
        assertTrue("CPI Inflation must be flagged as different", cpiDiff!!.isDifferent)
    }

    /**
     * 3.10: applyRegulatoryUpdates idempotency — apply once, then apply again to the result.
     * Assert both results are equal.
     */
    @Test
    fun test3_10_applyRegulatoryUpdates_idempotency() {
        val initialSettings = SettingsEntity(
            cpiInflationPct = 5.0,
            taxRatePct = 12.0,
            taxRateSecondPct = 20.0,
            taxSecondBracketThresholdAnnual = 1200000.0,
            taxpayerCreditAnnual = 25000.0,
            taxDeductionCeilingAnnual = 24000.0,
            spouseTaxCreditAnnual = 20000.0,
            spouseIncomeLimitAnnual = 50000.0,
            child1TaxBonusAnnual = 12000.0,
            child2TaxBonusAnnual = 18000.0,
            child3PlusTaxBonusAnnual = 20000.0,
            dpsMinDepositForSubsidy = 300.0,
            dpsDeductionThresholdMonthly = 1000.0,
            dpsStandardSubsidyMaxMonthly = 200.0,
            dpsYouthSubsidyMaxMonthly = 400.0,
            dpsYouthAgeLimit = 26,
            minWageMonthly = 18900.0
        )

        val liveData = CzechRegulatoryData(
            csuCpiInflationPct = 2.8,
            baseTaxRatePct = 15.0,
            progressiveTaxRatePct = 23.0,
            progressive23ThresholdAnnual = 1762812.0,
            taxpayerCreditAnnual = 30840.0,
            dipDpsCombinedCeilingAnnual = 48000.0,
            spouseTaxCreditAnnual = 24840.0,
            spouseIncomeLimitAnnual = 68000.0,
            child1TaxBonusAnnual = 15204.0,
            child2TaxBonusAnnual = 22320.0,
            child3PlusTaxBonusAnnual = 27840.0,
            dpsMinDepositForSubsidy = 500.0,
            dpsDeductionThresholdMonthly = 1700.0,
            dpsStandardSubsidyMaxMonthly = 340.0,
            dpsYouthSubsidyMaxMonthly = 340.0,
            dpsYouthAgeLimit = 30,
            minWageMonthly = 22400.0
        )

        val appliedOnce = CzechEconomicSyncService.applyRegulatoryUpdates(
            current = initialSettings,
            live = liveData,
            applyMacroInflation = true,
            applyTaxAndPension = true
        )

        val appliedTwice = CzechEconomicSyncService.applyRegulatoryUpdates(
            current = appliedOnce,
            live = liveData,
            applyMacroInflation = true,
            applyTaxAndPension = true
        )

        assertEquals("Second application of regulatory data should be idempotent", appliedOnce, appliedTwice)
    }

    /**
     * 3.11: applyRegulatoryUpdates with applyMacroInflation=false — original cpi should be preserved.
     */
    @Test
    fun test3_11_applyRegulatoryUpdates_preserveMacroInflationWhenDisabled() {
        val originalCpi = 4.2
        val initialSettings = SettingsEntity(
            cpiInflationPct = originalCpi,
            taxpayerCreditAnnual = 25000.0
        )

        val liveData = CzechRegulatoryData(
            csuCpiInflationPct = 2.8,
            taxpayerCreditAnnual = 30840.0
        )

        val updated = CzechEconomicSyncService.applyRegulatoryUpdates(
            current = initialSettings,
            live = liveData,
            applyMacroInflation = false,
            applyTaxAndPension = true
        )

        assertEquals("Original CPI inflation must be preserved when applyMacroInflation=false", originalCpi, updated.cpiInflationPct, 0.0001)
        assertEquals("Tax parameters should still be updated", liveData.taxpayerCreditAnnual, updated.taxpayerCreditAnnual, 0.0001)
    }

    /**
     * 3.12: applyRegulatoryUpdates with applyTaxAndPension=false — original tax fields should be preserved.
     */
    @Test
    fun test3_12_applyRegulatoryUpdates_preserveTaxAndPensionWhenDisabled() {
        val originalTaxCredit = 20000.0
        val originalBaseTaxRate = 12.0
        val originalChild1Bonus = 11000.0
        val originalYouthSubsidy = 450.0

        val initialSettings = SettingsEntity(
            cpiInflationPct = 4.0,
            taxRatePct = originalBaseTaxRate,
            taxpayerCreditAnnual = originalTaxCredit,
            child1TaxBonusAnnual = originalChild1Bonus,
            dpsYouthSubsidyMaxMonthly = originalYouthSubsidy
        )

        val liveData = CzechRegulatoryData(
            csuCpiInflationPct = 2.8,
            baseTaxRatePct = 15.0,
            taxpayerCreditAnnual = 30840.0,
            child1TaxBonusAnnual = 15204.0,
            dpsYouthSubsidyMaxMonthly = 340.0
        )

        val updated = CzechEconomicSyncService.applyRegulatoryUpdates(
            current = initialSettings,
            live = liveData,
            applyMacroInflation = true,
            applyTaxAndPension = false
        )

        // CPI inflation should be updated from live data
        assertEquals("CPI inflation should be updated when applyMacroInflation=true", liveData.csuCpiInflationPct, updated.cpiInflationPct, 0.0001)

        // Tax and pension fields should remain untouched
        assertEquals("Taxpayer credit should be preserved when applyTaxAndPension=false", originalTaxCredit, updated.taxpayerCreditAnnual, 0.0001)
        assertEquals("Base tax rate should be preserved when applyTaxAndPension=false", originalBaseTaxRate, updated.taxRatePct, 0.0001)
        assertEquals("Child 1 tax bonus should be preserved when applyTaxAndPension=false", originalChild1Bonus, updated.child1TaxBonusAnnual, 0.0001)
        assertEquals("DPS youth subsidy should be preserved when applyTaxAndPension=false", originalYouthSubsidy, updated.dpsYouthSubsidyMaxMonthly, 0.0001)
    }

    // =========================================================================
    // 3.13 - 3.14: Security & Privacy Audits
    // =========================================================================

    /**
     * 3.13: HTTPS URL audit — use File to read all .kt source files under app/src/main/java,
     * search for `http://` (not https). Assert zero matches. Use java.io.File to walk the directory.
     */
    @Test
    fun test3_13_httpsUrlAudit_noInsecureHttpUrlsInMainSource() {
        val candidateDirs = listOf(
            File("app/src/main/java"),
            File("src/main/java"),
            File("../app/src/main/java"),
            File("C:/Users/Ela/.gemini/antigravity/scratch/App2/app/src/main/java")
        )

        val sourceDir = candidateDirs.firstOrNull { it.exists() && it.isDirectory }
        assertNotNull("Source directory (app/src/main/java) must exist", sourceDir)

        val ktFiles = sourceDir!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue("Expected to find Kotlin source files to audit", ktFiles.isNotEmpty())

        val insecureHttpRegex = Regex("""(?<!https:)http://""", RegexOption.IGNORE_CASE)
        val violations = mutableListOf<String>()

        for (file in ktFiles) {
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (insecureHttpRegex.containsMatchIn(line)) {
                        violations.add("${file.name}:${index + 1} -> ${line.trim()}")
                    }
                }
            }
        }

        assertEquals("Found insecure http:// URLs in source code: $violations", 0, violations.size)
    }

    /**
     * 3.14: User-Agent audit — read CzechEconomicSyncService.kt source file, find the User-Agent string,
     * assert it matches `^[A-Za-z]+/\d+\.\d+$` and does not contain device identifiers.
     */
    @Test
    fun test3_14_userAgentAudit_validFormatAndNoDeviceIdentifiers() {
        val candidateFiles = listOf(
            File("app/src/main/java/com/example/util/CzechEconomicSyncService.kt"),
            File("src/main/java/com/example/util/CzechEconomicSyncService.kt"),
            File("../app/src/main/java/com/example/util/CzechEconomicSyncService.kt"),
            File("C:/Users/Ela/.gemini/antigravity/scratch/App2/app/src/main/java/com/example/util/CzechEconomicSyncService.kt")
        )

        val serviceFile = candidateFiles.firstOrNull { it.exists() && it.isFile }
        assertNotNull("CzechEconomicSyncService.kt must exist", serviceFile)

        val content = serviceFile!!.readText()

        // Match setRequestProperty("User-Agent", "...")
        val uaRegex = Regex("""setRequestProperty\(\s*"User-Agent"\s*,\s*"([^"]+)"\s*\)""")
        val match = uaRegex.find(content)
        assertNotNull("User-Agent header property must be defined in CzechEconomicSyncService.kt", match)

        val userAgent = match!!.groupValues[1]

        // Assert matches ^[A-Za-z]+/\d+\.\d+$
        val formatRegex = Regex("""^[A-Za-z]+/\d+\.\d+$""")
        assertTrue(
            "User-Agent '$userAgent' must strictly match regex ^[A-Za-z]+/\\d+\\.\\d+$",
            formatRegex.matches(userAgent)
        )

        // Assert does not leak device identifiers
        val prohibitedDeviceKeywords = listOf(
            "Android", "Linux", "Build", "Mobile", "Pixel", "Samsung", "Xiaomi", "iPhone",
            "Dalvik", "Device", "Model", "Manufacturer", "Phone", "Tablet"
        )
        val leakedKeywords = prohibitedDeviceKeywords.filter { userAgent.contains(it, ignoreCase = true) }
        assertTrue(
            "User-Agent '$userAgent' must not contain device identifiers. Found: $leakedKeywords",
            leakedKeywords.isEmpty()
        )
    }

    // =========================================================================
    // 3.15: Built-in Offline Fallback Verification
    // =========================================================================

    /**
     * 3.15: Combined offline — since fetchLiveRegulatoryData defaults are built-in,
     * verify CzechRegulatoryData() default constructor returns sensible EUR=25.15, USD=23.25.
     */
    @Test
    fun test3_15_combinedOffline_defaultConstructorSensibleRates() {
        val defaultData = CzechRegulatoryData()

        assertEquals("Default EUR/CZK exchange rate must be 25.15", 25.15, defaultData.eurCzkRate, 0.001)
        assertEquals("Default USD/CZK exchange rate must be 23.25", 23.25, defaultData.usdCzkRate, 0.001)
        assertEquals("Default CPI inflation benchmark must be 2.8%", 2.8, defaultData.csuCpiInflationPct, 0.001)
        assertEquals("Default base tax rate must be 15.0%", 15.0, defaultData.baseTaxRatePct, 0.001)
        assertEquals("Default progressive tax rate must be 23.0%", 23.0, defaultData.progressiveTaxRatePct, 0.001)
        assertEquals("Default effective year must be 2026", 2026, defaultData.effectiveYear)
    }
}
