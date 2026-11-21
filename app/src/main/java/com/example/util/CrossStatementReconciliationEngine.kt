package com.example.util

import com.example.data.ImportedBankTransactionEntity
import com.example.util.statement.CzechValueParsers
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
    val combinedEmergencyReserve: Double? = null,
    val bankBalances: Map<String, Double> = emptyMap()
)

data class ReconcileResult(
    val currentMonthTxs: List<ImportedBankTransactionEntity>,
    val updatedBoundaryTxs: List<ImportedBankTransactionEntity>,
    val auditReport: CrossStatementAuditReport
)

object CrossStatementReconciliationEngine {
    // Statutory interbank clearing window tolerance requested by user
    const val CLEARING_WINDOW_DAYS = 5L

    private val EXTERNAL_EXPENSE_CATEGORIES = setOf(
        BankTransactionType.GROCERIES.name,
        BankTransactionType.HEALTH_DRUGSTORE.name,
        BankTransactionType.SHOPPING_GOODS.name,
        BankTransactionType.DINING_RESTAURANT.name,
        BankTransactionType.TRANSPORTATION.name,
        BankTransactionType.SUBSCRIPTIONS_MEDIA.name,
        BankTransactionType.SERVICES_UTILITIES.name,
        BankTransactionType.HOUSING_RENT.name,
        BankTransactionType.CHARITY_DONATION.name,
        BankTransactionType.LIFESTYLE_LIVING.name,
        BankTransactionType.ATM_CASH.name,
        BankTransactionType.INVESTMENT_PORTU.name,
        BankTransactionType.INVESTMENT_DIP.name,
        BankTransactionType.INVESTMENT_DPS.name
    )

    private val EXTERNAL_INCOME_CATEGORIES = setOf(
        BankTransactionType.SALARY_VACLAV.name,
        BankTransactionType.SALARY_ELEONORA.name,
        BankTransactionType.PARENTAL_BENEFIT.name,
        BankTransactionType.FAMILY_GIFT.name
    )

    private val SVJ_REGEX = Regex("""\bsvj\b""")
    private val CIL_WORD_REGEX = Regex("""\bcil(?:e|em|u)?\b""")
    private val RB_CODE_REGEX = Regex("""\brb\b""")
    private val KB_CODE_REGEX = Regex("""\bkb\b""")
    private val CS_CODE_REGEX = Regex("""\bcs\b""")
    private val FAMILY_GIFT_WORD_REGEX = Regex(
        """\b(?:dar|dary|darek|darem|darovani|maminka|mamka|tati|tatinek|tatka|babicka|dedecek|rodice|rodicu|tchyne|tchan)\b"""
    )
    private val FAMILY_PRISPEVEK_REGEX = Regex(
        """\bprispevek\s+(?:na\s+(?:bydleni|najem|najemne|domacnost|dite|deti|studium|rodinu|zivobyti|vyzivne|kocarek|vybaveni)|od\b)"""
    )
    private val EMPLOYER_BENEFIT_REJECTION_REGEX = Regex(
        """\b(?:stravovani|stravenk|obed|penzij|dip|dps|kultura|multisport|doprava|mobil|telefon|dovolen|benefity)\b"""
    )

    internal class NormalizedHouseholdContext(
        val knownFamilyCanonical: Set<String>,
        val knownParentalGiftCanonical: Set<String>,
        val normalizedNames: List<List<String>>,
        val allNameTokens: Set<String>
    ) {
        companion object {
            fun from(
                knownFamilyAccounts: Set<String>,
                knownParentalGiftAccounts: Set<String>,
                householdNames: Collection<String>
            ): NormalizedHouseholdContext {
                val familyCanon = knownFamilyAccounts.asSequence()
                    .map { CzechValueParsers.canonicalAccountKey(it) }
                    .filter { it.isNotEmpty() }
                    .toSet()
                val giftCanon = knownParentalGiftAccounts.asSequence()
                    .map { CzechValueParsers.canonicalAccountKey(it) }
                    .filter { it.isNotEmpty() }
                    .toSet()
                val normNames = householdNames.asSequence()
                    .map { CzechMerchantCatalog.normalizeSimple(it) }
                    .filter { it.length >= 3 }
                    .map { it.split(" ").filter { tok -> tok.isNotBlank() } }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .toList()
                val allTokens = normNames.flatten().filter { it.length >= 3 }.toSet()
                return NormalizedHouseholdContext(familyCanon, giftCanon, normNames, allTokens)
            }
        }
    }

    private fun isKnownCanonicalMatch(canonicalTxAcc: String, knownCanonicalAccounts: Set<String>): Boolean {
        if (canonicalTxAcc.isEmpty() || knownCanonicalAccounts.isEmpty()) return false
        if (canonicalTxAcc in knownCanonicalAccounts) return true
        val accCore = canonicalTxAcc.substringBefore('/')
        return knownCanonicalAccounts.any { !it.contains('/') && it == accCore }
    }

    fun computeFingerprint(
        date: String,
        bankName: String,
        amount: Double,
        counterpartyAccount: String,
        counterpartyName: String,
        message: String,
        variableSymbol: String
    ): String {
        val amtStr = String.format(Locale.US, "%.2f", amount)
        val cleanAcc = counterpartyAccount.replace(" ", "").replace("-", "").lowercase(Locale.ROOT)
        val cleanName = counterpartyName.trim().lowercase(Locale.ROOT)
        val cleanMsg = message.trim().lowercase(Locale.ROOT)
        val cleanVs = variableSymbol.trim()
        val normBank = bankName.trim().uppercase(Locale.ROOT)
        return "$date|$normBank|$amtStr|$cleanAcc|$cleanName|$cleanMsg|$cleanVs"
    }

    fun computeFingerprint(tx: ImportedBankTransactionEntity): String {
        return computeFingerprint(
            date = tx.date,
            bankName = tx.bankName,
            amount = tx.amount,
            counterpartyAccount = tx.counterpartyAccount,
            counterpartyName = tx.counterpartyName,
            message = tx.message,
            variableSymbol = tx.variableSymbol
        )
    }

    fun isSelfOrFamilyTransfer(
        counterpartyName: String,
        counterpartyAccount: String,
        message: String,
        knownFamilyAccounts: Set<String> = emptySet(),
        isDebit: Boolean = false,
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): Boolean {
        val ctx = NormalizedHouseholdContext.from(knownFamilyAccounts, knownParentalGiftAccounts, householdNames)
        return isSelfOrFamilyTransferInternal(
            counterpartyName = counterpartyName,
            counterpartyAccount = counterpartyAccount,
            message = message,
            isDebit = isDebit,
            ctx = ctx
        )
    }

    internal fun isSelfOrFamilyTransferInternal(
        counterpartyName: String,
        counterpartyAccount: String,
        message: String,
        isDebit: Boolean,
        ctx: NormalizedHouseholdContext
    ): Boolean {
        val cleanAcc = CzechValueParsers.canonicalAccountKey(counterpartyAccount)

        // Precedence 1: Incoming credits from parental gift accounts or matching family gift rules
        // are 100% tax-exempt household inflows and must NEVER be netted as internal self-transfers.
        if (!isDebit && isFamilyGiftContributionInternal(counterpartyName, message, cleanAcc, ctx.knownParentalGiftCanonical)) {
            return false
        }

        val normAll = CzechMerchantCatalog.normalizeSimple("$counterpartyName $message")

        // Precedence 2: Rent and housing payments are never self-transfers.
        if (normAll.contains("najem") || normAll.contains("najmu") ||
            normAll.contains("cinze") || normAll.contains("fond oprav") ||
            SVJ_REGEX.containsMatchIn(normAll)
        ) {
            return false
        }

        // Precedence 3: Salaries, wages, and state parental benefits are external/statutory income.
        if (normAll.contains("mzda") || normAll.contains("vyplata") ||
            normAll.contains("rodicov") || normAll.contains("urad prace")
        ) {
            return false
        }

        // Precedence 4: Configured own household accounts.
        if (isKnownCanonicalMatch(cleanAcc, ctx.knownFamilyCanonical)) {
            return true
        }

        // Precedence 5: Configured household member names (no hardcoded personal names).
        val normCp = CzechMerchantCatalog.normalizeSimple(counterpartyName)
        val normMsg = CzechMerchantCatalog.normalizeSimple(message)
        val cpTokens = normCp.split(" ").filter { it.isNotBlank() }
        val allTokens = normAll.split(" ").filter { it.isNotBlank() }

        val matchesConfiguredInCounterparty = cpTokens.isNotEmpty() && ctx.normalizedNames.any { nameTokens ->
            if (nameTokens.size >= 2) {
                nameTokens.all { tok -> tok in cpTokens }
            } else {
                cpTokens == nameTokens || (cpTokens.size >= 2 && cpTokens.all { it in ctx.allNameTokens })
            }
        }
        val matchesConfiguredFullNameAnywhere = ctx.normalizedNames.any { nameTokens ->
            nameTokens.size >= 2 && nameTokens.all { tok -> tok in allTokens }
        } || (ctx.allNameTokens.size >= 2 && allTokens.count { it in ctx.allNameTokens } >= 2)

        if (!isDebit) {
            if (matchesConfiguredInCounterparty) return true
            if (normMsg.contains("prevod z vlastniho uctu") || normMsg.contains("prevod ze sporeni")) return true
            if (normAll.contains("revolut") && matchesConfiguredFullNameAnywhere) return true
        } else {
            val isPayerPrefix = normCp.contains("prikazce") || normCp.contains("platce") || normCp.contains("majitel")
            if (!isPayerPrefix && matchesConfiguredInCounterparty) return true
            if (normAll.contains("revolut") && !isPayerPrefix && matchesConfiguredFullNameAnywhere) return true
        }

        // Explicit self-transfer keywords
        if (normAll.contains("vlastni prevod") || normAll.contains("prevod mezi ucty")) return true
        if (normAll.contains("sporici ucet") || normAll.contains("prevod na sporeni") || normAll.contains("prevod ze sporeni")) return true

        // mBank micro-savings and goals (mSporeni / Cile)
        if (isMbankGoalOrMsporeni(counterpartyName, message)) return true

        return false
    }

    fun isMbankGoalOrMsporeni(counterpartyName: String, message: String): Boolean {
        val norm = CzechMerchantCatalog.normalizeSimple("$counterpartyName $message")
        return norm.contains("msporeni") ||
            norm.contains("prevod z cile") ||
            norm.contains("prevod cile") ||
            norm.contains("prevod na cil") ||
            (CIL_WORD_REGEX.containsMatchIn(norm) &&
                (norm.contains("prevod") || norm.contains("sporeni") || norm.contains("spori")))
    }

    fun isFamilyGiftContribution(
        counterpartyName: String,
        message: String,
        counterpartyAccount: String = "",
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): Boolean {
        val cleanAcc = CzechValueParsers.canonicalAccountKey(counterpartyAccount)
        val giftCanonical = knownParentalGiftAccounts.asSequence()
            .map { CzechValueParsers.canonicalAccountKey(it) }
            .filter { it.isNotEmpty() }
            .toSet()
        return isFamilyGiftContributionInternal(counterpartyName, message, cleanAcc, giftCanonical)
    }

    internal fun isFamilyGiftContributionInternal(
        counterpartyName: String,
        message: String,
        canonicalCounterpartyAcc: String,
        knownParentalGiftCanonical: Set<String>
    ): Boolean {
        if (isKnownCanonicalMatch(canonicalCounterpartyAcc, knownParentalGiftCanonical)) {
            return true
        }
        val norm = CzechMerchantCatalog.normalizeSimple("$counterpartyName $message")
        if (norm.isBlank()) return false
        if (norm.contains("rodicovsk") || norm.contains("urad prace")) return false

        val isCharityOrEmployment = norm.contains("nadace") ||
            norm.contains("charita") ||
            norm.contains("darcov") ||
            norm.contains("mzda") ||
            norm.contains("vyplata") ||
            norm.contains("zamestnavatel") ||
            EMPLOYER_BENEFIT_REJECTION_REGEX.containsMatchIn(norm)
        if (isCharityOrEmployment) return false

        val hasKnownFamilyPhrase = norm.contains("prispevek od") ||
            FAMILY_PRISPEVEK_REGEX.containsMatchIn(norm) ||
            norm.contains("dar od rodiny") ||
            norm.contains("rodinny dar") ||
            norm.contains("financni podpora") ||
            norm.contains("podpora rodiny") ||
            norm.contains("rodinna podpora") ||
            norm.contains("od rodicu") ||
            norm.contains("od mamy") ||
            norm.contains("od maminky") ||
            norm.contains("od taty") ||
            norm.contains("od tatinka") ||
            norm.contains("od babicky") ||
            norm.contains("od dedecka") ||
            norm.contains("od tchyne") ||
            norm.contains("od tchana") ||
            norm.contains("k narozeninam") ||
            norm.contains("k vanocum") ||
            norm.contains("k svatku") ||
            norm.contains("svatebni dar") ||
            norm.contains("kapesne")
        if (hasKnownFamilyPhrase) return true
        return FAMILY_GIFT_WORD_REGEX.containsMatchIn(norm)
    }

    private fun parseDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }

    fun bankCodeForBankName(bankName: String): String? {
        val norm = CzechMerchantCatalog.normalizeSimple(bankName)
        if (norm.isEmpty()) return null
        return when {
            norm.contains("moneta") -> "0600"
            norm.contains("csob") -> "0300"
            norm.contains("mbank") -> "6210"
            norm.contains("sporitelna") || CS_CODE_REGEX.containsMatchIn(norm) -> "0800"
            norm.contains("komercni") || KB_CODE_REGEX.containsMatchIn(norm) -> "0100"
            norm.contains("fio") -> "2010"
            norm.contains("airbank") || norm.contains("air bank") -> "3030"
            norm.contains("raiffeisen") || RB_CODE_REGEX.containsMatchIn(norm) -> "5500"
            norm.contains("unicredit") -> "2700"
            norm.contains("creditas") -> "2250"
            else -> null
        }
    }

    fun reconcileTransactions(
        transactions: List<ImportedBankTransactionEntity>,
        yearMonth: String,
        knownFamilyAccounts: Set<String> = emptySet(),
        boundaryTransactions: List<ImportedBankTransactionEntity> = emptyList(),
        bankBalances: Map<String, Double> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): Pair<List<ImportedBankTransactionEntity>, CrossStatementAuditReport> {
        val result = reconcileTransactionsWithBoundaries(
            transactions = transactions,
            yearMonth = yearMonth,
            knownFamilyAccounts = knownFamilyAccounts,
            boundaryTransactions = boundaryTransactions,
            bankBalances = bankBalances,
            householdNames = householdNames,
            knownParentalGiftAccounts = knownParentalGiftAccounts
        )
        return Pair(result.currentMonthTxs, result.auditReport)
    }

    private data class TxMeta(
        val index: Int,
        val tx: ImportedBankTransactionEntity,
        val parsedDate: LocalDate?,
        val absAmount: Double,
        val isSelfTransfer: Boolean,
        val isKnownParentalGift: Boolean,
        val normName: String,
        val normNameIsBankLabel: Boolean,
        val canonicalAccount: String,
        val isKnownFamilyAcc: Boolean,
        val routingBankCode: String?,
        val ownBankCode: String?,
        val normBankName: String
    )

    private fun buildTxMeta(
        index: Int,
        tx: ImportedBankTransactionEntity,
        ctx: NormalizedHouseholdContext
    ): TxMeta {
        val isDebit = tx.amount < 0
        val cleanAcc = CzechValueParsers.canonicalAccountKey(tx.counterpartyAccount)
        val normName = CzechMerchantCatalog.normalizeSimple(tx.counterpartyName)
        val isGiftAcc = !isDebit && isKnownCanonicalMatch(cleanAcc, ctx.knownParentalGiftCanonical)
        return TxMeta(
            index = index,
            tx = tx,
            parsedDate = parseDate(tx.date),
            absAmount = abs(tx.amount),
            isSelfTransfer = isSelfOrFamilyTransferInternal(
                counterpartyName = tx.counterpartyName,
                counterpartyAccount = tx.counterpartyAccount,
                message = tx.message,
                isDebit = isDebit,
                ctx = ctx
            ),
            isKnownParentalGift = isGiftAcc,
            normName = normName,
            normNameIsBankLabel = normName.isNotEmpty() && bankCodeForBankName(tx.counterpartyName) != null,
            canonicalAccount = cleanAcc,
            isKnownFamilyAcc = !isGiftAcc && isKnownCanonicalMatch(cleanAcc, ctx.knownFamilyCanonical),
            routingBankCode = tx.counterpartyAccount.split("/").getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() },
            ownBankCode = bankCodeForBankName(tx.bankName),
            normBankName = CzechMerchantCatalog.normalizeSimple(tx.bankName)
        )
    }

    private fun scoreTransferPair(debit: TxMeta, credit: TxMeta): Pair<Int, Long>? {
        val dDate = debit.parsedDate ?: return null
        val cDate = credit.parsedDate ?: return null
        if (!debit.absAmount.isFinite() || !credit.absAmount.isFinite()) return null
        if (abs(debit.absAmount - credit.absAmount) >= 0.05) return null

        val daysDiff = ChronoUnit.DAYS.between(dDate, cDate)
        if (daysDiff !in -1..CLEARING_WINDOW_DAYS) return null

        if (debit.tx.category == BankTransactionType.FAMILY_GIFT.name ||
            credit.tx.category == BankTransactionType.FAMILY_GIFT.name ||
            credit.isKnownParentalGift
        ) {
            return null
        }

        val debitIsExternal = debit.tx.category in EXTERNAL_EXPENSE_CATEGORIES
        val creditIsExternal = credit.tx.category in EXTERNAL_INCOME_CATEGORIES
        if (debitIsExternal && !debit.isSelfTransfer) return null
        if (creditIsExternal && !credit.isSelfTransfer) return null

        val isFlaggedSelf = debit.isSelfTransfer || credit.isSelfTransfer
        val debitExplicitInternal = debit.tx.category == BankTransactionType.INTERNAL_TRANSFER.name
        val creditExplicitInternal = credit.tx.category == BankTransactionType.INTERNAL_TRANSFER.name
        val sameAccountKnownFamily = debit.isKnownFamilyAcc || credit.isKnownFamilyAcc

        if (!isFlaggedSelf && !debitExplicitInternal && !creditExplicitInternal && !sameAccountKnownFamily) {
            val dHasName = debit.normName.isNotEmpty() && !debit.normNameIsBankLabel
            val cHasName = credit.normName.isNotEmpty() && !credit.normNameIsBankLabel
            if (dHasName && cHasName && debit.normName != credit.normName) return null
            if (dHasName xor cHasName) return null
        }

        val isCrossBank = credit.normBankName != debit.normBankName
        val hasRoutingCodeMatch = (credit.ownBankCode != null && debit.routingBankCode == credit.ownBankCode) ||
            (debit.ownBankCode != null && credit.routingBankCode == debit.ownBankCode)

        val isSelfOrExplicitTransfer = isFlaggedSelf || hasRoutingCodeMatch ||
            debitExplicitInternal || creditExplicitInternal || sameAccountKnownFamily

        if (!isCrossBank && !isSelfOrExplicitTransfer) {
            return null
        }

        var score = 0
        if (hasRoutingCodeMatch) score += 100
        if (debit.isSelfTransfer && credit.isSelfTransfer) score += 60 else if (isFlaggedSelf) score += 40
        if (debit.normName.isNotEmpty() && debit.normName == credit.normName) score += 30
        if (isCrossBank) score += 10
        score += if (daysDiff >= 0) (20 - daysDiff.toInt() * 3) else 2
        return score to abs(daysDiff)
    }

    private fun indexByAmountBucket(items: List<TxMeta>): HashMap<Long, MutableList<TxMeta>> {
        val map = HashMap<Long, MutableList<TxMeta>>(items.size * 2)
        for (item in items) {
            if (!item.absAmount.isFinite() || item.absAmount >= 1e15 || item.parsedDate == null) continue
            val bucket = kotlin.math.round(item.absAmount).toLong()
            map.getOrPut(bucket) { mutableListOf() }.add(item)
        }
        return map
    }

    private fun popBestCandidate(
        anchor: TxMeta,
        isAnchorDebit: Boolean,
        candidateBuckets: HashMap<Long, MutableList<TxMeta>>
    ): Pair<TxMeta, Long>? {
        if (!anchor.absAmount.isFinite() || anchor.absAmount >= 1e15 || anchor.parsedDate == null) return null
        val centerBucket = kotlin.math.round(anchor.absAmount).toLong()
        var bestBucket = 0L
        var bestIdxInBucket = -1
        var bestScore = Int.MIN_VALUE
        var bestDaysApart = 0L
        var bestCandidate: TxMeta? = null

        for (b in (centerBucket - 1)..(centerBucket + 1)) {
            val list = candidateBuckets[b] ?: continue
            for (idx in list.indices) {
                val cand = list[idx]
                val scored = if (isAnchorDebit) {
                    scoreTransferPair(anchor, cand)
                } else {
                    scoreTransferPair(cand, anchor)
                } ?: continue
                if (scored.first > bestScore) {
                    bestScore = scored.first
                    bestDaysApart = scored.second
                    bestCandidate = cand
                    bestBucket = b
                    bestIdxInBucket = idx
                }
            }
        }

        if (bestCandidate == null || bestIdxInBucket == -1) return null
        candidateBuckets[bestBucket]?.removeAt(bestIdxInBucket)
        return bestCandidate to bestDaysApart
    }

    fun reconcileTransactionsWithBoundaries(
        transactions: List<ImportedBankTransactionEntity>,
        yearMonth: String,
        knownFamilyAccounts: Set<String> = emptySet(),
        boundaryTransactions: List<ImportedBankTransactionEntity> = emptyList(),
        bankBalances: Map<String, Double> = emptyMap(),
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet()
    ): ReconcileResult {
        val workingList = transactions.map { it.copy() }.toMutableList()
        val workingBoundary = boundaryTransactions.map { it.copy() }.toMutableList()
        val modifiedBoundaryIndices = mutableSetOf<Int>()
        val ctx = NormalizedHouseholdContext.from(knownFamilyAccounts, knownParentalGiftAccounts, householdNames)

        // 1. Initial pass: ensure parental gift account credits stay FAMILY_GIFT (never netted),
        // and identify known self-transfers.
        for (i in workingList.indices) {
            val tx = workingList[i]
            if (tx.nettingReason.startsWith("Manually ")) continue

            val cleanAcc = CzechValueParsers.canonicalAccountKey(tx.counterpartyAccount)
            if (tx.amount > 0 && isKnownCanonicalMatch(cleanAcc, ctx.knownParentalGiftCanonical)) {
                if (tx.category != BankTransactionType.FAMILY_GIFT.name || tx.isNetted) {
                    workingList[i] = tx.copy(
                        isNetted = false,
                        category = BankTransactionType.FAMILY_GIFT.name,
                        nettingReason = "",
                        matchedTxId = null
                    )
                }
                continue
            }

            if (tx.isNetted ||
                tx.category == BankTransactionType.HOUSING_RENT.name ||
                tx.category == BankTransactionType.FAMILY_GIFT.name
            ) continue

            if (isSelfOrFamilyTransferInternal(tx.counterpartyName, tx.counterpartyAccount, tx.message, isDebit = tx.amount < 0, ctx = ctx)) {
                val reason = when {
                    isMbankGoalOrMsporeni(tx.counterpartyName, tx.message) ->
                        "mBank internal transfer (mSpoření / Cíl)"
                    tx.message.contains("Revolut", ignoreCase = true) || tx.counterpartyName.contains("Revolut", ignoreCase = true) ->
                        "Own Revolut account transfer"
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

        // 2a. Cross-Statement Pairwise Matcher within current month
        val matchedPairs = mutableListOf<MatchedTransferPair>()
        val pairedWorkingIndices = mutableSetOf<Int>()

        val currentDebits = workingList.withIndex()
            .filter { (_, tx) ->
                tx.amount < 0 &&
                    tx.category != BankTransactionType.HOUSING_RENT.name &&
                    (!tx.isNetted || !isConfirmedPair(tx)) &&
                    !tx.nettingReason.startsWith("Manually ")
            }
            .map { (idx, tx) -> buildTxMeta(idx, tx, ctx) }

        val currentCredits = workingList.withIndex()
            .filter { (_, tx) ->
                tx.amount > 0 &&
                    tx.category != BankTransactionType.FAMILY_GIFT.name &&
                    (!tx.isNetted || !isConfirmedPair(tx)) &&
                    !tx.nettingReason.startsWith("Manually ")
            }
            .map { (idx, tx) -> buildTxMeta(idx, tx, ctx) }

        val currentCreditBuckets = indexByAmountBucket(currentCredits)
        for (debitMeta in currentDebits) {
            val (creditMeta, daysApart) = popBestCandidate(debitMeta, isAnchorDebit = true, currentCreditBuckets) ?: continue
            val debit = debitMeta.tx
            val credit = creditMeta.tx

            val reason = "Cross-account transfer: ${debit.bankName} -> ${credit.bankName} ($daysApart d apart)"
            pairedWorkingIndices.add(debitMeta.index)
            workingList[debitMeta.index] = workingList[debitMeta.index].copy(
                isNetted = true,
                category = BankTransactionType.INTERNAL_TRANSFER.name,
                nettingReason = reason,
                matchedTxId = credit.id.takeIf { it > 0L }
            )
            pairedWorkingIndices.add(creditMeta.index)
            workingList[creditMeta.index] = workingList[creditMeta.index].copy(
                isNetted = true,
                category = BankTransactionType.INTERNAL_TRANSFER.name,
                nettingReason = reason,
                matchedTxId = debit.id.takeIf { it > 0L }
            )

            matchedPairs.add(
                MatchedTransferPair(
                    debitTx = debit,
                    creditTx = credit,
                    daysApart = daysApart,
                    amount = debitMeta.absAmount
                )
            )
        }

        // 2b. Cross-Month Boundary Pairing: match remaining unmatched debits against boundary credits
        val boundaryCredits = workingBoundary.withIndex()
            .filter { (_, tx) ->
                tx.amount > 0 &&
                    tx.category != BankTransactionType.FAMILY_GIFT.name &&
                    (!tx.isNetted || !isConfirmedPair(tx)) &&
                    !tx.nettingReason.startsWith("Manually ")
            }
            .map { (idx, tx) -> buildTxMeta(idx, tx, ctx) }
        val remainingDebits = currentDebits.filter { it.index !in pairedWorkingIndices }

        val boundaryCreditBuckets = indexByAmountBucket(boundaryCredits)
        for (debitMeta in remainingDebits) {
            val (creditMeta, daysApart) = popBestCandidate(debitMeta, isAnchorDebit = true, boundaryCreditBuckets) ?: continue
            val debit = debitMeta.tx
            val credit = creditMeta.tx

            val reason = "Cross-month transfer: ${debit.bankName} (${debit.date}) -> ${credit.bankName} (${credit.date}, $daysApart d apart)"
            pairedWorkingIndices.add(debitMeta.index)
            workingList[debitMeta.index] = workingList[debitMeta.index].copy(
                isNetted = true,
                category = BankTransactionType.INTERNAL_TRANSFER.name,
                nettingReason = reason,
                matchedTxId = credit.id.takeIf { it > 0L }
            )
            workingBoundary[creditMeta.index] = workingBoundary[creditMeta.index].copy(
                isNetted = true,
                category = BankTransactionType.INTERNAL_TRANSFER.name,
                nettingReason = reason,
                matchedTxId = debit.id.takeIf { it > 0L }
            )
            modifiedBoundaryIndices.add(creditMeta.index)

            matchedPairs.add(
                MatchedTransferPair(
                    debitTx = debit,
                    creditTx = credit,
                    daysApart = daysApart,
                    amount = debitMeta.absAmount
                )
            )
        }

        // 2c. Cross-Month Boundary Pairing: match remaining unmatched credits against boundary debits
        val boundaryDebits = workingBoundary.withIndex()
            .filter { (_, tx) ->
                tx.amount < 0 &&
                    (!tx.isNetted || !isConfirmedPair(tx)) &&
                    tx.category != BankTransactionType.HOUSING_RENT.name &&
                    !tx.nettingReason.startsWith("Manually ")
            }
            .map { (idx, tx) -> buildTxMeta(idx, tx, ctx) }
        val remainingCredits = currentCredits.filter { it.index !in pairedWorkingIndices }

        val boundaryDebitBuckets = indexByAmountBucket(boundaryDebits)
        for (creditMeta in remainingCredits) {
            val (debitMeta, daysApart) = popBestCandidate(creditMeta, isAnchorDebit = false, boundaryDebitBuckets) ?: continue
            val debit = debitMeta.tx
            val credit = creditMeta.tx

            val reason = "Cross-month transfer: ${debit.bankName} (${debit.date}) -> ${credit.bankName} (${credit.date}, $daysApart d apart)"
            pairedWorkingIndices.add(creditMeta.index)
            workingList[creditMeta.index] = workingList[creditMeta.index].copy(
                isNetted = true,
                category = BankTransactionType.INTERNAL_TRANSFER.name,
                nettingReason = reason,
                matchedTxId = debit.id.takeIf { it > 0L }
            )
            workingBoundary[debitMeta.index] = workingBoundary[debitMeta.index].copy(
                isNetted = true,
                category = BankTransactionType.INTERNAL_TRANSFER.name,
                nettingReason = reason,
                matchedTxId = credit.id.takeIf { it > 0L }
            )
            modifiedBoundaryIndices.add(debitMeta.index)

            matchedPairs.add(
                MatchedTransferPair(
                    debitTx = debit,
                    creditTx = credit,
                    daysApart = daysApart,
                    amount = creditMeta.absAmount
                )
            )
        }

        // 3. Compute audit report metrics (count each matched pair once — never double-count both legs!)
        val singleSideTransfers = workingList.filterIndexed { idx, tx ->
            tx.isNetted && idx !in pairedWorkingIndices
        }

        val totalNetted = matchedPairs.sumOf { if (it.amount.isFinite()) it.amount * 2.0 else 0.0 } +
            singleSideTransfers.sumOf { if (it.amount.isFinite()) abs(it.amount) else 0.0 }
        val agg = StatementImportCoordinator.aggregateMonthTransactions(workingList)
        val totalInflows = agg.incVaclav + agg.incEleonora + agg.incOther
        val totalExpenses = agg.expRent + agg.expGroceries + agg.expOther
        val cleanReserve = if (bankBalances.isNotEmpty()) {
            bankBalances.values.filter { it.isFinite() }.sum()
        } else {
            null
        }

        val auditReport = CrossStatementAuditReport(
            yearMonth = yearMonth,
            totalNettedAmount = totalNetted,
            matchedPairs = matchedPairs,
            singleSideNettedTransfers = singleSideTransfers,
            participatingBanks = workingList.map { it.bankName }.distinct(),
            reconciledInflows = totalInflows,
            reconciledExpenses = totalExpenses,
            reconciledNetCashFlow = totalInflows - totalExpenses,
            combinedEmergencyReserve = cleanReserve,
            bankBalances = bankBalances
        )

        val updatedBoundaryTxs = modifiedBoundaryIndices.sorted().map { workingBoundary[it] }
        return ReconcileResult(workingList, updatedBoundaryTxs, auditReport)
    }

    private fun isConfirmedPair(tx: ImportedBankTransactionEntity): Boolean {
        val reason = tx.nettingReason
        return reason.startsWith("Cross-account") || reason.startsWith("Cross-month")
    }
}
