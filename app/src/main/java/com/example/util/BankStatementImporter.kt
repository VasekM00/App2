package com.example.util

import android.content.ContentResolver
import android.net.Uri
import com.example.util.statement.AboGpcParser
import com.example.util.statement.BankDetector
import com.example.util.statement.CsvStatementParser
import com.example.util.statement.CzechValueParsers
import com.example.util.statement.PaymentDescriptionCleaner
import com.example.util.statement.PdfStatementParser
import com.example.util.statement.StatementTextDecoder
import com.example.util.statement.TransactionCategorizer
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Thin facade over modular bank statement parsing, decoding, value parsing,
 * description cleaning, and transaction categorization helpers.
 */
object BankStatementImporter {

    const val MAX_STATEMENT_BYTES: Int = StatementTextDecoder.MAX_STATEMENT_BYTES

    fun isWithinSizeLimit(byteCount: Int): Boolean = StatementTextDecoder.isWithinSizeLimit(byteCount)

    fun parseStatement(
        bytes: ByteArray,
        knownFamilyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): StatementParseSummary {
        if (!isWithinSizeLimit(bytes.size)) return TransactionCategorizer.emptySummary(BankType.GENERIC)
        return parseStatement(
            inputStream = ByteArrayInputStream(bytes),
            knownFamilyAccounts = knownFamilyAccounts,
            userOverrides = userOverrides,
            householdNames = householdNames,
            knownParentalGiftAccounts = knownParentalGiftAccounts
        )
    }

    fun parseStatement(
        inputStream: InputStream,
        knownFamilyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): StatementParseSummary {
        val bytes = StatementTextDecoder.readStreamBounded(inputStream)
            ?: return TransactionCategorizer.emptySummary(BankType.GENERIC)

        if (PdfTextExtractor.isPdf(bytes)) {
            return parsePdfStatement(
                pdfBytes = bytes,
                knownFamilyAccounts = knownFamilyAccounts,
                userOverrides = userOverrides,
                householdNames = householdNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts
            )
        }

        val content = decodeStatementText(bytes)
        val rawLines = content.lines()
        if (isAboFormat(rawLines)) {
            val abo = parseAboGpc(
                lines = rawLines,
                familyAccounts = knownFamilyAccounts,
                userOverrides = userOverrides,
                householdNames = householdNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts
            )
            if (abo.transactions.isNotEmpty()) return abo
        }

        val lines = splitCsvLines(content)
        val bankType = BankDetector.detectBankType(lines)
        return CsvStatementParser.parseCsvStatement(
            lines = lines,
            bankType = bankType,
            knownFamilyAccounts = knownFamilyAccounts,
            userOverrides = userOverrides,
            householdNames = householdNames,
            knownParentalGiftAccounts = knownParentalGiftAccounts
        )
    }

    fun decodeStatementText(bytes: ByteArray): String = StatementTextDecoder.decodeStatementText(bytes)

    fun countMojibakeSequences(text: String): Int = StatementTextDecoder.countMojibakeSequences(text)

    fun countCzechAccents(text: String): Int = StatementTextDecoder.countCzechAccents(text)

    fun isAboFormat(lines: List<String>): Boolean = AboGpcParser.isAboFormat(lines)

    fun parseAboGpc(
        lines: List<String>,
        familyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): StatementParseSummary = AboGpcParser.parseAboGpc(
        lines = lines,
        familyAccounts = familyAccounts,
        userOverrides = userOverrides,
        householdNames = householdNames,
        knownParentalGiftAccounts = knownParentalGiftAccounts
    )

    fun parsePdfStatement(
        pdfBytes: ByteArray,
        knownFamilyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): StatementParseSummary = PdfStatementParser.parsePdfStatement(
        pdfBytes = pdfBytes,
        knownFamilyAccounts = knownFamilyAccounts,
        userOverrides = userOverrides,
        householdNames = householdNames,
        knownParentalGiftAccounts = knownParentalGiftAccounts
    )

    fun extractAccount(text: String): String = CzechValueParsers.extractAccount(text)

    fun cleanPaymentDescription(
        raw: String,
        householdNames: Collection<String> = emptyList()
    ): String = PaymentDescriptionCleaner.cleanPaymentDescription(raw, householdNames)

    internal fun extractAllAmountsPublic(text: String): List<Double> = CzechValueParsers.extractAllAmounts(text)

    fun splitCsvLines(content: String): List<String> = StatementTextDecoder.splitCsvLines(content)

    fun detectCsvDelimiter(lines: List<String>): Char = StatementTextDecoder.detectCsvDelimiter(lines)

    fun categorizeTransaction(
        amount: Double,
        counterpartyAcc: String,
        counterpartyName: String,
        message: String,
        vs: String,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        rawContext: String = "",
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): BankTransactionType = TransactionCategorizer.categorizeTransaction(
        amount = amount,
        counterpartyAcc = counterpartyAcc,
        counterpartyName = counterpartyName,
        message = message,
        vs = vs,
        familyAccounts = familyAccounts,
        userOverrides = userOverrides,
        rawContext = rawContext,
        householdNames = householdNames,
        knownParentalGiftAccounts = knownParentalGiftAccounts
    )

    fun determineNettingReason(
        cat: BankTransactionType,
        counterpartyName: String,
        message: String
    ): String = TransactionCategorizer.determineNettingReason(cat, counterpartyName, message)

    fun recomputeSummary(
        summary: StatementParseSummary,
        updatedTransactions: List<ParsedBankTransaction>
    ): StatementParseSummary = TransactionCategorizer.recomputeSummary(summary, updatedTransactions)

    fun parseCzechAmountDetailed(
        str: String,
        explicitCurrency: String? = null,
        eurRate: Double = FxRateService.getRate("EUR"),
        usdRate: Double = FxRateService.getRate("USD")
    ): FxRateService.CurrencyConversionDetails =
        CzechValueParsers.parseCzechAmountDetailed(str, explicitCurrency, eurRate, usdRate)

    fun parseCzechAmount(
        str: String,
        explicitCurrency: String? = null,
        eurRate: Double = FxRateService.getRate("EUR"),
        usdRate: Double = FxRateService.getRate("USD")
    ): Double = CzechValueParsers.parseCzechAmount(str, explicitCurrency, eurRate, usdRate)

    fun normalizeDate(str: String): String = CzechValueParsers.normalizeDate(str)

    fun readUriBytes(contentResolver: ContentResolver, uri: Uri): ByteArray? =
        StatementTextDecoder.readUriBytes(contentResolver, uri)

    fun mergeSummaries(summaries: List<StatementParseSummary>): StatementParseSummary =
        TransactionCategorizer.mergeSummaries(summaries)

    fun parseCsvLine(line: String): List<String> = StatementTextDecoder.splitLine(line, ',')
}
