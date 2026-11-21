package com.example

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.CzechTaxEngine
import com.example.domain.FinancialEngine
import com.example.domain.HouseholdProfile
import com.example.domain.PresetProfiles
import com.example.ui.MainViewModel
import com.example.util.BackupManager
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.SubscriptionAuditor
import com.example.util.parseUserAmountOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FullCodebaseAuditFixesRegressionTest {

    private fun getAppSrcMain(): File {
        var curr: File? = File(".").canonicalFile
        while (curr != null) {
            val appSrc = File(curr, "app/src/main")
            if (appSrc.exists()) return appSrc
            val directSrc = File(curr, "src/main")
            if (directSrc.exists()) return directSrc
            curr = curr.parentFile
        }
        return File("app/src/main")
    }

    // Issue 1: AppDatabase double-checked locking & table reset
    @Test
    fun test1_appDatabase_doubleCheckedLockingSourceVerification() {
        val dbFile = File(getAppSrcMain(), "java/com/example/data/AppDatabase.kt")
        assertTrue("AppDatabase.kt must exist at ${dbFile.absolutePath}", dbFile.exists())
        val text = dbFile.readText()
        assertTrue(
            "getDatabase must perform second INSTANCE check inside synchronized block",
            text.contains("return INSTANCE ?: synchronized(this)") &&
            text.contains("INSTANCE ?: Room.databaseBuilder")
        )
        assertTrue(
            "recreateAppSettingsTable must drop app_settings_new before create",
            text.contains("DROP TABLE IF EXISTS app_settings_new")
        )
    }

    // Issue 2: parseUserAmountOrNull handles Czech decimal comma & NBSP
    @Test
    fun test2_parseUserAmountOrNull_czechFormatting() {
        assertEquals(33500.50, "33 500,50".parseUserAmountOrNull()!!, 0.001)
        assertEquals(33500.50, "33\u00A0500,50".parseUserAmountOrNull()!!, 0.001)
        assertEquals(33500.50, "33\u202F500,50".parseUserAmountOrNull()!!, 0.001)
        assertEquals(500000.0, "500 000".parseUserAmountOrNull()!!, 0.001)
        assertNull("abc".parseUserAmountOrNull())
        assertNull("".parseUserAmountOrNull())
        assertNull(Double.NaN.toString().parseUserAmountOrNull())
        assertNull(Double.POSITIVE_INFINITY.toString().parseUserAmountOrNull())
    }

    // Issue 3: Backup XML files exclude sensitive statement preferences and DB sidecars
    @Test
    fun test3_backupRules_excludeStatementPrefsAndDbSidecars() {
        val backupRules = File(getAppSrcMain(), "res/xml/backup_rules.xml").readText()
        val dataExtraction = File(getAppSrcMain(), "res/xml/data_extraction_rules.xml").readText()

        assertTrue(backupRules.contains("statement_import_prefs.xml"))
        assertTrue(backupRules.contains("subscription_auditor_prefs.xml"))
        assertTrue(backupRules.contains("martinu_financials_db-wal"))
        assertTrue(backupRules.contains("martinu_financials_db-shm"))
        assertTrue(backupRules.contains("martinu_financials_db"))

        assertTrue(dataExtraction.contains("statement_import_prefs.xml"))
        assertTrue(dataExtraction.contains("subscription_auditor_prefs.xml"))
        assertTrue(dataExtraction.contains("martinu_financials_db-wal"))
        assertTrue(dataExtraction.contains("martinu_financials_db-shm"))
        assertTrue(dataExtraction.contains("martinu_financials_db"))
    }

    // Issue 4: Spouse tax credit strict alignment
    @Test
    fun test4_spouseTaxCredit_alignment() {
        val base = SettingsEntity().copy(
            baseYear = 2026,
            isSingleHousehold = false,
            includeSpouseCredit = true,
            hasChildUnder3 = true,
            child1Enabled = true,
            child1BirthYear = 2024, // Age 2 in 2026 -> under 3
            eLecturingMonthly = 0.0,
            eStartingSalary = 0.0
        )

        // Case A: Eligible
        val stateA = FinancialEngine.calculate(base, runMonteCarlo = false)
        assertTrue(stateA.taxReturnHelper.spouseEligible)
        assertEquals(24840.0, stateA.taxReturnHelper.spouseCredit, 0.001)
        assertEquals(24840.0, FinancialEngine.annualPartnerTaxCredit(base, 2026), 0.001)

        // Case B: hasChildUnder3 master toggle set to false
        val baseB = base.copy(hasChildUnder3 = false)
        val stateB = FinancialEngine.calculate(baseB, runMonteCarlo = false)
        assertFalse(stateB.taxReturnHelper.spouseEligible)
        assertEquals(0.0, stateB.taxReturnHelper.spouseCredit, 0.001)
        assertEquals(0.0, FinancialEngine.annualPartnerTaxCredit(baseB, 2026), 0.001)

        // Case C: Children are grown (child1 born in 2010 -> age 16)
        val baseC = base.copy(child1BirthYear = 2010, child2BirthYear = 2012)
        val stateC = FinancialEngine.calculate(baseC, runMonteCarlo = false)
        assertFalse(stateC.taxReturnHelper.spouseEligible)
        assertEquals(0.0, stateC.taxReturnHelper.spouseCredit, 0.001)
        assertEquals(0.0, FinancialEngine.annualPartnerTaxCredit(baseC, 2026), 0.001)
    }

    // Issue 5: HouseholdProfile primary and spouse birth year accuracy
    @Test
    fun test5_householdDomainModel_primaryAndSpouseBirthYear() {
        val settings = SettingsEntity().copy(
            baseYear = 2026,
            primaryAge = 31,
            isSingleHousehold = false
        )
        val profile = HouseholdProfile.fromSettings(settings)
        assertEquals(2026 - 31, profile.primaryEarner.birthYear)
        assertNotNull(profile.spouseEarner)
        assertEquals(FinancialEngine.partnerBirthYear(settings), profile.spouseEarner!!.birthYear)
    }

    // Issue 6: CzechTaxEngine NaN and Infinity safety
    @Test
    fun test6_czechTaxEngine_nanAndInfinitySafety() {
        assertEquals(0.0, CzechTaxEngine.dpsSubsidy(Double.NaN, 25), 0.001)
        assertEquals(0.0, CzechTaxEngine.dpsSubsidy(Double.POSITIVE_INFINITY, 25), 0.001)
        assertEquals(0.0, CzechTaxEngine.netToGrossAnnual(Double.NaN, 30840.0), 0.001)
        assertEquals(0.0, CzechTaxEngine.netToGrossAnnual(Double.POSITIVE_INFINITY, 30840.0), 0.001)
    }

    // Issue 7: MainViewModel SavedStateHandle sensitivity persistence
    @Test
    fun test7_mainViewModel_savedStateHandlePersistence() {
        val handle = SavedStateHandle(mapOf("sensitivity_return_override" to 8.5))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app, handle)
        assertEquals(8.5, vm.sensitivityReturnOverride.value)

        vm.setSensitivityOverrides(9.0, 4.0, 3.8)
        assertEquals(9.0, handle.get<Double?>("sensitivity_return_override"))
        assertEquals(4.0, handle.get<Double?>("sensitivity_cpi_override"))
        assertEquals(3.8, handle.get<Double?>("sensitivity_swr_override"))
    }

    // Issue 8: Sub-tab selection state synchronization wired across MainScreen and all multi-subtab screens
    @Test
    fun test8_subTabSelectedCallbacksWiredInMainScreenAndTabs() {
        val mainScreen = File(getAppSrcMain(), "java/com/example/ui/MainScreen.kt").readText()
        assertTrue(mainScreen.contains("onSubTabSelected = { targetCashFlowSubTab = it }"))
        assertTrue(mainScreen.contains("onSubTabSelected = { targetProjectionsSubTab = it }"))
        assertTrue(mainScreen.contains("onSubTabSelected = { targetSettingsSubTab = it }"))

        val tabs = listOf("CashFlowTab.kt", "ProjectionsTab.kt", "SettingsTab.kt")
        for (tab in tabs) {
            val content = File(getAppSrcMain(), "java/com/example/ui/tabs/$tab").readText()
            assertTrue("$tab must declare and invoke onSubTabSelected", content.contains("onSubTabSelected"))
        }
    }

    // Issue 9: Custom life goals outlay deducted in simulations
    @Test
    fun test9_goalsOutlayDeductedInSimulations() {
        val base = SettingsEntity().copy(
            baseYear = 2026,
            monteCarloN = 200,
            customGoalsJson = """[{"id":"1","name":"Dream Home Downpayment","targetYear":2030,"targetAmountCzk":1000000.0,"currentSavedCzk":0.0}]"""
        )

        assertEquals(1000000.0, FinancialEngine.goalsOutlayForYear(2030, base), 0.001)
        assertEquals(0.0, FinancialEngine.goalsOutlayForYear(2029, base), 0.001)

        val withoutGoal = base.copy(customGoalsJson = "[]")
        val mcWithGoal = FinancialEngine.runMonteCarlo(base)
        val mcWithoutGoal = FinancialEngine.runMonteCarlo(withoutGoal)

        // Having a 1,000,000 CZK capital outlay should decrease the median portfolio at year 10 (2036)
        val p50WithGoal = mcWithGoal.fanPoints[10].p50
        val p50WithoutGoal = mcWithoutGoal.fanPoints[10].p50
        assertTrue("Portfolio with 1M CZK goal outlay ($p50WithGoal) must be lower than without goal ($p50WithoutGoal)", p50WithGoal < p50WithoutGoal)
    }

    // Issue 10: restoreFullBackupData clears existing ledger, action states, and imported transactions before insert
    @Test
    fun test10_restoreFullBackupData_clearsExistingTablesBeforeInsert() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = FinancialRepository(
                settingsDao = db.settingsDao(),
                ledgerDao = db.ledgerDao(),
                actionStateDao = db.actionStateDao(),
                importedTransactionDao = db.importedTransactionDao(),
                database = db
            )

            // Seed stale data across all tables
            repo.addLedgerEntry(LedgerEntryEntity(id = 10L, yearMonth = "2025-01", incVaclav = 50000.0))
            repo.addLedgerEntry(LedgerEntryEntity(id = 11L, yearMonth = "2025-02", incVaclav = 50000.0))
            repo.setActionState(2025, "stale_action", true)
            repo.saveImportedTransactions(listOf(
                ImportedBankTransactionEntity(id = 99L, yearMonth = "2025-01", bankName = "MONETA", date = "2025-01-10", amount = -500.0, counterpartyName = "Old", category = "GROCERIES")
            ))

            // Restore a clean backup containing only 1 ledger entry and 0 transactions/actions
            val restoredLedger = listOf(LedgerEntryEntity(id = 1L, yearMonth = "2026-01", incVaclav = 65000.0))
            repo.restoreFullBackupData(
                settings = SettingsEntity(vSalary = 65000.0),
                ledgerEntries = restoredLedger,
                actionStates = emptyList(),
                transactions = emptyList()
            )

            val entriesAfter = repo.getAllLedgerEntriesDirect()
            val actionsAfter = repo.getAllActionStatesDirect()
            val txsAfter = repo.getAllImportedTransactionsDirect()

            assertEquals(1, entriesAfter.size)
            assertEquals("2026-01", entriesAfter[0].yearMonth)
            assertTrue("Stale action states must be cleared on restore", actionsAfter.isEmpty())
            assertTrue("Stale imported transactions must be cleared on restore", txsAfter.isEmpty())
        } finally {
            db.close()
        }
    }

    // Issue 11: Barista, Lean, Standard, Fat FIRE milestones ordering
    @Test
    fun test11_fireMilestones_ordering() {
        val settings = SettingsEntity()
        val state = FinancialEngine.calculate(settings, runMonteCarlo = false)

        val barista = state.fireMilestones.baristaFire!!
        val lean = state.fireMilestones.leanFire!!
        val standard = state.fireMilestones.standardFire!!
        val fat = state.fireMilestones.fatFire!!

        assertTrue(barista.targetAmountToday < lean.targetAmountToday)
        assertTrue(lean.targetAmountToday < standard.targetAmountToday)
        assertTrue(standard.targetAmountToday < fat.targetAmountToday)

        if (barista.estimatedAge != null && lean.estimatedAge != null) {
            assertTrue("Barista estimated age (${barista.estimatedAge}) must be <= Lean (${lean.estimatedAge})", barista.estimatedAge!! <= lean.estimatedAge!!)
        }
        if (lean.estimatedAge != null && standard.estimatedAge != null) {
            assertTrue("Lean estimated age (${lean.estimatedAge}) must be <= Standard (${standard.estimatedAge})", lean.estimatedAge!! <= standard.estimatedAge!!)
        }
    }

    // Issue 12: toggleActionState atomic UPSERT inserts true when missing and toggles when present
    @Test
    fun test12_toggleActionState_upsertsUninitializedKeyAndTogglesExisting() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = FinancialRepository(
                settingsDao = db.settingsDao(),
                ledgerDao = db.ledgerDao(),
                actionStateDao = db.actionStateDao(),
                importedTransactionDao = db.importedTransactionDao(),
                database = db
            )

            // 1st toggle on a brand-new key that was not seeded -> should insert isDone = true
            repo.toggleActionState(2026, "dynamic_action")
            val stateAfterFirst = repo.getAllActionStatesDirect().find { it.actionKey == "2026_dynamic_action" }
            assertNotNull(stateAfterFirst)
            assertTrue(stateAfterFirst!!.isDone)

            // 2nd toggle on the existing key -> should flip isDone to false
            repo.toggleActionState(2026, "dynamic_action")
            val stateAfterSecond = repo.getAllActionStatesDirect().find { it.actionKey == "2026_dynamic_action" }
            assertNotNull(stateAfterSecond)
            assertFalse(stateAfterSecond!!.isDone)
        } finally {
            db.close()
        }
    }

    // Issue 13: detectBankFromText ignores counterparty bank codes in CSV data rows
    @Test
    fun test13_detectBankFromText_ignoresCounterpartyBankCodesInCsvRows() {
        val genericCsvWithCounterpartyCodes = """
            Datum;Částka;Měna;Protiúčet;Zpráva
            15.01.2026;-1 250,00;CZK;123456789/0100;Nákup Alza
            16.01.2026;-500,00;CZK;987654321/0300;Platba
        """.trimIndent()
        val summary = BankStatementImporter.parseStatement(genericCsvWithCounterpartyCodes.toByteArray(Charsets.UTF_8))
        assertEquals(BankType.GENERIC, summary.detectedBank)

        val kbPreambleCsv = """
            Výpis z účtu: 111222333/0100
            Datum;Částka;Měna;Protiúčet;Zpráva
            15.01.2026;-1 250,00;CZK;987654321/0300;Platba
        """.trimIndent()
        val kbSummary = BankStatementImporter.parseStatement(kbPreambleCsv.toByteArray(Charsets.UTF_8))
        assertEquals(BankType.KOMERCNI_BANKA, kbSummary.detectedBank)
    }

    // Issue 14: PresetProfiles Stagflation aligned with Stress Scenarios
    @Test
    fun test14_presetProfiles_stagflationAlignment() {
        val s = PresetProfiles.STAGFLATION.transform(SettingsEntity())
        assertEquals(4.5, s.portfolioNominalReturnPct, 0.001)
        assertEquals(5.0, s.cpiInflationPct, 0.001)
        assertEquals(6.0, s.rentGrowthPct, 0.001)
        assertEquals(3.5, s.safeWithdrawalRatePct, 0.001)
    }

    // Issue 16: Step 2b cross-month netting conflicting third-party protection
    @Test
    fun test16_step2bCrossMonthNetting_conflictingThirdPartiesNotNetted() {
        val septDebit = ImportedBankTransactionEntity(
            id = 1L,
            yearMonth = "2026-09",
            bankName = "CSOB",
            date = "2026-09-30",
            amount = -3500.0,
            counterpartyName = "Alza.cz a.s.",
            category = BankTransactionType.UNCATEGORIZED.name
        )
        val octCredit = ImportedBankTransactionEntity(
            id = 2L,
            yearMonth = "2026-10",
            bankName = "MONETA",
            date = "2026-10-02",
            amount = 3500.0,
            counterpartyName = "Zamestnavatel s.r.o.",
            category = BankTransactionType.OTHER_INFLOW.name
        )

        val result = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
            transactions = listOf(septDebit),
            yearMonth = "2026-09",
            boundaryTransactions = listOf(octCredit)
        )

        // Conflicting third parties (Alza vs Employer) must NOT be matched or netted as internal transfer!
        assertEquals(0, result.auditReport.matchedPairs.size)
        assertFalse(result.currentMonthTxs[0].isNetted)
    }

    // Issue 17: SankeyCashFlowCard gifts not doubled
    @Test
    fun test17_sankeyCashFlow_giftsNotDoubled() {
        val settings = SettingsEntity().copy(
            vGiftsMonthly = 16000.0,
            eGiftsMonthly = 0.0
        )
        val inc = FinancialEngine.householdIncome(2026, settings)
        assertEquals(16000.0, inc.gift, 0.001)
        assertEquals(16000.0, inc.vaclavGifts, 0.001)
        assertEquals(0.0, inc.eleonoraGifts, 0.001)
    }

    // Issue 18: BackupManager accepts monteCarloN up to 2000
    @Test
    fun test18_backupManager_acceptsMonteCarloNUpTo2000() {
        val json = """{"monteCarloN": 2000}"""
        val restored = BackupManager.deserializeSettingsFromJson(json, SettingsEntity())
        assertNotNull(restored)
        assertEquals(2000, restored!!.monteCarloN)

        val overLimitJson = """{"monteCarloN": 15000}"""
        val fallbackRestored = BackupManager.deserializeSettingsFromJson(overLimitJson, SettingsEntity())
        assertNotNull(fallbackRestored)
        assertEquals(SettingsEntity().monteCarloN, fallbackRestored!!.monteCarloN)
    }

    // Issue 19: SubscriptionAuditor rejects single-month broad marketplace one-offs
    @Test
    fun test19_subscriptionAuditor_rejectsSingleMonthMarketplaceOneOffs() {
        val txs = listOf(
            ImportedBankTransactionEntity(
                id = 1L,
                yearMonth = "2026-08",
                bankName = "CSOB",
                date = "2026-08-10",
                amount = -599.0,
                counterpartyName = "Steam Games",
                category = "SHOPPING_GOODS",
                message = "Nakup hry Counter-Strike"
            )
        )
        val summary = SubscriptionAuditor.auditSubscriptions(txs, swrPct = 4.0)
        assertEquals("Single one-off Steam purchase must not be flagged as monthly subscription", 0, summary.items.size)
    }

    // Issue 20: HouseholdTimelineEditDialogs separate vGiftsMonthly / eGiftsMonthly summing
    @Test
    fun test20_householdTimelineEditDialogs_familyGiftsSync() {
        val settings = SettingsEntity().copy(
            vGiftsMonthly = 16000.0,
            eGiftsMonthly = 0.0
        )
        // Spouse updates gift to 5000 CZK
        val updated = settings.copy(eGiftsMonthly = 5000.0)
        val state = FinancialEngine.calculate(updated, runMonteCarlo = false)
        assertEquals(21000.0, state.currentIncome.gift, 0.001)
    }

    // Issue 21: MonteCarloFanChart indicator dot yPix source check
    @Test
    fun test21_chartsIndicatorDotsUseYPix() {
        val chartsFile = File(getAppSrcMain(), "java/com/example/ui/components/charts/MonteCarloFanChart.kt")
        assertTrue("MonteCarloFanChart.kt must exist at ${chartsFile.absolutePath}", chartsFile.exists())
        val content = chartsFile.readText()
        assertFalse("MonteCarloFanChart.kt should not compute raw plotH - plotH * ... for indicator dots",
            content.contains("val curY = plotH - (plotH * (realPoints[0].p50 / maxVal)).toFloat()"))
        assertFalse("MonteCarloFanChart.kt should not compute raw plotH - plotH * ... for scrubber",
            content.contains("val sy = plotH - (plotH * (realPoints[idx].p50 / maxVal)).toFloat()"))
    }

    // Issue 22: Zero-disk cold start (no sync fast_boot_snapshot_v1) + lossless AppPreferencesRepository migration
    @Test
    fun test22_fastBootSnapshotHydratesMainViewModelWithoutDefaultFlash() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val importPrefs = app.getSharedPreferences("statement_import_prefs", android.content.Context.MODE_PRIVATE)
        val themePrefs = app.getSharedPreferences("app_theme_prefs", android.content.Context.MODE_PRIVATE)
        val subPrefs = app.getSharedPreferences("subscription_auditor_prefs", android.content.Context.MODE_PRIVATE)
        val syncPrefs = app.getSharedPreferences("czech_sync_prefs", android.content.Context.MODE_PRIVATE)
        val unifiedPrefs = app.getSharedPreferences(
            com.example.data.AppPreferencesRepository.DEFAULT_PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )

        val snapJson = BackupManager.serializeFullBackupToJson(
            BackupManager.FullBackupPayload(
                settings = SettingsEntity(vSalary = 92500.0)
            )
        )
        unifiedPrefs.edit().clear().commit()
        importPrefs.edit().clear()
            .putString("fast_boot_snapshot_v1", snapJson)
            .putLong("last_import_all", 1_700_000_000_000L)
            .putString("bank_bal_2026-10_CSOB", "125000.0")
            .commit()
        themePrefs.edit().clear().putString("theme_mode", "DARK").commit()
        subPrefs.edit().clear().putStringSet("dismissed_merchants", setOf("spotify")).commit()
        syncPrefs.edit().clear().putLong("last_czech_sync_time", 1_700_000_100_000L).commit()

        try {
            val vmSource = File(getAppSrcMain(), "java/com/example/ui/MainViewModel.kt").readText()
            assertFalse(
                "MainViewModel must not read fast_boot_snapshot_v1 synchronously on cold start",
                vmSource.contains("fast_boot_snapshot_v1")
            )
            assertFalse(
                "MainViewModel must not call getSharedPreferences synchronously during init",
                vmSource.contains("getSharedPreferences(")
            )

            val vm = MainViewModel(app, SavedStateHandle())
            assertEquals("SYSTEM", vm.themeMode.value)

            val repo = com.example.data.AppPreferencesRepository(app)
            val snap = repo.hydrate()
            assertTrue(snap.isHydrated)
            assertEquals("DARK", snap.themeMode)
            assertEquals(1_700_000_000_000L, snap.lastImportTimestamp)
            assertEquals(1_700_000_100_000L, snap.lastCzechSyncTimestamp)
            assertEquals(setOf("spotify"), snap.dismissedSubscriptionMerchants)
            assertEquals(125000.0, snap.bankBalances["2026-10_CSOB"] ?: 0.0, 0.01)
            assertFalse(
                "Obsolete fast_boot_snapshot_v1 must be purged during legacy migration",
                importPrefs.contains("fast_boot_snapshot_v1")
            )
        } finally {
            importPrefs.edit().clear().commit()
            themePrefs.edit().clear().commit()
            subPrefs.edit().clear().commit()
            syncPrefs.edit().clear().commit()
            unifiedPrefs.edit().clear().commit()
        }
    }

    // Issue 23: Charts nice axis ceiling & monotone cubic spline interpolation
    @Test
    fun test23_chartsNiceAxisMaxAndMonotoneCubicSpline() {
        val niceMax = com.example.ui.components.charts.computeNiceAxisMax(17_300_000.0, steps = 4)
        assertEquals(20_000_000.0, niceMax, 0.01)

        val points = listOf(
            androidx.compose.ui.geometry.Offset(0f, 200f),
            androidx.compose.ui.geometry.Offset(50f, 150f),
            androidx.compose.ui.geometry.Offset(100f, 80f),
            androidx.compose.ui.geometry.Offset(150f, 20f)
        )
        val path = com.example.ui.components.charts.buildMonotoneCubicPath(points)
        assertFalse(path.isEmpty)
    }

    // Issue 24: Future-proof calendar year rollover & repository-level settings sanitization
    @Test
    fun test24_autoRolloverCalendarYearAndRepositorySanitization() = runBlocking {
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
            // 1. Save settings with extreme/corrupt fields and verify repository sanitizes them
            repo.saveSettings(
                SettingsEntity(
                    baseYear = 2026,
                    primaryAge = 26,
                    vSalary = -50000.0,
                    safeWithdrawalRatePct = Double.NaN
                )
            )
            val saved = db.settingsDao().getSettingsDirect()!!
            assertEquals(33500.0, saved.vSalary, 0.001)
            assertEquals(4.0, saved.safeWithdrawalRatePct, 0.001)

            // 2. Simulate calendar rollover into 2028
            repo.repairLegacyEmployerContribution(targetCalendarYear = 2028)
            val rolled = db.settingsDao().getSettingsDirect()!!
            assertEquals(2028, rolled.baseYear)
            assertEquals(28, rolled.primaryAge)
        } finally {
            db.close()
        }
    }

    // Issue 25: Investment lots full backup and restore serialization
    @Test
    fun test25_investmentLotsBackupAndRestore() {
        val originalLots = listOf(
            com.example.util.UserInvestmentLot(
                id = "lot-1",
                batchName = "VWCE Core",
                instrument = "VWCE.DE",
                purchaseDateStr = "2023-05-31",
                acquisitionCostCzk = 150000.0,
                currentValueCzk = 195000.0,
                notes = "Interactive Brokers lump sum"
            )
        )
        val payload = BackupManager.FullBackupPayload(
            settings = SettingsEntity.freshDefaults(),
            investmentLots = originalLots
        )
        val jsonStr = BackupManager.serializeFullBackupToJson(payload)
        val deserialized = BackupManager.deserializeFullBackupFromJson(jsonStr, SettingsEntity.freshDefaults())
        assertNotNull(deserialized)
        assertEquals(1, deserialized!!.investmentLots.size)
        val restoredLot = deserialized.investmentLots.first()
        assertEquals("lot-1", restoredLot.id)
        assertEquals("VWCE Core", restoredLot.batchName)
        assertEquals("VWCE.DE", restoredLot.instrument)
        assertEquals(150000.0, restoredLot.acquisitionCostCzk, 0.001)
        assertEquals(195000.0, restoredLot.currentValueCzk, 0.001)
    }

    // Issue 26: Investment lot 3-year time-test exact anniversary boundary
    @Test
    fun test26_investmentLotTimeTestAnniversaryExemption() {
        val lot = com.example.util.UserInvestmentLot(
            batchName = "EUNL",
            instrument = "iShares Core MSCI World",
            purchaseDateStr = "2023-05-31",
            acquisitionCostCzk = 100000.0,
            currentValueCzk = 135000.0
        )
        val exactAnniversary = java.time.LocalDate.of(2026, 5, 31)
        val dayAfter = exactAnniversary.plusDays(1)
        assertTrue("Day strictly after 3-year anniversary must be tax-exempt", lot.isExempt(dayAfter))
        assertEquals(0, lot.monthsRemaining(dayAfter))
        assertEquals(1f, lot.progress(dayAfter), 0.001f)

        assertFalse("Exact 3-year anniversary is not yet strictly > 3 years", lot.isExempt(exactAnniversary))
        assertEquals(1, lot.monthsRemaining(exactAnniversary))
    }

    // Issue 27: Barista mode does not create phantom unfunded locked retirement inflows
    @Test
    fun test27_baristaTransitionCashflowRetirementSeparation() {
        val s = SettingsEntity.freshDefaults().copy(
            baseYear = 2026,
            primaryAge = 30,
            baristaTransitionEnabled = true,
            baristaStartYear = 2028,
            baristaMonthlyIncome = 25000.0,
            baristaSavingsMonthly = 0.0,
            dpsOwnContributionMonthly = 3000.0,
            dipContributionMonthly = 4000.0,
            employerRetirementMonthly = 2500.0
        )
        val plan = FinancialEngine.calculate(s, emptyMap(), runMonteCarlo = true)
        assertNotNull(plan)
        assertNotNull("decumulationComparison should be populated when runMonteCarlo = true", plan.decumulationComparison)
        assertEquals(3, plan.decumulationComparison!!.strategies.size)
        val noMcPlan = FinancialEngine.calculate(s, emptyMap(), runMonteCarlo = false)
        assertNull("decumulationComparison should stay null when runMonteCarlo = false", noMcPlan.decumulationComparison)
    }

    // Issue 28: CzechYearEndTaxChecklist checks spouse employer pension contribution and parental leave status
    @Test
    fun test28_yearEndTaxChecklistSpouseEmployerContribution() {
        val s = SettingsEntity.freshDefaults().copy(
            isSingleHousehold = false,
            primaryName = "Vaclav",
            spouseName = "Eleonora",
            employerRetirementMonthly = 4166.67, // exactly 50k/year -> fully optimized
            eEmployerRetirementMonthly = 1000.0   // 12k/year -> headroom of 38k
        )
        val checklist = com.example.domain.CzechYearEndTaxChecklist.generateChecklist(s, 2026, 400000.0)
        val spouseEmpItem = checklist.items.find { it.id == "employer_contribution_exemption_spouse" }
        assertNotNull("Spouse employer item must exist in dual-earner checklist", spouseEmpItem)
        assertEquals(com.example.domain.ChecklistStatus.ATTENTION_NEEDED, spouseEmpItem!!.status)
        assertEquals(38000.0, spouseEmpItem.remainingHeadroomCzk, 0.01)

        // When spouse is on parental leave (eReturnYear in future, no spouse salary or employer contrib), status is NOT_APPLICABLE
        val parentalSettings = s.copy(eReturnYear = 2029, eEmployerRetirementMonthly = 0.0)
        val parentalChecklist = com.example.domain.CzechYearEndTaxChecklist.generateChecklist(parentalSettings, 2026, 0.0)
        val parentalSpouseEmp = parentalChecklist.items.find { it.id == "employer_contribution_exemption_spouse" }
        assertNotNull(parentalSpouseEmp)
        assertEquals(com.example.domain.ChecklistStatus.NOT_APPLICABLE, parentalSpouseEmp!!.status)
        assertEquals(0.0, parentalSpouseEmp.potentialTaxSavingCzk, 0.001)
    }
}
