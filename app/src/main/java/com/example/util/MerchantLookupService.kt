package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/**
 * Provides offline lookup and intelligent category suggestions for unknown or indie merchants.
 * Operates purely locally using the Czech Merchant Catalog and keyword heuristics with zero data leakage.
 */
object MerchantLookupService {

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
        val query = CzechMerchantCatalog.suggestMerchantSearchQuery(rawMerchant)
        if (query.isBlank()) {
            return@withContext LookupResult(rawMerchant, null, "No merchant name detected")
        }

        // 1. First check if CzechMerchantCatalog can categorize it directly
        val directMatch = CzechMerchantCatalog.matchCategory(query)
        if (directMatch != null) {
            return@withContext LookupResult(query, directMatch, "Catalog match", "High")
        }

        // 2. Local heuristic category guess based on common Czech naming patterns
        val heuristic = inferCategoryFromKeywords(query)
        LookupResult(
            merchantName = query,
            suggestedCategory = heuristic,
            snippet = if (heuristic != null) "Inferred from merchant keywords" else "Unrecognized merchant",
            confidence = if (heuristic != null) "Inferred" else "None"
        )
    }

    private fun inferCategoryFromKeywords(text: String): BankTransactionType? {
        val norm = CzechMerchantCatalog.normalize(text)
        return when {
            norm.contains("restaur") || norm.contains("hospod") || norm.contains("bistro") ||
            norm.contains("pizz") || norm.contains("kav") || norm.contains("cafe") ||
            norm.contains("kafe") || norm.contains("kebab") || norm.contains("burger") ||
            norm.contains("cukrar") ->
                BankTransactionType.DINING_RESTAURANT

            norm.contains("potravin") || norm.contains("vecerk") || norm.contains("lahudk") ||
            norm.contains("pek") || norm.contains("reznict") || norm.contains("supermarket") ->
                BankTransactionType.GROCERIES

            norm.contains("lekarn") || norm.contains("droger") || norm.contains("optik") ||
            norm.contains("dental") || norm.contains("dr max") || norm.contains("benu") ->
                BankTransactionType.HEALTH_DRUGSTORE

            norm.contains("servis") || norm.contains("pneu") || norm.contains("cerpaci") ||
            norm.contains("benzin") || norm.contains("taxi") || norm.contains("jizden") ||
            norm.contains("doprav") ->
                BankTransactionType.TRANSPORTATION

            norm.contains("elektro") || norm.contains("nabytek") || norm.contains("odev") ||
            norm.contains("sport") || norm.contains("obuv") ->
                BankTransactionType.SHOPPING_GOODS

            norm.contains("salon") || norm.contains("kadern") || norm.contains("barber") ||
            norm.contains("cistir") || norm.contains("posta") ->
                BankTransactionType.SERVICES_UTILITIES

            norm.contains("nadac") || norm.contains("charit") || norm.contains("dar") ||
            norm.contains("fond") || norm.contains("sirius") ->
                BankTransactionType.CHARITY_DONATION

            else -> null
        }
    }
}
