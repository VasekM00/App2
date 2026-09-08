package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FinancialRepository(
    private val settingsDao: SettingsDao,
    private val ledgerDao: LedgerDao,
    private val actionStateDao: ActionStateDao,
    private val importedTransactionDao: ImportedTransactionDao
) {
    val settingsFlow: Flow<SettingsEntity> = settingsDao.getSettings()
        .map { entity ->
            val s = entity ?: SettingsEntity.freshDefaults()
            // Migrate: old data stored 2800.0 as if it were monthly (incorrectly).
            // The real annual employer contribution is 2 800 CZK/yr = 233 CZK/mo.
            if (s.employerRetirementMonthly >= 2795.0 && s.employerRetirementMonthly <= 2805.0) {
                s.copy(employerRetirementMonthly = 233.0)
            } else {
                s
            }
        }

    val ledgerFlow: Flow<List<LedgerEntryEntity>> = ledgerDao.getAllEntries()

    val actionStatesFlow: Flow<Map<String, Boolean>> = actionStateDao.getAllActionStates()
        .map { list -> list.associate { it.actionKey to it.isDone } }

    suspend fun saveSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        settingsDao.saveSettings(settings)
    }

    suspend fun addLedgerEntry(entry: LedgerEntryEntity) = withContext(Dispatchers.IO) {
        ledgerDao.insertEntry(entry)
    }

    suspend fun updateLedgerEntry(entry: LedgerEntryEntity) = withContext(Dispatchers.IO) {
        ledgerDao.updateEntry(entry)
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

    suspend fun deleteLedgerEntry(id: Long) = withContext(Dispatchers.IO) {
        ledgerDao.deleteEntry(id)
    }

    suspend fun setActionState(year: Int, actionId: String, isDone: Boolean) = withContext(Dispatchers.IO) {
        val key = "${year}_$actionId"
        actionStateDao.saveActionState(ActionStateEntity(key, year, actionId, isDone))
    }

    suspend fun toggleActionState(year: Int, actionId: String) = withContext(Dispatchers.IO) {
        actionStateDao.toggleActionState("${year}_$actionId")
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        ledgerDao.deleteAllEntries()
        actionStateDao.deleteAllActionStates()
        importedTransactionDao.deleteAllTransactions()
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
        val existing = importedTransactionDao.getTransactionsForBankAndMonthDirect(yearMonth, bankName)
        if (existing.isEmpty()) {
            importedTransactionDao.insertTransactions(newTransactions)
            return@withContext newTransactions
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
        return@withContext toInsert
    }
}

