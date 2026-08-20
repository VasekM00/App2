package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.SettingsEntity
import com.example.ui.MainViewModel
import com.example.ui.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RuntimePermissionInstrumentedTest {

    private lateinit var application: Application
    private lateinit var viewModel: MainViewModel
    private var tempCsvFile: File? = null

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(application)
    }

    @After
    fun tearDown() {
        tempCsvFile?.let {
            if (it.exists()) it.delete()
        }
    }

    /**
     * Test 1: CSV import permission handling & Uri reading.
     * Validates that:
     *  - Valid CSV file Uri is opened, parsed, entries are inserted, and success snackbar is emitted.
     *  - Inaccessible/invalid Uri is handled gracefully without crashing, emitting an error notification.
     */
    @Test
    fun testCsvImportPermissionHandlingAndUriReading() = runTest {
        // 1. Prepare a valid CSV file in the app's cache directory
        tempCsvFile = File(application.cacheDir, "test_ledger_import_${System.currentTimeMillis()}.csv").apply {
            writeText(
                """
                YearMonth,IncVaclav,IncEleonora,IncExtra,ExpRent,ExpGroceries,ExpOther,Notes
                2026-04,36000.0,13000.0,500.0,21770.0,4800.0,1200.0,April Payroll
                2026-05,37000.0,13000.0,0.0,21770.0,5100.0,800.0,May Payroll
                """.trimIndent()
            )
        }
        val validUri = Uri.fromFile(tempCsvFile)

        // Collect UI snackbar events concurrently
        val emittedMessages = mutableListOf<String>()
        val collectJob = launch(Dispatchers.IO) {
            viewModel.uiEvent.collect { event ->
                if (event is UiMessage.ShowSnackbar) {
                    emittedMessages.add(event.message)
                }
            }
        }

        // Import valid CSV Uri
        viewModel.importCsvData(validUri)

        // Wait for processing and emission
        var successMessageEmitted = false
        for (i in 1..20) {
            delay(100)
            if (emittedMessages.any { it.contains("Successfully imported 2 entries") }) {
                successMessageEmitted = true
                break
            }
        }
        assertTrue("Expected successful CSV import message to be emitted", successMessageEmitted)

        // Verify entries were saved in the database
        val entries = viewModel.ledgerEntries.first { it.isNotEmpty() }
        assertTrue("Expected imported entries in ledger", entries.any { it.yearMonth == "2026-04" })
        assertTrue("Expected imported entries in ledger", entries.any { it.yearMonth == "2026-05" })

        // 2. Test reading an invalid / inaccessible Uri without crash
        emittedMessages.clear()
        val invalidUri = Uri.parse("content://invalid.nonexistent.authority.provider/file.csv")
        viewModel.importCsvData(invalidUri)

        var failureMessageEmitted = false
        for (i in 1..20) {
            delay(100)
            if (emittedMessages.any { it.contains("CSV import failed") || it.contains("Error") || it.contains("Unable to open") }) {
                failureMessageEmitted = true
                break
            }
        }
        assertTrue("Expected error snackbar when invalid Uri cannot be read", failureMessageEmitted)

        collectJob.cancel()
    }

    /**
     * Test 2: Notification & snackbar flow validation.
     * Validates that UI event streams correctly emit expected snackbar messages for all core user actions.
     */
    @Test
    fun testNotificationAndSnackbarFlowValidation() = runTest {
        val emittedMessages = mutableListOf<String>()
        val collectJob = launch(Dispatchers.IO) {
            viewModel.uiEvent.collect { event ->
                if (event is UiMessage.ShowSnackbar) {
                    emittedMessages.add(event.message)
                }
            }
        }

        // Action 1: Add a ledger entry
        viewModel.addLedgerEntry("2026-08", 35000.0, 13000.0, 0.0, 21770.0, 4800.0, "August Budget")
        var received = waitForMessage(emittedMessages) { it.contains("Ledger entry added for 2026-08") }
        assertTrue("Expected snackbar for adding ledger entry", received)

        // Action 2: Update settings with notification
        emittedMessages.clear()
        viewModel.updateSettings(SettingsEntity(primaryName = "Václav Test"), showSnackbar = true)
        received = waitForMessage(emittedMessages) { it.contains("Settings saved successfully") }
        assertTrue("Expected snackbar for saving settings", received)

        // Action 3: Reset settings to default
        emittedMessages.clear()
        viewModel.resetSettingsToDefault()
        received = waitForMessage(emittedMessages) { it.contains("Reset all settings to default") }
        assertTrue("Expected snackbar for reset settings", received)

        // Action 4: Clear all user data
        emittedMessages.clear()
        viewModel.clearAllUserData()
        received = waitForMessage(emittedMessages) { it.contains("All user data cleared") }
        assertTrue("Expected snackbar for clearing all data", received)

        collectJob.cancel()
    }

    private suspend fun waitForMessage(messages: List<String>, predicate: (String) -> Boolean): Boolean {
        for (i in 1..25) {
            delay(100)
            if (messages.any(predicate)) return true
        }
        return false
    }
}
