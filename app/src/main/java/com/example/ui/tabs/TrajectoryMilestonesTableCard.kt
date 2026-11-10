package com.example.ui.tabs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PortfolioYearPoint
import com.example.ui.components.ColorPill
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import java.util.Locale
import kotlin.math.pow

@Composable
fun TrajectoryMilestonesTableCard(
    trajectory: List<PortfolioYearPoint>,
    isRealPurchasingPower: Boolean,
    cpiInflationPct: Double,
    swrPct: Double = 3.5,
    firePoint: PortfolioYearPoint? = null,
    modifier: Modifier = Modifier
) {
    if (trajectory.isEmpty()) return
    var showAllYears by rememberSaveable { mutableStateOf(false) }
    var selectedYearForDetail by rememberSaveable { mutableStateOf<Int?>(null) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val displayPoints = remember(trajectory, isRealPurchasingPower, cpiInflationPct) {
        if (!isRealPurchasingPower) {
            trajectory
        } else {
            trajectory.mapIndexed { idx, pt ->
                val discount = (1.0 + (cpiInflationPct / 100.0)).coerceAtLeast(0.0001).pow(idx.toDouble())
                val discPort = pt.portfolio / discount
                val discPension = pt.pensionPortfolio / discount
                pt.copy(
                    portfolio = discPort,
                    target = pt.target / discount,
                    pensionPortfolio = discPension,
                    totalPortfolio = discPort + discPension
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

    fun exportToClipboard() {
        val header = "Year\tLiquid Portfolio (CZK)\tPension Portfolio (CZK)\tTotal Net Worth (CZK)\tFIRE Target (CZK)\tStatus\n"
        val rows = displayPoints.joinToString("\n") { pt ->
            val status = if (pt.year == fireYear) "FIRE Milestone" else if (pt.totalPortfolio >= pt.target) "FIRE Ready" else "Accumulation"
            "${pt.year}\t${pt.portfolio.toLong()}\t${pt.pensionPortfolio.toLong()}\t${pt.totalPortfolio.toLong()}\t${pt.target.toLong()}\t$status"
        }
        clipboardManager.setText(AnnotatedString(header + rows))
        Toast.makeText(context, "Copied 35-year trajectory table to clipboard (TSV format)", Toast.LENGTH_SHORT).show()
    }

    Card(
        modifier = modifier.fillMaxWidth().testTag("trajectory_milestones_table_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { exportToClipboard() },
                        modifier = Modifier.size(28.dp).testTag("btn_export_trajectory_grid")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy table to clipboard",
                            tint = BrandTeal,
                            modifier = Modifier.size(16.dp)
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle hint
            Text(
                text = "Tap any year to inspect portfolio breakdown & safe withdrawal capacity",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Year", modifier = Modifier.weight(0.85f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                Text("Liquid", modifier = Modifier.weight(1.05f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End))
                Text("Pension", modifier = Modifier.weight(1.05f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End))
                Text("Total Net Worth", modifier = Modifier.weight(1.45f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End))
            }

            Spacer(modifier = Modifier.height(6.dp))

            rowsToShow.forEachIndexed { rowIdx, pt ->
                val isFire = pt.year == fireYear
                val isSelected = selectedYearForDetail == pt.year
                val rowBg = when {
                    isSelected -> BrandTeal.copy(alpha = 0.12f)
                    isFire -> BrandGold.copy(alpha = 0.14f)
                    rowIdx % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg, RoundedCornerShape(6.dp))
                            .clickable {
                                selectedYearForDetail = if (isSelected) null else pt.year
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(0.85f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${pt.year}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isFire || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isFire) BrandGold else if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            if (isFire) {
                                Spacer(modifier = Modifier.width(4.dp))
                                ColorPill(text = "FIRE", color = BrandGold, fontSize = 7.5.sp, horizontalPadding = 3.dp, verticalPadding = 1.dp)
                            }
                        }
                        Text(
                            text = fmtCompact(pt.portfolio),
                            modifier = Modifier.weight(1.05f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = BrandTeal,
                                textAlign = TextAlign.End,
                                fontWeight = if (isFire || isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        Text(
                            text = fmtCompact(pt.pensionPortfolio),
                            modifier = Modifier.weight(1.05f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF6366F1),
                                textAlign = TextAlign.End,
                                fontWeight = if (isFire || isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        Text(
                            text = fmtCompact(pt.totalPortfolio),
                            modifier = Modifier.weight(1.45f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isFire) GoodGreen else if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End
                            )
                        )
                    }

                    // Expanded Year Inspector Badge
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        YearDetailInspectorCard(
                            point = pt,
                            swrPct = swrPct,
                            isFire = isFire
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f))
                }
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

@Composable
private fun YearDetailInspectorCard(
    point: PortfolioYearPoint,
    swrPct: Double,
    isFire: Boolean
) {
    val total = point.totalPortfolio.coerceAtLeast(1.0)
    val liquidPct = (point.portfolio / total).coerceIn(0.0, 1.0)
    val isPensionUnlocked = point.age >= 60
    val accessiblePortfolio = if (isPensionUnlocked) point.totalPortfolio else point.portfolio
    val annualSwrCapacity = accessiblePortfolio * (swrPct / 100.0)
    val monthlySwrCapacity = annualSwrCapacity / 12.0
    val targetPct = ((point.totalPortfolio / point.target.coerceAtLeast(1.0)) * 100.0).coerceAtMost(999.0)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("year_detail_inspector_${point.year}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Year ${point.year} Breakdown",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    if (isFire) {
                        ColorPill(text = "FIRE REACHED", color = BrandGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    } else if (point.totalPortfolio >= point.target) {
                        ColorPill(text = "FINANCIALLY INDEPENDENT", color = GoodGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "${String.format(Locale.ROOT, "%.1f", targetPct)}% of Target",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (targetPct >= 100.0) GoodGreen else BrandTeal
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Portfolio Split Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Liquid: ${fmtCZK(point.portfolio)} (${String.format(Locale.ROOT, "%.0f", liquidPct * 100)}%)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        color = BrandTeal,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "Pension: ${fmtCZK(point.pensionPortfolio)} (${String.format(Locale.ROOT, "%.0f", (1.0 - liquidPct) * 100)}%)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        color = Color(0xFF6366F1),
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { liquidPct.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = BrandTeal,
                trackColor = Color(0xFF6366F1),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Cashflow Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // SWR Safe Drawdown Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Safe Drawdown (${fmtPct(swrPct)}${if (isPensionUnlocked) " • 60+ Unlocked" else " • Liquid"})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(
                            text = "${fmtCZK(monthlySwrCapacity)} / mo",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = BrandTeal
                            )
                        )
                        Text(
                            text = "${fmtCZK(annualSwrCapacity)} / yr",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                // FIRE Target Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "FIRE Target Capital",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Text(
                            text = fmtCZK(point.target),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (point.totalPortfolio >= point.target) GoodGreen else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        val gap = point.target - point.totalPortfolio
                        Text(
                            text = if (gap > 0) "Gap: -${fmtCZK(gap)}" else "Surplus: +${fmtCZK(-gap)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                color = if (gap > 0) MaterialTheme.colorScheme.onSurfaceVariant else GoodGreen,
                                fontWeight = if (gap <= 0) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }
    }
}
