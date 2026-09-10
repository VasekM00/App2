package com.example

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the Room migration chain against the exported schema JSONs.
 *
 * Room upgrades apply every migration from the installed version straight to the latest version
 * and validates the schema only at the final version, so each supported starting version is tested
 * against the latest schema (16 -> 24 ... 23 -> 24).
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationInstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val migrationsInOrder: List<Migration> = listOf(
        AppDatabase.MIGRATION_16_17,
        AppDatabase.MIGRATION_17_18,
        AppDatabase.MIGRATION_18_19,
        AppDatabase.MIGRATION_19_20,
        AppDatabase.MIGRATION_20_21,
        AppDatabase.MIGRATION_21_22,
        AppDatabase.MIGRATION_22_23,
        AppDatabase.MIGRATION_23_24,
        AppDatabase.MIGRATION_24_25
    )

    @Test
    fun migrateFromEverySupportedVersionToLatest() {
        for (start in 16..24) {
            val applicable = migrationsInOrder.filter { it.startVersion >= start }
            helper.createDatabase(TEST_DB, start).close()
            helper.runMigrationsAndValidate(TEST_DB, 25, true, *applicable.toTypedArray()).close()
        }
    }

    @Test
    fun migrateFullChain16To25PreservesLedgerDataAndAddsSnapshotColumns() {
        val db16 = helper.createDatabase(TEST_DB, 16)
        db16.execSQL(
            "INSERT INTO ledger_entries " +
                "(id, yearMonth, incVaclav, incEleonora, incUnforeseen, expRent, expGroceries, expOther, notes) " +
                "VALUES (1, '2024-01', 31000.0, 12000.0, 0.0, 19000.0, 4200.0, 0.0, 'legacy')"
        )
        db16.close()

        val db25 = helper.runMigrationsAndValidate(TEST_DB, 25, true, *migrationsInOrder.toTypedArray())
        db25.query(
            "SELECT incVaclav, notes, portfolioBalanceAtMonthEnd, pensionBalanceAtMonthEnd, " +
                "emergencyReserveAtMonthEnd FROM ledger_entries WHERE id = 1"
        ).use { cursor ->
            assertTrue("Ledger row must survive the full 16 -> 25 chain", cursor.moveToFirst())
            assertEquals(31000.0, cursor.getDouble(0), 0.001)
            assertEquals("legacy", cursor.getString(1))
            assertEquals(0.0, cursor.getDouble(2), 0.001)
            assertEquals(0.0, cursor.getDouble(3), 0.001)
            assertEquals(0.0, cursor.getDouble(4), 0.001)
        }
        val settingsColumns = mutableListOf<String>()
        db25.query("PRAGMA table_info(app_settings)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) settingsColumns.add(cursor.getString(nameIndex))
        }
        assertTrue(
            "app_settings must gain retirementHorizonYears in schema 25, found: $settingsColumns",
            settingsColumns.contains("retirementHorizonYears")
        )
        db25.close()
    }

    @Test
    fun migrate21To22PreservesLedgerRowsAndAddsSnapshotColumns() {
        val db21 = helper.createDatabase(TEST_DB, 21)
        db21.execSQL(
            "INSERT INTO ledger_entries " +
                "(id, yearMonth, incVaclav, incEleonora, incUnforeseen, expRent, expGroceries, expOther, notes) " +
                "VALUES (1, '2026-01', 35000.0, 13000.0, 0.0, 21770.0, 4800.0, 0.0, 'seed')"
        )
        db21.close()

        val db22 = helper.runMigrationsAndValidate(TEST_DB, 22, true, AppDatabase.MIGRATION_21_22)
        db22.query(
            "SELECT incVaclav, notes, portfolioBalanceAtMonthEnd, pensionBalanceAtMonthEnd, " +
                "emergencyReserveAtMonthEnd FROM ledger_entries WHERE id = 1"
        ).use { cursor ->
            assertTrue("Seeded ledger row must survive the migration", cursor.moveToFirst())
            assertEquals(35000.0, cursor.getDouble(0), 0.001)
            assertEquals("seed", cursor.getString(1))
            assertEquals(0.0, cursor.getDouble(2), 0.001)
            assertEquals(0.0, cursor.getDouble(3), 0.001)
            assertEquals(0.0, cursor.getDouble(4), 0.001)
        }
        db22.close()
    }

    @Test
    fun migrate23To24AddsTransactionDateIndex() {
        helper.createDatabase(TEST_DB, 23).close()
        val db24 = helper.runMigrationsAndValidate(TEST_DB, 24, true, AppDatabase.MIGRATION_23_24)
        val indexNames = mutableListOf<String>()
        db24.query("PRAGMA index_list(imported_bank_transactions)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                indexNames.add(cursor.getString(nameIndex))
            }
        }
        assertTrue(
            "Expected date index after 23->24 migration, found: $indexNames",
            indexNames.any { it.contains("date") }
        )
        db24.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
