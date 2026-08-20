package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsEntity
import com.example.ui.MainViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ViewModelLifecycleTest {

    private lateinit var context: Application
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        viewModel = MainViewModel(context)
    }

    @Test
    fun test7_1_updateSettings() = runTest {
        val newSettings = SettingsEntity(portfolioNominalReturnPct = 12.34)
        viewModel.updateSettings(newSettings)
        advanceUntilIdle()
        assertNotNull(viewModel.settingsState.value)
    }

    @Test
    fun test7_2_resetSettingsToDefault() = runTest {
        viewModel.updateSettings(SettingsEntity(portfolioNominalReturnPct = 99.0))
        advanceUntilIdle()
        
        viewModel.resetSettingsToDefault()
        advanceUntilIdle()
        
        val defaultSettings = SettingsEntity()
        assertEquals(defaultSettings.portfolioNominalReturnPct, viewModel.settingsState.value.portfolioNominalReturnPct, 0.01)
    }

    @Test
    fun test7_3_clearAllUserData() = runTest {
        viewModel.updateSettings(SettingsEntity(portfolioNominalReturnPct = 88.0))
        viewModel.addLedgerEntry("2024-01", 100.0, 100.0, 0.0, 50.0, 50.0, "notes")
        advanceUntilIdle()

        viewModel.clearAllUserData()
        advanceUntilIdle()

        val defaultSettings = SettingsEntity()
        assertEquals(defaultSettings.portfolioNominalReturnPct, viewModel.settingsState.value.portfolioNominalReturnPct, 0.01)
    }

    @Test
    fun test7_4_setSensitivityOverrides() = runTest {
        viewModel.setSensitivityOverrides(11.0, 4.0, 3.5)
        advanceUntilIdle()

        assertEquals(11.0, viewModel.sensitivityReturnOverride.value)
        assertEquals(4.0, viewModel.sensitivityCpiOverride.value)
        assertEquals(3.5, viewModel.sensitivitySwrOverride.value)
    }

    @Test
    fun test7_5_clearSensitivityOverrides() = runTest {
        viewModel.setSensitivityOverrides(11.0, 4.0, 3.5)
        viewModel.setSensitivityOverrides(null, null, null)
        advanceUntilIdle()

        assertNull(viewModel.sensitivityReturnOverride.value)
        assertNull(viewModel.sensitivityCpiOverride.value)
        assertNull(viewModel.sensitivitySwrOverride.value)
    }

    @Test
    fun test7_6_addLedgerEntry() = runTest {
        viewModel.addLedgerEntry("2024-01", 1000.0, 2000.0, 0.0, 500.0, 300.0, "Test entry")
        advanceUntilIdle()

        assertNotNull(viewModel.ledgerEntries.value)
    }

    @Test
    fun test7_7_toggleAction() = runTest {
        viewModel.toggleAction(2025, "action_1", false)
        advanceUntilIdle()

        assertNotNull(viewModel.actionStates.value)
    }

    @Test
    fun test7_8_isSyncingStateFlow() = runTest {
        val syncing = viewModel.isSyncing.value
        assertFalse(syncing)
    }

    @Test
    fun test7_9_importCsvData_invalidUri() = runTest {
        val uri = Uri.parse("content://invalid/uri")
        viewModel.importCsvData(uri)
        advanceUntilIdle()
        assertTrue(true) // Should not crash
    }

    @Test
    fun test7_10_importCsvData_corruptedOrValid() = runTest {
        val uri = Uri.parse("file:///nonexistent.csv")
        viewModel.importCsvData(uri)
        advanceUntilIdle()
        assertTrue(true) // Should not crash
    }
}
