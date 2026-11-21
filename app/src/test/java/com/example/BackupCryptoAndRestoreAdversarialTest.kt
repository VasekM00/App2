package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActionStateEntity
import com.example.data.AppDatabase
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.ui.tabs.ParsedBackupPreview
import com.example.ui.tabs.parseBackupJsonForPreview
import com.example.util.BackupCrypto
import com.example.util.BackupManager
import com.example.util.UserInvestmentLot
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCryptoAndRestoreAdversarialTest {

    private val testPhrase = "RedTeam-KDF-Passphrase-2026!".toCharArray()

    @Test
    fun malformedEnvelopes_wrongSchema_badBase64_and_iterationDos_areRejected() {
        val validEnvelope = BackupCrypto.encryptJson(
            plaintextJson = """{"_format":"martinu-settings","_schema":1,"baseYear":2026}""",
            passphrase = testPhrase.copyOf(),
            iterations = BackupCrypto.MIN_ITERATIONS
        )

        // Empty / blank / non-JSON
        assertEquals(BackupCrypto.DecryptResult.MalformedEnvelope, BackupCrypto.decryptJson("", testPhrase.copyOf()))
        assertEquals(BackupCrypto.DecryptResult.MalformedEnvelope, BackupCrypto.decryptJson("   \n\t ", testPhrase.copyOf()))
        assertEquals(BackupCrypto.DecryptResult.MalformedEnvelope, BackupCrypto.decryptJson("not-json-at-all", testPhrase.copyOf()))
        assertEquals(BackupCrypto.DecryptResult.WrongPassphraseOrTampered, BackupCrypto.decryptJson(validEnvelope, CharArray(0)))

        // Empty passphrase disallowed on encryption
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encryptJson("{}", CharArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encryptJson("{}", testPhrase.copyOf(), iterations = BackupCrypto.MIN_ITERATIONS - 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encryptJson("{}", testPhrase.copyOf(), iterations = BackupCrypto.MAX_ITERATIONS + 1)
        }

        // Mutated metadata fields
        val badFormat = JSONObject(validEnvelope).put("_format", "other-app").toString()
        val badSchema = JSONObject(validEnvelope).put("_schema", 99).toString()
        val badKdf = JSONObject(validEnvelope).put("kdf", "MD5").toString()
        val badCipher = JSONObject(validEnvelope).put("cipher", "AES/ECB/PKCS5Padding").toString()
        val lowIter = JSONObject(validEnvelope).put("iterations", BackupCrypto.MIN_ITERATIONS - 1).toString()
        val highIter = JSONObject(validEnvelope).put("iterations", BackupCrypto.MAX_ITERATIONS + 1).toString()
        val badBase64 = JSONObject(validEnvelope).put("ciphertext", "!!!not-base64!!!").toString()
        val shortIv = JSONObject(validEnvelope).put("iv", Base64.getEncoder().encodeToString(ByteArray(4))).toString()
        val shortSalt = JSONObject(validEnvelope).put("salt", Base64.getEncoder().encodeToString(ByteArray(4))).toString()
        val truncatedCipher = JSONObject(validEnvelope)
            .put("ciphertext", Base64.getEncoder().encodeToString(ByteArray(16)))
            .toString()

        for (case in listOf(badFormat, badSchema, badKdf, badCipher, lowIter, highIter, badBase64, shortIv, shortSalt, truncatedCipher)) {
            assertEquals(
                "Expected MalformedEnvelope for adversarial envelope",
                BackupCrypto.DecryptResult.MalformedEnvelope,
                BackupCrypto.decryptJson(case, testPhrase.copyOf())
            )
        }
    }

    @Test
    fun singleBitFlipsAcrossSaltIvPayloadAndAuthTag_failGcmAuthentication() {
        val envelope = BackupCrypto.encryptJson(
            plaintextJson = """{"payload":"sensitive-financial-data"}""",
            passphrase = testPhrase.copyOf(),
            iterations = BackupCrypto.MIN_ITERATIONS
        )
        val decoder = Base64.getDecoder()
        val encoder = Base64.getEncoder()

        for (field in listOf("salt", "iv", "ciphertext")) {
            val rawBytes = decoder.decode(JSONObject(envelope).getString(field))
            // Flip bit 0 of first byte
            val firstFlipped = rawBytes.copyOf().apply { this[0] = (this[0].toInt() xor 0x01).toByte() }
            val envFirst = JSONObject(envelope).put(field, encoder.encodeToString(firstFlipped)).toString()
            assertEquals(
                "Expected WrongPassphraseOrTampered on first-byte bit flip for $field",
                BackupCrypto.DecryptResult.WrongPassphraseOrTampered,
                BackupCrypto.decryptJson(envFirst, testPhrase.copyOf())
            )

            // Flip high bit of last byte (tests GCM auth tag directly for ciphertext!)
            val lastIdx = rawBytes.lastIndex
            val lastFlipped = rawBytes.copyOf().apply { this[lastIdx] = (this[lastIdx].toInt() xor 0x80).toByte() }
            val envLast = JSONObject(envelope).put(field, encoder.encodeToString(lastFlipped)).toString()
            assertEquals(
                "Expected WrongPassphraseOrTampered on last-byte bit flip for $field",
                BackupCrypto.DecryptResult.WrongPassphraseOrTampered,
                BackupCrypto.decryptJson(envLast, testPhrase.copyOf())
            )
        }
    }

    @Test
    fun backupManager_rejectsInvalidSchema_and_sanitizesHostileExtremeValues() {
        val fallback = SettingsEntity.freshDefaults()

        assertNull(parseBackupJsonForPreview("", fallback))
        assertNull(parseBackupJsonForPreview("{{bad json", fallback))
        assertNull(parseBackupJsonForPreview("""{"_format":"martinu-settings","_schema":42}""", fallback))
        assertNull(parseBackupJsonForPreview("""{"_format":"martinu-full-backup","_schema":99}""", fallback))
        assertNull(BackupManager.deserializeFullBackupFromJson("""{"_format":"unknown"}""", fallback))

        val hostileSettingsJson = JSONObject().apply {
            put("_format", "martinu-settings")
            put("_schema", 1)
            put("baseYear", 1800)
            put("vSalary", -500_000.0)
            put("rentMonthly", "NaN")
            put("cpiInflationPct", "Infinity")
            put("portfolioNominalReturnPct", 9999.0)
            put("safeWithdrawalRatePct", 0.0)
            put("customExpensesJson", "{not-an-array")
            put("knownFamilyAccountsJson", 12345)
        }.toString()

        val deserialized = BackupManager.deserializeSettingsFromJson(hostileSettingsJson, fallback)
        assertNotNull(deserialized)
        val clean = deserialized!!
        assertEquals(fallback.baseYear, clean.baseYear)
        assertTrue(clean.vSalary >= 0.0)
        assertTrue(clean.rentMonthly.isFinite() && clean.rentMonthly >= 0.0)
        assertTrue(clean.safeWithdrawalRatePct in 0.1..100.0)
        assertTrue(clean.portfolioNominalReturnPct in -99.0..1000.0)
        assertEquals(fallback.customExpensesJson, clean.customExpensesJson)
        assertEquals(fallback.knownFamilyAccountsJson, clean.knownFamilyAccountsJson)
    }

    @Test
    fun encryptedFullBackupRoundTrip_and_atomicRoomRestore_protectsExistingData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val repo = FinancialRepository(
                database = db,
                settingsDao = db.settingsDao(),
                ledgerDao = db.ledgerDao(),
                actionStateDao = db.actionStateDao(),
                importedTransactionDao = db.importedTransactionDao()
            )

            // Seed initial pre-restore database state
            repo.saveSettings(SettingsEntity(baseYear = 2026, vSalary = 45_000.0))
            repo.addLedgerEntry(LedgerEntryEntity(yearMonth = "2026-01", incVaclav = 45_000.0))
            repo.setActionState(2026, "dip_max", true)
            repo.saveImportedTransactions(
                listOf(
                    ImportedBankTransactionEntity(
                        yearMonth = "2026-01",
                        bankName = "MONETA",
                        date = "2026-01-15",
                        amount = -1_500.0,
                        counterpartyName = "Lidl"
                    )
                )
            )

            // Invalid restore preview leaves existing DB completely untouched
            assertNull(parseBackupJsonForPreview("{corrupt-backup", SettingsEntity.freshDefaults()))
            assertEquals(1, repo.getAllLedgerEntriesDirect().size)
            assertEquals(1, repo.getAllImportedTransactionsDirect().size)

            // Settings-Only backup differentiates from Full backup and leaves DB ledger/transactions untouched
            val settingsOnlyJson = BackupManager.serializeSettingsToJson(SettingsEntity(baseYear = 2027, vSalary = 60_000.0))
            val settingsOnlyPreview = parseBackupJsonForPreview(settingsOnlyJson, SettingsEntity.freshDefaults())
            assertTrue(settingsOnlyPreview is ParsedBackupPreview.SettingsOnly)
            repo.saveSettings((settingsOnlyPreview as ParsedBackupPreview.SettingsOnly).restoredSettings)
            assertEquals(1, repo.getAllLedgerEntriesDirect().size)
            assertEquals(1, repo.getAllImportedTransactionsDirect().size)

            // Encrypt full backup payload and verify full round-trip + atomic Room DB replacement
            val payload = BackupManager.FullBackupPayload(
                settings = SettingsEntity(baseYear = 2028, vSalary = 82_000.0, isSingleHousehold = true),
                ledgerEntries = listOf(
                    LedgerEntryEntity(yearMonth = "2028-02", incVaclav = 82_000.0, expRent = 22_000.0)
                ),
                actionStates = listOf(
                    ActionStateEntity(actionKey = "2028_etf", year = 2028, actionId = "etf", isDone = true)
                ),
                importedTransactions = listOf(
                    ImportedBankTransactionEntity(
                        yearMonth = "2028-02",
                        bankName = "CSOB",
                        date = "2028-02-05",
                        amount = -22_000.0,
                        counterpartyName = "Landlord"
                    )
                ),
                preferences = mapOf("theme_mode" to "DARK"),
                investmentLots = listOf(
                    UserInvestmentLot(
                        id = "lot-1",
                        batchName = "VWCE 2024",
                        instrument = "VWCE",
                        purchaseDateStr = "2024-01-10",
                        acquisitionCostCzk = 100_000.0,
                        currentValueCzk = 130_000.0
                    )
                )
            )

            val fullJson = BackupManager.serializeFullBackupToJson(payload)
            val encrypted = BackupCrypto.encryptJson(
                plaintextJson = fullJson,
                passphrase = testPhrase.copyOf(),
                iterations = BackupCrypto.MIN_ITERATIONS
            )
            assertTrue(BackupCrypto.isEncryptedBackup(encrypted))
            assertFalse(encrypted.contains("82000"))

            val decrypted = BackupCrypto.decryptJson(encrypted, testPhrase.copyOf())
            assertTrue(decrypted is BackupCrypto.DecryptResult.Success)
            val preview = parseBackupJsonForPreview((decrypted as BackupCrypto.DecryptResult.Success).plaintext, SettingsEntity.freshDefaults())
            assertTrue(preview is ParsedBackupPreview.Full)
            val restoredPayload = (preview as ParsedBackupPreview.Full).payload

            repo.restoreFullBackupData(
                settings = restoredPayload.settings,
                ledgerEntries = restoredPayload.ledgerEntries,
                actionStates = restoredPayload.actionStates,
                transactions = restoredPayload.importedTransactions
            )

            val dbLedger = repo.getAllLedgerEntriesDirect()
            val dbTxs = repo.getAllImportedTransactionsDirect()
            val dbActions = repo.getAllActionStatesDirect()
            assertEquals(1, dbLedger.size)
            assertEquals("2028-02", dbLedger.first().yearMonth)
            assertEquals(1, dbTxs.size)
            assertEquals("CSOB", dbTxs.first().bankName)
            assertEquals(1, dbActions.size)
            assertEquals("2028_etf", dbActions.first().actionKey)
            assertEquals(1, restoredPayload.investmentLots.size)
            assertEquals("DARK", restoredPayload.preferences["theme_mode"])
        } finally {
            db.close()
        }
    }
}
