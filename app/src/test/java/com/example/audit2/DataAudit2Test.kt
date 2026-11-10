package com.example.audit2

import com.example.data.ActionStateEntity
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.util.BackupManager
import com.example.util.CzechEconomicSyncService
import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataAudit2Test {

    // =========================================================================
    // 1. BACKUP MANAGER & ROUND-TRIP FIDELITY
    // =========================================================================

    @Test
    fun ok_settingsEntity_fullJsonRoundTripPreservesAllFields() {
        val original = SettingsEntity(
            baseYear = 2027,
            primaryAge = 27,
            primaryName = "Václav Custom",
            spouseName = "Eleonora Custom",
            isSingleHousehold = false,
            vSalary = 45000.0,
            vSalaryGrowthPct = 4.5,
            vMealVouchersMonthly = 2500.0,
            eStartingSalary = 30000.0,
            liquidPortfolioCurrent = 750000.0,
            emergencyReserveCurrent = 300000.0,
            dpsBalanceCurrent = 80000.0,
            dipBalanceCurrent = 50000.0,
            safeWithdrawalRatePct = 3.75,
            cpiInflationPct = 3.1,
            rentMonthly = 24000.0,
            groceriesMonthly = 6000.0,
            childExpensesEnabled = true,
            child1Enabled = true,
            child1BirthYear = 2024,
            child2Enabled = false,
            emergencyReserveMode = "Target",
            emergencyReserveTarget = 400000.0
        )

        val json = BackupManager.serializeSettingsToJson(original)
        assertNotNull(json)
        assertTrue(json.contains("\"_format\":\"martinu-settings\""))

        val restored = BackupManager.deserializeSettingsFromJson(json, fallback = SettingsEntity())
        assertNotNull(restored)
        assertEquals(original.baseYear, restored!!.baseYear)
        assertEquals(original.primaryAge, restored.primaryAge)
        assertEquals(original.primaryName, restored.primaryName)
        assertEquals(original.spouseName, restored.spouseName)
        assertEquals(original.vSalary, restored.vSalary, 0.001)
        assertEquals(original.vSalaryGrowthPct, restored.vSalaryGrowthPct, 0.001)
        assertEquals(original.liquidPortfolioCurrent, restored.liquidPortfolioCurrent, 0.001)
        assertEquals(original.safeWithdrawalRatePct, restored.safeWithdrawalRatePct, 0.001)
        assertEquals(original.cpiInflationPct, restored.cpiInflationPct, 0.001)
        assertEquals(original.rentMonthly, restored.rentMonthly, 0.001)
        assertEquals(original.emergencyReserveMode, restored.emergencyReserveMode)
        assertEquals(original.emergencyReserveTarget, restored.emergencyReserveTarget, 0.001)
    }

    @Test
    fun ok_backupManager_rejectsMalformedOrEmptyJson() {
        val fallback = SettingsEntity(vSalary = 50000.0)
        assertNull(BackupManager.deserializeSettingsFromJson("not a json", fallback))
        assertNull(BackupManager.deserializeSettingsFromJson("", fallback))
    }

    @Test
    fun ok_backupManager_fullBackupPayloadRoundTrip() {
        val settings = SettingsEntity(vSalary = 60000.0)
        val ledger = listOf(
            LedgerEntryEntity(
                id = 1L,
                yearMonth = "2026-01",
                incVaclav = 60000.0,
                incEleonora = 20000.0,
                incUnforeseen = 0.0,
                expRent = 21770.0,
                expGroceries = 4800.0,
                expOther = 5000.0,
                notes = "Test month",
                portfolioBalanceAtMonthEnd = 500000.0,
                pensionBalanceAtMonthEnd = 100000.0,
                emergencyReserveAtMonthEnd = 200000.0
            )
        )
        val actions = listOf(
            ActionStateEntity(
                actionKey = "ac01_2026",
                year = 2026,
                actionId = "ac01",
                isDone = true
            )
        )
        val txs = listOf(
            ImportedBankTransactionEntity(
                id = 100L,
                yearMonth = "2026-01",
                bankName = "MONETA",
                date = "2026-01-15",
                amount = -1500.0,
                counterpartyName = "Lidl",
                category = "GROCERIES"
            )
        )

        val payload = BackupManager.FullBackupPayload(
            settings = settings,
            ledgerEntries = ledger,
            actionStates = actions,
            importedTransactions = txs,
            preferences = mapOf("test_pref" to "value123")
        )

        val fullJson = BackupManager.serializeFullBackupToJson(payload)
        assertTrue(fullJson.contains("\"_format\":\"martinu-financials-full-backup\""))
        assertTrue(fullJson.contains("\"schemaVersion\":2") || fullJson.contains("\"_schema\":2"))

        val restoredPayload = BackupManager.deserializeFullBackupFromJson(fullJson, fallback = settings)
        assertNotNull(restoredPayload)
        assertEquals(1, restoredPayload!!.ledgerEntries.size)
        assertEquals("2026-01", restoredPayload.ledgerEntries[0].yearMonth)
        assertEquals(60000.0, restoredPayload.ledgerEntries[0].incVaclav, 0.01)

        assertEquals(1, restoredPayload.actionStates.size)
        assertTrue(restoredPayload.actionStates[0].isDone)

        assertEquals(1, restoredPayload.importedTransactions.size)
        assertEquals(-1500.0, restoredPayload.importedTransactions[0].amount, 0.01)

        assertEquals("value123", restoredPayload.preferences["test_pref"])
    }

    // =========================================================================
    // 2. CZECH ECONOMIC SYNC SERVICE & PARSERS
    // =========================================================================

    @Test
    fun ok_cnbRatesJson_parsesEurAndUsdAccurately() {
        val json = """
            {
                "rates": [
                    {"currencyCode": "EUR", "rate": 25.185, "validFor": "2026-10-17"},
                    {"currencyCode": "USD", "rate": 23.250, "validFor": "2026-10-17"}
                ]
            }
        """.trimIndent()

        val parsed = CzechEconomicSyncService.parseCnbRatesJson(json)
        assertEquals(25.185, parsed.first, 0.001)
        assertEquals(23.250, parsed.second, 0.001)
        assertEquals("2026-10-17", parsed.third)
    }

    @Test
    fun ok_cnbRatesTxt_parsesPipeDelimitedFallback() {
        val txt = """
            17.10.2026 #203
            země|měna|množství|kód|kurz
            EMU|euro|1|EUR|25,185
            USA|dolar|1|USD|23,250
        """.trimIndent()

        val parsed = CzechEconomicSyncService.parseCnbRatesTxt(txt)
        assertEquals(25.185, parsed.first, 0.001)
        assertEquals(23.250, parsed.second, 0.001)
        assertEquals("17.10.2026", parsed.third)
    }

    // =========================================================================
    // 3. FORMATTERS & LOCALE RESILIENCE
    // =========================================================================

    @Test
    fun ok_formatters_currencyAndCompactFormats() {
        val czk = Formatters.fmtCZK(1250000.0)
        assertTrue("Must contain million digits", czk.contains("1") && czk.contains("250"))
        assertTrue("Must contain Kč currency suffix", czk.contains("Kč"))

        val compactM = Formatters.fmtCompact(1500000.0)
        assertTrue("1.5M should be formatted compactly", compactM.contains("1.5") || compactM.contains("1,5"))

        val pct = Formatters.fmtPct(7.25)
        assertTrue("Must contain percentage symbol", pct.contains("%"))
        assertTrue(pct.contains("7.3") || pct.contains("7,3") || pct.contains("7.25") || pct.contains("7,25"))
    }
}
