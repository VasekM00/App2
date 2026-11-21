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
import com.example.domain.StressScenarioResult
import com.example.ui.components.ColorPill
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.LocalIsDarkTheme
import com.example.util.Formatters.fmtCompact
import kotlin.math.max
import kotlin.math.round

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StressComparisonChart(
    scenarios: List<StressScenarioResult>,
    modifier: Modifier = Modifier,
    currentYearMonth: java.time.YearMonth? = null,
) {
    if (scenarios.isEmpty()) return

    val cTeal = BrandTeal
    val cBlue = BrandBlue
    val cGreen = GoodGreen
    val cRed = BadRed
    val cGold = BrandGold
    val cViolet = if (LocalIsDarkTheme.current) androidx.compose.ui.graphics.Color(0xFFA78BFA) else androidx.compose.ui.graphics.Color(0xFF7C3AED)
    val cCardSurface = MaterialTheme.colorScheme.surface
    val haptic = LocalHapticFeedback.current

    val scenarioColors = listOf(
        cBlue,   // Baseline (clearly distinct from GoodGreen Bull!)
        cGreen,  // Bull
        cRed,    // Stagflation
        cGold,   // Crash
        cViolet  // Inflation Shock
    )

    val refTraj = remember(scenarios) {
        scenarios.maxByOrNull { it.trajectory.size }?.trajectory ?: emptyList()
    }
    val maxTrajSize = scenarios.maxOfOrNull { it.trajectory.size } ?: refTraj.size
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val safeSelectedIndex = selectedIndex?.takeIf { maxTrajSize > 0 }?.coerceIn(0, maxTrajSize - 1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stress_comparison_chart_card")
            .semantics {
                contentDescription = "Stress scenario comparison chart showing portfolio trajectories under baseline, bull, stagflation, crash and high inflation regimes."
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ColorPill(
                text = "MACRO STRESS SCENARIOS",
                color = BadRed,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                horizontalPadding = 6.dp,
                verticalPadding = 2.dp,
                cornerRadius = 6.dp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Stress & Scenario Multi-Trajectory Comparison",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            val horizonLabel = if (maxTrajSize > 0) "over $maxTrajSize years" else "across horizon"
            Text(
                text = "Comparing portfolio growth across economic regimes $horizonLabel",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Legend (FlowRow for narrow 360dp screen wrapping)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                scenarios.forEachIndexed { idx, sc ->
                    val color = scenarioColors[idx % scenarioColors.size]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = sc.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val ySteps = 4
            val maxVal = remember(scenarios) {
                val rawMax = scenarios.flatMap { it.trajectory }
                    .maxOfOrNull { it.totalPortfolio.finiteOrZero().coerceAtLeast(0.0) } ?: 0.0
                computeNiceAxisMax((rawMax * 1.1).coerceAtLeast(100000.0), steps = ySteps)
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
            val scenarioPath = remember { Path() }
            val crashDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) }
            val selDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }
            val baselineStroke = remember { Stroke(width = 5f, cap = StrokeCap.Round) }
            val regularStroke = remember { Stroke(width = 3f, cap = StrokeCap.Round) }
            val crashStroke = remember(crashDashEffect) {
                Stroke(width = 3f, cap = StrokeCap.Round, pathEffect = crashDashEffect)
            }
            val nowMarkerResources = rememberNowMarkerResources(cTeal)
            val resolvedNowYm = currentYearMonth ?: remember {
                java.time.YearMonth.now(java.time.ZoneId.of("Europe/Prague"))
            }
            val yTickLabels = remember(maxVal) {
                (0..ySteps).map { i -> fmtCompact(maxVal * i / ySteps) }
            }
            val xYearLabels = remember(refTraj) {
                refTraj.map { "${it.year}" }
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
                if (plotW > 0f && maxTrajSize > 0) {
                    val clickedIdx = if (maxTrajSize > 1) {
                        val stepX = plotW / (maxTrajSize - 1).toFloat()
                        val relativeX = touchX - paddingLeft
                        if (stepX > 0f) round(relativeX / stepX).toInt().coerceIn(0, maxTrajSize - 1) else 0
                    } else 0
                    if (clickedIdx != selectedIndex) {
                        selectedIndex = clickedIdx
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }

            val chartHeight = (240f * density.fontScale.coerceIn(1f, 1.35f)).dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .pointerInput(scenarios, paddingLeft, paddingRight) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> updateScrubber(offset.x, size.width.toFloat()) },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    updateScrubber(change.position.x, size.width.toFloat())
                                }
                            )
                        }
                        .pointerInput(scenarios, paddingLeft, paddingRight) {
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

                    fun yPix(v: Double): Float {
                        val frac = safeFraction(v.finiteOrZero().coerceAtLeast(0.0), maxVal)
                        return paddingTop + (usableH - usableH * frac)
                    }

                    // Y-Axis
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

                    // X-Axis
                    val stepX = if (maxTrajSize > 1) plotW / (maxTrajSize - 1).toFloat() else 0f
                    fun xForIndex(index: Int): Float = if (maxTrajSize > 1) {
                        paddingLeft + (index * stepX)
                    } else {
                        paddingLeft + plotW / 2f
                    }
                    val xStepCount = 5
                    var lastDrawnRight = -Float.MAX_VALUE
                    for (i in 0 until refTraj.size step max(1, refTraj.size / xStepCount)) {
                        val x = xForIndex(i)
                        if (x in paddingLeft..(w - paddingRight + 4f)) {
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
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, w - paddingRight, plotH)

                    for (sIdx in scenarios.indices) {
                        val scenario = scenarios[sIdx]
                        val color = scenarioColors[sIdx % scenarioColors.size]
                        if (scenario.trajectory.size > 1) {
                            scenarioPath.reset()
                            for (i in scenario.trajectory.indices) {
                                val pt = scenario.trajectory[i]
                                val x = xForIndex(i)
                                val y = yPix(pt.totalPortfolio)
                                if (i == 0) scenarioPath.moveTo(x, y) else scenarioPath.lineTo(x, y)
                            }
                            val lineStroke = when (scenario.id) {
                                "baseline" -> baselineStroke
                                "crash" -> crashStroke
                                else -> regularStroke
                            }
                            drawPath(
                                path = scenarioPath,
                                color = color,
                                style = lineStroke
                            )
                        } else if (scenario.trajectory.size == 1) {
                            drawCircle(
                                color = color,
                                radius = if (scenario.id == "baseline") 7f else 5f,
                                center = Offset(xForIndex(0), yPix(scenario.trajectory[0].totalPortfolio))
                            )
                        }
                    }

                    // Draw Current Position (Now) Indicator Dot along baseline trajectory
                    drawNowMarkerOnSeries(
                        items = refTraj,
                        yearOf = { it.year },
                        valueOf = { it.portfolio },
                        xForFraction = { frac -> if (refTraj.size > 1) paddingLeft + frac * stepX else xForIndex(0) },
                        yPix = ::yPix,
                        plotLeft = paddingLeft,
                        plotRight = w - paddingRight,
                        plotTop = paddingTop,
                        plotBottom = plotH,
                        accentColor = cTeal,
                        currentYearMonth = resolvedNowYm,
                        resources = nowMarkerResources,
                    )

                    // Selected scrubber highlight
                    safeSelectedIndex?.let { idx ->
                        val sx = xForIndex(idx)
                        drawLine(
                            color = cTeal.copy(alpha = 0.5f),
                            start = Offset(sx, paddingTop),
                            end = Offset(sx, plotH),
                            strokeWidth = 2f,
                            pathEffect = selDashEffect
                        )
                        scenarios.forEachIndexed { sIdx, sc ->
                            sc.trajectory.getOrNull(idx)?.let { pt ->
                                val sy = yPix(pt.portfolio)
                                val color = scenarioColors[sIdx % scenarioColors.size]
                                drawCircle(color = color, radius = 6f, center = Offset(sx, sy))
                                drawCircle(color = cCardSurface, radius = 2.5f, center = Offset(sx, sy))
                            }
                        }
                    }

                    drawContext.canvas.restore()
                }
            }

            // Nested Inspection Panel for Selected or Terminal Year
            if (refTraj.isNotEmpty()) {
                val inspectIdx = safeSelectedIndex ?: (refTraj.size - 1)
                val inspectYearPt = refTraj.getOrNull(inspectIdx)
                if (inspectYearPt != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Year ${inspectYearPt.year} · Age ${inspectYearPt.age} Regime Comparison",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            scenarios.forEachIndexed { sIdx, sc ->
                                val color = scenarioColors[sIdx % scenarioColors.size]
                                val ptVal = (sc.trajectory.getOrNull(inspectIdx)?.totalPortfolio ?: 0.0).finiteOrZero()
                                ChartMetricPill(
                                    label = sc.name,
                                    value = fmtCompact(ptVal),
                                    color = color,
                                    isBold = sc.id == "baseline",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
