package com.example.ui.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.example.data.AppPreferencesRepository
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.util.BankStatementImporter
import com.example.util.BankType
import com.example.util.FxRateService
import com.example.util.InvestmentLotManager
import com.example.util.MerchantCategoryManager
import com.example.util.ReportShareHelper
import com.example.util.StatementParseSummary
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.ObjectOutputStream
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.round

private const val MAX_SAVED_STATE_SUMMARY_BYTES = 128 * 1024
private const val MAX_LEDGER_CSV_LINES = 50_000

internal data class BatchUriReadResult(
    val byteList: List<ByteArray>,
    val hasOversized: Boolean,
    val hasReadError: Boolean
)

internal fun dispatchSettingsPatch(
    current: SettingsEntity,
    onUpdateSettings: (SettingsEntity) -> Unit,
    onReduceSettings: (((SettingsEntity) -> SettingsEntity) -> Unit)?,
    transform: (SettingsEntity) -> SettingsEntity
) {
    if (onReduceSettings != null) {
        onReduceSettings(transform)
    } else {
        onUpdateSettings(transform(current))
    }
}

internal fun applySensitivityOverridesToSettings(
    settings: SettingsEntity,
    retOver: Double?,
    cpiOver: Double?,
    swrOver: Double?
): SettingsEntity {
    var updated = settings
    if (retOver != null && retOver.isFinite()) updated = updated.copy(portfolioNominalReturnPct = retOver)
    if (cpiOver != null && cpiOver.isFinite()) updated = updated.copy(cpiInflationPct = cpiOver)
    if (swrOver != null && swrOver.isFinite()) updated = updated.copy(safeWithdrawalRatePct = swrOver)
    return updated
}

internal fun computeCalculationStateInternal(
    settings: SettingsEntity,
    actions: Map<String, Boolean>,
    ledger: List<LedgerEntryEntity>,
    importedTxs: List<ImportedBankTransactionEntity>,
    retOver: Double?,
    cpiOver: Double?,
    swrOver: Double?,
    runMonteCarlo: Boolean,
    partnerBirthYearOverride: Int? = null
): FullCalculationState {
    val effectiveSettings = applySensitivityOverridesToSettings(settings, retOver, cpiOver, swrOver)
    val now = YearMonth.now(ZoneId.of("Europe/Prague"))
    val currentYm = now.toString()
    val importedMonths = importedTxs.asSequence().map { it.yearMonth }.toSet()
    // Only bind an editable, non-imported current-month ledger entry so historical statements or
    // read-only bank imports never freeze or override manual income/portfolio changes in Settings.
    val activeEntry = ledger.find {
        it.yearMonth == currentYm &&
            it.yearMonth !in importedMonths &&
            !it.notes.startsWith("Imported from", ignoreCase = true) &&
            !it.notes.startsWith("Direct Bank Statement", ignoreCase = true)
    }
    return FinancialEngine.calculate(
        effectiveSettings,
        actions,
        runMonteCarlo = runMonteCarlo,
        activeEntry,
        now.monthValue,
        ledger,
        importedTxs,
        partnerBirthYearOverride = partnerBirthYearOverride
    )
}

internal suspend fun runOneTimeMigrationsInternal(
    application: Application,
    repository: FinancialRepository,
    preferencesRepository: AppPreferencesRepository
) {
    repository.rolloverCalendarYearIfNeeded()
    if (!preferencesRepository.isFlagSet("legacy_employer_repaired_v1")) {
        val current = repository.settingsFlow.first()
        val looksLikeUntouchedLegacyDefault = current.employerRetirementMonthly in 2795.0..2805.0 &&
            current.vSalary == 53500.0 &&
            current.portuDcaMonthly == 13000.0
        if (looksLikeUntouchedLegacyDefault) {
            repository.repairLegacyEmployerContribution()
        }
        preferencesRepository.markFlag("legacy_employer_repaired_v1", true)
    }
    if (!preferencesRepository.isFlagSet("legacy_balances_backfilled")) {
        val entries = repository.getAllLedgerEntriesDirect()
        if (entries.isNotEmpty()) {
            val cur = repository.settingsFlow.first()
            val isSingle = cur.isSingleHousehold
            val snapLiquid = cur.liquidPortfolioCurrent + if (!isSingle) cur.eLiquidPortfolioCurrent else 0.0
            val snapPension = cur.dipBalanceCurrent + cur.dpsBalanceCurrent +
                if (!isSingle) (cur.eDipBalanceCurrent + cur.eDpsBalanceCurrent) else 0.0
            val toUpdate = mutableListOf<LedgerEntryEntity>()
            for (e in entries) {
                if (e.portfolioBalanceAtMonthEnd <= 0.0 && e.totalNetWorthAtMonthEnd <= 0.0) {
                    val reserve = if (e.emergencyReserveAtMonthEnd > 0.0) {
                        e.emergencyReserveAtMonthEnd
                    } else {
                        cur.emergencyReserveCurrent
                    }
                    toUpdate.add(
                        e.copy(
                            portfolioBalanceAtMonthEnd = snapLiquid,
                            pensionBalanceAtMonthEnd = snapPension,
                            emergencyReserveAtMonthEnd = reserve
                        )
                    )
                }
            }
            if (toUpdate.isNotEmpty()) repository.updateLedgerEntries(toUpdate)
        }
        preferencesRepository.markFlag("legacy_balances_backfilled", true)
    }
    if (!preferencesRepository.isFlagSet("merchant_rules_unified_v1")) {
        val curSettings = repository.settingsFlow.first()
        val spRules = MerchantCategoryManager.getOverrides(application, preferencesRepository.storeName)
        val dbRules = MerchantCategoryManager.parseRulesFromJson(curSettings.merchantRulesJson)
        if (spRules.isNotEmpty() || dbRules.isNotEmpty()) {
            val merged = spRules + dbRules
            val mergedJson = MerchantCategoryManager.serializeRulesToJson(merged)
            if (mergedJson != curSettings.merchantRulesJson) {
                repository.saveSettings(curSettings.copy(merchantRulesJson = mergedJson))
            }
            MerchantCategoryManager.saveAllOverrides(application, merged, preferencesRepository.storeName)
        }
        preferencesRepository.markFlag("merchant_rules_unified_v1", true)
    }
}

internal fun buildMergedLedgerEntryInternal(
    existing: LedgerEntryEntity?,
    yearMonth: String,
    incVaclav: Double,
    incEleonora: Double,
    incUnforeseen: Double,
    expRent: Double,
    expLiving: Double,
    notes: String,
    portfolioBalance: Double,
    pensionBalance: Double,
    emergencyReserve: Double
): LedgerEntryEntity {
    val prevLiving = (existing?.expGroceries ?: 0.0) + (existing?.expOther ?: 0.0)
    val (calcGroceries, calcOther) = if (existing != null && prevLiving > 0.0 && existing.expOther > 0.0) {
        val otherRatio = (existing.expOther / prevLiving).coerceIn(0.0, 1.0)
        val otherVal = round(expLiving * otherRatio)
        (expLiving - otherVal) to otherVal
    } else {
        expLiving to 0.0
    }
    return LedgerEntryEntity(
        id = existing?.id ?: 0L,
        yearMonth = yearMonth,
        incVaclav = incVaclav,
        incEleonora = incEleonora,
        incUnforeseen = incUnforeseen,
        expRent = expRent,
        expGroceries = calcGroceries,
        expOther = calcOther,
        notes = notes,
        portfolioBalanceAtMonthEnd = portfolioBalance,
        pensionBalanceAtMonthEnd = pensionBalance,
        emergencyReserveAtMonthEnd = emergencyReserve
    )
}

internal suspend fun parseAndInsertLedgerCsvFallbackInternal(
    repository: FinancialRepository,
    bytes: ByteArray,
    parseFiniteDouble: (String) -> Double
): Pair<Int, Int> {
    var insertedCount = 0
    var skippedDuplicates = 0
    val yearMonthRegex = Regex("""\d{4}-\d{2}""")
    repository.withTransaction {
        val existingYearMonths = repository.existingYearMonths()
        val entriesToInsert = mutableListOf<LedgerEntryEntity>()
        BufferedReader(InputStreamReader(bytes.inputStream())).use { reader ->
            var lineCount = 0
            while (lineCount < MAX_LEDGER_CSV_LINES) {
                val rawLine = reader.readLine()?.trim() ?: break
                lineCount++
                if (rawLine.isBlank()) continue
                val tokens = BankStatementImporter.parseCsvLine(rawLine)
                if (tokens.size >= 6) {
                    val ym = tokens[0].trim()
                    val incV = parseFiniteDouble(tokens[1]).coerceAtLeast(0.0)
                    val incE = parseFiniteDouble(tokens[2]).coerceAtLeast(0.0)
                    val incExtra = (if (tokens.size >= 8) parseFiniteDouble(tokens[3]) else 0.0).coerceAtLeast(0.0)
                    val expR = (if (tokens.size >= 8) parseFiniteDouble(tokens[4]) else parseFiniteDouble(tokens[3])).coerceAtLeast(0.0)
                    val expG = (if (tokens.size >= 8) parseFiniteDouble(tokens[5]) else parseFiniteDouble(tokens[4])).coerceAtLeast(0.0)
                    val expO = when {
                        tokens.size >= 8 -> parseFiniteDouble(tokens[6]).coerceAtLeast(0.0)
                        tokens.size >= 7 -> parseFiniteDouble(tokens[5]).coerceAtLeast(0.0)
                        else -> 0.0
                    }
                    val rawNotes = if (tokens.size >= 7) tokens.last().trim() else ""
                    val notes = when {
                        rawNotes.startsWith("Imported from", ignoreCase = true) -> rawNotes
                        rawNotes.isNotBlank() -> "Imported from CSV: $rawNotes"
                        else -> "Imported from CSV"
                    }
                    if (ym.isNotEmpty() && yearMonthRegex.matches(ym)) {
                        if (ym in existingYearMonths || entriesToInsert.any { it.yearMonth == ym }) {
                            skippedDuplicates++
                        } else {
                            entriesToInsert.add(
                                LedgerEntryEntity(
                                    yearMonth = ym,
                                    incVaclav = incV,
                                    incEleonora = incE,
                                    incUnforeseen = incExtra,
                                    expRent = expR,
                                    expGroceries = expG + expO,
                                    expOther = 0.0,
                                    notes = notes
                                )
                            )
                        }
                    }
                }
            }
        }
        if (entriesToInsert.isNotEmpty()) {
            repository.addLedgerEntries(entriesToInsert)
            insertedCount = entriesToInsert.size
        }
    }
    return insertedCount to skippedDuplicates
}

internal fun readStatementUrisSafely(
    application: Application,
    uris: List<Uri>,
    maxBatchBytes: Int = 25 * 1024 * 1024
): BatchUriReadResult {
    val contentResolver = application.contentResolver
    val canonicalDataDir = runCatching { File(application.applicationInfo.dataDir).canonicalPath }.getOrNull()
    val canonicalCacheDir = runCatching { application.cacheDir.canonicalPath }.getOrNull()
    val canonicalExportsDir = canonicalCacheDir?.let { runCatching { File(it, "exports").canonicalPath }.getOrNull() }

    val byteList = mutableListOf<ByteArray>()
    var cumulativeBytes = 0L
    var hasOversized = false
    var hasReadError = false

    for (uri in uris) {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            val canonicalTarget = uri.path?.let { runCatching { File(it).canonicalPath }.getOrNull() }
            val insideDataDir = canonicalTarget != null && canonicalDataDir != null && (
                canonicalTarget == canonicalDataDir ||
                    canonicalTarget.startsWith(canonicalDataDir + File.separator)
                )
            val insideAllowedCache = canonicalTarget != null && canonicalCacheDir != null &&
                canonicalTarget.startsWith(canonicalCacheDir + File.separator) &&
                (canonicalExportsDir == null || (
                    canonicalTarget != canonicalExportsDir &&
                        !canonicalTarget.startsWith(canonicalExportsDir + File.separator)
                    ))
            if (canonicalTarget == null || (insideDataDir && !insideAllowedCache)) {
                hasReadError = true
                continue
            }
        }
        val bytes = BankStatementImporter.readUriBytes(contentResolver, uri)
        if (bytes == null) {
            val canOpen = runCatching { contentResolver.openInputStream(uri)?.use { true } ?: false }.getOrDefault(false)
            if (canOpen) hasOversized = true else hasReadError = true
            continue
        }
        if (cumulativeBytes + bytes.size > maxBatchBytes) {
            hasOversized = true
            continue
        }
        cumulativeBytes += bytes.size
        byteList.add(bytes)
    }

    return BatchUriReadResult(byteList = byteList, hasOversized = hasOversized, hasReadError = hasReadError)
}

internal fun buildSyntheticAuditTransactions(pending: StatementParseSummary): List<ImportedBankTransactionEntity> {
    return pending.transactions.mapIndexed { idx, pt ->
        val txBank = if (pt.bankType != BankType.GENERIC) pt.bankType else pending.detectedBank
        ImportedBankTransactionEntity(
            id = -(idx + 1L),
            yearMonth = pending.yearMonth,
            bankName = txBank.name,
            date = pt.date,
            amount = pt.amount,
            counterpartyAccount = pt.counterpartyAccount,
            counterpartyName = pt.counterpartyName,
            message = pt.message,
            variableSymbol = pt.variableSymbol,
            category = pt.category.name,
            isNetted = pt.isNetted,
            nettingReason = pt.nettingReason,
            accountKey = pt.accountKey.ifBlank { pending.accountKey },
            originalCurrency = pt.originalCurrency?.ifBlank { "CZK" } ?: "CZK",
            originalAmount = pt.originalAmount ?: 0.0,
            fxRateToCzk = pt.exchangeRate?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        )
    }
}

internal fun persistPendingStatementToHandle(
    savedStateHandle: SavedStateHandle,
    key: String,
    summary: StatementParseSummary?
) {
    if (summary == null || summary.transactions.size > 200) {
        savedStateHandle.remove<StatementParseSummary>(key)
        return
    }
    val fitsBinderLimit = runCatching {
        ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos -> oos.writeObject(summary) }
            baos.size() <= MAX_SAVED_STATE_SUMMARY_BYTES
        }
    }.getOrDefault(false)

    if (fitsBinderLimit) {
        savedStateHandle[key] = summary
    } else {
        savedStateHandle.remove<StatementParseSummary>(key)
    }
}

internal suspend fun clearAuxiliaryStoresInternal(
    application: Application,
    preferencesRepository: AppPreferencesRepository
) {
    preferencesRepository.clearAllUserData()
    MerchantCategoryManager.clearOverrides(application, preferencesRepository.storeName)
    InvestmentLotManager.clearAll(application, preferencesRepository.storeName)
    ReportShareHelper.clearExportCache(application)
    FxRateService.resetRates()
}
