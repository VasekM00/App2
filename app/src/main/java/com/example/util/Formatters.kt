package com.example.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
        return getCzkFormat().format(displayVal.roundToInt()).replace(' ', '\u00A0')
    }

    fun fmtCZK(value: Double, symbol: String = "Kč"): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val displayVal = roundToDisplay(value)
        val formatted = getCzkFormat().format(displayVal.roundToInt()).replace(' ', '\u00A0')
        return if (symbol.isBlank()) formatted else "$formatted\u00A0$symbol"
    }

    fun fmtCompact(value: Double, symbol: String = "Kč"): String {
        if (value.isNaN() || value.isInfinite()) return "--"
        val absVal = abs(value)
        val numStr = when {
            absVal >= 1_000_000 -> {
                val mil = value / 1_000_000.0
                if (abs(mil - mil.roundToInt()) < 0.05) {
                    String.format(czkLocale, "%.0fM", mil)
                } else {
                    String.format(czkLocale, "%.1fM", mil)
                }
            }
            absVal >= 100_000 -> {
                val k = value / 1_000.0
                String.format(czkLocale, "%.0fk", k)
            }
            else -> {
                val displayVal = roundToDisplay(value)
                getCzkFormat().format(displayVal.roundToInt()).replace(' ', '\u00A0')
            }
        }
        return if (symbol.isBlank()) numStr else "$numStr\u00A0$symbol"
    }

    fun fmtPct(value: Double, digits: Int? = null): String {
        if (value.isNaN() || value.isInfinite()) return "--%"
        val formatted = if (digits != null) {
            String.format(czkLocale, "%.${digits}f%%", value)
        } else {
            val isWhole = (value % 1.0) == 0.0
            if (isWhole) {
                String.format(czkLocale, "%.0f%%", value)
            } else {
                String.format(czkLocale, "%.1f%%", value)
            }
        }
        return formatted.replace(' ', '\u00A0')
    }
}

