package com.example.util.statement

import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.CzechMerchantCatalog
import com.example.util.FxRateService
import com.example.util.ParsedBankTransaction
import com.example.util.StatementParseSummary
import java.util.Locale

internal object AboGpcParser {

    fun isAboFormat(lines: List<String>): Boolean {
        val nonBlank = lines.map { it.trim() }.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return false
        val first = nonBlank.first()
        if (first.startsWith("074") && first.length >= 40 && !first.contains(";") && !first.contains(",")) return true
        val aboCount = nonBlank.count { (it.startsWith("074") || it.startsWith("075")) && !it.contains(";") }
        return aboCount >= 1 && (first.startsWith("074") || first.startsWith("075"))
    }

    private fun parseAboDate(rawDate: String): String? {
        val clean = rawDate.trim()
        if (clean.length < 6 || !clean.substring(0, 6).all { it.isDigit() }) return null
        val d = clean.substring(0, 2).toIntOrNull() ?: return null
        val m = clean.substring(2, 4).toIntOrNull() ?: return null
        val y = clean.substring(4, 6).toIntOrNull() ?: return null
        val fullYear = if (y < 70) 2000 + y else 1900 + y
        return CzechValueParsers.safeIsoDate(fullYear, m, d)
    }

    private fun mapIso4217NumericCurrency(code: String): String {
        val trimmed = code.trim()
        return when (trimmed) {
            "", "0000", "0203" -> "CZK"
            "0978" -> "EUR"
            "0840" -> "USD"
            "0826" -> "GBP"
            "0985" -> "PLN"
            "0756" -> "CHF"
            "0348" -> "HUF"
            "0392" -> "JPY"
            "0752" -> "SEK"
            "0578" -> "NOK"
            "0208" -> "DKK"
            "0124" -> "CAD"
            "0036" -> "AUD"
            else -> if (trimmed.length == 4 && trimmed.all(Char::isDigit)) "ISO_$trimmed" else trimmed.uppercase(Locale.ROOT)
        }
    }

    fun parseAboGpc(
        lines: List<String>,
        familyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): StatementParseSummary {
        var detectedBank = BankType.GENERIC
        var rawOwnAcc16 = ""
        var headerExplicitAcc = ""
        var statementYm: String? = null
        var closingBalance: Double? = null
        val unknownCurrencies = linkedSetOf<String>()
        val compiledOverrides = CzechMerchantCatalog.compileOverrides(userOverrides)

        val transactions = mutableListOf<ParsedBankTransaction>()
        var pendingTransaction: ParsedBankTransaction? = null
        var pendingRawContext = ""

        for (rawLine in lines) {
            val line = rawLine.trimEnd('\r', '\n')
            if (line.isBlank()) continue

            when {
                line.startsWith("074") -> {
                    if (line.length >= 19) {
                        val accField = line.substring(3, 19)
                        if (accField.all { it.isDigit() } && rawOwnAcc16.isEmpty()) {
                            rawOwnAcc16 = accField
                        }
                    }
                    if (headerExplicitAcc.isEmpty()) {
                        headerExplicitAcc = CzechValueParsers.extractAccount(line).let { CzechValueParsers.canonicalAccountKey(it) }
                    }
                    // ČBA ABO/GPC 074 standard: closing balance [60, 74), sign at [74]
                    if (line.length >= 75) {
                        val closeAmtRaw = line.substring(60, 74).trim().toLongOrNull()
                        if (closeAmtRaw != null) {
                            val closeAmt = closeAmtRaw / 100.0
                            val closeSign = line[74]
                            closingBalance = if (closeSign == '-') -closeAmt else closeAmt
                        }
                    }
                    // ČBA ABO/GPC 074 standard: statement date [108, 114)
                    if (line.length >= 114) {
                        val stDateStr = line.substring(108, 114).trim()
                        val fullDate = parseAboDate(stDateStr)
                        if (fullDate != null) {
                            statementYm = fullDate.substring(0, 7)
                        }
                    }

                    val headerFolded = StatementTextDecoder.foldHeader(line)
                    when {
                        headerFolded.contains("moneta") || line.contains("/0600") -> detectedBank = BankType.MONETA
                        headerFolded.contains("csob") || line.contains("/0300") -> detectedBank = BankType.CSOB
                        headerFolded.contains("mbank") || line.contains("/6210") -> detectedBank = BankType.MBANK
                        headerFolded.contains("sporitelna") || line.contains("/0800") -> detectedBank = BankType.CESKA_SPORITELNA
                        headerFolded.contains("komercni") || line.contains("/0100") -> detectedBank = BankType.KOMERCNI_BANKA
                        headerFolded.contains("fio") || line.contains("/2010") -> detectedBank = BankType.FIO
                        headerFolded.contains("raiffeisen") || line.contains("/5500") -> detectedBank = BankType.RAIFFEISENBANK
                        headerFolded.contains("air") || line.contains("/3030") -> detectedBank = BankType.AIR_BANK
                    }
                }

                line.startsWith("075") -> {
                    pendingTransaction?.let { transactions.add(it) }
                    pendingTransaction = null
                    pendingRawContext = line

                    if (line.length >= 61) {
                        val rawCpField = line.substring(minOf(19, line.length), minOf(35, line.length)).trim()
                        val rawCpAcc = if (rawCpField.length == 16 && rawCpField.all { it.isDigit() }) {
                            val prefix = rawCpField.substring(0, 6).trimStart('0')
                            val accNum = rawCpField.substring(6, 16).trimStart('0')
                            when {
                                prefix.isNotEmpty() && accNum.isNotEmpty() -> "$prefix-$accNum"
                                accNum.isNotEmpty() -> accNum
                                else -> ""
                            }
                        } else {
                            rawCpField.trimStart('0')
                        }
                        val rawAmtStr = line.substring(minOf(48, line.length), minOf(60, line.length)).trim()
                        val rawAmt = (rawAmtStr.toLongOrNull() ?: 0L) / 100.0
                        val code = line.getOrNull(60) ?: '1'
                        val isDebit = (code == '1' || code == '5')
                        val signedNativeAmt = if (isDebit) -rawAmt else rawAmt

                        val rawCurrencyCode = if (line.length >= 122) {
                            line.substring(118, 122)
                        } else {
                            ""
                        }
                        val currency = mapIso4217NumericCurrency(rawCurrencyCode)
                        var txAmount = signedNativeAmt
                        var origCurr: String? = null
                        var origAmt: Double? = null
                        var usedRate: Double? = null
                        if (currency != "CZK") {
                            val rate = FxRateService.getRateOrNull(currency)
                            if (rate == null || !rate.isFinite() || rate <= 0.0) {
                                unknownCurrencies.add(currency)
                                continue
                            }
                            txAmount = signedNativeAmt * rate
                            origCurr = currency
                            origAmt = signedNativeAmt
                            usedRate = rate
                        }

                        val vs = if (line.length >= 71) {
                            line.substring(61, minOf(71, line.length)).trim().trimStart('0')
                        } else ""

                        // ČBA ABO/GPC 075 standard: counterparty bank code at [73, 77)
                        val bankCode = if (line.length >= 77) {
                            line.substring(73, 77).trim().takeIf {
                                it.length == 4 && it.all(Char::isDigit) && it != "0000"
                            } ?: ""
                        } else ""

                        val counterpartyAcc = when {
                            rawCpAcc.isNotBlank() && bankCode.isNotBlank() -> "$rawCpAcc/$bankCode"
                            rawCpAcc.isNotBlank() -> rawCpAcc
                            else -> ""
                        }

                        // ČBA ABO/GPC 075 standard: counterparty note at [97, 117)
                        val rawNote = if (line.length >= 117) {
                            line.substring(97, 117).trim()
                        } else if (line.length > 97) {
                            line.substring(97).trim()
                        } else ""

                        // ČBA ABO/GPC 075 standard: posting date at [122, 128), fallback to value date [91, 97)
                        val postingDate = when {
                            line.length >= 128 && parseAboDate(line.substring(122, 128)) != null -> parseAboDate(line.substring(122, 128))
                            line.length >= 97 && parseAboDate(line.substring(91, 97)) != null -> parseAboDate(line.substring(91, 97))
                            else -> null
                        }

                        val dateStr = postingDate ?: statementYm?.let { "$it-01" } ?: "2026-01-01"
                        if (statementYm == null && dateStr.length >= 7) {
                            statementYm = dateStr.substring(0, 7)
                        }

                        // Categorize BEFORE stripping boilerplate/payer prefixes
                        val cat = TransactionCategorizer.categorizeTransaction(
                            amount = txAmount,
                            counterpartyAcc = counterpartyAcc,
                            counterpartyName = rawNote,
                            message = "",
                            vs = vs,
                            familyAccounts = familyAccounts,
                            rawContext = line,
                            householdNames = householdNames,
                            knownParentalGiftAccounts = knownParentalGiftAccounts,
                            compiledOverrides = compiledOverrides
                        )
                        val cleanDesc = PaymentDescriptionCleaner.cleanTransactionDescription(
                            blockText = rawNote,
                            dateStr = dateStr,
                            amounts = listOf(txAmount),
                            account = counterpartyAcc,
                            vs = vs,
                            householdNames = householdNames
                        )
                        val isNetted = (cat == BankTransactionType.INTERNAL_TRANSFER)
                        val nettingReason = TransactionCategorizer.determineNettingReason(cat, cleanDesc, "")

                        pendingTransaction = ParsedBankTransaction(
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
                    }
                }

                line.startsWith("076") || line.startsWith("078") || line.startsWith("079") -> {
                    val msgText = line.substring(minOf(3, line.length)).trim()
                    if (pendingTransaction != null && msgText.isNotBlank()) {
                        val prev = pendingTransaction!!
                        val updatedMsg = if (prev.message.isBlank()) msgText else "${prev.message} $msgText"
                        val updatedDesc = if (prev.counterpartyName.isBlank()) {
                            PaymentDescriptionCleaner.cleanPaymentDescription(msgText, householdNames)
                        } else prev.counterpartyName
                        pendingRawContext = "$pendingRawContext $msgText"
                        val reCat = TransactionCategorizer.categorizeTransaction(
                            amount = prev.amount,
                            counterpartyAcc = prev.counterpartyAccount,
                            counterpartyName = updatedDesc,
                            message = updatedMsg,
                            vs = prev.variableSymbol,
                            familyAccounts = familyAccounts,
                            rawContext = pendingRawContext,
                            householdNames = householdNames,
                            knownParentalGiftAccounts = knownParentalGiftAccounts,
                            compiledOverrides = compiledOverrides
                        )
                        pendingTransaction = prev.copy(
                            counterpartyName = updatedDesc,
                            message = updatedMsg,
                            category = reCat,
                            isNetted = (reCat == BankTransactionType.INTERNAL_TRANSFER),
                            nettingReason = TransactionCategorizer.determineNettingReason(reCat, updatedDesc, updatedMsg)
                        )
                    }
                }
            }
        }
        pendingTransaction?.let { transactions.add(it) }

        val sharedNonCzkCurr = if (transactions.isNotEmpty() && transactions.all { it.originalCurrency != null }) {
            transactions.mapNotNull { it.originalCurrency }.distinct().singleOrNull()
        } else null
        if (closingBalance != null && sharedNonCzkCurr != null) {
            val sharedRate = FxRateService.getRateOrNull(sharedNonCzkCurr)
            if (sharedRate != null && sharedRate.isFinite() && sharedRate > 0.0) {
                closingBalance = closingBalance!! * sharedRate
            }
        }

        val resolvedOwnKey = when {
            headerExplicitAcc.isNotEmpty() -> headerExplicitAcc
            rawOwnAcc16.isNotEmpty() -> {
                val bankCode = CrossStatementReconciliationEngine.bankCodeForBankName(detectedBank.name)
                CzechValueParsers.formatAboAccountKey(rawOwnAcc16, bankCode)
            }
            else -> ""
        }
        val taggedTxs = if (resolvedOwnKey.isNotEmpty()) {
            transactions.map { it.copy(accountKey = resolvedOwnKey) }
        } else {
            transactions
        }

        val parseWarning = if (unknownCurrencies.isNotEmpty()) {
            "Skipped ABO/GPC transaction(s) with unsupported currency: ${unknownCurrencies.joinToString(", ")}"
        } else {
            null
        }

        return TransactionCategorizer.buildSummary(
            bankType = detectedBank,
            transactions = taggedTxs,
            monthEndBalance = closingBalance,
            forcedYearMonth = statementYm,
            accountKey = resolvedOwnKey
        ).copy(parseWarning = parseWarning)
    }
}
