package com.example

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.CzechTaxEngine
import com.example.domain.FinancialEngine
import com.example.ui.MainViewModel
import com.example.ui.components.sankey.buildCentralHubNode
import com.example.ui.components.sankey.buildInflowNodes
import com.example.ui.components.sankey.buildOutflowNodes
import com.example.ui.state.computeCalculationStateInternal
import com.example.util.BankTransactionType
import com.example.util.CrossStatementReconciliationEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomeReactivityAndGiftTaxExemptionTest {

    @Test
    fun parentalAllowanceLateInYearIsNeverZeroedOrCappedWhenRaised() {
        val base = SettingsEntity(
            baseYear = 2026,
            child1Enabled = true,
            child1BirthYear = 2024,
            eReturnYear = 2029,
            vSalary = 33500.0,
            vMealVouchersMonthly = 2090.0,
            vGiftsMonthly = 16000.0,
            eParentalAllowanceMonthly = 13000.0,
            eLecturingMonthly = 6900.0,
            eIncludeLecturing = true
        )

        // November 2026 (month 11) must still reflect full 13,000 Kč parental allowance and 71,490 Kč total
        val novIncome = FinancialEngine.householdIncome(2026, base, activeLedgerEntry = null, month = 11)
        assertEquals(13000.0, novIncome.benefit, 0.001)
        assertEquals(71490.0, novIncome.totalMonthly, 0.001)

        // Manually raising parental allowance to 25,000 Kč or 40,000 Kč in November must rise immediately
        val raised25k = FinancialEngine.householdIncome(
            2026,
            base.copy(eParentalAllowanceMonthly = 25000.0),
            activeLedgerEntry = null,
            month = 11
        )
        assertEquals(25000.0, raised25k.benefit, 0.001)
        assertEquals(71490.0 + 12000.0, raised25k.totalMonthly, 0.001)

        val raised40k = FinancialEngine.householdIncome(
            2026,
            base.copy(eParentalAllowanceMonthly = 40000.0),
            activeLedgerEntry = null,
            month = 11
        )
        assertEquals(40000.0, raised40k.benefit, 0.001)
        assertEquals(71490.0 + 27000.0, raised40k.totalMonthly, 0.001)
    }

    @Test
    fun manualIncomeAndPortfolioEditsAreNeverFrozenByImportedOrPastLedgerEntries() {
        val now = YearMonth.now(ZoneId.of("Europe/Prague"))
        val currentYm = now.toString()
        val pastYm = now.minusMonths(2).toString()

        val importedCurrent = LedgerEntryEntity(
            yearMonth = currentYm,
            incVaclav = 32000.0,
            incEleonora = 8000.0,
            incUnforeseen = 1000.0,
            expRent = 20000.0,
            expGroceries = 5000.0,
            expOther = 3000.0,
            notes = "Imported from CSOB + MONETA (42 transactions)",
            portfolioBalanceAtMonthEnd = 200000.0,
            pensionBalanceAtMonthEnd = 10000.0,
            emergencyReserveAtMonthEnd = 150000.0
        )
        val pastManual = LedgerEntryEntity(
            yearMonth = pastYm,
            incVaclav = 30000.0,
            incEleonora = 10000.0,
            incUnforeseen = 500.0,
            expRent = 20000.0,
            expGroceries = 5000.0,
            expOther = 3000.0,
            notes = "Manual past month",
            portfolioBalanceAtMonthEnd = 180000.0,
            pensionBalanceAtMonthEnd = 5000.0,
            emergencyReserveAtMonthEnd = 120000.0
        )

        val editedSettings = SettingsEntity.freshDefaults().copy(
            vSalary = 75000.0,
            eParentalAllowanceMonthly = 18000.0,
            vOtherInflowsMonthly = 10000.0,
            eOtherInflowsMonthly = 5000.0,
            vGiftsMonthly = 20000.0,
            eGiftsMonthly = 10000.0,
            vMealVouchersMonthly = 2090.0,
            eLecturingMonthly = 6900.0,
            eIncludeLecturing = true,
            liquidPortfolioCurrent = 500000.0,
            eLiquidPortfolioCurrent = 100000.0
        )

        // With imported current-month ledger: manual edits MUST immediately take effect
        val stateWithImported = computeCalculationStateInternal(
            settings = editedSettings,
            actions = emptyMap(),
            ledger = listOf(pastManual, importedCurrent),
            importedTxs = emptyList(),
            retOver = null,
            cpiOver = null,
            swrOver = null,
            runMonteCarlo = false
        )
        assertEquals(75000.0, stateWithImported.currentIncome.vaclavNet, 0.001)
        assertEquals(18000.0, stateWithImported.currentIncome.benefit, 0.001)
        assertEquals(10000.0, stateWithImported.currentIncome.vaclavOther, 0.001)
        assertEquals(5000.0, stateWithImported.currentIncome.eleonoraOther, 0.001)
        assertEquals(30000.0, stateWithImported.currentIncome.gift, 0.001)
        assertEquals(
            75000.0 + 18000.0 + 10000.0 + 5000.0 + 30000.0 + 2090.0 + 6900.0,
            stateWithImported.currentIncome.totalMonthly,
            0.001
        )
        assertEquals(600000.0, stateWithImported.currentLiquidPortfolio, 0.001)

        // With only a past-month ledger: manual edits MUST immediately take effect
        val stateWithPastOnly = computeCalculationStateInternal(
            settings = editedSettings,
            actions = emptyMap(),
            ledger = listOf(pastManual),
            importedTxs = emptyList(),
            retOver = null,
            cpiOver = null,
            swrOver = null,
            runMonteCarlo = false
        )
        assertEquals(75000.0, stateWithPastOnly.currentIncome.vaclavNet, 0.001)
        assertEquals(18000.0, stateWithPastOnly.currentIncome.benefit, 0.001)
        assertEquals(stateWithImported.currentIncome.totalMonthly, stateWithPastOnly.currentIncome.totalMonthly, 0.001)
        assertEquals(600000.0, stateWithPastOnly.currentLiquidPortfolio, 0.001)
    }

    @Test
    fun familyGiftsAreCompletelyTaxExemptAndNeverNettedAsInternalTransfers() {
        val noGiftSettings = SettingsEntity(
            baseYear = 2026,
            isSingleHousehold = false,
            hasChildUnder3 = true,
            includeSpouseCredit = true,
            child1Enabled = true,
            child1BirthYear = 2024,
            eLecturingMonthly = 0.0,
            eIncludeLecturing = false,
            vGiftsMonthly = 0.0,
            eGiftsMonthly = 0.0
        )
        val hugeGiftSettings = noGiftSettings.copy(
            vGiftsMonthly = 100_000.0,
            eGiftsMonthly = 100_000.0
        )

        val stateNoGift = FinancialEngine.calculate(noGiftSettings, runMonteCarlo = false)
        val stateHugeGift = FinancialEngine.calculate(hugeGiftSettings, runMonteCarlo = false)

        // Gifts increase household income 1:1 with zero tax reduction
        assertEquals(
            stateNoGift.currentIncome.totalMonthly + 200_000.0,
            stateHugeGift.currentIncome.totalMonthly,
            0.001
        )
        // Gifts are 100% disregarded for ALL tax calculations (spouse 68k test, credits, DIP deduction)
        assertEquals(0.0, CzechTaxEngine.spouseOwnIncomeAnnual(2026, hugeGiftSettings), 0.001)
        assertEquals(stateNoGift.taxReturnHelper, stateHugeGift.taxReturnHelper)
        assertTrue(stateHugeGift.taxReturnHelper.spouseEligible)
        assertEquals(24_840.0, stateHugeGift.taxReturnHelper.spouseCredit, 0.001)

        // Incoming gifts from parents must NEVER be netted as internal self-transfers during reconciliation
        val parentGiftTx = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-11",
            bankName = "CSOB",
            date = "2026-11-05",
            amount = 16_000.0,
            counterpartyAccount = "777888999/0300",
            counterpartyName = "Andrea Martinů",
            message = "Příspěvek na bydlení - převod z účtu",
            category = BankTransactionType.OTHER_INFLOW.name
        )
        val (reconciled, _) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(parentGiftTx),
            yearMonth = "2026-11",
            householdNames = listOf("Václav", "Martinů", "Eleonora"),
            knownParentalGiftAccounts = setOf("777888999/0300")
        )
        assertFalse(
            "Family gift from parent must never be netted as an internal transfer",
            reconciled.first().isNetted
        )
        assertEquals(
            "Known parental gift account credit must be promoted to FAMILY_GIFT",
            BankTransactionType.FAMILY_GIFT.name,
            reconciled.first().category
        )
    }

    @Test
    fun sankeyHubMatchesInflowsAndOutflowsInBothViewModes() {
        val state = FinancialEngine.calculate(SettingsEntity(), runMonteCarlo = false)

        for (mode in listOf(0, 1)) {
            val inflows = buildInflowNodes(state, viewMode = mode).sumOf { it.amount }
            val hub = buildCentralHubNode(state, viewMode = mode).amount
            val outflows = buildOutflowNodes(state, viewMode = mode).sumOf { it.amount }
            assertEquals("Mode $mode: hub must equal inflows", inflows, hub, 0.01)
            assertEquals("Mode $mode: outflows must equal hub", hub, outflows, 0.01)
        }
    }

    @Test
    fun importedTransactionsForCurrentMonthBlockCustomNoteLedgerOverride() {
        val currentYm = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
        val customNoteLedger = LedgerEntryEntity(
            yearMonth = currentYm,
            incVaclav = 33500.0,
            incEleonora = 13000.0,
            incUnforeseen = 0.0,
            expRent = 21770.0,
            expGroceries = 4800.0,
            expOther = 10000.0,
            notes = "Custom user note"
        )
        val importedTx = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = currentYm,
            bankName = "CSOB",
            date = "$currentYm-10",
            amount = 33500.0,
            counterpartyName = "Employer",
            category = BankTransactionType.SALARY_VACLAV.name
        )
        val raisedSettings = SettingsEntity.freshDefaults().copy(vSalary = 88_000.0)

        val state = computeCalculationStateInternal(
            settings = raisedSettings,
            actions = emptyMap(),
            ledger = listOf(customNoteLedger),
            importedTxs = listOf(importedTx),
            retOver = null,
            cpiOver = null,
            swrOver = null,
            runMonteCarlo = false
        )
        assertEquals(88_000.0, state.currentIncome.vaclavNet, 0.001)
    }

    @Test
    fun repositoryMonthAwareReturnSyncPreservesAllowanceBeforeReturnMonth() = runBlocking {
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
            repo.saveSettings(
                SettingsEntity.freshDefaults().copy(
                    baseYear = 2028,
                    eReturnYear = 2028,
                    eReturnMonth = 9,
                    eParentalAllowanceMonthly = 18_000.0,
                    eStartingSalary = 45_000.0
                )
            )
            db.ledgerDao().insertEntry(
                LedgerEntryEntity(
                    yearMonth = "2028-03",
                    incVaclav = 33_500.0,
                    incEleonora = 18_000.0,
                    expRent = 21_770.0,
                    notes = "Manual entry"
                )
            )

            repo.updateSettingsTransformAndSyncLedger("2028-03") {
                it.copy(eParentalAllowanceMonthly = 22_000.0)
            }
            val syncedManual = db.ledgerDao().getEntryByYearMonth("2028-03")
            assertNotNull(syncedManual)
            assertEquals(22_000.0, syncedManual!!.incEleonora, 0.001)

            // Once the month has imported bank transactions, further Settings updates must NOT overwrite it
            db.importedTransactionDao().insertTransactions(
                listOf(
                    ImportedBankTransactionEntity(
                        id = 10L,
                        yearMonth = "2028-03",
                        bankName = "MONETA",
                        date = "2028-03-15",
                        amount = 22_000.0,
                        counterpartyName = "MPSV",
                        category = BankTransactionType.PARENTAL_BENEFIT.name
                    )
                )
            )
            repo.updateSettingsTransformAndSyncLedger("2028-03") {
                it.copy(eParentalAllowanceMonthly = 35_000.0, vSalary = 95_000.0)
            }
            val preservedImportedMonth = db.ledgerDao().getEntryByYearMonth("2028-03")
            assertNotNull(preservedImportedMonth)
            assertEquals(22_000.0, preservedImportedMonth!!.incEleonora, 0.001)
            assertEquals(33_500.0, preservedImportedMonth.incVaclav, 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun resetSettingsToDefaultSyncsCurrentManualLedger() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        fun clearDbSingleton() {
            runCatching {
                val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
                field.isAccessible = true
                (field.get(null) as? AppDatabase)?.close()
                field.set(null, null)
            }
            context.deleteDatabase("martinu_financials_db")
        }

        clearDbSingleton()
        val viewModel = MainViewModel(context)
        try {
            val currentYm = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
            val db = AppDatabase.getDatabase(context)
            db.ledgerDao().insertEntry(
                LedgerEntryEntity(
                    yearMonth = currentYm,
                    incVaclav = 19_000.0,
                    incEleonora = 13_000.0,
                    expRent = 21_770.0,
                    notes = "Manual current month"
                )
            )
            viewModel.updateSettings(SettingsEntity.freshDefaults().copy(vSalary = 77_000.0))

            val deadline1 = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < deadline1 &&
                (db.ledgerDao().getEntryByYearMonth(currentYm)?.incVaclav ?: 0.0) != 77_000.0
            ) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(25)
            }

            viewModel.resetSettingsToDefault()

            val expectedDefaultSalary = SettingsEntity.freshDefaults().vSalary
            val deadline2 = System.currentTimeMillis() + 8000
            var syncedEntry: LedgerEntryEntity? = null
            while (System.currentTimeMillis() < deadline2) {
                shadowOf(Looper.getMainLooper()).idle()
                syncedEntry = db.ledgerDao().getEntryByYearMonth(currentYm)
                if (syncedEntry != null &&
                    kotlin.math.abs(syncedEntry.incVaclav - expectedDefaultSalary) < 0.01
                ) {
                    break
                }
                Thread.sleep(25)
            }
            assertNotNull(syncedEntry)
            assertEquals(expectedDefaultSalary, syncedEntry!!.incVaclav, 0.01)
            assertEquals(expectedDefaultSalary, viewModel.settingsState.value.vSalary, 0.01)
        } finally {
            runCatching {
                val job = viewModel.viewModelScope.coroutineContext[Job]
                job?.cancel()
                withTimeoutOrNull(3000) { job?.join() }
                shadowOf(Looper.getMainLooper()).idle()
            }
            clearDbSingleton()
        }
    }
}
