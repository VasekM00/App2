package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.roundToInt

@Composable
fun DcaTrajectoryBarChart(
    data: List<PortfolioYearPoint>,
    settings: SettingsEntity? = null,
    modifier: Modifier = Modifier,
    currentYearMonth: java.time.YearMonth? = null,
) {
    if (data.isEmpty()) return

    val cTeal = BrandTeal
    val cGold = BrandGold
    val cGreen = GoodGreen

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val safeSelectedIndex = selectedIndex?.takeIf { data.isNotEmpty() }?.coerceIn(0, data.lastIndex)
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0.0f) }
    var viewMode by remember { mutableIntStateOf(0) } // 0 = Annual Inflow Streams, 1 = Principal vs Compound Gains

    val initialBal = remember(data) {
        (data.firstOrNull()?.totalPortfolio ?: 0.0).finiteOrZero().coerceAtLeast(0.0)
    }

    // Precalculate cumulative invested principal and cumulative compound gains
    val cumulativeData = remember(data, initialBal) {
        var runningPrincipal = initialBal
        data.mapIndexed { idx, pt ->
            if (idx > 0) {
                runningPrincipal += pt.investedAnnual.finiteOrZero().coerceAtLeast(0.0) +
                    pt.pensionInflowAnnual.finiteOrZero().coerceAtLeast(0.0) +
                    pt.reinvestAnnual.finiteOrZero().coerceAtLeast(0.0) +
                    pt.lumpSum.finiteOrZero().coerceAtLeast(0.0)
            }
            val safeTotal = pt.totalPortfolio.finiteOrZero().coerceAtLeast(0.0)
            val gains = (safeTotal - runningPrincipal).finiteOrZero().coerceAtLeast(0.0)
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
                        cornerRadius = 6.dp
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
                        modifier = Modifier.size(48.dp).testTag("dca_bar_zoom_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale / 1.25f).coerceAtLeast(1.0f)
                            if (zoomScale == 1.0f) panOffsetX = 0.0f
                        },
                        modifier = Modifier.size(48.dp).testTag("dca_bar_zoom_out")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            zoomScale = 1.0f
                            panOffsetX = 0.0f
                            selectedIndex = null
                        },
                        modifier = Modifier.size(48.dp).testTag("dca_bar_zoom_reset")
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
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
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
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
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

            val ySteps = 4
            val maxVal = remember(data, viewMode, cumulativeData) {
                val rawMax = if (viewMode == 0) {
                    (data.maxOfOrNull {
                        it.investedAnnual.finiteOrZero().coerceAtLeast(0.0) +
                            it.pensionInflowAnnual.finiteOrZero().coerceAtLeast(0.0) +
                            it.reinvestAnnual.finiteOrZero().coerceAtLeast(0.0) +
                            it.lumpSum.finiteOrZero().coerceAtLeast(0.0)
                    } ?: 0.0) * 1.2
                } else {
                    (data.maxOfOrNull { it.totalPortfolio.finiteOrZero().coerceAtLeast(0.0) } ?: 0.0) * 1.15
                }
                computeNiceAxisMax(rawMax.finiteOrZero().coerceAtLeast(100000.0), steps = ySteps)
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
            val selDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }

            val maxYLabelW = remember(maxVal, textPaint) {
                (0..ySteps).maxOf { i -> textPaint.measureText(fmtCompact(maxVal * i / ySteps)) }
            }
            val paddingTop = with(density) { 14.dp.toPx() }
            val paddingLeft = maxOf(with(density) { 54.dp.toPx() }, maxYLabelW + with(density) { 10.dp.toPx() })
            val paddingRight = with(density) { 16.dp.toPx() }
            val paddingBottom = maxOf(with(density) { 26.dp.toPx() }, textPx + with(density) { 12.dp.toPx() })
            val xLabelGap = with(density) { 6.dp.toPx() }

            val updateBarScrubber: (Float, Float) -> Unit = { touchX, width ->
                val chartWidth = (width - paddingLeft - paddingRight).coerceAtLeast(1f) * zoomScale
                val relativeX = touchX - paddingLeft - panOffsetX
                val clickedIdx = if (data.size > 1) {
                    val stepX = chartWidth / (data.size - 1).toFloat()
                    if (stepX > 0f) round(relativeX / stepX).toInt().coerceIn(0, data.size - 1) else 0
                } else 0
                selectedIndex = clickedIdx
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
                        .pointerInput(data, paddingLeft, paddingRight) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPos = down.position
                                var moved = false
                                var multiTouch = false
                                var lastX = downPos.x
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        multiTouch = true
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        if (zoomChange != 1f) {
                                            zoomScale = (zoomScale * zoomChange).coerceIn(1.0f, 4.0f)
                                        }
                                        val plotW = (size.width - paddingLeft - paddingRight).coerceAtLeast(1f)
                                        val maxPan = (plotW * (zoomScale - 1f)).coerceAtLeast(0f)
                                        panOffsetX = (panOffsetX + panChange.x).coerceIn(-maxPan, 0f)
                                        pressed.forEach { it.consume() }
                                    } else if (!multiTouch) {
                                        val change = pressed.first()
                                        val dx = change.position.x - lastX
                                        val dy = change.position.y - downPos.y
                                        if (!moved && kotlin.math.abs(change.position.x - downPos.x) > viewConfiguration.touchSlop) {
                                            moved = true
                                        }
                                        if (moved && kotlin.math.abs(change.position.x - downPos.x) >= kotlin.math.abs(dy)) {
                                            change.consume()
                                            if (zoomScale > 1.01f) {
                                                val plotW = (size.width - paddingLeft - paddingRight).coerceAtLeast(1f)
                                                val maxPan = (plotW * (zoomScale - 1f)).coerceAtLeast(0f)
                                                panOffsetX = (panOffsetX + dx).coerceIn(-maxPan, 0f)
                                            } else {
                                                updateBarScrubber(change.position.x, size.width.toFloat())
                                            }
                                        }
                                        lastX = change.position.x
                                    }
                                }
                                if (!moved && !multiTouch) {
                                    updateBarScrubber(downPos.x, size.width.toFloat())
                                }
                            }
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
                            fmtCompact(valAtStep),
                            4.dp.toPx(),
                            (y + textPx * 0.35f).coerceIn(textPx, plotH.coerceAtLeast(textPx)),
                            textPaint
                        )
                    }

                    // X-Axis
                    val chartWidth = plotW * zoomScale
                    val stepX = if (data.size > 1) chartWidth / (data.size - 1).toFloat() else 0f
                    val barWidth = if (data.size > 1) (stepX * 0.55f).coerceIn(5f, 28f) else 24f.coerceAtMost(plotW * 0.5f)
                    fun xForIndex(index: Int): Float = if (data.size > 1) {
                        paddingLeft + panOffsetX + (index * stepX)
                    } else {
                        paddingLeft + panOffsetX + (chartWidth / 2f)
                    }

                    val xStepCount = 5
                    var lastDrawnRight = -Float.MAX_VALUE
                    for (i in 0 until data.size step max(1, data.size / xStepCount)) {
                        val pt = data[i]
                        val x = xForIndex(i)
                        if (x in paddingLeft..(w - paddingRight + 4f)) {
                            drawLine(
                                color = gridColor,
                                start = Offset(x, plotH),
                                end = Offset(x, plotH + 5.dp.toPx()),
                                strokeWidth = 1.5f
                            )
                            val labelText = "${pt.year}"
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

                    // Draw Bars
                    data.forEachIndexed { i, pt ->
                        val x = xForIndex(i)
                        val left = x - barWidth / 2f
                        val right = x + barWidth / 2f

                        if (right >= paddingLeft && left <= (w - paddingRight)) {
                            if (viewMode == 0) {
                                // Annual Streams
                                val baseAnnual = pt.investedAnnual.finiteOrZero().coerceAtLeast(0.0) +
                                    pt.pensionInflowAnnual.finiteOrZero().coerceAtLeast(0.0)
                                val hBase = safeFraction(baseAnnual, maxVal) * usableH
                                val hReinvest = safeFraction(pt.reinvestAnnual, maxVal) * usableH
                                val hLump = safeFraction(pt.lumpSum, maxVal) * usableH

                                var currentY = plotH

                                if (hBase > 0f) {
                                    drawRect(
                                        color = cTeal,
                                        topLeft = Offset(left, currentY - hBase),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hBase)
                                    )
                                    currentY -= hBase
                                }
                                if (hReinvest > 0f) {
                                    drawRect(
                                        color = cGold,
                                        topLeft = Offset(left, currentY - hReinvest),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hReinvest)
                                    )
                                    currentY -= hReinvest
                                }
                                if (hLump > 0f) {
                                    drawRect(
                                        color = cGreen,
                                        topLeft = Offset(left, currentY - hLump),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hLump)
                                    )
                                }
                            } else {
                                // Principal vs Gains
                                val (_, principal, gains) = cumulativeData[i]
                                val hPrincipal = safeFraction(principal, maxVal) * usableH
                                val hGains = safeFraction(gains, maxVal) * usableH

                                var currentY = plotH
                                if (hPrincipal > 0f) {
                                    drawRect(
                                        color = cTeal,
                                        topLeft = Offset(left, currentY - hPrincipal),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hPrincipal)
                                    )
                                    currentY -= hPrincipal
                                }
                                if (hGains > 0f) {
                                    drawRect(
                                        color = cGreen,
                                        topLeft = Offset(left, currentY - hGains),
                                        size = androidx.compose.ui.geometry.Size(barWidth, hGains)
                                    )
                                }
                            }

                            // Selection highlight indicator
                            if (safeSelectedIndex == i) {
                                drawLine(
                                    color = cTeal.copy(alpha = 0.6f),
                                    start = Offset(x, paddingTop),
                                    end = Offset(x, plotH),
                                    strokeWidth = 2f,
                                    pathEffect = selDashEffect
                                )
                                drawCircle(color = cTeal, radius = 6f, center = Offset(x, paddingTop + 7f))
                            }
                        }
                    }

                    drawNowMarkerOnSeries(
                        items = data,
                        yearOf = { it.year },
                        valueOf = { pt ->
                            if (viewMode == 0) {
                                pt.investedAnnual.finiteOrZero() + pt.pensionInflowAnnual.finiteOrZero() +
                                    pt.reinvestAnnual.finiteOrZero() + pt.lumpSum.finiteOrZero()
                            } else {
                                pt.totalPortfolio.finiteOrZero()
                            }
                        },
                        xForFraction = { frac -> if (data.size > 1) paddingLeft + panOffsetX + (frac * stepX) else xForIndex(0) },
                        yPix = ::yPix,
                        plotLeft = paddingLeft,
                        plotRight = w - paddingRight,
                        plotTop = paddingTop,
                        plotBottom = plotH,
                        accentColor = cTeal,
                        currentYearMonth = currentYearMonth,
                        showDot = false,
                    )

                    drawContext.canvas.restore()
                }
            }

            // Interactive Tooltip Box
            val activeIdx = safeSelectedIndex ?: (if (data.isNotEmpty()) data.size - 1 else 0)
            val activeTriple = cumulativeData.getOrNull(activeIdx)
            if (activeTriple != null) {
                val (pt, principal, gains) = activeTriple
                val safeInvested = pt.investedAnnual.finiteOrZero().coerceAtLeast(0.0)
                val safePensionInflow = pt.pensionInflowAnnual.finiteOrZero().coerceAtLeast(0.0)
                val safeReinvest = pt.reinvestAnnual.finiteOrZero().coerceAtLeast(0.0)
                val safeLump = pt.lumpSum.finiteOrZero().coerceAtLeast(0.0)
                val safeTotalPortfolio = pt.totalPortfolio.finiteOrZero().coerceAtLeast(0.0)
                val totalInflow = safeInvested + safePensionInflow + safeReinvest + safeLump
                val gainsPct = if (safeTotalPortfolio > 0.0) (gains / safeTotalPortfolio * 100.0) else 0.0

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
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
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.5.sp
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ColorPill(
                                    text = "Age ${pt.age}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    horizontalPadding = 5.dp,
                                    verticalPadding = 1.5.dp,
                                    cornerRadius = 6.dp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                ColorPill(
                                    text = pt.status,
                                    color = if (pt.status == "FIRE OK" || pt.status == "FIRE READY") GoodGreen else BrandTeal,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    horizontalPadding = 6.dp,
                                    verticalPadding = 2.5.dp,
                                    cornerRadius = 6.dp
                                )
                                if (safeSelectedIndex != null) {
                                    IconButton(
                                        onClick = { selectedIndex = null },
                                        modifier = Modifier.size(36.dp)
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
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Grid of Pillboxes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartMetricPill(
                                label = "Monthly DCA",
                                value = fmtCompact((safeInvested + safePensionInflow) / 12.0),
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
                                label = "Profit (${gainsPct.roundToInt()}%)",
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
                                value = fmtCompact(safeTotalPortfolio),
                                color = BrandTeal,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                            ChartMetricPill(
                                label = "FIRE Target",
                                value = fmtCompact(pt.target.finiteOrZero()),
                                color = BrandGold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
