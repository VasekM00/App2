package com.example.ui.components.charts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ChartMetricPill(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 4.5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = color
                ),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Computes a human-friendly ceiling for chart axes (e.g. 17.3M -> 20.0M with 4 steps of 5M).
 */
fun computeNiceAxisMax(rawMax: Double, steps: Int = 4): Double {
    if (rawMax <= 0.0) return 1.0
    val rawStep = rawMax / steps
    val exponent = floor(log10(rawStep))
    val fraction = rawStep / 10.0.pow(exponent)
    val niceFraction = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    val niceStep = niceFraction * 10.0.pow(exponent)
    var ceilMax = niceStep * steps
    while (ceilMax < rawMax) {
        ceilMax += niceStep
    }
    return ceilMax
}

/**
 * Constructs a smooth monotone cubic Bezier path passing through the given points.
 */
fun buildMonotoneCubicPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 0 until points.size - 1) {
        val p0 = if (i > 0) points[i - 1] else points[i]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else p2

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

/**
 * Constructs a smooth monotone cubic area path closed against [baselineY].
 */
fun buildMonotoneCubicAreaPath(points: List<Offset>, baselineY: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, baselineY)
    path.lineTo(points[0].x, points[0].y)
    if (points.size == 1) {
        path.lineTo(points[0].x, baselineY)
        path.close()
        return path
    }
    for (i in 0 until points.size - 1) {
        val p0 = if (i > 0) points[i - 1] else points[i]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else p2

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    path.lineTo(points.last().x, baselineY)
    path.close()
    return path
}

/**
 * Constructs a smooth closed band path between [topPoints] and [bottomPoints].
 */
fun buildMonotoneBandPath(topPoints: List<Offset>, bottomPoints: List<Offset>): Path {
    val path = Path()
    if (topPoints.isEmpty() || bottomPoints.isEmpty()) return path
    path.moveTo(topPoints[0].x, topPoints[0].y)
    for (i in 0 until topPoints.size - 1) {
        val p0 = if (i > 0) topPoints[i - 1] else topPoints[i]
        val p1 = topPoints[i]
        val p2 = topPoints[i + 1]
        val p3 = if (i + 2 < topPoints.size) topPoints[i + 2] else p2

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }

    path.lineTo(bottomPoints.last().x, bottomPoints.last().y)
    for (i in bottomPoints.size - 1 downTo 1) {
        val p0 = if (i < bottomPoints.size - 1) bottomPoints[i + 1] else bottomPoints[i]
        val p1 = bottomPoints[i]
        val p2 = bottomPoints[i - 1]
        val p3 = if (i > 1) bottomPoints[i - 2] else p2

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    path.close()
    return path
}
