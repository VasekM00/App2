package com.example.ui.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActionMeta
import com.example.domain.FullCalculationState
import com.example.ui.components.CardHeaderPill
import com.example.ui.components.ColorPill
import com.example.ui.components.KpiCard
import com.example.ui.components.NetWorthChart
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct

import com.example.ui.components.EmergencyReserveWidget
import com.example.ui.components.MetricInfo
import com.example.ui.components.MetricInfoDialog
import com.example.ui.components.infoTapHold
import com.example.ui.components.rememberMetricInfoState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean> = emptyMap(),
    onToggleAction: ((year: Int, actionId: String, currentIsDone: Boolean) -> Unit)? = null,
    onNavigateToIncome: (() -> Unit)? = null,
    onNavigateToProjections: (() -> Unit)? = null,
    onNavigateToPlan: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val currentYear = state.settings.baseYear
    val completedActionsCount = ActionMeta.items.count { meta ->
        actionStates["${currentYear}_${meta.id}"] == true
    }
    var isActionBannerExpanded by remember { mutableStateOf(false) }
    val infoState = rememberMetricInfoState()

    val incomeInfo = MetricInfo(
        title = "Household Net Inflows",
        category = "Cash Flow & Inflows",
        formulaOrRule = "Net Take-Home Pay + Meal Vouchers + Employer Pension Matching",
        explanation = "Measures true monthly cash generation after all personal Czech taxes (15%/23%), social security (7.1%), and health insurance (4.5%). Employer contributions to DIP and DPS enter directly into tax-sheltered investment accounts without personal tax drag.",
        statutoryReference = "§ 6 odst. 9 písm. d) ZDP (50k/yr employer limit)",
        practicalImplication = "Maximizing tax-exempt employer matching provides an immediate 100% risk-free return on capital before market compounding.",
        accentColor = BrandTeal
    )

    val fireTargetInfo = MetricInfo(
        title = "Base FIRE Target Capital",
        category = "Retirement Actuarial Target",
        formulaOrRule = "Target = (Annual Living Burn - State Pension) / SWR + Bridge Deficit",
        explanation = "Calculated in today's constant purchasing power (real CZK). It accounts for the multi-decade bridge period where private investment assets must sustain 100% of household expenditures before the Czech state pension kicks in at age 65.",
        statutoryReference = "Act No. 155/1995 Coll. (Pension Insurance Act)",
        practicalImplication = "Every 1,000 CZK/month reduction in permanent baseline living expenses reduces required FIRE capital by ~342,000 CZK at a 3.5% SWR.",
        accentColor = BrandGold
    )

    val firePoint = state.fireDualPoint
    val fireAgeInfo = MetricInfo(
        title = "Projected FIRE Horizon",
        category = "Trajectory Milestone",
        formulaOrRule = "Compound DCA + Organic Growth >= Dynamic FIRE Target",
        explanation = "Determined dynamically by modeling when aggregate liquid brokerage investments, DIP, and accessible retirement assets surpass the required cost-of-living capital barrier under inflation and real return assumptions.",
        practicalImplication = "Front-loading savings rate early compounds exponentially due to sequence-of-returns acceleration in initial accumulation years.",
        accentColor = BrandBlue
    )

    val netWorthInfo = MetricInfo(
        title = "Consolidated Net Worth",
        category = "Balance Sheet",
        formulaOrRule = "Liquid Brokerage + DIP + DPS + Emergency Cash",
        explanation = "Represents consolidated financial capital. Excludes illiquid primary residential real estate equity to ensure strict FIRE withdrawal modeling against income-generating assets.",
        practicalImplication = "Tracking liquid investment equity versus emergency cash preserves an optimal allocation between cash drag and compounding growth.",
        accentColor = GoodGreen
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("overview_tab")
    ) {
        // 4 Interactive KPI Cards (Symmetric 2x2 Grid, Tap / Hold for deep insight)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Household Net / Mo",
                value = fmtCZK(state.currentIncome.totalMonthly),
                hint = "Combined family income",
                accentColor = BrandTeal,
                modifier = Modifier.weight(1f),
                testTagStr = "kpi_family_net",
                info = incomeInfo,
                onShowInfo = { infoState.show(it) },
                onClick = onNavigateToIncome
            )

            KpiCard(
                title = "Base FIRE Target",
                value = fmtCompact(state.fireBaseTargetToday),
                hint = "Today's purchasing power",
                accentColor = BrandGold,
                modifier = Modifier.weight(1f),
                testTagStr = "kpi_fire_target",
                info = fireTargetInfo,
                onShowInfo = { infoState.show(it) },
                onClick = onNavigateToProjections
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Projected FIRE Age",
                value = firePoint?.let { "Age ${it.age}" } ?: "Age ${state.settings.primaryAge + 35}+",
                hint = firePoint?.let { "Projected year ${it.year}" } ?: "Beyond 35y horizon",
                accentColor = BrandBlue,
                modifier = Modifier.weight(1f),
                testTagStr = "kpi_fire_age",
                info = fireAgeInfo,
                onShowInfo = { infoState.show(it) },
                onClick = onNavigateToProjections
            )

            KpiCard(
                title = "Net Worth Total",
                value = fmtCompact(state.netWorthTotal),
                hint = "Liquid + reserve + pension",
                accentColor = GoodGreen,
                modifier = Modifier.weight(1f),
                testTagStr = "kpi_net_worth",
                info = netWorthInfo,
                onShowInfo = { infoState.show(it) },
                onClick = onNavigateToProjections
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Collapsible Action Checklist Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("overview_action_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isActionBannerExpanded = !isActionBannerExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(GoodGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ListAlt,
                                contentDescription = null,
                                tint = GoodGreen,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Execution Checklist",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                ColorPill(
                                    text = "YEAR $currentYear",
                                    color = GoodGreen,
                                    fontSize = 9.sp,
                                    horizontalPadding = 6.dp,
                                    verticalPadding = 2.dp
                                )
                            }
                            Text(
                                text = "$completedActionsCount of ${ActionMeta.items.size} optimization moves completed",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorPill(
                            text = "${((completedActionsCount.toDouble() / ActionMeta.items.size) * 100).toInt()}% DONE",
                            color = GoodGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            horizontalPadding = 7.dp,
                            verticalPadding = 3.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { isActionBannerExpanded = !isActionBannerExpanded },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isActionBannerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isActionBannerExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isActionBannerExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        ActionMeta.items.forEach { meta ->
                            val key = "${currentYear}_${meta.id}"
                            val isDone = actionStates[key] == true
                            val impact = state.actionsImpacts[meta.id] ?: 0.0

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onToggleAction?.invoke(currentYear, meta.id, isDone)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isDone,
                                    onCheckedChange = {
                                        onToggleAction?.invoke(currentYear, meta.id, isDone)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = GoodGreen,
                                        uncheckedColor = MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = meta.title,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isDone) FontWeight.Normal else FontWeight.SemiBold,
                                            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    if (impact > 0) {
                                        Text(
                                            text = "+${fmtCZK(impact)} / yr impact",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                color = GoodGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Emergency Reserve & Runway Goal Tracker Widget
        EmergencyReserveWidget(
            state = state,
            onShowInfo = { infoState.show(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Net Worth Chart
        NetWorthChart(
            data = state.dualTrajectory,
            cpiInflationPct = state.settings.cpiInflationPct
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Highlights
        val surplusVal = state.currentIncome.totalMonthly - state.totalLivingCostMonthly
        val surplusInfo = MetricInfo(
            title = "Monthly Capital Surplus",
            category = "Cash Flow & DCA Engine",
            formulaOrRule = "Surplus = Total Net Monthly Inflows - All Fixed/Variable Living Expenses",
            explanation = "Represents your monthly discretionary investment firepower. In pure FIRE math, your savings rate (Surplus / Inflows) is the single most dominant lever governing years-to-financial-independence, outweighing investment returns during early accumulation.",
            practicalImplication = "Directing surplus automatically on pay-day into index ETFs and DIP eliminates lifestyle creep and enforces paying yourself first.",
            accentColor = BrandTeal
        )

        val dipSavingInfo = MetricInfo(
            title = "DIP & DPS Statutory Tax Shield",
            category = "Czech Tax Optimization",
            formulaOrRule = "Tax Refund = min(DIP + DPS Deposits - Subsidy Threshold, 48k CZK) × Tax Rate (15% / 23%)",
            explanation = "Under § 15a of the Czech Income Tax Act (ZDP), taxpayers can deduct up to 48,000 CZK combined annually from their personal taxable base across qualifying Long-Term Investment Products (DIP) and Supplementary Pension Savings (DPS).",
            statutoryReference = "§ 15 odst. 5 & § 15a Act No. 586/1992 Coll. (ZDP)",
            practicalImplication = "Contributing 4,000 CZK/month into DIP unlocks the full 48k ceiling, providing an immediate risk-free 15% (7,200 CZK) or 23% (11,040 CZK) annual tax refund.",
            accentColor = GoodGreen
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Strategic Highlights",
                    subtitle = "Monthly dynamics & tax efficiency (Tap for deep insight)",
                    badgeText = "KEY STATS",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))
                SummaryRow(
                    label = "Monthly surplus",
                    value = fmtCZK(surplusVal),
                    valueColor = if (surplusVal < 0) BadRed else GoodGreen,
                    info = surplusInfo,
                    onShowInfo = { infoState.show(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow(
                    label = "Monthly savings rate",
                    value = fmtPct(state.savingsRatePct, 1),
                    info = MetricInfo(
                        title = "Monthly Savings Rate",
                        category = "Wealth Accumulation Velocity",
                        formulaOrRule = "Savings Rate = Net Monthly Surplus / Total Net Inflows",
                        explanation = "Indicates the proportion of total monthly cash inflows preserved and deployed toward building financial independence capital.",
                        practicalImplication = "Elevating savings rate reduces working years exponentially due to the dual effect of higher investment deposits and lower required baseline lifestyle expenses.",
                        accentColor = BrandTeal
                    ),
                    onShowInfo = { infoState.show(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow(
                    label = "Annual DIP tax saving",
                    value = fmtCZK(state.taxReturnHelper.dipSaving),
                    info = dipSavingInfo,
                    onShowInfo = { infoState.show(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }

    // Modal Metric Info Dialog
    MetricInfoDialog(
        info = infoState.currentInfo,
        onDismiss = { infoState.dismiss() }
    )
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    info: MetricInfo? = null,
    onShowInfo: ((MetricInfo) -> Unit)? = null,
    valueColor: androidx.compose.ui.graphics.Color = BrandTeal
) {
    val clickModifier = if (info != null && onShowInfo != null) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .infoTapHold(info, onShowInfo)
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Text(
                text = "• $label:",
                style = MaterialTheme.typography.bodyMedium
            )
            if (info != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Info available",
                    tint = BrandTeal.copy(alpha = 0.5f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
