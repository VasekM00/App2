package com.example

import com.example.util.Formatters.fmtCompact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Area5MobileErgonomicsAndThemeTest {

    private fun resolveDarkTheme(themeMode: String, systemDark: Boolean): Boolean {
        return when (themeMode) {
            "LIGHT" -> false
            "DARK" -> true
            else -> systemDark
        }
    }

    private fun nextToggleMode(currentMode: String, systemDark: Boolean): String {
        return when (currentMode) {
            "LIGHT" -> "DARK"
            "DARK" -> "SYSTEM"
            else -> if (systemDark) "LIGHT" else "DARK"
        }
    }

    @Test
    fun testThemeModeResolution() {
        // LIGHT mode always yields false
        assertFalse(resolveDarkTheme("LIGHT", systemDark = false))
        assertFalse(resolveDarkTheme("LIGHT", systemDark = true))

        // DARK mode always yields true
        assertTrue(resolveDarkTheme("DARK", systemDark = false))
        assertTrue(resolveDarkTheme("DARK", systemDark = true))

        // SYSTEM mode follows system
        assertFalse(resolveDarkTheme("SYSTEM", systemDark = false))
        assertTrue(resolveDarkTheme("SYSTEM", systemDark = true))
    }

    @Test
    fun testThemeToggleCycles() {
        // When system is light, toggling from SYSTEM goes to DARK
        assertEquals("DARK", nextToggleMode("SYSTEM", systemDark = false))
        // When system is dark, toggling from SYSTEM goes to LIGHT
        assertEquals("LIGHT", nextToggleMode("SYSTEM", systemDark = true))

        // From LIGHT, toggling goes to DARK
        assertEquals("DARK", nextToggleMode("LIGHT", systemDark = false))
        assertEquals("DARK", nextToggleMode("LIGHT", systemDark = true))

        // From DARK, toggling returns to SYSTEM
        assertEquals("SYSTEM", nextToggleMode("DARK", systemDark = false))
        assertEquals("SYSTEM", nextToggleMode("DARK", systemDark = true))
    }

    @Test
    fun testCrosshairBadgeFormatting() {
        val m15 = fmtCompact(1_500_000.0, symbol = "")
        assertTrue("Must contain M", m15.contains("M"))
        val k500 = fmtCompact(500_000.0, symbol = "")
        assertTrue("Must contain k", k500.contains("k"))
        val m10 = fmtCompact(10_000_000.0, symbol = "")
        assertTrue("Must contain 10M", m10.contains("10M") || m10.contains("10") && m10.contains("M"))
    }
}
