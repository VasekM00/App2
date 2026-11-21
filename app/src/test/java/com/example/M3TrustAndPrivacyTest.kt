package com.example

import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.ui.components.normalizeAndValidateCzechAccount
import com.example.ui.tabs.ParsedBackupPreview
import com.example.ui.tabs.parseBackupJsonForPreview
import com.example.util.BackupCrypto
import com.example.util.BackupManager
import com.example.util.CzechEconomicSyncService
import com.example.util.FxRateService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class M3TrustAndPrivacyTest {

    @Test
    fun backupCrypto_roundTrip_and_wrongPassphraseRejected() {
        val samplePayload = BackupManager.serializeFullBackupToJson(
            BackupManager.FullBackupPayload(
                settings = SettingsEntity(baseYear = 2026),
                ledgerEntries = emptyList(),
                actionStates = emptyList(),
                importedTransactions = emptyList(),
                preferences = mapOf("theme_mode" to "DARK"),
                investmentLots = emptyList()
            )
        )
        val pass = "correct-horse-battery-staple".toCharArray()
        val envelope = BackupCrypto.encryptJson(samplePayload, pass, iterations = BackupCrypto.MIN_ITERATIONS)

        assertTrue(BackupCrypto.isEncryptedBackup(envelope))
        assertFalse(BackupCrypto.isEncryptedBackup(samplePayload))

        val ok = BackupCrypto.decryptJson(envelope, "correct-horse-battery-staple".toCharArray())
        assertTrue(ok is BackupCrypto.DecryptResult.Success)
        assertEquals(samplePayload, (ok as BackupCrypto.DecryptResult.Success).plaintext)

        val wrong = BackupCrypto.decryptJson(envelope, "wrong-passphrase".toCharArray())
        assertEquals(BackupCrypto.DecryptResult.WrongPassphraseOrTampered, wrong)
    }

    @Test
    fun backupCrypto_tamperedCiphertext_iv_salt_orMalformedEnvelope_failsCleanly() {
        val envelope = BackupCrypto.encryptJson(
            plaintextJson = "{\"hello\":\"world\"}",
            passphrase = "strong-test-passphrase".toCharArray(),
            iterations = BackupCrypto.MIN_ITERATIONS
        )
        val obj = JSONObject(envelope)

        // Flip 1 byte in ciphertext
        val cBytes = Base64.getDecoder().decode(obj.getString("ciphertext"))
        cBytes[0] = (cBytes[0].toInt() xor 0xFF).toByte()
        val tamperedCipher = JSONObject(envelope).put("ciphertext", Base64.getEncoder().encodeToString(cBytes)).toString()
        assertEquals(
            BackupCrypto.DecryptResult.WrongPassphraseOrTampered,
            BackupCrypto.decryptJson(tamperedCipher, "strong-test-passphrase".toCharArray())
        )

        // Flip 1 byte in IV
        val ivBytes = Base64.getDecoder().decode(obj.getString("iv"))
        ivBytes[0] = (ivBytes[0].toInt() xor 0x01).toByte()
        val tamperedIv = JSONObject(envelope).put("iv", Base64.getEncoder().encodeToString(ivBytes)).toString()
        assertEquals(
            BackupCrypto.DecryptResult.WrongPassphraseOrTampered,
            BackupCrypto.decryptJson(tamperedIv, "strong-test-passphrase".toCharArray())
        )

        // Excessive iteration count outside bounds -> MalformedEnvelope (DoS guard)
        val hugeIter = JSONObject(envelope).put("iterations", 99_999_999).toString()
        assertEquals(
            BackupCrypto.DecryptResult.MalformedEnvelope,
            BackupCrypto.decryptJson(hugeIter, "strong-test-passphrase".toCharArray())
        )
    }

    @Test
    fun parseBackupJsonForPreview_distinguishesSettingsOnlyFromFullBackup() {
        val base = SettingsEntity(baseYear = 2026)
        val settingsOnlyJson = BackupManager.serializeSettingsToJson(base.copy(baseYear = 2027))
        val previewSettings = parseBackupJsonForPreview(settingsOnlyJson, base)
        assertTrue(previewSettings is ParsedBackupPreview.SettingsOnly)
        assertNull(BackupManager.deserializeFullBackupFromJson(settingsOnlyJson, base))

        val fullJson = BackupManager.serializeFullBackupToJson(
            BackupManager.FullBackupPayload(settings = base.copy(baseYear = 2028))
        )
        val previewFull = parseBackupJsonForPreview(fullJson, base)
        assertTrue(previewFull is ParsedBackupPreview.Full)
        assertEquals(2028, (previewFull as ParsedBackupPreview.Full).payload.settings.baseYear)
    }

    @Test
    fun normalizeAndValidateCzechAccount_handlesDomesticAndIban() {
        assertEquals("123456-9876543210/0800", normalizeAndValidateCzechAccount(" 123456 - 9876543210/0800 "))
        assertNull("Spaces inside prefix-number/bank should require valid format without garbage", normalizeAndValidateCzechAccount("invalid-acct"))
        assertEquals("123456789/0100", normalizeAndValidateCzechAccount("123456789/0100"))
        assertEquals("CZ6508000000192000145399", normalizeAndValidateCzechAccount("cz65 0800 0000 1920 0014 5399"))
        assertNull(normalizeAndValidateCzechAccount("CZ65"))
    }

    @Test
    fun cnbSync_parsesAll6Currencies_including100Huf_andOfflineDefaultsAreHonest() {
        val defaults = CzechRegulatoryData()
        assertFalse("Offline default CzechRegulatoryData must report isFxLive = false", defaults.isFxLive)
        assertTrue(defaults.sourceName.contains("Bundled", ignoreCase = true))

        val jsonFeed = """
            {
              "rates": [
                {"validFor":"2026-11-20","amount":1,"currencyCode":"EUR","rate":25.35},
                {"validFor":"2026-11-20","amount":1,"currencyCode":"USD","rate":23.95},
                {"validFor":"2026-11-20","amount":1,"currencyCode":"GBP","rate":30.15},
                {"validFor":"2026-11-20","amount":1,"currencyCode":"PLN","rate":5.88},
                {"validFor":"2026-11-20","amount":1,"currencyCode":"CHF","rate":27.10},
                {"validFor":"2026-11-20","amount":100,"currencyCode":"HUF","rate":6.25}
              ]
            }
        """.trimIndent()
        val parsedJson = CzechEconomicSyncService.parseCnbAllRatesJson(jsonFeed)
        assertEquals("2026-11-20", parsedJson.rateDate)
        assertEquals(25.35, parsedJson.rates["EUR"] ?: 0.0, 0.0001)
        assertEquals(23.95, parsedJson.rates["USD"] ?: 0.0, 0.0001)
        assertEquals(30.15, parsedJson.rates["GBP"] ?: 0.0, 0.0001)
        assertEquals(5.88, parsedJson.rates["PLN"] ?: 0.0, 0.0001)
        assertEquals(27.10, parsedJson.rates["CHF"] ?: 0.0, 0.0001)
        assertEquals(0.0625, parsedJson.rates["HUF"] ?: 0.0, 0.0001)

        val txtFeed = """
            20.11.2026 #225
            země|měna|množství|kód|kurz
            EMU|euro|1|EUR|25,400
            USA|dolar|1|USD|24,010
            Velká Británie|libra|1|GBP|30,200
            Polsko|zlotý|1|PLN|5,920
            Švýcarsko|frank|1|CHF|27,250
            Maďarsko|forint|100|HUF|6,400
        """.trimIndent()
        val parsedTxt = CzechEconomicSyncService.parseCnbAllRatesTxt(txtFeed)
        assertEquals("20.11.2026", parsedTxt.rateDate)
        assertEquals(25.40, parsedTxt.rates["EUR"] ?: 0.0, 0.0001)
        assertEquals(0.064, parsedTxt.rates["HUF"] ?: 0.0, 0.0001)

        FxRateService.resetRates()
        FxRateService.updateRates(parsedTxt.rates)
        assertEquals(30.20, FxRateService.getRate("GBP"), 0.0001)
        assertEquals(0.064, FxRateService.getRate("HUF"), 0.0001)
        FxRateService.resetRates()
    }
}
