package com.example

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    private fun pollUntil(timeoutMs: Long = 8000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(25)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    @Test
    fun test7_1_updateSettings() = runTest {
        val newSettings = SettingsEntity(portfolioNominalReturnPct = 12.34)
        viewModel.updateSettings(newSettings)
        assertNotNull(viewModel.settingsState.value)
    }

    @Test
    fun test7_2_resetSettingsToDefault() = runTest {
        val states = mutableListOf<SettingsEntity>()
        val collectJob = launch(Dispatchers.Default) { viewModel.settingsState.collect { states.add(it) } }
        pollUntil { states.isNotEmpty() }

        viewModel.updateSettings(SettingsEntity(portfolioNominalReturnPct = 99.0))
        pollUntil { states.any { it.portfolioNominalReturnPct == 99.0 } }

        viewModel.resetSettingsToDefault()
        pollUntil { states.lastOrNull()?.portfolioNominalReturnPct == 7.0 }
        collectJob.cancel()

        assertTrue("Settings write of 99.0 must be observed", states.any { it.portfolioNominalReturnPct == 99.0 })
        assertTrue("Settings must be observed", states.isNotEmpty())
        assertEquals(7.0, states.last().portfolioNominalReturnPct, 0.01)
    }

    @Test
    fun test7_3_clearAllUserData() = runTest {
        val settingsStates = mutableListOf<SettingsEntity>()
        val ledgerStates = mutableListOf<List<LedgerEntryEntity>>()
        val collectSettings = launch(Dispatchers.Default) { viewModel.settingsState.collect { settingsStates.add(it) } }
        val collectLedger = launch(Dispatchers.Default) { viewModel.ledgerEntries.collect { ledgerStates.add(it) } }
        pollUntil { settingsStates.isNotEmpty() }

        viewModel.updateSettings(SettingsEntity(portfolioNominalReturnPct = 88.0))
        viewModel.addLedgerEntry("2024-01", 100.0, 100.0, 0.0, 50.0, 50.0, "notes")
        pollUntil { ledgerStates.any { it.isNotEmpty() } && settingsStates.any { it.portfolioNominalReturnPct == 88.0 } }

        viewModel.clearAllUserData()
        pollUntil {
            settingsStates.lastOrNull()?.portfolioNominalReturnPct == 7.0 &&
                    ledgerStates.lastOrNull()?.isEmpty() == true
        }
        collectSettings.cancel()
        collectLedger.cancel()

        assertTrue("Settings must be observed", settingsStates.isNotEmpty())
        assertTrue("Ledger must be observed", ledgerStates.isNotEmpty())
        assertEquals(7.0, settingsStates.last().portfolioNominalReturnPct, 0.01)
        assertTrue("Ledger must be empty after clearAllUserData", ledgerStates.last().isEmpty())
    }

    @Test
    fun test7_4_setSensitivityOverrides() {
        viewModel.setSensitivityOverrides(11.0, 4.0, 3.5)

        assertEquals(11.0, viewModel.sensitivityReturnOverride.value)
        assertEquals(4.0, viewModel.sensitivityCpiOverride.value)
        assertEquals(3.5, viewModel.sensitivitySwrOverride.value)
    }

    @Test
    fun test7_5_clearSensitivityOverrides() {
        viewModel.setSensitivityOverrides(11.0, 4.0, 3.5)
        viewModel.setSensitivityOverrides(null, null, null)

        assertNull(viewModel.sensitivityReturnOverride.value)
        assertNull(viewModel.sensitivityCpiOverride.value)
        assertNull(viewModel.sensitivitySwrOverride.value)
    }

    @Test
    fun test7_6_addLedgerEntry() = runTest {
        viewModel.addLedgerEntry("2024-01", 1000.0, 2000.0, 0.0, 500.0, 300.0, "Test entry")

        assertNotNull(viewModel.ledgerEntries.value)
    }

    @Test
    fun test7_7_toggleAction() = runTest {
        viewModel.toggleAction(2025, "action_1", false)

        assertNotNull(viewModel.actionStates.value)
    }

    @Test
    fun test7_8_isSyncingStateFlow() {
        val syncing = viewModel.isSyncing.value
        assertFalse(syncing)
    }

    @Test
    fun test7_9_importCsvData_invalidUri() = runTest {
        val ledgerStates = mutableListOf<List<LedgerEntryEntity>>()
        val collectJob = launch(Dispatchers.Default) { viewModel.ledgerEntries.collect { ledgerStates.add(it) } }
        pollUntil { ledgerStates.isNotEmpty() }

        val uri = Uri.parse("content://invalid/uri")
        viewModel.importCsvData(uri)
        Thread.sleep(400)
        shadowOf(Looper.getMainLooper()).idle()
        collectJob.cancel()

        assertTrue("Ledger must be observed", ledgerStates.isNotEmpty())
        assertTrue("Invalid URI import must not insert any ledger entries", ledgerStates.last().isEmpty())
    }

    @Test
    fun test7_10_importCsvData_corruptedOrValid() = runTest {
        val ledgerStates = mutableListOf<List<LedgerEntryEntity>>()
        val collectJob = launch(Dispatchers.Default) { viewModel.ledgerEntries.collect { ledgerStates.add(it) } }
        pollUntil { ledgerStates.isNotEmpty() }

        val uri = Uri.parse("file:///nonexistent.csv")
        viewModel.importCsvData(uri)
        Thread.sleep(400)
        shadowOf(Looper.getMainLooper()).idle()
        collectJob.cancel()

        assertTrue("Ledger must be observed", ledgerStates.isNotEmpty())
        assertTrue("Unreadable file import must not insert any ledger entries", ledgerStates.last().isEmpty())
    }
}
