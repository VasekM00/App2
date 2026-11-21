package com.example.ui.components.sankey

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.charts.finiteOrZero
import com.example.ui.components.charts.rememberChartPathReveal
import com.example.ui.components.charts.safeFraction
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct
import kotlin.math.abs

private data class PositionedSankeyNode(
    val layout: LayoutNode,
    val labelCenterY: Float
)

private data class SankeyRibbonSpec(
    val tapNodeId: String,
    val focusIds: Set<String>,
    val x1: Float,
    val x2: Float,
    val y1Top: Float,
    val y1Bottom: Float,
    val y2Top: Float,
    val y2Bottom: Float,
    val startColor: Color,
    val endColor: Color,
    val strokeColor: Color
)

private data class SankeyLayoutData(
    val width: Float,
    val height: Float,
    val paddingTop: Float,
    val nodeWidth: Float,
    val cornerR: Float,
    val xCenter: Float,
    val inflowLayouts: List<PositionedSankeyNode>,
    val outflowLayouts: List<PositionedSankeyNode>,
    val hubLayout: LayoutNode,
    val ribbons: List<SankeyRibbonSpec>,
    val allClickableNodes: List<LayoutNode>
)

private fun resolveNonOverlappingLabelCenters(
    rawCenters: List<Float>,
    minY: Float,
    maxY: Float,
    minPitch: Float
): List<Float> {
    if (rawCenters.isEmpty()) return emptyList()
    if (rawCenters.size == 1) return listOf(rawCenters[0].coerceIn(minY, maxY))
    val ys = rawCenters.map { it.coerceIn(minY, maxY) }.toMutableList()
    val effectivePitch = minPitch.coerceAtMost(((maxY - minY) / (ys.size - 1).coerceAtLeast(1)).coerceAtLeast(8f))
    for (i in 1 until ys.size) {
        if (ys[i] < ys[i - 1] + effectivePitch) {
            ys[i] = ys[i - 1] + effectivePitch
        }
    }
    if (ys.last() > maxY) {
        val overflow = ys.last() - maxY
        ys[ys.lastIndex] = maxY
        for (i in ys.lastIndex - 1 downTo 0) {
            val maxAllowed = ys[i + 1] - effectivePitch
            if (ys[i] > maxAllowed) {
                ys[i] = maxAllowed.coerceAtLeast(minY)
            }
        }
        if (ys.first() < minY && overflow > 0f) {
            ys[0] = minY
            for (i in 1 until ys.size) {
                if (ys[i] < ys[i - 1] + effectivePitch) {
                    ys[i] = (ys[i - 1] + effectivePitch).coerceAtMost(maxY)
                }
            }
        }
    }
    return ys
}

private fun fitCanvasTextLines(
    text: String,
    maxWidth: Float,
    paint: Paint,
    baseTextSize: Float,
    minTextSize: Float,
    allowTwoLineWrap: Boolean
): Pair<Float, List<String>> {
    val safeMax = maxWidth.coerceAtLeast(12f)
    paint.textSize = baseTextSize
    val fullW = paint.measureText(text)
    if (fullW <= safeMax) {
        return baseTextSize to listOf(text)
    }

    val shrunkSize = (baseTextSize * (safeMax / fullW)).coerceAtLeast(minTextSize)
    paint.textSize = shrunkSize
    if (paint.measureText(text) <= safeMax) {
        return shrunkSize to listOf(text)
    }

    if (allowTwoLineWrap && text.contains(' ')) {
        val wrapSize = maxOf(minTextSize, baseTextSize * 0.86f)
        paint.textSize = wrapSize
        val mid = text.length / 2
        val spaceIndices = text.indices.filter { text[it] == ' ' }.sortedBy { abs(it - mid) }
        for (idx in spaceIndices) {
            val l1 = text.substring(0, idx).trimEnd()
            val l2 = text.substring(idx + 1).trimStart()
            if (l1.isNotEmpty() && l2.isNotEmpty() &&
                paint.measureText(l1) <= safeMax &&
                paint.measureText(l2) <= safeMax
            ) {
                return wrapSize to listOf(l1, l2)
            }
        }
        paint.textSize = minTextSize
        val firstFit = paint.breakText(text, true, safeMax, null).coerceAtLeast(1)
        val splitAt = text.lastIndexOf(' ', firstFit.coerceAtMost(text.length - 1))
        if (splitAt > 0 && splitAt < text.length - 1) {
            val l1 = text.substring(0, splitAt).trimEnd()
            val rawL2 = text.substring(splitAt + 1).trimStart()
            val l2 = if (paint.measureText(rawL2) <= safeMax) {
                rawL2
            } else {
                val ellW = paint.measureText("…")
                val c = paint.breakText(rawL2, true, (safeMax - ellW).coerceAtLeast(4f), null)
                rawL2.take(c).trimEnd() + "…"
            }
            if (l1.isNotEmpty()) {
                return minTextSize to listOf(l1, l2)
            }
        }
    }

    paint.textSize = minTextSize
    val ellW = paint.measureText("…")
    val count = paint.breakText(text, true, (safeMax - ellW).coerceAtLeast(4f), null).coerceAtLeast(1)
    return minTextSize to listOf(text.take(count).trimEnd() + "…")
}

private fun solveBezierTForX(x: Float, x1: Float, cX: Float, x2: Float): Float {
    if (x <= x1) return 0f
    if (x >= x2) return 1f
    val p1x = x1 + cX
    val p2x = x2 - cX
    var lo = 0f
    var hi = 1f
    repeat(10) {
        val t = (lo + hi) * 0.5f
        val u = 1f - t
        val bx = (u * u * u * x1) + (3f * u * u * t * p1x) + (3f * u * t * t * p2x) + (t * t * t * x2)
        if (bx < x) lo = t else hi = t
    }
    return (lo + hi) * 0.5f
}

private fun evalCubicY(t: Float, y1: Float, y2: Float): Float {
    val u = 1f - t
    return (u * u * u * y1) + (3f * u * u * t * y1) + (3f * u * t * t * y2) + (t * t * t * y2)
}

@Composable
fun SankeyCanvasDiagram(
    inflowNodes: List<SankeyNode>,
    outflowNodes: List<SankeyNode>,
    centralHubNode: SankeyNode,
    sumInflows: Double,
    sumOutflows: Double,
    viewMode: Int,
    selectedNodeId: String?,
    canvasHeight: Dp,
    onSelectNode: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val effScale = density.fontScale.coerceIn(1f, 1.45f)
    val textPx = with(density) { (9.5f * effScale).dp.toPx() }
    val boldTextPx = with(density) { (10f * effScale).dp.toPx() }
    val minTextPx = with(density) { 7.5.dp.toPx() }
    val titleBaselineOffset = boldTextPx * 0.24f
    val amountBaselineOffset = textPx * 1.02f
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val selectionBorderColor = MaterialTheme.colorScheme.onSurface

    val labelPaint = remember(textColor, textPx) {
        Paint().apply {
            color = textColor
            textSize = textPx
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
    }
    val boldPaint = remember(textColor, boldTextPx) {
        Paint().apply {
            color = textColor
            textSize = boldTextPx
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
    }
    val poolLabelPaint = remember(textMutedColor, textPx) {
        Paint().apply {
            color = textMutedColor
            textSize = textPx * 0.95f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
            isAntiAlias = true
        }
    }

    // Animated ribbon flow gradient phase (infinite smooth flow from source to sink)
    val infiniteTransition = rememberInfiniteTransition(label = "SankeyFlow")
    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowPhase"
    )

    // Smooth reveal progress on viewMode or stream changes
    val revealProgress by rememberChartPathReveal(
        key = Pair(viewMode, inflowNodes.size + outflowNodes.size),
        durationMillis = 650
    )

    val cTeal = BrandTeal
    val cGreen = GoodGreen
    val hubBrush = remember(cTeal, cGreen) { Brush.verticalGradient(listOf(cTeal, cGreen)) }
    val ribbonPath = remember { Path() }
    val topEdgePath = remember { Path() }
    val bottomEdgePath = remember { Path() }

    val latestSelectedNodeId by rememberUpdatedState(selectedNodeId)
    val latestOnSelectNode by rememberUpdatedState(onSelectNode)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()

            val layoutData = remember(
                wPx,
                hPx,
                inflowNodes,
                outflowNodes,
                centralHubNode,
                sumInflows,
                sumOutflows,
                viewMode,
                textPx,
                boldTextPx,
                density,
                labelPaint,
                boldPaint,
                poolLabelPaint
            ) {
                if (wPx <= 0f || hPx <= 0f) {
                    null
                } else {
                    with(density) {
                        val is3Pillar = viewMode == 0
                        val poolTextHeight = poolLabelPaint.fontMetrics.let { it.descent - it.ascent }
                        val paddingTop = if (is3Pillar) {
                            16.dp.toPx()
                        } else {
                            maxOf(22.dp.toPx(), poolTextHeight + 10.dp.toPx())
                        }
                        val paddingBottom = 20.dp.toPx()
                        val nodeWidth = 8.5f.dp.toPx()
                        val cornerR = 4.dp.toPx()

                        boldPaint.textSize = boldTextPx
                        labelPaint.textSize = textPx
                        val widestInLabel = inflowNodes.maxOfOrNull { node ->
                            maxOf(
                                boldPaint.measureText(node.shortLabel),
                                labelPaint.measureText(fmtCompact(node.amount.finiteOrZero()))
                            )
                        } ?: 64.dp.toPx()
                        val pctDenomLayout = sumInflows.finiteOrZero().let { if (it > 0.0) it else 1.0 }
                        val widestOutLabel = outflowNodes.maxOfOrNull { node ->
                            val pct = (node.amount.finiteOrZero().coerceAtLeast(0.0) / pctDenomLayout * 100.0).coerceIn(0.0, 999.0)
                            val titleText = "${node.shortLabel} (${fmtPct(pct, 0)})"
                            maxOf(
                                boldPaint.measureText(titleText),
                                labelPaint.measureText(fmtCompact(node.amount.finiteOrZero()))
                            )
                        } ?: 96.dp.toPx()

                        val minLeftMargin = if (is3Pillar) 76.dp.toPx() else 84.dp.toPx()
                        val minRightMargin = if (is3Pillar) 116.dp.toPx() else 108.dp.toPx()
                        val rawLeftMargin = maxOf(minLeftMargin, widestInLabel + 12.dp.toPx())
                        val rawRightMargin = maxOf(minRightMargin, widestOutLabel + 12.dp.toPx())
                        val minRibbonSpan = 48.dp.toPx() + nodeWidth * 2f
                        val marginScale = ((wPx - minRibbonSpan).coerceAtLeast(40f) / (rawLeftMargin + rawRightMargin)).coerceAtMost(1f)
                        val leftMargin = rawLeftMargin * marginScale
                        val rightMargin = rawRightMargin * marginScale
                        val xInflow = leftMargin
                        val xOutflow = (wPx - rightMargin - nodeWidth).coerceAtLeast(xInflow + nodeWidth * 3f)
                        val ribbonStart = xInflow + nodeWidth
                        val ribbonEnd = xOutflow
                        val xCenter = (ribbonStart + ribbonEnd - nodeWidth) / 2f

                        val availableH = (hPx - paddingTop - paddingBottom).coerceAtLeast(16f)
                        val gap = if (is3Pillar) 10.dp.toPx() else 5.dp.toPx()
                        val minNodeH = if (is3Pillar) 16.dp.toPx() else 8.dp.toPx()
                        val minLabelPitch = boldTextPx * 2.10f + textPx + 8.dp.toPx()
                        val minLabelY = paddingTop + boldTextPx * 1.2f
                        val maxLabelY = (hPx - amountBaselineOffset - boldTextPx * 0.4f - 6.dp.toPx()).coerceAtLeast(minLabelY)

                        val safeSumIn = sumInflows.finiteOrZero().coerceAtLeast(1.0)
                        val safeSumOut = sumOutflows.finiteOrZero().coerceAtLeast(1.0)
                        val flowScaleMax = maxOf(safeSumIn, safeSumOut, 1.0)
                        val inScale = (safeSumIn / flowScaleMax).toFloat().coerceIn(0f, 1f)
                        val outScale = (safeSumOut / flowScaleMax).toFloat().coerceIn(0f, 1f)

                        // 1. Inflow Node Geometries
                        val totalInflowGap = (inflowNodes.size - 1).coerceAtLeast(0) * gap
                        val maxInflowBarH = (availableH - totalInflowGap).coerceAtLeast(minNodeH)
                        val targetInflowBarH = maxOf(
                            inflowNodes.size * minNodeH,
                            maxInflowBarH * inScale
                        ).coerceAtMost(maxInflowBarH)
                        val extraInflowH = (targetInflowBarH - inflowNodes.size * minNodeH).coerceAtLeast(0f)

                        val rawInLayouts = mutableListOf<LayoutNode>()
                        var curInY = paddingTop
                        inflowNodes.forEach { node ->
                            val frac = safeFraction(node.amount, safeSumIn)
                            val nodeH = minNodeH + (frac * extraInflowH)
                            val bounds = RoundRect(
                                left = xInflow,
                                top = curInY,
                                right = xInflow + nodeWidth,
                                bottom = curInY + nodeH,
                                cornerRadius = CornerRadius(cornerR, cornerR)
                            )
                            rawInLayouts.add(LayoutNode(node, curInY, curInY + nodeH, bounds))
                            curInY += nodeH + gap
                        }
                        val resolvedInLabelYs = resolveNonOverlappingLabelCenters(
                            rawCenters = rawInLayouts.map { (it.yTop + it.yBottom) / 2f },
                            minY = minLabelY,
                            maxY = maxLabelY,
                            minPitch = minLabelPitch
                        )
                        val inflowLayouts = rawInLayouts.mapIndexed { idx, ln ->
                            PositionedSankeyNode(ln, resolvedInLabelYs[idx])
                        }

                        // 2. Central Hub Geometry
                        val hubTop = paddingTop
                        val hubInH = (availableH * inScale).coerceAtLeast(minNodeH)
                        val hubOutH = (availableH * outScale).coerceAtLeast(minNodeH)
                        val hubBottom = (hubTop + maxOf(hubInH, hubOutH)).coerceAtMost(hPx - paddingBottom)
                        val hubBounds = RoundRect(
                            left = xCenter,
                            top = hubTop,
                            right = xCenter + nodeWidth,
                            bottom = hubBottom,
                            cornerRadius = CornerRadius(cornerR, cornerR)
                        )
                        val hubLayout = LayoutNode(centralHubNode, hubTop, hubBottom, hubBounds)

                        // 3. Outflow Node Geometries
                        val totalOutflowGap = (outflowNodes.size - 1).coerceAtLeast(0) * gap
                        val maxOutflowBarH = (availableH - totalOutflowGap).coerceAtLeast(minNodeH)
                        val targetOutflowBarH = maxOf(
                            outflowNodes.size * minNodeH,
                            maxOutflowBarH * outScale
                        ).coerceAtMost(maxOutflowBarH)
                        val extraOutflowH = (targetOutflowBarH - outflowNodes.size * minNodeH).coerceAtLeast(0f)

                        val rawOutLayouts = mutableListOf<LayoutNode>()
                        var curOutY = paddingTop
                        outflowNodes.forEach { node ->
                            val frac = safeFraction(node.amount, safeSumOut)
                            val nodeH = minNodeH + (frac * extraOutflowH)
                            val bounds = RoundRect(
                                left = xOutflow,
                                top = curOutY,
                                right = xOutflow + nodeWidth,
                                bottom = curOutY + nodeH,
                                cornerRadius = CornerRadius(cornerR, cornerR)
                            )
                            rawOutLayouts.add(LayoutNode(node, curOutY, curOutY + nodeH, bounds))
                            curOutY += nodeH + gap
                        }
                        val resolvedOutLabelYs = resolveNonOverlappingLabelCenters(
                            rawCenters = rawOutLayouts.map { (it.yTop + it.yBottom) / 2f },
                            minY = minLabelY,
                            maxY = maxLabelY,
                            minPitch = minLabelPitch
                        )
                        val outflowLayouts = rawOutLayouts.mapIndexed { idx, ln ->
                            PositionedSankeyNode(ln, resolvedOutLabelYs[idx])
                        }

                        // 4. Ribbons
                        val ribbons = buildList {
                            if (is3Pillar) {
                                val inLayout = rawInLayouts.firstOrNull()
                                if (inLayout != null) {
                                    val inUsableH = (inLayout.yBottom - inLayout.yTop).coerceAtLeast(1f)
                                    var curSourceY = inLayout.yTop
                                    rawOutLayouts.forEach { outLayout ->
                                        val frac = safeFraction(outLayout.node.amount, safeSumOut)
                                        val sliceH = frac * inUsableH
                                        val sourceTop = curSourceY
                                        val sourceBottom = curSourceY + sliceH
                                        curSourceY += sliceH
                                        add(
                                            SankeyRibbonSpec(
                                                tapNodeId = outLayout.node.id,
                                                focusIds = setOf(inLayout.node.id, outLayout.node.id),
                                                x1 = inLayout.bounds.right,
                                                x2 = outLayout.bounds.left,
                                                y1Top = sourceTop,
                                                y1Bottom = sourceBottom,
                                                y2Top = outLayout.yTop,
                                                y2Bottom = outLayout.yBottom,
                                                startColor = inLayout.node.color,
                                                endColor = outLayout.node.color,
                                                strokeColor = outLayout.node.color
                                            )
                                        )
                                    }
                                }
                            } else {
                                var hubInY = hubTop
                                rawInLayouts.forEach { inLayout ->
                                    val ribbonH = safeFraction(inLayout.node.amount, safeSumIn) * hubInH
                                    val targetTop = hubInY
                                    val targetBottom = hubInY + ribbonH
                                    hubInY += ribbonH
                                    add(
                                        SankeyRibbonSpec(
                                            tapNodeId = inLayout.node.id,
                                            focusIds = setOf(inLayout.node.id, centralHubNode.id),
                                            x1 = inLayout.bounds.right,
                                            x2 = xCenter,
                                            y1Top = inLayout.yTop,
                                            y1Bottom = inLayout.yBottom,
                                            y2Top = targetTop,
                                            y2Bottom = targetBottom,
                                            startColor = inLayout.node.color,
                                            endColor = cTeal,
                                            strokeColor = inLayout.node.color
                                        )
                                    )
                                }
                                var hubOutY = hubTop
                                rawOutLayouts.forEach { outLayout ->
                                    val ribbonH = safeFraction(outLayout.node.amount, safeSumOut) * hubOutH
                                    val sourceTop = hubOutY
                                    val sourceBottom = hubOutY + ribbonH
                                    hubOutY += ribbonH
                                    add(
                                        SankeyRibbonSpec(
                                            tapNodeId = outLayout.node.id,
                                            focusIds = setOf(outLayout.node.id, centralHubNode.id),
                                            x1 = xCenter + nodeWidth,
                                            x2 = outLayout.bounds.left,
                                            y1Top = sourceTop,
                                            y1Bottom = sourceBottom,
                                            y2Top = outLayout.yTop,
                                            y2Bottom = outLayout.yBottom,
                                            startColor = cTeal,
                                            endColor = outLayout.node.color,
                                            strokeColor = outLayout.node.color
                                        )
                                    )
                                }
                            }
                        }

                        SankeyLayoutData(
                            width = wPx,
                            height = hPx,
                            paddingTop = paddingTop,
                            nodeWidth = nodeWidth,
                            cornerR = cornerR,
                            xCenter = xCenter,
                            inflowLayouts = inflowLayouts,
                            outflowLayouts = outflowLayouts,
                            hubLayout = hubLayout,
                            ribbons = ribbons,
                            allClickableNodes = if (is3Pillar) {
                                rawInLayouts + rawOutLayouts
                            } else {
                                rawInLayouts + hubLayout + rawOutLayouts
                            }
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(layoutData) {
                        detectTapGestures { offset ->
                            val layout = layoutData ?: return@detectTapGestures
                            val padX = 24.dp.toPx()
                            val padY = 6.dp.toPx()
                            val labelHalfH = (boldTextPx + textPx + 6.dp.toPx()) / 2f
                            val curSelected = latestSelectedNodeId

                            // 1a. Check inflow & outflow label columns (tapping a node's text label selects it)
                            val clickedInLabel = layout.inflowLayouts.firstOrNull { pos ->
                                offset.x <= pos.layout.bounds.right + padX &&
                                    abs(offset.y - pos.labelCenterY) <= labelHalfH
                            }
                            if (clickedInLabel != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnSelectNode(if (curSelected == clickedInLabel.layout.node.id) null else clickedInLabel.layout.node.id)
                                return@detectTapGestures
                            }
                            val clickedOutLabel = layout.outflowLayouts.firstOrNull { pos ->
                                offset.x >= pos.layout.bounds.left - padX &&
                                    abs(offset.y - pos.labelCenterY) <= labelHalfH
                            }
                            if (clickedOutLabel != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnSelectNode(if (curSelected == clickedOutLabel.layout.node.id) null else clickedOutLabel.layout.node.id)
                                return@detectTapGestures
                            }

                            // 1b. Check direct node hit
                            val clickedNode = layout.allClickableNodes.firstOrNull { node ->
                                offset.x in (node.bounds.left - padX)..(node.bounds.right + padX) &&
                                    offset.y in (node.bounds.top - padY)..(node.bounds.bottom + padY)
                            }
                            if (clickedNode != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnSelectNode(if (curSelected == clickedNode.node.id) null else clickedNode.node.id)
                                return@detectTapGestures
                            }

                            // 2. Accurate cubic Bezier ribbon envelope hit-testing
                            val ribbonPadY = 4.dp.toPx()
                            val hitRibbon = layout.ribbons.firstOrNull { r ->
                                if (offset.x !in r.x1..r.x2) {
                                    false
                                } else {
                                    val cX = (r.x2 - r.x1) * 0.52f
                                    val t = solveBezierTForX(offset.x, r.x1, cX, r.x2)
                                    val yTop = evalCubicY(t, r.y1Top, r.y2Top)
                                    val yBot = evalCubicY(t, r.y1Bottom, r.y2Bottom)
                                    val lo = minOf(yTop, yBot) - ribbonPadY
                                    val hi = maxOf(yTop, yBot) + ribbonPadY
                                    offset.y in lo..hi
                                }
                            }

                            if (hitRibbon != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnSelectNode(if (curSelected == hitRibbon.tapNodeId) null else hitRibbon.tapNodeId)
                            } else {
                                latestOnSelectNode(null)
                            }
                        }
                    }
            ) {
                val layout = layoutData ?: return@Canvas
                val is3Pillar = viewMode == 0
                val curSelected = selectedNodeId
                val safeFlowPhase = flowPhase.finiteOrZero().coerceIn(0f, 1f)
                val safeReveal = revealProgress.finiteOrZero().coerceIn(0f, 1f)

                fun createRibbonBrush(
                    cStart: Color,
                    cEnd: Color,
                    startX: Float,
                    endX: Float,
                    alpha: Float,
                    isFocused: Boolean
                ): Brush {
                    val baseStart = cStart.copy(alpha = alpha)
                    val baseEnd = cEnd.copy(alpha = alpha)
                    val pulseColor = if (isFocused) {
                        Color.White.copy(alpha = (alpha * 1.5f).coerceAtMost(0.95f))
                    } else {
                        Color(
                            (baseStart.red + baseEnd.red) / 2f,
                            (baseStart.green + baseEnd.green) / 2f,
                            (baseStart.blue + baseEnd.blue) / 2f,
                            (alpha * 1.25f).coerceAtMost(0.70f)
                        )
                    }
                    val p0 = (safeFlowPhase - 0.22f).coerceIn(0.01f, 0.98f)
                    val p1 = safeFlowPhase.coerceIn(0.02f, 0.99f)
                    val p2 = (safeFlowPhase + 0.22f).coerceIn(0.03f, 1.0f)
                    return Brush.horizontalGradient(
                        0.0f to baseStart,
                        p0 to baseStart,
                        p1 to pulseColor,
                        p2 to baseEnd,
                        1.0f to baseEnd,
                        startX = startX,
                        endX = endX
                    )
                }

                // Smooth reveal clip
                val revealRight = layout.width * safeReveal
                drawContext.canvas.save()
                drawContext.canvas.clipRect(0f, 0f, revealRight, layout.height)

                // Draw Ribbons using pooled Path objects
                layout.ribbons.forEach { r ->
                    val isFocused = curSelected != null && curSelected in r.focusIds
                    val isDimmed = curSelected != null && !isFocused

                    val fillAlpha = when {
                        isFocused -> 0.58f
                        isDimmed -> if (is3Pillar) 0.06f else 0.05f
                        else -> 0.40f
                    }
                    val strokeAlpha = when {
                        isFocused -> 0.88f
                        isDimmed -> 0.12f
                        else -> 0.70f
                    }
                    val strokeW = when {
                        isFocused -> 2.2f.dp.toPx()
                        isDimmed -> 0.6f.dp.toPx()
                        else -> 1.25f.dp.toPx()
                    }
                    val cX = (r.x2 - r.x1) * 0.52f

                    ribbonPath.reset()
                    ribbonPath.moveTo(r.x1, r.y1Top)
                    ribbonPath.cubicTo(r.x1 + cX, r.y1Top, r.x2 - cX, r.y2Top, r.x2, r.y2Top)
                    ribbonPath.lineTo(r.x2, r.y2Bottom)
                    ribbonPath.cubicTo(r.x2 - cX, r.y2Bottom, r.x1 + cX, r.y1Bottom, r.x1, r.y1Bottom)
                    ribbonPath.close()

                    drawPath(
                        path = ribbonPath,
                        brush = createRibbonBrush(
                            cStart = r.startColor,
                            cEnd = r.endColor,
                            startX = r.x1,
                            endX = r.x2,
                            alpha = fillAlpha,
                            isFocused = isFocused
                        )
                    )

                    topEdgePath.reset()
                    topEdgePath.moveTo(r.x1, r.y1Top)
                    topEdgePath.cubicTo(r.x1 + cX, r.y1Top, r.x2 - cX, r.y2Top, r.x2, r.y2Top)
                    drawPath(topEdgePath, color = r.strokeColor.copy(alpha = strokeAlpha), style = Stroke(strokeW))

                    bottomEdgePath.reset()
                    bottomEdgePath.moveTo(r.x1, r.y1Bottom)
                    bottomEdgePath.cubicTo(r.x1 + cX, r.y1Bottom, r.x2 - cX, r.y2Bottom, r.x2, r.y2Bottom)
                    drawPath(bottomEdgePath, color = r.strokeColor.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                }

                // Inflow Nodes & Margin Labels
                layout.inflowLayouts.forEach { pos ->
                    val item = pos.layout
                    val isSel = curSelected == item.node.id
                    drawRoundRect(
                        color = item.node.color,
                        topLeft = Offset(item.bounds.left, item.bounds.top),
                        size = Size(item.bounds.width, item.bounds.height),
                        cornerRadius = CornerRadius(layout.cornerR, layout.cornerR)
                    )
                    if (isSel) {
                        drawRoundRect(
                            color = selectionBorderColor,
                            topLeft = Offset(item.bounds.left - 2.5f, item.bounds.top - 2.5f),
                            size = Size(item.bounds.width + 5f, item.bounds.height + 5f),
                            cornerRadius = CornerRadius(layout.cornerR + 2f, layout.cornerR + 2f),
                            style = Stroke(width = 3f)
                        )
                    }

                    val nodeCenterY = (item.yTop + item.yBottom) / 2f
                    val yCenter = pos.labelCenterY
                    val xLabel = item.bounds.left - 6.dp.toPx()
                    if (abs(yCenter - nodeCenterY) > 3.dp.toPx()) {
                        drawLine(
                            color = item.node.color.copy(alpha = 0.45f),
                            start = Offset(item.bounds.left, nodeCenterY),
                            end = Offset(xLabel + 2.dp.toPx(), yCenter),
                            strokeWidth = 1f.dp.toPx()
                        )
                    }

                    labelPaint.textAlign = Paint.Align.RIGHT
                    boldPaint.textAlign = Paint.Align.RIGHT
                    val maxLeftW = (xLabel - 4.dp.toPx()).coerceAtLeast(10f)

                    val labelText = item.node.shortLabel
                    val originalBoldSize = boldPaint.textSize
                    val (fittedTitleSize, titleLines) = fitCanvasTextLines(
                        text = labelText,
                        maxWidth = maxLeftW,
                        paint = boldPaint,
                        baseTextSize = boldTextPx,
                        minTextSize = minTextPx,
                        allowTwoLineWrap = true
                    )
                    boldPaint.textSize = fittedTitleSize
                    val titleLineStep = fittedTitleSize * 1.12f
                    val amountExtraShift = if (titleLines.size > 1) {
                        val l1Y = yCenter - titleBaselineOffset - titleLineStep * 0.55f
                        val l2Y = yCenter - titleBaselineOffset + titleLineStep * 0.35f
                        drawContext.canvas.nativeCanvas.drawText(titleLines[0], xLabel, l1Y, boldPaint)
                        drawContext.canvas.nativeCanvas.drawText(titleLines[1], xLabel, l2Y, boldPaint)
                        titleLineStep * 0.42f
                    } else {
                        drawContext.canvas.nativeCanvas.drawText(
                            titleLines[0],
                            xLabel,
                            yCenter - titleBaselineOffset,
                            boldPaint
                        )
                        0f
                    }
                    boldPaint.textSize = originalBoldSize

                    val amountText = fmtCompact(item.node.amount.finiteOrZero())
                    val originalLabelSize = labelPaint.textSize
                    val (fittedAmountSize, amountLines) = fitCanvasTextLines(
                        text = amountText,
                        maxWidth = maxLeftW,
                        paint = labelPaint,
                        baseTextSize = textPx,
                        minTextSize = minTextPx,
                        allowTwoLineWrap = false
                    )
                    labelPaint.textSize = fittedAmountSize
                    labelPaint.color = item.node.color.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        amountLines[0],
                        xLabel,
                        yCenter + amountBaselineOffset + amountExtraShift,
                        labelPaint
                    )
                    labelPaint.textSize = originalLabelSize
                    labelPaint.color = textColor
                }

                // Central Pool Hub (in Granular Mode)
                if (!is3Pillar) {
                    val hub = layout.hubLayout
                    drawRoundRect(
                        brush = hubBrush,
                        topLeft = Offset(hub.bounds.left, hub.bounds.top),
                        size = Size(hub.bounds.width, hub.bounds.height),
                        cornerRadius = CornerRadius(layout.cornerR, layout.cornerR)
                    )
                    if (curSelected == centralHubNode.id) {
                        drawRoundRect(
                            color = selectionBorderColor,
                            topLeft = Offset(hub.bounds.left - 2.5f, hub.bounds.top - 2.5f),
                            size = Size(hub.bounds.width + 5f, hub.bounds.height + 5f),
                            cornerRadius = CornerRadius(layout.cornerR + 2f, layout.cornerR + 2f),
                            style = Stroke(width = 3f)
                        )
                    }
                    poolLabelPaint.textAlign = Paint.Align.CENTER
                    val poolBaselineY = (hub.bounds.top - 6.dp.toPx() - poolLabelPaint.fontMetrics.descent)
                        .coerceAtLeast(-poolLabelPaint.fontMetrics.ascent + 2.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(
                        "POOL",
                        layout.xCenter + (layout.nodeWidth / 2f),
                        poolBaselineY,
                        poolLabelPaint
                    )
                }

                // Outflow Nodes & Margin Labels
                val pctDenom = sumInflows.finiteOrZero().let { if (it > 0.0) it else 1.0 }
                layout.outflowLayouts.forEach { pos ->
                    val item = pos.layout
                    val isSel = curSelected == item.node.id
                    drawRoundRect(
                        color = item.node.color,
                        topLeft = Offset(item.bounds.left, item.bounds.top),
                        size = Size(item.bounds.width, item.bounds.height),
                        cornerRadius = CornerRadius(layout.cornerR, layout.cornerR)
                    )
                    if (isSel) {
                        drawRoundRect(
                            color = selectionBorderColor,
                            topLeft = Offset(item.bounds.left - 2.5f, item.bounds.top - 2.5f),
                            size = Size(item.bounds.width + 5f, item.bounds.height + 5f),
                            cornerRadius = CornerRadius(layout.cornerR + 2f, layout.cornerR + 2f),
                            style = Stroke(width = 3f)
                        )
                    }

                    val nodeCenterY = (item.yTop + item.yBottom) / 2f
                    val yCenter = pos.labelCenterY
                    val xLabel = item.bounds.right + 6.dp.toPx()
                    if (abs(yCenter - nodeCenterY) > 3.dp.toPx()) {
                        drawLine(
                            color = item.node.color.copy(alpha = 0.45f),
                            start = Offset(item.bounds.right, nodeCenterY),
                            end = Offset(xLabel - 2.dp.toPx(), yCenter),
                            strokeWidth = 1f.dp.toPx()
                        )
                    }

                    labelPaint.textAlign = Paint.Align.LEFT
                    boldPaint.textAlign = Paint.Align.LEFT
                    val pct = (item.node.amount.finiteOrZero().coerceAtLeast(0.0) / pctDenom * 100.0).coerceIn(0.0, 999.0)
                    val maxRightW = (layout.width - xLabel - 4.dp.toPx()).coerceAtLeast(10f)

                    val titleText = "${item.node.shortLabel} (${fmtPct(pct, 0)})"
                    val originalBoldSize = boldPaint.textSize
                    val (fittedTitleSize, titleLines) = fitCanvasTextLines(
                        text = titleText,
                        maxWidth = maxRightW,
                        paint = boldPaint,
                        baseTextSize = boldTextPx,
                        minTextSize = minTextPx,
                        allowTwoLineWrap = true
                    )
                    boldPaint.textSize = fittedTitleSize
                    val titleLineStep = fittedTitleSize * 1.12f
                    val amountExtraShift = if (titleLines.size > 1) {
                        val l1Y = yCenter - titleBaselineOffset - titleLineStep * 0.55f
                        val l2Y = yCenter - titleBaselineOffset + titleLineStep * 0.35f
                        drawContext.canvas.nativeCanvas.drawText(titleLines[0], xLabel, l1Y, boldPaint)
                        drawContext.canvas.nativeCanvas.drawText(titleLines[1], xLabel, l2Y, boldPaint)
                        titleLineStep * 0.42f
                    } else {
                        drawContext.canvas.nativeCanvas.drawText(
                            titleLines[0],
                            xLabel,
                            yCenter - titleBaselineOffset,
                            boldPaint
                        )
                        0f
                    }
                    boldPaint.textSize = originalBoldSize

                    val amountText = fmtCompact(item.node.amount.finiteOrZero())
                    val originalLabelSize = labelPaint.textSize
                    val (fittedAmountSize, amountLines) = fitCanvasTextLines(
                        text = amountText,
                        maxWidth = maxRightW,
                        paint = labelPaint,
                        baseTextSize = textPx,
                        minTextSize = minTextPx,
                        allowTwoLineWrap = false
                    )
                    labelPaint.textSize = fittedAmountSize
                    labelPaint.color = item.node.color.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        amountLines[0],
                        xLabel,
                        yCenter + amountBaselineOffset + amountExtraShift,
                        labelPaint
                    )
                    labelPaint.textSize = originalLabelSize
                    labelPaint.color = textColor
                }

                drawContext.canvas.restore()
            }
        }
    }
}
