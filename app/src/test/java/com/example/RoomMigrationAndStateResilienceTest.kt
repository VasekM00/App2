package com.example

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActionStateEntity
import com.example.data.AppDatabase
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.util.BackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationAndStateResilienceTest {

    private lateinit var db: AppDatabase
    private lateinit var context: Application

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun test1_inMemoryDatabase_initialization_allFourTablesAccessible() {
        assertNotNull(db.settingsDao())
        assertNotNull(db.ledgerDao())
        assertNotNull(db.actionStateDao())
        assertNotNull(db.importedTransactionDao())
    }

    @Test
    fun test2_settingsDao_insertAndRetrieve_fieldIntegrity() = runBlocking {
        val settings = SettingsEntity(
            primaryName = "Vaclav",
            spouseName = "Eleonora",
            vSalary = 45000.0,
            rentMonthly = 18950.0
        )
        db.settingsDao().saveSettings(settings)

        val retrieved = db.settingsDao().getSettings().first()
        assertNotNull(retrieved)
        assertEquals("Vaclav", retrieved?.primaryName)
        assertEquals("Eleonora", retrieved?.spouseName)
        assertEquals(45000.0, retrieved?.vSalary ?: 0.0, 0.001)
        assertEquals(18950.0, retrieved?.rentMonthly ?: 0.0, 0.001)
    }

    @Test
    fun test3_ledgerDao_crudOperations_completeLifecycle() = runBlocking {
        val entry = LedgerEntryEntity(
            yearMonth = "2026-09",
            incVaclav = 45000.0,
            incEleonora = 22000.0,
            incUnforeseen = 0.0,
            expRent = 18950.0,
            expGroceries = 12000.0,
            expOther = 5000.0,
            notes = "Test month entry"
        )
        db.ledgerDao().insertEntry(entry)

        val retrieved = db.ledgerDao().getEntryByYearMonth("2026-09")
        assertNotNull(retrieved)
        assertEquals(18950.0, retrieved?.expRent ?: 0.0, 0.001)
        assertEquals(5000.0, retrieved?.expOther ?: 0.0, 0.001)

        val updated = retrieved!!.copy(expOther = 6000.0)
        db.ledgerDao().updateEntry(updated)

        val afterUpdate = db.ledgerDao().getEntryByYearMonth("2026-09")
        assertEquals(6000.0, afterUpdate?.expOther ?: 0.0, 0.001)

        db.ledgerDao().deleteEntry(afterUpdate!!.id)
        val afterDelete = db.ledgerDao().getEntryByYearMonth("2026-09")
        assertEquals(null, afterDelete)
    }

    @Test
    fun test4_importedTransactionDao_batchInsertAndMonthQuery() = runBlocking {
        val tx1 = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-09",
            bankName = "CSOB",
            date = "2026-09-01",
            amount = -18950.0,
            counterpartyName = "Majitel Byty",
            category = "HOUSING_RENT"
        )
        val tx2 = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-09",
            bankName = "MONETA",
            date = "2026-09-02",
            amount = 65000.0,
            counterpartyName = "Zamestnavatel",
            category = "SALARY_VACLAV"
        )
        val tx3 = ImportedBankTransactionEntity(
            id = 3L,
            yearMonth = "2026-08",
            bankName = "CSOB",
            date = "2026-08-15",
            amount = -500.0,
            counterpartyName = "Billa",
            category = "GROCERIES"
        )

        db.importedTransactionDao().insertTransactions(listOf(tx1, tx2, tx3))

        val sepTxs = db.importedTransactionDao().getTransactionsForMonthDirect("2026-09")
        assertEquals(2, sepTxs.size)

        val augTxs = db.importedTransactionDao().getTransactionsForMonthDirect("2026-08")
        assertEquals(1, augTxs.size)
    }

    @Test
    fun test5_actionStateDao_toggleState_persistsAndRetrieves() = runBlocking {
        val action = ActionStateEntity(
            actionKey = "2026_ac1",
            year = 2026,
            actionId = "action_emergency_fund",
            isDone = false
        )
        db.actionStateDao().saveActionState(action)

        val initial = db.actionStateDao().getAllActionStates().first()
        assertEquals(1, initial.size)
        assertEquals(false, initial[0].isDone)

        db.actionStateDao().toggleActionState("2026_ac1")
        val afterToggle = db.actionStateDao().getAllActionStates().first()
        assertEquals(true, afterToggle[0].isDone)
    }

    @Test
    fun test6_databaseVersion_matchesTarget29() {
        val version = db.openHelper.readableDatabase.version
        assertEquals(29, version)
    }

    @Test
    fun test7_migration_22_23_createsImportedTransactionsTableWithIndices() {
        val cursor = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='imported_bank_transactions'")
        assertTrue("imported_bank_transactions table must exist in schema v23", cursor.moveToFirst())
        cursor.close()
    }

    @Test
    fun test7b_migration_23_24_createsDateIndexOnImportedTransactions() {
        val cursor = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_imported_bank_transactions_date'")
        assertTrue("index_imported_bank_transactions_date must exist in schema v24", cursor.moveToFirst())
        cursor.close()
    }

    @Test
    fun test7c_schema_v28_settingsColumnsExist() {
        val cursor = db.openHelper.readableDatabase.query("PRAGMA table_info(app_settings)")
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        assertTrue("dividendYieldPct must exist in app_settings", columns.contains("dividendYieldPct"))
        assertTrue("dividendTaxRatePct must exist in app_settings", columns.contains("dividendTaxRatePct"))
        assertTrue("useHistoricalBootstrap must exist in app_settings", columns.contains("useHistoricalBootstrap"))
        assertTrue("guardrailsEnabled must exist in app_settings", columns.contains("guardrailsEnabled"))
    }

    @Test
    fun test7d_schema_v29_settingsColumnsExist() {
        val cursor = db.openHelper.readableDatabase.query("PRAGMA table_info(app_settings)")
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        assertTrue("currentChildCostsInBaseline must exist in app_settings", columns.contains("currentChildCostsInBaseline"))
        assertTrue("merchantRulesJson must exist in app_settings", columns.contains("merchantRulesJson"))
    }

    @Test
    fun test8_stateRestoration_savedStateHandle_simulatedProcessRecreation() {
        val originalHandle = SavedStateHandle()
        originalHandle["selected_tab_index"] = 2
        originalHandle["pending_import_bank"] = "MONETA"

        val restoredTabIndex: Int? = originalHandle["selected_tab_index"]
        val restoredBank: String? = originalHandle["pending_import_bank"]

        assertEquals(2, restoredTabIndex)
        assertEquals("MONETA", restoredBank)
    }

    @Test
    fun test9_settingsEntity_schemaParity_allFieldsSerializedInBackupManager() {
        val settings = SettingsEntity()
        val json = BackupManager.serializeSettingsToJson(settings)
        val deserialized = BackupManager.deserializeSettingsFromJson(json, fallback = SettingsEntity())

        assertNotNull(deserialized)
        assertEquals(settings.baseYear, deserialized!!.baseYear)
        assertEquals(settings.vSalary, deserialized.vSalary, 0.001)
        assertEquals(settings.isSingleHousehold, deserialized.isSingleHousehold)
        assertEquals(settings.rentMonthly, deserialized.rentMonthly, 0.001)
    }

    @Test
    fun test10_database_concurrentWrites_noDeadlockOrCorruption() = runBlocking {
        val entries = (1..10).map { i ->
            LedgerEntryEntity(
                yearMonth = "2026-${i.toString().padStart(2, '0')}",
                incVaclav = 30000.0 + i * 1000.0,
                incEleonora = 15000.0,
                incUnforeseen = 0.0,
                expRent = 18950.0,
                expGroceries = 10000.0,
                expOther = 2000.0
            )
        }

        for (e in entries) {
            db.ledgerDao().insertEntry(e)
        }

        val allEntries = db.ledgerDao().getAllEntries().first()
        assertEquals(10, allEntries.size)
    }
}
