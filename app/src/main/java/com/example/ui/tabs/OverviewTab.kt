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
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact

import com.example.ui.components.EmergencyReserveWidget

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
    state: FullCalculationState,
    actionStates: Map<String, Boolean> = emptyMap(),
    onToggleAction: ((year: Int, actionId: String, currentIsDone: Boolean) -> Unit)? = null,
    onNavigateToPlan: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val currentYear = state.settings.baseYear
    val completedActionsCount = ActionMeta.items.count { meta ->
        actionStates["${currentYear}_${meta.id}"] == true
    }
    var isActionBannerExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("overview_tab")
    ) {
        // 4 KPI Cards
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val itemWidth = Modifier.fillMaxWidth(0.48f)

            KpiCard(
                title = if (state.settings.isSingleHousehold) "Monthly net income" else "Household net / mo",
                value = fmtCZK(state.currentIncome.totalMonthly),
                hint = if (state.settings.isSingleHousehold) "Take-home + inflows" else "Combined family income",
                accentColor = BrandTeal,
                modifier = itemWidth,
                testTagStr = "kpi_family_net"
            )

            KpiCard(
                title = "Base FIRE target",
                value = fmtCompact(state.fireBaseTargetToday),
                hint = "Today's purchasing power",
                accentColor = BrandGold,
                modifier = itemWidth,
                testTagStr = "kpi_fire_target"
            )

            val firePoint = if (state.settings.isSingleHousehold) state.fireSinglePoint else state.fireDualPoint
            KpiCard(
                title = if (state.settings.isSingleHousehold) "FIRE age" else "FIRE age (dual)",
                value = firePoint?.let { "Age ${it.age}" } ?: ">60",
                hint = firePoint?.let { "Projected year ${it.year}" } ?: "Beyond 35y horizon",
                accentColor = BrandBlue,
                modifier = itemWidth,
                testTagStr = "kpi_fire_age"
            )

            KpiCard(
                title = "Net worth total",
                value = fmtCompact(state.netWorthTotal),
                hint = "Liquid + reserve + pension",
                accentColor = GoodGreen,
                modifier = itemWidth,
                testTagStr = "kpi_net_worth"
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
                            modifier = Modifier.size(28.dp)
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
            state = state
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Net Worth Chart
        NetWorthChart(data = state.dualTrajectory)

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Highlights
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CardHeaderPill(
                    title = "Strategic Highlights",
                    subtitle = "Monthly dynamics & tax efficiency",
                    badgeText = "KEY STATS",
                    accentColor = BrandTeal
                )
                Spacer(modifier = Modifier.height(14.dp))
                SummaryRow(label = "Monthly surplus", value = fmtCZK(state.currentIncome.totalMonthly - state.totalLivingCostMonthly))
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow(label = "Emergency coverage", value = "${String.format("%.1f", state.emergencyCoverageMonths)} months")
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow(label = "Annual DIP tax saving", value = fmtCZK(state.taxReturnHelper.dipSaving))
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• $label:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = BrandTeal,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
