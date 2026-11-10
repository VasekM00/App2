package com.example.util

import com.example.data.ImportedBankTransactionEntity
import java.util.Locale
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
    val priceCreepCount: Int,
    val dismissedCount: Int = 0
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

    private val EXCLUDED_MERCHANT_KEYWORDS = listOf(
        // Florists & Flowers
        "kvetin", "kvetinarstvi", "florist", "kytky", "kytk",
        // Restaurants, Cafes, Bistros, Bars, Bakeries & Food
        "restaur", "bistro", "caff", "cafe", "kafe", "kavarn", "hospoda", "pivn", "pivovar",
        "pub", "bar", "obed", "jidlo", "burger", "pizza", "kebab", "sushi", "ramen",
        "aarav", "gopal", "dhaba", "wolt", "foodora", "dame jidlo", "bolt food", "bageterie",
        "mcdonald", "kfc", "pekar", "pekarna", "cukrar", "cukrarna", "masna", "reznictvi",
        "lahudky", "zelin", "potravin", "vecerka", "market", "supermarket", "hypermarket",
        // Retail goods, clothing, electronics & books
        "knih", "knihy", "papirnictvi", "odev", "obuv", "textil", "drogerie", "lekarn",
        "nabytek", "hracky", "sport", "elektro", "optik", "alza", "datart", "mall",
        // Transit, Fuel & Parking
        "cerpaci", "benzin", "nafta", "orlen", "benzina", "mol", "shell", "omv", "eurooil",
        "parking", "parkov", "jizdenk", "taxi", "bolt ride", "uber", "liftago",
        // One-off postal, courier, hair, repair & leisure services
        "posta", "ceska posta", "balikovna", "zasilkovna", "packeta", "ppl", "dpd", "gls", "dhl",
        "kadernic", "barber", "salon", "cistirna", "pradelna", "zamecnik", "hodinovy manzel",
        "divadlo", "kino", "vstupenk", "ticket", "zoo", "muzeum", "galerie", "hotel", "ubytovani"
    )

    private val VERIFIED_RECURRING_PROVIDERS = listOf(
        // Media, Streaming & Cloud
        "spotify", "netflix", "youtube", "google", "apple", "disney", "hbo", "voyo", "prima plus",
        "skyshowtime", "audible", "audioteka", "patreon", "herohero", "forendors", "pickey",
        "substack", "steam", "playstation", "xbox", "microsoft", "openai", "chatgpt", "anthropic",
        "claude", "midjourney", "perplexity", "jetbrains", "github", "adobe", "notion", "canva",
        "figma", "icloud", "dropbox", "1password", "bitwarden", "proton", "nordvpn", "amazon prime", "amazon",
        "wedos", "forpsi",
        // Telecom & Internet
        "vodafone", "o2", "t mobile", "t-mobile", "nordic telecom", "poda", "nej cz", "cetin",
        // Energy & Utilities
        "cez", "pre", "e on", "innogy", "mnd", "prazska plynarenska", "centropol", "epet",
        "teplarny", "bvk", "brnenske vodarny", "vodarny", "veolia", "pvk", "sako", "poplatek za odpad",
        "komunalni odpad", "poplatek za psa", "sipo",
        // Transit & Passes
        "litacka", "salinkarta", "pid litacka", "dpmb", "dpp",
        // Insurance
        "kooperativa", "generali", "ceska pojistovna", "allianz", "uniqa", "pillow", "direct",
        "slavia", "vzp", "ozp", "vozp", "cpzp", "rbp", "pvzp", "pojisteni", "pojist", "povinne ruceni", "havarijni",
        // Housing & Rent
        "najem", "najemne", "fond oprav", "svj", "sprava domu", "bytove druzstvo"
    )

    private val RECURRING_TEXT_HINTS = listOf(
        "predplatne", "pausal", "inkaso", "trvaly", "clenstvi", "clenske",
        "poplatek", "dar", "fitness", "posilovna", "gym", "membership", "subscr"
    )

    fun extractMerchantKey(tx: ImportedBankTransactionEntity): String {
        val candidates = listOf(tx.counterpartyName, tx.message)
        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            val cleaned = BankStatementImporter.cleanPaymentDescription(candidate)
            val query = CzechMerchantCatalog.suggestMerchantSearchQuery(cleaned)
            val norm = if (query.isNotBlank()) CzechMerchantCatalog.normalize(query) else CzechMerchantCatalog.normalize(cleaned)
            if (norm.isNotBlank() && norm != "platba kartou" && norm != "platba" && norm != "transakce") {
                return norm
            }
        }
        val fallback = tx.counterpartyName.ifBlank { tx.message }
        return CzechMerchantCatalog.normalize(fallback).ifBlank { "unknown" }
    }

    private fun extractDisplayName(tx: ImportedBankTransactionEntity, merchantKey: String): String {
        val candidates = listOf(tx.counterpartyName, tx.message)
        for (candidate in candidates) {
            val cleaned = BankStatementImporter.cleanPaymentDescription(candidate)
            if (cleaned.isNotBlank() && !cleaned.equals("platba kartou", ignoreCase = true)) {
                return cleaned.trim()
            }
        }
        return merchantKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun isExcludedByKeywords(text: String): Boolean {
        val norm = CzechMerchantCatalog.normalize(text)
        val words = norm.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        return EXCLUDED_MERCHANT_KEYWORDS.any { kw ->
            if (kw.length <= 4) words.contains(kw) else norm.contains(kw)
        }
    }

    private fun isVerifiedRecurringProvider(merchantKey: String, rawText: String): Boolean {
        val normKey = CzechMerchantCatalog.normalize(merchantKey)
        val normRaw = CzechMerchantCatalog.normalize(rawText)
        val keyWords = normKey.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        val rawWords = normRaw.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        return VERIFIED_RECURRING_PROVIDERS.any { provider ->
            if (provider.length <= 4) {
                keyWords.contains(provider) || rawWords.contains(provider)
            } else {
                normKey.contains(provider) || normRaw.contains(provider)
            }
        }
    }

    private fun hasRecurringTextHint(rawText: String): Boolean {
        val norm = CzechMerchantCatalog.normalize(rawText)
        val words = norm.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        return RECURRING_TEXT_HINTS.any { hint ->
            if (hint.length <= 4) words.contains(hint) else norm.contains(hint)
        }
    }

    fun auditSubscriptions(
        transactions: List<ImportedBankTransactionEntity>,
        swrPct: Double = 4.0,
        dismissedMerchantKeys: Set<String> = emptySet()
    ): SubscriptionAuditSummary {
        val debits = transactions.filter { it.amount < 0 && !it.isNetted }
        if (debits.isEmpty()) {
            return SubscriptionAuditSummary(
                items = emptyList(),
                totalMonthlyBurn = 0.0,
                annualizedBurn = 0.0,
                fireCapitalRequired = 0.0,
                priceCreepCount = 0,
                dismissedCount = dismissedMerchantKeys.size
            )
        }

        // Group debits by robust normalized merchant key
        val byMerchant = debits.groupBy { tx -> extractMerchantKey(tx) }

        val items = mutableListOf<RecurringSubscriptionItem>()
        var activeDismissedCount = 0

        for ((merchantKey, txs) in byMerchant) {
            if (merchantKey.isBlank() || merchantKey == "unknown") continue

            // Check if user dismissed this merchant
            if (merchantKey in dismissedMerchantKeys) {
                activeDismissedCount++
                continue
            }

            val combinedRawText = txs.joinToString(" ") { "${it.counterpartyName} ${it.message}" }

            // 1. Strict negative keyword check: florists, dining, restaurants, bakeries, transit, retail, haircuts, etc.
            if (isExcludedByKeywords(merchantKey) || isExcludedByKeywords(combinedRawText)) {
                continue
            }

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

            // 2. Strict exclusion filter by category: groceries, dining, shopping, transit, health, ATM, investments, transfers
            if (primaryCat in EXCLUDED_CATEGORIES) {
                continue
            }

            val distinctMonths = txs.map { it.yearMonth }.distinct().sorted()
            val isVerifiedProvider = isVerifiedRecurringProvider(merchantKey, combinedRawText)

            // Monthly debit aggregation: if a single month has 2 charges due to 1st/31st calendar-month drift
            // (charges at opposite ends of the month, ~24+ days apart, with similar amounts <= 1.25x),
            // use the per-charge amount so billing-date drift does not trigger a fake +100% price creep or double the monthly burn.
            val txsByMonth = txs.groupBy { it.yearMonth }
            val monthlySums = txsByMonth.mapValues { (_, list) ->
                val rawSum = list.sumOf { abs(it.amount) }
                if (list.size == 2) {
                    val a0 = abs(list[0].amount)
                    val a1 = abs(list[1].amount)
                    val isSimilarAmount = a0 > 0.0 && a1 > 0.0 && (max(a0, a1) / kotlin.math.min(a0, a1)) <= 1.25
                    val sortedDates = list.mapNotNull {
                        try { java.time.LocalDate.parse(it.date) } catch (_: Exception) { null }
                    }.sorted()
                    val isDriftDates = if (sortedDates.size == 2) {
                        val d0 = sortedDates[0]
                        val d1 = sortedDates[1]
                        val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(d0, d1)
                        daysBetween >= 24 || (d0.dayOfMonth <= 5 && d1.dayOfMonth >= 25)
                    } else false

                    if (isSimilarAmount && isDriftDates) {
                        a1 // Use latest single billing charge when 2 identical charges land in one calendar month
                    } else {
                        rawSum
                    }
                } else {
                    rawSum
                }
            }
            val latestMonth = distinctMonths.last()
            val oldestMonth = distinctMonths.first()
            val latestAmount = monthlySums[latestMonth] ?: 0.0
            val oldestAmount = monthlySums[oldestMonth] ?: 0.0

            if (latestAmount <= 0.0) continue

            // 3. Cadence and occurrence validation:
            // Single-month occurrences (distinctMonths.size == 1) can ONLY be classified as ANNUAL if:
            // - The merchant is an explicitly verified recurring/annual provider (energy, insurance, telecom, software license, municipal waste)
            // - AND the charge is >= 1200 CZK
            // Any other single-month occurrence is a one-off and CANNOT be treated as a recurring subscription!
            var cadence = "MONTHLY"
            var normalizedAmount = latestAmount

            val normRaw = CzechMerchantCatalog.normalize(combinedRawText)
            val annualRegex = Regex("""\b(rok|roky|roku|rocni|rocne|annual|annually|yearly)\b""", RegexOption.IGNORE_CASE)
            val semiAnnualRegex = Regex("""\b(pololeti|pololetni|pololetne|semiannual|half-year)\b""", RegexOption.IGNORE_CASE)
            val quarterlyRegex = Regex("""\b(ctvrtleti|ctvrtletni|ctvrtletne|ctvrtlet|quarter|quarterly|kvartal|kvartalni|kvartalne)\b""", RegexOption.IGNORE_CASE)
            val hasAnnualHint = annualRegex.containsMatchIn(normRaw)
            val hasSemiAnnualHint = semiAnnualRegex.containsMatchIn(normRaw)
            val hasQuarterlyHint = quarterlyRegex.containsMatchIn(normRaw)
            val isTypicallyAnnualOnly = normRaw.contains("odpad") || normRaw.contains("poplatek za psa") || normRaw.contains("dalnicni znamk")

            if (distinctMonths.size == 1) {
                val isBroadMarketplace = normRaw.contains("google") || normRaw.contains("apple") || normRaw.contains("amazon") || normRaw.contains("steam") || normRaw.contains("playstation")
                val hasExplicitSubKeyword = normRaw.contains("predplatne") || normRaw.contains("subscri") || normRaw.contains("recurring")

                if ((hasAnnualHint || (isVerifiedProvider && isTypicallyAnnualOnly)) && latestAmount >= 1200.0) {
                    cadence = "ANNUAL"
                    normalizedAmount = latestAmount / 12.0
                } else if (hasSemiAnnualHint) {
                    cadence = "SEMI_ANNUAL"
                    normalizedAmount = latestAmount / 6.0
                } else if (hasQuarterlyHint) {
                    cadence = "QUARTERLY"
                    normalizedAmount = latestAmount / 3.0
                } else if (isBroadMarketplace && !hasExplicitSubKeyword && primaryCat != BankTransactionType.SUBSCRIPTIONS_MEDIA) {
                    // Single one-off store/marketplace purchase: reject
                    continue
                } else if (isVerifiedProvider || primaryCat in EXPLICIT_RECURRING_CATEGORIES) {
                    cadence = "MONTHLY"
                    normalizedAmount = latestAmount
                } else {
                    // Single one-off charge from unverified merchant: reject!
                    continue
                }
            } else {
                // Multi-month occurrences (distinctMonths.size >= 2):
                // Check monthly charge frequency: subscriptions are billed at most 1-2 times a month, not everyday shopping
                val maxMonthlyCount = txs.groupBy { it.yearMonth }.values.maxOfOrNull { it.size } ?: 0
                if (maxMonthlyCount > 2) continue

                // Check amount stability: variance across months must be controlled (<= 1.80x ratio)
                val monthSums = monthlySums.values.toList()
                val minMonthly = monthSums.minOrNull() ?: 0.0
                val maxMonthly = monthSums.maxOrNull() ?: 0.0
                if (minMonthly <= 0.0 || (maxMonthly / minMonthly) > 1.80) continue

                // If not an explicit category or verified provider, require recurring text hint or >= 3 distinct months
                val isExplicitCategory = primaryCat in EXPLICIT_RECURRING_CATEGORIES
                if (!isExplicitCategory && !isVerifiedProvider) {
                    val hasHint = hasRecurringTextHint(combinedRawText)
                    if (!hasHint && distinctMonths.size < 3) {
                        continue
                    }
                }

                // Analyze gap between distinct months for cadence
                val sortedMonths = distinctMonths.mapNotNull {
                    try { java.time.YearMonth.parse(it) } catch (_: Exception) { null }
                }.sorted()

                val avgGapMonths = if (sortedMonths.size >= 2) {
                    val gaps = (0 until sortedMonths.size - 1).map { i ->
                        java.time.temporal.ChronoUnit.MONTHS.between(sortedMonths[i], sortedMonths[i + 1])
                    }
                    gaps.average()
                } else 1.0

                if (avgGapMonths in 2.4..3.6 || hasQuarterlyHint) {
                    cadence = "QUARTERLY"
                    normalizedAmount = latestAmount / 3.0
                } else if (avgGapMonths in 5.4..6.6 || hasSemiAnnualHint) {
                    cadence = "SEMI_ANNUAL"
                    normalizedAmount = latestAmount / 6.0
                } else if (avgGapMonths >= 10.0 || hasAnnualHint) {
                    cadence = "ANNUAL"
                    normalizedAmount = latestAmount / 12.0
                } else {
                    cadence = "MONTHLY"
                    normalizedAmount = latestAmount
                }
            }

            // Sort chronological to find last billed date and representative display name
            val sortedTxs = txs.sortedBy { it.date }
            val latestTx = sortedTxs.last()

            // Price creep detection comparing newest month aggregate to oldest month aggregate
            var prevAmount: Double? = null
            var priceCreepDelta = 0.0
            var priceCreepPct = 0.0

            if (distinctMonths.size >= 2 && oldestAmount > 0.0 && latestAmount > oldestAmount + 1.0) {
                prevAmount = oldestAmount
                priceCreepDelta = latestAmount - oldestAmount
                priceCreepPct = (priceCreepDelta / oldestAmount) * 100.0
            }

            val displayName = extractDisplayName(latestTx, merchantKey)

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
            priceCreepCount = priceCreepCount,
            dismissedCount = activeDismissedCount
        )
    }
}
