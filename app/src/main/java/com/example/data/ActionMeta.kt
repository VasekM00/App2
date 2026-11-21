package com.example.data

data class ActionItemInfo(
    val id: String,
    val title: String,
    val description: String
)

object ActionMeta {
    val items = listOf(
        ActionItemInfo(
            "ac1",
            "Route lecturing income to Portu ETF",
            "Transfer every freelance lecturing payment directly into Portu equity ETF on the date received."
        ),
        ActionItemInfo(
            "ac2",
            "Claim eligible tax deductions & credits",
            "Verify spouse tax credit and maximize DIP/DPS tax deductions up to the 48,000 Kč statutory cap."
        ),
        ActionItemInfo(
            "ac3",
            "Verify Portu allocation is 100% equity",
            "Ensure Portu risk profile is set to 10 (100% global equities) to maximize compound growth."
        ),
        ActionItemInfo(
            "ac4",
            "Keep emergency cash in yield account",
            "Ensure operating cash and emergency reserves earn interest in high-yield savings or repo funds."
        ),
        ActionItemInfo(
            "ac5",
            "Optimize monthly DIP contributions",
            "Fine-tune DIP monthly deposits to fully utilize the 48,000 Kč annual pension tax shield."
        ),
        ActionItemInfo(
            "ac6",
            "Plan Eleonora's return-to-work timeline",
            "Align family timeline and determine post-return salary reinvestment rate into ETFs."
        ),
        ActionItemInfo(
            "ac7",
            "Reconcile monthly ledger with bank records",
            "Cross-check actual bank statement transactions against portfolio ledger balances."
        ),
        ActionItemInfo(
            "ac8",
            "Audit subscriptions & recurring fees",
            "Review recurring subscriptions and cancel unused media apps and duplicate services."
        ),
        ActionItemInfo(
            "ac9",
            "Review emergency reserve target",
            "Compare liquid cash reserves against the 6-month essential living cost baseline."
        ),
        ActionItemInfo(
            "ac10",
            "Verify DPS/DIP annual fee caps",
            "Confirm funds comply with the 0.5% TER annual management fee cap."
        ),
        ActionItemInfo(
            "ac11",
            "Re-run Monte Carlo trajectory simulation",
            "Refresh probability projections and percentile cones using current net worth balances."
        ),
        ActionItemInfo(
            "ac12",
            "Export offline JSON data backup",
            "Save an offline JSON backup file of all customized settings and ledger entries."
        )
    )
}
