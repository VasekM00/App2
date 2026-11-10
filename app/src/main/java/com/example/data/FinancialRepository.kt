package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FinancialRepository(
    private val database: AppDatabase,
    private val settingsDao: SettingsDao,
    private val ledgerDao: LedgerDao,
    private val actionStateDao: ActionStateDao,
    private val importedTransactionDao: ImportedTransactionDao
) {
    val settingsFlow: Flow<SettingsEntity> = settingsDao.getSettings()
        .map { it ?: SettingsEntity.freshDefaults() }

    val ledgerFlow: Flow<List<LedgerEntryEntity>> = ledgerDao.getAllEntries()

    val actionStatesFlow: Flow<Map<String, Boolean>> = actionStateDao.getAllActionStates()
        .map { list -> list.associate { it.actionKey to it.isDone } }

    suspend fun saveSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        val clean = com.example.util.BackupManager.sanitizeSettings(settings, SettingsEntity.freshDefaults())
        settingsDao.saveSettings(clean)
    }

    suspend fun updateSettingsAndSyncLedger(
        newSettings: SettingsEntity,
        currentYearMonth: String
    ) = withContext(Dispatchers.IO) {
        val cleanSettings = com.example.util.BackupManager.sanitizeSettings(newSettings, SettingsEntity.freshDefaults())
        database.withTransaction {
            settingsDao.saveSettings(cleanSettings)
            val currentEntry = ledgerDao.getEntryByYearMonth(currentYearMonth)
            if (currentEntry != null) {
                if (currentEntry.notes.contains("Imported")) {
                    return@withTransaction
                }
                val updatedEleonora = if (cleanSettings.baseYear < cleanSettings.eReturnYear) {
                    cleanSettings.eParentalAllowanceMonthly
                } else {
                    cleanSettings.eStartingSalary
                }
                val updatedLiquid = cleanSettings.liquidPortfolioCurrent + if (!cleanSettings.isSingleHousehold) cleanSettings.eLiquidPortfolioCurrent else 0.0
                val updatedPension = cleanSettings.dpsBalanceCurrent + cleanSettings.dipBalanceCurrent + if (!cleanSettings.isSingleHousehold) (cleanSettings.eDpsBalanceCurrent + cleanSettings.eDipBalanceCurrent) else 0.0
                ledgerDao.updateEntry(
                    currentEntry.copy(
                        incVaclav = cleanSettings.vSalary,
                        incEleonora = if (updatedEleonora > 0.0) updatedEleonora else currentEntry.incEleonora,
                        expRent = if (cleanSettings.rentMonthly > 0.0) cleanSettings.rentMonthly else currentEntry.expRent,
                        portfolioBalanceAtMonthEnd = if (updatedLiquid > 0.0) updatedLiquid else currentEntry.portfolioBalanceAtMonthEnd,
                        pensionBalanceAtMonthEnd = if (updatedPension > 0.0) updatedPension else currentEntry.pensionBalanceAtMonthEnd,
                        emergencyReserveAtMonthEnd = if (cleanSettings.emergencyReserveCurrent > 0.0) cleanSettings.emergencyReserveCurrent else currentEntry.emergencyReserveAtMonthEnd
                    )
                )
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
    ) = withContext(Dispatchers.IO) {
        if (yearMonth == currentYearMonth) {
            database.withTransaction {
                val cur = settingsDao.getSettingsDirect() ?: SettingsEntity.freshDefaults()
                var updated = cur
                if (incVaclav > 0.0 && cur.vSalary != incVaclav) {
                    updated = updated.copy(vSalary = incVaclav)
                }
                if (incEleonora > 0.0) {
                    if (cur.baseYear < cur.eReturnYear) {
                        val candidateAllowance = (incEleonora - cur.eLecturingMonthly).coerceAtLeast(0.0)
                        val allowanceToSet = if (candidateAllowance > 0.0) candidateAllowance else incEleonora
                        if (cur.eParentalAllowanceMonthly != allowanceToSet) {
                            updated = updated.copy(eParentalAllowanceMonthly = allowanceToSet)
                        }
                    } else {
                        if (cur.eStartingSalary != incEleonora) {
                            updated = updated.copy(eStartingSalary = incEleonora)
                        }
                    }
                }
                if (expRent > 0.0 && cur.rentMonthly != expRent) {
                    updated = updated.copy(rentMonthly = expRent)
                }
                // Ledger balances are HOUSEHOLD totals (see updateSettingsAndSyncLedger, which writes
                // primary + spouse). Primary-only settings fields must receive total minus spouse share.
                val includeSpouse = !cur.isSingleHousehold
                val spouseLiquid = if (includeSpouse) cur.eLiquidPortfolioCurrent else 0.0
                val spousePension = if (includeSpouse) cur.eDpsBalanceCurrent + cur.eDipBalanceCurrent else 0.0
                if (portfolioBalance > 0.0) {
                    val primaryLiquid = (portfolioBalance - spouseLiquid).coerceAtLeast(0.0)
                    if (cur.liquidPortfolioCurrent != primaryLiquid) {
                        updated = updated.copy(liquidPortfolioCurrent = primaryLiquid)
                    }
                }
                if (pensionBalance > 0.0) {
                    val primaryPension = (pensionBalance - spousePension).coerceAtLeast(0.0)
                    val curPrimary = cur.dpsBalanceCurrent + cur.dipBalanceCurrent
                    if (curPrimary != primaryPension) {
                        // Preserve the existing DPS/DIP split instead of collapsing everything into DPS.
                        val dpsShare = if (curPrimary > 0.0) cur.dpsBalanceCurrent / curPrimary else 1.0
                        val newDps = primaryPension * dpsShare
                        updated = updated.copy(
                            dpsBalanceCurrent = newDps,
                            dipBalanceCurrent = primaryPension - newDps
                        )
                    }
                }
                if (emergencyReserve > 0.0 && cur.emergencyReserveCurrent != emergencyReserve) {
                    updated = updated.copy(emergencyReserveCurrent = emergencyReserve)
                }
                if (updated != cur) {
                    settingsDao.saveSettings(updated)
                }
            }
        }
    }

    /**
     * One-time repair for databases written by older app versions that stored the employer
     * retirement contribution as 2800 (monthly) instead of the correct 233 CZK/month
     * (2800 CZK/yr), plus automatic calendar-year rollover when the real Czech year advances.
     */
    suspend fun repairLegacyEmployerContribution(
        targetCalendarYear: Int = java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague")).year
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val current = settingsDao.getSettingsDirect() ?: return@withTransaction
            var updated = current
            if (updated.employerRetirementMonthly >= 2795.0 && updated.employerRetirementMonthly <= 2805.0) {
                updated = updated.copy(employerRetirementMonthly = 233.0)
            }
            if (updated.baseYear in 2026 until targetCalendarYear) {
                val delta = targetCalendarYear - updated.baseYear
                updated = updated.copy(
                    baseYear = targetCalendarYear,
                    primaryAge = (updated.primaryAge + delta).coerceIn(15, 100)
                )
            }
            if (updated != current) {
                settingsDao.saveSettings(updated)
            }
        }
    }

    suspend fun addLedgerEntry(entry: LedgerEntryEntity) = withContext(Dispatchers.IO) {
        ledgerDao.insertEntry(entry)
    }

    suspend fun updateLedgerEntry(entry: LedgerEntryEntity) = withContext(Dispatchers.IO) {
        ledgerDao.updateEntry(entry)
    }

    suspend fun updateLedgerEntries(entries: List<LedgerEntryEntity>) = withContext(Dispatchers.IO) {
        ledgerDao.updateEntries(entries)
    }

    suspend fun addLedgerEntries(entries: List<LedgerEntryEntity>) = withContext(Dispatchers.IO) {
        ledgerDao.insertEntries(entries)
    }

    suspend fun existingYearMonths(): Set<String> = withContext(Dispatchers.IO) {
        ledgerDao.getAllYearMonths().toSet()
    }

    suspend fun getLedgerEntryByYearMonth(yearMonth: String): LedgerEntryEntity? = withContext(Dispatchers.IO) {
        ledgerDao.getEntryByYearMonth(yearMonth)
    }

    suspend fun getAllLedgerEntriesDirect(): List<LedgerEntryEntity> = withContext(Dispatchers.IO) {
        ledgerDao.getAllEntriesDirect()
    }

    suspend fun deleteLedgerEntry(id: Long): String? = withContext(Dispatchers.IO) {
        database.withTransaction {
            val entry = ledgerDao.getEntryById(id)
            ledgerDao.deleteEntry(id)
            if (entry != null) {
                importedTransactionDao.deleteTransactionsForMonth(entry.yearMonth)
            }
            entry?.yearMonth
        }
    }

    suspend fun deleteImportedTransactionsForMonth(yearMonth: String) = withContext(Dispatchers.IO) {
        importedTransactionDao.deleteTransactionsForMonth(yearMonth)
    }

    suspend fun cleanupOrphanedImportedTransactions(): Int = withContext(Dispatchers.IO) {
        importedTransactionDao.deleteOrphanedTransactions()
    }

    suspend fun setActionState(year: Int, actionId: String, isDone: Boolean) = withContext(Dispatchers.IO) {
        val key = "${year}_$actionId"
        actionStateDao.saveActionState(ActionStateEntity(key, year, actionId, isDone))
    }

    suspend fun toggleActionState(year: Int, actionId: String) = withContext(Dispatchers.IO) {
        actionStateDao.toggleActionStateUpsert("${year}_$actionId", year, actionId)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.withTransaction {
            ledgerDao.deleteAllEntries()
            actionStateDao.deleteAllActionStates()
            importedTransactionDao.deleteAllTransactions()
        }
    }

    fun getImportedTransactions(yearMonth: String): Flow<List<ImportedBankTransactionEntity>> {
        return importedTransactionDao.getTransactionsForMonth(yearMonth)
    }

    suspend fun getImportedTransactionsDirect(yearMonth: String): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        importedTransactionDao.getTransactionsForMonthDirect(yearMonth)
    }

    suspend fun saveImportedTransactions(transactions: List<ImportedBankTransactionEntity>) = withContext(Dispatchers.IO) {
        importedTransactionDao.insertTransactions(transactions)
    }

    suspend fun updateImportedTransactions(transactions: List<ImportedBankTransactionEntity>) = withContext(Dispatchers.IO) {
        importedTransactionDao.updateTransactions(transactions)
    }

    suspend fun updateImportedTransaction(transaction: ImportedBankTransactionEntity) = withContext(Dispatchers.IO) {
        importedTransactionDao.updateTransaction(transaction)
    }

    suspend fun getImportedTransactionById(id: Long): ImportedBankTransactionEntity? = withContext(Dispatchers.IO) {
        importedTransactionDao.getTransactionById(id)
    }

    suspend fun deleteImportedTransactionsForBankAndMonth(yearMonth: String, bankName: String) = withContext(Dispatchers.IO) {
        importedTransactionDao.deleteTransactionsForBankAndMonth(yearMonth, bankName)
    }

    fun getAllImportedTransactions(): kotlinx.coroutines.flow.Flow<List<ImportedBankTransactionEntity>> {
        return importedTransactionDao.getAllImportedTransactions()
    }

    suspend fun getTransactionsForBankAndMonthDirect(yearMonth: String, bankName: String): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        importedTransactionDao.getTransactionsForBankAndMonthDirect(yearMonth, bankName)
    }

    suspend fun getTransactionsInDateRangeDirect(startDate: String, endDate: String): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        importedTransactionDao.getTransactionsInDateRangeDirect(startDate, endDate)
    }

    suspend fun saveImportedTransactionsSmartMerge(
        yearMonth: String,
        bankName: String,
        newTransactions: List<ImportedBankTransactionEntity>
    ): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        // Read-modify-write must be atomic: two concurrent imports of the same
        // bank/month would otherwise both observe the same existing set and double-insert.
        database.withTransaction {
            val existing = importedTransactionDao.getTransactionsForBankAndMonthDirect(yearMonth, bankName)
            if (existing.isEmpty()) {
                importedTransactionDao.insertTransactions(newTransactions)
                return@withTransaction newTransactions
            }

            val existingCounts = mutableMapOf<String, Int>()
            for (tx in existing) {
                val fp = com.example.util.CrossStatementReconciliationEngine.computeFingerprint(
                    tx.date, tx.bankName, tx.amount, tx.counterpartyAccount, tx.counterpartyName, tx.message, tx.variableSymbol
                )
                existingCounts[fp] = (existingCounts[fp] ?: 0) + 1
            }

            val incomingCounts = mutableMapOf<String, Int>()
            val toInsert = mutableListOf<ImportedBankTransactionEntity>()
            for (tx in newTransactions) {
                val fp = com.example.util.CrossStatementReconciliationEngine.computeFingerprint(
                    tx.date, tx.bankName, tx.amount, tx.counterpartyAccount, tx.counterpartyName, tx.message, tx.variableSymbol
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
        actionStateDao.getAllActionStatesDirect()
    }

    suspend fun getAllImportedTransactionsDirect(): List<ImportedBankTransactionEntity> = withContext(Dispatchers.IO) {
        importedTransactionDao.getAllTransactionsDirect()
    }

    suspend fun restoreFullBackupData(
        settings: SettingsEntity,
        ledgerEntries: List<LedgerEntryEntity>,
        actionStates: List<ActionStateEntity>,
        transactions: List<ImportedBankTransactionEntity>
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            ledgerDao.deleteAllEntries()
            actionStateDao.deleteAllActionStates()
            importedTransactionDao.deleteAllTransactions()
            settingsDao.saveSettings(settings)
            if (ledgerEntries.isNotEmpty()) {
                ledgerDao.insertEntries(ledgerEntries)
            }
            if (actionStates.isNotEmpty()) {
                actionStateDao.saveActionStates(actionStates)
            }
            if (transactions.isNotEmpty()) {
                importedTransactionDao.insertTransactions(transactions)
            }
        }
    }
}

