package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FinancialRepository
import com.example.data.SettingsEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegacySettingsRepairTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FinancialRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinancialRepository(
            database = db,
            settingsDao = db.settingsDao(),
            ledgerDao = db.ledgerDao(),
            actionStateDao = db.actionStateDao(),
            importedTransactionDao = db.importedTransactionDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun repairsLegacyEmployerContributionAndPersists() = runTest {
        db.settingsDao().saveSettings(SettingsEntity(employerRetirementMonthly = 2800.0))

        repository.repairLegacyEmployerContribution()

        assertEquals(233.0, db.settingsDao().getSettingsDirect()!!.employerRetirementMonthly, 0.001)
    }

    @Test
    fun leavesCorrectEmployerContributionUntouched() = runTest {
        db.settingsDao().saveSettings(SettingsEntity(employerRetirementMonthly = 233.0))

        repository.repairLegacyEmployerContribution()

        assertEquals(233.0, db.settingsDao().getSettingsDirect()!!.employerRetirementMonthly, 0.001)
    }

    @Test
    fun leavesUnrelatedEmployerContributionUntouched() = runTest {
        db.settingsDao().saveSettings(SettingsEntity(employerRetirementMonthly = 1000.0))

        repository.repairLegacyEmployerContribution()

        assertEquals(1000.0, db.settingsDao().getSettingsDirect()!!.employerRetirementMonthly, 0.001)
    }
}
