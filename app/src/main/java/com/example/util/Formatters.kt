package com.example.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Formatters {
    private val czkLocale = Locale.forLanguageTag("cs-CZ")

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

    fun fmtNum(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val displayVal = roundToDisplay(value)
        return getCzkFormat().format(displayVal.roundToLong()).replace(' ', '\u00A0')
    }

    fun fmtCZK(value: Double, symbol: String = "Kč"): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val displayVal = roundToDisplay(value)
        val formatted = getCzkFormat().format(displayVal.roundToLong()).replace(' ', '\u00A0')
        return if (symbol.isBlank()) formatted else "$formatted\u00A0$symbol"
    }

    fun fmtCompact(value: Double, symbol: String = "Kč"): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val absVal = abs(value)
        val numStr = when {
            absVal >= 1_000_000_000 -> {
                val b = value / 1_000_000_000.0
                if (abs(b - b.roundToInt()) < 0.05) {
                    String.format(czkLocale, "%.0fB", b)
                } else {
                    String.format(czkLocale, "%.1fB", b)
                }
            }
            absVal >= 1_000_000 -> {
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

    fun fmtPct(value: Double, digits: Int? = null): String {
        if (value.isNaN() || value.isInfinite()) return "--%"
        val formatted = if (digits != null) {
            String.format(czkLocale, "%.${digits.coerceAtLeast(0)}f%%", value)
        } else {
            val isWhole = abs(value - kotlin.math.round(value)) < 1e-6
            if (isWhole) {
                String.format(czkLocale, "%.0f%%", value)
            } else {
                String.format(czkLocale, "%.1f%%", value)
            }
        }
        return formatted.replace(' ', '\u00A0')
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
        result = result.replace(Regex("§\\s+"), "§\u00A0")

        // 2. Czech thousands separators within multi-digit numbers: "48 000" -> "48\u00A0000"
        result = result.replace(Regex("(\\d{1,3})\\s+(\\d{3})"), "$1\u00A0$2")
        result = result.replace(Regex("(\\d{1,3})\\s+(\\d{3})"), "$1\u00A0$2")

        // 3. Numbers with currency, units, or percentages: "1,700 Kč", "48k CZK", "15 %"
        result = result.replace(Regex("(\\d+)\\s+(Kč|CZK|EUR|USD|%)"), "$1\u00A0$2")
        result = result.replace(Regex("(\\d+k)\\s+(Kč|CZK|EUR|USD)"), "$1\u00A0$2")
        result = result.replace(Regex("(\\d+)\\s+(let|roky|roků|rok|měsíců|měsíce|dní|dnů|hodin|years|months|days)"), "$1\u00A0$2")

        // 4. Single-letter prepositions & conjunctions (Czech: k, s, v, z, o, u, a, i; English: a, I)
        // Matches when preceded by whitespace, NBSP, opening punctuation, or start of string/line.
        val singleLetterRegex = Regex("(?<=[\\s\\u00A0\\u202F(\\[\"'“„]|^)([ksvzouaiIKSVZOUAI])\\s+")
        result = result.replace(singleLetterRegex, "$1\u00A0")
        result = result.replace(singleLetterRegex, "$1\u00A0")

        // 5. Ordinals: "1st child" -> "1st\u00A0child", "2nd child" -> "2nd\u00A0child"
        result = result.replace(Regex("(\\d+(?:st|nd|rd|th|\\.)?)\\s+(child|dítě|kroku|step|level)"), "$1\u00A0$2")

        return result
    }
}

