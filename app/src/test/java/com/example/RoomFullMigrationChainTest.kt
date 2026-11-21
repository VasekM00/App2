package com.example

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * End-to-end Room schema & migration verification across exported schema snapshots (16..34 -> 35),
 * incremental 22 -> 23 -> 24 index ordering, skip migrations 16..19 -> 21, and destructive v12 fallback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomFullMigrationChainTest {

    private fun getSchemaDir(): File {
        val candidates = listOf(
            File("app/schemas/com.example.data.AppDatabase"),
            File("schemas/com.example.data.AppDatabase")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate Room schemas directory from ${File("").absolutePath}")
    }

    private fun loadSchemaJson(version: Int): JSONObject {
        val file = File(getSchemaDir(), "$version.json")
        assertTrue("Expected Room schema file at ${file.absolutePath}", file.exists())
        return JSONObject(file.readText(Charsets.UTF_8)).getJSONObject("database")
    }

    private fun allMigrations() = arrayOf(
        AppDatabase.MIGRATION_13_14,
        AppDatabase.MIGRATION_14_15,
        AppDatabase.MIGRATION_15_16,
        AppDatabase.MIGRATION_16_17,
        AppDatabase.MIGRATION_17_18,
        AppDatabase.MIGRATION_18_19,
        AppDatabase.MIGRATION_19_20,
        AppDatabase.MIGRATION_20_21,
        AppDatabase.MIGRATION_19_21,
        AppDatabase.MIGRATION_18_21,
        AppDatabase.MIGRATION_17_21,
        AppDatabase.MIGRATION_16_21,
        AppDatabase.MIGRATION_21_22,
        AppDatabase.MIGRATION_22_23,
        AppDatabase.MIGRATION_23_24,
        AppDatabase.MIGRATION_24_25,
        AppDatabase.MIGRATION_25_26,
        AppDatabase.MIGRATION_26_27,
        AppDatabase.MIGRATION_27_28,
        AppDatabase.MIGRATION_28_29,
        AppDatabase.MIGRATION_29_30,
        AppDatabase.MIGRATION_30_31,
        AppDatabase.MIGRATION_31_32,
        AppDatabase.MIGRATION_32_33,
        AppDatabase.MIGRATION_33_34,
        AppDatabase.MIGRATION_34_35,
        AppDatabase.MIGRATION_35_36,
        AppDatabase.MIGRATION_36_37,
        AppDatabase.MIGRATION_37_38
    )

    private fun buildVersionedSqliteDb(
        dbFile: File,
        version: Int,
        seedCustomData: (SQLiteDatabase, JSONObject) -> Unit = { _, _ -> }
    ) {
        if (dbFile.exists()) dbFile.delete()
        val schema = loadSchemaJson(version)
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                sqlite.execSQL(createSql)
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val idxSql = indices.getJSONObject(j)
                            .getString("createSql")
                            .replace("\${TABLE_NAME}", tableName)
                        sqlite.execSQL(idxSql)
                    }
                }
            }
            val setupQueries = schema.optJSONArray("setupQueries")
            if (setupQueries != null) {
                for (i in 0 until setupQueries.length()) {
                    sqlite.execSQL(setupQueries.getString(i))
                }
            }
            seedCustomData(sqlite, schema)
            sqlite.version = version
        } finally {
            sqlite.close()
        }
    }

    private fun insertSeedAppSettings(sqlite: SQLiteDatabase, schema: JSONObject, version: Int) {
        val entities = schema.getJSONArray("entities")
        val appSettings = (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .first { it.getString("tableName") == "app_settings" }
        val fields = appSettings.getJSONArray("fields")
        val colNames = mutableListOf<String>()
        val values = mutableListOf<Any>()

        for (i in 0 until fields.length()) {
            val f = fields.getJSONObject(i)
            val col = f.getString("columnName")
            val aff = f.getString("affinity")
            colNames.add(col)
            val v: Any = when (col) {
                "id" -> 1
                "baseYear" -> 2026
                "primaryName" -> "UserV$version"
                "spouseName" -> "SpouseV$version"
                "familyGiftMonthly" -> 18500.0
                "dpsYouthSubsidyMaxMonthly" -> 680.0
                "dpsSubsidyRateYouthPct" -> 40.0
                "parentalAllowanceTotalFromCutoff" -> 400000.0
                else -> when (aff) {
                    "INTEGER" -> 1
                    "REAL" -> 12.5
                    else -> "[]".takeIf { col.endsWith("Json") } ?: ""
                }
            }
            values.add(v)
        }
        val placeholders = colNames.joinToString(",") { "?" }
        sqlite.execSQL(
            "INSERT INTO app_settings (${colNames.joinToString(",")}) VALUES ($placeholders)",
            values.toTypedArray()
        )
    }

    private fun seedLedgerAndAuxiliaryRows(sqlite: SQLiteDatabase, version: Int) {
        val hasMonthEndCols = version >= 22
        val secondYearMonth = if (version < 26) "2026-01" else "2025-12"
        if (hasMonthEndCols) {
            sqlite.execSQL(
                "INSERT INTO ledger_entries (id, yearMonth, incVaclav, incEleonora, incUnforeseen, expRent, expGroceries, expOther, " +
                    "portfolioBalanceAtMonthEnd, pensionBalanceAtMonthEnd, emergencyReserveAtMonthEnd, notes) " +
                    "VALUES (10, '$secondYearMonth', 90000.0, 0.0, 0.0, 25000.0, 10000.0, 5000.0, 600000.0, 200000.0, 200000.0, 'older')"
            )
            sqlite.execSQL(
                "INSERT INTO ledger_entries (id, yearMonth, incVaclav, incEleonora, incUnforeseen, expRent, expGroceries, expOther, " +
                    "portfolioBalanceAtMonthEnd, pensionBalanceAtMonthEnd, emergencyReserveAtMonthEnd, notes) " +
                    "VALUES (20, '2026-01', 95000.0, 0.0, 0.0, 25000.0, 11000.0, 5000.0, 650000.0, 250000.0, 200000.0, 'newer')"
            )
        } else {
            sqlite.execSQL(
                "INSERT INTO ledger_entries (id, yearMonth, incVaclav, incEleonora, incUnforeseen, expRent, expGroceries, expOther, notes) " +
                    "VALUES (10, '$secondYearMonth', 90000.0, 0.0, 0.0, 25000.0, 10000.0, 5000.0, 'older')"
            )
            sqlite.execSQL(
                "INSERT INTO ledger_entries (id, yearMonth, incVaclav, incEleonora, incUnforeseen, expRent, expGroceries, expOther, notes) " +
                    "VALUES (20, '2026-01', 95000.0, 0.0, 0.0, 25000.0, 11000.0, 5000.0, 'newer')"
            )
        }
        sqlite.execSQL(
            "INSERT INTO action_states (actionKey, year, actionId, isDone) VALUES ('2026_act_1', 2026, 'act_1', 1)"
        )
        if (version >= 23) {
            sqlite.execSQL(
                "INSERT INTO imported_bank_transactions (id, yearMonth, bankName, date, amount, " +
                    "counterpartyAccount, counterpartyName, message, variableSymbol, category, isNetted, nettingReason, matchedTxId) " +
                    "VALUES (1, '2026-01', 'CSOB', '2026-01-15', -1250.5, '123/0300', 'Rohlik', 'Order', '111', 'FOOD', 0, '', NULL)"
            )
        }
    }

    @Test
    fun v16ToV37_migrateCleanlyToV38() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val expectedV38Schema = loadSchemaJson(38)
        val expectedEntities = expectedV38Schema.getJSONArray("entities")

        for (startVersion in 16..37) {
            val dbName = "v$startVersion.db"
            val dbFile = context.getDatabasePath(dbName)
            dbFile.parentFile?.mkdirs()

            try {
                buildVersionedSqliteDb(dbFile, startVersion) { sqlite, schema ->
                    insertSeedAppSettings(sqlite, schema, startVersion)
                    seedLedgerAndAuxiliaryRows(sqlite, startVersion)
                }

                val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                    .addMigrations(*allMigrations())
                    .allowMainThreadQueries()
                    .build()

                try {
                    val supportDb = roomDb.openHelper.writableDatabase
                    assertEquals("DB must reach v38 from v$startVersion", 38, supportDb.version)

                    // Verify every table columns & indices match 38.json
                    for (i in 0 until expectedEntities.length()) {
                        val entity = expectedEntities.getJSONObject(i)
                        val tableName = entity.getString("tableName")
                        val expectedCols = mutableSetOf<String>()
                        val fields = entity.getJSONArray("fields")
                        for (j in 0 until fields.length()) {
                            expectedCols.add(fields.getJSONObject(j).getString("columnName"))
                        }

                        val actualCols = mutableSetOf<String>()
                        supportDb.query("PRAGMA table_info($tableName)").use { c ->
                            while (c.moveToNext()) {
                                actualCols.add(c.getString(c.getColumnIndexOrThrow("name")))
                            }
                        }
                        assertEquals("Column parity mismatch for $tableName from v$startVersion", expectedCols, actualCols)

                        val expectedIdx = mutableSetOf<String>()
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) {
                            for (j in 0 until indices.length()) {
                                expectedIdx.add(indices.getJSONObject(j).getString("name"))
                            }
                        }
                        val actualIdx = mutableSetOf<String>()
                        supportDb.query("PRAGMA index_list($tableName)").use { c ->
                            while (c.moveToNext()) {
                                val name = c.getString(c.getColumnIndexOrThrow("name"))
                                if (!name.startsWith("sqlite_autoindex_")) {
                                    actualIdx.add(name)
                                }
                            }
                        }
                        assertEquals("Index parity mismatch for $tableName from v$startVersion", expectedIdx, actualIdx)
                    }

                    // Verify user data survived migration
                    supportDb.query(
                        "SELECT primaryName, vGiftsMonthly, dpsYouthSubsidyMaxMonthly, dpsSubsidyRateYouthPct, " +
                            "parentalAllowanceTotalFromCutoff FROM app_settings WHERE id = 1"
                    ).use { c ->
                        assertTrue("Seeded app_settings row missing from v$startVersion", c.moveToFirst())
                        assertEquals("UserV$startVersion", c.getString(0))
                        if (startVersion <= 31) {
                            assertEquals(18500.0, c.getDouble(1), 0.01)
                        }
                        if (startVersion <= 33) {
                            assertEquals(340.0, c.getDouble(2), 0.01)
                            assertEquals(20.0, c.getDouble(3), 0.01)
                            assertEquals(350000.0, c.getDouble(4), 0.01)
                        }
                    }

                    // Verify MIGRATION_25_26 kept only MAX(id) row for duplicate '2026-01'
                    supportDb.query("SELECT id, notes FROM ledger_entries WHERE yearMonth = '2026-01'").use { c ->
                        assertEquals("Expected deduplicated 1 ledger row for 2026-01 from v$startVersion", 1, c.count)
                        assertTrue(c.moveToFirst())
                        assertEquals(20L, c.getLong(0))
                        assertEquals("newer", c.getString(1))
                    }
                } finally {
                    roomDb.close()
                }
            } finally {
                dbFile.delete()
                File("${dbFile.path}-wal").delete()
                File("${dbFile.path}-shm").delete()
            }
        }
    }

    @Test
    fun inc_22_23_24_indexOrder() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dbName = "m22_24.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        try {
            buildVersionedSqliteDb(dbFile, 22)
            val sqlite = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            val frameworkHelper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(22) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                        override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
            )
            sqlite.close()

            val supportDb = frameworkHelper.writableDatabase
            try {
                AppDatabase.MIGRATION_22_23.migrate(supportDb)
                val idxAfter23 = mutableSetOf<String>()
                supportDb.query("PRAGMA index_list(imported_bank_transactions)").use { c ->
                    while (c.moveToNext()) idxAfter23.add(c.getString(c.getColumnIndexOrThrow("name")))
                }
                assertTrue(idxAfter23.contains("index_imported_bank_transactions_yearMonth"))
                assertTrue(idxAfter23.contains("index_imported_bank_transactions_bankName"))
                assertFalse(
                    "MIGRATION_22_23 must NOT prematurely create index_imported_bank_transactions_date",
                    idxAfter23.contains("index_imported_bank_transactions_date")
                )

                AppDatabase.MIGRATION_23_24.migrate(supportDb)
                val idxAfter24 = mutableSetOf<String>()
                supportDb.query("PRAGMA index_list(imported_bank_transactions)").use { c ->
                    while (c.moveToNext()) idxAfter24.add(c.getString(c.getColumnIndexOrThrow("name")))
                }
                assertTrue(
                    "MIGRATION_23_24 must create index_imported_bank_transactions_date",
                    idxAfter24.contains("index_imported_bank_transactions_date")
                )
            } finally {
                frameworkHelper.close()
            }
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun skip_16To19_21_and_v12Fallback() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        // Exercise direct skip migrations 16->21, 17->21, 18->21, 19->21
        val skipMigrations = listOf(
            16 to AppDatabase.MIGRATION_16_21,
            17 to AppDatabase.MIGRATION_17_21,
            18 to AppDatabase.MIGRATION_18_21,
            19 to AppDatabase.MIGRATION_19_21
        )
        for ((startVersion, skipMig) in skipMigrations) {
            val dbName = "sk$startVersion.db"
            val dbFile = context.getDatabasePath(dbName)
            dbFile.parentFile?.mkdirs()
            try {
                buildVersionedSqliteDb(dbFile, startVersion) { sqlite, schema ->
                    insertSeedAppSettings(sqlite, schema, startVersion)
                }
                val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                    .addMigrations(
                        skipMig,
                        AppDatabase.MIGRATION_21_22,
                        AppDatabase.MIGRATION_22_23,
                        AppDatabase.MIGRATION_23_24,
                        AppDatabase.MIGRATION_24_25,
                        AppDatabase.MIGRATION_25_26,
                        AppDatabase.MIGRATION_26_27,
                        AppDatabase.MIGRATION_27_28,
                        AppDatabase.MIGRATION_28_29,
                        AppDatabase.MIGRATION_29_30,
                        AppDatabase.MIGRATION_30_31,
                        AppDatabase.MIGRATION_31_32,
                        AppDatabase.MIGRATION_32_33,
                        AppDatabase.MIGRATION_33_34,
                        AppDatabase.MIGRATION_34_35,
                        AppDatabase.MIGRATION_35_36,
                        AppDatabase.MIGRATION_36_37,
                        AppDatabase.MIGRATION_37_38
                    )
                    .allowMainThreadQueries()
                    .build()
                try {
                    val supportDb = roomDb.openHelper.writableDatabase
                    assertEquals(38, supportDb.version)
                    supportDb.query("SELECT primaryName FROM app_settings WHERE id = 1").use { c ->
                        assertTrue(c.moveToFirst())
                        assertEquals("UserV$startVersion", c.getString(0))
                    }
                } finally {
                    roomDb.close()
                }
            } finally {
                dbFile.delete()
            }
        }

        // Destructive recovery from legacy v12 database (< 13)
        val v12Name = "legacy_v12_${UUID.randomUUID()}.db"
        val v12File = context.getDatabasePath(v12Name)
        v12File.parentFile?.mkdirs()
        try {
            val legacySql = SQLiteDatabase.openOrCreateDatabase(v12File, null)
            legacySql.execSQL("CREATE TABLE legacy_dummy (id INTEGER PRIMARY KEY)")
            legacySql.version = 12
            legacySql.close()

            val recoveredDb = Room.databaseBuilder(context, AppDatabase::class.java, v12Name)
                .addMigrations(*allMigrations())
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                .allowMainThreadQueries()
                .build()
            try {
                val supportDb = recoveredDb.openHelper.writableDatabase
                assertEquals(38, supportDb.version)
            } finally {
                recoveredDb.close()
            }
        } finally {
            v12File.delete()
        }
    }
}
