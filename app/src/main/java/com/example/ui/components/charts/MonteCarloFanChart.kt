package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.MonteCarloPoint
import com.example.ui.components.ColorPill
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonteCarloFanChart(
    points: List<MonteCarloPoint>,
    cpiPct: Double = 2.8,
    modifier: Modifier = Modifier,
    currentYearMonth: java.time.YearMonth? = null,
) {
    if (points.isEmpty()) return

    val baseYear = points.first().year
    val safeCpiPct = cpiPct.finiteOrZero().coerceAtLeast(0.0)
    val cpiRate = safeCpiPct / 100.0

    // Deflate every point back to base-year purchasing power
    val realPoints = remember(points, safeCpiPct) {
        points.map { pt ->
            val rawDeflator = (1.0 + cpiRate).pow((pt.year - baseYear).toDouble())
            val deflator = if (rawDeflator.isFinite() && rawDeflator > 0.0) rawDeflator.coerceAtLeast(1.0) else 1.0
            pt.copy(
                p5 = pt.p5.finiteOrZero().coerceAtLeast(0.0) / deflator,
                p50 = pt.p50.finiteOrZero().coerceAtLeast(0.0) / deflator,
                p95 = pt.p95.finiteOrZero().coerceAtLeast(0.0) / deflator,
                target = pt.target.finiteOrZero().coerceAtLeast(0.0) / deflator
            )
        }
    }

    val revealProgress by rememberChartPathReveal(
        key = Pair(points.size, cpiPct),
        durationMillis = 800
    )

    val cTeal = BrandTeal
    val cP95 = BrandBlue
    val cRed = BadRed
    val cGold = BrandGold
    val cCardSurface = MaterialTheme.colorScheme.surface
    val haptic = LocalHapticFeedback.current

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val safeSelectedIndex = selectedIndex?.takeIf { realPoints.isNotEmpty() }?.coerceIn(0, realPoints.lastIndex)

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
                text = "95th Optimistic, 50th Median, 5th Pessimistic · today's Kč (${com.example.util.Formatters.fmtPct(safeCpiPct, 1)} inflation-adjusted)",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cP95, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "P95",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cTeal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "P50",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cRed, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "P5",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cGold, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "FIRE Target",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val ySteps = 4
            val maxVal = remember(realPoints) {
                val rawMax = realPoints.maxOfOrNull { maxOf(it.p95.finiteOrZero(), it.target.finiteOrZero()) } ?: 0.0
                computeNiceAxisMax((rawMax * 1.1).coerceAtLeast(100.0), steps = ySteps)
            }
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

            val density = LocalDensity.current
            val textPx = with(density) { 10.sp.toPx() }

            val textPaint = remember(textColor, textPx) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = textPx
                    typeface = android.graphics.Typeface.MONOSPACE
                    isAntiAlias = true
                }
            }
            val fillPath = remember { Path() }
            val p95Path = remember { Path() }
            val p5Path = remember { Path() }
            val p50Path = remember { Path() }
            val targetPath = remember { Path() }
            val targetDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) }
            val selDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }
            val strokeThin = remember { Stroke(width = 3f) }
            val strokeMedian = remember { Stroke(width = 5f, cap = StrokeCap.Round) }
            val strokeTarget = remember(targetDashEffect) { Stroke(width = 3.5f, pathEffect = targetDashEffect) }
            val nowMarkerResources = rememberNowMarkerResources(cTeal)
            val resolvedNowYm = currentYearMonth ?: remember {
                java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague"))
            }
            val yTickLabels = remember(maxVal) {
                (0..ySteps).map { i -> fmtCompact(maxVal * i / ySteps) }
            }
            val xYearLabels = remember(realPoints) {
                realPoints.map { "${it.year}" }
            }

            val maxYLabelW = remember(yTickLabels, textPaint) {
                yTickLabels.maxOf { label -> textPaint.measureText(label) }
            }
            val paddingTop = with(density) { 14.dp.toPx() }
            val paddingLeft = maxOf(with(density) { 56.dp.toPx() }, maxYLabelW + with(density) { 12.dp.toPx() })
            val paddingRight = with(density) { 16.dp.toPx() }
            val paddingBottom = maxOf(with(density) { 28.dp.toPx() }, textPx + with(density) { 14.dp.toPx() })
            val xLabelGap = with(density) { 6.dp.toPx() }

            val updateScrubber: (Float, Float) -> Unit = { touchX, width ->
                val plotW = width - paddingLeft - paddingRight
                if (plotW > 0f && realPoints.isNotEmpty()) {
                    val clickedIdx = if (realPoints.size > 1) {
                        val stepX = plotW / (realPoints.size - 1).toFloat()
                        val relativeX = touchX - paddingLeft
                        if (stepX > 0f) round(relativeX / stepX).toInt().coerceIn(0, realPoints.lastIndex) else 0
                    } else 0
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
                        .pointerInput(realPoints, paddingLeft, paddingRight) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> updateScrubber(offset.x, size.width.toFloat()) },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    updateScrubber(change.position.x, size.width.toFloat())
                                }
                            )
                        }
                        .pointerInput(realPoints, paddingLeft, paddingRight) {
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
                    val stepX = if (realPoints.size > 1) plotW / (realPoints.size - 1).toFloat() else 0f
                    fun xForIndex(index: Int): Float = if (realPoints.size > 1) {
                        paddingLeft + (index * stepX)
                    } else {
                        paddingLeft + plotW / 2f
                    }

                    fun yPix(v: Double): Float {
                        val frac = safeFraction(v.finiteOrZero().coerceAtLeast(0.0), maxVal)
                        return paddingTop + (usableH - usableH * frac)
                    }

                    // Draw Y-Axis lines and numeric labels
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
                            yTickLabels[i],
                            4.dp.toPx(),
                            (y + textPx * 0.35f).coerceIn(textPx, plotH.coerceAtLeast(textPx)),
                            textPaint
                        )
                    }

                    // Draw X-Axis Year Labels
                    val xStepCount = 5
                    var lastDrawnRight = -Float.MAX_VALUE
                    for (i in 0 until realPoints.size step max(1, realPoints.size / xStepCount)) {
                        val x = xForIndex(i)
                        drawLine(
                            color = gridColor,
                            start = Offset(x, plotH),
                            end = Offset(x, plotH + 5.dp.toPx()),
                            strokeWidth = 2f
                        )
                        val labelText = xYearLabels[i]
                        val textWidth = textPaint.measureText(labelText)
                        val drawX = (x - textWidth / 2f).coerceIn(paddingLeft, (w - paddingRight - textWidth).coerceAtLeast(paddingLeft))
                        if (drawX >= lastDrawnRight + xLabelGap) {
                            drawContext.canvas.nativeCanvas.drawText(
                                labelText,
                                drawX,
                                plotH + 5.dp.toPx() + textPx,
                                textPaint
                            )
                            lastDrawnRight = drawX + textWidth
                        }
                    }

                    // Clip plot area and apply smooth reveal clip
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, w - paddingRight, plotH)

                    val clampedReveal = revealProgress.finiteOrZero().coerceIn(0f, 1f)
                    val revealRight = paddingLeft + (w - paddingRight - paddingLeft) * clampedReveal
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, revealRight, plotH)

                    if (realPoints.size > 1) {
                        // P95 -> P5 Area Shade
                        fillPath.reset()
                        for (i in realPoints.indices) {
                            val pt = realPoints[i]
                            val x = xForIndex(i)
                            val yP95 = yPix(pt.p95)
                            if (i == 0) fillPath.moveTo(x, yP95) else fillPath.lineTo(x, yP95)
                        }
                        for (origIdx in realPoints.lastIndex downTo 0) {
                            val pt = realPoints[origIdx]
                            val x = xForIndex(origIdx)
                            val yP5 = yPix(pt.p5)
                            fillPath.lineTo(x, yP5)
                        }
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            color = cTeal.copy(alpha = 0.18f)
                        )

                        // P95 Line (Blue — visually distinct from Teal P50)
                        p95Path.reset()
                        for (i in realPoints.indices) {
                            val pt = realPoints[i]
                            val x = xForIndex(i)
                            val yP95 = yPix(pt.p95)
                            if (i == 0) p95Path.moveTo(x, yP95) else p95Path.lineTo(x, yP95)
                        }
                        drawPath(p95Path, cP95, style = strokeThin)

                        // P5 Line (Red)
                        p5Path.reset()
                        for (i in realPoints.indices) {
                            val pt = realPoints[i]
                            val x = xForIndex(i)
                            val yP5 = yPix(pt.p5)
                            if (i == 0) p5Path.moveTo(x, yP5) else p5Path.lineTo(x, yP5)
                        }
                        drawPath(p5Path, cRed, style = strokeThin)

                        // P50 Median Line (Teal Thick)
                        p50Path.reset()
                        for (i in realPoints.indices) {
                            val pt = realPoints[i]
                            val x = xForIndex(i)
                            val yP50 = yPix(pt.p50)
                            if (i == 0) p50Path.moveTo(x, yP50) else p50Path.lineTo(x, yP50)
                        }
                        drawPath(p50Path, cTeal, style = strokeMedian)

                        // FIRE Target Line (Gold Dashed)
                        targetPath.reset()
                        for (i in realPoints.indices) {
                            val pt = realPoints[i]
                            val x = xForIndex(i)
                            val yTarget = yPix(pt.target)
                            if (i == 0) targetPath.moveTo(x, yTarget) else targetPath.lineTo(x, yTarget)
                        }
                        drawPath(
                            targetPath,
                            cGold,
                            style = strokeTarget
                        )
                    } else {
                        val sx = xForIndex(0)
                        val pt = realPoints[0]
                        drawLine(
                            color = cTeal.copy(alpha = 0.25f),
                            start = Offset(sx, yPix(pt.p95)),
                            end = Offset(sx, yPix(pt.p5)),
                            strokeWidth = 8f
                        )
                        drawCircle(color = cP95, radius = 6f, center = Offset(sx, yPix(pt.p95)))
                        drawCircle(color = cRed, radius = 6f, center = Offset(sx, yPix(pt.p5)))
                        drawCircle(color = cGold, radius = 6f, center = Offset(sx, yPix(pt.target)))
                    }

                    // Draw Current Position (Now) Indicator Dot along median trajectory
                    drawNowMarkerOnSeries(
                        items = realPoints,
                        yearOf = { it.year },
                        valueOf = { it.p50 },
                        xForFraction = { frac -> if (realPoints.size > 1) paddingLeft + frac * stepX else xForIndex(0) },
                        yPix = ::yPix,
                        plotLeft = paddingLeft,
                        plotRight = w - paddingRight,
                        plotTop = paddingTop,
                        plotBottom = plotH,
                        accentColor = cTeal,
                        currentYearMonth = resolvedNowYm,
                        resources = nowMarkerResources,
                    )

                    // Highlight selected scrubber point marker
                    safeSelectedIndex?.let { idx ->
                        val sx = xForIndex(idx)
                        val sy = yPix(realPoints[idx].p50)
                        val syTarget = yPix(realPoints[idx].target)
                        drawLine(
                            color = cTeal.copy(alpha = 0.5f),
                            start = Offset(sx, paddingTop),
                            end = Offset(sx, plotH),
                            strokeWidth = 2f,
                            pathEffect = selDashEffect
                        )
                        drawCircle(color = cTeal, radius = 10f, center = Offset(sx, sy))
                        drawCircle(color = cGold, radius = 6f, center = Offset(sx, syTarget))
                    }

                    drawContext.canvas.restore()
                }
            }

            // Modern Interactive Detail Tooltip & Inspection Card
            val activeIdx = safeSelectedIndex
            val activeRealPt = activeIdx?.let { realPoints.getOrNull(it) } ?: realPoints.lastOrNull()
            val activeNomPt = activeIdx?.let { points.getOrNull(it) } ?: points.lastOrNull()

            if (activeRealPt != null && activeNomPt != null) {
                Spacer(modifier = Modifier.height(12.dp))

                val safeRealP50 = activeRealPt.p50.finiteOrZero()
                val safeTarget = activeRealPt.target.finiteOrZero()
                val targetDelta = safeRealP50 - safeTarget
                val isFireAchieved = targetDelta >= 0
                val pctFunded = if (safeTarget > 0.0) ((safeRealP50 / safeTarget) * 100.0).roundToInt() else 100
                val deltaText = if (isFireAchieved) "+${fmtCompact(targetDelta)}" else fmtCompact(targetDelta)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("monte_carlo_tooltip_box")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Year ${activeRealPt.year} - Age ${activeRealPt.age}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp
                                    )
                                )
                                ColorPill(
                                    text = if (isFireAchieved) "FIRE REACHED ($pctFunded%)" else "$pctFunded% OF TARGET",
                                    color = if (isFireAchieved) GoodGreen else BrandGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    horizontalPadding = 6.dp,
                                    verticalPadding = 2.dp,
                                    cornerRadius = 6.dp
                                )
                            }

                            if (safeSelectedIndex != null) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedIndex = null
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear selection",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Metric Pills (2x2 Grid so values never clip on 360dp narrow screens)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartMetricPill(
                                label = "Nominal P50",
                                value = fmtCompact(activeNomPt.p50.finiteOrZero()),
                                color = BrandTeal,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                            ChartMetricPill(
                                label = "Real P50",
                                value = fmtCompact(activeRealPt.p50),
                                color = BrandTeal,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartMetricPill(
                                label = "Target",
                                value = fmtCompact(activeRealPt.target),
                                color = BrandGold,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                            ChartMetricPill(
                                label = "Delta",
                                value = deltaText,
                                color = if (isFireAchieved) GoodGreen else BadRed,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Percentile Range Row: P5 Pessimistic & P95 Optimistic
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartMetricPill(
                                label = "P5 (Pessimistic)",
                                value = fmtCompact(activeRealPt.p5),
                                color = BadRed,
                                modifier = Modifier.weight(1f)
                            )
                            ChartMetricPill(
                                label = "P95 (Optimistic)",
                                value = fmtCompact(activeRealPt.p95),
                                color = cP95,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Real values are adjusted to today's purchasing power using ${com.example.util.Formatters.fmtPct(safeCpiPct, 1)} annual inflation.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward-compatible alias for [MonteCarloFanChart].
 */
@Composable
fun FanChart(
    points: List<MonteCarloPoint>,
    cpiPct: Double = 2.8,
    modifier: Modifier = Modifier
) {
    MonteCarloFanChart(
        points = points,
        cpiPct = cpiPct,
        modifier = modifier
    )
}
