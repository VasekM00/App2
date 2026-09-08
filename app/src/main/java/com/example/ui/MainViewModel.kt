package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiMessage {
    data class ShowSnackbar(val message: String) : UiMessage
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = FinancialRepository(
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
            repository.addLedgerEntry(entry)
            _uiEvent.emit(UiMessage.ShowSnackbar("Ledger entry added for $yearMonth"))
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

                // 1. Try bank statement parser first (Moneta, CSOB, mBank) - supports PDF and CSV
                try {
                    val overrides = com.example.util.MerchantCategoryManager.getOverrides(getApplication())
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
                    _uiEvent.emit(UiMessage.ShowSnackbar("No valid entries found in statement"))
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

    fun importStatementData(uri: android.net.Uri) {
        importCsvData(uri)
    }

    fun confirmStatementImport(summary: com.example.util.StatementParseSummary) {
        viewModelScope.launch {
            // 1. Remove previous transactions for this bank and yearMonth to prevent duplicates on re-import
            repository.deleteImportedTransactionsForBankAndMonth(summary.yearMonth, summary.detectedBank.name)

            // 2. Map summary transactions to entities and persist to Room (v23)
            val txEntities = summary.transactions.map { tx ->
                com.example.data.ImportedBankTransactionEntity(
                    yearMonth = summary.yearMonth,
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
            repository.saveImportedTransactions(txEntities)

            // 3. Reconcile across all banks for this month (Cross-Statement Pairwise Matcher with 5-day clearing window)
            val allMonthTxs = repository.getImportedTransactionsDirect(summary.yearMonth)
            val (reconciledTxs, auditReport) = com.example.util.CrossStatementReconciliationEngine.reconcileTransactions(allMonthTxs, summary.yearMonth)
            repository.updateImportedTransactions(reconciledTxs)

            // 4. Update or insert LedgerEntryEntity with true reconciled figures
            val existingEntry = repository.getLedgerEntryByYearMonth(summary.yearMonth)
            val banksSummary = auditReport.participatingBanks.joinToString(" + ")
            val nettingNote = if (auditReport.totalNettedAmount > 0) " | ${auditReport.matchedPairs.size} cross-transfers netted (${com.example.util.Formatters.fmtCompact(auditReport.totalNettedAmount)})" else ""
            val autoNotes = "Imported from $banksSummary (${allMonthTxs.size} txs)$nettingNote"

            // Compute clean categorized sums directly from reconciled transactions
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

            if (existingEntry != null) {
                val updated = existingEntry.copy(
                    incVaclav = if (incVaclav > 0) incVaclav else existingEntry.incVaclav,
                    incEleonora = if (incEleonora > 0) incEleonora else existingEntry.incEleonora,
                    incUnforeseen = if (incOther > 0) incOther else existingEntry.incUnforeseen,
                    expRent = if (expRent > 0) expRent else existingEntry.expRent,
                    expGroceries = if (expGroceries > 0) expGroceries else existingEntry.expGroceries,
                    expOther = if (expOther > 0) expOther else existingEntry.expOther,
                    notes = autoNotes,
                    emergencyReserveAtMonthEnd = summary.monthEndBalance ?: existingEntry.emergencyReserveAtMonthEnd
                )
                repository.updateLedgerEntry(updated)
                _uiEvent.emit(UiMessage.ShowSnackbar("Reconciled ${summary.yearMonth}: ${allMonthTxs.size} transactions from $banksSummary"))
            } else {
                val newEntry = com.example.data.LedgerEntryEntity(
                    yearMonth = summary.yearMonth,
                    incVaclav = incVaclav,
                    incEleonora = incEleonora,
                    incUnforeseen = incOther,
                    expRent = expRent,
                    expGroceries = expGroceries,
                    expOther = expOther,
                    notes = autoNotes,
                    emergencyReserveAtMonthEnd = summary.monthEndBalance ?: 0.0
                )
                repository.addLedgerEntry(newEntry)
                _uiEvent.emit(UiMessage.ShowSnackbar("Saved ${summary.yearMonth} from $banksSummary (${allMonthTxs.size} transactions)"))
            }
            _pendingStatementImport.value = null
            activeAuditReport.value = auditReport
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
            repository.toggleActionState(year, actionId)
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

