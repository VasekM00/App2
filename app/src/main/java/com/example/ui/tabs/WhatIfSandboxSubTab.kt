package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.LedgerEntryEntity
import com.example.data.SettingsEntity
import com.example.domain.FinancialEngine
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.MetricInfo
import com.example.ui.components.SandboxScenarioChips
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import com.example.domain.PresetProfiles
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun WhatIfSandboxSubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    actionStates: Map<String, Boolean> = emptyMap(),
    onApplySettings: (SettingsEntity) -> Unit,
    onShowInfo: (MetricInfo) -> Unit
) {
    val scrollState = rememberScrollState()

    var sandbox by remember(state.settings) { mutableStateOf(state.settings) }
    var activePresetId by remember(state.settings) { mutableStateOf<String?>(PresetProfiles.PLAN_BASELINE.id) }
    val sandboxState = remember(sandbox, ledgerEntries, actionStates) {
        FinancialEngine.calculate(sandbox, ledgerEntries = ledgerEntries, actionStates = actionStates, runMonteCarlo = false)
    }
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                Spacer(modifier = Modifier.height(10.dp))

                // Compact live delta summary banner above sliders
                SandboxLiveDeltaBar(state = state, sandboxState = sandboxState)

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
        PortfolioAccountsView(state = sandboxState, onShowInfo = onShowInfo)
    }
}

@Composable
private fun SandboxLiveDeltaBar(
    state: FullCalculationState,
    sandboxState: FullCalculationState
) {
    val isSingle = state.settings.isSingleHousehold
    val targetDelta = sandboxState.fireBaseTargetToday - state.fireBaseTargetToday
    val baseYearPt = (if (isSingle) state.fireSinglePoint else state.fireDualPoint)?.year
    val scenarioYearPt = (if (isSingle) sandboxState.fireSinglePoint else sandboxState.fireDualPoint)?.year
    val baseWealth = (if (isSingle) state.singleTrajectory else state.dualTrajectory).lastOrNull()?.totalPortfolio ?: 0.0
    val scenarioWealth = (if (isSingle) sandboxState.singleTrajectory else sandboxState.dualTrajectory).lastOrNull()?.totalPortfolio ?: 0.0
    val wealthDelta = scenarioWealth - baseWealth

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sandbox_live_delta_bar"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SandboxDeltaItem(
                label = "FIRE TARGET",
                valueText = when {
                    abs(targetDelta) < 1.0 -> "Unchanged"
                    targetDelta > 0 -> "+${fmtCompact(targetDelta)}"
                    else -> "-${fmtCompact(abs(targetDelta))}"
                },
                color = when {
                    abs(targetDelta) < 1.0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    targetDelta < 0 -> GoodGreen
                    else -> BadRed
                },
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )

            val yearDelta = if (baseYearPt != null && scenarioYearPt != null) scenarioYearPt - baseYearPt else null
            val yearText = when {
                yearDelta == null -> if (scenarioYearPt != null) "$scenarioYearPt" else "N/A"
                yearDelta == 0 -> "Unchanged"
                yearDelta > 0 -> "+$yearDelta yr"
                else -> "$yearDelta yr"
            }
            val yearColor = when {
                yearDelta == null || yearDelta == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                yearDelta < 0 -> GoodGreen
                else -> BadRed
            }
            SandboxDeltaItem(
                label = "FIRE YEAR",
                valueText = yearText,
                color = yearColor,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )

            SandboxDeltaItem(
                label = "35Y WEALTH",
                valueText = when {
                    abs(wealthDelta) < 1.0 -> "Unchanged"
                    wealthDelta > 0 -> "+${fmtCompact(wealthDelta)}"
                    else -> "-${fmtCompact(abs(wealthDelta))}"
                },
                color = when {
                    abs(wealthDelta) < 1.0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    wealthDelta > 0 -> GoodGreen
                    else -> BadRed
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SandboxDeltaItem(
    label: String,
    valueText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color,
            maxLines = 1
        )
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
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
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
    val isSingle = state.settings.isSingleHousehold
    val baseWealth = (if (isSingle) state.singleTrajectory else state.dualTrajectory).lastOrNull()?.totalPortfolio ?: 0.0
    val scenarioWealth = (if (isSingle) sandboxState.singleTrajectory else sandboxState.dualTrajectory).lastOrNull()?.totalPortfolio ?: 0.0
    val baseYearPt = (if (isSingle) state.fireSinglePoint else state.fireDualPoint)?.year
    val scenarioYearPt = (if (isSingle) sandboxState.fireSinglePoint else sandboxState.fireDualPoint)?.year

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
            label = "Projected FIRE year",
            baselineText = baseYearPt?.let { "$it" } ?: "Beyond 35y",
            scenarioText = scenarioYearPt?.let { "$it" } ?: "Beyond 35y",
            delta = if (baseYearPt != null && scenarioYearPt != null) (scenarioYearPt - baseYearPt).toDouble() else Double.NaN,
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
            label = "Wealth in 35 years (nominal)",
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

@Composable
private fun PortfolioAccountsView(
    state: FullCalculationState,
    onShowInfo: (MetricInfo) -> Unit
) {
    val s = state.settings

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Current Account Balances Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
                val empCap = s.employerRetirementExemptionAnnual / 12.0
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
