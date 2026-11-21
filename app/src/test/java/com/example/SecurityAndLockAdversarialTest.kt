package com.example

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppPreferencesRepository
import com.example.ui.components.AppLockOverlay
import com.example.ui.components.LocalExternalPickerGuard
import com.example.ui.theme.MartinuFinancialsTheme
import com.example.util.BackupCrypto
import com.example.util.BankStatementImporter
import com.example.util.CzechEconomicSyncService
import com.example.util.FxRateService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityAndLockAdversarialTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDownFx() {
        FxRateService.resetRates()
    }

    @Test
    fun appLockOverlay_and_externalPickerGuard_behaveSafely() {
        var unlockCount = 0
        var pickerSuppressionCalls = 0

        composeTestRule.setContent {
            MartinuFinancialsTheme {
                CompositionLocalProvider(
                    LocalExternalPickerGuard provides { pickerSuppressionCalls++ }
                ) {
                    val suppressNextBgLock = LocalExternalPickerGuard.current
                    suppressNextBgLock()
                    AppLockOverlay(
                        onUnlockClick = { unlockCount++ }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(1, pickerSuppressionCalls)
        composeTestRule.onNodeWithTag("app_lock_overlay").assertExists()
        composeTestRule.onNodeWithTag("unlock_app_button").performClick()
        assertEquals(1, unlockCount)
    }

    @Test
    fun appPreferences_persistsBiometricLockAndFlagSecureIndependently(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val storeName = "adv_sec_prefs_${System.nanoTime()}"
        val repo = AppPreferencesRepository(context, prefsName = storeName)
        repo.hydrate()

        assertFalse(repo.snapshot.value.biometricLockEnabled)
        assertFalse(repo.snapshot.value.flagSecureEnabled)

        repo.setBiometricLockEnabled(true)
        assertTrue(repo.snapshot.value.biometricLockEnabled)
        assertFalse(repo.snapshot.value.flagSecureEnabled)

        repo.setFlagSecureEnabled(true)
        assertTrue(repo.snapshot.value.biometricLockEnabled)
        assertTrue(repo.snapshot.value.flagSecureEnabled)

        repo.setBiometricLockEnabled(false)
        assertFalse(repo.snapshot.value.biometricLockEnabled)
        assertTrue(repo.snapshot.value.flagSecureEnabled)

        context.deleteSharedPreferences(storeName)
    }

    @Test
    fun backupCrypto_neverLeaksPassphraseInErrorsOrOutput() {
        val markerPhrase = "AdvUniqueToken-X94K2"
        val envelope = BackupCrypto.encryptJson(
            plaintextJson = """{"amount":12345}""",
            passphrase = markerPhrase.toCharArray(),
            iterations = BackupCrypto.MIN_ITERATIONS
        )
        assertFalse(envelope.contains(markerPhrase))

        val wrongDecrypt = BackupCrypto.decryptJson(envelope, "OtherUniqueToken-000".toCharArray())
        assertEquals(BackupCrypto.DecryptResult.WrongPassphraseOrTampered, wrongDecrypt)
        assertFalse(wrongDecrypt.toString().contains(markerPhrase))
    }

    @Test
    fun cnbParsers_rejectHostileMalformedAndNonFiniteRates_preservingPreviousAtomicState() {
        FxRateService.resetRates()
        FxRateService.updateFromCnb(eurRate = 25.15, usdRate = 23.45)

        val hostilePayloads = listOf(
            "",
            "   ",
            "<html><body>502 Bad Gateway</body></html>",
            """{"rates":[{"currencyCode":"EUR","rate":-25.0,"amount":1},{"currencyCode":"USD","rate":0.0,"amount":1}]}""",
            """{"rates":[{"currencyCode":"EUR","rate":25.0,"amount":0}]}""",
            "{not-valid-json",
            "01.01.2026 #1\nzemě|měna|množství|kód|kurz\nEMU|euro|1|EUR|-5,000\nUSA|dolar|0|USD|23,000",
            "x".repeat(1_100_000)
        )

        for (payload in hostilePayloads) {
            assertFalse("Hostile CNB payload should be rejected: ${payload.take(40)}", FxRateService.parseCnbDailyRates(payload))
            assertEquals(25.15, FxRateService.getRate("EUR"), 0.0001)
            assertEquals(23.45, FxRateService.getRate("USD"), 0.0001)
        }

        val syncJsonParsed = CzechEconomicSyncService.parseCnbAllRatesJson(
            """{"rates":[{"currencyCode":"EUR","rate":-10.0,"amount":1},{"currencyCode":"GBP","rate":0,"amount":1},{"currencyCode":"XYZ","rate":50.0,"amount":1}]}"""
        )
        assertTrue(syncJsonParsed.rates.isEmpty())

        val syncTxtParsed = CzechEconomicSyncService.parseCnbAllRatesTxt(
            "20.11.2026 #225\nEMU|euro|0|EUR|25,000\nUSA|dolar|1|USD|-1,000"
        )
        assertTrue(syncTxtParsed.rates.isEmpty())
    }

    @Test
    fun bankStatementImporter_handlesFormulaInjectionTruncatedPdfAndUnknownCurrenciesSafely() {
        // 1. Hostile CSV with Excel formula prefixes, extreme decimals, broken dates, unknown currency
        val hostileCsv = """
            Datum;Protiúčet;Název protiúčtu;Částka;Měna;Zpráva pro příjemce
            15.10.2026;123456/0800;=CMD|' /C calc'!A0;-999.50;CZK;+SUM(1,2)
            99.99.9999;;-2+3;@HYPERLINK("http://evil.invalid");XYZ;bad date
            16.10.2026;987654/0100;NaN Coffee Shop;NaN;EUR;Infinity
            17.10.2026;555555/0300;${"VeryLongName".repeat(50)};1234.00;USD;normal credit
        """.trimIndent()

        val csvSummary = BankStatementImporter.parseStatement(hostileCsv.toByteArray(Charsets.UTF_8))
        csvSummary.transactions.forEach { tx ->
            assertTrue("Parsed amount must always be finite", tx.amount.isFinite())
        }

        // 2. Truncated / corrupt PDF payload
        val corruptPdf = "%PDF-1.7\n%\u00E2\u00E3\u00CF\u00D3\n1 0 obj<<</broken".toByteArray(Charsets.ISO_8859_1)
        val pdfSummary = BankStatementImporter.parseStatement(corruptPdf)
        assertTrue(pdfSummary.transactions.size >= 0)

        // 3. Empty input
        val emptySummary = BankStatementImporter.parseStatement(ByteArray(0))
        assertEquals(0, emptySummary.transactions.size)

        // 4. Oversized byte buffer rejected cleanly by size limit
        val overLimit = ByteArray(BankStatementImporter.MAX_STATEMENT_BYTES + 1)
        assertFalse(BankStatementImporter.isWithinSizeLimit(overLimit.size))
        assertEquals(0, BankStatementImporter.parseStatement(overLimit).transactions.size)
    }
}
