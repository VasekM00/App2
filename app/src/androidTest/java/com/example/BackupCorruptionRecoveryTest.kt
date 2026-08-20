package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.SettingsDao
import com.example.data.SettingsEntity
import com.example.util.BackupManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class BackupCorruptionRecoveryTest {

    private lateinit var db: AppDatabase
    private lateinit var settingsDao: SettingsDao
    private val defaultFallback = SettingsEntity(
        id = 1,
        baseYear = 2026,
        primaryName = "Václav",
        spouseName = "Eleonora",
        vSalary = 35000.0,
        rentMonthly = 21770.0,
        safeWithdrawalRatePct = 4.0,
        liquidPortfolioCurrent = 200000.0
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDao = db.settingsDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    /**
     * Validates that the application gracefully recovers when partial, corrupted,
     * type-mismatched, or malformed backup JSON payloads are supplied.
     */
    @Test
    fun testBackupCorruptionRecoveryAndPartialJsonHandling() = runBlocking {
        // Scenario 1: Malformed and corrupted JSON payloads
        val malformedPayloads = listOf(
            "",
            "   ",
            "{",
            "{\"baseYear\": 2026, \"vSalary\":",
            "not a json at all",
            "{\"unclosed\": true",
            "{\"key\": undefined}",
            "\u0000\u0001\u0002 corrupted binary data"
        )

        for (corrupted in malformedPayloads) {
            val result = BackupManager.deserializeSettingsFromJson(corrupted, defaultFallback)
            assertNull("Malformed JSON should return null and not throw: '$corrupted'", result)
        }

        // Scenario 2: Partial JSON payload (only a few fields supplied)
        val partialJson = """
            {
                "primaryName": "Jan Novák",
                "vSalary": 62000.0,
                "safeWithdrawalRatePct": 3.75,
                "liquidPortfolioCurrent": 850000.0
            }
        """.trimIndent()

        val partialRestored = BackupManager.deserializeSettingsFromJson(partialJson, defaultFallback)
        assertNotNull("Partial JSON should be successfully parsed", partialRestored)
        partialRestored!!

        // Assert provided fields are parsed correctly
        assertEquals("Jan Novák", partialRestored.primaryName)
        assertEquals(62000.0, partialRestored.vSalary, 0.001)
        assertEquals(3.75, partialRestored.safeWithdrawalRatePct, 0.001)
        assertEquals(850000.0, partialRestored.liquidPortfolioCurrent, 0.001)

        // Assert unprovided fields gracefully retained fallback values
        assertEquals(defaultFallback.spouseName, partialRestored.spouseName)
        assertEquals(defaultFallback.baseYear, partialRestored.baseYear)
        assertEquals(defaultFallback.rentMonthly, partialRestored.rentMonthly, 0.001)
        assertEquals(defaultFallback.dpsBalanceCurrent, partialRestored.dpsBalanceCurrent, 0.001)
        assertEquals(defaultFallback.cpiInflationPct, partialRestored.cpiInflationPct, 0.001)

        // Scenario 3: JSON with invalid / type-mismatched values
        val typeMismatchedJson = """
            {
                "baseYear": "not_an_int",
                "primaryName": "Valid Name",
                "vSalary": "invalid_double",
                "isSingleHousehold": "not_a_boolean"
            }
        """.trimIndent()

        val mismatchedRestored = BackupManager.deserializeSettingsFromJson(typeMismatchedJson, defaultFallback)
        assertNotNull("Type-mismatched JSON should safely deserialize using fallback defaults", mismatchedRestored)
        assertEquals("Valid Name", mismatchedRestored?.primaryName)
        assertEquals(defaultFallback.baseYear, mismatchedRestored?.baseYear)
        assertEquals(defaultFallback.vSalary, mismatchedRestored?.vSalary ?: 0.0, 0.001)

        // Scenario 4: Database recovery validation
        // Persist the safely recovered settings into Room database and verify persistence integrity
        settingsDao.saveSettings(partialRestored)
        val dbSettings = settingsDao.getSettingsDirect()

        assertNotNull("Recovered settings must be successfully persisted in Room", dbSettings)
        assertEquals("Jan Novák", dbSettings?.primaryName)
        assertEquals(62000.0, dbSettings?.vSalary ?: 0.0, 0.001)
        assertEquals(defaultFallback.spouseName, dbSettings?.spouseName)
    }
}
