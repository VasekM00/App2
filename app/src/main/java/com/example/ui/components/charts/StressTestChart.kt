package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.StressScenarioResult
import com.example.ui.components.ColorPill
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import kotlin.math.max

@Composable
fun StressComparisonChart(
    scenarios: List<StressScenarioResult>,
    modifier: Modifier = Modifier
) {
    if (scenarios.isEmpty()) return

    val cTeal = BrandTeal
    val cGreen = GoodGreen
    val cRed = BadRed
    val cGold = BrandGold
    val cCardSurface = MaterialTheme.colorScheme.surface

    val scenarioColors = listOf(
        cTeal,                             // Baseline
        cGreen,                            // Bull
        cRed,                              // Stagflation
        cGold,                             // Crash
        MaterialTheme.colorScheme.tertiary // Inflation Shock
    )

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
            Text(
                text = "Comparing portfolio growth across economic regimes over 35 years",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                scenarios.chunked(3).forEach { rowScenarios ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowScenarios.forEach { sc ->
                            val colorIdx = scenarios.indexOf(sc) % scenarioColors.size
                            val color = scenarioColors[colorIdx]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = sc.name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val firstTraj = scenarios.firstOrNull()?.trajectory ?: emptyList()
            val maxVal = (scenarios.flatMap { it.trajectory }.maxOfOrNull { it.portfolio } ?: 1000000.0).coerceAtLeast(100.0) * 1.1
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
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
                            10f,
                            y + 8f,
                            textPaint
                        )
                    }

                    // X-Axis
                    val maxTrajSize = scenarios.maxOfOrNull { it.trajectory.size } ?: firstTraj.size
                    val stepX = if (maxTrajSize > 1) plotW / (maxTrajSize - 1).toFloat() else plotW
                    val xStepCount = 5
                    for (i in 0 until firstTraj.size step max(1, firstTraj.size / xStepCount)) {
                        val pt = firstTraj[i]
                        val x = paddingLeft + (i * stepX)
                        if (x in paddingLeft..(w - paddingRight + 10f)) {
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
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, w - paddingRight, plotH)

                    scenarios.forEachIndexed { sIdx, scenario ->
                        val color = scenarioColors[sIdx % scenarioColors.size]
                        val path = Path()
                        scenario.trajectory.forEachIndexed { i, pt ->
                            val x = paddingLeft + (i * stepX)
                            val y = yPix(pt.portfolio)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = if (scenario.id == "baseline") 5f else 3f,
                                cap = StrokeCap.Round,
                                pathEffect = if (scenario.id == "crash") PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                            )
                        )
                    }

                    // Draw Current Position (Now / Start Year) Indicator Dot
                    if (firstTraj.isNotEmpty()) {
                        val curX = paddingLeft + (0 * stepX)
                        val curY = plotH - (plotH * (firstTraj[0].portfolio / maxVal)).toFloat()
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

                    drawContext.canvas.restore()
                }
            }
        }
    }
}
