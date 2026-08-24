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
        actionStateDao = db.actionStateDao()
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
        notes: String
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
                notes = notes
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
                    _uiEvent.emit(UiMessage.ShowSnackbar("Error: Unable to open CSV file"))
                    return@launch
                }
                val existingYearMonths = repository.existingYearMonths()
                val entriesToInsert = mutableListOf<LedgerEntryEntity>()
                var skippedDuplicates = 0
                inputStream.use { stream ->
                    java.io.BufferedReader(java.io.InputStreamReader(stream)).use { reader ->
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
                }
                if (entriesToInsert.isNotEmpty()) {
                    repository.addLedgerEntries(entriesToInsert)
                    val dupNote = if (skippedDuplicates > 0) " ($skippedDuplicates duplicate months skipped)" else ""
                    _uiEvent.emit(UiMessage.ShowSnackbar("Successfully imported ${entriesToInsert.size} entries!$dupNote"))
                } else if (skippedDuplicates > 0) {
                    _uiEvent.emit(UiMessage.ShowSnackbar("All $skippedDuplicates entries already exist - nothing imported"))
                } else {
                    _uiEvent.emit(UiMessage.ShowSnackbar("No valid entries found in CSV"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiMessage.ShowSnackbar("CSV import failed: ${e.localizedMessage ?: "Invalid format"}"))
            }
        }
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

