package com.example.ui.state

import com.example.data.AppPreferencesRepository
import com.example.data.FinancialRepository
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.util.CrossStatementAuditReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

internal class LedgerSyncController(
    private val repository: FinancialRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val liveSettings: MutableStateFlow<SettingsEntity>,
    private val pendingSettingsWrites: AtomicInteger,
    private val activeAuditReport: MutableStateFlow<CrossStatementAuditReport?>,
    private val importMutex: Mutex,
    private val emitSnackbar: suspend (String) -> Unit
) {
    private suspend fun syncLedgerToSettingsIfCurrentMonth(
        yearMonth: String,
        incVaclav: Double?,
        incEleonora: Double?,
        expRent: Double?,
        portfolioBalance: Double?,
        pensionBalance: Double?,
        emergencyReserve: Double?
    ) {
        val now = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
        val existingMax = repository.getAllLedgerEntriesDirect().map { it.yearMonth }.maxOrNull()
        val latestYm = if (existingMax != null && existingMax > now) existingMax else now
        val effectiveCurrentYm = if (yearMonth == now || yearMonth >= latestYm) yearMonth else now
        repository.syncManualLedgerToSettingsTransaction(
            yearMonth, effectiveCurrentYm, incVaclav, incEleonora, expRent, portfolioBalance, pensionBalance, emergencyReserve
        )
        if (pendingSettingsWrites.get() == 0) {
            liveSettings.value = repository.settingsFlow.first()
        }
    }

    suspend fun addLedgerEntryWithOptionalBalances(
        yearMonth: String,
        incVaclav: Double,
        incEleonora: Double,
        incUnforeseen: Double,
        expRent: Double,
        expLiving: Double,
        notes: String,
        portfolioBalance: Double?,
        pensionBalance: Double?,
        emergencyReserve: Double?
    ) {
        try {
            importMutex.withLock {
                var wasUpdated = false
                repository.withTransaction {
                    val existing = repository.getLedgerEntryByYearMonth(yearMonth)
                    wasUpdated = existing != null
                    val entry = buildMergedLedgerEntryInternal(
                        existing = existing,
                        yearMonth = yearMonth,
                        incVaclav = incVaclav,
                        incEleonora = incEleonora,
                        incUnforeseen = incUnforeseen,
                        expRent = expRent,
                        expLiving = expLiving,
                        notes = notes,
                        portfolioBalance = portfolioBalance ?: 0.0,
                        pensionBalance = pensionBalance ?: 0.0,
                        emergencyReserve = emergencyReserve ?: 0.0
                    )
                    if (existing != null) repository.updateLedgerEntry(entry) else repository.addLedgerEntry(entry)
                }
                val isImportedStatementNote = notes.startsWith("Imported from", ignoreCase = true) ||
                    notes.startsWith("Direct Bank Statement", ignoreCase = true)
                if (!isImportedStatementNote) {
                    syncLedgerToSettingsIfCurrentMonth(
                        yearMonth = yearMonth,
                        incVaclav = incVaclav,
                        incEleonora = incEleonora,
                        expRent = expRent,
                        portfolioBalance = portfolioBalance,
                        pensionBalance = pensionBalance,
                        emergencyReserve = emergencyReserve
                    )
                }
                val msg = if (wasUpdated) "Ledger entry updated for $yearMonth" else "Ledger entry added for $yearMonth"
                emitSnackbar(msg)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to save ledger entry")
        }
    }

    suspend fun updateLedgerEntryWithOptionalBalances(
        entry: LedgerEntryEntity,
        portfolioBalance: Double?,
        pensionBalance: Double?,
        emergencyReserve: Double?
    ) {
        try {
            importMutex.withLock {
                repository.updateLedgerEntry(entry)
                val isImportedStatementNote = entry.notes.startsWith("Imported from", ignoreCase = true) ||
                    entry.notes.startsWith("Direct Bank Statement", ignoreCase = true)
                if (!isImportedStatementNote) {
                    syncLedgerToSettingsIfCurrentMonth(
                        yearMonth = entry.yearMonth,
                        incVaclav = entry.incVaclav,
                        incEleonora = entry.incEleonora,
                        expRent = entry.expRent,
                        portfolioBalance = portfolioBalance,
                        pensionBalance = pensionBalance,
                        emergencyReserve = emergencyReserve
                    )
                }
                emitSnackbar("Ledger entry updated for ${entry.yearMonth}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to update ledger entry")
        }
    }

    suspend fun deleteLedgerEntry(id: Long) {
        try {
            importMutex.withLock {
                val deletedYm = repository.deleteLedgerEntry(id)
                if (deletedYm != null) {
                    preferencesRepository.clearBankBalancesForMonth(deletedYm)
                    if (activeAuditReport.value?.yearMonth == deletedYm) {
                        activeAuditReport.value = null
                    }
                }
                emitSnackbar("Ledger entry and associated bank transactions deleted")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emitSnackbar("Failed to delete ledger entry")
        }
    }

    suspend fun importLedgerCsvFallback(bytes: ByteArray): Boolean {
        val (insertedCount, skippedDuplicates) = parseAndInsertLedgerCsvFallbackInternal(repository, bytes) { cell ->
            cell.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
        }
        return when {
            insertedCount > 0 -> {
                val dupNote = if (skippedDuplicates > 0) " ($skippedDuplicates duplicate months skipped)" else ""
                emitSnackbar("Successfully imported $insertedCount entries!$dupNote")
                true
            }
            skippedDuplicates > 0 -> {
                emitSnackbar("All $skippedDuplicates entries already exist - nothing imported")
                true
            }
            else -> false
        }
    }
}
