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
import com.example.ui.state.BackupAndSyncController
import com.example.ui.state.LedgerSyncController
import com.example.ui.state.StatementImportController
import com.example.ui.state.computeCalculationStateInternal
import com.example.ui.state.persistPendingStatementToHandle
import com.example.ui.state.runOneTimeMigrationsInternal
import com.example.util.BackupManager
import com.example.util.BankTransactionType
import com.example.util.CrossStatementAuditReport
import com.example.util.StatementImportCoordinator
import com.example.util.StatementParseSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

class MainViewModel internal constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val repository: FinancialRepository,
    private val preferencesRepository: AppPreferencesRepository
) : AndroidViewModel(application) {

    @JvmOverloads
    constructor(
        application: Application,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ) : this(
        application = application,
        savedStateHandle = savedStateHandle,
        repository = AppDatabase.getDatabase(application).let { db ->
            FinancialRepository(
                db, db.settingsDao(), db.ledgerDao(), db.actionStateDao(), db.importedTransactionDao()
            )
        },
        preferencesRepository = AppPreferencesRepository(application)
    )

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

    val isPreferencesHydrated: StateFlow<Boolean> = preferencesRepository.snapshot
        .map { it.isHydrated }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesRepository.snapshot.value.isHydrated)

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

    val sensitivityReturnOverride = MutableStateFlow<Double?>(savedStateHandle.get<Double?>(KEY_SENSITIVITY_RETURN))
    val sensitivityCpiOverride = MutableStateFlow<Double?>(savedStateHandle.get<Double?>(KEY_SENSITIVITY_CPI))
    val sensitivitySwrOverride = MutableStateFlow<Double?>(savedStateHandle.get<Double?>(KEY_SENSITIVITY_SWR))

    val liveRegulatoryData = MutableStateFlow<CzechRegulatoryData?>(null)
    val isSyncing = MutableStateFlow(false)

    private val _pendingStatementImport = MutableStateFlow<StatementParseSummary?>(
        savedStateHandle.get<StatementParseSummary?>(KEY_PENDING_STATEMENT_IMPORT)
    )
    val pendingStatementImport: StateFlow<StatementParseSummary?> = _pendingStatementImport.asStateFlow()

    val lastImportTimestamp: StateFlow<Long?> = preferencesRepository.snapshot
        .map { it.lastImportTimestamp }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastCzechSyncTimestamp: StateFlow<Long?> = preferencesRepository.snapshot
        .map { it.lastCzechSyncTimestamp }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dismissedSubscriptionMerchants: StateFlow<Set<String>> = preferencesRepository.snapshot
        .map { it.dismissedSubscriptionMerchants }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val activeAuditReport = MutableStateFlow<CrossStatementAuditReport?>(null)

    private val pendingEpoch = AtomicLong(0L)
    private val importMutex = Mutex()
    private val importCoordinator by lazy { StatementImportCoordinator(getApplication(), repository, preferencesRepository) }

    private fun setPendingStatementImport(summary: StatementParseSummary?, bumpEpoch: Boolean) {
        if (bumpEpoch) pendingEpoch.incrementAndGet()
        _pendingStatementImport.value = summary
        persistPendingStatementToHandle(savedStateHandle, KEY_PENDING_STATEMENT_IMPORT, summary)
    }

    private suspend fun emitSnackbar(message: String) {
        _uiEvent.send(UiMessage.ShowSnackbar(message))
    }

    private val ledgerSyncController = LedgerSyncController(
        repository = repository,
        preferencesRepository = preferencesRepository,
        liveSettings = _liveSettings,
        pendingSettingsWrites = pendingSettingsWrites,
        activeAuditReport = activeAuditReport,
        importMutex = importMutex,
        emitSnackbar = ::emitSnackbar
    )

    private val statementImportController = StatementImportController(
        application = getApplication(),
        scope = viewModelScope,
        repository = repository,
        preferencesRepository = preferencesRepository,
        importCoordinator = importCoordinator,
        liveSettings = _liveSettings,
        pendingSettingsWrites = pendingSettingsWrites,
        activeAuditReport = activeAuditReport,
        pendingStatementImport = pendingStatementImport,
        setPendingStatementImport = ::setPendingStatementImport,
        pendingEpoch = pendingEpoch,
        importMutex = importMutex,
        emitSnackbar = ::emitSnackbar,
        importLedgerCsvFallback = ledgerSyncController::importLedgerCsvFallback
    )

    private val backupAndSyncController = BackupAndSyncController(
        application = getApplication(),
        scope = viewModelScope,
        repository = repository,
        preferencesRepository = preferencesRepository,
        liveSettings = _liveSettings,
        pendingSettingsWrites = pendingSettingsWrites,
        liveRegulatoryData = liveRegulatoryData,
        isSyncing = isSyncing,
        activeAuditReport = activeAuditReport,
        setPendingStatementImport = ::setPendingStatementImport,
        clearSensitivityOverrides = { setSensitivityOverrides(null, null, null) },
        cancelStatementJobs = statementImportController::cancelPendingJobs,
        importMutex = importMutex,
        emitSnackbar = ::emitSnackbar
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefSnap = preferencesRepository.hydrate()
                backupAndSyncController.hydrateFxRatesFromJson(prefSnap.cnbFxRatesJson)
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

    fun setThemeMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) { preferencesRepository.setThemeMode(mode) }
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { preferencesRepository.setFlagSecureEnabled(enabled) }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { preferencesRepository.setBiometricLockEnabled(enabled) }
    }

    fun syncLiveCzechData() = backupAndSyncController.syncLiveCzechData()

    fun updateSettings(showSnackbar: Boolean = false, transform: (SettingsEntity) -> SettingsEntity) =
        backupAndSyncController.updateSettings(showSnackbar, transform)

    fun updateSettings(newSettings: SettingsEntity, showSnackbar: Boolean = false) =
        backupAndSyncController.updateSettings(showSnackbar) { newSettings }

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
            ledgerSyncController.addLedgerEntryWithOptionalBalances(
                yearMonth, incVaclav, incEleonora, incUnforeseen, expRent, expLiving,
                notes, portfolioBalance, pensionBalance, emergencyReserve
            )
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
            ledgerSyncController.updateLedgerEntryWithOptionalBalances(
                entry, portfolioBalance, pensionBalance, emergencyReserve
            )
        }
    }

    fun deleteLedgerEntry(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { ledgerSyncController.deleteLedgerEntry(id) }
    }

    fun dismissSubscription(merchantKey: String) {
        viewModelScope.launch(Dispatchers.IO) { backupAndSyncController.dismissSubscription(merchantKey) }
    }

    fun restoreSubscription(merchantKey: String) {
        viewModelScope.launch(Dispatchers.IO) { backupAndSyncController.restoreSubscription(merchantKey) }
    }

    fun clearAllDismissedSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) { backupAndSyncController.clearAllDismissedSubscriptions() }
    }

    fun deleteImportedStatement(yearMonth: String, bankName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) { statementImportController.deleteImportedStatement(yearMonth, bankName) }
    }

    fun importStatements(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { statementImportController.importStatements(uris) }
    }

    fun importCsvData(uri: Uri) = importStatements(listOf(uri))

    fun loadAuditReportForMonth(yearMonth: String) = statementImportController.loadAuditReportForMonth(yearMonth)

    fun clearAuditReport() = statementImportController.clearAuditReport()

    suspend fun recalculateMonthLedger(targetYm: String) = importCoordinator.recalculateMonthLedger(targetYm)

    fun confirmStatementImport(summary: StatementParseSummary) {
        viewModelScope.launch(Dispatchers.IO) { statementImportController.confirmStatementImport(summary) }
    }

    fun dismissStatementImport() = statementImportController.dismissStatementImport()

    fun togglePendingTransactionExcluded(txIndex: Int) {
        viewModelScope.launch(Dispatchers.Default) { statementImportController.togglePendingTransactionExcluded(txIndex) }
    }

    fun bulkExcludeMerchant(merchantQuery: String) {
        viewModelScope.launch(Dispatchers.Default) { statementImportController.bulkExcludeMerchant(merchantQuery) }
    }

    fun updatePendingTransactionCategory(
        txIndex: Int,
        newCategory: BankTransactionType,
        rememberForMerchant: Boolean = false,
        applyToAllMatching: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            statementImportController.updatePendingTransactionCategory(
                txIndex, newCategory, rememberForMerchant, applyToAllMatching
            )
        }
    }

    fun updateImportedTransactionCategory(
        txId: Long,
        newCategory: BankTransactionType,
        rememberForMerchant: Boolean = false,
        applyToAllMatchingInMonth: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            statementImportController.updateImportedTransactionCategory(
                txId, newCategory, rememberForMerchant, applyToAllMatchingInMonth
            )
        }
    }

    fun toggleImportedTransactionExcluded(txId: Long) {
        viewModelScope.launch(Dispatchers.IO) { statementImportController.toggleImportedTransactionExcluded(txId) }
    }

    fun toggleAction(year: Int, actionId: String, @Suppress("UNUSED_PARAMETER") currentIsDone: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { backupAndSyncController.toggleAction(year, actionId) }
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
        viewModelScope.launch(Dispatchers.IO) { backupAndSyncController.restoreFullBackup(payload) }
    }

    fun resetSettingsToDefault() {
        viewModelScope.launch(Dispatchers.IO) { backupAndSyncController.resetSettingsToDefault() }
    }

    fun clearAllUserData() {
        viewModelScope.launch(Dispatchers.IO) { backupAndSyncController.clearAllUserData() }
    }
}
