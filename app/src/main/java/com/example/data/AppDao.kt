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

    @Query("SELECT yearMonth FROM ledger_entries")
    suspend fun getAllYearMonths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LedgerEntryEntity)

    @Update
    suspend fun updateEntry(entry: LedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<LedgerEntryEntity>)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

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
