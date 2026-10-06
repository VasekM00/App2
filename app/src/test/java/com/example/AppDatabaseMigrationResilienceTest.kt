package com.example

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.SettingsEntity
import com.example.util.BackupManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AppDatabaseMigrationResilienceTest {

    private lateinit var db: AppDatabase
    private lateinit var supportDb: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        supportDb = db.openHelper.writableDatabase
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
    }

    @Test
    fun testRecreateAppSettingsTableSql_doesNotContainGiftsColumnsPrematurely() {
        // AppDatabase.recreateAppSettingsTable is used by migrations 13..20.
        // It must NOT declare vGiftsMonthly or eGiftsMonthly, because MIGRATION_31_32
        // executes ALTER TABLE ADD COLUMN vGiftsMonthly / eGiftsMonthly.
        val method = AppDatabase.Companion::class.java.getDeclaredMethod(
            "recreateAppSettingsTable",
            SupportSQLiteDatabase::class.java
        )
        method.isAccessible = true

        // Recreate a minimal v13 app_settings table
        supportDb.execSQL("DROP TABLE IF EXISTS app_settings")
        supportDb.execSQL("CREATE TABLE app_settings (id INTEGER PRIMARY KEY NOT NULL, baseYear INTEGER NOT NULL)")
        supportDb.execSQL("INSERT INTO app_settings (id, baseYear) VALUES (1, 2026)")

        // Invoke recreateAppSettingsTable
        method.invoke(AppDatabase.Companion, supportDb)

        // Check columns of recreated table
        val cursor = supportDb.query("PRAGMA table_info(app_settings)")
        val cols = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            cols.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()

        assertFalse("recreateAppSettingsTable must NOT include vGiftsMonthly", cols.contains("vGiftsMonthly"))
        assertFalse("recreateAppSettingsTable must NOT include eGiftsMonthly", cols.contains("eGiftsMonthly"))
        assertTrue("recreateAppSettingsTable must include familyGiftMonthly", cols.contains("familyGiftMonthly"))

        // Now verify MIGRATION_31_32 succeeds on this table without SQLiteException
        AppDatabase.MIGRATION_31_32.migrate(supportDb)

        val cursorAfter = supportDb.query("PRAGMA table_info(app_settings)")
        val colsAfter = mutableSetOf<String>()
        while (cursorAfter.moveToNext()) {
            colsAfter.add(cursorAfter.getString(cursorAfter.getColumnIndexOrThrow("name")))
        }
        cursorAfter.close()

        assertTrue("vGiftsMonthly must now exist after MIGRATION_31_32", colsAfter.contains("vGiftsMonthly"))
        assertTrue("eGiftsMonthly must now exist after MIGRATION_31_32", colsAfter.contains("eGiftsMonthly"))

        // Also verify MIGRATION_32_33 succeeds
        AppDatabase.MIGRATION_32_33.migrate(supportDb)
    }

    @Test
    fun testBackupManagerSanitizesMalformedNestedJsonArrays() {
        val fallback = SettingsEntity()
        val corruptedSettings = fallback.copy(
            customExpensesJson = "{malformed: invalid_json}",
            customGoalsJson = "not_an_array",
            customLumpSumsJson = "null",
            deletedCategoriesJson = "12345"
        )

        val json = BackupManager.serializeSettingsToJson(corruptedSettings)
        val restored = BackupManager.deserializeSettingsFromJson(json, fallback)

        assertNotNull("Deserialization should succeed with sanitized values", restored)
        assertEquals(fallback.customExpensesJson, restored?.customExpensesJson)
        assertEquals(fallback.customGoalsJson, restored?.customGoalsJson)
        assertEquals(fallback.customLumpSumsJson, restored?.customLumpSumsJson)
        assertEquals(fallback.deletedCategoriesJson, restored?.deletedCategoriesJson)
    }
}
