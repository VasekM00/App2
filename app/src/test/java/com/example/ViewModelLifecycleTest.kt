package com.example

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelLifecycleTest {

    private lateinit var context: Application
    private lateinit var viewModel: MainViewModel

    private fun resetDatabaseSingleton(app: Application) {
        runCatching {
            val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            val existing = field.get(null) as? AppDatabase
            existing?.close()
            field.set(null, null)
        }
        app.deleteDatabase("martinu_financials_db")
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        resetDatabaseSingleton(context)
        viewModel = MainViewModel(context)
    }

    @After
    fun tearDown() {
        runCatching {
            val job = viewModel.viewModelScope.coroutineContext[Job]
            runBlocking {
                job?.cancel()
                withTimeoutOrNull(3000) {
                    job?.join()
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        resetDatabaseSingleton(context)
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
        val states = CopyOnWriteArrayList<SettingsEntity>()
        val collectJob = launch(Dispatchers.Default) { viewModel.settingsState.collect { states.add(it) } }
        pollUntil { states.isNotEmpty() }

        val newSettings = SettingsEntity(portfolioNominalReturnPct = 12.34)
        viewModel.updateSettings(newSettings)
        val observed = pollUntil { states.any { it.portfolioNominalReturnPct == 12.34 } }
        collectJob.cancel()

        assertTrue("Updated portfolioNominalReturnPct=12.34 must be observed", observed)
        assertEquals(12.34, viewModel.settingsState.value.portfolioNominalReturnPct, 0.001)
    }

    @Test
    fun test7_1b_concurrentAtomicReduceSettingsDoesNotLoseEdits() = runTest {
        val states = CopyOnWriteArrayList<SettingsEntity>()
        val collectJob = launch(Dispatchers.Default) { viewModel.settingsState.collect { states.add(it) } }
        pollUntil { states.isNotEmpty() }

        viewModel.updateSettings { it.copy(vSalary = 111_000.0) }
        viewModel.updateSettings { it.copy(vGiftsMonthly = 15_000.0) }
        viewModel.updateSettings { it.copy(rentMonthly = 28_500.0) }

        val observed = pollUntil {
            val cur = viewModel.settingsState.value
            cur.vSalary == 111_000.0 && cur.vGiftsMonthly == 15_000.0 && cur.rentMonthly == 28_500.0
        }
        collectJob.cancel()

        assertTrue("All three concurrent reducer patches must persist without overwriting each other", observed)
    }

    @Test
    fun test7_2_resetSettingsToDefault() = runTest {
        val states = CopyOnWriteArrayList<SettingsEntity>()
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
        val settingsStates = CopyOnWriteArrayList<SettingsEntity>()
        val ledgerStates = CopyOnWriteArrayList<List<LedgerEntryEntity>>()
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
        val entries = CopyOnWriteArrayList<List<LedgerEntryEntity>>()
        val collectJob = launch(Dispatchers.Default) { viewModel.ledgerEntries.collect { entries.add(it) } }
        pollUntil { entries.isNotEmpty() }

        viewModel.addLedgerEntry("2024-01", 1000.0, 2000.0, 0.0, 500.0, 300.0, "Test entry")
        val inserted = pollUntil { entries.any { list -> list.any { it.yearMonth == "2024-01" && it.notes == "Test entry" } } }
        collectJob.cancel()

        assertTrue("Inserted ledger entry must be persisted and observed", inserted)
    }

    @Test
    fun test7_7_toggleAction() = runTest {
        val states = CopyOnWriteArrayList<Map<String, Boolean>>()
        val collectJob = launch(Dispatchers.Default) { viewModel.actionStates.collect { states.add(it) } }
        pollUntil { states.isNotEmpty() }

        viewModel.toggleAction(2025, "action_1", false)
        val toggled = pollUntil { states.any { it["2025_action_1"] == true } }
        collectJob.cancel()

        assertTrue("Toggled action state 2025_action_1=true must be persisted and observed", toggled)
    }

    @Test
    fun test7_8_isSyncingStateFlow() {
        val syncing = viewModel.isSyncing.value
        assertFalse(syncing)
    }

    @Test
    fun test7_9_importCsvData_invalidUri() = runTest {
        val ledgerStates = CopyOnWriteArrayList<List<LedgerEntryEntity>>()
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
        val ledgerStates = CopyOnWriteArrayList<List<LedgerEntryEntity>>()
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
