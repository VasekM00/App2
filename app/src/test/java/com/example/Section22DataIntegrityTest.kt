package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppPreferencesRepository
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.MainViewModel
import com.example.ui.state.StatementImportController
import com.example.ui.state.runOneTimeMigrationsInternal
import com.example.util.BackupManager
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementAuditReport
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.InvestmentLotManager
import com.example.util.MerchantCategoryManager
import com.example.util.ParsedBankTransaction
import com.example.util.StatementImportCoordinator
import com.example.util.StatementParseSummary
import com.example.util.UserInvestmentLot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Section22DataIntegrityTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: FinancialRepository
    private lateinit var prefsRepo: AppPreferencesRepository
    private val testPrefsName = "app_user_prefs"

    private fun resetDatabaseSingleton(context: Context) {
        runCatching {
            val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            val existing = field.get(null) as? AppDatabase
            existing?.close()
            field.set(null, null)
        }
        context.deleteDatabase("martinu_financials_db")
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        resetDatabaseSingleton(app)
        app.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("investment_lots_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("merchant_category_overrides", Context.MODE_PRIVATE).edit().clear().commit()

        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinancialRepository(
            database = db,
            settingsDao = db.settingsDao(),
            ledgerDao = db.ledgerDao(),
            actionStateDao = db.actionStateDao(),
            importedTransactionDao = db.importedTransactionDao()
        )
        prefsRepo = AppPreferencesRepository(app, testPrefsName)
    }

    @After
    fun tearDown() {
        db.close()
        resetDatabaseSingleton(app)
    }

    @Test
    fun calendarRolloverRunsRepeatedlyAndDoesNotClobberCustom2800EmployerMatch() = runBlocking {
        // User customized profile with a legitimate 2,800 Kč/mo employer match
        val custom = SettingsEntity(
            baseYear = 2026,
            primaryAge = 27,
            vSalary = 78_000.0,
            portuDcaMonthly = 18_000.0,
            employerRetirementMonthly = 2800.0
        )
        repository.saveSettings(custom)
        prefsRepo.hydrate()

        // First startup runs migration check without overwriting custom 2,800 Kč match
        runOneTimeMigrationsInternal(app, repository, prefsRepo)
        assertEquals(2800.0, db.settingsDao().getSettingsDirect()!!.employerRetirementMonthly, 0.001)
        assertTrue(prefsRepo.isFlagSet("legacy_employer_repaired_v1"))

        // Even after legacy flag is set, calendar rollover continues to advance in 2027 and 2028
        repository.rolloverCalendarYearIfNeeded(targetCalendarYear = 2027)
        val at2027 = db.settingsDao().getSettingsDirect()!!
        assertEquals(2027, at2027.baseYear)
        assertEquals(28, at2027.primaryAge)
        assertEquals(2800.0, at2027.employerRetirementMonthly, 0.001)

        repository.rolloverCalendarYearIfNeeded(targetCalendarYear = 2028)
        val at2028 = db.settingsDao().getSettingsDirect()!!
        assertEquals(2028, at2028.baseYear)
        assertEquals(29, at2028.primaryAge)
        assertEquals(2800.0, at2028.employerRetirementMonthly, 0.001)
    }

    @Test
    fun clearAllAndRemoveDoNotResurrectLegacyGhostDataAndSyncSnapshotAndClearThemePrefs() = runBlocking {
        // Seed legacy stores with stale ghost entries + theme prefs
        app.getSharedPreferences("investment_lots_prefs", Context.MODE_PRIVATE).edit()
            .putString(
                AppPreferencesRepository.KEY_LOTS_JSON,
                """[{"id":"ghost1","batchName":"Old","instrument":"VWCE","purchaseDateStr":"2022-01-01","acquisitionCostCzk":50000,"currentValueCzk":60000,"notes":""}]"""
            ).commit()
        app.getSharedPreferences("merchant_category_overrides", Context.MODE_PRIVATE).edit()
            .putString(AppPreferencesRepository.KEY_MERCHANT_OVERRIDES_JSON, """{"ghost shop":"GROCERIES"}""")
            .commit()
        app.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE).edit()
            .putString("theme_mode", "DARK")
            .commit()

        prefsRepo.hydrate()
        assertEquals(1, InvestmentLotManager.getLots(app, testPrefsName).size)
        assertEquals(1, MerchantCategoryManager.getOverrides(app, testPrefsName).size)

        // Clearing lots must NOT fall back to legacy lots store on re-read, and must refresh snapshot immediately
        InvestmentLotManager.clearAll(app, testPrefsName)
        assertTrue(InvestmentLotManager.getLots(app, testPrefsName).isEmpty())
        assertEquals("[]", prefsRepo.snapshot.value.investmentLotsJson)

        // Removing the last merchant rule or clearing rules must NOT resurrect legacy rules
        MerchantCategoryManager.removeOverride(app, "ghost shop", testPrefsName)
        assertTrue(MerchantCategoryManager.getOverrides(app, testPrefsName).isEmpty())
        assertEquals("{}", prefsRepo.snapshot.value.merchantOverridesJson)

        MerchantCategoryManager.saveOverride(app, "Rohlik", BankTransactionType.GROCERIES, testPrefsName)
        assertEquals(BankTransactionType.GROCERIES, MerchantCategoryManager.getOverrides(app, testPrefsName)["rohlik"])
        MerchantCategoryManager.clearOverrides(app, testPrefsName)
        assertTrue(MerchantCategoryManager.getOverrides(app, testPrefsName).isEmpty())
        assertEquals("{}", prefsRepo.snapshot.value.merchantOverridesJson)

        // clearAllUserData must wipe legacy app_theme_prefs as well
        prefsRepo.clearAllUserData()
        assertFalse(app.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE).contains("theme_mode"))
    }

    @Test
    fun resetSettingsToDefaultPreservesLotsAndMerchantRulesWhileClearAllWipesThem() {
        val vm = MainViewModel(app)
        try {
            val deadline = System.currentTimeMillis() + 8000L
            while (System.currentTimeMillis() < deadline && !vm.isPreferencesHydrated.value) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }

            val sampleLot = UserInvestmentLot(
                id = "lot-preserve-1",
                batchName = "VWCE Core",
                instrument = "VWCE.DE",
                purchaseDateStr = "2024-06-15",
                acquisitionCostCzk = 100_000.0,
                currentValueCzk = 120_000.0
            )
            InvestmentLotManager.saveLot(app, sampleLot, testPrefsName)
            MerchantCategoryManager.saveOverride(app, "Košík", BankTransactionType.GROCERIES, testPrefsName)
            vm.updateSettings(
                vm.settingsState.value.copy(
                    vSalary = 88_000.0,
                    merchantRulesJson = """{"kaufland":"GROCERIES"}"""
                )
            )

            val waitUpdate = System.currentTimeMillis() + 8000L
            while (System.currentTimeMillis() < waitUpdate && vm.settingsState.value.vSalary != 88_000.0) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
            // Allow IO updateSettings coroutine + rule sync to settle before resetting
            Thread.sleep(150)
            shadowOf(Looper.getMainLooper()).idle()

            // Updating unrelated salary alone must not wipe direct SharedPreferences rules
            MerchantCategoryManager.saveOverride(app, "Košík", BankTransactionType.GROCERIES, testPrefsName)
            vm.updateSettings(vm.settingsState.value.copy(vSalary = 89_000.0))
            val waitSalary = System.currentTimeMillis() + 8000L
            while (System.currentTimeMillis() < waitSalary && vm.settingsState.value.vSalary != 89_000.0) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
            Thread.sleep(150)

            vm.resetSettingsToDefault()
            val waitReset = System.currentTimeMillis() + 8000L
            while (System.currentTimeMillis() < waitReset && vm.settingsState.value.vSalary == 89_000.0) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
            Thread.sleep(150)

            // Settings reset back to default salary, while lots and merchant rules (both SP & DB) stay intact
            assertEquals(SettingsEntity.freshDefaults().vSalary, vm.settingsState.value.vSalary, 0.01)
            assertEquals(1, InvestmentLotManager.getLots(app, testPrefsName).size)
            assertEquals(BankTransactionType.GROCERIES, MerchantCategoryManager.getOverrides(app, testPrefsName)["kosik"])
            assertTrue(vm.settingsState.value.merchantRulesJson.contains("kaufland"))

            // Full clearAllUserData MUST delete lots & rules
            vm.clearAllUserData()
            val waitClear = System.currentTimeMillis() + 8000L
            while (
                System.currentTimeMillis() < waitClear &&
                (InvestmentLotManager.getLots(app, testPrefsName).isNotEmpty() ||
                    MerchantCategoryManager.getOverrides(app, testPrefsName).isNotEmpty())
            ) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20)
            }
            assertTrue(InvestmentLotManager.getLots(app, testPrefsName).isEmpty())
            assertTrue(MerchantCategoryManager.getOverrides(app, testPrefsName).isEmpty())
            assertEquals("{}", vm.settingsState.value.merchantRulesJson)
        } finally {
            runBlocking {
                val job = vm.viewModelScope.coroutineContext[Job]
                job?.cancel()
                withTimeoutOrNull(3000) { job?.join() }
            }
        }
    }

    @Test
    fun historicalStatementImportDoesNotStampCurrentPortfolioOntoPastMonthsAndPreservesManualReserve() = runBlocking {
        val currentYm = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
        val settings = SettingsEntity(
            liquidPortfolioCurrent = 1_250_000.0,
            eLiquidPortfolioCurrent = 250_000.0,
            dpsBalanceCurrent = 180_000.0,
            dipBalanceCurrent = 120_000.0,
            emergencyReserveCurrent = 350_000.0
        )
        repository.saveSettings(settings)
        prefsRepo.hydrate()

        val coordinator = StatementImportCoordinator(app, repository, prefsRepo)
        val pastYm = "2024-03"
        assertTrue(pastYm < currentYm)
        val pastSummary = StatementParseSummary(
            detectedBank = BankType.CSOB,
            yearMonth = pastYm,
            totalInflows = 50_000.0,
            incVaclav = 50_000.0,
            incEleonora = 0.0,
            incOther = 0.0,
            totalExpenses = 22_000.0,
            expRent = 15_000.0,
            expGroceries = 4_000.0,
            expOther = 3_000.0,
            totalInvested = 0.0,
            invPortu = 0.0,
            invDip = 0.0,
            invDps = 0.0,
            internalTransfersCount = 0,
            monthEndBalance = 64_000.0,
            transactions = listOf(
                ParsedBankTransaction(
                    date = "2024-03-15",
                    amount = 50_000.0,
                    counterpartyName = "Zamestnavatel",
                    category = BankTransactionType.SALARY_VACLAV,
                    bankType = BankType.CSOB
                ),
                ParsedBankTransaction(
                    date = "2024-03-20",
                    amount = -15_000.0,
                    counterpartyName = "Najem",
                    category = BankTransactionType.HOUSING_RENT,
                    bankType = BankType.CSOB
                )
            )
        )

        coordinator.confirmStatementImport(pastSummary)
        val pastEntry = repository.getLedgerEntryByYearMonth(pastYm)!!
        assertEquals("Historical import must NOT stamp today's liquid portfolio", 0.0, pastEntry.portfolioBalanceAtMonthEnd, 0.01)
        assertEquals("Historical import must NOT stamp today's pension balance", 0.0, pastEntry.pensionBalanceAtMonthEnd, 0.01)
        assertEquals("Historical import preserves statement closing balance without copying today's reserve", 64_000.0, pastEntry.emergencyReserveAtMonthEnd, 0.01)

        // Current month import with existing manual emergency reserve preserves the manual reserve
        repository.addLedgerEntry(
            LedgerEntryEntity(
                yearMonth = currentYm,
                emergencyReserveAtMonthEnd = 400_000.0,
                notes = "Manual baseline"
            )
        )
        val curSummary = pastSummary.copy(
            yearMonth = currentYm,
            monthEndBalance = 35_000.0,
            transactions = pastSummary.transactions.map { it.copy(date = "$currentYm-15") }
        )
        coordinator.confirmStatementImport(curSummary)
        val curEntry = repository.getLedgerEntryByYearMonth(currentYm)!!
        assertEquals(400_000.0, curEntry.emergencyReserveAtMonthEnd, 0.01)
    }

    @Test
    fun financialEngineDoesNotLeakMonthlyBonusTo35YearsAndSplitsZeroPensionByContribution() {
        val base = SettingsEntity(
            baseYear = 2026,
            primaryAge = 30,
            vSalary = 55_000.0,
            dpsBalanceCurrent = 0.0,
            dipBalanceCurrent = 0.0,
            dpsOwnContributionMonthly = 1_500.0,
            dipContributionMonthly = 3_000.0
        )
        val bonusLedger = LedgerEntryEntity(
            yearMonth = "2026-08",
            incVaclav = 155_000.0, // includes 100k one-off bonus
            pensionBalanceAtMonthEnd = 90_000.0
        )
        val state = FinancialEngine.calculate(
            settings = base,
            runMonteCarlo = false,
            activeLedgerEntry = bonusLedger,
            activeMonth = 8
        )

        // Active month reflects actual 155k inflow, but 35-year projection settings retain base 55k salary
        assertEquals(155_000.0, state.currentIncome.vaclavNet, 0.01)
        assertEquals(55_000.0, state.effectiveSettings.vSalary, 0.01)

        // Zero initial DPS/DIP balances split 90k pension by contribution ratio (1500/4500 = 1/3 DPS, 2/3 DIP)
        assertEquals(30_000.0, state.effectiveSettings.dpsBalanceCurrent, 0.01)
        assertEquals(60_000.0, state.effectiveSettings.dipBalanceCurrent, 0.01)
    }

    private fun buildAbo074(ownAcc16: String, bankLabel: String, closingCzk: Long, dateDdMmYy: String): String {
        val buf = CharArray(128) { '0' }
        fun put(pos: Int, str: String) {
            str.forEachIndexed { idx, c -> if (pos + idx < buf.size) buf[pos + idx] = c }
        }
        put(0, "074")
        put(3, ownAcc16.padStart(16, '0').take(16))
        put(19, bankLabel.padEnd(20, ' ').take(20))
        put(39, dateDdMmYy)
        put(59, "+")
        put(60, String.format(Locale.US, "%014d", closingCzk * 100))
        put(74, "+")
        put(108, dateDdMmYy)
        return String(buf)
    }

    private fun buildAbo075(
        ownAcc16: String,
        cpAcc16: String,
        amountCzk: Long,
        code: Char,
        bankCode: String,
        dateDdMmYy: String
    ): String {
        val buf = CharArray(128) { '0' }
        fun put(pos: Int, str: String) {
            str.forEachIndexed { idx, c -> if (pos + idx < buf.size) buf[pos + idx] = c }
        }
        put(0, "075")
        put(3, ownAcc16.padStart(16, '0').take(16))
        put(19, cpAcc16.padStart(16, '0').take(16))
        put(48, String.format(Locale.US, "%012d", amountCzk * 100))
        put(60, code.toString())
        put(61, "0000001234")
        put(71, "00")
        put(73, bankCode.padStart(4, '0').take(4))
        put(91, dateDdMmYy)
        put(97, "                    ")
        put(118, "0203")
        put(122, dateDdMmYy)
        return String(buf)
    }

    @Test
    fun deleteImportedStatementCleansDirectBankStatementLedgerRowWithoutGhostEntry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            repository.addLedgerEntry(
                LedgerEntryEntity(
                    yearMonth = "2026-06",
                    incVaclav = 60_000.0,
                    expRent = 20_000.0,
                    notes = "Direct Bank Statement (CSOB)"
                )
            )
            repository.saveImportedTransactionsSmartMerge(
                yearMonth = "2026-06",
                bankName = "CSOB",
                newTransactions = listOf(
                    ImportedBankTransactionEntity(
                        yearMonth = "2026-06",
                        bankName = "CSOB",
                        date = "2026-06-15",
                        amount = 60_000.0,
                        category = BankTransactionType.SALARY_VACLAV.name
                    )
                )
            )
            val coordinator = StatementImportCoordinator(app, repository, prefsRepo)
            val controller = StatementImportController(
                application = app,
                scope = scope,
                repository = repository,
                preferencesRepository = prefsRepo,
                importCoordinator = coordinator,
                liveSettings = MutableStateFlow(SettingsEntity.freshDefaults()),
                pendingSettingsWrites = AtomicInteger(0),
                activeAuditReport = MutableStateFlow<CrossStatementAuditReport?>(null),
                pendingStatementImport = MutableStateFlow<StatementParseSummary?>(null),
                setPendingStatementImport = { _, _ -> },
                pendingEpoch = AtomicLong(0L),
                importMutex = Mutex(),
                emitSnackbar = {},
                importLedgerCsvFallback = { false }
            )

            controller.deleteImportedStatement("2026-06", "CSOB")
            assertNull("Direct Bank Statement ledger entry must be deleted when last statement is removed", repository.getLedgerEntryByYearMonth("2026-06"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun sameBankMultiAccountPreservesIdenticalDepositsBalancesCrossTransferAndFxBackupRoundTrip() = runBlocking {
        // Two ČSOB ABO/GPC statements from two distinct same-bank accounts with identical -1,700 Kč DPS payments on the same day
        val acc1Abo = listOf(
            buildAbo074("0000001112223333", "CSOB VASEK", 80_000L, "310726"),
            buildAbo075("0000001112223333", "0000009998887777", 1_700L, '1', "2010", "310726"),
            "076Generali DPS penzijko",
            buildAbo075("0000001112223333", "0000004445556666", 5_000L, '1', "0300", "310726"),
            "076Prevod na druhy ucet CSOB"
        ).joinToString("\n")
        val acc2Abo = listOf(
            buildAbo074("0000004445556666", "CSOB ELKA", 45_000L, "310726"),
            buildAbo075("0000004445556666", "0000009998887777", 1_700L, '1', "2010", "310726"),
            "076Generali DPS penzijko"
        ).joinToString("\n")

        val s1 = BankStatementImporter.parseStatement(acc1Abo.toByteArray(Charsets.UTF_8))
        val s2 = BankStatementImporter.parseStatement(acc2Abo.toByteArray(Charsets.UTF_8))
        assertTrue("Expected distinct accountKeys, got '${s1.accountKey}' vs '${s2.accountKey}'",
            s1.accountKey.isNotBlank() && s2.accountKey.isNotBlank() && s1.accountKey != s2.accountKey)

        val merged = BankStatementImporter.mergeSummaries(listOf(s1, s2))
        // Neither -1,700 Kč DPS transaction was dropped as a duplicate
        val dpsCount = merged.transactions.count { abs(it.amount + 1700.0) < 0.01 }
        assertEquals(2, dpsCount)

        // Both closing balances survived under account-scoped keys for 2026-07
        val balMap = merged.monthEndBalancesByBank["2026-07"] ?: emptyMap()
        assertEquals(2, balMap.size)
        assertEquals(80_000.0 + 45_000.0, balMap.values.sum(), 0.01)

        // Smart-merge and FX metadata survive DB persistence and BackupManager JSON round-trip
        val txFx = ImportedBankTransactionEntity(
            yearMonth = "2026-07",
            bankName = "CSOB",
            date = "2026-07-31",
            amount = -2500.0,
            counterpartyName = "Amazon.de",
            category = BankTransactionType.SHOPPING_GOODS.name,
            accountKey = s1.accountKey,
            originalCurrency = "EUR",
            originalAmount = -100.0,
            fxRateToCzk = 25.0
        )
        repository.saveImportedTransactionsSmartMerge("2026-07", "CSOB", listOf(txFx), s1.accountKey)
        val persisted = repository.getImportedTransactionsDirect("2026-07").single()
        assertEquals(s1.accountKey, persisted.accountKey)
        assertEquals("EUR", persisted.originalCurrency)
        assertEquals(-100.0, persisted.originalAmount, 0.01)
        assertEquals(25.0, persisted.fxRateToCzk, 0.001)

        val backupJson = BackupManager.serializeFullBackupToJson(
            BackupManager.FullBackupPayload(
                settings = SettingsEntity.freshDefaults(),
                importedTransactions = listOf(persisted)
            )
        )
        val restored = BackupManager.deserializeFullBackupFromJson(backupJson, SettingsEntity.freshDefaults())!!
        val restoredTx = restored.importedTransactions.single()
        assertEquals(persisted.accountKey, restoredTx.accountKey)
        assertEquals("EUR", restoredTx.originalCurrency)
        assertEquals(-100.0, restoredTx.originalAmount, 0.01)
        assertEquals(25.0, restoredTx.fxRateToCzk, 0.001)

        // Same-bank cross-account transfer reconciliation matches debit & credit with different accountKeys
        val debit = ImportedBankTransactionEntity(
            id = 10L,
            yearMonth = "2026-07",
            bankName = "CSOB",
            date = "2026-07-20",
            amount = -5000.0,
            counterpartyAccount = s2.accountKey.ifBlank { "4445556666/0300" },
            counterpartyName = "Partner",
            category = BankTransactionType.UNCATEGORIZED.name,
            accountKey = s1.accountKey
        )
        val credit = ImportedBankTransactionEntity(
            id = 11L,
            yearMonth = "2026-07",
            bankName = "CSOB",
            date = "2026-07-20",
            amount = 5000.0,
            counterpartyAccount = s1.accountKey.ifBlank { "1112223333/0300" },
            counterpartyName = "Owner",
            category = BankTransactionType.UNCATEGORIZED.name,
            accountKey = s2.accountKey
        )
        val (reconciled, audit) = CrossStatementReconciliationEngine.reconcileTransactions(
            transactions = listOf(debit, credit),
            yearMonth = "2026-07"
        )
        assertEquals(1, audit.matchedPairs.size)
        assertTrue(reconciled.all { it.isNetted && it.category == BankTransactionType.INTERNAL_TRANSFER.name })
    }
}
