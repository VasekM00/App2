package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.ui.components.ColorPill
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.components.charts.safeFraction
import com.example.ui.theme.BodyFontFamily
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact

private fun safeEntryIncome(e: LedgerEntryEntity): Double =
    e.incVaclav.finiteOrZero().coerceAtLeast(0.0) +
        e.incEleonora.finiteOrZero().coerceAtLeast(0.0) +
        e.incUnforeseen.finiteOrZero().coerceAtLeast(0.0)

private fun safeEntryExpense(e: LedgerEntryEntity): Double =
    e.expRent.finiteOrZero().coerceAtLeast(0.0) +
        e.expGroceries.finiteOrZero().coerceAtLeast(0.0) +
        e.expOther.finiteOrZero().coerceAtLeast(0.0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LedgerChart(
    entries: List<LedgerEntryEntity>,
    selectedYm: String = "",
    onSelectMonth: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val sorted = remember(entries) { entries.sortedBy { it.yearMonth }.takeLast(6) }
    if (sorted.isEmpty()) return

    val maxVal = remember(sorted) {
        val rawMax = sorted.maxOfOrNull { maxOf(safeEntryIncome(it), safeEntryExpense(it)) } ?: 0.0
        rawMax.finiteOrZero().coerceAtLeast(100.0) * 1.15
    }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cTeal = BrandTeal
    val cGreen = GoodGreen
    val cRed = BadRed
    val cSurface = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val textPaintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textPx = with(density) { 10.sp.toPx() }
    val textPaint = remember(textPaintColor, textPx) {
        android.graphics.Paint().apply {
            color = textPaintColor
            textSize = textPx
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val yLabelPaint = remember(textPaintColor, textPx) {
        android.graphics.Paint().apply {
            color = textPaintColor
            textSize = textPx * 0.92f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    val gridDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }
    val selectedDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f) }
    val lineStroke = remember(density) {
        Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round)
    }
    val gridSteps = 2
    val yTickLabels = remember(maxVal) {
        (0..gridSteps).map { i -> fmtCompact(maxVal * (gridSteps - i) / gridSteps) }
    }
    val xLabels = remember(sorted) {
        sorted.map { e ->
            val parts = e.yearMonth.split("-")
            if (parts.size >= 2 && parts[0].length >= 2) {
                "${parts[1]}/${parts[0].takeLast(2)}"
            } else {
                e.yearMonth
            }
        }
    }
    val incPath = remember { Path() }
    val expPath = remember { Path() }
    val incPoints = remember { mutableListOf<Offset>() }
    val expPoints = remember { mutableListOf<Offset>() }

    val selectedEntry = remember(sorted, selectedYm) {
        sorted.find { it.yearMonth == selectedYm } ?: sorted.lastOrNull()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "6-Month Cash Flow Trend chart showing monthly income and expense history." },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // Header Row with Title & Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "6-Month Trend",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ColorPill(
                        text = "FLOW",
                        color = cTeal,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 5.dp,
                        verticalPadding = 2.dp,
                        cornerRadius = 6.dp
                    )
                }

                // Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(cGreen, CircleShape))
                        Text(
                            "Incomes",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(cRed, CircleShape))
                        Text(
                            "Expenses",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Canvas (density-aware padding with Y-axis scale)
            val maxYLabelW = remember(yTickLabels, yLabelPaint) {
                yTickLabels.maxOf { label -> yLabelPaint.measureText(label) }
            }
            val paddingLeft = maxOf(with(density) { 48.dp.toPx() }, maxYLabelW + with(density) { 10.dp.toPx() })
            val paddingRight = with(density) { 16.dp.toPx() }
            val paddingTop = with(density) { 12.dp.toPx() }
            val paddingBottom = maxOf(with(density) { 24.dp.toPx() }, textPx + with(density) { 10.dp.toPx() })
            val xLabelGap = with(density) { 6.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .semantics {
                        contentDescription = "Monthly ledger trend chart showing income and expense history across ${sorted.size} months."
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(sorted, paddingLeft, paddingRight) {
                            detectTapGestures { offset ->
                                val plotW = size.width - paddingLeft - paddingRight
                                val tappedIdx = if (sorted.size > 1 && plotW > 0f) {
                                    val stepX = plotW / (sorted.size - 1)
                                    val clickX = (offset.x - paddingLeft).coerceAtLeast(0f)
                                    (clickX / stepX + 0.5f).toInt().coerceIn(0, sorted.size - 1)
                                } else {
                                    0
                                }
                                val target = sorted[tappedIdx]
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectMonth(target.yearMonth)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - paddingLeft - paddingRight
                    val plotH = h - paddingTop - paddingBottom
                    if (plotW <= 0f || plotH <= 0f) return@Canvas
                    val stepX = if (sorted.size > 1) plotW / (sorted.size - 1) else 0f

                    // 1. Grid Lines & Y-Axis Scale Labels
                    for (i in 0..gridSteps) {
                        val yPos = paddingTop + (plotH / gridSteps) * i
                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, yPos),
                            end = Offset(w - paddingRight, yPos),
                            strokeWidth = 1f,
                            pathEffect = gridDashEffect
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            yTickLabels[i],
                            2.dp.toPx(),
                            (yPos + textPx * 0.32f).coerceIn(textPx, (paddingTop + plotH).coerceAtLeast(textPx)),
                            yLabelPaint
                        )
                    }

                    // 2. Selected Month Vertical Accent Highlight
                    selectedEntry?.let { entry ->
                        val idx = sorted.indexOf(entry)
                        if (idx >= 0) {
                            val selX = if (sorted.size > 1) {
                                paddingLeft + idx * stepX
                            } else {
                                paddingLeft + plotW / 2f
                            }
                            drawLine(
                                color = cTeal.copy(alpha = 0.4f),
                                start = Offset(selX, paddingTop),
                                end = Offset(selX, h - paddingBottom),
                                strokeWidth = 2f,
                                pathEffect = selectedDashEffect
                            )
                        }
                    }

                    // 3. Compute Coordinates
                    incPoints.clear()
                    expPoints.clear()

                    for (index in sorted.indices) {
                        val e = sorted[index]
                        val x = if (sorted.size > 1) {
                            paddingLeft + index * stepX
                        } else {
                            paddingLeft + plotW / 2f
                        }
                        val incVal = safeEntryIncome(e)
                        val expVal = safeEntryExpense(e)

                        val yInc = paddingTop + plotH - (safeFraction(incVal, maxVal) * plotH)
                        val yExp = paddingTop + plotH - (safeFraction(expVal, maxVal) * plotH)

                        incPoints.add(Offset(x, yInc))
                        expPoints.add(Offset(x, yExp))
                    }

                    // 4. Draw Incomes Line (when multiple points exist)
                    if (incPoints.size > 1) {
                        incPath.reset()
                        for (i in incPoints.indices) {
                            val pt = incPoints[i]
                            if (i == 0) incPath.moveTo(pt.x, pt.y) else incPath.lineTo(pt.x, pt.y)
                        }
                        drawPath(
                            path = incPath,
                            color = cGreen,
                            style = lineStroke
                        )
                    }

                    // 5. Draw Expenses Line (when multiple points exist)
                    if (expPoints.size > 1) {
                        expPath.reset()
                        for (i in expPoints.indices) {
                            val pt = expPoints[i]
                            if (i == 0) expPath.moveTo(pt.x, pt.y) else expPath.lineTo(pt.x, pt.y)
                        }
                        drawPath(
                            path = expPath,
                            color = cRed,
                            style = lineStroke
                        )
                    }

                    // 6. Draw Points and X-Axis Labels (with collision guard)
                    val labelBaselineY = (h - 5.dp.toPx()).coerceAtLeast(paddingTop + plotH + textPx)
                    var lastLabelRight = -Float.MAX_VALUE
                    for (index in sorted.indices) {
                        val e = sorted[index]
                        val incPt = incPoints[index]
                        val expPt = expPoints[index]
                        val isSelected = e.yearMonth == selectedEntry?.yearMonth

                        // Incomes Node Dot
                        drawCircle(
                            color = cSurface,
                            radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                            center = incPt
                        )
                        drawCircle(
                            color = cGreen,
                            radius = if (isSelected) 3.5.dp.toPx() else 2.5.dp.toPx(),
                            center = incPt
                        )

                        // Expenses Node Dot
                        drawCircle(
                            color = cSurface,
                            radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                            center = expPt
                        )
                        drawCircle(
                            color = cRed,
                            radius = if (isSelected) 3.5.dp.toPx() else 2.5.dp.toPx(),
                            center = expPt
                        )

                        val label = xLabels[index]
                        val labelW = textPaint.measureText(label)
                        val clampedCenter = incPt.x.coerceIn(
                            paddingLeft + labelW / 2f,
                            (w - paddingRight - labelW / 2f).coerceAtLeast(paddingLeft + labelW / 2f)
                        )
                        val labelLeft = clampedCenter - labelW / 2f
                        if (labelLeft >= lastLabelRight + xLabelGap || index == sorted.lastIndex && labelLeft >= lastLabelRight + 2f) {
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                clampedCenter,
                                labelBaselineY,
                                textPaint
                            )
                            lastLabelRight = clampedCenter + labelW / 2f
                        }
                    }
                }
            }

            // Selected Month Inspection Summary Row
            selectedEntry?.let { entry ->
                val incTotal = safeEntryIncome(entry)
                val expTotal = safeEntryExpense(entry)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = entry.yearMonth,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Income: ${fmtCompact(incTotal)}",
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = cGreen
                            )
                        )
                        Text(
                            text = "Expenses: ${fmtCompact(expTotal)}",
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = BodyFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = cRed
                            )
                        )
                    }
                }
            }
        }
    }
}
