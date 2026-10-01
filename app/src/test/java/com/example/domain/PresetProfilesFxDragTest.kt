package com.example.domain

import com.example.data.SettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetProfilesFxDragTest {

    @Test
    fun testFxDragPresetProperties() {
        val preset = PresetProfiles.FX_DRAG_INFLATION
        assertEquals("fx_drag", preset.id)
        assertEquals("MACRO", preset.badge)
        assertEquals("FX Drag & CZK Divergence", preset.title)
        assertTrue(preset.description.contains("-1.0pp"))
        assertTrue(preset.description.contains("3.5%"))
        assertTrue(preset.description.contains("3.25%"))
    }

    @Test
    fun testFxDragPresetTransformation() {
        val base = SettingsEntity(
            portfolioNominalReturnPct = 8.5,
            cpiInflationPct = 2.5,
            safeWithdrawalRatePct = 3.5,
            monteCarloVolatilityPct = 15.0
        )

        val transformed = PresetProfiles.FX_DRAG_INFLATION.transform(base)

        // 8.5 - 1.0 = 7.5
        assertEquals(7.5, transformed.portfolioNominalReturnPct, 0.001)
        assertEquals(3.5, transformed.cpiInflationPct, 0.001)
        assertEquals(3.25, transformed.safeWithdrawalRatePct, 0.001)
        assertEquals(17.0, transformed.monteCarloVolatilityPct, 0.001)
    }

    @Test
    fun testFxDragPresetContainedInAllPresets() {
        val found = PresetProfiles.ALL_PRESETS.find { it.id == "fx_drag" }
        assertNotNull(found)
        assertEquals(PresetProfiles.FX_DRAG_INFLATION, found)
    }
}
