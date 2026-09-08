package com.example.util

import com.example.data.ImportedBankTransactionEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

data class MatchedTransferPair(
    val debitTx: ImportedBankTransactionEntity,
    val creditTx: ImportedBankTransactionEntity,
    val daysApart: Long,
    val amount: Double
)

data class CrossStatementAuditReport(
    val yearMonth: String,
    val totalNettedAmount: Double,
    val matchedPairs: List<MatchedTransferPair>,
    val singleSideNettedTransfers: List<ImportedBankTransactionEntity>,
    val participatingBanks: List<String>,
    val reconciledInflows: Double,
    val reconciledExpenses: Double,
    val reconciledNetCashFlow: Double,
    val combinedEmergencyReserve: Double? = null
)

object CrossStatementReconciliationEngine {
    // Statutory interbank clearing window tolerance requested by user
    const val CLEARING_WINDOW_DAYS = 5L

    fun isSelfOrFamilyTransfer(
        counterpartyName: String,
        counterpartyAccount: String,
        message: String,
        knownFamilyAccounts: Set<String> = emptySet()
    ): Boolean {
        val cleanAcc = counterpartyAccount.replace(" ", "").replace("-", "").lowercase(Locale.ROOT)
        if (knownFamilyAccounts.any { cleanAcc.contains(it.replace(" ", "").replace("-", "").lowercase(Locale.ROOT)) && it.isNotBlank() }) {
            return true
        }

        val text = "$counterpartyName $message".lowercase(Locale.ROOT)

        // Self and spouse name occurrences
        if (text.contains("vaclav martinu") || text.contains("václav martinů") || text.contains("martinu vaclav") || text.contains("martinů václav")) return true
        if (text.contains("eleonora martinu") || text.contains("eleonora martinů") || text.contains("martinu eleonora") || text.contains("martinů eleonora")) return true

        // Explicit self-transfer keywords
        if (text.contains("vlastní převod") || text.contains("vlastni prevod") || text.contains("převod mezi účty") || text.contains("prevod mezi ucty")) return true
        if (text.contains("spořicí účet") || text.contains("sporicí ucet") || text.contains("spořící účet") || text.contains("prevod na sporeni") || text.contains("převod na spoření")) return true
        if (text.contains("sent from revolut") || (text.contains("revolut") && text.contains("martinu"))) return true

        // mBank micro-savings and goals (mSpoření / Cíle)
        if (isMbankGoalOrMsporeni(counterpartyName, message)) return true

        return false
    }

    fun isMbankGoalOrMsporeni(counterpartyName: String, message: String): Boolean {
        val text = "$counterpartyName $message".lowercase(Locale.ROOT)
        val norm = CzechMerchantCatalog.normalize("$counterpartyName $message")
        return text.contains("převod z cíle") || text.contains("prevod z cile") ||
                norm.contains("prevod cile") || norm.contains("prevod z cile") ||
                text.contains("převod na mspoření") || text.contains("prevod na msporeni") ||
                norm.contains("prevod na msporeni") ||
                text.contains("převod z mspoření") || text.contains("prevod z msporeni") ||
                norm.contains("prevod msporeni") || norm.contains("prevod z msporeni") ||
                text.contains("převod na cíl") || text.contains("prevod na cil") ||
                norm.contains("prevod na cil") ||
                text.contains("mspoření") || text.contains("msporeni") ||
                norm.contains("msporeni") ||
                ((text.contains("cíl") || norm.contains("cil") || norm.contains("cile")) &&
                        (text.contains("převod") || text.contains("prevod") || text.contains("sporeni") || text.contains("spori")))
    }

    fun isFamilyGiftContribution(
        counterpartyName: String,
        message: String
    ): Boolean {
        val text = "$counterpartyName $message".lowercase(Locale.ROOT)
        return (text.contains("andrea martinu") || text.contains("andrea martinů") ||
                text.contains("petr martinu") || text.contains("petr martinů") ||
                text.contains("příspěvek na") || text.contains("prispevek na") ||
                text.contains("dar od rodiny") || text.contains("rodinný dar"))
    }

    private fun parseDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }

    fun reconcileTransactions(
        transactions: List<ImportedBankTransactionEntity>,
        yearMonth: String,
        knownFamilyAccounts: Set<String> = emptySet()
    ): Pair<List<ImportedBankTransactionEntity>, CrossStatementAuditReport> {
        val workingList = transactions.map { it.copy() }.toMutableList()

        // 1. Initial pass: identify known self-transfers and family contributions
        for (i in workingList.indices) {
            val tx = workingList[i]
            if (tx.isNetted) continue

            if (isSelfOrFamilyTransfer(tx.counterpartyName, tx.counterpartyAccount, tx.message, knownFamilyAccounts)) {
                val reason = when {
                    isMbankGoalOrMsporeni(tx.counterpartyName, tx.message) ->
                        "mBank internal transfer (mSpoření / Cíl)"
                    tx.message.contains("Revolut", ignoreCase = true) || tx.counterpartyName.contains("Revolut", ignoreCase = true) ->
                        "Own Revolut account transfer"
                    tx.counterpartyName.contains("Václav", ignoreCase = true) || tx.counterpartyName.contains("Vaclav", ignoreCase = true) || tx.message.contains("Václav", ignoreCase = true) || tx.message.contains("Vaclav", ignoreCase = true) ->
                        "Self-transfer (Václav Martinu)"
                    tx.counterpartyName.contains("Eleonora", ignoreCase = true) || tx.message.contains("Eleonora", ignoreCase = true) ->
                        "Self-transfer (Eleonora Martinu)"
                    else ->
                        "Internal household transfer"
                }

                workingList[i] = tx.copy(
                    isNetted = true,
                    category = BankTransactionType.INTERNAL_TRANSFER.name,
                    nettingReason = reason
                )
            }
        }

        // 2. Cross-Statement Pairwise Matcher: Pair debit on Account A with credit on Account B within ±5 days
        val matchedPairs = mutableListOf<MatchedTransferPair>()
        val debits = workingList.filter { it.amount < 0 }.toMutableList()
        val credits = workingList.filter { it.amount > 0 }.toMutableList()

        for (debit in debits) {
            val debitDate = parseDate(debit.date) ?: continue
            val debitAbs = abs(debit.amount)

            // Look for matching credit in another bank or internal transfer pair
            val candidateIndex = credits.indexOfFirst { credit ->
                val creditDate = parseDate(credit.date)
                if (creditDate == null) return@indexOfFirst false

                val amountMatches = abs(debitAbs - credit.amount) < 0.05
                val within5Days = abs(ChronoUnit.DAYS.between(debitDate, creditDate)) <= CLEARING_WINDOW_DAYS

                if (!amountMatches || !within5Days) return@indexOfFirst false

                // Different bank statements or flagged self-transfers match
                val isCrossBank = credit.bankName != debit.bankName
                val isFlaggedSelf = isSelfOrFamilyTransfer(credit.counterpartyName, credit.counterpartyAccount, credit.message, knownFamilyAccounts) ||
                        isSelfOrFamilyTransfer(debit.counterpartyName, debit.counterpartyAccount, debit.message, knownFamilyAccounts)

                isCrossBank || isFlaggedSelf
            }

            if (candidateIndex != -1) {
                val credit = credits.removeAt(candidateIndex)
                val creditDate = parseDate(credit.date)!!
                val daysApart = abs(ChronoUnit.DAYS.between(debitDate, creditDate))

                val debitIdxInWorking = workingList.indexOfFirst { it.id == debit.id && it.date == debit.date && it.amount == debit.amount }
                val creditIdxInWorking = workingList.indexOfFirst { it.id == credit.id && it.date == credit.date && it.amount == credit.amount }

                val reason = "Cross-account transfer: ${debit.bankName} -> ${credit.bankName} ($daysApart d apart)"
                if (debitIdxInWorking != -1) {
                    workingList[debitIdxInWorking] = workingList[debitIdxInWorking].copy(
                        isNetted = true,
                        category = BankTransactionType.INTERNAL_TRANSFER.name,
                        nettingReason = reason
                    )
                }
                if (creditIdxInWorking != -1) {
                    workingList[creditIdxInWorking] = workingList[creditIdxInWorking].copy(
                        isNetted = true,
                        category = BankTransactionType.INTERNAL_TRANSFER.name,
                        nettingReason = reason
                    )
                }

                matchedPairs.add(
                    MatchedTransferPair(
                        debitTx = debit,
                        creditTx = credit,
                        daysApart = daysApart,
                        amount = debitAbs
                    )
                )
            }
        }

        // 3. Compute audit report metrics
        val singleSideTransfers = workingList.filter { tx ->
            tx.isNetted && matchedPairs.none {
                (it.debitTx.id != 0L && it.debitTx.id == tx.id) ||
                (it.creditTx.id != 0L && it.creditTx.id == tx.id) ||
                (it.debitTx.date == tx.date && it.debitTx.amount == tx.amount) ||
                (it.creditTx.date == tx.date && it.creditTx.amount == tx.amount)
            }
        }

        val totalNetted = (matchedPairs.sumOf { it.amount } * 2.0) + singleSideTransfers.sumOf { abs(it.amount) }

        var incVaclav = 0.0
        var incEleonora = 0.0
        var incOther = 0.0
        var expRent = 0.0
        var expGroceries = 0.0
        var expOther = 0.0

        for (tx in workingList) {
            if (tx.isNetted) continue
            val amt = tx.amount
            when (tx.category) {
                BankTransactionType.SALARY_VACLAV.name -> incVaclav += amt
                BankTransactionType.SALARY_ELEONORA.name -> incEleonora += amt
                BankTransactionType.PARENTAL_BENEFIT.name -> incEleonora += amt
                BankTransactionType.OTHER_INFLOW.name -> incOther += amt
                BankTransactionType.HOUSING_RENT.name -> expRent += abs(amt)
                BankTransactionType.GROCERIES.name -> expGroceries += abs(amt)
                BankTransactionType.LIFESTYLE_LIVING.name -> expOther += abs(amt)
                BankTransactionType.INVESTMENT_PORTU.name -> expOther += 0.0
                BankTransactionType.INVESTMENT_DIP.name -> expOther += 0.0
                BankTransactionType.INVESTMENT_DPS.name -> expOther += 0.0
                else -> {
                    if (amt > 0) incOther += amt else expOther += abs(amt)
                }
            }
        }

        val auditReport = CrossStatementAuditReport(
            yearMonth = yearMonth,
            totalNettedAmount = totalNetted,
            matchedPairs = matchedPairs,
            singleSideNettedTransfers = singleSideTransfers,
            participatingBanks = workingList.map { it.bankName }.distinct(),
            reconciledInflows = incVaclav + incEleonora + incOther,
            reconciledExpenses = expRent + expGroceries + expOther,
            reconciledNetCashFlow = (incVaclav + incEleonora + incOther) - (expRent + expGroceries + expOther),
            combinedEmergencyReserve = null
        )

        return Pair(workingList, auditReport)
    }
}
