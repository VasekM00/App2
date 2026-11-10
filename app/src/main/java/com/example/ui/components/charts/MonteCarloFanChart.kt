package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.MonteCarloPoint
import com.example.ui.components.ColorPill
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonteCarloFanChart(
    points: List<MonteCarloPoint>,
    cpiPct: Double = 2.8,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val baseYear = points.first().year
    val cpiRate = (cpiPct / 100.0).coerceAtLeast(0.0)
    // Deflate every point back to base-year purchasing power
    val realPoints = remember(points, cpiPct) {
        points.map { pt ->
            val deflator = (1.0 + cpiRate).pow((pt.year - baseYear).toDouble()).coerceAtLeast(1.0)
            pt.copy(
                p5     = pt.p5     / deflator,
                p50    = pt.p50    / deflator,
                p95    = pt.p95    / deflator,
                target = pt.target / deflator
            )
        }
    }

    val cTeal = BrandTeal
    val cGreen = GoodGreen
    val cRed = BadRed
    val cGold = BrandGold
    val cCardSurface = MaterialTheme.colorScheme.surface

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monte_carlo_chart_card")
            .semantics {
                contentDescription =
                    "Monte Carlo simulation fan chart showing the 5th, 50th and 95th percentile portfolio outcomes and the FIRE target over ${realPoints.size} years, inflation-adjusted to today's purchasing power."
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ColorPill(
                text = "MONTE CARLO PROBABILITY",
                color = BrandTeal,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                horizontalPadding = 6.dp,
                verticalPadding = 2.dp,
                cornerRadius = 6.dp
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Multi-Run Simulation Fan",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "P95 Upper Bound, P50 Median, P5 Lower Bound — real CZK (${String.format(java.util.Locale.getDefault(), "%.1f", cpiPct)}% CPI-deflated to today's buying power)",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cGreen, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "P95", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cTeal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "P50", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cRed, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "P5", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cGold, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "FIRE Target", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val maxVal = ((realPoints.maxOfOrNull { maxOf(it.p95, it.target) } ?: 0.0) * 1.1).coerceAtLeast(100.0)
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).toArgb()

            val paddingTop = 18f
            val paddingLeft = 85f
            val paddingRight = 32f
            val paddingBottom = 50f
            val textPx = with(LocalDensity.current) { 10.sp.toPx() }

            val textPaint = remember(textColor, textPx) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = textPx
                    isAntiAlias = true
                }
            }

            val haptic = LocalHapticFeedback.current
            val updateScrubber: (Float, Float) -> Unit = { touchX, width ->
                val plotW = width - paddingLeft - paddingRight
                val stepX = if (realPoints.size > 1) plotW / (realPoints.size - 1).toFloat() else plotW
                val relativeX = touchX - paddingLeft
                if (stepX > 0f && realPoints.isNotEmpty()) {
                    val clickedIdx = round(relativeX / stepX).toInt().coerceIn(0, realPoints.size - 1)
                    if (clickedIdx != selectedIndex) {
                        selectedIndex = clickedIdx
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .pointerInput(realPoints) {
                            detectDragGestures(
                                onDragStart = { offset -> updateScrubber(offset.x, size.width.toFloat()) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    updateScrubber(change.position.x, size.width.toFloat())
                                }
                            )
                        }
                        .pointerInput(realPoints) {
                            detectTapGestures(
                                onTap = { offset -> updateScrubber(offset.x, size.width.toFloat()) }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft - paddingRight
                    val plotH = h - paddingBottom
                    val usableH = plotH - paddingTop
                    if (w <= 0f || h <= 0f || plotW <= 0f || usableH <= 0f) return@Canvas
                    val stepX = if (realPoints.size > 1) plotW / (realPoints.size - 1).toFloat() else plotW

                    fun yPix(v: Double): Float =
                        paddingTop + (usableH - usableH * (v / maxVal).coerceIn(0.0, 1.0)).toFloat()

                    // Draw Y-Axis lines and numeric labels
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
                            10f,
                            y + 8f,
                            textPaint
                        )
                    }

                    // Draw X-Axis Year Labels
                    val xStepCount = 5
                    for (i in 0 until realPoints.size step max(1, realPoints.size / xStepCount)) {
                        val pt = realPoints[i]
                        val x = paddingLeft + (i * stepX)
                        drawLine(
                            color = if (i == 0) cTeal else gridColor,
                            start = Offset(x, plotH),
                            end = Offset(x, plotH + 8f),
                            strokeWidth = if (i == 0) 3f else 2f
                        )
                        val labelText = if (i == 0) "${pt.year} (Now)" else "${pt.year}"
                        val textWidth = textPaint.measureText(labelText)
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            (x - textWidth / 2f).coerceIn(paddingLeft, w - paddingRight - textWidth),
                            plotH + 34f,
                            textPaint
                        )
                    }

                    // Clip plot area
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, w - paddingRight, plotH)

                    // P95 -> P5 Area Shade
                    val fillPath = Path()
                    realPoints.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val yP95 = yPix(pt.p95)
                        if (i == 0) fillPath.moveTo(x, yP95) else fillPath.lineTo(x, yP95)
                    }
                    realPoints.reversed().forEachIndexed { i, pt ->
                        val origIdx = realPoints.size - 1 - i
                        val x = paddingLeft + (origIdx * stepX)
                        val yP5 = yPix(pt.p5)
                        fillPath.lineTo(x, yP5)
                    }
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        color = cTeal.copy(alpha = 0.18f)
                    )

                    // P95 Line (Green)
                    val p95Path = Path()
                    realPoints.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val yP95 = yPix(pt.p95)
                        if (i == 0) p95Path.moveTo(x, yP95) else p95Path.lineTo(x, yP95)
                    }
                    drawPath(p95Path, cGreen, style = Stroke(width = 3f))

                    // P5 Line (Red)
                    val p5Path = Path()
                    realPoints.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val yP5 = yPix(pt.p5)
                        if (i == 0) p5Path.moveTo(x, yP5) else p5Path.lineTo(x, yP5)
                    }
                    drawPath(p5Path, cRed, style = Stroke(width = 3f))

                    // P50 Median Line (Teal Thick)
                    val p50Path = Path()
                    realPoints.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val yP50 = yPix(pt.p50)
                        if (i == 0) p50Path.moveTo(x, yP50) else p50Path.lineTo(x, yP50)
                    }
                    drawPath(p50Path, cTeal, style = Stroke(width = 5f, cap = StrokeCap.Round))

                    // FIRE Target Line (Gold Dashed)
                    val targetPath = Path()
                    realPoints.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val yTarget = yPix(pt.target)
                        if (i == 0) targetPath.moveTo(x, yTarget) else targetPath.lineTo(x, yTarget)
                    }
                    drawPath(
                        targetPath,
                        cGold,
                        style = Stroke(
                            width = 3.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    )

                    // Draw Current Position (Now / Start Year) Indicator Dot
                    if (realPoints.isNotEmpty()) {
                        val curX = paddingLeft + (0 * stepX)
                        val curY = plotH - (plotH * (realPoints[0].p50 / maxVal)).toFloat()
                        drawLine(
                            color = cTeal.copy(alpha = 0.35f),
                            start = Offset(curX, curY),
                            end = Offset(curX, plotH),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                        drawCircle(color = cTeal.copy(alpha = 0.25f), radius = 14f, center = Offset(curX, curY))
                        drawCircle(color = cTeal, radius = 8f, center = Offset(curX, curY))
                        drawCircle(color = cCardSurface, radius = 4f, center = Offset(curX, curY))
                    }

                    // Highlight selected point marker
                    selectedIndex?.let { idx ->
                        val sx = paddingLeft + (idx * stepX)
                        val sy = plotH - (plotH * (realPoints[idx].p50 / maxVal)).toFloat()
                        val syTarget = plotH - (plotH * (realPoints[idx].target / maxVal)).toFloat()
                        drawLine(
                            color = cTeal.copy(alpha = 0.5f),
                            start = Offset(sx, 0f),
                            end = Offset(sx, plotH),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        drawCircle(color = cTeal, radius = 10f, center = Offset(sx, sy))
                        drawCircle(color = cGold, radius = 6f, center = Offset(sx, syTarget))
                    }

                    drawContext.canvas.restore()
                }
            }

            // Interactive Detail Tooltip Box
            val activePoint = selectedIndex?.let { realPoints.getOrNull(it) } ?: realPoints.lastOrNull()
            activePoint?.let { pt ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().testTag("monte_carlo_tooltip_box")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Year ${pt.year} (Age ${pt.age}) — real CZK range:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "P50: ${fmtCompact(pt.p50)}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BrandTeal
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "P5 (Pessimistic): ${fmtCompact(pt.p5)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = BadRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "P95 (Optimistic): ${fmtCompact(pt.p95)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = GoodGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Historical bootstrap uses actual MSCI 1970-2025 returns (avg ~12% nominal). Values shown in today's purchasing power (${String.format(java.util.Locale.getDefault(), "%.1f", cpiPct)}% CPI deflated).",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }
                }
            }
        }
    }
}
