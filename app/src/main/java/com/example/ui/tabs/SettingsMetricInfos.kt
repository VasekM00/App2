package com.example.ui.tabs

import androidx.compose.ui.graphics.Color
import com.example.ui.components.MetricInfo

object SettingsMetricInfos {
    val swr = MetricInfo(
        title = "Safe Withdrawal Rate (SWR)",
        category = "Actuarial Science",
        formulaOrRule = "Initial Annual Spend / Starting Portfolio Size",
        explanation = "The percentage of your investment portfolio withdrawn in year 1 of retirement, subsequently adjusted annually for CPI inflation. Based on the Trinity Study (Bengen, 1994). A 3.5% SWR historically provides a 99%+ 40-year portfolio survival rate.",
        statutoryReference = "Trinity Study & Modern Portfolio Theory",
        practicalImplication = "Dropping SWR from 4.0% to 3.5% provides extra safety margin against sequence-of-returns risk.",
        accentColor = Color(0xFF0D9488)
    )

    val safetyBuffer = MetricInfo(
        title = "FIRE Safety Capital Buffer",
        category = "Risk Management",
        formulaOrRule = "Target Base Portfolio * (1 + Safety Buffer %)",
        explanation = "An additional discretionary capital cushion added on top of your baseline FIRE nest egg. Shields against healthcare shocks, extended deep recessions, or unforeseen property maintenance.",
        practicalImplication = "A 10% buffer on a 10M Kč target adds 1,000,000 Kč in extra margin of safety.",
        accentColor = Color(0xFFD97706)
    )

    val partnerCredit = MetricInfo(
        title = "Partner Tax Credit",
        category = "Czech Tax Code",
        formulaOrRule = "24,840 Kč/year deduction if partner's own income <= 68,000 Kč",
        explanation = "Annual tax credit claimed when caring for a child under 3 years old and partner's own annual gross income does not exceed 68,000 Kč. Crucially, state parental allowance is legally excluded from this income ceiling.",
        statutoryReference = "§ 35ba(1)(b) Act No. 586/1992 Coll.",
        practicalImplication = "Directly reduces personal income tax liability by up to 24,840 Kč per year (2,070 Kč/month).",
        accentColor = Color(0xFF059669)
    )

    val childBonus = MetricInfo(
        title = "Child Tax Credit & Bonus",
        category = "Czech Tax Code",
        formulaOrRule = "1st child: 15,204 Kč/year · 2nd child: 22,320 Kč/year · 3rd+: 27,840 Kč/year",
        explanation = "Progressive tax allowance per dependent child. If your tax liability reaches zero, the unused portion is paid out by the state directly to you as a cash tax bonus.",
        statutoryReference = "§ 35c Act No. 586/1992 Coll.",
        practicalImplication = "Provides direct cash rebates when tax liability is low.",
        accentColor = Color(0xFF0D9488)
    )

    fun monteCarloFor(runs: Int, volatilityPct: Double, useHistorical: Boolean): MetricInfo {
        val method = if (useHistorical) "Block-Bootstrap (1970-2025)" else "Geometric Brownian Motion"
        val volFormatted = String.format(java.util.Locale.ROOT, "%.1f%%", volatilityPct)
        return MetricInfo(
            title = "Monte Carlo Volatility Modeling",
            category = "Statistical Simulation",
            formulaOrRule = "$method · $runs Iterations · $volFormatted Std Dev",
            explanation = "Simulates $runs randomized market return paths calibrated to $volFormatted annualized asset volatility and configured CPI inflation. Renders 5th percentile (bear worst case), 50th percentile (median), and 95th percentile (bull optimal) wealth trajectories.",
            practicalImplication = "Ensures financial planning accounts for unpredictable market sequences rather than unrealistic linear returns.",
            accentColor = Color(0xFF0D9488)
        )
    }

    val monteCarlo = monteCarloFor(runs = 400, volatilityPct = 16.0, useHistorical = false)

    val statePensionAge = MetricInfo(
        title = "State Pension Age",
        category = "Czech Pension System",
        formulaOrRule = "State Pillar 1 retirement age",
        explanation = "The legal retirement age when state pillar 1 pension benefits begin. In the FIRE model, your private investment portfolio bridges living expenses from early retirement until this age.",
        statutoryReference = "Act No. 155/1995 Coll. on Pension Insurance",
        practicalImplication = "Earlier personal retirement requires a larger private bridge fund to cover living costs before state pensions kick in.",
        accentColor = Color(0xFF0D9488)
    )

    val historicalBootstrap = MetricInfo(
        title = "Historical 1970–2025 Bootstrap",
        category = "Statistical Simulation",
        formulaOrRule = "Empirical Resampling · 56-Year Real Market Returns",
        explanation = "Samples real annual total return blocks from 1970 to 2025 (MSCI World / S&P 500 with dividends reinvested). Unlike synthetic Gaussian curves, historical bootstrapping preserves real-world fat tails, severe multi-year drawdowns (1973–74 stagflation, 2000–02 dot-com bust, 2008 GFC), and return autocorrelation.",
        practicalImplication = "Provides a stress-tested reality check against real market history instead of idealized theoretical distributions.",
        accentColor = Color(0xFF0D9488)
    )

    val prolongChildSupport = MetricInfo(
        title = "Prolong Child Support in FIRE",
        category = "Family Budgeting",
        formulaOrRule = "Preserve child expenses in post-FIRE lifestyle target",
        explanation = "When enabled, your target financial nest egg accounts for ongoing child allowances, education costs, and university support instead of assuming child expenses drop to zero immediately upon early retirement.",
        practicalImplication = "Essential if you plan to achieve FIRE while your children are teenagers or attending university.",
        accentColor = Color(0xFFD97706)
    )
}
