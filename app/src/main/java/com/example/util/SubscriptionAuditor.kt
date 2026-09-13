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
    val lastBilledDate: String,
    val billingCadence: String = "MONTHLY",
    val normalizedMonthlyAmount: Double = latestMonthlyAmount
)

data class SubscriptionAuditSummary(
    val items: List<RecurringSubscriptionItem>,
    val totalMonthlyBurn: Double,
    val annualizedBurn: Double,
    val fireCapitalRequired: Double,
    val priceCreepCount: Int
)

object SubscriptionAuditor {

    private val EXCLUDED_CATEGORIES = setOf(
        BankTransactionType.GROCERIES,
        BankTransactionType.DINING_RESTAURANT,
        BankTransactionType.SHOPPING_GOODS,
        BankTransactionType.TRANSPORTATION,
        BankTransactionType.HEALTH_DRUGSTORE,
        BankTransactionType.ATM_CASH,
        BankTransactionType.INVESTMENT_PORTU,
        BankTransactionType.INVESTMENT_DIP,
        BankTransactionType.INVESTMENT_DPS,
        BankTransactionType.INTERNAL_TRANSFER,
        BankTransactionType.SALARY_VACLAV,
        BankTransactionType.SALARY_ELEONORA,
        BankTransactionType.PARENTAL_BENEFIT,
        BankTransactionType.OTHER_INFLOW
    )

    private val EXPLICIT_RECURRING_CATEGORIES = setOf(
        BankTransactionType.SUBSCRIPTIONS_MEDIA,
        BankTransactionType.SERVICES_UTILITIES
    )

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

            // Determine primary category with resilient fallback
            val catCounts = txs.groupingBy {
                val catStr = it.category.trim().uppercase()
                when {
                    catStr == "GROCERIES" || catStr.contains("GROCERY") -> BankTransactionType.GROCERIES
                    catStr == "DINING_RESTAURANT" || catStr == "RESTAURANTS_DINING" || catStr.contains("DINING") || catStr.contains("RESTAURANT") -> BankTransactionType.DINING_RESTAURANT
                    catStr == "TRANSPORTATION" || catStr.contains("TRANSPORT") -> BankTransactionType.TRANSPORTATION
                    catStr == "SHOPPING_GOODS" || catStr.contains("SHOPPING") -> BankTransactionType.SHOPPING_GOODS
                    catStr == "HEALTH_DRUGSTORE" || catStr.contains("HEALTH") -> BankTransactionType.HEALTH_DRUGSTORE
                    else -> try {
                        BankTransactionType.valueOf(it.category)
                    } catch (_: Exception) {
                        BankTransactionType.GENERAL_EXPENSE
                    }
                }
            }.eachCount()
            val primaryCat = catCounts.maxByOrNull { it.value }?.key ?: BankTransactionType.GENERAL_EXPENSE

            // 1. Strict exclusion filter: Groceries, dining, shopping, transit, pharmacy, cash, transfers
            if (primaryCat in EXCLUDED_CATEGORIES) {
                continue
            }

            val distinctMonths = txs.map { it.yearMonth }.distinct().sorted()
            val isExplicitCategory = primaryCat in EXPLICIT_RECURRING_CATEGORIES

            // 2. Candidate criteria:
            // Explicit categories can be single-month (e.g. annual license or newly added service) or multi-month.
            // Heuristic categories (GENERAL_EXPENSE, LIFESTYLE_LIVING, CHARITY_DONATION, UNCATEGORIZED)
            // MUST span >= 2 distinct months, have low monthly frequency (<= 2 charges/mo), and low month-over-month variance.
            if (!isExplicitCategory) {
                if (distinctMonths.size < 2) continue

                // Check monthly charge frequency: subscriptions are billed 1-2 times a month, not everyday shopping
                val maxMonthlyCount = txs.groupBy { it.yearMonth }.values.maxOfOrNull { it.size } ?: 0
                if (maxMonthlyCount > 2) continue

                // Check amount stability: variance across months must be low (<= 1.60x ratio)
                val monthSums = txs.groupBy { it.yearMonth }.values.map { list -> list.sumOf { abs(it.amount) } }
                val minMonthly = monthSums.minOrNull() ?: 0.0
                val maxMonthly = monthSums.maxOrNull() ?: 0.0
                if (minMonthly <= 0.0 || (maxMonthly / minMonthly) > 1.60) continue
            }

            // Monthly debit aggregation: sum debits by yearMonth
            val monthlySums = txs.groupBy { it.yearMonth }
                .mapValues { (_, list) -> list.sumOf { abs(it.amount) } }
            val latestMonth = distinctMonths.last()
            val oldestMonth = distinctMonths.first()
            val latestAmount = monthlySums[latestMonth] ?: 0.0
            val oldestAmount = monthlySums[oldestMonth] ?: 0.0

            if (latestAmount <= 0.0) continue

            // Sort chronological to find last billed date and representative display name
            val sortedTxs = txs.sortedBy { it.date }
            val latestTx = sortedTxs.last()

            // Cadence detection (Annual vs Monthly)
            var cadence = "MONTHLY"
            var normalizedAmount = latestAmount
            if (distinctMonths.size == 1 && latestAmount >= 1200.0 && isExplicitCategory) {
                cadence = "ANNUAL"
                normalizedAmount = latestAmount / 12.0
            }

            // Price creep detection comparing newest month aggregate to oldest month aggregate
            var prevAmount: Double? = null
            var priceCreepDelta = 0.0
            var priceCreepPct = 0.0

            if (distinctMonths.size >= 2 && oldestAmount > 0.0 && latestAmount > oldestAmount + 1.0) {
                prevAmount = oldestAmount
                priceCreepDelta = latestAmount - oldestAmount
                priceCreepPct = (priceCreepDelta / oldestAmount) * 100.0
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
                    lastBilledDate = latestTx.date,
                    billingCadence = cadence,
                    normalizedMonthlyAmount = normalizedAmount
                )
            )
        }

        // Sort items by normalized monthly amount descending
        items.sortByDescending { it.normalizedMonthlyAmount }

        val totalMonthlyBurn = items.sumOf { it.normalizedMonthlyAmount }
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
