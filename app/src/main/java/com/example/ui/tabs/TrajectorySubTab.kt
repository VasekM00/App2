package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.charts.DcaTrajectoryBarChart
import com.example.ui.components.MetricInfo
import com.example.ui.components.charts.NetWorthChart
import com.example.ui.components.infoTapHold
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct

@Composable
internal fun TrajectorySubTab(
    state: FullCalculationState,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    onOpenSandbox: (() -> Unit)? = null,
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
        formulaOrRule = "Real CAGR = (1 + Net Nominal Return) / (1 + CPI Inflation) - 1",
        explanation = "With an expected ${fmtPct(state.settings.portfolioNominalReturnPct)} gross nominal return (${fmtPct(com.example.domain.FinancialEngine.netTaxableNominalReturnPct(state.settings), 2)} net after dividend tax drag) and ${fmtPct(state.settings.cpiInflationPct)} inflation, your equity assets grow at ~${fmtPct(state.realReturnPct, 2)} real purchasing power annually.",
        statutoryReference = "Fisher Equation of Real Interest",
        practicalImplication = "Maintaining realistic inflation assumptions guarantees your FIRE target in today's Kč remains accurate in future purchasing power.",
        accentColor = BrandGold
    )

    val pLabel = state.settings.primaryName.ifBlank { "Primary Earner" }
    val sLabel = state.settings.spouseName.ifBlank { "Partner" }
    val combinedPension = state.settings.vStatePensionMonthly + if (state.settings.isSingleHousehold) 0.0 else state.settings.eStatePensionMonthly
    val pensionAgeDisplay = if (state.settings.isSingleHousehold || state.settings.vStatePensionAge == state.settings.eStatePensionAge) {
        "${state.settings.vStatePensionAge}"
    } else {
        "$pLabel: ${state.settings.vStatePensionAge} / $sLabel: ${state.settings.eStatePensionAge}"
    }
    val pensionBridgeInfo = MetricInfo(
        title = "State Pension Bridge Years",
        category = "Actuarial Horizon",
        formulaOrRule = "Bridge Horizon = State Pension Age ($pensionAgeDisplay) - Target FIRE Age",
        explanation = "The actuarial phase between early retirement and statutory state pension entitlement. During this bridge, your investment portfolio must support 100% of household cash outlays. Once state pension arrives (${fmtCZK(combinedPension)} monthly), the required portfolio draw drops dramatically.",
        statutoryReference = "Act No. 155/1995 Coll. (Pension Insurance Act § 32)",
        practicalImplication = "Dynamic bridge modeling avoids over-saving millions of Kč by accounting for future guaranteed state annuity cash flows.",
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
        val haptic = LocalHapticFeedback.current
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val chartModes = listOf(
                    Triple(0, "Net Worth Curve", "chip_net_worth_curve"),
                    Triple(1, "Contributions & Growth", "chip_dca_bars")
                )
                chartModes.forEach { (idx, label, tag) ->
                    val isSelected = selectedTrajectoryChart == idx
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTrajectoryChart = idx
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        contentColor = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        shadowElevation = if (isSelected) 1.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(tag)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
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

        val sEff = state.effectiveSettings
        val snap = state.snapshot
        val isSingleHh = sEff.isSingleHousehold
        val activeTrajectory = if (isSingleHh) state.singleTrajectory else state.dualTrajectory
        val activeFirePoint = snap.activeFirePoint
        val singleCanonicalFireYear = if (isSingleHh) {
            snap.activeFireYear
        } else {
            val singleBridge = state.singleTrajectory.firstOrNull { it.totalPortfolio >= it.target && it.isLiquidBridgeFunded }
            (singleBridge ?: state.fireSinglePoint)?.year
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedTrajectoryChart == 0) {
            // Primary 35-Year Trajectory Chart
            NetWorthChart(
                data = activeTrajectory,
                cpiInflationPct = state.settings.cpiInflationPct,
                ledgerEntries = ledgerEntries,
                isRealPurchasingPower = isRealPurchasingPower,
                onRealPurchasingPowerChange = { isRealPurchasingPower = it },
                onOpenSandbox = onOpenSandbox
            )
        } else {
            // 35-Year DCA Bar Chart & Growth
            DcaTrajectoryBarChart(
                data = activeTrajectory,
                settings = state.settings
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 35-Year Trajectory Milestones Table with Interactive Inspector & CSV Export
        TrajectoryMilestonesTableCard(
            trajectory = activeTrajectory,
            isRealPurchasingPower = isRealPurchasingPower,
            cpiInflationPct = state.settings.cpiInflationPct,
            swrPct = state.settings.safeWithdrawalRatePct,
            firePoint = activeFirePoint
        )

        Spacer(modifier = Modifier.height(16.dp))

        // FIRE Trajectory Analysis Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Forecast Assumptions",
                    subtitle = "Tap any row to see how it affects your goal",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (!isSingleHh) {
                    ProjectionMetricRow(
                        label = "Projected FIRE Year (Dual Income)",
                        value = snap.activeFireYear?.let { "$it" } ?: "Beyond 35 years",
                        isBold = true,
                        highlightColor = BrandTeal,
                        info = MetricInfo(
                            title = "Dual-Income FIRE Date",
                            category = "Household Horizon",
                            explanation = "Models both $pLabel and $sLabel contributing via combined DCA, DIP, and employer matching until aggregate wealth covers the shared household lifestyle budget and pre-60 liquid bridge.",
                            accentColor = BrandTeal
                        ),
                        onShowInfo = onShowInfo
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ProjectionMetricRow(
                        label = "Projected FIRE Year (Single Income)",
                        value = singleCanonicalFireYear?.let { "$it" } ?: "Beyond 35 years",
                        isBold = true,
                        highlightColor = BrandGold,
                        info = MetricInfo(
                            title = "Single-Income Resilience Test",
                            category = "Household Horizon",
                            explanation = "Calculates the independent FIRE horizon (including pre-60 liquid bridge) if funded purely by the primary earner's savings capacity.",
                            accentColor = BrandGold
                        ),
                        onShowInfo = onShowInfo
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    ProjectionMetricRow(
                        label = "Projected FIRE Year",
                        value = snap.activeFireYear?.let { "$it" } ?: "Beyond 35 years",
                        isBold = true,
                        highlightColor = BrandTeal,
                        info = MetricInfo(
                            title = "Single-Earner FIRE Date",
                            category = "Household Horizon",
                            explanation = "Models primary earner contributing via DCA, DIP, and employer matching until aggregate wealth covers the household lifestyle budget.",
                            accentColor = BrandTeal
                        ),
                        onShowInfo = onShowInfo
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                ProjectionMetricRow(
                    label = if (isRealPurchasingPower) "Today's FIRE Target (${fmtPct(state.settings.safeWithdrawalRatePct)} SWR)" else "Target at FIRE (${fmtPct(state.settings.safeWithdrawalRatePct)} SWR)",
                    value = if (isRealPurchasingPower) fmtCZK(state.fireBaseTargetToday) else (activeFirePoint?.let { "${fmtCZK(it.target)} (${it.year})" } ?: fmtCZK(state.fireBaseTargetToday)),
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
                    label = if (isSingleHh || state.settings.vStatePensionAge == state.settings.eStatePensionAge) "State Pension Age" else "State Pension Ages",
                    value = if (isSingleHh || state.settings.vStatePensionAge == state.settings.eStatePensionAge) "${state.settings.vStatePensionAge} years" else "$pLabel: ${state.settings.vStatePensionAge} / $sLabel: ${state.settings.eStatePensionAge} years",
                    info = pensionBridgeInfo,
                    onShowInfo = onShowInfo
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ProjectionMetricRow(
                    label = if (isSingleHh) "State Pension (Monthly)" else "Combined State Pension",
                    value = fmtCZK(combinedPension),
                    info = pensionBridgeInfo,
                    onShowInfo = onShowInfo
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun ProjectionMetricRow(
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = 4.dp)
    ) {
        val maxValueWidth = maxWidth * 0.54f
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    else MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
                if (info != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info available",
                        tint = BrandTeal,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            val isNumericValue = value.any { it.isDigit() }
            Text(
                text = value,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = maxValueWidth),
                style = if (isBold) MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = BodyFontFamily,
                    fontFeatureSettings = if (isNumericValue) "tnum" else null,
                    fontWeight = FontWeight.Bold,
                    color = highlightColor ?: MaterialTheme.colorScheme.onSurface
                ) else MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = BodyFontFamily,
                    fontFeatureSettings = if (isNumericValue) "tnum" else null,
                    fontWeight = FontWeight.SemiBold,
                    color = highlightColor ?: MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
