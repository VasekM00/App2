package com.example.util.statement

import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.CzechMerchantCatalog
import com.example.util.FxRateService
import com.example.util.ParsedBankTransaction
import com.example.util.StatementParseSummary
import java.util.Locale

internal object CsvStatementParser {

    private val DATE_TOKEN_IN_CELL_REGEX = Regex("""(\d{1,4}[./-]\s*\d{1,2}[./-]\s*\d{2,4})""")
    private val DIR_OUT_WORD_REGEX = Regex("""\bout\b""")
    private val DIR_IN_WORD_REGEX = Regex("""\bin\b""")
    private val SUMMARY_FOOTER_REGEX = Regex(
        """(?:^|\s)(?:pocatecni zustatek|konecny zustatek|ucetni zustatek|celkovy pocet|obraty celkem|celkem obraty|suma obratu|celkem za obdobi|soucet obratu|kreditni obrat|debetni obrat|obrat debet|obrat kredit)(?:\s|$|:)"""
    )
    private val SKIPPED_TX_STATES = setOf(
        "REVERTED", "FAILED", "DECLINED", "PENDING", "CANCELLED", "CANCELED", "REJECTED", "EXPIRED"
    )

    private data class HeaderRoles(
        val dateIdx: Int = -1,
        val amountIdx: Int = -1,
        val debitIdx: Int = -1,
        val creditIdx: Int = -1,
        val currencyIdx: Int = -1,
        val accountIdx: Int = -1,
        val bankCodeIdx: Int = -1,
        val nameIdx: Int = -1,
        val messageIdx: Int = -1,
        val vsIdx: Int = -1,
        val balanceIdx: Int = -1,
        val balanceHeaderCurrency: String? = null,
        val directionIdx: Int = -1,
        val stateIdx: Int = -1
    )

    private fun extractHeaderCurrency(header: String?): String? {
        if (header.isNullOrBlank()) return null
        return FxRateService.detectCurrency(header)
    }

    private fun resolveBalanceAmount(
        balCell: String,
        balanceHeaderCurrency: String?,
        rowCurrencyToken: String?,
        bankType: BankType
    ): Double {
        val explicitInCell = FxRateService.detectCurrency(balCell)
        val effectiveCurrency = when {
            explicitInCell != null -> explicitInCell
            balanceHeaderCurrency != null -> balanceHeaderCurrency
            bankType == BankType.REVOLUT || bankType == BankType.WISE -> rowCurrencyToken
            else -> "CZK"
        }
        return CzechValueParsers.parseCzechAmount(balCell, explicitCurrency = effectiveCurrency)
    }

    private fun inferHeaderRoles(cells: List<String>): HeaderRoles {
        fun find(vararg predicates: (String) -> Boolean): Int {
            for (pred in predicates) {
                val idx = cells.indexOfFirst { cell -> cell.isNotBlank() && pred(cell) }
                if (idx >= 0) return idx
            }
            return -1
        }

        // Prefer completed/booking date over started/due date
        val dateIdx = find(
            { it.contains("completed") && it.contains("date") },
            { it.contains("datum") && (it.contains("zauct") || it.contains("proved")) },
            { it.contains("datum") },
            { it.contains("date") && !it.contains("started") },
            { it.contains("date") },
            { it.contains("valuta") },
            { it.contains("proved") },
            { it.contains("zauc") }
        )
        val balanceIdx = find({ it.contains("zustatek") }, { it.contains("balance") })
        val balanceHeaderCurrency = balanceIdx.takeIf { it >= 0 }?.let { extractHeaderCurrency(cells[it]) }
        val vsIdx = find({ it.contains("variabil") }, { it == "vs" }, { it.contains("var symbol") }, { it.contains("varsymbol") })
        val currencyIdx = find(
            { it.contains("mena") && !it.contains("puvodni") && !it.contains("original") },
            { it.contains("currency") && !it.contains("original") && !it.contains("puvodni") }
        )
        val debitIdx = find(
            { it.contains("debet") && !it.contains("karta") && !it.contains("kartou") },
            { it.contains("debit") && !it.contains("card") },
            { it.contains("odeps") }, { it.contains("na vrub") }, { it.contains("vydaj") }, { it.contains("vydej") }
        )
        val creditIdx = find(
            { it.contains("kredit") && !it.contains("karta") && !it.contains("kartou") },
            { it.contains("credit") && !it.contains("card") },
            { it.contains("prips") }, { it.contains("ve prospech") }, { it.contains("vklad") },
            { it.contains("prijem") && !it.contains("prijemce") }
        )
        val amountIdx = find(
            {
                (it.contains("castka") || it.contains("objem") || it.contains("amount") ||
                    it.contains("suma") || it.contains("hodnota") || it.contains("cena")) &&
                    !it.contains("puvodni") && !it.contains("original") && !it.contains("poplat") && !it.contains("zustatek") &&
                    !it.contains("na vrub") && !it.contains("ve prospech") && !it.contains("debet") && !it.contains("debit") &&
                    !it.contains("kredit") && !it.contains("credit") && !it.contains("odeps") && !it.contains("prips")
            }
        )
        val directionIdx = find(
            { it == "smer" }, { it == "direction" }, { it == "typ" }, { it == "typ pohybu" }, { it == "typ operace" }, { it == "typ transakce" },
            { it == "druh" }, { it == "druh pohybu" }, { it == "d/c" }, { it == "dr/cr" }, { it == "debet/kredit" },
            { it == "d/k" }, { it == "dr/kr" }, { it.contains("d/k") }, { it.contains("dr/kr") }, { it.contains("d / k") }, { it.contains("d / c") },
            { it.contains("smer platby") || it.contains("smer pohybu") }
        )
        val stateIdx = find(
            { it == "state" },
            { it == "stav" },
            { it.contains("stav transakce") || it.contains("stav platby") }
        )
        val messageIdx = find(
            { it.contains("zprava") }, { it.contains("popis") }, { it.contains("message") },
            { it.contains("description") }, { it.contains("poznamka") }, { it.contains("ucel") },
            { it.contains("reference") }, { it.contains("detail") }, { it.contains("transakce") }, { it.contains("info") }
        )
        // Prioritize explicit counterparty name columns and never confuse "Účet příjemce" with nameIdx.
        val nameIdx = find(
            { it.contains("nazev") && (it.contains("proti") || it.contains("protistran")) },
            { it.contains("obchodnik") || it.contains("odesilatel") },
            {
                (it.contains("nazev") || it.contains("name") || it.contains("prijemce") || it.contains("platce") || it.contains("protistrana")) &&
                    !it.contains("vlastni") && !it.contains("majitel") && it != "nazev uctu" &&
                    !it.contains("cislo") && !it.contains("iban") && !it.contains("ucet")
            }
        )
        // Prioritize "Protiúčet"/"Protistrana" strictly ahead of generic "Účet příjemce".
        val accountIdx = find(
            {
                (it.contains("protiuc") || it.contains("protistran")) &&
                    !it.contains("vlastni") && !it.contains("majitel") && !it.contains("kod") && !it.contains("nazev") && !it.contains("jmeno") && !it.contains("bank")
            },
            {
                (it.contains("iban") || it.contains("account")) &&
                    !it.contains("vlastni") && !it.contains("majitel") && !it.contains("kod") && !it.contains("nazev") && !it.contains("jmeno")
            },
            {
                it.contains("ucet") && (it.contains("cislo") || it.contains("prijemce") || it.contains("platce") || it.contains("cilov") || it.contains("kam")) &&
                    !it.contains("vlastni") && !it.contains("majitel") && !it.contains("kod") && !it.contains("nazev") && !it.contains("jmeno")
            }
        )
        val bankCodeIdx = find(
            { it.contains("kod bank") || it.contains("banka protiuctu") || it.contains("kod ustavu") || (it.contains("bank") && it.contains("kod")) }
        )
        return HeaderRoles(
            dateIdx = dateIdx,
            amountIdx = amountIdx,
            debitIdx = debitIdx,
            creditIdx = creditIdx,
            currencyIdx = currencyIdx,
            accountIdx = accountIdx,
            bankCodeIdx = bankCodeIdx,
            nameIdx = nameIdx,
            messageIdx = messageIdx,
            vsIdx = vsIdx,
            balanceIdx = balanceIdx,
            balanceHeaderCurrency = balanceHeaderCurrency,
            directionIdx = directionIdx,
            stateIdx = stateIdx
        )
    }

    private fun extractDateToken(cell: String): String {
        val match = DATE_TOKEN_IN_CELL_REGEX.find(cell) ?: return ""
        return CzechValueParsers.normalizeDate(match.value.replace(" ", ""))
    }

    fun parseCsvStatement(
        lines: List<String>,
        bankType: BankType,
        knownFamilyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): StatementParseSummary {
        val compiledOverrides = CzechMerchantCatalog.compileOverrides(userOverrides)
        val headerBoundary = lines.indexOfFirst { line ->
            val f = StatementTextDecoder.foldHeader(line)
            (f.contains("datum") || f.contains("date")) &&
                (f.contains("castka") || f.contains("objem") || f.contains("amount") || f.contains("debet") || f.contains("completed"))
        }.let { if (it > 0) it else minOf(lines.size, 25) }
        val bankCode = CrossStatementReconciliationEngine.bankCodeForBankName(bankType.name)
        val preambleAccountKey = CzechValueParsers.extractOwnStatementAccountKey(
            lines = lines.take(headerBoundary),
            bankCode = bankCode,
            limitLines = headerBoundary
        )

        fun attachOwnAccountKey(summary: StatementParseSummary): StatementParseSummary {
            if (summary.accountKey.isNotBlank() || preambleAccountKey.isBlank()) return summary
            return summary.copy(
                accountKey = preambleAccountKey,
                transactions = summary.transactions.map {
                    if (it.accountKey.isBlank()) it.copy(accountKey = preambleAccountKey) else it
                }
            )
        }

        val legacy = when (bankType) {
            BankType.MONETA -> parseMonetaCsv(lines, knownFamilyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
            BankType.CSOB -> parseCsobCsv(lines, knownFamilyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
            BankType.MBANK -> parseMbankCsv(lines, knownFamilyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
            else -> null
        }
        if (legacy != null && legacy.transactions.isNotEmpty()) return attachOwnAccountKey(legacy)

        val universal = parseUniversalCsv(lines, bankType, knownFamilyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
        if (universal.transactions.isNotEmpty()) return attachOwnAccountKey(universal)
        return attachOwnAccountKey(
            legacy ?: parseGenericCsv(lines, knownFamilyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
        )
    }

    private fun parseMonetaCsv(
        lines: List<String>,
        familyAccounts: Set<String>,
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): StatementParseSummary {
        val delimiter = StatementTextDecoder.detectCsvDelimiter(lines)
        var headerIdx = -1
        for (i in lines.indices) {
            val lFolded = StatementTextDecoder.foldHeader(lines[i])
            if (lFolded.contains("datum") && (lFolded.contains("castka") || lFolded.contains("objem"))) {
                headerIdx = i
                break
            }
        }
        if (headerIdx == -1) return TransactionCategorizer.emptySummary(BankType.MONETA)

        val headers = StatementTextDecoder.splitLine(lines[headerIdx], delimiter).map { StatementTextDecoder.foldHeader(it) }
        val roles = inferHeaderRoles(headers)
        if (roles.debitIdx >= 0 || roles.creditIdx >= 0 || roles.directionIdx >= 0 || roles.stateIdx >= 0) {
            return parseUniversalCsv(lines, BankType.MONETA, familyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
        }

        val dateIdx = headers.indexOfFirst { it.contains("datum") }
        val amountIdx = headers.indexOfFirst { h ->
            (h.contains("castka") || h.contains("objem") || h.contains("amount")) &&
                !h.contains("puvodni") && !h.contains("original")
        }
        if (dateIdx < 0 || amountIdx < 0) return TransactionCategorizer.emptySummary(BankType.MONETA)

        val accIdx = headers.indexOfFirst { h ->
            (h.contains("protiuc") || h.contains("protiucet")) &&
                !h.contains("nazev") && !h.contains("jmeno") && !h.contains("bank")
        }
        val bankCodeIdx = headers.indexOfFirst { it.contains("kod bank") || it.contains("banka protiuctu") }
        val nameIdx = run {
            val protiName = headers.indexOfFirst { it.contains("nazev") && (it.contains("proti") || it.contains("prijemce") || it.contains("platce")) }
            if (protiName >= 0) protiName
            else headers.indexOfFirst { it.contains("nazev") && !it.contains("nazev uctu") }
        }
        val msgIdx = headers.indexOfFirst { h ->
            h.contains("zprava") || h.contains("poznamka") || h.contains("popis")
        }
        val vsIdx = headers.indexOfFirst { it.contains("variabilni") || it == "vs" }
        val balanceIdx = headers.indexOfFirst { it.contains("zustatek") || it.contains("balance") }
        val balanceHeaderCurrency = balanceIdx.takeIf { it >= 0 }?.let { extractHeaderCurrency(headers[it]) }
        val currencyIdx = headers.indexOfFirst { h ->
            (h.contains("mena") || h.contains("currency")) && !h.contains("puvodni") && !h.contains("original")
        }

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        val balanceWithDates = mutableListOf<Pair<String, Double>>()

        for (i in (headerIdx + 1) until lines.size) {
            val tokens = StatementTextDecoder.splitLine(lines[i], delimiter)
            if (tokens.size <= maxOf(dateIdx, amountIdx)) continue
            val dateStr = CzechValueParsers.normalizeDate(tokens.getOrNull(dateIdx) ?: "")

            val currencyToken = if (currencyIdx >= 0) tokens.getOrNull(currencyIdx)?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() } else null
            val detectedCurrency = currencyToken ?: FxRateService.detectCurrency(tokens.getOrNull(amountIdx) ?: "")
            val details = CzechValueParsers.parseCzechAmountDetailed(tokens.getOrNull(amountIdx) ?: "", explicitCurrency = detectedCurrency)
            val amount = details.amountInCzk
            val origCurr = if (details.originalCurrency != "CZK") details.originalCurrency else null
            val origAmt = if (details.originalCurrency != "CZK") details.originalAmount else null
            val usedRate = if (details.originalCurrency != "CZK") details.exchangeRate else null

            val rawAcc = tokens.getOrNull(accIdx)?.trim() ?: ""
            val rawBank = if (bankCodeIdx >= 0) tokens.getOrNull(bankCodeIdx)?.trim()?.take(4) ?: "" else ""
            val counterpartyAcc = if (rawAcc.isNotBlank() && rawBank.isNotBlank() && !rawAcc.contains("/")) "$rawAcc/$rawBank" else rawAcc
            val counterpartyName = tokens.getOrNull(nameIdx)?.trim() ?: ""
            val message = tokens.getOrNull(msgIdx)?.trim() ?: ""
            val vs = tokens.getOrNull(vsIdx)?.trim() ?: ""

            if (balanceIdx >= 0 && balanceIdx < tokens.size && dateStr.isNotBlank()) {
                val balCell = tokens[balanceIdx]
                if (balCell.any { it.isDigit() }) {
                    val bal = resolveBalanceAmount(balCell, balanceHeaderCurrency, currencyToken, BankType.MONETA)
                    if (bal.isFinite()) balanceWithDates.add(dateStr to bal)
                }
            }

            val rowContext = StatementTextDecoder.foldHeader("$counterpartyName $message")
            if (SUMMARY_FOOTER_REGEX.containsMatchIn(rowContext)) continue

            if (dateStr.isNotBlank() && amount.isFinite() && amount != 0.0) {
                val cat = TransactionCategorizer.categorizeTransaction(
                    amount = amount,
                    counterpartyAcc = counterpartyAcc,
                    counterpartyName = counterpartyName,
                    message = message,
                    vs = vs,
                    familyAccounts = familyAccounts,
                    rawContext = lines[i],
                    householdNames = householdNames,
                    knownParentalGiftAccounts = knownParentalGiftAccounts,
                    compiledOverrides = compiledOverrides
                )
                val cleanName = PaymentDescriptionCleaner.cleanPaymentDescription(counterpartyName, householdNames)
                val cleanMsg = PaymentDescriptionCleaner.cleanPaymentDescription(message, householdNames)
                val distinctMsg = if (cleanMsg.isNotBlank() && !cleanMsg.equals(cleanName, ignoreCase = true)) cleanMsg else ""
                val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
                val nettingReason = TransactionCategorizer.determineNettingReason(cat, cleanName, distinctMsg)
                rawTransactions.add(
                    ParsedBankTransaction(
                        date = dateStr,
                        amount = amount,
                        counterpartyAccount = counterpartyAcc,
                        counterpartyName = cleanName,
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
        }
        val closingBalance = CzechValueParsers.resolveClosingBalance(balanceWithDates, rawTransactions)
        return TransactionCategorizer.buildSummary(BankType.MONETA, rawTransactions, closingBalance)
    }

    private fun parseCsobCsv(
        lines: List<String>,
        familyAccounts: Set<String>,
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): StatementParseSummary {
        val delimiter = StatementTextDecoder.detectCsvDelimiter(lines)
        var headerIdx = -1
        for (i in lines.indices) {
            val lFolded = StatementTextDecoder.foldHeader(lines[i])
            if (lFolded.contains("datum") && (lFolded.contains("castka") || lFolded.contains("objem"))) {
                headerIdx = i
                break
            }
        }
        if (headerIdx == -1) return TransactionCategorizer.emptySummary(BankType.CSOB)

        val headers = StatementTextDecoder.splitLine(lines[headerIdx], delimiter).map { StatementTextDecoder.foldHeader(it) }
        val roles = inferHeaderRoles(headers)
        if (roles.debitIdx >= 0 || roles.creditIdx >= 0 || roles.directionIdx >= 0 || roles.stateIdx >= 0) {
            return parseUniversalCsv(lines, BankType.CSOB, familyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
        }

        val dateIdx = run {
            val zauctovani = headers.indexOfFirst { it.contains("zauctovani") || it.contains("zaucto") }
            if (zauctovani >= 0) zauctovani else headers.indexOfFirst { it.contains("datum") }
        }
        val amountIdx = headers.indexOfFirst { h ->
            (h.contains("castka") || h.contains("objem") || h.contains("amount")) &&
                !h.contains("original") && !h.contains("puvodni")
        }
        if (dateIdx < 0 || amountIdx < 0) return TransactionCategorizer.emptySummary(BankType.CSOB)

        val accIdx = run {
            val protiIdx = headers.indexOfFirst { h ->
                (h.contains("protiuc") || (h.contains("protistran") && (h.contains("ucet") || h.contains("iban")))) &&
                    !h.contains("nazev") && !h.contains("jmeno") && !h.contains("bank")
            }
            if (protiIdx >= 0) protiIdx
            else headers.indexOfFirst { h ->
                (h.contains("iban") && h.contains("cislo")) &&
                    !h.contains("nazev") && !h.contains("jmeno")
            }
        }
        val nameIdx = run {
            val nazevIdx = headers.indexOfFirst { it.contains("nazev") && (it.contains("protistran") || it.contains("proti")) }
            if (nazevIdx >= 0) nazevIdx
            else headers.indexOfFirst { h ->
                h.contains("odesilatel") || (h.contains("nazev") && !h.contains("uctu"))
            }
        }
        val msgIdx = run {
            val podrob = headers.indexOfFirst { it.contains("popis") && it.contains("transakce") }
            if (podrob >= 0) podrob
            else headers.indexOfFirst { h ->
                h.contains("zprava") || h.contains("informace") || h.contains("popis")
            }
        }
        val vsIdx = headers.indexOfFirst { it.contains("variabilni") || it == "vs" }
        val balanceIdx = headers.indexOfFirst { it.contains("zustatek") || it.contains("balance") }
        val balanceHeaderCurrency = balanceIdx.takeIf { it >= 0 }?.let { extractHeaderCurrency(headers[it]) }
        val currencyIdx = headers.indexOfFirst { h ->
            (h.contains("mena") || h.contains("currency")) && !h.contains("puvodni") && !h.contains("original")
        }

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        val balanceWithDates = mutableListOf<Pair<String, Double>>()

        for (i in (headerIdx + 1) until lines.size) {
            val tokens = StatementTextDecoder.splitLine(lines[i], delimiter)
            if (tokens.size <= maxOf(dateIdx, amountIdx)) continue
            val dateStr = CzechValueParsers.normalizeDate(tokens.getOrNull(dateIdx) ?: "")

            val currencyToken = if (currencyIdx >= 0) tokens.getOrNull(currencyIdx)?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() } else null
            val detectedCurrency = currencyToken ?: FxRateService.detectCurrency(tokens.getOrNull(amountIdx) ?: "")
            val details = CzechValueParsers.parseCzechAmountDetailed(tokens.getOrNull(amountIdx) ?: "", explicitCurrency = detectedCurrency)
            val amount = details.amountInCzk
            val origCurr = if (details.originalCurrency != "CZK") details.originalCurrency else null
            val origAmt = if (details.originalCurrency != "CZK") details.originalAmount else null
            val usedRate = if (details.originalCurrency != "CZK") details.exchangeRate else null

            val counterpartyAcc = tokens.getOrNull(accIdx)?.trim() ?: ""
            val counterpartyName = tokens.getOrNull(nameIdx)?.trim() ?: ""
            val message = tokens.getOrNull(msgIdx)?.trim() ?: ""
            val vs = tokens.getOrNull(vsIdx)?.trim() ?: ""

            if (balanceIdx >= 0 && balanceIdx < tokens.size && dateStr.isNotBlank()) {
                val balCell = tokens[balanceIdx]
                if (balCell.any { it.isDigit() }) {
                    val bal = resolveBalanceAmount(balCell, balanceHeaderCurrency, currencyToken, BankType.CSOB)
                    if (bal.isFinite()) balanceWithDates.add(dateStr to bal)
                }
            }

            val rowContext = StatementTextDecoder.foldHeader("$counterpartyName $message")
            if (SUMMARY_FOOTER_REGEX.containsMatchIn(rowContext)) continue

            if (dateStr.isNotBlank() && amount.isFinite() && amount != 0.0) {
                val cat = TransactionCategorizer.categorizeTransaction(
                    amount = amount,
                    counterpartyAcc = counterpartyAcc,
                    counterpartyName = counterpartyName,
                    message = message,
                    vs = vs,
                    familyAccounts = familyAccounts,
                    rawContext = lines[i],
                    householdNames = householdNames,
                    knownParentalGiftAccounts = knownParentalGiftAccounts,
                    compiledOverrides = compiledOverrides
                )
                val cleanName = PaymentDescriptionCleaner.cleanPaymentDescription(counterpartyName, householdNames)
                val cleanMsg = PaymentDescriptionCleaner.cleanPaymentDescription(message, householdNames)
                val distinctMsg = if (cleanMsg.isNotBlank() && !cleanMsg.equals(cleanName, ignoreCase = true)) cleanMsg else ""
                val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
                val nettingReason = TransactionCategorizer.determineNettingReason(cat, cleanName, distinctMsg)
                rawTransactions.add(
                    ParsedBankTransaction(
                        date = dateStr,
                        amount = amount,
                        counterpartyAccount = counterpartyAcc,
                        counterpartyName = cleanName,
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
        }
        val closingBalance = CzechValueParsers.resolveClosingBalance(balanceWithDates, rawTransactions)
        return TransactionCategorizer.buildSummary(BankType.CSOB, rawTransactions, closingBalance)
    }

    private fun parseMbankCsv(
        lines: List<String>,
        familyAccounts: Set<String>,
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): StatementParseSummary {
        val delimiter = StatementTextDecoder.detectCsvDelimiter(lines)
        var headerIdx = -1
        for (i in lines.indices) {
            val lFolded = StatementTextDecoder.foldHeader(lines[i].replace("#", ""))
            if (lFolded.contains("datum") && (lFolded.contains("castka") || lFolded.contains("popis"))) {
                headerIdx = i
                break
            }
        }
        if (headerIdx == -1) return TransactionCategorizer.emptySummary(BankType.MBANK)

        val headers = StatementTextDecoder.splitLine(lines[headerIdx].replace("#", ""), delimiter).map { StatementTextDecoder.foldHeader(it) }
        val roles = inferHeaderRoles(headers)
        if (roles.debitIdx >= 0 || roles.creditIdx >= 0 || roles.directionIdx >= 0 || roles.stateIdx >= 0) {
            return parseUniversalCsv(lines, BankType.MBANK, familyAccounts, compiledOverrides, householdNames, knownParentalGiftAccounts)
        }

        val dateIdx = headers.indexOfFirst { it.contains("datum") }
        val amountIdx = headers.indexOfFirst { h ->
            (h.contains("castka") || h.contains("objem") || h.contains("amount")) &&
                !h.contains("zustatek") && !h.contains("puvodni") && !h.contains("original")
        }
        if (dateIdx < 0 || amountIdx < 0) return TransactionCategorizer.emptySummary(BankType.MBANK)
        val descIdx = headers.indexOfFirst { it.contains("popis") }
        val accIdx = headers.indexOfFirst { h ->
            (h.contains("protiuc") || h.contains("protistr") || h.contains("iban") ||
                (h.contains("ucet") && (h.contains("cislo") || h.contains("prijemce") || h.contains("platce")))) &&
                !h.contains("nazev") && !h.contains("jmeno") && !h.contains("vlastni") && !h.contains("majitel")
        }
        val nameIdx = headers.indexOfFirst { h ->
            (h.contains("nazev") || h.contains("name") || h.contains("platce") ||
                h.contains("prijemce") || h.contains("protistr")) &&
                !h.contains("cislo") && !h.contains("iban") && !h.contains("vlastni") && !h.contains("majitel") && !h.contains("ucet")
        }
        val balanceIdx = headers.indexOfFirst { it.contains("zustatek") || it.contains("balance") }
        val balanceHeaderCurrency = balanceIdx.takeIf { it >= 0 }?.let { extractHeaderCurrency(headers[it]) }
        val currencyIdx = headers.indexOfFirst { h ->
            (h.contains("mena") || h.contains("currency")) && !h.contains("puvodni") && !h.contains("original")
        }
        val vsIdx = headers.indexOfFirst { it.contains("variabilni") || it == "vs" }

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        val balanceWithDates = mutableListOf<Pair<String, Double>>()

        for (i in (headerIdx + 1) until lines.size) {
            val tokens = StatementTextDecoder.splitLine(lines[i], delimiter)
            if (tokens.size <= maxOf(dateIdx, amountIdx)) continue
            val dateStr = CzechValueParsers.normalizeDate(tokens.getOrNull(dateIdx) ?: "")
            val currencyToken = if (currencyIdx >= 0) tokens.getOrNull(currencyIdx)?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() } else null
            val detectedCurrency = currencyToken ?: FxRateService.detectCurrency(tokens.getOrNull(amountIdx) ?: "")
            val details = CzechValueParsers.parseCzechAmountDetailed(tokens.getOrNull(amountIdx) ?: "", explicitCurrency = detectedCurrency)
            val amount = details.amountInCzk
            val origCurr = if (details.originalCurrency != "CZK") details.originalCurrency else null
            val origAmt = if (details.originalCurrency != "CZK") details.originalAmount else null
            val usedRate = if (details.originalCurrency != "CZK") details.exchangeRate else null

            val counterpartyAcc = tokens.getOrNull(accIdx)?.trim() ?: ""
            val counterpartyName = tokens.getOrNull(nameIdx)?.trim() ?: ""
            val message = tokens.getOrNull(descIdx)?.trim() ?: ""
            val vs = tokens.getOrNull(vsIdx)?.trim() ?: ""

            if (balanceIdx != -1 && balanceIdx < tokens.size && dateStr.isNotBlank()) {
                val balCell = tokens[balanceIdx]
                if (balCell.any { it.isDigit() }) {
                    val bal = resolveBalanceAmount(balCell, balanceHeaderCurrency, currencyToken, BankType.MBANK)
                    if (bal.isFinite()) balanceWithDates.add(dateStr to bal)
                }
            }

            val rowContext = StatementTextDecoder.foldHeader("$counterpartyName $message")
            if (SUMMARY_FOOTER_REGEX.containsMatchIn(rowContext)) continue

            if (dateStr.isNotBlank() && amount.isFinite() && amount != 0.0) {
                val cat = TransactionCategorizer.categorizeTransaction(
                    amount = amount,
                    counterpartyAcc = counterpartyAcc,
                    counterpartyName = counterpartyName,
                    message = message,
                    vs = vs,
                    familyAccounts = familyAccounts,
                    rawContext = lines[i],
                    householdNames = householdNames,
                    knownParentalGiftAccounts = knownParentalGiftAccounts,
                    compiledOverrides = compiledOverrides
                )
                val cleanName = PaymentDescriptionCleaner.cleanPaymentDescription(counterpartyName, householdNames)
                val cleanMsg = PaymentDescriptionCleaner.cleanPaymentDescription(message, householdNames)
                val distinctMsg = if (cleanMsg.isNotBlank() && !cleanMsg.equals(cleanName, ignoreCase = true)) cleanMsg else ""
                val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
                val nettingReason = TransactionCategorizer.determineNettingReason(cat, cleanName, distinctMsg)
                rawTransactions.add(
                    ParsedBankTransaction(
                        date = dateStr,
                        amount = amount,
                        counterpartyAccount = counterpartyAcc,
                        counterpartyName = cleanName,
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
        }
        val closingBalance = CzechValueParsers.resolveClosingBalance(balanceWithDates, rawTransactions)
        return TransactionCategorizer.buildSummary(BankType.MBANK, rawTransactions, closingBalance)
    }

    private fun parseUniversalCsv(
        lines: List<String>,
        bankType: BankType,
        familyAccounts: Set<String>,
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): StatementParseSummary {
        val delimiter = StatementTextDecoder.detectCsvDelimiter(lines)

        var headerIdx = -1
        var roles = HeaderRoles()
        for (i in 0 until minOf(lines.size, 40)) {
            val cells = StatementTextDecoder.splitLine(lines[i], delimiter).map { StatementTextDecoder.foldHeader(it) }
            if (cells.size < 2) continue
            val candidate = inferHeaderRoles(cells)
            val hasAmount = candidate.amountIdx >= 0 || candidate.debitIdx >= 0 || candidate.creditIdx >= 0
            if (candidate.dateIdx >= 0 && hasAmount) {
                headerIdx = i
                roles = candidate
                break
            }
        }
        if (headerIdx < 0) return TransactionCategorizer.emptySummary(bankType)

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        val balanceWithDates = mutableListOf<Pair<String, Double>>()

        for (i in (headerIdx + 1) until lines.size) {
            val line = lines[i]
            val tokens = StatementTextDecoder.splitLine(line, delimiter)
            if (tokens.isEmpty()) continue

            if (roles.stateIdx >= 0) {
                val txState = tokens.getOrNull(roles.stateIdx)?.trim()?.uppercase(Locale.ROOT) ?: ""
                if (txState in SKIPPED_TX_STATES) continue
            }

            val dateStr = extractDateToken(tokens.getOrNull(roles.dateIdx) ?: "")
            if (dateStr.isBlank()) continue

            val currencyToken = if (roles.currencyIdx >= 0) {
                tokens.getOrNull(roles.currencyIdx)?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
            } else null
            val detectedCurrency = currencyToken
                ?: FxRateService.detectCurrency(tokens.getOrNull(roles.amountIdx) ?: "")
                ?: (if (roles.debitIdx >= 0) FxRateService.detectCurrency(tokens.getOrNull(roles.debitIdx) ?: "") else null)
                ?: (if (roles.creditIdx >= 0) FxRateService.detectCurrency(tokens.getOrNull(roles.creditIdx) ?: "") else null)

            var amount = 0.0
            var origAmount: Double? = null
            var origCurr: String? = null
            var usedRate: Double? = null
            val rawAmountCell = if (roles.amountIdx >= 0) tokens.getOrNull(roles.amountIdx) ?: "" else ""

            if (roles.debitIdx >= 0 || roles.creditIdx >= 0) {
                val debitDetails = if (roles.debitIdx >= 0) {
                    CzechValueParsers.parseCzechAmountDetailed(tokens.getOrNull(roles.debitIdx) ?: "", explicitCurrency = detectedCurrency)
                } else null
                val creditDetails = if (roles.creditIdx >= 0) {
                    CzechValueParsers.parseCzechAmountDetailed(tokens.getOrNull(roles.creditIdx) ?: "", explicitCurrency = detectedCurrency)
                } else null
                val debit = debitDetails?.amountInCzk?.takeIf { it.isFinite() }?.let { kotlin.math.abs(it) } ?: 0.0
                val credit = creditDetails?.amountInCzk?.takeIf { it.isFinite() }?.let { kotlin.math.abs(it) } ?: 0.0
                if (debit > 0.0 || credit > 0.0) {
                    amount = credit - debit
                    val activeDetails = if (credit > 0.0) creditDetails else debitDetails
                    if (activeDetails != null && activeDetails.originalCurrency != "CZK") {
                        origCurr = activeDetails.originalCurrency
                        origAmount = if (amount < 0) -kotlin.math.abs(activeDetails.originalAmount) else kotlin.math.abs(activeDetails.originalAmount)
                        usedRate = activeDetails.exchangeRate
                    }
                }
            }
            if (amount == 0.0 && roles.amountIdx >= 0) {
                val details = CzechValueParsers.parseCzechAmountDetailed(rawAmountCell, explicitCurrency = detectedCurrency)
                amount = details.amountInCzk
                if (details.originalCurrency != "CZK") {
                    origCurr = details.originalCurrency
                    origAmount = details.originalAmount
                    usedRate = details.exchangeRate
                }
            }
            if (amount.isFinite() && amount != 0.0 && roles.directionIdx >= 0) {
                val direction = StatementTextDecoder.foldHeader(tokens.getOrNull(roles.directionIdx) ?: "")
                val explicitPositive = rawAmountCell.trimStart().startsWith("+")
                val isRefundDir = direction.contains("vracen") || direction.contains("vratk") ||
                    direction.contains("refund") || direction.contains("storn")
                val isCardPayment = !isRefundDir && (direction.contains("karta") || direction.contains("kartou"))
                val isDebitSign = direction == "d" || direction == "dr" || direction.startsWith("d ") || direction.endsWith(" d")
                val isCreditSign = direction == "k" || direction == "c" || direction == "kr" || direction == "cr" ||
                    direction.startsWith("k ") || direction.endsWith(" k") || direction.startsWith("c ") || direction.endsWith(" c")
                val outflow = isDebitSign || (isCardPayment && !explicitPositive) || direction.contains("odchoz") ||
                    direction.contains("debet") || direction.contains("debit") || direction.contains("vydaj") ||
                    direction.contains("vydej") || direction.contains("odeps") || direction.contains("na vrub") ||
                    DIR_OUT_WORD_REGEX.containsMatchIn(direction)
                val inflow = isCreditSign || isRefundDir || direction.contains("prichoz") ||
                    (!isCardPayment && (direction.contains("kredit") || direction.contains("credit"))) ||
                    direction.contains("prips") || direction.contains("prijem") || direction.contains("vklad") ||
                    DIR_IN_WORD_REGEX.containsMatchIn(direction)
                if (outflow && !inflow && amount > 0) {
                    amount = -amount
                    if (origAmount != null && origAmount > 0) origAmount = -origAmount
                }
                if (inflow && amount < 0) {
                    amount = -amount
                    if (origAmount != null && origAmount < 0) origAmount = -origAmount
                }
            }
            if (!amount.isFinite() || amount == 0.0) continue

            val rawCpAcc = tokens.getOrNull(roles.accountIdx)?.trim() ?: ""
            val rawBankCode = if (roles.bankCodeIdx >= 0) tokens.getOrNull(roles.bankCodeIdx)?.trim()?.take(4) ?: "" else ""
            val counterpartyAcc = if (rawCpAcc.isNotBlank() && rawBankCode.isNotBlank() && !rawCpAcc.contains("/")) {
                "$rawCpAcc/$rawBankCode"
            } else {
                rawCpAcc
            }
            val counterpartyName = tokens.getOrNull(roles.nameIdx)?.trim() ?: ""
            val message = tokens.getOrNull(roles.messageIdx)?.trim() ?: ""
            val vs = tokens.getOrNull(roles.vsIdx)?.trim() ?: ""

            val context = StatementTextDecoder.foldHeader("$counterpartyName $message")
            if (SUMMARY_FOOTER_REGEX.containsMatchIn(context)) continue

            if (roles.balanceIdx >= 0) {
                val balCell = tokens.getOrNull(roles.balanceIdx) ?: ""
                if (balCell.any { it.isDigit() }) {
                    val balance = resolveBalanceAmount(balCell, roles.balanceHeaderCurrency, currencyToken, bankType)
                    if (balance.isFinite()) balanceWithDates.add(dateStr to balance)
                }
            }

            val category = TransactionCategorizer.categorizeTransaction(
                amount = amount,
                counterpartyAcc = counterpartyAcc,
                counterpartyName = counterpartyName,
                message = message,
                vs = vs,
                familyAccounts = familyAccounts,
                rawContext = line,
                householdNames = householdNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts,
                compiledOverrides = compiledOverrides
            )
            val cleanName = PaymentDescriptionCleaner.cleanPaymentDescription(counterpartyName, householdNames)
            val cleanMsg = PaymentDescriptionCleaner.cleanPaymentDescription(message, householdNames)
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
                    nettingReason = TransactionCategorizer.determineNettingReason(category, cleanName, distinctMsg),
                    bankType = bankType,
                    originalCurrency = origCurr,
                    originalAmount = origAmount,
                    exchangeRate = usedRate
                )
            )
        }

        val closingBalance = CzechValueParsers.resolveClosingBalance(balanceWithDates, rawTransactions)
        return TransactionCategorizer.buildSummary(bankType, rawTransactions, closingBalance)
    }

    private fun parseGenericCsv(
        lines: List<String>,
        familyAccounts: Set<String>,
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides,
        householdNames: Collection<String>,
        knownParentalGiftAccounts: Set<String>
    ): StatementParseSummary {
        val delimiter = StatementTextDecoder.detectCsvDelimiter(lines)
        var headerIdx = -1
        for (i in lines.indices) {
            val l = lines[i].lowercase(Locale.ROOT)
            if (l.contains("date") || l.contains("datum")) {
                headerIdx = i
                break
            }
        }
        if (headerIdx == -1) return TransactionCategorizer.emptySummary(BankType.GENERIC)

        val headers = StatementTextDecoder.splitLine(lines[headerIdx], delimiter).map { StatementTextDecoder.foldHeader(it) }
        val dateIdx = headers.indexOfFirst { it.contains("date") || it.contains("datum") }
        val amountIdx = headers.indexOfFirst { it.contains("amount") || it.contains("castka") }
        if (dateIdx < 0 || amountIdx < 0) return TransactionCategorizer.emptySummary(BankType.GENERIC)
        val msgIdx = headers.indexOfFirst { it.contains("description") || it.contains("message") || it.contains("popis") || it.contains("zprava") }

        val rawTransactions = mutableListOf<ParsedBankTransaction>()
        for (i in (headerIdx + 1) until lines.size) {
            val tokens = StatementTextDecoder.splitLine(lines[i], delimiter)
            if (tokens.size <= maxOf(dateIdx, amountIdx)) continue
            val dateStr = CzechValueParsers.normalizeDate(tokens.getOrNull(dateIdx) ?: "")
            val amount = CzechValueParsers.parseCzechAmount(tokens.getOrNull(amountIdx) ?: "")
            val message = tokens.getOrNull(msgIdx)?.trim() ?: ""

            if (dateStr.isNotBlank() && amount.isFinite() && amount != 0.0) {
                val cat = TransactionCategorizer.categorizeTransaction(
                    amount = amount,
                    counterpartyAcc = "",
                    counterpartyName = "",
                    message = message,
                    vs = "",
                    familyAccounts = familyAccounts,
                    rawContext = lines[i],
                    householdNames = householdNames,
                    knownParentalGiftAccounts = knownParentalGiftAccounts,
                    compiledOverrides = compiledOverrides
                )
                val cleanMsg = PaymentDescriptionCleaner.cleanPaymentDescription(message, householdNames)
                rawTransactions.add(
                    ParsedBankTransaction(
                        date = dateStr,
                        amount = amount,
                        message = cleanMsg,
                        category = cat,
                        isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER),
                        nettingReason = TransactionCategorizer.determineNettingReason(cat, "", cleanMsg)
                    )
                )
            }
        }
        return TransactionCategorizer.buildSummary(BankType.GENERIC, rawTransactions)
    }
}
