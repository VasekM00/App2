package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/**
 * Provides offline lookup and intelligent category suggestions for unknown or indie merchants.
 * Operates purely locally using the Czech Merchant Catalog and keyword heuristics with zero data leakage.
 */
object MerchantLookupService {

    private val DINING_PATTERN = Regex("""\b(?:restaur\w*|hospod\w*|bistr\w*|pizz\w*|kavar\w*|kafet\w*|cafe|kafe|kebab\w*|burger\w*|cukrar\w*)""")
    private val GROCERIES_PATTERN = Regex("""\b(?:potravin\w*|vecerk\w*|lahudk\w*|pekar\w*|reznic\w*|supermarket\w*|hypermarket\w*|mlekarn\w*)""")
    private val HEALTH_PATTERN = Regex("""\b(?:lekarn\w*|droger\w*|optik\w*|dental\w*|dr\s+max|benu|poliklinik\w*|klinik\w*|fyzio\w*)""")
    private val TRANSPORT_PATTERN = Regex("""\b(?:autoservis\w*|pneuservis\w*|motoservis\w*|pneu\w*|cerpaci\w*|benzin\w*|taxi\w*|jizden\w*|doprav\w*|parkov\w*)""")
    private val SHOPPING_PATTERN = Regex("""\b(?:elektro\w*|nabytek|odev\w*|sport\w*|obuv\w*|knihkup\w*|papirnic\w*)""")
    private val SERVICES_PATTERN = Regex("""\b(?:salon\w*|kadern\w*|barber\w*|cistir\w*|posta\w*|pojist\w*|vodovod\w*|teplarn\w*)""")
    private val CHARITY_PATTERN = Regex("""\b(?:nadac\w*|charit\w*|darc\w*|daruj\w*|darovat\w*|dar|sirius)\b""")
    private val HOUSING_PATTERN = Regex("""\b(?:najem\w*|najmu|svj|fond\s+oprav|bytov\w*)\b""")
    private val SUBSCRIPTION_PATTERN = Regex("""\b(?:predplat\w*|subscription\w*|streaming|hosting|antivirus)\b""")
    private val INVESTMENT_DPS_PATTERN = Regex("""\b(?:penzijn\w*|dps|penze)\b""")
    private val INVESTMENT_PORTU_PATTERN = Regex("""\b(?:investic\w*|broker\w*|akcie|etf)\b""")

    data class LookupResult(
        val merchantName: String,
        val suggestedCategory: BankTransactionType?,
        val snippet: String = "",
        val confidence: String = "Normal"
    )

    /**
     * Identifies merchant category using local Czech merchant catalog and intelligent keyword heuristics.
     * Operates 100% offline with zero data leakage.
     */
    suspend fun lookupMerchant(rawMerchant: String): LookupResult = withContext(Dispatchers.Default) {
        if (rawMerchant.isBlank()) {
            return@withContext LookupResult(rawMerchant, null, "No merchant name detected")
        }
        val query = CzechMerchantCatalog.suggestMerchantSearchQuery(rawMerchant)
        val displayQuery = query.ifBlank { rawMerchant.trim() }

        // 1. First check if CzechMerchantCatalog can categorize either raw or cleaned text directly
        val directMatch = CzechMerchantCatalog.matchCategory(rawMerchant)
            ?: CzechMerchantCatalog.matchCategory(displayQuery)
        if (directMatch != null) {
            return@withContext LookupResult(displayQuery, directMatch, "Catalog match", "High")
        }

        // 2. Local heuristic category guess based on word-bounded Czech naming patterns
        val heuristic = inferCategoryFromKeywords(rawMerchant)
            ?: inferCategoryFromKeywords(displayQuery)
        LookupResult(
            merchantName = displayQuery,
            suggestedCategory = heuristic,
            snippet = if (heuristic != null) "Inferred from merchant keywords" else "Unrecognized merchant",
            confidence = if (heuristic != null) "Inferred" else "None"
        )
    }

    private fun inferCategoryFromKeywords(text: String): BankTransactionType? {
        val norm = CzechMerchantCatalog.normalizeSimple(text)
        if (norm.isBlank()) return null
        return when {
            DINING_PATTERN.containsMatchIn(norm) -> BankTransactionType.DINING_RESTAURANT
            GROCERIES_PATTERN.containsMatchIn(norm) -> BankTransactionType.GROCERIES
            HEALTH_PATTERN.containsMatchIn(norm) -> BankTransactionType.HEALTH_DRUGSTORE
            TRANSPORT_PATTERN.containsMatchIn(norm) -> BankTransactionType.TRANSPORTATION
            SHOPPING_PATTERN.containsMatchIn(norm) -> BankTransactionType.SHOPPING_GOODS
            SERVICES_PATTERN.containsMatchIn(norm) -> BankTransactionType.SERVICES_UTILITIES
            CHARITY_PATTERN.containsMatchIn(norm) -> BankTransactionType.CHARITY_DONATION
            HOUSING_PATTERN.containsMatchIn(norm) -> BankTransactionType.HOUSING_RENT
            SUBSCRIPTION_PATTERN.containsMatchIn(norm) -> BankTransactionType.SUBSCRIPTIONS_MEDIA
            INVESTMENT_DPS_PATTERN.containsMatchIn(norm) -> BankTransactionType.INVESTMENT_DPS
            INVESTMENT_PORTU_PATTERN.containsMatchIn(norm) -> BankTransactionType.INVESTMENT_PORTU
            else -> null
        }
    }
}
