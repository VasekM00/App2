package com.example.data

data class ActionItemInfo(
    val id: String,
    val title: String,
    val description: String
)

object ActionMeta {
    val items = listOf(
        ActionItemInfo("ac1", "Route lecturing income to Portu", "Every lecturing payment flows to Portu same day."),
        ActionItemInfo("ac2", "File tax return with eligible claims", "Check spouse credit and DIP deductions."),
        ActionItemInfo("ac3", "Verify Portu allocation is 100% equity", "Avoid long-run return drags."),
        ActionItemInfo("ac4", "Keep emergency cash in >= 4% yield", "Idle cash and reserves should earn interest/yield."),
        ActionItemInfo("ac5", "Adjust DIP contribution level", "Stay liquid or optimize ceiling."),
        ActionItemInfo("ac6", "Plan Eleonora's return-to-work date", "Align career timeline and post-return salary reinvestment."),
        ActionItemInfo("ac7", "Reconcile ledger with bank records", "Verify expenses and investment entries."),
        ActionItemInfo("ac8", "Audit subscriptions & charges", "Cancel unused recurring apps."),
        ActionItemInfo("ac9", "Review emergency reserve level", "Compare reserve against target."),
        ActionItemInfo("ac10", "Review Lepší penzijko DPS/DIP fee caps", "Verify 0.5% TER cap compliance."),
        ActionItemInfo("ac11", "Re-run Monte Carlo simulations", "Refresh projections with current balances."),
        ActionItemInfo("ac12", "Export JSON backup of data", "Keep offline settings backup.")
    )
}
