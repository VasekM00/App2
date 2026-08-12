package com.example.ui.tabs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.FullCalculationState
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
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

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
                title = "Family net / mo",
                value = fmtCZK(state.currentIncome.totalMonthly),
                hint = "Combined family income",
                accentColor = BrandTeal,
                modifier = itemWidth,
                testTagStr = "kpi_family_net"
            )

            KpiCard(
                title = "Base FIRE target",
                value = fmtCompact(state.fireBaseTargetToday),
                hint = "Target in today's CZK",
                accentColor = BrandGold,
                modifier = itemWidth,
                testTagStr = "kpi_fire_target"
            )

            KpiCard(
                title = "FIRE age (dual)",
                value = state.fireDualPoint?.let { "Age ${it.age}" } ?: ">60",
                hint = state.fireDualPoint?.let { "Projected year ${it.year}" } ?: "Beyond 35y horizon",
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

        Spacer(modifier = Modifier.height(16.dp))

        // Emergency Reserve & Runway Goal Tracker Widget
        EmergencyReserveWidget(state = state)

        Spacer(modifier = Modifier.height(16.dp))

        // Net Worth Chart
        NetWorthChart(data = state.dualTrajectory)

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Highlights
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Strategic Summary",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))
                SummaryRow(label = "Monthly surplus", value = fmtCZK(state.currentIncome.totalMonthly - state.totalLivingCostMonthly))
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow(label = "Emergency coverage", value = "${String.format("%.1f", state.emergencyCoverageMonths)} months")
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow(label = "Annual DIP tax saving", value = fmtCZK(state.taxReturnHelper.dipSaving))
            }
        }
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
