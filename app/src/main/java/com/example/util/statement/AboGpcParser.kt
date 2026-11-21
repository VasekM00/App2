package com.example.util.statement

import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CzechMerchantCatalog
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

    fun parseAboGpc(
        lines: List<String>,
        familyAccounts: Set<String> = emptySet(),
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): StatementParseSummary {
        var detectedBank = BankType.GENERIC
        var statementYm: String? = null
        var closingBalance: Double? = null
        val compiledOverrides = CzechMerchantCatalog.compileOverrides(userOverrides)

        val transactions = mutableListOf<ParsedBankTransaction>()
        var pendingTransaction: ParsedBankTransaction? = null
        var pendingRawContext = ""

        for (rawLine in lines) {
            val line = rawLine.trimEnd('\r', '\n')
            if (line.isBlank()) continue

            when {
                line.startsWith("074") -> {
                    if (line.length >= 81) {
                        val closeAmtStr = line.substring(66, minOf(line.length, 80)).trim()
                        val closeSign = line.getOrNull(80) ?: '+'
                        val closeAmt = (closeAmtStr.toLongOrNull() ?: 0L) / 100.0
                        closingBalance = if (closeSign == '-') -closeAmt else closeAmt
                    }
                    if (line.length >= 118) {
                        val stDateStr = line.substring(112, minOf(line.length, 118)).trim()
                        val fullDate = parseAboDate(stDateStr)
                            ?: (if (line.length >= 120) parseAboDate(line.substring(114, 120)) else null)
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
                        val txAmount = if (isDebit) -rawAmt else rawAmt

                        val vs = if (line.length >= 71) {
                            line.substring(61, minOf(71, line.length)).trim().trimStart('0')
                        } else ""

                        val bankCode = if (line.length >= 75) {
                            line.substring(71, minOf(75, line.length)).trim()
                        } else ""

                        val counterpartyAcc = when {
                            rawCpAcc.isNotBlank() && bankCode.isNotBlank() -> "$rawCpAcc/$bankCode"
                            rawCpAcc.isNotBlank() -> rawCpAcc
                            else -> ""
                        }

                        val rawNote = if (line.length >= 115) {
                            line.substring(89, minOf(115, line.length)).trim()
                        } else if (line.length > 89) {
                            line.substring(89).trim()
                        } else ""

                        // Support standard ČBA 128-byte offsets plus legacy offset fallbacks
                        val postingDate = when {
                            line.length >= 128 && parseAboDate(line.substring(122, 128)) != null -> parseAboDate(line.substring(122, 128))
                            line.length >= 127 && parseAboDate(line.substring(121, 127)) != null -> parseAboDate(line.substring(121, 127))
                            line.length >= 121 && parseAboDate(line.substring(115, 121)) != null -> parseAboDate(line.substring(115, 121))
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
                            nettingReason = nettingReason
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

        if (detectedBank == BankType.GENERIC && transactions.isNotEmpty()) {
            val detectedFromTxs = transactions.mapNotNull { tx ->
                BankDetector.bankFromAccountCode(tx.counterpartyAccount.split("/").getOrNull(1))
            }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            if (detectedFromTxs != null) detectedBank = detectedFromTxs
        }

        return TransactionCategorizer.buildSummary(detectedBank, transactions, closingBalance, forcedYearMonth = statementYm)
    }
}
