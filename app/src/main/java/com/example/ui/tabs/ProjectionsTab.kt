package com.example.ui.tabs

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.FullCalculationState
import com.example.ui.components.MonteCarloFanChart
import com.example.ui.components.StressComparisonChart
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct

import com.example.data.SettingsEntity
import com.example.ui.components.ScenarioSimulatorChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectionsTab(
    state: FullCalculationState,
    onSensitivityChange: (returnPct: Double?, cpiPct: Double?, swrPct: Double?) -> Unit,
    onApplySettings: (SettingsEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("FIRE", "Investments", "Monte Carlo", "Sensitivity")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("projections_tab")
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            edgePadding = 12.dp,
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
            0 -> FireSubTab(state, onApplySettings)
            1 -> InvestmentsSubTab(state)
            2 -> MonteCarloSubTab(state)
            3 -> SensitivitySubTab(state, onSensitivityChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvestmentsSubTab(state: FullCalculationState) {
    val scrollState = rememberScrollState()
    val s = state.settings
    var selectedViewMode by remember { mutableIntStateOf(0) } // 0 = All, 1 = Balances, 2 = Monthly DCA

    val vaclavTotalBal by remember(s) { derivedStateOf { s.liquidPortfolioCurrent + s.dipBalanceCurrent + s.dpsBalanceCurrent } }
    val vaclavTotalDca by remember(s) { derivedStateOf { s.portuDcaMonthly + s.dipContributionMonthly + s.dpsOwnContributionMonthly } }
    val eTotalBal by remember(s) { derivedStateOf { s.eLiquidPortfolioCurrent + s.eDipBalanceCurrent + s.eDpsBalanceCurrent } }
    val eTotalDca by remember(s) { derivedStateOf { s.ePortuDcaMonthly + s.eDipContributionMonthly + s.eDpsOwnContributionMonthly } }
    val empMonthly by remember(s) { derivedStateOf { (s.employerRetirementAnnual + s.eEmployerRetirementAnnual) / 12.0 } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // View filter tabs
        SecondaryTabRow(
            selectedTabIndex = selectedViewMode,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Tab(
                selected = selectedViewMode == 0,
                onClick = { selectedViewMode = 0 },
                text = { Text("All Overview", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("inv_filter_all")
            )
            Tab(
                selected = selectedViewMode == 1,
                onClick = { selectedViewMode = 1 },
                text = { Text("Balances Only", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("inv_filter_balances")
            )
            Tab(
                selected = selectedViewMode == 2,
                onClick = { selectedViewMode = 2 },
                text = { Text("Monthly DCA", fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("inv_filter_dca")
            )
        }

        // 1. Current Account Balances Card
        if (selectedViewMode == 0 || selectedViewMode == 1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💼 Current Portfolio Balances",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Accumulated Assets",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Monthly Investment DCA Flow Card
        if (selectedViewMode == 0 || selectedViewMode == 2) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔄 Monthly Investment Contributions (DCA)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Monthly Inflows",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(16.dp))
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Václav 👨‍💼",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = BrandTeal)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedViewMode == 0 || selectedViewMode == 1) {
                        Text("💼 CURRENT BALANCES", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Portu: ${fmtCZK(s.liquidPortfolioCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("• DIP: ${fmtCZK(s.dipBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("• DPS: ${fmtCZK(s.dpsBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("Total Balances: ${fmtCZK(vaclavTotalBal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandTeal)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (selectedViewMode == 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }

                    if (selectedViewMode == 0 || selectedViewMode == 2) {
                        Text("🔄 MONTHLY DCA FLOW", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Portu: ${fmtCZK(s.portuDcaMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("• DIP: ${fmtCZK(s.dipContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("• DPS: ${fmtCZK(s.dpsOwnContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("Total Monthly: ${fmtCZK(vaclavTotalDca)}/mo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandTeal)
                    }
                }
            }

            // Eleonora's Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Eleonora 👩‍💼",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = BrandGold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedViewMode == 0 || selectedViewMode == 1) {
                        Text("💼 CURRENT BALANCES", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Portu: ${fmtCZK(s.eLiquidPortfolioCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("• DIP: ${fmtCZK(s.eDipBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("• DPS: ${fmtCZK(s.eDpsBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("Total Balances: ${fmtCZK(eTotalBal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandGold)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (selectedViewMode == 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }

                    if (selectedViewMode == 0 || selectedViewMode == 2) {
                        Text("🔄 MONTHLY DCA FLOW", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Portu: ${fmtCZK(s.ePortuDcaMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("• DIP: ${fmtCZK(s.eDipContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("• DPS: ${fmtCZK(s.eDpsOwnContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("Total Monthly: ${fmtCZK(eTotalDca)}/mo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandGold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lepší penzijko Reform Features & Regulations
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Lepší penzijko & DIP Tax Optimizations 🇨🇿",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                ProjectionMetricRow("0.5% TER Statutory Fee Cap", "Active (0.5% max)")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("1/3 Early Withdrawal at Age 36", fmtCZK(state.dps.earlyWithdrawalLimitAt36))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Youth Subsidy Rate (<30 y/o)", if (state.dps.youthSubsidyActive) "40% Boost (Active)" else "Standard 20%")
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("DIP Tax Refund Base", fmtCZK(state.taxReturnHelper.retirementDeductionBase))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Annual Tax Saved from DIP/DPS", fmtCZK(state.taxReturnHelper.dipSaving), isBold = true, highlightColor = BrandTeal)
            }
        }
    }
}

@Composable
private fun FireSubTab(
    state: FullCalculationState,
    onApplySettings: (SettingsEntity) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Quick FIRE Scenario Simulator Presets
        ScenarioSimulatorChips(
            state = state,
            onApplySettings = onApplySettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "FIRE Trajectory Analysis",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(16.dp))

                ProjectionMetricRow("Safe Withdrawal Rate (SWR)", fmtPct(state.settings.safeWithdrawalRatePct))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow("CPI Inflation Assumption", fmtPct(state.settings.cpiInflationPct))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow("State Pension Age", "${state.settings.statePensionAge} yrs")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow("State Pension Amount / mo", fmtCZK(state.settings.statePensionMonthly))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = BrandTeal
                )

                ProjectionMetricRow(
                    label = "Projected FIRE Year (Dual Income)",
                    value = state.fireDualPoint?.let { "${it.year} (Age ${it.age})" } ?: "Beyond 35y",
                    isBold = true,
                    highlightColor = BrandTeal
                )

                ProjectionMetricRow(
                    label = "Projected FIRE Year (Single Income)",
                    value = state.fireSinglePoint?.let { "${it.year} (Age ${it.age})" } ?: "Beyond 35y",
                    isBold = true,
                    highlightColor = BrandGold
                )
            }
        }
    }
}

@Composable
private fun MonteCarloSubTab(state: FullCalculationState) {
    val scrollState = rememberScrollState()
    val mc = state.monteCarlo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Monte Carlo Simulation Summary",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                ProjectionMetricRow("Overall Success Rate", fmtPct(mc.successRatePct), isBold = true, highlightColor = BrandTeal)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow("Median FIRE Age (P50)", mc.medianFireAge?.let { "Age $it" } ?: "Beyond 35y")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow("Best Case FIRE Age (P95)", mc.bestCaseAge?.let { "Age $it" } ?: "--")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow("Worst Case FIRE Age (P5)", mc.worstCaseAge?.let { "Age $it" } ?: "--")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        MonteCarloFanChart(points = mc.fanPoints)
    }
}

@Composable
private fun SensitivitySubTab(
    state: FullCalculationState,
    onSensitivityChange: (Double?, Double?, Double?) -> Unit
) {
    val scrollState = rememberScrollState()
    var returnSlider by remember { mutableFloatStateOf(state.settings.portfolioNominalReturnPct.toFloat()) }
    var cpiSlider by remember { mutableFloatStateOf(state.settings.cpiInflationPct.toFloat()) }
    var swrSlider by remember { mutableFloatStateOf(state.settings.safeWithdrawalRatePct.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Multi-Scenario Chart
        StressComparisonChart(scenarios = state.stressScenarios)

        Spacer(modifier = Modifier.height(16.dp))

        // Scenario Breakdown Cards
        Text(
            text = "Economic Stress Regimes Breakdown",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
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
                )
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
                        Text(
                            text = scenario.fireAge?.let { "FIRE Age: $it" } ?: "FIRE: Exceeds 35y",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
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

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Interactive Sensitivity Sliders",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Adjust key variables in real-time to test model robustness",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Return slider
                Text(
                    text = "Portfolio Nominal Return: ${String.format("%.1f%%", returnSlider)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = returnSlider,
                    onValueChange = {
                        returnSlider = it
                        onSensitivityChange(it.toDouble(), cpiSlider.toDouble(), swrSlider.toDouble())
                    },
                    valueRange = 4.0f..12.0f,
                    colors = SliderDefaults.colors(thumbColor = BrandTeal, activeTrackColor = BrandTeal),
                    modifier = Modifier.testTag("slider_return")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // CPI slider
                Text(
                    text = "CPI Inflation: ${String.format("%.1f%%", cpiSlider)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = cpiSlider,
                    onValueChange = {
                        cpiSlider = it
                        onSensitivityChange(returnSlider.toDouble(), it.toDouble(), swrSlider.toDouble())
                    },
                    valueRange = 1.0f..8.0f,
                    colors = SliderDefaults.colors(thumbColor = BrandGold, activeTrackColor = BrandGold),
                    modifier = Modifier.testTag("slider_cpi")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // SWR slider
                Text(
                    text = "Safe Withdrawal Rate (SWR): ${String.format("%.1f%%", swrSlider)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Slider(
                    value = swrSlider,
                    onValueChange = {
                        swrSlider = it
                        onSensitivityChange(returnSlider.toDouble(), cpiSlider.toDouble(), it.toDouble())
                    },
                    valueRange = 2.0f..6.0f,
                    modifier = Modifier.testTag("slider_swr")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        returnSlider = state.settings.portfolioNominalReturnPct.toFloat()
                        cpiSlider = state.settings.cpiInflationPct.toFloat()
                        swrSlider = state.settings.safeWithdrawalRatePct.toFloat()
                        onSensitivityChange(null, null, null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_sliders_button")
                ) {
                    Text("Reset Sliders to Base Settings")
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
    highlightColor: androidx.compose.ui.graphics.Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurface
            ) else MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )
    }
}
