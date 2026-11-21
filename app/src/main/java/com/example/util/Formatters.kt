package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Formatters {
    private val czkLocale = Locale.forLanguageTag("cs-CZ")

    // Precompiled typographic & formatting regexes to avoid per-call allocation in Compose loops
    private val NEGATIVE_ZERO_PCT_REGEX = Regex("^[-\\u2212]+(0+(?:[,.]0+)?%)$")
    private val SECTION_SIGN_REGEX = Regex("§[ \\t]+")
    private val THOUSANDS_SPACE_REGEX = Regex("(?<!\\d)(\\d{1,3})[ \\t]+(\\d{3})(?!\\d)")
    private val NUMBER_UNIT_REGEX = Regex("(\\d+)[ \\t]+(Kč|CZK|EUR|USD|%)")
    private val COMPACT_K_UNIT_REGEX = Regex("(\\d+k)[ \\t]+(Kč|CZK|EUR|USD)")
    private val DURATION_UNIT_REGEX = Regex("(\\d+)[ \\t]+(let|roky|roků|rok|měsíců|měsíce|dní|dnů|hodin|years|months|days)")
    private val SINGLE_LETTER_PREP_REGEX = Regex("(?<=[\\s\\u00A0\\u202F(\\[\"'“„]|^)([ksvzouaiIKSVZOUAI])[ \\t]+")
    private val ORDINAL_UNIT_REGEX = Regex("(\\d+(?:st|nd|rd|th|\\.)?)[ \\t]+(child|dítě|kroku|step|level)")

    private val czkFormatThreadLocal = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance(czkLocale).apply {
            maximumFractionDigits = 0
        }
    }

    private fun getCzkFormat(): NumberFormat = czkFormatThreadLocal.get() ?: NumberFormat.getNumberInstance(czkLocale).apply { maximumFractionDigits = 0 }

    fun roundToDisplay(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        val absVal = abs(value)
        return if (absVal >= 200.0) {
            kotlin.math.round(value / 10.0) * 10.0
        } else {
            value
        }
    }

    fun roundTo10k(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return kotlin.math.round(value / 10_000.0) * 10_000.0
    }

    fun roundTo1k(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return kotlin.math.round(value / 1_000.0) * 1_000.0
    }

    private fun formatIntegerDouble(value: Double): String {
        val clean = if (value == 0.0) 0.0 else value
        val absVal = abs(clean)
        return when {
            absVal < 9.0e15 -> getCzkFormat().format(clean.roundToLong()).replace(' ', '\u00A0')
            absVal <= 1.0e25 -> {
                val rounded = BigDecimal.valueOf(clean).setScale(0, RoundingMode.HALF_UP)
                getCzkFormat().format(rounded).replace(' ', '\u00A0')
            }
            else -> String.format(czkLocale, "%.2e", clean).replace(' ', '\u00A0')
        }
    }

    fun fmtEditableAmount(value: Double): String {
        if (!value.isFinite() || value == 0.0) return "0"
        val bd = BigDecimal.valueOf(value)
            .setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return if (bd.compareTo(BigDecimal.ZERO) == 0) "0" else bd.toPlainString()
    }

    fun fmtNum(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val displayVal = roundToDisplay(value)
        return formatIntegerDouble(displayVal)
    }

    fun fmtCZK(value: Double, symbol: String = "Kč"): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val displayVal = roundToDisplay(value)
        val formatted = formatIntegerDouble(displayVal)
        return if (symbol.isBlank()) formatted else "$formatted\u00A0$symbol"
    }

    /** Formats exact CZK amounts without nearest-10 display rounding (e.g. statutory credits 15 204 Kč). */
    fun fmtExactCZK(value: Double, symbol: String = "Kč"): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val formatted = formatIntegerDouble(value)
        return if (symbol.isBlank()) formatted else "$formatted\u00A0$symbol"
    }

    fun fmtCompact(value: Double, symbol: String = "Kč"): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val absVal = abs(value)
        val numStr = when {
            absVal >= 999_950_000 -> {
                val b = value / 1_000_000_000.0
                if (abs(b - b.roundToInt()) < 0.05) {
                    String.format(czkLocale, "%.0fB", b)
                } else {
                    String.format(czkLocale, "%.1fB", b)
                }
            }
            absVal >= 999_500 -> {
                val mil = value / 1_000_000.0
                if (abs(mil - mil.roundToInt()) < 0.05) {
                    String.format(czkLocale, "%.0fM", mil)
                } else {
                    String.format(czkLocale, "%.1fM", mil)
                }
            }
            absVal >= 100_000 -> {
                val k = kotlin.math.round(value / 1_000.0).roundToLong()
                "${k}k"
            }
            else -> {
                val displayVal = roundToDisplay(value)
                getCzkFormat().format(displayVal.roundToLong()).replace(' ', '\u00A0')
            }
        }
        return if (symbol.isBlank()) numStr else "$numStr\u00A0$symbol"
    }

    fun fmtLeverageReduction(amount: Double, symbol: String = "Kč"): String {
        if (amount.isNaN() || amount.isInfinite()) return "--"
        val absVal = abs(amount)
        val numStr = when {
            absVal >= 999_950_000 -> {
                val b = amount / 1_000_000_000.0
                if (abs(b - b.roundToInt()) < 0.05) String.format(czkLocale, "%.0fB", b)
                else String.format(czkLocale, "%.1fB", b)
            }
            absVal >= 999_500 -> {
                val mil = amount / 1_000_000.0
                if (abs(mil - mil.roundToInt()) < 0.05) String.format(czkLocale, "%.0fM", mil)
                else String.format(czkLocale, "%.1fM", mil)
            }
            absVal >= 10_000 -> {
                val k = kotlin.math.round(amount / 1_000.0).roundToLong()
                "${k}k"
            }
            absVal >= 1_000 -> {
                val k = amount / 1_000.0
                if (abs(k - k.roundToInt()) < 0.05) String.format(czkLocale, "%.0fk", k)
                else String.format(czkLocale, "%.1fk", k)
            }
            else -> {
                val displayVal = roundToDisplay(amount)
                formatIntegerDouble(displayVal)
            }
        }
        return if (symbol.isBlank()) numStr else "$numStr\u00A0$symbol"
    }

    fun fmtPct(value: Double, digits: Int? = null): String {
        if (value.isNaN() || value.isInfinite()) return "--%"
        val effectiveDigits = digits?.coerceAtLeast(0)
            ?: if (abs(value - kotlin.math.round(value)) < 1e-6) 0 else 1
        val zeroThreshold = if (effectiveDigits <= 15) 0.5 * 10.0.pow(-effectiveDigits) else 1e-9
        val cleanVal = if (abs(value) < maxOf(1e-9, zeroThreshold)) 0.0 else value
        val formatted = String.format(czkLocale, "%.${effectiveDigits}f%%", cleanVal)
            .replace(' ', '\u00A0')
        return NEGATIVE_ZERO_PCT_REGEX.replaceFirst(formatted, "$1")
    }

    /**
     * Typography formatter for justified ("in block") text layout.
     * Enforces Czech typographic standards (ČSN 01 6910) and prevents orphan characters:
     * 1. Binds all single-letter prepositions and conjunctions (k, s, v, z, o, u, a, i, and uppercase K, S, V, Z, O, U, A, I, plus English a, I)
     *    to the following word using a non-breaking space (NBSP, \u00A0) so they can never hang at the end of a line.
     * 2. Binds section marks (§) to the following number/citation.
     * 3. Binds numbers with currency/units/percentages (e.g. 1,700 Kč, 48k CZK, 15 %) so they never break across lines.
     * 4. Binds Czech thousands separators within numbers (e.g. 48 000 -> 48\u00A0000).
     */
    fun formatTypographicBlock(text: String): String {
        if (text.isBlank()) return text
        var result = text

        // 1. Bind section signs: "§ 15" -> "§\u00A015"
        result = SECTION_SIGN_REGEX.replace(result, "§\u00A0")

        // 2. Czech thousands separators within multi-digit numbers: "48 000 000" -> "48\u00A0000\u00A0000"
        var prevResult: String
        var pass = 0
        do {
            prevResult = result
            result = THOUSANDS_SPACE_REGEX.replace(result, "$1\u00A0$2")
            pass++
        } while (result != prevResult && pass < 5)

        // 3. Numbers with currency, units, or percentages: "1,700 Kč", "48k CZK", "15 %"
        result = NUMBER_UNIT_REGEX.replace(result, "$1\u00A0$2")
        result = COMPACT_K_UNIT_REGEX.replace(result, "$1\u00A0$2")
        result = DURATION_UNIT_REGEX.replace(result, "$1\u00A0$2")

        // 4. Single-letter prepositions & conjunctions (Czech: k, s, v, z, o, u, a, i; English: a, I)
        // Matches when preceded by whitespace, NBSP, opening punctuation, or start of string/line.
        result = SINGLE_LETTER_PREP_REGEX.replace(result, "$1\u00A0")
        result = SINGLE_LETTER_PREP_REGEX.replace(result, "$1\u00A0")

        // 5. Ordinals: "1st child" -> "1st\u00A0child", "2nd child" -> "2nd\u00A0child"
        result = ORDINAL_UNIT_REGEX.replace(result, "$1\u00A0$2")

        return result
    }

    fun fmtRelativeTime(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
        if (timestamp == null || timestamp <= 0L) return "Never checked"
        val diff = now - timestamp
        if (diff < 0) return "Just now"
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val months = days / 30

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days < 30 -> if (days == 1L) "Yesterday" else "$days days ago"
            months < 12 -> if (months == 1L) "1 month ago" else "$months months ago"
            else -> {
                val years = months / 12
                if (years == 1L) "1 year ago" else "$years years ago"
            }
        }
    }
}

private val MULTI_DOT_THOUSANDS_REGEX = Regex("^-?\\d{1,3}(\\.\\d{3}){2,}$")
private val MULTI_COMMA_THOUSANDS_REGEX = Regex("^-?\\d{1,3}(,\\d{3}){2,}$")

fun String.parseUserAmountOrNull(): Double? {
    val cleaned = this.replace("\u00A0", "")
        .replace("\u202F", "")
        .replace(" ", "")
        .replace("\t", "")
        .trim()
    if (cleaned.isEmpty()) return null
    val normalized = when {
        cleaned.contains(',') && cleaned.contains('.') -> {
            if (cleaned.lastIndexOf('.') > cleaned.lastIndexOf(',')) {
                cleaned.replace(",", "")
            } else {
                cleaned.replace(".", "").replace(",", ".")
            }
        }
        MULTI_COMMA_THOUSANDS_REGEX.matches(cleaned) -> cleaned.replace(",", "")
        MULTI_DOT_THOUSANDS_REGEX.matches(cleaned) -> cleaned.replace(".", "")
        cleaned.count { it == ',' } > 1 -> return null
        cleaned.count { it == '.' } > 1 -> return null
        else -> cleaned.replace(",", ".")
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

