package com.example.ui.components.sankey

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.GoodGreen
import com.example.util.Formatters.fmtCompact
import com.example.util.Formatters.fmtPct

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
    val textPx = with(density) { 9.5.sp.toPx() }
    val boldTextPx = with(density) { 10.sp.toPx() }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    val labelPaint = remember(textColor, textPx) {
        Paint().apply {
            color = textColor
            textSize = textPx
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

    var layoutNodesCache by remember { mutableStateOf<List<LayoutNode>>(emptyList()) }
    val cTeal = BrandTeal
    val cGreen = GoodGreen

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(inflowNodes, outflowNodes, viewMode) {
                    detectTapGestures { offset ->
                        val padX = 20.dp.toPx()
                        val clicked = layoutNodesCache.firstOrNull { node ->
                            offset.x in (node.bounds.left - padX)..(node.bounds.right + padX) &&
                                offset.y in node.bounds.top..node.bounds.bottom
                        }
                        if (clicked != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectNode(if (selectedNodeId == clicked.node.id) null else clicked.node.id)
                        } else {
                            onSelectNode(null)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val is3Pillar = viewMode == 0

            val paddingTop = 14.dp.toPx()
            val paddingBottom = 22.dp.toPx()
            val nodeWidth = 8.5f.dp.toPx()
            val cornerR = 4.dp.toPx()

            val leftMargin = if (is3Pillar) 68.dp.toPx() else 74.dp.toPx()
            val rightMargin = if (is3Pillar) 118.dp.toPx() else 96.dp.toPx()

            val xInflow = leftMargin
            val xOutflow = w - rightMargin - nodeWidth
            val ribbonStart = xInflow + nodeWidth
            val ribbonEnd = xOutflow
            val xCenter = (ribbonStart + ribbonEnd - nodeWidth) / 2f

            val availableH = h - paddingTop - paddingBottom
            if (availableH <= 0f) return@Canvas
            val gap = if (is3Pillar) 10.dp.toPx() else 4.dp.toPx()

            // 1. Layout Inflow Nodes
            val totalInflowGap = (inflowNodes.size - 1) * gap
            val usableInflowH = (availableH - totalInflowGap).coerceAtLeast(10f)
            val inflowLayouts = mutableListOf<LayoutNode>()
            var currentY = paddingTop

            val minNodeH = if (is3Pillar) 36.dp.toPx() else 15.dp.toPx()
            val extraInflowH = (usableInflowH - (inflowNodes.size * minNodeH)).coerceAtLeast(0f)

            inflowNodes.forEach { node ->
                val fraction = (node.amount / sumInflows).toFloat()
                val nodeH = if (is3Pillar) {
                    usableInflowH
                } else {
                    minNodeH + (fraction * extraInflowH)
                }
                val bounds = RoundRect(
                    left = xInflow,
                    top = currentY,
                    right = xInflow + nodeWidth,
                    bottom = currentY + nodeH,
                    cornerRadius = CornerRadius(cornerR, cornerR)
                )
                inflowLayouts.add(LayoutNode(node, currentY, currentY + nodeH, bounds))
                currentY += nodeH + gap
            }

            // 2. Layout Central Hub Node
            val hubTop = paddingTop
            val hubBottom = h - paddingBottom
            val hubBounds = RoundRect(
                left = xCenter,
                top = hubTop,
                right = xCenter + nodeWidth,
                bottom = hubBottom,
                cornerRadius = CornerRadius(cornerR, cornerR)
            )
            val hubLayout = LayoutNode(centralHubNode, hubTop, hubBottom, hubBounds)

            // 3. Layout Outflow Nodes
            val totalOutflowGap = (outflowNodes.size - 1) * gap
            val usableOutflowH = (availableH - totalOutflowGap).coerceAtLeast(10f)
            val outflowLayouts = mutableListOf<LayoutNode>()
            var currentOutY = paddingTop

            val extraOutflowH = (usableOutflowH - (outflowNodes.size * minNodeH)).coerceAtLeast(0f)

            outflowNodes.forEach { node ->
                val fraction = (node.amount / sumOutflows).toFloat()
                val nodeH = minNodeH + (fraction * extraOutflowH)
                val bounds = RoundRect(
                    left = xOutflow,
                    top = currentOutY,
                    right = xOutflow + nodeWidth,
                    bottom = currentOutY + nodeH,
                    cornerRadius = CornerRadius(cornerR, cornerR)
                )
                outflowLayouts.add(LayoutNode(node, currentOutY, currentOutY + nodeH, bounds))
                currentOutY += nodeH + gap
            }

            layoutNodesCache = if (is3Pillar) {
                inflowLayouts + outflowLayouts
            } else {
                inflowLayouts + hubLayout + outflowLayouts
            }

            if (is3Pillar) {
                val inLayout = inflowLayouts.firstOrNull()
                if (inLayout != null) {
                    val inUsableH = inLayout.yBottom - inLayout.yTop
                    var currentSourceY = inLayout.yTop

                    outflowLayouts.forEach { outLayout ->
                        val fraction = (outLayout.node.amount / sumOutflows).toFloat()
                        val sliceH = fraction * inUsableH
                        val sourceTop = currentSourceY
                        val sourceBottom = currentSourceY + sliceH
                        currentSourceY += sliceH

                        val isHighlighted = selectedNodeId == null ||
                            selectedNodeId == inLayout.node.id ||
                            selectedNodeId == outLayout.node.id
                        val fillAlpha = if (isHighlighted) 0.44f else 0.08f
                        val strokeAlpha = if (isHighlighted) 0.75f else 0.18f
                        val strokeW = if (isHighlighted) 1.5.dp.toPx() else 0.75.dp.toPx()

                        val x1 = inLayout.bounds.right
                        val x2 = outLayout.bounds.left
                        val cX = (x2 - x1) * 0.52f

                        val ribbonPath = Path().apply {
                            moveTo(x1, sourceTop)
                            cubicTo(x1 + cX, sourceTop, x2 - cX, outLayout.yTop, x2, outLayout.yTop)
                            lineTo(x2, outLayout.yBottom)
                            cubicTo(x2 - cX, outLayout.yBottom, x1 + cX, sourceBottom, x1, sourceBottom)
                            close()
                        }

                        drawPath(
                            path = ribbonPath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    inLayout.node.color.copy(alpha = fillAlpha),
                                    outLayout.node.color.copy(alpha = fillAlpha)
                                ),
                                startX = x1,
                                endX = x2
                            )
                        )

                        val topEdge = Path().apply {
                            moveTo(x1, sourceTop)
                            cubicTo(x1 + cX, sourceTop, x2 - cX, outLayout.yTop, x2, outLayout.yTop)
                        }
                        val bottomEdge = Path().apply {
                            moveTo(x1, sourceBottom)
                            cubicTo(x1 + cX, sourceBottom, x2 - cX, outLayout.yBottom, x2, outLayout.yBottom)
                        }
                        drawPath(topEdge, color = outLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                        drawPath(bottomEdge, color = outLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                    }
                }
            } else {
                var hubInY = hubTop
                val hubUsableH = hubBottom - hubTop

                inflowLayouts.forEach { inLayout ->
                    val ribbonH = ((inLayout.node.amount / sumInflows) * hubUsableH).toFloat()
                    val hubTargetTop = hubInY
                    val hubTargetBottom = hubInY + ribbonH
                    hubInY += ribbonH

                    val isHighlighted = selectedNodeId == null || selectedNodeId == inLayout.node.id || selectedNodeId == centralHubNode.id
                    val fillAlpha = if (isHighlighted) 0.42f else 0.08f
                    val strokeAlpha = if (isHighlighted) 0.75f else 0.20f
                    val strokeW = if (isHighlighted) 1.5.dp.toPx() else 0.75.dp.toPx()

                    val x1 = inLayout.bounds.right
                    val x2 = xCenter
                    val cX = (x2 - x1) * 0.52f

                    val ribbonPath = Path().apply {
                        moveTo(x1, inLayout.yTop)
                        cubicTo(x1 + cX, inLayout.yTop, x2 - cX, hubTargetTop, x2, hubTargetTop)
                        lineTo(x2, hubTargetBottom)
                        cubicTo(x2 - cX, hubTargetBottom, x1 + cX, inLayout.yBottom, x1, inLayout.yBottom)
                        close()
                    }

                    drawPath(
                        path = ribbonPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(inLayout.node.color.copy(alpha = fillAlpha), cTeal.copy(alpha = fillAlpha)),
                            startX = x1,
                            endX = x2
                        )
                    )

                    val topEdge = Path().apply {
                        moveTo(x1, inLayout.yTop)
                        cubicTo(x1 + cX, inLayout.yTop, x2 - cX, hubTargetTop, x2, hubTargetTop)
                    }
                    val bottomEdge = Path().apply {
                        moveTo(x1, inLayout.yBottom)
                        cubicTo(x1 + cX, inLayout.yBottom, x2 - cX, hubTargetBottom, x2, hubTargetBottom)
                    }
                    drawPath(topEdge, color = inLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                    drawPath(bottomEdge, color = inLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                }

                var hubOutY = hubTop

                outflowLayouts.forEach { outLayout ->
                    val ribbonH = ((outLayout.node.amount / sumOutflows) * hubUsableH).toFloat()
                    val hubSourceTop = hubOutY
                    val hubSourceBottom = hubOutY + ribbonH
                    hubOutY += ribbonH

                    val isHighlighted = selectedNodeId == null || selectedNodeId == outLayout.node.id || selectedNodeId == centralHubNode.id
                    val fillAlpha = if (isHighlighted) 0.42f else 0.08f
                    val strokeAlpha = if (isHighlighted) 0.75f else 0.20f
                    val strokeW = if (isHighlighted) 1.5.dp.toPx() else 0.75.dp.toPx()

                    val x1 = xCenter + nodeWidth
                    val x2 = outLayout.bounds.left
                    val cX = (x2 - x1) * 0.52f

                    val ribbonPath = Path().apply {
                        moveTo(x1, hubSourceTop)
                        cubicTo(x1 + cX, hubSourceTop, x2 - cX, outLayout.yTop, x2, outLayout.yTop)
                        lineTo(x2, outLayout.yBottom)
                        cubicTo(x2 - cX, outLayout.yBottom, x1 + cX, hubSourceBottom, x1, hubSourceBottom)
                        close()
                    }

                    drawPath(
                        path = ribbonPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(cTeal.copy(alpha = fillAlpha), outLayout.node.color.copy(alpha = fillAlpha)),
                            startX = x1,
                            endX = x2
                        )
                    )

                    val topEdge = Path().apply {
                        moveTo(x1, hubSourceTop)
                        cubicTo(x1 + cX, hubSourceTop, x2 - cX, outLayout.yTop, x2, outLayout.yTop)
                    }
                    val bottomEdge = Path().apply {
                        moveTo(x1, hubSourceBottom)
                        cubicTo(x1 + cX, hubSourceBottom, x2 - cX, outLayout.yBottom, x2, outLayout.yBottom)
                    }
                    drawPath(topEdge, color = outLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                    drawPath(bottomEdge, color = outLayout.node.color.copy(alpha = strokeAlpha), style = Stroke(strokeW))
                }
            }

            // Inflow Nodes & Margin Labels
            inflowLayouts.forEach { layout ->
                val isSel = selectedNodeId == layout.node.id
                drawRoundRect(
                    color = layout.node.color,
                    topLeft = Offset(layout.bounds.left, layout.bounds.top),
                    size = Size(layout.bounds.width, layout.bounds.height),
                    cornerRadius = CornerRadius(cornerR, cornerR)
                )
                if (isSel) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(layout.bounds.left - 2f, layout.bounds.top - 2f),
                        size = Size(layout.bounds.width + 4f, layout.bounds.height + 4f),
                        cornerRadius = CornerRadius(cornerR + 2f, cornerR + 2f),
                        style = Stroke(width = 2.5f)
                    )
                }

                labelPaint.textAlign = Paint.Align.RIGHT
                boldPaint.textAlign = Paint.Align.RIGHT
                val xLabel = layout.bounds.left - 6.dp.toPx()
                val yCenter = (layout.yTop + layout.yBottom) / 2f
                val maxLeftW = (xLabel - 4.dp.toPx()).coerceAtLeast(10f)

                val labelText = layout.node.shortLabel
                val originalBoldSize = boldPaint.textSize
                val measuredBoldW = boldPaint.measureText(labelText)
                if (measuredBoldW > maxLeftW) {
                    boldPaint.textSize = (originalBoldSize * (maxLeftW / measuredBoldW)).coerceAtLeast(7f)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    labelText,
                    xLabel,
                    yCenter - 2.dp.toPx(),
                    boldPaint
                )
                boldPaint.textSize = originalBoldSize

                val amountText = fmtCompact(layout.node.amount)
                val originalLabelSize = labelPaint.textSize
                val measuredLabelW = labelPaint.measureText(amountText)
                if (measuredLabelW > maxLeftW) {
                    labelPaint.textSize = (originalLabelSize * (maxLeftW / measuredLabelW)).coerceAtLeast(7f)
                }
                labelPaint.color = layout.node.color.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    amountText,
                    xLabel,
                    yCenter + 9.dp.toPx(),
                    labelPaint
                )
                labelPaint.textSize = originalLabelSize
                labelPaint.color = textColor
            }

            if (!is3Pillar) {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(cTeal, cGreen)),
                    topLeft = Offset(hubBounds.left, hubBounds.top),
                    size = Size(hubBounds.width, hubBounds.height),
                    cornerRadius = CornerRadius(cornerR, cornerR)
                )
                if (selectedNodeId == centralHubNode.id) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(hubBounds.left - 2f, hubBounds.top - 2f),
                        size = Size(hubBounds.width + 4f, hubBounds.height + 4f),
                        cornerRadius = CornerRadius(cornerR + 2f, cornerR + 2f),
                        style = Stroke(width = 2.5f)
                    )
                }
                poolLabelPaint.textAlign = Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(
                    "POOL",
                    xCenter + (nodeWidth / 2f),
                    hubTop - 5.dp.toPx(),
                    poolLabelPaint
                )
            }

            // Outflow Nodes & Margin Labels
            outflowLayouts.forEach { layout ->
                val isSel = selectedNodeId == layout.node.id
                drawRoundRect(
                    color = layout.node.color,
                    topLeft = Offset(layout.bounds.left, layout.bounds.top),
                    size = Size(layout.bounds.width, layout.bounds.height),
                    cornerRadius = CornerRadius(cornerR, cornerR)
                )
                if (isSel) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(layout.bounds.left - 2f, layout.bounds.top - 2f),
                        size = Size(layout.bounds.width + 4f, layout.bounds.height + 4f),
                        cornerRadius = CornerRadius(cornerR + 2f, cornerR + 2f),
                        style = Stroke(width = 2.5f)
                    )
                }

                labelPaint.textAlign = Paint.Align.LEFT
                boldPaint.textAlign = Paint.Align.LEFT
                val xLabel = layout.bounds.right + 6.dp.toPx()
                val yCenter = (layout.yTop + layout.yBottom) / 2f
                val pct = (layout.node.amount / sumInflows * 100.0).coerceIn(0.0, 100.0)

                val maxRightW = (w - xLabel - 4.dp.toPx()).coerceAtLeast(10f)

                val titleText = "${layout.node.shortLabel} (${fmtPct(pct, 0)})"
                val originalBoldSize = boldPaint.textSize
                val measuredBoldW = boldPaint.measureText(titleText)
                if (measuredBoldW > maxRightW) {
                    boldPaint.textSize = (originalBoldSize * (maxRightW / measuredBoldW)).coerceAtLeast(7f)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    titleText,
                    xLabel,
                    yCenter - 2.dp.toPx(),
                    boldPaint
                )
                boldPaint.textSize = originalBoldSize

                val amountText = fmtCompact(layout.node.amount)
                val originalLabelSize = labelPaint.textSize
                val measuredLabelW = labelPaint.measureText(amountText)
                if (measuredLabelW > maxRightW) {
                    labelPaint.textSize = (originalLabelSize * (maxRightW / measuredLabelW)).coerceAtLeast(7f)
                }
                labelPaint.color = layout.node.color.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    amountText,
                    xLabel,
                    yCenter + 9.dp.toPx(),
                    labelPaint
                )
                labelPaint.textSize = originalLabelSize
                labelPaint.color = textColor
            }
        }
    }
}
