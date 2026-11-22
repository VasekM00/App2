package com.example.ui.state

import android.app.Application
import android.net.Uri
import com.example.data.AppPreferencesRepository
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.SettingsEntity
import com.example.domain.parseAccountListJson
import com.example.ui.components.formatCategoryLabel
import com.example.ui.tabs.friendlyBankName
import com.example.util.BankStatementImporter
import com.example.util.BankTransactionType
import com.example.util.CrossStatementAuditReport
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.CzechMerchantCatalog
import com.example.util.MerchantCategoryManager
import com.example.util.StatementImportCoordinator
import com.example.util.StatementParseSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class StatementImportController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val repository: FinancialRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val importCoordinator: StatementImportCoordinator,
    private val liveSettings: MutableStateFlow<SettingsEntity>,
    private val pendingSettingsWrites: AtomicInteger,
    private val activeAuditReport: MutableStateFlow<CrossStatementAuditReport?>,
    private val pendingStatementImport: StateFlow<StatementParseSummary?>,
    private val setPendingStatementImport: (StatementParseSummary?, Boolean) -> Unit,
    private val pendingEpoch: AtomicLong,
    private val importMutex: Mutex,
    private val emitSnackbar: suspend (String) -> Unit,
    private val importLedgerCsvFallback: suspend (ByteArray) -> Boolean
) {
    private var lastCommittedPendingEpoch: Long = -1L
    private var loadAuditJob: Job? = null
    private val pendingMerchantRules = linkedMapOf<String, BankTransactionType>()

    private fun householdNamesPositional(settings: SettingsEntity): List<String> = listOf(
        settings.primaryName.trim(),
        if (!settings.isSingleHousehold) settings.spouseName.trim() else ""
    )

    private fun householdNamesForReconcile(settings: SettingsEntity): List<String> = listOfNotNull(
        settings.primaryName.trim().takeIf { it.isNotBlank() },
        settings.spouseName.trim().takeIf { !settings.isSingleHousehold && it.isNotBlank() }
    )

    private fun mergedOverrides(settings: SettingsEntity): Map<String, BankTransactionType> {
        return MerchantCategoryManager.getOverrides(application, preferencesRepository.storeName) +
            MerchantCategoryManager.parseRulesFromJson(settings.merchantRulesJson)
    }

    suspend fun cancelPendingJobs() {
        loadAuditJob?.cancel()
        loadAuditJob?.join()
    }

    suspend fun deleteImportedStatement(yearMonth: String, bankName: String? = null) {
        try {
            importMutex.withLock {
                val hasBank = !bankName.isNullOrBlank()
                val existingForMonth = repository.getImportedTransactionsDirect(yearMonth)
                val deletedIds = if (hasBank) {
                    existingForMonth.filter { it.bankName == bankName }.map { it.id }.filter { it > 0L }.toSet()
                } else {
                    existingForMonth.map { it.id }.filter { it > 0L }.toSet()
                }

                if (hasBank) {
                    val bank = bankName!!
                    repository.deleteImportedTransactionsForBankAndMonth(yearMonth, bank)
                    preferencesRepository.removeBankBalanceForMonthBank(yearMonth, bank)
                } else {
                    repository.deleteImportedTransactionsForMonth(yearMonth)
                    preferencesRepository.clearBankBalancesForMonth(yearMonth)
                }

                val curSettings = repository.settingsFlow.first()
                val overrides = mergedOverrides(curSettings)
                val posNames = householdNamesPositional(curSettings)
                val reconNames = householdNamesForReconcile(curSettings)
                val knownFamilyAccounts = parseAccountListJson(curSettings.knownFamilyAccountsJson).toSet()
                val knownParentalGiftAccounts = parseAccountListJson(curSettings.knownParentalGiftAccountsJson).toSet()

                // Clear/reconcile stale cross-month boundary links pointing into the deleted statement
                clearStaleBoundaryLinks(
                    yearMonth = yearMonth,
                    deletedIds = deletedIds,
                    overrides = overrides,
                    posNames = posNames,
                    reconNames = reconNames,
                    knownFamilyAccounts = knownFamilyAccounts,
                    knownParentalGiftAccounts = knownParentalGiftAccounts
                )

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
                    val resetTxs = remainingTxs.map { tx ->
                        if (tx.nettingReason.startsWith("Manually ")) {
                            tx
                        } else {
                            val restoredCat = if (tx.category == BankTransactionType.INTERNAL_TRANSFER.name) {
                                BankStatementImporter.categorizeTransaction(
                                    amount = tx.amount,
                                    counterpartyAcc = tx.counterpartyAccount,
                                    counterpartyName = tx.counterpartyName,
                                    message = tx.message,
                                    vs = tx.variableSymbol,
                                    familyAccounts = knownFamilyAccounts,
                                    userOverrides = overrides,
                                    householdNames = posNames,
                                    knownParentalGiftAccounts = knownParentalGiftAccounts
                                ).name
                            } else tx.category
                            tx.copy(category = restoredCat, isNetted = false, nettingReason = "", matchedTxId = null)
                        }
                    }
                    val (reconciledTxs, auditReport) = CrossStatementReconciliationEngine.reconcileTransactions(
                        transactions = resetTxs,
                        yearMonth = yearMonth,
                        knownFamilyAccounts = knownFamilyAccounts,
                        boundaryTransactions = boundaryTxs,
                        bankBalances = storedBalances,
                        householdNames = reconNames,
                        knownParentalGiftAccounts = knownParentalGiftAccounts
                    )
                    repository.updateImportedTransactions(reconciledTxs)
                    activeAuditReport.value = auditReport
                    if (existingEntry != null) importCoordinator.recalculateMonthLedger(yearMonth)
                }

                val displayBank = if (hasBank) friendlyBankName(bankName!!) else ""
                val msg = if (hasBank) "Deleted $displayBank statement for $yearMonth" else "Deleted imported statement data for $yearMonth"
                emitSnackbar(msg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to delete imported statement")
        }
    }

    private suspend fun clearStaleBoundaryLinks(
        yearMonth: String,
        deletedIds: Set<Long>,
        overrides: Map<String, BankTransactionType>,
        posNames: List<String>,
        reconNames: List<String>,
        knownFamilyAccounts: Set<String>,
        knownParentalGiftAccounts: Set<String>
    ) {
        val adjacent = importCoordinator.fetchAllAdjacentTransactions(yearMonth)
        if (adjacent.isEmpty()) return

        val brokenAdjacent = adjacent.filter { tx ->
            !tx.nettingReason.startsWith("Manually ") && (
                (tx.matchedTxId != null && tx.matchedTxId in deletedIds) ||
                    (tx.isNetted && tx.nettingReason.startsWith("Cross-month"))
                )
        }
        if (brokenAdjacent.isEmpty()) return

        val affectedMonths = brokenAdjacent.map { it.yearMonth }.distinct()
        for (adjYm in affectedMonths) {
            val adjMonthTxs = repository.getImportedTransactionsDirect(adjYm)
            if (adjMonthTxs.isEmpty()) continue
            val resetAdj = adjMonthTxs.map { tx ->
                if (tx.nettingReason.startsWith("Manually ")) {
                    tx
                } else if ((tx.matchedTxId != null && tx.matchedTxId in deletedIds) ||
                    (tx.isNetted && tx.nettingReason.startsWith("Cross-month"))
                ) {
                    val restoredCat = if (tx.category == BankTransactionType.INTERNAL_TRANSFER.name) {
                        BankStatementImporter.categorizeTransaction(
                            amount = tx.amount,
                            counterpartyAcc = tx.counterpartyAccount,
                            counterpartyName = tx.counterpartyName,
                            message = tx.message,
                            vs = tx.variableSymbol,
                            familyAccounts = knownFamilyAccounts,
                            userOverrides = overrides,
                            householdNames = posNames,
                            knownParentalGiftAccounts = knownParentalGiftAccounts
                        ).name
                    } else tx.category
                    tx.copy(category = restoredCat, isNetted = false, nettingReason = "", matchedTxId = null)
                } else {
                    tx
                }
            }
            val adjBoundary = importCoordinator.fetchBoundaryTransactions(adjYm)
            val adjBalances = importCoordinator.getStoredBankBalancesForMonth(adjYm)
            val reconciledAdj = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
                transactions = resetAdj,
                yearMonth = adjYm,
                knownFamilyAccounts = knownFamilyAccounts,
                boundaryTransactions = adjBoundary,
                bankBalances = adjBalances,
                householdNames = reconNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts
            )
            repository.updateImportedTransactions(reconciledAdj.currentMonthTxs)
            if (reconciledAdj.updatedBoundaryTxs.isNotEmpty()) {
                repository.updateImportedTransactions(reconciledAdj.updatedBoundaryTxs)
            }
            importCoordinator.recalculateMonthLedger(adjYm)
        }
    }

    suspend fun importStatements(uris: List<Uri>) {
        if (uris.isEmpty()) return
        try {
            val readResult = readStatementUrisSafely(application, uris)
            val byteList = readResult.byteList

            if (byteList.isEmpty()) {
                val msg = if (readResult.hasOversized && !readResult.hasReadError) {
                    "Statement file is too large (max 25 MB)"
                } else {
                    "Error: Unable to open statement file"
                }
                emitSnackbar(msg)
                return
            }

            importMutex.withLock {
                val curSettings = repository.settingsFlow.first()
                val overrides = mergedOverrides(curSettings)
                val householdNames = householdNamesPositional(curSettings)
                val knownFamilyAccounts = parseAccountListJson(curSettings.knownFamilyAccountsJson).toSet()
                val knownParentalGiftAccounts = parseAccountListJson(curSettings.knownParentalGiftAccountsJson).toSet()
                val parsedSummaries = mutableListOf<StatementParseSummary>()
                var failedCount = 0
                var encryptedPdfCount = 0

                for (bytes in byteList) {
                    try {
                        val isEncrypted = com.example.util.PdfTextExtractor.isEncryptedPdf(bytes)
                        val bankSummary = BankStatementImporter.parseStatement(
                            inputStream = bytes.inputStream(),
                            knownFamilyAccounts = knownFamilyAccounts,
                            userOverrides = overrides,
                            householdNames = householdNames,
                            knownParentalGiftAccounts = knownParentalGiftAccounts
                        )
                        if (bankSummary.transactions.isNotEmpty() && bankSummary.yearMonth.matches(Regex("""\d{4}-\d{2}"""))) {
                            parsedSummaries.add(bankSummary)
                        } else {
                            if (isEncrypted) encryptedPdfCount++
                            failedCount++
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failedCount++
                    }
                }

                if (parsedSummaries.isNotEmpty()) {
                    pendingMerchantRules.clear()
                    val consolidated = BankStatementImporter.mergeSummaries(parsedSummaries)
                    setPendingStatementImport(consolidated, true)
                    val nettingMsg = if (consolidated.internalTransfersCount > 0) " (${consolidated.internalTransfersCount} netted)" else ""
                    val failedNote = if (failedCount > 0) " · $failedCount file(s) skipped" else ""
                    val bankDisplay = friendlyBankName(consolidated.detectedBank.name)
                    emitSnackbar(
                        "Parsed ${consolidated.transactions.size} transactions for ${consolidated.yearMonth} ($bankDisplay)$nettingMsg$failedNote"
                    )
                    return
                }

                if (byteList.size == 1 && importLedgerCsvFallback(byteList[0])) {
                    return
                }

                if (encryptedPdfCount > 0) {
                    emitSnackbar("This PDF is password-protected or encrypted. Export as CSV, ABO/GPC, or an unencrypted PDF from your bank.")
                } else {
                    emitSnackbar("No transactions found. Supported: Moneta, ČSOB, mBank PDF/CSV, or ledger CSV.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Statement import failed. Please verify the file format.")
        }
    }

    fun loadAuditReportForMonth(yearMonth: String) {
        loadAuditJob?.cancel()
        loadAuditJob = scope.launch(Dispatchers.IO) { reloadAuditReportForMonthInternal(yearMonth) }
    }

    fun clearAuditReport() {
        loadAuditJob?.cancel()
        activeAuditReport.value = null
    }

    private suspend fun reloadAuditReportForMonthInternal(yearMonth: String) {
        val transactions = repository.getImportedTransactionsDirect(yearMonth)
        val txsToUse = if (transactions.isNotEmpty()) {
            transactions
        } else {
            val pending = pendingStatementImport.value
            if (pending != null && pending.yearMonth == yearMonth) buildSyntheticAuditTransactions(pending) else emptyList()
        }

        if (txsToUse.isNotEmpty()) {
            val boundaryTxs = importCoordinator.fetchBoundaryTransactions(yearMonth)
            val storedBalances = importCoordinator.getStoredBankBalancesForMonth(yearMonth)
            val curSettings = repository.settingsFlow.first()
            val householdNames = householdNamesForReconcile(curSettings)
            val knownFamilyAccounts = parseAccountListJson(curSettings.knownFamilyAccountsJson).toSet()
            val knownParentalGiftAccounts = parseAccountListJson(curSettings.knownParentalGiftAccountsJson).toSet()
            val (_, report) = CrossStatementReconciliationEngine.reconcileTransactions(
                transactions = txsToUse,
                yearMonth = yearMonth,
                knownFamilyAccounts = knownFamilyAccounts,
                boundaryTransactions = boundaryTxs,
                bankBalances = storedBalances,
                householdNames = householdNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts
            )
            activeAuditReport.value = report
        } else {
            activeAuditReport.value = null
        }
    }

    suspend fun confirmStatementImport(summary: StatementParseSummary) {
        val capturedEpoch = pendingEpoch.get()
        try {
            importMutex.withLock {
                if (pendingEpoch.get() != capturedEpoch || lastCommittedPendingEpoch == capturedEpoch) return@withLock
                if (pendingMerchantRules.isNotEmpty()) {
                    val rulesSnapshot = pendingMerchantRules.toMap()
                    rulesSnapshot.forEach { (query, cat) ->
                        MerchantCategoryManager.saveOverride(application, query, cat, preferencesRepository.storeName)
                    }
                    val nowYm = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
                    val updatedSettings = repository.updateSettingsTransformAndSyncLedger(nowYm) { cur ->
                        val currentRules = MerchantCategoryManager.parseRulesFromJson(cur.merchantRulesJson).toMutableMap()
                        rulesSnapshot.forEach { (query, cat) ->
                            val key = MerchantCategoryManager.canonicalRuleKey(query)
                            if (key.isNotBlank()) currentRules[key] = cat
                        }
                        cur.copy(merchantRulesJson = MerchantCategoryManager.serializeRulesToJson(currentRules))
                    }
                    if (updatedSettings != null && pendingSettingsWrites.get() == 0) {
                        liveSettings.value = updatedSettings
                    }
                    pendingMerchantRules.clear()
                }

                val result = importCoordinator.confirmStatementImport(summary)
                lastCommittedPendingEpoch = capturedEpoch
                setPendingStatementImport(null, true)
                if (result != null) {
                    if (result.auditReport != null) activeAuditReport.value = result.auditReport
                    val monthsLabel = result.affectedMonths.joinToString(", ")
                    val bankDisplay = friendlyBankName(summary.detectedBank.name)
                    emitSnackbar("Imported statement into $monthsLabel (${result.importedCount} transactions from $bankDisplay)")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to save imported transactions")
        }
    }

    fun dismissStatementImport() {
        pendingMerchantRules.clear()
        setPendingStatementImport(null, true)
    }

    suspend fun togglePendingTransactionExcluded(txIndex: Int) {
        val requestEpoch = pendingEpoch.get()
        importMutex.withLock {
            if (pendingEpoch.get() != requestEpoch) return@withLock
            val current = pendingStatementImport.value ?: return@withLock
            val updated = importCoordinator.toggleTransactionExcluded(current, txIndex)
            if (pendingEpoch.get() == requestEpoch) setPendingStatementImport(updated, false)
        }
    }

    suspend fun bulkExcludeMerchant(merchantQuery: String) {
        val requestEpoch = pendingEpoch.get()
        importMutex.withLock {
            if (pendingEpoch.get() != requestEpoch) return@withLock
            val current = pendingStatementImport.value ?: return@withLock
            val updated = importCoordinator.bulkExcludeMerchant(current, merchantQuery)
            if (pendingEpoch.get() == requestEpoch) setPendingStatementImport(updated, false)
        }
    }

    suspend fun updatePendingTransactionCategory(
        txIndex: Int,
        newCategory: BankTransactionType,
        rememberForMerchant: Boolean = false,
        applyToAllMatching: Boolean = false
    ) {
        val requestEpoch = pendingEpoch.get()
        importMutex.withLock {
            if (pendingEpoch.get() != requestEpoch) return@withLock
            val current = pendingStatementImport.value ?: return@withLock
            if (rememberForMerchant && txIndex in current.transactions.indices) {
                val targetTx = current.transactions[txIndex]
                val merchantName = targetTx.counterpartyName.ifBlank { targetTx.message }
                val query = CzechMerchantCatalog.suggestMerchantSearchQuery(merchantName)
                if (query.isNotBlank()) {
                    pendingMerchantRules[query] = newCategory
                }
            }
            val updated = importCoordinator.updateTransactionCategory(
                summary = current,
                txIndex = txIndex,
                newCategory = newCategory,
                rememberForMerchant = rememberForMerchant,
                applyToAllMatching = applyToAllMatching,
                persistMerchantOverride = false
            )
            if (pendingEpoch.get() == requestEpoch) setPendingStatementImport(updated, false)
        }
    }

    suspend fun updateImportedTransactionCategory(
        txId: Long,
        newCategory: BankTransactionType,
        rememberForMerchant: Boolean = false,
        applyToAllMatchingInMonth: Boolean = false
    ) {
        try {
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
                        MerchantCategoryManager.saveOverride(application, query, newCategory, preferencesRepository.storeName)
                        val normalizedRuleKey = CzechMerchantCatalog.normalizeSimple(query).ifBlank { query.trim().lowercase() }
                        val nowYm = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
                        val updatedSettings = repository.updateSettingsTransformAndSyncLedger(nowYm) { cur ->
                            val currentRules = MerchantCategoryManager.parseRulesFromJson(cur.merchantRulesJson).toMutableMap()
                            currentRules[normalizedRuleKey] = newCategory
                            cur.copy(merchantRulesJson = MerchantCategoryManager.serializeRulesToJson(currentRules))
                        }
                        if (updatedSettings != null && pendingSettingsWrites.get() == 0) {
                            liveSettings.value = updatedSettings
                        }
                    }
                }

                importCoordinator.recalculateMonthLedger(target.yearMonth)
                if (activeAuditReport.value?.yearMonth == target.yearMonth) reloadAuditReportForMonthInternal(target.yearMonth)
                val catLabel = formatCategoryLabel(newCategory)
                val txWord = if (txsToUpdate.size == 1) "transaction" else "transactions"
                emitSnackbar("Updated category to $catLabel (${txsToUpdate.size} $txWord updated)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to update transaction category")
        }
    }

    suspend fun toggleImportedTransactionExcluded(txId: Long) {
        try {
            importMutex.withLock {
                val target = repository.getImportedTransactionById(txId) ?: return@withLock
                val willBeExcluded = !target.isNetted
                val restoredCategory = if (!willBeExcluded && target.category == BankTransactionType.INTERNAL_TRANSFER.name) {
                    val currentSettings = repository.settingsFlow.first()
                    val overrides = mergedOverrides(currentSettings)
                    val knownFamilyAccounts = parseAccountListJson(currentSettings.knownFamilyAccountsJson).toSet()
                    val knownParentalGiftAccounts = parseAccountListJson(currentSettings.knownParentalGiftAccountsJson).toSet()
                    val posNames = householdNamesPositional(currentSettings)
                    BankStatementImporter.categorizeTransaction(
                        amount = target.amount,
                        counterpartyAcc = target.counterpartyAccount,
                        counterpartyName = target.counterpartyName,
                        message = target.message,
                        vs = target.variableSymbol,
                        familyAccounts = knownFamilyAccounts,
                        userOverrides = overrides,
                        householdNames = posNames,
                        knownParentalGiftAccounts = knownParentalGiftAccounts
                    ).name
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
                if (activeAuditReport.value?.yearMonth == target.yearMonth) reloadAuditReportForMonthInternal(target.yearMonth)
                val actionLabel = if (willBeExcluded) "Excluded from budget" else "Restored to budget"
                emitSnackbar("$actionLabel: ${target.counterpartyName.ifBlank { target.message }}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to update transaction status")
        }
    }
}
