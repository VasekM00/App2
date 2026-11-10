package com.example.ui.tabs

import androidx.compose.ui.graphics.Color
import com.example.ui.components.MetricInfo

internal object PlanMetricInfos {
    val coastFire = MetricInfo(
        title = "Coast FIRE Milestone",
        category = "Organic Compounding",
        formulaOrRule = "Target / (1 + Real CAGR)^Years_to_Pension",
        explanation = "The amount of invested capital needed today such that, with zero additional contributions, it will compound into your full retirement nest egg by statutory state pension age.",
        practicalImplication = "Reaching Coast FIRE eliminates survival employment pressure; you only need to earn enough to cover current living burn.",
        accentColor = Color(0xFF0F766E)
    )

    val baristaFire = MetricInfo(
        title = "Barista FIRE Milestone",
        category = "Partial Independence",
        formulaOrRule = "(50% Baseline Living Costs) / SWR",
        explanation = "Financial milestone where private portfolio passive returns cover 50% of current household living expenses, with the remaining 50% covered by part-time, low-stress, freelance, or passion work.",
        practicalImplication = "Allows stepping down from high-stress corporate careers significantly earlier while portfolio capital continues compounding toward full FIRE.",
        accentColor = Color(0xFF0F766E)
    )

    val leanFire = MetricInfo(
        title = "Lean FIRE Milestone",
        category = "Essential Independence",
        formulaOrRule = "(75% Baseline Living Costs) / SWR",
        explanation = "Financial independence covering 100% of essential non-negotiable living expenses (housing, utilities, groceries, healthcare) without discretionary lifestyle costs.",
        practicalImplication = "Guarantees absolute basic survival security even in catastrophic economic scenarios.",
        accentColor = Color(0xFF0F766E)
    )

    val standardFire = MetricInfo(
        title = "Standard FIRE Target",
        category = "Full Independence",
        formulaOrRule = "(100% Living Burn - State Pension) / SWR + Bridge Deficit",
        explanation = "Full financial independence sustaining 100% of your current household lifestyle, including discretionary spending, vacations, and child expenses perpetually.",
        practicalImplication = "Private investment portfolio supports 100% of living burn during early retirement and bridges until statutory pensions arrive.",
        accentColor = Color(0xFFD97706)
    )

    val fatFire = MetricInfo(
        title = "Fat FIRE Milestone",
        category = "Abundance & Legacy",
        formulaOrRule = "(130% Enhanced Living Burn) / SWR",
        explanation = "Financial abundance providing an extra 30% spending buffer for frequent travel, luxury, major family support, and generational wealth preservation.",
        practicalImplication = "Offers maximum safety margin against prolonged stagflation or bear markets.",
        accentColor = Color(0xFFD97706)
    )

    val dipDeduction = MetricInfo(
        title = "DIP (Dlouhodobý investiční produkt)",
        category = "Retirement Tax Shield",
        formulaOrRule = "§ 15a ZDP · Up to 48 000 CZK annual personal tax deduction",
        explanation = "Czech long-term investment product allowing you to buy global index ETFs with pre-tax income. Up to 48 000 CZK combined with DPS saves 7 200 CZK (15% bracket) or 11 040 CZK (23% bracket) per person annually.\n\n" +
                "Statutory Deduction Matrix:\n" +
                "• 1 000 CZK/mo (12 000 CZK) → Saves 1 800 CZK (15%)\n" +
                "• 2 000 CZK/mo (24 000 CZK) → Saves 3 600 CZK (15%)\n" +
                "• 3 000 CZK/mo (36 000 CZK) → Saves 5 400 CZK (15%)\n" +
                "• 4 000 CZK/mo (48 000 CZK) → Max 7 200 CZK (15%) / 11 040 CZK (23%)\n\n" +
                "In two-income households, both partners can independently claim up to 48 000 CZK each (saving up to 14 400 CZK combined annually). Note: Deductions require personal taxable income (§ 15 ZDP) and cannot be transferred between partners.",
        statutoryReference = "§ 15a Act No. 586/1992 Coll. (Income Tax Act)",
        practicalImplication = "Requires maintaining the contract for at least 120 months (10 years) and withdrawing only after age 60 for tax-free maturity without clawbacks.",
        accentColor = Color(0xFF16A34A)
    )

    val dpsLepsiPenzijko = MetricInfo(
        title = "DPS 'Lepší Penzijko' Reform (Proposed 2027)",
        category = "State Subsidy & Pension",
        formulaOrRule = "20% state match up to 340 CZK/mo on deposits up to 1,700 CZK/mo",
        explanation = "State supplementary pension savings. Current statutory law provides a 20% flat match (up to 340 CZK/mo) on monthly contributions up to 1,700 CZK/mo.",
        statutoryReference = "Act No. 427/2011 Coll.",
        practicalImplication = "Under current statutory law, deposits between 500 CZK and 1,700 CZK receive a 20% state subsidy (max 340 CZK/mo).",
        accentColor = Color(0xFF0F766E)
    )

    val dpsAge36 = MetricInfo(
        title = "Age 36 One-Third DPS Withdrawal (Proposed 2027)",
        category = "Statutory Liquidity Option",
        formulaOrRule = "Proposed 2027 Reform · 1/3 penalty-free withdrawal",
        explanation = "Proposed reform feature targeting 1. 1. 2027 effect (not available under current 2026 law). Under the proposal, participants in DPS participation funds who reach age 36 with at least 120 months of contributions can withdraw up to one-third of their accumulated balance without penalty or clawbacks.",
        statutoryReference = "Government Draft Amending Act No. 427/2011 Coll. (Lepší penzijko, expected 2027)",
        practicalImplication = "Provides intermediate liquidity for home down payment or major life milestone without forfeiting the pension plan.",
        accentColor = Color(0xFF0F766E)
    )

    val etfTimeTest = MetricInfo(
        title = "3-Year ETF Time Test Exemption",
        category = "Czech Capital Gains Tax",
        formulaOrRule = "§ 4 odst. 1 písm. u) ZDP · 3-year holding test",
        explanation = "Capital gains from selling securities (stocks, ETFs like VWCE/SPPW) held by a natural person for more than 3 years are 100% exempt from Czech personal income tax, health insurance, and social security (subject to 40M CZK annual exempt ceiling under 2025+ consolidation package).",
        statutoryReference = "§ 4 odst. 1 písm. u) Act No. 586/1992 Coll.",
        practicalImplication = "Allows broad liquid ETF portfolios to compound and be liquidated during FIRE with completely tax-free cash returns.",
        accentColor = Color(0xFF16A34A)
    )
}
