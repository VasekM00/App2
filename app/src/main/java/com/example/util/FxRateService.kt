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
    const val DEFAULT_JPY_CZK_RATE: Double = 0.155
    const val DEFAULT_SEK_CZK_RATE: Double = 2.25
    const val DEFAULT_NOK_CZK_RATE: Double = 2.15
    const val DEFAULT_DKK_CZK_RATE: Double = 3.38
    const val DEFAULT_CAD_CZK_RATE: Double = 16.80
    const val DEFAULT_AUD_CZK_RATE: Double = 15.20
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
        "JPY" to DEFAULT_JPY_CZK_RATE,
        "SEK" to DEFAULT_SEK_CZK_RATE,
        "NOK" to DEFAULT_NOK_CZK_RATE,
        "DKK" to DEFAULT_DKK_CZK_RATE,
        "CAD" to DEFAULT_CAD_CZK_RATE,
        "AUD" to DEFAULT_AUD_CZK_RATE,
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
    private val JPY_PREFIX_REGEX = Regex("""^JPY(?:\s|[^A-Z]|$)""")
    private val SEK_PREFIX_REGEX = Regex("""^SEK(?:\s|[^A-Z]|$)""")
    private val NOK_PREFIX_REGEX = Regex("""^NOK(?:\s|[^A-Z]|$)""")
    private val DKK_PREFIX_REGEX = Regex("""^DKK(?:\s|[^A-Z]|$)""")
    private val CAD_PREFIX_REGEX = Regex("""^CAD(?:\s|[^A-Z]|$)""")
    private val AUD_PREFIX_REGEX = Regex("""^AUD(?:\s|[^A-Z]|$)""")
    private val CZK_PREFIX_REGEX = Regex("""^CZK(?:\s|[^A-Z]|$)""")

    // Avoid matching brand words like "Euro Pizza" / "Hotel Euro" — only match ISO code "EUR" or "€"
    private val EUR_DETECT_REGEX = Regex("""(?<![\p{L}])eur(?![\p{L}])""")
    private val USD_DETECT_REGEX = Regex("""(?<![\p{L}])usd(?![\p{L}])""")
    private val GBP_DETECT_REGEX = Regex("""(?<![\p{L}])gbp(?![\p{L}])""")
    private val PLN_DETECT_REGEX = Regex("""(?<![\p{L}])(?:pln|zł)(?![\p{L}])|(?<=\d\s{0,2})zl(?![\p{L}])""")
    private val CHF_DETECT_REGEX = Regex("""(?<![\p{L}])chf(?![\p{L}])""")
    private val HUF_DETECT_REGEX = Regex("""(?<![\p{L}])huf(?![\p{L}])|(?<=\d\s{0,2})ft(?![\p{L}])""")
    private val JPY_DETECT_REGEX = Regex("""(?<![\p{L}])jpy(?![\p{L}])""")
    private val SEK_DETECT_REGEX = Regex("""(?<![\p{L}])sek(?![\p{L}])""")
    private val NOK_DETECT_REGEX = Regex("""(?<![\p{L}])nok(?![\p{L}])""")
    private val DKK_DETECT_REGEX = Regex("""(?<![\p{L}])dkk(?![\p{L}])""")
    private val CAD_DETECT_REGEX = Regex("""(?<![\p{L}])(?:cad|ca?\$)""")
    private val AUD_DETECT_REGEX = Regex("""(?<![\p{L}])(?:aud|au?\$)""")
    private val CZK_DETECT_REGEX = Regex("""(?<![\p{L}])(?:czk|kč|kc)(?![\p{L}])""")

    data class CurrencyConversionDetails(
        val originalAmount: Double,
        val originalCurrency: String,
        val amountInCzk: Double,
        val exchangeRate: Double,
        val isUnknownCurrency: Boolean = false
    )

    /**
     * Retrieves the active conversion rate from the given currency to CZK, or null if unsupported.
     */
    fun getRateOrNull(currency: String): Double? {
        val norm = normalizeCurrencyCode(currency)
        if (norm.isBlank()) return null
        val snapshot = rates
        return snapshot[norm] ?: when (norm) {
            "EUR" -> DEFAULT_EUR_CZK_RATE
            "USD" -> DEFAULT_USD_CZK_RATE
            "GBP" -> DEFAULT_GBP_CZK_RATE
            "PLN" -> DEFAULT_PLN_CZK_RATE
            "CHF" -> DEFAULT_CHF_CZK_RATE
            "HUF" -> DEFAULT_HUF_CZK_RATE
            "JPY" -> DEFAULT_JPY_CZK_RATE
            "SEK" -> DEFAULT_SEK_CZK_RATE
            "NOK" -> DEFAULT_NOK_CZK_RATE
            "DKK" -> DEFAULT_DKK_CZK_RATE
            "CAD" -> DEFAULT_CAD_CZK_RATE
            "AUD" -> DEFAULT_AUD_CZK_RATE
            "CZK" -> DEFAULT_CZK_CZK_RATE
            else -> null
        }
    }

    fun isSupportedCurrency(currency: String): Boolean = getRateOrNull(currency) != null

    /**
     * Retrieves the active conversion rate from the given currency to CZK, or [Double.NaN] if unsupported.
     */
    fun getRate(currency: String): Double = getRateOrNull(currency) ?: Double.NaN

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
     * Normalizes raw currency text, symbols, or ISO codes into standard uppercase ISO-4217 codes.
     */
    fun normalizeCurrencyCode(raw: String): String {
        val trimmed = raw.trim().uppercase(Locale.ROOT)
        return when {
            trimmed == "EUR" || trimmed == "€" || EUR_PREFIX_REGEX.containsMatchIn(trimmed) -> "EUR"
            trimmed == "CAD" || trimmed == "C$" || trimmed == "CA$" || CAD_PREFIX_REGEX.containsMatchIn(trimmed) -> "CAD"
            trimmed == "AUD" || trimmed == "A$" || trimmed == "AU$" || AUD_PREFIX_REGEX.containsMatchIn(trimmed) -> "AUD"
            trimmed == "USD" || trimmed == "$" || trimmed == "US$" || USD_PREFIX_REGEX.containsMatchIn(trimmed) -> "USD"
            trimmed == "GBP" || trimmed == "£" || GBP_PREFIX_REGEX.containsMatchIn(trimmed) -> "GBP"
            trimmed == "PLN" || trimmed == "ZŁ" || trimmed == "ZL" || PLN_PREFIX_REGEX.containsMatchIn(trimmed) -> "PLN"
            trimmed == "CHF" || CHF_PREFIX_REGEX.containsMatchIn(trimmed) -> "CHF"
            trimmed == "HUF" || trimmed == "FT" || HUF_PREFIX_REGEX.containsMatchIn(trimmed) -> "HUF"
            trimmed == "JPY" || trimmed == "¥" || JPY_PREFIX_REGEX.containsMatchIn(trimmed) -> "JPY"
            trimmed == "SEK" || SEK_PREFIX_REGEX.containsMatchIn(trimmed) -> "SEK"
            trimmed == "NOK" || NOK_PREFIX_REGEX.containsMatchIn(trimmed) -> "NOK"
            trimmed == "DKK" || DKK_PREFIX_REGEX.containsMatchIn(trimmed) -> "DKK"
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
            CAD_DETECT_REGEX.containsMatchIn(lower) -> "CAD"
            AUD_DETECT_REGEX.containsMatchIn(lower) -> "AUD"
            lower.contains("$") || USD_DETECT_REGEX.containsMatchIn(lower) -> "USD"
            lower.contains("£") || GBP_DETECT_REGEX.containsMatchIn(lower) -> "GBP"
            PLN_DETECT_REGEX.containsMatchIn(lower) -> "PLN"
            CHF_DETECT_REGEX.containsMatchIn(lower) -> "CHF"
            HUF_DETECT_REGEX.containsMatchIn(lower) -> "HUF"
            lower.contains("¥") || JPY_DETECT_REGEX.containsMatchIn(lower) -> "JPY"
            SEK_DETECT_REGEX.containsMatchIn(lower) -> "SEK"
            NOK_DETECT_REGEX.containsMatchIn(lower) -> "NOK"
            DKK_DETECT_REGEX.containsMatchIn(lower) -> "DKK"
            else -> null
        }
    }

    /**
     * Converts a given amount in foreign currency to CZK, or null if the currency is unsupported.
     */
    fun convertToCzkOrNull(amount: Double, currency: String, customRate: Double? = null): Double? {
        if (!amount.isFinite()) return null
        val norm = normalizeCurrencyCode(currency)
        val rate = customRate?.takeIf { it.isFinite() && it > 0.0 } ?: getRateOrNull(norm) ?: return null
        val converted = amount * rate
        return converted.takeIf { it.isFinite() }
    }

    /**
     * Converts a given amount in foreign currency to CZK, returning [Double.NaN] for unknown currencies.
     */
    fun convertToCzk(amount: Double, currency: String, customRate: Double? = null): Double {
        if (!amount.isFinite()) return 0.0
        return convertToCzkOrNull(amount, currency, customRate) ?: Double.NaN
    }

    /**
     * Converts an amount between any two supported currencies. Returns [Double.NaN] if either currency is unknown.
     */
    fun convert(amount: Double, fromCurrency: String, toCurrency: String = "CZK"): Double {
        val safeAmount = if (amount.isFinite()) amount else 0.0
        val fromNorm = normalizeCurrencyCode(fromCurrency)
        val toNorm = normalizeCurrencyCode(toCurrency)
        if (fromNorm == toNorm) return safeAmount

        val fromRate = getRateOrNull(fromNorm) ?: return Double.NaN
        val toRate = getRateOrNull(toNorm) ?: return Double.NaN
        val czkAmount = safeAmount * fromRate
        val converted = if (toRate.isFinite() && toRate > 0.0) czkAmount / toRate else return Double.NaN
        return if (converted.isFinite()) converted else Double.NaN
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
     * Atomically updates multiple ČNB currency rates to CZK.
     */
    fun updateRates(newRates: Map<String, Double>) {
        if (newRates.isEmpty()) return
        synchronized(lock) {
            val updated = rates.toMutableMap()
            var changed = false
            for ((rawCode, rate) in newRates) {
                val norm = normalizeCurrencyCode(rawCode)
                if (norm.isNotBlank() && rate.isFinite() && rate > 0.0) {
                    updated[norm] = rate
                    changed = true
                }
            }
            if (changed) {
                rates = updated.toMap()
            }
        }
    }

    /**
     * Parses ČNB official exchange rate table (either JSON or pipe-delimited daily TXT format).
     */
    fun parseCnbDailyRates(content: String): Boolean {
        if (content.isBlank() || content.length > MAX_CNB_CONTENT_CHARS) return false

        val supportedCodes = defaultRateMap().keys
        val parsedRates = linkedMapOf<String, Double>()

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
                        if (code !in supportedCodes || code == "CZK") continue
                        val rawRate = item.optDouble("rate", Double.NaN)
                        val amount = if (item.has("amount") && !item.isNull("amount")) {
                            item.optDouble("amount", Double.NaN)
                        } else {
                            1.0
                        }
                        if (rawRate.isFinite() && rawRate > 0.0 && amount.isFinite() && amount > 0.0) {
                            val unitRate = rawRate / amount
                            if (unitRate.isFinite() && unitRate > 0.0) {
                                parsedRates[code] = unitRate
                            }
                        }
                    }
                }
                if (parsedRates.isNotEmpty()) {
                    updateRates(parsedRates)
                    return true
                }
            } catch (_: Exception) {
                // Fall through to TXT parsing
            }
        }

        // 2. Try parsing ČNB daily TXT format (lines with "|EUR|", "|USD|", etc.)
        // e.g.: "EMU|euro|1|EUR|25,200"
        try {
            val lines = content.lines()
            for (line in lines) {
                val tokens = line.split("|")
                if (tokens.size >= 5) {
                    val code = tokens[3].trim().uppercase(Locale.ROOT)
                    if (code !in supportedCodes || code == "CZK") continue
                    val amount = tokens[2].trim().replace(',', '.').toDoubleOrNull() ?: continue
                    val rawRate = tokens[4].trim().replace(',', '.').toDoubleOrNull() ?: continue
                    if (!amount.isFinite() || amount <= 0.0 || !rawRate.isFinite() || rawRate <= 0.0) continue
                    val unitRate = rawRate / amount
                    if (!unitRate.isFinite() || unitRate <= 0.0) continue
                    parsedRates[code] = unitRate
                }
            }
        } catch (_: Exception) {
            // Return parsed status if anything succeeded before failure
        }

        if (parsedRates.isNotEmpty()) {
            updateRates(parsedRates)
            return true
        }
        return false
    }
}
