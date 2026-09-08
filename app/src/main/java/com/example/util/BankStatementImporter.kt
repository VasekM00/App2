package com.example.util

import com.example.data.LedgerEntryEntity
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale

enum class BankType {
    MONETA,
    CSOB,
    MBANK,
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

        // 2. Otherwise parse as standard CSV
        var content = try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            String(bytes, Charset.forName("windows-1250"))
        }

        if (content.contains("\uFFFD")) {
            content = String(bytes, Charset.forName("windows-1250"))
        }

        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
        val bankType = detectBankType(lines)

        return when (bankType) {
            BankType.MONETA -> parseMonetaCsv(lines, knownFamilyAccounts, userOverrides)
            BankType.CSOB -> parseCsobCsv(lines, knownFamilyAccounts, userOverrides)
            BankType.MBANK -> parseMbankCsv(lines, knownFamilyAccounts, userOverrides)
            BankType.GENERIC -> parseGenericCsv(lines, knownFamilyAccounts, userOverrides)
        }
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
            BankType.GENERIC -> parseGenericPdf(lines, extractedText, knownFamilyAccounts, userOverrides)
        }
    }

    private fun detectPdfBankType(fullText: String, lines: List<String>): BankType {
        val lower = fullText.lowercase(Locale.ROOT)
        return when {
            lower.contains("moneta") || lower.contains("/0600") || lower.contains(" 0600") || lower.contains("agbacz") -> BankType.MONETA
            lower.contains("čsob") || lower.contains("csob") || lower.contains("československá obchodní banka") || lower.contains("/0300") || lower.contains("cekoce") -> BankType.CSOB
            lower.contains("mbank") || lower.contains("/6210") || lower.contains("brexcz") || lower.contains("mkonto") -> BankType.MBANK
            else -> {
                for (line in lines.take(20)) {
                    val l = line.lowercase(Locale.ROOT)
                    if (l.contains("moneta")) return BankType.MONETA
                    if (l.contains("csob") || l.contains("čsob")) return BankType.CSOB
                    if (l.contains("mbank")) return BankType.MBANK
                }
                BankType.GENERIC
            }
        }
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
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val closingBalance = extractClosingBalance(fullText, lines)
        val transactions = extractTransactionsFromPdfBlocks(lines, familyAccounts, userOverrides)
        return buildSummary(BankType.GENERIC, transactions, closingBalance)
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
            val cat = categorizeTransaction(txAmount, counterpartyAcc, cleanDesc, cleanDesc, vs, familyAccounts, userOverrides)

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

        clean = cleanPaymentDescription(clean)

        val words = clean.split(Regex("""\s+""")).filter { w ->
            w.isNotBlank() && !w.contains(',') && w != "+" && w != "-" && w != "/"
        }
        val result = words.joinToString(" ").trim()
        val finalClean = Regex("""^\s*\d{1,2}\s+\d{1,2}\s+""").replace(result, "").trim()
        return finalClean.ifBlank { blockText.trim() }
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

    private fun detectBankType(lines: List<String>): BankType {
        for (line in lines.take(15)) {
            val lower = line.lowercase(Locale.ROOT)
            if (lower.contains("#datum operace") || lower.contains("#popis transakce") || lower.contains("mbank")) {
                return BankType.MBANK
            }
            if (lower.contains("číslo účtu protistrany") || lower.contains("název protistrany") || lower.contains("csob")) {
                return BankType.CSOB
            }
            if (lower.contains("číslo protiúčtu") || lower.contains("banka protiúčtu") || lower.contains("moneta")) {
                return BankType.MONETA
            }
        }
        return BankType.GENERIC
    }

    private fun parseMonetaCsv(
        lines: List<String>,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): StatementParseSummary {
        val delimiter = if (lines.firstOrNull { it.contains(";") } != null) ';' else ','
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
        val delimiter = if (lines.firstOrNull { it.contains(";") } != null) ';' else ','
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
    // Categorization & Normalization
    // ==========================================

    private fun categorizeTransaction(
        amount: Double,
        counterpartyAcc: String,
        counterpartyName: String,
        message: String,
        vs: String,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap()
    ): BankTransactionType {
        if (CrossStatementReconciliationEngine.isSelfOrFamilyTransfer(counterpartyName, counterpartyAcc, message, familyAccounts)) {
            return BankTransactionType.INTERNAL_TRANSFER
        }

        val cleanAcc = counterpartyAcc.replace(" ", "").replace("-", "").lowercase(Locale.ROOT)
        if (familyAccounts.any { cleanAcc.contains(it.replace(" ", "").replace("-", "").lowercase(Locale.ROOT)) && it.isNotBlank() }) {
            return BankTransactionType.INTERNAL_TRANSFER
        }

        val combinedText = "$counterpartyName $message".lowercase(Locale.ROOT)

        if (amount > 0) {
            return when {
                combinedText.contains("rodičov") || combinedText.contains("rodicov") || combinedText.contains("úřad práce") || combinedText.contains("urad prace") || combinedText.contains("čssz") || combinedText.contains("cssz") ->
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
        val catalogMatch = CzechMerchantCatalog.matchCategory("$counterpartyName $message", userOverrides)
        if (catalogMatch != null) {
            return catalogMatch
        }

        if (combinedText.contains("portu") || combinedText.contains("wood & company") || combinedText.contains("degiro") || combinedText.contains("xtb") || combinedText.contains("interactive brokers")) {
            return BankTransactionType.INVESTMENT_PORTU
        }
        if (combinedText.contains("dip") || vs.startsWith("7") || combinedText.contains("patria dip")) {
            return BankTransactionType.INVESTMENT_DIP
        }
        if (combinedText.contains("penzij") || combinedText.contains("dps") || combinedText.contains("conseq") || combinedText.contains("generali") || combinedText.contains("allianz") || combinedText.contains("nn penzij")) {
            return BankTransactionType.INVESTMENT_DPS
        }

        if (combinedText.contains("nájem") || combinedText.contains("najem") || combinedText.contains("činže") || combinedText.contains("cinze") || combinedText.contains("byt")) {
            return BankTransactionType.HOUSING_RENT
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

    private fun parseCzechAmount(str: String): Double {
        var clean = str.replace("CZK", "", ignoreCase = true)
            .replace("Kč", "")
            .replace("\u00A0", "")
            .replace(" ", "")
            .trim()

        if (clean.isBlank()) return 0.0

        clean = if (clean.contains(',') && clean.contains('.')) {
            clean.replace(".", "").replace(',', '.')
        } else {
            clean.replace(',', '.')
        }

        return clean.toDoubleOrNull() ?: 0.0
    }

    private fun normalizeDate(str: String): String {
        val clean = str.trim().replace("/", ".").replace("-", ".")
        val parts = clean.split(".").filter { it.isNotBlank() }
        if (parts.size == 3) {
            if (parts[0].length == 4) {
                val y = parts[0]
                val m = parts[1].padStart(2, '0')
                val d = parts[2].padStart(2, '0')
                return "$y-$m-$d"
            } else if (parts[2].length == 4) {
                val d = parts[0].padStart(2, '0')
                val m = parts[1].padStart(2, '0')
                val y = parts[2]
                return "$y-$m-$d"
            }
        }
        return ""
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