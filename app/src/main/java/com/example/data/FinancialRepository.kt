package com.example.data

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FinancialRepository(
    private val database: AppDatabase,
    private val settingsDao: SettingsDao,
    private val ledgerDao: LedgerDao,
    private val actionStateDao: ActionStateDao,
    private val importedTransactionDao: ImportedTransactionDao
) {
    private val _storageErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val storageErrors: Flow<String> = _storageErrors.asSharedFlow()

    val settingsFlow: Flow<SettingsEntity> = settingsDao.getSettings()
        .map { it ?: SettingsEntity.freshDefaults() }
        .catch { e ->
            if (e is CancellationException) throw e
            reportStorageFailure("settings_stream", e)
            emit(SettingsEntity.freshDefaults())
        }

    val ledgerFlow: Flow<List<LedgerEntryEntity>> = ledgerDao.getAllEntries()
        .catch { e ->
            if (e is CancellationException) throw e
            reportStorageFailure("ledger_stream", e)
            emit(emptyList())
        }

    val actionStatesFlow: Flow<Map<String, Boolean>> = actionStateDao.getAllActionStates()
        .map { list -> list.associate { it.actionKey to it.isDone } }
        .catch { e ->
            if (e is CancellationException) throw e
            reportStorageFailure("actions_stream", e)
            emit(emptyMap())
        }

    suspend fun saveSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        val clean = com.example.util.BackupManager.sanitizeSettings(settings, SettingsEntity.freshDefaults())
        safeDbWrite("save_settings", Unit) {
            database.withTransaction {
                settingsDao.saveSettings(clean)
            }
        }
    }

    suspend fun updateSettingsTransformAndSyncLedger(
        currentYearMonth: String,
        transform: (SettingsEntity) -> SettingsEntity
    ): SettingsEntity? = withContext(Dispatchers.IO) {
        safeDbWrite<SettingsEntity?>("update_settings_transform", null) {
            database.withTransaction {
                val current = settingsDao.getSettingsDirect() ?: SettingsEntity.freshDefaults()
                val cleanSettings = com.example.util.BackupManager.sanitizeSettings(
                    transform(current),
                    SettingsEntity.freshDefaults()
                )
                settingsDao.saveSettings(cleanSettings)
                syncCurrentManualLedgerFromSettingsLocked(cleanSettings, currentYearMonth)
                cleanSettings
            }
        }
    }

    suspend fun updateSettingsAndSyncLedger(
        newSettings: SettingsEntity,
        currentYearMonth: String
    ) = withContext(Dispatchers.IO) {
        val cleanSettings = com.example.util.BackupManager.sanitizeSettings(newSettings, SettingsEntity.freshDefaults())
        safeDbWrite("update_settings", Unit) {
            database.withTransaction {
                settingsDao.saveSettings(cleanSettings)
                syncCurrentManualLedgerFromSettingsLocked(cleanSettings, currentYearMonth)
            }
        }
    }

    private suspend fun syncCurrentManualLedgerFromSettingsLocked(
        cleanSettings: SettingsEntity,
        currentYearMonth: String
    ) {
        val currentEntry = ledgerDao.getEntryByYearMonth(currentYearMonth) ?: return
        if (currentEntry.notes.startsWith("Imported from", ignoreCase = true) ||
            currentEntry.notes.startsWith("Direct Bank Statement", ignoreCase = true) ||
            importedTransactionDao.getTransactionsForMonthDirect(currentYearMonth).isNotEmpty()
        ) {
            return
        }
        val currentYm = runCatching { java.time.YearMonth.parse(currentYearMonth) }.getOrNull()
        val returnYm = java.time.YearMonth.of(
            cleanSettings.eReturnYear,
            cleanSettings.eReturnMonth.coerceIn(1, 12)
        )
        val isBeforeReturn = currentYm?.isBefore(returnYm)
            ?: (cleanSettings.baseYear < cleanSettings.eReturnYear)
        val updatedEleonora = if (cleanSettings.isSingleHousehold) {
            0.0
        } else if (isBeforeReturn) {
            // Lecturing is added separately by FinancialEngine.householdIncome; store allowance only to avoid double counting.
            cleanSettings.eParentalAllowanceMonthly
        } else {
            cleanSettings.eStartingSalary
        }
        val updatedLiquid = cleanSettings.liquidPortfolioCurrent +
            if (!cleanSettings.isSingleHousehold) cleanSettings.eLiquidPortfolioCurrent else 0.0
        val updatedPension = cleanSettings.dpsBalanceCurrent + cleanSettings.dipBalanceCurrent +
            if (!cleanSettings.isSingleHousehold) (cleanSettings.eDpsBalanceCurrent + cleanSettings.eDipBalanceCurrent) else 0.0
        ledgerDao.updateEntry(
            currentEntry.copy(
                incVaclav = cleanSettings.vSalary,
                incEleonora = updatedEleonora,
                expRent = cleanSettings.rentMonthly,
                portfolioBalanceAtMonthEnd = updatedLiquid,
                pensionBalanceAtMonthEnd = updatedPension,
                emergencyReserveAtMonthEnd = cleanSettings.emergencyReserveCurrent
            )
        )
    }

    suspend fun syncManualLedgerToSettingsTransaction(
        yearMonth: String,
        currentYearMonth: String,
        incVaclav: Double?,
        incEleonora: Double?,
        expRent: Double?,
        portfolioBalance: Double?,
        pensionBalance: Double?,
        emergencyReserve: Double?
    ) = withContext(Dispatchers.IO) {
        if (yearMonth == currentYearMonth) {
            safeDbWrite("sync_manual_ledger_to_settings", Unit) {
                database.withTransaction {
                    val cur = settingsDao.getSettingsDirect() ?: SettingsEntity.freshDefaults()
                    var updated = cur
                    if (incVaclav != null && incVaclav.isFinite() && incVaclav >= 0.0 && cur.vSalary != incVaclav) {
                        updated = updated.copy(vSalary = incVaclav)
                    }
                    if (!cur.isSingleHousehold && incEleonora != null && incEleonora.isFinite() && incEleonora >= 0.0) {
                        val entryYm = runCatching { java.time.YearMonth.parse(yearMonth) }.getOrNull()
                        val returnYm = java.time.YearMonth.of(
                            cur.eReturnYear,
                            cur.eReturnMonth.coerceIn(1, 12)
                        )
                        val isBeforeReturn = entryYm?.isBefore(returnYm) ?: (cur.baseYear < cur.eReturnYear)
                        if (isBeforeReturn) {
                            if (cur.eParentalAllowanceMonthly != incEleonora) {
                                updated = updated.copy(eParentalAllowanceMonthly = incEleonora)
                            }
                        } else {
                            if (cur.eStartingSalary != incEleonora) {
                                updated = updated.copy(eStartingSalary = incEleonora)
                            }
                        }
                    }
                    if (expRent != null && expRent.isFinite() && expRent >= 0.0 && cur.rentMonthly != expRent) {
                        updated = updated.copy(rentMonthly = expRent)
                    }
                    // Ledger balances are HOUSEHOLD totals (see updateSettingsAndSyncLedger, which writes
                    // primary + spouse). Primary-only settings fields must receive total minus spouse share.
                    val includeSpouse = !cur.isSingleHousehold
                    val spouseLiquid = if (includeSpouse) cur.eLiquidPortfolioCurrent else 0.0
                    val spousePension = if (includeSpouse) cur.eDpsBalanceCurrent + cur.eDipBalanceCurrent else 0.0
                    if (portfolioBalance != null && portfolioBalance.isFinite() && portfolioBalance >= 0.0) {
                        val primaryLiquid = (portfolioBalance - spouseLiquid).coerceAtLeast(0.0)
                        if (cur.liquidPortfolioCurrent != primaryLiquid) {
                            updated = updated.copy(liquidPortfolioCurrent = primaryLiquid)
                        }
                    }
                    if (pensionBalance != null && pensionBalance.isFinite() && pensionBalance >= 0.0) {
                        val primaryPension = (pensionBalance - spousePension).coerceAtLeast(0.0)
                        val curPrimary = cur.dpsBalanceCurrent + cur.dipBalanceCurrent
                        if (curPrimary != primaryPension) {
                            // Preserve existing DPS/DIP split; if balances are 0, weight by contributions (or 50/50) instead of dumping 100% into DPS.
                            val contribSum = cur.dpsOwnContributionMonthly.coerceAtLeast(0.0) +
                                cur.dipContributionMonthly.coerceAtLeast(0.0)
                            val dpsShare = when {
                                curPrimary > 0.0 -> cur.dpsBalanceCurrent / curPrimary
                                contribSum > 0.0 -> cur.dpsOwnContributionMonthly.coerceAtLeast(0.0) / contribSum
                                else -> 0.5
                            }
                            val newDps = primaryPension * dpsShare
                            updated = updated.copy(
                                dpsBalanceCurrent = newDps,
                                dipBalanceCurrent = primaryPension - newDps
                            )
                        }
                    }
                    if (emergencyReserve != null && emergencyReserve.isFinite() && emergencyReserve >= 0.0 && cur.emergencyReserveCurrent != emergencyReserve) {
                        updated = updated.copy(emergencyReserveCurrent = emergencyReserve)
                    }
                    val clean = com.example.util.BackupManager.sanitizeSettings(updated, cur)
                    if (clean != cur) {
                        settingsDao.saveSettings(clean)
                    }
                }
            }
        }
    }

    suspend fun syncLedgerToSettingsTransaction(
        yearMonth: String,
        currentYearMonth: String,
        incVaclav: Double,
        incEleonora: Double,
        expRent: Double,
        portfolioBalance: Double,
        pensionBalance: Double,
        emergencyReserve: Double
    ) {
        syncManualLedgerToSettingsTransaction(
            yearMonth = yearMonth,
            currentYearMonth = currentYearMonth,
            incVaclav = incVaclav.takeIf { it.isFinite() && it > 0.0 },
            incEleonora = incEleonora.takeIf { it.isFinite() && it > 0.0 },
            expRent = expRent.takeIf { it.isFinite() && it > 0.0 },
            portfolioBalance = portfolioBalance.takeIf { it.isFinite() && it > 0.0 },
            pensionBalance = pensionBalance.takeIf { it.isFinite() && it > 0.0 },
            emergencyReserve = emergencyReserve.takeIf { it.isFinite() && it > 0.0 }
        )
    }

    private fun applyCalendarYearRollover(
        current: SettingsEntity,
        targetCalendarYear: Int
    ): SettingsEntity {
        if (current.baseYear !in 2026 until targetCalendarYear) return current
        val delta = targetCalendarYear - current.baseYear
        return current.copy(
            baseYear = targetCalendarYear,
            primaryAge = (current.primaryAge + delta).coerceIn(15, 100)
        )
    }

    /**
     * Advances `baseYear` and `primaryAge` when the real Prague calendar year rolls over.
     * Safe to invoke on every startup without modifying user-entered employer contributions.
     */
    suspend fun rolloverCalendarYearIfNeeded(
        targetCalendarYear: Int = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague")).year
    ) = withContext(Dispatchers.IO) {
        safeDbWrite("rollover_calendar_year", Unit) {
            database.withTransaction {
                val current = settingsDao.getSettingsDirect() ?: return@withTransaction
                val updated = applyCalendarYearRollover(current, targetCalendarYear)
                val clean = com.example.util.BackupManager.sanitizeSettings(updated, current)
                if (clean != current) {
                    settingsDao.saveSettings(clean)
                }
            }
        }
    }

    /**
     * Legacy migration helper for older DB rows storing 2800 (annual) in monthly employer field.
     */
    suspend fun repairLegacyEmployerContribution(
        targetCalendarYear: Int = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague")).year
    ) = withContext(Dispatchers.IO) {
        safeDbWrite("repair_legacy_employer", Unit) {
            database.withTransaction {
                val current = settingsDao.getSettingsDirect() ?: return@withTransaction
                var updated = current
                if (updated.employerRetirementMonthly >= 2795.0 && updated.employerRetirementMonthly <= 2805.0) {
                    updated = updated.copy(employerRetirementMonthly = 233.0)
                }
                updated = applyCalendarYearRollover(updated, targetCalendarYear)
                val clean = com.example.util.BackupManager.sanitizeSettings(updated, current)
                if (clean != current) {
                    settingsDao.saveSettings(clean)
                }
            }
        }
    }

    suspend fun addLedgerEntry(entry: LedgerEntryEntity) = withContext(Dispatchers.IO) {
        safeDbWrite("add_ledger_entry", Unit) { ledgerDao.insertEntry(sanitizeLedgerEntry(entry)) }
    }

    suspend fun updateLedgerEntry(entry: LedgerEntryEntity) = withContext(Dispatchers.IO) {
        safeDbWrite("update_ledger_entry", Unit) { ledgerDao.updateEntry(sanitizeLedgerEntry(entry)) }
    }

    suspend fun updateLedgerEntries(entries: List<LedgerEntryEntity>) = withContext(Dispatchers.IO) {
        safeDbWrite("update_ledger_entries", Unit) { ledgerDao.updateEntries(entries.map(::sanitizeLedgerEntry)) }
    }

    suspend fun addLedgerEntries(entries: List<LedgerEntryEntity>) = withContext(Dispatchers.IO) {
        safeDbWrite("add_ledger_entries", Unit) { ledgerDao.insertEntries(entries.map(::sanitizeLedgerEntry)) }
    }

    suspend fun existingYearMonths(): Set<String> = withContext(Dispatchers.IO) {
        safeDbRead("existing_year_months", emptySet()) { ledgerDao.getAllYearMonths().toSet() }
    }

    suspend fun getLedgerEntryByYearMonth(yearMonth: String): LedgerEntryEntity? = withContext(Dispatchers.IO) {
        safeDbRead("get_ledger_entry", null) { ledgerDao.getEntryByYearMonth(yearMonth) }
    }

    suspend fun getAllLedgerEntriesDirect(): List<LedgerEntryEntity> = withContext(Dispatchers.IO) {
        safeDbRead("get_all_ledger_entries", emptyList()) { ledgerDao.getAllEntriesDirect() }
    }

    suspend fun deleteLedgerEntry(id: Long): String? = withContext(Dispatchers.IO) {
        safeDbWrite<String?>("delete_ledger_entry", null) {
            database.withTransaction {
                val entry = ledgerDao.getEntryById(id)
                ledgerDao.deleteEntry(id)
                if (entry != null) {
                    importedTransactionDao.deleteTransactionsForMonth(entry.yearMonth)
                    importedTransactionDao.clearStaleMatchedTransactionLinks()
                }
                entry?.yearMonth
            }
        }
    }

    suspend fun deleteImportedTransactionsForMonth(yearMonth: String) = withContext(Dispatchers.IO) {
        safeDbWrite("delete_imported_month", Unit) {
            database.withTransaction {
                importedTransactionDao.deleteTransactionsForMonth(yearMonth)
                importedTransactionDao.clearStaleMatchedTransactionLinks()
            }
        }
    }

    suspend fun setActionState(year: Int, actionId: String, isDone: Boolean) = withContext(Dispatchers.IO) {
        val key = "${year}_$actionId"
        safeDbWrite("set_action_state", Unit) {
            actionStateDao.saveActionState(ActionStateEntity(key, year, actionId, isDone))
        }
    }

    suspend fun toggleActionState(year: Int, actionId: String) = withContext(Dispatchers.IO) {
        safeDbWrite("toggle_action_state", Unit) {
            actionStateDao.toggleActionStateUpsert("${year}_$actionId", year, actionId)
        }
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        safeDbWrite("clear_all_data", Unit) {
            database.withTransaction {
                ledgerDao.deleteAllEntries()
                actionStateDao.deleteAllActionStates()
                importedTransactionDao.deleteAllTransactions()
            }
        }
    }

    suspend fun getImportedTransactionsDirect(yearMonth: String): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        safeDbRead("get_imported_txs", emptyList()) { importedTransactionDao.getTransactionsForMonthDirect(yearMonth) }
    }

    suspend fun saveImportedTransactions(transactions: List<ImportedBankTransactionEntity>) = withContext(Dispatchers.IO) {
        safeDbWrite("save_imported_txs", Unit) {
            importedTransactionDao.insertTransactions(transactions.map(::sanitizeTransaction))
        }
    }

    suspend fun updateImportedTransactions(transactions: List<ImportedBankTransactionEntity>) = withContext(Dispatchers.IO) {
        safeDbWrite("update_imported_txs", Unit) {
            importedTransactionDao.updateTransactions(transactions.map(::sanitizeTransaction))
        }
    }

    suspend fun updateImportedTransaction(transaction: ImportedBankTransactionEntity) = withContext(Dispatchers.IO) {
        safeDbWrite("update_imported_tx", Unit) {
            importedTransactionDao.updateTransaction(sanitizeTransaction(transaction))
        }
    }

    suspend fun getImportedTransactionById(id: Long): ImportedBankTransactionEntity? = withContext(Dispatchers.IO) {
        safeDbRead("get_imported_tx_by_id", null) { importedTransactionDao.getTransactionById(id) }
    }

    suspend fun deleteImportedTransactionsForBankAndMonth(yearMonth: String, bankName: String) = withContext(Dispatchers.IO) {
        safeDbWrite("delete_imported_bank_month", Unit) {
            database.withTransaction {
                importedTransactionDao.deleteTransactionsForBankAndMonth(yearMonth, bankName)
                importedTransactionDao.clearStaleMatchedTransactionLinks()
            }
        }
    }

    suspend fun deleteImportedTransactionsForBankAccountAndMonth(
        yearMonth: String,
        bankName: String,
        accountKey: String
    ) = withContext(Dispatchers.IO) {
        safeDbWrite("delete_imported_bank_account_month", Unit) {
            database.withTransaction {
                importedTransactionDao.deleteTransactionsForBankAccountAndMonth(yearMonth, bankName, accountKey)
                importedTransactionDao.clearStaleMatchedTransactionLinks()
            }
        }
    }

    fun getAllImportedTransactions(): Flow<List<ImportedBankTransactionEntity>> {
        return importedTransactionDao.getAllImportedTransactions()
            .catch { e ->
                if (e is CancellationException) throw e
                reportStorageFailure("imported_txs_stream", e)
                emit(emptyList())
            }
    }

    suspend fun getTransactionsForBankAndMonthDirect(yearMonth: String, bankName: String): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        safeDbRead("get_txs_bank_month", emptyList()) {
            importedTransactionDao.getTransactionsForBankAndMonthDirect(yearMonth, bankName)
        }
    }

    suspend fun getTransactionsInDateRangeDirect(startDate: String, endDate: String): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        safeDbRead("get_txs_date_range", emptyList()) {
            importedTransactionDao.getTransactionsInDateRangeDirect(startDate, endDate)
        }
    }

    suspend fun saveImportedTransactionsSmartMerge(
        yearMonth: String,
        bankName: String,
        newTransactions: List<ImportedBankTransactionEntity>,
        accountKey: String = ""
    ): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        // Read-modify-write must be atomic: two concurrent imports of the same
        // bank/account/month would otherwise both observe the same existing set and double-insert.
        val cleanScopeKey = accountKey.trim().take(80)
        database.withTransaction {
            val existing = if (cleanScopeKey.isNotEmpty()) {
                importedTransactionDao.getTransactionsForBankAccountAndMonthDirect(yearMonth, bankName, cleanScopeKey)
            } else {
                importedTransactionDao.getTransactionsForBankAndMonthDirect(yearMonth, bankName)
            }
            val cleanIncoming = newTransactions.map(::sanitizeTransaction)
            if (existing.isEmpty()) {
                importedTransactionDao.insertTransactions(cleanIncoming)
                return@withTransaction cleanIncoming
            }

            val existingCounts = mutableMapOf<String, Int>()
            for (tx in existing) {
                val fp = com.example.util.CrossStatementReconciliationEngine.computeFingerprint(
                    tx.date, tx.bankName, tx.amount, tx.counterpartyAccount, tx.counterpartyName, tx.message, tx.variableSymbol, tx.accountKey
                )
                existingCounts[fp] = (existingCounts[fp] ?: 0) + 1
            }

            val incomingCounts = mutableMapOf<String, Int>()
            val toInsert = mutableListOf<ImportedBankTransactionEntity>()
            for (tx in cleanIncoming) {
                val fp = com.example.util.CrossStatementReconciliationEngine.computeFingerprint(
                    tx.date, tx.bankName, tx.amount, tx.counterpartyAccount, tx.counterpartyName, tx.message, tx.variableSymbol, tx.accountKey
                )
                val seen = incomingCounts[fp] ?: 0
                val existingCount = existingCounts[fp] ?: 0
                if (seen >= existingCount) {
                    toInsert.add(tx)
                }
                incomingCounts[fp] = seen + 1
            }

            if (toInsert.isNotEmpty()) {
                importedTransactionDao.insertTransactions(toInsert)
            }
            toInsert
        }
    }

    suspend fun <R> withTransaction(block: suspend () -> R): R = withContext(Dispatchers.IO) {
        database.withTransaction(block)
    }

    suspend fun getAllActionStatesDirect(): List<ActionStateEntity> = withContext(Dispatchers.IO) {
        safeDbRead("get_all_action_states", emptyList()) { actionStateDao.getAllActionStatesDirect() }
    }

    suspend fun getAllImportedTransactionsDirect(): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        safeDbRead("get_all_imported_txs", emptyList()) { importedTransactionDao.getAllTransactionsDirect() }
    }

    suspend fun restoreFullBackupData(
        settings: SettingsEntity,
        ledgerEntries: List<LedgerEntryEntity>,
        actionStates: List<ActionStateEntity>,
        transactions: List<ImportedBankTransactionEntity>
    ) = withContext(Dispatchers.IO) {
        val cleanSettings = com.example.util.BackupManager.sanitizeSettings(settings, SettingsEntity.freshDefaults())
        val cleanLedger = ledgerEntries
            .map(::sanitizeLedgerEntry)
            .filter { it.yearMonth.isNotBlank() }
            .associateBy { it.yearMonth }
            .values
            .toList()
        val cleanActions = actionStates
            .filter { it.actionKey.isNotBlank() }
            .associateBy { it.actionKey }
            .values
            .toList()
        val cleanTransactions = transactions
            .map(::sanitizeTransaction)
            .filter { it.yearMonth.isNotBlank() }

        try {
            database.withTransaction {
                ledgerDao.deleteAllEntries()
                actionStateDao.deleteAllActionStates()
                importedTransactionDao.deleteAllTransactions()
                settingsDao.saveSettings(cleanSettings)
                if (cleanLedger.isNotEmpty()) {
                    ledgerDao.insertEntries(cleanLedger)
                }
                if (cleanActions.isNotEmpty()) {
                    actionStateDao.saveActionStates(cleanActions)
                }
                if (cleanTransactions.isNotEmpty()) {
                    importedTransactionDao.insertTransactions(cleanTransactions)
                    importedTransactionDao.clearStaleMatchedTransactionLinks()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            reportStorageFailure("restore_full_backup", e)
            throw e
        } catch (e: IOException) {
            reportStorageFailure("restore_full_backup", e)
            throw e
        }
    }

    private suspend inline fun <T> safeDbRead(operation: String, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            reportStorageFailure(operation, e)
            fallback
        } catch (e: IOException) {
            reportStorageFailure(operation, e)
            fallback
        }
    }

    private suspend inline fun <T> safeDbWrite(operation: String, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            reportStorageFailure(operation, e)
            fallback
        } catch (e: IOException) {
            reportStorageFailure(operation, e)
            fallback
        }
    }

    private fun reportStorageFailure(operation: String, error: Throwable) {
        val label = error.javaClass.simpleName.ifBlank { "StorageError" }
        runCatching { android.util.Log.w("FinancialRepository", "Storage failure in $operation ($label)") }
        _storageErrors.tryEmit("Database storage error during $operation ($label). Check free device storage.")
    }

    private fun finiteOrZero(v: Double): Double = if (v.isFinite()) v else 0.0

    private fun nonNegativeFiniteOrZero(v: Double): Double = if (v.isFinite() && v > 0.0) v else 0.0

    private fun sanitizeLedgerEntry(entry: LedgerEntryEntity): LedgerEntryEntity = entry.copy(
        yearMonth = entry.yearMonth.trim(),
        incVaclav = nonNegativeFiniteOrZero(entry.incVaclav),
        incEleonora = nonNegativeFiniteOrZero(entry.incEleonora),
        incUnforeseen = nonNegativeFiniteOrZero(entry.incUnforeseen),
        expRent = nonNegativeFiniteOrZero(entry.expRent),
        expGroceries = nonNegativeFiniteOrZero(entry.expGroceries),
        expOther = nonNegativeFiniteOrZero(entry.expOther),
        portfolioBalanceAtMonthEnd = nonNegativeFiniteOrZero(entry.portfolioBalanceAtMonthEnd),
        pensionBalanceAtMonthEnd = nonNegativeFiniteOrZero(entry.pensionBalanceAtMonthEnd),
        emergencyReserveAtMonthEnd = nonNegativeFiniteOrZero(entry.emergencyReserveAtMonthEnd)
    )

    private fun sanitizeTransaction(tx: ImportedBankTransactionEntity): ImportedBankTransactionEntity = tx.copy(
        yearMonth = tx.yearMonth.trim(),
        bankName = tx.bankName.trim().ifBlank { "GENERIC" },
        date = tx.date.trim(),
        amount = finiteOrZero(tx.amount),
        category = tx.category.trim().ifBlank { "LIFESTYLE_LIVING" },
        matchedTxId = tx.matchedTxId?.takeIf { it > 0L },
        accountKey = tx.accountKey.trim().take(80),
        originalCurrency = tx.originalCurrency.trim().uppercase(java.util.Locale.ROOT).take(8).ifBlank { "CZK" },
        originalAmount = finiteOrZero(tx.originalAmount),
        fxRateToCzk = tx.fxRateToCzk.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    )
}

