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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinancialEngine.calculate(SettingsEntity())
    )

    fun updateSettings(newSettings: SettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(newSettings)
            _uiEvent.emit(UiMessage.ShowSnackbar("Settings saved successfully"))
        }
    }

    fun addLedgerEntry(
        yearMonth: String,
        incVaclav: Double,
        incEleonora: Double,
        expRent: Double,
        expGroc: Double,
        expOther: Double,
        notes: String
    ) {
        viewModelScope.launch {
            val entry = LedgerEntryEntity(
                yearMonth = yearMonth,
                incVaclav = incVaclav,
                incEleonora = incEleonora,
                expRent = expRent,
                expGroceries = expGroc,
                expOther = expOther,
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
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                val entriesToInsert = mutableListOf<LedgerEntryEntity>()
                var line: String? = reader.readLine() // Skip header
                while (run { line = reader.readLine(); line } != null) {
                    val rawLine = line!!.trim()
                    if (rawLine.isBlank()) continue
                    val tokens = rawLine.split(",")
                    if (tokens.size >= 7) {
                        val ym = tokens[0].trim()
                        val incV = tokens[1].trim().toDoubleOrNull() ?: 0.0
                        val incE = tokens[2].trim().toDoubleOrNull() ?: 0.0
                        val expR = tokens[3].trim().toDoubleOrNull() ?: 0.0
                        val expG = tokens[4].trim().toDoubleOrNull() ?: 0.0
                        val expO = tokens[5].trim().toDoubleOrNull() ?: 0.0
                        val notes = tokens[6].trim()
                        if (ym.isNotEmpty()) {
                            entriesToInsert.add(
                                LedgerEntryEntity(
                                    yearMonth = ym,
                                    incVaclav = incV,
                                    incEleonora = incE,
                                    expRent = expR,
                                    expGroceries = expG,
                                    expOther = expO,
                                    notes = notes
                                )
                            )
                        }
                    }
                }
                reader.close()
                if (entriesToInsert.isNotEmpty()) {
                    repository.addLedgerEntries(entriesToInsert)
                    _uiEvent.emit(UiMessage.ShowSnackbar("Successfully imported ${entriesToInsert.size} entries!"))
                } else {
                    _uiEvent.emit(UiMessage.ShowSnackbar("No valid entries found in CSV"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvent.emit(UiMessage.ShowSnackbar("CSV import failed: ${e.localizedMessage ?: "Invalid format"}"))
            }
        }
    }

    fun toggleAction(year: Int, actionId: String, currentIsDone: Boolean) {
        viewModelScope.launch {
            repository.setActionState(year, actionId, !currentIsDone)
        }
    }

    fun setSensitivityOverrides(returnPct: Double?, cpiPct: Double?, swrPct: Double?) {
        sensitivityReturnOverride.value = returnPct
        sensitivityCpiOverride.value = cpiPct
        sensitivitySwrOverride.value = swrPct
    }

    fun resetSettingsToDefault() {
        viewModelScope.launch {
            repository.saveSettings(SettingsEntity())
            setSensitivityOverrides(null, null, null)
            _uiEvent.emit(UiMessage.ShowSnackbar("Reset all settings to default"))
        }
    }

    fun clearAllUserData() {
        viewModelScope.launch {
            repository.clearAllData()
            repository.saveSettings(SettingsEntity())
            setSensitivityOverrides(null, null, null)
            _uiEvent.emit(UiMessage.ShowSnackbar("All user data cleared"))
        }
    }
}

