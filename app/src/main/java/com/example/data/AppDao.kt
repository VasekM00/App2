package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: SettingsEntity)
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY yearMonth DESC")
    fun getAllEntries(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries ORDER BY yearMonth ASC")
    suspend fun getAllEntriesDirect(): List<LedgerEntryEntity>

    @Query("SELECT yearMonth FROM ledger_entries")
    suspend fun getAllYearMonths(): List<String>

    @Query("SELECT * FROM ledger_entries WHERE yearMonth = :yearMonth LIMIT 1")
    suspend fun getEntryByYearMonth(yearMonth: String): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LedgerEntryEntity)

    @Update
    suspend fun updateEntry(entry: LedgerEntryEntity)

    @Update
    suspend fun updateEntries(entries: List<LedgerEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<LedgerEntryEntity>)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): LedgerEntryEntity?

    @Query("DELETE FROM ledger_entries")
    suspend fun deleteAllEntries()
}

@Dao
interface ActionStateDao {
    @Query("SELECT * FROM action_states")
    fun getAllActionStates(): Flow<List<ActionStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActionState(state: ActionStateEntity)

    @Query("UPDATE action_states SET isDone = NOT isDone WHERE actionKey = :key")
    suspend fun toggleActionState(key: String)

    @Query("DELETE FROM action_states")
    suspend fun deleteAllActionStates()
}

@Dao
interface ImportedTransactionDao {
    @Query("SELECT * FROM imported_bank_transactions WHERE yearMonth = :yearMonth ORDER BY date ASC, id ASC")
    fun getTransactionsForMonth(yearMonth: String): Flow<List<ImportedBankTransactionEntity>>

    @Query("SELECT * FROM imported_bank_transactions WHERE yearMonth = :yearMonth ORDER BY date ASC, id ASC")
    suspend fun getTransactionsForMonthDirect(yearMonth: String): List<ImportedBankTransactionEntity>

    @Query("SELECT * FROM imported_bank_transactions WHERE yearMonth = :yearMonth AND bankName = :bankName ORDER BY date ASC, id ASC")
    suspend fun getTransactionsForBankAndMonthDirect(yearMonth: String, bankName: String): List<ImportedBankTransactionEntity>

    @Query("SELECT * FROM imported_bank_transactions ORDER BY date ASC, id ASC")
    fun getAllImportedTransactions(): Flow<List<ImportedBankTransactionEntity>>

    @Query("SELECT * FROM imported_bank_transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC, id ASC")
    suspend fun getTransactionsInDateRangeDirect(startDate: String, endDate: String): List<ImportedBankTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<ImportedBankTransactionEntity>)

    @Update
    suspend fun updateTransactions(transactions: List<ImportedBankTransactionEntity>)

    @Query("DELETE FROM imported_bank_transactions WHERE yearMonth = :yearMonth AND bankName = :bankName")
    suspend fun deleteTransactionsForBankAndMonth(yearMonth: String, bankName: String)

    @Query("DELETE FROM imported_bank_transactions WHERE yearMonth = :yearMonth")
    suspend fun deleteTransactionsForMonth(yearMonth: String)

    @Query("DELETE FROM imported_bank_transactions WHERE yearMonth NOT IN (SELECT yearMonth FROM ledger_entries)")
    suspend fun deleteOrphanedTransactions(): Int

    @Query("DELETE FROM imported_bank_transactions")
    suspend fun deleteAllTransactions()
}
