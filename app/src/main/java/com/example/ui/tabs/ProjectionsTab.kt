package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.domain.RegulatoryConstants
import kotlin.math.min
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.MonteCarloFanChart
import com.example.ui.components.NetWorthChart
import com.example.ui.components.DcaTrajectoryBarChart
import androidx.compose.material3.Slider
import com.example.domain.PresetProfiles
import com.example.ui.components.SandboxScenarioChips
import kotlin.math.abs
import kotlin.math.roundToInt
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
import com.example.data.LedgerEntryEntity
import com.example.domain.PortfolioYearPoint
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.TextButton
import kotlin.math.pow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectionsTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
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
                            maxLines = 2,
                            softWrap = true
                        )
                    },
                    modifier = Modifier.testTag("projections_subtab_$index")
                )
            }
        }

        when (selectedSubTab) {
            0 -> TrajectorySubTab(state = state, ledgerEntries = ledgerEntries, onShowInfo = { infoState.show(it) })
            1 -> WhatIfSandboxSubTab(state = state, onApplySettings = onApplySettings, onShowInfo = { infoState.show(it) })
            2 -> MonteCarloAndStressSubTab(state = state, onShowInfo = { infoState.show(it) })
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
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
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
        explanation = "With an expected ${fmtPct(state.settings.portfolioNominalReturnPct)} nominal return and ${fmtPct(state.settings.cpiInflationPct)} inflation, your equity assets grow at ~${String.format(java.util.Locale.getDefault(), "%.2f%%", ((1 + state.settings.portfolioNominalReturnPct/100.0)/(1 + state.settings.cpiInflationPct/100.0) - 1.0) * 100.0)} net purchasing power annually.",
        statutoryReference = "Fisher Equation of Real Interest",
        practicalImplication = "Maintaining realistic inflation assumptions guarantees your FIRE target in today's CZK remains accurate in future purchasing power.",
        accentColor = BrandGold
    )

    val combinedPension = state.settings.vStatePensionMonthly + state.settings.eStatePensionMonthly
    val pensionAgeDisplay = if (state.settings.vStatePensionAge == state.settings.eStatePensionAge) {
        "${state.settings.vStatePensionAge}"
    } else {
        "V: ${state.settings.vStatePensionAge} / E: ${state.settings.eStatePensionAge}"
    }
    val pensionBridgeInfo = MetricInfo(
        title = "State Pension Bridge Years",
        category = "Actuarial Horizon",
        formulaOrRule = "Bridge Horizon = State Pension Age ($pensionAgeDisplay) - Target FIRE Age",
        explanation = "The actuarial phase between early retirement and statutory state pension entitlement. During this bridge, your investment portfolio must support 100% of household cash outlays. Once state pension arrives (${fmtCZK(combinedPension)}/mo), the required portfolio draw drops dramatically.",
        statutoryReference = "§ 32 Act No. 155/1995 Coll.",
        practicalImplication = "Dynamic bridge modeling avoids over-saving millions of CZK by accounting for future guaranteed state annuity cash flows.",
        accentColor = BrandTeal
    )

    var selectedTrajectoryChart by rememberSaveable { mutableIntStateOf(0) }
    var isRealPurchasingPower by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Chart Selector Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { selectedTrajectoryChart = 0 },
                label = { Text("Net Worth Curve", fontSize = 12.sp, fontWeight = if (selectedTrajectoryChart == 0) FontWeight.Bold else FontWeight.Normal) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selectedTrajectoryChart == 0) BrandTeal.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    labelColor = if (selectedTrajectoryChart == 0) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f).testTag("chip_net_worth_curve")
            )
            AssistChip(
                onClick = { selectedTrajectoryChart = 1 },
                label = { Text("DCA & Growth Bars", fontSize = 12.sp, fontWeight = if (selectedTrajectoryChart == 1) FontWeight.Bold else FontWeight.Normal) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selectedTrajectoryChart == 1) BrandGold.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    labelColor = if (selectedTrajectoryChart == 1) BrandGold else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f).testTag("chip_dca_bars")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedTrajectoryChart == 0) {
            // Primary 35-Year Trajectory Chart
            NetWorthChart(
                data = state.dualTrajectory,
                cpiInflationPct = state.settings.cpiInflationPct,
                ledgerEntries = ledgerEntries,
                isRealPurchasingPower = isRealPurchasingPower,
                onRealPurchasingPowerChange = { isRealPurchasingPower = it }
            )
        } else {
            // 35-Year DCA Bar Chart & Growth
            DcaTrajectoryBarChart(
                data = state.dualTrajectory,
                settings = state.settings
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 35-Year Trajectory Milestones Table
        TrajectoryMilestonesTable(
            trajectory = state.dualTrajectory,
            isRealPurchasingPower = isRealPurchasingPower,
            cpiInflationPct = state.settings.cpiInflationPct,
            firePoint = state.fireDualPoint
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
                    badgeText = if (isRealPurchasingPower) "REAL MODE" else "NOMINAL MODE",
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
                    label = if (isRealPurchasingPower) "Today's FIRE Target (${fmtPct(state.settings.safeWithdrawalRatePct)} SWR)" else "Target at FIRE (${fmtPct(state.settings.safeWithdrawalRatePct)} SWR)",
                    value = if (isRealPurchasingPower) fmtCZK(state.fireBaseTargetToday) else (state.fireDualPoint?.let { "${fmtCZK(it.target)} (${it.year})" } ?: fmtCZK(state.fireBaseTargetToday)),
                    info = swrInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = if (isRealPurchasingPower) "Real Portfolio Compound Return" else "Expected Portfolio Nominal Return",
                    value = if (isRealPurchasingPower) "${fmtPct(state.realReturnPct, 1)} (Real CAGR)" else "${fmtPct(state.settings.portfolioNominalReturnPct)} (Nominal)",
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
                    label = if (state.settings.vStatePensionAge == state.settings.eStatePensionAge) "State Pension Age" else "State Pension Ages",
                    value = if (state.settings.vStatePensionAge == state.settings.eStatePensionAge) "${state.settings.vStatePensionAge} yrs" else "V: ${state.settings.vStatePensionAge} / E: ${state.settings.eStatePensionAge} yrs",
                    info = pensionBridgeInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val totalStatePension = state.settings.vStatePensionMonthly + state.settings.eStatePensionMonthly
                ProjectionMetricRow(
                    label = "Combined State Pension",
                    value = fmtCZK(totalStatePension) + "/mo",
                    info = pensionBridgeInfo,
                    onShowInfo = onShowInfo
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

/**
 * SubTab 1: What-If Sandbox
 *
 * A non-destructive sandbox: every assumption is simulated against a private copy of the plan
 * and nothing is persisted until the user explicitly taps "Apply to Plan".
 */
@Composable
private fun WhatIfSandboxSubTab(
    state: FullCalculationState,
    onApplySettings: (SettingsEntity) -> Unit,
    onShowInfo: (MetricInfo) -> Unit
) {
    val scrollState = rememberScrollState()

    var sandbox by remember { mutableStateOf(state.settings) }
    var activePresetId by remember { mutableStateOf<String?>(PresetProfiles.PLAN_BASELINE.id) }
    val sandboxState = remember(sandbox) { FinancialEngine.calculate(sandbox, runMonteCarlo = false) }
    val isDirty = sandbox != state.settings

    val sandboxInfo = MetricInfo(
        title = "What-If Sandbox",
        category = "Scenario Planning",
        formulaOrRule = "Private copy of your plan -> live re-calculation -> optional Apply",
        explanation = "Adjust return, inflation, withdrawal rate, monthly investing or pension age and see the FIRE target, FIRE age and horizon wealth respond instantly. Your saved plan and every other tab stay untouched until you tap Apply to Plan.",
        practicalImplication = "Stress-testing one pessimistic and one optimistic assumption is the cheapest risk management available in retirement planning.",
        accentColor = BrandGold
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header + Apply / Reset
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "What-If Sandbox",
                    subtitle = "Simulate assumptions without touching your plan",
                    badgeText = if (isDirty) "MODIFIED" else "IN SYNC",
                    accentColor = BrandGold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Drag the assumptions below — results update live. Nothing is saved until you tap Apply to Plan.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            sandbox = state.settings
                            activePresetId = PresetProfiles.PLAN_BASELINE.id
                        },
                        enabled = isDirty,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("sandbox_reset")
                    ) {
                        Text("Reset", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Button(
                        onClick = { onApplySettings(sandbox) },
                        enabled = isDirty,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                        modifier = Modifier.weight(1.5f).testTag("sandbox_apply")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply to Plan", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }

        // 2. Scenario presets
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Scenario Presets",
                    subtitle = "One tap sets a coherent assumption set",
                    badgeText = "PRESETS",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(10.dp))
                SandboxScenarioChips(
                    activePresetId = activePresetId,
                    onSelectPreset = { preset ->
                        sandbox = preset.transform(state.settings)
                        activePresetId = preset.id
                    }
                )
            }
        }

        // 3. Live assumptions
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Assumptions",
                    subtitle = "Every change recalculates instantly",
                    badgeText = "LIVE",
                    accentColor = BrandBlue
                )
                Spacer(modifier = Modifier.height(6.dp))

                val sandboxTotalDca = sandbox.portuDcaMonthly + if (!sandbox.isSingleHousehold) sandbox.ePortuDcaMonthly else 0.0

                SandboxSliderRow(
                    label = "Portfolio return (% p.a.)",
                    value = sandbox.portfolioNominalReturnPct,
                    range = 0f..12f,
                    steps = 0,
                    valueText = fmtPct(sandbox.portfolioNominalReturnPct, 1),
                    testTagStr = "sandbox_slider_return"
                ) { v ->
                    sandbox = sandbox.copy(portfolioNominalReturnPct = (v * 4).roundToInt() / 4.0)
                    activePresetId = null
                }
                SandboxSliderRow(
                    label = "Inflation (% p.a.)",
                    value = sandbox.cpiInflationPct,
                    range = 0f..8f,
                    steps = 0,
                    valueText = fmtPct(sandbox.cpiInflationPct, 1),
                    testTagStr = "sandbox_slider_inflation"
                ) { v ->
                    sandbox = sandbox.copy(cpiInflationPct = (v * 10).roundToInt() / 10.0)
                    activePresetId = null
                }
                SandboxSliderRow(
                    label = "Safe withdrawal rate (%)",
                    value = sandbox.safeWithdrawalRatePct,
                    range = 2.5f..6f,
                    steps = 13,
                    valueText = fmtPct(sandbox.safeWithdrawalRatePct, 2),
                    testTagStr = "sandbox_slider_swr"
                ) { v ->
                    sandbox = sandbox.copy(safeWithdrawalRatePct = v)
                    activePresetId = null
                }
                SandboxSliderRow(
                    label = "Monthly ETF investing",
                    value = sandboxTotalDca,
                    range = 0f..50_000f,
                    steps = 0,
                    valueText = fmtCZK(sandboxTotalDca),
                    testTagStr = "sandbox_slider_dca"
                ) { v ->
                    val rounded = (v / 500.0).roundToInt() * 500.0
                    val base = state.settings
                    val baseTotal = base.portuDcaMonthly + if (!base.isSingleHousehold) base.ePortuDcaMonthly else 0.0
                    sandbox = if (!base.isSingleHousehold && baseTotal > 0.0) {
                        val ratio = base.portuDcaMonthly / baseTotal
                        sandbox.copy(portuDcaMonthly = rounded * ratio, ePortuDcaMonthly = rounded * (1.0 - ratio))
                    } else {
                        sandbox.copy(portuDcaMonthly = rounded)
                    }
                    activePresetId = null
                }
                SandboxSliderRow(
                    label = "State pension age",
                    value = sandbox.vStatePensionAge.toDouble(),
                    range = 60f..70f,
                    steps = 9,
                    valueText = "${sandbox.vStatePensionAge}",
                    testTagStr = "sandbox_slider_pension_age"
                ) { v ->
                    val age = v.roundToInt()
                    sandbox = sandbox.copy(vStatePensionAge = age, eStatePensionAge = age)
                    activePresetId = null
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .infoTapHold(sandboxInfo, onShowInfo)
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = BrandGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Simulation is local and instant; only Apply writes to your plan.",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 4. Live impact vs the saved plan
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Live Impact",
                    subtitle = "Saved plan vs sandbox",
                    badgeText = "DELTA",
                    accentColor = GoodGreen
                )
                Spacer(modifier = Modifier.height(10.dp))
                SandboxComparison(state = state, sandboxState = sandboxState)
            }
        }

        // 5. Read-only accounts & DCA breakdown
        PortfolioAccountsView(state = state, onShowInfo = onShowInfo)
    }
}

@Composable
private fun SandboxSliderRow(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    testTagStr: String,
    onValueChange: (Double) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = BrandTeal
            )
        }
        Slider(
            value = value.toFloat().coerceIn(range.start, range.endInclusive),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth().testTag(testTagStr)
        )
    }
}

@Composable
private fun SandboxComparison(
    state: FullCalculationState,
    sandboxState: FullCalculationState
) {
    val basePassive = state.fireBaseTargetToday * (state.settings.safeWithdrawalRatePct / 100.0) / 12.0
    val scenarioPassive = sandboxState.fireBaseTargetToday * (sandboxState.settings.safeWithdrawalRatePct / 100.0) / 12.0
    val baseWealth = state.dualTrajectory.lastOrNull()?.totalPortfolio ?: 0.0
    val scenarioWealth = sandboxState.dualTrajectory.lastOrNull()?.totalPortfolio ?: 0.0
    val baseAge = state.fireDualPoint?.age
    val scenarioAge = sandboxState.fireDualPoint?.age

    Column(modifier = Modifier.fillMaxWidth()) {
        SandboxComparisonRow(
            label = "FIRE target (today)",
            baselineText = fmtCZK(state.fireBaseTargetToday),
            scenarioText = fmtCZK(sandboxState.fireBaseTargetToday),
            delta = sandboxState.fireBaseTargetToday - state.fireBaseTargetToday,
            higherIsBetter = false,
            deltaFormatter = { fmtCZK(abs(it)) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        SandboxComparisonRow(
            label = "Projected FIRE age",
            baselineText = baseAge?.let { "Age $it" } ?: "Beyond 35y",
            scenarioText = scenarioAge?.let { "Age $it" } ?: "Beyond 35y",
            delta = if (baseAge != null && scenarioAge != null) (scenarioAge - baseAge).toDouble() else Double.NaN,
            higherIsBetter = false,
            deltaFormatter = { "${abs(it).roundToInt()} yrs" }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        SandboxComparisonRow(
            label = "Monthly passive income",
            baselineText = fmtCZK(basePassive),
            scenarioText = fmtCZK(scenarioPassive),
            delta = scenarioPassive - basePassive,
            higherIsBetter = true,
            deltaFormatter = { fmtCZK(abs(it)) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        SandboxComparisonRow(
            label = "Wealth in 35 years",
            baselineText = fmtCompact(baseWealth),
            scenarioText = fmtCompact(scenarioWealth),
            delta = scenarioWealth - baseWealth,
            higherIsBetter = true,
            deltaFormatter = { fmtCompact(abs(it)) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        SandboxComparisonRow(
            label = "Real return",
            baselineText = fmtPct(state.realReturnPct, 1),
            scenarioText = fmtPct(sandboxState.realReturnPct, 1),
            delta = sandboxState.realReturnPct - state.realReturnPct,
            higherIsBetter = true,
            deltaFormatter = { fmtPct(abs(it), 1) }
        )
    }
}

@Composable
private fun SandboxComparisonRow(
    label: String,
    baselineText: String,
    scenarioText: String,
    delta: Double,
    higherIsBetter: Boolean,
    deltaFormatter: (Double) -> String
) {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val isFlat = delta.isNaN() || abs(delta) < 0.0001
    val color = when {
        isFlat -> neutral
        (delta > 0) == higherIsBetter -> GoodGreen
        else -> BadRed
    }
    val deltaText = if (isFlat) {
        "no change"
    } else {
        (if (delta > 0) "+" else "-") + deltaFormatter(delta)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp))
            Text(
                text = "Plan: $baselineText",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = neutral
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = scenarioText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = deltaText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                color = color
            )
        }
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
        formulaOrRule = "Up to 1/3 of own deposits plus their appreciation, before age 36, after 120 months of saving",
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
        title = "Retirement Tax Shield",
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

                val isSingle = s.isSingleHousehold
                val liquidTotal = s.liquidPortfolioCurrent + if (!isSingle) s.eLiquidPortfolioCurrent else 0.0
                val dipTotalBal = s.dipBalanceCurrent + if (!isSingle) s.eDipBalanceCurrent else 0.0
                val dpsTotalBal = s.dpsBalanceCurrent + if (!isSingle) s.eDpsBalanceCurrent else 0.0

                ProjectionMetricRow("Brokerage / ETF Portfolio", fmtCZK(liquidTotal))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Total DIP Investment Balance", fmtCZK(dipTotalBal))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("Total DPS Pension Balance", fmtCZK(dpsTotalBal))
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

                val isSingle = s.isSingleHousehold
                val portuTotal = s.portuDcaMonthly + if (!isSingle) s.ePortuDcaMonthly else 0.0
                val dipTotal = s.dipContributionMonthly + if (!isSingle) s.eDipContributionMonthly else 0.0
                val dpsTotal = s.dpsOwnContributionMonthly + if (!isSingle) s.eDpsOwnContributionMonthly else 0.0
                val empCap = RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0
                val empV = min(s.employerRetirementMonthly, empCap)
                val empE = if (!isSingle) min(s.eEmployerRetirementMonthly, empCap) else 0.0
                val empTotal = empV + empE

                ProjectionMetricRow("Brokerage / ETF DCA", fmtCZK(portuTotal))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("DIP Contribution", fmtCZK(dipTotal))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                ProjectionMetricRow("DPS Own Contribution", fmtCZK(dpsTotal))
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                if (empTotal > 0) {
                    ProjectionMetricRow("Employer Benefit (Equiv.)", fmtCZK(empTotal))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }
                ProjectionMetricRow("Total Combined Investment", fmtCZK(state.investMonthlyTotal), isBold = true, highlightColor = BrandTeal)
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
    onShowInfo: (MetricInfo) -> Unit
) {
    val scrollState = rememberScrollState()
    var isHistoricalMode by rememberSaveable { mutableStateOf(state.settings.useHistoricalBootstrap) }
    val mc = if (isHistoricalMode) state.historicalMonteCarlo else state.monteCarlo
    val survival = if (isHistoricalMode) state.historicalRetirementSurvival else state.retirementSurvival

    val simulationEngineInfo = MetricInfo(
        title = "Simulation Engine Methodology",
        category = "Stochastic Methodology",
        formulaOrRule = "Parametric: Geometric Brownian Motion (mu = Return, sigma = Volatility) | Historical: 3-Year Block-Bootstrap from 1970-2025 empirical returns",
        explanation = "Parametric simulation assumes normal annual return distributions, which underestimates fat-tail risk (e.g. 1973-1974 stagflation, 2000-2002 dot-com bust, 2008 GFC). Block-bootstrap resamples contiguous 3-year historical blocks from 1970-2025, preserving real multi-year sequence-of-returns drawdowns.",
        practicalImplication = "Historical resampling shows real-world sequence resilience without synthetic distribution assumptions.",
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
                    accentColor = BrandTeal
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
                    label = "Median FIRE Age (P50)",
                    value = mc.medianFireAge?.let { "Age $it" } ?: "Beyond 35y",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Best Case FIRE Age (Top 5% market)",
                    value = mc.bestCaseAge?.let { "Age $it" } ?: "Beyond 35y",
                    info = percentileInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ProjectionMetricRow(
                    label = "Conservative FIRE Age (95th %ile)",
                    value = mc.worstCaseAge?.let { "Age $it" } ?: "Beyond 35y",
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
                        label = "Median Depletion Age (failing paths)",
                        value = "Age $depletionAge",
                        info = survivalInfo,
                        onShowInfo = onShowInfo
                    )
                }
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
                            text = scenario.name,
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

@Composable
private fun TrajectoryMilestonesTable(
    trajectory: List<PortfolioYearPoint>,
    isRealPurchasingPower: Boolean,
    cpiInflationPct: Double,
    firePoint: PortfolioYearPoint? = null
) {
    if (trajectory.isEmpty()) return
    var showAllYears by rememberSaveable { mutableStateOf(false) }

    val displayPoints = remember(trajectory, isRealPurchasingPower, cpiInflationPct) {
        if (!isRealPurchasingPower) {
            trajectory
        } else {
            trajectory.mapIndexed { idx, pt ->
                val discount = (1.0 + (cpiInflationPct / 100.0)).pow(idx.toDouble())
                pt.copy(
                    portfolio = pt.portfolio / discount,
                    target = pt.target / discount,
                    pensionPortfolio = pt.pensionPortfolio / discount
                )
            }
        }
    }

    val fireYear = firePoint?.year
    val rowsToShow = remember(displayPoints, showAllYears, fireYear) {
        if (showAllYears) {
            displayPoints
        } else {
            val keyYears = mutableSetOf<Int>()
            displayPoints.forEachIndexed { idx, pt ->
                if (idx == 0 || idx == displayPoints.size - 1 || pt.year % 5 == 0 || pt.year == fireYear) {
                    keyYears.add(pt.year)
                }
            }
            displayPoints.filter { it.year in keyYears }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("trajectory_milestones_table_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "35-Year Trajectory Table",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isRealPurchasingPower) "Discounted at ${String.format(Locale.ROOT, "%.1f", cpiInflationPct)}% inflation (Today's CZK)" else "Nominal projected compound growth",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                ColorPill(
                    text = if (isRealPurchasingPower) "TODAY'S CZK" else "NOMINAL",
                    color = BrandTeal,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 6.dp,
                    verticalPadding = 2.5.dp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Year (Age)", modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Text("Liquid", modifier = Modifier.weight(1.0f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End))
                Text("Pension", modifier = Modifier.weight(1.0f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End))
                Text("Total Net Worth", modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End))
            }

            Spacer(modifier = Modifier.height(6.dp))

            rowsToShow.forEach { pt ->
                val isFire = pt.year == fireYear
                val rowBg = if (isFire) BrandGold.copy(alpha = 0.12f) else Color.Transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(modifier = Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${pt.year}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isFire) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFire) BrandGold else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = " (${pt.age})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                        if (isFire) {
                            Spacer(modifier = Modifier.width(4.dp))
                            ColorPill(text = "FIRE", color = BrandGold, fontSize = 7.5.sp, horizontalPadding = 3.dp, verticalPadding = 1.dp)
                        }
                    }
                    Text(
                        text = fmtCompact(pt.portfolio),
                        modifier = Modifier.weight(1.0f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = BrandTeal,
                            textAlign = TextAlign.End,
                            fontWeight = if (isFire) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    Text(
                        text = fmtCompact(pt.pensionPortfolio),
                        modifier = Modifier.weight(1.0f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF6366F1),
                            textAlign = TextAlign.End,
                            fontWeight = if (isFire) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    Text(
                        text = fmtCompact(pt.totalPortfolio),
                        modifier = Modifier.weight(1.4f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isFire) GoodGreen else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End
                        )
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expand / Collapse button
            TextButton(
                onClick = { showAllYears = !showAllYears },
                modifier = Modifier.fillMaxWidth().testTag("toggle_all_years_button")
            ) {
                Icon(
                    imageVector = if (showAllYears) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showAllYears) "Collapse to Key Milestones" else "Show All 35 Years",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

