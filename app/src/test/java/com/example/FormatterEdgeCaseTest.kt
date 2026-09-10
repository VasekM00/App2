package com.example

import com.example.util.Formatters
import org.junit.Assert.*
import org.junit.Test

class FormatterEdgeCaseTest {

    @Test
    fun test5_1_fmtCZK_NaN() {
        val result = Formatters.fmtCZK(Double.NaN)
        assertEquals("--", result)
    }

    @Test
    fun test5_2_fmtCZK_positiveInfinity() {
        val result = Formatters.fmtCZK(Double.POSITIVE_INFINITY)
        assertEquals("--", result)
    }

    @Test
    fun test5_3_fmtCZK_zero() {
        val result = Formatters.fmtCZK(0.0)
        assertTrue(result.contains("0"))
        assertTrue(result.contains("Kč"))
    }

    @Test
    fun test5_4_fmtCZK_negative() {
        val result = Formatters.fmtCZK(-25000.0)
        assertTrue(result.contains("-") || result.contains("−"))
    }

    @Test
    fun test5_5_fmtCompact_millions() {
        val result = Formatters.fmtCompact(1_500_000.0)
        assertTrue(result.contains("M"))
    }

    @Test
    fun test5_6_fmtCompact_thousands() {
        val result = Formatters.fmtCompact(250_000.0)
        assertTrue(result.contains("k"))
    }

    @Test
    fun test5_7_fmtCompact_under100k() {
        val result = Formatters.fmtCompact(99_999.0)
        assertFalse(result.contains("k"))
        assertFalse(result.contains("M"))
    }

    @Test
    fun test5_8_fmtPct_wholeNumber() {
        val result = Formatters.fmtPct(3.0)
        assertTrue(result.contains("3%") || result.contains("3 %") || result.contains("3\u00A0%"))
    }

    @Test
    fun test5_9_fmtPct_fractional() {
        val result = Formatters.fmtPct(3.14)
        assertTrue(result.contains("3"))
        assertTrue(result.contains("%"))
    }

    @Test
    fun test5_10_fmtPct_NaN() {
        val result = Formatters.fmtPct(Double.NaN)
        assertEquals("--%", result)
    }

    @Test
    fun test5_11_roundToDisplay_under200() {
        val result = Formatters.roundToDisplay(199.0)
        assertEquals(199.0, result, 0.001)
    }

    @Test
    fun test5_12_roundToDisplay_over200() {
        val result = Formatters.roundToDisplay(201.0)
        assertEquals(200.0, result, 0.001)
    }

    @Test
    fun test5_13_fmtCompact_largeNumber() {
        val result = Formatters.fmtCompact(1_000_000_000_000.0)
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun test5_14_formatTypographicBlock_prepositions() {
        val input = "V bance a v obchodech s rodinou k 1. lednu o vikendu u stolu z uctu."
        val formatted = Formatters.formatTypographicBlock(input)
        assertFalse("Should not contain 'a ' with regular space", formatted.contains(" a "))
        assertFalse("Should not contain 'v ' with regular space", formatted.contains(" v "))
        assertFalse("Should not contain 's ' with regular space", formatted.contains(" s "))
        assertFalse("Should not contain 'k ' with regular space", formatted.contains(" k "))
        assertFalse("Should not contain 'o ' with regular space", formatted.contains(" o "))
        assertFalse("Should not contain 'u ' with regular space", formatted.contains(" u "))
        assertFalse("Should not contain 'z ' with regular space", formatted.contains(" z "))
        assertTrue("Should contain 'a\\u00A0'", formatted.contains("a\u00A0"))
        assertTrue("Should contain 'v\\u00A0'", formatted.contains("v\u00A0"))
    }

    @Test
    fun test5_15_formatTypographicBlock_chainedPrepositions() {
        val input = "a v Praze a i v Brne"
        val formatted = Formatters.formatTypographicBlock(input)
        assertEquals("a\u00A0v\u00A0Praze a\u00A0i\u00A0v\u00A0Brne", formatted)
    }

    @Test
    fun test5_16_formatTypographicBlock_numbersAndSections() {
        val input = "§ 15a ZDP with 48 000 Kč and 1 700 CZK at 15 %"
        val formatted = Formatters.formatTypographicBlock(input)
        assertTrue(formatted.contains("§\u00A015a"))
        assertTrue(formatted.contains("48\u00A0000\u00A0Kč"))
        assertTrue(formatted.contains("1\u00A0700\u00A0CZK"))
        assertTrue(formatted.contains("15\u00A0%"))
    }

    @Test
    fun test5_17_formatTypographicBlock_punctuation() {
        val input = "(v bance) [k domu] \"o vikendu\""
        val formatted = Formatters.formatTypographicBlock(input)
        assertTrue(formatted.contains("(v\u00A0bance)"))
        assertTrue(formatted.contains("[k\u00A0domu]"))
        assertTrue(formatted.contains("\"o\u00A0vikendu\""))
    }
}
