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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.MetricInfo
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.MonteCarloFanChart
import com.example.ui.components.NetWorthChart
import com.example.ui.components.ScenarioSimulatorChips
import com.example.ui.components.StressComparisonChart
import com.example.ui.components.infoTapHold
import com.example.ui.components.rememberMetricInfoState
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
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
    var selectedSubTab by rememberSaveable(initialSubTab) { mutableIntStateOf(initialSubTab.coerceIn(0, 2)) }
    val subTabs = listOf("Trajectory", "What-If Sandbox", "Monte Carlo")
    val infoState = rememberMetricInfoState()

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
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("projections_subtab_$index")
                )
            }
        }

        when (selectedSubTab) {
            0 -> TrajectorySubTab(state = state, onShowInfo = { infoState.show(it) })
            1 -> WhatIfSandboxSubTab(state = state, onApplySettings = onApplySettings, onSensitivityChange = onSensitivityChange, onShowInfo = { infoState.show(it) })
            2 -> MonteCarloAndStressSubTab(state = state, onSensitivityChange = onSensitivityChange, onShowInfo = { infoState.show(it) })
        }
    }

    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
}

/**
 * SubTab 0: 35-Year Net Worth Trajectory & FIRE Projections
 */
@Composable
private fun TrajectorySubTab(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit
) {
    val scrollState = rememberScrollState()

    val swrInfo = MetricInfo(
        title = "Safe Withdrawal Rate (SWR)",
        category = "Retirement Actuarial Math",
        formulaOrRule = "Initial Annual Draw = Portfolio Capital × SWR (Adjusted for CPI Yearly)",
        explanation = "While the classic Trinity Study established a 4.0% rule for a 30-year retirement, an early FIRE horizon of 40–50 years introduces substantial longevity and sequence-of-returns risks. Calibrating at 3.25%–3.50% delivers a 98%+ historical survival rate without principal exhaustion.",
        statutoryReference = "Trinity Study & Bengen Longevity Analysis",
        practicalImplication = "A 3.5% SWR requires 28.6× annual living expenses in invested assets, providing a resilient buffer against extended market drawdowns.",
        accentColor = BrandTeal
    )

    val returnInflationInfo = MetricInfo(
        title = "Nominal vs Real Compound Returns",
        category = "Macroeconomic Assumptions",
        formulaOrRule = "Real CAGR = (1 + Nominal Return) / (1 + CPI Inflation) - 1",
        explanation = "With an expected ${fmtPct(state.settings.portfolioNominalReturnPct)} nominal return and ${fmtPct(state.settings.cpiInflationPct)} inflation, your equity assets grow at ~${String.format("%.2f%%", ((1 + state.settings.portfolioNominalReturnPct/100.0)/(1 + state.settings.cpiInflationPct/100.0) - 1.0) * 100.0)} net purchasing power annually.",
        statutoryReference = "Fisher Equation of Real Interest",
        practicalImplication = "Maintaining realistic inflation assumptions guarantees your FIRE target in today's CZK remains accurate in future purchasing power.",
        accentColor = BrandGold
    )

    val pensionBridgeInfo = MetricInfo(
        title = "State Pension Bridge Years",
        category = "Actuarial Horizon",
        formulaOrRule = "Bridge Horizon = State Pension Age (${state.settings.statePensionAge}) - Target FIRE Age",
        explanation = "The actuarial phase between early retirement and statutory state pension entitlement. During this bridge, your investment portfolio must support 100% of household cash outlays. Once state pension arrives (${fmtCZK(state.settings.statePensionMonthly)}/mo), the required portfolio draw drops dramatically.",
        statutoryReference = "§ 32 Act No. 155/1995 Coll.",
        practicalImplication = "Dynamic bridge modeling avoids over-saving millions of CZK by accounting for future guaranteed state annuity cash flows.",
        accentColor = BrandTeal
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Primary 35-Year Trajectory Chart
        NetWorthChart(
            data = state.dualTrajectory,
            cpiInflationPct = state.settings.cpiInflationPct
        )

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
                    subtitle = "Withdrawal rates, inflation & pension targets (Tap for deep insight)",
                    badgeText = "MILESTONES",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                ProjectionMetricRow(
                    label = "Projected FIRE Year (Dual Income)",
                    value = state.fireDualPoint?.let { "${it.year} (Age ${it.age})" } ?: "Beyond 35y",
                    isBold = true,
                    highlightColor = BrandTeal,
                    info = MetricInfo(
                        title = "Dual-Income FIRE Date",
                        category = "Household Horizon",
                        explanation = "Models both Václav and Eleonora contributing via combined DCA, DIP, and employer matching until aggregate wealth covers the shared household lifestyle budget.",
                        accentColor = BrandTeal
                    ),
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = "Projected FIRE Year (Single Income)",
                    value = state.fireSinglePoint?.let { "${it.year} (Age ${it.age})" } ?: "Beyond 35y",
                    isBold = true,
                    highlightColor = BrandGold,
                    info = MetricInfo(
                        title = "Single-Income Resilience Test",
                        category = "Household Horizon",
                        explanation = "Calculates the independent FIRE horizon if funded purely by the primary earner's savings capacity, providing a baseline stress test.",
                        accentColor = BrandGold
                    ),
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = "Today's FIRE Target (${fmtPct(state.settings.safeWithdrawalRatePct)} SWR)",
                    value = fmtCZK(state.fireBaseTargetToday),
                    info = swrInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = "Expected Portfolio Nominal Return",
                    value = fmtPct(state.settings.portfolioNominalReturnPct),
                    info = returnInflationInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = "CPI Inflation Assumption",
                    value = fmtPct(state.settings.cpiInflationPct),
                    info = returnInflationInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = "Safe Withdrawal Rate (SWR)",
                    value = fmtPct(state.settings.safeWithdrawalRatePct),
                    info = swrInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = "State Pension Age",
                    value = "${state.settings.statePensionAge} yrs",
                    info = pensionBridgeInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = "State Pension Amount / mo",
                    value = fmtCZK(state.settings.statePensionMonthly),
                    info = pensionBridgeInfo,
                    onShowInfo = onShowInfo
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

/**
 * SubTab 1: What-If Sandbox & Scenarios
 */
@Composable
private fun WhatIfSandboxSubTab(
    state: FullCalculationState,
    onApplySettings: (SettingsEntity) -> Unit,
    onSensitivityChange: (Double?, Double?, Double?) -> Unit,
    onShowInfo: (MetricInfo) -> Unit
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
        PortfolioAccountsView(state = state, onShowInfo = onShowInfo)
    }
}

/**
 * Accounts, Balances, and Monthly DCA Breakdown Content
 */
@Composable
private fun PortfolioAccountsView(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit
) {
    val s = state.settings
    val vaclavTotalBal by remember(s) { derivedStateOf { s.liquidPortfolioCurrent + s.dipBalanceCurrent + s.dpsBalanceCurrent } }
    val vaclavTotalDca by remember(s) { derivedStateOf { s.portuDcaMonthly + s.dipContributionMonthly + s.dpsOwnContributionMonthly } }
    val eTotalBal by remember(s) { derivedStateOf { s.eLiquidPortfolioCurrent + s.eDipBalanceCurrent + s.eDpsBalanceCurrent } }
    val eTotalDca by remember(s) { derivedStateOf { s.ePortuDcaMonthly + s.eDipContributionMonthly + s.eDpsOwnContributionMonthly } }
    val empMonthly by remember(s) { derivedStateOf { (s.employerRetirementAnnual + s.eEmployerRetirementAnnual) / 12.0 } }

    val feeCapInfo = MetricInfo(
        title = "Statutory DPS Fee Cap (0.50% TER)",
        category = "Pension Cost Efficiency",
        formulaOrRule = "Management Fee TER <= 0.50% p.a.",
        explanation = "Statutory cap on annual asset management fees for participating pension funds (DPS). Legacy transformed funds charge 1.0%–1.5% TER with 0% real long-term return guarantees that erode capital to inflation.",
        statutoryReference = "Act No. 427/2011 Coll. (Supplementary Pension Savings)",
        practicalImplication = "A 0.50% fee cap preserves ~18% more final wealth over a 30-year accumulation horizon compared to 1.50% legacy fee structures.",
        accentColor = BrandTeal
    )

    val earlyWithdrawalInfo = MetricInfo(
        title = "10-Year Partial Pension Liquidity Rule",
        category = "Pension Flexibility",
        formulaOrRule = "Up to 33.3% of own contributions after 120 deposit months",
        explanation = "Savers who open a DPS contract young can withdraw up to one-third of their accumulated personal contributions after 10 years without canceling the contract, incurring tax penalties, or forfeiting state subsidies on the remaining balance.",
        statutoryReference = "§ 22 Act No. 427/1994 / 427/2011 Coll.",
        practicalImplication = "Allows leveraging DPS as a flexible mid-career bridge liquidity reservoir (e.g. real estate down payment or emergency bridge buffer).",
        accentColor = BrandGold
    )

    val youthSubsidyInfo = MetricInfo(
        title = "DPS Youth State Match (<30 y/o)",
        category = "State Subsidy Optimization",
        formulaOrRule = "40% state match up to 680 CZK/mo on 1,700 CZK deposit",
        explanation = "Provides a doubled 40% matching contribution for participants under age 30, up to a monthly maximum of 680 CZK on a 1,700 CZK deposit (vs standard 20% / 340 CZK).",
        statutoryReference = "Act No. 427/2011 Coll. Amendments",
        practicalImplication = "Yields an instantaneous, guaranteed 40% risk-free return on deposits prior to turning 30.",
        accentColor = GoodGreen
    )

    val dipTaxShieldInfo = MetricInfo(
        title = "Retirement Tax Shield (§ 15a ZDP)",
        category = "Czech Tax Optimization",
        formulaOrRule = "Tax Deduction = min(DIP + DPS Deposits - Subsidy Threshold, 48,000 CZK)",
        explanation = "Enables deducting up to 48,000 CZK combined annually from your taxable income base. At the 15% income tax rate, this yields a 7,200 CZK refund; at 23%, an 11,040 CZK refund.",
        statutoryReference = "§ 15 odst. 5 & § 15a Act No. 586/1992 Coll. (ZDP)",
        practicalImplication = "Contributing 4,000 CZK/month into low-cost index ETF DIP accounts captures full tax relief while maximizing compound equity returns.",
        accentColor = BrandTeal
    )

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

                ProjectionMetricRow("Liquid Brokerage / ETF Portfolio", fmtCZK(s.liquidPortfolioCurrent + s.eLiquidPortfolioCurrent))
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

                ProjectionMetricRow("Monthly Brokerage / ETF Investment", "${fmtCZK(s.portuDcaMonthly + s.ePortuDcaMonthly)} / mo")
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

        if (s.isSingleHousehold) {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            text = s.primaryName,
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
                    Text("• Brokerage / ETF: ${fmtCZK(s.liquidPortfolioCurrent)}", style = MaterialTheme.typography.bodySmall)
                    Text("• DIP: ${fmtCZK(s.dipBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                    Text("• DPS: ${fmtCZK(s.dpsBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                    Text("Total: ${fmtCZK(vaclavTotalBal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandTeal)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    ColorPill(text = "MONTHLY DCA", color = GoodGreen, fontSize = 8.sp, horizontalPadding = 4.dp, verticalPadding = 1.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Brokerage / ETF: ${fmtCZK(s.portuDcaMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                    Text("• DIP: ${fmtCZK(s.dipContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                    Text("• DPS: ${fmtCZK(s.dpsOwnContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                    Text("Total: ${fmtCZK(vaclavTotalDca)}/mo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandTeal)
                }
            }
        } else {
            // Side-by-Side Account Breakdown (Václav & Eleonora)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Earner Card (Václav)
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
                                text = s.primaryName,
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
                        Text("• Brokerage / ETF: ${fmtCZK(s.liquidPortfolioCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("• DIP: ${fmtCZK(s.dipBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("• DPS: ${fmtCZK(s.dpsBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("Total: ${fmtCZK(vaclavTotalBal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandTeal)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                        ColorPill(text = "MONTHLY DCA", color = GoodGreen, fontSize = 8.sp, horizontalPadding = 4.dp, verticalPadding = 1.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Brokerage / ETF: ${fmtCZK(s.portuDcaMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("• DIP: ${fmtCZK(s.dipContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("• DPS: ${fmtCZK(s.dpsOwnContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("Total: ${fmtCZK(vaclavTotalDca)}/mo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandTeal)
                    }
                }

                // Wife Card (Eleonora)
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
                                text = "Eleonora (Wife)",
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
                        Text("• Brokerage / ETF: ${fmtCZK(s.eLiquidPortfolioCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("• DIP: ${fmtCZK(s.eDipBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("• DPS: ${fmtCZK(s.eDpsBalanceCurrent)}", style = MaterialTheme.typography.bodySmall)
                        Text("Total: ${fmtCZK(eTotalBal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandGold)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                        ColorPill(text = "MONTHLY DCA", color = GoodGreen, fontSize = 8.sp, horizontalPadding = 4.dp, verticalPadding = 1.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Brokerage / ETF: ${fmtCZK(s.ePortuDcaMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("• DIP: ${fmtCZK(s.eDipContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("• DPS: ${fmtCZK(s.eDpsOwnContributionMonthly)}/mo", style = MaterialTheme.typography.bodySmall)
                        Text("Total: ${fmtCZK(eTotalDca)}/mo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = BrandGold)
                    }
                }
            }
        }

        // Pension & DIP Statutory Regulations
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Pension & DIP Statutory Regulations",
                    subtitle = "Statutory fee caps, subsidies & tax shield (§ 15a ZDP) (Tap for insight)",
                    badgeText = "STATUTORY RULES",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                ProjectionMetricRow(
                    label = "0.5% TER Statutory Fee Cap",
                    value = "Active (0.5% max)",
                    info = feeCapInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow(
                    label = "1/3 Early Withdrawal at Age 36",
                    value = fmtCZK(state.dps.earlyWithdrawalLimitAt36),
                    info = earlyWithdrawalInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow(
                    label = "Youth Subsidy Rate (<${state.settings.dpsYouthAgeLimit} y/o)",
                    value = if (state.dps.youthSubsidyActive) "40% Boost (Active)" else "Standard 20%",
                    info = youthSubsidyInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow(
                    label = "DIP Tax Refund Base",
                    value = fmtCZK(state.taxReturnHelper.retirementDeductionBase),
                    info = dipTaxShieldInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow(
                    label = "Annual Tax Saved from DIP/DPS",
                    value = fmtCZK(state.taxReturnHelper.dipSaving),
                    isBold = true,
                    highlightColor = BrandTeal,
                    info = dipTaxShieldInfo,
                    onShowInfo = onShowInfo
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

/**
 * SubTab 2: Monte Carlo Simulation & Macro Stress Regimes
 */
@Composable
private fun MonteCarloAndStressSubTab(
    state: FullCalculationState,
    onSensitivityChange: (Double?, Double?, Double?) -> Unit,
    onShowInfo: (MetricInfo) -> Unit
) {
    val scrollState = rememberScrollState()
    val mc = state.monteCarlo

    val successRateInfo = MetricInfo(
        title = "Monte Carlo Success Probability",
        category = "Stochastic Risk Modeling",
        formulaOrRule = "Success = % of simulated paths where portfolio >= 0 across full horizon",
        explanation = "Runs ${state.settings.monteCarloN} log-normal randomized market paths incorporating historical volatility, sequence-of-returns risk, and prolonged market crashes. A success rate above 90% is widely regarded in quantitative financial planning as bulletproof.",
        practicalImplication = "Exposing the portfolio to random sequence shocks prevents the fallacy of assuming smooth average returns.",
        accentColor = BrandTeal
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
                    title = "Monte Carlo Simulation (${state.settings.monteCarloN} Runs)",
                    subtitle = "Confidence distribution across market sequences (Tap for insight)",
                    badgeText = "${mc.successRatePct.toInt()}% PROBABILITY",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

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
                    label = "Median FIRE Age (P50)",
                    value = mc.medianFireAge?.let { "Age $it" } ?: "Beyond 35y",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Best Case FIRE Age (Top 5% market)",
                    value = mc.bestCaseAge?.let { "Age $it" } ?: "--",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Conservative FIRE Age (95th %ile)",
                    value = mc.worstCaseAge?.let { "Age $it" } ?: "--",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
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
            val scenarioInfo = MetricInfo(
                title = "${scenario.iconEmoji} ${scenario.name}",
                category = "Stress Regime Parameters",
                formulaOrRule = "${String.format("%.1f%%", scenario.nominalReturnPct)} Nominal Return | ${String.format("%.1f%%", scenario.cpiInflationPct)} CPI Inflation",
                explanation = scenario.description,
                practicalImplication = "Tests portfolio survivability under non-linear historical stress regimes (such as 1970s stagflation or prolonged tech drawdowns).",
                accentColor = BadRed
            )

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
                        value = "${String.format("%.1f%%", scenario.nominalReturnPct)} / ${String.format("%.1f%%", scenario.cpiInflationPct)} CPI",
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
                        value = "${String.format("%.1f%%", scenario.successRatePct)}",
                        info = scenarioInfo,
                        onShowInfo = onShowInfo
                    )
                    ProjectionMetricRow(
                        label = "Emergency Reserve Survival:",
                        value = "${String.format("%.1f", scenario.emergencySurvivalMonths)} months",
                        info = scenarioInfo,
                        onShowInfo = onShowInfo
                    )
                    ProjectionMetricRow(
                        label = "Net Worth at Age 60:",
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

@Composable
private fun ProjectionMetricRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    highlightColor: Color? = null,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .infoTapHold(info, onShowInfo)
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Text(
                text = label,
                style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.bodyMedium
            )
            if (info != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Info available",
                    tint = BrandTeal.copy(alpha = 0.45f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
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
