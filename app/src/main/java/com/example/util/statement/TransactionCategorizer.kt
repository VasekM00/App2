package com.example.util.statement

import com.example.util.BankTransactionType
import com.example.util.BankType
import com.example.util.CrossStatementReconciliationEngine
import com.example.util.CzechMerchantCatalog
import com.example.util.ParsedBankTransaction
import com.example.util.StatementParseSummary
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

internal object TransactionCategorizer {

    private val SALARY_PLAT_REGEX = Regex("""\bplat\b""")
    private val SALARY_RODP_REGEX = Regex("""\brodp\b""")
    private val HOUSING_BYT_REGEX = Regex("""\bbyt\b""")
    private val HOUSING_SVJ_REGEX = Regex("""\bsvj\b""")
    private val INV_PORTU_WORD_REGEX = Regex("""\bportu\b""")
    private val INV_XTB_WORD_REGEX = Regex("""\bxtb\b""")
    private val INV_DIP_WORD_REGEX = Regex("""\bdip\b""")
    private val INV_DPS_WORD_REGEX = Regex("""\bdps\b""")
    private val GROCERY_JIP_WORD_REGEX = Regex("""\bjip\b""")

    fun categorizeTransaction(
        amount: Double,
        counterpartyAcc: String,
        counterpartyName: String,
        message: String,
        @Suppress("UNUSED_PARAMETER") vs: String,
        familyAccounts: Set<String>,
        userOverrides: Map<String, BankTransactionType> = emptyMap(),
        rawContext: String = "",
        householdNames: Collection<String> = emptyList(),
        knownParentalGiftAccounts: Set<String> = emptySet(),
        compiledOverrides: CzechMerchantCatalog.CompiledOverrides? = null
    ): BankTransactionType {
        val rawCombined = "$counterpartyName $message $rawContext"
        val folded = CzechMerchantCatalog.normalizeSimple(rawCombined)
        val cleanAcc = counterpartyAcc.replace(" ", "").replace("-", "").lowercase(Locale.ROOT)
        val isExplicitRefund = folded.contains("vraceni") || folded.contains("vratka") ||
            folded.contains("storno") || folded.contains("refund")

        // 1. Incoming parental/family gift accounts take precedence over self-transfer netting.
        if (amount > 0 && !isExplicitRefund &&
            CzechValueParsers.isKnownAccountMatch(counterpartyAcc, knownParentalGiftAccounts)
        ) {
            return BankTransactionType.FAMILY_GIFT
        }

        // 2. Self or family internal transfers (canonical account match, NOT raw substring).
        if (CrossStatementReconciliationEngine.isSelfOrFamilyTransfer(
                counterpartyName = counterpartyName,
                counterpartyAccount = counterpartyAcc,
                message = message,
                knownFamilyAccounts = familyAccounts,
                isDebit = amount < 0,
                householdNames = householdNames,
                knownParentalGiftAccounts = knownParentalGiftAccounts
            )
        ) {
            return BankTransactionType.INTERNAL_TRANSFER
        }
        if (CzechValueParsers.isKnownAccountMatch(counterpartyAcc, familyAccounts)) {
            return BankTransactionType.INTERNAL_TRANSFER
        }

        // 3. Explicit user merchant overrides apply across both inflows and outflows.
        val resolvedOverrides = compiledOverrides ?: CzechMerchantCatalog.compileOverrides(userOverrides)
        val overrideMatch = CzechMerchantCatalog.matchUserOverrideOnly(
            "$counterpartyName $counterpartyAcc $message $rawContext",
            resolvedOverrides
        )
        if (overrideMatch != null) {
            return overrideMatch
        }

        // 4. Inflows (amount > 0): income unless explicitly marked as a refund/storno.
        if (amount > 0 && !isExplicitRefund) {
            val spouseNameNorm = householdNames.drop(1).firstOrNull()
                ?.let { CzechMerchantCatalog.normalizeSimple(it) }
                ?.takeIf { it.length >= 3 }
            return when {
                folded.contains("rodicovsk") || folded.contains("urad prace") ||
                    SALARY_RODP_REGEX.containsMatchIn(folded) ->
                    BankTransactionType.PARENTAL_BENEFIT
                CrossStatementReconciliationEngine.isFamilyGiftContribution(
                    counterpartyName = counterpartyName,
                    message = message,
                    counterpartyAccount = counterpartyAcc,
                    knownParentalGiftAccounts = knownParentalGiftAccounts
                ) ->
                    BankTransactionType.FAMILY_GIFT
                spouseNameNorm != null && spouseNameNorm.split(" ").any { tok ->
                    tok.length >= 3 && Regex("""\b${Regex.escape(tok)}\b""").containsMatchIn(folded)
                } ->
                    BankTransactionType.SALARY_ELEONORA
                folded.contains("zamestnavatel") || folded.contains("mzda") ||
                    SALARY_PLAT_REGEX.containsMatchIn(folded) || folded.contains("vyplata") ||
                    folded.contains("odmena") || folded.contains("salary") || folded.contains("payroll") ->
                    BankTransactionType.SALARY_VACLAV
                else ->
                    BankTransactionType.OTHER_INFLOW
            }
        }

        // 5. Built-in merchant catalog
        val catalogMatch = CzechMerchantCatalog.matchCategory(
            "$counterpartyName $counterpartyAcc $message $rawContext",
            CzechMerchantCatalog.CompiledOverrides.EMPTY
        )
        if (catalogMatch != null) {
            return catalogMatch
        }

        // 6. Rent & Housing Priority Check (matched on ASCII-folded text so \b handles diacritics cleanly)
        if (amount < 0) {
            val isExcludedFromHousing = folded.contains("nabytek") ||
                folded.contains("ubytovan") || folded.contains("hotel") || folded.contains("ikea") ||
                folded.contains("sporen") || folded.contains("investic") || folded.contains("cil") ||
                folded.contains("penzij") || folded.contains("staveb") || folded.contains("pojist") ||
                folded.contains("splatk") || folded.contains("uver") || folded.contains("pujck") ||
                folded.contains("leasing") || folded.contains("skoln") || folded.contains("dane") ||
                folded.contains("aliment") || folded.contains("vyziv")
            val hasHousingKeyword = !isExcludedFromHousing && (
                folded.contains("najem") || folded.contains("najemne") ||
                    folded.contains("cinze") || folded.contains("fond oprav") ||
                    HOUSING_SVJ_REGEX.containsMatchIn(folded) || folded.contains("platba najmu") ||
                    folded.contains("bydleni") || HOUSING_BYT_REGEX.containsMatchIn(folded) ||
                    folded.contains("pronajem") || folded.contains("pronajimatel") ||
                    folded.contains("najemce") || folded.contains("podnajem") ||
                    folded.contains("sipo") ||
                    (kotlin.math.abs(amount) in 15000.0..25000.0 && (
                        folded.contains("trvaly") || folded.contains("prikaz")
                    ))
            )
            if (hasHousingKeyword) {
                return BankTransactionType.HOUSING_RENT
            }
        }

        val isGeneralInvestmentAcc = cleanAcc.contains("76788295") || cleanAcc.contains("7678876788") ||
            cleanAcc.contains("2038012508") || cleanAcc.contains("2555410109") ||
            cleanAcc.contains("518746050") ||
            folded.contains("76788295") || folded.contains("7678876788") ||
            folded.contains("518746050")

        if (isGeneralInvestmentAcc || INV_PORTU_WORD_REGEX.containsMatchIn(folded) ||
            folded.contains("wood company") || folded.contains("wood retail") ||
            INV_XTB_WORD_REGEX.containsMatchIn(folded) || folded.contains("x trade") ||
            folded.contains("degiro") || folded.contains("interactive brokers") ||
            folded.contains("trading 212") || folded.contains("csob drobne")
        ) {
            return BankTransactionType.INVESTMENT_PORTU
        }
        if (INV_DIP_WORD_REGEX.containsMatchIn(folded) || folded.contains("patria dip") || folded.contains("patria finance")) {
            return BankTransactionType.INVESTMENT_DIP
        }

        val isDpsAcc = cleanAcc.contains("5005004433") || folded.contains("5005004433")
        if (isDpsAcc || folded.contains("penzij") || INV_DPS_WORD_REGEX.containsMatchIn(folded) ||
            folded.contains("conseq") || folded.contains("generali") ||
            folded.contains("allianz") || folded.contains("nn penzij") ||
            folded.contains("nn penze")
        ) {
            return BankTransactionType.INVESTMENT_DPS
        }

        if (folded.contains("albert") || folded.contains("billa") || folded.contains("lidl") ||
            folded.contains("tesco") || folded.contains("penny") || folded.contains("kaufland") ||
            folded.contains("rohlik") || folded.contains("kosik") ||
            folded.contains("globus") || GROCERY_JIP_WORD_REGEX.containsMatchIn(folded) ||
            folded.contains("potraviny") || folded.contains("pekarstvi") ||
            folded.contains("pekarna") || folded.contains("reznictvi")
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
        return buildSummary(
            bankType = summary.detectedBank,
            transactions = updatedTransactions,
            monthEndBalance = summary.monthEndBalance,
            forcedYearMonth = summary.yearMonth.takeIf { StatementTextDecoder.YEAR_MONTH_REGEX.matches(it) },
            accountKey = summary.accountKey
        ).copy(
            isPdfSource = summary.isPdfSource,
            monthEndBalancesByBank = summary.monthEndBalancesByBank
        )
    }

    fun buildSummary(
        bankType: BankType,
        transactions: List<ParsedBankTransaction>,
        monthEndBalance: Double? = null,
        forcedYearMonth: String? = null,
        accountKey: String = ""
    ): StatementParseSummary {
        if (transactions.isEmpty()) return emptySummary(bankType)

        val effectiveForYm = transactions.filter { !it.isExcluded }.ifEmpty { transactions }
        val ymCounts = effectiveForYm.map { it.date.take(7) }.filter { StatementTextDecoder.YEAR_MONTH_REGEX.matches(it) }
            .groupingBy { it }.eachCount()
        val predominantYm = forcedYearMonth ?: ymCounts.maxByOrNull { it.value }?.key
            ?: YearMonth.now(ZoneId.of("Europe/Prague")).toString()

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
            if (tx.isExcluded) continue
            val amt = tx.amount
            when (tx.category) {
                BankTransactionType.INTERNAL_TRANSFER -> {
                    internalTransfers++
                    totalNettedAmount += kotlin.math.abs(amt)
                }
                BankTransactionType.SALARY_VACLAV -> incVaclav += amt
                BankTransactionType.SALARY_ELEONORA -> incEleonora += amt
                BankTransactionType.PARENTAL_BENEFIT -> incEleonora += amt
                BankTransactionType.FAMILY_GIFT -> incOther += amt
                BankTransactionType.OTHER_INFLOW -> incOther += amt
                BankTransactionType.HOUSING_RENT -> {
                    if (amt < 0) expRent += -amt else expRent -= amt
                }
                BankTransactionType.GROCERIES -> {
                    if (amt < 0) expGroceries += -amt else expGroceries -= amt
                }
                BankTransactionType.LIFESTYLE_LIVING,
                BankTransactionType.DINING_RESTAURANT,
                BankTransactionType.TRANSPORTATION,
                BankTransactionType.SHOPPING_GOODS,
                BankTransactionType.HEALTH_DRUGSTORE,
                BankTransactionType.SUBSCRIPTIONS_MEDIA,
                BankTransactionType.SERVICES_UTILITIES,
                BankTransactionType.ATM_CASH,
                BankTransactionType.CHARITY_DONATION,
                BankTransactionType.GENERAL_EXPENSE -> {
                    if (amt < 0) expOther += -amt else expOther -= amt
                }
                BankTransactionType.INVESTMENT_PORTU -> {
                    if (amt < 0) invPortu += -amt else invPortu -= amt
                }
                BankTransactionType.INVESTMENT_DIP -> {
                    if (amt < 0) invDip += -amt else invDip -= amt
                }
                BankTransactionType.INVESTMENT_DPS -> {
                    if (amt < 0) invDps += -amt else invDps -= amt
                }
                BankTransactionType.UNCATEGORIZED -> {
                    if (amt > 0) incOther += amt else expOther += -amt
                }
            }
        }

        if (expRent < 0.0) {
            incOther += -expRent
            expRent = 0.0
        }
        if (expGroceries < 0.0) {
            incOther += -expGroceries
            expGroceries = 0.0
        }
        if (expOther < 0.0) {
            incOther += -expOther
            expOther = 0.0
        }
        if (invPortu < 0.0) invPortu = 0.0
        if (invDip < 0.0) invDip = 0.0
        if (invDps < 0.0) invDps = 0.0

        val totalInflows = incVaclav + incEleonora + incOther
        val totalExpenses = expRent + expGroceries + expOther
        val totalInvested = invPortu + invDip + invDps

        val resolvedAccountKey = accountKey.ifBlank {
            val distinctAccKeys = transactions.mapNotNull { it.accountKey.takeIf(String::isNotBlank) }.distinct()
            if (distinctAccKeys.size == 1) distinctAccKeys[0] else ""
        }
        val taggedTransactions = transactions.map { tx ->
            val withBank = if (bankType != BankType.GENERIC && tx.bankType == BankType.GENERIC) tx.copy(bankType = bankType) else tx
            if (resolvedAccountKey.isNotBlank() && withBank.accountKey.isBlank()) {
                withBank.copy(accountKey = resolvedAccountKey)
            } else {
                withBank
            }
        }

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
            transactions = taggedTransactions,
            accountKey = resolvedAccountKey
        )
    }

    fun mergeSummaries(summaries: List<StatementParseSummary>): StatementParseSummary {
        if (summaries.isEmpty()) return emptySummary(BankType.GENERIC)
        if (summaries.size == 1) return summaries[0]

        val seen = mutableSetOf<String>()
        val uniqueTxs = mutableListOf<ParsedBankTransaction>()
        val balancesByMonthAndBank = mutableMapOf<String, MutableMap<String, Double>>()
        // Track whether multiple distinct same-bank statements without accountKey were imported together
        val multiUnkeyedBanks = summaries.groupBy { it.yearMonth to it.detectedBank.name }
            .filter { (_, grp) ->
                grp.size > 1 && grp.any { it.accountKey.isBlank() } &&
                    grp.mapNotNull { it.monthEndBalance }.distinct().size > 1
            }.keys
        for ((idx, s) in summaries.withIndex()) {
            val summaryCounts = mutableMapOf<String, Int>()
            val isAmbiguousUnkeyed = s.accountKey.isBlank() && (s.yearMonth to s.detectedBank.name) in multiUnkeyedBanks
            val balanceBankKey = when {
                s.accountKey.isNotBlank() -> "${s.detectedBank.name}#${s.accountKey}"
                isAmbiguousUnkeyed -> "${s.detectedBank.name}#ACC_${idx + 1}"
                else -> s.detectedBank.name
            }
            if (s.monthEndBalance != null && s.monthEndBalance.isFinite() && StatementTextDecoder.YEAR_MONTH_REGEX.matches(s.yearMonth)) {
                balancesByMonthAndBank.getOrPut(s.yearMonth) { mutableMapOf() }[balanceBankKey] = s.monthEndBalance
            }
            s.monthEndBalancesByBank.forEach { (ym, bankMap) ->
                balancesByMonthAndBank.getOrPut(ym) { mutableMapOf() }.putAll(bankMap)
            }
            val summaryBalDiscriminator = if (isAmbiguousUnkeyed && s.monthEndBalance != null && s.monthEndBalance.isFinite()) {
                "bal:${String.format(Locale.ROOT, "%.2f", s.monthEndBalance)}"
            } else ""
            for (tx in s.transactions) {
                val effectiveBank = if (tx.bankType != BankType.GENERIC) tx.bankType else s.detectedBank
                val effectiveAccKey = tx.accountKey.ifBlank { s.accountKey }
                val taggedTx = tx.copy(
                    bankType = if (tx.bankType == BankType.GENERIC && effectiveBank != BankType.GENERIC) effectiveBank else tx.bankType,
                    accountKey = effectiveAccKey
                )
                val formattedAmount = String.format(Locale.ROOT, "%.2f", tx.amount)
                // Dedup key intentionally excludes the detected bank: the same physical transaction
                // can surface in statements of different banks (e.g. misdetected bank or a re-exported
                // file). Identity is established by date + amount + counterparty account/name/message/VS,
                // scoped by the source account key (or balance discriminator for ambiguous unkeyed files).
                // Opposite-sign internal transfers never collide because the signed amount differs.
                val accKeyScope = effectiveAccKey.ifBlank { summaryBalDiscriminator }
                val baseKey = "$accKeyScope|${tx.date}|$formattedAmount|${tx.counterpartyAccount}|${tx.counterpartyName}|${tx.message}|${tx.variableSymbol}"
                val count = summaryCounts[baseKey] ?: 0
                summaryCounts[baseKey] = count + 1
                val globalKey = "$baseKey#$count"
                if (seen.add(globalKey)) {
                    uniqueTxs.add(taggedTx)
                }
            }
        }

        val sortedTxs = uniqueTxs.sortedWith(compareBy({ it.date }, { it.amount }))
        val banks = summaries.map { it.detectedBank }.distinct()
        val bankType = if (banks.size == 1) banks[0] else BankType.GENERIC

        val yearMonths = summaries.map { it.yearMonth }.filter { StatementTextDecoder.YEAR_MONTH_REGEX.matches(it) }.distinct().sorted()
        val latestYm = yearMonths.lastOrNull() ?: summaries.first().yearMonth
        val latestBalance = balancesByMonthAndBank[latestYm]?.values?.sum()?.takeIf { it.isFinite() }
            ?: summaries
                .filter { it.monthEndBalance != null && it.monthEndBalance.isFinite() }
                .maxWithOrNull(compareBy<StatementParseSummary> { if (StatementTextDecoder.YEAR_MONTH_REGEX.matches(it.yearMonth)) it.yearMonth else "" })
                ?.monthEndBalance

        return buildSummary(
            bankType = bankType,
            transactions = sortedTxs,
            monthEndBalance = latestBalance,
            forcedYearMonth = latestYm
        ).copy(
            isPdfSource = summaries.any { it.isPdfSource },
            monthEndBalancesByBank = balancesByMonthAndBank.mapValues { it.value.toMap() }
        )
    }

    fun emptySummary(bankType: BankType): StatementParseSummary {
        return StatementParseSummary(
            detectedBank = bankType,
            yearMonth = YearMonth.now(ZoneId.of("Europe/Prague")).toString(),
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
