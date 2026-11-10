package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsEntity
import com.example.domain.PortfolioYearPoint
import com.example.ui.components.ColorPill
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import kotlin.math.max
import kotlin.math.round

@Composable
fun DcaTrajectoryBarChart(
    data: List<PortfolioYearPoint>,
    settings: SettingsEntity? = null,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val cTeal = BrandTeal
    val cGold = BrandGold
    val cGreen = GoodGreen

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }
    var viewMode by remember { mutableIntStateOf(0) } // 0 = Annual Inflow Streams, 1 = Principal vs Compound Gains

    val initialBal = remember(data) { data.firstOrNull()?.totalPortfolio ?: 0.0 }

    // Precalculate cumulative invested principal and cumulative compound gains
    val cumulativeData = remember(data, initialBal) {
        var runningPrincipal = initialBal
        data.mapIndexed { idx, pt ->
            if (idx > 0) {
                runningPrincipal += pt.investedAnnual + pt.pensionInflowAnnual + pt.reinvestAnnual + pt.lumpSum
            }
            val gains = (pt.totalPortfolio - runningPrincipal).coerceAtLeast(0.0)
            Triple(pt, runningPrincipal, gains)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dca_trajectory_bar_chart_card")
            .semantics {
                contentDescription = "DCA trajectory bar chart visualizing annual contribution streams, growth rates, and compound market gains."
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ColorPill(
                        text = "DCA & WEALTH ENGINE",
                        color = BrandTeal,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 5.dp,
                        verticalPadding = 2.dp,
                        cornerRadius = 5.dp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "DCA & Compounding Breakdown",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        softWrap = true
                    )
                    Text(
                        text = if (viewMode == 0) "Annual savings inflows & reinvestment" else "Invested capital vs compound gains",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        softWrap = true
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(4.0f) },
                        modifier = Modifier.size(40.dp).testTag("dca_bar_zoom_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale / 1.25f).coerceAtLeast(1.0f)
                            if (zoomScale == 1.0f) panOffsetX = 0.0f
                        },
                        modifier = Modifier.size(40.dp).testTag("dca_bar_zoom_out")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0.0f
                            selectedIndex = null
                        },
                        modifier = Modifier.size(40.dp).testTag("dca_bar_zoom_reset")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { viewMode = 0 },
                    label = {
                        Text(
                            "Inflow Streams",
                            fontSize = 11.sp,
                            fontWeight = if (viewMode == 0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (viewMode == 0) BrandTeal.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        labelColor = if (viewMode == 0) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).testTag("dca_chip_annual_inflows")
                )
                AssistChip(
                    onClick = { viewMode = 1 },
                    label = {
                        Text(
                            "Principal vs Gains",
                            fontSize = 11.sp,
                            fontWeight = if (viewMode == 1) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (viewMode == 1) GoodGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        labelColor = if (viewMode == 1) GoodGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f).testTag("dca_chip_principal_gains")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Legend Pillboxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (viewMode == 0) {
                    ColorPill(
                        text = "Base DCA",
                        color = cTeal,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 3.dp,
                        cornerRadius = 6.dp,
                        modifier = Modifier.weight(1f)
                    )
                    ColorPill(
                        text = "Reinvestment",
                        color = cGold,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 3.dp,
                        cornerRadius = 6.dp,
                        modifier = Modifier.weight(1f)
                    )
                    ColorPill(
                        text = "Lump Sum",
                        color = cGreen,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 3.dp,
                        cornerRadius = 6.dp,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ColorPill(
                        text = "Invested Principal",
                        color = cTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        horizontalPadding = 8.dp,
                        verticalPadding = 3.dp,
                        cornerRadius = 6.dp,
                        modifier = Modifier.weight(1f)
                    )
                    ColorPill(
                        text = "Compound Profit",
                        color = cGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        horizontalPadding = 8.dp,
                        verticalPadding = 3.dp,
                        cornerRadius = 6.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val maxVal = remember(data, viewMode, cumulativeData) {
                if (viewMode == 0) {
                    ((data.maxOfOrNull { it.investedAnnual + it.pensionInflowAnnual + it.reinvestAnnual + it.lumpSum } ?: 100000.0) * 1.25).coerceAtLeast(100000.0)
                } else {
                    ((data.maxOfOrNull { it.totalPortfolio } ?: 1000000.0) * 1.15).coerceAtLeast(100000.0)
                }
            }

            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).toArgb()

            val paddingTop = 18f
            val paddingLeft = 75f
            val paddingRight = 24f
            val paddingBottom = 45f
            val textPx = with(LocalDensity.current) { 10.sp.toPx() }

            val textPaint = remember(textColor, textPx) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = textPx
                    isAntiAlias = true
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .pointerInput(data) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1.0f, 4.0f)
                                val maxPan = (size.width * (zoomScale - 1f))
                                panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPan, 0f)
                            }
                        }
                        .pointerInput(data) {
                            detectTapGestures { offset ->
                                val chartWidth = (size.width - paddingLeft - paddingRight) * zoomScale
                                val relativeX = offset.x - paddingLeft - panOffsetX
                                val stepX = if (data.size > 1) chartWidth / (data.size - 1).toFloat() else chartWidth
                                val clickedIdx = if (data.size > 1 && stepX > 0f) round(relativeX / stepX).toInt().coerceIn(0, data.size - 1) else 0
                                selectedIndex = clickedIdx
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft - paddingRight
                    val plotH = h - paddingBottom
                    val usableH = plotH - paddingTop
                    if (w <= 0f || h <= 0f || plotW <= 0f || usableH <= 0f) return@Canvas

                    fun yPix(v: Double): Float =
                        paddingTop + (usableH - usableH * (v / maxVal).coerceIn(0.0, 1.0)).toFloat()

                    // Y-Axis
                    val ySteps = 4
                    for (i in 0..ySteps) {
                        val valAtStep = maxVal * i / ySteps
                        val y = yPix(valAtStep)

                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, y),
                            end = Offset(w - paddingRight, y),
                            strokeWidth = 1.5f
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            fmtCompact(valAtStep),
                            8f,
                            y + 7f,
                            textPaint
                        )
                    }

                    // X-Axis
                    val chartWidth = plotW * zoomScale
                    val stepX = if (data.size > 1) chartWidth / (data.size - 1).toFloat() else chartWidth
                    val barWidth = (stepX * 0.55f).coerceIn(5f, 28f)

                    val xStepCount = 5
                    for (i in 0 until data.size step max(1, data.size / xStepCount)) {
                        val pt = data[i]
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        if (x in paddingLeft..(w - paddingRight + 10f)) {
                            drawLine(
                                color = if (i == 0) cTeal else gridColor,
                                start = Offset(x, plotH),
                                end = Offset(x, plotH + 6f),
                                strokeWidth = if (i == 0) 2.5f else 1.5f
                            )
                            val labelText = if (i == 0) "${pt.year} (Now)" else "${pt.year}"
                            val textWidth = textPaint.measureText(labelText)
                            drawContext.canvas.nativeCanvas.drawText(
                                labelText,
                                (x - textWidth / 2f).coerceIn(paddingLeft, w - paddingRight - textWidth),
                                plotH + 30f,
                                textPaint
                            )
                        }
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, w - paddingRight, plotH)

                    // Draw Bars
                    data.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val left = x - barWidth / 2f
                        val right = x + barWidth / 2f

                        if (right >= paddingLeft && left <= (w - paddingRight)) {
                            if (viewMode == 0) {
                                // Annual Streams
                                val hBase = ((pt.investedAnnual + pt.pensionInflowAnnual) / maxVal).toFloat() * usableH
                                val hReinvest = (pt.reinvestAnnual / maxVal).toFloat() * usableH
                                val hLump = (pt.lumpSum / maxVal).toFloat() * usableH

                                var currentY = plotH

                                if (hBase > 0) {
                                    drawRect(
                                        color = cTeal,
                                        topLeft = Offset(left, currentY - hBase),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hBase)
                                    )
                                    currentY -= hBase
                                }
                                if (hReinvest > 0) {
                                    drawRect(
                                        color = cGold,
                                        topLeft = Offset(left, currentY - hReinvest),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hReinvest)
                                    )
                                    currentY -= hReinvest
                                }
                                if (hLump > 0) {
                                    drawRect(
                                        color = cGreen,
                                        topLeft = Offset(left, currentY - hLump),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hLump)
                                    )
                                }
                            } else {
                                // Principal vs Gains
                                val (_, principal, gains) = cumulativeData[i]
                                val hPrincipal = ((principal / maxVal).toFloat() * usableH).coerceAtLeast(0f)
                                val hGains = ((gains / maxVal).toFloat() * usableH).coerceAtLeast(0f)

                                var currentY = plotH
                                if (hPrincipal > 0) {
                                    drawRect(
                                        color = cTeal,
                                        topLeft = Offset(left, currentY - hPrincipal),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hPrincipal)
                                    )
                                    currentY -= hPrincipal
                                }
                                if (hGains > 0) {
                                    drawRect(
                                        color = cGreen,
                                        topLeft = Offset(left, currentY - hGains),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hGains)
                                    )
                                }
                            }

                            // Selection highlight indicator
                            if (selectedIndex == i) {
                                drawLine(
                                    color = cTeal.copy(alpha = 0.6f),
                                    start = Offset(x, paddingTop),
                                    end = Offset(x, plotH),
                                    strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                                )
                                drawCircle(color = cTeal, radius = 6f, center = Offset(x, 14f))
                            }
                        }
                    }

                    drawContext.canvas.restore()
                }
            }

            // Interactive Tooltip Box
            val activeIdx = selectedIndex ?: (if (data.isNotEmpty()) data.size - 1 else 0)
            val activeTriple = cumulativeData.getOrNull(activeIdx)
            if (activeTriple != null) {
                val (pt, principal, gains) = activeTriple
                val totalInflow = pt.investedAnnual + pt.pensionInflowAnnual + pt.reinvestAnnual + pt.lumpSum
                val gainsPct = if (pt.totalPortfolio > 0) (gains / pt.totalPortfolio * 100.0) else 0.0

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("dca_tooltip_box")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Year ${pt.year}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ColorPill(
                                    text = "Age ${pt.age}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp,
                                    cornerRadius = 4.dp
                                )
                            }
                            ColorPill(
                                text = pt.status,
                                color = if (pt.status == "FIRE OK" || pt.status == "FIRE READY") GoodGreen else BrandTeal,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                horizontalPadding = 6.dp,
                                verticalPadding = 2.5.dp,
                                cornerRadius = 6.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Grid of Pillboxes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartMetricPill(
                                label = "Monthly DCA",
                                value = fmtCompact((pt.investedAnnual + pt.pensionInflowAnnual) / 12.0),
                                color = BrandTeal,
                                modifier = Modifier.weight(1f)
                            )
                            ChartMetricPill(
                                label = "Annual Inflow",
                                value = fmtCompact(totalInflow),
                                color = BrandGold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartMetricPill(
                                label = "Principal",
                                value = fmtCompact(principal),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            ChartMetricPill(
                                label = "Profit (${gainsPct.toInt()}%)",
                                value = "+${fmtCompact(gains)}",
                                color = GoodGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartMetricPill(
                                label = "Portfolio Balance",
                                value = fmtCompact(pt.portfolio),
                                color = BrandTeal,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                            ChartMetricPill(
                                label = "FIRE Target",
                                value = fmtCompact(pt.target),
                                color = BrandGold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Embedded Monthly Allocation Bar
            if (settings != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))
                DcaAllocationBreakdownBar(settings = settings)
            }
        }
    }
}
