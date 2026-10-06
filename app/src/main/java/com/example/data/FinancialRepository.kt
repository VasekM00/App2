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
        settingsDao.saveSettings(settings)
    }

    suspend fun updateSettingsAndSyncLedger(
        newSettings: SettingsEntity,
        currentYearMonth: String
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            settingsDao.saveSettings(newSettings)
            val currentEntry = ledgerDao.getEntryByYearMonth(currentYearMonth)
            if (currentEntry != null) {
                val updatedEleonora = if (newSettings.baseYear < newSettings.eReturnYear) {
                    newSettings.eParentalAllowanceMonthly
                } else {
                    newSettings.eStartingSalary
                }
                val updatedLiquid = newSettings.liquidPortfolioCurrent + if (!newSettings.isSingleHousehold) newSettings.eLiquidPortfolioCurrent else 0.0
                val updatedPension = newSettings.dpsBalanceCurrent + newSettings.dipBalanceCurrent + if (!newSettings.isSingleHousehold) (newSettings.eDpsBalanceCurrent + newSettings.eDipBalanceCurrent) else 0.0
                ledgerDao.updateEntry(
                    currentEntry.copy(
                        incVaclav = newSettings.vSalary,
                        incEleonora = if (updatedEleonora > 0.0) updatedEleonora else currentEntry.incEleonora,
                        expRent = if (newSettings.rentMonthly > 0.0) newSettings.rentMonthly else currentEntry.expRent,
                        portfolioBalanceAtMonthEnd = if (updatedLiquid > 0.0) updatedLiquid else currentEntry.portfolioBalanceAtMonthEnd,
                        pensionBalanceAtMonthEnd = if (updatedPension > 0.0) updatedPension else currentEntry.pensionBalanceAtMonthEnd,
                        emergencyReserveAtMonthEnd = if (newSettings.emergencyReserveCurrent > 0.0) newSettings.emergencyReserveCurrent else currentEntry.emergencyReserveAtMonthEnd
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
                        if (cur.eParentalAllowanceMonthly != incEleonora) {
                            updated = updated.copy(eParentalAllowanceMonthly = incEleonora)
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
     * (2800 CZK/yr). Persists the correction so the value stops drifting between DB and UI.
     */
    suspend fun repairLegacyEmployerContribution() = withContext(Dispatchers.IO) {
        val current = settingsDao.getSettingsDirect() ?: return@withContext
        if (current.employerRetirementMonthly >= 2795.0 && current.employerRetirementMonthly <= 2805.0) {
            settingsDao.saveSettings(current.copy(employerRetirementMonthly = 233.0))
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
        actionStateDao.toggleActionState("${year}_$actionId")
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
}

