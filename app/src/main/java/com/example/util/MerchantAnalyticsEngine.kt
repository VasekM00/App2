package com.example.util

import com.example.data.ImportedBankTransactionEntity
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Advanced analytics, intelligence, and search engine for imported bank transactions.
 * Computes:
 * - Top merchants leaderboard with spending aggregates and budget share.
 * - Month-over-month category spending drift with trend direction.
 * - Outlier transaction detection (single charges > 3x the merchant's 6-month median).
 * - Multi-token fuzzy search and filtering for transactions.
 */
object MerchantAnalyticsEngine {

    private val WHITESPACE_REGEX = Regex("""\s+""")

    private val NON_EXPENSE_CATEGORIES = setOf(
        BankTransactionType.SALARY_VACLAV,
        BankTransactionType.SALARY_ELEONORA,
        BankTransactionType.PARENTAL_BENEFIT,
        BankTransactionType.FAMILY_GIFT,
        BankTransactionType.OTHER_INFLOW,
        BankTransactionType.INVESTMENT_PORTU,
        BankTransactionType.INVESTMENT_DIP,
        BankTransactionType.INVESTMENT_DPS,
        BankTransactionType.INTERNAL_TRANSFER
    )

    enum class MerchantDriftTimeframe {
        DAYS_30,
        DAYS_90,
        YTD
    }

    data class MerchantDriftLeaderboardItem(
        val rank: Int,
        val merchantKey: String,
        val displayName: String,
        val category: BankTransactionType,
        val totalSpend: Double,
        val transactionCount: Int,
        val spendSharePct: Double,
        val priorSpend: Double,
        val driftPct: Double?,
        val hasPriorPeriodData: Boolean,
        val representativeTxIdsByMonth: List<Long>
    )

    data class MerchantSpendingStat(
        val rank: Int,
        val merchantKey: String,
        val displayName: String,
        val category: BankTransactionType,
        val totalSpend: Double,
        val transactionCount: Int,
        val percentageOfTotal: Double,
        val averageTransactionAmount: Double,
        val percentageOfBudget: Double? = null
    )

    enum class DriftDirection {
        INCREASED,
        DECREASED,
        UNCHANGED
    }

    data class CategorySpendingDrift(
        val category: BankTransactionType,
        val baseMonthAmount: Double,
        val targetMonthAmount: Double,
        val deltaAmount: Double,
        val deltaPercentage: Double,
        val direction: DriftDirection
    )

    data class OutlierTransaction(
        val transactionId: Long = 0,
        val date: String,
        val merchantKey: String,
        val displayName: String,
        val category: BankTransactionType,
        val amount: Double,
        val merchantMedian: Double,
        val ratioToMedian: Double,
        val outlierReason: String,
        val rawTransaction: ImportedBankTransactionEntity? = null
    )

    data class TransactionSearchCriteria(
        val query: String = "",
        val categories: Set<BankTransactionType> = emptySet(),
        val bankTypes: Set<String> = emptySet(),
        val startDate: String? = null, // "YYYY-MM-DD"
        val endDate: String? = null,   // "YYYY-MM-DD"
        val minAmount: Double? = null,
        val maxAmount: Double? = null,
        val inflowsOnly: Boolean = false,
        val expensesOnly: Boolean = false,
        val excludeNetted: Boolean = true
    )

    /**
     * Computes the top merchants leaderboard by total spending.
     * Only analyzes expense/debit transactions (amount < 0, !isNetted).
     *
     * @param transactions Imported transactions to analyze.
     * @param limit Maximum number of merchants to return in leaderboard (default 10).
     * @param totalBudget Optional total budget (e.g. monthly expense target) to compute budget %.
     */
    fun topMerchants(
        transactions: List<ImportedBankTransactionEntity>,
        limit: Int = 10,
        totalBudget: Double? = null
    ): List<MerchantSpendingStat> {
        if (limit <= 0) return emptyList()
        val debits = transactions.filter { it.amount.isFinite() && it.amount < 0 && !it.isNetted }
        if (debits.isEmpty()) return emptyList()

        val validBudget = totalBudget?.takeIf { it.isFinite() && it > 0.0 }
        val totalSpendAll = debits.sumOf { abs(it.amount) }
        val byMerchant = debits.groupBy { resolveMerchantKey(it) }

        val stats = mutableListOf<MerchantSpendingStat>()

        for ((merchantKey, txs) in byMerchant) {
            if (merchantKey.isBlank() || merchantKey == "unknown") continue
            val totalSpend = txs.sumOf { abs(it.amount) }
            val count = txs.size
            val avg = if (count > 0) totalSpend / count else 0.0
            val pctTotal = if (totalSpendAll > 0.0) (totalSpend / totalSpendAll) * 100.0 else 0.0
            val pctBudget = if (validBudget != null) (totalSpend / validBudget) * 100.0 else null

            // Determine category
            val catCounts = txs.groupingBy { parseCategorySafely(it.category) }.eachCount()
            val primaryCat = catCounts.maxByOrNull { it.value }?.key ?: BankTransactionType.GENERAL_EXPENSE

            // Determine display name
            val displayName = resolveDisplayName(txs, merchantKey)

            stats.add(
                MerchantSpendingStat(
                    rank = 0,
                    merchantKey = merchantKey,
                    displayName = displayName,
                    category = primaryCat,
                    totalSpend = totalSpend,
                    transactionCount = count,
                    percentageOfTotal = pctTotal,
                    averageTransactionAmount = avg,
                    percentageOfBudget = pctBudget
                )
            )
        }

        return stats.sortedByDescending { it.totalSpend }
            .take(limit)
            .mapIndexed { index, stat -> stat.copy(rank = index + 1) }
    }

    /**
     * Computes top spending merchants within the requested [timeframe] anchored to the latest
     * imported expense transaction date, alongside genuine period-over-period spend drift.
     * Excludes inflows, netted entries, investment transfers, and internal transfers.
     */
    fun computeMerchantLeaderboardWithDrift(
        transactions: List<ImportedBankTransactionEntity>,
        timeframe: MerchantDriftTimeframe = MerchantDriftTimeframe.DAYS_30,
        limit: Int = 5,
        totalBudget: Double? = null
    ): List<MerchantDriftLeaderboardItem> {
        if (limit <= 0) return emptyList()
        val expenseDebits = transactions.mapNotNull { tx ->
            if (!tx.amount.isFinite() || tx.amount >= 0 || tx.isNetted) return@mapNotNull null
            val cat = parseCategorySafely(tx.category)
            if (cat in NON_EXPENSE_CATEGORIES) return@mapNotNull null
            val date = runCatching { LocalDate.parse(tx.date.trim()) }.getOrElse {
                runCatching { java.time.YearMonth.parse(tx.yearMonth.trim()).atDay(15) }.getOrNull()
            } ?: return@mapNotNull null
            Triple(tx, cat, date)
        }
        if (expenseDebits.isEmpty()) return emptyList()

        val anchorDate = expenseDebits.maxOf { it.third }
        val (currentStart, priorStart, priorEnd) = when (timeframe) {
            MerchantDriftTimeframe.DAYS_30 -> {
                val cStart = anchorDate.minusDays(29)
                val pEnd = cStart.minusDays(1)
                Triple(cStart, pEnd.minusDays(29), pEnd)
            }
            MerchantDriftTimeframe.DAYS_90 -> {
                val cStart = anchorDate.minusDays(89)
                val pEnd = cStart.minusDays(1)
                Triple(cStart, pEnd.minusDays(89), pEnd)
            }
            MerchantDriftTimeframe.YTD -> {
                val cStart = LocalDate.of(anchorDate.year, 1, 1)
                Triple(cStart, cStart.minusYears(1), anchorDate.minusYears(1))
            }
        }

        val currentTxs = expenseDebits.filter { !it.third.isBefore(currentStart) && !it.third.isAfter(anchorDate) }
        if (currentTxs.isEmpty()) return emptyList()

        val priorTxs = expenseDebits.filter { !it.third.isBefore(priorStart) && !it.third.isAfter(priorEnd) }
        val hasPriorPeriodData = expenseDebits.any { !it.third.isAfter(priorEnd) }

        val priorSpendByMerchant = priorTxs
            .groupBy { resolveMerchantKey(it.first) }
            .mapValues { (_, items) -> items.sumOf { abs(it.first.amount) } }

        val validBudget = totalBudget?.takeIf { it.isFinite() && it > 0.0 }
        val periodSpendTotal = currentTxs.sumOf { abs(it.first.amount) }.coerceAtLeast(1.0)
        val shareDenominator = validBudget ?: periodSpendTotal

        val groupedCurrent = currentTxs.groupBy { resolveMerchantKey(it.first) }
            .filterKeys { it.isNotBlank() && it != "unknown" }

        val items = groupedCurrent.entries.mapNotNull { (merchantKey, entries) ->
            val txs = entries.map { it.first }
            val totalSpend = txs.sumOf { abs(it.amount) }
            if (totalSpend <= 0.0) return@mapNotNull null

            val priorSpend = priorSpendByMerchant[merchantKey] ?: 0.0
            val driftPct = when {
                priorSpend > 0.0 -> ((totalSpend - priorSpend) / priorSpend) * 100.0
                else -> null
            }

            val primaryCat = entries.groupingBy { it.second }.eachCount().maxByOrNull { it.value }?.key
                ?: BankTransactionType.GENERAL_EXPENSE
            val displayName = resolveDisplayName(txs, merchantKey)
            val repIdsByMonth = txs.groupBy { it.yearMonth }
                .values
                .mapNotNull { list -> list.firstOrNull { it.id > 0L }?.id }

            MerchantDriftLeaderboardItem(
                rank = 0,
                merchantKey = merchantKey,
                displayName = displayName,
                category = primaryCat,
                totalSpend = totalSpend,
                transactionCount = txs.size,
                spendSharePct = ((totalSpend / shareDenominator) * 100.0).coerceIn(0.0, 100.0),
                priorSpend = priorSpend,
                driftPct = driftPct,
                hasPriorPeriodData = hasPriorPeriodData,
                representativeTxIdsByMonth = repIdsByMonth
            )
        }

        return items.sortedByDescending { it.totalSpend }
            .take(limit)
            .mapIndexed { idx, item -> item.copy(rank = idx + 1) }
    }

    /**
     * Computes month-over-month category spending drift (e.g., Groceries +12%, Restaurants -8%).
     *
     * @param transactions Historical transactions.
     * @param baseYearMonth Base month (e.g. "2026-08"). If null, uses second latest distinct month.
     * @param targetYearMonth Target month (e.g. "2026-09"). If null, uses latest distinct month.
     */
    fun computeCategoryDrift(
        transactions: List<ImportedBankTransactionEntity>,
        baseYearMonth: String? = null,
        targetYearMonth: String? = null
    ): List<CategorySpendingDrift> {
        val expenseTxs = transactions.filter { tx ->
            if (!tx.amount.isFinite() || tx.isNetted) return@filter false
            val cat = parseCategorySafely(tx.category)
            cat !in NON_EXPENSE_CATEGORIES && (tx.amount < 0 || cat != BankTransactionType.UNCATEGORIZED)
        }
        val debits = expenseTxs.filter { it.amount < 0 }
        if (debits.isEmpty()) return emptyList()

        val distinctMonths = expenseTxs.map { it.yearMonth }.filter { it.isNotBlank() }.distinct().sorted()
        val baseMonth = baseYearMonth ?: distinctMonths.getOrNull(distinctMonths.size - 2) ?: return emptyList()
        val targetMonth = targetYearMonth ?: distinctMonths.lastOrNull() ?: return emptyList()

        if (baseMonth == targetMonth) return emptyList()

        val baseTxs = expenseTxs.filter { it.yearMonth == baseMonth }
        val targetTxs = expenseTxs.filter { it.yearMonth == targetMonth }

        // Net refunds against debits in each expense category (never below zero net spend)
        val baseByCat = baseTxs.groupBy { parseCategorySafely(it.category) }
            .mapValues { (_, list) -> max(0.0, -list.sumOf { it.amount }) }
            .filterValues { it > 0.0 }
        val targetByCat = targetTxs.groupBy { parseCategorySafely(it.category) }
            .mapValues { (_, list) -> max(0.0, -list.sumOf { it.amount }) }
            .filterValues { it > 0.0 }

        val allCategories = (baseByCat.keys + targetByCat.keys).distinct()
        val results = mutableListOf<CategorySpendingDrift>()

        for (cat in allCategories) {
            val baseAmt = baseByCat[cat] ?: 0.0
            val targetAmt = targetByCat[cat] ?: 0.0
            val deltaAmt = targetAmt - baseAmt
            val deltaPct = when {
                baseAmt > 0.0 -> ((targetAmt - baseAmt) / baseAmt) * 100.0
                targetAmt > 0.0 -> 100.0
                else -> 0.0
            }

            val direction = when {
                deltaAmt > 0.01 -> DriftDirection.INCREASED
                deltaAmt < -0.01 -> DriftDirection.DECREASED
                else -> DriftDirection.UNCHANGED
            }

            results.add(
                CategorySpendingDrift(
                    category = cat,
                    baseMonthAmount = baseAmt,
                    targetMonthAmount = targetAmt,
                    deltaAmount = deltaAmt,
                    deltaPercentage = deltaPct,
                    direction = direction
                )
            )
        }

        // Sort descending by magnitude of target spend or absolute change
        return results.sortedByDescending { it.targetMonthAmount }
    }

    /**
     * Detects outlier transactions where a single charge is greater than [multiplier] times
     * the merchant's 6-month median spending (default > 3.0x median).
     */
    fun detectOutliers(
        transactions: List<ImportedBankTransactionEntity>,
        multiplier: Double = 3.0,
        minimumChargeThreshold: Double = 100.0
    ): List<OutlierTransaction> {
        if (!multiplier.isFinite() || multiplier <= 0.0 || !minimumChargeThreshold.isFinite()) return emptyList()
        val debits = transactions.filter { it.amount.isFinite() && it.amount < 0 && !it.isNetted }
        if (debits.isEmpty()) return emptyList()

        val latestDate = debits.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.maxOrNull()
        val cutoffDate = latestDate?.minusMonths(6)

        val byMerchant = debits.groupBy { resolveMerchantKey(it) }
        val outliers = mutableListOf<OutlierTransaction>()

        for ((merchantKey, txs) in byMerchant) {
            if (merchantKey.isBlank() || merchantKey == "unknown" || txs.size < 2) continue

            val recentTxs = if (cutoffDate != null) {
                txs.filter { tx ->
                    val d = runCatching { LocalDate.parse(tx.date) }.getOrNull()
                    d == null || !d.isBefore(cutoffDate)
                }
            } else {
                txs
            }
            val baselineTxs = if (recentTxs.size >= 2) recentTxs else txs

            // Compute median of charges for this merchant over the 6-month window (or fallback history)
            val amounts = baselineTxs.map { abs(it.amount) }.sorted()
            val median = if (amounts.size % 2 == 1) {
                amounts[amounts.size / 2]
            } else {
                val mid = amounts.size / 2
                (amounts[mid - 1] + amounts[mid]) / 2.0
            }

            if (!median.isFinite() || median <= 0.0) continue

            for (tx in txs) {
                val spend = abs(tx.amount)
                if (spend >= minimumChargeThreshold && spend > multiplier * median) {
                    val ratio = spend / median
                    val displayName = tx.counterpartyName.ifBlank { tx.message }.ifBlank { merchantKey }
                    outliers.add(
                        OutlierTransaction(
                            transactionId = tx.id,
                            date = tx.date,
                            merchantKey = merchantKey,
                            displayName = displayName,
                            category = parseCategorySafely(tx.category),
                            amount = spend,
                            merchantMedian = median,
                            ratioToMedian = ratio,
                            outlierReason = "Charge of ${spend.toLong()} Kč is ${String.format(Locale.ROOT, "%.1f", ratio)}x above merchant median (${median.toLong()} Kč)",
                            rawTransaction = tx
                        )
                    )
                }
            }
        }

        return outliers.sortedByDescending { it.ratioToMedian }
    }

    /**
     * Multi-token fuzzy search and filtering engine for imported bank transactions.
     * Matches across counterparty name, payment message, category, bank name, account number,
     * variable symbol, and transaction amount.
     */
    fun searchTransactions(
        transactions: List<ImportedBankTransactionEntity>,
        criteria: TransactionSearchCriteria
    ): List<ImportedBankTransactionEntity> {
        // Use normalizeSimple so cities ("Brno", "Praha") and 1-char brand tokens ("H&M") are preserved
        val rawTokens = WHITESPACE_REGEX.split(CzechMerchantCatalog.normalizeSimple(criteria.query).trim())
            .filter { it.isNotBlank() }
        if (criteria.query.isNotBlank() && rawTokens.isEmpty()) return emptyList()

        val normalizedBanks = criteria.bankTypes.map { it.uppercase(Locale.ROOT) }.toHashSet()
        val hasQuery = rawTokens.isNotEmpty()

        val scoredMatches = mutableListOf<Pair<ImportedBankTransactionEntity, Int>>()
        for (tx in transactions) {
            if (criteria.categories.isNotEmpty()) {
                val cat = parseCategorySafely(tx.category)
                if (cat !in criteria.categories) continue
            }
            if (normalizedBanks.isNotEmpty() && tx.bankName.uppercase(Locale.ROOT) !in normalizedBanks) {
                continue
            }
            if (criteria.startDate != null && tx.date < criteria.startDate) continue
            if (criteria.endDate != null && tx.date > criteria.endDate) continue

            val absAmt = abs(tx.amount)
            if (criteria.minAmount != null && (!absAmt.isFinite() || absAmt < criteria.minAmount)) continue
            if (criteria.maxAmount != null && (!absAmt.isFinite() || absAmt > criteria.maxAmount)) continue

            if (criteria.inflowsOnly && tx.amount <= 0) continue
            if (criteria.expensesOnly && tx.amount >= 0) continue
            if (criteria.excludeNetted && tx.isNetted) continue

            if (!hasQuery) {
                scoredMatches.add(tx to 0)
            } else {
                val haystack = buildHaystack(tx)
                val words = WHITESPACE_REGEX.split(haystack).filter { it.isNotBlank() }
                if (rawTokens.all { token -> matchToken(token, haystack, words) }) {
                    scoredMatches.add(tx to scoreMatch(rawTokens, haystack, words))
                }
            }
        }

        return scoredMatches
            .sortedWith(compareByDescending<Pair<ImportedBankTransactionEntity, Int>> { it.second }.thenByDescending { it.first.date })
            .map { it.first }
    }

    /**
     * Convenience search method by plain query string.
     */
    fun searchTransactions(
        transactions: List<ImportedBankTransactionEntity>,
        query: String
    ): List<ImportedBankTransactionEntity> {
        return searchTransactions(transactions, TransactionSearchCriteria(query = query))
    }

    private fun buildHaystack(tx: ImportedBankTransactionEntity): String {
        val absAmt = if (tx.amount.isFinite()) abs(tx.amount) else 0.0
        val wholeAmtStr = absAmt.toLong().toString()
        val decimalAmtStr = String.format(Locale.ROOT, "%.2f", absAmt)
        val raw = "${tx.counterpartyName} ${tx.message} ${tx.category} ${tx.bankName} ${tx.counterpartyAccount} ${tx.variableSymbol} $wholeAmtStr $decimalAmtStr"
        return CzechMerchantCatalog.normalizeSimple(raw)
    }

    private fun matchToken(token: String, haystack: String, words: List<String>): Boolean {
        if (haystack.contains(token)) return true
        if (words.any { it.startsWith(token) }) return true

        // Fuzzy match via Levenshtein for longer tokens (prevent false positives on short words)
        if (token.length >= 4) {
            val maxAllowedDistance = if (token.length >= 7) 2 else 1
            for (word in words) {
                if (abs(word.length - token.length) <= maxAllowedDistance) {
                    if (levenshteinDistance(token, word) <= maxAllowedDistance) return true
                }
            }
        }

        return false
    }

    private fun scoreMatch(tokens: List<String>, haystack: String, words: List<String>): Int {
        var score = 0
        for (token in tokens) {
            when {
                words.contains(token) -> score += 100 // Exact word
                words.any { it.startsWith(token) } -> score += 50 // Word prefix
                haystack.contains(token) -> score += 25 // Substring
                else -> score += 10 // Fuzzy match
            }
        }
        return score
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun parseCategorySafely(categoryStr: String): BankTransactionType {
        val clean = categoryStr.trim().uppercase(Locale.ROOT)
        return try {
            BankTransactionType.valueOf(clean)
        } catch (_: Exception) {
            when {
                clean.contains("GROCER") -> BankTransactionType.GROCERIES
                clean.contains("RESTAURANT") || clean.contains("DINING") -> BankTransactionType.DINING_RESTAURANT
                clean.contains("TRANSPORT") -> BankTransactionType.TRANSPORTATION
                clean.contains("RENT") || clean.contains("HOUSING") -> BankTransactionType.HOUSING_RENT
                clean.contains("SUBSCRIPTION") -> BankTransactionType.SUBSCRIPTIONS_MEDIA
                clean.contains("UTILITIES") || clean.contains("ENERGY") -> BankTransactionType.SERVICES_UTILITIES
                clean.contains("SALARY") -> BankTransactionType.SALARY_VACLAV
                else -> BankTransactionType.GENERAL_EXPENSE
            }
        }
    }

    fun resolveMerchantKey(tx: ImportedBankTransactionEntity): String {
        val baseKey = SubscriptionAuditor.extractMerchantKey(tx)
        val norm = CzechMerchantCatalog.normalize(baseKey)
        val simple = CzechMerchantCatalog.normalizeSimple(baseKey)
        val words = norm.split(" ").filter { it.isNotBlank() }
        val simpleWords = simple.split(" ").filter { it.isNotBlank() }
        val simplePadded = " $simple "
        val noiseWords = setOf(
            "hypermarket", "supermarket", "market", "potraviny", "prodejna",
            "store", "obchod", "cerpaci", "stanice", "phm", "benzin", "express", "city"
        )
        if (simplePadded.contains(" e on ") || words.contains("eon") || simpleWords.contains("eon")) {
            return "eon"
        }
        if (simplePadded.contains(" h m ") || simpleWords.contains("hm")) {
            return "h m"
        }
        val knownChains = listOf(
            "albert", "billa", "lidl", "tesco", "kaufland", "penny", "globus", "coop", "zabka",
            "rohlik", "kosik", "cez", "pre", "eon", "innogy", "shell", "orlen", "benzina", "mol", "omv",
            "alza", "datart", "ikea", "mcdonald", "mcdonalds", "kfc", "dm", "rossmann", "teta", "notino"
        )
        for (chain in knownChains) {
            if (words.contains(chain) || simpleWords.contains(chain)) {
                return if (chain == "mcdonalds") "mcdonald" else chain
            }
        }
        val cleanWords = words.filter { it !in noiseWords }
        if (cleanWords.isNotEmpty()) return cleanWords.joinToString(" ")
        val cleanSimple = simpleWords.filter { it !in noiseWords }
        return if (cleanSimple.isNotEmpty()) cleanSimple.joinToString(" ") else baseKey
    }

    private fun resolveDisplayName(txs: List<ImportedBankTransactionEntity>, merchantKey: String): String {
        val prettyBrands = mapOf(
            "albert" to "Albert",
            "rohlik" to "Rohlík.cz",
            "kosik" to "Košík.cz",
            "billa" to "Billa",
            "lidl" to "Lidl",
            "tesco" to "Tesco",
            "kaufland" to "Kaufland",
            "penny" to "Penny Market",
            "globus" to "Globus",
            "cez" to "ČEZ",
            "eon" to "E.ON",
            "pre" to "PRE",
            "innogy" to "Innogy",
            "shell" to "Shell",
            "orlen" to "Orlen",
            "mol" to "MOL",
            "omv" to "OMV",
            "alza" to "Alza.cz",
            "datart" to "Datart",
            "ikea" to "IKEA"
        )
        prettyBrands[merchantKey]?.let { return it }

        for (tx in txs) {
            val candidate = tx.counterpartyName.ifBlank { tx.message }
            val cleaned = BankStatementImporter.cleanPaymentDescription(candidate)
            if (cleaned.isNotBlank() && !cleaned.equals("platba kartou", ignoreCase = true)) {
                return cleaned.trim()
            }
        }
        return merchantKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }
}
