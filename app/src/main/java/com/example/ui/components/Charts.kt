package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.MonteCarloPoint
import com.example.domain.PortfolioYearPoint
import com.example.domain.RegulatoryConstants
import com.example.domain.StressScenarioResult
import com.example.data.SettingsEntity
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.WarnAmber
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtCZK
import com.example.util.Formatters.fmtPct
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Composable
fun NetWorthChart(
    data: List<PortfolioYearPoint>,
    cpiInflationPct: Double = 2.8,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val cTeal = BrandTeal
    val cGold = BrandGold
    val cCardSurface = MaterialTheme.colorScheme.surface

    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }
    var isRealPurchasingPower by remember { mutableStateOf(false) }

    val displayData = remember(data, isRealPurchasingPower, cpiInflationPct) {
        if (!isRealPurchasingPower) {
            data
        } else {
            data.mapIndexed { idx, pt ->
                val discount = (1.0 + (cpiInflationPct / 100.0)).pow(idx.toDouble())
                pt.copy(
                    portfolio = pt.portfolio / discount,
                    target = pt.target / discount
                )
            }
        }
    }

    val fireReachedIndex = displayData.indexOfFirst { it.portfolio >= it.target }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("net_worth_chart_card")
            .semantics {
                contentDescription =
                    "Net worth trajectory chart showing projected portfolio growth and FIRE target over ${data.size} years."
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ColorPill(
                        text = if (isRealPurchasingPower) "TODAY'S PURCHASING POWER" else "GROWTH TRAJECTORY",
                        color = cTeal,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp,
                        cornerRadius = 6.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Net Worth Trajectory",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (isRealPurchasingPower) "Discounted at ${String.format("%.1f", cpiInflationPct)}% inflation (Today's CZK)" else "Nominal growth over 35-year investment horizon",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                // Interactive Zoom Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(4.0f) },
                        modifier = Modifier.size(48.dp).testTag("chart_zoom_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale / 1.25f).coerceAtLeast(1.0f)
                            if (zoomScale == 1.0f) panOffsetX = 0.0f
                        },
                        modifier = Modifier.size(48.dp).testTag("chart_zoom_out")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0.0f
                            selectedPointIndex = null
                        },
                        modifier = Modifier.size(48.dp).testTag("chart_zoom_reset")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Purchasing Power Toggle & Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nominal vs Real Toggle Pills
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        val nominalBg = if (!isRealPurchasingPower) BrandTeal else Color.Transparent
                        val nominalFg = if (!isRealPurchasingPower) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        val realBg = if (isRealPurchasingPower) BrandTeal else Color.Transparent
                        val realFg = if (isRealPurchasingPower) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(nominalBg)
                                .clickable { isRealPurchasingPower = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Nominal", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = nominalFg)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(realBg)
                                .clickable { isRealPurchasingPower = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Real (Today's CZK)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = realFg)
                        }
                    }
                }

                // Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cTeal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Portfolio", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(8.dp).background(cGold, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "FIRE Target", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val maxVal = (maxOf(
                displayData.maxOfOrNull { it.portfolio } ?: 0.0,
                displayData.maxOfOrNull { it.target } ?: 0.0
            ) * 1.1).coerceAtLeast(100.0)

            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).toArgb()

            val paddingLeft = 85f // Space for Y-Axis labels
            val paddingRight = 32f // Space on right to prevent clipping of curve and labels
            val paddingBottom = 50f // Space for X-Axis labels

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 22f
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
                        .pointerInput(displayData, zoomScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1.0f, 4.0f)
                                val maxPan = (size.width * (zoomScale - 1f))
                                panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPan, 0f)
                            }
                        }
                        .pointerInput(displayData, zoomScale, panOffsetX) {
                            detectTapGestures { offset ->
                                val chartWidth = (size.width - paddingLeft - paddingRight) * zoomScale
                                val relativeX = offset.x - paddingLeft - panOffsetX
                                val stepX = if (displayData.size > 1) chartWidth / (displayData.size - 1).toFloat() else chartWidth
                                val clickedIdx = if (displayData.size > 1 && stepX > 0f) (relativeX / stepX).toInt().coerceIn(0, displayData.size - 1) else 0
                                selectedPointIndex = clickedIdx
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft - paddingRight
                    val plotH = h - paddingBottom

                    // Draw Y-Axis lines and numeric labels
                    val ySteps = 4

                    for (i in 0..ySteps) {
                        val valAtStep = maxVal * i / ySteps
                        val y = plotH - (plotH * i / ySteps)

                        // Horizontal Gridline
                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, y),
                            end = Offset(w - paddingRight, y),
                            strokeWidth = 1.5f
                        )

                        // Y-Axis Label
                        drawContext.canvas.nativeCanvas.drawText(
                            fmtCompact(valAtStep),
                            10f,
                            y + 8f,
                            textPaint
                        )
                    }

                    // Vertical Y-Axis border line
                    drawLine(
                        color = gridColor,
                        start = Offset(paddingLeft, 0f),
                        end = Offset(paddingLeft, plotH),
                        strokeWidth = 2f
                    )

                    // Draw X-Axis Year Labels
                    val chartWidth = plotW * zoomScale
                    val stepX = if (displayData.size > 1) chartWidth / (displayData.size - 1).toFloat() else chartWidth

                    val xStepCount = 5
                    for (i in 0 until displayData.size step max(1, displayData.size / xStepCount)) {
                        val pt = displayData[i]
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        if (x in paddingLeft..(w - paddingRight + 10f)) {
                            // Tick mark
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
                                (x - textWidth / 2f).coerceIn(0f, w - textWidth),
                                plotH + 34f,
                                textPaint
                            )
                        }
                    }

                    // Clip chart plotting within the axes space
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w - paddingRight, plotH)

                    // Build Target Path (Dashed Gold)
                    val targetPath = Path()
                    displayData.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = plotH - (plotH * (pt.target / maxVal)).toFloat()
                        if (i == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
                    }

                    // Build Portfolio Path (Solid Teal)
                    val portfolioPath = Path()
                    displayData.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = plotH - (plotH * (pt.portfolio / maxVal)).toFloat()
                        if (i == 0) portfolioPath.moveTo(x, y) else portfolioPath.lineTo(x, y)
                    }

                    // Draw Target path
                    drawPath(
                        path = targetPath,
                        color = cGold,
                        style = Stroke(
                            width = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )
                    )

                    // Draw Portfolio path
                    drawPath(
                        path = portfolioPath,
                        color = cTeal,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )

                    // Draw Current Position (Now / Start Year) Indicator Dot
                    if (displayData.isNotEmpty()) {
                        val curX = paddingLeft + panOffsetX + (0 * stepX)
                        val curY = plotH - (plotH * (displayData[0].portfolio / maxVal)).toFloat()
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

                    // Draw FIRE Milestone marker dot
                    if (fireReachedIndex >= 0) {
                        val fx = paddingLeft + panOffsetX + (fireReachedIndex * stepX)
                        val fy = plotH - (plotH * (displayData[fireReachedIndex].portfolio / maxVal)).toFloat()
                        drawCircle(color = cGold, radius = 12f, center = Offset(fx, fy))
                        drawCircle(color = cCardSurface, radius = 6f, center = Offset(fx, fy))
                    }

                    // Draw Selected Point Highlight Line and Marker
                    selectedPointIndex?.let { idx ->
                        val sx = paddingLeft + panOffsetX + (idx * stepX)
                        val sy = plotH - (plotH * (displayData[idx].portfolio / maxVal)).toFloat()
                        drawLine(
                            color = cTeal.copy(alpha = 0.5f),
                            start = Offset(sx, 0f),
                            end = Offset(sx, plotH),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        drawCircle(color = cTeal, radius = 14f, center = Offset(sx, sy))
                        drawCircle(color = cCardSurface, radius = 7f, center = Offset(sx, sy))
                    }

                    drawContext.canvas.restore()
                }
            }

            // Interactive Detail Tooltip Box
            val activePoint = selectedPointIndex?.let { displayData.getOrNull(it) } ?: displayData.lastOrNull()
            activePoint?.let { pt ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth().testTag("chart_tooltip_box")
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
                            Text(
                                text = "Year ${pt.year} (Age ${pt.age}):",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = pt.status,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (pt.status == "FIRE OK") GoodGreen else cGold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Portfolio: ${fmtCompact(pt.portfolio)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = BrandTeal,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Target: ${fmtCompact(pt.target)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = cGold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonteCarloFanChart(
    points: List<MonteCarloPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val cTeal = BrandTeal
    val cGreen = GoodGreen
    val cRed = BadRed
    val cCardSurface = MaterialTheme.colorScheme.surface

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monte_carlo_chart_card")
            .semantics {
                contentDescription =
                    "Monte Carlo simulation fan chart showing the 5th, 50th and 95th percentile portfolio outcomes and the FIRE target over ${points.size} years."
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                text = "P95 Upper Bound, P50 Median, and P5 Lower Bound Confidence Band",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val maxVal = ((points.maxOfOrNull { maxOf(it.p95, it.target) } ?: 0.0) * 1.1).coerceAtLeast(100.0)
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).toArgb()

            val paddingLeft = 85f
            val paddingRight = 32f
            val paddingBottom = 50f

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 22f
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
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val plotW = size.width - paddingLeft - paddingRight
                                val stepX = if (points.size > 1) plotW / (points.size - 1).toFloat() else plotW
                                val relativeX = offset.x - paddingLeft
                                val clickedIdx = if (points.size > 1 && stepX > 0f) (relativeX / stepX).toInt().coerceIn(0, points.size - 1) else 0
                                selectedIndex = clickedIdx
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft - paddingRight
                    val plotH = h - paddingBottom
                    val stepX = if (points.size > 1) plotW / (points.size - 1).toFloat() else plotW

                    // Draw Y-Axis lines and numeric labels
                    val ySteps = 4

                    for (i in 0..ySteps) {
                        val valAtStep = maxVal * i / ySteps
                        val y = plotH - (plotH * i / ySteps)

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
                    for (i in 0 until points.size step max(1, points.size / xStepCount)) {
                        val pt = points[i]
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
                            (x - textWidth / 2f).coerceIn(0f, w - textWidth),
                            plotH + 34f,
                            textPaint
                        )
                    }

                    // Clip plot area
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w - paddingRight, plotH)

                    // P95 -> P5 Area Shade
                    val fillPath = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val yP95 = plotH - (plotH * (pt.p95 / maxVal)).toFloat()
                        if (i == 0) fillPath.moveTo(x, yP95) else fillPath.lineTo(x, yP95)
                    }
                    points.reversed().forEachIndexed { i, pt ->
                        val origIdx = points.size - 1 - i
                        val x = paddingLeft + (origIdx * stepX)
                        val yP5 = plotH - (plotH * (pt.p5 / maxVal)).toFloat()
                        fillPath.lineTo(x, yP5)
                    }
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        color = cTeal.copy(alpha = 0.18f)
                    )

                    // P95 Line (Green)
                    val p95Path = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val yP95 = plotH - (plotH * (pt.p95 / maxVal)).toFloat()
                        if (i == 0) p95Path.moveTo(x, yP95) else p95Path.lineTo(x, yP95)
                    }
                    drawPath(p95Path, cGreen, style = Stroke(width = 3f))

                    // P5 Line (Red)
                    val p5Path = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val yP5 = plotH - (plotH * (pt.p5 / maxVal)).toFloat()
                        if (i == 0) p5Path.moveTo(x, yP5) else p5Path.lineTo(x, yP5)
                    }
                    drawPath(p5Path, cRed, style = Stroke(width = 3f))

                    // P50 Median Line (Teal Thick)
                    val p50Path = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val yP50 = plotH - (plotH * (pt.p50 / maxVal)).toFloat()
                        if (i == 0) p50Path.moveTo(x, yP50) else p50Path.lineTo(x, yP50)
                    }
                    drawPath(p50Path, cTeal, style = Stroke(width = 5f, cap = StrokeCap.Round))

                    // Draw Current Position (Now / Start Year) Indicator Dot
                    if (points.isNotEmpty()) {
                        val curX = paddingLeft + (0 * stepX)
                        val curY = plotH - (plotH * (points[0].p50 / maxVal)).toFloat()
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
                        val sy = plotH - (plotH * (points[idx].p50 / maxVal)).toFloat()
                        drawLine(
                            color = cTeal.copy(alpha = 0.5f),
                            start = Offset(sx, 0f),
                            end = Offset(sx, plotH),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        drawCircle(color = cTeal, radius = 12f, center = Offset(sx, sy))
                    }

                    drawContext.canvas.restore()
                }
            }

            // Interactive Detail Tooltip Box
            val activePoint = selectedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Year ${pt.year} (Age ${pt.age}) Monte Carlo Range:",
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
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
                    }
                }
            }
        }
    }
}

@Composable
fun CashFlowProjectionChart(
    data: List<PortfolioYearPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val cTeal = BrandTeal
    val cGold = BrandGold
    val cCardSurface = MaterialTheme.colorScheme.surface

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cashflow_chart_card")
            .semantics {
                contentDescription = "Cash flow projection chart comparing monthly income and expenses over the planning horizon."
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    ColorPill(
                        text = "DCA & ACCUMULATION",
                        color = GoodGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp,
                        cornerRadius = 6.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cash Flow & DCA Trajectory",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Annual portfolio contributions & income growth over time",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(4.0f) },
                        modifier = Modifier.size(48.dp).testTag("cf_zoom_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale / 1.25f).coerceAtLeast(1.0f)
                            if (zoomScale == 1.0f) panOffsetX = 0.0f
                        },
                        modifier = Modifier.size(48.dp).testTag("cf_zoom_out")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0.0f
                            selectedIndex = null
                        },
                        modifier = Modifier.size(48.dp).testTag("cf_zoom_reset")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val maxVal = ((data.maxOfOrNull { maxOf(it.reinvestAnnual, it.investedAnnual) } ?: 100000.0) * 1.25).coerceAtLeast(100000.0)
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).toArgb()

            val paddingLeft = 85f
            val paddingRight = 32f
            val paddingBottom = 50f

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 22f
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
                        .pointerInput(data, zoomScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1.0f, 4.0f)
                                val maxPan = (size.width * (zoomScale - 1f))
                                panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPan, 0f)
                            }
                        }
                        .pointerInput(data, zoomScale, panOffsetX) {
                            detectTapGestures { offset ->
                                val chartWidth = (size.width - paddingLeft - paddingRight) * zoomScale
                                val relativeX = offset.x - paddingLeft - panOffsetX
                                val stepX = if (data.size > 1) chartWidth / (data.size - 1).toFloat() else chartWidth
                                val clickedIdx = if (data.size > 1 && stepX > 0f) (relativeX / stepX).toInt().coerceIn(0, data.size - 1) else 0
                                selectedIndex = clickedIdx
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft - paddingRight
                    val plotH = h - paddingBottom

                    // Y-Axis
                    val ySteps = 4
                    for (i in 0..ySteps) {
                        val valAtStep = maxVal * i / ySteps
                        val y = plotH - (plotH * i / ySteps)

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
                    val chartWidth = plotW * zoomScale
                    val stepX = if (data.size > 1) chartWidth / (data.size - 1).toFloat() else chartWidth

                    val xStepCount = 5
                    for (i in 0 until data.size step max(1, data.size / xStepCount)) {
                        val pt = data[i]
                        val x = paddingLeft + panOffsetX + (i * stepX)
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
                                (x - textWidth / 2f).coerceIn(0f, w - textWidth),
                                plotH + 34f,
                                textPaint
                            )
                        }
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w - paddingRight, plotH)

                    // Invested Annual path (Teal)
                    val addedPath = Path()
                    data.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = plotH - (plotH * (pt.investedAnnual / maxVal)).toFloat()
                        if (i == 0) addedPath.moveTo(x, y) else addedPath.lineTo(x, y)
                    }
                    drawPath(addedPath, cTeal, style = Stroke(width = 5f, cap = StrokeCap.Round))

                    // Reinvested Annual path (Gold)
                    val reinvestedPath = Path()
                    data.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = plotH - (plotH * (pt.reinvestAnnual / maxVal)).toFloat()
                        if (i == 0) reinvestedPath.moveTo(x, y) else reinvestedPath.lineTo(x, y)
                    }
                    drawPath(reinvestedPath, cGold, style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))

                    // Draw Current Position (Now / Start Year) Indicator Dot
                    if (data.isNotEmpty()) {
                        val curX = paddingLeft + panOffsetX + (0 * stepX)
                        val curY = plotH - (plotH * (data[0].investedAnnual / maxVal)).toFloat()
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

                    // Highlight line
                    selectedIndex?.let { idx ->
                        val sx = paddingLeft + panOffsetX + (idx * stepX)
                        val sy = plotH - (plotH * (data[idx].investedAnnual / maxVal)).toFloat()
                        drawLine(
                            color = cTeal.copy(alpha = 0.5f),
                            start = Offset(sx, 0f),
                            end = Offset(sx, plotH),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        drawCircle(color = cTeal, radius = 12f, center = Offset(sx, sy))
                    }

                    drawContext.canvas.restore()
                }
            }

            // Tooltip
            val activePoint = selectedIndex?.let { data.getOrNull(it) } ?: data.lastOrNull()
            activePoint?.let { pt ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Year ${pt.year} Cash Flow Details:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Annual DCA Base: ${fmtCompact(pt.investedAnnual)} (${fmtCompact(pt.investedAnnual / 12.0)} / mo)",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = BrandTeal, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Eleonora Reinvested: ${fmtCompact(pt.reinvestAnnual)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = cGold)
                        )
                    }
                }
            }
        }
    }
}

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
        cTeal,                         // Baseline
        cGreen,                        // Bull
        cRed,                          // Stagflation
        cGold,                         // Crash
        MaterialTheme.colorScheme.tertiary // Inflation Shock
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stress_comparison_chart_card")
            .semantics {
                contentDescription = "Stress scenario comparison chart showing portfolio trajectories under baseline, bull, stagflation, crash and high inflation regimes."
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        rowScenarios.forEachIndexed { idx, sc ->
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

            val paddingLeft = 85f
            val paddingRight = 32f
            val paddingBottom = 50f

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 22f
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

                    // Y-Axis
                    val ySteps = 4
                    for (i in 0..ySteps) {
                        val valAtStep = maxVal * i / ySteps
                        val y = plotH - (plotH * i / ySteps)

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
                    val stepX = if (firstTraj.size > 1) plotW / (firstTraj.size - 1).toFloat() else plotW
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
                                (x - textWidth / 2f).coerceIn(0f, w - textWidth),
                                plotH + 34f,
                                textPaint
                            )
                        }
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w - paddingRight, plotH)

                    scenarios.forEachIndexed { sIdx, scenario ->
                        val color = scenarioColors[sIdx % scenarioColors.size]
                        val path = Path()
                        scenario.trajectory.forEachIndexed { i, pt ->
                            val x = paddingLeft + (i * stepX)
                            val y = plotH - (plotH * (pt.portfolio / maxVal)).toFloat()
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

private data class DcaStreamItem(
    val label: String,
    val amount: Double,
    val color: Color
)

@Composable
fun DcaAllocationBreakdownBar(
    settings: SettingsEntity,
    modifier: Modifier = Modifier
) {
    val isSingle = settings.isSingleHousehold
    val portuV = settings.portuDcaMonthly
    val portuE = if (!isSingle) settings.ePortuDcaMonthly else 0.0
    val dipV = settings.dipContributionMonthly
    val dipE = if (!isSingle) settings.eDipContributionMonthly else 0.0
    val dpsV = settings.dpsOwnContributionMonthly
    val dpsE = if (!isSingle) settings.eDpsOwnContributionMonthly else 0.0
    val empV = min(settings.employerRetirementMonthly, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0)
    val empE = if (!isSingle) min(settings.eEmployerRetirementMonthly, RegulatoryConstants.STATUTORY_EMPLOYER_RETIREMENT_EXEMPTION_ANNUAL / 12.0) else 0.0

    val cPortuV = BrandTeal
    val cPortuE = BrandGold
    val cDipV = GoodGreen
    val cDipE = Color(0xFF059669)
    val cDpsV = BrandBlue
    val cDpsE = Color(0xFF38BDF8)
    val cEmpV = Color(0xFF8B5CF6)
    val cEmpE = Color(0xFFA78BFA)

    val streams = remember(settings, isSingle, portuV, portuE, dipV, dipE, dpsV, dpsE, empV, empE, cPortuV, cPortuE, cDipV, cDipE, cDpsV, cDpsE, cEmpV, cEmpE) {
        buildList {
            if (portuV > 0.0) {
                add(DcaStreamItem(label = "Brokerage · ${settings.primaryName}", amount = portuV, color = cPortuV))
            }
            if (portuE > 0.0) {
                add(DcaStreamItem(label = "Brokerage · ${settings.spouseName}", amount = portuE, color = cPortuE))
            }
            if (dipV > 0.0) {
                add(DcaStreamItem(label = "DIP · ${settings.primaryName}", amount = dipV, color = cDipV))
            }
            if (dipE > 0.0) {
                add(DcaStreamItem(label = "DIP · ${settings.spouseName}", amount = dipE, color = cDipE))
            }
            if (dpsV > 0.0) {
                add(DcaStreamItem(label = "DPS · ${settings.primaryName}", amount = dpsV, color = cDpsV))
            }
            if (dpsE > 0.0) {
                add(DcaStreamItem(label = "DPS · ${settings.spouseName}", amount = dpsE, color = cDpsE))
            }
            if (empV > 0.0) {
                add(DcaStreamItem(label = "Employer · ${settings.primaryName}", amount = empV, color = cEmpV))
            }
            if (empE > 0.0) {
                add(DcaStreamItem(label = "Employer · ${settings.spouseName}", amount = empE, color = cEmpE))
            }
        }
    }

    val totalDca = streams.sumOf { it.amount }
    if (totalDca <= 0.0 || streams.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorPill(
                    text = "DCA ALLOCATIONS",
                    color = BrandTeal,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    horizontalPadding = 6.dp,
                    verticalPadding = 2.dp,
                    cornerRadius = 6.dp
                )
            }
            ColorPill(
                text = "${fmtCompact(totalDca)} / mo",
                color = BrandTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                horizontalPadding = 8.dp,
                verticalPadding = 2.5.dp,
                cornerRadius = 8.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Segmented Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(7.dp)
                )
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            streams.forEachIndexed { index, stream ->
                val ratio = (stream.amount / totalDca).toFloat()
                if (ratio > 0f) {
                    val startRadius = if (index == 0) 6.dp else 0.dp
                    val endRadius = if (index == streams.size - 1) 6.dp else 0.dp
                    Box(
                        modifier = Modifier
                            .weight(ratio)
                            .fillMaxHeight()
                            .background(
                                stream.color,
                                RoundedCornerShape(
                                    topStart = startRadius,
                                    bottomStart = startRadius,
                                    topEnd = endRadius,
                                    bottomEnd = endRadius
                                )
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Single-line Pillbox Stream Rows
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            streams.forEach { stream ->
                val pct = ((stream.amount / totalDca) * 100).toInt()
                DcaPillBox(
                    label = stream.label,
                    amount = stream.amount,
                    pct = pct,
                    color = stream.color
                )
            }
        }
    }
}

@Composable
private fun DcaPillBox(
    label: String,
    amount: Double,
    pct: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = "${fmtCZK(amount)} · $pct%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = color
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun ChartMetricPill(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 4.5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = color
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

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

    val initialBal = remember(data) { data.firstOrNull()?.portfolio ?: 0.0 }

    // Precalculate cumulative invested principal and cumulative compound gains
    val cumulativeData = remember(data, initialBal) {
        var runningPrincipal = initialBal
        data.mapIndexed { idx, pt ->
            if (idx > 0) {
                runningPrincipal += pt.investedAnnual + pt.reinvestAnnual + pt.lumpSum
            }
            val gains = (pt.portfolio - runningPrincipal).coerceAtLeast(0.0)
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    ((data.maxOfOrNull { it.investedAnnual + it.reinvestAnnual + it.lumpSum } ?: 100000.0) * 1.25).coerceAtLeast(100000.0)
                } else {
                    ((data.maxOfOrNull { it.portfolio } ?: 1000000.0) * 1.15).coerceAtLeast(100000.0)
                }
            }

            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).toArgb()

            val paddingLeft = 75f
            val paddingRight = 24f
            val paddingBottom = 45f

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 20f
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
                        .pointerInput(data, zoomScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1.0f, 4.0f)
                                val maxPan = (size.width * (zoomScale - 1f))
                                panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPan, 0f)
                            }
                        }
                        .pointerInput(data, zoomScale, panOffsetX) {
                            detectTapGestures { offset ->
                                val chartWidth = (size.width - paddingLeft - paddingRight) * zoomScale
                                val relativeX = offset.x - paddingLeft - panOffsetX
                                val stepX = if (data.size > 1) chartWidth / (data.size - 1).toFloat() else chartWidth
                                val clickedIdx = if (data.size > 1 && stepX > 0f) (relativeX / stepX).toInt().coerceIn(0, data.size - 1) else 0
                                selectedIndex = clickedIdx
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft - paddingRight
                    val plotH = h - paddingBottom

                    // Y-Axis
                    val ySteps = 4
                    for (i in 0..ySteps) {
                        val valAtStep = maxVal * i / ySteps
                        val y = plotH - (plotH * i / ySteps)

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
                                (x - textWidth / 2f).coerceIn(0f, w - textWidth),
                                plotH + 30f,
                                textPaint
                            )
                        }
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w - paddingRight, plotH)

                    // Draw Bars
                    data.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val left = x - barWidth / 2f
                        val right = x + barWidth / 2f

                        if (right >= paddingLeft && left <= (w - paddingRight)) {
                            if (viewMode == 0) {
                                // Annual Streams
                                val hBase = (pt.investedAnnual / maxVal).toFloat() * plotH
                                val hReinvest = (pt.reinvestAnnual / maxVal).toFloat() * plotH
                                val hLump = (pt.lumpSum / maxVal).toFloat() * plotH

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
                                val hPrincipal = ((principal / maxVal).toFloat() * plotH).coerceAtLeast(0f)
                                val hGains = ((gains / maxVal).toFloat() * plotH).coerceAtLeast(0f)

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
                                    start = Offset(x, 0f),
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
                    val totalInflow = pt.investedAnnual + pt.reinvestAnnual + pt.lumpSum
                    val gainsPct = if (pt.portfolio > 0) (gains / pt.portfolio * 100.0) else 0.0

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
                                    color = if (pt.status == "FIRE OK") GoodGreen else BrandTeal,
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
                                    value = "${fmtCompact(pt.investedAnnual / 12.0)}/mo",
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




