package com.example.util.statement

import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.CzechMerchantCatalog
import com.example.util.ParsedBankTransaction
import com.example.util.PdfTextExtractor
import com.example.util.StatementParseSummary
import java.time.LocalDate
import java.util.Locale

internal object PdfStatementParser {

    private val MONETA_PERIOD_REGEX = Regex("""(?i)obdob[ií]:?\s*(?:od\s*)?(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})\s*(?:do|-)\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})""")
    private val MONETA_REPORT_YEAR_REGEX = Regex("""(?i)výpis\s+z\s+účtu\s+č\.\s*(\d{1,2})/(\d{4})""")
    private val MONETA_MONTH_YEAR_REGEX = Regex("""(?i)měsíc:\s*(\d{1,2})/(\d{4})""")
    private val MONETA_OPENING_BAL_REGEX = Regex("""(?i)(?:počáteční|pocatecni)\s*(?:účetní\s*)?zůstatek[^\d+-]*([+-]?\s*[\d\s\u00A0]+,\d{2})""")
    private val MONETA_CLOSING_BAL_REGEX = Regex("""(?i)(?:konečný|konecny|účetní|ucetni)\s*zůstatek[^\d+-]*([+-]?\s*[\d\s\u00A0]+,\d{2})""")

    private val CSOB_PERIOD_REGEX = Regex("""(?i)obdob[ií]:\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})\s*-\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})""")
    private val CSOB_REPORT_YEAR_REGEX = Regex("""(?i)rok/č\.\s*výpisu:\s*(\d{4})/(\d{1,2})""")
    private val CSOB_CASTKA_LOC_REGEX = Regex("""(?i)\b\d{1,2}\.\d{1,2}\.\d{4}\s+([A-Za-z\u00C0-\u017E].*)""")
    private val CSOB_MERCHANT_CHARS_REGEX = Regex("""[A-Za-z0-9*._ -]+""")
    private val CSOB_STRIP_CASTKA_REGEX = Regex("""(?i)\s*č[aá]stka:.*""")
    private val CSOB_INCOMING_REGEX = Regex("""(?i)^\s*\d{1,2}\.\d{1,2}\.(?:\d{4})?\s+p[rř][ií]choz[ií]\s+[uú]hrada(?:\s+okam[zž]it[aá])?\s+([A-Za-z\u00C0-\u017E].*?)(?:\s+\d{4,}\b|\s+[+-]?\d+,\d{2})""")
    private val CSOB_OUTGOING_REGEX = Regex("""(?i)^\s*\d{1,2}\.\d{1,2}\.(?:\d{4})?\s+odchoz[ií]\s+[uú]hrada(?:\s+okam[zž]it[aá])?\s+([A-Za-z\u00C0-\u017E].*?)(?:\s+\d{4,}\b|\s+[+-]?\d+,\d{2})""")

    private val MBANK_PERIOD_REGEX = Regex("""(?i)(?:obdob[ií]|za\s+obdob[ií])[:\s]*(\d{1,2})[./-](\d{1,2})[./-](\d{4})\s*(?:-|do)\s*(\d{1,2})[./-](\d{1,2})[./-](\d{4})""")
    private val MBANK_MONTH_YEAR_REGEX = Regex("""(?i)(?:za\s+m[eě]s[ií]c|v[yý]pis\s+za)\s*(\d{1,2})/(\d{4})""")
    private val MBANK_ALT_MONTH_YEAR_REGEX = Regex("""(?i)(?:za\s+obdob[ií]|m[eě]s[ií]c/rok)[:\s]*(\d{4})/(\d{1,2})""")
    private val MBANK_REPORT_YEAR_REGEX = Regex("""(?i)v[yý]pis\s+[cč]\.?\s*\d+/(\d{4})""")
    private val MBANK_DATE_PATTERN = Regex("""^\s*(\d{1,2})[./-](\d{1,2})(?:[./-](\d{2,4}))?(?![\p{L}\d])""")

    private val YEAR_202X_REGEX = Regex("""\b(202[0-9])\b""")
    private val TX_START_FULL_DATE_REGEX = Regex("""^\s*(\d{1,2})\.(\d{1,2})\.(\d{4})\s+(.*)""")
    private val TX_START_SHORT_DATE_REGEX = Regex("""^\s*(\d{1,2})\.(\d{1,2})\.\s+(.*)""")
    private val DATE_BLOCKS_START_REGEX = Regex("""^\s*(\d{1,4})[./-]\s*(\d{1,2})(?:[./-]\s*(\d{2,4}))?(?![\p{L}\d])""")
    private val DATE_BLOCKS_ANY_REGEX = Regex("""(?<![\p{L}\d])(\d{1,4})[./-]\s*(\d{1,2})[./-]\s*(\d{2,4})(?![\p{L}\d])""")

    private data class DateBlock(
        val date: String,
        val lines: List<String>
    )

    fun parsePdfStatement(
        pdfBytes: ByteArray,
        knownFamilyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): StatementParseSummary {
        val extractedText = PdfTextExtractor.extractText(pdfBytes)
        if (extractedText.isBlank()) {
            return TransactionCategorizer.emptySummary(BankType.GENERIC).copy(isPdfSource = true)
        }

        val lines = extractedText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val bankType = BankDetector.detectPdfBankType(extractedText, lines)
        val compiledOverrides = CzechMerchantCatalog.compileOverrides(userOverrides)

        val parsed = when (bankType) {
            BankType.MONETA -> parseMonetaPdf(lines, extractedText, knownFamilyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
            BankType.CSOB -> parseCsobPdf(lines, extractedText, knownFamilyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
            BankType.MBANK -> parseMbankPdf(lines, extractedText, knownFamilyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
            else -> parseGenericPdf(lines, extractedText, knownFamilyAccounts, compiledOverrides, bankType, householdNames, knownParentalGiftAccounts)
        }
        val ownBankCode = CrossStatementReconciliationEngine.bankCodeForBankName(parsed.detectedBank.name)
        val ownAccountKey = parsed.accountKey.ifBlank {
            CzechValueParsers.extractOwnStatementAccountKey(lines, bankCode = ownBankCode, limitLines = 35)
        }
        return parsed.copy(
            isPdfSource = true,
            accountKey = ownAccountKey,
            transactions = if (ownAccountKey.isNotBlank()) {
                parsed.transactions.map { if (it.accountKey.isBlank()) it.copy(accountKey = ownAccountKey) else it }
            } else {
                parsed.transactions
            }
        )
    }

    private fun isPageHeaderOrFooter(line: String): Boolean {
        val folded = StatementTextDecoder.foldHeader(line)
        return BankDetector.isIgnoredDateContext(line) ||
            folded.contains("vypis z bezneho uctu") ||
            folded.contains("vypis z uctu") ||
            folded.contains("cislo vypisu") ||
            folded.contains("periodicita vypisu") ||
            folded.contains("vypis pokracuje") ||
            folded.contains("prehled transakci") ||
            folded.contains("zakaznicky servis") ||
            folded.contains("vklad na tomto uctu podleha") ||
            folded.contains("smluvni urok") ||
            folded.contains("celkovy pocet transakci") ||
            folded.contains("soucet obratu") ||
            (folded.contains("moneta money bank") && folded.contains("praha")) ||
            (folded.contains("ceskoslovenska obchodni banka") && folded.contains("praha")) ||
            (folded.contains("mbank s.a.") && folded.contains("praha"))
    }

    private fun parseMonetaPdf(
        lines: List<String>,
        fullText: String,
        familyAccounts: Set<String>,
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): StatementParseSummary {
        var statementYear = 0
        var statementMonth = 0

        val periodMatch = MONETA_PERIOD_REGEX.find(fullText)
        if (periodMatch != null) {
            statementYear = periodMatch.groupValues[6].toIntOrNull() ?: 0
            statementMonth = periodMatch.groupValues[5].toIntOrNull() ?: 0
        }
        if (statementYear == 0) {
            val reportMatch = MONETA_REPORT_YEAR_REGEX.find(fullText)
            if (reportMatch != null) {
                statementMonth = reportMatch.groupValues[1].toIntOrNull() ?: 0
                statementYear = reportMatch.groupValues[2].toIntOrNull() ?: 0
            }
        }
        if (statementYear == 0) {
            val myMatch = MONETA_MONTH_YEAR_REGEX.find(fullText)
            if (myMatch != null) {
                statementMonth = myMatch.groupValues[1].toIntOrNull() ?: 0
                statementYear = myMatch.groupValues[2].toIntOrNull() ?: 0
            }
        }
        if (statementYear == 0) {
            val yearInText = YEAR_202X_REGEX.find(fullText)?.groupValues?.get(1)?.toIntOrNull()
            statementYear = yearInText ?: LocalDate.now().year
        }

        val ym = if (statementYear > 0 && statementMonth in 1..12) {
            String.format(Locale.ROOT, "%04d-%02d", statementYear, statementMonth)
        } else null

        val openMatch = MONETA_OPENING_BAL_REGEX.find(fullText)
        if (openMatch != null) {
            CzechValueParsers.parseCzechAmount(openMatch.groupValues[1])
        }

        var closingBalance = CzechValueParsers.extractClosingBalance(fullText, lines)
        if (closingBalance == null) {
            val closeMatch = MONETA_CLOSING_BAL_REGEX.find(fullText)
            if (closeMatch != null) {
                val parsedClose = CzechValueParsers.parseCzechAmount(closeMatch.groupValues[1])
                if (parsedClose.isFinite()) closingBalance = parsedClose
            }
        }

        fun isMonetaNoise(line: String): Boolean {
            val folded = StatementTextDecoder.foldHeader(line)
            return isPageHeaderOrFooter(line) ||
                folded.contains("moneta money bank") ||
                folded.contains("vyskocilova") ||
                folded.contains("ico: 25672720") ||
                folded.contains("zapsana v obchodnim rejstriku") ||
                folded.contains("cislo uctu:") ||
                folded.contains("kod banky: 0600") ||
                folded.contains("datum zauctovani") ||
                folded.contains("datum provedeni") ||
                folded.contains("popis transakce") ||
                folded.contains("pohyby na uctu") ||
                folded.contains("soucet kreditnich") ||
                folded.contains("soucet debetnich") ||
                folded.contains("vklad na tomto uctu podleha") ||
                folded.contains("informacni prehled")
        }

        data class MonetaBlock(
            val day: Int,
            val month: Int,
            val year: Int,
            val headerLine: String,
            val detailLines: MutableList<String> = mutableListOf()
        )

        val blocks = mutableListOf<MonetaBlock>()
        var currentBlock: MonetaBlock? = null
        val cardDetailMarkers = listOf("misto:", "karta c.", "puvodni castka", "kurz:", "autorizace", "poplatek za transakci")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || isMonetaNoise(trimmed)) continue

            val folded = StatementTextDecoder.foldHeader(trimmed)
            val isCardDetail = cardDetailMarkers.any { folded.contains(it) }

            val matchFull = TX_START_FULL_DATE_REGEX.find(trimmed)
            val matchShort = if (matchFull == null) TX_START_SHORT_DATE_REGEX.find(trimmed) else null
            val hasAmounts = CzechValueParsers.extractAllAmounts(trimmed).isNotEmpty()

            if (!isCardDetail && hasAmounts && matchFull != null) {
                currentBlock?.let { blocks.add(it) }
                currentBlock = MonetaBlock(
                    matchFull.groupValues[1].toInt(),
                    matchFull.groupValues[2].toInt(),
                    matchFull.groupValues[3].toInt(),
                    matchFull.groupValues[4]
                )
            } else if (!isCardDetail && hasAmounts && matchShort != null) {
                currentBlock?.let { blocks.add(it) }
                val d = matchShort.groupValues[1].toInt()
                val m = matchShort.groupValues[2].toInt()
                val y = if (statementYear > 0) {
                    if (statementMonth == 1 && m == 12) statementYear - 1
                    else if (statementMonth == 12 && m == 1) statementYear + 1
                    else statementYear
                } else LocalDate.now().year
                currentBlock = MonetaBlock(d, m, y, matchShort.groupValues[3])
            } else {
                currentBlock?.detailLines?.add(trimmed)
            }
        }
        currentBlock?.let { blocks.add(it) }

        val transactions = mutableListOf<ParsedBankTransaction>()
        for (block in blocks) {
            val blockText = (listOf(block.headerLine) + block.detailLines).joinToString(" ")
            val amountDetails = CzechValueParsers.extractAllAmountsWithCurrency(blockText)
            val primaryDetail = amountDetails.firstOrNull { it.amountInCzk.isFinite() && it.amountInCzk != 0.0 } ?: continue
            val txAmount = primaryDetail.amountInCzk

            val dateStr = CzechValueParsers.safeIsoDate(block.year, block.month, block.day) ?: continue
            val counterpartyAcc = CzechValueParsers.extractAccount(blockText)
            val vs = CzechValueParsers.extractVariableSymbol(blockText)

            // Categorize BEFORE PaymentDescriptionCleaner strips "Příkazce:" / boilerplate!
            val rawDesc = blockText
            val cat = TransactionCategorizer.categorizeTransaction(
                amount = txAmount,
                counterpartyAcc = counterpartyAcc,
                counterpartyName = rawDesc,
                message = "",
                vs = vs,
                familyAccounts = familyAccounts,
                rawContext = blockText,
                householdNames = householdNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts,
                compiledOverrides = compiledOverrides
            )
            val cleanDesc = PaymentDescriptionCleaner.cleanTransactionDescription(
                blockText = blockText,
                dateStr = dateStr,
                amounts = amountDetails.map { it.amountInCzk },
                account = counterpartyAcc,
                vs = vs,
                householdNames = householdNames
            )
            val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
            val nettingReason = TransactionCategorizer.determineNettingReason(cat, cleanDesc, "")

            val origCurr = primaryDetail.originalCurrency.takeIf { it != "CZK" }
            val origAmt = if (origCurr != null) primaryDetail.originalAmount else null
            val usedRate = if (origCurr != null) primaryDetail.exchangeRate else null

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
                    nettingReason = nettingReason,
                    bankType = BankType.MONETA,
                    originalCurrency = origCurr,
                    originalAmount = origAmt,
                    exchangeRate = usedRate
                )
            )
        }

        val finalTransactions = transactions.ifEmpty {
            extractTransactionsFromPdfBlocks(lines, familyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
        }
        return TransactionCategorizer.buildSummary(BankType.MONETA, finalTransactions, closingBalance, forcedYearMonth = ym)
    }

    private fun parseCsobPdf(
        lines: List<String>,
        fullText: String,
        familyAccounts: Set<String>,
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): StatementParseSummary {
        var statementYear = 0
        var statementMonth = 0

        val periodMatch = CSOB_PERIOD_REGEX.find(fullText)
        if (periodMatch != null) {
            statementYear = periodMatch.groupValues[6].toIntOrNull() ?: 0
            statementMonth = periodMatch.groupValues[5].toIntOrNull() ?: 0
        }
        if (statementYear == 0) {
            val reportMatch = CSOB_REPORT_YEAR_REGEX.find(fullText)
            if (reportMatch != null) {
                statementYear = reportMatch.groupValues[1].toIntOrNull() ?: 0
                statementMonth = reportMatch.groupValues[2].toIntOrNull() ?: 0
            }
        }
        if (statementYear == 0) {
            val yearInText = YEAR_202X_REGEX.find(fullText)?.groupValues?.get(1)?.toIntOrNull()
            statementYear = yearInText ?: LocalDate.now().year
        }

        val ym = if (statementYear > 0 && statementMonth in 1..12) {
            String.format(Locale.ROOT, "%04d-%02d", statementYear, statementMonth)
        } else null

        var closingBalance = CzechValueParsers.extractClosingBalance(fullText, lines)

        fun isCsobNoise(line: String): Boolean {
            val folded = StatementTextDecoder.foldHeader(line)
            return isPageHeaderOrFooter(line) ||
                folded.startsWith("ucet:") ||
                folded.contains("nazev uctu:") ||
                folded.contains("datum oznaceni platby") ||
                folded.contains("oznaceni platby") ||
                folded.contains("valuta protiucet") ||
                folded.contains("protiucet nebo poznamka") ||
                folded.contains("prosime vas") ||
                folded.contains("uvedeny v zapati") ||
                folded.contains("pujcka na") ||
                folded.contains("predpripravili jsme") ||
                folded.contains("kreditni karta ve vysi") ||
                folded.contains("uvedene predschvalene limity") ||
                folded.contains("pokud pri zuctovani") ||
                folded.contains("evropske centralni banky") ||
                folded.contains("prehled pohybu na uctu") ||
                folded.contains("souhrnne informace") ||
                folded.contains("v pripade nesouhlasu") ||
                folded.contains("vklad na tomto uctu") ||
                folded.contains("system pojisteni pohledavek") ||
                folded.contains("pujcku vam vyridime")
        }

        data class CsobBlock(
            val day: Int,
            val month: Int,
            val explicitYear: Int?,
            val headerLine: String,
            val detailLines: MutableList<String> = mutableListOf()
        )

        val csobCardDetailMarkers = listOf("misto:", "puvodni castka", "kurz:", "karta:", "autorizace")
        val blocks = mutableListOf<CsobBlock>()
        var curBlock: CsobBlock? = null

        for (line in lines) {
            if (isCsobNoise(line)) continue

            val folded = StatementTextDecoder.foldHeader(line)
            val isCardDetail = csobCardDetailMarkers.any { folded.contains(it) }

            val fullMatch = TX_START_FULL_DATE_REGEX.find(line)
            val shortMatch = if (fullMatch == null) TX_START_SHORT_DATE_REGEX.find(line) else null
            if (!isCardDetail && (fullMatch != null || shortMatch != null) && CzechValueParsers.extractAllAmounts(line).isNotEmpty()) {
                val newBlock = if (fullMatch != null) {
                    CsobBlock(
                        day = fullMatch.groupValues[1].toInt(),
                        month = fullMatch.groupValues[2].toInt(),
                        explicitYear = fullMatch.groupValues[3].toIntOrNull(),
                        headerLine = line
                    )
                } else {
                    CsobBlock(
                        day = shortMatch!!.groupValues[1].toInt(),
                        month = shortMatch.groupValues[2].toInt(),
                        explicitYear = null,
                        headerLine = line
                    )
                }
                blocks.add(newBlock)
                curBlock = newBlock
            } else if (curBlock != null) {
                curBlock.detailLines.add(line)
            }
        }

        val transactions = mutableListOf<ParsedBankTransaction>()
        val balanceWithDates = mutableListOf<Pair<String, Double>>()

        for (block in blocks) {
            var txYear = block.explicitYear ?: statementYear
            if (block.explicitYear == null) {
                if (statementMonth == 1 && block.month == 12) txYear = statementYear - 1
                else if (statementMonth == 12 && block.month == 1) txYear = statementYear + 1
            }
            val dateStr = CzechValueParsers.safeIsoDate(txYear, block.month, block.day) ?: continue

            val headerAmounts = CzechValueParsers.extractAllAmountsWithCurrency(block.headerLine)
            val allBlockAmounts = if (headerAmounts.isNotEmpty()) {
                headerAmounts
            } else {
                CzechValueParsers.extractAllAmountsWithCurrency(block.detailLines.joinToString(" "))
            }
            if (allBlockAmounts.isEmpty()) continue

            val primaryDetail = allBlockAmounts[0]
            val txAmount = primaryDetail.amountInCzk
            if (!txAmount.isFinite()) continue
            if (allBlockAmounts.size >= 2) {
                val runningBalance = allBlockAmounts[1].amountInCzk
                if (runningBalance.isFinite()) {
                    balanceWithDates.add(dateStr to runningBalance)
                }
            }

            val detailText = block.detailLines.joinToString(" ")
            val blockFullText = "${block.headerLine} $detailText"

            val counterpartyAcc = CzechValueParsers.extractAccount(detailText).ifBlank { CzechValueParsers.extractAccount(block.headerLine) }
            val vs = CzechValueParsers.extractVariableSymbol(detailText).ifBlank { CzechValueParsers.extractVariableSymbol(block.headerLine) }

            var rawMerchant = ""
            var message = ""

            val foldedHeader = StatementTextDecoder.foldHeader(block.headerLine)
            val isCard = foldedHeader.contains("kartou") || foldedHeader.contains("karta")
            val isIncoming = foldedHeader.contains("prichozi")
            val isOutgoing = foldedHeader.contains("odchozi")
            val isStanding = foldedHeader.contains("trvaly")
            val isDrobne = foldedHeader.contains("drobne")

            if (isCard) {
                val mistoLine = block.detailLines.firstOrNull {
                    it.contains("místo:", ignoreCase = true) || it.contains("misto:", ignoreCase = true)
                }
                if (mistoLine != null) {
                    val idxMisto = mistoLine.indexOf("místo:", ignoreCase = true)
                        .takeIf { it >= 0 }
                        ?: mistoLine.indexOf("misto:", ignoreCase = true)
                    val rawMisto = if (idxMisto >= 0) mistoLine.substring(idxMisto + 6) else mistoLine
                    val idxCastka = rawMisto.indexOf("částka:", ignoreCase = true)
                        .takeIf { it >= 0 }
                        ?: rawMisto.indexOf("castka:", ignoreCase = true)
                    rawMerchant = (if (idxCastka >= 0) rawMisto.substring(0, idxCastka) else rawMisto).trim()
                }
                if (rawMerchant.isBlank()) {
                    val castkaLine = block.detailLines.firstOrNull { it.startsWith("Částka:", ignoreCase = true) || it.startsWith("Castka:", ignoreCase = true) }
                    val locMatch = CSOB_CASTKA_LOC_REGEX.find(castkaLine ?: "")
                    if (locMatch != null) {
                        val loc = locMatch.groupValues[1].trim()
                        val preceding = block.detailLines.firstOrNull {
                            CSOB_MERCHANT_CHARS_REGEX.matches(it) &&
                                !it.startsWith("Částka", ignoreCase = true) &&
                                !CzechValueParsers.DIGITS_SPACE_ONLY_REGEX.matches(it)
                        }
                        rawMerchant = if (preceding != null) "${preceding.trim()} $loc" else loc
                    }
                }
                if (rawMerchant.isBlank()) {
                    val meaningfulDetails = block.detailLines.filter { d ->
                        !CzechValueParsers.DIGITS_SPACE_ONLY_REGEX.matches(d) &&
                            !d.startsWith("Částka:", ignoreCase = true) &&
                            !d.startsWith("Castka:", ignoreCase = true)
                    }
                    rawMerchant = meaningfulDetails.joinToString(" ").trim()
                }
                rawMerchant = CSOB_STRIP_CASTKA_REGEX.replace(rawMerchant, "").trim()
            } else if (isIncoming) {
                val incomingMatch = CSOB_INCOMING_REGEX.find(block.headerLine)
                val extractedName = incomingMatch?.groupValues?.get(1)?.trim() ?: ""
                if (extractedName.isNotBlank() && !CzechValueParsers.DIGITS_SPACE_ONLY_REGEX.matches(extractedName)) {
                    rawMerchant = extractedName
                }
                val msgLines = block.detailLines.filter { d ->
                    CzechValueParsers.extractAccount(d).isBlank() && !CzechValueParsers.DIGITS_SPACE_ONLY_REGEX.matches(d)
                }
                message = msgLines.joinToString(" ").trim()
            } else if (isOutgoing) {
                val outgoingMatch = CSOB_OUTGOING_REGEX.find(block.headerLine)
                val extractedName = outgoingMatch?.groupValues?.get(1)?.trim() ?: ""
                if (extractedName.isNotBlank() && !CzechValueParsers.DIGITS_SPACE_ONLY_REGEX.matches(extractedName)) {
                    rawMerchant = extractedName
                }
                val msgLines = block.detailLines.filter { d ->
                    CzechValueParsers.extractAccount(d).isBlank() && !CzechValueParsers.DIGITS_SPACE_ONLY_REGEX.matches(d)
                }
                message = msgLines.joinToString(" ").trim()
                if (rawMerchant.isBlank()) {
                    rawMerchant = if (message.isNotBlank()) message else if (counterpartyAcc.isNotBlank()) "Odchozí úhrada $counterpartyAcc" else "Odchozí úhrada"
                }
            } else if (isStanding) {
                val msgLines = block.detailLines.filter { d ->
                    CzechValueParsers.extractAccount(d).isBlank() && !CzechValueParsers.DIGITS_SPACE_ONLY_REGEX.matches(d)
                }
                message = msgLines.joinToString(" ").trim()
                rawMerchant = if (message.isNotBlank()) message else if (counterpartyAcc.isNotBlank()) "Trvalý příkaz $counterpartyAcc" else "Trvalý příkaz"
            } else if (isDrobne) {
                rawMerchant = "ČSOB Drobné"
                val fundLine = block.detailLines.firstOrNull { it.contains("ČSOB", ignoreCase = true) }
                if (fundLine != null) message = fundLine.trim()
            }

            if (rawMerchant.isBlank()) {
                rawMerchant = PaymentDescriptionCleaner.cleanTransactionDescription(
                    blockFullText,
                    dateStr,
                    allBlockAmounts.map { it.amountInCzk },
                    counterpartyAcc,
                    vs,
                    householdNames
                )
            }

            // Categorize BEFORE PaymentDescriptionCleaner strips Příkazce:/boilerplate
            val cat = TransactionCategorizer.categorizeTransaction(
                amount = txAmount,
                counterpartyAcc = counterpartyAcc,
                counterpartyName = rawMerchant,
                message = message,
                vs = vs,
                familyAccounts = familyAccounts,
                rawContext = blockFullText,
                householdNames = householdNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts,
                compiledOverrides = compiledOverrides
            )
            val cleanMerchant = PaymentDescriptionCleaner.cleanPaymentDescription(rawMerchant, householdNames)
            val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
            val nettingReason = TransactionCategorizer.determineNettingReason(cat, cleanMerchant, message)

            val origCurr = primaryDetail.originalCurrency.takeIf { it != "CZK" }
            val origAmt = if (origCurr != null) primaryDetail.originalAmount else null
            val usedRate = if (origCurr != null) primaryDetail.exchangeRate else null

            transactions.add(
                ParsedBankTransaction(
                    date = dateStr,
                    amount = txAmount,
                    counterpartyAccount = counterpartyAcc,
                    counterpartyName = cleanMerchant,
                    message = message,
                    variableSymbol = vs,
                    category = cat,
                    isNetted = isNetted,
                    nettingReason = nettingReason,
                    originalCurrency = origCurr,
                    originalAmount = origAmt,
                    exchangeRate = usedRate
                )
            )
        }

        val finalTransactions = transactions.ifEmpty {
            extractTransactionsFromPdfBlocks(lines, familyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
        }

        if (closingBalance == null && balanceWithDates.isNotEmpty()) {
            closingBalance = CzechValueParsers.resolveClosingBalance(balanceWithDates, finalTransactions)
                ?: balanceWithDates.lastOrNull()?.second
        }

        return TransactionCategorizer.buildSummary(BankType.CSOB, finalTransactions, closingBalance, forcedYearMonth = ym)
    }

    private fun parseMbankPdf(
        lines: List<String>,
        fullText: String,
        familyAccounts: Set<String>,
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): StatementParseSummary {
        var statementYear = 0
        var statementMonth = 0

        val periodMatch = MBANK_PERIOD_REGEX.find(fullText)
        if (periodMatch != null) {
            statementYear = periodMatch.groupValues[6].toIntOrNull() ?: 0
            statementMonth = periodMatch.groupValues[5].toIntOrNull() ?: 0
        }
        if (statementYear == 0) {
            val myMatch = MBANK_MONTH_YEAR_REGEX.find(fullText)
            if (myMatch != null) {
                statementMonth = myMatch.groupValues[1].toIntOrNull() ?: 0
                statementYear = myMatch.groupValues[2].toIntOrNull() ?: 0
            }
        }
        if (statementYear == 0) {
            val altMatch = MBANK_ALT_MONTH_YEAR_REGEX.find(fullText)
            if (altMatch != null) {
                statementYear = altMatch.groupValues[1].toIntOrNull() ?: 0
                statementMonth = altMatch.groupValues[2].toIntOrNull() ?: 0
            }
        }
        if (statementYear == 0) {
            val repMatch = MBANK_REPORT_YEAR_REGEX.find(fullText)
            if (repMatch != null) {
                statementYear = repMatch.groupValues[1].toIntOrNull() ?: 0
            }
        }
        if (statementYear == 0) {
            val yearInText = YEAR_202X_REGEX.find(fullText)?.groupValues?.get(1)?.toIntOrNull()
            statementYear = yearInText ?: LocalDate.now().year
        }

        val ym = if (statementYear > 0 && statementMonth in 1..12) {
            String.format(Locale.ROOT, "%04d-%02d", statementYear, statementMonth)
        } else null

        var closingBalance = CzechValueParsers.extractClosingBalance(fullText, lines)
        val transactions = mutableListOf<ParsedBankTransaction>()
        val balanceWithDates = mutableListOf<Pair<String, Double>>()

        data class MbankBlock(
            val dateStr: String,
            val lines: MutableList<String> = mutableListOf()
        )

        fun isMbankNoise(line: String): Boolean {
            val folded = StatementTextDecoder.foldHeader(line)
            return isPageHeaderOrFooter(line) ||
                folded.contains("datum operace") ||
                folded.contains("datum zauctovani") ||
                folded.contains("ucetni zustatek") ||
                folded.contains("informace o poplatcich") ||
                (folded.contains("mbank s.a.") && folded.contains("organizacni slozka"))
        }

        val blocks = mutableListOf<MbankBlock>()
        var currentBlock: MbankBlock? = null

        for (line in lines) {
            if (isMbankNoise(line)) continue
            val match = MBANK_DATE_PATTERN.find(line)
            val amounts = CzechValueParsers.extractAllAmounts(line)
            val isDateStart = match != null && !BankDetector.isIgnoredDateContext(line) && amounts.isNotEmpty()

            if (isDateStart) {
                val d = match!!.groupValues[1].toIntOrNull() ?: 1
                val m = match.groupValues[2].toIntOrNull() ?: 1
                val rawY = match.groupValues.getOrNull(3)?.toIntOrNull()
                val y = when {
                    rawY != null && rawY in 1000..9999 -> rawY
                    rawY != null && rawY in 0..99 -> 2000 + rawY
                    statementYear > 0 -> {
                        if (statementMonth == 1 && m == 12) statementYear - 1
                        else if (statementMonth == 12 && m == 1) statementYear + 1
                        else statementYear
                    }
                    else -> statementYear
                }
                val isoDate = CzechValueParsers.safeIsoDate(y, m, d)
                if (isoDate != null) {
                    currentBlock?.let { if (it.lines.isNotEmpty()) blocks.add(it) }
                    currentBlock = MbankBlock(isoDate, mutableListOf(line))
                } else if (currentBlock != null) {
                    currentBlock.lines.add(line)
                }
            } else if (currentBlock != null) {
                currentBlock.lines.add(line)
            }
        }
        currentBlock?.let { if (it.lines.isNotEmpty()) blocks.add(it) }

        val blocksToUse = if (blocks.isNotEmpty()) {
            blocks.map { DateBlock(it.dateStr, it.lines) }
        } else {
            groupLinesIntoDateBlocks(lines)
        }

        for (block in blocksToUse) {
            val dateStr = block.date
            val blockText = block.lines.joinToString(" ")
            val amountDetails = CzechValueParsers.extractAllAmountsWithCurrency(blockText)
            if (amountDetails.isEmpty() || dateStr.isBlank()) continue

            val primaryDetail = amountDetails[0]
            val txAmount = primaryDetail.amountInCzk
            if (!txAmount.isFinite()) continue
            if (amountDetails.size >= 2) {
                val runningBalance = amountDetails[1].amountInCzk
                if (runningBalance.isFinite()) {
                    balanceWithDates.add(dateStr to runningBalance)
                }
            }

            val counterpartyAcc = CzechValueParsers.extractAccount(blockText)
            val vs = CzechValueParsers.extractVariableSymbol(blockText)

            // Categorize BEFORE stripping Příkazce: / payment boilerplate
            val cat = TransactionCategorizer.categorizeTransaction(
                amount = txAmount,
                counterpartyAcc = counterpartyAcc,
                counterpartyName = blockText,
                message = "",
                vs = vs,
                familyAccounts = familyAccounts,
                rawContext = blockText,
                householdNames = householdNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts,
                compiledOverrides = compiledOverrides
            )
            val cleanDesc = PaymentDescriptionCleaner.cleanTransactionDescription(
                blockText = blockText,
                dateStr = dateStr,
                amounts = amountDetails.map { it.amountInCzk },
                account = counterpartyAcc,
                vs = vs,
                householdNames = householdNames
            )
            val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
            val nettingReason = TransactionCategorizer.determineNettingReason(cat, cleanDesc, "")

            val origCurr = primaryDetail.originalCurrency.takeIf { it != "CZK" }
            val origAmt = if (origCurr != null) primaryDetail.originalAmount else null
            val usedRate = if (origCurr != null) primaryDetail.exchangeRate else null

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
                    nettingReason = nettingReason,
                    originalCurrency = origCurr,
                    originalAmount = origAmt,
                    exchangeRate = usedRate
                )
            )
        }

        if (closingBalance == null && balanceWithDates.isNotEmpty()) {
            closingBalance = CzechValueParsers.resolveClosingBalance(balanceWithDates, transactions)
        }

        return TransactionCategorizer.buildSummary(BankType.MBANK, transactions, closingBalance, forcedYearMonth = ym)
    }

    private fun parseGenericPdf(
        lines: List<String>,
        fullText: String,
        familyAccounts: Set<String>,
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        bankType: BankType,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): StatementParseSummary {
        val closingBalance = CzechValueParsers.extractClosingBalance(fullText, lines)
        val transactions = extractTransactionsFromPdfBlocks(
            lines, familyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts
        )
        return TransactionCategorizer.buildSummary(bankType, transactions, closingBalance)
    }

    private fun groupLinesIntoDateBlocks(lines: List<String>): List<DateBlock> {
        val blocks = mutableListOf<DateBlock>()
        var currentDate = ""
        val currentLines = mutableListOf<String>()

        val inferredYear = lines.asSequence().mapNotNull { line ->
            YEAR_202X_REGEX.find(line)?.groupValues?.get(1)?.toIntOrNull()
        }.firstOrNull() ?: LocalDate.now().year

        val hasDateStartLines = lines.any { line ->
            DATE_BLOCKS_START_REGEX.containsMatchIn(line) &&
                CzechValueParsers.extractAllAmounts(line).isNotEmpty() &&
                !BankDetector.isIgnoredDateContext(line)
        }

        for (line in lines) {
            if (isPageHeaderOrFooter(line)) continue

            if (hasDateStartLines) {
                val match = DATE_BLOCKS_START_REGEX.find(line)
                val amounts = CzechValueParsers.extractAllAmounts(line)
                val isTxStart = match != null && amounts.isNotEmpty() && !BankDetector.isIgnoredDateContext(line)

                if (isTxStart) {
                    if (currentDate.isNotBlank() && currentLines.isNotEmpty()) {
                        blocks.add(DateBlock(currentDate, currentLines.toList()))
                        currentLines.clear()
                    }
                    val rawDateMatch = match!!.value.trim()
                    val normalized = CzechValueParsers.normalizeDate(rawDateMatch)
                    val nextDate = if (normalized.isNotBlank()) {
                        normalized
                    } else {
                        val p1 = match.groupValues[1].toIntOrNull() ?: 0
                        val p2 = match.groupValues[2].toIntOrNull() ?: 0
                        val p3 = match.groupValues.getOrNull(3)?.toIntOrNull()
                        val yr = when {
                            p3 != null && p3 in 1000..9999 -> p3
                            p3 != null && p3 in 0..99 -> 2000 + p3
                            p1 in 1000..9999 -> p1
                            else -> inferredYear
                        }
                        val (d, m) = if (p1 in 1000..9999) {
                            Pair(match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0, p2)
                        } else {
                            Pair(p1, p2)
                        }
                        CzechValueParsers.safeIsoDate(yr, m, d) ?: ""
                    }
                    if (nextDate.isNotBlank()) {
                        currentDate = nextDate
                        currentLines.add(line)
                    } else {
                        currentDate = ""
                    }
                } else if (currentDate.isNotBlank()) {
                    currentLines.add(line)
                }
            } else {
                val match = DATE_BLOCKS_ANY_REGEX.find(line)
                if (match != null && !BankDetector.isIgnoredDateContext(line)) {
                    val normalized = CzechValueParsers.normalizeDate(match.value)
                    if (normalized.isNotBlank()) {
                        if (currentDate.isNotBlank() && currentLines.isNotEmpty()) {
                            blocks.add(DateBlock(currentDate, currentLines.toList()))
                            currentLines.clear()
                        }
                        currentDate = normalized
                        currentLines.add(line)
                    } else if (currentDate.isNotBlank()) {
                        currentLines.add(line)
                    }
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
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): List<ParsedBankTransaction> {
        val blocks = groupLinesIntoDateBlocks(lines)
        val transactions = mutableListOf<ParsedBankTransaction>()

        for (block in blocks) {
            val dateStr = block.date
            val blockText = block.lines.joinToString(" ")
            val amountDetails = CzechValueParsers.extractAllAmountsWithCurrency(blockText)
            if (amountDetails.isEmpty() || dateStr.isBlank()) continue

            val primaryDetail = amountDetails.firstOrNull { it.amountInCzk != 0.0 } ?: continue
            val txAmount = primaryDetail.amountInCzk
            val counterpartyAcc = CzechValueParsers.extractAccount(blockText)
            val vs = CzechValueParsers.extractVariableSymbol(blockText)

            val detailLines = block.lines.drop(1).filter { d ->
                !CzechValueParsers.DIGITS_SPACE_ONLY_REGEX.matches(d) &&
                    !d.startsWith("Zůstatek", ignoreCase = true) &&
                    !d.startsWith("Zustatek", ignoreCase = true) &&
                    CzechValueParsers.extractAccount(d).isBlank()
            }
            val rawMsg = detailLines.joinToString(" ").trim()

            // Categorize BEFORE PaymentDescriptionCleaner strips Příkazce:/boilerplate
            val cat = TransactionCategorizer.categorizeTransaction(
                amount = txAmount,
                counterpartyAcc = counterpartyAcc,
                counterpartyName = blockText,
                message = rawMsg,
                vs = vs,
                familyAccounts = familyAccounts,
                rawContext = blockText,
                householdNames = householdNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts,
                compiledOverrides = compiledOverrides
            )

            val cleanDesc = PaymentDescriptionCleaner.cleanTransactionDescription(
                blockText = blockText,
                dateStr = dateStr,
                amounts = amountDetails.map { it.amountInCzk },
                account = counterpartyAcc,
                vs = vs,
                householdNames = householdNames
            )
            val cleanMsg = PaymentDescriptionCleaner.cleanPaymentDescription(rawMsg, householdNames)
            val distinctMsg = if (cleanMsg.isNotBlank() && !cleanMsg.equals(cleanDesc, ignoreCase = true)) cleanMsg else ""
            val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
            val nettingReason = TransactionCategorizer.determineNettingReason(cat, cleanDesc, distinctMsg)

            val origCurr = primaryDetail.originalCurrency.takeIf { it != "CZK" }
            val origAmt = if (origCurr != null) primaryDetail.originalAmount else null
            val usedRate = if (origCurr != null) primaryDetail.exchangeRate else null

            transactions.add(
                ParsedBankTransaction(
                    date = dateStr,
                    amount = txAmount,
                    counterpartyAccount = counterpartyAcc,
                    counterpartyName = cleanDesc,
                    message = distinctMsg,
                    variableSymbol = vs,
                    category = cat,
                    isNetted = isNetted,
                    nettingReason = nettingReason,
                    originalCurrency = origCurr,
                    originalAmount = origAmt,
                    exchangeRate = usedRate
                )
            )
        }
        return transactions
    }
}
