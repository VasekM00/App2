package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.MonteCarloPoint
import com.example.domain.PortfolioYearPoint
import com.example.domain.StressScenarioResult
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import kotlin.math.max

@Composable
fun NetWorthChart(
    data: List<PortfolioYearPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val cTeal = BrandTeal
    val cGold = BrandGold
    val cCardSurface = MaterialTheme.colorScheme.surface

    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }

    val fireReachedIndex = data.indexOfFirst { it.portfolio >= it.target }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("net_worth_chart_card"),
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
                        text = "GROWTH TRAJECTORY",
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
                        text = "Pinch or use zoom controls to inspect year details",
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

            Spacer(modifier = Modifier.height(8.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).background(cTeal, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Portfolio", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(10.dp).background(cGold, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "FIRE Target", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            val maxVal = (maxOf(
                data.maxOfOrNull { it.portfolio } ?: 0.0,
                data.maxOfOrNull { it.target } ?: 0.0
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
                    val stepX = if (data.size > 1) chartWidth / (data.size - 1).toFloat() else chartWidth

                    val xStepCount = 5
                    for (i in 0 until data.size step max(1, data.size / xStepCount)) {
                        val pt = data[i]
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        if (x in paddingLeft..(w - paddingRight + 10f)) {
                            // Tick mark
                            drawLine(
                                color = gridColor,
                                start = Offset(x, plotH),
                                end = Offset(x, plotH + 8f),
                                strokeWidth = 2f
                            )
                            val labelText = "${pt.year}"
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
                    data.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = plotH - (plotH * (pt.target / maxVal)).toFloat()
                        if (i == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
                    }

                    // Build Portfolio Path (Solid Teal)
                    val portfolioPath = Path()
                    data.forEachIndexed { i, pt ->
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

                    // Draw FIRE Milestone marker dot
                    if (fireReachedIndex >= 0) {
                        val fx = paddingLeft + panOffsetX + (fireReachedIndex * stepX)
                        val fy = plotH - (plotH * (data[fireReachedIndex].portfolio / maxVal)).toFloat()
                        drawCircle(color = cGold, radius = 12f, center = Offset(fx, fy))
                        drawCircle(color = cCardSurface, radius = 6f, center = Offset(fx, fy))
                    }

                    // Draw Selected Point Highlight Line and Marker
                    selectedPointIndex?.let { idx ->
                        val sx = paddingLeft + panOffsetX + (idx * stepX)
                        val sy = plotH - (plotH * (data[idx].portfolio / maxVal)).toFloat()
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
            val activePoint = selectedPointIndex?.let { data.getOrNull(it) } ?: data.lastOrNull()
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monte_carlo_chart_card"),
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
                            color = gridColor,
                            start = Offset(x, plotH),
                            end = Offset(x, plotH + 8f),
                            strokeWidth = 2f
                        )
                        val labelText = "${pt.year}"
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
                        val y = plotH - (plotH * (pt.p95 / maxVal)).toFloat()
                        if (i == 0) p95Path.moveTo(x, y) else p95Path.lineTo(x, y)
                    }
                    drawPath(p95Path, cGreen, style = Stroke(width = 3f))

                    // P5 Line (Red)
                    val p5Path = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val y = plotH - (plotH * (pt.p5 / maxVal)).toFloat()
                        if (i == 0) p5Path.moveTo(x, y) else p5Path.lineTo(x, y)
                    }
                    drawPath(p5Path, cRed, style = Stroke(width = 3f))

                    // P50 Median Line (Teal Thick)
                    val p50Path = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val y = plotH - (plotH * (pt.p50 / maxVal)).toFloat()
                        if (i == 0) p50Path.moveTo(x, y) else p50Path.lineTo(x, y)
                    }
                    drawPath(p50Path, cTeal, style = Stroke(width = 5f, cap = StrokeCap.Round))

                    drawContext.canvas.restore()
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

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cashflow_chart_card"),
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

            val maxVal = (data.maxOf { maxOf(it.reinvestAnnual, it.investedAnnual) } * 1.25).coerceAtLeast(100000.0)
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
                                color = gridColor,
                                start = Offset(x, plotH),
                                end = Offset(x, plotH + 8f),
                                strokeWidth = 2f
                            )
                            val labelText = "${pt.year}"
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

    val scenarioColors = listOf(
        cTeal,                         // Baseline
        cGreen,                        // Bull
        cRed,                          // Stagflation
        cGold,                         // Crash
        Color(0xFF9C27B0)              // Inflation Shock
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stress_comparison_chart_card"),
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
                                Text(text = "${sc.iconEmoji} ${sc.name}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val firstTraj = scenarios.first().trajectory
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
                                color = gridColor,
                                start = Offset(x, plotH),
                                end = Offset(x, plotH + 8f),
                                strokeWidth = 2f
                            )
                            val labelText = "${pt.year}"
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

                    drawContext.canvas.restore()
                }
            }
        }
    }
}



