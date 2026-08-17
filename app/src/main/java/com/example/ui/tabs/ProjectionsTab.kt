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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.MonteCarloFanChart
import com.example.ui.components.NetWorthChart
import com.example.ui.components.ScenarioSimulatorChips
import com.example.ui.components.StressComparisonChart
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectionsTab(
    state: FullCalculationState,
    onSensitivityChange: (returnPct: Double?, cpiPct: Double?, swrPct: Double?) -> Unit,
    onApplySettings: (SettingsEntity) -> Unit = {},
    initialSubTab: Int = 0,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 2)) }
    val subTabs = listOf("35y Trajectory", "What-If Sandbox", "Monte Carlo & Stress")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("projections_tab")
    ) {
        SecondaryTabRow(
            selectedTabIndex = selectedSubTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("projections_subtab_$index")
                )
            }
        }

        when (selectedSubTab) {
            0 -> TrajectorySubTab(state = state)
            1 -> WhatIfSandboxSubTab(state = state, onApplySettings = onApplySettings, onSensitivityChange = onSensitivityChange)
            2 -> MonteCarloAndStressSubTab(state = state, onSensitivityChange = onSensitivityChange)
        }
    }
}

/**
 * SubTab 0: 35-Year Net Worth Trajectory & FIRE Projections
 */
@Composable
private fun TrajectorySubTab(state: FullCalculationState) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Primary 35-Year Trajectory Chart
        NetWorthChart(data = state.dualTrajectory)

        Spacer(modifier = Modifier.height(16.dp))

        // FIRE Trajectory Analysis Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "FIRE Trajectory Assumptions",
                    subtitle = "Withdrawal rates, inflation & pension targets",
                    badgeText = "MILESTONES",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                ProjectionMetricRow(
                    label = "Projected FIRE Year (Dual Income)",
                    value = state.fireDualPoint?.let { "${it.year} (Age ${it.age})" } ?: "Beyond 35y",
                    isBold = true,
                    highlightColor = BrandTeal
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = "Projected FIRE Year (Single Income)",
                    value = state.fireSinglePoint?.let { "${it.year} (Age ${it.age})" } ?: "Beyond 35y",
                    isBold = true,
                    highlightColor = BrandGold
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow("Today's FIRE Target (3.75% SWR)", fmtCZK(state.fireBaseTargetToday))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow("Expected Portfolio Nominal Return", fmtPct(state.settings.portfolioNominalReturnPct))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow("CPI Inflation Assumption", fmtPct(state.settings.cpiInflationPct))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow("Safe Withdrawal Rate (SWR)", fmtPct(state.settings.safeWithdrawalRatePct))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow("State Pension Age", "${state.settings.statePensionAge} yrs")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow("State Pension Amount / mo", fmtCZK(state.settings.statePensionMonthly))
            }
        }
    }
}

/**
 * SubTab 1: What-If Sandbox & Scenarios
 */
@Composable
private fun WhatIfSandboxSubTab(
    state: FullCalculationState,
    onApplySettings: (SettingsEntity) -> Unit,
    onSensitivityChange: (Double?, Double?, Double?) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Scenario Presets (1-tap macro & life tests)
        ScenarioSimulatorChips(
            state = state,
            onApplySettings = onApplySettings
        )

        // Portfolio Accounts & DCA Flow Breakdown
        PortfolioAccountsView(state = state)
    }
}

/**
 * Accounts, Balances, and Monthly DCA Breakdown Content
 */
@Composable
private fun PortfolioAccountsView(state: FullCalculationState) {
    val s = state.settings
    val vaclavTotalBal by remember(s) { derivedStateOf { s.liquidPortfolioCurrent + s.dipBalanceCurrent + s.dpsBalanceCurrent } }
    val vaclavTotalDca by remember(s) { derivedStateOf { s.portuDcaMonthly + s.dipContributionMonthly + s.dpsOwnContributionMonthly } }
    val eTotalBal by remember(s) { derivedStateOf { s.eLiquidPortfolioCurrent + s.eDipBalanceCurrent + s.eDpsBalanceCurrent } }
    val eTotalDca by remember(s) { derivedStateOf { s.ePortuDcaMonthly + s.eDipContributionMonthly + s.eDpsOwnContributionMonthly } }
    val empMonthly by remember(s) { derivedStateOf { (s.employerRetirementAnnual + s.eEmployerRetirementAnnual) / 12.0 } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Current Account Balances Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Portfolio Balances",
                    subtitle = "Liquid investments, DIP & pension capital",
                    badgeText = "ASSETS",
                    accentColor = BrandTeal
                )

                Spacer(modifier = Modifier.height(14.dp))

                ProjectionMetricRow("Liquid Portu/ETF Portfolio", fmtCZK(s.liquidPortfolioCurrent + s.eLiquidPortfolioCurrent))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Total DIP Investment Balance", fmtCZK(s.dipBalanceCurrent + s.eDipBalanceCurrent))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Total DPS Pension Balance", fmtCZK(s.dpsBalanceCurrent + s.eDpsBalanceCurrent))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Emergency Reserve Cash", fmtCZK(s.emergencyReserveCurrent))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Total Portfolio Net Worth", fmtCZK(state.netWorthTotal), isBold = true, highlightColor = BrandTeal)
            }
        }

        // 2. Monthly Investment DCA Flow Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Monthly DCA Contributions",
                    subtitle = "Recurring automated investing cadence",
                    badgeText = "SAVINGS",
                    accentColor = GoodGreen
                )

                Spacer(modifier = Modifier.height(14.dp))

                ProjectionMetricRow("Portu / ETF Monthly DCA", "${fmtCZK(s.portuDcaMonthly + s.ePortuDcaMonthly)} / mo")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("DIP Monthly Contribution", "${fmtCZK(s.dipContributionMonthly + s.eDipContributionMonthly)} / mo")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("DPS Monthly Own Contribution", "${fmtCZK(s.dpsOwnContributionMonthly + s.eDpsOwnContributionMonthly)} / mo")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                if (empMonthly > 0) {
                    ProjectionMetricRow("Employer Benefit (Monthly Equiv.)", "${fmtCZK(empMonthly)} / mo")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }
                ProjectionMetricRow("Total Combined Monthly Investment", "${fmtCZK(state.investMonthlyTotal)} / mo", isBold = true, highlightColor = BrandTeal)
            }
        }

        // Side-by-Side Account Breakdown (Václav vs. Eleonora)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Václav's Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Václav",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
                        )
                        ColorPill(
                            text = "ACCUMULATING",
                            color = BrandTeal,
                            fontSize = 8.5.sp,
                            horizontalPadding = 5.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    ColorPill(text = "BALANCES", color = MaterialTheme.colorScheme.primary, fontSize = 8.sp, horizontalPadding = 4.dp, verticalPadding = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Portu: ${fmtCZK(s.liquidPortfolioCurrent)}", style = MaterialTheme.typography.bodySmall)
                    Text("• DIP: ${fmtCZK(s.dipBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                    Text("• DPS: ${fmtCZK(s.dpsBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                    Text("Total: ${fmtCZK(vaclavTotalBal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandTeal)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    ColorPill(text = "MONTHLY DCA", color = GoodGreen, fontSize = 8.sp, horizontalPadding = 4.dp, verticalPadding = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Portu: ${fmtCZK(s.portuDcaMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                    Text("• DIP: ${fmtCZK(s.dipContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                    Text("• DPS: ${fmtCZK(s.dpsOwnContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                    Text("Total: ${fmtCZK(vaclavTotalDca)}/mo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandTeal)
                }
            }

            // Eleonora's Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Eleonora",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                        )
                        ColorPill(
                            text = "ACCUMULATING",
                            color = BrandGold,
                            fontSize = 8.5.sp,
                            horizontalPadding = 5.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    ColorPill(text = "BALANCES", color = MaterialTheme.colorScheme.primary, fontSize = 8.sp, horizontalPadding = 4.dp, verticalPadding = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Portu: ${fmtCZK(s.eLiquidPortfolioCurrent)}", style = MaterialTheme.typography.bodySmall)
                    Text("• DIP: ${fmtCZK(s.eDipBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                    Text("• DPS: ${fmtCZK(s.eDpsBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                    Text("Total: ${fmtCZK(eTotalBal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandGold)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    ColorPill(text = "MONTHLY DCA", color = GoodGreen, fontSize = 8.sp, horizontalPadding = 4.dp, verticalPadding = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Portu: ${fmtCZK(s.ePortuDcaMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                    Text("• DIP: ${fmtCZK(s.eDipContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                    Text("• DPS: ${fmtCZK(s.eDpsOwnContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                    Text("Total: ${fmtCZK(eTotalDca)}/mo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandGold)
                }
            }
        }

        // Lepší penzijko Reform Features & Regulations
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Lepší penzijko & DIP Tax Shield",
                    subtitle = "Czech statutory fee caps & youth incentives",
                    badgeText = "CZ REFORM",
                    accentColor = BrandGold
                )
                Spacer(modifier = Modifier.height(14.dp))

                ProjectionMetricRow("0.5% TER Statutory Fee Cap", "Active (0.5% max)")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("1/3 Early Withdrawal at Age 36", fmtCZK(state.dps.earlyWithdrawalLimitAt36))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Youth Subsidy Rate (<${state.settings.dpsYouthAgeLimit} y/o)", if (state.dps.youthSubsidyActive) "40% Boost (Active)" else "Standard 20%")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("DIP Tax Refund Base", fmtCZK(state.taxReturnHelper.retirementDeductionBase))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Annual Tax Saved from DIP/DPS", fmtCZK(state.taxReturnHelper.dipSaving), isBold = true, highlightColor = BrandTeal)
            }
        }
    }
}

/**
 * SubTab 2: Monte Carlo Simulation & Macro Stress Regimes
 */
@Composable
private fun MonteCarloAndStressSubTab(
    state: FullCalculationState,
    onSensitivityChange: (Double?, Double?, Double?) -> Unit
) {
    val scrollState = rememberScrollState()
    val mc = state.monteCarlo

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
                    title = "Monte Carlo 1,000 Runs",
                    subtitle = "Confidence distribution across market sequences",
                    badgeText = "${mc.successRatePct.toInt()}% PROBABILITY",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                ProjectionMetricRow("Overall Success Rate", fmtPct(mc.successRatePct), isBold = true, highlightColor = BrandTeal)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow("Median FIRE Age (P50)", mc.medianFireAge?.let { "Age $it" } ?: "Beyond 35y")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow("Best Case FIRE Age (Top 5% market)", mc.bestCaseAge?.let { "Age $it" } ?: "--")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow("Conservative FIRE Age (95th %ile)", mc.worstCaseAge?.let { "Age $it" } ?: "--")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Monte Carlo Fan Chart
        MonteCarloFanChart(points = mc.fanPoints)

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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("stress_scenario_card_${scenario.id}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (scenario.id == "baseline") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${scenario.iconEmoji} ${scenario.name}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        ColorPill(
                            text = scenario.fireAge?.let { "FIRE: Age $it" } ?: "FIRE: > 35y",
                            color = if (scenario.fireAge != null) GoodGreen else BadRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    Text(
                        text = scenario.description,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ProjectionMetricRow(
                        label = "Market Return / Inflation:",
                        value = "${String.format("%.1f%%", scenario.nominalReturnPct)} / ${String.format("%.1f%%", scenario.cpiInflationPct)} CPI"
                    )
                    ProjectionMetricRow(
                        label = "Today's FIRE Target:",
                        value = fmtCompact(scenario.fireTargetToday)
                    )
                    ProjectionMetricRow(
                        label = "Monte Carlo Success Rate:",
                        value = "${String.format("%.1f%%", scenario.successRatePct)}"
                    )
                    ProjectionMetricRow(
                        label = "Emergency Reserve Survival:",
                        value = "${String.format("%.1f", scenario.emergencySurvivalMonths)} months"
                    )
                    ProjectionMetricRow(
                        label = "Net Worth at Age 60:",
                        value = fmtCompact(scenario.netWorthAt60),
                        isBold = true,
                        highlightColor = BrandTeal
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectionMetricRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    highlightColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = value,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurface
            ) else MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )
    }
}
