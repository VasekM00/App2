package com.example.domain

import com.example.data.SettingsEntity

/**
 * A named what-if scenario expressed as a transform of the user's saved plan.
 * Presets are deliberately relative (never hardcode personal data) so they remain
 * meaningful whatever the current assumptions are.
 */
data class FireScenarioPreset(
    val id: String,
    val title: String,
    val badge: String,
    val description: String,
    val transform: (SettingsEntity) -> SettingsEntity
)

object PresetProfiles {

    private fun scaleDca(s: SettingsEntity, factor: Double): SettingsEntity =
        s.copy(
            portuDcaMonthly = s.portuDcaMonthly.finiteNonNegative() * factor,
            ePortuDcaMonthly = s.ePortuDcaMonthly.finiteNonNegative() * factor
        )

    val PLAN_BASELINE = FireScenarioPreset(
        id = "baseline",
        title = "Plan Baseline",
        badge = "BASE",
        description = "Your saved plan, exactly as it is today.",
        transform = { it }
    )

    val CONSERVATIVE = FireScenarioPreset(
        id = "conservative",
        title = "Conservative",
        badge = "CAUTION",
        description = "Equity return -1.5pp, inflation +0.5pp, 3.5% withdrawal rate and 18% volatility.",
        transform = { s ->
            val baseRet = s.portfolioNominalReturnPct.finiteOr(7.0)
            val baseCpi = s.cpiInflationPct.finiteOr(2.8)
            s.copy(
                portfolioNominalReturnPct = minOf(baseRet, (baseRet - 1.5).coerceAtLeast(1.0)),
                cpiInflationPct = maxOf(baseCpi, (baseCpi + 0.5).coerceAtMost(10.0)),
                safeWithdrawalRatePct = 3.5,
                monteCarloVolatilityPct = 18.0
            )
        }
    )

    val OPTIMISTIC = FireScenarioPreset(
        id = "optimistic",
        title = "Optimistic",
        badge = "BULL",
        description = "Equity return +1.5pp, inflation -0.25pp and 12% volatility.",
        transform = { s ->
            val baseRet = s.portfolioNominalReturnPct.finiteOr(7.0)
            val baseCpi = s.cpiInflationPct.finiteOr(2.8)
            s.copy(
                portfolioNominalReturnPct = maxOf(baseRet, (baseRet + 1.5).coerceAtMost(15.0)),
                cpiInflationPct = minOf(baseCpi, (baseCpi - 0.25).coerceAtLeast(0.0)),
                monteCarloVolatilityPct = 12.0
            )
        }
    )

    val STAGFLATION = FireScenarioPreset(
        id = "stagflation",
        title = "Stagflation",
        badge = "STRESS",
        description = "5% inflation with 6% rent growth, 4.5% equity return and 3.5% SWR.",
        transform = { s ->
            s.copy(
                cpiInflationPct = 5.0,
                rentGrowthPct = 6.0,
                portfolioNominalReturnPct = 4.5,
                safeWithdrawalRatePct = 3.5,
                monteCarloVolatilityPct = 18.0
            )
        }
    )

    val LOWER_SAVINGS = FireScenarioPreset(
        id = "lower_savings",
        title = "Save 25% Less",
        badge = "LIFE",
        description = "Cuts monthly ETF investing by 25% (e.g. a career break or higher costs).",
        transform = { s -> scaleDca(s, 0.75) }
    )

    val HIGHER_SAVINGS = FireScenarioPreset(
        id = "higher_savings",
        title = "Save 25% More",
        badge = "LIFE",
        description = "Raises monthly ETF investing by 25% to test the effect of extra discipline.",
        transform = { s -> scaleDca(s, 1.25) }
    )

    val LATER_RETIREMENT = FireScenarioPreset(
        id = "later_retirement",
        title = "State Pension +3 Years",
        badge = "PENSION",
        description = "Shifts state-pension age three years later (pension reform stress test), extending the private bridge period and raising your FIRE target.",
        transform = { s ->
            s.copy(
                vStatePensionAge = maxOf(s.vStatePensionAge, (s.vStatePensionAge + 3).coerceAtMost(75)),
                eStatePensionAge = maxOf(s.eStatePensionAge, (s.eStatePensionAge + 3).coerceAtMost(75))
            )
        }
    )

    val FX_DRAG_INFLATION = FireScenarioPreset(
        id = "fx_drag",
        title = "FX Drag & CZK Divergence",
        badge = "MACRO",
        description = "Global ETF return -1.0pp from CZK currency appreciation, domestic inflation 3.5%, and 3.25% SWR.",
        transform = { s ->
            val baseRet = s.portfolioNominalReturnPct.finiteOr(7.0)
            s.copy(
                portfolioNominalReturnPct = minOf(baseRet, (baseRet - 1.0).coerceAtLeast(1.0)),
                cpiInflationPct = 3.5,
                safeWithdrawalRatePct = 3.25,
                monteCarloVolatilityPct = 17.0
            )
        }
    )

    val ALL_PRESETS: List<FireScenarioPreset> = listOf(
        PLAN_BASELINE,
        CONSERVATIVE,
        OPTIMISTIC,
        STAGFLATION,
        FX_DRAG_INFLATION,
        LOWER_SAVINGS,
        HIGHER_SAVINGS,
        LATER_RETIREMENT
    )
}
