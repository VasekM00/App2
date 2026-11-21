package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
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
import com.example.data.LedgerEntryEntity
import com.example.domain.PortfolioYearPoint
import com.example.ui.components.ColorPill
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.ui.theme.SurfaceDark
import com.example.util.Formatters.fmtCompact
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private fun PortfolioYearPoint.resolvedDipDps(): Pair<Double, Double> {
    val dip = dipPortfolio.finiteOrZero().coerceAtLeast(0.0)
    val dps = dpsPortfolio.finiteOrZero().coerceAtLeast(0.0)
    val pen = pensionPortfolio.finiteOrZero().coerceAtLeast(0.0)
    return if (dip <= 0.0 && dps <= 0.0 && pen > 0.0) {
        (pen * 0.5) to (pen * 0.5)
    } else {
        dip to dps
    }
}

private fun inflationDiscountFactor(cpiInflationPct: Double, yearFraction: Double): Double {
    val safeCpi = cpiInflationPct.finiteOrZero()
    val safeYears = yearFraction.finiteOrZero()
    val raw = (1.0 + (safeCpi / 100.0)).coerceAtLeast(0.0001).pow(safeYears)
    return if (raw.isFinite() && raw > 0.0) raw else 1.0
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetWorthChart(
    data: List<PortfolioYearPoint>,
    cpiInflationPct: Double = 2.8,
    ledgerEntries: List<LedgerEntryEntity> = emptyList(),
    isRealPurchasingPower: Boolean = false,
    onRealPurchasingPowerChange: ((Boolean) -> Unit)? = null,
    onOpenSandbox: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    currentYearMonth: YearMonth? = null
) {
    if (data.isEmpty()) return

    val safeCpiPct = cpiInflationPct.finiteOrZero()
    val isDarkSurface = MaterialTheme.colorScheme.surface == SurfaceDark
    val cTeal = BrandTeal // Liquid Portfolio
    val cGold = BrandGold // FIRE Target
    val cGreen = GoodGreen // Actual History
    val cDip = if (isDarkSurface) Color(0xFF34D399) else Color(0xFF059669) // DIP (Pension)
    val cDps = BrandBlue // DPS (Pension) - Sky Blue
    val cCash = if (isDarkSurface) Color(0xFFFBBF24) else Color(0xFFD97706) // Emergency Cash
    val cPurple = if (isDarkSurface) Color(0xFF818CF8) else Color(0xFF4F46E5) // Combined Net Worth
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

    // Wealth Layer Visibility Toggles
    var showLiquid by remember { mutableStateOf(true) }
    var showDip by remember { mutableStateOf(true) }
    var showDps by remember { mutableStateOf(true) }
    var showCash by remember { mutableStateOf(true) }

    val animLiquid by rememberLayerVisibilityAnimation(showLiquid)
    val animDip by rememberLayerVisibilityAnimation(showDip)
    val animDps by rememberLayerVisibilityAnimation(showDps)
    val animCash by rememberLayerVisibilityAnimation(showCash)

    // Smooth reveal animation on initial load or scenario / real change
    val revealProgress by rememberChartPathReveal(
        key = Triple(data.size, effectiveIsReal, safeCpiPct),
        durationMillis = 800
    )

    val actualPoints = remember(ledgerEntries) {
        ledgerEntries
            .filter {
                (it.portfolioBalanceAtMonthEnd.finiteOrZero() +
                    it.pensionBalanceAtMonthEnd.finiteOrZero() +
                    it.emergencyReserveAtMonthEnd.finiteOrZero()) > 0.0
            }
            .sortedBy { it.yearMonth }
    }

    val displayData = remember(data, effectiveIsReal, safeCpiPct) {
        if (!effectiveIsReal) {
            data
        } else {
            data.mapIndexed { idx, pt ->
                val discount = inflationDiscountFactor(safeCpiPct, idx.toDouble())
                val discPort = pt.portfolio.finiteOrZero().coerceAtLeast(0.0) / discount
                val discPension = pt.pensionPortfolio.finiteOrZero().coerceAtLeast(0.0) / discount
                val discDip = pt.dipPortfolio.finiteOrZero().coerceAtLeast(0.0) / discount
                val discDps = pt.dpsPortfolio.finiteOrZero().coerceAtLeast(0.0) / discount
                val discCash = pt.emergencyCash.finiteOrZero().coerceAtLeast(0.0) / discount
                pt.copy(
                    portfolio = discPort,
                    target = pt.target.finiteOrZero().coerceAtLeast(0.0) / discount,
                    pensionPortfolio = discPension,
                    totalPortfolio = discPort + discPension,
                    dipPortfolio = discDip,
                    dpsPortfolio = discDps,
                    emergencyCash = discCash
                )
            }
        }
    }

    val baseYear = displayData.firstOrNull()?.year ?: 2026
    val nowYm = currentYearMonth ?: remember { YearMonth.now(ZoneId.of("Europe/Prague")) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val safeSelectedIndex = selectedIndex?.takeIf { displayData.isNotEmpty() }?.coerceIn(0, displayData.lastIndex)

    val spanYears = (data.size - 1).coerceAtLeast(1)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("net_worth_chart_card")
            .semantics {
                contentDescription =
                    "Net worth trajectory chart showing projected wealth layers and FIRE target over $spanYears years."
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
                val isNarrowChart = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 385
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Net Worth Trajectory",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = if (isNarrowChart) 14.5.sp else 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (effectiveIsReal) {
                            "Discounted at ${com.example.util.Formatters.fmtPct(safeCpiPct, 1)} inflation (Today's Kč)"
                        } else {
                            "Nominal growth over $spanYears-year investment horizon"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                // Interactive Zoom Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            zoomScale = (zoomScale * 1.25f).coerceAtMost(4.0f)
                        },
                        modifier = Modifier.size(48.dp).testTag("chart_zoom_in")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            zoomScale = (zoomScale / 1.25f).coerceAtLeast(1.0f)
                            if (zoomScale == 1.0f) panOffsetX = 0.0f
                        },
                        modifier = Modifier.size(48.dp).testTag("chart_zoom_out")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            zoomScale = 1.0f
                            panOffsetX = 0.0f
                        },
                        modifier = Modifier.size(48.dp).testTag("chart_zoom_reset")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Purchasing Power Toggle Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val modes = listOf(false to "Nominal", true to "Real")
                        modes.forEach { (realMode, label) ->
                            val isSelected = effectiveIsReal == realMode
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    updateReal(realMode)
                                },
                                shape = RoundedCornerShape(7.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                                shadowElevation = if (isSelected) 1.dp else 0.dp
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) cTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onOpenSandbox != null) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenSandbox()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = cGold.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, cGold.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("chart_open_sandbox")
                        ) {
                            Text(
                                text = "What-If",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cGold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = "Drag to scrub",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Wealth Layer Filter Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WealthLayerFilterChip(
                    label = "Liquid",
                    color = cTeal,
                    isChecked = showLiquid,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showLiquid = !showLiquid
                    },
                    modifier = Modifier.testTag("layer_chip_liquid")
                )
                WealthLayerFilterChip(
                    label = "DIP",
                    color = cDip,
                    isChecked = showDip,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDip = !showDip
                    },
                    modifier = Modifier.testTag("layer_chip_dip")
                )
                WealthLayerFilterChip(
                    label = "DPS",
                    color = cDps,
                    isChecked = showDps,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDps = !showDps
                    },
                    modifier = Modifier.testTag("layer_chip_dps")
                )
                WealthLayerFilterChip(
                    label = "Cash",
                    color = cCash,
                    isChecked = showCash,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showCash = !showCash
                    },
                    modifier = Modifier.testTag("layer_chip_cash")
                )
                // Static Combined Total Indicator Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(cPurple, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "Total", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
                // Static Target Indicator Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(cGold, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "Target", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                }
                if (actualPoints.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(cGreen, CircleShape))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = "Actual", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val maxActual = actualPoints.maxOfOrNull { entry ->
                val ymParts = entry.yearMonth.split("-")
                val yr = ymParts.getOrNull(0)?.toIntOrNull() ?: baseYear
                val mo = ymParts.getOrNull(1)?.toIntOrNull() ?: 1
                val yearFraction = (yr - baseYear) + (mo - 1) / 12.0
                val discount = if (effectiveIsReal) inflationDiscountFactor(safeCpiPct, yearFraction) else 1.0
                val investable = entry.portfolioBalanceAtMonthEnd.finiteOrZero().coerceAtLeast(0.0) +
                    entry.pensionBalanceAtMonthEnd.finiteOrZero().coerceAtLeast(0.0) +
                    entry.emergencyReserveAtMonthEnd.finiteOrZero().coerceAtLeast(0.0)
                (investable / discount).finiteOrZero()
            } ?: 0.0

            val maxVal = (maxOf(
                displayData.maxOfOrNull {
                    val (dip, dps) = it.resolvedDipDps()
                    val cash = it.emergencyCash.finiteOrZero().coerceAtLeast(0.0)
                    val liq = it.portfolio.finiteOrZero().coerceAtLeast(0.0)
                    (liq * animLiquid + dip * animDip + dps * animDps + cash * animCash).finiteOrZero()
                } ?: 0.0,
                displayData.maxOfOrNull { it.totalPortfolio.finiteOrZero().coerceAtLeast(0.0) } ?: 0.0,
                displayData.maxOfOrNull { it.target.finiteOrZero().coerceAtLeast(0.0) } ?: 0.0,
                maxActual
            ) * 1.1).finiteOrZero().coerceAtLeast(100.0)

            val cSurface = MaterialTheme.colorScheme.surface
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
            val nowTextColor = cTeal.toArgb()
            val nowBadgePaint = remember(nowTextColor, textPx) {
                android.graphics.Paint().apply {
                    color = nowTextColor
                    textSize = textPx
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    isAntiAlias = true
                }
            }
            val targetPath = remember { Path() }
            val dipPath = remember { Path() }
            val dpsPath = remember { Path() }
            val cashPath = remember { Path() }
            val liquidPath = remember { Path() }
            val activeNwPath = remember { Path() }
            val actualPath = remember { Path() }
            val targetDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) }
            val curDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) }
            val selDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) }

            val tickValues = remember(maxVal) { (0..4).map { maxVal * it / 4.0 } }
            val maxYLabelW = remember(tickValues, textPaint) {
                tickValues.maxOf { v -> textPaint.measureText(fmtCompact(v)) }
            }
            val paddingTop = with(density) { 14.dp.toPx() }
            val paddingLeft = maxOf(with(density) { 58.dp.toPx() }, maxYLabelW + with(density) { 12.dp.toPx() })
            val paddingRight = with(density) { 16.dp.toPx() }
            val paddingBottom = maxOf(with(density) { 28.dp.toPx() }, textPx + with(density) { 14.dp.toPx() })
            val xLabelGap = with(density) { 6.dp.toPx() }

            val updateScrubber: (Float, Float) -> Unit = { touchX, width ->
                val plotW = width - paddingLeft - paddingRight
                val chartWidth = plotW * zoomScale
                if (plotW > 0f && displayData.isNotEmpty()) {
                    val newIdx = if (displayData.size > 1) {
                        val stepX = chartWidth / (displayData.size - 1).toFloat()
                        val relativeX = touchX - paddingLeft - panOffsetX
                        if (stepX > 0f) kotlin.math.round(relativeX / stepX).toInt().coerceIn(0, displayData.lastIndex) else 0
                    } else 0
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
                        .pointerInput(displayData, paddingLeft, paddingRight) {
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
                                                updateScrubber(change.position.x, size.width.toFloat())
                                            }
                                        }
                                        lastX = change.position.x
                                    }
                                }
                                if (!moved && !multiTouch) {
                                    updateScrubber(downPos.x, size.width.toFloat())
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

                    fun yPix(value: Double): Float {
                        val frac = safeFraction(value.finiteOrZero().coerceAtLeast(0.0), maxVal)
                        return paddingTop + (usableH - usableH * frac)
                    }

                    // Draw Y-Axis lines and numeric labels
                    for (valAtStep in tickValues) {
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

                    // Vertical Y-Axis line
                    drawLine(
                        color = gridColor,
                        start = Offset(paddingLeft, paddingTop),
                        end = Offset(paddingLeft, plotH),
                        strokeWidth = 2f
                    )

                    // Draw X-Axis Year Labels
                    val chartWidth = plotW * zoomScale
                    val stepX = if (displayData.size > 1) chartWidth / (displayData.size - 1).toFloat() else 0f
                    fun xForIndex(index: Int): Float = if (displayData.size > 1) {
                        paddingLeft + panOffsetX + (index * stepX)
                    } else {
                        paddingLeft + panOffsetX + (chartWidth / 2f)
                    }
                    val xStepCount = 5
                    var lastDrawnRight = -Float.MAX_VALUE
                    for (i in 0 until displayData.size step max(1, displayData.size / xStepCount)) {
                        val pt = displayData[i]
                        val x = xForIndex(i)
                        if (x in paddingLeft..(w - paddingRight + 4f)) {
                            drawLine(
                                color = gridColor,
                                start = Offset(x, plotH),
                                end = Offset(x, plotH + 5.dp.toPx()),
                                strokeWidth = 2f
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

                    // Clip chart plotting within the axes space & reveal clip
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, w - paddingRight, plotH)

                    val clampedReveal = revealProgress.finiteOrZero().coerceIn(0f, 1f)
                    val revealClipRight = paddingLeft + (w - paddingRight - paddingLeft) * clampedReveal
                    drawContext.canvas.clipRect(paddingLeft, paddingTop, revealClipRight, plotH)

                    // Helper to get active layer values for any point
                    fun getLayerValues(pt: PortfolioYearPoint): ActiveLayerSnapshot {
                        val liq = pt.portfolio.finiteOrZero().coerceAtLeast(0.0)
                        val (dip, dps) = pt.resolvedDipDps()
                        val cash = pt.emergencyCash.finiteOrZero().coerceAtLeast(0.0)
                        val effLiq = liq * animLiquid
                        val effDip = dip * animDip
                        val effDps = dps * animDps
                        val effCash = cash * animCash
                        val activeTotal = (effLiq + effDip + effDps + effCash).finiteOrZero()
                        return ActiveLayerSnapshot(liq, dip, dps, cash, effDip, effDps, effCash, activeTotal)
                    }

                    if (displayData.size > 1) {
                        // 1. Build & Draw FIRE Target Path (Dashed Gold)
                        targetPath.reset()
                        displayData.forEachIndexed { i, pt ->
                            val x = xForIndex(i)
                            val y = yPix(pt.target)
                            if (i == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
                        }
                        drawPath(
                            path = targetPath,
                            color = cGold,
                            style = Stroke(width = 3.5f, pathEffect = targetDashEffect)
                        )

                        // 2. Individual Wealth Layer Lines (DIP, DPS, Cash, Liquid) with animated alphas
                        if (animDip > 0.02f) {
                            dipPath.reset()
                            displayData.forEachIndexed { i, pt ->
                                val x = xForIndex(i)
                                val snap = getLayerValues(pt)
                                val y = yPix(snap.dip * animDip)
                                if (i == 0) dipPath.moveTo(x, y) else dipPath.lineTo(x, y)
                            }
                            drawPath(dipPath, color = cDip.copy(alpha = animDip * 0.85f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                        }

                        if (animDps > 0.02f) {
                            dpsPath.reset()
                            displayData.forEachIndexed { i, pt ->
                                val x = xForIndex(i)
                                val snap = getLayerValues(pt)
                                val y = yPix(snap.dps * animDps)
                                if (i == 0) dpsPath.moveTo(x, y) else dpsPath.lineTo(x, y)
                            }
                            drawPath(dpsPath, color = cDps.copy(alpha = animDps * 0.85f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                        }

                        if (animCash > 0.02f) {
                            cashPath.reset()
                            displayData.forEachIndexed { i, pt ->
                                val x = xForIndex(i)
                                val snap = getLayerValues(pt)
                                val y = yPix(snap.cash * animCash)
                                if (i == 0) cashPath.moveTo(x, y) else cashPath.lineTo(x, y)
                            }
                            drawPath(cashPath, color = cCash.copy(alpha = animCash * 0.80f), style = Stroke(width = 3f, cap = StrokeCap.Round))
                        }

                        if (animLiquid > 0.02f) {
                            liquidPath.reset()
                            displayData.forEachIndexed { i, pt ->
                                val x = xForIndex(i)
                                val snap = getLayerValues(pt)
                                val y = yPix(snap.liquid * animLiquid)
                                if (i == 0) liquidPath.moveTo(x, y) else liquidPath.lineTo(x, y)
                            }
                            drawPath(liquidPath, color = cTeal.copy(alpha = animLiquid * 0.90f), style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                        }

                        // 3. Combined Active Net Worth Trajectory Path
                        activeNwPath.reset()
                        displayData.forEachIndexed { i, pt ->
                            val x = xForIndex(i)
                            val snap = getLayerValues(pt)
                            val y = yPix(snap.activeTotal)
                            if (i == 0) activeNwPath.moveTo(x, y) else activeNwPath.lineTo(x, y)
                        }
                        drawPath(
                            path = activeNwPath,
                            color = cPurple,
                            style = Stroke(width = 5.5f, cap = StrokeCap.Round)
                        )
                    } else {
                        val sx = xForIndex(0)
                        val pt = displayData[0]
                        val snap = getLayerValues(pt)
                        drawCircle(color = cGold, radius = 6f, center = Offset(sx, yPix(pt.target)))
                        if (animDip > 0.02f) drawCircle(color = cDip.copy(alpha = animDip), radius = 5f, center = Offset(sx, yPix(snap.dip * animDip)))
                        if (animDps > 0.02f) drawCircle(color = cDps.copy(alpha = animDps), radius = 5f, center = Offset(sx, yPix(snap.dps * animDps)))
                        if (animCash > 0.02f) drawCircle(color = cCash.copy(alpha = animCash), radius = 5f, center = Offset(sx, yPix(snap.cash * animCash)))
                        if (animLiquid > 0.02f) drawCircle(color = cTeal.copy(alpha = animLiquid), radius = 6f, center = Offset(sx, yPix(snap.liquid * animLiquid)))
                        drawCircle(color = cPurple, radius = 8f, center = Offset(sx, yPix(snap.activeTotal)))
                    }

                    // 4. Actual Historical Wealth Path (GoodGreen)
                    if (actualPoints.isNotEmpty()) {
                        val baseYearActual = displayData.firstOrNull()?.year ?: 2026
                        actualPath.reset()
                        actualPoints.forEachIndexed { i, entry ->
                            val ymParts = entry.yearMonth.split("-")
                            val yr = ymParts.getOrNull(0)?.toIntOrNull() ?: baseYearActual
                            val mo = ymParts.getOrNull(1)?.toIntOrNull() ?: 1
                            val yearFraction = (yr - baseYearActual) + (mo - 1) / 12.0
                            val discount = if (effectiveIsReal) inflationDiscountFactor(safeCpiPct, yearFraction) else 1.0
                            val investable = entry.portfolioBalanceAtMonthEnd.finiteOrZero().coerceAtLeast(0.0) +
                                entry.pensionBalanceAtMonthEnd.finiteOrZero().coerceAtLeast(0.0) +
                                entry.emergencyReserveAtMonthEnd.finiteOrZero().coerceAtLeast(0.0)
                            val discountedNw = (investable / discount).finiteOrZero()
                            val ax = if (displayData.size > 1) {
                                paddingLeft + panOffsetX + (yearFraction.toFloat() * stepX)
                            } else {
                                paddingLeft + panOffsetX + (chartWidth / 2f)
                            }
                            val ay = yPix(discountedNw)
                            if (i == 0) actualPath.moveTo(ax, ay) else actualPath.lineTo(ax, ay)
                        }
                        if (actualPoints.size >= 2) {
                            drawPath(
                                path = actualPath,
                                color = cGreen,
                                style = Stroke(width = 6f, cap = StrokeCap.Round)
                            )
                        }
                        actualPoints.forEach { entry ->
                            val ymParts = entry.yearMonth.split("-")
                            val yr = ymParts.getOrNull(0)?.toIntOrNull() ?: baseYearActual
                            val mo = ymParts.getOrNull(1)?.toIntOrNull() ?: 1
                            val yearFraction = (yr - baseYearActual) + (mo - 1) / 12.0
                            val discount = if (effectiveIsReal) inflationDiscountFactor(safeCpiPct, yearFraction) else 1.0
                            val investable = entry.portfolioBalanceAtMonthEnd.finiteOrZero().coerceAtLeast(0.0) +
                                entry.pensionBalanceAtMonthEnd.finiteOrZero().coerceAtLeast(0.0) +
                                entry.emergencyReserveAtMonthEnd.finiteOrZero().coerceAtLeast(0.0)
                            val discountedNw = (investable / discount).finiteOrZero()
                            val ax = if (displayData.size > 1) {
                                paddingLeft + panOffsetX + (yearFraction.toFloat() * stepX)
                            } else {
                                paddingLeft + panOffsetX + (chartWidth / 2f)
                            }
                            val ay = yPix(discountedNw)
                            drawCircle(color = cGreen.copy(alpha = 0.3f), radius = 12f, center = Offset(ax, ay))
                            drawCircle(color = cGreen, radius = 6.5f, center = Offset(ax, ay))
                            drawCircle(color = cSurface, radius = 3f, center = Offset(ax, ay))
                        }
                    }

                    // 5. Draw Current Position (Now) Indicator Dot & Label on the Line
                    if (displayData.isNotEmpty()) {
                        val maxSpanYears = (displayData.size - 1).coerceAtLeast(0).toDouble()
                        val rawNowFraction = (nowYm.year - baseYear) + (nowYm.monthValue - 1) / 12.0
                        val nowFraction = rawNowFraction.coerceIn(0.0, maxSpanYears)
                        val curX = if (displayData.size > 1) {
                            paddingLeft + panOffsetX + (nowFraction.toFloat() * stepX)
                        } else {
                            xForIndex(0)
                        }
                        val loIdx = floor(nowFraction).toInt().coerceIn(0, displayData.lastIndex)
                        val hiIdx = (loIdx + 1).coerceIn(0, displayData.lastIndex)
                        val localT = (nowFraction - loIdx).coerceIn(0.0, 1.0)
                        val snapLo = getLayerValues(displayData[loIdx]).activeTotal
                        val snapHi = getLayerValues(displayData[hiIdx]).activeTotal
                        val nowTotal = snapLo + (snapHi - snapLo) * localT
                        val curY = yPix(nowTotal)

                        if (curX in paddingLeft..(w - paddingRight)) {
                            drawLine(
                                color = cTeal.copy(alpha = 0.45f),
                                start = Offset(curX, curY),
                                end = Offset(curX, plotH),
                                strokeWidth = 2f,
                                pathEffect = curDashEffect
                            )
                            drawCircle(color = cTeal.copy(alpha = 0.25f), radius = 14f, center = Offset(curX, curY))
                            drawCircle(color = cTeal, radius = 8f, center = Offset(curX, curY))
                            drawCircle(color = cCardSurface, radius = 4f, center = Offset(curX, curY))

                            val nowText = "Now"
                            val nowTextW = nowBadgePaint.measureText(nowText)
                            val padH = 6.dp.toPx()
                            val padV = 3.dp.toPx()
                            val badgeW = nowTextW + padH * 2f
                            val badgeH = textPx + padV * 2f
                            val minBadgeLeft = paddingLeft + 4.dp.toPx()
                            val maxBadgeLeft = (w - paddingRight - badgeW - 4.dp.toPx()).coerceAtLeast(minBadgeLeft)
                            val badgeLeft = (curX - badgeW / 2f).coerceIn(minBadgeLeft, maxBadgeLeft)
                            val preferAboveTop = curY - 12.dp.toPx() - badgeH
                            val badgeTop = if (preferAboveTop >= paddingTop + 2.dp.toPx()) {
                                preferAboveTop
                            } else {
                                (curY + 12.dp.toPx()).coerceAtMost(plotH - badgeH - 2.dp.toPx())
                            }
                            drawRoundRect(
                                color = cCardSurface.copy(alpha = 0.92f),
                                topLeft = Offset(badgeLeft, badgeTop),
                                size = Size(badgeW, badgeH),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                            drawRoundRect(
                                color = cTeal.copy(alpha = 0.75f),
                                topLeft = Offset(badgeLeft, badgeTop),
                                size = Size(badgeW, badgeH),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                nowText,
                                badgeLeft + padH,
                                badgeTop + padV + textPx * 0.85f,
                                nowBadgePaint
                            )
                        }
                    }

                    // 6. Draw Interactive Scrubber Crosshair on Selected Year
                    safeSelectedIndex?.let { idx ->
                        if (idx in displayData.indices) {
                            val sx = xForIndex(idx)
                            if (sx in paddingLeft..(w - paddingRight)) {
                                drawLine(
                                    color = cTeal.copy(alpha = 0.12f),
                                    start = Offset(sx, paddingTop),
                                    end = Offset(sx, plotH),
                                    strokeWidth = 24f
                                )
                                drawLine(
                                    color = cTeal.copy(alpha = 0.75f),
                                    start = Offset(sx, paddingTop),
                                    end = Offset(sx, plotH),
                                    strokeWidth = 2f,
                                    pathEffect = selDashEffect
                                )
                                val pt = displayData[idx]
                                val snap = getLayerValues(pt)
                                val syTarget = yPix(pt.target)
                                val syActiveTotal = yPix(snap.activeTotal)

                                // Target indicator dot
                                drawCircle(color = cGold.copy(alpha = 0.35f), radius = 10f, center = Offset(sx, syTarget))
                                drawCircle(color = cGold, radius = 5.5f, center = Offset(sx, syTarget))
                                drawCircle(color = cCardSurface, radius = 2.5f, center = Offset(sx, syTarget))

                                // Active Total indicator dot
                                drawCircle(color = cPurple.copy(alpha = 0.35f), radius = 14f, center = Offset(sx, syActiveTotal))
                                drawCircle(color = cPurple, radius = 8f, center = Offset(sx, syActiveTotal))
                                drawCircle(color = cCardSurface, radius = 4f, center = Offset(sx, syActiveTotal))

                                if (showLiquid && animLiquid > 0.05f) {
                                    val syLiq = yPix(snap.liquid * animLiquid)
                                    drawCircle(color = cTeal, radius = 5f, center = Offset(sx, syLiq))
                                }
                            }
                        }
                    }

                    drawContext.canvas.restore()
                }
            }

            // Modern Interactive Scrubber Inspection Card / Pill
            val activeIdx = safeSelectedIndex
            val activePt = activeIdx?.let { displayData.getOrNull(it) }
            val rawActivePt = activeIdx?.let { data.getOrNull(it) }

            if (activePt != null && rawActivePt != null) {
                Spacer(modifier = Modifier.height(10.dp))

                val discount = inflationDiscountFactor(safeCpiPct, activeIdx.toDouble())
                val nominalTotalNw = rawActivePt.totalPortfolio.finiteOrZero()
                val realTotalNw = nominalTotalNw / discount
                val activeTotal = activePt.totalPortfolio.finiteOrZero()
                val activeTarget = activePt.target.finiteOrZero()

                val targetDelta = activeTotal - activeTarget
                val isFireAchieved = targetDelta >= 0
                val pctFunded = if (activeTarget > 0.0) ((activeTotal / activeTarget) * 100.0).roundToInt() else 100

                val deltaText = if (isFireAchieved) {
                    "+${fmtCompact(targetDelta)}"
                } else {
                    fmtCompact(targetDelta)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
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
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${activePt.year} - Age ${activePt.age}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp
                                    )
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
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedIndex = null
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close details",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Key Metrics 2x2 Grid (so values never clip on 360dp narrow screens)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartMetricPill(
                                label = "Nominal Total",
                                value = fmtCompact(nominalTotalNw),
                                color = cPurple,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                            ChartMetricPill(
                                label = "Real Total",
                                value = fmtCompact(realTotalNw),
                                color = cTeal,
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
                                label = "FIRE Target",
                                value = fmtCompact(activeTarget),
                                color = cGold,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                            ChartMetricPill(
                                label = "Target Delta",
                                value = deltaText,
                                color = if (isFireAchieved) GoodGreen else BrandGold,
                                isBold = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Active Wealth Layer Breakdown Rows (2 columns max per row)
                        val (dipAmt, dpsAmt) = activePt.resolvedDipDps()
                        val cashAmt = activePt.emergencyCash.finiteOrZero().coerceAtLeast(0.0)

                        val activeLayerPills = buildList {
                            if (showLiquid) add(Triple("Liquid", fmtCompact(activePt.portfolio.finiteOrZero()), cTeal))
                            if (showDip) add(Triple("DIP", fmtCompact(dipAmt), cDip))
                            if (showDps) add(Triple("DPS", fmtCompact(dpsAmt), cDps))
                            if (showCash) add(Triple("Cash", fmtCompact(cashAmt), cCash))
                        }

                        activeLayerPills.chunked(2).forEach { rowPills ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowPills.forEach { (lbl, valStr, col) ->
                                    ChartMetricPill(
                                        label = lbl,
                                        value = valStr,
                                        color = col,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowPills.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ActiveLayerSnapshot(
    val liquid: Double,
    val dip: Double,
    val dps: Double,
    val cash: Double,
    val effDip: Double,
    val effDps: Double,
    val effCash: Double,
    val activeTotal: Double
)

@Composable
fun WealthLayerFilterChip(
    label: String,
    color: Color,
    isChecked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        color = if (isChecked) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (isChecked) color.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
        ),
        modifier = modifier.defaultMinSize(minHeight = 36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isChecked) color else Color.Transparent)
                    .then(
                        if (!isChecked) Modifier.background(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(3.dp)
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.32f) Color(0xFF0F172A) else Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
