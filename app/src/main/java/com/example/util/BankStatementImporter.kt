package com.example.util

import com.example.data.LedgerEntryEntity
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale

enum class BankType {
    MONETA,
    CSOB,
    MBANK,
    CESKA_SPORITELNA,
    KOMERCNI_BANKA,
    FIO,
    RAIFFEISENBANK,
    AIR_BANK,
    UNICREDIT,
    CREDITAS,
    MAX_BANKA,
    PARTNERS,
    PPF,
    JT,
    EQUA,
    ING,
    OBERBANK,
    REVOLUT,
    WISE,
    GENERIC
}

enum class BankTransactionType {
    SALARY_VACLAV,
    SALARY_ELEONORA,
    PARENTAL_BENEFIT,
    OTHER_INFLOW,
    HOUSING_RENT,
    GROCERIES,
    DINING_RESTAURANT,
    TRANSPORTATION,
    SHOPPING_GOODS,
    HEALTH_DRUGSTORE,
    SUBSCRIPTIONS_MEDIA,
    SERVICES_UTILITIES,
    ATM_CASH,
    CHARITY_DONATION,
    GENERAL_EXPENSE,
    LIFESTYLE_LIVING,
    INVESTMENT_PORTU,
    INVESTMENT_DIP,
    INVESTMENT_DPS,
    INTERNAL_TRANSFER,
    UNCATEGORIZED
}

data class ParsedBankTransaction(
    val date: String, // e.g. "2026-09-15"
    val amount: Double,
    val counterpartyAccount: String = "",
    val counterpartyName: String = "",
    val message: String = "",
    val variableSymbol: String = "",
    val category: BankTransactionType = BankTransactionType.UNCATEGORIZED,
    val isNetted: Boolean = (category == BankTransactionType.INTERNAL_TRANSFER),
    val nettingReason: String = ""
)

data class StatementParseSummary(
    val detectedBank: BankType,
    val yearMonth: String,
    val totalInflows: Double,
    val incVaclav: Double,
    val incEleonora: Double,
    val incOther: Double,
    val totalExpenses: Double,
    val expRent: Double,
    val expGroceries: Double,
    val expOther: Double,
    val totalInvested: Double,
    val invPortu: Double,
    val invDip: Double,
    val invDps: Double,
    val internalTransfersCount: Int,
    val totalNettedAmount: Double = 0.0,
    val monthEndBalance: Double? = null,
    val transactions: List<ParsedBankTransaction>
) {
    fun toLedgerEntry(existingNotes: String = ""): LedgerEntryEntity {
        val autoNote = "Imported from ${detectedBank.name}: ${transactions.size} txs"
        val combinedNotes = if (existingNotes.isNotBlank()) "$existingNotes | $autoNote" else autoNote
        return LedgerEntryEntity(
            yearMonth = yearMonth,
            incVaclav = incVaclav,
            incEleonora = incEleonora,
            incUnforeseen = incOther,
            expRent = expRent,
            expGroceries = expGroceries,
            expOther = expOther,
            notes = combinedNotes,
            portfolioBalanceAtMonthEnd = 0.0,
            pensionBalanceAtMonthEnd = 0.0,
            emergencyReserveAtMonthEnd = monthEndBalance ?: 0.0
        )
    }
}

object BankStatementImporter {

    /** Hard upper bound for statement imports to avoid OOM on pathological files. */
    const val MAX_STATEMENT_BYTES: Int = 25 * 1024 * 1024

    fun isWithinSizeLimit(byteCount: Int): Boolean = byteCount in 1..MAX_STATEMENT_BYTES

    fun parseStatement(
        bytes: ByteArray,
        knownFamilyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary = parseStatement(java.io.ByteArrayInputStream(bytes), knownFamilyAccounts, userOverrides)

    fun parseStatement(
        inputStream: InputStream,
        knownFamilyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val bytes = inputStream.readBytes()

        // 1. Route to PDF parser if magic bytes match PDF
        if (PdfTextExtractor.isPdf(bytes)) {
            return parsePdfStatement(bytes, knownFamilyAccounts, userOverrides)
        }

        // 2. Otherwise parse as CSV/TSV text (UTF-8 / UTF-16 / Windows-1250 / ISO-8859-2)
        val content = decodeStatementText(bytes)
        val lines = splitCsvLines(content)
        val bankType = detectBankType(lines)

        // Known-bank layouts keep their dedicated parser; if it yields nothing, or the bank is
        // not one of the legacy three, the universal engine handles any Czech bank CSV/TSV.
        val legacy = when (bankType) {
            BankType.MONETA -> parseMonetaCsv(lines, knownFamilyAccounts, userOverrides)
            BankType.CSOB -> parseCsobCsv(lines, knownFamilyAccounts, userOverrides)
            BankType.MBANK -> parseMbankCsv(lines, knownFamilyAccounts, userOverrides)
            else -> null
        }
        if (legacy != null && legacy.transactions.isNotEmpty()) return legacy

        val universal = parseUniversalCsv(lines, bankType, knownFamilyAccounts, userOverrides)
        if (universal.transactions.isNotEmpty()) return universal
        return legacy ?: parseGenericCsv(lines, knownFamilyAccounts, userOverrides)
    }

    /**
     * Decodes statement bytes into text, handling BOMs and the encodings Czech banks actually use.
     */
    private fun decodeStatementText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        val cp1250 = try {
            String(bytes, Charset.forName("windows-1250"))
        } catch (_: Exception) {
            ""
        }
        if (cp1250.isNotBlank() && !cp1250.contains('\uFFFD')) return cp1250
        return if (cp1250.isNotBlank()) cp1250 else String(bytes, Charset.forName("ISO-8859-2"))
    }

    // ==========================================
    // PDF Statement Parsing
    // ==========================================

    fun parsePdfStatement(
        pdfBytes: ByteArray,
        knownFamilyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val extractedText = PdfTextExtractor.extractText(pdfBytes)
        if (extractedText.isBlank()) {
            return emptySummary(BankType.GENERIC)
        }

        val lines = extractedText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val bankType = detectPdfBankType(extractedText, lines)

        return when (bankType) {
            BankType.MONETA -> parseMonetaPdf(lines, extractedText, knownFamilyAccounts, userOverrides)
            BankType.CSOB -> parseCsobPdf(lines, extractedText, knownFamilyAccounts, userOverrides)
            BankType.MBANK -> parseMbankPdf(lines, extractedText, knownFamilyAccounts, userOverrides)
            else -> parseGenericPdf(lines, extractedText, knownFamilyAccounts, userOverrides, bankType)
        }
    }

    private fun detectPdfBankType(fullText: String, lines: List<String>): BankType {
        return detectBankFromText(foldHeader(fullText), lines)
    }

    private fun parseMonetaPdf(
        lines: List<String>,
        fullText: String,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val closingBalance = extractClosingBalance(fullText, lines)
        val transactions = extractTransactionsFromPdfBlocks(lines, familyAccounts, userOverrides)
        return buildSummary(BankType.MONETA, transactions, closingBalance)
    }

    private fun parseCsobPdf(
        lines: List<String>,
        fullText: String,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val closingBalance = extractClosingBalance(fullText, lines)
        val transactions = extractTransactionsFromPdfBlocks(lines, familyAccounts, userOverrides)
        return buildSummary(BankType.CSOB, transactions, closingBalance)
    }

    private fun parseMbankPdf(
        lines: List<String>,
        fullText: String,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        // In mBank PDF statements, closing balance can be explicitly stated or from the last transaction's balance
        var closingBalance = extractClosingBalance(fullText, lines)
        val transactions = mutableListOf<ParsedBankTransaction>()
        val balanceWithDates = mutableListOf<Pair<String, Double>>()

        val blocks = groupLinesIntoDateBlocks(lines)
        for (block in blocks) {
            val dateStr = block.date
            val blockText = block.lines.joinToString(" ")

            // In mBank, each row often has: Date, Popis, Amount, and Running Balance
            val amounts = extractAllAmounts(blockText)
            if (amounts.isNotEmpty() && dateStr.isNotBlank()) {
                val txAmount = amounts[0]
                if (amounts.size >= 2) {
                    val runningBalance = amounts[1]
                    if (runningBalance != 0.0) {
                        balanceWithDates.add(dateStr to runningBalance)
                    }
                }

                val counterpartyAcc = extractAccount(blockText)
                val vs = extractVariableSymbol(blockText)
                val cleanDesc = cleanTransactionDescription(blockText, dateStr, amounts, counterpartyAcc, vs)
                val cat = categorizeTransaction(txAmount, counterpartyAcc, cleanDesc, cleanDesc, vs, familyAccounts, userOverrides)
                val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
                val nettingReason = determineNettingReason(cat, cleanDesc, "")

                transactions.add(
                    ParsedBankTransaction(
                        date = dateStr,
                        amount = txAmount,
                        counterpartyAccount = counterpartyAcc,
                        counterpartyName = cleanDesc,
                        message = "",
                        variableSymbol = vs,
                        category = cat,
                        isNetted = isNetted,
                        nettingReason = nettingReason
                    )
                )
            }
        }

        if (closingBalance == null && balanceWithDates.isNotEmpty()) {
            closingBalance = balanceWithDates.maxByOrNull { it.first }?.second
        }

        return buildSummary(BankType.MBANK, transactions, closingBalance)
    }

    private fun parseGenericPdf(
        lines: List<String>,
        fullText: String,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        bankType: BankType = BankType.GENERIC
    ): StatementParseSummary {
        val closingBalance = extractClosingBalance(fullText, lines)
        val transactions = extractTransactionsFromPdfBlocks(lines, familyAccounts, userOverrides)
        return buildSummary(bankType, transactions, closingBalance)
    }

    private data class DateBlock(
        val date: String,
        val lines: List<String>
    )

    private fun isIgnoredDateContext(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return lower.contains("období") || lower.contains("obdobi") ||
                lower.contains("vystavení") || lower.contains("vystaveni") ||
                lower.contains("výpis ze dne") || lower.contains("vypis ze dne") ||
                lower.contains("datum tisku") || lower.contains("platnost do") ||
                lower.contains("zůstatek") || lower.contains("zustatek") ||
                lower.contains("konečný stav") || lower.contains("konecny stav") ||
                lower.contains("počáteční") || lower.contains("pocatecni") ||
                lower.contains("vyhotoveno") || lower.contains("strana") ||
                lower.contains("obchodní místo") || lower.contains("souhrnné zúčtování")
    }

    private fun isPageHeaderOrFooter(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        return isIgnoredDateContext(line) ||
                lower.contains("výpis z běžného účtu") || lower.contains("vypis z bezneho uctu") ||
                lower.contains("výpis z účtu") || lower.contains("vypis z uctu") ||
                lower.contains("číslo výpisu") || lower.contains("cislo vypisu") ||
                lower.contains("periodicita výpisu") || lower.contains("periodicita vypisu") ||
                lower.contains("výpis pokračuje") || lower.contains("vypis pokracuje") ||
                lower.contains("přehled transakcí") || lower.contains("prehled transakci") ||
                lower.contains("zákaznický servis") || lower.contains("zakaznicky servis") ||
                lower.contains("vklad na tomto účtu podléhá") || lower.contains("vklad na tomto uctu podleha") ||
                lower.contains("smluvní úrok") || lower.contains("smluvni urok") ||
                lower.contains("celkový počet transakcí") || lower.contains("celkovy pocet transakci") ||
                lower.contains("součet obratů") || lower.contains("soucet obratu") ||
                (lower.contains("moneta money bank") && lower.contains("praha")) ||
                (lower.contains("československá obchodní banka") && lower.contains("praha")) ||
                (lower.contains("mbank s.a.") && lower.contains("praha"))
    }

    private fun groupLinesIntoDateBlocks(lines: List<String>): List<DateBlock> {
        val dateStartRegex = Regex("""^\s*(\d{1,2})[\./-](\d{1,2})[\./-](\d{4})\b""")
        val dateAnyRegex = Regex("""\b(\d{1,2})[\./-](\d{1,2})[\./-](\d{4})\b""")
        val blocks = mutableListOf<DateBlock>()
        var currentDate = ""
        val currentLines = mutableListOf<String>()

        val hasDateStartLines = lines.any { line ->
            dateStartRegex.containsMatchIn(line) && extractAllAmounts(line).isNotEmpty() && !isIgnoredDateContext(line)
        }

        for (line in lines) {
            if (isPageHeaderOrFooter(line)) continue

            if (hasDateStartLines) {
                val match = dateStartRegex.find(line)
                val amounts = extractAllAmounts(line)
                val isTxStart = match != null && amounts.isNotEmpty() && !isIgnoredDateContext(line)

                if (isTxStart) {
                    if (currentDate.isNotBlank() && currentLines.isNotEmpty()) {
                        blocks.add(DateBlock(currentDate, currentLines.toList()))
                        currentLines.clear()
                    }
                    currentDate = normalizeDate(match!!.value)
                    currentLines.add(line)
                } else if (currentDate.isNotBlank()) {
                    currentLines.add(line)
                }
            } else {
                val match = dateAnyRegex.find(line)
                if (match != null && !isIgnoredDateContext(line)) {
                    if (currentDate.isNotBlank() && currentLines.isNotEmpty()) {
                        blocks.add(DateBlock(currentDate, currentLines.toList()))
                        currentLines.clear()
                    }
                    currentDate = normalizeDate(match.value)
                    currentLines.add(line)
                } else if (currentDate.isNotBlank()) {
                    currentLines.add(line)
                }
            }
        }

        if (currentDate.isNotBlank() && currentLines.isNotEmpty()) {
            blocks.add(DateBlock(currentDate, currentLines.toList()))
        }

        return blocks
    }

    private fun extractTransactionsFromPdfBlocks(
        lines: List<String>,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): List<ParsedBankTransaction> {
        val blocks = groupLinesIntoDateBlocks(lines)
        val transactions = mutableListOf<ParsedBankTransaction>()

        for (block in blocks) {
            val dateStr = block.date
            val blockText = block.lines.joinToString(" ")
            val amounts = extractAllAmounts(blockText)
            if (amounts.isEmpty() || dateStr.isBlank()) continue

            val txAmount = amounts.firstOrNull { it != 0.0 } ?: continue
            val counterpartyAcc = extractAccount(blockText)
            val vs = extractVariableSymbol(blockText)
            val cleanDesc = cleanTransactionDescription(blockText, dateStr, amounts, counterpartyAcc, vs)
            val cat = categorizeTransaction(txAmount, counterpartyAcc, cleanDesc, cleanDesc, vs, familyAccounts, userOverrides, rawContext = blockText)

            transactions.add(
                ParsedBankTransaction(
                    date = dateStr,
                    amount = txAmount,
                    counterpartyAccount = counterpartyAcc,
                    counterpartyName = cleanDesc,
                    message = "",
                    variableSymbol = vs,
                    category = cat
                )
            )
        }
        return transactions
    }

    private fun extractAllAmounts(text: String): List<Double> {
        val amounts = mutableListOf<Double>()
        // Regex matches standard Czech currency formats e.g. -25 000,00, 75 000,00 CZK, - 1 756,99
        val amountRegex = Regex("""(?<!\w)([+-]?\s*(?:\d{1,3}(?:[\s\u00A0]\d{3})*|\d+),\d{2})\s*(CZK|Kč)?\s*([+-])?(?!\w)""")
        val matches = amountRegex.findAll(text)

        for (m in matches) {
            val rawNum = m.groupValues[1]
            val trailingSign = m.groupValues[3]

            var parsed = parseCzechAmount(rawNum)
            if (trailingSign == "-" && parsed > 0) {
                parsed = -parsed
            } else if (trailingSign == "+" && parsed < 0) {
                parsed = -parsed
            }

            if (parsed != 0.0) {
                val lower = text.lowercase(Locale.ROOT)
                if (parsed > 0 && (lower.contains("odchozí") || lower.contains("debet") || lower.contains("platba kartou") || lower.contains("nákup"))) {
                    if (!rawNum.contains("+") && trailingSign != "+") {
                        parsed = -parsed
                    }
                }
                amounts.add(parsed)
            }
        }

        // Fallback for PDFs that print whole-koruna amounts (e.g. "1 234 Kč", "1234 CZK").
        if (amounts.isEmpty()) {
            val wholeCurrencyRegex = Regex("""(?<!\w)([+-]?\s*(?:\d{1,3}(?:[\s\u00A0]\d{3})+|\d+))\s*(?:CZK|Kč)(?!\w)""")
            val lower = text.lowercase(Locale.ROOT)
            for (m in wholeCurrencyRegex.findAll(text)) {
                var parsed = parseCzechAmount(m.groupValues[1])
                if (parsed > 0 && (lower.contains("odchozí") || lower.contains("debet") || lower.contains("platba kartou") || lower.contains("nákup"))) {
                    parsed = -parsed
                }
                if (parsed != 0.0) amounts.add(parsed)
            }
        }
        return amounts
    }

    private fun extractAccount(text: String): String {
        val accRegex = Regex("""\b(?:\d{1,6}-)?\d{1,10}/\d{4}\b""")
        val match = accRegex.find(text)
        return match?.value?.trim() ?: ""
    }

    private fun extractVariableSymbol(text: String): String {
        val vsRegex = Regex("""\b(?:VS|vs|v\.s\.|variabilní symbol)[:\s]*(\d{1,10})\b""")
        val match = vsRegex.find(text)
        return match?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    fun cleanPaymentDescription(raw: String): String {
        var text = raw
        val boilerplatePatterns = listOf(
            Regex("""(?i)\bplatba\s+kartou\s+v\s+[čc]r\b"""),
            Regex("""(?i)\bplatba\s+kartou\s+v\s+zahrani[čc][ií]\b"""),
            Regex("""(?i)\bplatba\s+kartou\s+online\b"""),
            Regex("""(?i)\bplatba\s+kartou\b"""),
            Regex("""(?i)\btransakce\s+platebn[ií]\s+kartou\b"""),
            Regex("""(?i)\bplatba\s+na\s+internetu\b"""),
            Regex("""(?i)\bplatba\s+mobilem\b"""),
            Regex("""(?i)\bbezhotovostn[ií]\s+platba\b"""),
            Regex("""(?i)\bokam[zž]it[aá]\s+platba\b"""),
            Regex("""(?i)\bp[rř][ií]choz[ií]\s+[uú]hrada\b"""),
            Regex("""(?i)\bodchoz[ií]\s+[uú]hrada\b"""),
            Regex("""(?i)\bp[rř][ií]choz[ií]\s+platba\b"""),
            Regex("""(?i)\bodchoz[ií]\s+platba\b"""),
            Regex("""(?i)\btrval[yý]\s+p[rř][ií]kaz\s+k\s+[uú]hrad[eě]:?\b"""),
            Regex("""(?i)\btrval[yý]\s+p[rř][ií]kaz:?\b"""),
            Regex("""(?i)\bp[rř][ií]kazce:\s*(?:v[aá]clav\s+martin[uů]|martin[uů]\s+v[aá]clav)?\b"""),
            Regex("""(?i)\bpl[aá]tce:\s*(?:v[aá]clav\s+martin[uů]|martin[uů]\s+v[aá]clav)?\b"""),
            Regex("""(?i)\bp[rř][ií]kazce:?\b"""),
            Regex("""(?i)\bpl[aá]tce:?\b"""),
            Regex("""(?i)\bmajitel\s+[uú][cč]tu:?\b"""),
            Regex("""(?i)\b[cč][ií]slo\s+[uú][cč]tu\s+pl[aá]tce:?\b"""),
            Regex("""(?i)\b[cč][ií]slo\s+[uú][cč]tu\s+p[rř][ií]kazce:?\b"""),
            Regex("""(?i)\bzpr[aá]va\s+pro\s+p[rř][ií]jemce:?\b"""),
            Regex("""(?i)\bpopis\s+transakce:?\b"""),
            Regex("""(?i)\bdetaily\s+platby:?\b"""),
            Regex("""(?i)\bn[aá]zev\s+proti[uú][cč]tu:?\b"""),
            Regex("""(?i)\bm[ií]sto:?\b"""),
            Regex("""(?i)\bn[aá]kup:?\b"""),
            Regex("""(?i)\b[cč][ií]slo\s+karty:?\b"""),
            Regex("""(?i)\bdatum\s+a\s+[cč]as:?\b""")
        )

        for (p in boilerplatePatterns) {
            text = p.replace(text, " ")
        }

        // Remove terminal sequence tokens e.g. 260708V115305518803 or 26070926708D4004533
        text = Regex("""\b\d{6}[A-Za-z0-9]\d{8,}\b""").replace(text, " ")
        // Remove masked terminal card reference digits e.g. 0000009498000494
        text = Regex("""\b0{3,}\d*\b""").replace(text, " ")
        // Remove long card digits
        text = Regex("""\b\d{12,}\b""").replace(text, " ")
        // Remove timestamps e.g. 14:22:05 or 14:22
        text = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""").replace(text, " ")
        // Remove dates inside text e.g. 15.09.2026 or 15.09.
        text = Regex("""\b\d{1,2}[\./-]\d{1,2}(?:[\./-]\d{2,4})?\b""").replace(text, " ")
        // Remove currencies
        text = Regex("""(?i)\b(?:czk|k[čc])\b""").replace(text, " ")
        // Remove stray terminal numbers at start e.g. "9 32"
        text = Regex("""^\s*\d{1,2}\s+\d{1,2}\s+""").replace(text, " ")
        // Remove leading punctuation symbols
        text = Regex("""^[\s\-:,./]+""").replace(text, " ")
        // Remove multiple spaces
        text = text.replace(Regex("""\s+"""), " ").trim()

        return text.ifBlank { raw.trim() }
    }

    private fun cleanTransactionDescription(
        blockText: String,
        dateStr: String,
        amounts: List<Double>,
        account: String,
        vs: String
    ): String {
        var clean = blockText
        val dateRegex = Regex("""\b\d{1,2}[\./-]\d{1,2}[\./-]\d{4}\b""")
        clean = dateRegex.replace(clean, " ")
        if (account.isNotBlank()) clean = clean.replace(account, " ")
        if (vs.isNotBlank()) clean = clean.replace(vs, " ")

        clean = clean.replace("CZK", "", ignoreCase = true)
            .replace("Kč", "")
            .replace("Variabilní symbol", "", ignoreCase = true)
            .replace("Konstantní symbol", "", ignoreCase = true)
            .replace("Specifický symbol", "", ignoreCase = true)
            .replace("Zpráva pro příjemce", "", ignoreCase = true)
            .replace("Popis transakce", "", ignoreCase = true)
            .replace("Detaily platby", "", ignoreCase = true)
            .replace("Trvalý příkaz k úhradě", "", ignoreCase = true)
            .replace("Trvaly prikaz k uhrade", "", ignoreCase = true)
            .replace("Trvalý příkaz", "", ignoreCase = true)
            .replace("Trvaly prikaz", "", ignoreCase = true)

        clean = cleanPaymentDescription(clean)

        val words = clean.split(Regex("""\s+""")).filter { w ->
            w.isNotBlank() && !w.contains(',') && w != "+" && w != "-" && w != "/"
        }
        val result = words.joinToString(" ").trim()
        val finalClean = Regex("""^\s*\d{1,2}\s+\d{1,2}\s+""").replace(result, "").trim()
        return finalClean.ifBlank {
            if (blockText.contains("trvalý", ignoreCase = true) || blockText.contains("trvaly", ignoreCase = true)) {
                "Trvalý příkaz"
            } else {
                blockText.trim()
            }
        }
    }

    internal fun extractAllAmountsPublic(text: String): List<Double> = extractAllAmounts(text)
    internal fun extractClosingBalancePublic(fullText: String, lines: List<String>): Double? = extractClosingBalance(fullText, lines)

    private fun extractClosingBalance(fullText: String, lines: List<String>): Double? {
        val balanceKeywords = listOf(
            "konečný zůstatek", "konecny zustatek",
            "účetní zůstatek", "ucetni zustatek",
            "zůstatek ke konci", "zustatek ke konci",
            "zůstatek na konci", "zustatek na konci",
            "konečný stav", "konecny stav",
            "zůstatek k", "zustatek k"
        )

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (balanceKeywords.any { lower.contains(it) }) {
                val amounts = extractAllAmounts(line)
                if (amounts.isNotEmpty()) {
                    return kotlin.math.abs(amounts.last())
                }
            }
        }

        // Search in fullText if line splitting separated keyword and amount
        val lowerFull = fullText.lowercase(Locale.ROOT)
        for (kw in balanceKeywords) {
            val idx = lowerFull.indexOf(kw)
            if (idx != -1) {
                val snippet = fullText.substring(idx, (idx + 120).coerceAtMost(fullText.length))
                val amounts = extractAllAmounts(snippet)
                if (amounts.isNotEmpty()) {
                    return kotlin.math.abs(amounts.first())
                }
            }
        }

        return null
    }

    // ==========================================
    // CSV Statement Parsing (Existing & Robust)
    // ==========================================

    fun splitCsvLines(content: String): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = content.length
        while (i < len) {
            val c = content[i]
            when {
                c == '"' && inQuotes && i + 1 < len && content[i + 1] == '"' -> {
                    sb.append("\"\"")
                    i++
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    sb.append(c)
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    if (c == '\r' && i + 1 < len && content[i + 1] == '\n') {
                        i++
                    }
                    val line = sb.toString().trim()
                    if (line.isNotBlank()) {
                        lines.add(line)
                    }
                    sb.setLength(0)
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        val remaining = sb.toString().trim()
        if (remaining.isNotBlank()) {
            lines.add(remaining)
        }
        return lines
    }

    fun detectCsvDelimiter(lines: List<String>): Char {
        val sample = lines.take(30).filter { it.isNotBlank() }
        if (sample.isEmpty()) return ';'

        fun countUnquoted(line: String, delim: Char): Int {
            var count = 0
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '"') {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                } else if (c == delim && !inQuotes) {
                    count++
                }
                i++
            }
            return count
        }

        // Choose the delimiter with the most consistent non-zero column count across lines
        // (supports ';', ',', TAB and '|'); ';' wins ties because Czech exports favor it.
        var bestDelim = ';'
        var bestScore = -1.0
        for (delim in charArrayOf(';', ',', '\t', '|')) {
            val counts = sample.map { countUnquoted(it, delim) }.filter { it > 0 }
            if (counts.isEmpty()) continue
            val mode = counts.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            val consistency = mode.value.toDouble() / counts.size
            val score = consistency * 1000.0 + mode.key
            if (score > bestScore) {
                bestScore = score
                bestDelim = delim
            }
        }
        return bestDelim
    }

    private fun detectBankType(lines: List<String>): BankType {
        return detectBankFromText(foldHeader(lines.take(30).joinToString(" \n ")), lines)
    }

    /** Drops diacritics and collapses whitespace so Czech/English headers match reliably. */
    private fun foldHeader(value: String): String {
        val decomposed = java.text.Normalizer.normalize(value.trim().lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{M}+"), "")
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Universal Czech-bank detection: explicit bank names first, then distinctive header
     * signatures, and only then bank account codes (which can appear as counterparty accounts).
     */
    private fun detectBankFromText(folded: String, lines: List<String>): BankType {
        val nameSignatures = listOf(
            "moneta money bank" to BankType.MONETA,
            "moneta" to BankType.MONETA,
            "ceskoslovenska obchodni banka" to BankType.CSOB,
            "csob" to BankType.CSOB,
            "mbank" to BankType.MBANK,
            "ceska sporitelna" to BankType.CESKA_SPORITELNA,
            "sporitelna" to BankType.CESKA_SPORITELNA,
            "komercni banka" to BankType.KOMERCNI_BANKA,
            "fio banka" to BankType.FIO,
            "fio" to BankType.FIO,
            "raiffeisenbank" to BankType.RAIFFEISENBANK,
            "raiffeisen" to BankType.RAIFFEISENBANK,
            "air bank" to BankType.AIR_BANK,
            "unicredit" to BankType.UNICREDIT,
            "creditas" to BankType.CREDITAS,
            "max banka" to BankType.MAX_BANKA,
            "expobank" to BankType.MAX_BANKA,
            "partners banka" to BankType.PARTNERS,
            "ppf banka" to BankType.PPF,
            "j&t banka" to BankType.JT,
            "jt banka" to BankType.JT,
            "equa bank" to BankType.EQUA,
            "ing bank" to BankType.ING,
            "oberbank" to BankType.OBERBANK,
            "revolut" to BankType.REVOLUT,
            "wise europe" to BankType.WISE,
            "wise payments" to BankType.WISE
        )
        for ((keyword, bank) in nameSignatures) {
            if (folded.contains(keyword)) return bank
        }

        val headerText = foldHeader(lines.take(30).joinToString(" | "))
        if (headerText.contains("#datum operace") || headerText.contains("#popis transakce")) return BankType.MBANK
        if (headerText.contains("cislo protiuctu") || headerText.contains("banka protiuctu") || headerText.contains("nazev protiuctu")) {
            return BankType.MONETA
        }
        if (headerText.contains("cislo uctu protistrany") || headerText.contains("nazev protistrany")) return BankType.CSOB
        if (headerText.contains("cislo uctu") && headerText.contains("smer")) return BankType.CESKA_SPORITELNA
        if (headerText.contains("objem") && headerText.contains("protiucet")) return BankType.FIO

        val accountCodes = listOf(
            "0100" to BankType.KOMERCNI_BANKA,
            "0300" to BankType.CSOB,
            "0600" to BankType.MONETA,
            "0800" to BankType.CESKA_SPORITELNA,
            "2010" to BankType.FIO,
            "2250" to BankType.CREDITAS,
            "2700" to BankType.UNICREDIT,
            "3030" to BankType.AIR_BANK,
            "4000" to BankType.MAX_BANKA,
            "5500" to BankType.RAIFFEISENBANK,
            "5800" to BankType.JT,
            "6000" to BankType.PPF,
            "6100" to BankType.EQUA,
            "6210" to BankType.MBANK,
            "8040" to BankType.OBERBANK
        )
        val codeText = foldHeader(lines.take(40).joinToString(" "))
        for ((code, bank) in accountCodes) {
            if (Regex("/\\s*$code\\b").containsMatchIn(codeText)) return bank
        }
        return BankType.GENERIC
    }

    private fun parseMonetaCsv(
        lines: List<String>,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val delimiter = detectCsvDelimiter(lines)
        var headerIdx = -1
        for (i in lines.indices) {
            val l = lines[i].lowercase(Locale.ROOT)
            if (l.contains("datum") && (l.contains("částka") || l.contains("castka") || l.contains("objem"))) {
                headerIdx = i
                break
            }
        }

        if (headerIdx == -1) return emptySummary(BankType.MONETA)
        val headers = splitLine(lines[headerIdx], delimiter).map { it.lowercase(Locale.ROOT).trim() }

        val dateIdx = headers.indexOfFirst { it.contains("datum") }
        val amountIdx = headers.indexOfFirst { it.contains("částka") || it.contains("castka") || it.contains("objem") }
        val accIdx = headers.indexOfFirst { it.contains("protiúč") || it.contains("protiuc") }
        val nameIdx = headers.indexOfFirst { it.contains("název") || it.contains("nazev") }
        val msgIdx = headers.indexOfFirst { it.contains("zpráva") || it.contains("zprava") || it.contains("poznámka") }
        val vsIdx = headers.indexOfFirst { it.contains("variabilní") || it.contains("vs") }

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        for (i in (headerIdx + 1) until lines.size) {
            val tokens = splitLine(lines[i], delimiter)
            if (tokens.size <= maxOf(dateIdx, amountIdx)) continue
            val dateStr = normalizeDate(tokens.getOrNull(dateIdx) ?: "")
            val amount = parseCzechAmount(tokens.getOrNull(amountIdx) ?: "")
            val counterpartyAcc = tokens.getOrNull(accIdx)?.trim() ?: ""
            val counterpartyName = tokens.getOrNull(nameIdx)?.trim() ?: ""
            val message = tokens.getOrNull(msgIdx)?.trim() ?: ""
            val vs = tokens.getOrNull(vsIdx)?.trim() ?: ""

            if (dateStr.isNotBlank() && amount != 0.0) {
                val cat = categorizeTransaction(amount, counterpartyAcc, counterpartyName, message, vs, familyAccounts, userOverrides)
                val cleanName = cleanPaymentDescription(counterpartyName)
                val cleanMsg = cleanPaymentDescription(message)
                val distinctMsg = if (cleanMsg.isNotBlank() && !cleanMsg.equals(cleanName, ignoreCase = true)) cleanMsg else ""
                rawTransactions.add(
                    ParsedBankTransaction(
                        date = dateStr,
                        amount = amount,
                        counterpartyAccount = counterpartyAcc,
                        counterpartyName = cleanName,
                        message = distinctMsg,
                        variableSymbol = vs,
                        category = cat
                    )
                )
            }
        }
        return buildSummary(BankType.MONETA, rawTransactions)
    }

    private fun parseCsobCsv(
        lines: List<String>,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val delimiter = ';'
        var headerIdx = -1
        for (i in lines.indices) {
            val l = lines[i].lowercase(Locale.ROOT)
            if (l.contains("datum") && (l.contains("částka") || l.contains("castka"))) {
                headerIdx = i
                break
            }
        }

        if (headerIdx == -1) return emptySummary(BankType.CSOB)
        val headers = splitLine(lines[headerIdx], delimiter).map { it.lowercase(Locale.ROOT).trim() }

        val dateIdx = headers.indexOfFirst { it.contains("datum") }
        val amountIdx = headers.indexOfFirst { it.contains("částka") || it.contains("castka") }
        val accIdx = headers.indexOfFirst { it.contains("protistran") && (it.contains("účet") || it.contains("ucet")) }
        val nameIdx = headers.indexOfFirst { it.contains("název") || it.contains("nazev") }
        val msgIdx = headers.indexOfFirst { it.contains("zpráva") || it.contains("zprava") || it.contains("informace") }
        val vsIdx = headers.indexOfFirst { it.contains("variabilní") || it.contains("vs") }

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        for (i in (headerIdx + 1) until lines.size) {
            val tokens = splitLine(lines[i], delimiter)
            if (tokens.size <= maxOf(dateIdx, amountIdx)) continue
            val dateStr = normalizeDate(tokens.getOrNull(dateIdx) ?: "")
            val amount = parseCzechAmount(tokens.getOrNull(amountIdx) ?: "")
            val counterpartyAcc = tokens.getOrNull(accIdx)?.trim() ?: ""
            val counterpartyName = tokens.getOrNull(nameIdx)?.trim() ?: ""
            val message = tokens.getOrNull(msgIdx)?.trim() ?: ""
            val vs = tokens.getOrNull(vsIdx)?.trim() ?: ""

            if (dateStr.isNotBlank() && amount != 0.0) {
                val cat = categorizeTransaction(amount, counterpartyAcc, counterpartyName, message, vs, familyAccounts, userOverrides)
                val cleanName = cleanPaymentDescription(counterpartyName)
                val cleanMsg = cleanPaymentDescription(message)
                val distinctMsg = if (cleanMsg.isNotBlank() && !cleanMsg.equals(cleanName, ignoreCase = true)) cleanMsg else ""
                rawTransactions.add(
                    ParsedBankTransaction(
                        date = dateStr,
                        amount = amount,
                        counterpartyAccount = counterpartyAcc,
                        counterpartyName = cleanName,
                        message = distinctMsg,
                        variableSymbol = vs,
                        category = cat
                    )
                )
            }
        }
        return buildSummary(BankType.CSOB, rawTransactions)
    }

    private fun parseMbankCsv(
        lines: List<String>,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val delimiter = ';'
        var headerIdx = -1
        for (i in lines.indices) {
            val l = lines[i].lowercase(Locale.ROOT)
            if (l.contains("datum") && (l.contains("částka") || l.contains("castka") || l.contains("popis"))) {
                headerIdx = i
                break
            }
        }

        if (headerIdx == -1) return emptySummary(BankType.MBANK)
        val headers = splitLine(lines[headerIdx].replace("#", ""), delimiter).map { it.lowercase(Locale.ROOT).trim() }

        val dateIdx = headers.indexOfFirst { it.contains("datum") }
        val amountIdx = headers.indexOfFirst { it.contains("částka") || it.contains("castka") }
        val descIdx = headers.indexOfFirst { it.contains("popis") }
        val accIdx = headers.indexOfFirst { it.contains("účet") || it.contains("ucet") || it.contains("iban") }
        val nameIdx = headers.indexOfFirst { it.contains("název") || it.contains("nazev") }
        val balanceIdx = headers.indexOfFirst { it.contains("zůstatek") || it.contains("zustatek") }

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        val balanceWithDates = mutableListOf<Pair<String, Double>>()

        for (i in (headerIdx + 1) until lines.size) {
            val tokens = splitLine(lines[i], delimiter)
            if (tokens.size <= maxOf(dateIdx, amountIdx)) continue
            val dateStr = normalizeDate(tokens.getOrNull(dateIdx) ?: "")
            val amount = parseCzechAmount(tokens.getOrNull(amountIdx) ?: "")
            val counterpartyAcc = tokens.getOrNull(accIdx)?.trim() ?: ""
            val counterpartyName = tokens.getOrNull(nameIdx)?.trim() ?: ""
            val message = tokens.getOrNull(descIdx)?.trim() ?: ""

            if (balanceIdx != -1 && balanceIdx < tokens.size && dateStr.isNotBlank()) {
                val bal = parseCzechAmount(tokens[balanceIdx])
                if (bal != 0.0) {
                    balanceWithDates.add(dateStr to bal)
                }
            }

            if (dateStr.isNotBlank() && amount != 0.0) {
                val cat = categorizeTransaction(amount, counterpartyAcc, counterpartyName, message, "", familyAccounts, userOverrides)
                val cleanName = cleanPaymentDescription(counterpartyName)
                val cleanMsg = cleanPaymentDescription(message)
                val distinctMsg = if (cleanMsg.isNotBlank() && !cleanMsg.equals(cleanName, ignoreCase = true)) cleanMsg else ""
                val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
                val nettingReason = determineNettingReason(cat, cleanName, distinctMsg)
                rawTransactions.add(
                    ParsedBankTransaction(
                        date = dateStr,
                        amount = amount,
                        counterpartyAccount = counterpartyAcc,
                        counterpartyName = cleanName,
                        message = distinctMsg,
                        variableSymbol = "",
                        category = cat,
                        isNetted = isNetted,
                        nettingReason = nettingReason
                    )
                )
            }
        }
        val closingBalance = balanceWithDates.maxByOrNull { it.first }?.second
        return buildSummary(BankType.MBANK, rawTransactions, closingBalance)
    }

    private fun parseGenericCsv(
        lines: List<String>,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val delimiter = detectCsvDelimiter(lines)
        var headerIdx = -1
        for (i in lines.indices) {
            val l = lines[i].lowercase(Locale.ROOT)
            if (l.contains("date") || l.contains("datum")) {
                headerIdx = i
                break
            }
        }

        if (headerIdx == -1) return emptySummary(BankType.GENERIC)
        val headers = splitLine(lines[headerIdx], delimiter).map { it.lowercase(Locale.ROOT).trim() }

        val dateIdx = headers.indexOfFirst { it.contains("date") || it.contains("datum") }
        val amountIdx = headers.indexOfFirst { it.contains("amount") || it.contains("částka") || it.contains("castka") }
        val msgIdx = headers.indexOfFirst { it.contains("description") || it.contains("message") || it.contains("popis") || it.contains("zpráva") }

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        for (i in (headerIdx + 1) until lines.size) {
            val tokens = splitLine(lines[i], delimiter)
            if (tokens.size <= maxOf(dateIdx, amountIdx)) continue
            val dateStr = normalizeDate(tokens.getOrNull(dateIdx) ?: "")
            val amount = parseCzechAmount(tokens.getOrNull(amountIdx) ?: "")
            val message = tokens.getOrNull(msgIdx)?.trim() ?: ""

            if (dateStr.isNotBlank() && amount != 0.0) {
                val cat = categorizeTransaction(amount, "", "", message, "", familyAccounts, userOverrides)
                val cleanMsg = cleanPaymentDescription(message)
                rawTransactions.add(
                    ParsedBankTransaction(
                        date = dateStr,
                        amount = amount,
                        message = cleanMsg,
                        category = cat
                    )
                )
            }
        }
        return buildSummary(BankType.GENERIC, rawTransactions)
    }

    // ==========================================
    // Universal CSV engine (all Czech banks)
    // ==========================================

    private data class HeaderRoles(
        val dateIdx: Int = -1,
        val amountIdx: Int = -1,
        val debitIdx: Int = -1,
        val creditIdx: Int = -1,
        val currencyIdx: Int = -1,
        val accountIdx: Int = -1,
        val nameIdx: Int = -1,
        val messageIdx: Int = -1,
        val vsIdx: Int = -1,
        val balanceIdx: Int = -1,
        val directionIdx: Int = -1
    )

    /**
     * Maps header cells to semantic roles using Czech/English keyword heuristics.
     * Handles signed single-amount columns as well as separate debit/credit columns.
     */
    private fun inferHeaderRoles(cells: List<String>): HeaderRoles {
        fun find(vararg predicates: (String) -> Boolean): Int =
            cells.indexOfFirst { cell -> cell.isNotBlank() && predicates.any { it(cell) } }

        val dateIdx = find({ it.contains("datum") }, { it.contains("date") }, { it.contains("valuta") }, { it.contains("proved") }, { it.contains("zauc") })
        val balanceIdx = find({ it.contains("zustatek") }, { it.contains("balance") })
        val vsIdx = find({ it.contains("variabil") }, { it == "vs" }, { it.contains("var symbol") }, { it.contains("varsymbol") })
        val currencyIdx = find({ it.contains("mena") }, { it.contains("currency") })
        val debitIdx = find({ it.contains("debet") }, { it.contains("debit") }, { it.contains("odeps") }, { it.contains("na vrub") }, { it.contains("vydaj") })
        val creditIdx = find({ it.contains("kredit") }, { it.contains("credit") }, { it.contains("prips") }, { it.contains("ve prospech") }, { it.contains("vklad") })
        val amountIdx = find({ it.contains("castka") }, { it.contains("objem") }, { it.contains("amount") }, { it.contains("suma") }, { it.contains("hodnota") }, { it.contains("cena") })
        val directionIdx = find({ it.contains("smer") }, { it.contains("direction") }, { it == "typ" }, { it.contains("druh") })
        val messageIdx = find(
            { it.contains("zprava") }, { it.contains("popis") }, { it.contains("message") },
            { it.contains("description") }, { it.contains("poznamka") }, { it.contains("ucel") },
            { it.contains("reference") }, { it.contains("detail") }, { it.contains("transakce") }, { it.contains("info") }
        )
        val nameIdx = find(
            { it.contains("nazev") }, { it.contains("name") }, { it.contains("obchodnik") },
            { it.contains("prijemce") }, { it.contains("platce") }, { it.contains("protistrana") }, { it.contains("majitel") }
        )
        val accountIdx = find(
            { it.contains("protiuc") }, { it.contains("protistran") }, { it.contains("iban") },
            { it.contains("account") }, { it.contains("protiucet") },
            { it.contains("ucet") && it.contains("cislo") }
        )
        return HeaderRoles(dateIdx, amountIdx, debitIdx, creditIdx, currencyIdx, accountIdx, nameIdx, messageIdx, vsIdx, balanceIdx, directionIdx)
    }

    private fun extractDateToken(cell: String): String {
        val match = Regex("""(\d{1,4}[./-]\d{1,2}[./-]\d{2,4})""").find(cell) ?: return ""
        return normalizeDate(match.value)
    }

    private fun parseUniversalCsv(
        lines: List<String>,
        bankType: BankType,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val delimiter = detectCsvDelimiter(lines)

        var headerIdx = -1
        var roles = HeaderRoles()
        for (i in 0 until minOf(lines.size, 40)) {
            val cells = splitLine(lines[i], delimiter).map { foldHeader(it) }
            if (cells.size < 2) continue
            val candidate = inferHeaderRoles(cells)
            val hasAmount = candidate.amountIdx >= 0 || candidate.debitIdx >= 0 || candidate.creditIdx >= 0
            if (candidate.dateIdx >= 0 && hasAmount) {
                headerIdx = i
                roles = candidate
                break
            }
        }
        if (headerIdx < 0) return emptySummary(bankType)

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        val balanceWithDates = mutableListOf<Pair<String, Double>>()

        for (i in (headerIdx + 1) until lines.size) {
            val line = lines[i]
            val tokens = splitLine(line, delimiter)
            if (tokens.isEmpty()) continue

            val dateStr = extractDateToken(tokens.getOrNull(roles.dateIdx) ?: "")
            if (dateStr.isBlank()) continue

            var amount = 0.0
            if (roles.amountIdx >= 0) {
                amount = parseCzechAmount(tokens.getOrNull(roles.amountIdx) ?: "")
            }
            if (amount == 0.0 && (roles.debitIdx >= 0 || roles.creditIdx >= 0)) {
                val debit = kotlin.math.abs(parseCzechAmount(tokens.getOrNull(roles.debitIdx) ?: ""))
                val credit = kotlin.math.abs(parseCzechAmount(tokens.getOrNull(roles.creditIdx) ?: ""))
                amount = credit - debit
            }
            if (amount != 0.0 && roles.directionIdx >= 0) {
                val direction = foldHeader(tokens.getOrNull(roles.directionIdx) ?: "")
                val outflow = direction.contains("odchoz") || direction.contains("debet") || direction.contains("vydaj") ||
                    direction.contains("odeps") || direction.contains("na vrub") || direction.contains("out")
                val inflow = direction.contains("prichoz") || direction.contains("kredit") || direction.contains("prips") ||
                    direction.contains("vklad") || direction.contains("in")
                if (outflow && amount > 0) amount = -amount
                if (inflow && amount < 0) amount = -amount
            }
            if (amount == 0.0) continue

            val counterpartyAcc = tokens.getOrNull(roles.accountIdx)?.trim() ?: ""
            val counterpartyName = tokens.getOrNull(roles.nameIdx)?.trim() ?: ""
            val message = tokens.getOrNull(roles.messageIdx)?.trim() ?: ""
            val vs = tokens.getOrNull(roles.vsIdx)?.trim() ?: ""

            val context = foldHeader("$counterpartyName $message")
            if (context.contains("konecny zustatek") || context.contains("pocatecni zustatek") ||
                context.contains("ucetni zustatek") || context.contains("obrat") || context.contains("celkem")
            ) {
                continue
            }

            if (roles.balanceIdx >= 0) {
                val balance = parseCzechAmount(tokens.getOrNull(roles.balanceIdx) ?: "")
                if (balance != 0.0) balanceWithDates.add(dateStr to balance)
            }

            val category = categorizeTransaction(
                amount, counterpartyAcc, counterpartyName, message, vs, familyAccounts, userOverrides, rawContext = line
            )
            val cleanName = cleanPaymentDescription(counterpartyName)
            val cleanMsg = cleanPaymentDescription(message)
            val distinctMsg = if (cleanMsg.isNotBlank() && !cleanMsg.equals(cleanName, ignoreCase = true)) cleanMsg else ""
            val isNetted = (category == BankTransactionType.INTERNAL_TRANSFER)
            rawTransactions.add(
                ParsedBankTransaction(
                    date = dateStr,
                    amount = amount,
                    counterpartyAccount = counterpartyAcc,
                    counterpartyName = cleanName,
                    message = distinctMsg,
                    variableSymbol = vs,
                    category = category,
                    isNetted = isNetted,
                    nettingReason = determineNettingReason(category, cleanName, distinctMsg)
                )
            )
        }

        val closingBalance = balanceWithDates.maxByOrNull { it.first }?.second
        return buildSummary(bankType, rawTransactions, closingBalance)
    }

    // ==========================================
    // Categorization & Normalization
    // ==========================================

    fun categorizeTransaction(
        amount: Double,
        counterpartyAcc: String,
        counterpartyName: String,
        message: String,
        vs: String,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        rawContext: String = ""
    ): BankTransactionType {
        val combinedText = "$counterpartyName $message $rawContext".lowercase(Locale.ROOT)

        // Rent & Housing Priority Check (standing orders with housing keywords, rent keywords, housing payments)
        if (amount < 0) {
            val isHouseholdRentAmount = kotlin.math.abs(kotlin.math.abs(amount) - 18950.0) < 0.01
            val hasHousingKeyword = combinedText.contains("nájem") || combinedText.contains("najem") ||
                combinedText.contains("nájemné") || combinedText.contains("najemne") ||
                combinedText.contains("činže") || combinedText.contains("cinze") ||
                combinedText.contains("fond oprav") || combinedText.contains("svj") ||
                combinedText.contains("platba najmu") || combinedText.contains("bydleni") ||
                combinedText.contains("bydlení") || combinedText.contains("byt") ||
                combinedText.contains("pronájem") || combinedText.contains("pronajem") ||
                combinedText.contains("pronajímatel") || combinedText.contains("pronajimatel") ||
                combinedText.contains("najemce") || combinedText.contains("nájemce") ||
                combinedText.contains("podnájem") || combinedText.contains("podnajem") ||
                combinedText.contains("sipo")

            if (isHouseholdRentAmount || hasHousingKeyword) {
                return BankTransactionType.HOUSING_RENT
            }
        }

        if (CrossStatementReconciliationEngine.isSelfOrFamilyTransfer(counterpartyName, counterpartyAcc, message, familyAccounts, isDebit = amount < 0)) {
            return BankTransactionType.INTERNAL_TRANSFER
        }

        val cleanAcc = counterpartyAcc.replace(" ", "").replace("-", "").lowercase(Locale.ROOT)
        if (familyAccounts.any { cleanAcc.contains(it.replace(" ", "").replace("-", "").lowercase(Locale.ROOT)) && it.isNotBlank() }) {
            return BankTransactionType.INTERNAL_TRANSFER
        }

        if (amount > 0) {
            return when {
                combinedText.contains("rodičov") || combinedText.contains("rodicov") || combinedText.contains("úřad práce") || combinedText.contains("urad prace") ->
                    BankTransactionType.PARENTAL_BENEFIT
                combinedText.contains("eleonora") ->
                    BankTransactionType.SALARY_ELEONORA
                combinedText.contains("uohs") || combinedText.contains("úřad hosp") || combinedText.contains("urad hosp") ||
                        combinedText.contains("zaměstnavatel") || combinedText.contains("zamestnavatel") ||
                        combinedText.contains("mzda") || combinedText.contains("plat") || combinedText.contains("výplata") || combinedText.contains("vyplata") || combinedText.contains("odměna") || combinedText.contains("odmena") ->
                    BankTransactionType.SALARY_VACLAV
                else ->
                    BankTransactionType.OTHER_INFLOW
            }
        }

        // Consult Czech Merchant Catalog (with user custom overrides)
        val catalogMatch = CzechMerchantCatalog.matchCategory("$counterpartyName $counterpartyAcc $message $rawContext", userOverrides)
        if (catalogMatch != null) {
            return catalogMatch
        }

        val isGeneralInvestmentAcc = cleanAcc.contains("76788295") || cleanAcc.contains("7678876788") ||
            cleanAcc.contains("2038012508") || cleanAcc.contains("2555410109") ||
            cleanAcc.contains("518746050") ||
            combinedText.contains("76788295") || combinedText.contains("7678876788") ||
            combinedText.contains("518746050")

        if (isGeneralInvestmentAcc || combinedText.contains("portu") || combinedText.contains("wood & company") ||
            combinedText.contains("wood company") || combinedText.contains("wood retail") ||
            combinedText.contains("xtb") || combinedText.contains("x-trade") ||
            combinedText.contains("degiro") || combinedText.contains("interactive brokers") ||
            combinedText.contains("trading 212")
        ) {
            return BankTransactionType.INVESTMENT_PORTU
        }
        if (combinedText.contains("dip") || vs.startsWith("7") || combinedText.contains("patria dip")) {
            return BankTransactionType.INVESTMENT_DIP
        }

        val isDpsAcc = cleanAcc.contains("5005004433") || combinedText.contains("5005004433")
        if (isDpsAcc || combinedText.contains("penzij") || combinedText.contains("dps") ||
            combinedText.contains("conseq") || combinedText.contains("generali") ||
            combinedText.contains("allianz") || combinedText.contains("nn penzij") ||
            combinedText.contains("nn penze")
        ) {
            return BankTransactionType.INVESTMENT_DPS
        }

        if (combinedText.contains("albert") || combinedText.contains("billa") || combinedText.contains("lidl") ||
            combinedText.contains("tesco") || combinedText.contains("penny") || combinedText.contains("kaufland") ||
            combinedText.contains("rohlík") || combinedText.contains("rohlik") || combinedText.contains("košík") ||
            combinedText.contains("kosik") || combinedText.contains("globus") || combinedText.contains("jip") ||
            combinedText.contains("potraviny") || combinedText.contains("pekarstvi") || combinedText.contains("pekárna") ||
            combinedText.contains("pekarna") || combinedText.contains("řeznictví") || combinedText.contains("reznictvi")
        ) {
            return BankTransactionType.GROCERIES
        }

        return BankTransactionType.GENERAL_EXPENSE
    }

    fun determineNettingReason(
        cat: BankTransactionType,
        counterpartyName: String,
        message: String
    ): String {
        if (cat != BankTransactionType.INTERNAL_TRANSFER) return ""
        return if (CrossStatementReconciliationEngine.isMbankGoalOrMsporeni(counterpartyName, message)) {
            "mBank internal transfer (mSpoření / Cíl)"
        } else {
            "Internal transfer"
        }
    }

    fun recomputeSummary(
        summary: StatementParseSummary,
        updatedTransactions: List<ParsedBankTransaction>
    ): StatementParseSummary {
        return buildSummary(summary.detectedBank, updatedTransactions, summary.monthEndBalance)
    }

    private fun buildSummary(
        bankType: BankType,
        transactions: List<ParsedBankTransaction>,
        monthEndBalance: Double? = null
    ): StatementParseSummary {
        if (transactions.isEmpty()) return emptySummary(bankType)

        val ymCounts = transactions.map { it.date.take(7) }.filter { it.matches(Regex("""\d{4}-\d{2}""")) }
            .groupingBy { it }.eachCount()
        val predominantYm = ymCounts.maxByOrNull { it.value }?.key ?: "2026-09"

        var incVaclav = 0.0
        var incEleonora = 0.0
        var incOther = 0.0
        var expRent = 0.0
        var expGroceries = 0.0
        var expOther = 0.0
        var invPortu = 0.0
        var invDip = 0.0
        var invDps = 0.0
        var internalTransfers = 0
        var totalNettedAmount = 0.0

        for (tx in transactions) {
            val amt = tx.amount
            when (tx.category) {
                BankTransactionType.INTERNAL_TRANSFER -> {
                    internalTransfers++
                    totalNettedAmount += kotlin.math.abs(amt)
                }
                BankTransactionType.SALARY_VACLAV -> incVaclav += amt
                BankTransactionType.SALARY_ELEONORA -> incEleonora += amt
                BankTransactionType.PARENTAL_BENEFIT -> incEleonora += amt
                BankTransactionType.OTHER_INFLOW -> incOther += amt
                BankTransactionType.HOUSING_RENT -> expRent += kotlin.math.abs(amt)
                BankTransactionType.GROCERIES -> expGroceries += kotlin.math.abs(amt)
                BankTransactionType.LIFESTYLE_LIVING,
                BankTransactionType.DINING_RESTAURANT,
                BankTransactionType.TRANSPORTATION,
                BankTransactionType.SHOPPING_GOODS,
                BankTransactionType.HEALTH_DRUGSTORE,
                BankTransactionType.SUBSCRIPTIONS_MEDIA,
                BankTransactionType.SERVICES_UTILITIES,
                BankTransactionType.ATM_CASH,
                BankTransactionType.CHARITY_DONATION,
                BankTransactionType.GENERAL_EXPENSE -> expOther += kotlin.math.abs(amt)
                BankTransactionType.INVESTMENT_PORTU -> invPortu += kotlin.math.abs(amt)
                BankTransactionType.INVESTMENT_DIP -> invDip += kotlin.math.abs(amt)
                BankTransactionType.INVESTMENT_DPS -> invDps += kotlin.math.abs(amt)
                BankTransactionType.UNCATEGORIZED -> {
                    if (amt > 0) incOther += amt else expOther += kotlin.math.abs(amt)
                }
            }
        }

        val totalInflows = incVaclav + incEleonora + incOther
        val totalExpenses = expRent + expGroceries + expOther
        val totalInvested = invPortu + invDip + invDps

        return StatementParseSummary(
            detectedBank = bankType,
            yearMonth = predominantYm,
            totalInflows = totalInflows,
            incVaclav = incVaclav,
            incEleonora = incEleonora,
            incOther = incOther,
            totalExpenses = totalExpenses,
            expRent = expRent,
            expGroceries = expGroceries,
            expOther = expOther,
            totalInvested = totalInvested,
            invPortu = invPortu,
            invDip = invDip,
            invDps = invDps,
            internalTransfersCount = internalTransfers,
            totalNettedAmount = totalNettedAmount,
            monthEndBalance = monthEndBalance,
            transactions = transactions
        )
    }

    fun parseCzechAmount(str: String): Double {
        var clean = str.trim()
        if (clean.isBlank()) return 0.0

        // Check for accounting parentheses e.g. (1 500,00) or (1500)
        val isNegativeParentheses = clean.startsWith("(") && clean.endsWith(")")
        if (isNegativeParentheses) {
            clean = clean.substring(1, clean.length - 1).trim()
        }

        // Standardize all Unicode minus, en-dash, em-dash, figure dash, small dash, fullwidth hyphen to ASCII '-'
        clean = clean.replace("\u2212", "-")
            .replace("\u2013", "-")
            .replace("\u2014", "-")
            .replace("\u2012", "-")
            .replace("\uFE63", "-")
            .replace("\uFF0D", "-")
            .replace("+", "")

        // Strip currency symbols
        clean = clean.replace("CZK", "", ignoreCase = true)
            .replace("Kč", "", ignoreCase = true)
            .replace("Kc", "", ignoreCase = true)

        // Strip all Unicode whitespace categories:
        // \p{Z} (space, line, paragraph separators), \s, \u00A0 (NBSP), \u202F (Narrow NBSP), \u200B (Zero-width), \uFEFF (BOM)
        clean = clean.replace(Regex("[\\p{Z}\\s\\uFEFF]"), "").trim()

        if (clean.isBlank()) return 0.0

        clean = if (clean.contains(',') && clean.contains('.')) {
            if (clean.lastIndexOf(',') > clean.lastIndexOf('.')) {
                clean.replace(".", "").replace(',', '.')
            } else {
                clean.replace(",", "")
            }
        } else {
            clean.replace(',', '.')
        }

        val result = clean.toDoubleOrNull() ?: 0.0
        return if (isNegativeParentheses && result > 0) -result else result
    }

    fun normalizeDate(str: String): String {
        val clean = str.trim().replace("/", ".").replace("-", ".")
        val parts = clean.split(".").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size != 3) return ""

        val (yearToken, monthToken, dayToken) = when {
            parts[0].length == 4 && parts[0].all { it.isDigit() } -> Triple(parts[0], parts[1], parts[2])
            parts[2].length == 4 && parts[2].all { it.isDigit() } -> Triple(parts[2], parts[1], parts[0])
            parts[2].length == 2 && parts[2].all { it.isDigit() } -> Triple("20" + parts[2], parts[1], parts[0])
            else -> return ""
        }
        if (!yearToken.all { it.isDigit() } || !monthToken.all { it.isDigit() } || !dayToken.all { it.isDigit() }) {
            return ""
        }

        val year = yearToken.toIntOrNull() ?: return ""
        val month = monthToken.toIntOrNull() ?: return ""
        val day = dayToken.toIntOrNull() ?: return ""
        if (year !in 1900..2200 || month !in 1..12 || day !in 1..31) return ""

        // Reject calendar-invalid dates (e.g. 31.02) instead of emitting malformed ISO strings.
        return try {
            java.time.LocalDate.of(year, month, day).toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    private fun emptySummary(bankType: BankType): StatementParseSummary {
        return StatementParseSummary(
            detectedBank = bankType,
            yearMonth = "2026-09",
            totalInflows = 0.0,
            incVaclav = 0.0,
            incEleonora = 0.0,
            incOther = 0.0,
            totalExpenses = 0.0,
            expRent = 0.0,
            expGroceries = 0.0,
            expOther = 0.0,
            totalInvested = 0.0,
            invPortu = 0.0,
            invDip = 0.0,
            invDps = 0.0,
            internalTransfersCount = 0,
            monthEndBalance = null,
            transactions = emptyList()
        )
    }
}