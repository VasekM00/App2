package com.example.util

import com.example.data.ImportedBankTransactionEntity
import kotlin.math.abs
import kotlin.math.max

/**
 * Data structures and audit engine for recurring fixed subscriptions, telecom, and insurance debits.
 * Analyzes imported bank transactions to detect recurring monthly overhead, flags price creep,
 * and calculates the dedicated perpetual FIRE capital required to fund these subscriptions.
 */
data class RecurringSubscriptionItem(
    val merchantKey: String,
    val displayName: String,
    val category: BankTransactionType,
    val latestMonthlyAmount: Double,
    val previousMonthlyAmount: Double?,
    val priceCreepDelta: Double,
    val priceCreepPct: Double,
    val transactionCount: Int,
    val lastBilledDate: String
)

data class SubscriptionAuditSummary(
    val items: List<RecurringSubscriptionItem>,
    val totalMonthlyBurn: Double,
    val annualizedBurn: Double,
    val fireCapitalRequired: Double,
    val priceCreepCount: Int
)

object SubscriptionAuditor {

    fun auditSubscriptions(
        transactions: List<ImportedBankTransactionEntity>,
        swrPct: Double = 4.0
    ): SubscriptionAuditSummary {
        val debits = transactions.filter { it.amount < 0 && !it.isNetted }
        if (debits.isEmpty()) {
            return SubscriptionAuditSummary(
                items = emptyList(),
                totalMonthlyBurn = 0.0,
                annualizedBurn = 0.0,
                fireCapitalRequired = 0.0,
                priceCreepCount = 0
            )
        }

        // Group debits by normalized merchant key
        val byMerchant = debits.groupBy { tx ->
            val name = tx.counterpartyName.ifBlank { tx.message }
            val query = CzechMerchantCatalog.suggestMerchantSearchQuery(name)
            if (query.isNotBlank()) CzechMerchantCatalog.normalize(query)
            else CzechMerchantCatalog.normalize(name)
        }

        val items = mutableListOf<RecurringSubscriptionItem>()

        for ((merchantKey, txs) in byMerchant) {
            if (merchantKey.isBlank()) continue

            // Determine primary category
            val catCounts = txs.groupingBy {
                try {
                    BankTransactionType.valueOf(it.category)
                } catch (_: Exception) {
                    BankTransactionType.GENERAL_EXPENSE
                }
            }.eachCount()
            val primaryCat = catCounts.maxByOrNull { it.value }?.key ?: BankTransactionType.GENERAL_EXPENSE

            // Candidate criteria:
            // 1. Explicit subscription/telecom/utilities category, OR
            // 2. Debits span across at least 2 distinct months
            val distinctMonths = txs.map { it.yearMonth }.distinct()
            val isExplicitCategory = primaryCat in setOf(
                BankTransactionType.SUBSCRIPTIONS_MEDIA,
                BankTransactionType.SERVICES_UTILITIES
            )

            if (!isExplicitCategory && distinctMonths.size < 2) {
                continue
            }

            // Sort chronological
            val sortedTxs = txs.sortedBy { it.date }
            val latestTx = sortedTxs.last()
            val latestAmount = abs(latestTx.amount)

            // Look for price creep comparing newest month to oldest observed month
            var prevAmount: Double? = null
            var priceCreepDelta = 0.0
            var priceCreepPct = 0.0

            if (sortedTxs.size >= 2) {
                val oldestTx = sortedTxs.first()
                val oldAmount = abs(oldestTx.amount)
                if (oldAmount > 0.0 && latestAmount > oldAmount + 1.0) {
                    prevAmount = oldAmount
                    priceCreepDelta = latestAmount - oldAmount
                    priceCreepPct = (priceCreepDelta / oldAmount) * 100.0
                }
            }

            val displayName = latestTx.counterpartyName.ifBlank { latestTx.message }
                .ifBlank { merchantKey.replaceFirstChar { it.uppercase() } }

            items.add(
                RecurringSubscriptionItem(
                    merchantKey = merchantKey,
                    displayName = displayName,
                    category = primaryCat,
                    latestMonthlyAmount = latestAmount,
                    previousMonthlyAmount = prevAmount,
                    priceCreepDelta = priceCreepDelta,
                    priceCreepPct = priceCreepPct,
                    transactionCount = txs.size,
                    lastBilledDate = latestTx.date
                )
            )
        }

        // Sort items by monthly amount descending
        items.sortByDescending { it.latestMonthlyAmount }

        val totalMonthlyBurn = items.sumOf { it.latestMonthlyAmount }
        val annualizedBurn = totalMonthlyBurn * 12.0
        val safeSwr = max(0.001, swrPct / 100.0)
        val fireCapitalRequired = annualizedBurn / safeSwr
        val priceCreepCount = items.count { it.priceCreepDelta > 0.0 }

        return SubscriptionAuditSummary(
            items = items,
            totalMonthlyBurn = totalMonthlyBurn,
            annualizedBurn = annualizedBurn,
            fireCapitalRequired = fireCapitalRequired,
            priceCreepCount = priceCreepCount
        )
    }
}
