package com.example.util.statement

import com.example.util.FxRateService
import com.example.util.ParsedBankTransaction
import java.time.LocalDate
import java.util.Locale

internal object CzechValueParsers {

    val DIGITS_SPACE_ONLY_REGEX = Regex("""^\d+(\s+\d+)*$""")

    private val AMOUNT_REGEX = Regex(
        """(?<![\p{L}\d])([+\-\u2212\u2013\u2014]?\s*(?:\d{1,3}(?:[\s\u00A0\u202F]\d{3})*(?:,\d{2}|\.\d{2}(?![.\d]))|\d{1,3}(?:\.\d{3})+,\d{2}|\d{1,3}(?:,\d{3})+\.\d{2}(?![.\d])|\d+(?:,\d{2}|\.\d{2}(?![.\d]))))\s*(CZK|Kč|Kc|EUR|€|USD|\$|GBP|£|PLN|zł|zl|CHF|HUF|Ft)?\s*([+\-\u2212\u2013\u2014])?(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )
    private val WHOLE_CURRENCY_REGEX = Regex(
        """(?<![\p{L}\d])([+\-\u2212\u2013\u2014]?\s*(?:\d{1,3}(?:[\s\u00A0\u202F]\d{3})+|\d+))\s*(CZK|Kč|Kc|EUR|€|USD|\$|GBP|£|PLN|zł|zl|CHF|HUF|Ft)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )
    private val CURRENCY_STRIP_REGEX = Regex(
        """(?i)(?<![\p{L}])(?:czk|kč|kc|eur|usd|gbp|pln|chf|huf|zł|zl|ft)(?![\p{L}])"""
    )

    private val ACC_SLASH_REGEX = Regex("""(?<!\d)(?:\d{1,6}-)?\d{1,10}/\d{4}(?!\d)""")
    private val OWN_ACC_BARE_REGEX = Regex("""(?<!\d)(?:\d{1,6}-)?\d{6,10}(?!/\d)(?!\d)""")
    private val IBAN_SPACED_REGEX = Regex(
        """(?<![\p{L}\d])(CZ\d{2})[\s\u00A0]*(\d{4})[\s\u00A0]*(\d{6})[\s\u00A0]*(\d{10})(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE
    )
    private val IBAN_GENERIC_REGEX = Regex("""(?<![\p{L}\d])CZ\d{22}(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    private val VS_EXTRACT_REGEX = Regex("""(?i)(?<![\p{L}\d])(?:vs|v\.s\.|var(?:iabiln[ií])?\s*symbol)[:\s]*(\d{1,10})(?![\p{L}\d])""")

    private val AMOUNT_STRIP_WS_REGEX = Regex("""[\p{Z}\s\u200B-\u200D\u00AD\uFEFF]""")
    private val MULTI_COMMA_THOUSANDS_REGEX = Regex("""^-?[1-9]\d{0,2}(,\d{3})+$""")
    private val MULTI_DOT_THOUSANDS_REGEX = Regex("""^-?[1-9]\d{0,2}(\.\d{3})+$""")
    private val ISO_TIME_SUFFIX_REGEX = Regex("""(?<=\d)T\d.*$""")
    private val TRAILING_TIME_REGEX = Regex("""\s+\d{1,2}:\d{2}(:\d{2})?.*$""")

    private val CLOSING_BALANCE_KEYWORDS = listOf(
        "konecny zustatek",
        "ucetni zustatek",
        "zustatek ke konci",
        "zustatek na konci",
        "konecny stav",
        "zustatek k"
    )

    fun safeIsoDate(year: Int, month: Int, day: Int): String? {
        if (year !in 1900..2200 || month !in 1..12 || day !in 1..31) return null
        return try {
            LocalDate.of(year, month, day).toString()
        } catch (_: Exception) {
            null
        }
    }

    fun canonicalAccountKey(rawAccount: String): String {
        val stripped = rawAccount.replace(" ", "").lowercase(Locale.ROOT).trim()
        if (stripped.isEmpty()) return ""
        val slashIdx = stripped.indexOf('/')
        if (slashIdx == -1) return stripped.replace("-", "").trimStart('0')
        val accPart = stripped.substring(0, slashIdx)
        val bankPart = stripped.substring(slashIdx + 1)
        val parts = accPart.split("-")
            .map { it.trimStart('0').ifEmpty { "0" } }
            .filter { it.isNotEmpty() }
        val normalizedAcc = if (parts.size >= 2 && parts[0] == "0") {
            parts.drop(1).joinToString("")
        } else {
            parts.joinToString("")
        }
        return "$normalizedAcc/$bankPart"
    }

    fun isKnownAccountMatch(rawAccount: String, knownAccounts: Set<String>): Boolean {
        if (rawAccount.isBlank() || knownAccounts.isEmpty()) return false
        val canonicalTxAcc = canonicalAccountKey(rawAccount)
        if (canonicalTxAcc.isEmpty()) return false
        val accCore = canonicalTxAcc.substringBefore('/')
        return knownAccounts.any { fam ->
            val famKey = canonicalAccountKey(fam)
            famKey.isNotEmpty() && (famKey == canonicalTxAcc || (!famKey.contains('/') && famKey == accCore))
        }
    }

    fun resolveClosingBalance(
        balanceWithDates: List<Pair<String, Double>>,
        transactions: List<ParsedBankTransaction>
    ): Double? {
        if (balanceWithDates.isEmpty()) return null
        val isAscending = if (transactions.size >= 2) {
            transactions.first().date <= transactions.last().date
        } else true
        val maxDate = balanceWithDates.maxOf { it.first }
        val matching = balanceWithDates.filter { it.first == maxDate }
        return if (isAscending) matching.last().second else matching.first().second
    }

    fun extractClosingBalance(fullText: String, lines: List<String>): Double? {
        for (line in lines) {
            val folded = StatementTextDecoder.foldHeader(line)
            if (CLOSING_BALANCE_KEYWORDS.any { folded.contains(it) }) {
                val amounts = extractAllAmountsWithCurrency(line)
                val bal = amounts.lastOrNull { it.amountInCzk.isFinite() }?.amountInCzk
                if (bal != null) return bal
            }
        }

        val foldedFull = StatementTextDecoder.foldHeader(fullText)
        for (kw in CLOSING_BALANCE_KEYWORDS) {
            val idx = foldedFull.indexOf(kw)
            if (idx != -1) {
                val snippet = foldedFull.substring(idx, (idx + 120).coerceAtMost(foldedFull.length))
                val amounts = extractAllAmountsWithCurrency(snippet)
                val bal = amounts.firstOrNull { it.amountInCzk.isFinite() }?.amountInCzk
                if (bal != null) return bal
            }
        }
        return null
    }

    fun extractAllAmounts(text: String): List<Double> =
        extractAllAmountsWithCurrency(text).map { it.amountInCzk }

    fun extractAllAmountsWithCurrency(text: String): List<FxRateService.CurrencyConversionDetails> {
        val amounts = mutableListOf<FxRateService.CurrencyConversionDetails>()
        val folded = StatementTextDecoder.foldHeader(text)
        val isRefund = folded.contains("vraceni") || folded.contains("vratka") ||
            folded.contains("storno") || folded.contains("refund")
        val isExplicitInflow = folded.contains("prichozi") || folded.contains("kredit") ||
            folded.contains("credit") || folded.contains("ve prospech") ||
            folded.contains("pripsano") || folded.contains("prijem")
        // Do NOT flip positive credits on incidental "nakup" or bare "vyber" words.
        val hasExplicitDebitIndicator = folded.contains("odchozi") || folded.contains("debet") ||
            folded.contains("debit") || folded.contains("platba kartou") ||
            folded.contains("platba mobilem") || folded.contains("vyber z bankomatu") ||
            folded.contains("poplatek za") || folded.contains("poplatek") || folded.contains("vydej")

        for (m in AMOUNT_REGEX.findAll(text)) {
            val endIdx = m.range.last + 1
            val following = text.substring(endIdx, minOf(text.length, endIdx + 10)).trim().lowercase(Locale.ROOT)
            if (following.startsWith("%")) continue

            val rawNum = m.groupValues[1]
            val currencyToken = m.groupValues[2]

            if (rawNum.contains('.')) {
                if (text.getOrNull(m.range.last + 1) == '.') continue
                if (currencyToken.isBlank()) {
                    val start = m.range.first
                    val prefixChar = if (start > 0) text.substring(start - 1, start) else ""
                    if (prefixChar == "." || prefixChar == "/") continue
                    val before = text.substring(0, start).trim()
                    if (before.isEmpty()) continue
                }
            }

            val trailingSign = m.groupValues[3].replace("\u2212", "-").replace("\u2013", "-").replace("\u2014", "-")

            val start = m.range.first
            val prefixWindow = text.substring(maxOf(0, start - 5), start).trim().lowercase(Locale.ROOT)
            val prefixCurr = when {
                prefixWindow.endsWith("€") -> "EUR"
                prefixWindow.endsWith("$") -> "USD"
                prefixWindow.endsWith("£") -> "GBP"
                else -> null
            }

            val explicitCurr = when {
                currencyToken.isNotBlank() -> FxRateService.normalizeCurrencyCode(currencyToken)
                following.startsWith("eur") || following.startsWith("€") -> "EUR"
                following.startsWith("usd") || following.startsWith("$") -> "USD"
                following.startsWith("gbp") || following.startsWith("£") -> "GBP"
                following.startsWith("pln") || following.startsWith("zł") || following.startsWith("zl") -> "PLN"
                following.startsWith("chf") -> "CHF"
                following.startsWith("huf") || following.startsWith("ft") -> "HUF"
                following.startsWith("kč") || following.startsWith("kc") || following.startsWith("czk") -> "CZK"
                prefixCurr != null -> prefixCurr
                else -> null
            }

            var details = parseCzechAmountDetailed(rawNum, explicitCurrency = explicitCurr)
            if (!details.amountInCzk.isFinite()) continue
            var parsed = details.amountInCzk
            var orig = details.originalAmount
            if (trailingSign == "-" && parsed > 0) {
                parsed = -parsed
                orig = -kotlin.math.abs(orig)
            } else if (trailingSign == "+" && parsed < 0) {
                parsed = -parsed
                orig = kotlin.math.abs(orig)
            }

            if (parsed != 0.0) {
                val rowExplicitInflow = isExplicitInflow || rawNum.contains("+") || trailingSign == "+"
                if (amounts.isEmpty() && parsed > 0 && !isRefund && !rowExplicitInflow && hasExplicitDebitIndicator) {
                    parsed = -parsed
                    orig = -kotlin.math.abs(orig)
                }
                details = details.copy(originalAmount = orig, amountInCzk = parsed)
                amounts.add(details)
            }
        }

        if (amounts.isEmpty()) {
            for (m in WHOLE_CURRENCY_REGEX.findAll(text)) {
                val rawNum = m.groupValues[1]
                val curr = FxRateService.normalizeCurrencyCode(m.groupValues[2])
                var details = parseCzechAmountDetailed(rawNum, explicitCurrency = curr)
                if (!details.amountInCzk.isFinite() || details.amountInCzk == 0.0) continue
                var parsed = details.amountInCzk
                var orig = details.originalAmount
                val rowExplicitInflow = isExplicitInflow || rawNum.contains("+")
                if (amounts.isEmpty() && parsed > 0 && !isRefund && !rowExplicitInflow && hasExplicitDebitIndicator) {
                    parsed = -parsed
                    orig = -kotlin.math.abs(orig)
                }
                details = details.copy(originalAmount = orig, amountInCzk = parsed)
                if (parsed.isFinite() && parsed != 0.0) amounts.add(details)
            }
        }

        return amounts
    }

    fun extractAccount(text: String): String {
        val match = ACC_SLASH_REGEX.find(text)
        if (match != null) return match.value.trim()

        val ibanSpacedMatch = IBAN_SPACED_REGEX.find(text)
        if (ibanSpacedMatch != null) {
            val bankCode = ibanSpacedMatch.groupValues[2]
            val prefix = ibanSpacedMatch.groupValues[3].trimStart('0')
            val acc = ibanSpacedMatch.groupValues[4].trimStart('0')
            return if (prefix.isNotBlank()) "$prefix-$acc/$bankCode" else "$acc/$bankCode"
        }
        val genericMatch = IBAN_GENERIC_REGEX.find(text.replace(" ", "").replace("\u00A0", ""))
        if (genericMatch != null) {
            val iban = genericMatch.value
            val bankCode = iban.substring(4, 8)
            val prefix = iban.substring(8, 14).trimStart('0')
            val acc = iban.substring(14, 24).trimStart('0')
            return if (prefix.isNotBlank()) "$prefix-$acc/$bankCode" else "$acc/$bankCode"
        }
        return ""
    }

    fun formatAboAccountKey(raw16Digits: String, bankCode: String?): String {
        val clean = raw16Digits.trim()
        if (clean.length != 16 || !clean.all { it.isDigit() }) return ""
        val prefix = clean.substring(0, 6)
        val number = clean.substring(6, 16)
        val combined = "$prefix-$number"
        return if (!bankCode.isNullOrBlank()) {
            canonicalAccountKey("$combined/$bankCode")
        } else {
            canonicalAccountKey(combined)
        }
    }

    fun extractOwnStatementAccountKey(
        lines: List<String>,
        bankCode: String? = null,
        limitLines: Int = 35
    ): String {
        val scanLines = if (lines.size > limitLines) lines.take(limitLines) else lines
        for (line in scanLines) {
            val folded = StatementTextDecoder.foldHeader(line)
            if (folded.contains("protiuc") || folded.contains("protistran")) continue
            val isExplicitOwnLabel = folded.contains("cislo uctu") ||
                folded.contains("vlastni ucet") ||
                folded.contains("muj ucet") ||
                folded.contains("ucet c") ||
                folded.contains("account number") ||
                folded.contains("account no") ||
                folded.contains("iban") ||
                folded.startsWith("ucet:") ||
                folded.startsWith("account:")
            if (!isExplicitOwnLabel) continue

            val acc = extractAccount(line)
            if (acc.isNotEmpty()) {
                return canonicalAccountKey(acc)
            }
            val bare = OWN_ACC_BARE_REGEX.find(line)?.value
            if (!bare.isNullOrBlank()) {
                return if (!bankCode.isNullOrBlank()) canonicalAccountKey("$bare/$bankCode") else canonicalAccountKey(bare)
            }
        }
        return ""
    }

    fun extractVariableSymbol(text: String): String {
        val match = VS_EXTRACT_REGEX.find(text)
        return match?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    fun parseCzechAmountDetailed(
        str: String,
        explicitCurrency: String? = null,
        eurRate: Double = FxRateService.getRate("EUR"),
        usdRate: Double = FxRateService.getRate("USD")
    ): FxRateService.CurrencyConversionDetails {
        var clean = str.trim()
        if (clean.isBlank()) return FxRateService.CurrencyConversionDetails(0.0, "CZK", 0.0, 1.0)

        val detectedFromText = FxRateService.detectCurrency(clean)
        val targetCurrency = explicitCurrency ?: detectedFromText ?: "CZK"
        val normCurrency = FxRateService.normalizeCurrencyCode(targetCurrency)

        clean = CURRENCY_STRIP_REGEX.replace(clean, "")
            .replace("€", "")
            .replace("$", "")
            .replace("£", "")
            .trim()

        val isNegativeParentheses = clean.startsWith("(") && clean.endsWith(")")
        if (isNegativeParentheses) {
            clean = clean.substring(1, clean.length - 1).trim()
        }

        clean = clean.replace("\u2212", "-")
            .replace("\u2013", "-")
            .replace("\u2014", "-")
            .replace("\u2012", "-")
            .replace("\uFE63", "-")
            .replace("\uFF0D", "-")
            .replace("+", "")

        clean = AMOUNT_STRIP_WS_REGEX.replace(clean, "").trim()
        if (clean.isBlank()) return FxRateService.CurrencyConversionDetails(0.0, normCurrency, 0.0, 1.0)

        val hasTrailingMinus = clean.endsWith("-")
        if (hasTrailingMinus) {
            clean = clean.removeSuffix("-").trim()
        }

        clean = when {
            clean.contains(',') && clean.contains('.') -> {
                if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) {
                    clean.replace(".", "").replace(',', '.')
                } else {
                    clean.replace(",", "")
                }
            }
            MULTI_COMMA_THOUSANDS_REGEX.matches(clean) -> clean.replace(",", "")
            MULTI_DOT_THOUSANDS_REGEX.matches(clean) -> clean.replace(".", "")
            else -> clean.replace(',', '.')
        }

        var result = clean.toDoubleOrNull() ?: 0.0
        if (!result.isFinite()) result = 0.0
        if (hasTrailingMinus && result > 0) result = -result
        val signedResult = if (isNegativeParentheses && result > 0) -result else result

        val safeEurRate = if (eurRate.isFinite() && eurRate > 0.0) eurRate else 1.0
        val safeUsdRate = if (usdRate.isFinite() && usdRate > 0.0) usdRate else 1.0
        val (rawConverted, rate) = when (normCurrency) {
            "EUR" -> (signedResult * safeEurRate) to safeEurRate
            "USD" -> (signedResult * safeUsdRate) to safeUsdRate
            "CZK" -> signedResult to 1.0
            else -> {
                val otherRate = FxRateService.getRate(normCurrency).takeIf { it.isFinite() && it > 0.0 } ?: 1.0
                (signedResult * otherRate) to otherRate
            }
        }
        val converted = if (rawConverted.isFinite()) rawConverted else 0.0

        return FxRateService.CurrencyConversionDetails(
            originalAmount = signedResult,
            originalCurrency = normCurrency,
            amountInCzk = converted,
            exchangeRate = rate
        )
    }

    fun parseCzechAmount(
        str: String,
        explicitCurrency: String? = null,
        eurRate: Double = FxRateService.getRate("EUR"),
        usdRate: Double = FxRateService.getRate("USD")
    ): Double = parseCzechAmountDetailed(str, explicitCurrency, eurRate, usdRate).amountInCzk

    fun normalizeDate(str: String): String {
        val trimmed = str.trim()
        if (trimmed.isBlank()) return ""

        val noIso = ISO_TIME_SUFFIX_REGEX.replace(trimmed, "").trim()
        val dateOnly = TRAILING_TIME_REGEX.replace(noIso, "").trim()

        val clean = dateOnly.replace("/", ".").replace("-", ".")
        val parts = clean.split(".").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size != 3) return ""

        val (yearToken, monthToken, dayToken) = when {
            parts[0].length == 4 && parts[0].all { it.isDigit() } -> Triple(parts[0], parts[1], parts[2])
            parts[2].length == 4 && parts[2].all { it.isDigit() } -> Triple(parts[2], parts[1], parts[0])
            parts[2].length == 2 && parts[2].all { it.isDigit() } -> Triple("20" + parts[2], parts[1], parts[0])
            else -> return ""
        }
        if (!yearToken.all { it.isDigit() } || !monthToken.all { it.isDigit() } || !dayToken.all { it.isDigit() }) {
            return ""
        }

        val year = yearToken.toIntOrNull() ?: return ""
        val month = monthToken.toIntOrNull() ?: return ""
        val day = dayToken.toIntOrNull() ?: return ""
        if (year !in 1900..2200 || month !in 1..12 || day !in 1..31) return ""

        return try {
            LocalDate.of(year, month, day).toString()
        } catch (_: Exception) {
            ""
        }
    }
}
