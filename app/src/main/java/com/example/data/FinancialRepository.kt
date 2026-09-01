package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FinancialRepository(
    private val settingsDao: SettingsDao,
    private val ledgerDao: LedgerDao,
    private val actionStateDao: ActionStateDao
) {
    val settingsFlow: Flow<SettingsEntity> = settingsDao.getSettings()
        .map { entity ->
            val s = entity ?: SettingsEntity.freshDefaults()
            // Migrate: old data stored 2800.0 as if it were monthly (incorrectly).
            // The real annual employer contribution is 2 800 Kč/yr = 233 Kč/mo.
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
    }
}

