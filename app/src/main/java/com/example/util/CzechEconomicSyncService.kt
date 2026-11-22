package com.example.util

import com.example.data.SettingsEntity
import com.example.domain.CzechRegulatoryData
import com.example.domain.SyncDifferenceItem
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

object CzechEconomicSyncService {

    private const val CNB_DAILY_RATES_URL = "https://api.cnb.cz/cnbapi/exrates/daily?lang=EN"
    private const val CNB_TXT_FALLBACK_URL = "https://www.cnb.cz/cs/financni-trhy/devizovy-trh/kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/denni_kurz.txt"
    private const val CNB_CACHE_TTL_MS = 15L * 60L * 1000L
    private const val MAX_CNB_PAYLOAD_CHARS = 1_000_000
    private const val MAX_REDIRECTS = 3

    private val syncMutex = Mutex()
    private val ALLOWED_CNB_HOSTS = setOf("api.cnb.cz", "www.cnb.cz", "cnb.cz")

    private val EUR_KEY_VALUE_REGEX = Regex(""""currencyCode"\s*:\s*"EUR"[^}]*?"rate"\s*:\s*([0-9.]+)""")
    private val EUR_VALUE_KEY_REGEX = Regex(""""rate"\s*:\s*([0-9.]+)[^}]*?"currencyCode"\s*:\s*"EUR"""")
    private val USD_KEY_VALUE_REGEX = Regex(""""currencyCode"\s*:\s*"USD"[^}]*?"rate"\s*:\s*([0-9.]+)""")
    private val USD_VALUE_KEY_REGEX = Regex(""""rate"\s*:\s*([0-9.]+)[^}]*?"currencyCode"\s*:\s*"USD"""")
    private val VALID_FOR_REGEX = Regex(""""validFor"\s*:\s*"([^"]+)"""")
    private val CNB_TXT_DATE_REGEX = Regex("""^(\d{2}\.\d{2}\.\d{4})\b""")

    private data class CachedCnbResult(
        val data: CzechRegulatoryData,
        val fetchedAtMs: Long
    )

    internal data class ParsedCnbRates(
        val rates: Map<String, Double>,
        val rateDate: String
    )

    private val SUPPORTED_CNB_CURRENCIES = setOf("EUR", "USD", "GBP", "PLN", "CHF", "HUF")

    @Volatile
    private var cachedCnbSuccess: CachedCnbResult? = null

    /**
     * Fetches live official Czech National Bank (ČNB) daily exchange rates and
     * couples them with the locally bundled 2026 Czech statutory baseline (Act No. 586/1992 Coll. ZDP).
     * Guaranteed to return a valid CzechRegulatoryData object with zero third-party dependencies.
     */
    suspend fun fetchLiveRegulatoryData(): CzechRegulatoryData = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        cachedCnbSuccess?.let { cached ->
            val ageMs = nowMs - cached.fetchedAtMs
            if (ageMs in 0L until CNB_CACHE_TTL_MS) {
                return@withContext cached.data
            }
        }

        syncMutex.withLock {
            val lockedNowMs = System.currentTimeMillis()
            cachedCnbSuccess?.let { cached ->
                val ageMs = lockedNowMs - cached.fetchedAtMs
                if (ageMs in 0L until CNB_CACHE_TTL_MS) {
                    return@withLock cached.data
                }
            }

            val parsed = localDefaults()
            val cnbParsed = withTimeoutOrNull(15_000L) {
                fetchCnbAllRates()
            } ?: ParsedCnbRates(emptyMap(), "")
            val eurLive = cnbParsed.rates["EUR"]
            val usdLive = cnbParsed.rates["USD"]
            val eurValid = eurLive != null && isValidCurrencyRate("EUR", eurLive)
            val usdValid = usdLive != null && isValidCurrencyRate("USD", usdLive)
            val isFxLive = eurValid || usdValid

            val eurRate = if (eurValid) eurLive!! else FxRateService.DEFAULT_EUR_CZK_RATE
            val usdRate = if (usdValid) usdLive!! else FxRateService.DEFAULT_USD_CZK_RATE
            val gbpRate = cnbParsed.rates["GBP"]?.takeIf { isValidCurrencyRate("GBP", it) }
                ?: FxRateService.DEFAULT_GBP_CZK_RATE
            val plnRate = cnbParsed.rates["PLN"]?.takeIf { isValidCurrencyRate("PLN", it) }
                ?: FxRateService.DEFAULT_PLN_CZK_RATE
            val chfRate = cnbParsed.rates["CHF"]?.takeIf { isValidCurrencyRate("CHF", it) }
                ?: FxRateService.DEFAULT_CHF_CZK_RATE
            val hufRate = cnbParsed.rates["HUF"]?.takeIf { isValidCurrencyRate("HUF", it) }
                ?: FxRateService.DEFAULT_HUF_CZK_RATE
            val rateDate = if (isFxLive) cnbParsed.rateDate else ""

            val sourceDescription = if (isFxLive) {
                FxRateService.updateRates(cnbParsed.rates)
                "Official ČNB Daily FX API & Bundled 2026 Statutory Constants"
            } else {
                "Bundled 2026 Czech Statutory Constants (Offline)"
            }

            val result = parsed.copy(
                timestamp = lockedNowMs,
                sourceName = sourceDescription,
                isFxLive = isFxLive,
                eurCzkRate = eurRate,
                usdCzkRate = usdRate,
                gbpCzkRate = gbpRate,
                plnCzkRate = plnRate,
                chfCzkRate = chfRate,
                hufCzkRate = hufRate,
                rateDate = rateDate
            )
            if (eurValid && usdValid) {
                cachedCnbSuccess = CachedCnbResult(data = result, fetchedAtMs = lockedNowMs)
            }
            result
        }
    }

    private fun localDefaults() = CzechRegulatoryData()

    private fun differsBy(currentVal: Double, liveVal: Double, tolerance: Double): Boolean {
        if (!currentVal.isFinite() || !liveVal.isFinite()) return true
        return abs(currentVal - liveVal) > tolerance
    }

    /**
     * Compares live official data against the user's current settings.
     */
    fun computeDifferences(current: SettingsEntity, live: CzechRegulatoryData): List<SyncDifferenceItem> {
        val list = mutableListOf<SyncDifferenceItem>()

        // 1. Inflation & Macro
        val isCpiDiff = differsBy(current.cpiInflationPct, live.csuCpiInflationPct, 0.05)
        list.add(
            SyncDifferenceItem(
                category = "Macroeconomics",
                label = "CPI Inflation (ČSÚ Benchmark)",
                currentValueFormatted = fmtPct(current.cpiInflationPct),
                liveValueFormatted = fmtPct(live.csuCpiInflationPct),
                isDifferent = isCpiDiff,
                impactHint = if (isCpiDiff) "Affects 35-year purchasing power discounting" else "Aligned with ČSÚ headline rate"
            )
        )

        // 2. Child 1 Tax Bonus
        val isChild1Diff = differsBy(current.child1TaxBonusAnnual, live.child1TaxBonusAnnual, 1.0)
        list.add(
            SyncDifferenceItem(
                category = "Child Tax Framework",
                label = "Child 1 Tax Bonus",
                currentValueFormatted = fmtCZK(current.child1TaxBonusAnnual),
                liveValueFormatted = fmtCZK(live.child1TaxBonusAnnual),
                isDifferent = isChild1Diff,
                impactHint = "Annual tax credit reduction for 1st child"
            )
        )

        // 3. Child 2 Tax Bonus
        val isChild2Diff = differsBy(current.child2TaxBonusAnnual, live.child2TaxBonusAnnual, 1.0)
        list.add(
            SyncDifferenceItem(
                category = "Child Tax Framework",
                label = "Child 2 Tax Bonus",
                currentValueFormatted = fmtCZK(current.child2TaxBonusAnnual),
                liveValueFormatted = fmtCZK(live.child2TaxBonusAnnual),
                isDifferent = isChild2Diff,
                impactHint = "Annual tax credit reduction for 2nd child"
            )
        )

        // 4. Basic Taxpayer Credit
        val isTaxpayerCreditDiff = differsBy(current.taxpayerCreditAnnual, live.taxpayerCreditAnnual, 1.0)
        list.add(
            SyncDifferenceItem(
                category = "Personal Tax Credits",
                label = "Basic Taxpayer Credit",
                currentValueFormatted = fmtCZK(current.taxpayerCreditAnnual),
                liveValueFormatted = fmtCZK(live.taxpayerCreditAnnual),
                isDifferent = isTaxpayerCreditDiff,
                impactHint = "Universal personal annual tax credit"
            )
        )

        // 5. DIP & DPS Combined Tax Deduction Ceiling
        val isDeductionCeilingDiff = differsBy(current.taxDeductionCeilingAnnual, live.dipDpsCombinedCeilingAnnual, 1.0)
        list.add(
            SyncDifferenceItem(
                category = "Retirement Deductions",
                label = "DIP + DPS Combined Tax Deduction",
                currentValueFormatted = fmtCZK(current.taxDeductionCeilingAnnual),
                liveValueFormatted = fmtCZK(live.dipDpsCombinedCeilingAnnual),
                isDifferent = isDeductionCeilingDiff,
                impactHint = "Saves up to 7 200 Kč (15%) or 11 040 Kč (23%) yearly"
            )
        )

        // 6. DPS Subsidy Cap
        val isYouthSubsidyDiff = differsBy(current.dpsYouthSubsidyMaxMonthly, live.dpsYouthSubsidyMaxMonthly, 1.0)
        list.add(
            SyncDifferenceItem(
                category = "Pension Regulations (DPS)",
                label = "DPS Subsidy Cap (State Match)",
                currentValueFormatted = "${fmtCZK(current.dpsYouthSubsidyMaxMonthly)} monthly",
                liveValueFormatted = "${fmtCZK(live.dpsYouthSubsidyMaxMonthly)} monthly",
                isDifferent = isYouthSubsidyDiff,
                impactHint = "20% state match up to ${fmtCZK(live.dpsYouthSubsidyMaxMonthly)} monthly"
            )
        )

        // 7. Progressive Tax 23% Threshold
        val isProgThresholdDiff = differsBy(current.taxSecondBracketThresholdAnnual, live.progressive23ThresholdAnnual, 1.0)
        list.add(
            SyncDifferenceItem(
                category = "Progressive Tax Threshold",
                label = "23% Tax Bracket Threshold",
                currentValueFormatted = fmtCZK(current.taxSecondBracketThresholdAnnual),
                liveValueFormatted = fmtCZK(live.progressive23ThresholdAnnual),
                isDifferent = isProgThresholdDiff,
                impactHint = "36x national average wage"
            )
        )

        // 8. Parental Allowance Pot
        val fromDiffers = differsBy(current.parentalAllowanceTotalFromCutoff, live.parentalAllowanceTotalFromCutoff, 1.0)
        val beforeDiffers = differsBy(current.parentalAllowanceTotalBeforeCutoff, live.parentalAllowanceTotalBeforeCutoff, 1.0)
        val isParentalDiff = fromDiffers || beforeDiffers
        val showBeforeDetail = !fromDiffers && beforeDiffers
        list.add(
            SyncDifferenceItem(
                category = "Social & Parental Support",
                label = "Parental Benefit Pot",
                currentValueFormatted = if (showBeforeDetail) {
                    "${fmtCZK(current.parentalAllowanceTotalFromCutoff)} (pre-cutoff ${fmtCZK(current.parentalAllowanceTotalBeforeCutoff)})"
                } else {
                    fmtCZK(current.parentalAllowanceTotalFromCutoff)
                },
                liveValueFormatted = if (showBeforeDetail) {
                    "${fmtCZK(live.parentalAllowanceTotalFromCutoff)} (pre-cutoff ${fmtCZK(live.parentalAllowanceTotalBeforeCutoff)})"
                } else {
                    fmtCZK(live.parentalAllowanceTotalFromCutoff)
                },
                isDifferent = isParentalDiff,
                impactHint = "${fmtCZK(live.parentalAllowanceTotalFromCutoff)} total state benefit entitlement"
            )
        )

        // 9. Employer Retirement Exemption
        val isEmployerDiff = differsBy(current.employerRetirementExemptionAnnual, live.employerRetirementExemptionAnnual, 1.0)
        list.add(
            SyncDifferenceItem(
                category = "Retirement Tax Shield",
                label = "Employer Contribution Exemption Cap",
                currentValueFormatted = "${fmtCZK(current.employerRetirementExemptionAnnual)}/yr",
                liveValueFormatted = "${fmtCZK(live.employerRetirementExemptionAnnual)}/yr",
                isDifferent = isEmployerDiff,
                impactHint = "Tax-exempt annual employer contribution limit"
            )
        )

        return list
    }

    /**
     * Applies verified live regulatory parameters to the user's settings.
     */
    fun applyRegulatoryUpdates(
        current: SettingsEntity,
        live: CzechRegulatoryData,
        applyMacroInflation: Boolean = true,
        applyTaxAndPension: Boolean = true
    ): SettingsEntity {
        var updated = current

        if (applyTaxAndPension) {
            updated = updated.copy(
                taxRatePct = live.baseTaxRatePct,
                taxRateSecondPct = live.progressiveTaxRatePct,
                taxSecondBracketThresholdAnnual = live.progressive23ThresholdAnnual,
                taxpayerCreditAnnual = live.taxpayerCreditAnnual,
                taxDeductionCeilingAnnual = live.dipDpsCombinedCeilingAnnual,
                spouseTaxCreditAnnual = live.spouseTaxCreditAnnual,
                spouseIncomeLimitAnnual = live.spouseIncomeLimitAnnual,
                child1TaxBonusAnnual = live.child1TaxBonusAnnual,
                child2TaxBonusAnnual = live.child2TaxBonusAnnual,
                child3PlusTaxBonusAnnual = live.child3PlusTaxBonusAnnual,
                dpsMinDepositForSubsidy = live.dpsMinDepositForSubsidy,
                dpsDeductionThresholdMonthly = live.dpsDeductionThresholdMonthly,
                dpsStandardSubsidyMaxMonthly = live.dpsStandardSubsidyMaxMonthly,
                dpsYouthSubsidyMaxMonthly = live.dpsYouthSubsidyMaxMonthly,
                dpsYouthAgeLimit = live.dpsYouthAgeLimit,
                dpsSubsidyRateStandardPct = live.dpsSubsidyRateStandardPct,
                dpsSubsidyRateYouthPct = live.dpsSubsidyRateYouthPct,
                minWageMonthly = live.minWageMonthly,
                dpsStatutoryFeeCapPct = live.dpsStatutoryFeeCapPct,
                employerRetirementExemptionAnnual = live.employerRetirementExemptionAnnual,
                childBonusMinWageMultiplier = live.childBonusMinWageMultiplier,
                parentalAllowanceTotalBeforeCutoff = live.parentalAllowanceTotalBeforeCutoff,
                parentalAllowanceTotalFromCutoff = live.parentalAllowanceTotalFromCutoff,
                parentalAllowanceCutoffYear = live.parentalAllowanceCutoffYear
            )
        }

        if (applyMacroInflation) {
            updated = updated.copy(
                cpiInflationPct = live.csuCpiInflationPct
            )
        }

        return updated
    }

    private fun extractCnbRatesViaRegex(jsonString: String): Triple<Double, Double, String> {
        val eurMatch = EUR_KEY_VALUE_REGEX.find(jsonString) ?: EUR_VALUE_KEY_REGEX.find(jsonString)
        val usdMatch = USD_KEY_VALUE_REGEX.find(jsonString) ?: USD_VALUE_KEY_REGEX.find(jsonString)
        val dateMatch = VALID_FOR_REGEX.find(jsonString)
        val eur = eurMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val usd = usdMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val rateDate = dateMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
        return Triple(eur, usd, rateDate)
    }

    internal fun parseCnbRatesJson(jsonString: String): Triple<Double, Double, String> {
        val parsed = parseCnbAllRatesJson(jsonString)
        return Triple(parsed.rates["EUR"] ?: 0.0, parsed.rates["USD"] ?: 0.0, parsed.rateDate)
    }

    internal fun parseCnbRatesTxt(txtString: String): Triple<Double, Double, String> {
        val parsed = parseCnbAllRatesTxt(txtString)
        return Triple(parsed.rates["EUR"] ?: 0.0, parsed.rates["USD"] ?: 0.0, parsed.rateDate)
    }

    internal fun parseCnbAllRatesJson(jsonString: String): ParsedCnbRates {
        if (jsonString.isBlank() || jsonString.length > MAX_CNB_PAYLOAD_CHARS) {
            return ParsedCnbRates(emptyMap(), "")
        }
        val parsedRates = linkedMapOf<String, Double>()
        var rateDate = ""
        try {
            val json = JSONObject(jsonString)
            val ratesArray = json.optJSONArray("rates")
            if (ratesArray != null) {
                for (i in 0 until ratesArray.length()) {
                    val item = ratesArray.optJSONObject(i) ?: continue
                    val code = item.optString("currencyCode", "").trim().uppercase(Locale.ROOT)
                    if (code !in SUPPORTED_CNB_CURRENCIES) continue
                    val rawRate = item.optDouble("rate", Double.NaN)
                    val amount = if (item.has("amount") && !item.isNull("amount")) {
                        item.optDouble("amount", Double.NaN)
                    } else {
                        1.0
                    }
                    val date = if (item.has("validFor") && !item.isNull("validFor")) {
                        item.optString("validFor", "").trim()
                    } else {
                        ""
                    }
                    if (date.isNotBlank() && rateDate.isBlank()) rateDate = date
                    if (rawRate.isFinite() && rawRate > 0.0 && amount.isFinite() && amount > 0.0) {
                        val unitRate = rawRate / amount
                        if (isValidCurrencyRate(code, unitRate)) {
                            parsedRates[code] = unitRate
                        }
                    }
                }
            }
        } catch (_: Exception) {
            val fallback = extractCnbRatesViaRegex(jsonString)
            if (isValidCurrencyRate("EUR", fallback.first)) parsedRates["EUR"] = fallback.first
            if (isValidCurrencyRate("USD", fallback.second)) parsedRates["USD"] = fallback.second
            return ParsedCnbRates(parsedRates, fallback.third)
        }
        if ("EUR" !in parsedRates || "USD" !in parsedRates || rateDate.isBlank()) {
            val fallback = extractCnbRatesViaRegex(jsonString)
            if ("EUR" !in parsedRates && isValidCurrencyRate("EUR", fallback.first)) {
                parsedRates["EUR"] = fallback.first
            }
            if ("USD" !in parsedRates && isValidCurrencyRate("USD", fallback.second)) {
                parsedRates["USD"] = fallback.second
            }
            if (rateDate.isBlank()) rateDate = fallback.third
        }
        return ParsedCnbRates(parsedRates, rateDate)
    }

    internal fun parseCnbAllRatesTxt(txtString: String): ParsedCnbRates {
        if (txtString.isBlank() || txtString.length > MAX_CNB_PAYLOAD_CHARS) {
            return ParsedCnbRates(emptyMap(), "")
        }
        val parsedRates = linkedMapOf<String, Double>()
        var rateDate = ""
        try {
            val lines = txtString.lines()
            val firstLine = lines.firstOrNull()?.trim().orEmpty()
            CNB_TXT_DATE_REGEX.find(firstLine)?.let { match ->
                rateDate = match.groupValues[1]
            }
            lines.forEach { line ->
                val parts = line.split("|")
                if (parts.size >= 5) {
                    val amount = parts[2].trim().replace(",", ".").toDoubleOrNull()
                    val code = parts[3].trim().uppercase(Locale.ROOT)
                    val rawRate = parts[4].trim().replace(",", ".").toDoubleOrNull()
                    if (code in SUPPORTED_CNB_CURRENCIES &&
                        rawRate != null && rawRate.isFinite() && rawRate > 0.0 &&
                        amount != null && amount.isFinite() && amount > 0.0
                    ) {
                        val unitRate = rawRate / amount
                        if (isValidCurrencyRate(code, unitRate)) {
                            parsedRates[code] = unitRate
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Return whatever valid rates were parsed prior to error
        }
        return ParsedCnbRates(parsedRates, rateDate)
    }

    private fun isValidCurrencyRate(code: String, rate: Double): Boolean {
        if (!rate.isFinite() || rate <= 0.0) return false
        return when (code.uppercase(Locale.ROOT)) {
            "HUF" -> rate in 0.01..5.0
            else -> rate in 1.0..200.0
        }
    }

    private fun fetchCnbAllRates(): ParsedCnbRates {
        var jsonRates = ParsedCnbRates(emptyMap(), "")
        val content = fetchUrlContent(CNB_DAILY_RATES_URL, timeoutMs = 2500)
        if (!content.isNullOrBlank()) {
            jsonRates = parseCnbAllRatesJson(content)
            val eurOk = jsonRates.rates["EUR"]?.let { isValidCurrencyRate("EUR", it) } == true
            val usdOk = jsonRates.rates["USD"]?.let { isValidCurrencyRate("USD", it) } == true
            if (eurOk && usdOk) {
                return jsonRates
            }
        }

        val txt = fetchUrlContent(CNB_TXT_FALLBACK_URL, timeoutMs = 2000)
        if (!txt.isNullOrBlank()) {
            val txtRates = parseCnbAllRatesTxt(txt)
            val merged = LinkedHashMap(txtRates.rates)
            merged.putAll(jsonRates.rates)
            val mergedDate = jsonRates.rateDate.ifBlank { txtRates.rateDate }
            return ParsedCnbRates(merged, mergedDate)
        }
        return jsonRates
    }

    private fun isAllowedCnbHttpsUrl(url: URL): Boolean {
        if (!url.protocol.equals("https", ignoreCase = true)) return false
        if (!url.userInfo.isNullOrEmpty()) return false
        if (url.port != -1 && url.port != 443) return false
        val host = url.host.lowercase(Locale.ROOT)
        return host == "api.cnb.cz" || host == "www.cnb.cz" || host == "cnb.cz"
    }

    private fun isAcceptableContentType(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return true
        val mime = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (mime.contains("html") || mime.contains("xhtml")) return false
        return mime == "application/json" ||
            mime == "text/plain" ||
            mime == "text/csv" ||
            mime == "application/octet-stream" ||
            mime.endsWith("+json")
    }

    private fun fetchUrlContent(urlString: String, timeoutMs: Int = 3000): String? {
        return try {
            var currentUrl = URL(urlString)
            for (hop in 0..MAX_REDIRECTS) {
                if (!isAllowedCnbHttpsUrl(currentUrl)) return null
                var connection: HttpURLConnection? = null
                try {
                    connection = (currentUrl.openConnection() as? HttpURLConnection) ?: return null
                    connection.instanceFollowRedirects = false
                    connection.requestMethod = "GET"
                    connection.connectTimeout = timeoutMs
                    connection.readTimeout = timeoutMs
                    connection.setRequestProperty("User-Agent", "MartinufinancialsFIRE/6.0")
                    connection.setRequestProperty("Accept", "application/json, text/plain, */*")

                    val code = connection.responseCode
                    if (code in 300..399) {
                        if (hop == MAX_REDIRECTS) return null
                        val location = connection.getHeaderField("Location")?.trim()
                        if (location.isNullOrBlank()) return null
                        currentUrl = URL(currentUrl, location)
                        continue
                    }

                    if (code != HttpURLConnection.HTTP_OK) return null
                    if (!isAcceptableContentType(connection.contentType)) return null

                    BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                        val sb = StringBuilder()
                        val buf = CharArray(8192)
                        var total = 0
                        while (true) {
                            val n = reader.read(buf)
                            if (n < 0) break
                            if (total + n > MAX_CNB_PAYLOAD_CHARS) return null
                            total += n
                            sb.append(buf, 0, n)
                        }
                        return sb.toString()
                    }
                } finally {
                    connection?.disconnect()
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
