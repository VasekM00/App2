package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LedgerEntryEntity
import com.example.ui.components.ColorPill
import com.example.ui.theme.BadRed
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen

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
        sorted.maxOf { maxOf(it.incVaclav + it.incEleonora + it.incUnforeseen, it.expRent + it.expGroceries + it.expOther) }.coerceAtLeast(100.0) * 1.15
    }

    val haptic = LocalHapticFeedback.current
    val cTeal = BrandTeal
    val cGreen = GoodGreen
    val cRed = BadRed
    val cSurface = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val textPaintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textPx = with(LocalDensity.current) { 10.sp.toPx() }
    val textPaint = remember(textPaintColor, textPx) {
        android.graphics.Paint().apply {
            color = textPaintColor
            textSize = textPx
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "6-Month Trend",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    ColorPill(
                        text = "FLOW",
                        color = cTeal,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 5.dp,
                        verticalPadding = 2.dp
                    )
                }

                // Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(cGreen, CircleShape))
                        Text("Incomes", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).background(cRed, CircleShape))
                        Text("Expenses", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Canvas
            val paddingHorizontal = 36f
            val paddingTop = 18f
            val paddingBottom = 38f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .semantics {
                        contentDescription = "Monthly ledger trend chart showing income and expense history across ${sorted.size} months."
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(sorted) {
                            detectTapGestures { offset ->
                                val plotW = size.width - (paddingHorizontal * 2)
                                val stepX = if (sorted.size > 1) plotW / (sorted.size - 1) else plotW
                                val clickX = (offset.x - paddingHorizontal).coerceAtLeast(0f)
                                val tappedIdx = if (sorted.size > 1) {
                                    (clickX / stepX + 0.5f).toInt().coerceIn(0, sorted.size - 1)
                                } else 0
                                val target = sorted[tappedIdx]
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectMonth(target.yearMonth)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val plotW = w - (paddingHorizontal * 2)
                    val plotH = h - paddingTop - paddingBottom
                    if (plotW <= 0f || plotH <= 0f) return@Canvas
                    val stepX = if (sorted.size > 1) plotW / (sorted.size - 1) else plotW

                    // 1. Grid Lines (3 horizontal dotted lines)
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val yPos = paddingTop + (plotH / gridSteps) * i
                        drawLine(
                            color = gridColor,
                            start = Offset(paddingHorizontal, yPos),
                            end = Offset(w - paddingHorizontal, yPos),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    // 2. Selected Month Vertical Accent Highlight
                    selectedEntry?.let { entry ->
                        val idx = sorted.indexOf(entry)
                        if (idx >= 0) {
                            val selX = paddingHorizontal + idx * stepX
                            drawLine(
                                color = cTeal.copy(alpha = 0.4f),
                                start = Offset(selX, paddingTop),
                                end = Offset(selX, h - paddingBottom),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                        }
                    }

                    // 3. Compute Coordinates
                    val incPoints = mutableListOf<Offset>()
                    val expPoints = mutableListOf<Offset>()

                    sorted.forEachIndexed { index, e ->
                        val x = paddingHorizontal + index * stepX
                        val incVal = e.incVaclav + e.incEleonora + e.incUnforeseen
                        val expVal = e.expRent + e.expGroceries + e.expOther

                        val yInc = paddingTop + plotH - ((incVal / maxVal).coerceIn(0.0, 1.0) * plotH).toFloat()
                        val yExp = paddingTop + plotH - ((expVal / maxVal).coerceIn(0.0, 1.0) * plotH).toFloat()

                        incPoints.add(Offset(x, yInc))
                        expPoints.add(Offset(x, yExp))
                    }

                    // 4. Draw Incomes Line
                    val incPath = Path().apply {
                        incPoints.forEachIndexed { i, pt ->
                            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }
                    drawPath(
                        path = incPath,
                        color = cGreen,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 5. Draw Expenses Line
                    val expPath = Path().apply {
                        expPoints.forEachIndexed { i, pt ->
                            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }
                    drawPath(
                        path = expPath,
                        color = cRed,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // 6. Draw Points and X-Axis Labels
                    sorted.forEachIndexed { index, e ->
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

                        // Label formatting (e.g. "09/26" or "Sep")
                        val label = try {
                            val ym = e.yearMonth.split("-")
                            "${ym.getOrNull(1)}/${ym.getOrNull(0)?.takeLast(2)}"
                        } catch (ex: Exception) { e.yearMonth }

                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            incPt.x,
                            h - 8f,
                            textPaint
                        )
                    }
                }
            }
        }
    }
}
