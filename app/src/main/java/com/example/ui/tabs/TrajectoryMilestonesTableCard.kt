package com.example.ui.tabs

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PortfolioYearPoint
import com.example.ui.components.ColorPill
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BrandBlue
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
        val header = "Year\tLiquid Portfolio (Kč)\tPension Portfolio (Kč)\tTotal Net Worth (Kč)\tFIRE Target (Kč)\tStatus\n"
        val rows = displayPoints.joinToString("\n") { pt ->
            val status = if (pt.year == fireYear) "FIRE Milestone" else if (pt.totalPortfolio >= pt.target) "FIRE Ready" else "Accumulation"
            "${pt.year}\t${pt.portfolio.toLong()}\t${pt.pensionPortfolio.toLong()}\t${pt.totalPortfolio.toLong()}\t${pt.target.toLong()}\t$status"
        }
        val clip = ClipData.newPlainText("FIRE Trajectory Table", header + rows)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ClipDescription.EXTRA_IS_SENSITIVE
                } else {
                    "android.content.extra.IS_SENSITIVE"
                },
                true
            )
        }
        val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        sysClipboard?.setPrimaryClip(clip)
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
                        text = if (isRealPurchasingPower) "Today's Kč (${String.format(Locale.ROOT, "%.1f", cpiInflationPct)}% CPI) · Tap year to inspect" else "Nominal growth · Tap year to inspect",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                IconButton(
                    onClick = { exportToClipboard() },
                    modifier = Modifier.size(48.dp).testTag("btn_export_trajectory_grid")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy table to clipboard",
                        tint = BrandTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Year",
                    modifier = Modifier.weight(1.22f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Liquid",
                    modifier = Modifier.weight(0.95f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pension",
                    modifier = Modifier.weight(0.95f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Invested",
                    modifier = Modifier.weight(1.25f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
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
                            .heightIn(min = 48.dp)
                            .background(rowBg, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedYearForDetail = if (isSelected) null else pt.year
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1.22f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${pt.year}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = BodyFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = if (isFire || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isFire) BrandGold else if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            if (isFire) {
                                Spacer(modifier = Modifier.width(3.dp))
                                ColorPill(text = "FIRE", color = BrandGold, fontSize = 7.5.sp, horizontalPadding = 3.dp, verticalPadding = 1.dp)
                            }
                        }
                        Text(
                            text = fmtCompact(pt.portfolio),
                            modifier = Modifier.weight(0.95f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = BodyFontFamily,
                                fontSize = 11.sp,
                                color = BrandTeal,
                                textAlign = TextAlign.End,
                                fontWeight = if (isFire || isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = fmtCompact(pt.pensionPortfolio),
                            modifier = Modifier.weight(0.95f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = BodyFontFamily,
                                fontSize = 11.sp,
                                color = BrandBlue,
                                textAlign = TextAlign.End,
                                fontWeight = if (isFire || isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = fmtCompact(pt.totalPortfolio),
                            modifier = Modifier.weight(1.25f),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = BodyFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFire) GoodGreen else if (isSelected) BrandTeal else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("toggle_all_years_button")
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

@OptIn(ExperimentalLayoutApi::class)
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, BrandTeal.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag("year_detail_inspector_${point.year}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title & Status
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "Year ${point.year} Breakdown",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isFire) {
                        ColorPill(text = "FIRE REACHED", color = BrandGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    } else if (point.totalPortfolio >= point.target) {
                        ColorPill(text = "FI READY", color = GoodGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "${String.format(Locale.ROOT, "%.1f", targetPct)}% of Target",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = BodyFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (targetPct >= 100.0) GoodGreen else BrandTeal
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Portfolio Split Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Liquid: ${fmtCompact(point.portfolio)} (${String.format(Locale.ROOT, "%.0f", liquidPct * 100)}%)",
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontFamily = BodyFontFamily,
                        color = BrandTeal,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pension: ${fmtCompact(point.pensionPortfolio)} (${String.format(Locale.ROOT, "%.0f", (1.0 - liquidPct) * 100)}%)",
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontFamily = BodyFontFamily,
                        color = BrandBlue,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { liquidPct.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = BrandTeal,
                trackColor = BrandBlue,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Cashflow Metrics Row
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useCompact = maxWidth < 290.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // SWR Safe Drawdown Box
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Safe Drawdown (${fmtPct(swrPct)}${if (isPensionUnlocked) " • 60+ Unlocked" else " • Liquid"})",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 2,
                                softWrap = true
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${if (useCompact) fmtCompact(monthlySwrCapacity) else fmtCZK(monthlySwrCapacity)} / mo",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = BodyFontFamily,
                                    color = BrandTeal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${if (useCompact) fmtCompact(annualSwrCapacity) else fmtCZK(annualSwrCapacity)} / yr",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontFamily = BodyFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // FIRE Target Box
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "FIRE Target Capital",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 2,
                                softWrap = true
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (useCompact) fmtCompact(point.target) else fmtCZK(point.target),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = BodyFontFamily,
                                    color = if (point.totalPortfolio >= point.target) GoodGreen else MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val gap = point.target - point.totalPortfolio
                            Text(
                                text = if (gap > 0) "Gap: -${if (useCompact) fmtCompact(gap) else fmtCZK(gap)}" else "Surplus: +${if (useCompact) fmtCompact(-gap) else fmtCZK(-gap)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontFamily = BodyFontFamily,
                                    color = if (gap > 0) MaterialTheme.colorScheme.onSurfaceVariant else GoodGreen,
                                    fontWeight = if (gap <= 0) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
