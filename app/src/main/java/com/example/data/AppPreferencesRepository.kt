package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class AppPreferencesSnapshot(
    val themeMode: String = "SYSTEM",
    val lastImportTimestamp: Long? = null,
    val lastCzechSyncTimestamp: Long? = null,
    val dismissedSubscriptionMerchants: Set<String> = emptySet(),
    val bankBalances: Map<String, Double> = emptyMap(),
    val bankImportTimestamps: Map<String, Long> = emptyMap(),
    val investmentLotsJson: String = "[]",
    val merchantOverridesJson: String = "{}",
    val cnbFxRatesJson: String = "{}",
    val biometricLockEnabled: Boolean = false,
    val flagSecureEnabled: Boolean = false,
    val isHydrated: Boolean = false
)

/**
 * Single consolidated SharedPreferences repository ("app_user_prefs") that replaces all 7 legacy XML stores.
 * All disk reads and writes execute on [Dispatchers.IO] so Activity/ViewModel cold start performs zero main-thread I/O.
 */
class AppPreferencesRepository(
    context: Context,
    private val prefsName: String = DEFAULT_PREFS_NAME
) {
    val storeName: String get() = prefsName
    private val appContext: Context = context.applicationContext
    private val prefsLock = Any()

    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    private val _snapshot = MutableStateFlow(AppPreferencesSnapshot())
    val snapshot: StateFlow<AppPreferencesSnapshot> = _snapshot.asStateFlow()

    suspend fun hydrate(): AppPreferencesSnapshot = withContext(Dispatchers.IO) {
        val loaded = synchronized(prefsLock) {
            if (!prefs.getBoolean(KEY_LEGACY_MIGRATED_V1, false) && prefsName == DEFAULT_PREFS_NAME) {
                migrateLegacyStoresLocked()
            }
            readSnapshotLocked().copy(isHydrated = true)
        }
        _snapshot.value = loaded
        loaded
    }

    private fun migrateLegacyStoresLocked() {
        val themePrefs = appContext.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE)
        val importPrefs = appContext.getSharedPreferences("statement_import_prefs", Context.MODE_PRIVATE)
        val syncPrefs = appContext.getSharedPreferences("czech_sync_prefs", Context.MODE_PRIVATE)
        val subPrefs = appContext.getSharedPreferences("subscription_auditor_prefs", Context.MODE_PRIVATE)
        val lotPrefs = appContext.getSharedPreferences("investment_lots_prefs", Context.MODE_PRIVATE)
        val merchPrefs = appContext.getSharedPreferences("merchant_category_overrides", Context.MODE_PRIVATE)
        val appPrefs = appContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

        prefs.edit(commit = true) {
            // Theme
            if (!prefs.contains(KEY_THEME_MODE)) {
                val legacyTheme = themePrefs.getString("theme_mode", null)
                    ?: if (themePrefs.contains("is_dark_theme")) {
                        if (themePrefs.getBoolean("is_dark_theme", false)) "DARK" else "LIGHT"
                    } else {
                        null
                    }
                if (!legacyTheme.isNullOrBlank()) {
                    putString(KEY_THEME_MODE, normalizeThemeMode(legacyTheme))
                }
            }

            // Statement import timestamps & per-bank closing balances
            if (!prefs.contains(KEY_LAST_IMPORT_ALL)) {
                val ts = importPrefs.getLong("last_import_all", 0L)
                if (ts > 0L) putLong(KEY_LAST_IMPORT_ALL, ts)
            }
            for ((k, v) in importPrefs.all) {
                when {
                    k == "fast_boot_snapshot_v1" -> Unit // Intentionally dropped (eliminates sync cold-start JSON)
                    k.startsWith("last_import_") && v is Number -> {
                        if (!prefs.contains(k)) putLong(k, v.toLong())
                    }
                    k.startsWith("bank_bal_") && v != null -> {
                        if (!prefs.contains(k)) putString(k, v.toString())
                    }
                    k in MIGRATION_FLAG_KEYS && v is Boolean -> {
                        if (!prefs.contains(k)) putBoolean(k, v)
                    }
                }
            }

            // Czech economic sync timestamps & FX rates
            if (!prefs.contains(KEY_LAST_CZECH_SYNC)) {
                val ts = syncPrefs.getLong("last_czech_sync_time", 0L)
                if (ts > 0L) putLong(KEY_LAST_CZECH_SYNC, ts)
            }
            if (!prefs.contains(KEY_CNB_FX_JSON)) {
                val fxJson = syncPrefs.getString("cnb_fx_rates_json", null)
                if (!fxJson.isNullOrBlank()) putString(KEY_CNB_FX_JSON, fxJson)
            }

            // Dismissed recurring subscriptions
            if (!prefs.contains(KEY_DISMISSED_MERCHANTS)) {
                val dismissed = subPrefs.getStringSet("dismissed_merchants", null)
                if (!dismissed.isNullOrEmpty()) {
                    putStringSet(KEY_DISMISSED_MERCHANTS, dismissed.toSet())
                }
            }

            // Investment lots
            if (!prefs.contains(KEY_LOTS_JSON)) {
                val lots = lotPrefs.getString("user_investment_lots_json", null)
                if (!lots.isNullOrBlank()) putString(KEY_LOTS_JSON, lots)
            }

            // Merchant overrides
            if (!prefs.contains(KEY_MERCHANT_OVERRIDES_JSON)) {
                val merch = merchPrefs.getString("overrides_json", null)
                if (!merch.isNullOrBlank()) putString(KEY_MERCHANT_OVERRIDES_JSON, merch)
            }

            // Privacy/misc flags if any existed in generic store
            if (!prefs.contains(KEY_BIOMETRIC_LOCK) && appPrefs.contains(KEY_BIOMETRIC_LOCK)) {
                putBoolean(KEY_BIOMETRIC_LOCK, appPrefs.getBoolean(KEY_BIOMETRIC_LOCK, false))
            }
            if (!prefs.contains(KEY_FLAG_SECURE) && appPrefs.contains(KEY_FLAG_SECURE)) {
                putBoolean(KEY_FLAG_SECURE, appPrefs.getBoolean(KEY_FLAG_SECURE, false))
            }

            putBoolean(KEY_LEGACY_MIGRATED_V1, true)
        }

        // Clean obsolete boot snapshot from legacy store without erasing historical user prefs
        if (importPrefs.contains("fast_boot_snapshot_v1")) {
            importPrefs.edit { remove("fast_boot_snapshot_v1") }
        }
    }

    private fun readSnapshotLocked(): AppPreferencesSnapshot {
        val theme = normalizeThemeMode(prefs.getString(KEY_THEME_MODE, "SYSTEM"))
        val lastImp = prefs.getLong(KEY_LAST_IMPORT_ALL, 0L).let { if (it > 0L) it else null }
        val lastSync = prefs.getLong(KEY_LAST_CZECH_SYNC, 0L).let { if (it > 0L) it else null }
        val dismissed = prefs.getStringSet(KEY_DISMISSED_MERCHANTS, emptySet())?.toSet() ?: emptySet()
        val lotsJson = prefs.getString(KEY_LOTS_JSON, "[]") ?: "[]"
        val merchJson = prefs.getString(KEY_MERCHANT_OVERRIDES_JSON, "{}") ?: "{}"
        val fxJson = prefs.getString(KEY_CNB_FX_JSON, "{}") ?: "{}"
        val bio = runCatching { prefs.getBoolean(KEY_BIOMETRIC_LOCK, false) }.getOrDefault(false)
        val secure = runCatching { prefs.getBoolean(KEY_FLAG_SECURE, false) }.getOrDefault(false)

        val balances = mutableMapOf<String, Double>()
        val bankTimes = mutableMapOf<String, Long>()
        for ((k, v) in prefs.all) {
            when {
                k.startsWith("bank_bal_") -> {
                    val bank = k.removePrefix("bank_bal_")
                    val num = when (v) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull()
                        else -> null
                    }
                    if (num != null && num.isFinite()) balances[bank] = num
                }
                k.startsWith("last_import_") && k != KEY_LAST_IMPORT_ALL -> {
                    val bank = k.removePrefix("last_import_")
                    val ts = (v as? Number)?.toLong() ?: (v as? String)?.toLongOrNull()
                    if (ts != null && ts > 0L) bankTimes[bank] = ts
                }
            }
        }

        return AppPreferencesSnapshot(
            themeMode = theme,
            lastImportTimestamp = lastImp,
            lastCzechSyncTimestamp = lastSync,
            dismissedSubscriptionMerchants = dismissed,
            bankBalances = balances,
            bankImportTimestamps = bankTimes,
            investmentLotsJson = lotsJson,
            merchantOverridesJson = merchJson,
            cnbFxRatesJson = fxJson,
            biometricLockEnabled = bio,
            flagSecureEnabled = secure,
            isHydrated = _snapshot.value.isHydrated
        )
    }

    suspend fun setThemeMode(mode: String) = updateOnIo {
        val safe = normalizeThemeMode(mode)
        prefs.edit { putString(KEY_THEME_MODE, safe) }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) = updateOnIo {
        prefs.edit { putBoolean(KEY_BIOMETRIC_LOCK, enabled) }
    }

    suspend fun setFlagSecureEnabled(enabled: Boolean) = updateOnIo {
        prefs.edit { putBoolean(KEY_FLAG_SECURE, enabled) }
    }

    suspend fun setLastImportTimestamp(bankName: String?, timestampMs: Long) = updateOnIo {
        prefs.edit {
            putLong(KEY_LAST_IMPORT_ALL, timestampMs)
            if (!bankName.isNullOrBlank()) {
                putLong("last_import_${bankName.uppercase(java.util.Locale.ROOT)}", timestampMs)
            }
        }
    }

    suspend fun setLastCzechSyncTimestamp(timestampMs: Long) = updateOnIo {
        prefs.edit { putLong(KEY_LAST_CZECH_SYNC, timestampMs) }
    }

    suspend fun setCnbFxRatesJson(jsonStr: String) = updateOnIo {
        prefs.edit { putString(KEY_CNB_FX_JSON, jsonStr) }
    }

    suspend fun setDismissedMerchants(keys: Set<String>) = updateOnIo {
        prefs.edit { putStringSet(KEY_DISMISSED_MERCHANTS, keys.toSet()) }
    }

    suspend fun putBankBalances(balances: Map<String, Double>) = updateOnIo {
        prefs.edit {
            for ((bank, bal) in balances) {
                if (bank.isNotBlank() && bal.isFinite()) {
                    putString("bank_bal_${bank.uppercase(java.util.Locale.ROOT)}", bal.toString())
                }
            }
        }
    }

    suspend fun removeBankBalances(bankNames: Set<String>) = updateOnIo {
        prefs.edit {
            for (bank in bankNames) {
                remove("bank_bal_${bank.uppercase(java.util.Locale.ROOT)}")
            }
        }
    }

    suspend fun clearBankBalancesForMonth(yearMonth: String) = updateOnIo {
        val prefix = "bank_bal_${yearMonth}_"
        prefs.edit {
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
        }
    }

    suspend fun removeBankBalanceForMonthBank(yearMonth: String, bankName: String) = updateOnIo {
        val exactKey = "bank_bal_${yearMonth}_${bankName.uppercase(java.util.Locale.ROOT)}"
        val rawKey = "bank_bal_${yearMonth}_$bankName"
        prefs.edit {
            remove(exactKey)
            remove(rawKey)
        }
    }

    suspend fun setInvestmentLotsJson(jsonStr: String) = updateOnIo {
        prefs.edit { putString(KEY_LOTS_JSON, jsonStr) }
    }

    suspend fun setMerchantOverridesJson(jsonStr: String?) = updateOnIo {
        prefs.edit {
            if (jsonStr.isNullOrBlank() || jsonStr.trim() == "{}") {
                remove(KEY_MERCHANT_OVERRIDES_JSON)
            } else {
                putString(KEY_MERCHANT_OVERRIDES_JSON, jsonStr)
            }
        }
    }

    suspend fun isFlagSet(key: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(prefsLock) {
            runCatching { prefs.getBoolean(key, false) }.getOrDefault(false)
        }
    }

    suspend fun markFlag(key: String, value: Boolean = true) = updateOnIo {
        prefs.edit { putBoolean(key, value) }
    }

    suspend fun exportPreferencesForBackup(): Map<String, String> = withContext(Dispatchers.IO) {
        synchronized(prefsLock) {
            val out = mutableMapOf<String, String>()
            val snap = readSnapshotLocked()
            if (snap.dismissedSubscriptionMerchants.isNotEmpty()) {
                out["dismissed_merchants_json"] = JSONArray(snap.dismissedSubscriptionMerchants.toList()).toString()
                out["dismissed_merchants"] = snap.dismissedSubscriptionMerchants.joinToString("|")
            }
            for ((bank, bal) in snap.bankBalances) {
                out["bank_bal_$bank"] = bal.toString()
            }
            out["theme_mode"] = snap.themeMode
            out["biometric_lock_enabled"] = snap.biometricLockEnabled.toString()
            out["flag_secure_enabled"] = snap.flagSecureEnabled.toString()
            out
        }
    }

    suspend fun restorePreferencesFromBackup(prefMap: Map<String, String>) = updateOnIo {
        prefs.edit {
            val restoredDismissed = mutableSetOf<String>()
            val dismissedJson = prefMap["dismissed_merchants_json"]
            if (!dismissedJson.isNullOrBlank()) {
                runCatching {
                    val arr = JSONArray(dismissedJson)
                    for (i in 0 until arr.length()) {
                        val v = arr.optString(i, "").trim()
                        if (v.isNotEmpty()) restoredDismissed.add(v)
                    }
                }
            } else {
                prefMap["dismissed_merchants"]?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
                    restoredDismissed.addAll(it)
                }
            }
            putStringSet(KEY_DISMISSED_MERCHANTS, restoredDismissed)

            val existingBalanceKeys = prefs.all.keys.filter { it.startsWith("bank_bal_") }
            existingBalanceKeys.forEach { remove(it) }
            prefMap.forEach { (k, v) ->
                if (k.startsWith("bank_bal_")) {
                    v.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { d ->
                        putString(k, d.toString())
                    }
                }
            }
            prefMap["theme_mode"]?.let {
                putString(KEY_THEME_MODE, normalizeThemeMode(it))
            }
            prefMap["biometric_lock_enabled"]?.toBooleanStrictOrNull()?.let {
                putBoolean(KEY_BIOMETRIC_LOCK, it)
            }
            prefMap["flag_secure_enabled"]?.toBooleanStrictOrNull()?.let {
                putBoolean(KEY_FLAG_SECURE, it)
            }
            // Mark one-time migrations complete so restored historical zero-balance entries aren't overwritten
            putBoolean("legacy_balances_backfilled", true)
            putBoolean("legacy_employer_repaired_v1", true)
        }
    }

    suspend fun clearAllUserData() = withContext(Dispatchers.IO) {
        synchronized(prefsLock) {
            prefs.edit(commit = true) { clear() }
            if (prefsName == DEFAULT_PREFS_NAME) {
                for (legacy in LEGACY_STORES) {
                    appContext.getSharedPreferences(legacy, Context.MODE_PRIVATE).edit(commit = true) { clear() }
                }
            }
            prefs.edit(commit = true) { putBoolean(KEY_LEGACY_MIGRATED_V1, true) }
        }
        _snapshot.value = AppPreferencesSnapshot(isHydrated = true)
    }

    private suspend fun updateOnIo(block: () -> Unit) = withContext(Dispatchers.IO) {
        val updated = synchronized(prefsLock) {
            block()
            readSnapshotLocked().copy(isHydrated = true)
        }
        _snapshot.value = updated
    }

    companion object {
        const val DEFAULT_PREFS_NAME = "app_user_prefs"
        private const val KEY_LEGACY_MIGRATED_V1 = "legacy_prefs_migrated_v1"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAST_IMPORT_ALL = "last_import_all"
        private const val KEY_LAST_CZECH_SYNC = "last_czech_sync_time"
        private const val KEY_DISMISSED_MERCHANTS = "dismissed_merchants"
        const val KEY_LOTS_JSON = "user_investment_lots_json"
        const val KEY_MERCHANT_OVERRIDES_JSON = "overrides_json"
        private const val KEY_CNB_FX_JSON = "cnb_fx_rates_json"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        private const val KEY_FLAG_SECURE = "flag_secure_enabled"

        private val MIGRATION_FLAG_KEYS = setOf(
            "legacy_employer_repaired_v1",
            "legacy_balances_backfilled",
            "merchant_rules_unified_v1"
        )

        fun normalizeThemeMode(raw: String?): String {
            return when (raw?.trim()?.uppercase(java.util.Locale.ROOT)) {
                "LIGHT" -> "LIGHT"
                "DARK" -> "DARK"
                else -> "SYSTEM"
            }
        }

        private val LEGACY_STORES = listOf(
            "statement_import_prefs",
            "subscription_auditor_prefs",
            "czech_sync_prefs",
            "investment_lots_prefs",
            "merchant_category_overrides",
            "app_preferences"
        )
    }
}
