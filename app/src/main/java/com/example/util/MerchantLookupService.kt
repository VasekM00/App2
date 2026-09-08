package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Provides online lookup and intelligent category suggestions for unknown or indie merchants.
 * Uses secure HTTPS queries (DuckDuckGo Instant Answer / Knowledge endpoints) with strict timeouts.
 */
object MerchantLookupService {

    private const val CONNECT_TIMEOUT_MS = 2500
    private const val READ_TIMEOUT_MS = 2500
    private const val USER_AGENT = "MartinufinancialsFIRE/2.0"

    data class LookupResult(
        val merchantName: String,
        val suggestedCategory: BankTransactionType?,
        val snippet: String = "",
        val confidence: String = "Normal"
    )

    /**
     * Attempts to query online intelligence for the given raw merchant or payment description.
     */
    suspend fun lookupMerchant(rawMerchant: String): LookupResult = withContext(Dispatchers.IO) {
        val query = CzechMerchantCatalog.suggestMerchantSearchQuery(rawMerchant)
        if (query.isBlank()) {
            return@withContext LookupResult(rawMerchant, null, "No merchant name detected")
        }

        // 1. First check if CzechMerchantCatalog can categorize it directly
        val directMatch = CzechMerchantCatalog.matchCategory(query)
        if (directMatch != null) {
            return@withContext LookupResult(query, directMatch, "Catalog match", "High")
        }

        // 2. Perform online knowledge query via HTTPS
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)

                val abstractText = json.optString("AbstractText", "")
                val heading = json.optString("Heading", "")
                val answer = json.optString("Answer", "")
                val combinedSnippet = "$heading $abstractText $answer".trim()

                if (combinedSnippet.isNotBlank()) {
                    val category = CzechMerchantCatalog.matchCategory(combinedSnippet)
                    if (category != null) {
                        return@withContext LookupResult(
                            merchantName = query,
                            suggestedCategory = category,
                            snippet = combinedSnippet.take(120),
                            confidence = "Online Verified"
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Silently fall back if offline or network unavailable
        }

        // 3. Fallback: Heuristic category guess based on common Czech naming patterns
        val heuristic = inferCategoryFromKeywords(query)
        LookupResult(
            merchantName = query,
            suggestedCategory = heuristic,
            snippet = if (heuristic != null) "Inferred from name" else "Unrecognized merchant",
            confidence = if (heuristic != null) "Inferred" else "None"
        )
    }

    private fun inferCategoryFromKeywords(text: String): BankTransactionType? {
        val norm = CzechMerchantCatalog.normalize(text)
        return when {
            norm.contains("restaur") || norm.contains("hospod") || norm.contains("bistro") ||
            norm.contains("pizz") || norm.contains("kav") || norm.contains("cafe") ||
            norm.contains("kafe") || norm.contains("kebab") || norm.contains("burger") ||
            norm.contains("cukrar") || norm.contains("pek") || norm.contains("reznict") ->
                BankTransactionType.DINING_RESTAURANT

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
