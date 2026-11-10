package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PortfolioYearPoint
import com.example.ui.components.ColorPill
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import kotlin.math.max
import kotlin.math.round

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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                                (x - textWidth / 2f).coerceIn(paddingLeft, w - paddingRight - textWidth),
                                plotH + 34f,
                                textPaint
                            )
                        }
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, w - paddingRight, plotH)

                    // Invested Annual path (Teal)
                    val addedPath = Path()
                    data.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = yPix(pt.investedAnnual)
                        if (i == 0) addedPath.moveTo(x, y) else addedPath.lineTo(x, y)
                    }
                    drawPath(addedPath, cTeal, style = Stroke(width = 5f, cap = StrokeCap.Round))

                    // Reinvested Annual path (Gold)
                    val reinvestedPath = Path()
                    data.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = yPix(pt.reinvestAnnual)
                        if (i == 0) reinvestedPath.moveTo(x, y) else reinvestedPath.lineTo(x, y)
                    }
                    drawPath(reinvestedPath, cGold, style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))

                    // Draw Current Position (Now / Start Year) Indicator Dot
                    if (data.isNotEmpty()) {
                        val curX = paddingLeft + panOffsetX + (0 * stepX)
                        val curY = yPix(data[0].investedAnnual)
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
