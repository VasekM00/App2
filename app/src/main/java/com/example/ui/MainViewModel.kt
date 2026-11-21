package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppPreferencesRepository
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.domain.DecumulationComparisonResult
import com.example.domain.FullCalculationState
import com.example.domain.MonteCarloResult
import com.example.domain.RetirementSurvivalResult
import com.example.domain.StressScenarioResult
import com.example.domain.parseAccountListJson
import com.example.ui.state.buildMergedLedgerEntryInternal
import com.example.ui.state.buildSyntheticAuditTransactions
import com.example.ui.state.clearAuxiliaryStoresInternal
import com.example.ui.state.computeCalculationStateInternal
import com.example.ui.state.parseAndInsertLedgerCsvFallbackInternal
import com.example.ui.state.persistPendingStatementToHandle
import com.example.ui.state.readStatementUrisSafely
import com.example.ui.state.runOneTimeMigrationsInternal
import com.example.ui.tabs.friendlyBankName
import com.example.ui.components.formatCategoryLabel
import com.example.util.BackupManager
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.CrossStatementAuditReport
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.CzechEconomicSyncService
import com.example.util.CzechMerchantCatalog
import com.example.util.FxRateService
import com.example.util.MerchantCategoryManager
import com.example.util.StatementImportCoordinator
import com.example.util.StatementParseSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

sealed interface UiMessage {
    data class ShowSnackbar(val message: String) : UiMessage
}

private const val KEY_PENDING_STATEMENT_IMPORT = "pending_statement_import"
private const val KEY_SENSITIVITY_RETURN = "sensitivity_return_override"
private const val KEY_SENSITIVITY_CPI = "sensitivity_cpi_override"
private const val KEY_SENSITIVITY_SWR = "sensitivity_swr_override"

class MainViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : AndroidViewModel(application) {

    // Architectural Note (F-018): Lightweight manual dependency injection.
    // Avoids annotation processor and code-gen overhead of Hilt/Dagger while maintaining clear separation.
    private val db = AppDatabase.getDatabase(application)
    private val repository = FinancialRepository(
        db, db.settingsDao(), db.ledgerDao(), db.actionStateDao(), db.importedTransactionDao()
    )
    private val preferencesRepository = AppPreferencesRepository(application)

    private val _uiEvent = Channel<UiMessage>(Channel.BUFFERED)
    val uiEvent: Flow<UiMessage> = _uiEvent.receiveAsFlow()

    private val _liveSettings = MutableStateFlow(SettingsEntity.freshDefaults())
    private val pendingSettingsWrites = AtomicInteger(0)

    val ledgerEntries: StateFlow<List<LedgerEntryEntity>> = repository.ledgerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actionStates: StateFlow<Map<String, Boolean>> = repository.actionStatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val settingsState: StateFlow<SettingsEntity> = _liveSettings.asStateFlow()

    val themeMode: StateFlow<String> = preferencesRepository.snapshot
        .map { it.themeMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")

    val flagSecureEnabled: StateFlow<Boolean> = preferencesRepository.snapshot
        .map { it.flagSecureEnabled }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val biometricLockEnabled: StateFlow<Boolean> = preferencesRepository.snapshot
        .map { it.biometricLockEnabled }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val importedTransactionsShared = repository.getAllImportedTransactions()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val importedBankSourcesByMonth: StateFlow<Map<String, Set<String>>> = importedTransactionsShared
        .map { txList ->
            txList.groupBy { it.yearMonth }
                .mapValues { (_, txs) -> txs.map { it.bankName }.filter { it.isNotBlank() }.toSet() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allImportedTransactions: StateFlow<List<ImportedBankTransactionEntity>> = importedTransactionsShared
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sensitivity slider override states backed by savedStateHandle
    val sensitivityReturnOverride = MutableStateFlow<Double?>(savedStateHandle.get<Double?>(KEY_SENSITIVITY_RETURN))
    val sensitivityCpiOverride = MutableStateFlow<Double?>(savedStateHandle.get<Double?>(KEY_SENSITIVITY_CPI))
    val sensitivitySwrOverride = MutableStateFlow<Double?>(savedStateHandle.get<Double?>(KEY_SENSITIVITY_SWR))

    // Live Czech Economic & Regulatory Sync state
    val liveRegulatoryData = MutableStateFlow<CzechRegulatoryData?>(null)
    val isSyncing = MutableStateFlow(false)

    // Pending bank statement import review
    private val _pendingStatementImport = MutableStateFlow<StatementParseSummary?>(
        savedStateHandle.get<StatementParseSummary?>(KEY_PENDING_STATEMENT_IMPORT)
    )
    val pendingStatementImport: StateFlow<StatementParseSummary?> = _pendingStatementImport.asStateFlow()

    private val pendingEpoch = AtomicLong(0L)
    private var lastCommittedPendingEpoch: Long = -1L

    private fun setPendingStatementImport(summary: StatementParseSummary?, bumpEpoch: Boolean = true) {
        if (bumpEpoch) pendingEpoch.incrementAndGet()
        _pendingStatementImport.value = summary
        persistPendingStatementToHandle(savedStateHandle, KEY_PENDING_STATEMENT_IMPORT, summary)
    }

    // Statement import recency tracking
    val lastImportTimestamp: StateFlow<Long?> = preferencesRepository.snapshot
        .map { it.lastImportTimestamp }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Czech economic sync recency tracking
    val lastCzechSyncTimestamp: StateFlow<Long?> = preferencesRepository.snapshot
        .map { it.lastCzechSyncTimestamp }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Dismissed recurring subscriptions tracking
    val dismissedSubscriptionMerchants: StateFlow<Set<String>> = preferencesRepository.snapshot
        .map { it.dismissedSubscriptionMerchants }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Active Cross-Statement Audit Report for transparency
    val activeAuditReport = MutableStateFlow<CrossStatementAuditReport?>(null)

    private val importCoordinator by lazy { StatementImportCoordinator(getApplication(), repository, preferencesRepository) }
    private val importMutex = Mutex()
    private val syncInFlight = AtomicBoolean(false)
    private var loadAuditJob: Job? = null
    private var syncJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefSnap = preferencesRepository.hydrate()
                hydrateFxRatesFromJson(prefSnap.cnbFxRatesJson)
                importMutex.withLock {
                    runOneTimeMigrationsInternal(getApplication(), repository, preferencesRepository)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-fatal startup migration error
            }
            repository.settingsFlow.collect { dbSettings ->
                if (pendingSettingsWrites.get() == 0) {
                    _liveSettings.value = dbSettings
                }
            }
        }
        viewModelScope.launch {
            repository.storageErrors.collect { msg ->
                _uiEvent.send(UiMessage.ShowSnackbar(msg))
            }
        }
    }

    private fun hydrateFxRatesFromJson(fxJson: String) {
        if (fxJson.isBlank() || fxJson.trim() == "{}") return
        runCatching {
            val obj = JSONObject(fxJson)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val rate = obj.optDouble(code, Double.NaN)
                if (rate.isFinite() && rate > 0.0) {
                    FxRateService.setRate(code, rate)
                }
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.setFlagSecureEnabled(enabled)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepository.setBiometricLockEnabled(enabled)
        }
    }

    fun syncLiveCzechData() {
        if (!syncInFlight.compareAndSet(false, true)) return
        isSyncing.value = true
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = CzechEconomicSyncService.fetchLiveRegulatoryData()
                liveRegulatoryData.value = data
                val fetchedLive = data.rateDate.isNotBlank() || data.sourceName.contains("Official")
                if (fetchedLive) {
                    val fxJson = JSONObject()
                    if (data.eurCzkRate.isFinite() && data.eurCzkRate > 0.0) {
                        FxRateService.setRate("EUR", data.eurCzkRate)
                        fxJson.put("EUR", data.eurCzkRate)
                    }
                    if (data.usdCzkRate.isFinite() && data.usdCzkRate > 0.0) {
                        FxRateService.setRate("USD", data.usdCzkRate)
                        fxJson.put("USD", data.usdCzkRate)
                    }
                    if (fxJson.length() > 0) {
                        preferencesRepository.setCnbFxRatesJson(fxJson.toString())
                    }
                    preferencesRepository.setLastCzechSyncTimestamp(System.currentTimeMillis())
                    _uiEvent.send(UiMessage.ShowSnackbar("ČNB FX rates synced & 2026 Czech statutory benchmarks verified"))
                } else {
                    _uiEvent.send(UiMessage.ShowSnackbar("Offline: using built-in 2026 Czech statutory parameters"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Sync failed. Using saved statutory parameters"))
            } finally {
                isSyncing.value = false
                syncInFlight.set(false)
            }
        }
    }

    private data class SensitivityOverrides(val retOver: Double?, val cpiOver: Double?, val swrOver: Double?)
    private data class CalcInput(
        val settings: SettingsEntity,
        val actions: Map<String, Boolean>,
        val ledger: List<LedgerEntryEntity>,
        val importedTxs: List<ImportedBankTransactionEntity>,
        val overrides: SensitivityOverrides
    )

    private data class StochasticSnapshot(
        val monteCarlo: MonteCarloResult,
        val historicalMonteCarlo: MonteCarloResult,
        val retirementSurvival: RetirementSurvivalResult,
        val historicalRetirementSurvival: RetirementSurvivalResult,
        val stressScenarios: List<StressScenarioResult>,
        val decumulationComparison: DecumulationComparisonResult?
    )

    private val lastStochasticSnapshot = AtomicReference<StochasticSnapshot?>(null)

    @OptIn(FlowPreview::class)
    private val sensitivityOverrides = combine(sensitivityReturnOverride, sensitivityCpiOverride, sensitivitySwrOverride) { r, c, s ->
        SensitivityOverrides(r, c, s)
    }.debounce(120L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val calculationState: StateFlow<FullCalculationState> = combine(
        _liveSettings,
        repository.actionStatesFlow.distinctUntilChanged(),
        repository.ledgerFlow.distinctUntilChanged(),
        importedTransactionsShared.distinctUntilChanged(),
        sensitivityOverrides.distinctUntilChanged()
    ) { settings, actions, ledger, importedTxs, overrides ->
        CalcInput(settings, actions, ledger, importedTxs, overrides)
    }
        .flatMapLatest { input ->
            flow {
                val fastState = computeCalculationStateInternal(
                    input.settings, input.actions, input.ledger, input.importedTxs,
                    input.overrides.retOver, input.overrides.cpiOver, input.overrides.swrOver,
                    runMonteCarlo = false
                )
                val prev = lastStochasticSnapshot.get()
                val immediateState = if (prev != null) {
                    fastState.copy(
                        monteCarlo = prev.monteCarlo,
                        historicalMonteCarlo = prev.historicalMonteCarlo,
                        retirementSurvival = prev.retirementSurvival,
                        historicalRetirementSurvival = prev.historicalRetirementSurvival,
                        stressScenarios = prev.stressScenarios,
                        decumulationComparison = prev.decumulationComparison
                    )
                } else {
                    fastState
                }
                emit(immediateState)

                delay(120L)
                val fullState = computeCalculationStateInternal(
                    input.settings, input.actions, input.ledger, input.importedTxs,
                    input.overrides.retOver, input.overrides.cpiOver, input.overrides.swrOver,
                    runMonteCarlo = true
                )
                lastStochasticSnapshot.set(
                    StochasticSnapshot(
                        monteCarlo = fullState.monteCarlo,
                        historicalMonteCarlo = fullState.historicalMonteCarlo,
                        retirementSurvival = fullState.retirementSurvival,
                        historicalRetirementSurvival = fullState.historicalRetirementSurvival,
                        stressScenarios = fullState.stressScenarios,
                        decumulationComparison = fullState.decumulationComparison
                    )
                )
                emit(fullState)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = computeCalculationStateInternal(
                SettingsEntity.freshDefaults(), emptyMap(), emptyList(), emptyList(),
                sensitivityReturnOverride.value, sensitivityCpiOverride.value, sensitivitySwrOverride.value,
                runMonteCarlo = false
            )
        )

    fun updateSettings(showSnackbar: Boolean = false, transform: (SettingsEntity) -> SettingsEntity) {
        pendingSettingsWrites.incrementAndGet()
        _liveSettings.update { cur ->
            BackupManager.sanitizeSettings(transform(cur), SettingsEntity.freshDefaults())
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    val now = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
                    val clean = repository.updateSettingsTransformAndSyncLedger(now, transform)
                    if (clean != null) {
                        val parsedRules = MerchantCategoryManager.parseRulesFromJson(clean.merchantRulesJson)
                        MerchantCategoryManager.saveAllOverrides(getApplication(), parsedRules)
                        if (pendingSettingsWrites.decrementAndGet() == 0) {
                            _liveSettings.value = clean
                        }
                        if (showSnackbar) _uiEvent.send(UiMessage.ShowSnackbar("Settings saved successfully"))
                    } else {
                        if (pendingSettingsWrites.decrementAndGet() == 0) {
                            _liveSettings.value = repository.settingsFlow.first()
                        }
                        _uiEvent.send(UiMessage.ShowSnackbar("Failed to save settings to local database"))
                    }
                }
            } catch (e: CancellationException) {
                pendingSettingsWrites.decrementAndGet()
                throw e
            } catch (_: Exception) {
                if (pendingSettingsWrites.decrementAndGet() == 0) {
                    runCatching { _liveSettings.value = repository.settingsFlow.first() }
                }
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to save settings to local database"))
            }
        }
    }

    fun updateSettings(newSettings: SettingsEntity, showSnackbar: Boolean = false) {
        updateSettings(showSnackbar) { newSettings }
    }

    private suspend fun syncLedgerToSettingsIfCurrentMonth(
        yearMonth: String,
        incVaclav: Double?,
        incEleonora: Double?,
        expRent: Double?,
        portfolioBalance: Double?,
        pensionBalance: Double?,
        emergencyReserve: Double?
    ) {
        val now = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
        val existingMax = repository.getAllLedgerEntriesDirect().map { it.yearMonth }.maxOrNull()
        val latestYm = if (existingMax != null && existingMax > now) existingMax else now
        val effectiveCurrentYm = if (yearMonth == now || yearMonth >= latestYm) yearMonth else now
        repository.syncManualLedgerToSettingsTransaction(
            yearMonth, effectiveCurrentYm, incVaclav, incEleonora, expRent, portfolioBalance, pensionBalance, emergencyReserve
        )
        if (pendingSettingsWrites.get() == 0) {
            _liveSettings.value = repository.settingsFlow.first()
        }
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
        addLedgerEntryWithOptionalBalances(
            yearMonth = yearMonth,
            incVaclav = incVaclav,
            incEleonora = incEleonora,
            incUnforeseen = incUnforeseen,
            expRent = expRent,
            expLiving = expLiving,
            notes = notes,
            portfolioBalance = portfolioBalance.takeIf { it > 0.0 },
            pensionBalance = pensionBalance.takeIf { it > 0.0 },
            emergencyReserve = emergencyReserve.takeIf { it > 0.0 }
        )
    }

    fun addLedgerEntryWithOptionalBalances(
        yearMonth: String,
        incVaclav: Double,
        incEleonora: Double,
        incUnforeseen: Double,
        expRent: Double,
        expLiving: Double,
        notes: String,
        portfolioBalance: Double?,
        pensionBalance: Double?,
        emergencyReserve: Double?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    var wasUpdated = false
                    repository.withTransaction {
                        val existing = repository.getLedgerEntryByYearMonth(yearMonth)
                        wasUpdated = existing != null
                        val entry = buildMergedLedgerEntryInternal(
                            existing = existing,
                            yearMonth = yearMonth,
                            incVaclav = incVaclav,
                            incEleonora = incEleonora,
                            incUnforeseen = incUnforeseen,
                            expRent = expRent,
                            expLiving = expLiving,
                            notes = notes,
                            portfolioBalance = portfolioBalance ?: 0.0,
                            pensionBalance = pensionBalance ?: 0.0,
                            emergencyReserve = emergencyReserve ?: 0.0
                        )
                        if (existing != null) repository.updateLedgerEntry(entry) else repository.addLedgerEntry(entry)
                    }
                    if (!notes.startsWith("Imported from", ignoreCase = true)) {
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
                    val msg = if (wasUpdated) "Ledger entry updated for $yearMonth" else "Ledger entry added for $yearMonth"
                    _uiEvent.send(UiMessage.ShowSnackbar(msg))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to save ledger entry"))
            }
        }
    }

    fun updateLedgerEntry(entry: LedgerEntryEntity) {
        updateLedgerEntryWithOptionalBalances(
            entry = entry,
            portfolioBalance = entry.portfolioBalanceAtMonthEnd.takeIf { it > 0.0 },
            pensionBalance = entry.pensionBalanceAtMonthEnd.takeIf { it > 0.0 },
            emergencyReserve = entry.emergencyReserveAtMonthEnd.takeIf { it > 0.0 }
        )
    }

    fun updateLedgerEntryWithOptionalBalances(
        entry: LedgerEntryEntity,
        portfolioBalance: Double?,
        pensionBalance: Double?,
        emergencyReserve: Double?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    repository.updateLedgerEntry(entry)
                    if (!entry.notes.startsWith("Imported from", ignoreCase = true)) {
                        syncLedgerToSettingsIfCurrentMonth(
                            yearMonth = entry.yearMonth,
                            incVaclav = entry.incVaclav,
                            incEleonora = entry.incEleonora,
                            expRent = entry.expRent,
                            portfolioBalance = portfolioBalance,
                            pensionBalance = pensionBalance,
                            emergencyReserve = emergencyReserve
                        )
                    }
                    _uiEvent.send(UiMessage.ShowSnackbar("Ledger entry updated for ${entry.yearMonth}"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to update ledger entry"))
            }
        }
    }

    fun deleteLedgerEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    val deletedYm = repository.deleteLedgerEntry(id)
                    if (deletedYm != null) {
                        preferencesRepository.clearBankBalancesForMonth(deletedYm)
                        if (activeAuditReport.value?.yearMonth == deletedYm) activeAuditReport.value = null
                    }
                    _uiEvent.send(UiMessage.ShowSnackbar("Ledger entry and associated bank transactions deleted"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to delete ledger entry"))
            }
        }
    }

    fun dismissSubscription(merchantKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                val updated = preferencesRepository.snapshot.value.dismissedSubscriptionMerchants + merchantKey
                preferencesRepository.setDismissedMerchants(updated)
                _uiEvent.send(UiMessage.ShowSnackbar("Subscription dismissed: $merchantKey"))
            }
        }
    }

    fun restoreSubscription(merchantKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                val updated = preferencesRepository.snapshot.value.dismissedSubscriptionMerchants - merchantKey
                preferencesRepository.setDismissedMerchants(updated)
                _uiEvent.send(UiMessage.ShowSnackbar("Subscription restored: $merchantKey"))
            }
        }
    }

    fun clearAllDismissedSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                preferencesRepository.setDismissedMerchants(emptySet())
                _uiEvent.send(UiMessage.ShowSnackbar("All dismissed subscriptions restored"))
            }
        }
    }

    fun deleteImportedStatement(yearMonth: String, bankName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    val hasBank = !bankName.isNullOrBlank()
                    if (hasBank) {
                        val bank = bankName!!
                        repository.deleteImportedTransactionsForBankAndMonth(yearMonth, bank)
                        preferencesRepository.removeBankBalanceForMonthBank(yearMonth, bank)
                    } else {
                        repository.deleteImportedTransactionsForMonth(yearMonth)
                        preferencesRepository.clearBankBalancesForMonth(yearMonth)
                    }

                    val curSettings = repository.settingsFlow.first()
                    val overrides = MerchantCategoryManager.getOverrides(getApplication()) +
                        MerchantCategoryManager.parseRulesFromJson(curSettings.merchantRulesJson)
                    val remainingTxs = repository.getImportedTransactionsDirect(yearMonth)
                    val existingEntry = repository.getLedgerEntryByYearMonth(yearMonth)
                    if (remainingTxs.isEmpty()) {
                        if (existingEntry != null) {
                            if (existingEntry.notes.startsWith("Imported from", ignoreCase = true) || existingEntry.notes.isBlank()) {
                                repository.deleteLedgerEntry(existingEntry.id)
                            } else {
                                repository.updateLedgerEntry(
                                    existingEntry.copy(
                                        incVaclav = 0.0, incEleonora = 0.0, incUnforeseen = 0.0,
                                        expRent = 0.0, expGroceries = 0.0, expOther = 0.0
                                    )
                                )
                            }
                        }
                        activeAuditReport.value = null
                    } else {
                        val boundaryTxs = importCoordinator.fetchBoundaryTransactions(yearMonth)
                        val storedBalances = importCoordinator.getStoredBankBalancesForMonth(yearMonth)
                        val householdNames = listOfNotNull(
                            curSettings.primaryName.takeIf { it.isNotBlank() },
                            curSettings.spouseName.takeIf { !curSettings.isSingleHousehold && it.isNotBlank() }
                        )
                        val knownFamilyAccounts = parseAccountListJson(curSettings.knownFamilyAccountsJson).toSet()
                        // Re-categorize previously auto-netted INTERNAL_TRANSFER rows so un-matched legs
                        // don't vanish into an uncounted INTERNAL_TRANSFER category black-hole.
                        val resetTxs = remainingTxs.map {
                            if (it.nettingReason.startsWith("Manually ")) {
                                it
                            } else {
                                val restoredCat = if (it.category == BankTransactionType.INTERNAL_TRANSFER.name) {
                                    val matched = CzechMerchantCatalog.matchCategory(
                                        "${it.counterpartyName} ${it.counterpartyAccount} ${it.message}",
                                        overrides
                                    )
                                    (matched ?: if (it.amount < 0) BankTransactionType.GENERAL_EXPENSE else BankTransactionType.OTHER_INFLOW).name
                                } else it.category
                                it.copy(category = restoredCat, isNetted = false, nettingReason = "", matchedTxId = null)
                            }
                        }
                        val (reconciledTxs, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(
                            transactions = resetTxs,
                            yearMonth = yearMonth,
                            knownFamilyAccounts = knownFamilyAccounts,
                            boundaryTransactions = boundaryTxs,
                            bankBalances = storedBalances,
                            householdNames = householdNames
                        )
                        repository.updateImportedTransactions(reconciledTxs)
                        activeAuditReport.value = auditReport
                        if (existingEntry != null) recalculateMonthLedger(yearMonth)
                    }

                    val displayBank = if (hasBank) friendlyBankName(bankName!!) else ""
                    val msg = if (hasBank) "Deleted $displayBank statement for $yearMonth" else "Deleted imported statement data for $yearMonth"
                    _uiEvent.send(UiMessage.ShowSnackbar(msg))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to delete imported statement"))
            }
        }
    }

    fun importStatements(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val readResult = readStatementUrisSafely(getApplication(), uris)
                val byteList = readResult.byteList

                if (byteList.isEmpty()) {
                    val msg = if (readResult.hasOversized && !readResult.hasReadError) {
                        "Statement file is too large (max 25 MB)"
                    } else {
                        "Error: Unable to open statement file"
                    }
                    _uiEvent.send(UiMessage.ShowSnackbar(msg))
                    return@launch
                }

                importMutex.withLock {
                    val curSettings = repository.settingsFlow.first()
                    val settingsRules = MerchantCategoryManager.parseRulesFromJson(curSettings.merchantRulesJson)
                    val overrides = MerchantCategoryManager.getOverrides(getApplication()) + settingsRules
                    val householdNames = listOfNotNull(
                        curSettings.primaryName.takeIf { it.isNotBlank() },
                        curSettings.spouseName.takeIf { !curSettings.isSingleHousehold && it.isNotBlank() }
                    )
                    val parsedSummaries = mutableListOf<StatementParseSummary>()
                    var failedCount = 0

                    for (bytes in byteList) {
                        try {
                            val bankSummary = BankStatementImporter.parseStatement(
                                bytes.inputStream(),
                                userOverrides = overrides,
                                householdNames = householdNames
                            )
                            if (bankSummary.transactions.isNotEmpty() && bankSummary.yearMonth.matches(Regex("""\d{4}-\d{2}"""))) {
                                parsedSummaries.add(bankSummary)
                            } else {
                                failedCount++
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failedCount++
                        }
                    }

                    if (parsedSummaries.isNotEmpty()) {
                        val consolidated = BankStatementImporter.mergeSummaries(parsedSummaries)
                        setPendingStatementImport(consolidated, bumpEpoch = true)
                        val nettingMsg = if (consolidated.internalTransfersCount > 0) " (${consolidated.internalTransfersCount} netted)" else ""
                        val failedNote = if (failedCount > 0) " · $failedCount file(s) skipped" else ""
                        val bankDisplay = friendlyBankName(consolidated.detectedBank.name)
                        _uiEvent.send(
                            UiMessage.ShowSnackbar(
                                "Parsed ${consolidated.transactions.size} transactions for ${consolidated.yearMonth} ($bankDisplay)$nettingMsg$failedNote"
                            )
                        )
                        return@launch
                    }

                    if (byteList.size == 1) {
                        val (insertedCount, skippedDuplicates) = parseAndInsertLedgerCsvFallbackInternal(repository, byteList[0]) { cell ->
                            cell.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
                        }
                        if (insertedCount > 0) {
                            val dupNote = if (skippedDuplicates > 0) " ($skippedDuplicates duplicate months skipped)" else ""
                            _uiEvent.send(UiMessage.ShowSnackbar("Successfully imported $insertedCount entries!$dupNote"))
                            return@launch
                        } else if (skippedDuplicates > 0) {
                            _uiEvent.send(UiMessage.ShowSnackbar("All $skippedDuplicates entries already exist - nothing imported"))
                            return@launch
                        }
                    }

                    _uiEvent.send(UiMessage.ShowSnackbar("No transactions found. Supported: Moneta, ČSOB, mBank PDF/CSV, or ledger CSV."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Statement import failed. Please verify the file format."))
            }
        }
    }

    fun importCsvData(uri: Uri) = importStatements(listOf(uri))

    fun loadAuditReportForMonth(yearMonth: String) {
        loadAuditJob?.cancel()
        loadAuditJob = viewModelScope.launch(Dispatchers.IO) { reloadAuditReportForMonthInternal(yearMonth) }
    }

    private suspend fun reloadAuditReportForMonthInternal(yearMonth: String) {
        val transactions = repository.getImportedTransactionsDirect(yearMonth)
        val txsToUse = if (transactions.isNotEmpty()) {
            transactions
        } else {
            val pending = _pendingStatementImport.value
            if (pending != null && pending.yearMonth == yearMonth) buildSyntheticAuditTransactions(pending) else emptyList()
        }

        if (txsToUse.isNotEmpty()) {
            val boundaryTxs = importCoordinator.fetchBoundaryTransactions(yearMonth)
            val storedBalances = importCoordinator.getStoredBankBalancesForMonth(yearMonth)
            val curSettings = repository.settingsFlow.first()
            val householdNames = listOfNotNull(
                curSettings.primaryName.takeIf { it.isNotBlank() },
                curSettings.spouseName.takeIf { !curSettings.isSingleHousehold && it.isNotBlank() }
            )
            val knownFamilyAccounts = parseAccountListJson(curSettings.knownFamilyAccountsJson).toSet()
            val (_, report) = CrossStatementReconciliationEngine.reconcileTransactions(
                transactions = txsToUse,
                yearMonth = yearMonth,
                knownFamilyAccounts = knownFamilyAccounts,
                boundaryTransactions = boundaryTxs,
                bankBalances = storedBalances,
                householdNames = householdNames
            )
            activeAuditReport.value = report
        } else {
            activeAuditReport.value = null
        }
    }

    fun clearAuditReport() {
        loadAuditJob?.cancel()
        activeAuditReport.value = null
    }

    suspend fun recalculateMonthLedger(targetYm: String) {
        importCoordinator.recalculateMonthLedger(targetYm)
    }

    fun confirmStatementImport(summary: StatementParseSummary) {
        val capturedEpoch = pendingEpoch.get()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    if (pendingEpoch.get() != capturedEpoch || lastCommittedPendingEpoch == capturedEpoch) return@withLock
                    val result = importCoordinator.confirmStatementImport(summary)
                    lastCommittedPendingEpoch = capturedEpoch
                    setPendingStatementImport(null, bumpEpoch = true)
                    if (result != null) {
                        if (result.auditReport != null) activeAuditReport.value = result.auditReport
                        val monthsLabel = result.affectedMonths.joinToString(", ")
                        val bankDisplay = friendlyBankName(summary.detectedBank.name)
                        _uiEvent.send(
                            UiMessage.ShowSnackbar(
                                "Imported statement into $monthsLabel (${result.importedCount} transactions from $bankDisplay)"
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to save imported transactions"))
            }
        }
    }

    fun dismissStatementImport() {
        setPendingStatementImport(null, bumpEpoch = true)
    }

    fun togglePendingTransactionExcluded(txIndex: Int) {
        val requestEpoch = pendingEpoch.get()
        viewModelScope.launch(Dispatchers.Default) {
            importMutex.withLock {
                if (pendingEpoch.get() != requestEpoch) return@withLock
                val current = _pendingStatementImport.value ?: return@withLock
                val updated = importCoordinator.toggleTransactionExcluded(current, txIndex)
                if (pendingEpoch.get() == requestEpoch) setPendingStatementImport(updated, bumpEpoch = false)
            }
        }
    }

    fun bulkExcludeMerchant(merchantQuery: String) {
        val requestEpoch = pendingEpoch.get()
        viewModelScope.launch(Dispatchers.Default) {
            importMutex.withLock {
                if (pendingEpoch.get() != requestEpoch) return@withLock
                val current = _pendingStatementImport.value ?: return@withLock
                val updated = importCoordinator.bulkExcludeMerchant(current, merchantQuery)
                if (pendingEpoch.get() == requestEpoch) setPendingStatementImport(updated, bumpEpoch = false)
            }
        }
    }

    fun updatePendingTransactionCategory(
        txIndex: Int,
        newCategory: BankTransactionType,
        rememberForMerchant: Boolean = false,
        applyToAllMatching: Boolean = false
    ) {
        val requestEpoch = pendingEpoch.get()
        viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                if (pendingEpoch.get() != requestEpoch) return@withLock
                val current = _pendingStatementImport.value ?: return@withLock
                val updated = importCoordinator.updateTransactionCategory(current, txIndex, newCategory, rememberForMerchant, applyToAllMatching)
                if (pendingEpoch.get() == requestEpoch) setPendingStatementImport(updated, bumpEpoch = false)
            }
        }
    }

    fun updateImportedTransactionCategory(
        txId: Long,
        newCategory: BankTransactionType,
        rememberForMerchant: Boolean = false,
        applyToAllMatchingInMonth: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                val target = repository.getImportedTransactionById(txId) ?: return@withLock
                val isNowInternal = (newCategory == BankTransactionType.INTERNAL_TRANSFER)
                val newNettingReason = if (isNowInternal) "Manually marked as internal transfer" else ""
                val updatedTarget = target.copy(category = newCategory.name, isNetted = isNowInternal, nettingReason = newNettingReason)

                val txsToUpdate = mutableListOf(updatedTarget)
                if (applyToAllMatchingInMonth) {
                    repository.getImportedTransactionsDirect(target.yearMonth).forEach { other ->
                        if (other.id != target.id && StatementImportCoordinator.matchesMerchantInMonth(target, other)) {
                            txsToUpdate.add(other.copy(category = newCategory.name, isNetted = isNowInternal, nettingReason = newNettingReason))
                        }
                    }
                }
                repository.updateImportedTransactions(txsToUpdate)

                if (rememberForMerchant) {
                    val rawMerchant = target.counterpartyName.ifBlank { target.message }
                    val query = CzechMerchantCatalog.suggestMerchantSearchQuery(rawMerchant)
                        .ifBlank { BankStatementImporter.cleanPaymentDescription(rawMerchant) }
                    if (query.isNotBlank()) {
                        MerchantCategoryManager.saveOverride(getApplication(), query, newCategory)
                        val normalizedRuleKey = CzechMerchantCatalog.normalizeSimple(query).ifBlank { query.trim().lowercase() }
                        val nowYm = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
                        val updatedSettings = repository.updateSettingsTransformAndSyncLedger(nowYm) { cur ->
                            val currentRules = MerchantCategoryManager.parseRulesFromJson(cur.merchantRulesJson).toMutableMap()
                            currentRules[normalizedRuleKey] = newCategory
                            cur.copy(merchantRulesJson = MerchantCategoryManager.serializeRulesToJson(currentRules))
                        }
                        if (updatedSettings != null && pendingSettingsWrites.get() == 0) {
                            _liveSettings.value = updatedSettings
                        }
                    }
                }

                importCoordinator.recalculateMonthLedger(target.yearMonth)
                if (activeAuditReport.value?.yearMonth == target.yearMonth) reloadAuditReportForMonthInternal(target.yearMonth)
                val catLabel = formatCategoryLabel(newCategory)
                val txWord = if (txsToUpdate.size == 1) "transaction" else "transactions"
                _uiEvent.send(
                    UiMessage.ShowSnackbar("Updated category to $catLabel (${txsToUpdate.size} $txWord updated)")
                )
            }
        }
    }

    fun toggleImportedTransactionExcluded(txId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            importMutex.withLock {
                val target = repository.getImportedTransactionById(txId) ?: return@withLock
                val willBeExcluded = !target.isNetted
                val restoredCategory = if (!willBeExcluded && target.category == BankTransactionType.INTERNAL_TRANSFER.name) {
                    val currentSettings = repository.settingsFlow.first()
                    val settingsRules = MerchantCategoryManager.parseRulesFromJson(currentSettings.merchantRulesJson)
                    val overrides = MerchantCategoryManager.getOverrides(getApplication()) + settingsRules
                    val matched = CzechMerchantCatalog.matchCategory(
                        "${target.counterpartyName} ${target.counterpartyAccount} ${target.message}", overrides
                    )
                    (matched ?: if (target.amount < 0) BankTransactionType.GENERAL_EXPENSE else BankTransactionType.OTHER_INFLOW).name
                } else target.category

                val updated = target.copy(
                    category = restoredCategory,
                    isNetted = willBeExcluded,
                    nettingReason = if (willBeExcluded) "Manually excluded from budget" else ""
                )
                repository.updateImportedTransaction(updated)
                importCoordinator.recalculateMonthLedger(target.yearMonth)
                if (activeAuditReport.value?.yearMonth == target.yearMonth) reloadAuditReportForMonthInternal(target.yearMonth)
                val actionLabel = if (willBeExcluded) "Excluded from budget" else "Restored to budget"
                _uiEvent.send(UiMessage.ShowSnackbar("$actionLabel: ${target.counterpartyName.ifBlank { target.message }}"))
            }
        }
    }

    fun toggleAction(year: Int, actionId: String, @Suppress("UNUSED_PARAMETER") currentIsDone: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    repository.toggleActionState(year, actionId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to update action item"))
            }
        }
    }

    fun setSensitivityOverrides(returnPct: Double?, cpiPct: Double?, swrPct: Double?) {
        sensitivityReturnOverride.value = returnPct
        sensitivityCpiOverride.value = cpiPct
        sensitivitySwrOverride.value = swrPct
        savedStateHandle[KEY_SENSITIVITY_RETURN] = returnPct
        savedStateHandle[KEY_SENSITIVITY_CPI] = cpiPct
        savedStateHandle[KEY_SENSITIVITY_SWR] = swrPct
    }

    fun restoreFullBackup(payload: BackupManager.FullBackupPayload) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    loadAuditJob?.cancelAndJoin()
                    repository.restoreFullBackupData(
                        payload.settings, payload.ledgerEntries, payload.actionStates, payload.importedTransactions
                    )
                    val cleanRestored = BackupManager.sanitizeSettings(payload.settings, SettingsEntity.freshDefaults())
                    if (pendingSettingsWrites.get() == 0) {
                        _liveSettings.value = cleanRestored
                    }
                    val parsedRules = MerchantCategoryManager.parseRulesFromJson(cleanRestored.merchantRulesJson)
                    MerchantCategoryManager.saveAllOverrides(getApplication(), parsedRules)
                    val restoredLots = when {
                        payload.investmentLots.isNotEmpty() -> payload.investmentLots
                        payload.preferences.containsKey("user_investment_lots_json") ->
                            com.example.util.InvestmentLotManager.parseLotsFromJson(payload.preferences["user_investment_lots_json"] ?: "")
                        else -> emptyList()
                    }
                    com.example.util.InvestmentLotManager.persistLots(getApplication(), restoredLots)
                    preferencesRepository.restorePreferencesFromBackup(payload.preferences)
                    setSensitivityOverrides(null, null, null)
                    setPendingStatementImport(null, bumpEpoch = true)
                    activeAuditReport.value = null
                    _uiEvent.send(
                        UiMessage.ShowSnackbar(
                            "Full backup restored (${payload.ledgerEntries.size} months, ${payload.importedTransactions.size} transactions)"
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to restore backup"))
            }
        }
    }

    fun resetSettingsToDefault() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    val defaults = SettingsEntity.freshDefaults()
                    val currentYm = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague")).toString()
                    repository.updateSettingsAndSyncLedger(defaults, currentYm)
                    if (pendingSettingsWrites.get() == 0) {
                        _liveSettings.value = defaults
                    }
                    MerchantCategoryManager.clearOverrides(getApplication())
                    com.example.util.InvestmentLotManager.clearAll(getApplication())
                    setSensitivityOverrides(null, null, null)
                    _uiEvent.send(UiMessage.ShowSnackbar("Reset all settings to default"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to reset settings"))
            }
        }
    }

    fun clearAllUserData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    syncJob?.cancelAndJoin()
                    loadAuditJob?.cancelAndJoin()
                    val defaults = SettingsEntity.freshDefaults()
                    repository.withTransaction {
                        repository.clearAllData()
                        repository.saveSettings(defaults)
                    }
                    if (pendingSettingsWrites.get() == 0) {
                        _liveSettings.value = defaults
                    }
                    setSensitivityOverrides(null, null, null)
                    clearAuxiliaryStoresInternal(getApplication(), preferencesRepository)
                    liveRegulatoryData.value = null
                    setPendingStatementImport(null, bumpEpoch = true)
                    activeAuditReport.value = null
                    _uiEvent.send(UiMessage.ShowSnackbar("All user data cleared"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiEvent.send(UiMessage.ShowSnackbar("Failed to clear user data"))
            }
        }
    }
}
