package com.example.ui.state

import android.app.Application
import com.example.data.AppPreferencesRepository
import com.example.data.FinancialRepository
import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.util.BackupManager
import com.example.util.CrossStatementAuditReport
import com.example.util.CzechEconomicSyncService
import com.example.util.FxRateService
import com.example.util.InvestmentLotManager
import com.example.util.MerchantCategoryManager
import com.example.util.StatementParseSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class BackupAndSyncController(
    private val application: Application,
    private val scope: CoroutineScope,
    private val repository: FinancialRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val liveSettings: MutableStateFlow<SettingsEntity>,
    private val pendingSettingsWrites: AtomicInteger,
    private val liveRegulatoryData: MutableStateFlow<CzechRegulatoryData?>,
    private val isSyncing: MutableStateFlow<Boolean>,
    private val activeAuditReport: MutableStateFlow<CrossStatementAuditReport?>,
    private val setPendingStatementImport: (StatementParseSummary?, Boolean) -> Unit,
    private val clearSensitivityOverrides: () -> Unit,
    private val cancelStatementJobs: suspend () -> Unit,
    private val importMutex: Mutex,
    private val emitSnackbar: suspend (String) -> Unit
) {
    private val syncInFlight = AtomicBoolean(false)
    private var syncJob: Job? = null

    fun hydrateFxRatesFromJson(fxJson: String) {
        if (fxJson.isBlank() || fxJson.trim() == "{}") return
        runCatching {
            val obj = JSONObject(fxJson)
            val parsedRates = linkedMapOf<String, Double>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                if (code == "rateDate") continue
                val rate = obj.optDouble(code, Double.NaN)
                if (rate.isFinite() && rate > 0.0) {
                    FxRateService.setRate(code, rate)
                    parsedRates[code.uppercase(java.util.Locale.ROOT)] = rate
                }
            }
            if (parsedRates.isNotEmpty() && liveRegulatoryData.value == null) {
                val savedDate = obj.optString("rateDate", "").trim()
                val defaults = CzechEconomicSyncService.loadBundledRegulatoryManifest(application)
                liveRegulatoryData.value = defaults.copy(
                    sourceName = if (savedDate.isNotBlank()) {
                        "Saved ČNB FX ($savedDate) & Bundled 2026 Statutory Constants"
                    } else {
                        "Saved ČNB FX & Bundled 2026 Statutory Constants"
                    },
                    isFxLive = false,
                    eurCzkRate = parsedRates["EUR"] ?: defaults.eurCzkRate,
                    usdCzkRate = parsedRates["USD"] ?: defaults.usdCzkRate,
                    gbpCzkRate = parsedRates["GBP"] ?: defaults.gbpCzkRate,
                    plnCzkRate = parsedRates["PLN"] ?: defaults.plnCzkRate,
                    chfCzkRate = parsedRates["CHF"] ?: defaults.chfCzkRate,
                    hufCzkRate = parsedRates["HUF"] ?: defaults.hufCzkRate,
                    rateDate = savedDate
                )
            }
        }
    }

    fun syncLiveCzechData() {
        if (!syncInFlight.compareAndSet(false, true)) return
        isSyncing.value = true
        syncJob = scope.launch(Dispatchers.IO) {
            try {
                val data = CzechEconomicSyncService.fetchLiveRegulatoryData(application)
                liveRegulatoryData.value = data
                if (data.isFxLive) {
                    val liveMap = linkedMapOf(
                        "EUR" to data.eurCzkRate,
                        "USD" to data.usdCzkRate,
                        "GBP" to data.gbpCzkRate,
                        "PLN" to data.plnCzkRate,
                        "CHF" to data.chfCzkRate,
                        "HUF" to data.hufCzkRate
                    ).filterValues { it.isFinite() && it > 0.0 }
                    FxRateService.updateRates(liveMap)
                    val fxJson = JSONObject()
                    liveMap.forEach { (code, rate) -> fxJson.put(code, rate) }
                    if (data.rateDate.isNotBlank()) {
                        fxJson.put("rateDate", data.rateDate)
                    }
                    if (fxJson.length() > 0) {
                        preferencesRepository.setCnbFxRatesJson(fxJson.toString())
                    }
                    preferencesRepository.setLastCzechSyncTimestamp(System.currentTimeMillis())
                    emitSnackbar("Live ČNB FX rates synced · 2026 statutory constants verified")
                } else {
                    emitSnackbar("Offline: ČNB FX unavailable; using bundled 2026 statutory constants")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emitSnackbar("Sync failed. Using bundled 2026 statutory constants")
            } finally {
                isSyncing.value = false
                syncInFlight.set(false)
            }
        }
    }

    fun updateSettings(showSnackbar: Boolean = false, transform: (SettingsEntity) -> SettingsEntity) {
        pendingSettingsWrites.incrementAndGet()
        liveSettings.update { cur ->
            BackupManager.sanitizeSettings(transform(cur), SettingsEntity.freshDefaults())
        }
        scope.launch(Dispatchers.IO) {
            try {
                importMutex.withLock {
                    val now = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
                    val prevRulesJson = runCatching { repository.settingsFlow.first().merchantRulesJson }.getOrNull()
                    val clean = repository.updateSettingsTransformAndSyncLedger(now, transform)
                    if (clean != null) {
                        if (prevRulesJson != null && clean.merchantRulesJson != prevRulesJson) {
                            val parsedRules = MerchantCategoryManager.parseRulesFromJson(clean.merchantRulesJson)
                            MerchantCategoryManager.saveAllOverrides(application, parsedRules, preferencesRepository.storeName)
                        }
                        if (pendingSettingsWrites.decrementAndGet() == 0) {
                            liveSettings.value = clean
                        }
                        if (showSnackbar) emitSnackbar("Settings saved successfully")
                    } else {
                        if (pendingSettingsWrites.decrementAndGet() == 0) {
                            liveSettings.value = repository.settingsFlow.first()
                        }
                        emitSnackbar("Failed to save settings to local database")
                    }
                }
            } catch (e: CancellationException) {
                pendingSettingsWrites.decrementAndGet()
                throw e
            } catch (_: Exception) {
                if (pendingSettingsWrites.decrementAndGet() == 0) {
                    runCatching { liveSettings.value = repository.settingsFlow.first() }
                }
                emitSnackbar("Failed to save settings to local database")
            }
        }
    }

    suspend fun dismissSubscription(merchantKey: String) {
        try {
            importMutex.withLock {
                val updated = preferencesRepository.snapshot.value.dismissedSubscriptionMerchants + merchantKey
                preferencesRepository.setDismissedMerchants(updated)
                emitSnackbar("Subscription dismissed: $merchantKey")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to update subscription preference")
        }
    }

    suspend fun restoreSubscription(merchantKey: String) {
        try {
            importMutex.withLock {
                val updated = preferencesRepository.snapshot.value.dismissedSubscriptionMerchants - merchantKey
                preferencesRepository.setDismissedMerchants(updated)
                emitSnackbar("Subscription restored: $merchantKey")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to update subscription preference")
        }
    }

    suspend fun clearAllDismissedSubscriptions() {
        try {
            importMutex.withLock {
                preferencesRepository.setDismissedMerchants(emptySet())
                emitSnackbar("All dismissed subscriptions restored")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to update subscription preferences")
        }
    }

    suspend fun toggleAction(year: Int, actionId: String) {
        try {
            importMutex.withLock {
                repository.toggleActionState(year, actionId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to update action item")
        }
    }

    suspend fun restoreFullBackup(payload: BackupManager.FullBackupPayload) {
        try {
            importMutex.withLock {
                cancelStatementJobs()
                repository.restoreFullBackupData(
                    payload.settings, payload.ledgerEntries, payload.actionStates, payload.importedTransactions
                )
                val cleanRestored = BackupManager.sanitizeSettings(payload.settings, SettingsEntity.freshDefaults())
                if (pendingSettingsWrites.get() == 0) {
                    liveSettings.value = cleanRestored
                }
                val parsedRules = MerchantCategoryManager.parseRulesFromJson(cleanRestored.merchantRulesJson)
                MerchantCategoryManager.saveAllOverrides(application, parsedRules, preferencesRepository.storeName)
                val restoredLots = when {
                    payload.investmentLots.isNotEmpty() -> payload.investmentLots
                    payload.preferences.containsKey("user_investment_lots_json") ->
                        InvestmentLotManager.parseLotsFromJson(payload.preferences["user_investment_lots_json"] ?: "")
                    else -> emptyList()
                }
                InvestmentLotManager.persistLots(application, restoredLots, preferencesRepository.storeName)
                preferencesRepository.restorePreferencesFromBackup(payload.preferences)
                clearSensitivityOverrides()
                setPendingStatementImport(null, true)
                activeAuditReport.value = null
                emitSnackbar(
                    "Full backup restored (${payload.ledgerEntries.size} months, ${payload.importedTransactions.size} transactions)"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to restore backup")
        }
    }

    suspend fun resetSettingsToDefault() {
        try {
            importMutex.withLock {
                val curMerchantRules = runCatching { repository.settingsFlow.first().merchantRulesJson }.getOrDefault("{}")
                val defaults = SettingsEntity.freshDefaults().copy(merchantRulesJson = curMerchantRules)
                val currentYm = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
                repository.updateSettingsAndSyncLedger(defaults, currentYm)
                if (pendingSettingsWrites.get() == 0) {
                    liveSettings.value = defaults
                }
                clearSensitivityOverrides()
                emitSnackbar("Reset all settings to default")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to reset settings")
        }
    }

    suspend fun clearAllUserData() {
        try {
            importMutex.withLock {
                syncJob?.cancelAndJoin()
                cancelStatementJobs()
                val defaults = SettingsEntity.freshDefaults()
                repository.withTransaction {
                    repository.clearAllData()
                    repository.saveSettings(defaults)
                }
                if (pendingSettingsWrites.get() == 0) {
                    liveSettings.value = defaults
                }
                clearSensitivityOverrides()
                clearAuxiliaryStoresInternal(application, preferencesRepository)
                liveRegulatoryData.value = null
                setPendingStatementImport(null, true)
                activeAuditReport.value = null
                emitSnackbar("All user data cleared")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to clear user data")
        }
    }
}
