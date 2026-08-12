package com.example.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Formatters {
    private val czkLocale = Locale.forLanguageTag("cs-CZ")

    private val czkFormat = NumberFormat.getNumberInstance(czkLocale).apply {
        maximumFractionDigits = 0
    }

    fun fmtCZK(value: Double, symbol: String = "Kč"): String {
        if (value.isNaN() || value.isInfinite()) return "--\u00A0$symbol"
        return "${czkFormat.format(value.roundToInt())}\u00A0$symbol"
    }

    fun fmtCompact(value: Double, symbol: String = "Kč"): String {
        if (value.isNaN() || value.isInfinite()) return "--\u00A0$symbol"
        val absVal = abs(value)
        return when {
            absVal >= 1_000_000 -> String.format(czkLocale, "%.2fM\u00A0$symbol", value / 1_000_000.0)
            else -> fmtCZK(value, symbol)
        }
    }

    fun fmtPct(value: Double, digits: Int? = null): String {
        if (value.isNaN() || value.isInfinite()) return "--%"
        if (digits != null) {
            return String.format(czkLocale, "%.${digits}f%%", value)
        }
        val isWhole = (value % 1.0) == 0.0
        return if (isWhole) {
            String.format(czkLocale, "%.0f%%", value)
        } else {
            String.format(czkLocale, "%.2f%%", value)
        }
    }
}
