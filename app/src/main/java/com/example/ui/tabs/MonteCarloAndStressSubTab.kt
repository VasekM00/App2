package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.MonteCarloFanChart
import com.example.ui.components.StressComparisonChart
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import kotlin.math.roundToInt

@Composable
internal fun MonteCarloAndStressSubTab(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit
) {
    val scrollState = rememberScrollState()
    var isHistoricalMode by rememberSaveable { mutableStateOf(state.settings.useHistoricalBootstrap) }
    androidx.compose.runtime.LaunchedEffect(state.settings.useHistoricalBootstrap) {
        isHistoricalMode = state.settings.useHistoricalBootstrap
    }
    val mc = if (isHistoricalMode) state.historicalMonteCarlo else state.monteCarlo
    val survival = if (isHistoricalMode) state.historicalRetirementSurvival else state.retirementSurvival

    val simulationEngineInfo = MetricInfo(
        title = "Simulation Engine Methodology",
        category = "Stochastic Methodology",
        formulaOrRule = "Parametric: Log-normal distribution (mean = Return, sigma = Volatility) | Historical: 3-Year Block-Bootstrap from 1970-2025 empirical returns calibrated to your expected return",
        explanation = "Parametric simulation assumes normal annual return distributions, which underestimates fat-tail risk (e.g. 1973-1974 stagflation, 2000-2002 dot-com bust, 2008 GFC). Block-bootstrap resamples contiguous 3-year historical blocks from 1970-2025 calibrated to your expected return, preserving real multi-year sequence-of-returns drawdowns.",
        practicalImplication = "Historical resampling shows real-world sequence resilience without synthetic distribution assumptions, while remaining directly comparable to your configured return baseline.",
        accentColor = BrandTeal
    )

    val guardrailInfo = MetricInfo(
        title = "Guyton-Klinger Spending Guardrails",
        category = "Retirement Withdrawal Rules",
        formulaOrRule = "Capital Preservation: 10% spending cut if withdrawal rate > 1.2x initial SWR (floored at essential spending). Prosperity Rule: 10% spending increase if withdrawal rate < 0.8x initial SWR.",
        explanation = "In rigid withdrawal models, retirees blindly increase spending by inflation even during prolonged crashes. Guyton-Klinger dynamic rules reduce discretionary spending during market drawdowns while preserving essential living costs, dramatically increasing retirement survival rates.",
        practicalImplication = "Adding flexibility to discretionary expenses provides massive insurance against early retirement sequence-of-returns risk.",
        accentColor = BrandGold
    )

    val taxDragInfo = MetricInfo(
        title = "Czech Dividend Tax Drag",
        category = "Tax Drag Modeling",
        formulaOrRule = "Drag = Dividend Yield (${state.settings.dividendYieldPct}%) x Dividend Tax Rate (${state.settings.dividendTaxRatePct}%) = ${String.format(java.util.Locale.getDefault(), "%.2f%%", FinancialEngine.dividendTaxDragPct(state.settings))} p.a.",
        explanation = "In the Czech Republic, capital gains on securities held for more than 3 years are fully exempt from income tax under Section 4(1)(u) ZDP. However, dividends (and fund-internal withholding taxes in accumulating UCITS ETFs) incur tax drag under Section 8 ZDP. This drag is modeled on taxable brokerage assets (Portu) reducing net nominal return to ${String.format(java.util.Locale.getDefault(), "%.2f%%", FinancialEngine.netTaxableNominalReturnPct(state.settings))}%, while tax-sheltered DIP and DPS accounts compound gross.",
        practicalImplication = "Even a modest 0.27% annual tax drag compounds significantly over 30+ years, making tax-advantaged accounts like DIP crucial.",
        accentColor = BrandBlue
    )

    val successRateInfo = MetricInfo(
        title = "Monte Carlo Success Probability",
        category = "Stochastic Risk Modeling",
        formulaOrRule = "Success = % of simulated paths where portfolio >= 0 across full horizon",
        explanation = "Runs ${state.settings.monteCarloN} randomized market paths incorporating volatility, sequence-of-returns risk, and prolonged market crashes. A success rate above 90% is widely regarded in quantitative financial planning as bulletproof.",
        practicalImplication = "Exposing the portfolio to random sequence shocks prevents the fallacy of assuming smooth average returns.",
        accentColor = BrandTeal
    )

    val survivalInfo = MetricInfo(
        title = "Retirement Sustainability",
        category = "Withdrawal-Phase Risk",
        formulaOrRule = "Survival = % of FIRE paths where the portfolio never depletes over ${survival.horizonYears} years of inflation-adjusted withdrawals",
        explanation = "Continues every simulated path past FIRE, withdrawing your inflation-adjusted lifestyle costs minus indexed state pensions. This answers the question accumulation charts cannot: whether the money actually lasts through retirement.",
        practicalImplication = "A high accumulation success rate with a low sustainability rate means the plan reaches FIRE but is fragile afterwards. Aim for 85%+ on both metrics.",
        accentColor = BrandGold
    )

    val percentileInfo = MetricInfo(
        title = "Multi-Path Percentile Scenarios",
        category = "Stochastic Outcomes",
        formulaOrRule = "P50 (Median), Top 5% (P95 Bull Market), 95th %ile Conservative (P5 Drawdown)",
        explanation = "P50 reflects median expected market performance. The conservative 95th percentile simulates persistent economic adversity (high inflation + depressed equity returns in early retirement years).",
        practicalImplication = "If your plan achieves financial independence even under the conservative 95th percentile run, you possess an immense structural safety margin.",
        accentColor = BrandBlue
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Monte Carlo Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = if (isHistoricalMode) "Historical Simulation (1970-2025)" else "Monte Carlo Simulation (${state.settings.monteCarloN} Runs)",
                    subtitle = if (isHistoricalMode) "Block-bootstrap sequence resampling (Tap for insight)" else "Parametric log-normal distribution (Tap for insight)",
                    badgeText = "${mc.successRatePct.toInt()}% PROBABILITY",
                    accentColor = BrandTeal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .infoTapHold(simulationEngineInfo, onShowInfo)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isHistoricalMode,
                        onClick = { isHistoricalMode = false },
                        label = { Text("Parametric (Normal)", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = isHistoricalMode,
                        onClick = { isHistoricalMode = true },
                        label = { Text("Historical (1970-2025)", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                ProjectionMetricRow(
                    label = "Simulation Engine",
                    value = if (isHistoricalMode) "Historical Bootstrap" else "Parametric (Normal)",
                    isBold = true,
                    highlightColor = BrandTeal,
                    info = simulationEngineInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Overall Success Rate",
                    value = fmtPct(mc.successRatePct),
                    isBold = true,
                    highlightColor = BrandTeal,
                    info = successRateInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Median FIRE Year (P50)",
                    value = mc.medianFireAge?.let { "${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "Beyond 35y",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Best Case FIRE Year (Top 5% market)",
                    value = mc.bestCaseAge?.let { "${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "Beyond 35y",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Conservative FIRE Year (95th %ile)",
                    value = mc.worstCaseAge?.let { "${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "Beyond 35y",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Retirement Sustainability (${survival.horizonYears}-Year Horizon)",
                    value = fmtPct(survival.successRatePct),
                    isBold = true,
                    highlightColor = BrandGold,
                    info = survivalInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Dynamic Spending Guardrails",
                    value = if (survival.guardrailsActive) "Active (${survival.guardrailTriggeredPct.roundToInt()}% cuts)" else "Disabled (Fixed SWR)",
                    isBold = survival.guardrailsActive,
                    highlightColor = if (survival.guardrailsActive) BrandGold else null,
                    info = guardrailInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Dividend Tax Drag (Taxable)",
                    value = "${String.format(java.util.Locale.getDefault(), "%.2f%%", FinancialEngine.dividendTaxDragPct(state.settings))} p.a.",
                    info = taxDragInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Median Wealth at Horizon End",
                    value = fmtCZK(survival.medianEndBalanceToday),
                    info = survivalInfo,
                    onShowInfo = onShowInfo
                )
                survival.medianDepletionAge?.let { depletionAge ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ProjectionMetricRow(
                        label = "Median Depletion Year (failing paths)",
                        value = "${state.settings.baseYear + (depletionAge - state.settings.primaryAge)}",
                        info = survivalInfo,
                        onShowInfo = onShowInfo
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Monte Carlo Fan Chart
        MonteCarloFanChart(points = mc.fanPoints, cpiPct = state.settings.cpiInflationPct)

        Spacer(modifier = Modifier.height(20.dp))

        // Multi-Scenario Stress Chart
        StressComparisonChart(scenarios = state.stressScenarios)

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Economic Stress Regimes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            ColorPill(
                text = "${state.stressScenarios.size} SCENARIOS",
                color = BadRed,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                horizontalPadding = 6.dp,
                verticalPadding = 2.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        state.stressScenarios.forEach { scenario ->
            val scenarioInfo = MetricInfo(
                title = scenario.name,
                category = "Stress Regime Parameters",
                formulaOrRule = "${String.format(java.util.Locale.getDefault(), "%.1f%%", scenario.nominalReturnPct)} Nominal Return | ${String.format(java.util.Locale.getDefault(), "%.1f%%", scenario.cpiInflationPct)} CPI Inflation",
                explanation = scenario.description,
                practicalImplication = "Tests portfolio survivability under non-linear historical stress regimes (such as 1970s stagflation or prolonged tech drawdowns).",
                accentColor = BadRed
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("stress_scenario_card_${scenario.id}"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (scenario.id == "baseline") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = scenario.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        ColorPill(
                            text = scenario.fireAge?.let { "FIRE: ${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "FIRE: > 35y",
                            color = if (scenario.fireAge != null) GoodGreen else BadRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp,
                            info = scenarioInfo,
                            onShowInfo = onShowInfo
                        )
                    }
                    Text(
                        text = scenario.description,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ProjectionMetricRow(
                        label = "Market Return / Inflation:",
                        value = "${String.format(java.util.Locale.getDefault(), "%.1f%%", scenario.nominalReturnPct)} / ${String.format(java.util.Locale.getDefault(), "%.1f%%", scenario.cpiInflationPct)} CPI",
                        info = scenarioInfo,
                        onShowInfo = onShowInfo
                    )
                    ProjectionMetricRow(
                        label = "Today's FIRE Target:",
                        value = fmtCompact(scenario.fireTargetToday),
                        info = scenarioInfo,
                        onShowInfo = onShowInfo
                    )
                    ProjectionMetricRow(
                        label = "Monte Carlo Success Rate:",
                        value = "${String.format(java.util.Locale.getDefault(), "%.1f%%", scenario.successRatePct)}",
                        info = scenarioInfo,
                        onShowInfo = onShowInfo
                    )
                    ProjectionMetricRow(
                        label = "Emergency Reserve Survival:",
                        value = "${String.format(java.util.Locale.getDefault(), "%.1f", scenario.emergencySurvivalMonths)} months",
                        info = scenarioInfo,
                        onShowInfo = onShowInfo
                    )
                    ProjectionMetricRow(
                        label = "Net Worth in ${state.settings.baseYear + (60 - state.settings.primaryAge)}:",
                        value = fmtCompact(scenario.netWorthAt60),
                        isBold = true,
                        highlightColor = BrandTeal,
                        info = scenarioInfo,
                        onShowInfo = onShowInfo
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}
