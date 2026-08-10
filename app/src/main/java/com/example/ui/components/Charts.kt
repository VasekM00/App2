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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.HousingYearComparisonPoint
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
                    Text(
                        text = "Net Worth Growth Trajectory",
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
                        modifier = Modifier.size(32.dp).testTag("chart_zoom_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale / 1.25f).coerceAtLeast(1.0f)
                            if (zoomScale == 1.0f) panOffsetX = 0.0f
                        },
                        modifier = Modifier.size(32.dp).testTag("chart_zoom_out")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0.0f
                            selectedPointIndex = null
                        },
                        modifier = Modifier.size(32.dp).testTag("chart_zoom_reset")
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
                Box(modifier = Modifier.size(10.dp).background(BrandTeal, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Portfolio", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(10.dp).background(BrandGold, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "FIRE Target", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            val maxVal = maxOf(
                data.maxOf { it.portfolio },
                data.maxOf { it.target }
            ) * 1.1

            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).hashCode()

            val paddingLeft = 110f // Space for Y-Axis labels
            val paddingBottom = 60f // Space for X-Axis labels

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 24f
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
                                val chartWidth = (size.width - paddingLeft) * zoomScale
                                val relativeX = offset.x - paddingLeft - panOffsetX
                                val stepX = chartWidth / (data.size - 1).toFloat()
                                val clickedIdx = (relativeX / stepX).toInt().coerceIn(0, data.size - 1)
                                selectedPointIndex = clickedIdx
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft
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
                            end = Offset(w, y),
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
                    val stepX = chartWidth / (data.size - 1).toFloat()

                    val xStepCount = 5
                    for (i in 0 until data.size step max(1, data.size / xStepCount)) {
                        val pt = data[i]
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        if (x in paddingLeft..w) {
                            // Tick mark
                            drawLine(
                                color = gridColor,
                                start = Offset(x, plotH),
                                end = Offset(x, plotH + 8f),
                                strokeWidth = 2f
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "${pt.year}",
                                x - 25f,
                                plotH + 36f,
                                textPaint
                            )
                        }
                    }

                    // Clip chart plotting within the axes space
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w, plotH)

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
                        color = BrandGold,
                        style = Stroke(
                            width = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )
                    )

                    // Draw Portfolio path
                    drawPath(
                        path = portfolioPath,
                        color = BrandTeal,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )

                    // Draw FIRE Milestone marker dot
                    if (fireReachedIndex >= 0) {
                        val fx = paddingLeft + panOffsetX + (fireReachedIndex * stepX)
                        val fy = plotH - (plotH * (data[fireReachedIndex].portfolio / maxVal)).toFloat()
                        drawCircle(color = BrandGold, radius = 12f, center = Offset(fx, fy))
                        drawCircle(color = Color.White, radius = 6f, center = Offset(fx, fy))
                    }

                    // Draw Selected Point Highlight Line and Marker
                    selectedPointIndex?.let { idx ->
                        val sx = paddingLeft + panOffsetX + (idx * stepX)
                        val sy = plotH - (plotH * (data[idx].portfolio / maxVal)).toFloat()
                        drawLine(
                            color = BrandTeal.copy(alpha = 0.5f),
                            start = Offset(sx, 0f),
                            end = Offset(sx, plotH),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        drawCircle(color = BrandTeal, radius = 14f, center = Offset(sx, sy))
                        drawCircle(color = Color.White, radius = 7f, center = Offset(sx, sy))
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
                                    color = if (pt.status == "FIRE OK") GoodGreen else BrandGold
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
                                    color = BrandGold
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("monte_carlo_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monte Carlo 1,000-Run Fan Chart",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "P95 Best Case, P50 Median, and P5 Worst Case Confidence Band",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val maxVal = points.maxOf { maxOf(it.p95, it.target) } * 1.1
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).hashCode()

            val paddingLeft = 110f
            val paddingBottom = 60f

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 24f
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
                    val plotW = w - paddingLeft
                    val plotH = h - paddingBottom
                    val stepX = plotW / (points.size - 1).toFloat()

                    // Draw Y-Axis lines and numeric labels
                    val ySteps = 4

                    for (i in 0..ySteps) {
                        val valAtStep = maxVal * i / ySteps
                        val y = plotH - (plotH * i / ySteps)

                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, y),
                            end = Offset(w, y),
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
                        drawContext.canvas.nativeCanvas.drawText(
                            "${pt.year}",
                            x - 25f,
                            plotH + 36f,
                            textPaint
                        )
                    }

                    // Clip plot area
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w, plotH)

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
                        color = BrandTeal.copy(alpha = 0.18f)
                    )

                    // P95 Line (Green)
                    val p95Path = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val y = plotH - (plotH * (pt.p95 / maxVal)).toFloat()
                        if (i == 0) p95Path.moveTo(x, y) else p95Path.lineTo(x, y)
                    }
                    drawPath(p95Path, GoodGreen, style = Stroke(width = 3f))

                    // P5 Line (Red)
                    val p5Path = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val y = plotH - (plotH * (pt.p5 / maxVal)).toFloat()
                        if (i == 0) p5Path.moveTo(x, y) else p5Path.lineTo(x, y)
                    }
                    drawPath(p5Path, BadRed, style = Stroke(width = 3f))

                    // P50 Median Line (Teal Thick)
                    val p50Path = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val y = plotH - (plotH * (pt.p50 / maxVal)).toFloat()
                        if (i == 0) p50Path.moveTo(x, y) else p50Path.lineTo(x, y)
                    }
                    drawPath(p50Path, BrandTeal, style = Stroke(width = 5f, cap = StrokeCap.Round))

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
                        modifier = Modifier.size(32.dp).testTag("cf_zoom_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale / 1.25f).coerceAtLeast(1.0f)
                            if (zoomScale == 1.0f) panOffsetX = 0.0f
                        },
                        modifier = Modifier.size(32.dp).testTag("cf_zoom_out")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0.0f
                            selectedIndex = null
                        },
                        modifier = Modifier.size(32.dp).testTag("cf_zoom_reset")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val maxVal = (data.maxOf { maxOf(it.reinvestAnnual, it.investedAnnual) } * 1.25).coerceAtLeast(100000.0)
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).hashCode()

            val paddingLeft = 110f
            val paddingBottom = 60f

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 24f
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
                                val chartWidth = (size.width - paddingLeft) * zoomScale
                                val relativeX = offset.x - paddingLeft - panOffsetX
                                val stepX = chartWidth / (data.size - 1).toFloat()
                                val clickedIdx = (relativeX / stepX).toInt().coerceIn(0, data.size - 1)
                                selectedIndex = clickedIdx
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft
                    val plotH = h - paddingBottom

                    // Y-Axis
                    val ySteps = 4
                    for (i in 0..ySteps) {
                        val valAtStep = maxVal * i / ySteps
                        val y = plotH - (plotH * i / ySteps)

                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, y),
                            end = Offset(w, y),
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
                    val stepX = chartWidth / (data.size - 1).toFloat()

                    val xStepCount = 5
                    for (i in 0 until data.size step max(1, data.size / xStepCount)) {
                        val pt = data[i]
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        if (x in paddingLeft..w) {
                            drawLine(
                                color = gridColor,
                                start = Offset(x, plotH),
                                end = Offset(x, plotH + 8f),
                                strokeWidth = 2f
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "${pt.year}",
                                x - 25f,
                                plotH + 36f,
                                textPaint
                            )
                        }
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w, plotH)

                    // Invested Annual path (Teal)
                    val addedPath = Path()
                    data.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = plotH - (plotH * (pt.investedAnnual / maxVal)).toFloat()
                        if (i == 0) addedPath.moveTo(x, y) else addedPath.lineTo(x, y)
                    }
                    drawPath(addedPath, BrandTeal, style = Stroke(width = 5f, cap = StrokeCap.Round))

                    // Reinvested Annual path (Gold)
                    val reinvestedPath = Path()
                    data.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = plotH - (plotH * (pt.reinvestAnnual / maxVal)).toFloat()
                        if (i == 0) reinvestedPath.moveTo(x, y) else reinvestedPath.lineTo(x, y)
                    }
                    drawPath(reinvestedPath, BrandGold, style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))

                    // Highlight line
                    selectedIndex?.let { idx ->
                        val sx = paddingLeft + panOffsetX + (idx * stepX)
                        val sy = plotH - (plotH * (data[idx].investedAnnual / maxVal)).toFloat()
                        drawLine(
                            color = BrandTeal.copy(alpha = 0.5f),
                            start = Offset(sx, 0f),
                            end = Offset(sx, plotH),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                        )
                        drawCircle(color = BrandTeal, radius = 12f, center = Offset(sx, sy))
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
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = BrandGold)
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

    val scenarioColors = listOf(
        BrandTeal,                     // Baseline
        GoodGreen,                     // Bull
        BadRed,                        // Stagflation
        BrandGold,                     // Crash
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
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).hashCode()

            val paddingLeft = 110f
            val paddingBottom = 60f

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 24f
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
                    val plotW = w - paddingLeft
                    val plotH = h - paddingBottom

                    // Y-Axis
                    val ySteps = 4
                    for (i in 0..ySteps) {
                        val valAtStep = maxVal * i / ySteps
                        val y = plotH - (plotH * i / ySteps)

                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, y),
                            end = Offset(w, y),
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
                        drawLine(
                            color = gridColor,
                            start = Offset(x, plotH),
                            end = Offset(x, plotH + 8f),
                            strokeWidth = 2f
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "${pt.year}",
                            x - 25f,
                            plotH + 36f,
                            textPaint
                        )
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w, plotH)

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

@Composable
fun HousingCostAndEquityChart(
    points: List<HousingYearComparisonPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("housing_cost_equity_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Housing Cost Crossover & Equity Accrual",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Rent grows with inflation (3-4%/yr) vs. Fixed Mortgage payment + Maintenance",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(BadRed, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Monthly Rent Cost", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(BrandTeal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Monthly Buy Cost", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val maxCost = (points.flatMap { listOf(it.rentMonthly, it.buyMonthly) }.maxOrNull() ?: 50000.0).coerceAtLeast(100.0) * 1.15
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).hashCode()

            val paddingLeft = 110f
            val paddingBottom = 60f

            val textPaint = remember(textColor) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 24f
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
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft
                    val plotH = h - paddingBottom

                    // Y-Axis
                    val ySteps = 4
                    for (i in 0..ySteps) {
                        val valAtStep = maxCost * i / ySteps
                        val y = plotH - (plotH * i / ySteps)

                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, y),
                            end = Offset(w, y),
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
                    val stepX = if (points.size > 1) plotW / (points.size - 1).toFloat() else plotW
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
                        drawContext.canvas.nativeCanvas.drawText(
                            "${pt.year}",
                            x - 25f,
                            plotH + 36f,
                            textPaint
                        )
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, 0f, w, plotH)

                    // Rent Path
                    val rentPath = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val y = plotH - (plotH * (pt.rentMonthly / maxCost)).toFloat()
                        if (i == 0) rentPath.moveTo(x, y) else rentPath.lineTo(x, y)
                    }
                    drawPath(
                        path = rentPath,
                        color = BadRed,
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )

                    // Buy Path
                    val buyPath = Path()
                    points.forEachIndexed { i, pt ->
                        val x = paddingLeft + (i * stepX)
                        val y = plotH - (plotH * (pt.buyMonthly / maxCost)).toFloat()
                        if (i == 0) buyPath.moveTo(x, y) else buyPath.lineTo(x, y)
                    }
                    drawPath(
                        path = buyPath,
                        color = BrandTeal,
                        style = Stroke(width = 4f, cap = StrokeCap.Round)
                    )

                    drawContext.canvas.restore()
                }
            }
        }
    }
}



