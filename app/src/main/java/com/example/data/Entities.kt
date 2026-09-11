package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ledger_entries",
    indices = [Index(value = ["yearMonth"], unique = true)]
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearMonth: String, // e.g., "2026-04"
    val incVaclav: Double = 0.0,
    val incEleonora: Double = 0.0,
    val incUnforeseen: Double = 0.0,
    val expRent: Double = 0.0,
    val expGroceries: Double = 0.0,
    val expOther: Double = 0.0,
    val notes: String = "",
    val portfolioBalanceAtMonthEnd: Double = 0.0,
    val pensionBalanceAtMonthEnd: Double = 0.0,
    val emergencyReserveAtMonthEnd: Double = 0.0
) {
    val totalNetWorthAtMonthEnd: Double
        get() = portfolioBalanceAtMonthEnd + pensionBalanceAtMonthEnd + emergencyReserveAtMonthEnd
}

@Entity(
    tableName = "action_states"
)
data class ActionStateEntity(
    @PrimaryKey val actionKey: String, // e.g. "2026_ac1"
    val year: Int,
    val actionId: String,
    val isDone: Boolean
)

