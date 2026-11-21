package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import kotlin.math.roundToInt

data class AnnualLedgerSummary(
    val year: Int,
    val monthsCount: Int,
    val totalInflows: Double,
    val totalExpenses: Double,
    val netSurplus: Double,
    val savingsRatePct: Double,
    val openingNetWorth: Double,
    val closingNetWorth: Double,
    val netWorthGrowth: Double
)

@Composable
fun YoYRetrospectiveCard(
    ledgerEntries: List<LedgerEntryEntity>,
    modifier: Modifier = Modifier
) {
    if (ledgerEntries.isEmpty()) return

    val annualSummaries = remember(ledgerEntries) {
        val byYear = ledgerEntries.groupBy {
            it.yearMonth.take(4).toIntOrNull() ?: 2026
        }
        val allSortedWithNw = ledgerEntries.filter { it.totalNetWorthAtMonthEnd > 0 }.sortedBy { it.yearMonth }
        byYear.keys.sortedDescending().map { yr ->
            val entries = byYear[yr]?.sortedBy { it.yearMonth } ?: emptyList()
            val totalIn = entries.sumOf { it.incVaclav.finiteOrZero() + it.incEleonora.finiteOrZero() + it.incUnforeseen.finiteOrZero() }.finiteOrZero()
            val totalExp = entries.sumOf { it.expRent.finiteOrZero() + it.expGroceries.finiteOrZero() + it.expOther.finiteOrZero() }.finiteOrZero()
            val surplus = (totalIn - totalExp).finiteOrZero()
            val sr = if (totalIn > 0) ((surplus / totalIn) * 100.0).finiteOrZero().coerceIn(0.0, 100.0) else 0.0

            val entriesWithNw = entries.filter { it.totalNetWorthAtMonthEnd.finiteOrZero() > 0 }
            val prevYearEndNw = allSortedWithNw.lastOrNull { (it.yearMonth.take(4).toIntOrNull() ?: 0) < yr }?.totalNetWorthAtMonthEnd?.finiteOrZero()
            val openNw = prevYearEndNw ?: entriesWithNw.firstOrNull()?.totalNetWorthAtMonthEnd?.finiteOrZero() ?: 0.0
            val closeNw = entriesWithNw.lastOrNull()?.totalNetWorthAtMonthEnd?.finiteOrZero() ?: 0.0
            val nwGrowth = if (openNw > 0 && closeNw > 0) (closeNw - openNw).finiteOrZero() else 0.0

            AnnualLedgerSummary(
                year = yr,
                monthsCount = entries.size,
                totalInflows = totalIn,
                totalExpenses = totalExp,
                netSurplus = surplus,
                savingsRatePct = sr,
                openingNetWorth = openNw,
                closingNetWorth = closeNw,
                netWorthGrowth = nwGrowth
            )
        }
    }

    if (annualSummaries.isEmpty()) return

    var selectedYear by remember(annualSummaries) {
        mutableIntStateOf(annualSummaries.first().year)
    }
    val currentSummary = annualSummaries.firstOrNull { it.year == selectedYear } ?: annualSummaries.first()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CardHeaderPill(
                title = "Annual Retrospective",
                subtitle = "Realized earnings, living expenses & net worth growth",
                badgeText = "YOY PROGRESS",
                accentColor = BrandTeal
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Year Selector Chips if multiple years exist
            if (annualSummaries.size > 1) {
                ChipWrapRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalSpacing = 8.dp
                ) {
                    annualSummaries.forEach { summary ->
                        val isSelected = summary.year == selectedYear
                        AssistChip(
                            onClick = { selectedYear = summary.year },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            label = {
                                Text(
                                    text = "${summary.year} (${summary.monthsCount} ${if (summary.monthsCount == 1) "month" else "months"})",
                                    maxLines = 2,
                                    softWrap = true,
                                    fontFamily = BodyFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) BrandTeal.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                labelColor = if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4 Metric Grid for the Selected Year
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total Inflows
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoodGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Annual Inflows",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = fmtCompact(currentSummary.totalInflows),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                color = GoodGreen,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }

                // Total Living Outflows
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BadRed.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BadRed.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Annual Expenses",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = fmtCompact(currentSummary.totalExpenses),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                color = BadRed,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Net Savings Surplus
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandTeal.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Net Saved",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 4.dp)
                            )
                            ColorPill(
                                text = "${currentSummary.savingsRatePct.roundToInt()}%",
                                color = if (currentSummary.savingsRatePct >= 40) GoodGreen else BrandGold,
                                fontSize = 9.5.sp,
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.Bold,
                                horizontalPadding = 4.dp,
                                verticalPadding = 1.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = (if (currentSummary.netSurplus >= 0) "+ " else "") + fmtCompact(currentSummary.netSurplus),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                color = if (currentSummary.netSurplus >= 0) BrandTeal else BadRed,
                                fontSize = 13.5.sp
                            ),
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }

                // Net Worth Added / Capital Growth
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Wealth Added",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        if (currentSummary.closingNetWorth > 0) {
                            Text(
                                text = (if (currentSummary.netWorthGrowth >= 0) "+ " else "") + fmtCompact(currentSummary.netWorthGrowth),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = BodyFontFamily,
                                    color = if (currentSummary.netWorthGrowth >= 0) GoodGreen else BadRed,
                                    fontSize = 13.5.sp
                                ),
                                maxLines = 2,
                                softWrap = true
                            )
                        } else {
                            Text(
                                text = "Log snapshots",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }

            // Bottom Summary Strip
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentSummary.year} Coverage: ${currentSummary.monthsCount} / 12 months logged",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = BodyFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    if (currentSummary.closingNetWorth > 0) {
                        Text(
                            text = "Latest Wealth: ${fmtCompact(currentSummary.closingNetWorth)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = BodyFontFamily,
                                fontSize = 11.sp,
                                color = BrandTeal
                            ),
                            maxLines = 2,
                            softWrap = true
                        )
                    }
                }
            }
        }
    }
}