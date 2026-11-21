package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.ui.tabs.OverviewTab
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.MartinuFinancialsTheme
import com.example.util.Formatters.fmtCompact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Area5MobileErgonomicsAndThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = foreground.luminance()
        val l2 = background.luminance()
        val bright = maxOf(l1, l2)
        val dark = minOf(l1, l2)
        return (bright + 0.05) / (dark + 0.05)
    }

    private fun assertThemeContrast(scheme: ColorScheme, label: String, brandPairs: List<Pair<String, Color>>) {
        val onSurfaceSurface = contrastRatio(scheme.onSurface, scheme.surface)
        val onBackgroundBackground = contrastRatio(scheme.onBackground, scheme.background)
        val mutedOnSurface = contrastRatio(scheme.onSurfaceVariant, scheme.surface)
        val primaryOnSurface = contrastRatio(scheme.primary, scheme.surface)
        val onPrimaryContainer = contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer)
        val errorOnSurface = contrastRatio(scheme.error, scheme.surface)

        assertTrue("$label onSurface vs surface contrast was $onSurfaceSurface", onSurfaceSurface >= 4.5)
        assertTrue("$label onBackground vs background contrast was $onBackgroundBackground", onBackgroundBackground >= 4.5)
        assertTrue("$label onSurfaceVariant vs surface contrast was $mutedOnSurface", mutedOnSurface >= 4.5)
        assertTrue("$label primary vs surface contrast was $primaryOnSurface", primaryOnSurface >= 4.5)
        assertTrue("$label onPrimaryContainer vs primaryContainer contrast was $onPrimaryContainer", onPrimaryContainer >= 4.5)
        assertTrue("$label error vs surface contrast was $errorOnSurface", errorOnSurface >= 4.5)

        brandPairs.forEach { (name, color) ->
            val ratio = contrastRatio(color, scheme.surface)
            assertTrue("$label $name vs surface contrast was $ratio (< 3.0)", ratio >= 3.0)
        }
    }

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
        assertFalse(resolveDarkTheme("LIGHT", systemDark = false))
        assertFalse(resolveDarkTheme("LIGHT", systemDark = true))

        assertTrue(resolveDarkTheme("DARK", systemDark = false))
        assertTrue(resolveDarkTheme("DARK", systemDark = true))

        assertFalse(resolveDarkTheme("SYSTEM", systemDark = false))
        assertTrue(resolveDarkTheme("SYSTEM", systemDark = true))
    }

    @Test
    fun testThemeToggleCycles() {
        assertEquals("DARK", nextToggleMode("SYSTEM", systemDark = false))
        assertEquals("LIGHT", nextToggleMode("SYSTEM", systemDark = true))
        assertEquals("DARK", nextToggleMode("LIGHT", systemDark = false))
        assertEquals("DARK", nextToggleMode("LIGHT", systemDark = true))
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

    @Test
    fun testLightAndDarkThemesMeetWcagContrastAndRenderOverview() {
        val calcState = FinancialEngine.calculate(SettingsEntity(monteCarloN = 50), runMonteCarlo = false)
        var capturedLightScheme: ColorScheme? = null
        var capturedLightBrand: List<Pair<String, Color>> = emptyList()
        var capturedDarkScheme: ColorScheme? = null
        var capturedDarkBrand: List<Pair<String, Color>> = emptyList()

        composeTestRule.setContent {
            MartinuFinancialsTheme(darkTheme = false) {
                capturedLightScheme = MaterialTheme.colorScheme
                capturedLightBrand = listOf(
                    "BrandTeal" to BrandTeal,
                    "BrandGold" to BrandGold,
                    "BrandBlue" to BrandBlue,
                    "GoodGreen" to GoodGreen
                )
                OverviewTab(state = calcState)
            }
            MartinuFinancialsTheme(darkTheme = true) {
                capturedDarkScheme = MaterialTheme.colorScheme
                capturedDarkBrand = listOf(
                    "BrandTeal" to BrandTeal,
                    "BrandGold" to BrandGold,
                    "BrandBlue" to BrandBlue,
                    "GoodGreen" to GoodGreen
                )
                OverviewTab(state = calcState)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("hero_header_card").assertCountEquals(2)
        composeTestRule.onAllNodesWithTag("overview_tab").assertCountEquals(2)

        val light = requireNotNull(capturedLightScheme)
        val dark = requireNotNull(capturedDarkScheme)
        assertThemeContrast(light, "LightTheme", capturedLightBrand)
        assertThemeContrast(dark, "DarkTheme", capturedDarkBrand)
    }
}
