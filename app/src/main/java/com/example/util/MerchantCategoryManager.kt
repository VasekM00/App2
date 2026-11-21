package com.example.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject
import java.util.Locale

/**
 * Manages persistent user-defined merchant category overrides stored in SharedPreferences.
 * Allows users to manually assign or correct a merchant's category once,
 * which will automatically be remembered and applied to future statement imports.
 */
object MerchantCategoryManager {

    private const val PREFS_NAME = com.example.data.AppPreferencesRepository.DEFAULT_PREFS_NAME
    private const val LEGACY_PREFS_NAME = "merchant_category_overrides"
    private const val KEY_OVERRIDES_JSON = com.example.data.AppPreferencesRepository.KEY_MERCHANT_OVERRIDES_JSON
    private const val MAX_RULES_JSON_CHARS = 2_000_000
    private val prefsLock = Any()

    fun canonicalRuleKey(merchantPattern: String): String =
        CzechMerchantCatalog.normalizeSimple(merchantPattern)
            .ifBlank { merchantPattern.trim().lowercase(Locale.ROOT) }

    fun parseRulesFromJson(jsonStr: String): Map<String, BankTransactionType> {
        if (jsonStr.isBlank() || jsonStr.length > MAX_RULES_JSON_CHARS) return emptyMap()
        val map = linkedMapOf<String, BankTransactionType>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.isBlank() || json.isNull(key)) continue
                val catName = json.optString(key, "").trim()
                if (catName.isEmpty()) continue
                try {
                    val cat = BankTransactionType.valueOf(catName)
                    map[key] = cat
                } catch (_: IllegalArgumentException) {
                    // Ignore unrecognized categories
                }
            }
        } catch (_: Exception) {
            // Safe fallback on corrupted JSON
        }
        return map
    }

    fun serializeRulesToJson(rules: Map<String, BankTransactionType>): String {
        val json = JSONObject()
        for ((k, v) in rules) {
            if (k.isNotBlank()) {
                json.put(k, v.name)
            }
        }
        return json.toString()
    }

    private fun readOverridesLocked(prefs: SharedPreferences): Map<String, BankTransactionType> {
        val jsonStr = prefs.getString(KEY_OVERRIDES_JSON, null) ?: return emptyMap()
        return parseRulesFromJson(jsonStr)
    }

    fun getOverrides(context: Context, prefsName: String = PREFS_NAME): Map<String, BankTransactionType> = synchronized(prefsLock) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_OVERRIDES_JSON)) {
            readOverridesLocked(prefs)
        } else if (prefsName == PREFS_NAME) {
            val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            readOverridesLocked(legacy)
        } else {
            emptyMap()
        }
    }

    fun saveOverride(
        context: Context,
        merchantPattern: String,
        category: BankTransactionType,
        prefsName: String = PREFS_NAME
    ) {
        val cleanPattern = canonicalRuleKey(merchantPattern)
        if (cleanPattern.isBlank()) return

        synchronized(prefsLock) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val currentMap = readOverridesLocked(prefs).toMutableMap()
            currentMap[cleanPattern] = category
            prefs.edit { putString(KEY_OVERRIDES_JSON, serializeRulesToJson(currentMap)) }
        }
    }

    fun saveAllOverrides(
        context: Context,
        overrides: Map<String, BankTransactionType>,
        prefsName: String = PREFS_NAME
    ) {
        val normalized = linkedMapOf<String, BankTransactionType>()
        for ((pattern, category) in overrides) {
            val cleanPattern = canonicalRuleKey(pattern)
            if (cleanPattern.isNotBlank()) {
                normalized[cleanPattern] = category
            }
        }
        synchronized(prefsLock) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit {
                if (normalized.isEmpty()) {
                    remove(KEY_OVERRIDES_JSON)
                } else {
                    putString(KEY_OVERRIDES_JSON, serializeRulesToJson(normalized))
                }
            }
        }
    }

    fun removeOverride(context: Context, merchantPattern: String, prefsName: String = PREFS_NAME) {
        val cleanPattern = canonicalRuleKey(merchantPattern)
        val rawLower = merchantPattern.trim().lowercase(Locale.ROOT)
        synchronized(prefsLock) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val currentMap = readOverridesLocked(prefs).toMutableMap()
            if (cleanPattern.isNotBlank()) {
                currentMap.remove(cleanPattern)
            }
            if (rawLower.isNotBlank()) {
                currentMap.remove(rawLower)
            }
            if (cleanPattern.isNotBlank()) {
                currentMap.keys.removeAll { existing -> canonicalRuleKey(existing) == cleanPattern }
            }
            prefs.edit {
                if (currentMap.isEmpty()) {
                    remove(KEY_OVERRIDES_JSON)
                } else {
                    putString(KEY_OVERRIDES_JSON, serializeRulesToJson(currentMap))
                }
            }
        }
    }

    fun clearOverrides(context: Context, prefsName: String = PREFS_NAME) {
        synchronized(prefsLock) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit { remove(KEY_OVERRIDES_JSON) }
        }
    }
}
