package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.charts.MonteCarloFanChart
import com.example.ui.components.charts.StressComparisonChart
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BodyFontFamily
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

    val guardrailUpperX = 1.0 + state.settings.guardrailsUpperThresholdPct / 100.0
    val guardrailLowerX = (1.0 - state.settings.guardrailsLowerThresholdPct / 100.0).coerceAtLeast(0.0)
    val guardrailInfo = MetricInfo(
        title = "Guyton-Klinger Spending Guardrails",
        category = "Retirement Withdrawal Rules",
        formulaOrRule = "Capital Preservation: ${state.settings.guardrailsCutPct.roundToInt()}% spending cut if withdrawal rate > ${String.format(java.util.Locale.ROOT, "%.2fx", guardrailUpperX)} initial SWR (+${state.settings.guardrailsUpperThresholdPct.roundToInt()}%, floored at ${state.settings.guardrailsFloorPct.roundToInt()}% essential). Prosperity Rule: ${state.settings.guardrailsBoostPct.roundToInt()}% boost if < ${String.format(java.util.Locale.ROOT, "%.2fx", guardrailLowerX)} initial SWR (-${state.settings.guardrailsLowerThresholdPct.roundToInt()}%).",
        explanation = "In rigid withdrawal models, retirees blindly increase spending by inflation even during prolonged crashes. Guyton-Klinger dynamic rules reduce discretionary spending during market drawdowns while preserving essential living costs, dramatically increasing retirement survival rates.",
        practicalImplication = "Adding flexibility to discretionary expenses provides massive insurance against early retirement sequence-of-returns risk.",
        accentColor = BrandGold
    )

    val taxDragInfo = MetricInfo(
        title = "Czech Dividend Tax Drag",
        category = "Tax Drag Modeling",
        formulaOrRule = "Drag = Dividend Yield (${state.effectiveSettings.dividendYieldPct}%) x Dividend Tax Rate (${state.effectiveSettings.dividendTaxRatePct}%) = ${String.format(java.util.Locale.ROOT, "%.2f%%", FinancialEngine.dividendTaxDragPct(state.effectiveSettings))}/year",
        explanation = "In the Czech Republic, capital gains on securities held for more than 3 years are fully exempt from personal income tax. However, dividends (and fund-internal withholding taxes in accumulating UCITS ETFs) incur continuous tax drag. This drag is modeled on taxable brokerage assets (Portu) reducing net nominal return to ${String.format(java.util.Locale.ROOT, "%.2f%%", FinancialEngine.netTaxableNominalReturnPct(state.effectiveSettings))}%, while tax-sheltered DIP and DPS accounts compound gross.",
        statutoryReference = "Act No. 586/1992 Coll. (ZDP § 4(1)(u) & § 8)",
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
        formulaOrRule = "P50 (Median), Top 5% (P95 Bull Market), 5th Percentile Conservative (P5 Drawdown)",
        explanation = "P50 reflects median expected market performance. The conservative 5th percentile portfolio outcome (95th percentile retirement age) simulates persistent economic adversity (high inflation + depressed equity returns in early retirement years).",
        practicalImplication = "If your plan achieves financial independence even under the conservative drawdown scenario, you possess an immense structural safety margin.",
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
                    title = if (isHistoricalMode) "Historical Simulation" else "Monte Carlo (${state.settings.monteCarloN} Runs)",
                    subtitle = if (isHistoricalMode) "1970–2025 block-bootstrap · Tap for info" else "Parametric log-normal · Tap for info",
                    badgeText = null,
                    accentColor = BrandTeal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .infoTapHold(simulationEngineInfo, onShowInfo)
                )
                Spacer(modifier = Modifier.height(12.dp))

                val haptic = LocalHapticFeedback.current
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val modes = listOf(
                            false to "Parametric (Normal)",
                            true to "Historical (1970-2025)"
                        )
                        modes.forEach { (modeVal, label) ->
                            val isSelected = isHistoricalMode == modeVal
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                contentColor = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                                shadowElevation = if (isSelected) 1.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.Tab,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isHistoricalMode = modeVal
                                        }
                                    )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 11.5.sp
                                        ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                ProjectionMetricRow(
                    label = "Accumulation Success",
                    value = fmtPct(mc.successRatePct),
                    isBold = true,
                    highlightColor = BrandTeal,
                    info = successRateInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Median FIRE Year (P50)",
                    value = mc.medianFireAge?.let { "${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "Beyond 35 years",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Fastest Case (5th Percentile)",
                    value = mc.bestCaseAge?.let { "${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "Beyond 35 years",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Conservative (95th Percentile)",
                    value = mc.worstCaseAge?.let { "${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "Beyond 35 years",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Withdrawal Survival (${survival.horizonYears} years)",
                    value = fmtPct(survival.successRatePct),
                    isBold = true,
                    highlightColor = BrandGold,
                    info = survivalInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Guardrails",
                    value = if (survival.guardrailsActive) "Active (${survival.guardrailTriggeredPct.roundToInt()}% cuts)" else "Disabled (Fixed SWR)",
                    isBold = survival.guardrailsActive,
                    highlightColor = if (survival.guardrailsActive) BrandGold else null,
                    info = guardrailInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Dividend Tax Drag",
                    value = "${fmtPct(FinancialEngine.dividendTaxDragPct(state.effectiveSettings), 2)}/year",
                    info = taxDragInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Median End Wealth",
                    value = fmtCZK(survival.medianEndBalanceToday),
                    info = survivalInfo,
                    onShowInfo = onShowInfo
                )
                if (survival.successRatePct < 100.0) {
                    survival.medianDepletionAge?.let { depletionAge ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ProjectionMetricRow(
                            label = "Median Depletion (Failed Runs)",
                            value = "${state.settings.baseYear + (depletionAge - state.settings.primaryAge)}",
                            info = survivalInfo,
                            onShowInfo = onShowInfo
                        )
                    }
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
            var scenarioExpanded by rememberSaveable(scenario.id) { mutableStateOf(false) }
            val scenarioInfo = MetricInfo(
                title = scenario.name,
                category = "Stress Regime Parameters",
                formulaOrRule = "${String.format(java.util.Locale.ROOT, "%.1f%%", scenario.nominalReturnPct)} Nominal Return | ${String.format(java.util.Locale.ROOT, "%.1f%%", scenario.cpiInflationPct)} CPI Inflation",
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
                    containerColor = if (scenario.id == "baseline") BrandTeal.copy(alpha = 0.06f)
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (scenario.id == "baseline") BrandTeal.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { scenarioExpanded = !scenarioExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = scenario.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${String.format(java.util.Locale.ROOT, "%.1f%%", scenario.nominalReturnPct)} return · ${String.format(java.util.Locale.ROOT, "%.1f%%", scenario.cpiInflationPct)} CPI",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ColorPill(
                                text = scenario.fireAge?.let { "FIRE: ${state.settings.baseYear + (it - state.settings.primaryAge)}" } ?: "FIRE: > 35 years",
                                color = if (scenario.fireAge != null) GoodGreen else BadRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.dp,
                                info = scenarioInfo,
                                onShowInfo = onShowInfo
                            )
                            Icon(
                                imageVector = if (scenarioExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (scenarioExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (scenarioExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = scenario.description,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                ProjectionMetricRow(
                                    label = "Market Return / Inflation:",
                                    value = "${String.format(java.util.Locale.ROOT, "%.1f%%", scenario.nominalReturnPct)} / ${String.format(java.util.Locale.ROOT, "%.1f%%", scenario.cpiInflationPct)} CPI",
                                    info = scenarioInfo,
                                    onShowInfo = onShowInfo
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                                ProjectionMetricRow(
                                    label = "Today's FIRE Target:",
                                    value = fmtCompact(scenario.fireTargetToday),
                                    info = scenarioInfo,
                                    onShowInfo = onShowInfo
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                                ProjectionMetricRow(
                                    label = "Accumulation Success:",
                                    value = fmtPct(scenario.successRatePct),
                                    info = scenarioInfo,
                                    onShowInfo = onShowInfo
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                                ProjectionMetricRow(
                                    label = "Emergency Reserve Survival:",
                                    value = "${String.format(java.util.Locale.ROOT, "%.1f", scenario.emergencySurvivalMonths)} months",
                                    info = scenarioInfo,
                                    onShowInfo = onShowInfo
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                                val targetAge60Year = state.settings.baseYear + (60 - state.settings.primaryAge).coerceAtLeast(0)
                                ProjectionMetricRow(
                                    label = if (state.settings.primaryAge < 60) "Net Worth in $targetAge60Year:" else "Projected Net Worth ($targetAge60Year):",
                                    value = fmtCompact(scenario.netWorthAt60),
                                    isBold = true,
                                    highlightColor = BrandTeal,
                                    info = scenarioInfo,
                                    onShowInfo = onShowInfo
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}
