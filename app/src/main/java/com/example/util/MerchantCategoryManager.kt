package com.example.util

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Manages persistent user-defined merchant category overrides stored in SharedPreferences.
 * Allows users to manually assign or correct a merchant's category once,
 * which will automatically be remembered and applied to future statement imports.
 */
object MerchantCategoryManager {

    private const val PREFS_NAME = "merchant_category_overrides"
    private const val KEY_OVERRIDES_JSON = "overrides_json"

    fun parseRulesFromJson(jsonStr: String): Map<String, BankTransactionType> {
        if (jsonStr.isBlank()) return emptyMap()
        val map = mutableMapOf<String, BankTransactionType>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val catName = json.optString(key)
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
            json.put(k, v.name)
        }
        return json.toString()
    }

    fun getOverrides(context: Context): Map<String, BankTransactionType> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_OVERRIDES_JSON, null) ?: return emptyMap()
        return parseRulesFromJson(jsonStr)
    }

    fun saveOverride(context: Context, merchantPattern: String, category: BankTransactionType) {
        val cleanPattern = merchantPattern.trim().lowercase()
        if (cleanPattern.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentMap = getOverrides(context).toMutableMap()
        currentMap[cleanPattern] = category

        val json = JSONObject()
        for ((k, v) in currentMap) {
            json.put(k, v.name)
        }
        prefs.edit { putString(KEY_OVERRIDES_JSON, json.toString()) }
    }

    fun removeOverride(context: Context, merchantPattern: String) {
        val cleanPattern = merchantPattern.trim().lowercase()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentMap = getOverrides(context).toMutableMap()
        currentMap.remove(cleanPattern)

        val json = JSONObject()
        for ((k, v) in currentMap) {
            json.put(k, v.name)
        }
        prefs.edit { putString(KEY_OVERRIDES_JSON, json.toString()) }
    }

    fun clearOverrides(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_OVERRIDES_JSON) }
    }
}
