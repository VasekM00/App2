package com.example

import com.example.data.SettingsEntity
import com.example.util.BackupManager
import com.example.util.BankStatementImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Hardening tests for statement import (date validation, size limits) and backup format markers.
 */
@RunWith(RobolectricTestRunner::class)
class ImportHardeningTest {

    @Test
    fun rejectsCalendarInvalidDates() {
        assertEquals("", BankStatementImporter.normalizeDate("31.02.2026"))
        assertEquals("", BankStatementImporter.normalizeDate("2026-13-01"))
        assertEquals("", BankStatementImporter.normalizeDate("00.01.2026"))
        assertEquals("", BankStatementImporter.normalizeDate("2026-00-10"))
        assertEquals("", BankStatementImporter.normalizeDate("29.2.2023"))
        assertEquals("", BankStatementImporter.normalizeDate("not a date"))
        assertEquals("", BankStatementImporter.normalizeDate(""))
    }

    @Test
    fun acceptsValidDatesInBothOrders() {
        assertEquals("2026-02-28", BankStatementImporter.normalizeDate("28.02.2026"))
        assertEquals("2024-02-29", BankStatementImporter.normalizeDate("29.2.2024"))
        assertEquals("2026-12-31", BankStatementImporter.normalizeDate("2026/12/31"))
        assertEquals("2026-07-04", BankStatementImporter.normalizeDate("2026-7-4"))
    }

    @Test
    fun enforcesStatementSizeLimit() {
        assertFalse(BankStatementImporter.isWithinSizeLimit(0))
        assertTrue(BankStatementImporter.isWithinSizeLimit(1))
        assertTrue(BankStatementImporter.isWithinSizeLimit(BankStatementImporter.MAX_STATEMENT_BYTES))
        assertFalse(BankStatementImporter.isWithinSizeLimit(BankStatementImporter.MAX_STATEMENT_BYTES + 1))
    }

    @Test
    fun backupEmbedsSchemaMarkerAndRoundTrips() {
        val json = BackupManager.serializeSettingsToJson(SettingsEntity(primaryName = "Marker Test"))
        assertTrue(json.contains("\"_format\""))
        assertTrue(json.contains("\"_schema\""))

        val restored = BackupManager.deserializeSettingsFromJson(json, SettingsEntity())
        assertNotNull(restored)
        assertEquals("Marker Test", restored!!.primaryName)
    }

    @Test
    fun legacyBackupWithoutMarkerStillRestores() {
        val legacy = "{\"primaryName\":\"Legacy\",\"vSalary\":50000.0}"
        val restored = BackupManager.deserializeSettingsFromJson(legacy, SettingsEntity())
        assertNotNull(restored)
        assertEquals("Legacy", restored!!.primaryName)
        assertEquals(50000.0, restored.vSalary, 0.001)
    }
}
