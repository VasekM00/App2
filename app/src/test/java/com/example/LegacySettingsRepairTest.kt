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

    @Test
    fun ledgerHouseholdTotalsDoNotDoubleCountSpouseBalances() = runTest {
        val base = SettingsEntity(
            isSingleHousehold = false,
            liquidPortfolioCurrent = 200000.0,
            eLiquidPortfolioCurrent = 50000.0,
            dpsBalanceCurrent = 60000.0,
            dipBalanceCurrent = 40000.0,
            eDpsBalanceCurrent = 30000.0,
            eDipBalanceCurrent = 0.0
        )
        db.settingsDao().saveSettings(base)

        // Ledger reports household totals: Portu 300k (was 250k), pension 180k (was 130k).
        repository.syncLedgerToSettingsTransaction(
            yearMonth = "2026-10", currentYearMonth = "2026-10",
            incVaclav = 0.0, incEleonora = 0.0, expRent = 0.0,
            portfolioBalance = 300000.0, pensionBalance = 180000.0, emergencyReserve = 0.0
        )
        val s = db.settingsDao().getSettingsDirect()!!
        // Primary receives total minus spouse share; spouse fields untouched.
        assertEquals(250000.0, s.liquidPortfolioCurrent, 0.001)
        assertEquals(50000.0, s.eLiquidPortfolioCurrent, 0.001)
        assertEquals(300000.0, s.liquidPortfolioCurrent + s.eLiquidPortfolioCurrent, 0.001)
        // Primary pension 150k, split 60/40 as before (DIP not collapsed into DPS).
        assertEquals(90000.0, s.dpsBalanceCurrent, 0.001)
        assertEquals(60000.0, s.dipBalanceCurrent, 0.001)
        assertEquals(180000.0, s.dpsBalanceCurrent + s.dipBalanceCurrent + s.eDpsBalanceCurrent + s.eDipBalanceCurrent, 0.001)
    }

    @Test
    fun settingsLedgerRoundTripIsIdempotent() = runTest {
        val ym = "2026-10"
        val base = SettingsEntity(
            isSingleHousehold = false,
            liquidPortfolioCurrent = 200000.0,
            eLiquidPortfolioCurrent = 50000.0,
            dpsBalanceCurrent = 60000.0,
            dipBalanceCurrent = 40000.0,
            eDpsBalanceCurrent = 30000.0
        )
        db.ledgerDao().insertEntry(com.example.data.LedgerEntryEntity(yearMonth = ym))
        repository.updateSettingsAndSyncLedger(base, ym)
        val entry = db.ledgerDao().getEntryByYearMonth(ym)!!

        repository.syncLedgerToSettingsTransaction(
            yearMonth = ym, currentYearMonth = ym,
            incVaclav = 0.0, incEleonora = 0.0, expRent = 0.0,
            portfolioBalance = entry.portfolioBalanceAtMonthEnd,
            pensionBalance = entry.pensionBalanceAtMonthEnd,
            emergencyReserve = 0.0
        )
        val s = db.settingsDao().getSettingsDirect()!!
        assertEquals(200000.0, s.liquidPortfolioCurrent, 0.001)
        assertEquals(60000.0, s.dpsBalanceCurrent, 0.001)
        assertEquals(40000.0, s.dipBalanceCurrent, 0.001)
    }
}
