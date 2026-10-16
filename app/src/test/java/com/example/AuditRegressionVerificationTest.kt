package com.example

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.MainViewModel
import com.example.util.BackupManager
import com.example.util.SubscriptionAuditor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuditRegressionVerificationTest {

    private lateinit var context: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: FinancialRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        database = AppDatabase.getDatabase(context)
        repository = FinancialRepository(
            database = database,
            settingsDao = database.settingsDao(),
            ledgerDao = database.ledgerDao(),
            actionStateDao = database.actionStateDao(),
            importedTransactionDao = database.importedTransactionDao()
        )
        viewModel = MainViewModel(context)
    }

    private suspend fun drainAsync(maxRetries: Int = 40, condition: suspend () -> Boolean): Boolean {
        for (i in 0 until maxRetries) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            kotlinx.coroutines.delay(50)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    // 1. SubscriptionAuditor Single-Month Annualization and One-Off Rejection Safety
    @Test
    fun testSubscriptionAuditor_singleMonthOneOffRejected_andAnnualChargeCorrectlyProrated() {
        val txs = listOf(
            // One-off general expense in single month: must be rejected
            ImportedBankTransactionEntity(
                id = 1L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-12",
                amount = -8500.0,
                counterpartyName = "DATART ELEKTRO",
                category = "GENERAL_EXPENSE",
                message = "Nakup lednice"
            ),
            // Verified annual charge: "poplatek za svoz odpadu ročně"
            ImportedBankTransactionEntity(
                id = 2L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-15",
                amount = -1200.0,
                counterpartyName = "Mesto Brno - Odbor zivotniho prostredi",
                category = "SERVICES_UTILITIES",
                message = "Poplatek za komunalni odpad rocne 2026"
            )
        )

        val summary = SubscriptionAuditor.auditSubscriptions(txs, swrPct = 4.0)

        // Only the annual waste charge is recognized, DATART is rejected
        assertEquals(1, summary.items.size)
        val wasteItem = summary.items[0]
        assertEquals("ANNUAL", wasteItem.billingCadence)
        assertEquals(100.0, wasteItem.normalizedMonthlyAmount, 0.001) // 1200 / 12
        assertEquals(1200.0, summary.annualizedBurn, 0.001)
        assertEquals(1200.0 / 0.04, summary.fireCapitalRequired, 0.01)
    }

    // 2. BackupManager Negative Growth Retention and Parity
    @Test
    fun testBackupManager_preservesNegativeGrowthAndBroadAgeBounds() {
        val original = SettingsEntity.freshDefaults().copy(
            primaryAge = 22,
            vSalaryGrowthPct = -2.5,
            eSalaryGrowthPct = -1.0,
            rentGrowthPct = -3.0,
            cpiInflationPct = -0.5,
            portfolioNominalReturnPct = -10.0,
            dcaAnnualGrowthPct = -5.0
        )

        val json = BackupManager.serializeSettingsToJson(original)
        val restored = BackupManager.deserializeSettingsFromJson(json, fallback = SettingsEntity.freshDefaults())

        assertNotNull(restored)
        assertEquals(22, restored!!.primaryAge)
        assertEquals(-2.5, restored.vSalaryGrowthPct, 0.001)
        assertEquals(-1.0, restored.eSalaryGrowthPct, 0.001)
        assertEquals(-3.0, restored.rentGrowthPct, 0.001)
        assertEquals(-0.5, restored.cpiInflationPct, 0.001)
        assertEquals(-10.0, restored.portfolioNominalReturnPct, 0.001)
        assertEquals(-5.0, restored.dcaAnnualGrowthPct, 0.001)
    }

    // 3. FinancialRepository Baseline Sync Protection for Imported Actuals
    @Test
    fun testFinancialRepository_doesNotOverwriteImportedLedgerEntryWithBaseline() = runTest {
        val ym = "2026-09"
        val importedEntry = LedgerEntryEntity(
            yearMonth = ym,
            incVaclav = 45000.0,
            incEleonora = 25000.0,
            incUnforeseen = 0.0,
            expRent = 19500.0,
            expGroceries = 8200.0,
            expOther = 5100.0,
            notes = "Imported from CSOB (34 txs)",
            portfolioBalanceAtMonthEnd = 350000.0,
            pensionBalanceAtMonthEnd = 120000.0,
            emergencyReserveAtMonthEnd = 150000.0
        )
        repository.addLedgerEntry(importedEntry)

        // Baseline settings have different values
        val newSettings = SettingsEntity.freshDefaults().copy(
            vSalary = 80000.0,
            rentMonthly = 30000.0,
            emergencyReserveCurrent = 500000.0
        )

        repository.updateSettingsAndSyncLedger(newSettings, currentYearMonth = ym)

        // Verify the entry remained untouched
        val currentEntry = repository.getLedgerEntryByYearMonth(ym)
        assertNotNull(currentEntry)
        assertEquals("Imported from CSOB (34 txs)", currentEntry!!.notes)
        assertEquals(45000.0, currentEntry.incVaclav, 0.001)
        assertEquals(19500.0, currentEntry.expRent, 0.001)
    }

    // 4. MainViewModel Statement Deletion Netting Cleanup and Ledger Recalculation
    @Test
    fun testMainViewModel_deleteImportedStatement_cleansOrphanedNettingAndRecalculates() = runTest {
        val ym = "2026-10"
        val txBankA = ImportedBankTransactionEntity(
            id = 101L,
            yearMonth = ym,
            bankName = "KB",
            date = "2026-10-10",
            amount = -10000.0,
            counterpartyName = "Partner Sporeni",
            category = "INTERNAL_TRANSFER",
            isNetted = true,
            nettingReason = "Matched transfer"
        )
        val txBankB = ImportedBankTransactionEntity(
            id = 102L,
            yearMonth = ym,
            bankName = "CSOB",
            date = "2026-10-10",
            amount = 10000.0,
            counterpartyName = "Vlastni ucet",
            category = "INTERNAL_TRANSFER",
            isNetted = true,
            nettingReason = "Matched transfer"
        )
        val txExpense = ImportedBankTransactionEntity(
            id = 103L,
            yearMonth = ym,
            bankName = "CSOB",
            date = "2026-10-12",
            amount = -2500.0,
            counterpartyName = "BILLA",
            category = "GROCERIES",
            isNetted = false
        )

        repository.saveImportedTransactions(listOf(txBankA, txBankB, txExpense))
        val ledger = LedgerEntryEntity(
            yearMonth = ym,
            incVaclav = 0.0,
            incEleonora = 0.0,
            incUnforeseen = 0.0,
            expRent = 0.0,
            expGroceries = 2500.0,
            expOther = 0.0,
            notes = "Imported from KB + CSOB",
            portfolioBalanceAtMonthEnd = 100000.0,
            pensionBalanceAtMonthEnd = 50000.0,
            emergencyReserveAtMonthEnd = 80000.0
        )
        repository.addLedgerEntry(ledger)

        // Delete statement from KB
        viewModel.deleteImportedStatement(yearMonth = ym, bankName = "KB")
        val successKbDelete = drainAsync {
            val txs = repository.getImportedTransactionsDirect(ym)
            txs.none { it.bankName == "KB" } && txs.any { it.id == 102L && !it.isNetted }
        }
        assertTrue(successKbDelete)

        val remaining = repository.getImportedTransactionsDirect(ym)
        assertEquals(2, remaining.size)
        // Verify bank B transaction had its netting flag cleared since counterpart was removed
        val csobTransfer = remaining.find { it.id == 102L }
        assertNotNull(csobTransfer)
        assertFalse(csobTransfer!!.isNetted)

        // Now delete all remaining statements for month
        viewModel.deleteImportedStatement(yearMonth = ym, bankName = null)
        val successAllDelete = drainAsync {
            repository.getImportedTransactionsDirect(ym).isEmpty() &&
                repository.getLedgerEntryByYearMonth(ym)?.expGroceries == 0.0
        }
        assertTrue(successAllDelete)

        val finalEntry = repository.getLedgerEntryByYearMonth(ym)
        assertNotNull(finalEntry)
        assertEquals(0.0, finalEntry!!.expGroceries, 0.001)
        assertEquals("", finalEntry.notes)
        assertNull(viewModel.activeAuditReport.value)
    }

    // 5. Total Portfolio Trajectory and Compound Calculations
    @Test
    fun testPortfolioTrajectory_totalPortfolioCompoundsLiquidAndPensionWithLiquidBridgeGating() {
        val settings = SettingsEntity.freshDefaults().copy(
            baseYear = 2026,
            primaryAge = 30,
            vStatePensionAge = 65,
            liquidPortfolioCurrent = 500000.0,
            dpsBalanceCurrent = 200000.0,
            dipBalanceCurrent = 100000.0,
            portuDcaMonthly = 10000.0,
            dpsOwnContributionMonthly = 1700.0,
            dipContributionMonthly = 1700.0,
            employerRetirementMonthly = 233.0,
            rentMonthly = 20000.0,
            groceriesMonthly = 10000.0,
            isSingleHousehold = true
        )

        val trajectory = FinancialEngine.buildLiquidPortfolio(settings, dualIncome = false)

        assertTrue(trajectory.isNotEmpty())
        val year0 = trajectory[0]
        assertEquals(2026, year0.year)
        assertEquals(30, year0.age)
        assertEquals(500000.0, year0.portfolio, 0.001)
        assertEquals(300000.0, year0.pensionPortfolio, 0.001)
        assertEquals(800000.0, year0.totalPortfolio, 0.001)

        // Bridge to 60 requirement at age 30 (30 years to age 60)
        assertTrue(year0.liquidBridgeTo60Required > 0.0)
        // 500k liquid is less than the required bridge capital to 60 for 30k/mo living costs
        assertFalse(year0.isLiquidBridgeFunded)

        // Trajectory at future years compounds both liquid and pension
        val year10 = trajectory[10]
        assertTrue(year10.portfolio > year0.portfolio)
        assertTrue(year10.pensionPortfolio > year0.pensionPortfolio)
        assertEquals(year10.portfolio + year10.pensionPortfolio, year10.totalPortfolio, 0.001)
    }

    // 6. Household Timeline Net Cash Flow Verification & Ledger Overrides
    @Test
    fun testHouseholdTimeline_netCashFlowAndLedgerOverrides() {
        val baseSettings = SettingsEntity.freshDefaults().copy(
            baseYear = 2026,
            vSalary = 33500.0,
            vMealVouchersMonthly = 2090.0,
            familyGiftMonthly = 16000.0,
            vGiftsMonthly = 16000.0,
            eStartingSalary = 25000.0,
            eParentalAllowanceMonthly = 13000.0,
            eLecturingMonthly = 6900.0,
            eIncludeLecturing = true,
            eReturnYear = 2028,
            isSingleHousehold = false
        )

        // 1. Baseline scenario before return year:
        // Vaclav net = 33500, vouchers = 2090, gift = 16000 -> primary = 51590
        // Eleonora net = 13000 (RP) + 6900 (lecturing) = 19900
        // Total household net = 51590 + 19900 = 71490
        val baseIncome = FinancialEngine.householdIncome(2026, baseSettings)
        val primaryNetBase = baseIncome.vaclavNet + baseIncome.vouchers + baseIncome.vaclavGifts + baseIncome.vaclavOther
        val spouseNetBase = baseIncome.eleonoraSalary + baseIncome.benefit + baseIncome.lecturing + baseIncome.eleonoraGifts + baseIncome.eleonoraOther
        assertEquals(33500.0, baseIncome.vaclavNet, 0.001)
        assertEquals(51590.0, primaryNetBase, 0.001)
        assertEquals(19900.0, spouseNetBase, 0.001)
        assertEquals(71490.0, baseIncome.totalMonthly, 0.001)

        // 2. Active ledger month override (e.g. October actual statement net cash flow totaling 91 000 CZK net):
        val octLedger = LedgerEntryEntity(
            yearMonth = "2026-10",
            incVaclav = 45000.0,
            incEleonora = 25000.0,
            incUnforeseen = 2910.0,
            expRent = 18000.0,
            expGroceries = 12000.0,
            expOther = 5000.0,
            portfolioBalanceAtMonthEnd = 300000.0,
            pensionBalanceAtMonthEnd = 80000.0,
            emergencyReserveAtMonthEnd = 150000.0
        )
        // With active ledger entry and lecturing enabled:
        // v = 45000, e = 25000, lecturing = 6900, vouchers = 2090, gift = 16000, unforeseen = 2910 -> total = 97900
        val octIncome = FinancialEngine.householdIncome(2026, baseSettings, activeLedgerEntry = octLedger, month = 10)
        assertEquals(97900.0, octIncome.totalMonthly, 0.001)

        // When lecturing is already folded into ledger incEleonora (includeLecturing = false):
        val octSettingsNoExtraLec = baseSettings.copy(eIncludeLecturing = false)
        val octIncomeExact91k = FinancialEngine.householdIncome(2026, octSettingsNoExtraLec, activeLedgerEntry = octLedger, month = 10)
        assertEquals(91000.0, octIncomeExact91k.totalMonthly, 0.001)

        // 3. Post career return (e.g. 2029):
        val futureIncome = FinancialEngine.householdIncome(2029, baseSettings)
        assertTrue(futureIncome.eleonoraSalary > 0.0)
        assertEquals(0.0, futureIncome.benefit, 0.001)
    }
}
