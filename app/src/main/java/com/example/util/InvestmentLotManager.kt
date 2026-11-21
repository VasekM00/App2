package com.example.util

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

private val PRAGUE_ZONE: ZoneId = ZoneId.of("Europe/Prague")
private const val MAX_LOTS_JSON_CHARS = 1_000_000

/**
 * Model representing a user-recorded investment tranche for the Czech 3-year time-test
 * under § 4 odst. 1 písm. u) of Act No. 586/1992 Coll. (ZDP).
 */
data class UserInvestmentLot(
    val id: String = UUID.randomUUID().toString(),
    val batchName: String,
    val instrument: String,
    val purchaseDateStr: String, // "YYYY-MM" or "YYYY-MM-DD"
    val acquisitionCostCzk: Double,
    val currentValueCzk: Double,
    val notes: String = ""
) {
    fun parsedPurchaseDateOrNull(): LocalDate? {
        val raw = purchaseDateStr.trim()
        if (raw.isEmpty()) return null
        return try {
            when {
                raw.length == 7 -> YearMonth.parse(raw).atEndOfMonth()
                else -> LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
            }
        } catch (_: Exception) {
            null
        }
    }

    val isPurchaseDateValid: Boolean
        get() = parsedPurchaseDateOrNull() != null

    val purchaseDate: LocalDate
        get() = parsedPurchaseDateOrNull() ?: LocalDate.now(PRAGUE_ZONE)

    fun monthsHeld(asOfDate: LocalDate = LocalDate.now(PRAGUE_ZONE)): Int {
        val parsed = parsedPurchaseDateOrNull() ?: return 0
        if (asOfDate.isBefore(parsed)) return 0
        return ChronoUnit.MONTHS.between(parsed, asOfDate).coerceIn(0L, 12_000L).toInt()
    }

    // Czech Income Taxes Act (ZDP § 4(1)(u)): holding period strictly exceeds 3 years.
    fun isExempt(asOfDate: LocalDate = LocalDate.now(PRAGUE_ZONE)): Boolean {
        val parsed = parsedPurchaseDateOrNull() ?: return false
        return asOfDate.isAfter(parsed.plusYears(3))
    }

    fun monthsRemaining(asOfDate: LocalDate = LocalDate.now(PRAGUE_ZONE)): Int {
        if (isExempt(asOfDate)) return 0
        return (36 - monthsHeld(asOfDate)).coerceAtLeast(1)
    }

    fun progress(asOfDate: LocalDate = LocalDate.now(PRAGUE_ZONE)): Float {
        if (isExempt(asOfDate)) return 1f
        return (monthsHeld(asOfDate) / 36f).coerceIn(0f, 0.99f)
    }

    val unrealizedGainCzk: Double
        get() {
            val cur = if (currentValueCzk.isFinite()) currentValueCzk else 0.0
            val acq = if (acquisitionCostCzk.isFinite()) acquisitionCostCzk else 0.0
            return cur - acq
        }

    val unrealizedGainPct: Double
        get() {
            val acq = if (acquisitionCostCzk.isFinite()) acquisitionCostCzk else 0.0
            return if (acq > 0.0) (unrealizedGainCzk / acq) * 100.0 else 0.0
        }

    fun taxSavedCzk(taxRatePct: Double = 15.0, asOfDate: LocalDate = LocalDate.now(PRAGUE_ZONE)): Double {
        if (!isExempt(asOfDate)) return 0.0
        val safeRate = if (taxRatePct.isFinite()) taxRatePct.coerceIn(0.0, 100.0) else 0.0
        return unrealizedGainCzk.coerceAtLeast(0.0) * (safeRate / 100.0)
    }

    fun toDomainLot(): com.example.domain.InvestmentLot {
        return com.example.domain.InvestmentLot(
            id = id,
            ticker = instrument.ifBlank { batchName.ifBlank { "LOT" } },
            purchaseDate = purchaseDate,
            units = 1.0,
            acquisitionPricePerUnitCzk = if (acquisitionCostCzk.isFinite()) acquisitionCostCzk else 0.0,
            currentPricePerUnitCzk = if (currentValueCzk.isFinite()) currentValueCzk else 0.0,
            notes = notes
        )
    }
}

/**
 * Manages persistent user investment lots in SharedPreferences using MODE_PRIVATE.
 */
object InvestmentLotManager {
    private const val PREFS_NAME = "investment_lots_prefs"
    private const val KEY_LOTS_JSON = "user_investment_lots_json"
    private val prefsLock = Any()

    private fun safeFinite(value: Double): Double = if (value.isFinite()) value else 0.0

    private fun sanitizeLot(lot: UserInvestmentLot): UserInvestmentLot = lot.copy(
        id = lot.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
        acquisitionCostCzk = safeFinite(lot.acquisitionCostCzk),
        currentValueCzk = safeFinite(lot.currentValueCzk)
    )

    private fun optStringNonNull(obj: JSONObject, key: String, fallback: String = ""): String {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        return obj.opt(key)?.toString() ?: fallback
    }

    private fun optFiniteDouble(obj: JSONObject, key: String, fallback: Double = 0.0): Double {
        if (!obj.has(key) || obj.isNull(key)) return fallback
        val raw = obj.opt(key)
        val parsed = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull()
            else -> null
        }
        return if (parsed != null && parsed.isFinite()) parsed else fallback
    }

    fun getLots(context: Context): List<UserInvestmentLot> = synchronized(prefsLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = try {
            prefs.getString(KEY_LOTS_JSON, null)
        } catch (_: ClassCastException) {
            null
        } ?: return emptyList()
        return parseLotsFromJson(jsonStr)
    }

    fun saveLot(context: Context, lot: UserInvestmentLot) = synchronized(prefsLock) {
        val safeLot = sanitizeLot(lot)
        val current = getLots(context).toMutableList()
        val index = current.indexOfFirst { it.id == safeLot.id }
        if (index != -1) {
            current[index] = safeLot
        } else {
            current.add(safeLot)
        }
        persistLots(context, current)
    }

    fun deleteLot(context: Context, lotId: String) = synchronized(prefsLock) {
        val current = getLots(context).filterNot { it.id == lotId }
        persistLots(context, current)
    }

    fun persistLots(context: Context, lots: List<UserInvestmentLot>) = synchronized(prefsLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_LOTS_JSON, serializeLotsToJson(lots.map(::sanitizeLot))) }
    }

    fun clearAll(context: Context) = synchronized(prefsLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { clear() }
    }

    fun parseLotsFromJson(jsonStr: String): List<UserInvestmentLot> {
        if (jsonStr.isBlank() || jsonStr == "[]" || jsonStr.length > MAX_LOTS_JSON_CHARS) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<UserInvestmentLot>()
            val defaultDate = YearMonth.now(PRAGUE_ZONE).toString()
            for (i in 0 until array.length()) {
                try {
                    val obj = array.optJSONObject(i) ?: continue
                    val batchName = optStringNonNull(obj, "batchName", "")
                    val instrument = optStringNonNull(obj, "instrument", "")
                    val purchaseDateStr = optStringNonNull(obj, "purchaseDateStr", defaultDate)
                        .ifBlank { defaultDate }
                    val rawId = optStringNonNull(obj, "id", "").trim()
                    val resolvedId = rawId.ifBlank {
                        UUID.nameUUIDFromBytes(
                            "lot:$i|$batchName|$instrument|$purchaseDateStr".toByteArray(Charsets.UTF_8)
                        ).toString()
                    }
                    list.add(
                        UserInvestmentLot(
                            id = resolvedId,
                            batchName = batchName,
                            instrument = instrument,
                            purchaseDateStr = purchaseDateStr,
                            acquisitionCostCzk = optFiniteDouble(obj, "acquisitionCostCzk", 0.0),
                            currentValueCzk = optFiniteDouble(obj, "currentValueCzk", 0.0),
                            notes = optStringNonNull(obj, "notes", "")
                        )
                    )
                } catch (_: Exception) {
                    // Skip malformed entry while preserving remaining valid lots
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serializeLotsToJson(lots: List<UserInvestmentLot>): String {
        val array = JSONArray()
        for (lot in lots) {
            val obj = JSONObject()
            obj.put("id", lot.id)
            obj.put("batchName", lot.batchName)
            obj.put("instrument", lot.instrument)
            obj.put("purchaseDateStr", lot.purchaseDateStr)
            obj.put("acquisitionCostCzk", safeFinite(lot.acquisitionCostCzk))
            obj.put("currentValueCzk", safeFinite(lot.currentValueCzk))
            obj.put("notes", lot.notes)
            array.put(obj)
        }
        return array.toString()
    }
}
