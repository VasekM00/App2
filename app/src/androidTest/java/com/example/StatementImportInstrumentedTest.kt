package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.MainViewModel
import com.example.util.BankType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end verification of the real user flow: pick a bank statement file ->
 * preview parse summary -> confirm -> categorized ledger entry + bank badge persisted.
 * Runs against a real Room database on the device.
 */
@RunWith(AndroidJUnit4::class)
class StatementImportInstrumentedTest {

    private lateinit var application: Application
    private lateinit var viewModel: MainViewModel
    private var tempFile: File? = null

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(application)
    }

    @Test
    fun monetaStatementPreviewThenConfirmPersistsLedgerAndBadge() = runBlocking {
        val csv = """
            Datum zaúčtování;Číslo protiúčtu;Název protiúčtu;Částka;Měna;Zpráva pro příjemce;Variabilní symbol
            15.09.2026;123456/0800;Zaměstnavatel s.r.o.;75 000,00;CZK;Mzda za 08/2026;
            18.09.2026;987654/0300;Pronajímatel Novák;-25 000,00;CZK;Nájem září 2026;1234
            20.09.2026;111222/2010;WOOD Retail Solutions a.s.;-10 000,00;CZK;Portu DCA;
            22.09.2026;333444/0100;Albert Česká republika s.r.o.;-2 500,50;CZK;Nákup potravin;
        """.trimIndent()

        tempFile = File(application.cacheDir, "moneta_e2e_${System.currentTimeMillis()}.csv").apply {
            writeText(csv)
        }

        // 1. User picks the file -> parser produces a review summary
        viewModel.importCsvData(Uri.fromFile(tempFile))
        val pending = withTimeoutOrNull(15_000) {
            viewModel.pendingStatementImport.first { it != null }
        }
        assertNotNull("A statement preview must be produced for a valid Moneta CSV", pending)
        pending!!
        assertEquals(BankType.MONETA, pending.detectedBank)
        assertTrue("Preview must contain parsed transactions", pending.transactions.isNotEmpty())

        // 2. User confirms -> transaction is committed to the ledger
        viewModel.confirmStatementImport(pending)

        val entries = withTimeoutOrNull(15_000) {
            viewModel.ledgerEntries.first { list -> list.any { it.yearMonth == "2026-09" } }
        }
        assertNotNull("Confirmed import must create a ledger entry for 2026-09", entries)
        val september = entries!!.first { it.yearMonth == "2026-09" }
        assertEquals(75_000.0, september.incVaclav, 1.0)
        assertEquals(25_000.0, september.expRent, 1.0)
        assertEquals(2_500.50, september.expGroceries, 1.0)

        // 3. The emergency reserve must stay the user's configured reserve,
        //    never the statement's closing balance.
        assertEquals(
            viewModel.settingsState.value.emergencyReserveCurrent,
            september.emergencyReserveAtMonthEnd,
            1.0
        )

        // 4. Bank provenance badge appears for that month
        val badges = withTimeoutOrNull(10_000) {
            viewModel.importedBankSourcesByMonth.first { map ->
                map["2026-09"]?.contains(BankType.MONETA.name) == true
            }
        }
        assertNotNull("Imported bank badge must appear for 2026-09", badges)
    }

    @After
    fun tearDown() {
        tempFile?.let { if (it.exists()) it.delete() }
    }
}
