package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.example.data.AppDatabase
import com.example.data.FinancialRepository
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
import java.time.YearMonth

sealed interface UiMessage {
    data class ShowSnackbar(val message: String) : UiMessage
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = FinancialRepository(
        database = db,
        settingsDao = db.settingsDao(),
        ledgerDao = db.ledgerDao(),
        actionStateDao = db.actionStateDao(),
        importedTransactionDao = db.importedTransactionDao()
    )

    private val _uiEvent = MutableSharedFlow<UiMessage>()
    val uiEvent: SharedFlow<UiMessage> = _uiEvent.asSharedFlow()

    val ledgerEntries: StateFlow<List<LedgerEntryEntity>> = repository.ledgerFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val actionStates: StateFlow<Map<String, Boolean>> = repository.actionStatesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val settingsState: StateFlow<SettingsEntity> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsEntity()
        )

    val importedBankSourcesByMonth: StateFlow<Map<String, Set<String>>> = repository.getAllImportedTransactions()
        .map { txList ->
            txList.groupBy { it.yearMonth }
                .mapValues { (_, txs) -> txs.map { it.bankName }.filter { it.isNotBlank() }.toSet() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val allImportedTransactions: StateFlow<List<com.example.data.ImportedBankTransactionEntity>> = repository.getAllImportedTransactions()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Sensitivity slider override states
    val sensitivityReturnOverride = MutableStateFlow<Double?>(null)
    val sensitivityCpiOverride = MutableStateFlow<Double?>(null)
    val sensitivitySwrOverride = MutableStateFlow<Double?>(null)

    // Live Czech Economic & Regulatory Sync state
    val liveRegulatoryData = MutableStateFlow<com.example.domain.CzechRegulatoryData?>(null)
    val isSyncing = MutableStateFlow(false)

    // Pending bank statement import review
    private val _pendingStatementImport = MutableStateFlow<com.example.util.StatementParseSummary?>(null)
    val pendingStatementImport: StateFlow<com.example.util.StatementParseSummary?> = _pendingStatementImport.asStateFlow()

    // Statement import recency tracking
    private val importPrefs = application.getSharedPreferences("statement_import_prefs", android.content.Context.MODE_PRIVATE)
    private val _lastImportTimestamp = MutableStateFlow<Long?>(
        importPrefs.getLong("last_import_all", 0L).let { if (it > 0L) it else null }
    )
    val lastImportTimestamp: StateFlow<Long?> = _lastImportTimestamp.asStateFlow()

    init {
        viewModelScope.launch { repository.repairLegacyEmployerContribution() }
        viewModelScope.launch(Dispatchers.IO) {
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
                        toUpdate.add(
                            e.copy(
                                portfolioBalanceAtMonthEnd = snapLiquid,
                                pensionBalanceAtMonthEnd = snapPension,
                                emergencyReserveAtMonthEnd = reserve
                            )
                        )
                    }
                }
                if (toUpdate.isNotEmpty()) {
                    repository.updateLedgerEntries(toUpdate)
                }
            }
        }
    }

    fun syncLiveCzechData() {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                val data = com.example.util.CzechEconomicSyncService.fetchLiveRegulatoryData()
                liveRegulatoryData.value = data
                _uiEvent.emit(UiMessage.ShowSnackbar("Czech benchmarks fetched from ${data.sourceName}"))
            } catch (e: Exception) {
                _uiEvent.emit(UiMessage.ShowSnackbar("Sync completed with fallback statutory parameters"))
            } finally {
                isSyncing.value = false
            }
        }
    }

    val calculationState: StateFlow<FullCalculationState> = combine(
        settingsState,
        actionStates,
        sensitivityReturnOverride,
        sensitivityCpiOverride,
        sensitivitySwrOverride
    ) { settings, actions, retOver, cpiOver, swrOver ->
        var effectiveSettings = settings
        if (retOver != null) effectiveSettings = effectiveSettings.copy(portfolioNominalReturnPct = retOver)
        if (cpiOver != null) effectiveSettings = effectiveSettings.copy(cpiInflationPct = cpiOver)
        if (swrOver != null) effectiveSettings = effectiveSettings.copy(safeWithdrawalRatePct = swrOver)

        FinancialEngine.calculate(effectiveSettings, actions)
    }.flowOn(Dispatchers.Default).conflate().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinancialEngine.calculate(SettingsEntity(), runMonteCarlo = false)
    )

    fun updateSettings(newSettings: SettingsEntity, showSnackbar: Boolean = false) {
        viewModelScope.launch {
            repository.saveSettings(newSettings)
            if (showSnackbar) {
                _uiEvent.emit(UiMessage.ShowSnackbar("Settings saved successfully"))
            }
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
        viewModelScope.launch {
            val entry = LedgerEntryEntity(
                yearMonth = yearMonth,
                incVaclav = incVaclav,
                incEleonora = incEleonora,
                incUnforeseen = incUnforeseen,
                expRent = expRent,
                expGroceries = expLiving,
                expOther = 0.0,
                notes = notes,
                portfolioBalanceAtMonthEnd = portfolioBalance,
                pensionBalanceAtMonthEnd = pensionBalance,
                emergencyReserveAtMonthEnd = emergencyReserve
            )
            val existing = repository.getLedgerEntryByYearMonth(yearMonth)
            if (existing != null) {
                repository.updateLedgerEntry(entry.copy(id = existing.id))
                _uiEvent.emit(UiMessage.ShowSnackbar("Ledger entry updated for $yearMonth"))
            } else {
                repository.addLedgerEntry(entry)
                _uiEvent.emit(UiMessage.ShowSnackbar("Ledger entry added for $yearMonth"))
            }
        }
    }

    fun updateLedgerEntry(entry: LedgerEntryEntity) {
        viewModelScope.launch {
            repository.updateLedgerEntry(entry)
            _uiEvent.emit(UiMessage.ShowSnackbar("Ledger entry updated for ${entry.yearMonth}"))
        }
    }

    fun deleteLedgerEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteLedgerEntry(id)
            _uiEvent.emit(UiMessage.ShowSnackbar("Ledger entry deleted"))
        }
    }

    fun importCsvData(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiEvent.emit(UiMessage.ShowSnackbar("Error: Unable to open statement file"))
                    return@launch
                }
                val bytes = inputStream.use { it.readBytes() }

                if (!com.example.util.BankStatementImporter.isWithinSizeLimit(bytes.size)) {
                    _uiEvent.emit(UiMessage.ShowSnackbar("Statement file is too large (max 25 MB)"))
                    return@launch
                }

                // 1. Try bank statement parser first (Moneta, CSOB, mBank) - supports PDF and CSV
                try {
                    val settingsRules = com.example.util.MerchantCategoryManager.parseRulesFromJson(settingsState.value.merchantRulesJson)
                    val prefOverrides = com.example.util.MerchantCategoryManager.getOverrides(getApplication())
                    val overrides = prefOverrides + settingsRules
                    val bankSummary = com.example.util.BankStatementImporter.parseStatement(
                        bytes.inputStream(),
                        userOverrides = overrides
                    )
                    if (bankSummary.detectedBank != com.example.util.BankType.GENERIC && bankSummary.transactions.isNotEmpty() && bankSummary.yearMonth.matches(Regex("""\d{4}-\d{2}"""))) {
                        _pendingStatementImport.value = bankSummary
                        return@launch
                    }
                } catch (_: Exception) {
                    // Fall back to standard ledger CSV format below
                }

                // 2. Standard monthly ledger CSV format fallback
                val existingYearMonths = repository.existingYearMonths()
                val entriesToInsert = mutableListOf<LedgerEntryEntity>()
                var skippedDuplicates = 0
                java.io.BufferedReader(java.io.InputStreamReader(bytes.inputStream())).use { reader ->
                    var line: String? = reader.readLine() // Skip header
                    while (run { line = reader.readLine(); line } != null) {
                        val rawLine = line!!.trim()
                        if (rawLine.isBlank()) continue
                        val tokens = parseCsvLine(rawLine)
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
                                    entriesToInsert.add(
                                        LedgerEntryEntity(
                                            yearMonth = ym,
                                            incVaclav = incV,
                                            incEleonora = incE,
                                            incUnforeseen = incExtra,
                                            expRent = expR,
                                            expGroceries = expG + expO,
                                            expOther = 0.0,
                                            notes = notes
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (entriesToInsert.isNotEmpty()) {
                    repository.addLedgerEntries(entriesToInsert)
                    val dupNote = if (skippedDuplicates > 0) " ($skippedDuplicates duplicate months skipped)" else ""
                    _uiEvent.emit(UiMessage.ShowSnackbar("Successfully imported ${entriesToInsert.size} entries!$dupNote"))
                } else if (skippedDuplicates > 0) {
                    _uiEvent.emit(UiMessage.ShowSnackbar("All $skippedDuplicates entries already exist - nothing imported"))
                } else {
                    _uiEvent.emit(UiMessage.ShowSnackbar("No transactions found. Supported: Moneta, ČSOB, mBank PDF/CSV, or ledger CSV."))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiMessage.ShowSnackbar("Statement import failed: ${e.localizedMessage ?: "Invalid format"}"))
            }
        }
    }

    // Active Cross-Statement Audit Report for transparency
    val activeAuditReport = MutableStateFlow<com.example.util.CrossStatementAuditReport?>(null)

    fun loadAuditReportForMonth(yearMonth: String) {
        viewModelScope.launch {
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
                val (_, report) = com.example.util.CrossStatementReconciliationEngine.reconcileTransactions(txsToUse, yearMonth)
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
        val allMonthTxs = repository.getImportedTransactionsDirect(targetYm)
        if (allMonthTxs.isEmpty()) return

        val incVaclav = allMonthTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.SALARY_VACLAV.name }.sumOf { it.amount }
        val incEleonora = allMonthTxs.filter { !it.isNetted && (it.category == com.example.util.BankTransactionType.SALARY_ELEONORA.name || it.category == com.example.util.BankTransactionType.PARENTAL_BENEFIT.name) }.sumOf { it.amount }
        val incOther = allMonthTxs.filter { !it.isNetted && (it.category == com.example.util.BankTransactionType.OTHER_INFLOW.name || (it.category == com.example.util.BankTransactionType.UNCATEGORIZED.name && it.amount > 0)) }.sumOf { it.amount }
        val expRent = allMonthTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.HOUSING_RENT.name }.sumOf { kotlin.math.abs(it.amount) }
        val expGroceries = allMonthTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.GROCERIES.name }.sumOf { kotlin.math.abs(it.amount) }
        val expOther = allMonthTxs.filter { !it.isNetted && (
            it.category == com.example.util.BankTransactionType.LIFESTYLE_LIVING.name ||
            it.category == com.example.util.BankTransactionType.DINING_RESTAURANT.name ||
            it.category == com.example.util.BankTransactionType.TRANSPORTATION.name ||
            it.category == com.example.util.BankTransactionType.SHOPPING_GOODS.name ||
            it.category == com.example.util.BankTransactionType.HEALTH_DRUGSTORE.name ||
            it.category == com.example.util.BankTransactionType.SUBSCRIPTIONS_MEDIA.name ||
            it.category == com.example.util.BankTransactionType.SERVICES_UTILITIES.name ||
            it.category == com.example.util.BankTransactionType.ATM_CASH.name ||
            it.category == com.example.util.BankTransactionType.CHARITY_DONATION.name ||
            it.category == com.example.util.BankTransactionType.GENERAL_EXPENSE.name ||
            (it.category == com.example.util.BankTransactionType.UNCATEGORIZED.name && it.amount < 0)
        ) }.sumOf { kotlin.math.abs(it.amount) }

        val invPortu = allMonthTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.INVESTMENT_PORTU.name }.sumOf { kotlin.math.abs(it.amount) }
        val invDip = allMonthTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.INVESTMENT_DIP.name }.sumOf { kotlin.math.abs(it.amount) }
        val invDps = allMonthTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.INVESTMENT_DPS.name }.sumOf { kotlin.math.abs(it.amount) }
        val totalInvested = invPortu + invDip + invDps

        val existingEntry = repository.getLedgerEntryByYearMonth(targetYm)
        val participatingBanks = allMonthTxs.map { it.bankName }.filter { it.isNotBlank() }.distinct().sorted()
        val banksSummary = participatingBanks.joinToString(" + ")
        val nettedCount = allMonthTxs.count { it.isNetted }
        val nettingNote = if (nettedCount > 0) " | $nettedCount txs netted" else ""
        val investNote = if (totalInvested > 0) {
            val parts = mutableListOf<String>()
            if (invPortu > 0) parts.add("Portu: ${com.example.util.Formatters.fmtCompact(invPortu)}")
            if (invDip > 0) parts.add("DIP: ${com.example.util.Formatters.fmtCompact(invDip)}")
            if (invDps > 0) parts.add("DPS: ${com.example.util.Formatters.fmtCompact(invDps)}")
            " | Invested: ${com.example.util.Formatters.fmtCompact(totalInvested)} (${parts.joinToString(", ")})"
        } else ""
        val autoNotes = "Imported from $banksSummary (${allMonthTxs.size} txs)$nettingNote$investNote"

        if (existingEntry != null) {
            val updated = existingEntry.copy(
                incVaclav = if (incVaclav > 0) incVaclav else existingEntry.incVaclav,
                incEleonora = if (incEleonora > 0) incEleonora else existingEntry.incEleonora,
                incUnforeseen = if (incOther > 0) incOther else existingEntry.incUnforeseen,
                expRent = if (expRent > 0) expRent else existingEntry.expRent,
                expGroceries = if (expGroceries > 0) expGroceries else existingEntry.expGroceries,
                expOther = if (expOther > 0) expOther else existingEntry.expOther,
                notes = autoNotes
            )
            repository.updateLedgerEntry(updated)
        }
    }

    fun confirmStatementImport(summary: com.example.util.StatementParseSummary) {
        viewModelScope.launch {
            // Group transactions by calendar month (YYYY-MM)
            val txsByMonth = summary.transactions.groupBy { tx ->
                val ym = tx.date.take(7)
                if (ym.matches(Regex("""\d{4}-\d{2}"""))) ym else summary.yearMonth
            }

            val latestMonth = txsByMonth.keys.maxOrNull() ?: summary.yearMonth
            val affectedMonths = txsByMonth.keys.sorted()

            for ((targetYm, monthTxs) in txsByMonth) {
                // 1. Smart Fingerprint Merge: insert only new non-duplicate transactions for this bank and month
                val txEntities = monthTxs.map { tx ->
                    com.example.data.ImportedBankTransactionEntity(
                        yearMonth = targetYm,
                        bankName = summary.detectedBank.name,
                        date = tx.date,
                        amount = tx.amount,
                        counterpartyAccount = tx.counterpartyAccount,
                        counterpartyName = tx.counterpartyName,
                        message = tx.message,
                        variableSymbol = tx.variableSymbol,
                        category = tx.category.name,
                        isNetted = tx.isNetted,
                        nettingReason = tx.nettingReason
                    )
                }
                repository.saveImportedTransactionsSmartMerge(targetYm, summary.detectedBank.name, txEntities)

                // 2. Fetch boundary transactions within ±5 days across month borders for cross-month pairing
                val ymObj = try { YearMonth.parse(targetYm) } catch (e: Exception) { null }
                val boundaryTxs = if (ymObj != null) {
                    val firstDay = ymObj.atDay(1)
                    val lastDay = ymObj.atEndOfMonth()
                    val prevStart = firstDay.minusDays(5).toString()
                    val prevEnd = firstDay.minusDays(1).toString()
                    val nextStart = lastDay.plusDays(1).toString()
                    val nextEnd = lastDay.plusDays(5).toString()

                    val prevTxs = repository.getTransactionsInDateRangeDirect(prevStart, prevEnd)
                    val nextTxs = repository.getTransactionsInDateRangeDirect(nextStart, nextEnd)
                    (prevTxs + nextTxs).filter { !it.isNetted }
                } else emptyList()

                // 3. Reconcile across all banks for targetYm including cross-month boundaries
                val allMonthTxs = repository.getImportedTransactionsDirect(targetYm)
                val reconcileResult = com.example.util.CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
                    transactions = allMonthTxs,
                    yearMonth = targetYm,
                    boundaryTransactions = boundaryTxs
                )
                val reconciledTxs = reconcileResult.currentMonthTxs
                val auditReport = reconcileResult.auditReport
                repository.updateImportedTransactions(reconciledTxs)

                // Persist updated adjacent boundary transactions and recalculate adjacent month ledgers
                if (reconcileResult.updatedBoundaryTxs.isNotEmpty()) {
                    repository.updateImportedTransactions(reconcileResult.updatedBoundaryTxs)
                    val affectedAdjacentMonths = reconcileResult.updatedBoundaryTxs.map { it.yearMonth }.distinct().filter { it != targetYm }
                    for (adjYm in affectedAdjacentMonths) {
                        recalculateMonthLedger(adjYm)
                    }
                }

                if (targetYm == latestMonth || targetYm == summary.yearMonth) {
                    activeAuditReport.value = auditReport
                }

                // 4. Compute clean categorized sums and investment DCA flows
                val incVaclav = reconciledTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.SALARY_VACLAV.name }.sumOf { it.amount }
                val incEleonora = reconciledTxs.filter { !it.isNetted && (it.category == com.example.util.BankTransactionType.SALARY_ELEONORA.name || it.category == com.example.util.BankTransactionType.PARENTAL_BENEFIT.name) }.sumOf { it.amount }
                val incOther = reconciledTxs.filter { !it.isNetted && (it.category == com.example.util.BankTransactionType.OTHER_INFLOW.name || (it.category == com.example.util.BankTransactionType.UNCATEGORIZED.name && it.amount > 0)) }.sumOf { it.amount }
                val expRent = reconciledTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.HOUSING_RENT.name }.sumOf { kotlin.math.abs(it.amount) }
                val expGroceries = reconciledTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.GROCERIES.name }.sumOf { kotlin.math.abs(it.amount) }
                val expOther = reconciledTxs.filter { !it.isNetted && (
                    it.category == com.example.util.BankTransactionType.LIFESTYLE_LIVING.name ||
                    it.category == com.example.util.BankTransactionType.DINING_RESTAURANT.name ||
                    it.category == com.example.util.BankTransactionType.TRANSPORTATION.name ||
                    it.category == com.example.util.BankTransactionType.SHOPPING_GOODS.name ||
                    it.category == com.example.util.BankTransactionType.HEALTH_DRUGSTORE.name ||
                    it.category == com.example.util.BankTransactionType.SUBSCRIPTIONS_MEDIA.name ||
                    it.category == com.example.util.BankTransactionType.SERVICES_UTILITIES.name ||
                    it.category == com.example.util.BankTransactionType.ATM_CASH.name ||
                    it.category == com.example.util.BankTransactionType.CHARITY_DONATION.name ||
                    it.category == com.example.util.BankTransactionType.GENERAL_EXPENSE.name ||
                    (it.category == com.example.util.BankTransactionType.UNCATEGORIZED.name && it.amount < 0)
                ) }.sumOf { kotlin.math.abs(it.amount) }

                val invPortu = reconciledTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.INVESTMENT_PORTU.name }.sumOf { kotlin.math.abs(it.amount) }
                val invDip = reconciledTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.INVESTMENT_DIP.name }.sumOf { kotlin.math.abs(it.amount) }
                val invDps = reconciledTxs.filter { !it.isNetted && it.category == com.example.util.BankTransactionType.INVESTMENT_DPS.name }.sumOf { kotlin.math.abs(it.amount) }
                val totalInvested = invPortu + invDip + invDps

                // 5. Update or insert LedgerEntryEntity with true reconciled figures
                val existingEntry = repository.getLedgerEntryByYearMonth(targetYm)
                val banksSummary = auditReport.participatingBanks.joinToString(" + ")
                val nettingNote = if (auditReport.totalNettedAmount > 0) " | ${auditReport.matchedPairs.size} cross-transfers netted (${com.example.util.Formatters.fmtCompact(auditReport.totalNettedAmount)})" else ""
                val investNote = if (totalInvested > 0) {
                    val parts = mutableListOf<String>()
                    if (invPortu > 0) parts.add("Portu: ${com.example.util.Formatters.fmtCompact(invPortu)}")
                    if (invDip > 0) parts.add("DIP: ${com.example.util.Formatters.fmtCompact(invDip)}")
                    if (invDps > 0) parts.add("DPS: ${com.example.util.Formatters.fmtCompact(invDps)}")
                    " | Invested: ${com.example.util.Formatters.fmtCompact(totalInvested)} (${parts.joinToString(", ")})"
                } else ""
                val autoNotes = "Imported from $banksSummary (${allMonthTxs.size} txs)$nettingNote$investNote"

                // The closing balance belongs to the latest month of the statement, but it is the
                // account's cash balance — NOT the dedicated emergency reserve — so it must not be
                // written into emergencyReserveAtMonthEnd (that corrupted the actual net-worth line).
                val cur = settingsState.value
                val isSingle = cur.isSingleHousehold
                val snapLiquid = cur.liquidPortfolioCurrent + if (!isSingle) cur.eLiquidPortfolioCurrent else 0.0
                val snapPension = cur.dipBalanceCurrent + cur.dpsBalanceCurrent + if (!isSingle) (cur.eDipBalanceCurrent + cur.eDpsBalanceCurrent) else 0.0

                if (existingEntry != null) {
                    val updated = existingEntry.copy(
                        incVaclav = if (incVaclav > 0) incVaclav else existingEntry.incVaclav,
                        incEleonora = if (incEleonora > 0) incEleonora else existingEntry.incEleonora,
                        incUnforeseen = if (incOther > 0) incOther else existingEntry.incUnforeseen,
                        expRent = if (expRent > 0) expRent else existingEntry.expRent,
                        expGroceries = if (expGroceries > 0) expGroceries else existingEntry.expGroceries,
                        expOther = if (expOther > 0) expOther else existingEntry.expOther,
                        notes = autoNotes,
                        portfolioBalanceAtMonthEnd = if (existingEntry.portfolioBalanceAtMonthEnd > 0) existingEntry.portfolioBalanceAtMonthEnd else snapLiquid,
                        pensionBalanceAtMonthEnd = if (existingEntry.pensionBalanceAtMonthEnd > 0) existingEntry.pensionBalanceAtMonthEnd else snapPension,
                        emergencyReserveAtMonthEnd = if (existingEntry.emergencyReserveAtMonthEnd > 0) existingEntry.emergencyReserveAtMonthEnd else cur.emergencyReserveCurrent
                    )
                    repository.updateLedgerEntry(updated)
                } else {
                    val newEntry = com.example.data.LedgerEntryEntity(
                        yearMonth = targetYm,
                        incVaclav = incVaclav,
                        incEleonora = incEleonora,
                        incUnforeseen = incOther,
                        expRent = expRent,
                        expGroceries = expGroceries,
                        expOther = expOther,
                        notes = autoNotes,
                        portfolioBalanceAtMonthEnd = snapLiquid,
                        pensionBalanceAtMonthEnd = snapPension,
                        emergencyReserveAtMonthEnd = cur.emergencyReserveCurrent
                    )
                    repository.addLedgerEntry(newEntry)
                }
            }

            val now = System.currentTimeMillis()
            importPrefs.edit {
                putLong("last_import_all", now)
                putLong("last_import_${summary.detectedBank.name}", now)
            }
            _lastImportTimestamp.value = now

            val monthsLabel = affectedMonths.joinToString(", ")
            _uiEvent.emit(UiMessage.ShowSnackbar("Imported and split statement into $monthsLabel (${summary.transactions.size} txs from ${summary.detectedBank.name})"))
            _pendingStatementImport.value = null
        }
    }

    fun dismissStatementImport() {
        _pendingStatementImport.value = null
    }

    fun updatePendingTransactionCategory(
        txIndex: Int,
        newCategory: com.example.util.BankTransactionType,
        rememberForMerchant: Boolean = false
    ) {
        val currentSummary = _pendingStatementImport.value ?: return
        if (txIndex !in currentSummary.transactions.indices) return

        val targetTx = currentSummary.transactions[txIndex]
        val updatedTransactions = currentSummary.transactions.toMutableList()
        val isNetted = (newCategory == com.example.util.BankTransactionType.INTERNAL_TRANSFER)
        val nettingReason = if (isNetted) com.example.util.BankStatementImporter.determineNettingReason(newCategory, targetTx.counterpartyName, targetTx.message) else ""
        updatedTransactions[txIndex] = targetTx.copy(category = newCategory, isNetted = isNetted, nettingReason = nettingReason)

        if (rememberForMerchant) {
            val merchantName = targetTx.counterpartyName.ifBlank { targetTx.message }
            val query = com.example.util.CzechMerchantCatalog.suggestMerchantSearchQuery(merchantName)
            if (query.isNotBlank()) {
                com.example.util.MerchantCategoryManager.saveOverride(getApplication(), query, newCategory)
                val currentRules = com.example.util.MerchantCategoryManager.parseRulesFromJson(settingsState.value.merchantRulesJson).toMutableMap()
                currentRules[query.trim().lowercase()] = newCategory
                updateSettings(settingsState.value.copy(merchantRulesJson = com.example.util.MerchantCategoryManager.serializeRulesToJson(currentRules)))

                val normQuery = com.example.util.CzechMerchantCatalog.normalize(query)
                for (i in updatedTransactions.indices) {
                    val otherTx = updatedTransactions[i]
                    val otherNorm = com.example.util.CzechMerchantCatalog.normalize("${otherTx.counterpartyName} ${otherTx.message}")
                    if (otherNorm.contains(normQuery)) {
                        val otherReason = if (isNetted) com.example.util.BankStatementImporter.determineNettingReason(newCategory, otherTx.counterpartyName, otherTx.message) else ""
                        updatedTransactions[i] = otherTx.copy(category = newCategory, isNetted = isNetted, nettingReason = otherReason)
                    }
                }
            }
        }

        _pendingStatementImport.value = com.example.util.BankStatementImporter.recomputeSummary(
            currentSummary,
            updatedTransactions
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }

    fun toggleAction(year: Int, actionId: String, currentIsDone: Boolean) {
        viewModelScope.launch {
            // Persist an explicit target state (instead of a blind DB toggle) so the UI's
            // notion of "done" always matches what is stored.
            repository.setActionState(year, actionId, !currentIsDone)
        }
    }

    fun setSensitivityOverrides(returnPct: Double?, cpiPct: Double?, swrPct: Double?) {
        sensitivityReturnOverride.value = returnPct
        sensitivityCpiOverride.value = cpiPct
        sensitivitySwrOverride.value = swrPct
    }

    fun resetSettingsToDefault() {
        viewModelScope.launch {
            repository.saveSettings(SettingsEntity.freshDefaults())
            setSensitivityOverrides(null, null, null)
            _uiEvent.emit(UiMessage.ShowSnackbar("Reset all settings to default"))
        }
    }

    fun clearAllUserData() {
        viewModelScope.launch {
            repository.clearAllData()
            repository.saveSettings(SettingsEntity.freshDefaults())
            setSensitivityOverrides(null, null, null)
            _uiEvent.emit(UiMessage.ShowSnackbar("All user data cleared"))
        }
    }
}

