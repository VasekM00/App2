package com.example.util

import androidx.compose.runtime.Immutable
import com.example.data.LedgerEntryEntity

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
    FAMILY_GIFT,
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

@Immutable
data class ParsedBankTransaction(
    val date: String, // e.g. "2026-09-15"
    val amount: Double,
    val counterpartyAccount: String = "",
    val counterpartyName: String = "",
    val message: String = "",
    val variableSymbol: String = "",
    val category: BankTransactionType = BankTransactionType.UNCATEGORIZED,
    val isNetted: Boolean = (category == BankTransactionType.INTERNAL_TRANSFER),
    val nettingReason: String = "",
    val bankType: BankType = BankType.GENERIC,
    val isExcluded: Boolean = false,
    val originalCurrency: String? = null,
    val originalAmount: Double? = null,
    val exchangeRate: Double? = null
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

@Immutable
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
    val transactions: List<ParsedBankTransaction>,
    val isPdfSource: Boolean = false,
    val monthEndBalancesByBank: Map<String, Map<String, Double>> = emptyMap()
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }

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
