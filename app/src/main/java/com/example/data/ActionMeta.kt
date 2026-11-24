package com.example.data

import com.example.util.Formatters.fmtExactCZK

data class ActionItemInfo(
    val id: String,
    val title: String,
    val description: String
)

object ActionMeta {
    val items = listOf(
        ActionItemInfo(
            "ac1",
            "Route lecturing income to brokerage ETF",
            "Transfer freelance lecturing payments directly into equity ETFs on the date received."
        ),
        ActionItemInfo(
            "ac2",
            "Claim eligible tax deductions & credits",
            "Verify eligible household tax credits and maximize DIP/DPS tax deductions up to the 48 000 Kč statutory cap."
        ),
        ActionItemInfo(
            "ac3",
            "Verify liquid ETF allocation is 100% equity",
            "Ensure brokerage ETF risk profile targets 100% global equities to maximize long-term compound growth."
        ),
        ActionItemInfo(
            "ac4",
            "Keep emergency cash in yield account",
            "Ensure operating cash and emergency reserves earn interest in high-yield savings or repo funds."
        ),
        ActionItemInfo(
            "ac5",
            "Optimize monthly DIP contributions",
            "Fine-tune DIP monthly deposits to fully utilize the 48 000 Kč annual pension tax shield."
        ),
        ActionItemInfo(
            "ac6",
            "Plan partner's return-to-work timeline",
            "Align household timeline and determine post-return salary reinvestment rate into ETFs."
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

    fun itemsFor(settings: SettingsEntity): List<ActionItemInfo> {
        val capLabel = fmtExactCZK(settings.taxDeductionCeilingAnnual)
        val partnerName = settings.spouseName.ifBlank { "Partner" }
        val showLecturing = !settings.isSingleHousehold &&
            settings.eIncludeLecturing &&
            settings.eLecturingMonthly > 0.0

        return buildList {
            if (showLecturing) {
                add(
                    ActionItemInfo(
                        "ac1",
                        "Route lecturing income to brokerage ETF",
                        "Transfer freelance lecturing payments directly into equity ETFs on the date received."
                    )
                )
            }
            add(
                ActionItemInfo(
                    "ac2",
                    "Claim eligible tax deductions & credits",
                    if (settings.isSingleHousehold) {
                        "Maximize eligible DIP/DPS tax deductions up to the $capLabel statutory cap."
                    } else {
                        "Verify partner/child tax credits and maximize DIP/DPS tax deductions up to the $capLabel statutory cap."
                    }
                )
            )
            add(
                ActionItemInfo(
                    "ac3",
                    "Verify liquid ETF allocation is 100% equity",
                    "Ensure brokerage ETF risk profile targets 100% global equities to maximize long-term compound growth."
                )
            )
            add(
                ActionItemInfo(
                    "ac4",
                    "Keep emergency cash in yield account",
                    "Ensure operating cash and emergency reserves earn interest in high-yield savings or repo funds."
                )
            )
            add(
                ActionItemInfo(
                    "ac5",
                    "Optimize monthly DIP contributions",
                    "Fine-tune DIP monthly deposits to fully utilize the $capLabel annual pension tax shield."
                )
            )
            if (!settings.isSingleHousehold) {
                add(
                    ActionItemInfo(
                        "ac6",
                        "Plan $partnerName's return-to-work timeline",
                        "Align household timeline and determine post-return salary reinvestment rate into ETFs."
                    )
                )
            }
            add(
                ActionItemInfo(
                    "ac7",
                    "Reconcile monthly ledger with bank records",
                    "Cross-check actual bank statement transactions against portfolio ledger balances."
                )
            )
            add(
                ActionItemInfo(
                    "ac8",
                    "Audit subscriptions & recurring fees",
                    "Review recurring subscriptions and cancel unused media apps and duplicate services."
                )
            )
            add(
                ActionItemInfo(
                    "ac9",
                    "Review emergency reserve target",
                    "Compare liquid cash reserves against the 6-month essential living cost baseline."
                )
            )
            add(
                ActionItemInfo(
                    "ac10",
                    "Verify DPS/DIP annual fee caps",
                    "Confirm funds comply with the 0.5% TER annual management fee cap."
                )
            )
            add(
                ActionItemInfo(
                    "ac11",
                    "Re-run Monte Carlo trajectory simulation",
                    "Refresh probability projections and percentile cones using current net worth balances."
                )
            )
            add(
                ActionItemInfo(
                    "ac12",
                    "Export offline JSON data backup",
                    "Save an offline JSON backup file of all customized settings and ledger entries."
                )
            )
        }
    }
}
