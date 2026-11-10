package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.domain.PortfolioYearPoint
import com.example.ui.components.ColorPill
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import kotlin.math.max
import kotlin.math.pow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetWorthChart(
    data: List<PortfolioYearPoint>,
    cpiInflationPct: Double = 2.8,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    isRealPurchasingPower: Boolean = false,
    onRealPurchasingPowerChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val cTeal = BrandTeal
    val cGold = BrandGold
    val cGreen = GoodGreen
    val cPurple = Color(0xFF6366F1)
    val cCardSurface = MaterialTheme.colorScheme.surface
    val haptic = LocalHapticFeedback.current

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }
    var internalIsReal by remember(isRealPurchasingPower) { mutableStateOf(isRealPurchasingPower) }
    val effectiveIsReal = if (onRealPurchasingPowerChange != null) isRealPurchasingPower else internalIsReal
    val updateReal: (Boolean) -> Unit = { newVal ->
        internalIsReal = newVal
        onRealPurchasingPowerChange?.invoke(newVal)
    }

    val actualPoints = remember(ledgerEntries) {
        ledgerEntries
            .filter { (it.portfolioBalanceAtMonthEnd + it.pensionBalanceAtMonthEnd) > 0 }
            .sortedBy { it.yearMonth }
    }

    val displayData = remember(data, effectiveIsReal, cpiInflationPct) {
        if (!effectiveIsReal) {
            data
        } else {
            data.mapIndexed { idx, pt ->
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

    val fireReachedIndex = displayData.indexOfFirst { it.totalPortfolio >= it.target }
    val hasPension = displayData.any { it.pensionPortfolio > 0.0 }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("net_worth_chart_card")
            .semantics {
                contentDescription =
                    "Net worth trajectory chart showing projected portfolio growth and FIRE target over ${data.size} years."
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Net Worth Trajectory",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (effectiveIsReal) "Discounted at ${String.format(java.util.Locale.getDefault(), "%.1f", cpiInflationPct)}% inflation (Today's CZK)" else "Nominal growth over 35-year investment horizon",
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
                        },
                        modifier = Modifier.size(48.dp).testTag("chart_zoom_reset")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Purchasing Power & Y-Scale Toggles
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
                        val nominalBg = if (!effectiveIsReal) BrandTeal else Color.Transparent
                        val nominalFg = if (!effectiveIsReal) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        val realBg = if (effectiveIsReal) BrandTeal else Color.Transparent
                        val realFg = if (effectiveIsReal) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(nominalBg)
                                .clickable { updateReal(false) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Nominal", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = nominalFg)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(realBg)
                                .clickable { updateReal(true) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Real", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = realFg)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend — wraps as whole items so labels never squeeze into one character per line
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (actualPoints.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(cGreen, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Actual", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cTeal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (hasPension) "Liquid" else "Model", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
                if (hasPension) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(cPurple, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Total (incl. DIP/DPS)", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(cGold, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "FIRE Target", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val baseYear = displayData.firstOrNull()?.year ?: 2026
            val maxActual = actualPoints.maxOfOrNull { entry ->
                val yr = entry.yearMonth.take(4).toIntOrNull() ?: baseYear
                val mo = entry.yearMonth.takeLast(2).toIntOrNull() ?: 1
                val yearFraction = (yr - baseYear) + (mo - 1) / 12.0
                val discount = if (effectiveIsReal) (1.0 + (cpiInflationPct / 100.0)).pow(yearFraction) else 1.0
                val investable = entry.portfolioBalanceAtMonthEnd + entry.pensionBalanceAtMonthEnd
                investable / discount
            } ?: 0.0
            val maxVal = (maxOf(
                displayData.maxOfOrNull { it.totalPortfolio } ?: 0.0,
                displayData.maxOfOrNull { it.portfolio } ?: 0.0,
                displayData.maxOfOrNull { it.target } ?: 0.0,
                maxActual
            ) * 1.1).coerceAtLeast(100.0)

            val cSurface = MaterialTheme.colorScheme.surface
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f).toArgb()

            val paddingTop = 18f
            val paddingLeft = 110f // Space for Y-Axis labels (log ticks can be long)
            val paddingRight = 32f // Space on right to prevent clipping of curve and labels
            val paddingBottom = 62f // Space for X-Axis labels
            val textPx = with(LocalDensity.current) { 10.sp.toPx() }

            val textPaint = remember(textColor, textPx) {
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = textPx
                    isAntiAlias = true
                }
            }

            val updateScrubber: (Float, Float) -> Unit = { touchX, width ->
                val plotW = width - paddingLeft - paddingRight
                val chartWidth = plotW * zoomScale
                val stepX = if (displayData.size > 1) chartWidth / (displayData.size - 1).toFloat() else chartWidth
                val relativeX = touchX - paddingLeft - panOffsetX
                if (stepX > 0f && displayData.isNotEmpty()) {
                    val newIdx = kotlin.math.round(relativeX / stepX).toInt().coerceIn(0, displayData.size - 1)
                    if (newIdx != selectedIndex) {
                        selectedIndex = newIdx
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
                        .pointerInput(displayData, zoomScale, panOffsetX) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (zoom != 1.0f) {
                                    zoomScale = (zoomScale * zoom).coerceIn(1.0f, 4.0f)
                                }
                                val plotW = size.width - paddingLeft - paddingRight
                                val maxPan = (plotW * (zoomScale - 1f)).coerceAtLeast(0f)
                                panOffsetX = (panOffsetX + pan.x).coerceIn(-maxPan, 0f)
                            }
                        }
                        .pointerInput(displayData, zoomScale, panOffsetX) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    updateScrubber(offset.x, size.width.toFloat())
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    updateScrubber(change.position.x, size.width.toFloat())
                                }
                            )
                        }
                        .pointerInput(displayData, zoomScale, panOffsetX) {
                            detectTapGestures(
                                onTap = { offset ->
                                    updateScrubber(offset.x, size.width.toFloat())
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft - paddingRight
                    val plotH = h - paddingBottom
                    val usableH = plotH - paddingTop
                    if (w <= 0f || h <= 0f || plotW <= 0f || usableH <= 0f) return@Canvas

                    // Y-value -> pixel mapping (linear axis: 0 .. maxVal)
                    fun yPix(value: Double): Float =
                        paddingTop + (usableH - usableH * (value / maxVal).coerceIn(0.0, 1.0)).toFloat()

                    // Draw Y-Axis lines and numeric labels
                    val tickValues = (0..4).map { maxVal * it / 4.0 }

                    for (valAtStep in tickValues) {
                        val y = yPix(valAtStep)

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
                        start = Offset(paddingLeft, paddingTop),
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
                                (x - textWidth / 2f).coerceIn(paddingLeft, w - paddingRight - textWidth),
                                plotH + 34f,
                                textPaint
                            )
                        }
                    }

                    // Clip chart plotting within the axes space
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, w - paddingRight, plotH)

                    // Build Target Path (Dashed Gold)
                    val targetPath = Path()
                    displayData.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = yPix(pt.target)
                        if (i == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
                    }

                    // Build Portfolio Path (Solid Teal)
                    val portfolioPath = Path()
                    displayData.forEachIndexed { i, pt ->
                        val x = paddingLeft + panOffsetX + (i * stepX)
                        val y = yPix(pt.portfolio)
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

                    // Draw Total Net Worth Path (Solid Purple/Indigo) if pension exists
                    if (hasPension) {
                        val totalPath = Path()
                        displayData.forEachIndexed { i, pt ->
                            val x = paddingLeft + panOffsetX + (i * stepX)
                            val y = yPix(pt.totalPortfolio)
                            if (i == 0) totalPath.moveTo(x, y) else totalPath.lineTo(x, y)
                        }
                        drawPath(
                            path = totalPath,
                            color = cPurple,
                            style = Stroke(width = 5f, cap = StrokeCap.Round)
                        )
                    }

                    // Draw Portfolio path (Liquid Brokerage)
                    drawPath(
                        path = portfolioPath,
                        color = cTeal,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )

                    // Draw Actual Historical Wealth Path (Solid GoodGreen)
                    if (actualPoints.isNotEmpty()) {
                        val baseYearActual = displayData.firstOrNull()?.year ?: 2026
                        val actualPath = Path()
                        actualPoints.forEachIndexed { i, entry ->
                            val yr = entry.yearMonth.take(4).toIntOrNull() ?: baseYearActual
                            val mo = entry.yearMonth.takeLast(2).toIntOrNull() ?: 1
                            val yearFraction = (yr - baseYearActual) + (mo - 1) / 12.0
                            val discount = if (effectiveIsReal) (1.0 + (cpiInflationPct / 100.0)).coerceAtLeast(0.0001).pow(yearFraction) else 1.0
                            val investable = entry.portfolioBalanceAtMonthEnd + entry.pensionBalanceAtMonthEnd
                            val discountedNw = investable / discount
                            val ax = paddingLeft + panOffsetX + (yearFraction.toFloat() * stepX)
                            val ay = yPix(discountedNw)
                            if (i == 0) actualPath.moveTo(ax, ay) else actualPath.lineTo(ax, ay)
                        }

                        if (actualPoints.size >= 2) {
                            drawPath(
                                path = actualPath,
                                color = cGreen,
                                style = Stroke(width = 6.5f, cap = StrokeCap.Round)
                            )
                        }

                        actualPoints.forEach { entry ->
                            val yr = entry.yearMonth.take(4).toIntOrNull() ?: baseYearActual
                            val mo = entry.yearMonth.takeLast(2).toIntOrNull() ?: 1
                            val yearFraction = (yr - baseYearActual) + (mo - 1) / 12.0
                            val discount = if (effectiveIsReal) (1.0 + (cpiInflationPct / 100.0)).pow(yearFraction) else 1.0
                            val investable = entry.portfolioBalanceAtMonthEnd + entry.pensionBalanceAtMonthEnd
                            val discountedNw = investable / discount
                            val ax = paddingLeft + panOffsetX + (yearFraction.toFloat() * stepX)
                            val ay = yPix(discountedNw)

                            drawCircle(color = cGreen.copy(alpha = 0.3f), radius = 13f, center = Offset(ax, ay))
                            drawCircle(color = cGreen, radius = 7f, center = Offset(ax, ay))
                            drawCircle(color = cSurface, radius = 3.5f, center = Offset(ax, ay))
                        }
                    }

                    // Draw Current Position (Now / Start Year) Indicator Dot
                    if (displayData.isNotEmpty()) {
                        val curX = paddingLeft + panOffsetX + (0 * stepX)
                        val curY = yPix(displayData[0].portfolio)
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
                        val fy = yPix(displayData[fireReachedIndex].totalPortfolio)
                        drawCircle(color = cGold, radius = 12f, center = Offset(fx, fy))
                        drawCircle(color = cCardSurface, radius = 6f, center = Offset(fx, fy))
                    }

                    // Draw Interactive Scrubber Crosshair on Selected Year
                    selectedIndex?.let { idx ->
                        if (idx in displayData.indices) {
                            val sx = paddingLeft + panOffsetX + (idx * stepX)
                            if (sx in paddingLeft..(w - paddingRight)) {
                                // Vertical highlight glow band
                                drawLine(
                                    color = cTeal.copy(alpha = 0.08f),
                                    start = Offset(sx, paddingTop),
                                    end = Offset(sx, plotH),
                                    strokeWidth = 24f
                                )
                                // Vertical crosshair line
                                drawLine(
                                    color = cTeal.copy(alpha = 0.70f),
                                    start = Offset(sx, paddingTop),
                                    end = Offset(sx, plotH),
                                    strokeWidth = 2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                                )
                                val pt = displayData[idx]
                                val syTarget = yPix(pt.target)
                                val syLiquid = yPix(pt.portfolio)
                                val syTotal = yPix(pt.totalPortfolio)

                                // Target indicator
                                drawCircle(color = cGold.copy(alpha = 0.25f), radius = 10f, center = Offset(sx, syTarget))
                                drawCircle(color = cGold, radius = 5.5f, center = Offset(sx, syTarget))
                                drawCircle(color = cCardSurface, radius = 2.5f, center = Offset(sx, syTarget))

                                if (hasPension) {
                                    // Total portfolio (including DIP/DPS) indicator
                                    drawCircle(color = cPurple.copy(alpha = 0.25f), radius = 14f, center = Offset(sx, syTotal))
                                    drawCircle(color = cPurple, radius = 8f, center = Offset(sx, syTotal))
                                    drawCircle(color = cCardSurface, radius = 4f, center = Offset(sx, syTotal))
                                }

                                // Liquid brokerage indicator
                                drawCircle(color = cTeal.copy(alpha = 0.25f), radius = 14f, center = Offset(sx, syLiquid))
                                drawCircle(color = cTeal, radius = 8f, center = Offset(sx, syLiquid))
                                drawCircle(color = cCardSurface, radius = 4f, center = Offset(sx, syLiquid))
                            }
                        }
                    }

                    drawContext.canvas.restore()
                }
            }

            // Interactive Scrubber Detail HUD Box for Selected Year
            val activePt = selectedIndex?.let { displayData.getOrNull(it) }
            if (activePt != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val isFireAchieved = activePt.totalPortfolio >= activePt.target
                val pctFunded = if (activePt.target > 0) ((activePt.totalPortfolio / activePt.target) * 100.0).toInt() else 100
                val phaseText = when {
                    isFireAchieved -> "Financial Independence Achieved · Liquid bridge funded"
                    activePt.age < 60 -> "Accumulation Phase · Automated DCA compounding"
                    activePt.age < 65 -> "Retirement Phase · DIP & DPS pension assets unlocked"
                    else -> "State Pension Phase · Public pension supplementing portfolio"
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("net_worth_tooltip_box")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${activePt.year} · Age ${activePt.age}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                )
                                ColorPill(
                                    text = if (isFireAchieved) "FIRE ACHIEVED ($pctFunded%)" else "$pctFunded% OF TARGET",
                                    color = if (isFireAchieved) GoodGreen else BrandGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    horizontalPadding = 6.dp,
                                    verticalPadding = 2.dp,
                                    cornerRadius = 6.dp
                                )
                            }

                            IconButton(
                                onClick = { selectedIndex = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close details",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = phaseText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartMetricPill(
                                label = if (hasPension) "Liquid" else "Portfolio",
                                value = fmtCompact(activePt.portfolio),
                                color = cTeal,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasPension) {
                                ChartMetricPill(
                                    label = "Total NW",
                                    value = fmtCompact(activePt.totalPortfolio),
                                    color = cPurple,
                                    isBold = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            ChartMetricPill(
                                label = "Target",
                                value = fmtCompact(activePt.target),
                                color = cGold,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
