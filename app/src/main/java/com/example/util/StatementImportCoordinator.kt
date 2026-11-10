package com.example.util

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.data.FinancialRepository
import com.example.data.ImportedBankTransactionEntity
import com.example.data.LedgerEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId

class StatementImportCoordinator(
    private val application: Application,
    private val repository: FinancialRepository,
    private val importPrefs: SharedPreferences
) {
    data class AggregatedMonthTotals(
        val incVaclav: Double,
        val incEleonora: Double,
        val incOther: Double,
        val expRent: Double,
        val expGroceries: Double,
        val expOther: Double,
        val invPortu: Double,
        val invDip: Double,
        val invDps: Double
    ) {
        val totalInvested: Double get() = invPortu + invDip + invDps
    }

    companion object {
        val OTHER_EXPENSE_TYPES = setOf(
            "LIFESTYLE_LIVING", "DINING_RESTAURANT", "TRANSPORTATION", "SHOPPING_GOODS",
            "HEALTH_DRUGSTORE", "SUBSCRIPTIONS_MEDIA", "SERVICES_UTILITIES", "ATM_CASH",
            "CHARITY_DONATION", "GENERAL_EXPENSE"
        )

        fun isGeneralExpense(cat: String, amount: Double): Boolean =
            cat in OTHER_EXPENSE_TYPES || (cat == BankTransactionType.UNCATEGORIZED.name && amount < 0)

        fun matchesMerchantInMonth(
            target: ImportedBankTransactionEntity,
            other: ImportedBankTransactionEntity
        ): Boolean = matchesMerchantInMonth(
            targetCounterparty = target.counterpartyName,
            targetMessage = target.message,
            otherCounterparty = other.counterpartyName,
            otherMessage = other.message
        )

        fun matchesMerchantInMonth(
            targetCounterparty: String,
            targetMessage: String,
            otherCounterparty: String,
            otherMessage: String
        ): Boolean {
            val rawTarget = targetCounterparty.ifBlank { targetMessage }
            val rawOther = otherCounterparty.ifBlank { otherMessage }
            if (rawTarget.isBlank() || rawOther.isBlank()) return false

            if (targetCounterparty.isNotBlank() && targetCounterparty.equals(otherCounterparty, ignoreCase = true)) {
                return true
            }

            val cleanTarget = BankStatementImporter.cleanPaymentDescription(rawTarget)
            val cleanOther = BankStatementImporter.cleanPaymentDescription(rawOther)
            if (cleanTarget.isNotBlank() && cleanTarget.equals(cleanOther, ignoreCase = true)) {
                return true
            }

            val query = CzechMerchantCatalog.suggestMerchantSearchQuery(cleanTarget.ifBlank { rawTarget })
            val normQuery = CzechMerchantCatalog.normalize(query)
            if (normQuery.length >= 3) {
                val otherNorm = CzechMerchantCatalog.normalize("$otherCounterparty $otherMessage")
                if (otherNorm.contains(normQuery)) return true
            }
            return false
        }

        fun aggregateMonthTransactions(txs: List<ImportedBankTransactionEntity>): AggregatedMonthTotals {
            val active = txs.filter { !it.isNetted }
            val incVaclav = active.filter { it.category == BankTransactionType.SALARY_VACLAV.name }.sumOf { it.amount }.coerceAtLeast(0.0)
            val incEleonora = active.filter {
                it.category == BankTransactionType.SALARY_ELEONORA.name || it.category == BankTransactionType.PARENTAL_BENEFIT.name
            }.sumOf { it.amount }.coerceAtLeast(0.0)
            var incOther = active.filter {
                it.category == BankTransactionType.OTHER_INFLOW.name || (it.category == BankTransactionType.UNCATEGORIZED.name && it.amount > 0)
            }.sumOf { it.amount }.coerceAtLeast(0.0)

            var rawRent = -active.filter { it.category == BankTransactionType.HOUSING_RENT.name }.sumOf { it.amount }
            var rawGroceries = -active.filter { it.category == BankTransactionType.GROCERIES.name }.sumOf { it.amount }
            var rawOther = -active.filter { isGeneralExpense(it.category, it.amount) }.sumOf { it.amount }

            if (rawRent < 0.0) {
                incOther += -rawRent
                rawRent = 0.0
            }
            if (rawGroceries < 0.0) {
                incOther += -rawGroceries
                rawGroceries = 0.0
            }
            if (rawOther < 0.0) {
                incOther += -rawOther
                rawOther = 0.0
            }

            val invPortu = (-active.filter { it.category == BankTransactionType.INVESTMENT_PORTU.name }.sumOf { it.amount }).coerceAtLeast(0.0)
            val invDip = (-active.filter { it.category == BankTransactionType.INVESTMENT_DIP.name }.sumOf { it.amount }).coerceAtLeast(0.0)
            val invDps = (-active.filter { it.category == BankTransactionType.INVESTMENT_DPS.name }.sumOf { it.amount }).coerceAtLeast(0.0)

            return AggregatedMonthTotals(
                incVaclav = incVaclav,
                incEleonora = incEleonora,
                incOther = incOther,
                expRent = rawRent,
                expGroceries = rawGroceries,
                expOther = rawOther,
                invPortu = invPortu,
                invDip = invDip,
                invDps = invDps
            )
        }

        fun formatInvestmentNote(agg: AggregatedMonthTotals): String {
            if (agg.totalInvested <= 0.0) return ""
            val parts = mutableListOf<String>()
            if (agg.invPortu > 0) parts.add("Portu: ${Formatters.fmtCompact(agg.invPortu)}")
            if (agg.invDip > 0) parts.add("DIP: ${Formatters.fmtCompact(agg.invDip)}")
            if (agg.invDps > 0) parts.add("DPS: ${Formatters.fmtCompact(agg.invDps)}")
            return " | Invested: ${Formatters.fmtCompact(agg.totalInvested)} (${parts.joinToString(", ")})"
        }
    }

    fun getStoredBankBalancesForMonth(yearMonth: String): Map<String, Double> {
        val prefix = "bank_bal_${yearMonth}_"
        val map = mutableMapOf<String, Double>()
        importPrefs.all.forEach { (key, value) ->
            if (key.startsWith(prefix)) {
                val bal = (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull()
                if (bal != null) map[key.removePrefix(prefix)] = bal
            }
        }
        return map
    }

    suspend fun fetchBoundaryTransactions(targetYm: String): List<ImportedBankTransactionEntity> {
        val ym = try { YearMonth.parse(targetYm) } catch (_: Exception) { return emptyList() }
        val prev = repository.getTransactionsInDateRangeDirect(ym.atDay(1).minusDays(5).toString(), ym.atDay(1).minusDays(1).toString())
        val next = repository.getTransactionsInDateRangeDirect(ym.atEndOfMonth().plusDays(1).toString(), ym.atEndOfMonth().plusDays(5).toString())
        return (prev + next).filter { !it.isNetted || !(it.nettingReason?.startsWith("Cross-") == true) }
    }

    suspend fun recalculateMonthLedger(targetYm: String) = withContext(Dispatchers.IO) {
        val allMonthTxs = repository.getImportedTransactionsDirect(targetYm)
        if (allMonthTxs.isEmpty()) return@withContext

        val agg = aggregateMonthTransactions(allMonthTxs)

        val existingEntry = repository.getLedgerEntryByYearMonth(targetYm)
        val participatingBanks = allMonthTxs.map { it.bankName }.filter { it.isNotBlank() }.distinct().sorted()
        val banksSummary = participatingBanks.joinToString(" + ")
        val nettedCount = allMonthTxs.count { it.isNetted }
        val nettingNote = if (nettedCount > 0) " | $nettedCount txs netted" else ""
        val investNote = formatInvestmentNote(agg)
        val autoNotes = "Imported from $banksSummary (${allMonthTxs.size} txs)$nettingNote$investNote"

        if (existingEntry != null) {
            val preservedNotes = if (existingEntry.notes.isBlank() || existingEntry.notes.startsWith("Imported from")) {
                autoNotes
            } else {
                existingEntry.notes
            }
            val updated = existingEntry.copy(
                incVaclav = agg.incVaclav,
                incEleonora = agg.incEleonora,
                incUnforeseen = agg.incOther,
                expRent = agg.expRent,
                expGroceries = agg.expGroceries,
                expOther = agg.expOther,
                notes = preservedNotes
            )
            repository.updateLedgerEntry(updated)
            syncLedgerToSettingsIfCurrentMonth(targetYm, agg.incVaclav, agg.incEleonora, agg.expRent, 0.0, 0.0, 0.0)
        }
    }

    private suspend fun syncLedgerToSettingsIfCurrentMonth(
        yearMonth: String, incVaclav: Double, incEleonora: Double, expRent: Double,
        portfolioBalance: Double, pensionBalance: Double, emergencyReserve: Double
    ) {
        val now = YearMonth.now(ZoneId.of("Europe/Prague")).toString()
        val latestYm = repository.getAllLedgerEntriesDirect().map { it.yearMonth }.maxOrNull() ?: now
        val effectiveCurrentYm = if (yearMonth == now || yearMonth >= latestYm) yearMonth else now
        repository.syncLedgerToSettingsTransaction(
            yearMonth, effectiveCurrentYm, incVaclav, incEleonora, expRent,
            portfolioBalance, pensionBalance, emergencyReserve
        )
    }

    data class ConfirmResult(
        val auditReport: CrossStatementAuditReport?,
        val affectedMonths: List<String>,
        val importedCount: Int
    )

    suspend fun confirmStatementImport(summary: StatementParseSummary): ConfirmResult? = withContext(Dispatchers.IO) {
        val activeTransactions = summary.transactions.filter { !it.isExcluded }
        if (activeTransactions.isEmpty()) return@withContext null

        val txsByMonth = activeTransactions.groupBy { tx ->
            val ym = tx.date.take(7)
            if (ym.matches(Regex("""\d{4}-\d{2}"""))) ym else summary.yearMonth
        }

        val latestMonth = txsByMonth.keys.maxOrNull() ?: summary.yearMonth
        val affectedMonths = txsByMonth.keys.sorted()
        var latestAuditReport: CrossStatementAuditReport? = null

        repository.withTransaction {
            for ((targetYm, monthTxs) in txsByMonth) {
                val byBank = monthTxs.groupBy { if (it.bankType != BankType.GENERIC) it.bankType.name else summary.detectedBank.name }
                for ((bankName, bTxs) in byBank) {
                    val txEntities = bTxs.map { tx ->
                        ImportedBankTransactionEntity(
                            yearMonth = targetYm,
                            bankName = bankName,
                            date = tx.date,
                            amount = tx.amount,
                            counterpartyAccount = tx.counterpartyAccount,
                            counterpartyName = tx.counterpartyName,
                            message = tx.message,
                            variableSymbol = tx.variableSymbol,
                            category = tx.category.name,
                            isNetted = tx.isNetted,
                            nettingReason = tx.nettingReason
                        )
                    }
                    repository.saveImportedTransactionsSmartMerge(targetYm, bankName, txEntities)
                }

                val boundaryTxs = fetchBoundaryTransactions(targetYm)

                // Fix Flaw 5: Assign statement monthEndBalance to summary.yearMonth (the primary statement month),
                // or latestMonth if summary.yearMonth is not present in the transactions set.
                if (summary.monthEndBalance != null) {
                    val isTargetBalanceMonth = (targetYm == summary.yearMonth) || (summary.yearMonth !in txsByMonth.keys && targetYm == latestMonth)
                    if (isTargetBalanceMonth) {
                        importPrefs.edit().putString("bank_bal_${targetYm}_${summary.detectedBank.name}", summary.monthEndBalance.toString()).apply()
                    }
                }
                val storedBalances = getStoredBankBalancesForMonth(targetYm)

                val allMonthTxs = repository.getImportedTransactionsDirect(targetYm)
                val reconcileResult = CrossStatementReconciliationEngine.reconcileTransactionsWithBoundaries(
                    transactions = allMonthTxs,
                    yearMonth = targetYm,
                    boundaryTransactions = boundaryTxs,
                    bankBalances = storedBalances
                )
                val reconciledTxs = reconcileResult.currentMonthTxs
                val auditReport = reconcileResult.auditReport
                repository.updateImportedTransactions(reconciledTxs)

                if (reconcileResult.updatedBoundaryTxs.isNotEmpty()) {
                    repository.updateImportedTransactions(reconcileResult.updatedBoundaryTxs)
                    val affectedAdjacentMonths = reconcileResult.updatedBoundaryTxs.map { it.yearMonth }.distinct().filter { it != targetYm }
                    for (adjYm in affectedAdjacentMonths) {
                        recalculateMonthLedger(adjYm)
                    }
                }

                if (targetYm == latestMonth || targetYm == summary.yearMonth) {
                    latestAuditReport = auditReport
                }

                val agg = aggregateMonthTransactions(reconciledTxs)

                val existingEntry = repository.getLedgerEntryByYearMonth(targetYm)
                val banksSummary = auditReport.participatingBanks.joinToString(" + ")
                val nettingNote = if (auditReport.totalNettedAmount > 0) " | ${auditReport.matchedPairs.size} cross-transfers netted (${Formatters.fmtCompact(auditReport.totalNettedAmount)})" else ""
                val investNote = formatInvestmentNote(agg)
                val autoNotes = "Imported from $banksSummary (${allMonthTxs.size} txs)$nettingNote$investNote"

                val cur = repository.settingsFlow.first()
                val isSingle = cur.isSingleHousehold
                val snapLiquid = cur.liquidPortfolioCurrent + if (!isSingle) cur.eLiquidPortfolioCurrent else 0.0
                val snapPension = cur.dipBalanceCurrent + cur.dpsBalanceCurrent + if (!isSingle) (cur.eDipBalanceCurrent + cur.eDpsBalanceCurrent) else 0.0

                if (existingEntry != null) {
                    val preservedNotes = if (existingEntry.notes.isBlank() || existingEntry.notes.startsWith("Imported from")) {
                        autoNotes
                    } else {
                        existingEntry.notes
                    }
                    val updated = existingEntry.copy(
                        incVaclav = agg.incVaclav,
                        incEleonora = agg.incEleonora,
                        incUnforeseen = agg.incOther,
                        expRent = agg.expRent,
                        expGroceries = agg.expGroceries,
                        expOther = agg.expOther,
                        notes = preservedNotes,
                        portfolioBalanceAtMonthEnd = if (existingEntry.portfolioBalanceAtMonthEnd > 0) existingEntry.portfolioBalanceAtMonthEnd else snapLiquid,
                        pensionBalanceAtMonthEnd = if (existingEntry.pensionBalanceAtMonthEnd > 0) existingEntry.pensionBalanceAtMonthEnd else snapPension,
                        emergencyReserveAtMonthEnd = if (existingEntry.emergencyReserveAtMonthEnd > 0) existingEntry.emergencyReserveAtMonthEnd else cur.emergencyReserveCurrent
                    )
                    repository.updateLedgerEntry(updated)
                } else {
                    val newEntry = LedgerEntryEntity(
                        yearMonth = targetYm,
                        incVaclav = agg.incVaclav,
                        incEleonora = agg.incEleonora,
                        incUnforeseen = agg.incOther,
                        expRent = agg.expRent,
                        expGroceries = agg.expGroceries,
                        expOther = agg.expOther,
                        notes = autoNotes,
                        portfolioBalanceAtMonthEnd = snapLiquid,
                        pensionBalanceAtMonthEnd = snapPension,
                        emergencyReserveAtMonthEnd = cur.emergencyReserveCurrent
                    )
                    repository.addLedgerEntry(newEntry)
                }

                syncLedgerToSettingsIfCurrentMonth(
                    yearMonth = targetYm,
                    incVaclav = agg.incVaclav,
                    incEleonora = agg.incEleonora,
                    expRent = agg.expRent,
                    portfolioBalance = 0.0,
                    pensionBalance = 0.0,
                    emergencyReserve = 0.0
                )
            }
        }

        val now = System.currentTimeMillis()
        importPrefs.edit {
            putLong("last_import_all", now)
            putLong("last_import_${summary.detectedBank.name}", now)
        }

        ConfirmResult(
            auditReport = latestAuditReport,
            affectedMonths = affectedMonths,
            importedCount = activeTransactions.size
        )
    }

    fun toggleTransactionExcluded(
        summary: StatementParseSummary,
        txIndex: Int
    ): StatementParseSummary {
        if (txIndex !in summary.transactions.indices) return summary
        val tx = summary.transactions[txIndex]
        val updatedList = summary.transactions.toMutableList()
        updatedList[txIndex] = tx.copy(isExcluded = !tx.isExcluded)
        return BankStatementImporter.recomputeSummary(summary, updatedList)
    }

    fun bulkExcludeMerchant(
        summary: StatementParseSummary,
        merchantQuery: String
    ): StatementParseSummary {
        val normQuery = CzechMerchantCatalog.normalize(merchantQuery)
        if (normQuery.isBlank()) return summary
        val updatedList = summary.transactions.map { tx ->
            val matchText = "${tx.counterpartyName} ${tx.message}"
            if (CzechMerchantCatalog.normalize(matchText).contains(normQuery)) {
                tx.copy(isExcluded = true)
            } else {
                tx
            }
        }
        return BankStatementImporter.recomputeSummary(summary, updatedList)
    }

    suspend fun updateTransactionCategory(
        summary: StatementParseSummary,
        txIndex: Int,
        newCategory: BankTransactionType,
        rememberForMerchant: Boolean = false,
        applyToAllMatching: Boolean = false
    ): StatementParseSummary {
        if (txIndex !in summary.transactions.indices) return summary
        val targetTx = summary.transactions[txIndex]
        val updatedTransactions = summary.transactions.toMutableList()
        val isNetted = (newCategory == BankTransactionType.INTERNAL_TRANSFER)
        val nettingReason = if (isNetted) BankStatementImporter.determineNettingReason(newCategory, targetTx.counterpartyName, targetTx.message) else ""
        updatedTransactions[txIndex] = targetTx.copy(category = newCategory, isNetted = isNetted, nettingReason = nettingReason)

        val merchantName = targetTx.counterpartyName.ifBlank { targetTx.message }
        val query = CzechMerchantCatalog.suggestMerchantSearchQuery(merchantName)

        if (rememberForMerchant && query.isNotBlank()) {
            MerchantCategoryManager.saveOverride(application, query, newCategory)
            val currentSettings = repository.settingsFlow.first()
            val currentRules = MerchantCategoryManager.parseRulesFromJson(currentSettings.merchantRulesJson).toMutableMap()
            currentRules[query.trim().lowercase()] = newCategory
            repository.saveSettings(currentSettings.copy(merchantRulesJson = MerchantCategoryManager.serializeRulesToJson(currentRules)))
        }

        if ((rememberForMerchant || applyToAllMatching) && query.isNotBlank()) {
            val normQuery = CzechMerchantCatalog.normalize(query)
            if (normQuery.isNotBlank()) {
                for (i in updatedTransactions.indices) {
                    val otherTx = updatedTransactions[i]
                    val otherNorm = CzechMerchantCatalog.normalize("${otherTx.counterpartyName} ${otherTx.message}")
                    if (otherNorm.contains(normQuery)) {
                        val otherReason = if (isNetted) BankStatementImporter.determineNettingReason(newCategory, otherTx.counterpartyName, otherTx.message) else ""
                        updatedTransactions[i] = otherTx.copy(category = newCategory, isNetted = isNetted, nettingReason = otherReason)
                    }
                }
            }
        }

        return BankStatementImporter.recomputeSummary(summary, updatedTransactions)
    }
}
