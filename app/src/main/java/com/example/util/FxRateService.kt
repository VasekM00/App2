package com.example.util

import org.json.JSONObject
import java.util.Locale

/**
 * Multi-currency transaction engine supporting EUR, USD, and CZK.
 *
 * Preloaded with official Czech National Bank (ČNB) benchmark reference rates
 * (25.20 CZK/EUR, 23.50 CZK/USD) and supports custom user overrides or live synced rates.
 * Extends bank statement parsing to detect currency symbols and ISO codes and convert
 * them to standard CZK while preserving original foreign currency details.
 */
object FxRateService {

    const val DEFAULT_EUR_CZK_RATE: Double = 25.20
    const val DEFAULT_USD_CZK_RATE: Double = 23.50
    const val DEFAULT_GBP_CZK_RATE: Double = 29.80
    const val DEFAULT_PLN_CZK_RATE: Double = 5.90
    const val DEFAULT_CHF_CZK_RATE: Double = 26.90
    const val DEFAULT_HUF_CZK_RATE: Double = 0.063
    const val DEFAULT_CZK_CZK_RATE: Double = 1.00

    private const val MAX_CNB_CONTENT_CHARS = 1_000_000

    private val lock = Any()

    private fun defaultRateMap(): Map<String, Double> = mapOf(
        "EUR" to DEFAULT_EUR_CZK_RATE,
        "USD" to DEFAULT_USD_CZK_RATE,
        "GBP" to DEFAULT_GBP_CZK_RATE,
        "PLN" to DEFAULT_PLN_CZK_RATE,
        "CHF" to DEFAULT_CHF_CZK_RATE,
        "HUF" to DEFAULT_HUF_CZK_RATE,
        "CZK" to DEFAULT_CZK_CZK_RATE
    )

    @Volatile
    private var rates: Map<String, Double> = defaultRateMap()

    private val EUR_PREFIX_REGEX = Regex("""^EUR(?:O)?(?:\s|[^A-Z]|$)""")
    private val USD_PREFIX_REGEX = Regex("""^USD(?:\s|[^A-Z]|$)""")
    private val GBP_PREFIX_REGEX = Regex("""^GBP(?:\s|[^A-Z]|$)""")
    private val PLN_PREFIX_REGEX = Regex("""^PLN(?:\s|[^A-Z]|$)""")
    private val CHF_PREFIX_REGEX = Regex("""^CHF(?:\s|[^A-Z]|$)""")
    private val HUF_PREFIX_REGEX = Regex("""^HUF(?:\s|[^A-Z]|$)""")
    private val CZK_PREFIX_REGEX = Regex("""^CZK(?:\s|[^A-Z]|$)""")

    // Avoid matching brand words like "Euro Pizza" / "Hotel Euro" — only match ISO code "EUR" or "€"
    private val EUR_DETECT_REGEX = Regex("""(?<![\p{L}])eur(?![\p{L}])""")
    private val USD_DETECT_REGEX = Regex("""(?<![\p{L}])usd(?![\p{L}])""")
    private val GBP_DETECT_REGEX = Regex("""(?<![\p{L}])gbp(?![\p{L}])""")
    private val PLN_DETECT_REGEX = Regex("""(?<![\p{L}])(?:pln|zł)(?![\p{L}])|(?<=\d\s{0,2})zl(?![\p{L}])""")
    private val CHF_DETECT_REGEX = Regex("""(?<![\p{L}])chf(?![\p{L}])""")
    private val HUF_DETECT_REGEX = Regex("""(?<![\p{L}])huf(?![\p{L}])|(?<=\d\s{0,2})ft(?![\p{L}])""")
    private val CZK_DETECT_REGEX = Regex("""(?<![\p{L}])(?:czk|kč|kc)(?![\p{L}])""")

    data class CurrencyConversionDetails(
        val originalAmount: Double,
        val originalCurrency: String,
        val amountInCzk: Double,
        val exchangeRate: Double
    )

    /**
     * Retrieves the active conversion rate from the given currency to CZK.
     */
    fun getRate(currency: String): Double {
        val norm = normalizeCurrencyCode(currency)
        val snapshot = rates
        return snapshot[norm] ?: when (norm) {
            "EUR" -> DEFAULT_EUR_CZK_RATE
            "USD" -> DEFAULT_USD_CZK_RATE
            "GBP" -> DEFAULT_GBP_CZK_RATE
            "PLN" -> DEFAULT_PLN_CZK_RATE
            "CHF" -> DEFAULT_CHF_CZK_RATE
            "HUF" -> DEFAULT_HUF_CZK_RATE
            "CZK" -> DEFAULT_CZK_CZK_RATE
            else -> 1.00
        }
    }

    /**
     * Sets a custom or updated conversion rate for a currency against CZK.
     */
    fun setRate(currency: String, rateToCzk: Double) {
        require(rateToCzk.isFinite() && rateToCzk > 0.0) { "Exchange rate must be positive: $rateToCzk" }
        val norm = normalizeCurrencyCode(currency)
        require(norm.isNotBlank()) { "Currency code must not be blank" }
        synchronized(lock) {
            rates = rates.toMutableMap().apply { put(norm, rateToCzk) }.toMap()
        }
    }

    /**
     * Resets exchange rates to default ČNB statutory benchmark reference rates.
     */
    fun resetRates() {
        synchronized(lock) {
            rates = defaultRateMap()
        }
    }

    /**
     * Normalizes raw currency text, symbols, or ISO codes into standard uppercase codes:
     * "EUR", "USD", or "CZK".
     */
    fun normalizeCurrencyCode(raw: String): String {
        val trimmed = raw.trim().uppercase(Locale.ROOT)
        return when {
            trimmed == "EUR" || trimmed == "€" || EUR_PREFIX_REGEX.containsMatchIn(trimmed) -> "EUR"
            trimmed == "USD" || trimmed == "$" || trimmed == "US$" || USD_PREFIX_REGEX.containsMatchIn(trimmed) -> "USD"
            trimmed == "GBP" || trimmed == "£" || GBP_PREFIX_REGEX.containsMatchIn(trimmed) -> "GBP"
            trimmed == "PLN" || trimmed == "ZŁ" || trimmed == "ZL" || PLN_PREFIX_REGEX.containsMatchIn(trimmed) -> "PLN"
            trimmed == "CHF" || CHF_PREFIX_REGEX.containsMatchIn(trimmed) -> "CHF"
            trimmed == "HUF" || trimmed == "FT" || HUF_PREFIX_REGEX.containsMatchIn(trimmed) -> "HUF"
            trimmed == "CZK" || trimmed == "KČ" || trimmed == "KC" || CZK_PREFIX_REGEX.containsMatchIn(trimmed) -> "CZK"
            else -> trimmed
        }
    }

    /**
     * Inspects arbitrary text (e.g. statement description or header token) to detect currency code/symbol.
     * Prioritizes CZK when explicit CZK/Kč appears alongside foreign-currency original-amount notes.
     */
    fun detectCurrency(text: String): String? {
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.ROOT)
        if (CZK_DETECT_REGEX.containsMatchIn(lower)) return "CZK"
        return when {
            lower.contains("€") || EUR_DETECT_REGEX.containsMatchIn(lower) -> "EUR"
            lower.contains("$") || USD_DETECT_REGEX.containsMatchIn(lower) -> "USD"
            lower.contains("£") || GBP_DETECT_REGEX.containsMatchIn(lower) -> "GBP"
            PLN_DETECT_REGEX.containsMatchIn(lower) -> "PLN"
            CHF_DETECT_REGEX.containsMatchIn(lower) -> "CHF"
            HUF_DETECT_REGEX.containsMatchIn(lower) -> "HUF"
            else -> null
        }
    }

    /**
     * Converts a given amount in foreign currency to CZK.
     */
    fun convertToCzk(amount: Double, currency: String, customRate: Double? = null): Double {
        val safeAmount = if (amount.isFinite()) amount else 0.0
        val norm = normalizeCurrencyCode(currency)
        val rate = customRate?.takeIf { it.isFinite() && it > 0.0 } ?: getRate(norm)
        val converted = safeAmount * rate
        return if (converted.isFinite()) converted else 0.0
    }

    /**
     * Converts an amount between any two supported currencies (EUR, USD, CZK).
     */
    fun convert(amount: Double, fromCurrency: String, toCurrency: String = "CZK"): Double {
        val safeAmount = if (amount.isFinite()) amount else 0.0
        val fromNorm = normalizeCurrencyCode(fromCurrency)
        val toNorm = normalizeCurrencyCode(toCurrency)
        if (fromNorm == toNorm) return safeAmount

        val czkAmount = safeAmount * getRate(fromNorm)
        val toRate = getRate(toNorm)
        val converted = if (toRate.isFinite() && toRate > 0.0) czkAmount / toRate else czkAmount
        return if (converted.isFinite()) converted else 0.0
    }

    /**
     * Updates rates directly from ČNB sync.
     */
    fun updateFromCnb(eurRate: Double, usdRate: Double) {
        val eurValid = eurRate.isFinite() && eurRate > 0.0
        val usdValid = usdRate.isFinite() && usdRate > 0.0
        if (!eurValid && !usdValid) return
        synchronized(lock) {
            rates = rates.toMutableMap().apply {
                if (eurValid) put("EUR", eurRate)
                if (usdValid) put("USD", usdRate)
            }.toMap()
        }
    }

    /**
     * Parses ČNB official exchange rate table (either JSON or pipe-delimited daily TXT format).
     */
    fun parseCnbDailyRates(content: String): Boolean {
        if (content.isBlank() || content.length > MAX_CNB_CONTENT_CHARS) return false

        var parsedEur: Double? = null
        var parsedUsd: Double? = null

        // 1. Try parsing ČNB JSON API format: {"rates": [{"currencyCode":"EUR","rate":25.20}, ...]}
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            try {
                val json = JSONObject(trimmed)
                val ratesArray = json.optJSONArray("rates")
                if (ratesArray != null) {
                    for (i in 0 until ratesArray.length()) {
                        val item = ratesArray.optJSONObject(i) ?: continue
                        val code = item.optString("currencyCode", "").trim().uppercase(Locale.ROOT)
                        val rawRate = item.optDouble("rate", Double.NaN)
                        val amount = if (item.has("amount") && !item.isNull("amount")) {
                            item.optDouble("amount", Double.NaN)
                        } else {
                            1.0
                        }
                        if (rawRate.isFinite() && rawRate > 0.0 && amount.isFinite() && amount > 0.0) {
                            val unitRate = rawRate / amount
                            if (unitRate.isFinite() && unitRate > 0.0) {
                                if (code == "EUR") parsedEur = unitRate
                                if (code == "USD") parsedUsd = unitRate
                            }
                        }
                    }
                }
                if (parsedEur != null || parsedUsd != null) {
                    applyCnbRatesAtomic(parsedEur, parsedUsd)
                    return true
                }
            } catch (_: Exception) {
                // Fall through to TXT parsing
            }
        }

        // 2. Try parsing ČNB daily TXT format (lines with "|EUR|" and "|USD|")
        // e.g.: "EMU|euro|1|EUR|25,200"
        try {
            val lines = content.lines()
            for (line in lines) {
                val tokens = line.split("|")
                if (tokens.size >= 5) {
                    val code = tokens[3].trim().uppercase(Locale.ROOT)
                    val amount = tokens[2].trim().replace(',', '.').toDoubleOrNull() ?: continue
                    val rawRate = tokens[4].trim().replace(',', '.').toDoubleOrNull() ?: continue
                    if (!amount.isFinite() || amount <= 0.0 || !rawRate.isFinite() || rawRate <= 0.0) continue
                    val unitRate = rawRate / amount
                    if (!unitRate.isFinite() || unitRate <= 0.0) continue
                    if (code == "EUR") parsedEur = unitRate
                    if (code == "USD") parsedUsd = unitRate
                }
            }
        } catch (_: Exception) {
            // Return parsed status if anything succeeded before failure
        }

        if (parsedEur != null || parsedUsd != null) {
            applyCnbRatesAtomic(parsedEur, parsedUsd)
            return true
        }
        return false
    }

    private fun applyCnbRatesAtomic(eurRate: Double?, usdRate: Double?) {
        synchronized(lock) {
            rates = rates.toMutableMap().apply {
                if (eurRate != null && eurRate.isFinite() && eurRate > 0.0) put("EUR", eurRate)
                if (usdRate != null && usdRate.isFinite() && usdRate > 0.0) put("USD", usdRate)
            }.toMap()
        }
    }
}
