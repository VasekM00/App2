package com.example.util

import com.example.data.ImportedBankTransactionEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
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

    private val SPLIT_ALNUM_REGEX = Regex("[^a-z0-9]+")
    private val GYM_WORD_REGEX = Regex("""\bgym\b""")
    private val SIPO_WORD_REGEX = Regex("""\bsipo\b""")
    private val ANNUAL_HINT_REGEX = Regex("""\b(?:rok|roky|roku|rocni|rocne|annual|annually|yearly)\b""", RegexOption.IGNORE_CASE)
    private val SEMI_ANNUAL_HINT_REGEX = Regex("""\b(?:pololeti|pololetni|pololetne|semiannual|semi-annual|half-year)\b""", RegexOption.IGNORE_CASE)
    private val QUARTERLY_HINT_REGEX = Regex("""\b(?:ctvrtleti|ctvrtletni|ctvrtletne|ctvrtlet|quarter|quarterly|kvartal|kvartalni|kvartalne)\b""", RegexOption.IGNORE_CASE)

    private val EXCLUDED_CATEGORIES = setOf(
        BankTransactionType.GROCERIES,
        BankTransactionType.DINING_RESTAURANT,
        BankTransactionType.SHOPPING_GOODS,
        BankTransactionType.TRANSPORTATION,
        BankTransactionType.HEALTH_DRUGSTORE,
        BankTransactionType.ATM_CASH,
        BankTransactionType.HOUSING_RENT,
        BankTransactionType.CHARITY_DONATION,
        BankTransactionType.INVESTMENT_PORTU,
        BankTransactionType.INVESTMENT_DIP,
        BankTransactionType.INVESTMENT_DPS,
        BankTransactionType.INTERNAL_TRANSFER,
        BankTransactionType.SALARY_VACLAV,
        BankTransactionType.SALARY_ELEONORA,
        BankTransactionType.PARENTAL_BENEFIT,
        BankTransactionType.FAMILY_GIFT,
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
        "lahudky", "zelin", "potravin", "vecerka", "supermarket", "hypermarket",
        // Retail goods, clothing, electronics & books
        "knih", "knihy", "papirnictvi", "odev", "obuv", "textil", "drogerie", "lekarn",
        "nabytek", "hracky", "sportisimo", "decathlon", "intersport", "sanasport",
        "elektro", "optik", "alza", "datart", "mall", "kvetiny",
        // Transit, Fuel & Parking
        "cerpaci", "benzin", "nafta", "orlen", "benzina", "mol", "shell", "omv", "eurooil",
        "parking", "parkov", "jizdenk", "taxi", "bolt ride", "uber", "liftago",
        // One-off postal, courier, hair, repair & leisure venues
        "posta", "ceska posta", "balikovna", "zasilkovna", "packeta", "ppl", "dpd", "gls", "dhl", "fedex", "ups",
        "kadernic", "barber", "salon", "cistirna", "pradelna", "zamecnik", "hodinovy manzel",
        "divadlo", "kino", "vstupenk", "ticket", "zoo", "muzeum", "galerie", "hotel", "ubytovani",
        "tugendhat", "spilberk", "filharmonie", "besedni dum", "fleda", "sono centrum",
        "metro music bar", "hvezdarna", "planetarium", "scala"
    )

    private val PRE_NORMALIZED_VERIFIED_PROVIDERS: List<String> = listOf(
        // Media, Streaming & Cloud
        "spotify", "netflix", "youtube", "google", "apple", "disney", "hbo", "voyo", "prima plus",
        "skyshowtime", "canal plus", "canal+", "o2 tv", "sledovanitv", "lepsi tv", "kuki", "skylink",
        "audible", "audioteka", "patreon", "herohero", "forendors", "pickey",
        "substack", "steam", "playstation", "xbox", "microsoft", "openai", "chatgpt", "anthropic",
        "claude", "midjourney", "perplexity", "jetbrains", "github", "adobe", "notion", "canva",
        "figma", "icloud", "dropbox", "1password", "bitwarden", "proton", "nordvpn", "amazon prime", "amazon",
        "wedos", "forpsi",
        // Telecom & Internet
        "vodafone", "o2", "t mobile", "tmobile", "nordic telecom", "poda", "nej cz", "cetin",
        // Energy & Utilities
        "cez", "pre", "e on", "eon", "innogy", "mnd", "prazska plynarenska", "centropol", "epet",
        "teplarny", "bvk", "brnenske vodarny", "vodarny", "veolia", "pvk", "sako", "poplatek za odpad",
        "komunalni odpad", "poplatek za psa", "sipo",
        // Transit & Passes
        "litacka", "salinkarta", "pid litacka", "dpmb", "dpp",
        // Fitness / Wellness memberships
        "multisport",
        // Insurance
        "kooperativa", "generali", "ceska pojistovna", "allianz", "uniqa", "pillow", "direct",
        "slavia", "vzp", "ozp", "vozp", "cpzp", "rbp", "pvzp", "pojisteni", "pojist", "povinne ruceni", "havarijni"
    ).map { CzechMerchantCatalog.normalizeSimple(it) }.filter { it.isNotBlank() }.distinct()

    private val RECURRING_TEXT_HINTS = listOf(
        "predplatne", "pausal", "inkaso", "trvaly", "clenstvi", "clenske",
        "poplatek", "fitness", "posilovna", "gym", "multisport", "membership", "subscr"
    )

    fun extractMerchantKey(tx: ImportedBankTransactionEntity): String {
        val candidates = listOf(tx.counterpartyName, tx.message)
        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            val cleaned = BankStatementImporter.cleanPaymentDescription(candidate)
            val query = CzechMerchantCatalog.suggestMerchantSearchQueryFromCleaned(cleaned)
            val norm = if (query.isNotBlank()) CzechMerchantCatalog.normalize(query) else CzechMerchantCatalog.normalize(cleaned)
            val simple = if (query.isNotBlank()) CzechMerchantCatalog.normalizeSimple(query) else CzechMerchantCatalog.normalizeSimple(cleaned)
            if (simple == "e on") return "e on"
            if (simple == "t mobile") return "t mobile"
            if (norm.isNotBlank() && norm != "platba kartou" && norm != "platba" && norm != "transakce") {
                return norm
            }
            if (simple.isNotBlank() && simple != "platba kartou" && simple != "platba" && simple != "transakce") {
                return simple
            }
        }
        val fallback = tx.counterpartyName.ifBlank { tx.message }
        return CzechMerchantCatalog.normalize(fallback)
            .ifBlank { CzechMerchantCatalog.normalizeSimple(fallback) }
            .ifBlank { "unknown" }
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

    private fun isExcludedByKeywords(text: String, isVerified: Boolean): Boolean {
        val norm = CzechMerchantCatalog.normalize(text)
        val simple = CzechMerchantCatalog.normalizeSimple(text)
        val words = SPLIT_ALNUM_REGEX.split(simple).filter { it.isNotBlank() }
        val paddedSimple = " $simple "
        // Known TV/streaming verified plans ("O2 TV Sport", "Canal+ Sport") must not be rejected by retail "sport"
        if (!isVerified && words.contains("sport")) return true
        if (words.contains("market") && !isVerified) return true
        val isSipo = isVerified && SIPO_WORD_REGEX.containsMatchIn(simple)
        return EXCLUDED_MERCHANT_KEYWORDS.any { kw ->
            if (isSipo && (kw == "posta" || kw == "ceska posta")) return@any false
            when {
                kw.contains(' ') -> paddedSimple.contains(" $kw ") || norm.contains(kw)
                kw.length <= 4 -> words.contains(kw)
                else -> norm.contains(kw) || simple.contains(kw)
            }
        }
    }

    private fun isVerifiedRecurringProvider(merchantKey: String, rawText: String): Boolean {
        val normKey = CzechMerchantCatalog.normalize(merchantKey)
        val normRaw = CzechMerchantCatalog.normalize(rawText)
        val simpleKey = CzechMerchantCatalog.normalizeSimple(merchantKey)
        val simpleRaw = CzechMerchantCatalog.normalizeSimple(rawText)
        val keyWords = SPLIT_ALNUM_REGEX.split(simpleKey).filter { it.isNotBlank() }
        val rawWords = SPLIT_ALNUM_REGEX.split(simpleRaw).filter { it.isNotBlank() }
        val paddedKey = " $simpleKey "
        val paddedRaw = " $simpleRaw "
        return PRE_NORMALIZED_VERIFIED_PROVIDERS.any { cleanProv ->
            when {
                cleanProv.contains(' ') -> paddedKey.contains(" $cleanProv ") || paddedRaw.contains(" $cleanProv ")
                cleanProv.length <= 4 -> keyWords.contains(cleanProv) || rawWords.contains(cleanProv)
                else -> normKey.contains(cleanProv) || normRaw.contains(cleanProv) ||
                    simpleKey.contains(cleanProv) || simpleRaw.contains(cleanProv)
            }
        }
    }

    private fun hasRecurringTextHint(rawText: String): Boolean {
        val norm = CzechMerchantCatalog.normalize(rawText)
        val words = SPLIT_ALNUM_REGEX.split(norm).filter { it.isNotBlank() }
        return RECURRING_TEXT_HINTS.any { hint ->
            if (hint.length <= 4) words.contains(hint) else norm.contains(hint)
        }
    }

    fun auditSubscriptions(
        transactions: List<ImportedBankTransactionEntity>,
        swrPct: Double = 4.0,
        dismissedMerchantKeys: Set<String> = emptySet(),
        safetyBufferPct: Double = 0.0
    ): SubscriptionAuditSummary {
        val debits = transactions.filter { it.amount.isFinite() && it.amount < 0 && !it.isNetted }
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

        val importedMonths = transactions.asSequence()
            .mapNotNull { tx ->
                val ymStr = tx.yearMonth.ifBlank { tx.date.take(7) }
                try { YearMonth.parse(ymStr) } catch (_: Exception) { null }
            }
            .distinct()
            .sorted()
            .toList()
        val latestImportedYm = importedMonths.maxOrNull()

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
            val isVerifiedProvider = isVerifiedRecurringProvider(merchantKey, combinedRawText)

            // 1. Strict negative keyword check: florists, dining, restaurants, bakeries, transit, retail, venues, couriers
            if (isExcludedByKeywords(merchantKey, isVerifiedProvider) || isExcludedByKeywords(combinedRawText, isVerifiedProvider)) {
                continue
            }

            // Determine primary category with resilient fallback
            val catCounts = txs.groupingBy {
                val catStr = it.category.trim().uppercase(Locale.ROOT)
                when {
                    catStr == "GROCERIES" || catStr.contains("GROCERY") -> BankTransactionType.GROCERIES
                    catStr == "DINING_RESTAURANT" || catStr == "RESTAURANTS_DINING" || catStr.contains("DINING") || catStr.contains("RESTAURANT") -> BankTransactionType.DINING_RESTAURANT
                    catStr == "TRANSPORTATION" || catStr.contains("TRANSPORT") -> BankTransactionType.TRANSPORTATION
                    catStr == "SHOPPING_GOODS" || catStr.contains("SHOPPING") -> BankTransactionType.SHOPPING_GOODS
                    catStr == "HEALTH_DRUGSTORE" || catStr.contains("HEALTH") -> BankTransactionType.HEALTH_DRUGSTORE
                    catStr == "HOUSING_RENT" || catStr == "RENT" -> BankTransactionType.HOUSING_RENT
                    else -> try {
                        BankTransactionType.valueOf(catStr)
                    } catch (_: Exception) {
                        BankTransactionType.GENERAL_EXPENSE
                    }
                }
            }.eachCount()
            val primaryCat = catCounts.maxByOrNull { it.value }?.key ?: BankTransactionType.GENERAL_EXPENSE

            val normCombined = CzechMerchantCatalog.normalizeSimple("$merchantKey $combinedRawText")
            val isGymOrFitness = !normCombined.contains("gymbeam") &&
                !normCombined.contains("drogerie") &&
                !normCombined.contains("lekarn") &&
                (GYM_WORD_REGEX.containsMatchIn(normCombined) ||
                    normCombined.contains("fitness") ||
                    normCombined.contains("posilovna") ||
                    normCombined.contains("multisport"))
            val isSipo = SIPO_WORD_REGEX.containsMatchIn(normCombined)

            val bypassesExcludedCategory = when (primaryCat) {
                BankTransactionType.TRANSPORTATION -> isVerifiedProvider
                BankTransactionType.HOUSING_RENT -> isSipo
                BankTransactionType.HEALTH_DRUGSTORE -> isGymOrFitness
                else -> false
            }

            // 2. Strict exclusion filter by category (including CHARITY_DONATION and regular rent)
            if (primaryCat in EXCLUDED_CATEGORIES && !bypassesExcludedCategory) {
                continue
            }

            val sortedTxs = txs.sortedBy { it.date }
            val distinctMonths = sortedTxs.map { it.yearMonth }.filter { it.isNotBlank() }.distinct().sorted()
            if (distinctMonths.isEmpty()) continue

            // Monthly debit aggregation: if a single month has 2 charges due to 1st/31st calendar-month drift
            // (charges at opposite ends of the month, ~24+ days apart, with similar amounts <= 1.25x),
            // use the latest single charge so billing-date drift does not double burn or flag fake creep.
            val txsByMonth = sortedTxs.groupBy { it.yearMonth }
            val monthlySums = txsByMonth.mapValues { (_, list) ->
                val rawSum = list.sumOf { abs(it.amount) }
                if (list.size == 2) {
                    val a0 = abs(list[0].amount)
                    val a1 = abs(list[1].amount)
                    val isSimilarAmount = a0 > 0.0 && a1 > 0.0 && (max(a0, a1) / kotlin.math.min(a0, a1)) <= 1.25
                    val sortedDates = list.mapNotNull {
                        try { LocalDate.parse(it.date) } catch (_: Exception) { null }
                    }.sorted()
                    val isDriftDates = if (sortedDates.size == 2) {
                        val d0 = sortedDates[0]
                        val d1 = sortedDates[1]
                        val daysBetween = ChronoUnit.DAYS.between(d0, d1)
                        daysBetween >= 24 || (d0.dayOfMonth <= 5 && d1.dayOfMonth >= 25)
                    } else false

                    if (isSimilarAmount && isDriftDates) {
                        a1
                    } else {
                        rawSum
                    }
                } else {
                    rawSum
                }
            }
            val latestMonth = distinctMonths.last()
            val latestAmount = monthlySums[latestMonth] ?: 0.0
            val prevDistinctAmount = if (distinctMonths.size >= 2) {
                monthlySums[distinctMonths[distinctMonths.size - 2]] ?: 0.0
            } else {
                0.0
            }

            if (latestAmount <= 0.0) continue

            // 3. Cadence and occurrence validation (weekly detection runs across single or multiple months)
            var cadence = "MONTHLY"
            var normalizedAmount = latestAmount

            val normRaw = CzechMerchantCatalog.normalizeSimple(combinedRawText)
            val hasAnnualHint = ANNUAL_HINT_REGEX.containsMatchIn(normRaw)
            val hasSemiAnnualHint = SEMI_ANNUAL_HINT_REGEX.containsMatchIn(normRaw)
            val hasQuarterlyHint = QUARTERLY_HINT_REGEX.containsMatchIn(normRaw)
            val isTypicallyAnnualOnly = normRaw.contains("odpad") || normRaw.contains("poplatek za psa") || normRaw.contains("dalnicni znamk")
            val isExplicitCategory = primaryCat in EXPLICIT_RECURRING_CATEGORIES || bypassesExcludedCategory

            val parsedChargeDates = sortedTxs.mapNotNull {
                try { LocalDate.parse(it.date) } catch (_: Exception) { null }
            }.sorted()
            val interChargeDayGaps = if (parsedChargeDates.size >= 4) {
                (0 until parsedChargeDates.size - 1).map { idx ->
                    ChronoUnit.DAYS.between(parsedChargeDates[idx], parsedChargeDates[idx + 1])
                }.sorted()
            } else emptyList()
            val medianDayGap = if (interChargeDayGaps.isNotEmpty()) {
                interChargeDayGaps[interChargeDayGaps.size / 2].toDouble()
            } else Double.NaN
            val isWeeklyCadence = sortedTxs.size >= 4 && medianDayGap.isFinite() && medianDayGap in 5.0..9.0

            if (isWeeklyCadence) {
                val sortedCharges = sortedTxs.map { abs(it.amount) }.sorted()
                val medianCharge = sortedCharges[sortedCharges.size / 2]
                cadence = "WEEKLY"
                normalizedAmount = medianCharge * (52.0 / 12.0)
            } else if (distinctMonths.size == 1) {
                val isBroadMarketplace = normRaw.contains("google play") ||
                    normRaw.contains("apple com bill") ||
                    normRaw.contains("app store") ||
                    normRaw.contains("itunes") ||
                    normRaw.contains("steam") ||
                    normRaw.contains("playstation") ||
                    normRaw.contains("xbox") ||
                    normRaw.contains("nintendo") ||
                    normRaw.contains("epic games") ||
                    normRaw.contains("microsoft") ||
                    normRaw.contains("amazon")
                val hasExplicitSubKeyword = normRaw.contains("predplatne") ||
                    normRaw.contains("subscri") ||
                    normRaw.contains("recurring") ||
                    normRaw.contains("membership") ||
                    normRaw.contains("clenstvi") ||
                    hasAnnualHint ||
                    hasSemiAnnualHint ||
                    hasQuarterlyHint

                if (isBroadMarketplace && !hasExplicitSubKeyword) {
                    continue
                } else if ((isVerifiedProvider || isExplicitCategory) &&
                    (hasAnnualHint || (isVerifiedProvider && isTypicallyAnnualOnly)) &&
                    latestAmount >= 1200.0
                ) {
                    cadence = "ANNUAL"
                    normalizedAmount = latestAmount / 12.0
                } else if ((isVerifiedProvider || isExplicitCategory) &&
                    hasSemiAnnualHint &&
                    latestAmount >= 600.0
                ) {
                    cadence = "SEMI_ANNUAL"
                    normalizedAmount = latestAmount / 6.0
                } else if ((isVerifiedProvider || isExplicitCategory) &&
                    hasQuarterlyHint &&
                    latestAmount >= 300.0
                ) {
                    cadence = "QUARTERLY"
                    normalizedAmount = latestAmount / 3.0
                } else if (isVerifiedProvider || isExplicitCategory) {
                    cadence = "MONTHLY"
                    normalizedAmount = latestAmount
                } else {
                    // Single one-off charge from unverified merchant: reject!
                    continue
                }
            } else {
                val maxMonthlyCount = txsByMonth.values.maxOfOrNull { it.size } ?: 0
                if (!isVerifiedProvider && !isExplicitCategory && maxMonthlyCount > 5) continue

                val monthSums = monthlySums.values.toList()
                val minMonthly = monthSums.minOrNull() ?: 0.0
                val maxMonthly = monthSums.maxOrNull() ?: 0.0
                if (!isVerifiedProvider && !isExplicitCategory) {
                    val maxRatio = if (hasRecurringTextHint(combinedRawText)) 3.0 else 1.80
                    if (minMonthly <= 0.0 || (maxMonthly / minMonthly) > maxRatio) continue
                    val hasHint = hasRecurringTextHint(combinedRawText)
                    if (!hasHint && distinctMonths.size < 3) {
                        continue
                    }
                }

                val sortedMonths = distinctMonths.mapNotNull {
                    try { YearMonth.parse(it) } catch (_: Exception) { null }
                }.sorted()

                val effectiveGaps = if (sortedMonths.size >= 2) {
                    (0 until sortedMonths.size - 1).map { idx ->
                        val m0 = sortedMonths[idx]
                        val m1 = sortedMonths[idx + 1]
                        val skipped = importedMonths.count { it > m0 && it < m1 }
                        (skipped + 1).toDouble()
                    }.sorted()
                } else emptyList()
                val medianEffectiveGap = if (effectiveGaps.isNotEmpty()) {
                    effectiveGaps[effectiveGaps.size / 2]
                } else 1.0

                // Observed gap (accounting for non-contiguous imports) takes priority when monthly (< 2.2)
                if (medianEffectiveGap < 2.2) {
                    cadence = "MONTHLY"
                    normalizedAmount = latestAmount
                } else if (medianEffectiveGap in 2.4..3.6 || (medianEffectiveGap >= 2.2 && hasQuarterlyHint)) {
                    cadence = "QUARTERLY"
                    normalizedAmount = latestAmount / 3.0
                } else if (medianEffectiveGap in 5.4..6.6 || (medianEffectiveGap >= 4.0 && hasSemiAnnualHint)) {
                    cadence = "SEMI_ANNUAL"
                    normalizedAmount = latestAmount / 6.0
                } else if (medianEffectiveGap >= 10.0 || hasAnnualHint) {
                    cadence = "ANNUAL"
                    normalizedAmount = latestAmount / 12.0
                } else {
                    cadence = "MONTHLY"
                    normalizedAmount = latestAmount
                }
            }

            // Exclude canceled/expired subscriptions that haven't billed in newer imported months
            val lastBilledYm = try { YearMonth.parse(latestMonth) } catch (_: Exception) { null }
            if (latestImportedYm != null && lastBilledYm != null && lastBilledYm < latestImportedYm) {
                val importedMonthsAfterLast = importedMonths.count { it > lastBilledYm }
                val calMonthsAfterLast = ChronoUnit.MONTHS.between(lastBilledYm, latestImportedYm)
                val isExpired = when (cadence) {
                    "WEEKLY", "MONTHLY" -> (importedMonthsAfterLast >= 2) || (calMonthsAfterLast >= 3 && importedMonthsAfterLast >= 1)
                    "QUARTERLY" -> calMonthsAfterLast >= 5 && importedMonthsAfterLast >= 2
                    "SEMI_ANNUAL" -> calMonthsAfterLast >= 8 && importedMonthsAfterLast >= 2
                    "ANNUAL" -> calMonthsAfterLast >= 14 && importedMonthsAfterLast >= 2
                    else -> false
                }
                if (isExpired) continue
            }

            val latestTx = sortedTxs.last()

            // Price creep compares latest billing month against immediately preceding billing month
            var prevAmount: Double? = null
            var priceCreepDelta = 0.0
            var priceCreepPct = 0.0

            if (distinctMonths.size >= 2 && prevDistinctAmount > 0.0 && latestAmount > prevDistinctAmount + 1.0) {
                prevAmount = prevDistinctAmount
                priceCreepDelta = latestAmount - prevDistinctAmount
                priceCreepPct = (priceCreepDelta / prevDistinctAmount) * 100.0
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
        val safeSwr = if (swrPct.isFinite()) max(0.001, swrPct / 100.0) else 0.04
        val safeBuffer = if (safetyBufferPct.isFinite() && safetyBufferPct > 0.0) safetyBufferPct else 0.0
        val bufferMult = (1.0 + safeBuffer / 100.0).coerceAtLeast(0.0)
        val fireCapitalRequired = (annualizedBurn / safeSwr) * bufferMult
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
