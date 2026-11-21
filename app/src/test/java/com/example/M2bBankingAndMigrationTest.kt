package com.example

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ImportedBankTransactionEntity
import com.example.domain.parseAccountListJson
import com.example.domain.serializeAccountListJson
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.FxRateService
import com.example.util.PdfTextExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class M2bBankingAndMigrationTest {

    private fun createSyntheticPdf(contentLines: List<String>): ByteArray {
        val streamText = buildString {
            append("BT\n/F1 12 Tf\n50 750 Td\n")
            for (line in contentLines) {
                val safe = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                append("($safe) Tj\nT*\n")
            }
            append("ET\n")
        }
        val deflater = Deflater().apply {
            setInput(streamText.toByteArray(Charsets.UTF_8))
            finish()
        }
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            bos.write(buf, 0, deflater.deflate(buf))
        }
        deflater.end()
        val compressed = bos.toByteArray()
        val header = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>\nendobj\n4 0 obj\n<< /Length ${compressed.size} /Filter /FlateDecode >>\nstream\n".toByteArray(Charsets.US_ASCII)
        val footer = "\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f \ntrailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n100\n%%EOF\n".toByteArray(Charsets.US_ASCII)
        return header + compressed + footer
    }

    @Test
    fun roomV37ToV38MigrationPreservesSettingsAndDefaultsAccountLists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "m2b_v37_v38_${System.currentTimeMillis()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        try {
            val schemaDir = File("app/schemas/com.example.data.AppDatabase").takeIf { it.exists() }
                ?: File("schemas/com.example.data.AppDatabase")
            val schema37Json = File(schemaDir, "37.json").readText()
            val root = org.json.JSONObject(schema37Json)
            val dbObj = root.getJSONObject("database")
            val entities = dbObj.getJSONArray("entities")

            val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            sqlite.version = 37
            for (i in 0 until entities.length()) {
                val ent = entities.getJSONObject(i)
                val tableName = ent.getString("tableName")
                val createSql = ent.getString("createSql").replace("\${TABLE_NAME}", tableName)
                sqlite.execSQL(createSql)
                val indices = ent.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val idxSql = indices.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", tableName)
                        sqlite.execSQL(idxSql)
                    }
                }
            }
            val setupQueries = dbObj.optJSONArray("setupQueries")
            if (setupQueries != null) {
                for (k in 0 until setupQueries.length()) {
                    sqlite.execSQL(setupQueries.getString(k))
                }
            }
            val appSettings = (0 until entities.length())
                .map { entities.getJSONObject(it) }
                .first { it.getString("tableName") == "app_settings" }
            val fields = appSettings.getJSONArray("fields")
            val cols = mutableListOf<String>()
            val vals = mutableListOf<Any>()
            for (i in 0 until fields.length()) {
                val f = fields.getJSONObject(i)
                val col = f.getString("columnName")
                val aff = f.getString("affinity")
                cols.add(col)
                vals.add(
                    when (col) {
                        "id" -> 1
                        "baseYear" -> 2026
                        "primaryName" -> "Petr"
                        "spouseName" -> "Jana"
                        "vSalary" -> 92_000.0
                        else -> when (aff) {
                            "INTEGER" -> 0
                            "REAL" -> 0.0
                            else -> if (col.endsWith("Json")) "[]" else ""
                        }
                    }
                )
            }
            val placeholders = cols.joinToString(",") { "?" }
            sqlite.execSQL(
                "INSERT INTO app_settings (${cols.joinToString(",")}) VALUES ($placeholders)",
                vals.toTypedArray()
            )
            sqlite.close()

            val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(AppDatabase.MIGRATION_37_38)
                .allowMainThreadQueries()
                .build()
            try {
                val migrated = roomDb.settingsDao().getSettingsDirect()
                assertNotNull(migrated)
                assertEquals("Petr", migrated!!.primaryName)
                assertEquals("Jana", migrated.spouseName)
                assertEquals(92_000.0, migrated.vSalary, 0.01)
                assertEquals(emptyList<String>(), parseAccountListJson(migrated.knownFamilyAccountsJson))
                assertEquals(emptyList<String>(), parseAccountListJson(migrated.knownParentalGiftAccountsJson))

                val updated = migrated.copy(
                    knownFamilyAccountsJson = serializeAccountListJson(listOf("123456/0800")),
                    knownParentalGiftAccountsJson = serializeAccountListJson(listOf("999888/0300"))
                )
                roomDb.settingsDao().saveSettings(updated)
                val reloaded = roomDb.settingsDao().getSettingsDirect()!!
                assertEquals(listOf("123456/0800"), parseAccountListJson(reloaded.knownFamilyAccountsJson))
                assertEquals(listOf("999888/0300"), parseAccountListJson(reloaded.knownParentalGiftAccountsJson))
            } finally {
                roomDb.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun revolutNonCompletedStatesAndCsvAccountColumnPriorityAndMixedCurrencyBalance() {
        FxRateService.resetRates()
        val revolutCsv = """
            Type,Product,Started Date,Completed Date,Description,Amount,Fee,Currency,State,Balance
            CARD_PAYMENT,Current,2026-09-10 10:00:00,2026-09-10 10:05:00,Albert,-500.00,0.00,CZK,COMPLETED,9500.00
            CARD_PAYMENT,Current,2026-09-11 10:00:00,,Lidl,-800.00,0.00,CZK,REVERTED,9500.00
            CARD_PAYMENT,Current,2026-09-12 10:00:00,,Alza,-1200.00,0.00,CZK,DECLINED,9500.00
            CARD_PAYMENT,Current,2026-09-13 10:00:00,,Billa,-300.00,0.00,CZK,FAILED,9500.00
            CARD_PAYMENT,Current,2026-09-14 10:00:00,,Rohlik,-400.00,0.00,CZK,PENDING,9500.00
        """.trimIndent()
        val revSummary = BankStatementImporter.parseStatement(revolutCsv.toByteArray(Charsets.UTF_8))
        assertEquals(BankType.REVOLUT, revSummary.detectedBank)
        assertEquals(1, revSummary.transactions.size)
        assertEquals(500.0, revSummary.expGroceries, 0.01)

        // Mixed-currency row on a Czech bank CSV must NOT multiply CZK Zůstatek by 25.2x!
        val csobMixedCsv = """
            Československá obchodní banka, a. s.
            Datum zaúčtování;Účet příjemce;Protiúčet;Název protiúčtu;Částka;Měna;Zůstatek po transakci
            15.09.2026;111111/0300;222222/0800;GitHub Inc;-20,00;EUR;45 000,00
        """.trimIndent()
        val csobSummary = BankStatementImporter.parseStatement(csobMixedCsv.toByteArray(Charsets.UTF_8))
        assertEquals(1, csobSummary.transactions.size)
        assertEquals("222222/0800", csobSummary.transactions[0].counterpartyAccount)
        assertEquals(-20.0 * 25.20, csobSummary.transactions[0].amount, 0.01)
        assertEquals(45_000.0, csobSummary.monthEndBalance ?: 0.0, 0.01)
    }

    @Test
    fun csobFullDatePdfAndCurrenciesAndPrikazceAndGiftPrecedence() {
        FxRateService.resetRates()
        val pdfBytes = createSyntheticPdf(
            listOf(
                "Československá obchodní banka, a. s. - VÝPIS Z ÚČTU",
                "12.10.2026 999888/0300 Zamestnavatel s.r.o. 55 000,00 CZK Mzda za 09/2026",
                "14.10.2026 333444/0100 Albert 1 850,00 CZK Odchozí platba potraviny",
                "Zůstatek ke konci období: 80 000,00 CZK"
            )
        )
        assertTrue(PdfTextExtractor.isPdf(pdfBytes))
        val csobPdf = BankStatementImporter.parseStatement(pdfBytes)
        assertEquals(BankType.CSOB, csobPdf.detectedBank)
        assertEquals(2, csobPdf.transactions.size)
        assertEquals(55_000.0, csobPdf.incVaclav, 0.01)
        assertEquals(80_000.0, csobPdf.monthEndBalance ?: 0.0, 0.01)

        // Foreign currencies GBP / PLN / CHF / HUF (Ft)
        assertEquals(100.0 * FxRateService.getRate("GBP"), BankStatementImporter.parseCzechAmount("100,00 GBP"), 0.01)
        assertEquals(200.0 * FxRateService.getRate("PLN"), BankStatementImporter.parseCzechAmount("200,00 zł"), 0.01)
        assertEquals(50.0 * FxRateService.getRate("CHF"), BankStatementImporter.parseCzechAmount("50,00 CHF"), 0.01)
        assertEquals(1500.0 * FxRateService.getRate("HUF"), BankStatementImporter.parseCzechAmount("1 500 Ft"), 0.01)

        // Positive inflow with incidental "nakup"/"vyber" word must NOT flip negative
        val inflowAmounts = BankStatementImporter.extractAllAmountsPublic("Příchozí úhrada za společný nákup 500,00 Kč")
        assertEquals(500.0, inflowAmounts.first(), 0.01)

        // Parental gift account precedence beats family account & is never netted
        val giftCat = BankStatementImporter.categorizeTransaction(
            amount = 15_000.0,
            counterpartyAcc = "999777/0800",
            counterpartyName = "Příkazce: Rodiče",
            message = "Převod",
            vs = "",
            familyAccounts = setOf("999777/0800"),
            knownParentalGiftAccounts = setOf("999777/0800")
        )
        assertEquals(BankTransactionType.FAMILY_GIFT, giftCat)
    }

    @Test
    fun reconciliationScale5000TransactionsCompletesUnderTwoSeconds() {
        val txs = (1..5000).map { i ->
            val bank = if (i % 2 == 0) "MONETA" else "CSOB"
            val isPairLeg = i <= 400
            val amount = when {
                isPairLeg && i % 2 == 1 -> -(1000.0 + (i / 2) * 10.0)
                isPairLeg && i % 2 == 0 -> 1000.0 + ((i - 1) / 2) * 10.0
                else -> -((i % 200) + 50.0)
            }
            ImportedBankTransactionEntity(
                id = i.toLong(),
                yearMonth = "2026-09",
                bankName = bank,
                date = "2026-09-${String.format("%02d", (i % 28) + 1)}",
                amount = amount,
                counterpartyAccount = if (isPairLeg) "555555/0300" else "999${i}/0800",
                counterpartyName = if (isPairLeg) "Transfer" else "Merchant $i",
                category = BankTransactionType.UNCATEGORIZED.name
            )
        }

        val startMs = System.currentTimeMillis()
        val (reconciled, report) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = txs,
            yearMonth = "2026-09",
            knownFamilyAccounts = setOf("555555/0300"),
            householdNames = listOf("Petr Novak")
        )
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals(5000, reconciled.size)
        assertTrue("Expected pairs reconciled, got ${report.matchedPairs.size}", report.matchedPairs.isNotEmpty())
        assertTrue("5,000-tx reconciliation took ${elapsedMs}ms (must be < 2000ms)", elapsedMs < 2000L)
    }
}
