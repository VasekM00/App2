package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "imported_bank_transactions",
    indices = [
        Index(value = ["yearMonth"]),
        Index(value = ["bankName"])
    ]
)
data class ImportedBankTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearMonth: String, // e.g. "2026-07"
    val bankName: String,  // e.g. "MONETA", "CSOB", "MBANK", "REVOLUT", "GENERIC"
    val date: String,      // e.g. "2026-07-20"
    val amount: Double,    // negative for debit/expense, positive for credit/inflow
    val counterpartyAccount: String = "",
    val counterpartyName: String = "",
    val message: String = "",
    val variableSymbol: String = "",
    val category: String = "LIFESTYLE_LIVING", // BankTransactionType name
    val isNetted: Boolean = false,
    val nettingReason: String = "",
    val matchedTxId: Long? = null
)
