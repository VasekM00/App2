package com.example

import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersFuzzingAndAdversarialTest {

    @Test
    fun testNegativeZeroPercent_formatsAsPositiveZero() {
        val res1 = Formatters.fmtPct(-0.0)
        assertEquals("0%", res1)

        val res2 = Formatters.fmtPct(-0.0, digits = 1)
        assertEquals("0,0%", res2)

        val res3 = Formatters.fmtPct(-1e-11)
        assertEquals("0%", res3)
    }

    @Test
    fun testNanAndInfinity_formatGracefully() {
        assertEquals("--%", Formatters.fmtPct(Double.NaN))
        assertEquals("--%", Formatters.fmtPct(Double.POSITIVE_INFINITY))
        assertEquals("--%", Formatters.fmtPct(Double.NEGATIVE_INFINITY))

        assertEquals("--", Formatters.fmtNum(Double.NaN))
        assertEquals("--", Formatters.fmtNum(Double.POSITIVE_INFINITY))
        assertEquals("--", Formatters.fmtNum(Double.NEGATIVE_INFINITY))

        assertEquals("--", Formatters.fmtCompact(Double.NaN))
        assertEquals("--", Formatters.fmtCompact(Double.POSITIVE_INFINITY))

        assertEquals("--", Formatters.fmtCZK(Double.NaN))
        assertEquals("--", Formatters.fmtCZK(Double.POSITIVE_INFINITY))
        assertEquals("--", Formatters.fmtCZK(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun testExtremeDoubleValues_doNotCrashOrThrow() {
        val maxCzk = Formatters.fmtCZK(Double.MAX_VALUE)
        assertTrue(maxCzk.contains("Kč"))

        val minCzk = Formatters.fmtCZK(Double.MIN_VALUE)
        assertTrue(minCzk.contains("Kč"))

        val negMaxCzk = Formatters.fmtCZK(-Double.MAX_VALUE)
        assertTrue(negMaxCzk.startsWith("-") || negMaxCzk.contains("Kč"))
    }

    @Test
    fun testRoundingBehaviors() {
        assertEquals(0.0, Formatters.roundToDisplay(0.0), 0.0001)
        assertEquals(50.0, Formatters.roundToDisplay(50.0), 0.0001)
        assertEquals(190.0, Formatters.roundToDisplay(190.0), 0.0001)
        assertEquals(200.0, Formatters.roundToDisplay(204.0), 0.0001)
        assertEquals(210.0, Formatters.roundToDisplay(206.0), 0.0001)

        assertEquals(10_000.0, Formatters.roundTo10k(9_500.0), 0.0001)
        assertEquals(0.0, Formatters.roundTo10k(4_999.0), 0.0001)

        assertEquals(1_000.0, Formatters.roundTo1k(1_200.0), 0.0001)
        assertEquals(2_000.0, Formatters.roundTo1k(1_600.0), 0.0001)
    }

    @Test
    fun testCzechTypographicGlue_replacesPrepositionSpacesWithNBSP() {
        val input = "Cesta v Praze k lesu s rodinou o víkendu"
        val output = Formatters.formatTypographicBlock(input)
        assertFalse(output.contains(" v "))
        assertFalse(output.contains(" k "))
        assertFalse(output.contains(" s "))
        assertFalse(output.contains(" o "))
        assertTrue(output.contains("v\u00A0"))
        assertTrue(output.contains("k\u00A0"))
        assertTrue(output.contains("s\u00A0"))
        assertTrue(output.contains("o\u00A0"))
    }

    @Test
    fun testRelativeTime_handlesPastAndFutureTimestampsSafely() {
        val now = 1_775_000_000_000L
        assertEquals("Never checked", Formatters.fmtRelativeTime(null, now))
        assertEquals("Never checked", Formatters.fmtRelativeTime(0L, now))
        assertEquals("Never checked", Formatters.fmtRelativeTime(-100L, now))
        assertEquals("Just now", Formatters.fmtRelativeTime(now + 5000L, now))
        assertEquals("Just now", Formatters.fmtRelativeTime(now - 1000L, now))
        assertEquals("1 minute ago", Formatters.fmtRelativeTime(now - 65_000L, now))
        assertEquals("5 minutes ago", Formatters.fmtRelativeTime(now - 300_000L, now))
        assertEquals("1 hour ago", Formatters.fmtRelativeTime(now - 3_700_000L, now))
        assertEquals("Yesterday", Formatters.fmtRelativeTime(now - 86_400_000L, now))
        assertEquals("1 month ago", Formatters.fmtRelativeTime(now - 30L * 86_400_000L, now))
        assertEquals("1 year ago", Formatters.fmtRelativeTime(now - 365L * 86_400_000L, now))
    }
}
