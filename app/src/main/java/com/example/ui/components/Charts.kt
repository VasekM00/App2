package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.MonteCarloPoint
import com.example.domain.PortfolioYearPoint
import com.example.domain.StressScenarioResult

/**
 * Facade entry point for chart components.
 * Concrete chart implementations are modularized into [com.example.ui.components.charts]:
 * - [com.example.ui.components.charts.NetWorthChart] in TrajectoryChart.kt
 * - [com.example.ui.components.charts.MonteCarloFanChart] in MonteCarloFanChart.kt
 * - [com.example.ui.components.charts.CashFlowProjectionChart] in CashFlowProjectionChart.kt
 * - [com.example.ui.components.charts.StressComparisonChart] in StressTestChart.kt
 * - [com.example.ui.components.charts.DcaAllocationBreakdownBar] in AssetAllocationChart.kt
 * - [com.example.ui.components.charts.ChartMetricPill] in ChartHelpers.kt
 * - [com.example.ui.components.charts.DcaTrajectoryBarChart] in SavingsRateBarChart.kt
 * - [com.example.ui.components.charts.computeNiceAxisMax] and spline paths in ChartHelpers.kt
 */

@Composable
fun NetWorthChart(
    data: List<PortfolioYearPoint>,
    cpiInflationPct: Double = 2.8,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    isRealPurchasingPower: Boolean = false,
    onRealPurchasingPowerChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.charts.NetWorthChart(
        data = data,
        cpiInflationPct = cpiInflationPct,
        ledgerEntries = ledgerEntries,
        isRealPurchasingPower = isRealPurchasingPower,
        onRealPurchasingPowerChange = onRealPurchasingPowerChange,
        modifier = modifier
    )
}

@Composable
fun MonteCarloFanChart(
    points: List<MonteCarloPoint>,
    cpiPct: Double = 2.8,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.charts.MonteCarloFanChart(
        points = points,
        cpiPct = cpiPct,
        modifier = modifier
    )
}

@Composable
fun CashFlowProjectionChart(
    data: List<PortfolioYearPoint>,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.charts.CashFlowProjectionChart(
        data = data,
        modifier = modifier
    )
}

@Composable
fun StressComparisonChart(
    scenarios: List<StressScenarioResult>,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.charts.StressComparisonChart(
        scenarios = scenarios,
        modifier = modifier
    )
}

@Composable
fun DcaAllocationBreakdownBar(
    settings: SettingsEntity,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.charts.DcaAllocationBreakdownBar(
        settings = settings,
        modifier = modifier
    )
}

@Composable
fun DcaTrajectoryBarChart(
    data: List<PortfolioYearPoint>,
    settings: SettingsEntity? = null,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.charts.DcaTrajectoryBarChart(
        data = data,
        settings = settings,
        modifier = modifier
    )
}

@Composable
fun ChartMetricPill(
    label: String,
    value: String,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.charts.ChartMetricPill(
        label = label,
        value = value,
        color = color,
        isBold = isBold,
        modifier = modifier
    )
}

fun computeNiceAxisMax(rawMax: Double, steps: Int = 4): Double =
    com.example.ui.components.charts.computeNiceAxisMax(rawMax, steps)

fun buildMonotoneCubicPath(points: List<Offset>): Path =
    com.example.ui.components.charts.buildMonotoneCubicPath(points)

fun buildMonotoneCubicAreaPath(points: List<Offset>, baselineY: Float): Path =
    com.example.ui.components.charts.buildMonotoneCubicAreaPath(points, baselineY)

fun buildMonotoneBandPath(topPoints: List<Offset>, bottomPoints: List<Offset>): Path =
    com.example.ui.components.charts.buildMonotoneBandPath(topPoints, bottomPoints)
