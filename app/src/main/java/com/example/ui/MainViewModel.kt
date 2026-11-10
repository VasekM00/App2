package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.example.data.AppDatabase
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth

sealed interface UiMessage {
    data class ShowSnackbar(val message: String) : UiMessage
}

class MainViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : AndroidViewModel(application) {

    // Architectural Note (F-018): Lightweight manual dependency injection.
    // Avoids annotation processor and code-gen overhead of Hilt/Dagger while maintaining clear separation.
    private val db = AppDatabase.getDatabase(application)
    private val repository = FinancialRepository(db, db.settingsDao(), db.ledgerDao(), db.actionStateDao(), db.importedTransactionDao())

    private val _uiEvent = MutableSharedFlow<UiMessage>()
    val uiEvent: SharedFlow<UiMessage> = _uiEvent.asSharedFlow()

    // Statement import recency tracking & synchronous fast-boot snapshot cache
    private val importPrefs = application.getSharedPreferences("statement_import_prefs", android.content.Context.MODE_PRIVATE)
    private val bootCachePayload = runCatching {
        importPrefs.getString("fast_boot_snapshot_v1", null)?.let { com.example.util.BackupManager.deserializeFullBackupFromJson(it, SettingsEntity.freshDefaults()) }
    }.getOrNull()
    private val initialSettings: SettingsEntity = bootCachePayload?.settings ?: SettingsEntity.freshDefaults()
    private val initialLedger: List<LedgerEntryEntity> = bootCachePayload?.ledgerEntries ?: emptyList()
    private val initialActions: Map<String, Boolean> = bootCachePayload?.actionStates?.associate { it.actionKey to it.isDone } ?: emptyMap()

    val ledgerEntries: StateFlow<List<LedgerEntryEntity>> = repository.ledgerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialLedger)

    val actionStates: StateFlow<Map<String, Boolean>> = repository.actionStatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialActions)

    val settingsState: StateFlow<SettingsEntity> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialSettings)

    val importedBankSourcesByMonth: StateFlow<Map<String, Set<String>>> = repository.getAllImportedTransactions()
        .map { txList -> txList.groupBy { it.yearMonth }.mapValues { (_, txs) -> txs.map { it.bankName }.filter { it.isNotBlank() }.toSet() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allImportedTransactions: StateFlow<List<com.example.data.ImportedBankTransactionEntity>> = repository.getAllImportedTransactions()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sensitivity slider override states backed by savedStateHandle
    val sensitivityReturnOverride = MutableStateFlow<Double?>(savedStateHandle.get<Double?>("sensitivity_return_override"))
    val sensitivityCpiOverride = MutableStateFlow<Double?>(savedStateHandle.get<Double?>("sensitivity_cpi_override"))
    val sensitivitySwrOverride = MutableStateFlow<Double?>(savedStateHandle.get<Double?>("sensitivity_swr_override"))

    // Live Czech Economic & Regulatory Sync state
    val liveRegulatoryData = MutableStateFlow<com.example.domain.CzechRegulatoryData?>(null)
    val isSyncing = MutableStateFlow(false)

    // Pending bank statement import review
    private val _pendingStatementImport = MutableStateFlow<com.example.util.StatementParseSummary?>(null)
    val pendingStatementImport: StateFlow<com.example.util.StatementParseSummary?> = _pendingStatementImport.asStateFlow()

    private fun setPendingStatementImport(summary: com.example.util.StatementParseSummary?) {
        _pendingStatementImport.value = summary
    }

    // Statement import recency tracking
    private val _lastImportTimestamp = MutableStateFlow<Long?>(
        importPrefs.getLong("last_import_all", 0L).let { if (it > 0L) it else null }
    )
    val lastImportTimestamp: StateFlow<Long?> = _lastImportTimestamp.asStateFlow()

    // Czech economic sync recency tracking
    private val syncPrefs = application.getSharedPreferences("czech_sync_prefs", android.content.Context.MODE_PRIVATE)
    private val _lastCzechSyncTimestamp = MutableStateFlow<Long?>(syncPrefs.getLong("last_czech_sync_time", 0L).let { if (it > 0L) it else null })
    val lastCzechSyncTimestamp: StateFlow<Long?> = _lastCzechSyncTimestamp.asStateFlow()

    // Dismissed recurring subscriptions tracking
    private val subscriptionPrefs = application.getSharedPreferences("subscription_auditor_prefs", android.content.Context.MODE_PRIVATE)
    private val _dismissedSubscriptionMerchants = MutableStateFlow<Set<String>>(subscriptionPrefs.getStringSet("dismissed_merchants", emptySet()) ?: emptySet())
    val dismissedSubscriptionMerchants: StateFlow<Set<String>> = _dismissedSubscriptionMerchants.asStateFlow()

    init {
        viewModelScope.launch { repository.repairLegacyEmployerContribution() }
        viewModelScope.launch(Dispatchers.IO) {
            if (!importPrefs.getBoolean("legacy_balances_backfilled", false)) {
                val entries = repository.getAllLedgerEntriesDirect()
                if (entries.isNotEmpty()) {
                    val cur = repository.settingsFlow.first()
                    val isSingle = cur.isSingleHousehold
                    val snapLiquid = cur.liquidPortfolioCurrent + if (!isSingle) cur.eLiquidPortfolioCurrent else 0.0
                    val snapPension = cur.dipBalanceCurrent + cur.dpsBalanceCurrent + if (!isSingle) (cur.eDipBalanceCurrent + cur.eDpsBalanceCurrent) else 0.0
                    val toUpdate = mutableListOf<LedgerEntryEntity>()
                    for (e in entries) {
                        if (e.portfolioBalanceAtMonthEnd <= 0.0 && e.totalNetWorthAtMonthEnd <= 0.0) {
                            val reserve = if (e.emergencyReserveAtMonthEnd > 0.0) e.emergencyReserveAtMonthEnd else cur.emergencyReserveCurrent
                            toUpdate.add(e.copy(portfolioBalanceAtMonthEnd = snapLiquid, pensionBalanceAtMonthEnd = snapPension, emergencyReserveAtMonthEnd = reserve))
                        }
                    }
                    if (toUpdate.isNotEmpty()) repository.updateLedgerEntries(toUpdate)
                }
                importPrefs.edit().putBoolean("legacy_balances_backfilled", true).apply()
            }
            if (!importPrefs.getBoolean("merchant_rules_unified_v1", false)) {
                val curSettings = repository.settingsFlow.first()
                val spRules = com.example.util.MerchantCategoryManager.getOverrides(application)
                val dbRules = com.example.util.MerchantCategoryManager.parseRulesFromJson(curSettings.merchantRulesJson)
                if (spRules.isNotEmpty() || dbRules.isNotEmpty()) {
                    val merged = spRules + dbRules
                    val mergedJson = com.example.util.MerchantCategoryManager.serializeRulesToJson(merged)
                    if (mergedJson != curSettings.merchantRulesJson) {
                        repository.saveSettings(curSettings.copy(merchantRulesJson = mergedJson))
                    }
                    com.example.util.MerchantCategoryManager.clearOverrides(application)
                    merged.forEach { (k, v) -> com.example.util.MerchantCategoryManager.saveOverride(application, k, v) }
                }
                importPrefs.edit().putBoolean("merchant_rules_unified_v1", true).apply()
            }
        }
    }

    fun syncLiveCzechData() {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                val data = com.example.util.CzechEconomicSyncService.fetchLiveRegulatoryData()
                liveRegulatoryData.value = data
                val now = System.currentTimeMillis()
                syncPrefs.edit().putLong("last_czech_sync_time", now).apply()
                _lastCzechSyncTimestamp.value = now
                _uiEvent.emit(UiMessage.ShowSnackbar("Czech benchmarks fetched from ${data.sourceName}"))
            } catch (e: Exception) {
                val now = System.currentTimeMillis()
                syncPrefs.edit().putLong("last_czech_sync_time", now).apply()
                _lastCzechSyncTimestamp.value = now
                _uiEvent.emit(UiMessage.ShowSnackbar("Sync completed with fallback statutory parameters"))
            } finally {
                isSyncing.value = false
            }
        }
    }

    private data class SensitivityOverrides(val retOver: Double?, val cpiOver: Double?, val swrOver: Double?)
    private val sensitivityOverrides = combine(sensitivityReturnOverride, sensitivityCpiOverride, sensitivitySwrOverride) { r, c, s -> SensitivityOverrides(r, c, s) }

    val calculationState: StateFlow<FullCalculationState> = combine(
        repository.settingsFlow, repository.actionStatesFlow, repository.ledgerFlow, allImportedTransactions, sensitivityOverrides
    ) { settings, actions, ledger, importedTxs, overrides ->
        runCatching {
            val actionEntities = actions.map { (k, done) ->
                com.example.data.ActionStateEntity(k, k.substringBefore('_').toIntOrNull() ?: settings.baseYear, k.substringAfter('_', k), done)
            }
            val snapJson = com.example.util.BackupManager.serializeFullBackupToJson(
                com.example.util.BackupManager.FullBackupPayload(settings = settings, ledgerEntries = ledger, actionStates = actionEntities)
            )
            importPrefs.edit().putString("fast_boot_snapshot_v1", snapJson).apply()
        }
        var effectiveSettings = settings
        if (overrides.retOver != null) effectiveSettings = effectiveSettings.copy(portfolioNominalReturnPct = overrides.retOver)
        if (overrides.cpiOver != null) effectiveSettings = effectiveSettings.copy(cpiInflationPct = overrides.cpiOver)
        if (overrides.swrOver != null) effectiveSettings = effectiveSettings.copy(safeWithdrawalRatePct = overrides.swrOver)

        val now = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague"))
        val currentYm = now.toString()
        val activeEntry = ledger.find { it.yearMonth == currentYm } ?: ledger.maxByOrNull { it.yearMonth }
        FinancialEngine.calculate(effectiveSettings, actions, runMonteCarlo = true, activeEntry, now.monthValue, ledger, importedTxs)
    }.flowOn(Dispatchers.Default).conflate().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = run {
            val now = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague"))
            val activeEntry = initialLedger.find { it.yearMonth == now.toString() } ?: initialLedger.maxByOrNull { it.yearMonth }
            FinancialEngine.calculate(initialSettings, initialActions, runMonteCarlo = false, activeEntry, now.monthValue, initialLedger)
        }
    )

    fun updateSettings(newSettings: SettingsEntity, showSnackbar: Boolean = false) {
        viewModelScope.launch {
            val now = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague")).toString()
            repository.updateSettingsAndSyncLedger(newSettings, now)
            val parsedRules = com.example.util.MerchantCategoryManager.parseRulesFromJson(newSettings.merchantRulesJson)
            com.example.util.MerchantCategoryManager.clearOverrides(getApplication())
            parsedRules.forEach { (k, v) -> com.example.util.MerchantCategoryManager.saveOverride(getApplication(), k, v) }
            if (showSnackbar) {
                _uiEvent.emit(UiMessage.ShowSnackbar("Settings saved successfully"))
            }
        }
    }

    private suspend fun syncLedgerToSettingsIfCurrentMonth(
        yearMonth: String, incVaclav: Double, incEleonora: Double, expRent: Double,
        portfolioBalance: Double, pensionBalance: Double, emergencyReserve: Double
    ) {
        val now = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague")).toString()
        val latestYm = repository.getAllLedgerEntriesDirect().map { it.yearMonth }.maxOrNull() ?: now
        val effectiveCurrentYm = if (yearMonth == now || yearMonth >= latestYm) yearMonth else now
        repository.syncLedgerToSettingsTransaction(
            yearMonth, effectiveCurrentYm, incVaclav, incEleonora, expRent,
            portfolioBalance, pensionBalance, emergencyReserve
        )
    }

    fun addLedgerEntry(
        yearMonth: String,
        incVaclav: Double,
        incEleonora: Double,
        incUnforeseen: Double,
        expRent: Double,
        expLiving: Double,
        notes: String,
        portfolioBalance: Double = 0.0,
        pensionBalance: Double = 0.0,
        emergencyReserve: Double = 0.0
    ) {
        viewModelScope.launch {
            val existing = repository.getLedgerEntryByYearMonth(yearMonth)
            val prevLiving = (existing?.expGroceries ?: 0.0) + (existing?.expOther ?: 0.0)
            val (calcGroceries, calcOther) = if (existing != null && prevLiving > 0.0 && existing.expOther > 0.0) {
                val otherRatio = (existing.expOther / prevLiving).coerceIn(0.0, 1.0)
                val otherVal = kotlin.math.round(expLiving * otherRatio)
                (expLiving - otherVal) to otherVal
            } else {
                expLiving to 0.0
            }
            val entry = LedgerEntryEntity(
                id = existing?.id ?: 0L,
                yearMonth = yearMonth,
                incVaclav = incVaclav,
                incEleonora = incEleonora,
                incUnforeseen = incUnforeseen,
                expRent = expRent,
                expGroceries = calcGroceries,
                expOther = calcOther,
                notes = notes,
                portfolioBalanceAtMonthEnd = portfolioBalance,
                pensionBalanceAtMonthEnd = pensionBalance,
                emergencyReserveAtMonthEnd = emergencyReserve
            )
            if (existing != null) {
                repository.updateLedgerEntry(entry)
                _uiEvent.emit(UiMessage.ShowSnackbar("Ledger entry updated for $yearMonth"))
            } else {
                repository.addLedgerEntry(entry)
                _uiEvent.emit(UiMessage.ShowSnackbar("Ledger entry added for $yearMonth"))
            }

            syncLedgerToSettingsIfCurrentMonth(
                yearMonth = yearMonth,
                incVaclav = incVaclav,
                incEleonora = incEleonora,
                expRent = expRent,
                portfolioBalance = portfolioBalance,
                pensionBalance = pensionBalance,
                emergencyReserve = emergencyReserve
            )
        }
    }

    fun updateLedgerEntry(entry: LedgerEntryEntity) {
        viewModelScope.launch {
            repository.updateLedgerEntry(entry)
            syncLedgerToSettingsIfCurrentMonth(
                yearMonth = entry.yearMonth,
                incVaclav = entry.incVaclav,
                incEleonora = entry.incEleonora,
                expRent = entry.expRent,
                portfolioBalance = entry.portfolioBalanceAtMonthEnd,
                pensionBalance = entry.pensionBalanceAtMonthEnd,
                emergencyReserve = entry.emergencyReserveAtMonthEnd
            )
            _uiEvent.emit(UiMessage.ShowSnackbar("Ledger entry updated for ${entry.yearMonth}"))
        }
    }

    fun deleteLedgerEntry(id: Long) {
        viewModelScope.launch {
            val deletedYm = repository.deleteLedgerEntry(id)
            if (deletedYm != null) {
                val editor = importPrefs.edit()
                val prefix = "bank_bal_${deletedYm}_"
                importPrefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
                editor.apply()
            }
            _uiEvent.emit(UiMessage.ShowSnackbar("Ledger entry and associated bank transactions deleted"))
        }
    }

    fun dismissSubscription(merchantKey: String) {
        val updated = _dismissedSubscriptionMerchants.value + merchantKey
        _dismissedSubscriptionMerchants.value = updated
        subscriptionPrefs.edit().putStringSet("dismissed_merchants", updated).apply()
        viewModelScope.launch {
            _uiEvent.emit(UiMessage.ShowSnackbar("Subscription dismissed: $merchantKey"))
        }
    }

    fun restoreSubscription(merchantKey: String) {
        val updated = _dismissedSubscriptionMerchants.value - merchantKey
        _dismissedSubscriptionMerchants.value = updated
        subscriptionPrefs.edit().putStringSet("dismissed_merchants", updated).apply()
        viewModelScope.launch {
            _uiEvent.emit(UiMessage.ShowSnackbar("Subscription restored: $merchantKey"))
        }
    }

    fun clearAllDismissedSubscriptions() {
        _dismissedSubscriptionMerchants.value = emptySet()
        subscriptionPrefs.edit().remove("dismissed_merchants").apply()
        viewModelScope.launch {
            _uiEvent.emit(UiMessage.ShowSnackbar("All dismissed subscriptions restored"))
        }
    }

    fun deleteImportedStatement(yearMonth: String, bankName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                if (bankName != null && bankName.isNotBlank()) {
                    repository.deleteImportedTransactionsForBankAndMonth(yearMonth, bankName)
                    importPrefs.edit().remove("bank_bal_${yearMonth}_$bankName").apply()
                    _uiEvent.emit(UiMessage.ShowSnackbar("Deleted $bankName statement for $yearMonth"))
                } else {
                    repository.deleteImportedTransactionsForMonth(yearMonth)
                    val editor = importPrefs.edit()
                    val prefix = "bank_bal_${yearMonth}_"
                    importPrefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
                    editor.apply()
                    _uiEvent.emit(UiMessage.ShowSnackbar("Deleted imported statement data for $yearMonth"))
                }
                val remainingTxs = repository.getImportedTransactionsDirect(yearMonth)
                val existingEntry = repository.getLedgerEntryByYearMonth(yearMonth)
                if (existingEntry != null) {
                    if (remainingTxs.isEmpty()) {
                        val hasBalances = (existingEntry.portfolioBalanceAtMonthEnd ?: 0.0) > 0.0 ||
                                (existingEntry.pensionBalanceAtMonthEnd ?: 0.0) > 0.0 ||
                                (existingEntry.emergencyReserveAtMonthEnd ?: 0.0) > 0.0
                        if (existingEntry.notes.startsWith("Imported from") && !hasBalances) {
                            repository.deleteLedgerEntry(existingEntry.id)
                        } else {
                            repository.updateLedgerEntry(
                                existingEntry.copy(incVaclav = 0.0, incEleonora = 0.0, incUnforeseen = 0.0, expRent = 0.0, expGroceries = 0.0, expOther = 0.0, notes = "")
                            )
                        }
                        activeAuditReport.value = null
                    } else {
                        val storedBalances = importCoordinator.getStoredBankBalancesForMonth(yearMonth)
                        val (reconciledTxs, auditReport) = com.example.util.CrossStatementReconciliationEngine.reconcileTransactions(
                            transactions = remainingTxs.map {
                                if (it.nettingReason.startsWith("Manually ")) it else it.copy(isNetted = false, nettingReason = "")
                            },
                            yearMonth = yearMonth,
                            bankBalances = storedBalances
                        )
                        repository.updateImportedTransactions(reconciledTxs)
                        activeAuditReport.value = auditReport
                        recalculateMonthLedger(yearMonth)
                    }
                }
            }
        }
    }

    fun importStatements(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = getApplication<Application>().contentResolver
                val byteList = mutableListOf<ByteArray>()
                var hasOversized = false

                val dataDir = getApplication<Application>().applicationInfo.dataDir
                for (uri in uris) {
                    if (uri.scheme == "file" && uri.path?.let { java.io.File(it).canonicalPath.startsWith(java.io.File(dataDir).canonicalPath) } == true) continue
                    val bytes = com.example.util.BankStatementImporter.readUriBytes(contentResolver, uri)
                    if (bytes != null) {
                        byteList.add(bytes)
                    } else {
                        hasOversized = true
                    }
                }

                if (byteList.isEmpty()) {
                    val msg = if (hasOversized) "Statement file is too large (max 25 MB)" else "Error: Unable to open statement file"
                    _uiEvent.emit(UiMessage.ShowSnackbar(msg))
                    return@launch
                }

                // 1. Try bank statement parser first (batch or single)
                val settingsRules = com.example.util.MerchantCategoryManager.parseRulesFromJson(settingsState.value.merchantRulesJson)
                val overrides = com.example.util.MerchantCategoryManager.getOverrides(getApplication()) + settingsRules
                val parsedSummaries = mutableListOf<com.example.util.StatementParseSummary>()

                for (bytes in byteList) {
                    try {
                        val bankSummary = com.example.util.BankStatementImporter.parseStatement(
                            bytes.inputStream(),
                            userOverrides = overrides
                        )
                        if (bankSummary.transactions.isNotEmpty() && bankSummary.yearMonth.matches(Regex("""\d{4}-\d{2}"""))) {
                            parsedSummaries.add(bankSummary)
                        }
                    } catch (_: Exception) {}
                }

                if (parsedSummaries.isNotEmpty()) {
                    val consolidated = com.example.util.BankStatementImporter.mergeSummaries(parsedSummaries)
                    setPendingStatementImport(consolidated)
                    val bankName = consolidated.detectedBank.name
                    val nettedCount = consolidated.internalTransfersCount
                    val nettingMsg = if (nettedCount > 0) " ($nettedCount netted)" else ""
                    _uiEvent.emit(UiMessage.ShowSnackbar("Parsed: ${consolidated.transactions.size} transactions for ${consolidated.yearMonth} ($bankName)$nettingMsg"))
                    return@launch
                }

                // 2. Standard monthly ledger CSV format fallback (only if single file)
                if (byteList.size == 1) {
                    val existingYearMonths = repository.existingYearMonths()
                    val entriesToInsert = mutableListOf<LedgerEntryEntity>()
                    var skippedDuplicates = 0
                    java.io.BufferedReader(java.io.InputStreamReader(byteList[0].inputStream())).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            val rawLine = line.trim()
                            line = reader.readLine()
                            if (rawLine.isBlank()) continue
                            val tokens = com.example.util.BankStatementImporter.parseCsvLine(rawLine)
                            if (tokens.size >= 6) {
                                val ym = tokens[0].trim()
                                val incV = tokens[1].trim().toDoubleOrNull() ?: 0.0
                                val incE = tokens[2].trim().toDoubleOrNull() ?: 0.0
                                val incExtra = if (tokens.size >= 8) tokens[3].trim().toDoubleOrNull() ?: 0.0 else 0.0
                                val expR = if (tokens.size >= 8) tokens[4].trim().toDoubleOrNull() ?: 0.0 else tokens[3].trim().toDoubleOrNull() ?: 0.0
                                val expG = if (tokens.size >= 8) tokens[5].trim().toDoubleOrNull() ?: 0.0 else tokens[4].trim().toDoubleOrNull() ?: 0.0
                                val expO = if (tokens.size >= 8) tokens[6].trim().toDoubleOrNull() ?: 0.0 else if (tokens.size >= 7) tokens[5].trim().toDoubleOrNull() ?: 0.0 else 0.0
                                val notes = tokens.last().trim()
                                if (ym.isNotEmpty() && ym.matches(Regex("""\d{4}-\d{2}"""))) {
                                    if (ym in existingYearMonths) {
                                        skippedDuplicates++
                                    } else {
                                        entriesToInsert.add(LedgerEntryEntity(yearMonth = ym, incVaclav = incV, incEleonora = incE, incUnforeseen = incExtra, expRent = expR, expGroceries = expG + expO, expOther = 0.0, notes = notes))
                                    }
                                }
                            }
                        }
                    }
                    if (entriesToInsert.isNotEmpty()) {
                        repository.addLedgerEntries(entriesToInsert)
                        val dupNote = if (skippedDuplicates > 0) " ($skippedDuplicates duplicate months skipped)" else ""
                        _uiEvent.emit(UiMessage.ShowSnackbar("Successfully imported ${entriesToInsert.size} entries!$dupNote"))
                        return@launch
                    } else if (skippedDuplicates > 0) {
                        _uiEvent.emit(UiMessage.ShowSnackbar("All $skippedDuplicates entries already exist - nothing imported"))
                        return@launch
                    }
                }

                _uiEvent.emit(UiMessage.ShowSnackbar("No transactions found. Supported: Moneta, ČSOB, mBank PDF/CSV, or ledger CSV."))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiMessage.ShowSnackbar("Statement import failed: ${e.localizedMessage ?: "Invalid format"}"))
            }
        }
    }

    fun importCsvData(uri: android.net.Uri) = importStatements(listOf(uri))

    // Active Cross-Statement Audit Report for transparency
    val activeAuditReport = MutableStateFlow<com.example.util.CrossStatementAuditReport?>(null)

    private val importCoordinator by lazy { com.example.util.StatementImportCoordinator(getApplication(), repository, importPrefs) }

    fun loadAuditReportForMonth(yearMonth: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val transactions = repository.getImportedTransactionsDirect(yearMonth)
            val txsToUse = if (transactions.isNotEmpty()) {
                transactions
            } else {
                val pending = _pendingStatementImport.value
                if (pending != null && pending.yearMonth == yearMonth) {
                    pending.transactions.map { pt ->
                        com.example.data.ImportedBankTransactionEntity(
                            yearMonth = pending.yearMonth,
                            bankName = pending.detectedBank.name,
                            date = pt.date,
                            amount = pt.amount,
                            counterpartyAccount = pt.counterpartyAccount,
                            counterpartyName = pt.counterpartyName,
                            message = pt.message,
                            variableSymbol = pt.variableSymbol,
                            category = pt.category.name,
                            isNetted = pt.isNetted,
                            nettingReason = pt.nettingReason
                        )
                    }
                } else {
                    emptyList()
                }
            }

            if (txsToUse.isNotEmpty()) {
                val boundaryTxs = importCoordinator.fetchBoundaryTransactions(yearMonth)
                val storedBalances = importCoordinator.getStoredBankBalancesForMonth(yearMonth)
                val (_, report) = com.example.util.CrossStatementReconciliationEngine.reconcileTransactions(
                    transactions = txsToUse,
                    yearMonth = yearMonth,
                    boundaryTransactions = boundaryTxs,
                    bankBalances = storedBalances
                )
                activeAuditReport.value = report
            } else {
                activeAuditReport.value = null
            }
        }
    }

    fun clearAuditReport() {
        activeAuditReport.value = null
    }

    suspend fun recalculateMonthLedger(targetYm: String) {
        importCoordinator.recalculateMonthLedger(targetYm)
    }

    private val importMutex = kotlinx.coroutines.sync.Mutex()

    fun confirmStatementImport(summary: com.example.util.StatementParseSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                val result = importCoordinator.confirmStatementImport(summary)
                if (result != null) {
                    if (result.auditReport != null) {
                        activeAuditReport.value = result.auditReport
                    }
                    val now = System.currentTimeMillis()
                    _lastImportTimestamp.value = now
                    val monthsLabel = result.affectedMonths.joinToString(", ")
                    _uiEvent.emit(UiMessage.ShowSnackbar("Imported and split statement into $monthsLabel (${result.importedCount} txs from ${summary.detectedBank.name})"))
                }
                setPendingStatementImport(null)
            }
        }
    }

    fun dismissStatementImport() {
        setPendingStatementImport(null)
    }

    fun togglePendingTransactionExcluded(txIndex: Int) {
        val current = _pendingStatementImport.value ?: return
        setPendingStatementImport(importCoordinator.toggleTransactionExcluded(current, txIndex))
    }

    fun bulkExcludeMerchant(merchantQuery: String) {
        val current = _pendingStatementImport.value ?: return
        setPendingStatementImport(importCoordinator.bulkExcludeMerchant(current, merchantQuery))
    }

    fun updatePendingTransactionCategory(
        txIndex: Int,
        newCategory: com.example.util.BankTransactionType,
        rememberForMerchant: Boolean = false,
        applyToAllMatching: Boolean = false
    ) {
        val current = _pendingStatementImport.value ?: return
        viewModelScope.launch {
            val updated = importCoordinator.updateTransactionCategory(
                summary = current,
                txIndex = txIndex,
                newCategory = newCategory,
                rememberForMerchant = rememberForMerchant,
                applyToAllMatching = applyToAllMatching
            )
            setPendingStatementImport(updated)
        }
    }

    fun updateImportedTransactionCategory(
        txId: Long,
        newCategory: com.example.util.BankTransactionType,
        rememberForMerchant: Boolean = false,
        applyToAllMatchingInMonth: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = repository.getImportedTransactionById(txId) ?: return@launch
            val isNowInternal = (newCategory == com.example.util.BankTransactionType.INTERNAL_TRANSFER)

            val newIsNetted = isNowInternal
            val newNettingReason = if (isNowInternal) "Manually marked as internal transfer" else ""

            val updatedTarget = target.copy(
                category = newCategory.name,
                isNetted = newIsNetted,
                nettingReason = newNettingReason
            )

            val txsToUpdate = mutableListOf(updatedTarget)

            if (applyToAllMatchingInMonth) {
                val monthTxs = repository.getImportedTransactionsDirect(target.yearMonth)
                monthTxs.forEach { other ->
                    if (other.id != target.id && com.example.util.StatementImportCoordinator.matchesMerchantInMonth(target, other)) {
                        txsToUpdate.add(other.copy(
                            category = newCategory.name,
                            isNetted = newIsNetted,
                            nettingReason = newNettingReason
                        ))
                    }
                }
            }

            repository.updateImportedTransactions(txsToUpdate)

            if (rememberForMerchant) {
                val rawMerchant = target.counterpartyName.ifBlank { target.message }
                val query = com.example.util.CzechMerchantCatalog.suggestMerchantSearchQuery(rawMerchant)
                    .ifBlank { com.example.util.BankStatementImporter.cleanPaymentDescription(rawMerchant) }
                if (query.isNotBlank()) {
                    com.example.util.MerchantCategoryManager.saveOverride(getApplication(), query, newCategory)
                    val currentSettings = repository.settingsFlow.first()
                    val currentRules = com.example.util.MerchantCategoryManager.parseRulesFromJson(currentSettings.merchantRulesJson).toMutableMap()
                    currentRules[query.trim().lowercase()] = newCategory
                    repository.saveSettings(
                        currentSettings.copy(merchantRulesJson = com.example.util.MerchantCategoryManager.serializeRulesToJson(currentRules))
                    )
                }
            }

            importCoordinator.recalculateMonthLedger(target.yearMonth)
            if (activeAuditReport.value?.yearMonth == target.yearMonth) {
                loadAuditReportForMonth(target.yearMonth)
            }
            _uiEvent.emit(UiMessage.ShowSnackbar("Updated category to ${newCategory.name.replace('_', ' ')} (${txsToUpdate.size} txs updated)"))
        }
    }

    fun toggleImportedTransactionExcluded(txId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = repository.getImportedTransactionById(txId) ?: return@launch
            val willBeExcluded = !target.isNetted
            val restoredCategory = if (!willBeExcluded && target.category == com.example.util.BankTransactionType.INTERNAL_TRANSFER.name) {
                val settingsRules = com.example.util.MerchantCategoryManager.parseRulesFromJson(settingsState.value.merchantRulesJson)
                val overrides = com.example.util.MerchantCategoryManager.getOverrides(getApplication()) + settingsRules
                val matched = com.example.util.CzechMerchantCatalog.matchCategory(
                    "${target.counterpartyName} ${target.counterpartyAccount} ${target.message}",
                    overrides
                )
                (matched ?: if (target.amount < 0) com.example.util.BankTransactionType.GENERAL_EXPENSE else com.example.util.BankTransactionType.OTHER_INFLOW).name
            } else {
                target.category
            }
            val updated = target.copy(
                category = restoredCategory,
                isNetted = willBeExcluded,
                nettingReason = if (willBeExcluded) "Manually excluded from budget" else ""
            )
            repository.updateImportedTransaction(updated)
            importCoordinator.recalculateMonthLedger(target.yearMonth)
            if (activeAuditReport.value?.yearMonth == target.yearMonth) {
                loadAuditReportForMonth(target.yearMonth)
            }
            val actionLabel = if (willBeExcluded) "Excluded from budget" else "Restored to budget"
            _uiEvent.emit(UiMessage.ShowSnackbar("$actionLabel: ${target.counterpartyName.ifBlank { target.message }}"))
        }
    }

    fun toggleAction(year: Int, actionId: String, currentIsDone: Boolean) {
        viewModelScope.launch {
            repository.setActionState(year, actionId, !currentIsDone)
        }
    }

    fun setSensitivityOverrides(returnPct: Double?, cpiPct: Double?, swrPct: Double?) {
        sensitivityReturnOverride.value = returnPct
        sensitivityCpiOverride.value = cpiPct
        sensitivitySwrOverride.value = swrPct
        savedStateHandle["sensitivity_return_override"] = returnPct
        savedStateHandle["sensitivity_cpi_override"] = cpiPct
        savedStateHandle["sensitivity_swr_override"] = swrPct
    }

    fun restoreFullBackup(payload: com.example.util.BackupManager.FullBackupPayload) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreFullBackupData(payload.settings, payload.ledgerEntries, payload.actionStates, payload.importedTransactions)
            // Sync restored merchant rules to MerchantCategoryManager
            val parsedRules = com.example.util.MerchantCategoryManager.parseRulesFromJson(payload.settings.merchantRulesJson)
            com.example.util.MerchantCategoryManager.clearOverrides(getApplication())
            parsedRules.forEach { (k, v) -> com.example.util.MerchantCategoryManager.saveOverride(getApplication(), k, v) }

            // Restore auxiliary preferences (dismissed subscriptions, statement bank balances)
            val dismissedRaw = payload.preferences["dismissed_merchants"]
            val restoredSet = if (dismissedRaw != null) {
                dismissedRaw.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }
            _dismissedSubscriptionMerchants.value = restoredSet
            subscriptionPrefs.edit().putStringSet("dismissed_merchants", restoredSet).apply()

            val balEditor = importPrefs.edit()
            importPrefs.all.keys.filter { it.startsWith("bank_bal_") }.forEach { balEditor.remove(it) }
            payload.preferences.forEach { (k, v) ->
                if (k.startsWith("bank_bal_")) {
                    balEditor.putString(k, v)
                }
            }
            balEditor.apply()

            _uiEvent.emit(UiMessage.ShowSnackbar("Full backup restored (${payload.ledgerEntries.size} months, ${payload.importedTransactions.size} txs)"))
        }
    }

    fun resetSettingsToDefault() {
        viewModelScope.launch {
            importPrefs.edit().remove("fast_boot_snapshot_v1").apply()
            repository.saveSettings(SettingsEntity.freshDefaults())
            com.example.util.MerchantCategoryManager.clearOverrides(getApplication())
            setSensitivityOverrides(null, null, null)
            _uiEvent.emit(UiMessage.ShowSnackbar("Reset all settings to default"))
        }
    }

    fun clearAllUserData() {
        viewModelScope.launch {
            repository.clearAllData()
            repository.saveSettings(SettingsEntity.freshDefaults())
            setSensitivityOverrides(null, null, null)
            importPrefs.edit().clear().apply(); _lastImportTimestamp.value = null
            subscriptionPrefs.edit().clear().apply(); _dismissedSubscriptionMerchants.value = emptySet()
            syncPrefs.edit().clear().apply(); _lastCzechSyncTimestamp.value = null; liveRegulatoryData.value = null
            com.example.util.MerchantCategoryManager.clearOverrides(getApplication())
            setPendingStatementImport(null); activeAuditReport.value = null
            _uiEvent.emit(UiMessage.ShowSnackbar("All user data cleared"))
        }
    }
}
